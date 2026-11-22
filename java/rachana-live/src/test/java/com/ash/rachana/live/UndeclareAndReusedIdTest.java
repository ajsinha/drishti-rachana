/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.live;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ash.rachana.data.ProtocolError;
import com.ash.rachana.data.ProtocolHost;
import com.ash.rachana.data.TableProvider;
import com.ash.rachana.live.LiveTablesTest.Feed;
import com.ash.rachana.live.LiveTablesTest.Memory;
import com.ash.rachana.live.spi.Authoriser;
import com.ash.rachana.live.spi.Change;
import com.ash.rachana.live.spi.ChangeSink;
import com.ash.rachana.live.spi.ReaderView;
import com.ash.rachana.live.spi.SnapshotSource;
import com.ash.rachana.parse.Json;
import com.ash.rachana.table.LiveTableSpec;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A host taking a declaration out ({@link LiveTables#undeclare}): open streams end with an expired gap, later reads answer expired, never a
 * held row. A change id sent again through the Data Protocol's write path: the same content answers the first answer, other content is
 * {@code 409} ({@code reused}), audited {@code write-reused}, never passed to the sink, also after a restart of the outbox.
 */
class UndeclareAndReusedIdTest {

    @TempDir
    Path dir;

    static final String EDIT = "edit: { fields: [ { id: qty, bind: QTY, type: number } ] }\ntray: true\n";

    /** Everyone reads everything and may edit QTY; their name is the caller. */
    static Authoriser editors() {
        return new Authoriser() {
            @Override
            public ReaderView view(Object caller, String table) {
                return new ReaderView("all", null, Set.of(), null, null, Set.of("QTY"), null);
            }

            @Override
            public Decision mayEdit(Object caller, String table, Map<String, Object> change, Map<String, Object> row) {
                return new Decision.Allow();
            }
        };
    }

    static LiveTables engine(Memory src, Feed feed, List<Map<String, Object>> events) {
        LiveTables lt = LiveTablesTest.engine(src, feed, "", LiveTables.Options.DEFAULTS.withFlushMs(10)).authoriser(editors())
                .audit(events::add);
        lt.declare(LiveTableSpec.read(LiveTablesTest.DECL + EDIT, "trades.live.yaml"));
        return lt;
    }

    static Memory source() {
        Memory src = new Memory(SnapshotSource.Tier.WHOLE);
        src.put(LiveTablesTest.row(1, 1, "A", 30, null));
        src.put(LiveTablesTest.row(2, 1, "B", 20, null));
        return src;
    }

    @Test
    void anUndeclaredTableEndsItsStreamsAsExpiredAndAnswersExpiredNeverAHeldRow() throws Exception {
        Feed feed = new Feed();
        LiveTables lt = engine(source(), feed, new CopyOnWriteArrayList<>());
        ProtocolHost host = new ProtocolHost("", lt);
        assertEquals(2L, lt.rows("trades", Map.of(), "ann").get("total"));
        List<Map<String, Object>> plain = new CopyOnWriteArrayList<>();
        List<Map<String, Object>> windowed = new CopyOnWriteArrayList<>();
        List<Map<String, Object>> tray = new CopyOnWriteArrayList<>();
        List<Map<String, Object>> bridged = new CopyOnWriteArrayList<>();
        lt.subscribe("trades", TableProvider.Subscribe.after(null), plain::add, "ann");
        lt.subscribe("trades", new TableProvider.Subscribe(null, null, 0, List.of(List.of(1)), false), windowed::add, "ann");
        lt.subscribe("trades.changes", TableProvider.Subscribe.after(null), tray::add, "ann");
        ProtocolHost.Bridge bridge = host.bridge(bridged::add, "ann");
        bridge.handle(Map.of("rdp", 1L, "t", "sub", "id", "s1", "table", "trades"));

        assertTrue(lt.undeclare("trades"));

        for (List<Map<String, Object>> frames : List.of(plain, windowed, tray)) {
            Map<String, Object> last = frames.get(frames.size() - 1);
            assertEquals("gap", last.get("t"), Json.write(frames));
            assertEquals(false, last.get("recoverable"));
            assertEquals(true, last.get("expired"));
        }
        Map<String, Object> b = bridged.get(bridged.size() - 1);
        assertEquals("gap", b.get("t"));
        assertEquals("s1", b.get("sub"));
        assertEquals(true, b.get("expired"));
        assertNull(feed.l, "the stream was let go");
        assertTrue(lt.describe().isEmpty(), "nothing open behind the name");
        assertTrue(lt.specs().isEmpty(), "the spec and its tray are gone");

        ProtocolError e = assertThrows(ProtocolError.class, () -> lt.rows("trades", Map.of(), "ann"));
        assertEquals("rdp.table.unknown", e.code());
        assertEquals(404, e.status());
        assertEquals(true, e.error().get("expired"));
        assertEquals(true, assertThrows(ProtocolError.class, () -> lt.subscribe("trades", TableProvider.Subscribe.after(null), f -> { }, "ann"))
                .error().get("expired"));
        assertEquals(true, assertThrows(ProtocolError.class, () -> lt.rows("trades.changes", Map.of(), "ann")).error().get("expired"));
        ProtocolHost.Answer read = host.handle("ann", "POST", "/tables/trades/query", Map.of(), Map.of());
        assertEquals(404, read.status());
        assertTrue(Json.write(read.body()).contains("\"expired\":true"), Json.write(read.body()));
        ProtocolHost.Answer write = host.handle("ann", "POST", "/tables/trades/write", Map.of(),
                Json.read("{\"id\":\"c1\",\"key\":[1],\"basedOn\":1,\"set\":{\"QTY\":5}}"));
        assertEquals(404, write.status(), "a write to an expired table is never taken");
        assertTrue(lt.tables("ann").isEmpty(), "an expired table is not listed");

        assertFalse(lt.undeclare("trades"), "idempotent");
        assertFalse(lt.undeclare("never"));
        assertNull(assertThrows(ProtocolError.class, () -> lt.rows("never", Map.of(), "ann")).error().get("expired"),
                "a name never declared is a plain unknown");

        lt.declare(LiveTableSpec.read(LiveTablesTest.DECL + EDIT, "trades.live.yaml"));
        assertEquals(2L, lt.rows("trades", Map.of(), "ann").get("total"), "declared again, it is read afresh");
        assertThrows(IllegalArgumentException.class, () -> lt.undeclare("trades.changes"));
        lt.close();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> change(String id, Object qty) {
        return (Map<String, Object>) Json.read("{\"id\":\"" + id + "\",\"key\":[1],\"basedOn\":1,\"set\":{\"QTY\":" + qty + "}}");
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> error(ProtocolHost.Answer a) {
        return (Map<String, Object>) ((Map<String, Object>) a.body()).get("error");
    }

    @Test
    void aReusedChangeIdWithOtherContentIsRefusedAndAuditedAlsoAfterARestart() throws Exception {
        List<Change> sent = new CopyOnWriteArrayList<>();
        List<Map<String, Object>> events = new CopyOnWriteArrayList<>();
        Outbox outbox = new Outbox(new FileOutboxStore(dir.resolve("outbox.jsonl"), 100), c -> {
            sent.add(c);
            return 2L;
        }, null, null, 3, 100);
        LiveTables lt = engine(source(), new Feed(), events).sink(outbox);
        ProtocolHost host = new ProtocolHost("", lt);

        ProtocolHost.Answer first = host.handle("ann", "POST", "/tables/trades/write", Map.of(), change("c1", 5));
        assertEquals(200, first.status(), Json.write(first.body()));
        assertEquals("queued", ((Map<?, ?>) first.body()).get("state"));
        ProtocolHost.Answer same = host.handle("ann", "POST", "/tables/trades/write", Map.of(), change("c1", "5.0"));
        assertEquals(Json.write(first.body()), Json.write(same.body()), "the same content (5 and 5.0 are one value): the first answer");

        ProtocolHost.Answer other = host.handle("ann", "POST", "/tables/trades/write", Map.of(), change("c1", 6));
        assertEquals(409, other.status());
        assertEquals("rdp.write.conflict", error(other).get("code"));
        assertEquals(true, error(other).get("reused"));
        assertTrue(String.valueOf(error(other).get("message")).contains("different change"));
        Map<String, Object> audited = events.stream().filter(e -> "write-reused".equals(e.get("event"))).findFirst().orElseThrow();
        assertEquals("c1", audited.get("change"));
        assertEquals("ann", audited.get("caller"));
        assertEquals(List.of("QTY"), audited.get("columns"));
        assertFalse(audited.containsKey("to") || audited.containsKey("from") || Json.write(audited).contains(":6"), "the audit names columns, never the values");
        assertEquals(1, outbox.pending("trades").size(), "the sink took one change");
        assertEquals(403, host.handle("bob", "POST", "/tables/trades/write", Map.of(), change("c1", 5)).status(), "another's id: 403 as before");
        outbox.flush();
        assertEquals(1, sent.size());
        assertEquals(5L, ((Number) sent.get(0).set().get("QTY")).longValue());
        outbox.close();
        lt.close();

        // a restart: the guard's memory is empty, the outbox's journal holds the change and so its digest
        List<Map<String, Object>> events2 = new CopyOnWriteArrayList<>();
        Outbox reopened = new Outbox(new FileOutboxStore(dir.resolve("outbox.jsonl"), 100), c -> 2L, null, null, 3, 100);
        LiveTables lt2 = engine(source(), new Feed(), events2).sink(reopened);
        ProtocolHost host2 = new ProtocolHost("", lt2);
        assertEquals(409, host2.handle("ann", "POST", "/tables/trades/write", Map.of(), change("c1", 7)).status());
        assertEquals(1, events2.stream().filter(e -> "write-reused".equals(e.get("event"))).count());
        ProtocolHost.Answer again = host2.handle("ann", "POST", "/tables/trades/write", Map.of(), change("c1", 5));
        assertEquals(200, again.status());
        assertEquals("sent", ((Map<?, ?>) again.body()).get("state"), "the same content: the change as it stands now");
        reopened.close();
        lt2.close();
    }

    @Test
    void aSinkThatKeepsNoDigestStillHasTheGuardsMemory() {
        List<Change> taken = new ArrayList<>();
        ChangeSink bare = c -> {
            taken.add(c);
            return new ChangeSink.Outcome(ChangeSink.State.APPLIED, "done", 2L);
        };
        LiveTables lt = engine(source(), new Feed(), new CopyOnWriteArrayList<>()).sink(bare);
        ProtocolHost host = new ProtocolHost("", lt);
        assertEquals(200, host.handle("ann", "POST", "/tables/trades/write", Map.of(), change("c9", 5)).status());
        assertEquals(200, host.handle("ann", "POST", "/tables/trades/write", Map.of(), change("c9", 5)).status());
        assertEquals(409, host.handle("ann", "POST", "/tables/trades/write", Map.of(), change("c9", 8)).status());
        assertEquals(1, taken.size());
        lt.close();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> json(String s) {
        return (Map<String, Object>) Json.read(s);
    }

    @Test
    void theDigestIsTheContentNotTheIdTheMakerOrTheTime() {
        Instant t = Instant.parse("2026-11-22T00:00:00Z");
        Change a = new Change("x", "trades", "update", List.of(1L), 1L, Map.of("QTY", 5L, "B", "A"), null, null, "ann", t);
        Change b = new Change("y", "trades", "update", List.of(1), 1, Map.of("B", "A", "QTY", 5.0), Map.of(), null, "bob", t.plusSeconds(9));
        assertEquals(a.digest(), b.digest());
        assertEquals(a.digest(), Change.of(json(Json.write(a.toMap()))).digest(), "a journal's round trip");
        assertFalse(a.digest().equals(new Change("x", "trades", "update", List.of(1L), 1L, Map.of("QTY", 5L, "B", "A"), null, "why", "ann", t)
                .digest()), "the reason is content");
        assertFalse(a.digest().equals(new Change("x", "trades", "update", List.of(1L), 2L, Map.of("QTY", 5L, "B", "A"), null, null, "ann", t)
                .digest()), "the version it was made on is content");
        assertFalse(a.digest().equals(new Change("x", "trades", "update", List.of("1"), 1L, Map.of("QTY", 5L, "B", "A"), null, null, "ann", t)
                .digest()), "a text key is not a number key");
    }
}
