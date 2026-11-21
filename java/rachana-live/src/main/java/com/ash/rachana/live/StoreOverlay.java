/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.live;

import com.ash.rachana.data.ProtocolError;
import com.ash.rachana.data.RowQuery;
import com.ash.rachana.live.spi.SnapshotSource;
import com.ash.rachana.parse.Json;
import com.ash.rachana.table.KeyedRows;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A paged live table's pages (LIVE_TABLES 1.3, 1.5, 7): the snapshot stays in the source and the table holds the live side only. For a
 * columns-tier source an <em>index</em> is kept per order, filter and audience: the keys of the rows the reader may see that hold the
 * condition and the query's filter, in order, with the sort column's values, read in one {@code scan}; never the rows (a page
 * {@code fetch}es its rows by key). Each page merges the live side by key: a changed row shadows the source's, a removed key hides it, a
 * new row takes its place in the order; totals are the index's count with the same corrections. At most {@code maxIndexes} are kept
 * (the least recently asked dropped first); a new source version, or an unversioned index older than {@code indexTtl}, is read again.
 * A query-tier source pages itself (no index): its rows are shadowed and hidden by the live side in place. The flush never reads the
 * source: a window's total comes from a kept index. Thread-safe.
 */
final class StoreOverlay {

    /** One order of the source's rows for one audience and filter. */
    static final class Index {
        final String version;
        final long builtAt;
        final String[] keys;
        final List<List<Object>> keyValues;
        final Object[] values;
        final Map<String, Integer> position = new HashMap<>();

        Index(String version, long builtAt, String[] keys, List<List<Object>> keyValues, Object[] values) {
            this.version = version;
            this.builtAt = builtAt;
            this.keys = keys;
            this.keyValues = keyValues;
            this.values = values;
            for (int i = 0; i < keys.length; i++) {
                position.put(keys[i], i);
            }
        }

        int size() {
            return keys.length;
        }

        Object value(int pos) {
            return values == null ? null : values[pos];
        }
    }

    /** One row of the live side as a page merges it: its key, its sort value as the reader sees it, and its row. */
    record Live(String key, Object value, Map<String, Object> row) {}

    private final LiveTable table;
    private final Map<String, Index> indexes = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<String, Long> lastTotals = new HashMap<>();

    StoreOverlay(LiveTable table) {
        this.table = table;
    }

    synchronized void drop() {
        indexes.clear();
    }

    void changed() {
        // the live side moved: pages merge it at read time; the indexes stay (they hold the source's rows)
    }

    synchronized int indexes() {
        return indexes.size();
    }

    /** The order of two entries: the sort value (absent last, also when descending), then the key text. */
    static int compare(Object va, String ka, Object vb, String kb, boolean sorted, boolean desc) {
        if (sorted) {
            boolean an = va == null || isMasked(va), bn = vb == null || isMasked(vb);
            if (an || bn) {
                if (!(an && bn)) {
                    return an ? 1 : -1;
                }
            } else {
                int c = RowQuery.compare(va, vb);
                if (c != 0) {
                    return desc ? -c : c;
                }
            }
        }
        return ka.compareTo(kb);
    }

    private static boolean isMasked(Object v) {
        return v instanceof Map<?, ?> m && Boolean.TRUE.equals(m.get("$masked"));
    }

    /** A filter tree that is both (either may be null). */
    static Object and(Object a, Object b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return Map.of("op", "&&", "args", List.of(a, b));
    }

    // ---- pages -----------------------------------------------------------------------------------------------------

    /**
     * One page for a checked query document: {@code {table, version, columns, values, total, next?}}.
     *
     * @param filter the query's filter as a predicate on the row as the reader sees it, or null
     */
    @SuppressWarnings("unchecked")
    Map<String, Object> page(Map<String, Object> doc, List<String> columns, LiveTables.Reader reader, Predicate<Map<String, Object>> filter) {
        if (table.source.tier() == SnapshotSource.Tier.QUERY) {
            return queryPage(doc, columns, reader);
        }
        String sortCol = null;
        boolean desc = false;
        if (doc.get("sort") instanceof List<?> sl && !sl.isEmpty() && sl.get(0) instanceof Map<?, ?> s0) {
            sortCol = String.valueOf(s0.get("col"));
            desc = "desc".equals(s0.get("dir"));
        }
        Index idx = index(sortCol, desc, doc.get("filter"), reader, filter);
        boolean sorted = idx.values != null;
        Object[] side = table.liveSide();
        Map<String, Map<String, Object>> liveRows = (Map<String, Map<String, Object>>) side[0];
        Set<String> removed = (Set<String>) side[1];
        List<Live> l = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> e : liveRows.entrySet()) {
            Map<String, Object> raw = e.getValue();
            if (!reader.scope().test(raw)) {
                continue;
            }
            Map<String, Object> seen = reader.shown().apply(raw);
            if (filter != null && !filter.test(seen)) {
                continue;
            }
            l.add(new Live(e.getKey(), sortCol == null ? null : seen.get(sortCol), raw));
        }
        final boolean d = desc;
        l.sort((a, b) -> compare(a.value(), a.key(), b.value(), b.key(), sorted, d));
        int[] shadow = shadowed(idx, liveRows.keySet(), removed);
        int n = idx.size();
        int total = n - shadow.length + l.size();
        int offset = 0;
        if (doc.get("cursor") != null) {
            offset = RowQuery.offsetOf(table.spec.name(), table.epoch(), doc, String.valueOf(doc.get("cursor")));
        } else if (doc.get("offset") instanceof Number o) {
            offset = (int) Math.max(0, o.longValue());
        }
        int limit = RowQuery.limit(doc);
        int p = 0;
        int j = 0;
        long m = 0;
        List<Object> picked = new ArrayList<>();                     // an Integer: a source position; a Live: a live row
        while (picked.size() < limit) {
            while (p < n && Arrays.binarySearch(shadow, p) >= 0) {
                p++;
            }
            boolean storeLeft = p < n;
            boolean liveLeft = j < l.size();
            if (!storeLeft && !liveLeft) {
                break;
            }
            boolean fromStore = storeLeft && (!liveLeft || compare(idx.value(p), idx.keys[p], l.get(j).value(), l.get(j).key(), sorted, desc) < 0);
            Object next = fromStore ? (Object) p++ : l.get(j++);
            if (m++ >= offset) {
                picked.add(next);
            }
        }
        List<List<Object>> want = new ArrayList<>();
        for (Object o : picked) {
            if (o instanceof Integer pos) {
                want.add(idx.keyValues.get(pos));
            }
        }
        Map<String, Map<String, Object>> fetched = new HashMap<>();
        if (!want.isEmpty()) {
            try {
                for (Map<String, Object> r : table.source.fetch(want)) {
                    fetched.put(table.keyOf(r), r);
                }
            } catch (Exception e) {
                throw ProtocolError.of("rdp.unavailable", 503, "a page of " + table.spec.name() + " could not be read from its source", "table",
                        table.spec.name(), "retry", true);
            }
        }
        List<Object> values = new ArrayList<>();
        for (Object o : picked) {
            Map<String, Object> raw = o instanceof Integer pos ? fetched.get(idx.keys[pos]) : ((Live) o).row();
            if (raw == null) {
                continue;                                             // gone from the source since the index was read: left out
            }
            Map<String, Object> seen = reader.shown().apply(raw);
            List<Object> v = new ArrayList<>();
            columns.forEach(c -> v.add(seen.get(c)));
            values.add(v);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("table", table.spec.name());
        out.put("version", table.version());
        out.put("columns", columns);
        out.put("values", values);
        out.put("total", (long) total);
        if (offset + picked.size() < total) {
            out.put("next", RowQuery.cursorOf(table.spec.name(), table.epoch(), doc, offset + picked.size()));
        }
        synchronized (this) {
            lastTotals.put(totalKey(doc.get("filter"), reader), (long) total);
        }
        return out;
    }

    private static String totalKey(Object filter, LiveTables.Reader reader) {
        return (filter == null ? "" : Json.write(filter)) + "\u001f" + reader.audience();
    }

    /**
     * The reader's count for a subscription's filter, from a kept index (never a read of the source): the index's rows less those the
     * live side shadows or removed, plus the live side's rows the reader sees.
     */
    @SuppressWarnings("unchecked")
    long count(Object filterTree, LiveTables.Reader reader, Predicate<Map<String, Object>> filter) {
        Index idx;
        synchronized (this) {
            idx = null;
            String tail = "\u001f" + (filterTree == null ? "" : Json.write(filterTree)) + "\u001f" + reader.audience();
            for (Map.Entry<String, Index> e : indexes.entrySet()) {
                if (e.getKey().endsWith(tail)) {
                    idx = e.getValue();
                    break;
                }
            }
            if (idx == null) {
                return lastTotals.getOrDefault(totalKey(filterTree, reader), 0L);
            }
        }
        Object[] side = table.liveSide();
        Map<String, Map<String, Object>> live = (Map<String, Map<String, Object>>) side[0];
        Set<String> removed = (Set<String>) side[1];
        long n = idx.size();
        for (String k : live.keySet()) {
            if (idx.position.containsKey(k)) {
                n--;
            }
        }
        for (String k : removed) {
            if (!live.containsKey(k) && idx.position.containsKey(k)) {
                n--;
            }
        }
        for (Map<String, Object> r : live.values()) {
            if (reader.scope().test(r) && (filter == null || filter.test(reader.shown().apply(r)))) {
                n++;
            }
        }
        return n;
    }

    /** Makes sure an unsorted index of this filter and audience is kept (a subscription's totals read it). */
    void prepare(Object filterTree, LiveTables.Reader reader, Predicate<Map<String, Object>> filter) {
        if (table.source.tier() == SnapshotSource.Tier.COLUMNS) {
            index(null, false, filterTree, reader, filter);
        }
    }

    private static int[] shadowed(Index idx, Set<String> changed, Set<String> removed) {
        int[] out = new int[changed.size() + removed.size()];
        int k = 0;
        for (Set<String> s : List.of(changed, removed)) {
            for (String key : s) {
                Integer pos = idx.position.get(key);
                if (pos != null) {
                    out[k++] = pos;
                }
            }
        }
        out = Arrays.stream(Arrays.copyOf(out, k)).distinct().sorted().toArray();
        return out;
    }

    /** The index of an order, filter and audience: kept, or read now from the source. */
    private Index index(String sortCol, boolean desc, Object filterTree, LiveTables.Reader reader, Predicate<Map<String, Object>> filter) {
        String version = table.source.version();
        String key = String.join("\u001f", version == null ? "" : version, sortCol == null ? "" : sortCol + (desc ? "-" : "+"),
                filterTree == null ? "" : Json.write(filterTree), reader.audience());
        synchronized (this) {
            Index got = indexes.get(key);
            long now = table.clock.millis();
            if (got != null && (version != null || got.builtAt + table.options.indexTtlMs() > now)) {
                return got;
            }
            indexes.keySet().removeIf(k -> !k.startsWith((version == null ? "" : version) + "\u001f"));
            Index built = build(version, sortCol, desc, reader, filter);
            indexes.put(key, built);
            while (indexes.size() > table.options.maxIndexes()) {
                indexes.remove(indexes.keySet().iterator().next());
            }
            return built;
        }
    }

    private Index build(String version, String sortCol, boolean desc, LiveTables.Reader reader, Predicate<Map<String, Object>> filter) {
        List<String> cols = new ArrayList<>(table.spec.key());
        for (String c : table.spec.listColumns()) {
            if (!cols.contains(c)) {
                cols.add(c);
            }
        }
        boolean ordered = sortCol != null && table.source.column(sortCol);
        List<String> keys = new ArrayList<>();
        List<List<Object>> keyValues = new ArrayList<>();
        List<Object> values = sortCol == null ? null : new ArrayList<>();
        KeyedRows kr = new KeyedRows(table.spec.key(), List.of(), 0);
        try {
            table.source.scan(cols, and(table.spec.whereTree(), reader.rowFilter()), v -> {
                Map<String, Object> raw = new HashMap<>();
                for (int i = 0; i < cols.size() && i < v.length; i++) {
                    raw.put(cols.get(i), v[i]);
                }
                if (!table.where.test(raw) || !reader.scope().test(raw)) {
                    return;
                }
                Map<String, Object> seen = reader.shown().apply(raw);
                if (filter != null && !filter.test(seen)) {
                    return;
                }
                keys.add(kr.keyOf(raw));
                List<Object> kv = new ArrayList<>();
                table.spec.key().forEach(c -> kv.add(raw.get(c)));
                keyValues.add(kv);
                if (values != null) {
                    values.add(ordered ? seen.get(sortCol) : null);       // not a column of the source: after the live rows that carry it
                }
            });
        } catch (Exception e) {
            throw ProtocolError.of("rdp.unavailable", 503, table.spec.name() + "'s rows could not be read from its source", "table",
                    table.spec.name(), "retry", true);
        }
        int n = keys.size();
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        boolean sorted = values != null;
        Arrays.sort(order, (a, b) -> compare(sorted ? values.get(a) : null, keys.get(a), sorted ? values.get(b) : null, keys.get(b), sorted, desc));
        String[] k = new String[n];
        List<List<Object>> kv = new ArrayList<>(n);
        Object[] v = sorted ? new Object[n] : null;
        for (int i = 0; i < n; i++) {
            k[i] = keys.get(order[i]);
            kv.add(keyValues.get(order[i]));
            if (v != null) {
                v[i] = values.get(order[i]);
            }
        }
        return new Index(version, table.clock.millis(), k, kv, v);
    }

    // ---- the query tier ------------------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private Map<String, Object> queryPage(Map<String, Object> doc, List<String> columns, LiveTables.Reader reader) {
        Map<String, Object> q = new LinkedHashMap<>(doc);
        Object f = and(and(table.spec.whereTree(), reader.rowFilter()), doc.get("filter"));
        if (f != null) {
            q.put("filter", f);
        }
        List<String> asked = new ArrayList<>(columns);
        table.spec.key().forEach(c -> {
            if (!asked.contains(c)) {
                asked.add(c);
            }
        });
        q.put("columns", asked);
        Map<String, Object> got;
        try {
            got = table.source.page(q);
        } catch (Exception e) {
            throw ProtocolError.of("rdp.unavailable", 503, "a page of " + table.spec.name() + " could not be read from its source", "table",
                    table.spec.name(), "retry", true);
        }
        Object[] side = table.liveSide();
        Map<String, Map<String, Object>> live = (Map<String, Map<String, Object>>) side[0];
        Set<String> removed = (Set<String>) side[1];
        List<?> gotCols = (List<?>) got.getOrDefault("columns", asked);
        List<Object> values = new ArrayList<>();
        for (Object o : (List<?>) got.getOrDefault("values", List.of())) {
            List<?> cells = (List<?>) o;
            Map<String, Object> raw = new LinkedHashMap<>();
            for (int i = 0; i < gotCols.size() && i < cells.size(); i++) {
                raw.put(String.valueOf(gotCols.get(i)), cells.get(i));
            }
            String k = table.keyOf(raw);
            if (removed.contains(k) && !live.containsKey(k)) {
                continue;
            }
            if (live.containsKey(k)) {
                raw = live.get(k);
            }
            if (!reader.scope().test(raw)) {
                continue;
            }
            Map<String, Object> seen = reader.shown().apply(raw);
            List<Object> v = new ArrayList<>();
            columns.forEach(c -> v.add(seen.get(c)));
            values.add(v);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("table", table.spec.name());
        out.put("version", table.version());
        out.put("columns", columns);
        out.put("values", values);
        if (got.get("total") instanceof Number t) {
            out.put("total", t.longValue());
            synchronized (this) {
                lastTotals.put(totalKey(doc.get("filter"), reader), t.longValue());
            }
        }
        if (got.get("next") != null) {
            out.put("next", got.get("next"));
        }
        return out;
    }
}
