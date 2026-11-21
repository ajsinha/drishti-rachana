/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.live;

import com.ash.rachana.live.spi.Change;
import com.ash.rachana.live.spi.ChangeSink;
import com.ash.rachana.live.spi.ChangeWriter;
import com.ash.rachana.live.spi.OutboxStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * The small outbox for hosts without a write path of their own (LIVE_TABLES 1.6, LT-8): a {@link ChangeSink} that journals every change
 * ({@link OutboxStore}) before it answers, then sends it to the source through a {@link ChangeWriter}, one at a time in the order taken
 * (so per key in order). <b>Exactly once</b>: a change is resent after a crash with the same id, which the writer treats as already
 * applied; a repeated id answers the first answer ({@link #prior}). A change the host's {@code hold} rule names is {@code held} (four
 * eyes is the host's, LT-9) until the host calls {@link #release} or {@link #reject}. A writer failure leaves the change queued and is
 * tried again on the next {@link #flush} (or by the sender thread every {@code retryMs}); after {@code maxTries} it is {@code failed}.
 * A host without its own sender calls {@link #flush}; {@link #start} runs a daemon sender. Thread-safe.
 */
public final class Outbox implements ChangeSink, AutoCloseable {

    /** One change in the outbox. */
    private static final class Held {
        final Change change;
        volatile State state;
        volatile String message;
        volatile Object version;
        volatile Instant at;
        int tries;

        Held(Change change, State state, String message, Instant at) {
            this.change = change;
            this.state = state;
            this.message = message;
            this.at = at;
        }

        Outcome outcome() {
            return new Outcome(state, message, version);
        }
    }

    private final OutboxStore store;
    private final ChangeWriter writer;
    private final Function<Change, String> hold;
    private final Clock clock;
    private final int maxTries;
    private final Map<String, Held> byId = new LinkedHashMap<>();
    private final java.util.ArrayDeque<Held> queue = new java.util.ArrayDeque<>();      // queued, in the order they became so
    private final java.util.ArrayDeque<String> done = new java.util.ArrayDeque<>();     // finished ids, oldest first (kept for repeats)
    private final int keepDone;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final java.util.concurrent.locks.ReentrantLock sending = new java.util.concurrent.locks.ReentrantLock();
    private final Object signal = new Object();
    private volatile Thread sender;
    private volatile boolean closed;
    private boolean kicked;

    /**
     * Opens the outbox over its journal: the changes it holds are read back (queued ones are sent again, with their ids, on the next
     * {@link #flush}).
     *
     * @param hold the host's rule: the reason to hold a change for a decision, or null to send it
     * @param maxTries tries of one change before it is {@code failed}
     * @param keepDone finished changes kept in memory (and by a file journal) so a repeated id answers its first answer
     */
    public Outbox(OutboxStore store, ChangeWriter writer, Function<Change, String> hold, Clock clock, int maxTries, int keepDone)
            throws IOException {
        this.keepDone = Math.max(0, keepDone);
        this.store = store;
        this.writer = writer;
        this.hold = hold == null ? c -> null : hold;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.maxTries = Math.max(1, maxTries);
        for (Map<String, Object> r : store.replay()) {
            @SuppressWarnings("unchecked")
            Change c = Change.of((Map<String, Object>) r.get("change"));
            Held h = byId.computeIfAbsent(c.id(), k -> new Held(c, State.QUEUED, null, c.at()));
            h.state = State.of(String.valueOf(r.get("state")));
            h.message = r.get("message") == null ? null : String.valueOf(r.get("message"));
            h.version = r.get("version");
            h.at = r.get("at") == null ? c.at() : Instant.parse(String.valueOf(r.get("at")));
        }
        for (Held h : byId.values()) {
            if (h.state == State.QUEUED) {
                queue.add(h);
            } else if (h.state.terminal()) {
                done.add(h.change.id());
            }
        }
        prune();
    }

    /** Forgets the oldest finished changes beyond {@code keepDone}. Caller holds the lock (or is the constructor). */
    private void prune() {
        while (done.size() > keepDone) {
            byId.remove(done.pollFirst());
        }
    }

    private void finished(Held h) {
        done.add(h.change.id());
        prune();
    }

    @Override
    public Outcome accept(Change change) throws IOException {
        Held h;
        synchronized (this) {
            Held old = byId.get(change.id());
            if (old != null) {
                return old.outcome();
            }
            String why = hold.apply(change);
            h = new Held(change, why == null ? State.QUEUED : State.HELD, why == null ? "queued to be sent" : why, clock.instant());
            journal(h, null);                                         // durable before it is acknowledged
            byId.put(change.id(), h);
            if (h.state == State.QUEUED) {
                queue.add(h);
            }
        }
        tell(h, null, null);                                          // the tray; the engine audits the answer itself
        wake();
        return h.outcome();
    }

    @Override
    public synchronized Outcome prior(String changeId) {
        Held h = byId.get(changeId);
        return h == null ? null : h.outcome();
    }

    @Override
    public synchronized String madeBy(String changeId) {
        Held h = byId.get(changeId);
        return h == null ? null : h.change.by();
    }

    @Override
    public synchronized List<Entry> pending(String table) {
        List<Entry> out = new ArrayList<>();
        for (Held h : byId.values()) {
            if (h.change.table().equals(table) && (h.state == State.QUEUED || h.state == State.HELD || h.state == State.FAILED)) {
                out.add(entry(h));
            }
        }
        return out;
    }

    @Override
    public void watch(Listener listener) {
        listeners.add(listener);
    }

    @Override
    public Outcome release(String changeId, String by) {
        Held h = decide(changeId, State.QUEUED, "released by " + by, by);
        tell(h, "write-released", by);
        wake();
        return h.outcome();
    }

    @Override
    public Outcome reject(String changeId, String by, String reason) {
        Held h = decide(changeId, State.REJECTED, reason == null ? "rejected by " + by : reason, by);
        tell(h, "write-rejected", by);
        return h.outcome();
    }

    private synchronized Held decide(String id, State to, String message, String by) {
        Held h = byId.get(id);
        if (h == null || h.state != State.HELD) {
            throw new IllegalStateException("no held change '" + id + "'");
        }
        h.state = to;
        h.message = message;
        h.at = clock.instant();
        journal(h, by);
        if (to == State.QUEUED) {
            queue.add(h);
        } else {
            finished(h);
        }
        return h;
    }

    /**
     * Sends every queued change, in the order taken, one at a time; a change whose writer fails stays queued (tried again next time) and
     * stops the round, so later changes never overtake it. Answers how many were sent.
     */
    public int flush() {
        int sent = 0;
        sending.lock();
        try {
            while (!closed) {
                Held next;
                synchronized (this) {
                    next = queue.peekFirst();
                }
                if (next == null) {
                    return sent;
                }
                Object version;
                try {
                    version = writer.write(next.change);
                } catch (Exception e) {
                    boolean gaveUp;
                    synchronized (this) {
                        next.tries++;
                        next.message = "not sent: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                        if (next.tries >= maxTries || e instanceof ChangeWriter.Refused) {
                            next.state = State.FAILED;
                            next.at = clock.instant();
                            journal(next, null);
                            queue.pollFirst();
                            finished(next);
                        }
                        gaveUp = next.state == State.FAILED;
                    }
                    tell(next, "write-failed", null);
                    if (!gaveUp) {
                        return sent;                                  // tried again next time; nothing overtakes it
                    }
                    continue;
                }
                synchronized (this) {
                    next.state = State.SENT;
                    next.version = version;
                    next.message = "sent";
                    next.at = clock.instant();
                    journal(next, null);
                    queue.pollFirst();
                    finished(next);
                }
                tell(next, "write-sent", null);
                sent++;
            }
            return sent;
        } finally {
            sending.unlock();
        }
    }

    /** Starts a daemon sender: it flushes when a change arrives, and every {@code retryMs} while a change waits to be tried again. */
    public synchronized Outbox start(long retryMs) {
        if (sender == null) {
            sender = Thread.ofPlatform().daemon().name("rachana-outbox").start(() -> {
                while (!closed) {
                    flush();
                    synchronized (signal) {
                        try {
                            if (!kicked) {
                                signal.wait(Math.max(10, retryMs));
                            }
                            kicked = false;
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                }
            });
        }
        return this;
    }

    private void wake() {
        synchronized (signal) {
            kicked = true;
            signal.notifyAll();
        }
    }

    private void journal(Held h, String by) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("change", h.change.toMap());
        r.put("state", h.state.wire());
        if (h.message != null) {
            r.put("message", h.message);
        }
        if (h.version != null) {
            r.put("version", h.version);
        }
        if (by != null) {
            r.put("by", by);
        }
        r.put("at", h.at.toString());
        try {
            store.append(r);
        } catch (IOException e) {
            throw new UncheckedIOException("the outbox journal could not be written", e);
        }
    }

    private static Entry entry(Held h) {
        return new Entry(h.change, h.state, h.message, h.at);
    }

    private void tell(Held h, String event, String by) {
        Entry e;
        synchronized (this) {
            e = entry(h);
        }
        for (Listener l : listeners) {
            l.changed(e, event, by);
        }
    }

    @Override
    public void close() throws IOException {
        closed = true;
        wake();
        Thread t = sender;
        if (t != null) {
            t.interrupt();
        }
        store.close();
    }
}
