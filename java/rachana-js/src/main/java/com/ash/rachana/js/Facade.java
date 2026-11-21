/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.js;

import com.ash.rachana.conformance.Cases;
import com.ash.rachana.parse.Json;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The minimal facade both bundles export (split step A15): JSON text in, JSON text out, every answer the document the conformance
 * suite records for the same request (FORMAT section 5), so the JavaScript runtime is checked case by case against the JVM. A request
 * is a case's fields without its id ({@code {"expr": "@.a * 2", "given": {"record": {"a": 1}}}}).
 */
final class Facade {

    static final String VERSION = "0.11.0";

    private Facade() {
    }

    /**
     * Builds the instance: {@code profiles} as {@code core,drishti}, {@code formats} a JSON list of format-table texts (YAML), Core's
     * table first, then the first profile's names. The bundle has no file system: the texts are put into the build's in-memory one
     * and read from there by the same code the JVM runs.
     */
    static void init(String profiles, String formats) {
        List<String> files = new ArrayList<>();
        Object list = formats == null || formats.isBlank() ? List.of() : Json.read(formats);
        try {
            Path dir = Path.of("/rachana/formats");
            Files.createDirectories(dir);
            int i = 0;
            for (Object text : (List<?>) list) {
                Path f = dir.resolve(i++ + ".yaml");
                Files.write(f, String.valueOf(text).getBytes(StandardCharsets.UTF_8));
                files.add(f.toString());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Cases.init(profiles == null || profiles.isBlank() ? "core" : profiles, files);
    }

    static String expr(String json) {
        return Cases.run(Cases::expr, json);
    }

    static String exprError(String json) {
        return Cases.run(Cases::exprError, json);
    }

    static String format(String json) {
        return Cases.run(Cases::format, json);
    }

    static String problem(String json) {
        return Cases.run(Cases::problem, json);
    }

    static String library(String json) {
        return Cases.run(Cases::lib, json);
    }

    static String view(String json) {
        return Cases.run(Cases::sutra, json);
    }
}
