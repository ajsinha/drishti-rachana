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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import com.ash.rachana.model.Ordered;

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

    /** An order: the query's sort entries (column, descending, nulls first), then the key. */
    record Order(List<String> cols, boolean[] desc, boolean[] nullsFirst) {

        static final Order NONE = new Order(List.of(), new boolean[0], new boolean[0]);

        /** The sort list of a query document ({@code [{col, dir?, nulls?}]}). */
        static Order of(Object sort) {
            if (!(sort instanceof List<?> l) || l.isEmpty()) {
                return NONE;
            }
            List<String> cols = new ArrayList<>();
            boolean[] desc = new boolean[l.size()];
            boolean[] first = new boolean[l.size()];
            for (Object o : l) {
                if (o instanceof Map<?, ?> m && m.get("col") != null) {
                    desc[cols.size()] = "desc".equals(m.get("dir"));
                    first[cols.size()] = "first".equals(m.get("nulls"));
                    cols.add(String.valueOf(m.get("col")));
                }
            }
            return new Order(List.copyOf(cols), Arrays.copyOf(desc, cols.size()), Arrays.copyOf(first, cols.size()));
        }

        int width() {
            return cols.size();
        }

        /** The text an index is kept under. */
        String text() {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < cols.size(); i++) {
                b.append(cols.get(i)).append(desc[i] ? '-' : '+').append(nullsFirst[i] ? 'f' : 'l').append(',');
            }
            return b.toString();
        }

        /** Two entries in this order: each sort value (absent or masked last, unless nulls first), then the key values, then the key text. */
        int compare(Object[] va, int ia, List<?> kva, String ka, Object[] vb, int ib, List<?> kvb, String kb) {
            for (int i = 0; i < cols.size(); i++) {
                Object x = va == null ? null : va[ia + i];
                Object y = vb == null ? null : vb[ib + i];
                boolean xn = x == null || isMasked(x), yn = y == null || isMasked(y);
                if (xn || yn) {
                    if (!(xn && yn)) {
                        return xn == nullsFirst[i] ? -1 : 1;
                    }
                    continue;
                }
                int c = RowQuery.compare(x, y);
                if (c != 0) {
                    return desc[i] ? -c : c;
                }
            }
            for (int i = 0; i < kva.size() && i < kvb.size(); i++) {
                Object x = kva.get(i), y = kvb.get(i);
                if (x == null || y == null) {
                    if (x != y) {
                        return x == null ? 1 : -1;
                    }
                    continue;
                }
                int c = RowQuery.compare(x, y);
                if (c != 0) {
                    return c;
                }
            }
            return ka.compareTo(kb);
        }
    }

    /**
     * One order of the source's rows for one audience and filter: key texts, key values and the sort values ({@code width} per row,
     * flat), in order.
     */
    static final class Index {
        final String version;
        final long builtAt;
        final String[] keys;
        final List<List<Object>> keyValues;
        final Object[] values;
        final int width;
        final Map<String, Integer> position = new LinkedHashMap<>();

        Index(String version, long builtAt, String[] keys, List<List<Object>> keyValues, Object[] values, int width) {
            this.version = version;
            this.builtAt = builtAt;
            this.keys = keys;
            this.keyValues = keyValues;
            this.values = values;
            this.width = width;
            for (int i = 0; i < keys.length; i++) {
                position.put(keys[i], i);
            }
        }

        int size() {
            return keys.length;
        }

        /** The order of the entry at {@code pos} against another entry. */
        int compare(Order o, int pos, Object[] v, int iv, List<?> kv, String k) {
            return o.compare(values, pos * width, keyValues.get(pos), keys[pos], v, iv, kv, k);
        }

        /** The first position whose entry is after ({@code strictly}) or not before the given one, by binary search. */
        int seek(Order o, Object[] v, List<?> kv, String k, boolean strictly) {
            int lo = 0, hi = keys.length;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                int c = compare(o, mid, v, 0, kv, k);
                if (c < 0 || (strictly && c == 0)) {
                    lo = mid + 1;
                } else {
                    hi = mid;
                }
            }
            return lo;
        }
    }

    /** One row of the live side as a page merges it: its key, its key values, its sort values as the reader sees them, and its row. */
    record Live(String key, List<Object> keyValues, Object[] values, Map<String, Object> row) {}

    private final LiveTable table;
    private final Map<String, Index> indexes = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<String, Long> lastTotals = new LinkedHashMap<>();

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
        return Ordered.map("op", "&&", "args", List.of(a, b));
    }

    // ---- pages -----------------------------------------------------------------------------------------------------

    /**
     * One page for a checked query document: {@code {table, version, columns, values, total, next?}}, in the order of its whole sort list
     * (then the key). The start of a page is found without reading the rows before it: a cursor names the last row sent (keyset paging:
     * a binary search of the index and of the live side, so rows that change meanwhile are neither repeated nor skipped); an
     * {@code offset} is placed by counting the live side's rows and the shadowed positions before it.
     *
     * @param filter the query's filter as a predicate on the row as the reader sees it, or null
     */
    @SuppressWarnings("unchecked")
    Map<String, Object> page(Map<String, Object> doc, List<String> columns, LiveTables.Reader reader, Predicate<Map<String, Object>> filter) {
        if (table.source.tier() == SnapshotSource.Tier.QUERY) {
            return queryPage(doc, columns, reader);
        }
        Order order = Order.of(doc.get("sort"));
        Index idx = index(order, doc.get("filter"), reader, filter);
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
            Object[] v = new Object[order.width()];
            for (int i = 0; i < v.length; i++) {
                v[i] = seen.get(order.cols().get(i));
            }
            List<Object> kv = new ArrayList<>();
            table.spec.key().forEach(c -> kv.add(raw.get(c)));
            l.add(new Live(e.getKey(), kv, v, raw));
        }
        l.sort((a, b) -> order.compare(a.values(), 0, a.keyValues(), a.key(), b.values(), 0, b.keyValues(), b.key()));
        int[] shadow = shadowed(idx, liveRows.keySet(), removed);
        int n = idx.size();
        int total = n - shadow.length + l.size();
        String query = queryOf(doc);
        int p;
        int j;
        if (doc.get("cursor") != null) {                                 // keyset: the first entries after the last one sent
            Map<String, Object> mark = mark(String.valueOf(doc.get("cursor")), query);
            Object[] v = ((List<Object>) mark.get("v")).toArray();
            List<Object> kv = (List<Object>) mark.get("kv");
            String k = String.valueOf(mark.get("k"));
            p = idx.seek(order, v, kv, k, true);
            j = upper(l, order, v, kv, k);
        } else {
            int offset = doc.get("offset") instanceof Number o ? (int) Math.max(0, Math.min(Integer.MAX_VALUE, o.longValue())) : 0;
            int lo = 0, hi = l.size();                                   // the live rows before the offset: a live row's rank in the merged
            while (lo < hi) {                                            // order is its place in the live side plus the source entries
                int mid = (lo + hi) >>> 1;                               // before it that are not shadowed (it grows with the place)
                Live lv = l.get(mid);
                int q = idx.seek(order, lv.values(), lv.keyValues(), lv.key(), false);
                if (mid + q - below(shadow, q) < offset) {
                    lo = mid + 1;
                } else {
                    hi = mid;
                }
            }
            int j0 = lo;
            p = Math.max(0, offset - j0);                                // then the (offset - j0)th source entry not shadowed
            for (int sh : shadow) {
                if (sh <= p) {
                    p++;
                } else {
                    break;
                }
            }
            p = Math.min(p, n);
            j = j0;
        }
        int limit = RowQuery.limit(doc);
        List<Object> picked = new ArrayList<>();                     // an Integer: a source position; a Live: a live row
        while (true) {
            while (p < n && Arrays.binarySearch(shadow, p) >= 0) {
                p++;
            }
            boolean storeLeft = p < n;
            boolean liveLeft = j < l.size();
            if (!storeLeft && !liveLeft || picked.size() >= limit) {
                break;
            }
            boolean fromStore = storeLeft && (!liveLeft || idx.compare(order, p, l.get(j).values(), 0, l.get(j).keyValues(), l.get(j).key()) < 0);
            picked.add(fromStore ? (Object) p++ : l.get(j++));
        }
        boolean more = p < n || j < l.size();
        List<List<Object>> want = new ArrayList<>();
        for (Object o : picked) {
            if (o instanceof Integer pos) {
                want.add(idx.keyValues.get(pos));
            }
        }
        Map<String, Map<String, Object>> fetched = new LinkedHashMap<>();
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
        if (more && !picked.isEmpty()) {
            Object last = picked.get(picked.size() - 1);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("q", query);
            if (last instanceof Integer pos) {
                m.put("v", idx.values == null ? List.of() : Arrays.asList(Arrays.copyOfRange(idx.values, pos * idx.width, pos * idx.width + idx.width)));
                m.put("kv", idx.keyValues.get(pos));
                m.put("k", idx.keys[pos]);
            } else {
                Live lv = (Live) last;
                m.put("v", Arrays.asList(lv.values()));
                m.put("kv", lv.keyValues());
                m.put("k", lv.key());
            }
            out.put("next", Base64.getUrlEncoder().withoutPadding().encodeToString(Json.write(m).getBytes(StandardCharsets.UTF_8)));
        }
        synchronized (this) {
            lastTotals.put(totalKey(doc.get("filter"), reader), (long) total);
        }
        return out;
    }

    /** What a cursor belongs to: the table, its epoch and the query less its cursor, limit and offset (its fields in any order). */
    private String queryOf(Map<String, Object> doc) {
        Map<String, Object> k = new java.util.TreeMap<>(doc);
        k.remove("cursor");
        k.remove("limit");
        k.remove("offset");
        return Integer.toHexString((table.spec.name() + "|" + table.epoch() + "|" + Json.write(k)).hashCode());
    }

    /** The number of shadowed positions before {@code pos}. */
    private static int below(int[] shadow, int pos) {
        int i = Arrays.binarySearch(shadow, pos);
        return i >= 0 ? i : -i - 1;
    }

    /** The first live row after the given entry. */
    private static int upper(List<Live> l, Order o, Object[] v, List<?> kv, String k) {
        int lo = 0, hi = l.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            Live x = l.get(mid);
            if (o.compare(x.values(), 0, x.keyValues(), x.key(), v, 0, kv, k) <= 0) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    /** A page cursor's last entry; {@code rdp.cursor.invalid} when it is not one of this query's (another query, table or epoch). */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> mark(String cursor, String query) {
        try {
            Object m = Json.read(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));
            if (m instanceof Map<?, ?> mm && query.equals(mm.get("q")) && mm.get("v") instanceof List<?> && mm.get("kv") instanceof List<?>
                    && mm.get("k") instanceof String) {
                return (Map<String, Object>) mm;
            }
        } catch (RuntimeException e) {
            // not one of this host's cursors
        }
        throw ProtocolError.of("rdp.cursor.invalid", 410, "the cursor is not one of this query's");
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
            index(Order.NONE, filterTree, reader, filter);
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
    private Index index(Order order, Object filterTree, LiveTables.Reader reader, Predicate<Map<String, Object>> filter) {
        String version = table.source.version();
        String key = String.join("\u001f", version == null ? "" : version, order.text(),
                filterTree == null ? "" : Json.write(filterTree), reader.audience());
        synchronized (this) {
            Index got = indexes.get(key);
            long now = table.clock.millis();
            if (got != null && (version != null || got.builtAt + table.options.indexTtlMs() > now)) {
                return got;
            }
            indexes.keySet().removeIf(k -> !k.startsWith((version == null ? "" : version) + "\u001f"));
            Index built = build(version, order, reader, filter);
            indexes.put(key, built);
            while (indexes.size() > table.options.maxIndexes()) {
                indexes.remove(indexes.keySet().iterator().next());
            }
            return built;
        }
    }

    private Index build(String version, Order order, LiveTables.Reader reader, Predicate<Map<String, Object>> filter) {
        List<String> cols = new ArrayList<>(table.spec.key());
        for (String c : table.spec.listColumns()) {
            if (!cols.contains(c)) {
                cols.add(c);
            }
        }
        int w = order.width();
        boolean[] kept = new boolean[w];                              // a column the source does not give orders as absent (after the live rows)
        for (int i = 0; i < w; i++) {
            kept[i] = table.source.column(order.cols().get(i));
        }
        List<String> keys = new ArrayList<>();
        List<List<Object>> keyValues = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        KeyedRows kr = new KeyedRows(table.spec.key(), List.of(), 0);
        try {
            table.source.scan(cols, and(table.spec.whereTree(), reader.rowFilter()), v -> {
                Map<String, Object> raw = new LinkedHashMap<>();
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
                for (int i = 0; i < w; i++) {
                    values.add(kept[i] ? seen.get(order.cols().get(i)) : null);
                }
            });
        } catch (Exception e) {
            throw ProtocolError.of("rdp.unavailable", 503, table.spec.name() + "'s rows could not be read from its source", "table",
                    table.spec.name(), "retry", true);
        }
        int n = keys.size();
        Object[] flat = values.toArray();
        Integer[] sorted = new Integer[n];
        for (int i = 0; i < n; i++) {
            sorted[i] = i;
        }
        Arrays.sort(sorted, (a, b) -> order.compare(flat, a * w, keyValues.get(a), keys.get(a), flat, b * w, keyValues.get(b), keys.get(b)));
        String[] k = new String[n];
        List<List<Object>> kv = new ArrayList<>(n);
        Object[] v = new Object[n * w];
        for (int i = 0; i < n; i++) {
            int from = sorted[i];
            k[i] = keys.get(from);
            kv.add(keyValues.get(from));
            System.arraycopy(flat, from * w, v, i * w, w);
        }
        return new Index(version, table.clock.millis(), k, kv, w == 0 ? null : v, w);
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
