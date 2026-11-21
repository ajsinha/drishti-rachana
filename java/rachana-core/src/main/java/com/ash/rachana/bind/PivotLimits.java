/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.bind;

/**
 * Whether a table panel offers its rows to a pivot and how many it may hand over; {@code switchName} is the setting a host
 * names when the pivot is off (in the message a viewer reads).
 */
public record PivotLimits(Boolean enabled, Integer maxRecords, String switchName) {

    public PivotLimits {
        enabled = enabled == null || enabled;
        maxRecords = maxRecords == null || maxRecords <= 0 ? 50_000 : maxRecords;
        switchName = switchName == null ? "pivot.enabled" : switchName;
    }

    public static PivotLimits defaults() {
        return new PivotLimits(null, null, null);
    }
}
