/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.live;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ash.rachana.live.spi.Change;
import com.ash.rachana.live.spi.ChangeSink;
import com.ash.rachana.live.spi.ChangeWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The outbox's exactly-once (LIVE_TABLES 1.6, 9 "an outbox crash test"): a crash between the journal and the send, and a crash after the
 * send before the journal says so, each leave the source with one change; holds survive a restart; a torn last line is dropped.
 */
class OutboxTest {

    @TempDir
    Path dir;

    /** The source: applies a change id once, counts every call. */
    static final class Source implements ChangeWriter {
        final Set<String> applied = new LinkedHashSet<>();
        final List<String> calls = new ArrayList<>();

        @Override
        public synchronized Object write(Change c) {
            calls.add(c.id());
            applied.add(c.id());
            return 2L;
        }
    }

    static Change change(String id) {
        return new Change(id, "trades", "update", List.of("a"), 1L, Map.of("qty", 5), null, null, "bob", Instant.parse("2026-11-21T12:00:00Z"));
    }

    Outbox open(Source src, java.util.function.Function<Change, String> hold) throws IOException {
        return new Outbox(new FileOutboxStore(dir.resolve("outbox.jsonl"), 100), src, hold, null, 3, 100);
    }

    @Test
    void aCrashBetweenTheJournalAndTheSendSendsTheChangeOnceAfterTheRestart() throws Exception {
        Source src = new Source();
        Outbox first = open(src, null);
        assertEquals(ChangeSink.State.QUEUED, first.accept(change("c1")).state());   // journalled and acknowledged, never sent
        first.close();                                                                // the crash
        Outbox second = open(src, null);
        assertEquals(ChangeSink.State.QUEUED, second.prior("c1").state());
        assertEquals(1, second.flush());
        assertEquals(0, second.flush());
        assertEquals(List.of("c1"), src.calls);
        assertEquals(ChangeSink.State.SENT, second.prior("c1").state());
        second.close();
        Outbox third = open(src, null);                                               // finished: kept so a repeat answers it
        assertEquals(ChangeSink.State.SENT, third.accept(change("c1")).state());
        assertEquals(0, third.flush());
        assertEquals(List.of("c1"), src.calls);
        third.close();
    }

    @Test
    void aCrashAfterTheSendResendsTheSameIdAndTheSourceAppliesItOnce() throws Exception {
        Source src = new Source();
        FileOutboxStore real = new FileOutboxStore(dir.resolve("outbox.jsonl"), 100);
        Outbox first = new Outbox(new com.ash.rachana.live.spi.OutboxStore() {
            @Override
            public void append(Map<String, Object> r) throws IOException {
                if ("sent".equals(r.get("state"))) {
                    throw new IOException("the crash: the source has it, the journal does not say so");
                }
                real.append(r);
            }

            @Override
            public List<Map<String, Object>> replay() throws IOException {
                return real.replay();
            }
        }, src, null, null, 3, 100);
        first.accept(change("c1"));
        assertThrows(UncheckedIOException.class, first::flush);
        real.close();
        Outbox second = open(src, null);
        assertEquals(1, second.flush());
        assertEquals(List.of("c1", "c1"), src.calls);                                 // resent with the same id
        assertEquals(Set.of("c1"), src.applied);                                      // the source saw one change
        second.close();
    }

    @Test
    void aHeldChangeSurvivesARestartAndIsSentOnlyWhenReleased() throws Exception {
        Source src = new Source();
        Outbox first = open(src, c -> "four eyes");
        assertEquals(ChangeSink.State.HELD, first.accept(change("c1")).state());
        assertEquals(0, first.flush());
        first.close();
        Outbox second = open(src, c -> "four eyes");
        assertEquals(List.of("c1"), second.pending("trades").stream().map(e -> e.change().id()).toList());
        assertThrows(IllegalStateException.class, () -> second.release("nope", "carol"));
        second.release("c1", "carol");
        assertEquals(1, second.flush());
        assertEquals(List.of("c1"), src.calls);
        second.close();
    }

    @Test
    void aTornLastLineIsDroppedAndLaterChangesKeepTheirOrder() throws Exception {
        Source src = new Source();
        Outbox first = open(src, null);
        first.accept(change("c1"));
        first.accept(change("c2"));
        first.close();
        Files.writeString(dir.resolve("outbox.jsonl"), "{\"change\":{\"id\":\"c3\"", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        Outbox second = open(src, null);
        assertEquals(2, second.flush());
        assertEquals(List.of("c1", "c2"), src.calls);
        second.close();
    }

    @Test
    void aFailingSourceKeepsLaterChangesBehindAndFailsAfterItsTries() throws Exception {
        List<String> calls = new ArrayList<>();
        Outbox o = new Outbox(new LiveReferenceServer.MemoryStore(), c -> {
            calls.add(c.id());
            if (c.id().equals("c1")) {
                throw new IOException("down");
            }
            return null;
        }, null, null, 2, 10);
        o.accept(change("c1"));
        o.accept(change("c2"));
        assertEquals(0, o.flush());
        assertEquals(List.of("c1"), calls);                                           // c2 never overtakes c1
        assertEquals(1, o.flush());                                                   // c1 failed on its second try, then c2 sent
        assertEquals(List.of("c1", "c1", "c2"), calls);
        assertEquals(ChangeSink.State.FAILED, o.prior("c1").state());
        assertEquals(List.of("c1"), o.pending("trades").stream().map(e -> e.change().id()).toList());
    }
}
