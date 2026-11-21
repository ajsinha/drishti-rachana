/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.tools;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The text side of {@link Upgrade}: rewrites in place in every scalar style, refusals, the diff and the command line; a fragment's level
 * is its own {@code rachana:} key, as a Sutra's (SPEC 2.1 rule 4).
 */
class UpgradeTest {

    static final String HEAD = "rachana: 1\nsutra: up\nversion: 1\npanels:\n";

    @Test
    void aDoubleQuotedScalarWithEscapesIsRewrittenInPlace() {
        String in = HEAD + "  - { id: m, kind: metric, label: L, value: \"range($.r, \\\"p\\\") + \\u0031\" }\n";
        Upgrade.Result r = Upgrade.upgrade("a.sutra.yaml", in, "1.2");
        assertNull(r.refused());
        assertEquals(in.replace("rachana: 1\n", "rachana: 1.2\n").replace("range(", "span("), r.text());
    }

    @Test
    void aSingleQuotedScalarIsRewrittenInPlace() {
        String in = HEAD + "  - { id: m, kind: metric, label: L, value: 'range($.r, ''p'')' }\n";
        assertEquals(in.replace("rachana: 1\n", "rachana: 1.2\n").replace("range(", "span("), Upgrade.upgrade("a.sutra.yaml", in, "1.2").text());
    }

    @Test
    void aFoldedScalarThatNeedsARewriteIsRefusedNotGuessed() {
        String in = HEAD + "  - id: m\n    kind: metric\n    label: L\n    value: >\n      range($.r,\n      'p')\n";
        Upgrade.Result r = Upgrade.upgrade("a.sutra.yaml", in, "1.2");
        assertNotNull(r.refused());
        assertTrue(r.refused().contains("rewrite it by hand"), r.refused());
        assertEquals(in, r.text());
    }

    @Test
    void aFieldNamedRangeAndATextAreLeftAlone() {
        String in = HEAD + "  - { id: m, kind: metric, label: \"range(x)\", value: \"$.range + size('range(')\" }\n";
        Upgrade.Result r = Upgrade.upgrade("a.sutra.yaml", in, "1.2");
        assertEquals(1, r.changes().size(), "only the declaration");
    }

    @Test
    void aSutraOfALaterLevelIsNeverMovedDown() {
        assertNotNull(Upgrade.upgrade("a.sutra.yaml", HEAD.replace("rachana: 1\n", "rachana: 1.2\n"), "1.1").refused());
    }

    @Test
    void theDiffIsUnified() {
        String d = Upgrade.diff("a.yaml", "a\nb\nc\n", "a\nB\nc\n");
        assertEquals("--- a/a.yaml\n+++ b/a.yaml\n@@ -1,4 +1,4 @@\n a\n-b\n+B\n c\n \n", d);
        assertEquals("", Upgrade.diff("a.yaml", "x", "x"));
    }

    @Test
    void theCommandIsADryRunUntilWriteAndExitsOneOnABlockedFile(@TempDir Path dir) throws Exception {
        Path ok = dir.resolve("ok.sutra.yaml");
        Path bad = dir.resolve("bad.sutra.yaml");
        Files.writeString(ok, HEAD + "  - { id: m, kind: metric, label: L, value: \"range($.r, 'p')\" }\n");
        Files.writeString(bad, HEAD + "  - { id: m, kind: metric, label: L, value: \"lambda\" }\n");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(out, true, StandardCharsets.UTF_8);
        assertEquals(1, UpgradeCommand.run(new String[] {dir.toString(), "--diff"}, ps, ps));
        assertTrue(Files.readString(ok).contains("range("), "a dry run writes nothing");
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("+  - { id: m, kind: metric, label: L, value: \"span($.r, 'p')\" }"));
        assertEquals(1, UpgradeCommand.run(new String[] {dir.toString(), "--write", "--report", dir.resolve("r.json").toString()}, ps, ps));
        assertTrue(Files.readString(ok).startsWith("rachana: 1.2\n") && Files.readString(ok).contains("span("));
        assertTrue(Files.readString(bad).startsWith("rachana: 1\n"), "a blocked file is not written");
        assertTrue(Files.readString(dir.resolve("r.json")).contains("\"blocked\":1"));
        assertEquals(0, UpgradeCommand.run(new String[] {ok.toString()}, ps, ps), "upgrading again: nothing to do");
        assertEquals(0, UpgradeCommand.run(new String[] {"--rules"}, ps, ps));
        assertEquals(2, UpgradeCommand.run(new String[] {"--to", "9.9"}, ps, ps));
    }

    static final String FRAGMENT = "fragment: spread\nversion: 1\nparams:\n  rows: $.rows\npanels:\n"
            + "  - { id: s, kind: metric, label: Spread, value: \"range(%{rows}, 'p')\" }\n";

    @Test
    void aFragmentAtOneZeroIsRewrittenAndDeclaresTheNewLevel() {
        Upgrade.Result r = Upgrade.upgrade("s.v1.fragment.yaml", "rachana: 1\n" + FRAGMENT, "1.2");
        assertNull(r.refused());
        assertEquals("1.0", r.from());
        assertEquals("rachana: 1.2\n" + FRAGMENT.replace("range(", "span("), r.text());
        assertEquals(java.util.List.of("declare", "span"), r.changes().stream().map(Upgrade.Change::rule).toList());
    }

    @Test
    void aFragmentAtTheTargetLevelIsLeftUntouched() {
        String in = "rachana: 1.2\n" + FRAGMENT.replace("range(%{rows}, 'p')", "sum(range(1, 4))");
        Upgrade.Result r = Upgrade.upgrade("s.v1.fragment.yaml", in, "1.2");
        assertNull(r.refused());
        assertEquals("1.2", r.from());
        assertFalse(r.changed());
        assertEquals(in, r.text(), "level-1.2 text keeps its meaning: range is the sequence");
        assertTrue(r.changes().isEmpty() && r.flags().isEmpty());
    }

    @Test
    void aFragmentThatDeclaresNoLevelIsOneZeroAndGainsTheKeyAsItsFirstKey() {
        String in = "# a comment\n" + FRAGMENT;
        Upgrade.Result r = Upgrade.upgrade("s.v1.fragment.yaml", in, "1.2");
        assertNull(r.refused());
        assertEquals("1.0", r.from());
        assertEquals("# a comment\nrachana: 1.2\n" + FRAGMENT.replace("range(", "span("), r.text());
        assertEquals(java.util.List.of("declare", "span"), r.changes().stream().map(Upgrade.Change::rule).toList());
        Upgrade.Result again = Upgrade.upgrade("s.v1.fragment.yaml", r.text(), "1.2");
        assertFalse(again.changed());
        assertEquals(r.text(), again.text());
    }

    @Test
    void aFragmentOfALaterLevelIsNeverMovedDownAndAnUnknownLevelIsRefused() {
        assertNotNull(Upgrade.upgrade("s.v1.fragment.yaml", "rachana: 1.2\n" + FRAGMENT, "1.1").refused());
        assertTrue(Upgrade.upgrade("s.v1.fragment.yaml", "rachana: 1.7\n" + FRAGMENT, "1.2").refused().contains("no level"));
    }

    /** Gate 2 review 10.5: the three runs over a Sutra and a fragment that calls range(list, field). */
    @Test
    void runningAgainNeitherRefusesNorRewritesAFragmentItUpgraded(@TempDir Path dir) throws Exception {
        Path sutra = dir.resolve("up.sutra.yaml");
        Path fragment = dir.resolve("spread.v1.fragment.yaml");
        Files.writeString(sutra, HEAD + "  - { id: m, kind: metric, label: L, value: \"range($.r, 'p')\" }\n");
        Files.writeString(fragment, "rachana: 1\n" + FRAGMENT);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(out, true, StandardCharsets.UTF_8);
        String[] cmd = {dir.toString(), "--write"};

        assertEquals(0, UpgradeCommand.run(cmd, ps, ps), "run 1");
        String once = Files.readString(fragment);
        assertEquals("rachana: 1.2\n" + FRAGMENT.replace("range(", "span("), once, "run 1: the fragment is rewritten and declares 1.2");

        assertEquals(0, UpgradeCommand.run(cmd, ps, ps), "run 2: not refused");
        assertEquals(once, Files.readString(fragment), "run 2: nothing changes");

        String sequence = once.replace("span(%{rows}, 'p')", "sum(range(1, 4))");
        Files.writeString(fragment, sequence);
        assertEquals(0, UpgradeCommand.run(cmd, ps, ps), "run 3");
        assertEquals(sequence, Files.readString(fragment), "run 3: level-1.2 range (the sequence) is not renamed span");

        assertEquals(2, UpgradeCommand.run(new String[] {dir.toString(), "--from", "1.0"}, ps, ps), "--from is gone");
    }
}
