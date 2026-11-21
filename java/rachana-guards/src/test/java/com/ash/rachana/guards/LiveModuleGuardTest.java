/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.guards;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import org.junit.jupiter.api.Test;

/**
 * Live tables (LIVE_TABLES 0, 8, 9, decision LT-2): the engine is the JVM module rachana-live, never in Core and never compiled to
 * JavaScript; it reads no role and no user (rights are the host's Authoriser alone).
 */
class LiveModuleGuardTest {
    private static final String LIVE = "com.ash.rachana.live";

    @Test
    void coreAndTheJavaScriptBuildNeverNameTheEngine() throws Exception {
        List<String> hits = new ArrayList<>();
        for (String module : List.of("rachana-api", "rachana-core", "rachana-js", "rachana-data")) {
            Path dir = Sources.javaRoot().resolve(module);
            if (!Files.isDirectory(dir)) continue;
            try (var s = Files.walk(dir)) {
                for (Path f : s.filter(p -> p.toString().endsWith(".java") || p.getFileName().toString().equals("pom.xml"))
                        .filter(p -> !p.toString().contains("/target/")).toList()) {
                    String t = Files.readString(f);
                    if (t.contains(LIVE + ".") || t.contains("<artifactId>rachana-live</artifactId>")) hits.add(f.toString());
                }
            }
        }
        assertTrue(hits.isEmpty(), "the live tables engine is named outside rachana-live: " + hits);
    }

    @Test
    void theBundlesHoldNoEngineClass() throws Exception {
        Path dist = Sources.javaRoot().getParent().resolve("js/packages/rachana/dist");
        for (String b : List.of("rachana.runtime.js", "rachana.tools.js")) {
            Path f = dist.resolve(b);
            if (!Files.isRegularFile(f)) continue;                      // built by tools/js/build_js.py; checked when present
            String t = Files.readString(f);
            assertFalse(t.contains("LiveTables") || t.contains("StoreOverlay") || t.contains("PollingDiff"), b + " holds the live engine");
        }
    }

    @Test
    void theEngineReadsNoRoleAndNoUser() throws Exception {
        Pattern words = Pattern.compile("(?i)\\b(role|roles|user|users|principal|permission)\\b");
        List<String> hits = new ArrayList<>();
        Path main = Sources.javaRoot().resolve("rachana-live/src/main/java");
        try (var s = Files.walk(main)) {
            for (Path f : s.filter(p -> p.toString().endsWith(".java")).toList()) {
                String code = Files.readString(f).replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "").replaceAll("\"(\\\\.|[^\"\\\\])*\"", "\"\"");
                Matcher m = words.matcher(code);
                if (m.find()) hits.add(f.getFileName() + ": " + m.group());
            }
        }
        assertTrue(hits.isEmpty(), "rachana-live reads a role or a user (rights are the host's Authoriser): " + hits);
    }
}
