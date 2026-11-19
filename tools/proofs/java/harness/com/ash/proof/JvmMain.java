/*
 * Copyright (c) 2026 Ashutosh Sinha. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.proof;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** JVM entry: {@code JvmMain <cases.jsonl> <results.jsonl> [--yaml]}; {@code --yaml} for the reference build (Drishti's jars). */
public final class JvmMain {
    private JvmMain() {
    }

    public static void main(String[] args) throws Exception {
        Harness.useYaml = args.length > 2 && "--yaml".equals(args[2]);
        String in = Files.readString(Path.of(args[0]), StandardCharsets.UTF_8);
        long t0 = System.nanoTime();
        String out = Harness.runAll(in);
        long t1 = System.nanoTime();
        Files.writeString(Path.of(args[1]), out, StandardCharsets.UTF_8);
        System.err.println("jvm: " + out.split("\n").length + " lines in " + (t1 - t0) / 1_000_000 + " ms");
    }
}
