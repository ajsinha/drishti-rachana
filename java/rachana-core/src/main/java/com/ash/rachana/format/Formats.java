/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.format;

import com.ash.rachana.el.Values;
import com.ash.rachana.internal.Plain;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * The named formats used by {@code fmt:} and {@code fmt(...)}: the bundled {@code formats.yaml}, optionally
 * overridden by a site file. A host that reloads its formats builds a new table with {@link #load(String, java.util.List)}
 * and swaps it whole, so a reader sees the old table or the new one.
 */
public final class Formats {

    private volatile Map<String, FormatSpec> specs;
    private final String overrideFile;

    private Formats(Map<String, FormatSpec> specs, String overrideFile) {
        this.specs = Map.copyOf(specs);
        this.overrideFile = overrideFile;
    }


    /** The bundled formats, plus overrides from {@code overrideFile} when it exists. */
    public static Formats load(String overrideFile) {
        return load(overrideFile, java.util.List.of());
    }

    /** The bundled formats, then each pack's formats, then the site override file. */
    public static Formats load(String overrideFile, java.util.List<String> packFiles) {
        Map<String, FormatSpec> m = new HashMap<>();
        try (InputStream in = Formats.class.getClassLoader().getResourceAsStream("formats.yaml")) {
            read(in, m);
            for (String f : packFiles) {
                try (InputStream p = Files.newInputStream(Path.of(f))) {
                    read(p, m);
                }
            }
            if (overrideFile != null && !overrideFile.isBlank() && Files.isRegularFile(Path.of(overrideFile))) {
                try (InputStream o = Files.newInputStream(Path.of(overrideFile))) {
                    read(o, m);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new Formats(m, overrideFile);
    }

    public static Formats defaults() {
        return load(null);
    }

    private static void read(InputStream in, Map<String, FormatSpec> into) throws IOException {
        Object root = Plain.path(Plain.yaml(in), "formats");
        for (Map.Entry<String, Object> e : Plain.entries(root)) {
            Object f = e.getValue();
            into.put(e.getKey(), new FormatSpec(Plain.text(Plain.path(f, "type"), "number"), Plain.asInt(Plain.path(f, "decimals"), 0),
                    Plain.asBoolean(Plain.path(f, "grouping"), true), Plain.text(Plain.path(f, "sign"), "auto"),
                    Plain.asDouble(Plain.path(f, "scale"), 1), Plain.hasNonNull(f, "suffix") ? Plain.text(Plain.path(f, "suffix")) : null,
                    Plain.hasNonNull(f, "pattern") ? Plain.text(Plain.path(f, "pattern")) : null));
        }
    }

    public boolean has(String name) {
        return specs.containsKey(name);
    }

    public Set<String> names() {
        return specs.keySet();
    }

    /** The list format: a list attribute's values as JSON text ({@code ["fx","rates"]}), which every renderer draws as chips. */
    public static final String LIST = "list";

    private static String list(Object value) {
        Object v = Values.simplify(value);
        if (v == null) {
            return "";
        }
        java.util.List<Object> items = new java.util.ArrayList<>();
        if (v instanceof com.ash.rachana.hostcompat.DataNode n && n.type() == com.ash.rachana.hostcompat.NodeType.ARRAY) {
            for (int i = 0; i < n.size(); i++) {
                Object e = Values.simplify(n.get(i));
                if (e != null && !Values.text(e).isBlank()) {
                    items.add(Values.text(e));
                }
            }
        } else {
            java.util.List<String> parsed = com.ash.rachana.hostcompat.ListValues.of(v instanceof CharSequence ? v : Values.text(v));
            items.addAll(parsed != null ? parsed : java.util.List.of(Values.text(v)));
        }
        return items.isEmpty() ? "" : com.ash.rachana.hostcompat.ListValues.json(items);
    }

    /** Formats {@code value}; an unknown or null format name falls back to plain text. */
    public String format(String name, Object value) {
        if (LIST.equals(name)) {
            return list(value);
        }
        FormatSpec s = name == null ? null : specs.get(name);
        return s == null ? Values.text(value) : s.format(value);
    }
}
