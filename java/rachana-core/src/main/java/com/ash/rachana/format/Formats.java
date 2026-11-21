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
import java.util.Map;
import java.util.Set;
import com.ash.rachana.model.Ordered;
import java.util.LinkedHashMap;

/**
 * The named formats used by {@code fmt:} and {@code fmt(...)}: the bundled {@code formats.yaml}, then each pack's file, then the site's
 * override file. A table from {@link #load(String, java.util.List)} never changes. A <b>live</b> table ({@link #live(String,
 * java.util.List)}) is one a host reloads while readers hold it ({@link #reload()}, {@link #reload(java.util.List)}):
 *
 * <ul>
 *   <li>a reload reads the bundled table, the pack files and the site file again and swaps the whole table in one step: a reader sees
 *       the old table or the new one, never a mix, and a value being formatted finishes with the table it started with;</li>
 *   <li>every holder of the table sees the new one at its next read: the same instance, each of its {@link #atLevel(String)} views, the
 *       contexts, binders and Rachana instances built over it; compiled expressions hold no format, so nothing else is rebuilt;</li>
 *   <li>a reload that fails (a file that cannot be read, a table that does not parse) changes nothing and throws: the old table stays.</li>
 * </ul>
 */
public final class Formats {

    /** One read of the files: every name at every level, and the bundled names a later level brought. */
    private record Table(Map<String, FormatSpec> all, Map<String, String> since) {

        /** The names a reader at {@code level} sees. */
        Map<String, FormatSpec> at(String level) {
            Map<String, FormatSpec> at = new LinkedHashMap<>(all);
            since.forEach((name, l) -> {
                if (com.ash.rachana.model.PanelKind.compareLevels(l, level) > 0) {
                    at.remove(name);
                }
            });
            return Ordered.copy(at);
        }
    }

    /** Where a table comes from, and the table now; shared by a table and its level views. */
    private static final class Source {
        final String overrideFile;
        final boolean live;
        volatile java.util.List<String> packFiles;
        volatile Table table;

        Source(String overrideFile, java.util.List<String> packFiles, boolean live) {
            this.overrideFile = overrideFile;
            this.packFiles = java.util.List.copyOf(packFiles);
            this.live = live;
            this.table = read(overrideFile, this.packFiles);
        }
    }

    /** The names at this view's level, of one table. */
    private record View(Table table, Map<String, FormatSpec> specs) {}

    private final Source source;
    private final String level;
    private volatile View view;

    private Formats(Source source, String level) {
        this.source = source;
        this.level = level;
        this.view = new View(source.table, source.table.at(level));
    }

    /** The names this reader sees now: the table's current read, at this view's level. */
    private Map<String, FormatSpec> specs() {
        View v = view;
        Table t = source.table;
        if (v.table() != t) {
            v = new View(t, t.at(level));
            view = v;
        }
        return v.specs();
    }

    /**
     * This table at another language level (SUTRA_EXPANSION 6.2): a bundled name of a later level ({@code since}, such as the 1.2
     * promotions {@code pct4}, {@code bp1}, {@code df4}, {@code price4}) is unknown below it and prints as text, as any unknown name. A
     * name a pack or the site file defines stays at every level. A table starts at level 1.0. The view of a live table is live too.
     */
    public Formats atLevel(String next) {
        return next.equals(level) ? this : new Formats(source, next);
    }

    /** The bundled formats, plus overrides from {@code overrideFile} when it exists. */
    public static Formats load(String overrideFile) {
        return load(overrideFile, java.util.List.of());
    }

    /** The bundled formats, then each pack's formats, then the site override file; the table never changes. */
    public static Formats load(String overrideFile, java.util.List<String> packFiles) {
        return new Formats(new Source(overrideFile, packFiles, false), "1.0");
    }

    /**
     * As {@link #load(String, java.util.List)}, a table its host reloads while readers hold it ({@link #reload()},
     * {@link #reload(java.util.List)}).
     */
    public static Formats live(String overrideFile, java.util.List<String> packFiles) {
        return new Formats(new Source(overrideFile, packFiles, true), "1.0");
    }

    /** True for a table its host may reload ({@link #live(String, java.util.List)}). */
    public boolean isLive() {
        return source.live;
    }

    /**
     * Reads the same files again (a pack changed its {@code formats.yaml}, the site file changed) and swaps the whole table.
     *
     * @throws IllegalStateException for a table that is not live
     * @throws UncheckedIOException when a file cannot be read (nothing changes)
     */
    public Formats reload() {
        return reload(source.packFiles);
    }

    /**
     * Reads the bundled table, these pack files (a pack started or stopped) and the site file, and swaps the whole table: every holder
     * sees the new names at its next read.
     *
     * @throws IllegalStateException for a table that is not live
     * @throws UncheckedIOException when a file cannot be read (nothing changes)
     */
    public Formats reload(java.util.List<String> packFiles) {
        if (!source.live) {
            throw new IllegalStateException("a loaded format table never changes: build it with Formats.live(...) to reload it");
        }
        java.util.List<String> files = java.util.List.copyOf(packFiles);
        Table next = read(source.overrideFile, files);           // throws before anything changes
        synchronized (source) {
            source.packFiles = files;
            source.table = next;
        }
        return this;
    }

    private static Table read(String overrideFile, java.util.List<String> packFiles) {
        Map<String, FormatSpec> m = new LinkedHashMap<>();
        Map<String, String> since = new LinkedHashMap<>();
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
        return new Table(Ordered.copy(m), Ordered.copy(since));
    }

    /** The language level that brought a name: a bundled name's {@code since} ({@code 1.0} when it has none, and for a pack's or the site's name), or null for a name the table does not hold. */
    public String since(String name) {
        Table t = source.table;
        return t.since().containsKey(name) ? t.since().get(name) : t.all().containsKey(name) ? "1.0" : null;
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
        return specs().containsKey(name);
    }

    public Set<String> names() {
        return specs().keySet();
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
        FormatSpec s = name == null ? null : specs().get(name);
        return s == null ? Values.text(value) : s.format(value);
    }
}
