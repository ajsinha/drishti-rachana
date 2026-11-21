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

/** Every module has a module-info; no package under .internal is exported or opened. */
class PublicSurfaceTest {
    private static final Pattern EXPORT = Pattern.compile("\\b(exports|opens)\\s+([\\w.]+)");

    @Test
    void internalPackagesAreNotExported() throws Exception {
        for (Path f : Sources.nonEmpty(Sources.files("module-info.java"))) {
            Matcher m = EXPORT.matcher(Files.readString(f));
            while (m.find()) assertFalse(m.group(2).contains(".internal"), f + " " + m.group(1) + " " + m.group(2));
        }
    }

    @Test
    void everyProductModuleHasModuleInfo() throws Exception {
        List<String> missing = new ArrayList<>();
        try (var s = Files.list(Sources.javaRoot())) {
            for (Path dir : s.filter(Files::isDirectory).toList()) {
                String n = dir.getFileName().toString();
                boolean hasJava = Files.isDirectory(dir.resolve("src/main/java"));
                // rachana-js is the JavaScript build: its classes become bundles, never a JVM module, and it replaces Core classes by name
                if (hasJava && !n.equals("rachana-js") && !Files.exists(dir.resolve("src/main/java/module-info.java"))) missing.add(n);
            }
        }
        assertTrue(missing.isEmpty(), "modules without module-info: " + missing);
    }

    @Test
    void everyPackageHasPackageInfo() throws Exception {
        for (Path f : Sources.nonEmpty(Sources.files(".java"))) {
            String n = f.getFileName().toString();
            if (n.equals("package-info.java") || n.equals("module-info.java") || f.toString().contains("/src/test/")
                    || f.toString().replace('\\', '/').contains("/src/build/java/org/teavm/")) continue;   // a third-party file the JS build patches
            assertTrue(Files.exists(f.resolveSibling("package-info.java")), "no package-info.java next to " + f);
        }
    }

    @Test
    void casesJarHoldsNoCode() throws Exception {
        Path cases = Sources.javaRoot().resolve("rachana-conformance-cases");
        assertFalse(Files.exists(cases.resolve("src")), "the cases module must have no sources");
    }
}
