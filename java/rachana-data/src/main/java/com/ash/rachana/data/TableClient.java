/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.data;

import com.ash.rachana.parse.Json;
import com.ash.rachana.table.LiveScreen;
import com.ash.rachana.table.TableSchema;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The JVM side of the Data Protocol client (the JavaScript side is js/packages/rachana/src/rachana_data.js; both leave every meaning to
 * Core): reads a table's schema, reads its rows through the plan's query document (push-down) following {@code next} cursors to the
 * end, and follows its minimal SSE change stream. {@link #load} fills a {@link LiveScreen} with every table it reads. The HTTP client is
 * the host's: authentication is its job (PROTOCOL 9), this class adds no header.
 */
public final class TableClient {

    /** The row form asked of the host (PROTOCOL 5): JSON, Arrow IPC (a table whose {@code can} has {@code arrow}) or NDJSON. */
    public enum Form {
        JSON("application/json"), ARROW(ArrowRows.MEDIA), NDJSON(NdjsonRows.MEDIA);

        final String media;

        Form(String media) {
            this.media = media;
        }
    }

    /** A table read whole: its schema, rows in JSON form, and the stream position the rows are a snapshot of. */
    public record Read(TableSchema schema, List<Map<String, Object>> rows, String epoch, long seq) {}

    private final HttpClient http;
    private final String base;

    /** @param base the host's base address ({@code http://host/rdp/v1}) */
    public TableClient(HttpClient http, String base) {
        this.http = http == null ? HttpClient.newHttpClient() : http;
        this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /** The schema document of a table. */
    public TableSchema schema(String table) throws IOException, InterruptedException {
        return TableSchema.of(send("GET", "/tables/" + enc(table), null));
    }

    /**
     * Every row a query document selects (the plan's {@code query}; an empty one reads the table), page by page; the position is the
     * first page's (a host without {@code snapshot} may move rows between pages: a live table is reconciled by its stream).
     */
    public Read read(String table, Map<String, Object> query) throws IOException, InterruptedException {
        return read(table, query, Form.JSON);
    }

    /**
     * As {@link #read(String, Map)}, with the rows in the given form; Arrow is asked only of a table that declares it (JSON otherwise).
     * Every form gives the same rows in their JSON form.
     */
    @SuppressWarnings("unchecked")
    public Read read(String table, Map<String, Object> query, Form form) throws IOException, InterruptedException {
        TableSchema schema = schema(table);
        Form f = form == Form.ARROW && !schema.can("arrow") ? Form.JSON : form;
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> doc = new LinkedHashMap<>(query == null ? Map.of() : query);
        String epoch = null;
        long seq = 0;
        while (true) {
            Map<String, Object> page = f == Form.JSON ? (Map<String, Object>) send("POST", "/tables/" + enc(table) + "/query", doc)
                    : sendRows("/tables/" + enc(table) + "/query", doc, f);
            if (epoch == null && page.get("version") instanceof Map<?, ?> v) {
                epoch = String.valueOf(v.get("epoch"));
                seq = v.get("seq") instanceof Number n ? n.longValue() : 0;
            }
            rows.addAll(rows(page));
            if (!(page.get("next") instanceof String next)) {
                break;
            }
            doc.put("cursor", next);
        }
        return new Read(schema, rows, epoch, seq);
    }

    /** A page's rows as objects, from either JSON form (section 5.1). */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> rows(Map<String, Object> page) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (page.get("rows") instanceof List<?> l) {
            l.forEach(r -> out.add((Map<String, Object>) r));
        } else if (page.get("columns") instanceof List<?> cols && page.get("values") instanceof List<?> vals) {
            for (Object v : vals) {
                Map<String, Object> r = new LinkedHashMap<>();
                List<?> cells = (List<?>) v;
                for (int i = 0; i < cols.size() && i < cells.size(); i++) {
                    r.put(String.valueOf(cols.get(i)), cells.get(i));
                }
                out.add(r);
            }
        }
        return out;
    }

    /**
     * Reads every table a screen reads, through its plan (the filters and columns pushed down to tables that can take them), into the
     * screen. A table the host does not have is {@link LiveScreen#missing}: a failed entry, whose panels the screen shows as {@code error}
     * ({@code RCH-2070}, SPEC 2.4), never as no rows.
     *
     * @return the reads, by table name
     */
    @SuppressWarnings("unchecked")
    public Map<String, Read> load(LiveScreen screen) throws IOException, InterruptedException {
        Map<String, TableSchema> schemas = new LinkedHashMap<>();
        for (String t : screen.tableNames()) {
            try {
                schemas.put(t, schema(t));
            } catch (ProtocolError e) {
                if (!"rdp.table.unknown".equals(e.code())) {
                    throw e;
                }
                screen.missing(t);
            }
        }
        Map<String, Object> plan = screen.plan(schemas);
        Map<String, Read> out = new LinkedHashMap<>();
        for (Object o : (List<Object>) plan.get("tables")) {
            Map<String, Object> t = (Map<String, Object>) o;
            String name = String.valueOf(t.get("name"));
            if (!schemas.containsKey(name) || out.containsKey(name)) {
                continue;
            }
            Read r = read(name, (Map<String, Object>) t.get("query"));
            screen.table(name, r.schema(), r.rows());
            out.put(name, r);
        }
        return out;
    }

    /**
     * Follows a table's change stream from a position until {@code frames} returns false or the stream ends; each frame ({@code msg} or
     * {@code gap}) is handed over parsed. The caller applies the rules of section 8.3 (drop a seq not above the last; resync on a gap).
     */
    public void follow(String table, String epoch, long seq, java.util.function.Predicate<Map<String, Object>> frames)
            throws IOException, InterruptedException {
        follow(table, epoch, seq, null, 0, frames);
    }

    /**
     * As {@link #follow(String, String, long, java.util.function.Predicate)}, filtered (a filter tree: only the matching rows, PROTOCOL 8.2;
     * the table must declare {@code changes.filter}) and conflated ({@code everyMs} above 0: at most one message per window, merged by key,
     * PROTOCOL 8.4; a merged message's {@code first} is its first seq). Asked with {@code POST {base}/changes}.
     */
    public void follow(String table, String epoch, long seq, Object filter, long everyMs, java.util.function.Predicate<Map<String, Object>> frames)
            throws IOException, InterruptedException {
        follow(table, epoch, seq, filter, everyMs, null, false, frames);
    }

    /**
     * As {@link #follow(String, String, long, Object, long, java.util.function.Predicate)}, windowed (PROTOCOL 16, a table whose {@code can}
     * has {@code changes.window}): only patches for the keys the reader's page shows ({@code window}, each the list of key values), plus its
     * new {@code total} when rows outside the page change; {@code top} when the page starts at the first row of the table's own order. The
     * first frame handed over is {@code subd}, whose {@code sub} names the stream for {@link #window}. A null window is no window.
     */
    @SuppressWarnings("unchecked")
    public void follow(String table, String epoch, long seq, Object filter, long everyMs, List<List<Object>> window, boolean top,
            java.util.function.Predicate<Map<String, Object>> frames) throws IOException, InterruptedException {
        HttpRequest rq;
        if (filter == null && everyMs <= 0 && window == null) {
            rq = HttpRequest.newBuilder(URI.create(base + "/changes?table=" + enc(table) + "&after=" + enc(epoch + ":" + seq)))
                    .header("Accept", "text/event-stream").build();
        } else {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("table", table);
            body.put("after", epoch + ":" + seq);
            if (filter != null) {
                body.put("filter", filter);
            }
            if (everyMs > 0) {
                body.put("conflate", Map.of("everyMs", everyMs));
            }
            if (window != null) {
                Map<String, Object> w = new LinkedHashMap<>();
                w.put("keys", window);
                w.put("top", top);
                body.put("window", w);
            }
            rq = HttpRequest.newBuilder(URI.create(base + "/changes")).header("Accept", "text/event-stream")
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(Json.write(body))).build();
        }
        HttpResponse<java.io.InputStream> r = http.send(rq, HttpResponse.BodyHandlers.ofInputStream());
        if (r.statusCode() != 200) {
            throw error(r.statusCode(), new String(r.body().readAllBytes(), StandardCharsets.UTF_8));
        }
        try (BufferedReader in = new BufferedReader(new InputStreamReader(r.body(), StandardCharsets.UTF_8))) {
            StringBuilder data = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("data:")) {
                    data.append(line.substring(5).trim());
                } else if (line.isEmpty() && data.length() > 0) {
                    Object f = Json.read(data.toString());
                    data.setLength(0);
                    if (f instanceof Map<?, ?> m && !frames.test((Map<String, Object>) m)) {
                        return;
                    }
                }
            }
        }
    }

    /** Follows a table into a screen: each {@code msg} applied (in seq order) and its frames handed on; stops at a gap (resync is the caller's). */
    @SuppressWarnings("unchecked")
    public void follow(LiveScreen screen, String table, Read from, Consumer<List<com.ash.rachana.view.Frame>> frames, int max)
            throws IOException, InterruptedException {
        long[] last = {from.seq()};
        int[] n = {0};
        follow(table, from.epoch(), from.seq(), f -> {
            if (!"msg".equals(f.get("t"))) {
                return false;
            }
            long seq = f.get("seq") instanceof Number x ? x.longValue() : 0;
            long first = f.get("first") instanceof Number x ? x.longValue() : seq;     // a conflated message starts at first
            if (seq <= last[0]) {
                return true;
            }
            if (first != last[0] + 1) {
                return false;                                                        // a gap: the caller resyncs
            }
            last[0] = seq;
            frames.accept(screen.patch(table, (Map<String, Object>) f.get("data")));
            return ++n[0] < max;
        });
    }

    /**
     * Changes the window of a windowed stream this client follows ({@code sub} from its {@code subd} frame; PROTOCOL 16,
     * {@code POST {base}/changes/{sub}/window}): the keys the page now shows and whether it starts at the top of the table's order.
     * {@code rdp.table.unknown} when the stream is gone or is not this caller's.
     */
    public void window(String sub, List<List<Object>> keys, boolean top) throws IOException, InterruptedException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("keys", keys == null ? List.of() : keys);
        body.put("top", top);
        HttpRequest rq = HttpRequest.newBuilder(URI.create(base + "/changes/" + enc(sub) + "/window")).header("Accept", "application/json")
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(Json.write(body))).build();
        HttpResponse<String> r = http.send(rq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (r.statusCode() != 204 && r.statusCode() != 200) {
            throw error(r.statusCode(), r.body());
        }
    }

    /**
     * A write to a live table (PROTOCOL 16, {@code can: write}): {@code change} is {@code {id, op?, key, basedOn, set, from?, reason?}} (the
     * id a ULID the caller made: sending the same change again answers the first answer). Answers {@code {change, state, message,
     * version?}}; a refusal is a {@link ProtocolError} ({@code rdp.write.forbidden} 403, {@code rdp.write.conflict} 409 with {@code current},
     * {@code rdp.write.invalid} 422 with {@code fields}, {@code rdp.write.unsupported} 405).
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> write(String table, Map<String, Object> change) throws IOException, InterruptedException {
        return (Map<String, Object>) send("POST", "/tables/" + enc(table) + "/write", change);
    }

    private Object send(String method, String path, Object body) throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path)).header("Accept", "application/json");
        b = body == null ? b.GET() : b.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(Json.write(body)));
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (r.statusCode() != 200) {
            throw error(r.statusCode(), r.body());
        }
        return Json.read(r.body());
    }

    private Map<String, Object> sendRows(String path, Object body, Form form) throws IOException, InterruptedException {
        HttpRequest rq = HttpRequest.newBuilder(URI.create(base + path)).header("Accept", form.media).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.write(body))).build();
        HttpResponse<java.io.InputStream> r = http.send(rq, HttpResponse.BodyHandlers.ofInputStream());
        try (java.io.InputStream in = r.body()) {
            if (r.statusCode() != 200) {
                throw error(r.statusCode(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
            return form == Form.ARROW ? ArrowRows.read(in) : NdjsonRows.read(in);
        }
    }

    @SuppressWarnings("unchecked")
    private static ProtocolError error(int status, String body) {
        try {
            Map<String, Object> e = (Map<String, Object>) ((Map<String, Object>) Json.read(body)).get("error");
            Map<String, Object> fields = new LinkedHashMap<>(e);
            fields.remove("code");
            fields.remove("message");
            return new ProtocolError(String.valueOf(e.get("code")), status, String.valueOf(e.get("message")), fields);
        } catch (RuntimeException x) {
            return ProtocolError.of("rdp.internal", status, "the host answered " + status);
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
