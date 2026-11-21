/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.tools;

import com.ash.rachana.RachanaSchema;
import com.ash.rachana.el.ElLimits;
import com.ash.rachana.el.Functions;
import com.ash.rachana.format.Formats;
import com.ash.rachana.format.Tones;
import com.ash.rachana.model.ControlKind;
import com.ash.rachana.model.ControlRules;
import com.ash.rachana.model.Panel;
import com.ash.rachana.model.PanelKind;
import com.ash.rachana.model.Refresh;
import com.ash.rachana.model.Wiring;
import com.ash.rachana.parse.KeyTables;
import com.ash.rachana.parse.PositionalYamlReader;
import com.ash.rachana.profile.Grammar;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import com.ash.rachana.model.Ordered;
import java.util.LinkedHashSet;

/**
 * The two definition files of a language level, written from Rachana's own tables: {@code rachana.lang.json} (every key, panel kind,
 * panel option, control kind, function, format, tone, problem code and limit, each with its profile and the level that brought it) and
 * {@code sutra.schema.json} (the JSON Schema of a Core Sutra). Nothing is written by hand twice: the key sets are the parser's, the
 * kinds are {@link PanelKind}, the functions are {@link Functions} and {@code functions.yaml}, the codes are {@code language-tables.yaml},
 * the schema is {@link RachanaSchema} at that level. A name of a later level is left out of an earlier level's files; the files of level
 * 1.0 are the bytes {@code compat/levels/1.0} holds. Output is deterministic.
 *
 * <p>Run: {@code java -cp <rachana-tools and rachana-core jars> com.ash.rachana.tools.LangFiles <level> <out dir>}. The release that
 * writes a level ({@code tools/regime/levels.py write}) calls it.
 */
public final class LangFiles {

    static final String SPEC_COMMENT = "Copyright (c) 2026 Ashutosh Sinha. Specification licence: ../../LICENSE-SPEC.md (draft, not yet in force). ";
    static final String CODES_RESOURCE = "language-tables.yaml";

    /** The Drishti profile's names (CORE_AND_PROFILES section 2): its keys, its panel kinds, its source form and the form's save. */
    private static final Set<String> PROFILE_KEYS = Ordered.set("form.save", "sutra.match", "sutra.domain", "source.kind", "source.id", "source.search", "source.pack",
            "source.params", "source.as-of", "source.model");
    /** The format names Drishti's core pack adds to Core's (spec/1/formats.yaml header); from level 1.2 Core bundles pct4, bp1, df4 and price4 too. */
    private static final Set<String> PROFILE_FORMATS = Ordered.set("pct4", "rate5", "pips1", "df4", "bp1", "price4");

    private final String level;

    private LangFiles(String level) {
        if (!Grammar.LEVELS.contains(level)) {
            throw new IllegalArgumentException("language level '" + level + "' is not one of " + String.join(", ", Grammar.LEVELS));
        }
        this.level = level;
    }

    /** The two files of a level, by file name: {@code rachana.lang.json} and {@code sutra.schema.json}. */
    public static Map<String, String> generate(String level) {
        LangFiles g = new LangFiles(level);
        Map<String, String> out = new LinkedHashMap<>();
        out.put("rachana.lang.json", LangJson.write(g.lang()));
        out.put("sutra.schema.json", g.released(LangJson.write(g.schema())));
        return out;
    }

    /** The schema's wording as released with the level, where the compiler's text has changed since ({@code schemaText} of the table). */
    private String released(String json) {
        String out = json;
        for (Object p : elements(path(path(codeTable(), "schemaText"), level))) {
            out = out.replace(text(path(p, "from")), text(path(p, "to")));
        }
        return out;
    }

    /** {@code <level> <out dir>}: writes both files to the folder. */
    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("usage: LangFiles <level> <out dir>");
            System.exit(2);
        }
        Path dir = Path.of(args[1]);
        Files.createDirectories(dir);
        for (Map.Entry<String, String> f : generate(args[0]).entrySet()) {
            Files.writeString(dir.resolve(f.getKey()), f.getValue(), StandardCharsets.UTF_8);
        }
    }

    // ---- the language file -----------------------------------------------------------------------------------------

    private Map<String, Object> lang() {
        Map<String, Object> lang = new LinkedHashMap<>();
        lang.put("$comment", comment("lang", "Generated from the compiler's own tables by rachana-tools LangFiles (plan R0 H4, decision D9). "
                + "Do not edit: tools/regime/levels.py writes it with each language level. Every entry has its profile and since."));
        lang.put("rachana", "1");
        lang.put("language", level);
        lang.put("reference", reference());
        lang.put("keys", keys());
        lang.put("panelKinds", panelKinds());
        lang.put("panelOptions", panelOptions());
        lang.put("controlKinds", controlKinds());
        lang.put("functions", functions());
        List<Object> formats = new ArrayList<>();
        Formats core = Formats.defaults().atLevel(level);                // a promoted name (compat/promotions.yaml) is Core's from its level
        new TreeSet<>(core.names()).forEach(f -> formats.add(entry(f, "core", core.since(f))));
        PROFILE_FORMATS.stream().filter(f -> !core.has(f)).sorted().forEach(f -> formats.add(entry(f, "drishti", "1.0")));
        lang.put("formats", formats);
        List<Object> tones = new ArrayList<>();
        Tones.NAMES.forEach(t -> tones.add(entry(t, "core", "1.0")));
        lang.put("tones", tones);
        lang.put("codes", codes());
        lang.put("limits", limits());
        return lang;
    }

    /** The files' {@code $comment}: as released with the level when the table holds it, else the specification's licence line and this text. */
    private String comment(String file, String text) {
        String recorded = text(path(path(path(codeTable(), "comments"), level), file), "");
        return recorded.isEmpty() ? SPEC_COMMENT + text : recorded;
    }

    /** What the level's files were made from: the reference compiler's release for 1.0, as recorded in the code table; Rachana's own after. */
    private String reference() {
        Object ref = path(codeTable(), "reference");
        String recorded = text(path(ref, level), "");
        return recorded.isEmpty() ? "Drishti Rachana language " + level : recorded;
    }

    /** Every key a parser reads, by the place it is read, each with its profile and the level that brought it. */
    private Map<String, Object> keys() {
        Map<String, Set<String>> places = new LinkedHashMap<>();
        places.put("sutra", new TreeSet<>(Grammar.CORE_KEYS));
        Grammar.LEVEL_KEYS.forEach((k, since) -> { if (atLeast(level, since)) { places.get("sutra").add(k); } });
        places.get("sutra").addAll(List.of("match", "domain"));
        places.putAll(KeyTables.byPlace());
        Map<String, Object> out = new LinkedHashMap<>();
        places.forEach((place, names) -> {
            List<Object> l = new ArrayList<>();
            for (String n : new TreeSet<>(names)) {
                boolean profile = PROFILE_KEYS.contains(place + "." + n) || place.equals("form.save");
                String since = place.equals("sutra") && Grammar.LEVEL_KEYS.containsKey(n) ? Grammar.LEVEL_KEYS.get(n) : "1.0";
                l.add(entry(n, profile ? "drishti" : "core", since));
            }
            out.put(place, l);
        });
        return out;
    }

    private List<Object> panelKinds() {
        List<Object> out = new ArrayList<>();
        for (PanelKind k : PanelKind.values()) {
            if (!k.at(level)) { continue; }
            Map<String, Object> e = entry(k.id(), Grammar.PROFILE_KINDS.contains(k) ? "drishti" : "core", k.since());
            e.put("required", new TreeSet<>(k.required()));
            e.put("optional", new TreeSet<>(k.optional(level)));
            out.add(e);
        }
        out.sort(Comparator.comparing(m -> (String) ((Map<?, ?>) m).get("name")));
        return out;
    }

    /** Every panel option with the kinds that accept it (where, when, on-select and source included). */
    private List<Object> panelOptions() {
        List<PanelKind> kinds = new ArrayList<>();
        for (PanelKind k : PanelKind.values()) { if (k.at(level)) { kinds.add(k); } }
        TreeSet<String> names = new TreeSet<>(List.of("where", "when", "on-select", "source"));
        for (PanelKind k : kinds) {
            names.addAll(k.required());
            names.addAll(k.optional(level));
        }
        List<Object> out = new ArrayList<>();
        for (String n : names) {
            TreeSet<String> by = new TreeSet<>();
            for (PanelKind k : kinds) { if (k.accepts(n) && atLeast(level, k.since(n))) { by.add(k.id()); } }
            Map<String, Object> e = entry(n, "core", "1.0");
            e.put("kinds", by);
            out.add(e);
        }
        return out;
    }

    private List<Object> controlKinds() {
        List<Object> out = new ArrayList<>();
        for (ControlKind k : ControlKind.values()) { out.add(entry(k.id(), "core", "1.0")); }
        out.sort(Comparator.comparing(m -> (String) ((Map<?, ?>) m).get("name")));
        return out;
    }

    /** The function table: arity, family, Core or profile, and the level that brought it (the catalogue's {@code since}). */
    private List<Object> functions() {
        Map<String, String> since = Functions.sinceLevels();
        Map<String, Object> profile = new TreeMap<>();
        for (Object f : elements(path(codeTable(), "profileFunctions"))) { profile.put(text(path(f, "name")), f); }
        Map<String, int[]> arity = Functions.arity();
        Map<String, String> old = Functions.oldSpellings();
        Map<String, Functions.Entry> cat = new TreeMap<>();
        for (Functions.Entry e : Functions.catalogue()) { cat.put(e.name(), e); }
        List<Object> out = new ArrayList<>();
        for (String name : new TreeSet<>(arity.keySet())) {
            Object p = profile.get(name);
            if (p != null) {
                if (!atLeast(level, text(path(p, "since"), "1.0"))) { continue; }
                Map<String, Object> e = entry(name, "drishti", text(path(p, "since"), "1.0"));
                e.put("family", path(p, "family"));
                e.put("min", path(p, "min"));
                e.put("max", path(p, "max"));
                if (path(p, "oldSpellingOf") != null) { e.put("oldSpellingOf", path(p, "oldSpellingOf")); }
                out.add(e);
                continue;
            }
            String now = old.get(name);
            String one = now == null ? name : now;
            String from = since.getOrDefault(one, "1.0");
            if (!atLeast(level, from)) { continue; }
            Functions.Entry c = cat.get(one);
            Map<String, Object> e = entry(name, "core", from);
            e.put("family", c == null || c.family().isEmpty() ? null : c.family());
            e.put("min", arity.get(name)[0]);
            e.put("max", arity.get(name)[1] == Integer.MAX_VALUE ? null : arity.get(name)[1]);
            if (now != null) { e.put("oldSpellingOf", now); }
            out.add(e);
        }
        return out;
    }

    private static Object codeTable() {
        try (InputStream in = LangFiles.class.getClassLoader().getResourceAsStream(CODES_RESOURCE)) {
            if (in == null) { throw new IllegalStateException(CODES_RESOURCE + " is missing from the class path"); }
            return yaml(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The problem codes of the table whose level is at most this one, each with the classes that raise it. */
    private List<Object> codes() {
        List<Object> out = new ArrayList<>();
        for (Object c : elements(path(codeTable(), "codes"))) {
            String since = text(path(c, "since"), "1.0");
            if (!atLeast(level, since)) { continue; }
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("code", text(path(c, "code")));
            e.put("profile", text(path(c, "profile")));
            e.put("since", since);
            List<String> by = new ArrayList<>();
            elements(path(c, "raisedBy")).forEach(x -> by.add(text(x)));
            e.put("raisedBy", new TreeSet<>(by));
            out.add(e);
        }
        out.sort(Comparator.comparing(m -> (String) ((Map<?, ?>) m).get("code")));
        return out;
    }

    private static Map<String, Object> limits() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("expressionDepth", ElLimits.DEFAULTS.maxDepth());
        m.put("expressionLength", ElLimits.DEFAULTS.maxLength());
        m.put("controls", ControlRules.MAX_CONTROLS);
        m.put("refreshSeconds", Refresh.MAX);
        m.put("panelSpan", Panel.MAX_SPAN);
        m.put("panelHeight", Panel.MAX_HEIGHT);
        m.put("sourceLimit", Wiring.Source.MAX_LIMIT);
        return m;
    }

    // ---- the Core JSON Schema --------------------------------------------------------------------------------------

    /** {@link RachanaSchema} over Core's grammar at the level and Core's formats, with the specification's address and the level's functions. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> schema() {
        Set<String> coreFormats = new TreeSet<>(Formats.defaults().atLevel(level).names());
        Map<String, Object> m = (Map<String, Object>) strip(new LinkedHashMap<>(RachanaSchema.build(coreFormats, Grammar.core().atLevel(level))));
        m.put("$id", "https://ajsinha.github.io/drishti-rachana/spec/1/sutra.schema.json");
        m.put("title", "Rachana Sutra, language 1 (Core)");
        m.put("$comment", comment("schema", "Generated by rachana-tools LangFiles from RachanaSchema at the level (plan R0 H4). Do not edit."));
        Map<String, Object> fns = new LinkedHashMap<>((Map<String, Object>) m.get("x-rachana-functions"));
        Set<String> keep = new LinkedHashSet<>();
        for (Object f : functions()) {
            Map<String, Object> e = (Map<String, Object>) f;
            if ("core".equals(e.get("profile"))) { keep.add((String) e.get("name")); }
        }
        fns.keySet().removeIf(k -> !keep.contains(k));
        m.put("x-rachana-functions", fns);
        return m;
    }

    /** A deep copy with the Drishti profile's names removed: a source's entity forms, a form's save, a children group's entity source, the profile's kinds. */
    @SuppressWarnings("unchecked")
    private static Object strip(Object v) {
        if (v instanceof Map<?, ?> in) {
            Map<String, Object> m = new LinkedHashMap<>();
            in.forEach((k, x) -> m.put((String) k, strip(x)));
            if (m.get("properties") instanceof Map<?, ?> props) {
                Map<String, Object> p = (Map<String, Object>) props;
                if (p.containsKey("table") && p.containsKey("kind")) {
                    p.keySet().removeAll(Ordered.set("kind", "id", "search", "pack", "params", "as-of", "model"));
                    m.remove("anyOf");
                    m.put("required", List.of("table"));
                }
                if (p.containsKey("save") && p.containsKey("fields")) {
                    p.remove("save");
                }
                if (p.containsKey("place") && p.containsKey("panels")) {
                    p.remove("source");
                }
            }
            if (m.get("required") instanceof List<?> r) {
                m.put("required", r.stream().map(String::valueOf).sorted().toList());
            }
            if (m.get("enum") instanceof List<?> l && l.containsAll(List.of("links", "provenance")) && l.contains("table")) {
                m.put("enum", l.stream().filter(x -> !List.of("links", "provenance").contains(x)).toList());
            }
            if (m.get("allOf") instanceof List<?> l) {
                m.put("allOf", l.stream().filter(x -> !(x instanceof Map<?, ?> c && c.get("if") instanceof Map<?, ?> i
                        && i.get("properties") instanceof Map<?, ?> ip && ip.get("kind") instanceof Map<?, ?> kk
                        && List.of("links", "provenance").contains(kk.get("const")))).toList());
            }
            return m;
        }
        if (v instanceof Collection<?> c) {
            List<Object> l = new ArrayList<>();
            c.forEach(x -> l.add(strip(x)));
            return l;
        }
        return v;
    }

    // ---- helpers ---------------------------------------------------------------------------------------------------

    private static boolean atLeast(String have, String need) {
        return PanelKind.compareLevels(need, have) <= 0;
    }

    private static Map<String, Object> entry(String name, String profile, String since) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("name", name);
        e.put("profile", profile);
        e.put("since", since);
        return e;
    }

    // ---- plain YAML values (the parser's own reader) ---------------------------------------------------------------

    private static Object yaml(InputStream in) throws IOException {
        return new PositionalYamlReader().read(new String(in.readAllBytes(), StandardCharsets.UTF_8)).plain();
    }

    private static Object path(Object node, String key) {
        return node instanceof Map<?, ?> m ? m.get(key) : null;
    }

    private static Collection<?> elements(Object node) {
        return node instanceof Collection<?> c ? c : List.of();
    }

    private static String text(Object v) {
        return text(v, "");
    }

    private static String text(Object v, String otherwise) {
        return v == null ? otherwise : String.valueOf(v);
    }
}
