/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.live.spi;

import java.util.Map;

/**
 * Where the engine's audit events go (LIVE_TABLES 5): {@code {at, caller, table, key?, change?, event, reason?, columns?}}; the host stores
 * them, the engine keeps counters only. Events: {@code read-refused}; of the edit path {@code write-refused},
 * {@code write-conflicted}, {@code write-submitted}, {@code write-held}, {@code write-released}, {@code write-rejected}, {@code write-sent},
 * {@code write-applied}, {@code write-failed} (with {@code columns}, {@code from}, {@code to}; never a value masked for the caller), and
 * {@code write-reused}: a change id sent again by its maker with other content ({@link ChangeSink#priorDigest}), refused {@code 409} and
 * never passed on (with {@code columns}, no values).
 */
@FunctionalInterface
public interface AuditSink {

    AuditSink NONE = e -> { };

    void record(Map<String, Object> event);
}
