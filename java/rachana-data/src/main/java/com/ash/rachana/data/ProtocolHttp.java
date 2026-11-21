/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.data;

import com.ash.rachana.parse.Json;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * The Data Protocol over the JDK's HTTP server: one {@link ProtocolHost}'s resources (section 4) as JSON, Arrow IPC (5.2) or NDJSON (5.3),
 * the minimal SSE change stream (8.2: backlog, then live frames, a {@code : hb} heartbeat; filtered, conflated and windowed by a
 * {@code POST} body) and a windowed stream's {@code POST {base}/changes/{sub}/window} (16). The one HTTP binding of the protocol on the
 * JVM: the reference server ({@link ReferenceServer}) and the standalone server ({@code rachana-server}) both serve through it, so the two
 * cannot drift. Authentication, origins and limits are the server's; this class sees only the opaque caller it is handed.
 */
public final class ProtocolHttp {

    /** A windowed minimal SSE stream: its subscription and the caller that opened it. */
    private record Open(Subscription sub, Object caller) {}

    private final ProtocolHost host;
    private final BooleanSupplier closed;
    private final Map<String, Open> streams = new ConcurrentHashMap<>();

    /**
     * @param host the routes
     * @param closed whether the server is shutting down (open streams end)
     */
    public ProtocolHttp(ProtocolHost host, BooleanSupplier closed) {
        this.host = host;
        this.closed = closed;
    }

    public ProtocolHost host() {
        return host;
    }

    /** Ends every windowed stream (a reset or a shutdown). */
    public void closeStreams() {
        streams.values().forEach(o -> o.sub().close());
        streams.clear();
    }

    /**
     * Answers one request below the base.
     *
     * @param rel the path below the base ({@code /tables/sales/rows})
     * @param q the decoded query parameters
     * @param body the parsed JSON body, or null
     * @param caller the opaque caller handed to the providers (null for none)
     */
    public void serve(HttpExchange x, String rel, Map<String, String> q, Object body, Object caller) throws IOException {
        String m = x.getRequestMethod();
        if (rel.equals("/changes") && (m.equals("GET") || m.equals("POST"))) {
            stream(x, q, body, caller);
        } else if (rel.startsWith("/changes/") && rel.endsWith("/window") && m.equals("POST")) {
            window(x, rel.substring("/changes/".length(), rel.length() - "/window".length()), body, caller);
        } else if (rel.startsWith("/tables/") && (rel.endsWith("/rows") || rel.endsWith("/query"))
                && binaryAccept(x.getRequestHeaders().getFirst("Accept"))) {
            binary(x, m, rel, q, body, x.getRequestHeaders().getFirst("Accept").contains("arrow") ? "arrow" : "ndjson", caller);
        } else {
            ProtocolHost.Answer a = host.handle(caller, m, rel, q, body);
            json(x, a.status(), a.body());
        }
    }

    /**
     * {@code POST {base}/changes/{sub}/window} (PROTOCOL 16): changes the window of a windowed minimal SSE stream to {@code {keys, top?}};
     * {@code 204}. Only the caller that opened the stream may: anyone else, or a stream that is gone, is {@code rdp.table.unknown}.
     */
    private void window(HttpExchange x, String sub, Object body, Object caller) throws IOException {
        Open o = streams.get(URLDecoder.decode(sub, StandardCharsets.UTF_8));
        if (o == null || !Objects.equals(o.caller(), caller) || o.sub().closed() || !o.sub().windowed()) {
            json(x, 404, ProtocolError.of("rdp.table.unknown", 404, "no windowed stream '" + sub + "' of yours", "operation", "window").body());
            return;
        }
        Map<?, ?> b = body instanceof Map<?, ?> mm ? mm : Map.of();
        o.sub().window(ProtocolHost.keys(b.get("keys")), Boolean.TRUE.equals(b.get("top")));
        json(x, 204, null);
    }

    /**
     * The minimal SSE form (section 8.2): the frames after {@code after} (or {@code Last-Event-ID}), then live ones until the client leaves.
     * {@code POST} takes {@code {table, after, filter?, conflate?: {everyMs}, window?}}; {@code GET} also takes {@code everyMs}. A position
     * that cannot be resumed is one {@code gap} frame ({@code recoverable: false}).
     */
    private void stream(HttpExchange x, Map<String, String> q, Object body, Object who) throws IOException {
        long idle = 0;
        Map<?, ?> b = body instanceof Map<?, ?> m ? m : Map.of();
        String table = b.get("table") instanceof String t ? t : q.get("table");
        String after = b.get("after") instanceof String a ? a : q.get("after") != null ? q.get("after") : x.getRequestHeaders().getFirst("Last-Event-ID");
        long every;
        try {
            every = b.get("conflate") instanceof Map<?, ?> c && c.get("everyMs") instanceof Number n ? n.longValue()
                    : q.get("everyMs") != null ? Long.parseLong(q.get("everyMs")) : 0;
        } catch (NumberFormatException e) {
            json(x, 422, ProtocolError.of("rdp.query.invalid", 422, "everyMs is a whole number").body());
            return;
        }
        List<List<Object>> window = null;
        boolean top = false;
        if (b.get("window") instanceof Map<?, ?> w) {                 // PROTOCOL 16: a windowed stream; its first frame is subd
            window = ProtocolHost.keys(w.get("keys"));
            top = Boolean.TRUE.equals(w.get("top"));
        }
        BlockingQueue<Map<String, Object>> live = new LinkedBlockingQueue<>();
        Subscription sub;
        try {
            sub = host.subscribe(table, new TableProvider.Subscribe(after, b.get("filter"), every, window, top), live::add, who);
        } catch (ProtocolError e) {
            json(x, e.status(), e.body());
            return;
        }
        String id = window == null ? null : java.util.UUID.randomUUID().toString();
        if (id != null && sub.recovered()) {
            streams.put(id, new Open(sub, who));
        }
        try (sub) {
            x.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
            x.getResponseHeaders().set("Cache-Control", "no-store");
            x.sendResponseHeaders(200, 0);
            OutputStream out = x.getResponseBody();
            if (!sub.recovered()) {
                event(out, host.changes(table, "-:0", who).get(0));      // the gap frame at the table's position
                out.flush();
                return;
            }
            if (id != null) {
                Map<String, Object> subd = new LinkedHashMap<>();
                subd.put("t", "subd");
                subd.put("sub", id);
                subd.put("table", table);
                event(out, subd);
            }
            for (Map<String, Object> f = live.poll(); f != null; f = live.poll()) {
                event(out, f);                                       // the backlog, then a heartbeat, then live frames
            }
            out.write(": hb\n\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            while (!closed.getAsBoolean() && !sub.closed()) {
                Map<String, Object> f = live.poll(id == null ? 15_000 : 200, TimeUnit.MILLISECONDS);
                if (f == null) {
                    if (id == null || ++idle % 75 == 0) {
                        out.write(": hb\n\n".getBytes(StandardCharsets.UTF_8));
                    }
                } else {
                    event(out, f);
                }
                out.flush();
            }
            for (Map<String, Object> f = live.poll(); f != null; f = live.poll()) {
                event(out, f);                                       // what ended it (a gap)
            }
            out.flush();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (id != null) {
                streams.remove(id);
            }
        }
    }

    /**
     * Rows as Arrow IPC (section 5.2: only for a table whose {@code can} has {@code arrow}, else {@code 406}) or NDJSON (section 5.3: one
     * header object, then one array of cells per line, written as they are produced). The answer is the JSON page's, in the other form.
     */
    @SuppressWarnings("unchecked")
    private void binary(HttpExchange x, String m, String rel, Map<String, String> q, Object body, String form, Object caller) throws IOException {
        String name = rel.substring("/tables/".length(), rel.lastIndexOf('/'));
        com.ash.rachana.table.TableSchema schema;
        try {
            schema = host.schemaOf(name, caller);
        } catch (ProtocolError e) {
            json(x, e.status(), e.body());
            return;
        }
        if (form.equals("arrow") && !schema.can("arrow")) {
            json(x, 406, ProtocolError.of("rdp.query.unsupported", 406, name + " is not offered as Arrow", "table", name, "operation", "arrow").body());
            return;
        }
        ProtocolHost.Answer a = host.handle(caller, m, rel, q, body);
        if (a.status() != 200) {
            json(x, a.status(), a.body());
            return;
        }
        Map<String, Object> page = (Map<String, Object>) a.body();
        x.getResponseHeaders().set("Cache-Control", "no-store");
        if (form.equals("arrow")) {
            byte[] bytes = ArrowRows.write(page, schema, ArrowRows.BATCH_ROWS);
            x.getResponseHeaders().set("Content-Type", ArrowRows.MEDIA);
            x.sendResponseHeaders(200, bytes.length);
            x.getResponseBody().write(bytes);
            return;
        }
        x.getResponseHeaders().set("Content-Type", NdjsonRows.MEDIA);
        x.sendResponseHeaders(200, 0);
        OutputStream out = x.getResponseBody();
        NdjsonRows.write(page, out);
        out.flush();
    }

    /** Whether the reader asks only for Arrow or NDJSON (sections 5.2, 5.3). */
    private static boolean binaryAccept(String accept) {
        return accept != null && (accept.contains("application/vnd.apache.arrow.stream") || accept.contains("application/x-ndjson"))
                && !accept.contains("application/json") && !accept.contains("*/*");
    }

    private static void event(OutputStream out, Map<String, Object> f) throws IOException {
        String id = f.get("epoch") == null ? "" : "id: " + f.get("epoch") + ":" + f.get("seq") + "\n";
        String text = id + "event: " + f.get("t") + "\ndata: " + Json.write(f) + "\n\n";
        out.write(text.getBytes(StandardCharsets.UTF_8));
    }

    /** A JSON answer ({@code Cache-Control: no-store}); a null body is an empty answer. */
    public static void json(HttpExchange x, int status, Object body) throws IOException {
        x.getResponseHeaders().set("Cache-Control", "no-store");
        if (body == null) {
            x.sendResponseHeaders(status, -1);
            return;
        }
        byte[] b = Json.write(body).getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().set("Content-Type", "application/json");
        x.sendResponseHeaders(status, b.length);
        x.getResponseBody().write(b);
    }

    /** The query parameters of a raw query string, decoded (a repeated name: the last wins). */
    public static Map<String, String> query(String raw) {
        Map<String, String> q = new LinkedHashMap<>();
        if (raw != null) {
            for (String part : raw.split("&")) {
                if (!part.isEmpty()) {
                    int eq = part.indexOf('=');
                    String k = URLDecoder.decode(eq < 0 ? part : part.substring(0, eq), StandardCharsets.UTF_8);
                    q.put(k, eq < 0 ? "" : URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8));
                }
            }
        }
        return q;
    }
}
