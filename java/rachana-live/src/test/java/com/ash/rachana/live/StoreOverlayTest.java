/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.live;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ash.rachana.data.ProtocolError;
import com.ash.rachana.data.RowQuery;
import com.ash.rachana.live.LiveTablesTest.Feed;
import com.ash.rachana.live.LiveTablesTest.Memory;
import com.ash.rachana.live.spi.SnapshotSource;
import com.ash.rachana.table.TableSchema;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * A paged table's pages (LIVE_TABLES 1.3, 1.5): ordered by the whole sort list (then the key), paged by cursor (keyset: the last row sent)
 * or by offset, both placed without reading the rows before the page, and equal to the merged order of the source and the live side.
 */
class StoreOverlayTest {

    static Map<String, Object> row(long id, long v, String book, Long qty) {
        return LiveTablesTest.row(id, v, book, qty, null);
    }

    /** The table as a reader sees it, every row in the order the query asks, by the protocol's own sort (RowQuery). */
    static List<List<Object>> expected(Map<Long, Map<String, Object>> rows, List<Object> sort) {
        TableSchema s = TableSchema.of(Map.of("name", "trades", "key", List.of("ID"), "columns", List.of(Map.of("name", "ID", "type", "integer"),
                Map.of("name", "V", "type", "integer"), Map.of("name", "BOOK", "type", "string"), Map.of("name", "QTY", "type", "integer"),
                Map.of("name", "AT", "type", "timestamp"))));
        Comparator<Map<String, Object>> cmp = RowQuery.order(s, sort);
        List<Map<String, Object>> all = new ArrayList<>(rows.values());
        all.removeIf(r -> "DEAD".equals(r.get("BOOK")));
        all.sort(cmp);
        List<List<Object>> out = new ArrayList<>();
        all.forEach(r -> out.add(List.of(r.get("ID"))));
        return out;
    }

    @SuppressWarnings("unchecked")
    static List<List<Object>> ids(Map<String, Object> page) {
        List<List<Object>> out = new ArrayList<>();
        for (Object v : (List<?>) page.get("values")) {
            out.add(List.of(((List<Object>) v).get(0)));
        }
        return out;
    }

    @Test
    void pagesFollowTheWholeSortListByCursorAndByOffsetThroughTheLiveSide() {
        Memory src = new Memory(SnapshotSource.Tier.COLUMNS);
        Map<Long, Map<String, Object>> truth = new LinkedHashMap<>();
        Random rnd = new Random(7);
        for (long i = 0; i < 400; i++) {
            Map<String, Object> r = row(i, 1, "B" + rnd.nextInt(4), rnd.nextInt(5) == 0 ? null : (long) rnd.nextInt(20));
            src.put(r);
            truth.put(i, r);
        }
        Feed feed = new Feed();
        LiveTables lt = LiveTablesTest.engine(src, feed, "", null).openReads();
        List<Object> sort = List.of(Map.of("col", "BOOK", "dir", "desc"), Map.of("col", "QTY", "nulls", "first"));
        lt.rows("trades", Map.of("sort", sort, "limit", 1L), null);   // the index is read before the live side moves
        for (long i = 0; i < 60; i++) {                                // the live side: changes, removals, new rows, rows that leave
            long id = rnd.nextInt(500);
            Map<String, Object> was = truth.get(id);
            long v = was == null ? 1 : (long) was.get("V") + 1;
            if (rnd.nextInt(6) == 0 && was != null) {
                feed.l.delete(List.of(id), null);
                truth.remove(id);
            } else {
                Map<String, Object> r = row(id, v, rnd.nextInt(8) == 0 ? "DEAD" : "B" + rnd.nextInt(4), (long) rnd.nextInt(20));
                feed.l.upsert(r, null);
                truth.put(id, r);
            }
        }
        List<List<Object>> want = expected(truth, sort);
        for (long limit : List.of(1L, 7L, 50L, 1000L)) {
            List<List<Object>> got = new ArrayList<>();
            Map<String, Object> q = new LinkedHashMap<>(Map.of("columns", List.of("ID"), "sort", sort, "limit", limit));
            for (int guard = 0; guard < 1000; guard++) {
                Map<String, Object> page = lt.rows("trades", q, null);
                assertEquals((long) want.size(), page.get("total"));
                got.addAll(ids(page));
                if (page.get("next") == null) {
                    break;
                }
                q.put("cursor", page.get("next"));
            }
            assertEquals(want, got, "by cursor, " + limit + " a page");
            for (long offset = 0; offset < want.size() + 3; offset += 13) {
                Map<String, Object> page = lt.rows("trades", Map.of("columns", List.of("ID"), "sort", sort, "limit", limit, "offset", offset), null);
                assertEquals(want.subList((int) Math.min(offset, want.size()), (int) Math.min(offset + limit, want.size())), ids(page),
                        "offset " + offset + ", " + limit + " a page");
            }
        }
    }

    @Test
    void aCursorContinuesAfterItsLastRowWhenRowsBeforeItChangeNeitherRepeatingNorSkipping() {
        Memory src = new Memory(SnapshotSource.Tier.COLUMNS);
        for (long i = 0; i < 20; i++) {
            src.put(row(i, 1, "A", i));
        }
        Feed feed = new Feed();
        LiveTables lt = LiveTablesTest.engine(src, feed, "", null).openReads();
        List<Object> sort = List.of(Map.of("col", "QTY"));
        Map<String, Object> first = lt.rows("trades", Map.of("columns", List.of("ID"), "sort", sort, "limit", 5L), null);
        assertEquals("[[0],[1],[2],[3],[4]]", com.ash.rachana.parse.Json.write(ids(first)));
        feed.l.delete(List.of(1L), null);                              // before the cursor: an offset would now skip row 5
        feed.l.upsert(row(100, 1, "A", -1L), null);                    // a new row before it: an offset would repeat row 4
        Map<String, Object> next = lt.rows("trades", Map.of("columns", List.of("ID"), "sort", sort, "limit", 5L, "cursor", first.get("next")), null);
        assertEquals("[[5],[6],[7],[8],[9]]", com.ash.rachana.parse.Json.write(ids(next)));
        Map<String, Object> other = new LinkedHashMap<>(Map.of("columns", List.of("ID"), "sort", List.of(Map.of("col", "QTY", "dir", "desc")),
                "limit", 5L, "cursor", first.get("next")));
        ProtocolError e = org.junit.jupiter.api.Assertions.assertThrows(ProtocolError.class, () -> lt.rows("trades", other, null));
        assertEquals("rdp.cursor.invalid", e.code(), "a cursor of another query is refused");
    }

    @Test
    void aPageFarDownReadsOnlyItsOwnRows() {
        Memory src = new Memory(SnapshotSource.Tier.COLUMNS) {
            int fetched;

            @Override
            public List<Map<String, Object>> fetch(java.util.Collection<List<Object>> keys) {
                fetched += keys.size();
                assertTrue(keys.size() <= 10, "a page fetches its own rows only");
                return super.fetch(keys);
            }
        };
        for (long i = 0; i < 50_000; i++) {
            src.put(row(i, 1, "A", i % 1000));
        }
        Feed feed = new Feed();
        LiveTables lt = LiveTablesTest.engine(src, feed, "", null).openReads();
        List<Object> sort = List.of(Map.of("col", "QTY", "dir", "desc"));
        Map<String, Object> page = lt.rows("trades", Map.of("columns", List.of("ID", "QTY"), "sort", sort, "limit", 10L, "offset", 49_990L), null);
        assertEquals(10, ids(page).size());
        assertFalse(page.containsKey("next"), "the last page has no next");
        assertEquals("[[40000],[41000],[42000]]", com.ash.rachana.parse.Json.write(ids(page).subList(0, 3)), "the last rows of the order: QTY 0 by ID");
    }
}
