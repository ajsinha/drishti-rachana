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

/** The text side of {@link Upgrade}: rewrites in place in every scalar style, refusals, the diff and the command line. */
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
}
