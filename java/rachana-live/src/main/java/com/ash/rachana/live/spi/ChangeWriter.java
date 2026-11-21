/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.live.spi;

/**
 * Writes one change to the source system for the {@code Outbox} (LIVE_TABLES 1.6). It MUST treat a change id it has applied before as
 * already applied (the outbox resends a change after a crash with the same id), and SHOULD apply the version guard again at the source.
 */
@FunctionalInterface
public interface ChangeWriter {

    /**
     * Applies the change (idempotent by {@link Change#id()}).
     *
     * @return the row's new version, or null when the source does not say
     * @throws Exception when it could not be applied now (the outbox tries again)
     */
    Object write(Change change) throws Exception;
}
