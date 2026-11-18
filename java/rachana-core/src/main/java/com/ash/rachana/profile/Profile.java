/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.profile;

public final class Profile {
    private final String name;

    private Profile(String name) { this.name = name; }

    /** The Core profile (no registrations yet). */
    public static Profile core() { return new Profile("core"); }

    public String name() { return name; }
}
