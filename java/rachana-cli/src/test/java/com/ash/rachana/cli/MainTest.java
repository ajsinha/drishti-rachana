/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The library commands end to end through the launcher: build, pack, verify, lock; usage and failure exit codes. */
class MainTest {
    static final String MANIFEST = """
            rachana: 1
            library: demo
            version: 1.2.0
            title: Demo
            summary: A tiny synthetic library for the tests.
            publisher: { name: Tests, key: ~ }
            licence: LicenseRef-Rachana-Test
            scope: public
            profiles: []
            depends: []
            """;
    static final String FIGURE = """
            rachana: 1
            fragment: figure
            version: 1
            doc: { summary: One figure. }
            params:
              value: { type: number, required: true, doc: The figure }
            panels:
              - { id: figure, kind: metric, label: Value, value: arg.value }
            """;

    @Test
    void buildPackVerifyLock(@TempDir Path tmp) throws Exception {
        Path dir = tmp.resolve("demo");
        Files.createDirectories(dir.resolve("fragments"));
        Files.writeString(dir.resolve("demo.rachana-lib.yaml"), MANIFEST);
        Files.writeString(dir.resolve("fragments/figure.v1.fragment.yaml"), FIGURE);
        Files.writeString(dir.resolve("LICENSE"), "Tests only.\n");
        assertEquals(0, Main.run(new String[] {"lib", "build", dir.toString()}));
        assertEquals(0, Main.run(new String[] {"lib", "verify", dir.toString()}));
        assertEquals(1, Main.run(new String[] {"lib", "verify", dir.toString(), "--require-signed"}), "unsigned is refused when required");
        assertEquals(0, Main.run(new String[] {"lib", "pack", dir.toString(), "--out", tmp.resolve("out").toString()}));
        Path rlib = tmp.resolve("out/demo-1.2.0.rlib");
        assertTrue(Files.exists(rlib));
        Path lock = tmp.resolve("rachana.lock");
        assertEquals(0, Main.run(new String[] {"lib", "lock", rlib.toString(), "--lock", lock.toString()}));
        assertTrue(Files.readString(lock).contains("demo"));
    }

    @Test
    void usageAndFailures(@TempDir Path tmp) {
        assertEquals(2, Main.run(new String[] {}));
        assertEquals(2, Main.run(new String[] {"lib", "nope"}));
        assertEquals(2, Main.run(new String[] {"lib", "build"}));
        assertEquals(1, Main.run(new String[] {"lib", "verify", tmp.resolve("missing").toString()}));
    }
}
