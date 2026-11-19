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

    // Report mode during the split (java/SPLIT_EXEMPTIONS.md): host words still in the moved compiler are counted, and the count may
    // only go down. A DRS- or DEL- code, a "drishti." setting name, or the product's name other than in "Drishti Rachana" and
    // "Drishti Elements" (the patterns are built from pieces so this file does not match itself).
    private static final java.util.regex.Pattern HOST_WORD = java.util.regex.Pattern.compile(
            "\\b(DRS|DEL)-\\d|\\b" + "drishti" + "\\.[a-z]|" + "Drishti" + "(?! (Rachana|Elements))");

    @Test
    void hostWordsOnlyShrink() throws Exception {
        List<String> hits = new ArrayList<>();
        for (Path f : Sources.nonEmpty(Sources.files(".java"))) {
            if (!f.startsWith(Sources.javaRoot().resolve("rachana-core"))) continue;     // the moved compiler; rachana-compat names its customers by design
            if (HOST_WORD.matcher(Files.readString(f)).find()) hits.add(Sources.javaRoot().relativize(f).toString());
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^- host-word files: (\\d+)").matcher(
                Files.readString(Sources.javaRoot().resolve("SPLIT_EXEMPTIONS.md")));
        int baseline = m.find() ? Integer.parseInt(m.group(1)) : 0;
        assertTrue(hits.size() <= baseline, "host words in " + hits.size() + " files, more than the " + baseline + " allowed: " + hits);
        assertTrue(hits.size() == baseline, "host words now in " + hits.size() + " files: lower 'host-word files' in SPLIT_EXEMPTIONS.md to it");
    }

    @Test
    void noHostTypesAreHeld() {
        // the host types the moved compiler named were held in a temporary package until A3 removed the last of them
        assertTrue(!Files.exists(Sources.javaRoot().resolve("rachana-core/src/main/java/com/ash/rachana/hostcompat")),
                "the hostcompat package is back: Core names no host type");
    }
}
