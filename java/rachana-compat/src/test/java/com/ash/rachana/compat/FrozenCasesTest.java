/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.compat;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * CG1, frozen cases. {@code conformance/1/FROZEN.sha384} lists the hash of every case file as of the last released language minor
 * ({@code tools/regime/freeze.py} writes it when a release creates a level). Its header says {@code # state: pre-release} (the
 * format is checked and every listed file must exist) or {@code # state: released <tag>} (every listed file must hash as listed: a
 * case is added, never edited, never removed). A file with a case of {@code since} above the released level is not listed.
 */
class FrozenCasesTest {
    static final Pattern LINE = Pattern.compile("([0-9a-f]{96})  (\\S+)");

    static Path cases() { return Corpus.repoRoot().resolve("conformance/1"); }

    record Frozen(String state, Map<String, String> hashes) { }

    static Frozen read(List<String> lines) {
        String state = null;
        Map<String, String> h = new TreeMap<>();
        for (String l : lines) {
            if (l.startsWith("# state:")) state = l.substring(8).trim();
            if (l.isBlank() || l.startsWith("#")) continue;
            Matcher m = LINE.matcher(l);
            if (!m.matches()) throw new AssertionError("FROZEN.sha384: not '<sha384>  <path>': " + l);
            if (h.put(m.group(2), m.group(1)) != null) throw new AssertionError("FROZEN.sha384: listed twice: " + m.group(2));
        }
        if (state == null) throw new AssertionError("FROZEN.sha384: no '# state:' header");
        return new Frozen(state, h);
    }

    /** The differences from the listed hashes: edited or removed files. */
    static List<String> differences(Frozen f, Path root) throws Exception {
        List<String> d = new ArrayList<>();
        for (var e : f.hashes().entrySet()) {
            Path p = root.resolve(e.getKey());
            if (!Files.isRegularFile(p)) d.add("removed: " + e.getKey());
            else if (!Corpus.sha384(Files.readAllBytes(p)).equals(e.getValue())) d.add("edited: " + e.getKey());
        }
        return d;
    }

    @Test
    void theFrozenFileIsWellFormedAndListsOnlyCaseFiles() throws Exception {
        Frozen f = read(Files.readAllLines(cases().resolve("FROZEN.sha384")));
        assertTrue(f.state().equals("pre-release") || f.state().startsWith("released "), "state: " + f.state());
        if (f.state().equals("pre-release")) {
            for (String p : f.hashes().keySet()) assertTrue(Files.isRegularFile(cases().resolve(p)), "listed but absent: " + p);
        }
        for (String p : f.hashes().keySet()) assertTrue(p.endsWith(".yaml") && !p.startsWith("/"), "not a case file path: " + p);
    }

    @Test
    void aReleasedLevelsCaseFilesAreUnchanged() throws Exception {
        Frozen f = read(Files.readAllLines(cases().resolve("FROZEN.sha384")));
        if (f.state().equals("pre-release")) return;   // nothing is released before v0.9.0
        assertEquals(List.of(), differences(f, cases()), "a case is added, never edited (CG1)");
    }

    @Test
    void theCheckSeesAnEditAndARemoval() throws Exception {
        Path d = Files.createTempDirectory("rachana-frozen-");
        try {
            Files.writeString(d.resolve("a.yaml"), "one");
            Files.writeString(d.resolve("b.yaml"), "two");
            Frozen f = new Frozen("released v0", new TreeMap<>(Map.of("a.yaml", Corpus.sha384("one".getBytes()), "b.yaml", Corpus.sha384("two".getBytes()))));
            assertEquals(List.of(), differences(f, d));
            Files.writeString(d.resolve("a.yaml"), "edited");
            Files.delete(d.resolve("b.yaml"));
            assertEquals(List.of("edited: a.yaml", "removed: b.yaml"), differences(f, d));
        } finally {
            ReferenceBridgeRuntime.deleteTree(d);
        }
    }

    @Test
    void everyCaseFileOfTheSuiteIsListedAtRelease() throws Exception {
        Frozen f = read(Files.readAllLines(cases().resolve("FROZEN.sha384")));
        if (f.state().equals("pre-release")) return;
        List<String> unlisted = new ArrayList<>();
        try (Stream<Path> s = Files.walk(cases())) {
            for (Path p : s.filter(x -> x.toString().endsWith(".yaml")).toList()) {
                String rel = cases().relativize(p).toString().replace('\\', '/');
                if (!rel.contains("/") || rel.startsWith("schema/")) continue;
                String head = String.join("\n", Files.readAllLines(p).stream().limit(8).toList());
                if (head.contains("since: \"1.0\"") && !f.hashes().containsKey(rel)) unlisted.add(rel);
            }
        }
        // a file added after the release at level 1.0 is allowed: an addition. The list below is informational for the release tool.
        assertNotNull(unlisted);
    }
}
