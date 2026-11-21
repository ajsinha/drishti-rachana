/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.compat;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * CG2, level replay (SUTRA_EXPANSION 6.5), skeleton. {@code compat/levels/<L>/} holds, for a released language level L,
 * {@code results.jsonl} (the conformance results of Rachana's JVM runtime at {@code --language L}), {@code rachana.lang.json},
 * {@code sutra.schema.json} and {@code functions.yaml}. {@code tools/regime/levels.py write L} creates a level once (it refuses an
 * existing one); there is no re-record flag. This test checks (1) every level folder holds the four files; (2) the definition files of
 * the newest level equal the generated ones in {@code spec/1}, its {@code functions.yaml} less the Core entries of a later {@code since}
 * (as {@code levels.py write} makes it); (3) the replay: when the JVM runtime is built
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
        String level = newest.getFileName().toString();
        Path spec = Corpus.repoRoot().resolve("spec/1");
        for (String f : List.of("rachana.lang.json", "sutra.schema.json", "functions.yaml")) {
            String want = Files.readString(spec.resolve(f));
            assertEquals(f.equals("functions.yaml") ? functionsAt(want, level) : want, Files.readString(newest.resolve(f)),
                "level " + level + ": " + f + " differs from spec/1" + (f.equals("functions.yaml") ? " less its entries of a later since" : "")
                    + " (a level's files are written once; a change to the definition is a new level)");
        }
    }

    /**
     * spec/1/functions.yaml at a level, as levels.py write makes it: the {@code - name:} entries of a later {@code since} left out; before
     * the level of {@code roles_since} (parameter roles, {@code role: "column"}) that key and every parameter's {@code role} left out.
     */
    static String functionsAt(String text, String level) {
        java.util.regex.Matcher roles = java.util.regex.Pattern.compile("(?m)^roles_since: \"([0-9.]+)\"\n").matcher(text);
        if (roles.find() && minor(roles.group(1)) > minor(level)) {
            text = text.replace(roles.group(), "").replaceAll(", role: \"[a-z]+\"}", "}");
        }
        StringBuilder out = new StringBuilder();
        List<String> block = null;
        for (String line : text.split("(?<=\n)")) {
            if (line.startsWith("- ") || !line.startsWith(" ")) {
                if (block != null && keep(block, level)) block.forEach(out::append);
                block = line.startsWith("- name: ") ? new ArrayList<>(List.of(line)) : null;
                if (block == null) out.append(line);
            } else if (block != null) block.add(line);
            else out.append(line);
        }
        if (block != null && keep(block, level)) block.forEach(out::append);
        return out.toString();
    }

    private static boolean keep(List<String> block, String level) {
        for (String b : block) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("  since: \"([0-9.]+)\"\\s*").matcher(b);
            if (m.matches()) return minor(m.group(1)) <= minor(level);
        }
        return true;
    }

    private static int minor(String v) {
        String[] p = v.split("\\.");
        return Integer.parseInt(p[0]) * 1000 + Integer.parseInt(p[1]);
    }

    @Test
    void theFilteredCatalogueDropsOnlyLaterEntries() {
        String yaml = "core:\n# ---- x (1)\n- name: a\n  since: \"1.0\"\n- name: b\n  since: \"1.1\"\n# ---- y (1)\n- name: c\n  since: \"1.0\"\n";
        assertEquals("core:\n# ---- x (1)\n- name: a\n  since: \"1.0\"\n# ---- y (1)\n- name: c\n  since: \"1.0\"\n", functionsAt(yaml, "1.0"));
        assertEquals(yaml, functionsAt(yaml, "1.1"));
        String roles = "status: draft\nroles_since: \"1.2\"\ncore:\n- name: a\n  params:\n  - {name: \"f\", type: \"text\", role: \"column\"}\n  since: \"1.0\"\n";
        assertEquals("status: draft\ncore:\n- name: a\n  params:\n  - {name: \"f\", type: \"text\"}\n  since: \"1.0\"\n", functionsAt(roles, "1.1"));
        assertEquals(roles, functionsAt(roles, "1.2"));
    }

    /** EL1-4: the replay skips only the cases compat/levels/moved-cases.yaml lists; an unlisted difference or a wrong entry fails. */
    @Test
    void theReplaySkipsOnlyListedMovedCases(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        assertEquals(0, run(tmp, List.of("python3", "-c", "import yaml")), "python3 with PyYAML is a prerequisite of the build (pip install -r tools/conformance/requirements.txt)");
        Path suites = Files.createDirectories(tmp.resolve("suites"));
        Files.writeString(suites.resolve("s.yaml"), "suite: el/s\nsince: \"1.0\"\nprofile: core\ncases:\n"
            + "  - {id: kept, kind: expr}\n  - {id: moved, kind: expr, since: \"1.1\"}\n");
        Path then = tmp.resolve("then.jsonl");
        Files.writeString(then, "{\"case\":\"el/s#kept\",\"status\":\"pass\"}\n{\"case\":\"el/s#moved\",\"status\":\"fail\"}\n"
            + "{\"case\":\"el/s#gone\",\"status\":\"fail\"}\n");
        Path now = tmp.resolve("now.jsonl");
        Files.writeString(now, "{\"case\":\"el/s#kept\",\"status\":\"pass\"}\n{\"case\":\"el/s#moved\",\"status\":\"unsupported\"}\n");
        String moved = "moved_cases_version: 1\nlevels:\n  \"1.0\":\n    - {case: \"el/s#moved\", since: \"1.1\"}\n";
        String gone = "    - {case: \"el/s#gone\", withdrawn: \"why\"}\n";
        assertEquals(0, compare(tmp, then, now, moved + gone, suites), "both listed: identical");
        assertEquals(1, compare(tmp, then, now, moved, suites), "the withdrawn case unlisted: a difference");
        assertEquals(1, compare(tmp, then, now, gone, suites), "the moved case unlisted: a difference");
        assertEquals(1, compare(tmp, then, now, moved + gone + "    - {case: \"el/s#kept\", since: \"1.1\"}\n", suites),
            "a case that did not move listed: the list is wrong");
        assertEquals(1, compare(tmp, then, now, moved.replace("since: \"1.1\"", "since: \"1.2\"") + gone, suites),
            "a moved case listed at the wrong level");
        Files.writeString(now, "{\"case\":\"el/s#kept\",\"status\":\"fail\"}\n{\"case\":\"el/s#moved\",\"status\":\"unsupported\"}\n");
        assertEquals(1, compare(tmp, then, now, moved + gone, suites), "a recorded case that changed: a difference");
        // G2 gate F9: a recorded unsupported case that was deleted is not "newly answered": an unlisted deletion fails, a listed withdrawal passes
        Files.writeString(then, "{\"case\":\"el/s#kept\",\"status\":\"pass\"}\n{\"case\":\"el/s#dropped\",\"status\":\"unsupported\"}\n");
        Files.writeString(now, "{\"case\":\"el/s#kept\",\"status\":\"pass\"}\n");
        String dropped = "moved_cases_version: 1\nlevels:\n  \"1.0\":\n    - {case: \"el/s#dropped\", withdrawn: \"why\"}\n";
        assertEquals(1, compare(tmp, then, now, "moved_cases_version: 1\nlevels: {}\n", suites), "a deleted unsupported case, unlisted: a difference");
        assertEquals(0, compare(tmp, then, now, dropped, suites), "a deleted unsupported case, listed as withdrawn: identical");
    }

    private static int compare(Path tmp, Path then, Path now, String moved, Path suites) throws Exception {
        Path m = tmp.resolve("moved-cases.yaml");
        Files.writeString(m, moved);
        return run(tmp, List.of("python3", Corpus.repoRoot().resolve("tools/regime/levels.py").toString(), "compare", "1.0",
            then.toString(), now.toString(), "--moved", m.toString(), "--suites", suites.toString()));
    }

    private static int run(Path dir, List<String> cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true).start();
        p.getInputStream().readAllBytes();
        return p.waitFor();
    }

    @Test
    void theSuiteAtEachLevelGivesTheCommittedResults() throws Exception {
        Path cp = Corpus.repoRoot().resolve("java/rachana-conformance/target/runtime-classpath.txt");
        assertTrue(Files.isRegularFile(cp), "JVM runtime not built: run ./mvnw verify from java/ (rachana-conformance builds it before this module); nothing is skipped");
        Process p = new ProcessBuilder("python3", Corpus.repoRoot().resolve("tools/regime/levels.py").toString(), "check")
            .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        assertEquals(0, p.waitFor(), "levels.py check failed:\n" + out);
    }
}
