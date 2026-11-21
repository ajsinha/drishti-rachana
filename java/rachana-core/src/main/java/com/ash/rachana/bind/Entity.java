/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.bind;

import com.ash.rachana.api.Node;
import java.time.Instant;

/**
 * One entity as a {@link TableProvider} hands it over: its reference, its data, whether it was deleted, and where it came from
 * ({@code origin}, may be null). {@code host} is whatever the host keeps with it (its own provenance record, say); Core never reads it.
 */
public record Entity(EntityRef ref, Node data, boolean deleted, Origin origin, Object host) {

    /** Which source answered and when it was read (both may be null). */
    public record Origin(String source, Instant fetchedAt) {}

    public Entity {
        data = data == null ? Node.missing() : data;
    }

    public Entity(EntityRef ref, Node data) {
        this(ref, data, false, null, null);
    }
}
