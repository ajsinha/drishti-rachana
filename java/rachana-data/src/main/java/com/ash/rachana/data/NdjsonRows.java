/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.data;

import com.ash.rachana.parse.Json;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A page of rows as NDJSON and back (PROTOCOL 5.3): one header object ({@code table}, {@code version}, {@code columns}, {@code total},
 * {@code next}; page.json {@code ndjsonHeader}), then one array of cells per line in {@code columns} order. Reading gives the
 * columnar-by-row JSON page, so everything after the transport is the JSON path. Stateless.
 */
public final class NdjsonRows {

    public static final String MEDIA = "application/x-ndjson";

    private NdjsonRows() {}

    /** Writes a JSON page (either form) as NDJSON, line by line. */
    public static void write(Map<String, Object> page, OutputStream out) throws IOException {
        List<String> cols = ArrowRows.columns(page);
        Map<String, Object> head = new LinkedHashMap<>();
        head.put("table", page.get("table"));
        if (page.get("version") != null) {
            head.put("version", page.get("version"));
        }
        head.put("columns", cols);
        for (String k : List.of("total", "next", "totals", "schema")) {
            if (page.get(k) != null) {
                head.put(k, page.get(k));
            }
        }
        out.write((Json.write(head) + "\n").getBytes(StandardCharsets.UTF_8));
        for (List<Object> row : ArrowRows.values(page, cols)) {
            out.write((Json.write(row) + "\n").getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Reads NDJSON into the columnar-by-row JSON page. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> read(InputStream in) throws IOException {
        Map<String, Object> page = new LinkedHashMap<>();
        List<Object> values = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            boolean first = true;
            while ((line = r.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                if (first) {
                    page.putAll((Map<String, Object>) Json.read(line));
                    first = false;
                } else {
                    values.add(Json.read(line));
                }
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        page.forEach((k, v) -> {
            out.put(k, v);
            if (k.equals("columns")) {
                out.put("values", values);
            }
        });
        out.putIfAbsent("values", values);
        return out;
    }
}
