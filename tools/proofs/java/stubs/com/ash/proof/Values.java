/*
 * Copyright (c) 2026 Ashutosh Sinha. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.proof;

import java.util.Map;

/** Proof-only: the scalar tags of the prep step's JSON ({@code $f64} raw IEEE bits, {@code $big} a whole number beyond long). */
public final class Values {
    private Values() {
    }

    public static Object scalar(Object v) {
        if (v instanceof Map<?, ?> m && m.size() == 1) {
            if (m.containsKey("$f64")) {
                return Double.longBitsToDouble(Json.hex((String) m.get("$f64")));
            }
            if (m.containsKey("$big")) {
                return new java.math.BigInteger((String) m.get("$big"));
            }
        }
        return v;
    }
}
