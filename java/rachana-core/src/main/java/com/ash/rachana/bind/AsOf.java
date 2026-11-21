/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.bind;

import java.time.Instant;
import java.time.LocalDate;

/** The point in time a view reads: a business date, a known-at instant, or live (the latest). */
public record AsOf(LocalDate businessDate, Instant knownAt, boolean live) {

    public static final AsOf LATEST = new AsOf(null, null, true);

    public AsOf(LocalDate businessDate, Instant knownAt) {
        this(businessDate, knownAt, false);
    }

    public static AsOf of(LocalDate businessDate) {
        return new AsOf(businessDate, null, false);
    }

    public boolean isLatest() {
        return live && knownAt == null;
    }

    public LocalDate dateOr(LocalDate current) {
        return businessDate != null ? businessDate : current;
    }

    @Override
    public String toString() {
        return (live ? "live" : "") + (businessDate == null ? "" : (live ? " " : "") + businessDate) + (knownAt == null ? "" : "@" + knownAt);
    }
}
