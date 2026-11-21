/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.compat;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/** The bootstrap runtime: tools/regime/CorpusCompile.java over Drishti's jars (read only), fed the corpus's own files. Deleted with RefBridge. */
final class ReferenceBridgeRuntime implements CorpusRuntime {
    private final String classpath;

    ReferenceBridgeRuntime(String classpath) { this.classpath = classpath; }

    @Override public String name() { return "reference-bridge"; }

    @Override public void compileAll(Corpus corpus, Path out) throws Exception {
        Path scratch = scratch(corpus);
        try {
            String java = System.getProperty("rachana.compat.java", "java");
            Process p = new ProcessBuilder(java, "-cp", classpath, Corpus.repoRoot().resolve("tools/regime/CorpusCompile.java").toString(),
                    scratch.toString(), out.toString()).redirectErrorStream(true).start();
            String log = new String(p.getInputStream().readAllBytes());
            if (p.waitFor() != 0) throw new IllegalStateException("the reference bridge failed: " + log);
        } finally {
            deleteTree(scratch);
        }
    }

    /**
     * The tree both runtimes read, as corpus_export.py took it from Drishti: {@code tree/} (the Sutras and the blocks together, the
     * packs' {@code pack.yaml} beside their {@code sutras/}), {@code kit/fragments/} (the UI kit) and {@code groups.json}.
     */
    static Path scratch(Corpus corpus) throws IOException {
        Path scratch = Files.createTempDirectory("rachana-compat-");
        copyTree(corpus.dir().resolve("sutras"), scratch.resolve("tree"));
        copyTree(corpus.dir().resolve("blocks"), scratch.resolve("tree"));
        copyTree(corpus.dir().resolve("blocks/ui-kit"), scratch.resolve("kit/fragments"));
        deleteTree(scratch.resolve("tree/ui-kit"));
        Files.copy(corpus.dir().resolve("groups.json"), scratch.resolve("groups.json"));
        return scratch;
    }

    private static void copyTree(Path from, Path to) throws IOException {
        if (!Files.isDirectory(from)) return;
        try (Stream<Path> s = Files.walk(from)) {
            for (Path f : s.toList()) {
                Path t = to.resolve(from.relativize(f).toString());
                if (Files.isDirectory(f)) Files.createDirectories(t);
                else { Files.createDirectories(t.getParent()); Files.copy(f, t, StandardCopyOption.REPLACE_EXISTING); }
            }
        }
    }

    static void deleteTree(Path p) throws IOException {
        if (!Files.exists(p)) return;
        try (Stream<Path> s = Files.walk(p)) {
            for (Path f : s.sorted(Comparator.reverseOrder()).toList()) Files.delete(f);
        }
    }
}
