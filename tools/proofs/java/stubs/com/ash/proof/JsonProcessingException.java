/*
 * Copyright (c) 2026 Ashutosh Sinha. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.proof;

/** Proof-only stand-in (cut C5): the stub reader never throws it; kept so the copied catch blocks compile unchanged. */
public class JsonProcessingException extends java.io.IOException {
    private static final long serialVersionUID = 1L;

    public JsonProcessingException(String m) {
        super(m);
    }

    public Location getLocation() {
        return null;
    }

    public String getOriginalMessage() {
        return getMessage();
    }

    /** Line and column. */
    public static final class Location {
        public int getLineNr() {
            return 0;
        }

        public int getColumnNr() {
            return 0;
        }
    }
}
