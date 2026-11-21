/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.data;

import com.ash.rachana.parse.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * The reference Data Protocol server (small, for conformance and for trying a view): a {@link ProtocolHost} over the JDK's HTTP server,
 * serving a {@link MemoryHost} and any providers added to {@code host().protocol()}.
 * Under {@code base}: the resources of section 4, the minimal SSE change stream of section 8.2 (backlog, then live frames, a {@code : hb}
 * heartbeat), and the test adapter of section 15 ({@code POST /_test/fixture} loads a case's table, {@code POST /_test/patch} publishes a
 * patch, {@code POST /_test/bridge} answers a bridge message; {@link #actAs} and {@link #testPath} for providers with readers and
 * conventions of their own), and a windowed stream's {@code POST /changes/{sub}/window} (PROTOCOL 16). Optionally serves a folder of static files outside the base (same origin
 * for a page that embeds a view). Not a production server: no authentication, everything in memory, loopback only.
 *
 * <p>Usage: {@code java --add-opens=java.base/java.nio=org.apache.arrow.memory.core -m com.ash.rachana.data/com.ash.rachana.data.ReferenceServer [--port 0] [--base /rdp/v1] [--static <dir>]
 * [--table <{schema, rows}.json>]...}; prints {@code rdp-reference-server listening on http://127.0.0.1:<port><base>} when ready.
 */
public final class ReferenceServer implements AutoCloseable {

    private final HttpServer server;
    private final MemoryHost host;
    private final String base;
    private final Path statics;
    private volatile boolean closed;
    private final Map<Object, Channel> channels = new java.util.HashMap<>();          // the test bridge's channels, one per caller
    private final Map<String, Open> streams = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, java.util.function.Function<Object, ProtocolHost.Answer>> testPaths = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile Object caller;
    private final List<Runnable> resets = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** One bridge channel of the test adapter and the messages it pushed. */
    private record Channel(ProtocolHost.Bridge bridge, List<Map<String, Object>> out) {}

    /** A windowed minimal SSE stream (PROTOCOL 16): its subscription and the caller that opened it. */
    private record Open(Subscription sub, Object caller) {}

    public ReferenceServer(int port, String base, Path statics) throws IOException {
        this.base = base == null || base.equals("/") ? "" : base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        this.statics = statics;
        this.host = new MemoryHost(this.base);
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/", this::serve);
        server.start();
    }

    public MemoryHost host() {
        return host;
    }

    public int port() {
        return server.getAddress().getPort();
    }

    /** The base address: {@code http://127.0.0.1:<port><base>}. */
    public String url() {
        return "http://127.0.0.1:" + port() + base;
    }

    @Override
    public void close() {
        closed = true;
        server.stop(0);
    }

    /**
     * The caller every later request is made as (PROTOCOL 15's test adapter: a host with readers acts as one; null for none), handed to
     * the providers as the request's caller; the test bridge keeps one channel per caller.
     */
    public void actAs(Object caller) {
        this.caller = caller;
    }

    /** The caller requests are made as ({@link #actAs}). */
    public Object caller() {
        return caller;
    }

    /**
     * Adds a test adapter path ({@code /_test/<name>}, PROTOCOL 15): the handler takes the parsed body (null for none) and answers; a
     * {@link ProtocolError} it throws is answered as the error. A module that adds tables (live tables) adds its own conventions so.
     */
    public ReferenceServer testPath(String name, java.util.function.Function<Object, ProtocolHost.Answer> handler) {
        testPaths.put("/_test/" + name, handler);
        return this;
    }

    /** Adds what {@link #reset} also does (a module's own tables dropped). */
    public ReferenceServer onReset(Runnable r) {
        resets.add(r);
        return this;
    }

    /** Starts a case afresh: every table, bridge channel and windowed stream gone, requests made as no caller. */
    public void reset() {
        resets.forEach(Runnable::run);
        synchronized (channels) {
            channels.values().forEach(c -> c.bridge().close());
            channels.clear();
        }
        streams.values().forEach(o -> o.sub().close());
        streams.clear();
        caller = null;
        host.clear();
    }

    private void serve(HttpExchange x) throws IOException {
        try (x) {
            String path = x.getRequestURI().getRawPath();
            if (!base.isEmpty() && !(path.equals(base) || path.startsWith(base + "/"))) {
                file(x, path);
                return;
            }
            String rel = path.substring(base.length());
            Map<String, String> q = query(x.getRequestURI().getRawQuery());
            Object body = null;
            byte[] in = x.getRequestBody().readAllBytes();
            if (in.length > 0) {
                try {
                    body = Json.read(new String(in, StandardCharsets.UTF_8));
                } catch (RuntimeException e) {
                    json(x, 422, ProtocolError.of("rdp.query.invalid", 422, "the body is not JSON").body());
                    return;
                }
            }
            String m = x.getRequestMethod();
            if (rel.startsWith("/_test/")) {
                test(x, rel, body);
            } else if (rel.equals("/changes") && (m.equals("GET") || m.equals("POST"))) {
                stream(x, q, body);
            } else if (rel.startsWith("/changes/") && rel.endsWith("/window") && m.equals("POST")) {
                window(x, rel.substring("/changes/".length(), rel.length() - "/window".length()), body);
            } else if (rel.startsWith("/tables/") && (rel.endsWith("/rows") || rel.endsWith("/query"))
                    && binaryAccept(x.getRequestHeaders().getFirst("Accept"))) {
                binary(x, m, rel, q, body, x.getRequestHeaders().getFirst("Accept").contains("arrow") ? "arrow" : "ndjson");
            } else {
                ProtocolHost.Answer a = host.protocol().handle(caller, m, rel, q, body);
                json(x, a.status(), a.body());
            }
        } catch (IOException e) {
            // the client went away
        }
    }

    @SuppressWarnings("unchecked")
    private void test(HttpExchange x, String rel, Object body) throws IOException {
        Map<String, Object> b = body instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        try {
            java.util.function.Function<Object, ProtocolHost.Answer> extra = testPaths.get(rel);
            if (extra != null) {
                ProtocolHost.Answer a = extra.apply(body);
                json(x, a.status(), a.body());
                return;
            }
            switch (rel) {
                case "/_test/fixture" -> {
                    reset();
                    host.put(b.get("schema"), (List<Map<String, Object>>) b.getOrDefault("rows", List.of()), "t1");
                    json(x, 204, null);
                }
                case "/_test/patch" -> {
                    host.publish(b);
                    json(x, 204, null);
                }
                case "/_test/bridge" -> json(x, 200, channel().bridge().handle(b));
                case "/_test/bridge/out" -> {
                    List<Map<String, Object>> out = channel().out();
                    List<Map<String, Object>> sent;
                    synchronized (out) {
                        sent = new java.util.ArrayList<>(out);
                        out.clear();
                    }
                    json(x, 200, sent);
                }
                default -> json(x, 404, ProtocolError.of("rdp.table.unknown", 404, "no test path " + rel).body());
            }
        } catch (ProtocolError e) {
            json(x, e.status(), e.body());
        }
    }

    /** The test bridge's channel of the current caller (one per caller: its subscriptions last until the next fixture). */
    private Channel channel() {
        Object c = caller;
        synchronized (channels) {
            return channels.computeIfAbsent(c, k -> {
                List<Map<String, Object>> out = new java.util.ArrayList<>();
                return new Channel(host.protocol().bridge(f -> {
                    synchronized (out) {
                        out.add(f);
                    }
                }, k), out);
            });
        }
    }

    /**
     * {@code POST {base}/changes/{sub}/window} (PROTOCOL 16): changes the window of a windowed minimal SSE stream to {@code {keys, top?}};
     * {@code 204}. Only the caller that opened the stream may: anyone else, or a stream that is gone, is {@code rdp.table.unknown}.
     */
    private void window(HttpExchange x, String sub, Object body) throws IOException {
        Open o = streams.get(URLDecoder.decode(sub, StandardCharsets.UTF_8));
        if (o == null || !java.util.Objects.equals(o.caller(), caller) || o.sub().closed() || !o.sub().windowed()) {
            json(x, 404, ProtocolError.of("rdp.table.unknown", 404, "no windowed stream '" + sub + "' of yours", "operation", "window").body());
            return;
        }
        Map<?, ?> b = body instanceof Map<?, ?> m ? m : Map.of();
        o.sub().window(ProtocolHost.keys(b.get("keys")), Boolean.TRUE.equals(b.get("top")));
        json(x, 204, null);
    }

    /**
     * The minimal SSE form (section 8.2): the frames after {@code after} (or {@code Last-Event-ID}), then live ones until the client leaves.
     * {@code POST} takes {@code {table, after, filter?, conflate?: {everyMs}}} (a filtered or conflated stream, sections 8.2 and 8.4);
     * {@code GET} also takes {@code everyMs}. A position that cannot be resumed is one {@code gap} frame ({@code recoverable: false}).
     */
    private void stream(HttpExchange x, Map<String, String> q, Object body) throws IOException {
        long idle = 0;
        Map<?, ?> b = body instanceof Map<?, ?> m ? m : Map.of();
        String table = b.get("table") instanceof String t ? t : q.get("table");
        String after = b.get("after") instanceof String a ? a : q.get("after") != null ? q.get("after") : x.getRequestHeaders().getFirst("Last-Event-ID");
        long every = b.get("conflate") instanceof Map<?, ?> c && c.get("everyMs") instanceof Number n ? n.longValue()
                : q.get("everyMs") != null ? Long.parseLong(q.get("everyMs")) : 0;
        List<List<Object>> window = null;
        boolean top = false;
        if (b.get("window") instanceof Map<?, ?> w) {                 // PROTOCOL 16: a windowed stream; its first frame is subd
            window = ProtocolHost.keys(w.get("keys"));
            top = Boolean.TRUE.equals(w.get("top"));
        }
        Object who = caller;
        BlockingQueue<Map<String, Object>> live = new LinkedBlockingQueue<>();
        Subscription sub;
        try {
            sub = host.protocol().subscribe(table, new TableProvider.Subscribe(after, b.get("filter"), every, window, top), live::add, who);
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
                event(out, host.protocol().changes(table, "-:0", who).get(0));      // the gap frame at the table's position
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
            while (!closed && !sub.closed()) {
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
    private void binary(HttpExchange x, String m, String rel, Map<String, String> q, Object body, String form) throws IOException {
        String name = rel.substring("/tables/".length(), rel.lastIndexOf('/'));
        com.ash.rachana.table.TableSchema schema;
        try {
            schema = host.protocol().schemaOf(name, caller);
        } catch (ProtocolError e) {
            json(x, e.status(), e.body());
            return;
        }
        if (form.equals("arrow") && !schema.can("arrow")) {
            json(x, 406, ProtocolError.of("rdp.query.unsupported", 406, name + " is not offered as Arrow", "table", name, "operation", "arrow").body());
            return;
        }
        ProtocolHost.Answer a = host.protocol().handle(caller, m, rel, q, body);
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

    private static final Map<String, String> TYPES = Map.of(".html", "text/html; charset=utf-8", ".js", "text/javascript; charset=utf-8",
            ".mjs", "text/javascript; charset=utf-8", ".css", "text/css; charset=utf-8", ".json", "application/json", ".yaml",
            "text/plain; charset=utf-8", ".svg", "image/svg+xml");

    private void file(HttpExchange x, String path) throws IOException {
        Path root = statics == null ? null : statics.toAbsolutePath().normalize();
        Path f = root == null ? null : root.resolve(URLDecoder.decode(path, StandardCharsets.UTF_8).substring(1)).normalize();
        if (f == null || !f.startsWith(root) || !Files.isRegularFile(f)) {
            x.sendResponseHeaders(404, -1);
            return;
        }
        String name = f.getFileName().toString();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.')) : "";
        byte[] bytes = Files.readAllBytes(f);
        x.getResponseHeaders().set("Content-Type", TYPES.getOrDefault(ext, "application/octet-stream"));
        x.sendResponseHeaders(200, bytes.length);
        x.getResponseBody().write(bytes);
    }

    private static void json(HttpExchange x, int status, Object body) throws IOException {
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

    private static Map<String, String> query(String raw) {
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

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        int port = 0;
        String base = "/rdp/v1";
        Path statics = null;
        List<Path> tables = new java.util.ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--base" -> base = args[++i];
                case "--static" -> statics = Path.of(args[++i]);
                case "--table" -> tables.add(Path.of(args[++i]));
                default -> throw new IllegalArgumentException("unknown argument " + args[i]);
            }
        }
        ReferenceServer s = new ReferenceServer(port, base, statics);
        for (Path t : tables) {
            Map<String, Object> doc = (Map<String, Object>) Json.read(Files.readString(t));
            s.host().put(doc.get("schema"), (List<Map<String, Object>>) doc.getOrDefault("rows", List.of()), "e1");
        }
        System.out.println("rdp-reference-server listening on " + s.url());
        System.out.flush();
        Thread.currentThread().join();
    }
}
