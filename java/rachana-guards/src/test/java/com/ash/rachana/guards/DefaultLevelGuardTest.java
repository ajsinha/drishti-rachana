/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.guards;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * G2 gate F12: the default language level is written once, in Core ({@code Grammar.DEFAULT_LEVEL}). The entry points (the Node command
 * line, {@code <rachana-view>}, the Studio, the Python package and its adapters, the server) take it from there, through the bundle's
 * {@code levels()} or the constant, so none of their sources holds a quoted language level.
 */
class DefaultLevelGuardTest {
    private static final Pattern LEVEL = Pattern.compile("[\"']1\\.[0-9]+[\"']");

    private static Path repo() {
        String p = System.getProperty("rachana.repo.root");
        if (p == null || p.isBlank()) throw new IllegalStateException("system property rachana.repo.root is not set");
        return Paths.get(p).toAbsolutePath().normalize();
    }

    private static List<Path> under(Path dir, String... suffixes) throws Exception {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile).filter(f -> Arrays.stream(suffixes).anyMatch(x -> f.toString().endsWith(x)))
                    .filter(f -> !f.toString().contains("/target/") && !f.toString().contains("/node_modules/") && !f.toString().contains("/.venv/")
                            && !f.toString().contains("/bundle/") && !f.toString().contains("/web/")).toList();
        }
    }

    @Test
    void noEntryPointWritesALanguageLevel() throws Exception {
        Path r = repo();
        List<Path> files = new ArrayList<>();
        try (Stream<Path> pk = Files.list(r.resolve("js/packages"))) {
            for (Path p : pk.toList()) {
                for (String d : List.of("src", "bin", "server")) files.addAll(under(p.resolve(d), ".js", ".mjs", ".html"));
            }
        }
        files.addAll(under(r.resolve("python/src"), ".py"));
        for (String m : List.of("rachana-server", "rachana-cli", "rachana-live", "rachana-data")) files.addAll(under(r.resolve("java").resolve(m).resolve("src/main"), ".java"));
        // the Python conformance runner states the highest level an adapter implements: an adapter's own fact, not a default
        files.removeIf(f -> f.endsWith("conformance/runner.py"));
        assertTrue(files.size() > 20, "the guard saw too few files: wrong root " + r);
        List<String> hits = new ArrayList<>();
        for (Path f : files) {
            int n = 0;
            for (String line : Files.readAllLines(f)) {
                n++;
                if (LEVEL.matcher(line).find()) hits.add(r.relativize(f) + ":" + n + ": " + line.strip());
            }
        }
        assertTrue(hits.isEmpty(), "a language level written outside Core (use Grammar.DEFAULT_LEVEL / the bundle's levels()):\n" + String.join("\n", hits));
    }
}
