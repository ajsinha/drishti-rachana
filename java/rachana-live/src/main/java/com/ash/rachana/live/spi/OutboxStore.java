/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.live.spi;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * The outbox's journal (LIVE_TABLES 1.6): records appended durably before a change is acknowledged and read back, in order, after a
 * restart. Each record is {@code {change: {...}, state, message?, at, version?, by?}}; the last record of a change id is its state.
 * {@code rachana-live} ships a file journal; a host may keep it in its database.
 */
public interface OutboxStore extends AutoCloseable {

    /** Appends one record; returns only once it is durable. */
    void append(Map<String, Object> record) throws IOException;

    /** Every record, in the order appended. */
    List<Map<String, Object>> replay() throws IOException;

    @Override
    default void close() throws IOException {
    }
}
