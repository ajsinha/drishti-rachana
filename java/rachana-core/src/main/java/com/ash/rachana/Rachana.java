/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana;

public final class Rachana {
    private Rachana() { }

    /** The name of the library, from its internal banner. */
    public static String name() {
        return com.ash.rachana.internal.Banner.NAME;
    }
}
