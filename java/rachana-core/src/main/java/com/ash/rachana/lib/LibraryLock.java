/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.lib;

import com.ash.rachana.SutraException;
import com.ash.rachana.SutraProblem;
import com.ash.rachana.model.SourceLocation;
import com.ash.rachana.parse.PNode;
import com.ash.rachana.parse.PositionalYamlReader;
import com.ash.rachana.parse.YamlSyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A project's {@code rachana.lock} (LIBRARIES.md 9.3): the exact version and SHA-384 of every library its Sutras may use. A Sutra's
 * {@code libraries: [finance@1]} is a constraint; the lock is the choice. Written by {@code rachana lib add | update}, committed,
 * read by every compile. One version of a library per project; there is no "latest".
 *
 * <pre>
 * lock: 1
 * libraries:
 *   finance: { version: 1.4.2, sha384: "44a0…", source: "release:finance-1.4.2.rlib", signed-by: "ed25519:9f3c…", licence: LicenseRef-… }
 * </pre>
 */
public final class LibraryLock {

    /** The lock file's name. */
    public static final String FILE = "rachana.lock";

    /** One pinned library. {@code source}, {@code signedBy} and {@code licence} are recorded for people and tools; null when absent. */
    public record Entry(String name, String version, String sha384, String source, String signedBy, String licence) { }

    private final Map<String, Entry> entries;

    private LibraryLock(Map<String, Entry> entries) {
        this.entries = Collections.unmodifiableMap(new TreeMap<>(entries));
    }

    /** A lock of these entries. */
    public static LibraryLock of(List<Entry> entries) {
        Map<String, Entry> m = new TreeMap<>();
        entries.forEach(e -> m.put(e.name(), e));
        return new LibraryLock(m);
    }

    /**
     * A lock file's text, read.
     *
     * @throws SutraException ({@code RCH-2111}) when it is not a lock of this shape
     */
    public static LibraryLock read(String text) {
        PNode root;
        try {
            root = new PositionalYamlReader().read(text);
        } catch (YamlSyntaxException e) {
            throw bad("YAML syntax: " + e.getMessage(), e.line());
        }
        if (!root.isMap() || root.map().get("lock") == null || !Long.valueOf(1).equals(root.map().get("lock").value())) {
            throw bad("a lock file starts with lock: 1", 1);
        }
        Map<String, Entry> m = new TreeMap<>();
        PNode libs = root.map().get("libraries");
        for (Map.Entry<String, PNode> e : libs == null ? List.<Map.Entry<String, PNode>>of() : libs.map().entrySet()) {
            Map<String, PNode> d = e.getValue().map();
            String version = text(d.get("version"));
            String sha = text(d.get("sha384"));
            if (version == null || !version.matches("[0-9]+\\.[0-9]+\\.[0-9]+") || sha == null || !sha.matches("[0-9a-f]{96}")) {
                throw bad("the lock entry " + e.getKey() + " has a version (x.y.z) and a sha384 (96 hex digits)", e.getValue().line());
            }
            m.put(e.getKey(), new Entry(e.getKey(), version, sha, text(d.get("source")), text(d.get("signed-by")), text(d.get("licence"))));
        }
        return new LibraryLock(m);
    }

    /** The pinned entry of a library, or null. */
    public Entry get(String name) {
        return entries.get(name);
    }

    /** Every entry, by name. */
    public Map<String, Entry> entries() {
        return entries;
    }

    /** The lock's text: the same entries always give the same bytes. */
    public String write() {
        StringBuilder sb = new StringBuilder("# rachana.lock: written by `rachana lib add | update`; do not edit\nlock: 1\nlibraries:");
        if (entries.isEmpty()) {
            sb.append(" {}");
        }
        sb.append('\n');
        for (Entry e : entries.values()) {
            sb.append("  ").append(e.name()).append(": { version: ").append(e.version()).append(", sha384: \"").append(e.sha384()).append('"');
            if (e.source() != null) {
                sb.append(", source: \"").append(e.source()).append('"');
            }
            if (e.signedBy() != null) {
                sb.append(", signed-by: \"").append(e.signedBy()).append('"');
            }
            if (e.licence() != null) {
                sb.append(", licence: ").append(e.licence());
            }
            sb.append(" }\n");
        }
        return sb.toString();
    }

    private static String text(PNode n) {
        return n == null ? null : n.text();
    }

    private static SutraException bad(String message, int line) {
        List<SutraProblem> p = new ArrayList<>();
        p.add(new SutraProblem("RCH-2111", message, new SourceLocation(FILE, Math.max(1, line), 1)));
        return new SutraException(p);
    }
}
