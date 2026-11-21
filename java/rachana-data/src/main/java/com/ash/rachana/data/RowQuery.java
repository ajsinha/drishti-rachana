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
import java.util.function.Predicate;

/**
 * A query document (PROTOCOL 6, 7) over rows held in memory: the checks every host runs before a provider sees a document
 * ({@link #validate}), and the answer over a list of rows in their JSON form ({@link #page}): filtered by the filter tree evaluated as the
 * Rachana-EL condition it spells, sorted by the rules of 6.1 (the key last), paged by cursor or offset, or grouped (6.3). One
 * implementation for every provider that holds its rows ({@link MemoryHost}, a live table read whole). Thread-safe.
 */
public final class RowQuery {

    /** Default limits (section 13). */
    public static final int PAGE_ROWS = 10_000;
    public static final int FILTER_DEPTH = 32;

    private static final Set<String> FIELDS = Set.of("columns", "filter", "sort", "limit", "cursor", "offset", "group", "asOf", "params");

    private final ElCompiler el = new ElCompiler(1000);

    /**
     * The checks of a query document against a table, in the order the protocol's errors are given: unknown fields, unknown columns,
     * the filter tree, sort columns, then what the table can do ({@code sort}, {@code filter}, {@code group}, {@code offset}).
     */
    public static void validate(TableSchema s, Map<String, Object> doc) {
        for (String k : doc.keySet()) {
            if (!FIELDS.contains(k)) {
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
        for (String[] op : new String[][] {{"sort", "sort", " cannot sort"}, {"filter", "filter", " cannot filter"},
                {"group", "group", " cannot group"}, {"offset", "offset", " cannot page by offset"}}) {
            if (doc.get(op[0]) != null && !s.can(op[1])) {
                throw ProtocolError.of("rdp.query.unsupported", 422, s.name() + op[2], "table", s.name(), "operation", op[1]);
            }
        }
    }

    /** A filter tree as a row predicate: checked, then evaluated as the Rachana-EL condition it spells (PROTOCOL 7). */
    public Predicate<Map<String, Object>> matcher(TableSchema s, Object filter) {
        FilterTree.check(filter, s, FILTER_DEPTH);
        Expr cond = el.compile(FilterTree.toEl(filter));
        EvalContext ctx = EvalContext.of(Node.of(Map.of()), null);
        return row -> Values.truthy(cond.eval(ctx.withRow(Node.of(s.decode(row)), 0)));
    }

    /**
     * The answer to a checked query document over {@code rows} (JSON form, any order): {@code {table, version, columns, values, total?,
     * next?}} or the grouped answer. {@code epoch} names the cursor's stream (a cursor of another epoch is {@code rdp.cursor.invalid}).
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> page(TableSchema s, List<Map<String, Object>> rows, Map<String, Object> doc, String epoch,
            Map<String, Object> version) {
        List<String> columns = doc.get("columns") instanceof List<?> l ? l.stream().map(String::valueOf).toList() : s.names();
        List<Map<String, Object>> all = new ArrayList<>(rows);
        if (doc.get("filter") != null) {
            all = all.stream().filter(matcher(s, doc.get("filter"))).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("table", s.name());
        out.put("version", version);
        if (doc.get("group") instanceof Map<?, ?> g) {
            return group(s, (Map<String, Object>) g, all, doc, out);
        }
        List<Object> sort = doc.get("sort") instanceof List<?> l ? new ArrayList<>(l) : new ArrayList<>();
        all.sort(order(s, sort));
        String key = cursorKey(s.name(), epoch, doc);
        int offset = 0;
        if (doc.get("cursor") != null) {
            offset = cursorOffset(String.valueOf(doc.get("cursor")), key);
        } else if (doc.get("offset") instanceof Number n) {
            offset = (int) Math.max(0, n.longValue());
        }
        int limit = limit(doc);
        int end = Math.min(all.size(), offset + limit);
        List<Map<String, Object>> page = offset >= all.size() ? List.of() : all.subList(offset, end);
        out.put("columns", columns);
        List<Object> values = new ArrayList<>();
        for (Map<String, Object> r : page) {
            List<Object> v = new ArrayList<>();
            columns.forEach(c -> v.add(r.get(c)));
            values.add(v);
        }
        out.put("values", values);
        if (s.can("count")) {
            out.put("total", (long) all.size());
        }
        if (end < all.size()) {
            out.put("next", cursor(key, end));
        }
        return out;
    }

    /** The page size a document asks for, within 1 and {@link #PAGE_ROWS}. */
    public static int limit(Map<String, Object> doc) {
        return doc.get("limit") instanceof Number n ? (int) Math.max(1, Math.min(n.longValue(), PAGE_ROWS)) : PAGE_ROWS;
    }

    /** The cursor of a query (the document less its cursor, limit and offset) on a table's stream epoch, at an offset. */
    public static String cursorOf(String name, String epoch, Map<String, Object> doc, int offset) {
        return cursor(cursorKey(name, epoch, doc), offset);
    }

    /** The offset a cursor of this query stands for; {@code rdp.cursor.invalid} when it is not one of its cursors. */
    public static int offsetOf(String name, String epoch, Map<String, Object> doc, String cursor) {
        return cursorOffset(cursor, cursorKey(name, epoch, doc));
    }

    public static TableSchema.Column column(TableSchema s, String c) {
        TableSchema.Column col = s.column(c);
        if (col == null) {
            FilterTree.Refused r = FilterTree.unknownColumn(s, c);
            throw new ProtocolError(r.code(), 422, r.getMessage(), r.fields());
        }
        return col;
    }

    public static Comparator<Map<String, Object>> order(TableSchema s, List<Object> sort) {
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
    public static int compare(Object x, Object y) {
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

    private static String cursorKey(String name, String epoch, Map<String, Object> doc) {
        Map<String, Object> k = new LinkedHashMap<>(doc);
        k.remove("cursor");
        k.remove("limit");
        k.remove("offset");
        return Integer.toHexString((name + "|" + epoch + "|" + Json.write(k)).hashCode());
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

    private static Map<String, Object> group(TableSchema s, Map<String, Object> g, List<Map<String, Object>> rows, Map<String, Object> doc,
            Map<String, Object> out) {
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
}
