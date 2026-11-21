/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.live.spi;

import java.util.List;
import java.util.Map;

/**
 * The changes of one live table (LIVE_TABLES 1.2), resolved by the host by name; optional (without one a table is static). Rows arrive
 * whole and carry the declared version column when there is one. A stream whose broker is absent reports {@code paused}: the table
 * serves its snapshot and is never in error.
 */
public interface LiveStream {

    /** What a stream tells the engine. Called from any thread; the engine orders them. */
    interface Listener {
        /** A row inserted or changed, whole; {@code position} the stream's own (may be null). */
        void upsert(Map<String, Object> row, String position);

        /** A row deleted: its key values in key order. */
        void delete(List<Object> key, String position);

        /** Changes were lost (a reconnect that cannot resume): the engine starts a new epoch. */
        void gap(String reason);

        /** The stream is up, or paused with a reason (its broker absent). */
        void state(boolean up, String reason);
    }

    /** Starts sending changes to the listener; closing the handle stops them. */
    AutoCloseable subscribe(Listener listener) throws Exception;
}
