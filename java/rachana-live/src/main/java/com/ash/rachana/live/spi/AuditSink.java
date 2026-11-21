/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.live.spi;

import java.util.Map;

/**
 * Where the engine's audit events go (LIVE_TABLES 5): {@code {at, caller, table, key?, change?, event, reason?, columns?}}; the host stores
 * them, the engine keeps counters only. Events of the read side: {@code read-refused}.
 */
@FunctionalInterface
public interface AuditSink {

    AuditSink NONE = e -> { };

    void record(Map<String, Object> event);
}
