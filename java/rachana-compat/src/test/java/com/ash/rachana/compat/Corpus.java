/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.compat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;

/** One customer corpus folder, {@code compat/customers/<customer>/<release>/}: its manifest and its files. */
record Corpus(String customer, String release, Path dir) {
    static Path repoRoot() {
        String p = System.getProperty("rachana.repo.root");
        if (p == null || p.isBlank()) throw new IllegalStateException("system property rachana.repo.root is not set");
        return Paths.get(p).toAbsolutePath().normalize();
    }

    /** Every corpus of every customer, oldest release name first. */
    static List<Corpus> all() throws IOException {
        Path customers = repoRoot().resolve("compat/customers");
        List<Corpus> l = new ArrayList<>();
        if (!Files.isDirectory(customers)) return l;
        try (Stream<Path> c = Files.list(customers)) {
            for (Path cd : c.filter(Files::isDirectory).sorted().toList()) {
                try (Stream<Path> r = Files.list(cd)) {
                    r.filter(Files::isDirectory).sorted().forEach(rd -> l.add(new Corpus(cd.getFileName().toString(), rd.getFileName().toString(), rd)));
                }
            }
        }
        return l;
    }

    String name() { return "drishti-compat-corpus-" + release; }

    /** The files the manifest lists that are missing, changed, or present but not listed: empty when the corpus is exactly as exported. */
    List<String> manifestDifferences() throws Exception {
        Map<String, String> listed = new TreeMap<>();
        for (String line : Files.readAllLines(dir.resolve("MANIFEST.sha384"), StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            int sp = line.indexOf("  ");
            listed.put(line.substring(sp + 2), line.substring(0, sp));
        }
        List<String> diffs = new ArrayList<>();
        Set<String> present = new TreeSet<>();
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path f : s.filter(Files::isRegularFile).toList()) {
                String rel = dir.relativize(f).toString().replace('\\', '/');
                if (rel.equals("MANIFEST.sha384")) continue;
                present.add(rel);
                String want = listed.get(rel);
                if (want == null) diffs.add("not in the manifest: " + rel);
                else if (!want.equals(sha384(Files.readAllBytes(f)))) diffs.add("changed: " + rel);
            }
        }
        for (String rel : listed.keySet()) if (!present.contains(rel)) diffs.add("missing: " + rel);
        return diffs;
    }

    static String sha384(byte[] b) throws Exception {
        StringBuilder h = new StringBuilder();
        for (byte x : MessageDigest.getInstance("SHA-384").digest(b)) h.append(String.format("%02x", x));
        return h.toString();
    }
}
