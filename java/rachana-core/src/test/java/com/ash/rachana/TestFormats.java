/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana;

import java.net.URISyntaxException;
import java.nio.file.Path;

/** Test only: the profile format names the unit tests use, as a file path for {@code Formats.load}. */
public final class TestFormats {
    private TestFormats() { }

    /** The absolute path of {@code profile-formats.yaml} on the test class path. */
    public static String file() {
        try {
            return Path.of(TestFormats.class.getResource("/profile-formats.yaml").toURI()).toString();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
