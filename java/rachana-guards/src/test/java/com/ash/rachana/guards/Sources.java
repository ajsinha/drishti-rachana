/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.guards;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Stream;

/** Locates the reactor and lists its files; paths come from explicit system properties, never from the working folder. */
final class Sources {
    private Sources() { }

    static Path javaRoot() {
        String p = System.getProperty("rachana.java.root");
        if (p == null || p.isBlank()) throw new IllegalStateException("system property rachana.java.root is not set");
        return Paths.get(p).toAbsolutePath().normalize();
    }

    static List<Path> files(String suffix) throws IOException {
        try (Stream<Path> s = Files.walk(javaRoot())) {
            return s.filter(Files::isRegularFile)
                    .filter(f -> f.getFileName().toString().endsWith(suffix))
                    .filter(f -> !f.toString().contains("/target/"))
                    .toList();
        }
    }

    /** The scan must see something, or a wrong root would make every guard pass. */
    static List<Path> nonEmpty(List<Path> l) {
        if (l.isEmpty()) throw new AssertionError("the guard matched no file: wrong root " + javaRoot());
        return l;
    }
}
