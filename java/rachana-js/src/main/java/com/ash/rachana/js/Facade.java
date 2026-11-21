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
import com.ash.rachana.model.Ordered;

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

    /** Checked mode over known types (FORMAT 5.4): {@code {type, problems}}. */
    static String checked(String json) {
        return Cases.run(Cases::checked, json);
    }

    /** Whether the grammar reads a text (FORMAT 5.5): {@code {accepted}}. */
    static String grammar(String json) {
        return Cases.run(Cases::grammar, json);
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

    /** A view, then the ViewModel frames of each keyed table patch (FORMAT 5.9): {@code {viewModel, steps: [{frames, viewModel}]}}. */
    static String patch(String json) {
        return Cases.run(Cases::patch, json);
    }

    /** A view, then each fired trigger (FORMAT 5.15): {@code {viewModel, steps: [{state, effects, problems, viewModel}]}}. */
    static String fire(String json) {
        return Cases.run(Cases::fire, json);
    }

    /** What the runtime asks of each table of {@code data:} (FORMAT 5.10): {@code {plan: {tables, problems}}}. */
    static String plan(String json) {
        return Cases.run(Cases::plan, json);
    }

    /** The language levels this runtime reads and the default of the tools: {@code {"default": "1.2", "levels": ["1.0", ...]}} (Grammar.DEFAULT_LEVEL, the one place). */
    static String levels() {
        return Json.write(Ordered.map("default", com.ash.rachana.profile.Grammar.DEFAULT_LEVEL, "levels", com.ash.rachana.profile.Grammar.LEVELS));
    }

    /**
     * A Data Protocol query over rows the page holds (the static-files host, MODES 1): {@code {schema, rows, query, epoch, version}} ->
     * {@code {status, doc}}, the document of PROTOCOL 7 from Core's own {@code RowQuery} (the one evaluator of the filter tree), or the
     * protocol's error document with its status. The static host evaluates nothing itself.
     */
    static String query(String json) {
        return Cases.run((c, sb) -> {
            com.ash.rachana.table.TableSchema schema = com.ash.rachana.table.TableSchema.of(c.get("schema"));
            java.util.Map<String, Object> doc = Cases.obj(c.get("query"));
            @SuppressWarnings("unchecked")
            java.util.List<java.util.Map<String, Object>> rows = (java.util.List<java.util.Map<String, Object>>) c.get("rows");
            try {
                com.ash.rachana.data.RowQuery.validate(schema, doc);
                java.util.Map<String, Object> page = new com.ash.rachana.data.RowQuery().page(schema, rows == null ? java.util.List.of() : rows, doc,
                        String.valueOf(c.get("epoch")), Cases.obj(c.get("version")));
                sb.append("{\"status\":200,\"doc\":").append(Json.write(page)).append('}');
            } catch (com.ash.rachana.data.ProtocolError e) {
                sb.append("{\"status\":").append(e.status()).append(",\"doc\":").append(Json.write(e.body())).append('}');
            } catch (com.ash.rachana.table.FilterTree.Refused e) {
                sb.append("{\"status\":422,\"doc\":").append(Json.write(new com.ash.rachana.data.ProtocolError(e.code(), 422, e.getMessage(), e.fields()).body())).append('}');
            }
        }, json);
    }
}
