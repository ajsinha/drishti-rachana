/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.compat;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * CG2, level replay (SUTRA_EXPANSION 6.5), skeleton. {@code compat/levels/<L>/} holds, for a released language level L,
 * {@code results.jsonl} (the conformance results of Rachana's JVM runtime at {@code --language L}), {@code rachana.lang.json},
 * {@code sutra.schema.json} and {@code functions.yaml}. {@code tools/regime/levels.py write L} creates a level once (it refuses an
 * existing one); there is no re-record flag. This test checks (1) every level folder holds the four files; (2) the definition files of
 * the newest level equal the generated ones in {@code spec/1} (older levels are checked against their own files only once a later
 * level exists: then the generator filters by {@code since}, a step of language 1.1); (3) the replay: when the JVM runtime is built
 * ({@code java/rachana-conformance/target/runtime-classpath.txt}) and {@code python3} is present, {@code levels.py check} runs the
 * suite at each level and compares the bytes. Without the built runtime step 3 is skipped with a message, never silently passed
 * in CI, which builds first.
 */
class LevelReplayTest {
    static final List<String> FILES = List.of("results.jsonl", "rachana.lang.json", "sutra.schema.json", "functions.yaml");

    static Path levels() { return Corpus.repoRoot().resolve("compat/levels"); }

    static List<Path> all() throws Exception {
        if (!Files.isDirectory(levels())) return List.of();
        try (Stream<Path> s = Files.list(levels())) { return s.filter(Files::isDirectory).sorted().toList(); }
    }

    @Test
    void everyLevelHoldsItsFourFiles() throws Exception {
        assertFalse(all().isEmpty(), "compat/levels has no level: 1.0 is written by tools/regime/levels.py write 1.0");
        for (Path d : all()) for (String f : FILES) assertTrue(Files.isRegularFile(d.resolve(f)), d.getFileName() + " lacks " + f);
    }

    @Test
    void theNewestLevelsDefinitionFilesAreTheGeneratedOnes() throws Exception {
        List<Path> l = all();
        Path newest = l.get(l.size() - 1);
        Path spec = Corpus.repoRoot().resolve("spec/1");
        for (String f : List.of("rachana.lang.json", "sutra.schema.json", "functions.yaml"))
            assertArrayEquals(Files.readAllBytes(newest.resolve(f)), Files.readAllBytes(spec.resolve(f)),
                "level " + newest.getFileName() + ": " + f + " differs from spec/1 (a level's files are written once; a change to the definition is a new level)");
    }

    @Test
    void theSuiteAtEachLevelGivesTheCommittedResults() throws Exception {
        Path cp = Corpus.repoRoot().resolve("java/rachana-conformance/target/runtime-classpath.txt");
        assumeTrue(Files.isRegularFile(cp), "JVM runtime not built (cd java && ./mvnw package -DskipTests): replay skipped");
        Process p = new ProcessBuilder("python3", Corpus.repoRoot().resolve("tools/regime/levels.py").toString(), "check")
            .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        assertEquals(0, p.waitFor(), "levels.py check failed:\n" + out);
    }
}
