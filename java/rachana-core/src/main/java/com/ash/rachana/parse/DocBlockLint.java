/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.parse;

import com.ash.rachana.SutraProblem;
import com.ash.rachana.model.Ordered;
import com.ash.rachana.model.SourceLocation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The doc block lint, {@code RCH-2093} (SPEC 2.9): a {@code doc:} block missing or too thin for a catalogue, a component browser or an AI
 * agent to choose and use a Sutra or a fragment from it alone. Every problem is located (the node's line and column) and carries the JSON
 * Pointer of the field ({@code /doc/use-when/0}).
 *
 * <pre>
 * doc:
 *   summary: One line, four words or more, at most 120 characters.
 *   description: What it shows and how (40 characters or more).
 *   category: limits
 *   tags: [limit, gauge]                                   # optional
 *   use-when: [ "One limit deserves its own panel ..." ]   # sentences of four words or more
 *   avoid-when: [ "Many limits at once: use finance/limit-table." ]
 *   data: { table: history, in-words: "...", sample-row: { date: "2026-11-20", used: 1850000 } }
 *   example: { use: finance/limit-gauge@1, with: { used: "$.lim.USED", limit: "$.lim.LIMIT" } }
 *   see-also: [finance/limit-table, finance/limit-band]
 *   keywords: [limit, utilisation, headroom]               # three or more
 * </pre>
 *
 * A fragment (typed or a text-form block) needs every field; its sample row has every required column of the parameter {@code data.table}
 * names, and its example is a use of it that gives every required parameter and only declared ones. A Sutra (a screen) needs summary,
 * description, category, use-when, avoid-when and keywords; data, example and see-also are checked when written.
 *
 * <p>Not part of the compile (a compile's warnings are recorded by the conformance cases): hosts run it where docs matter, the library gate
 * LG3 fails a library on any of its problems, and the GenUI kit (tools/sajha) applies the same rule
 * in Python ({@code rachana_genui/doclint.py}) until the bundle exports this class. The rules, messages and paths of the two are the same.
 * Stateless and thread-safe.
 */
public final class DocBlockLint {

    /** A doc block missing or too thin. */
    public static final String CODE = "RCH-2093";
    /** The keys a doc block takes: the GenUI fields, then the docstring keys that came before them ({@link SutraDocstring}). */
    public static final Set<String> KEYS = Ordered.set("summary", "description", "category", "tags", "use-when", "avoid-when", "data", "example",
            "see-also", "keywords", "intents", "params", "emits", "expects", "examples", "since", "uses");
    /** The keys of {@code doc.data}. */
    public static final Set<String> DATA_KEYS = Ordered.set("table", "in-words", "sample-row");
    public static final int SUMMARY_MAX = 120;
    public static final int MIN_WORDS = 4;
    public static final int MIN_DESCRIPTION = 40;
    public static final int MIN_KEYWORDS = 3;
    private static final Pattern REF = Pattern.compile("^([a-z][a-z0-9-]{0,63}/)?[a-z][a-z0-9-]{1,63}(@[0-9]+)?$");

    /** What a doc block belongs to. */
    public enum Kind { FRAGMENT, SUTRA }

    private DocBlockLint() {}

    /** The problems of one file's text, its kind found from the text (a {@code fragment:} or {@code block:} key: a fragment). */
    public static List<SutraProblem> check(String text, String file, String severity) {
        PNode root;
        try {
            root = new PositionalYamlReader().read(text.replace("%{", "pXq{"));
        } catch (RuntimeException e) {
            return List.of();                                             // the compiler reports the text (RCH-2001)
        }
        if (root == null || !root.isMap()) {
            return List.of();
        }
        Kind kind = root.map().containsKey("fragment") || root.map().containsKey("block") ? Kind.FRAGMENT : Kind.SUTRA;
        return check(root, kind, file, severity);
    }

    /** The problems of a parsed file. */
    public static List<SutraProblem> check(PNode root, Kind kind, String file, String severity) {
        Map<String, PNode> m = root.map();
        String name = firstText(m.get("fragment"), m.get("sutra"), m.get("block"));
        Ctx c = new Ctx((kind == Kind.FRAGMENT ? "fragment " : "sutra ") + (name == null ? file : name), file, severity);
        PNode doc = m.get("doc");
        if (doc == null || !doc.isMap()) {
            c.add(doc == null ? root : doc, "/doc", "has no doc: block (summary, description, category, use-when, avoid-when, data, example, "
                    + "see-also, keywords)");
            return c.out;
        }
        Map<String, PNode> d = doc.map();
        d.forEach((k, v) -> {
            if (!KEYS.contains(k)) {
                c.add(v, "/doc/" + k, "doc." + k + " is not a doc key (one of " + String.join(", ", KEYS) + ")");
            }
        });
        String summary = text(d.get("summary"));
        if (summary == null || summary.strip().contains("\n") || summary.length() > SUMMARY_MAX || words(summary) < MIN_WORDS) {
            c.add(at(d.get("summary"), doc), "/doc/summary", "doc.summary is one line of " + MIN_WORDS + " words or more and at most " + SUMMARY_MAX
                    + " characters, saying what it shows");
        }
        String description = text(d.get("description"));
        if (description == null || description.strip().length() < MIN_DESCRIPTION) {
            c.add(at(d.get("description"), doc), "/doc/description", "doc.description says what it shows and how, in " + MIN_DESCRIPTION
                    + " characters or more");
        }
        if (blank(text(d.get("category")))) {
            c.add(at(d.get("category"), doc), "/doc/category", "doc.category names the group it is listed under");
        }
        for (String key : new String[] {"use-when", "avoid-when"}) {
            PNode list = d.get(key);
            if (list == null || !list.isList() || list.list().isEmpty()) {
                c.add(at(list, doc), "/doc/" + key, "doc." + key + " is a list of one sentence or more"
                        + ("avoid-when".equals(key) ? " (each naming what to use instead)" : ""));
                continue;
            }
            for (int i = 0; i < list.list().size(); i++) {
                if (words(text(list.list().get(i))) < MIN_WORDS) {
                    c.add(list.list().get(i), "/doc/" + key + "/" + i, "doc." + key + "[" + i + "] is a sentence of " + MIN_WORDS + " words or more");
                }
            }
        }
        PNode kw = d.get("keywords");
        long keywords = kw != null && kw.isList() ? kw.list().stream().filter(n -> !blank(text(n))).count() : 0;
        if (keywords < MIN_KEYWORDS) {
            c.add(at(kw, doc), "/doc/keywords", "doc.keywords lists " + MIN_KEYWORDS + " search words or more");
        }
        PNode tags = d.get("tags");
        if (tags != null && !tags.isList()) {
            c.add(tags, "/doc/tags", "doc.tags is a list of words");
        }
        boolean strict = kind == Kind.FRAGMENT;
        Map<String, PNode> params = strict && m.get("params") != null ? m.get("params").map() : Map.of();
        data(c, doc, strict, params);
        example(c, doc, strict, name, params);
        PNode see = d.get("see-also");
        if (see == null && strict) {
            c.add(doc, "/doc/see-also", "doc.see-also lists the related fragments (library/name), one or more");
        } else if (see != null && (!see.isList() || strict && see.list().isEmpty())) {
            c.add(see, "/doc/see-also", "doc.see-also is a list of fragment names (library/name)");
        } else if (see != null) {
            for (int i = 0; i < see.list().size(); i++) {
                String s = text(see.list().get(i));
                if (s == null || !REF.matcher(s).matches()) {
                    c.add(see.list().get(i), "/doc/see-also/" + i, "doc.see-also[" + i + "] '" + s + "' is a fragment name (library/name or "
                            + "library/name@major)");
                }
            }
        }
        return c.out;
    }

    /** The table or record parameters, each with its required columns. */
    private static Map<String, List<String>> shaped(Map<String, PNode> params) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        params.forEach((n, p) -> {
            String type = text(p.map().get("type"));
            if ("table".equals(type)) {
                List<String> req = new ArrayList<>();
                PNode cols = p.map().get("columns");
                if (cols != null) {
                    cols.map().forEach((cn, cd) -> {
                        if (!Boolean.TRUE.equals(cd.map().containsKey("optional") ? cd.map().get("optional").value() : null)) {
                            req.add(cn);
                        }
                    });
                }
                out.put(n, req);
            } else if (type != null && type.startsWith("record")) {
                out.put(n, List.of());
            }
        });
        return out;
    }

    private static void data(Ctx c, PNode doc, boolean strict, Map<String, PNode> params) {
        PNode data = doc.map().get("data");
        if (data == null) {
            if (strict) {
                c.add(doc, "/doc/data", "doc.data says in words what data it takes (in-words) and gives one sample-row");
            }
            return;
        }
        if (!data.isMap()) {
            c.add(data, "/doc/data", "doc.data is a mapping: table, in-words, sample-row");
            return;
        }
        data.map().forEach((k, v) -> {
            if (!DATA_KEYS.contains(k)) {
                c.add(v, "/doc/data/" + k, "doc.data." + k + " is not a data key (one of " + String.join(", ", DATA_KEYS) + ")");
            }
        });
        if (words(text(data.map().get("in-words"))) < MIN_WORDS) {
            c.add(at(data.map().get("in-words"), data), "/doc/data/in-words", "doc.data.in-words says in " + MIN_WORDS
                    + " words or more what the rows are (one row per what) and which columns they carry");
        }
        PNode sample = data.map().get("sample-row");
        if (sample == null || !sample.isMap() || sample.map().isEmpty()) {
            c.add(at(sample, data), "/doc/data/sample-row", "doc.data.sample-row is one row: a mapping of column to a synthetic value");
            return;
        }
        Map<String, List<String>> shaped = strict ? shaped(params) : Map.of();
        String table = text(data.map().get("table"));
        if (table == null) {
            if (strict && !shaped.isEmpty()) {
                c.add(data, "/doc/data/table", "doc.data.table names the parameter the sample row is of (one of " + String.join(", ", shaped.keySet())
                        + ")");
            }
            return;
        }
        if (strict && !shaped.containsKey(table)) {
            c.add(data.map().get("table"), "/doc/data/table", "doc.data.table '" + table + "' is not a table or record parameter"
                    + (shaped.isEmpty() ? "" : " (one of " + String.join(", ", shaped.keySet()) + ")"));
            return;
        }
        List<String> missing = new ArrayList<>();
        for (String col : shaped.getOrDefault(table, List.of())) {
            if (!sample.map().containsKey(col)) {
                missing.add(col);
            }
        }
        if (!missing.isEmpty()) {
            c.add(sample, "/doc/data/sample-row", "doc.data.sample-row has every required column of " + table + ": missing " + String.join(", ", missing));
        }
    }

    private static void example(Ctx c, PNode doc, boolean strict, String name, Map<String, PNode> params) {
        PNode ex = doc.map().get("example");
        if (ex == null) {
            if (strict) {
                c.add(doc, "/doc/example", "doc.example is a minimal use of it: { use: library/name@major, with: {...} }");
            }
            return;
        }
        if (!ex.isMap()) {
            c.add(ex, "/doc/example", "doc.example is a mapping: { use: library/name@major, with: {...} }");
            return;
        }
        String use = text(ex.map().get("use"));
        String used = use == null ? "" : use.substring(use.lastIndexOf('/') + 1).split("@")[0];
        if (strict && !used.equals(name)) {
            c.add(at(ex.map().get("use"), ex), "/doc/example/use", "doc.example.use is a use of " + name + " (library/" + name + "@major)");
        }
        PNode with = ex.map().get("with");
        if (with != null && !with.isMap()) {
            c.add(with, "/doc/example/with", "doc.example.with is a mapping of parameter to value");
        }
        if (!strict) {
            return;
        }
        Map<String, PNode> given = with == null ? Map.of() : with.map();
        new java.util.TreeSet<>(given.keySet()).forEach(k -> {
            if (!params.containsKey(k)) {
                c.add(with, "/doc/example/with/" + k, "doc.example sets '" + k + "', which is not a parameter");
            }
        });
        new java.util.TreeSet<>(params.keySet()).forEach(k -> {
            PNode req = params.get(k).map().get("required");
            if (req != null && Boolean.TRUE.equals(req.value()) && !given.containsKey(k)) {
                c.add(with == null ? ex : with, "/doc/example/with", "doc.example gives the required parameter '" + k + "'");
            }
        });
    }

    private static final class Ctx {
        final String what;
        final String file;
        final String severity;
        final List<SutraProblem> out = new ArrayList<>();

        Ctx(String what, String file, String severity) {
            this.what = what;
            this.file = file;
            this.severity = severity == null ? SutraProblem.WARNING : severity;
        }

        void add(PNode at, String path, String message) {
            out.add(new SutraProblem(CODE, severity, what + ": " + message,
                    new SourceLocation(file, at == null ? 1 : at.line(), at == null ? 1 : at.column()), path, null));
        }
    }

    private static PNode at(PNode n, PNode parent) {
        return n != null ? n : parent;
    }

    private static String firstText(PNode... nodes) {
        for (PNode n : nodes) {
            if (text(n) != null) {
                return text(n);
            }
        }
        return null;
    }

    private static String text(PNode n) {
        return n == null || n.text() == null ? null : n.text().replace("pXq{", "%{");
    }

    private static int words(String s) {
        return s == null || s.isBlank() ? 0 : s.strip().split("\\s+").length;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
