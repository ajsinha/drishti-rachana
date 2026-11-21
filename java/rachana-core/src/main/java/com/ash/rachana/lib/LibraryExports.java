/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.lib;

import com.ash.rachana.parse.Json;
import com.ash.rachana.parse.PNode;
import com.ash.rachana.parse.PositionalYamlReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The {@code exports.fragments} of a library manifest (LIBRARIES.md 3.2): for each fragment, generated from its files and never typed by
 * hand, the name, the majors and the whole public interface of the newest major: its {@code doc}, {@code params} (with the columns a table
 * parameter asks for, listed again as {@code contract}), {@code slots}, {@code events}, {@code budget} and the language level it needs.
 * The gallery, the palette, the AI kit and the quality gates read this instead of opening a fragment. Each entry is one line of flow YAML
 * (JSON is YAML), so the manifest stays diff-friendly.
 */
public final class LibraryExports {

    private static final Pattern FRAGMENT = Pattern.compile("fragments/([a-z][a-z0-9-]{1,63})\\.v([0-9]+)\\.fragment\\.yaml");

    private LibraryExports() {
    }

    /** The interface of every fragment (name to entry map: name, majors, then the newest major's metadata), sorted by name. */
    public static Map<String, Map<String, Object>> entries(Map<String, byte[]> files) {
        Map<String, TreeMap<Integer, Map<String, Object>>> by = new TreeMap<>();
        for (Map.Entry<String, byte[]> f : files.entrySet()) {
            Matcher m = FRAGMENT.matcher(f.getKey());
            if (m.matches()) {
                by.computeIfAbsent(m.group(1), k -> new TreeMap<>()).put(Integer.parseInt(m.group(2)),
                        interfaceOf(new String(f.getValue(), StandardCharsets.UTF_8)));
            }
        }
        Map<String, Map<String, Object>> out = new TreeMap<>();
        by.forEach((name, majors) -> {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("name", name);
            e.put("majors", new ArrayList<Object>(majors.keySet().stream().map(Integer::longValue).toList()));
            e.putAll(majors.lastEntry().getValue());
            out.put(name, e);
        });
        return out;
    }

    /** The public interface of one fragment file; empty when the file does not read (the library check reports that). */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> interfaceOf(String text) {
        Map<String, Object> out = new LinkedHashMap<>();
        PNode root;
        try {
            root = new PositionalYamlReader().read(text.replace("%{", "pXq{"));
        } catch (RuntimeException e) {
            return out;
        }
        Object doc = plain(root.map().get("doc"));
        if (doc instanceof Map<?, ?> d) {
            if (d.get("category") != null) {
                out.put("category", d.get("category"));
            }
            if (d.get("summary") != null) {
                out.put("summary", d.get("summary"));
            }
            if (d.get("tags") != null) {
                out.put("tags", d.get("tags"));
            }
        }
        boolean typed = root.map().get("params") != null && root.map().get("params").isMap()
                && root.map().get("params").map().values().stream().allMatch(p -> p.isMap() && p.map().containsKey("type"));
        Map<String, Object> needs = new LinkedHashMap<>();
        needs.put("language", typed ? "1.2" : "1.0");
        out.put("needs", needs);
        Object params = plain(root.map().get("params"));
        if (typed && params instanceof Map<?, ?> pm) {
            out.put("params", params);
            List<Object> contract = new ArrayList<>();
            ((Map<String, Object>) pm).forEach((n, p) -> {
                if (p instanceof Map<?, ?> q && "table".equals(q.get("type"))) {
                    Map<String, Object> c = new LinkedHashMap<>();
                    c.put("param", n);
                    c.put("required", Boolean.TRUE.equals(q.get("required")));
                    c.put("columns", q.get("columns") == null ? Collections.emptyMap() : q.get("columns"));
                    contract.add(c);
                }
            });
            out.put("contract", contract);
        }
        for (String k : new String[] {"slots", "emits", "budget"}) {
            Object v = plain(root.map().get(k));
            if (v != null) {
                out.put("emits".equals(k) ? "events" : k, v);
            }
        }
        return out;
    }

    /** The entries as manifest lines ({@code - {...}}), one per fragment. */
    public static List<String> lines(Map<String, byte[]> files) {
        List<String> out = new ArrayList<>();
        entries(files).values().forEach(e -> out.add("- " + json(e, false)));
        return out;
    }

    /** The listed entries of a manifest compared with the regenerated ones: null when they agree, else what differs. */
    public static String stale(PNode exports, Map<String, byte[]> files) {
        PNode fs = exports == null ? null : exports.map().get("fragments");
        Map<String, Map<String, Object>> now = entries(files);
        for (PNode e : fs == null ? List.<PNode>of() : fs.list()) {
            Object listed = plain(e);
            if (!(listed instanceof Map<?, ?> lm) || lm.size() <= 2) {
                continue;                                   // a hand-trimmed entry with no metadata: names and majors are checked elsewhere
            }
            Map<String, Object> want = now.get(String.valueOf(lm.get("name")));
            if (want != null && !json(want, true).equals(json(listed, true))) {
                return "describe " + lm.get("name") + " out of date";
            }
        }
        return null;
    }

    public static Object plain(PNode n) {
        if (n == null) {
            return null;
        }
        if (n.isMap()) {
            Map<String, Object> m = new LinkedHashMap<>();
            n.map().forEach((k, v) -> m.put(k, plain(v)));
            return m;
        }
        if (n.isList()) {
            List<Object> l = new ArrayList<>();
            n.list().forEach(v -> l.add(plain(v)));
            return l;
        }
        Object v = n.value();
        if (v instanceof String s) {
            return s.replace("pXq{", "%{");
        }
        return v instanceof java.math.BigInteger b ? (Object) b.toString() : v;
    }

    /** JSON text of plain data; with {@code sorted} the keys are in order (for comparing). */
    public static String json(Object o, boolean sorted) {
        StringBuilder sb = new StringBuilder();
        write(o, sorted, sb);
        return sb.toString();
    }

    private static void write(Object o, boolean sorted, StringBuilder sb) {
        if (o == null) {
            sb.append("null");
        } else if (o instanceof Map<?, ?> m) {
            Map<?, ?> use = sorted ? new TreeMap<Object, Object>(m) : m;
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : use.entrySet()) {
                sb.append(first ? "" : ", ");
                first = false;
                Json.quote(String.valueOf(e.getKey()), sb);
                sb.append(": ");
                write(e.getValue(), sorted, sb);
            }
            sb.append('}');
        } else if (o instanceof List<?> l) {
            sb.append('[');
            for (int i = 0; i < l.size(); i++) {
                sb.append(i == 0 ? "" : ", ");
                write(l.get(i), sorted, sb);
            }
            sb.append(']');
        } else if (o instanceof Number || o instanceof Boolean) {
            sb.append(o);
        } else {
            Json.quote(String.valueOf(o), sb);
        }
    }
}
