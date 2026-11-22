/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.data;

import com.ash.rachana.table.FilterTree;
import com.ash.rachana.table.TableSchema;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import com.ash.rachana.model.Ordered;

/**
 * The Data Protocol's routes and bridge (spec/1/data-protocol sections 4 and 12) over any number of {@link TableProvider}s, transport-free:
 * {@link #handle} takes a request ({@code method}, a path below the base, query parameters, a parsed JSON body) and answers a status and a
 * JSON body, so the same code serves HTTP ({@link ReferenceServer}, a host's own server), the in-process bridge ({@link #bridge}) and the
 * tests. A table is answered by the first provider that has it; the listing is every provider's. Query documents are checked here
 * ({@link RowQuery#validate}) before a provider sees them. Thread-safe; providers do their own locking.
 */
public final class ProtocolHost {

    /** One answer: an HTTP status and a JSON body (null for none). */
    public record Answer(int status, Object body) {}

    private final String base;
    private final List<TableProvider> providers = new CopyOnWriteArrayList<>();

    /**
     * @param base the base address the host's documents name ({@code /dash/data/v1}); "" for none
     * @param providers the providers, asked in this order
     */
    public ProtocolHost(String base, TableProvider... providers) {
        this.base = base == null ? "" : base;
        this.providers.addAll(List.of(providers));
    }

    /** Adds a provider, asked after the ones already there. */
    public ProtocolHost add(TableProvider provider) {
        providers.add(provider);
        return this;
    }

    /** Removes a provider (its tables are no longer answered here); true when it was there. */
    public boolean remove(TableProvider provider) {
        return providers.remove(provider);
    }

    public String base() {
        return base;
    }

    /** The provider of a table ({@code rdp.table.unknown} when none has it). */
    public TableProvider provider(String name) {
        for (TableProvider p : providers) {
            if (p.has(name)) {
                return p;
            }
        }
        throw ProtocolError.of("rdp.table.unknown", 404, "no table '" + name + "'", "table", name);
    }

    /** A table's schema as the caller sees it. */
    public TableSchema schemaOf(String name, Object caller) {
        return provider(name).schema(name, caller);
    }

    /** The frames of a table's stream after a position (see {@link MemoryHost#changes}). */
    public List<Map<String, Object>> changes(String name, String after, Object caller) {
        return provider(name).changes(name, after, caller);
    }

    /** Subscribes to a table's changes (see {@link MemoryHost#subscribe}). */
    public Subscription subscribe(String name, TableProvider.Subscribe request, Consumer<Map<String, Object>> sink, Object caller) {
        return provider(name).subscribe(name, request, sink, caller);
    }

    // ---- requests -------------------------------------------------------------------------------------------------

    /** {@link #handle(Object, String, String, Map, Object)} without a caller. */
    public Answer handle(String method, String path, Map<String, String> query, Object body) {
        return handle(null, method, path, query, body);
    }

    /**
     * One request below the base: {@code GET /}, {@code GET /tables}, {@code GET /tables/{name}}, {@code GET /tables/{name}/rows},
     * {@code POST /tables/{name}/query}, {@code POST /tables/{name}/write}, {@code GET /changes} (the frames as a JSON array; an HTTP server streams them).
     *
     * @param caller the opaque object the transport attaches to the request (null for none); handed to the providers
     */
    public Answer handle(Object caller, String method, String path, Map<String, String> query, Object body) {
        try {
            return new Answer(200, route(caller, method, path, query == null ? Map.of() : query, body));
        } catch (ProtocolError e) {
            return new Answer(e.status(), e.body());
        } catch (FilterTree.Refused r) {
            return new Answer("rdp.forbidden".equals(r.code()) ? 403 : 422, Map.of("error", refused(r)));
        } catch (RuntimeException e) {
            return new Answer(500, ProtocolError.of("rdp.internal", 500, "the host failed: " + e.getClass().getSimpleName()).body());
        }
    }

    private static Map<String, Object> refused(FilterTree.Refused r) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("code", r.code());
        e.put("message", r.getMessage());
        e.putAll(r.fields());
        return e;
    }

    private Object route(Object caller, String method, String path, Map<String, String> q, Object body) {
        String p = path.endsWith("/") && path.length() > 1 ? path.substring(0, path.length() - 1) : path;
        if (p.isEmpty() || p.equals("/")) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("protocol", "rdp");
            m.put("v", 1L);
            m.put("tables", base + "/tables");
            m.put("changes", base + "/changes");
            m.put("limits", limits());
            return m;
        }
        if (p.equals("/tables")) {
            List<Object> list = new ArrayList<>();
            for (TableProvider pr : providers) {
                list.addAll(pr.tables(caller));
            }
            return Map.of("tables", list);
        }
        if (p.equals("/changes")) {
            return changes(q.get("table"), q.get("after"), caller);
        }
        if (p.startsWith("/tables/")) {
            String rest = p.substring("/tables/".length());
            if (!rest.contains("/")) {
                return provider(rest).schemaDoc(rest, caller);
            }
            String name = rest.substring(0, rest.indexOf('/'));
            String what = rest.substring(rest.indexOf('/') + 1);
            TableProvider pr = provider(name);
            if (what.equals("rows") && method.equals("GET")) {
                return rows(pr, name, fromParams(q), q, caller);
            }
            if (what.equals("write") && method.equals("POST")) {        // PROTOCOL 16: a write (can: write)
                return pr.write(name, body, caller);
            }
            if (what.equals("query") && method.equals("POST")) {
                if (!(body == null || body instanceof Map)) {
                    throw ProtocolError.of("rdp.query.invalid", 422, "the query document is an object");
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> doc = body == null ? Map.of() : (Map<String, Object>) body;
                return rows(pr, name, doc, Map.of(), caller);
            }
        }
        throw ProtocolError.of("rdp.table.unknown", 404, "no resource " + method + " " + p);
    }

    /** A page: the document checked, the provider's answer, then shaped as the parameters ask ({@code shape=objects}, {@code schema=true}). */
    private Map<String, Object> rows(TableProvider pr, String name, Map<String, Object> doc, Map<String, String> params, Object caller) {
        RowQuery.validate(pr.schema(name, caller), doc);
        Map<String, Object> page = pr.rows(name, doc, caller);
        Map<String, Object> out = page;
        if ("objects".equals(params.get("shape")) && doc.get("group") == null && page.get("columns") instanceof List<?> cols
                && page.get("values") instanceof List<?> vals) {
            out = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : page.entrySet()) {
                if (e.getKey().equals("columns")) {
                    List<Object> rows = new ArrayList<>();
                    for (Object v : vals) {
                        Map<String, Object> o = new LinkedHashMap<>();
                        List<?> cells = (List<?>) v;
                        for (int i = 0; i < cols.size(); i++) {
                            o.put(String.valueOf(cols.get(i)), cells.get(i));
                        }
                        rows.add(o);
                    }
                    out.put("rows", rows);
                } else if (!e.getKey().equals("values")) {
                    out.put(e.getKey(), e.getValue());
                }
            }
        }
        if ("true".equalsIgnoreCase(params.get("schema"))) {
            out = new LinkedHashMap<>(out);
            out.put("schema", pr.schemaDoc(name, caller));
        }
        return out;
    }

    /** The limits of section 13 this host keeps. */
    public static Map<String, Object> limits() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pageRows", (long) RowQuery.PAGE_ROWS);
        m.put("filterDepth", (long) RowQuery.FILTER_DEPTH);
        return m;
    }

    /** The query parameters of {@code rows} as a query document. */
    private static Map<String, Object> fromParams(Map<String, String> q) {
        Map<String, Object> d = new LinkedHashMap<>();
        if (q.get("columns") != null) {
            d.put("columns", List.of(q.get("columns").split(",")));
        }
        if (q.get("sort") != null) {
            List<Object> sort = new ArrayList<>();
            for (String s : q.get("sort").split(",")) {
                if (!s.isBlank()) {
                    sort.add(Ordered.map("col", s.startsWith("-") ? s.substring(1) : s, "dir", s.startsWith("-") ? "desc" : "asc"));
                }
            }
            d.put("sort", sort);
        }
        for (String k : List.of("limit", "offset")) {
            if (q.get(k) != null) {
                try {
                    d.put(k, Long.parseLong(q.get(k)));
                } catch (NumberFormatException e) {
                    throw ProtocolError.of("rdp.query.invalid", 422, k + " is a whole number");
                }
            }
        }
        if (q.get("cursor") != null) {
            d.put("cursor", q.get("cursor"));
        }
        return d;
    }

    // ---- the bridge ------------------------------------------------------------------------------------------------

    /**
     * One view → host bridge message (section 12) answered with the host → view message it produces: {@code welcome} for {@code hello},
     * {@code res} for a request, {@code pong} for {@code ping}. Without a channel back there are no subscriptions: {@code sub} is
     * refused; {@link #bridge(Consumer)} opens a channel that has them.
     */
    public Map<String, Object> bridge(Map<String, Object> m) {
        return answer(m, null, null);
    }

    /** A bridge channel to one view, without a caller: see {@link #bridge(Consumer, Object)}. */
    public Bridge bridge(Consumer<Map<String, Object>> push) {
        return new Bridge(push, null);
    }

    /** A bridge channel to one view: {@link Bridge#handle} answers its messages and {@code push} receives the {@code msg} frames of its subscriptions. */
    public Bridge bridge(Consumer<Map<String, Object>> push, Object caller) {
        return new Bridge(push, caller);
    }

    /** One view's bridge channel (section 12): its subscriptions by request id, closed with it. */
    public final class Bridge implements AutoCloseable {
        private final Consumer<Map<String, Object>> push;
        private final Object caller;
        final Map<String, Subscription> subs = new ConcurrentHashMap<>();

        Bridge(Consumer<Map<String, Object>> push, Object caller) {
            this.push = push;
            this.caller = caller;
        }

        /** Answers one view → host message (exactly one answer per request); {@code sub} and {@code unsub} as well. */
        public Map<String, Object> handle(Map<String, Object> m) {
            return answer(m, this, caller);
        }

        /** The subscription a {@code sub} of this id opened, or null. */
        public Subscription subscription(String id) {
            return subs.get(id);
        }

        @Override
        public void close() {
            subs.values().forEach(Subscription::close);
            subs.clear();
        }

        private Map<String, Object> sub(Map<String, Object> m, Map<String, Object> out) {
            String id = String.valueOf(m.get("id"));
            String name = String.valueOf(m.get("table"));
            TableProvider pr = provider(name);
            String after;
            if (m.get("after") instanceof Map<?, ?> a) {
                after = a.get("epoch") + ":" + a.get("seq");
            } else {
                Map<String, Object> v = pr.version(name, caller);
                after = v.get("epoch") + ":" + v.get("seq");
            }
            long every = m.get("conflate") instanceof Map<?, ?> c && c.get("everyMs") instanceof Number n ? n.longValue() : 0;
            Subscription old = subs.remove(id);
            if (old != null) {
                old.close();
            }
            List<List<Object>> window = null;
            boolean top = false;
            if (m.get("window") instanceof Map<?, ?> w) {
                window = keys(w.get("keys"));
                top = Boolean.TRUE.equals(w.get("top"));
            }
            Subscription sub = pr.subscribe(name, new TableProvider.Subscribe(after, m.get("filter"), every, window, top), f -> {
                Map<String, Object> msg = new LinkedHashMap<>();
                msg.put("rdp", 1L);
                if ("gap".equals(f.get("t"))) {                     // behind by more than its window's capacity: read the page again
                    msg.put("t", "gap");
                    msg.put("sub", id);
                    msg.put("table", name);
                    msg.put("epoch", f.get("epoch"));
                    msg.put("seq", f.get("seq"));
                    msg.put("recoverable", false);
                    subs.remove(id);
                    push.accept(msg);
                    return;
                }
                msg.put("t", "msg");
                msg.put("sub", id);
                msg.put("table", name);
                msg.put("epoch", f.get("epoch"));
                msg.put("seq", f.get("seq"));
                if (f.get("first") != null) {
                    msg.put("first", f.get("first"));
                }
                msg.put("data", f.get("data"));
                push.accept(msg);
            }, caller);
            if (sub.recovered()) {
                subs.put(id, sub);
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("recovered", sub.recovered());
            body.put("version", pr.version(name, caller));
            out.put("body", body);
            return out;
        }
    }

    /**
     * A list of keys as the protocol sends them ({@code [[…], …]}; a bare value is a one-part key): a host that mounts the protocol on its own
     * routes (a windowed stream's {@code POST /changes/{sub}/window}) reads them as this host does.
     */
    public static List<List<Object>> keys(Object o) {
        List<List<Object>> out = new ArrayList<>();
        if (o instanceof List<?> l) {
            for (Object k : l) {
                out.add(k instanceof List<?> kl ? new ArrayList<>(kl) : java.util.Collections.singletonList(k));
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> answer(Map<String, Object> m, Bridge channel, Object caller) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rdp", 1L);
        String t = String.valueOf(m.get("t"));
        switch (t) {
            case "sub" -> {
                out.put("t", "res");
                out.put("re", m.get("id"));
                if (channel == null) {
                    out.put("error", ProtocolError.of("rdp.query.unsupported", 422, "no channel back for changes", "operation", "sub").error());
                    return out;
                }
                try {
                    return channel.sub(m, out);
                } catch (ProtocolError e) {
                    out.put("error", e.error());
                } catch (FilterTree.Refused r) {
                    out.put("error", refused(r));
                }
            }
            case "win" -> {                                     // PROTOCOL 16: a subscription's window changed
                out.put("t", "res");
                out.put("re", m.get("id"));
                Subscription s = channel == null ? null : channel.subs.get(String.valueOf(m.get("sub")));
                if (s == null || !s.windowed()) {
                    out.put("error", ProtocolError.of("rdp.table.unknown", 404, "no windowed subscription '" + m.get("sub") + "' on this channel",
                            "operation", "win").error());
                } else {
                    s.window(keys(m.get("keys")), Boolean.TRUE.equals(m.get("top")));
                    out.put("body", Map.of());
                }
            }
            case "unsub" -> {
                out.put("t", "res");
                out.put("re", m.get("id"));
                Subscription s = channel == null ? null : channel.subs.remove(String.valueOf(m.get("id")));
                if (s != null) {
                    s.close();
                }
                out.put("body", Map.of());
            }
            case "hello" -> {
                out.put("t", "welcome");
                out.put("v", 1L);
                out.put("limits", limits());
            }
            case "ping" -> {
                out.put("t", "pong");
                out.put("ts", m.get("ts"));
            }
            case "write" -> {                                   // PROTOCOL 16: answered by res { change, state, message, version? }
                out.put("t", "res");
                out.put("re", m.get("id"));
                Answer a = handle(caller, "POST", "/tables/" + m.get("table") + "/write", Map.of(), m.get("body"));
                if (a.status() == 200) {
                    out.put("body", a.body());
                } else {
                    out.put("error", ((Map<String, Object>) a.body()).get("error"));
                }
            }
            case "tables", "schema", "rows", "query" -> {
                out.put("t", "res");
                out.put("re", m.get("id"));
                String name = String.valueOf(m.get("table"));
                Answer a = switch (t) {
                    case "tables" -> handle(caller, "GET", "/tables", Map.of(), null);
                    case "schema" -> handle(caller, "GET", "/tables/" + name, Map.of(), null);
                    case "rows" -> handle(caller, "GET", "/tables/" + name + "/rows", params(m.get("body")), null);
                    default -> handle(caller, "POST", "/tables/" + name + "/query", Map.of(), m.get("body"));
                };
                boolean arrow = String.valueOf(m.get("accept")).contains("arrow") && (t.equals("rows") || t.equals("query"));
                if (a.status() == 200 && arrow && schemaOf(name, caller).can("arrow")) {
                    out.put("t", "res.arrow");                       // section 12: the Arrow IPC bytes, base64 in JSON
                    out.put("buffer", Base64.getEncoder().encodeToString(ArrowRows.write((Map<String, Object>) a.body(), schemaOf(name, caller),
                            ArrowRows.BATCH_ROWS)));
                } else if (a.status() == 200 && arrow) {
                    out.put("error", ProtocolError.of("rdp.query.unsupported", 406, name + " is not offered as Arrow", "table", name,
                            "operation", "arrow").error());
                } else if (a.status() == 200) {
                    out.put("body", a.body());
                } else {
                    out.put("error", ((Map<String, Object>) a.body()).get("error"));
                }
            }
            default -> {
                out.put("t", "res");
                out.put("re", m.get("id"));
                out.put("error", ProtocolError.of("rdp.query.unsupported", 422, "no bridge message '" + t + "' here", "operation", t).error());
            }
        }
        return out;
    }

    private static Map<String, String> params(Object body) {
        Map<String, String> q = new LinkedHashMap<>();
        if (body instanceof Map<?, ?> b) {
            b.forEach((k, v) -> q.put(String.valueOf(k), v instanceof List<?> l ? String.join(",", l.stream().map(String::valueOf).toList())
                    : String.valueOf(v)));
        }
        return q;
    }
}
