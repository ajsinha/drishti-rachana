/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.tools;

import static org.junit.jupiter.api.Assertions.*;

import com.ash.rachana.profile.Grammar;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The language files are generated from the compiler's own tables: regenerating a released level reproduces the files committed for it
 * byte for byte (compat/levels/1.0), the newest level's are the ones in spec/1, a later level only adds, and every problem code a main
 * source raises is in the table.
 */
class LangFilesTest {

    static Path root() {
        return Paths.get(System.getProperty("rachana.repo.root")).toAbsolutePath().normalize();
    }

    private static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    @Test
    void theLevelIsWhatSpecHolds() throws IOException {
        String newest = Grammar.LEVEL;
        Map<String, String> now = LangFiles.generate(newest);
        for (String f : List.of("rachana.lang.json", "sutra.schema.json")) {
            assertEquals(read(root().resolve("spec/1").resolve(f)), now.get(f), "spec/1/" + f + " is not the generated file of level " + newest);
        }
    }

    @Test
    void everyProblemCodeTheSourcesRaiseIsInTheTable() throws IOException {
        String table = read(root().resolve("spec/1/language-tables.yaml"));
        String exceptions = read(root().resolve("java/rachana-core/src/main/java/com/ash/rachana/RachanaCode.java"));
        Pattern code = Pattern.compile("RCH-[0-9]{4}");
        java.util.Set<String> missing = new java.util.TreeSet<>();
        for (String module : List.of("rachana-core", "rachana-data", "rachana-live")) {
            Path src = root().resolve("java").resolve(module).resolve("src/main/java");
            if (!Files.isDirectory(src)) { continue; }
            try (Stream<Path> files = Files.walk(src)) {
                for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    Matcher m = code.matcher(read(p));
                    while (m.find()) {
                        if (!table.contains("code: \"" + m.group() + "\"") && !exceptions.contains("\"" + m.group() + "\"")) { missing.add(m.group()); }
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(), "problem codes raised by the sources but not in spec/1/language-tables.yaml: " + missing);
    }
}
