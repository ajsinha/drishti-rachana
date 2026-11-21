/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.bind;

import java.util.Objects;

/** One entity a view binds or names: its kind and id (neither blank). */
public record EntityRef(String kind, String id) {

    public EntityRef {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
        if (kind.isBlank() || id.isBlank()) {
            throw new IllegalArgumentException("kind and id must not be blank");
        }
    }

    public static EntityRef of(String kind, String id) {
        return new EntityRef(kind, id);
    }

    @Override
    public String toString() {
        return kind + "/" + id;
    }
}
