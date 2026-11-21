/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.bind;

import com.ash.drishti.api.EntityRef;

/** A panel's {@code source} names an entity of a kind the caller may not open: the panel shows "no access", not data. */
public final class SourceDeniedException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private final String kind;

    public SourceDeniedException(EntityRef source) {
        super("no access to " + source.kind(), null, false, false);
        this.kind = source.kind();
    }

    public String kind() {
        return kind;
    }
}
