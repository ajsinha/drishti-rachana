/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.data;

import com.ash.rachana.api.Node;
import com.ash.rachana.el.ElCompiler;
import com.ash.rachana.el.EvalContext;
import com.ash.rachana.el.Expr;
import com.ash.rachana.el.Values;
import com.ash.rachana.parse.Json;
import com.ash.rachana.table.FilterTree;
import com.ash.rachana.table.KeyedRows;
import com.ash.rachana.table.TableSchema;
import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The in-memory Data Protocol host (spec/1/data-protocol): named tables held as {@link KeyedRows} in their JSON form, each with its schema,
 * its change stream ({@code epoch}, {@code seq}, a replay log) and the protocol's answers, transport-free: {@link #handle} takes a
 * request ({@code method}, a path below the base, query parameters, a parsed JSON body) and answers a status and a JSON body, so the same
 * code serves HTTP ({@link ReferenceServer}), the in-process bridge ({@link #bridge}) and the tests. Filters are checked by
 * {@link FilterTree} and evaluated as the Rachana-EL condition they spell, so the semantics of section 7 are Rachana-EL's by construction.
 * Thread-safe: every operation holds the host's lock; a subscriber is called outside it.
 */
public final class MemoryHost {

    /** Default limits (section 13). */
    public static final int PAGE_ROWS = 10_000;
    public static final int FILTER_DEPTH = 32;
    public static final int LOG = 10_000;

    /** One answer: an HTTP status, a JSON body (null for none) and, for the change stream, the frames. */
    public record Answer(int status, Object body) {}

    private final String base;
    private final ElCompiler el = new ElCompiler(1000);
    private final Map<String, Table> tables = new LinkedHashMap<>();
    private final List<Consumer<Map<String, Object>>> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    private static final class Table {
        TableSchema schema;
        KeyedRows rows;
        String epoch = "t1";
        long seq;
        final List<Map<String, Object>> log = new ArrayList<>();
        final List<Subscription> subs = new java.util.concurrent.CopyOnWriteArrayList<>();
    }

    /** @param base the base address the host's documents name ({@code /dash/data/v1}); "" for none */
    public MemoryHost(String base) {
        this.base = base == null ? "" : base;
    }

    // ---- tables ---------------------------------------------------------------------------------------------------

    /** Adds or replaces a table: its schema document and rows in JSON form; its stream starts again at {@code {epoch, 0}}. */
    public synchronized void put(Object schema, List<Map<String, Object>> rows, String epoch) {
        TableSchema s = TableSchema.of(schema);
        Table t = new Table();
        t.schema = s;
        int window = s.doc().get("append") instanceof Map<?, ?> a && a.get("window") instanceof Number w ? w.intValue() : 0;
        t.rows = new KeyedRows(s.key(), rows, window);
        t.epoch = epoch == null ? "t1" : epoch;
        tables.put(s.name(), t);
    }

    /** Removes every table. */
    public synchronized void clear() {
        tables.clear();
    }

    /** Listens to every published change frame (the {@code msg} frames of section 8.2, with {@code epoch}). */
    public void subscribe(Consumer<Map<String, Object>> listener) {
        listeners.add(listener);
    }

    public void unsubscribe(Consumer<Map<String, Object>> listener) {
        listeners.remove(listener);
    }

    /**
     * Applies a patch to a table and publishes it as the next message of its stream.
     *
     * @return the {@code msg} frame
     */
    public Map<String, Object> publish(Map<String, Object> patch) {
        Map<String, Object> frame;
        List<Subscription> subs;
        synchronized (this) {
            String name = String.valueOf(patch.get("table"));
            Table t = table(name);
            if (t.schema.key().isEmpty() && (patch.get("remove") != null || patch.get("update") != null)) {
                throw ProtocolError.of("rdp.table.no-key", 422, name + " has no key: it takes only reset and add", "table", name);
            }
            t.rows.apply(patch);
            t.seq++;
            frame = new LinkedHashMap<>();
            frame.put("t", "msg");
            frame.put("topic", "rdp." + name);
            frame.put("stream", "rdp." + name);
            frame.put("epoch", t.epoch);
            frame.put("seq", t.seq);
            frame.put("kind", "rows");
            frame.put("data", patch);
            t.log.add(frame);
            if (t.log.size() > LOG) {
                t.log.remove(0);
            }
            subs = t.subs;
            for (Subscription s : subs) {
                s.offer(frame, t.rows.rows());
            }
        }
        for (Subscription s : subs) {
            s.drain();
        }
        for (Consumer<Map<String, Object>> l : listeners) {
            l.accept(frame);
        }
        return frame;
    }

    /**
     * The frames of a table's stream after a position ({@code epoch:seq}; null for the start of this epoch): its {@code msg} frames in
     * order, or one {@code gap} frame ({@code recoverable: false}) when the position cannot be resumed (another epoch, or before the log).
     */
    public synchronized List<Map<String, Object>> changes(String name, String after) {
        Table t = table(name);
        if (t.schema.key().isEmpty() && !t.schema.doc().containsKey("append")) {
            throw ProtocolError.of("rdp.table.no-key", 422, name + " has no key: it cannot be live", "table", name);
        }
        String epoch = t.epoch;
        long seq = 0;
        if (after != null && !after.isEmpty()) {
            int colon = after.lastIndexOf(':');
            if (colon < 0) {
                throw ProtocolError.of("rdp.query.invalid", 422, "after is <epoch>:<seq>");
            }
            epoch = after.substring(0, colon);
            try {
                seq = Long.parseLong(after.substring(colon + 1));
            } catch (NumberFormatException e) {
                throw ProtocolError.of("rdp.query.invalid", 422, "after is <epoch>:<seq>");
            }
        }
        long first = t.log.isEmpty() ? t.seq + 1 : (long) t.log.get(0).get("seq");
        boolean replay = t.schema.can("replay");
        if (!epoch.equals(t.epoch) || seq > t.seq || seq + 1 < first || !replay && seq < t.seq) {
            Map<String, Object> gap = new LinkedHashMap<>();
            gap.put("t", "gap");
            gap.put("topic", "rdp." + name);
            gap.put("stream", "rdp." + name);
            gap.put("epoch", t.epoch);
            gap.put("seq", t.seq);
            gap.put("recoverable", false);
            return List.of(gap);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> f : t.log) {
            if ((long) f.get("seq") > seq) {
                out.add(f);
            }
        }
        return out;
    }

    /**
     * Subscribes to a table's changes after a position (PROTOCOL 8.2, 8.3): the frames after {@code after} are replayed, then every new
     * one follows, to {@code sink}. {@code filter} (a filter tree; the table must declare {@code changes.filter}) restates each patch for
     * the matching rows; a filtered subscription resumes only from the table's current position. {@code everyMs} above 0 conflates
     * (PROTOCOL 8.4). When the position cannot be resumed the subscription is not {@link Subscription#recovered()} and nothing is sent:
     * the reader reads the rows again and subscribes from their version.
     *
     * @throws ProtocolError {@code rdp.table.unknown}, {@code rdp.table.no-key}, {@code rdp.query.unsupported} (a filter the table cannot
     *     take), {@code rdp.query.invalid} or {@code rdp.column.unknown} (a bad filter tree)
     */
    public Subscription subscribe(String name, String after, Object filter, long everyMs, Consumer<Map<String, Object>> sink) {
        Subscription sub;
        synchronized (this) {
            Table t = table(name);
            java.util.function.Predicate<Map<String, Object>> match = null;
            if (filter != null) {
                if (!t.schema.can("changes.filter")) {
                    throw ProtocolError.of("rdp.query.unsupported", 422, name + " cannot filter its changes", "table", name, "operation",
                            "changes.filter");
                }
                try {
                    match = matcher(t.schema, filter);
                } catch (FilterTree.Refused r) {
                    throw new ProtocolError(r.code(), "rdp.forbidden".equals(r.code()) ? 403 : 422, r.getMessage(), r.fields());
                }
            }
            List<Map<String, Object>> backlog = changes(name, after);
            boolean gap = !backlog.isEmpty() && "gap".equals(backlog.get(0).get("t"));
            boolean recovered = !gap && (match == null || backlog.isEmpty());
            sub = new Subscription(name, t.schema.key(), match, everyMs, recovered, sink);
            if (!recovered) {
                return sub;
            }
            sub.start(t.rows.rows());
            for (Map<String, Object> f : backlog) {
                sub.offer(f, t.rows.rows());                 // no filter here: a filtered subscription has no backlog
            }
            t.subs.add(sub);
            Subscription s = sub;
            sub.onClose = () -> t.subs.remove(s);
        }
        sub.drain();
        return sub;
    }

    /** A filter tree as a row predicate: checked, then evaluated as the Rachana-EL condition it spells (PROTOCOL 7). */
    private java.util.function.Predicate<Map<String, Object>> matcher(TableSchema s, Object filter) {
        FilterTree.check(filter, s, FILTER_DEPTH);
        Expr cond = el.compile(FilterTree.toEl(filter));
        EvalContext ctx = EvalContext.of(Node.of(Map.of()), null);
        return row -> Values.truthy(cond.eval(ctx.withRow(Node.of(s.decode(row)), 0)));
    }

    /** The schema of a table ({@code rdp.table.unknown} when there is none). */
    public synchronized TableSchema schemaOf(String name) {
        return table(name).schema;
    }

    private Table table(String name) {
        Table t = tables.get(name);
        if (t == null) {
            throw ProtocolError.of("rdp.table.unknown", 404, "no table '" + name + "'", "table", name);
        }
        return t;
    }

    // ---- requests -------------------------------------------------------------------------------------------------

    /**
     * One request below the base: {@code GET /}, {@code GET /tables}, {@code GET /tables/{name}}, {@code GET /tables/{name}/rows},
     * {@code POST /tables/{name}/query}, {@code GET /changes} (the frames as a JSON array; the HTTP server streams them).
     */
    public Answer handle(String method, String path, Map<String, String> query, Object body) {
        try {
            return new Answer(200, route(method, path, query == null ? Map.of() : query, body));
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

    private synchronized Object route(String method, String path, Map<String, String> q, Object body) {
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
            for (Table t : tables.values()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", t.schema.name());
                if (t.schema.doc().get("title") != null) {
                    m.put("title", t.schema.doc().get("title"));
                }
                if (!t.schema.key().isEmpty()) {
                    m.put("key", t.schema.key());
                }
                if (t.schema.can("count")) {
                    m.put("rows", (long) t.rows.rows().size());
                }
                m.put("can", new ArrayList<>(canList(t)));
                list.add(m);
            }
            return Map.of("tables", list);
        }
        if (p.equals("/changes")) {
            return changes(q.get("table"), q.get("after"));
        }
        if (p.startsWith("/tables/")) {
            String rest = p.substring("/tables/".length());
            if (!rest.contains("/")) {
                return schemaDoc(table(rest));
            }
            String name = rest.substring(0, rest.indexOf('/'));
            String what = rest.substring(rest.indexOf('/') + 1);
            Table t = table(name);
            if (what.equals("rows") && method.equals("GET")) {
                return rows(t, fromParams(t, q), q);
            }
            if (what.equals("query") && method.equals("POST")) {
                if (!(body == null || body instanceof Map)) {
                    throw ProtocolError.of("rdp.query.invalid", 422, "the query document is an object");
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> doc = body == null ? Map.of() : (Map<String, Object>) body;
                return rows(t, doc, Map.of());
            }
        }
        throw ProtocolError.of("rdp.table.unknown", 404, "no resource " + method + " " + p);
    }

    private static List<String> canList(Table t) {
        return t.schema.doc().get("can") instanceof List<?> l ? l.stream().map(String::valueOf).toList() : List.of();
    }

    private static Map<String, Object> limits() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pageRows", (long) PAGE_ROWS);
        m.put("filterDepth", (long) FILTER_DEPTH);
        return m;
    }

    private static Map<String, Object> schemaDoc(Table t) {
        Map<String, Object> m = new LinkedHashMap<>(t.schema.doc());
        m.put("version", version(t));
        return m;
    }

    private static Map<String, Object> version(Table t) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("epoch", t.epoch);
        v.put("seq", t.seq);
        return v;
    }

    /** The query parameters of {@code rows} as a query document. */
    private static Map<String, Object> fromParams(Table t, Map<String, String> q) {
        Map<String, Object> d = new LinkedHashMap<>();
        if (q.get("columns") != null) {
            d.put("columns", List.of(q.get("columns").split(",")));
        }
        if (q.get("sort") != null) {
            List<Object> sort = new ArrayList<>();
            for (String s : q.get("sort").split(",")) {
                if (!s.isBlank()) {
                    sort.add(Map.of("col", s.startsWith("-") ? s.substring(1) : s, "dir", s.startsWith("-") ? "desc" : "asc"));
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

    // ---- reading ---------------------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private Map<String, Object> rows(Table t, Map<String, Object> doc, Map<String, String> params) {
        TableSchema s = t.schema;
        for (String k : doc.keySet()) {
            if (!Set.of("columns", "filter", "sort", "limit", "cursor", "offset", "group", "asOf", "params").contains(k)) {
                throw ProtocolError.of("rdp.query.invalid", 422, "unknown field '" + k + "' in the query document");
            }
        }
        List<String> columns = doc.get("columns") instanceof List<?> l ? l.stream().map(String::valueOf).toList() : s.names();
        for (String c : columns) {
            column(s, c);
        }
        if (doc.get("filter") != null) {
            FilterTree.check(doc.get("filter"), s, FILTER_DEPTH);              // a document is checked before what the table can do
        }
        if (doc.get("sort") instanceof List<?> sl) {
            for (Object o : sl) {
                if (o instanceof Map<?, ?> m && m.get("col") instanceof String c) {
                    column(s, c);
                }
            }
        }
        if (doc.get("sort") != null && !s.can("sort")) {
            throw ProtocolError.of("rdp.query.unsupported", 422, s.name() + " cannot sort", "table", s.name(), "operation", "sort");
        }
        if (doc.get("filter") != null && !s.can("filter")) {
            throw ProtocolError.of("rdp.query.unsupported", 422, s.name() + " cannot filter", "table", s.name(), "operation", "filter");
        }
        if (doc.get("group") != null && !s.can("group")) {
            throw ProtocolError.of("rdp.query.unsupported", 422, s.name() + " cannot group", "table", s.name(), "operation", "group");
        }
        if (doc.get("offset") != null && !s.can("offset")) {
            throw ProtocolError.of("rdp.query.unsupported", 422, s.name() + " cannot page by offset", "table", s.name(), "operation", "offset");
        }
        List<Map<String, Object>> all = new ArrayList<>(t.rows.rows());
        if (doc.get("filter") != null) {
            all = all.stream().filter(matcher(s, doc.get("filter"))).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("table", s.name());
        out.put("version", version(t));
        if (doc.get("group") instanceof Map<?, ?> g) {
            return group(t, (Map<String, Object>) g, all, doc, out);
        }
        List<Object> sort = doc.get("sort") instanceof List<?> l ? new ArrayList<>(l) : new ArrayList<>();
        all.sort(order(s, sort));
        String key = cursorKey(t, doc);
        int offset = 0;
        if (doc.get("cursor") != null) {
            offset = cursorOffset(String.valueOf(doc.get("cursor")), key);
        } else if (doc.get("offset") instanceof Number n) {
            offset = (int) Math.max(0, n.longValue());
        }
        int limit = doc.get("limit") instanceof Number n ? (int) Math.max(1, Math.min(n.longValue(), PAGE_ROWS)) : PAGE_ROWS;
        int end = Math.min(all.size(), offset + limit);
        List<Map<String, Object>> page = offset >= all.size() ? List.of() : all.subList(offset, end);
        boolean objects = "objects".equals(params.get("shape"));
        if (objects) {
            List<Object> rows = new ArrayList<>();
            for (Map<String, Object> r : page) {
                Map<String, Object> o = new LinkedHashMap<>();
                columns.forEach(c -> o.put(c, r.get(c)));
                rows.add(o);
            }
            out.put("rows", rows);
        } else {
            out.put("columns", columns);
            List<Object> values = new ArrayList<>();
            for (Map<String, Object> r : page) {
                List<Object> v = new ArrayList<>();
                columns.forEach(c -> v.add(r.get(c)));
                values.add(v);
            }
            out.put("values", values);
        }
        if (s.can("count")) {
            out.put("total", (long) all.size());
        }
        if (end < all.size()) {
            out.put("next", cursor(key, end));
        }
        if ("true".equalsIgnoreCase(params.get("schema"))) {
            out.put("schema", schemaDoc(t));
        }
        return out;
    }

    private static TableSchema.Column column(TableSchema s, String c) {
        TableSchema.Column col = s.column(c);
        if (col == null) {
            FilterTree.Refused r = FilterTree.unknownColumn(s, c);
            throw new ProtocolError(r.code(), 422, r.getMessage(), r.fields());
        }
        return col;
    }

    private Comparator<Map<String, Object>> order(TableSchema s, List<Object> sort) {
        List<Object> keys = new ArrayList<>(sort);
        for (String k : s.key()) {
            keys.add(Map.of("col", k));
        }
        Comparator<Map<String, Object>> cmp = (a, b) -> 0;
        for (Object o : keys) {
            if (!(o instanceof Map<?, ?> m) || !(m.get("col") instanceof String c)) {
                throw ProtocolError.of("rdp.query.invalid", 422, "a sort entry is { col, dir?, nulls? }");
            }
            TableSchema.Column col = column(s, c);
            if (col.masked()) {
                throw ProtocolError.of("rdp.forbidden", 403, "the column '" + c + "' is masked for this reader", "table", s.name(), "column", c);
            }
            boolean desc = "desc".equals(m.get("dir"));
            boolean nullsFirst = "first".equals(m.get("nulls"));
            cmp = cmp.thenComparing((a, b) -> {
                Object x = TableSchema.decode(col.type(), a.get(c)), y = TableSchema.decode(col.type(), b.get(c));
                boolean xn = x == null || Values.masked(x), yn = y == null || Values.masked(y);
                if (xn || yn) {
                    return xn && yn ? 0 : (xn ? (nullsFirst ? -1 : 1) : (nullsFirst ? 1 : -1));
                }
                int r = compare(x, y);
                return desc ? -r : r;
            });
        }
        return cmp;
    }

    /** Numbers by value, text by code point, flags false before true; anything else by its JSON text. */
    static int compare(Object x, Object y) {
        if (x instanceof Number a && y instanceof Number b) {
            return decimal(a).compareTo(decimal(b));
        }
        if (x instanceof String a && y instanceof String b) {
            int i = 0, j = 0;
            while (i < a.length() && j < b.length()) {
                int ca = a.codePointAt(i), cb = b.codePointAt(j);
                if (ca != cb) {
                    return Integer.compare(ca, cb);
                }
                i += Character.charCount(ca);
                j += Character.charCount(cb);
            }
            return Integer.compare(a.length() - i, b.length() - j);
        }
        if (x instanceof Boolean a && y instanceof Boolean b) {
            return Boolean.compare(a, b);
        }
        return compare(Json.write(x), Json.write(y));
    }

    private static BigDecimal decimal(Number n) {
        return n instanceof BigDecimal d ? d : n instanceof Double d ? BigDecimal.valueOf(d) : BigDecimal.valueOf(n.longValue());
    }

    private static String cursorKey(Table t, Map<String, Object> doc) {
        Map<String, Object> k = new LinkedHashMap<>(doc);
        k.remove("cursor");
        k.remove("limit");
        k.remove("offset");
        return Integer.toHexString((t.schema.name() + "|" + t.epoch + "|" + Json.write(k)).hashCode());
    }

    private static String cursor(String key, int offset) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString((key + ":" + offset).getBytes(StandardCharsets.UTF_8));
    }

    private static int cursorOffset(String cursor, String key) {
        try {
            String s = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int colon = s.lastIndexOf(':');
            if (colon > 0 && s.substring(0, colon).equals(key)) {
                return Integer.parseInt(s.substring(colon + 1));
            }
        } catch (IllegalArgumentException e) {
            // not one of this host's cursors
        }
        throw ProtocolError.of("rdp.cursor.invalid", 410, "the cursor is not one of this query's");
    }

    // ---- grouping --------------------------------------------------------------------------------------------------

    private Map<String, Object> group(Table t, Map<String, Object> g, List<Map<String, Object>> rows, Map<String, Object> doc,
            Map<String, Object> out) {
        TableSchema s = t.schema;
        List<String> by = g.get("by") instanceof List<?> l ? l.stream().map(String::valueOf).toList() : List.of();
        List<Map<?, ?>> values = new ArrayList<>();
        if (g.get("values") instanceof List<?> l) {
            for (Object o : l) {
                if (!(o instanceof Map<?, ?> m) || !(m.get("name") instanceof String) || !(m.get("agg") instanceof String a)
                        || !Set.of("sum", "count", "min", "max", "avg", "distinct", "median", "percentile").contains(a)
                        || !"count".equals(a) && !(m.get("col") instanceof String)) {
                    throw ProtocolError.of("rdp.query.invalid", 422, "a group value is { name, agg, col?, p? }");
                }
                values.add(m);
            }
        }
        for (String c : by) {
            if (column(s, c).masked()) {
                throw ProtocolError.of("rdp.forbidden", 403, "the column '" + c + "' is masked for this reader", "table", s.name(), "column", c);
            }
        }
        for (Map<?, ?> v : values) {
            if (v.get("col") instanceof String c && column(s, c).masked()) {
                throw ProtocolError.of("rdp.forbidden", 403, "the column '" + c + "' is masked for this reader", "table", s.name(), "column", c);
            }
        }
        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            List<Object> k = new ArrayList<>();
            by.forEach(c -> k.add(r.get(c)));
            groups.computeIfAbsent(Json.write(k), x -> new ArrayList<>()).add(r);
        }
        List<String> cols = new ArrayList<>(by);
        values.forEach(v -> cols.add((String) v.get("name")));
        List<Map<String, Object>> result = new ArrayList<>();
        for (List<Map<String, Object>> members : groups.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            by.forEach(c -> row.put(c, members.get(0).get(c)));
            values.forEach(v -> row.put((String) v.get("name"), aggregate(s, v, members)));
            result.add(row);
        }
        List<Object> sort = doc.get("sort") instanceof List<?> l ? new ArrayList<>(l) : new ArrayList<>();
        TableSchema grouped = TableSchema.of(Map.of("name", s.name(), "key", by, "columns", groupedColumns(s, by, values)));
        result.sort(order(grouped, sort));
        List<Object> vals = new ArrayList<>();
        for (Map<String, Object> r : result) {
            List<Object> v = new ArrayList<>();
            cols.forEach(c -> v.add(r.get(c)));
            vals.add(v);
        }
        out.put("columns", cols);
        out.put("values", vals);
        if (Boolean.TRUE.equals(g.get("totals"))) {
            Map<String, Object> totals = new LinkedHashMap<>();
            values.forEach(v -> totals.put((String) v.get("name"), aggregate(s, v, rows)));
            out.put("totals", totals);
        }
        return out;
    }

    private static List<Object> groupedColumns(TableSchema s, List<String> by, List<Map<?, ?>> values) {
        List<Object> cols = new ArrayList<>();
        by.forEach(c -> cols.add(Map.of("name", c, "type", s.column(c).type())));
        values.forEach(v -> cols.add(Map.of("name", v.get("name"), "type", "count".equals(v.get("agg")) || "distinct".equals(v.get("agg"))
                ? "integer" : v.get("col") instanceof String c ? s.column(c).type() : "number")));
        return cols;
    }

    /** One aggregate over rows, in the JSON form of the column's type (a decimal sum is exact and sent as text). */
    private static Object aggregate(TableSchema s, Map<?, ?> v, List<Map<String, Object>> rows) {
        String agg = (String) v.get("agg");
        String col = v.get("col") instanceof String c ? c : null;
        if (col == null) {
            return (long) rows.size();
        }
        String type = s.column(col).type();
        List<Object> xs = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Object x = TableSchema.decode(type, r.get(col));
            if (x != null && !Values.masked(x)) {
                xs.add(x);
            }
        }
        switch (agg) {
            case "count":
                return (long) xs.size();
            case "distinct": {
                Set<String> d = new LinkedHashSet<>();
                xs.forEach(x -> d.add(Json.write(x)));
                return (long) d.size();
            }
            case "min":
            case "max": {
                Object best = null;
                for (Object x : xs) {
                    if (best == null || (agg.equals("min") ? compare(x, best) < 0 : compare(x, best) > 0)) {
                        best = x;
                    }
                }
                return wire(type, best);
            }
            default:
                break;
        }
        List<BigDecimal> ns = new ArrayList<>();
        for (Object x : xs) {
            if (x instanceof Number n) {
                ns.add(decimal(n));
            }
        }
        if (agg.equals("sum")) {
            BigDecimal sum = BigDecimal.ZERO;
            for (BigDecimal n : ns) {
                sum = sum.add(n);
            }
            return wire(type, sum);
        }
        if (ns.isEmpty()) {
            return null;
        }
        if (agg.equals("avg")) {
            BigDecimal sum = BigDecimal.ZERO;
            for (BigDecimal n : ns) {
                sum = sum.add(n);
            }
            BigDecimal avg = sum.divide(BigDecimal.valueOf(ns.size()), MathContext.DECIMAL64);
            return "decimal".equals(type) ? avg.toPlainString() : (Object) avg.doubleValue();
        }
        ns.sort(BigDecimal::compareTo);
        double p = agg.equals("median") ? 0.5 : v.get("p") instanceof Number n ? n.doubleValue() : 0.5;
        double pos = p * (ns.size() - 1);
        int lo = (int) Math.floor(pos);
        int hi = Math.min(ns.size() - 1, lo + 1);
        double frac = pos - lo;
        double value = ns.get(lo).doubleValue() + (ns.get(hi).doubleValue() - ns.get(lo).doubleValue()) * frac;
        return value;
    }

    private static Object wire(String type, Object v) {
        if (v == null) {
            return null;
        }
        if ("decimal".equals(type) && v instanceof BigDecimal d) {
            return d.toPlainString();
        }
        if ("integer".equals(type) && v instanceof BigDecimal d) {
            try {
                long l = d.longValueExact();
                return Math.abs(l) <= (1L << 53) ? (Object) l : d.toPlainString();
            } catch (ArithmeticException e) {
                return d.toPlainString();
            }
        }
        if ("number".equals(type) && v instanceof BigDecimal d) {
            return d.doubleValue();
        }
        return v;
    }

    // ---- the bridge ------------------------------------------------------------------------------------------------

    /**
     * One view → host bridge message (section 12) answered with the host → view message it produces: {@code welcome} for {@code hello},
     * {@code res} for a request, {@code pong} for {@code ping}. Without a channel back there are no subscriptions: {@code sub} is
     * refused; {@link #bridge(Consumer)} opens a channel that has them.
     */
    public Map<String, Object> bridge(Map<String, Object> m) {
        return answer(m, null);
    }

    /** A bridge channel to one view: {@link Bridge#handle} answers its messages and {@code push} receives the {@code msg} frames of its subscriptions. */
    public Bridge bridge(Consumer<Map<String, Object>> push) {
        return new Bridge(push);
    }

    /** One view's bridge channel (section 12): its subscriptions by request id, closed with it. */
    public final class Bridge implements AutoCloseable {
        private final Consumer<Map<String, Object>> push;
        private final Map<String, Subscription> subs = new java.util.concurrent.ConcurrentHashMap<>();

        Bridge(Consumer<Map<String, Object>> push) {
            this.push = push;
        }

        /** Answers one view → host message (exactly one answer per request); {@code sub} and {@code unsub} as well. */
        public Map<String, Object> handle(Map<String, Object> m) {
            return answer(m, this);
        }

        @Override
        public void close() {
            subs.values().forEach(Subscription::close);
            subs.clear();
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> sub(Map<String, Object> m, Map<String, Object> out) {
            String id = String.valueOf(m.get("id"));
            String name = String.valueOf(m.get("table"));
            String after;
            synchronized (MemoryHost.this) {
                Table t = table(name);
                after = m.get("after") instanceof Map<?, ?> a ? a.get("epoch") + ":" + a.get("seq") : t.epoch + ":" + t.seq;
            }
            long every = m.get("conflate") instanceof Map<?, ?> c && c.get("everyMs") instanceof Number n ? n.longValue() : 0;
            Subscription old = subs.remove(id);
            if (old != null) {
                old.close();
            }
            Subscription sub = subscribe(name, after, m.get("filter"), every, f -> {
                Map<String, Object> msg = new LinkedHashMap<>();
                msg.put("rdp", 1L);
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
            });
            if (sub.recovered()) {
                subs.put(id, sub);
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("recovered", sub.recovered());
            synchronized (MemoryHost.this) {
                body.put("version", version(table(name)));
            }
            out.put("body", body);
            return out;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> answer(Map<String, Object> m, Bridge channel) {
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
            case "tables", "schema", "rows", "query" -> {
                out.put("t", "res");
                out.put("re", m.get("id"));
                String name = String.valueOf(m.get("table"));
                Answer a = switch (t) {
                    case "tables" -> handle("GET", "/tables", Map.of(), null);
                    case "schema" -> handle("GET", "/tables/" + name, Map.of(), null);
                    case "rows" -> handle("GET", "/tables/" + name + "/rows", params(m.get("body")), null);
                    default -> handle("POST", "/tables/" + name + "/query", Map.of(), m.get("body"));
                };
                boolean arrow = String.valueOf(m.get("accept")).contains("arrow") && (t.equals("rows") || t.equals("query"));
                if (a.status() == 200 && arrow && schemaOf(name).can("arrow")) {
                    out.put("t", "res.arrow");                       // section 12: the Arrow IPC bytes, base64 in JSON
                    out.put("buffer", Base64.getEncoder().encodeToString(ArrowRows.write((Map<String, Object>) a.body(), schemaOf(name),
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
