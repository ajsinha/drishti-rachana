/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.live;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ash.rachana.data.ProtocolError;
import com.ash.rachana.data.ProtocolHost;
import com.ash.rachana.data.Subscription;
import com.ash.rachana.data.TableProvider;
import com.ash.rachana.live.spi.Authoriser;
import com.ash.rachana.live.spi.LiveStream;
import com.ash.rachana.live.spi.ReaderView;
import com.ash.rachana.live.spi.SnapshotSource;
import com.ash.rachana.parse.Json;
import com.ash.rachana.table.LiveTableSpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * The live tables engine, steps L3 to L6 (LIVE_TABLES 1, 3, 5): ports the scenarios of the liveset engine's LiveSetTest, StorePagesTest
 * and LiveIsolationTest (mapping: merge, snapshot race, burst, paged window, newest-first top, retention, merged pages and totals, two
 * audiences).
 */
class LiveTablesTest {

    /** A source in memory: whole or columns tier; rows by key text. */
    static final class Memory implements SnapshotSource {
        final Tier tier;
        final Map<String, Map<String, Object>> rows = new LinkedHashMap<>();
        volatile Runnable duringRead = () -> { };
        String version = "v1";
        int scans;

        Memory(Tier tier) {
            this.tier = tier;
        }

        void put(Map<String, Object> r) {
            rows.put(String.valueOf(r.get("ID")), new LinkedHashMap<>(r));
        }

        @Override
        public Tier tier() {
            return tier;
        }

        @Override
        public String version() {
            return version;
        }

        @Override
        public void read(Object filter, Consumer<Map<String, Object>> sink) {
            duringRead.run();
            List.copyOf(rows.values()).forEach(sink);
        }

        @Override
        public void scan(List<String> columns, Object filter, Consumer<Object[]> sink) {
            scans++;
            for (Map<String, Object> r : rows.values()) {
                Object[] v = new Object[columns.size()];
                for (int i = 0; i < v.length; i++) {
                    v[i] = r.get(columns.get(i));
                }
                sink.accept(v);
            }
        }

        @Override
        public List<Map<String, Object>> fetch(Collection<List<Object>> keys) {
            List<Map<String, Object>> out = new ArrayList<>();
            keys.forEach(k -> {
                Map<String, Object> r = rows.get(String.valueOf(k.get(0)));
                if (r != null) {
                    out.add(r);
                }
            });
            return out;
        }
    }

    /** A stream the test drives. */
    static final class Feed implements LiveStream {
        Listener l;

        @Override
        public AutoCloseable subscribe(Listener listener) {
            l = listener;
            return () -> l = null;
        }
    }

    static Map<String, Object> row(Object id, long v, String book, Object qty, String at) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ID", id);
        r.put("V", v);
        r.put("BOOK", book);
        r.put("QTY", qty);
        r.put("AT", at);
        return r;
    }

    static final String DECL = """
            rachana: 1
            live-table: trades
            snapshot: s
            stream: f
            key: ID
            version: V
            changed-at: AT
            where: "@.BOOK != 'DEAD'"
            columns: [ { name: ID, type: integer }, { name: V, type: integer }, { name: BOOK }, { name: QTY, type: integer }, { name: AT, type: timestamp } ]
            """;

    static LiveTables engine(Memory src, Feed feed, String extra, LiveTables.Options o) {
        LiveTables lt = new LiveTables(new LiveTables.Resolver() {
            public SnapshotSource snapshot(String n) { return src; }
            public LiveStream stream(String n) { return feed; }
        }, (o == null ? LiveTables.Options.DEFAULTS : o).withUpkeepMs(0), Clock.fixed(Instant.parse("2026-11-21T12:00:00Z"), ZoneOffset.UTC));
        lt.declare(LiveTableSpec.read(DECL + extra, "trades.live.yaml"));
        return lt;
    }

    @SuppressWarnings("unchecked")
    static List<List<Object>> values(Map<String, Object> page) {
        return (List<List<Object>>) page.get("values");
    }

    @Test
    void changesMergeByKeyAndVersionWithoutDuplicatesOrGoingBack() {
        Memory src = new Memory(SnapshotSource.Tier.WHOLE);
        src.put(row(1, 1, "A", 10, null));
        Feed feed = new Feed();
        LiveTables lt = engine(src, feed, "", null).openReads();
        assertEquals(1L, lt.rows("trades", Map.of(), null).get("total"));
        feed.l.upsert(row(1, 3, "A", 30, null), null);
        feed.l.upsert(row(1, 2, "A", 20, null), null);              // older: dropped
        feed.l.upsert(row(1, 3, "A", 99, null), null);              // equal: dropped
        feed.l.upsert(row(2, 1, "DEAD", 5, null), null);            // does not hold the condition: never in
        assertEquals("[[1,3,\"A\",30,null]]", Json.write(values(lt.rows("trades", Map.of(), null))));
        feed.l.upsert(row(1, 4, "DEAD", 30, null), null);           // stops holding it: leaves
        feed.l.upsert(row(3, 1, "B", 1, null), null);
        feed.l.delete(List.of(3), null);
        assertEquals(0L, lt.rows("trades", Map.of(), null).get("total"));
        assertEquals(2L, lt.describe().get(0).get("staleDropped"));
    }

    @Test
    void whatArrivesWhileTheSnapshotIsReadIsAppliedAfterItByVersion() {
        Memory src = new Memory(SnapshotSource.Tier.WHOLE);
        src.put(row(1, 1, "A", 10, null));
        Feed feed = new Feed();
        src.duringRead = () -> {
            feed.l.upsert(row(1, 2, "A", 20, null), null);         // subscribed first: arrives while the snapshot is read
            feed.l.upsert(row(2, 1, "B", 5, null), null);
        };
        LiveTables lt = engine(src, feed, "", null).openReads();
        assertEquals("[[1,2,\"A\",20,null],[2,1,\"B\",5,null]]", Json.write(values(lt.rows("trades", Map.of("sort", List.of(Map.of("col", "ID"))), null))));
    }

    @Test
    void aBurstReachesAWindowedReaderCoalescedOrAsOneGap() throws Exception {
        Memory src = new Memory(SnapshotSource.Tier.WHOLE);
        for (int i = 0; i < 100; i++) {
            src.put(row(i, 1, "A", i, null));
        }
        Feed feed = new Feed();
        LiveTables lt = engine(src, feed, "", LiveTables.Options.DEFAULTS.withFlushMs(30).withWindowCapacity(50)).openReads();
        List<Map<String, Object>> got = new CopyOnWriteArrayList<>();
        Subscription s = lt.subscribe("trades", new TableProvider.Subscribe(null, null, 0, List.of(List.of(1), List.of(2)), false), got::add, null);
        for (int v = 2; v < 2_000; v++) {
            feed.l.upsert(row(1, v, "A", v, null), null);
        }
        Thread.sleep(150);
        assertTrue(got.size() < 20, "coalesced: " + got.size());
        Map<?, ?> last = (Map<?, ?>) got.get(got.size() - 1).get("data");
        assertEquals("[{\"ID\":1,\"V\":1999,\"BOOK\":\"A\",\"QTY\":1999,\"AT\":null}]", Json.write(last.get("update")));
        assertEquals(100L, last.get("total"));
        for (int i = 200; i < 300; i++) {                         // a burst of rows the window does not hold: only the total moves
            feed.l.upsert(row(i, 1, "A", i, null), null);
        }
        Thread.sleep(150);
        Map<?, ?> tail = (Map<?, ?>) got.get(got.size() - 1).get("data");
        assertEquals(200L, tail.get("total"));
        assertFalse(tail.containsKey("update"));
        got.clear();
        Subscription wide = lt.subscribe("trades", new TableProvider.Subscribe(null, null, 0, List.of(), true), got::add, null);
        wide.window(List.of(), true);
        for (int i = 0; i < 100; i++) {                           // a window of everything changed: more keys than its capacity
            feed.l.upsert(row(i, 5, "A", i, null), null);
        }
        Map<String, Object> at = lt.version("trades", null);
        Subscription all = lt.subscribe("trades", new TableProvider.Subscribe(at.get("epoch") + ":" + at.get("seq"), null, 0, keysUpTo(100), false),
                got::add, null);
        for (int i = 0; i < 100; i++) {
            feed.l.upsert(row(i, 6, "A", i, null), null);
        }
        Thread.sleep(150);
        assertTrue(got.stream().anyMatch(f -> "gap".equals(f.get("t")) && Boolean.FALSE.equals(f.get("recoverable"))), got.toString());
        assertTrue(all.closed());
        s.close();
        wide.close();
    }

    static List<List<Object>> keysUpTo(int n) {
        List<List<Object>> k = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            k.add(List.of(i));
        }
        return k;
    }

    @Test
    void inANewestFirstOrderANewRowIsInsertedOnTopOfAPageAtTheTopAndOnlyCountedElsewhere() throws Exception {
        Memory src = new Memory(SnapshotSource.Tier.WHOLE);
        src.put(row(1, 1, "A", 1, "2026-11-21T10:00:00Z"));
        src.put(row(2, 1, "A", 2, "2026-11-21T11:00:00Z"));
        Feed feed = new Feed();
        LiveTables lt = engine(src, feed, "sort: { col: AT, dir: desc }\n", LiveTables.Options.DEFAULTS.withFlushMs(10)).openReads();
        assertEquals("[[2],[1]]", Json.write(values(lt.rows("trades", Map.of("columns", List.of("ID")), null))));
        List<Map<String, Object>> top = new CopyOnWriteArrayList<>();
        List<Map<String, Object>> other = new CopyOnWriteArrayList<>();
        lt.subscribe("trades", new TableProvider.Subscribe(null, null, 0, List.of(List.of(2), List.of(1)), true), top::add, null);
        lt.subscribe("trades", new TableProvider.Subscribe(null, null, 0, List.of(List.of(1)), false), other::add, null);
        feed.l.upsert(row(3, 1, "A", 3, "2026-11-21T12:00:00Z"), null);
        Thread.sleep(80);
        Map<?, ?> t = (Map<?, ?>) top.get(top.size() - 1).get("data");
        assertEquals("[[3]]", Json.write(t.get("top")));
        assertEquals(3L, t.get("total"));
        Map<?, ?> o = (Map<?, ?>) other.get(other.size() - 1).get("data");
        assertFalse(o.containsKey("update"));
        assertEquals(3L, o.get("total"));
    }

    @Test
    void retentionDropsLiveRowsPastTheirAgeAndBeyondTheCapOldestFirst() {
        Memory src = new Memory(SnapshotSource.Tier.WHOLE);
        Feed feed = new Feed();
        LiveTables lt = engine(src, feed, "retention: { ttl: 1h, on: AT, max-rows: 2 }\n", null).openReads();
        lt.rows("trades", Map.of(), null);
        feed.l.upsert(row(1, 1, "A", 1, "2026-11-21T09:00:00Z"), null);       // three hours old at 12:00
        feed.l.upsert(row(2, 1, "A", 1, "2026-11-21T11:30:00Z"), null);
        feed.l.upsert(row(3, 1, "A", 1, "2026-11-21T11:40:00Z"), null);
        feed.l.upsert(row(4, 1, "A", 1, "2026-11-21T11:50:00Z"), null);
        lt.upkeep();
        assertEquals("[[3],[4]]", Json.write(values(lt.rows("trades", Map.of("columns", List.of("ID"), "sort", List.of(Map.of("col", "ID"))), null))));
    }

    @Test
    void aGapStartsANewEpochAndAPausedStreamServesItsSnapshot() {
        Memory src = new Memory(SnapshotSource.Tier.WHOLE);
        src.put(row(1, 1, "A", 1, null));
        Feed feed = new Feed();
        LiveTables lt = engine(src, feed, "", null).openReads();
        Map<String, Object> v1 = lt.version("trades", null);
        List<Map<String, Object>> got = new ArrayList<>();
        lt.subscribe("trades", TableProvider.Subscribe.after(null), got::add, null);
        feed.l.state(false, "the broker is absent");
        assertEquals("{\"state\":\"paused\",\"reason\":\"the broker is absent\"}", Json.write(lt.schemaDoc("trades", null).get("live")));
        src.put(row(2, 1, "A", 1, null));
        feed.l.gap("lost");
        assertNotEquals(v1.get("epoch"), lt.version("trades", null).get("epoch"));
        assertEquals("gap", got.get(got.size() - 1).get("t"));
        assertEquals(2L, lt.rows("trades", Map.of(), null).get("total"));
    }

    @Test
    void pagedPagesAndTotalsEqualTheMergedOrderWithTheLiveSide() {
        Memory src = new Memory(SnapshotSource.Tier.COLUMNS);
        for (int i = 0; i < 50; i++) {
            src.put(row(i, 1, i % 2 == 0 ? "A" : "B", 100 - i, null));
        }
        Feed feed = new Feed();
        LiveTables lt = engine(src, feed, "", null).openReads();
        Map<String, Object> q = Map.of("columns", List.of("ID", "QTY"), "sort", List.of(Map.of("col", "QTY", "dir", "desc")), "limit", 5L);
        assertEquals("[[0,100],[1,99],[2,98],[3,97],[4,96]]", Json.write(values(lt.rows("trades", q, null))));
        feed.l.upsert(row(3, 2, "A", 1000, null), null);            // a changed row moves to the top of this order
        feed.l.delete(List.of(0), null);                            // a removed key hides the source's row
        feed.l.upsert(row(500, 1, "B", 99, null), null);            // a new row takes its place in the order
        Map<String, Object> page = lt.rows("trades", q, null);
        assertEquals("[[3,1000],[1,99],[500,99],[2,98],[4,96]]", Json.write(values(page)));
        assertEquals(50L, page.get("total"));
        Map<String, Object> next = new LinkedHashMap<>(q);
        next.put("cursor", page.get("next"));
        assertEquals("[[5,95],[6,94],[7,93],[8,92],[9,91]]", Json.write(values(lt.rows("trades", next, null))));
        int scans = src.scans;
        lt.rows("trades", q, null);
        assertEquals(scans, src.scans, "the index is kept");
        src.version = "v2";
        lt.rows("trades", q, null);
        assertEquals(scans + 1, src.scans, "a new source version reads it again");
    }

    @Test
    void narrowRefusesABareListAndHeavyColumnsComeOnlyWithTheWholeKey() {
        Memory src = new Memory(SnapshotSource.Tier.WHOLE);
        src.put(row(1, 1, "A", 1, null));
        LiveTables lt = new LiveTables(new LiveTables.Resolver() {
            public SnapshotSource snapshot(String n) { return src; }
            public LiveStream stream(String n) { return null; }
        }, LiveTables.Options.DEFAULTS.withUpkeepMs(0), null).openReads();
        lt.declare(LiveTableSpec.read("""
                rachana: 1
                live-table: pos
                snapshot: s
                key: ID
                narrow: { any-of: [BOOK, ID] }
                columns: [ { name: ID, type: integer }, { name: BOOK }, { name: QTY, type: list, items: integer, heavy: true } ]
                """, "pos.live.yaml"));
        ProtocolError e = assertThrows(ProtocolError.class, () -> lt.rows("pos", Map.of(), null));
        assertEquals("rdp.query.narrow", e.code());
        Map<String, Object> byBook = Json.read("{\"filter\":{\"op\":\"==\",\"args\":[{\"col\":\"BOOK\"},{\"lit\":\"A\"}]}}") instanceof Map<?, ?> m
                ? cast(m) : null;
        assertEquals(List.of("ID", "BOOK"), lt.rows("pos", byBook, null).get("columns"));
        Map<String, Object> byKey = cast((Map<?, ?>) Json.read("{\"filter\":{\"op\":\"==\",\"args\":[{\"col\":\"ID\"},{\"lit\":1}]}}"));
        assertEquals(List.of("ID", "BOOK", "QTY"), lt.rows("pos", byKey, null).get("columns"));
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> cast(Map<?, ?> m) {
        return (Map<String, Object>) m;
    }

    @Test
    void pollingDiffSendsWhatChangedByKey() {
        Memory src = new Memory(SnapshotSource.Tier.WHOLE);
        src.put(row(1, 1, "A", 1, null));
        src.put(row(2, 1, "A", 1, null));
        PollingDiff poll = new PollingDiff(src, List.of("ID"), 0);
        LiveTables lt = new LiveTables(new LiveTables.Resolver() {
            public SnapshotSource snapshot(String n) { return src; }
            public LiveStream stream(String n) { return poll; }
        }, LiveTables.Options.DEFAULTS.withUpkeepMs(0), null).openReads();
        lt.declare(LiveTableSpec.read(DECL, "trades.live.yaml"));
        lt.rows("trades", Map.of(), null);
        poll.poll();
        src.put(row(1, 2, "A", 7, null));
        src.rows.remove("2");
        poll.poll();
        assertEquals("[[1,2,\"A\",7,null]]", Json.write(values(lt.rows("trades", Map.of(), null))));
    }

    // ---- readers (L6) ---------------------------------------------------------------------------------------------------

    /** Two readers: "a" sees book A with QTY masked; "b" sees book B; "x" may not read; "ghost" is told the table does not exist. */
    static Authoriser books() {
        return (caller, table) -> switch (String.valueOf(caller)) {
            case "a" -> new ReaderView("book=A;mask=QTY", Json.read("{\"op\":\"==\",\"args\":[{\"col\":\"BOOK\"},{\"lit\":\"A\"}]}"), Set.of("QTY"),
                    null, null, Set.of(), null);
            case "b" -> new ReaderView("book=B", Json.read("{\"op\":\"==\",\"args\":[{\"col\":\"BOOK\"},{\"lit\":\"B\"}]}"), Set.of(), null, null,
                    Set.of(), null);
            case "ghost" -> throw new Authoriser.Refusal("not yours", true);
            default -> throw new Authoriser.Refusal("no right", false);
        };
    }

    @Test
    void twoReadersNeverSeeEachOthersRowsInPagesPatchesOrTotalsAndAMaskedValueNeverOrdersOrFilters() throws Exception {
        for (SnapshotSource.Tier tier : List.of(SnapshotSource.Tier.WHOLE, SnapshotSource.Tier.COLUMNS)) {
            Memory src = new Memory(tier);
            src.put(row(1, 1, "A", 30, null));
            src.put(row(2, 1, "A", 10, null));
            src.put(row(3, 1, "B", 20, null));
            Feed feed = new Feed();
            List<Map<String, Object>> events = new CopyOnWriteArrayList<>();
            LiveTables lt = engine(src, feed, "", LiveTables.Options.DEFAULTS.withFlushMs(10)).authoriser(books()).audit(events::add);
            Map<String, Object> pa = lt.rows("trades", Map.of("columns", List.of("ID", "QTY")), "a");
            assertEquals("[[1,{\"$masked\":true}],[2,{\"$masked\":true}]]", Json.write(values(pa)), tier.name());
            assertEquals(2L, pa.get("total"));
            assertEquals("[[3,20]]", Json.write(values(lt.rows("trades", Map.of("columns", List.of("ID", "QTY")), "b"))));
            ProtocolHost host = new ProtocolHost("", lt);
            assertEquals(403, host.handle("a", "POST", "/tables/trades/query", Map.of(),
                    Json.read("{\"sort\":[{\"col\":\"QTY\"}]}")).status(), "a masked value never orders");
            assertEquals(403, host.handle("a", "POST", "/tables/trades/query", Map.of(),
                    Json.read("{\"filter\":{\"op\":\">\",\"args\":[{\"col\":\"QTY\"},{\"lit\":5}]}}")).status(), "nor filters");
            assertEquals(403, host.handle("x", "GET", "/tables/trades", Map.of(), null).status());
            assertEquals(404, host.handle("ghost", "GET", "/tables/trades", Map.of(), null).status());
            assertEquals(2, events.stream().filter(e -> "read-refused".equals(e.get("event"))).count());
            List<Map<String, Object>> ga = new CopyOnWriteArrayList<>();
            List<Map<String, Object>> gb = new CopyOnWriteArrayList<>();
            lt.subscribe("trades", new TableProvider.Subscribe(null, null, 0, List.of(List.of(1), List.of(2)), false), ga::add, "a");
            lt.subscribe("trades", TableProvider.Subscribe.after(null), gb::add, "b");
            feed.l.upsert(row(4, 1, "B", 40, null), null);
            feed.l.upsert(row(1, 2, "A", 31, null), null);
            Thread.sleep(60);
            String a = Json.write(ga), b = Json.write(gb);
            assertFalse(a.contains("\"ID\":4") || a.contains("\"ID\":3"), a);
            assertTrue(a.contains("{\"$masked\":true}") && !a.contains("31"), a);
            assertFalse(b.contains("\"ID\":1"), b);
            assertTrue(b.contains("\"ID\":4"), b);
            Map<?, ?> last = (Map<?, ?>) ga.get(ga.size() - 1).get("data");
            assertEquals(2L, last.get("total"), "a's total counts a's rows only");
            assertEquals(List.of("trades"), lt.tables("a").stream().map(t -> t.get("name")).toList());
            assertTrue(lt.tables("x").isEmpty());
            lt.close();
        }
    }

    @Test
    void withoutAnAuthoriserEveryReadIsRefusedAndAnEditableTableNeedsOne() {
        Memory src = new Memory(SnapshotSource.Tier.WHOLE);
        LiveTables lt = engine(src, new Feed(), "", null);
        assertEquals("rdp.forbidden", assertThrows(ProtocolError.class, () -> lt.rows("trades", Map.of(), null)).code());
        assertThrows(IllegalStateException.class, () -> lt.declare(LiveTableSpec.read(DECL
                + "edit: { fields: [ { id: qty, bind: QTY, type: number } ] }\n", "trades.live.yaml")));
    }
}
