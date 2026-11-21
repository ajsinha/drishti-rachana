/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.live.spi;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Where a live table's accepted changes go (LIVE_TABLES 1.6, LT-8): exactly one per host. A host with a write path of its own (Drishti's
 * write-back: lanes, approvals, outbox) binds it here; a host without one installs {@code rachana-live}'s {@code Outbox}. The engine
 * calls {@link #accept} only after every check passed (declaration, switch, rights, rules, version) and never changes its rows itself:
 * the edit shows when the source's own change arrives on the stream. Four eyes is the host's (LT-9): a sink answers {@link State#HELD}
 * and the host later decides through {@link #release} or {@link #reject}.
 */
public interface ChangeSink {

    /** A change's state as the protocol says it (lower case on the wire). */
    enum State {
        /** done at the source. */ APPLIED,
        /** journalled, to be sent. */ QUEUED,
        /** waiting for a decision the host owns. */ HELD,
        /** the sink would not take it. */ REFUSED,
        /** sent to the source (terminal for the outbox). */ SENT,
        /** the host decided against it (terminal). */ REJECTED,
        /** the source would not take it after every try (terminal). */ FAILED;

        public String wire() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static State of(String s) {
            return valueOf(s.toUpperCase(Locale.ROOT));
        }

        /** Whether nothing more happens to a change in this state. */
        public boolean terminal() {
            return this == APPLIED || this == REFUSED || this == SENT || this == REJECTED || this == FAILED;
        }
    }

    /** What the sink answers for a change: its state, a message in words, and the row's new version when known. */
    record Outcome(State state, String message, Object version) {}

    /** One change the sink holds, as the tray ({@code <name>.changes}) lists it. */
    record Entry(Change change, State state, String message, Instant at) {}

    /** What the sink tells the engine when an entry moves: the entry and the audit event it makes ({@code write-sent}, …). */
    @FunctionalInterface
    interface Listener {
        void changed(Entry entry, String event, String decidedBy);
    }

    /** Takes one checked change. A sink that cannot take it answers {@link State#REFUSED} with the reason (never throws for that). */
    Outcome accept(Change change) throws Exception;

    /** The first answer given for a change id, kept durably (a repeat answers it again), or null when unknown. */
    default Outcome prior(String changeId) {
        return null;
    }

    /** Who made the change of that id (so a repeat by someone else is refused), or null when unknown. */
    default String madeBy(String changeId) {
        return null;
    }

    /** The changes of a table not yet done ({@code queued}, {@code held}, {@code failed}): the tray's rows. */
    default List<Entry> pending(String table) {
        return List.of();
    }

    /** Registers the engine's listener (one per sink). */
    default void watch(Listener listener) {
    }

    /**
     * Whether this sink may answer {@link State#HELD} (a host rule holds changes for a decision): then a table's tray takes the approvers'
     * decisions ({@code release}, {@code reject}) as writes. False by default.
     */
    default boolean holds() {
        return false;
    }

    /** The host's decision for a held change: send it. */
    default Outcome release(String changeId, String by) {
        throw new UnsupportedOperationException("this sink holds no changes");
    }

    /** The host's decision for a held change: drop it, with the reason. */
    default Outcome reject(String changeId, String by, String reason) {
        throw new UnsupportedOperationException("this sink holds no changes");
    }
}
