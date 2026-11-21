/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.lib;

/** One library a resolver can offer: its name, version and where it is. */
public record LibraryRef(String name, String version, String source) {

    /** {@code name@version}. */
    @Override
    public String toString() {
        return name + "@" + version;
    }
}
