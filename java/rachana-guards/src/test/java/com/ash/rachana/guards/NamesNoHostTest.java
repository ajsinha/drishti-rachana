/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.guards;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Rachana names no host: no source or pom refers to the Drishti product's packages or artifacts. */
class NamesNoHostTest {
    // Built from pieces so this file does not match itself.
    private static final String HOST_PACKAGE = "com.ash." + "drishti";
    private static final String HOST_IMPORT = "import " + HOST_PACKAGE;

    @Test
    void noSourceNamesTheHostPackage() throws Exception {
        List<String> hits = new ArrayList<>();
        for (Path f : Sources.nonEmpty(Sources.files(".java"))) {
            String text = Files.readString(f);
            if (text.contains(HOST_PACKAGE) || text.contains(HOST_IMPORT)) hits.add(f.toString());
        }
        assertTrue(hits.isEmpty(), "sources naming the host: " + hits);
    }

    @Test
    void noPomNamesTheHost() throws Exception {
        List<String> hits = new ArrayList<>();
        for (Path f : Sources.nonEmpty(Sources.files("pom.xml"))) {
            if (Files.readString(f).contains(HOST_PACKAGE)
                    && !f.equals(Sources.javaRoot().resolve("pom.xml"))) hits.add(f.toString()); // the root pom lists it as banned
        }
        assertTrue(hits.isEmpty(), "poms naming the host: " + hits);
    }
}
