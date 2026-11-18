/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.guards;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Runtime code carries the proprietary header (the spec licence covers spec/ and conformance/ only); files stay under 2,500 lines. */
class LicenceHeaderAndLineCapTest {
    static final int LINE_CAP = 2500;

    @Test
    void everyJavaFileHasTheHeader() throws Exception {
        List<String> bad = new ArrayList<>();
        for (Path f : Sources.nonEmpty(Sources.files(".java"))) {
            String head = Files.readString(f);
            head = head.substring(0, Math.min(head.length(), 400));
            if (!head.contains("Copyright (c) 2026 Ashutosh Sinha") || !head.contains("Proprietary")) bad.add(f.toString());
        }
        assertTrue(bad.isEmpty(), "files without the licence header: " + bad);
    }

    @Test
    void noFileExceedsTheLineCap() throws Exception {
        List<String> bad = new ArrayList<>();
        for (Path f : Sources.nonEmpty(Sources.files(".java"))) {
            long n = Files.readAllLines(f).size();
            if (n > LINE_CAP) bad.add(f + " has " + n + " lines");
        }
        assertTrue(bad.isEmpty(), "files over the cap: " + bad);
    }
}
