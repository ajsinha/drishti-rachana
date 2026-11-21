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
    /** Every name read, at every level. */
    private final Map<String, FormatSpec> all;
    /** The bundled names a later language level brought ({@code since} in the table), with that level; a pack's or site's name is never here. */
    private final Map<String, String> since;
    private final String level;

    private Formats(Map<String, FormatSpec> all, Map<String, String> since, String level, String overrideFile) {
        this.all = Map.copyOf(all);
        this.since = Map.copyOf(since);
        this.level = level;
        Map<String, FormatSpec> at = new HashMap<>(all);
        since.forEach((name, l) -> {
            if (com.ash.rachana.model.PanelKind.compareLevels(l, level) > 0) {
                at.remove(name);
            }
        });
        this.specs = Map.copyOf(at);
        this.overrideFile = overrideFile;
    }

    /**
     * This table at another language level (SUTRA_EXPANSION 6.2): a bundled name of a later level ({@code since}, such as the 1.2
     * promotions {@code pct4}, {@code bp1}, {@code df4}, {@code price4}) is unknown below it and prints as text, as any unknown name. A
     * name a pack or the site file defines stays at every level. A table starts at level 1.0.
     */
    public Formats atLevel(String next) {
        return next.equals(level) ? this : new Formats(all, since, next, overrideFile);
    }


    /** The bundled formats, plus overrides from {@code overrideFile} when it exists. */
    public static Formats load(String overrideFile) {
        return load(overrideFile, java.util.List.of());
    }

    /** The bundled formats, then each pack's formats, then the site override file. */
    public static Formats load(String overrideFile, java.util.List<String> packFiles) {
        Map<String, FormatSpec> m = new HashMap<>();
        Map<String, String> since = new HashMap<>();
        try (InputStream in = Formats.class.getClassLoader().getResourceAsStream("formats.yaml")) {
            read(in, m, since, true);
            for (String f : packFiles) {
                try (InputStream p = Files.newInputStream(Path.of(f))) {
                    read(p, m, since, false);
                }
            }
            if (overrideFile != null && !overrideFile.isBlank() && Files.isRegularFile(Path.of(overrideFile))) {
                try (InputStream o = Files.newInputStream(Path.of(overrideFile))) {
                    read(o, m, since, false);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new Formats(m, since, "1.0", overrideFile);
    }

    /** The language level that brought a name: a bundled name's {@code since} ({@code 1.0} when it has none, and for a pack's or the site's name), or null for a name the table does not hold. */
    public String since(String name) {
        return since.containsKey(name) ? since.get(name) : all.containsKey(name) ? "1.0" : null;
    }

    public static Formats defaults() {
        return load(null);
    }

    /**
     * Reads a table into {@code into}. An entry's {@code since} goes into {@code since}; a pack's or the site's name without one holds at
     * every level, so it leaves {@code since} (a customer profile's {@code pct4} stays its own at every level).
     */
    private static void read(InputStream in, Map<String, FormatSpec> into, Map<String, String> since, boolean bundled) throws IOException {
        Object root = Plain.path(Plain.yaml(in), "formats");
        for (Map.Entry<String, Object> e : Plain.entries(root)) {
            Object f = e.getValue();
            if (Plain.hasNonNull(f, "since")) {
                since.put(e.getKey(), Plain.text(Plain.path(f, "since")));   // a level-gated name wherever it is read (spec/1/formats.yaml)
            } else if (!bundled) {
                since.remove(e.getKey());
            }
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
        if (v instanceof com.ash.rachana.api.Node n && n.kind() == com.ash.rachana.api.Node.Kind.ARRAY) {
            for (int i = 0; i < n.size(); i++) {
                Object e = Values.simplify(n.get(i));
                if (e != null && !Values.text(e).isBlank()) {
                    items.add(Values.text(e));
                }
            }
        } else {
            java.util.List<String> parsed = com.ash.rachana.format.ListValues.of(v instanceof CharSequence ? v : Values.text(v));
            items.addAll(parsed != null ? parsed : java.util.List.of(Values.text(v)));
        }
        return items.isEmpty() ? "" : com.ash.rachana.format.ListValues.json(items);
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
