/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ash.rachana.SutraProblem;
import com.ash.rachana.parse.DocBlockLint;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** The guard (SPEC 2.9): every fragment of every shipped library has a complete doc block, RCH-2093 as an error. */
class LibraryDocBlocksTest {

    @Test
    void everyShippedFragmentHasACompleteDocBlock() throws Exception {
        Path libs = Paths.get(System.getProperty("rachana.repo.root")).toAbsolutePath().normalize().resolve("libraries");
        List<String> problems = new ArrayList<>();
        int n = 0;
        try (Stream<Path> files = Files.walk(libs)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".fragment.yaml")).sorted().toList()) {
                n++;
                for (SutraProblem p : DocBlockLint.check(Files.readString(f, StandardCharsets.UTF_8), libs.relativize(f).toString(), SutraProblem.ERROR)) {
                    problems.add(p.location().file() + ":" + p.location().line() + " " + p.path() + " " + p.message());
                }
            }
        }
        assertTrue(n >= 90, "fragments found: " + n);
        assertEquals(List.of(), problems);
    }
}
