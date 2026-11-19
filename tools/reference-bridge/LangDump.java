// Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
// Drishti Rachana. Proprietary: see LICENSE.
//
// Plan R0 H4 (SPLIT_PLAN 0c): spec/1/rachana.lang.json and spec/1/sutra.schema.json generated from the reference compiler's own tables
// (decision D9: the tables are the truth for `rachana: 1`; nothing is written by hand twice). Read-only over Drishti's released jars:
// the parser's key sets, PanelKind, ControlKind, the function table, the bundled format table, Tones, the limits and RachanaSchema are
// read by reflection; the problem codes are read from the constant pools of the compiled classes (the reference keeps them as literals
// in the class that raises them, not in one table). Retired at A10, when the generator moves into rachana-tools beside the tables.
//
// Run:  java -cp <Drishti i7a jars> tools/reference-bridge/LangDump.java <out dir> <Drishti core pack formats.yaml>
// (tools/conformance/spec_files.py does it, and checks the committed files are not stale)
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LangDump {
    static final String P = "com.ash.drishti.rachana.";
    static final String SINCE = "1.0";
    /** The Drishti profile's names (CORE_AND_PROFILES section 2): its keys, its panel kinds, its source form and the form's save. */
    static final Set<String> PROFILE_KEYS = Set.of("sutra.match", "sutra.domain", "source.kind", "source.id", "source.search", "source.pack",
            "source.params", "source.as-of", "source.model", "form.save");
    static final Set<String> PROFILE_KINDS = Set.of("links", "provenance");
    /** SPEC 5.7: the list aggregates are Core (the reference computes them in its engine today; the profile keeps the model functions). */
    static final Set<String> CORE_AGGREGATES = Set.of("sum", "count", "avg", "min", "max", "median", "percentile", "distinct", "wavg", "variance",
            "stddev", "variance_pop", "stddev_pop", "group");
    static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    public static void main(String[] a) throws Exception {
        Path out = Path.of(a[0]);
        Files.createDirectories(out);
        Set<String> coreFormats = new TreeSet<>(names(call("format.Formats", "load", new Class<?>[] {String.class}, (Object) null)));
        Set<String> packFormats = new TreeSet<>(names(call("format.Formats", "load", new Class<?>[] {String.class, List.class}, null, List.of(a[1]))));
        packFormats.removeAll(coreFormats);
        Map<String, Object> lang = new LinkedHashMap<>();
        lang.put("$comment", "Copyright (c) 2026 Ashutosh Sinha. Specification licence: ../../LICENSE-SPEC.md (draft, not yet in force). Generated from the reference compiler's tables by tools/reference-bridge/LangDump.java (plan R0 H4, decision D9). Do not edit: "
                + "tools/conformance/spec_files.py regenerates it and fails when the committed copy is stale. Every entry has its profile and since.");
        lang.put("rachana", "1");
        lang.put("language", SINCE);
        lang.put("reference", "Drishti " + implVersion());
        lang.put("keys", keys());
        lang.put("panelKinds", panelKinds());
        lang.put("panelOptions", panelOptions());
        lang.put("controlKinds", controlKinds());
        lang.put("functions", functions());
        List<Object> formats = new ArrayList<>();
        coreFormats.forEach(f -> formats.add(entry(f, "core")));
        packFormats.forEach(f -> formats.add(entry(f, "drishti")));
        lang.put("formats", formats);
        List<Object> tones = new ArrayList<>();
        for (Object t : (List<?>) field("format.Tones", "NAMES")) { tones.add(entry((String) t, "core")); }
        lang.put("tones", tones);
        lang.put("codes", codes());
        lang.put("limits", limits());
        write(out.resolve("rachana.lang.json"), lang);
        write(out.resolve("sutra.schema.json"), coreSchema(coreFormats));
    }

    // ---- keys ------------------------------------------------------------------------------------------------------

    /** Every key the parser reads, by the place it is read (the parser's own sets), each with its profile. */
    static Map<String, Object> keys() throws Exception {
        Map<String, Set<String>> places = new LinkedHashMap<>();
        places.put("sutra", set("parse.SutraBuilder", "TOP"));
        places.put("panel", set("parse.SutraBuilder", "PANEL"));
        places.put("strip", set("parse.SutraBuilder", "STRIP"));
        places.put("column", set("parse.SutraBuilder", "COLUMN"));
        places.put("control", set("parse.ControlsReader", "KEYS"));
        places.put("layout", set("parse.LayoutReader", "KEYS"));
        places.put("phone", set("parse.PhoneReader", "SCREEN_KEYS"));
        places.put("panel.phone", set("parse.PhoneReader", "PANEL_KEYS"));
        places.put("source", set("parse.WiringReader", "SOURCE_KEYS"));
        places.put("emit", set("parse.WiringReader", "EMIT_KEYS"));
        places.put("children", set("parse.WiringReader", "GROUP_KEYS"));
        places.put("form", set("model.Form", "TOP"));
        places.put("form.save", set("model.Form", "SAVE"));
        places.put("form.field", set("model.Form", "FIELD"));
        places.put("form.lookup", set("model.Form", "LOOKUP"));
        places.put("form.lookup.source", set("model.Form", "SOURCE"));
        places.put("form.rule", set("model.Form", "RULE"));
        places.put("form.hint", set("model.Form", "HINT"));
        places.put("level", set("model.PanelOptions", "LEVEL_KEYS"));
        places.put("marker", set("model.PanelOptions", "MARKER_KEYS"));
        Map<String, Object> out = new LinkedHashMap<>();
        places.forEach((place, names) -> {
            List<Object> l = new ArrayList<>();
            for (String n : new TreeSet<>(names)) {
                boolean profile = PROFILE_KEYS.contains(place + "." + n) || place.equals("form.save");
                l.add(entry(n, profile ? "drishti" : "core"));
            }
            out.put(place, l);
        });
        return out;
    }

    static List<Object> panelKinds() throws Exception {
        List<Object> out = new ArrayList<>();
        for (Object k : (Object[]) Class.forName(P + "model.PanelKind").getMethod("values").invoke(null)) {
            String id = (String) k.getClass().getMethod("id").invoke(k);
            Map<String, Object> e = entry(id, PROFILE_KINDS.contains(id) ? "drishti" : "core");
            e.put("required", new TreeSet<>((Set<?>) k.getClass().getMethod("required").invoke(k)));
            e.put("optional", new TreeSet<>((Set<?>) k.getClass().getMethod("optional").invoke(k)));
            out.add(e);
        }
        out.sort(Comparator.comparing(m -> (String) ((Map<?, ?>) m).get("name")));
        return out;
    }

    /** Every panel option, with the kinds that accept it (PanelKind.accepts, so where, when, on-select and source are included). */
    static List<Object> panelOptions() throws Exception {
        Object[] kinds = (Object[]) Class.forName(P + "model.PanelKind").getMethod("values").invoke(null);
        TreeSet<String> names = new TreeSet<>(List.of("where", "when", "on-select", "source"));
        for (Object k : kinds) {
            names.addAll(stringSet(k.getClass().getMethod("required").invoke(k)));
            names.addAll(stringSet(k.getClass().getMethod("optional").invoke(k)));
        }
        List<Object> out = new ArrayList<>();
        for (String n : names) {
            TreeSet<String> by = new TreeSet<>();
            for (Object k : kinds) {
                if ((Boolean) k.getClass().getMethod("accepts", String.class).invoke(k, n)) {
                    by.add((String) k.getClass().getMethod("id").invoke(k));
                }
            }
            Map<String, Object> e = entry(n, "core");
            e.put("kinds", by);
            out.add(e);
        }
        return out;
    }

    static List<Object> controlKinds() throws Exception {
        List<Object> out = new ArrayList<>();
        for (Object k : (Object[]) Class.forName(P + "model.ControlKind").getMethod("values").invoke(null)) {
            out.add(entry((String) k.getClass().getMethod("id").invoke(k), "core"));
        }
        out.sort(Comparator.comparing(m -> (String) ((Map<?, ?>) m).get("name")));
        return out;
    }

    /** The function table: arity, family, and Core or profile (the engine's model functions are the profile's, SPEC 5.11). */
    static List<Object> functions() throws Exception {
        Class<?> fns = Class.forName(P + "el.Functions");
        @SuppressWarnings("unchecked") Map<String, int[]> arity = (Map<String, int[]>) fns.getMethod("arity").invoke(null);
        @SuppressWarnings("unchecked") Map<String, String> old = (Map<String, String>) fns.getMethod("oldSpellings").invoke(null);
        Map<String, Object[]> cat = new TreeMap<>();
        for (Object e : (List<?>) fns.getMethod("catalogue").invoke(null)) {
            Class<?> r = e.getClass();
            cat.put((String) r.getMethod("name").invoke(e), new Object[] {r.getMethod("family").invoke(e), r.getMethod("runs").invoke(e)});
        }
        List<Object> out = new ArrayList<>();
        for (String name : new TreeSet<>(arity.keySet())) {
            String now = old.get(name);
            Object[] c = cat.get(now == null ? name : now);
            boolean core = c == null || "R".equals(c[1]) || CORE_AGGREGATES.contains(now == null ? name : now);
            Map<String, Object> e = entry(name, core ? "core" : "drishti");
            e.put("family", c == null || "".equals(c[0]) ? null : c[0]);
            e.put("min", arity.get(name)[0]);
            e.put("max", arity.get(name)[1] == Integer.MAX_VALUE ? null : arity.get(name)[1]);
            if (now != null) {
                e.put("oldSpellingOf", now);
            }
            out.add(e);
        }
        return out;
    }

    /**
     * Every problem code the reference's compiled classes raise ({@code DRS-2nnn}, the editing operations' {@code DRS-50nn}, checked mode's
     * {@code DRS-91nn}), written as Core's ({@code RCH-} with the same number, SPEC 6.2), with the classes that raise it; a code raised only by
     * the Drishti profile's classes ({@code about}, the matcher, the registry, the Spring wiring) is the profile's.
     */
    static List<Object> codes() throws Exception {
        Path jar = Path.of(Class.forName(P + "SutraParser".replace("SutraParser", "parse.SutraParser")).getProtectionDomain().getCodeSource().getLocation().toURI());
        Pattern code = Pattern.compile("(?:DRS|DEL)-([0-9]{4})");
        Map<String, TreeSet<String>> by = new TreeMap<>();
        try (JarFile j = new JarFile(jar.toFile())) {
            for (JarEntry e : Collections.list(j.entries())) {
                if (!e.getName().endsWith(".class")) { continue; }
                String cls = e.getName().replace('/', '.').replaceAll("\\.class$", "").replaceAll("\\$.*", "");
                try (InputStream in = j.getInputStream(e)) {
                    Matcher m = code.matcher(new String(in.readAllBytes(), StandardCharsets.ISO_8859_1));
                    while (m.find()) {
                        by.computeIfAbsent(m.group(1), x -> new TreeSet<>()).add(cls.substring(P.length()));
                    }
                }
            }
        }
        List<Object> out = new ArrayList<>();
        Set<String> profileClasses = Set.of("SutraMatcher", "MatchTrace", "SutraRegistry", "RachanaConfiguration", "RachanaProperties", "UnderConstruction", "UiKitDir");
        by.forEach((n, classes) -> {
            boolean core = classes.stream().anyMatch(c -> !c.startsWith("about.") && !profileClasses.contains(c));
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("code", "RCH-" + n);
            e.put("profile", core ? "core" : "drishti");
            e.put("since", SINCE);
            e.put("raisedBy", classes);
            out.add(e);
        });
        return out;
    }

    static Map<String, Object> limits() throws Exception {
        Object d = field("el.ElLimits", "DEFAULTS");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("expressionDepth", d.getClass().getMethod("maxDepth").invoke(d));
        m.put("expressionLength", d.getClass().getMethod("maxLength").invoke(d));
        m.put("controls", field("model.ControlRules", "MAX_CONTROLS"));
        m.put("refreshSeconds", field("model.Refresh", "MAX"));
        m.put("panelSpan", field("model.Panel", "MAX_SPAN"));
        m.put("panelHeight", field("model.Panel", "MAX_HEIGHT"));
        m.put("sourceLimit", field("model.Wiring$Source", "MAX_LIMIT"));
        return m;
    }

    // ---- the Core JSON Schema --------------------------------------------------------------------------------------

    /**
     * RachanaSchema.build over Core's formats and no entity kinds, with the Drishti profile's names taken out: the top-level match and
     * domain, the panel kinds links and provenance, a source's entity forms (kind, id, search, pack, params, as-of), a form's save, the
     * profile's functions; $id is the specification's address. Object keys are written sorted (the reference builds some with Map.of).
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> coreSchema(Set<String> coreFormats) throws Exception {
        Map<String, Object> s = new LinkedHashMap<>((Map<String, Object>) Class.forName(P + "RachanaSchema")
                .getMethod("build", Collection.class, Collection.class).invoke(null, coreFormats, List.of()));
        Object core = strip(s);
        Map<String, Object> m = (Map<String, Object>) core;
        m.put("$id", "https://ajsinha.github.io/drishti-rachana/spec/1/sutra.schema.json");
        m.put("title", "Rachana Sutra, language 1 (Core)");
        m.put("$comment", "Copyright (c) 2026 Ashutosh Sinha. Specification licence: ../../LICENSE-SPEC.md (draft, not yet in force). Generated by tools/reference-bridge/LangDump.java from the reference's RachanaSchema with the Drishti profile's names removed (plan R0 H4). Do not edit.");
        ((Map<String, Object>) m.get("properties")).keySet().removeAll(Set.of("match", "domain"));
        m.put("required", ((List<String>) m.get("required")).stream().filter(r -> !r.equals("match") && !r.equals("domain")).toList());
        Map<String, Object> fns = (Map<String, Object>) m.get("x-rachana-functions");
        Set<String> profile = new HashSet<>();
        for (Object f : functions()) {
            Map<String, Object> e = (Map<String, Object>) f;
            if ("drishti".equals(e.get("profile"))) { profile.add((String) e.get("name")); }
        }
        fns.keySet().removeAll(profile);
        return m;
    }

    /** A deep copy with the profile's panel kinds, source forms and form save removed. */
    @SuppressWarnings("unchecked")
    static Object strip(Object v) {
        if (v instanceof Map<?, ?> in) {
            Map<String, Object> m = new LinkedHashMap<>();
            in.forEach((k, x) -> m.put((String) k, strip(x)));
            if (m.get("properties") instanceof Map<?, ?> props) {
                Map<String, Object> p = (Map<String, Object>) props;
                if (p.containsKey("table") && p.containsKey("kind")) {          // a source: Core keeps table, where, limit
                    p.keySet().removeAll(Set.of("kind", "id", "search", "pack", "params", "as-of", "model"));
                    m.remove("anyOf");
                    m.put("required", List.of("table"));
                }
                if (p.containsKey("save") && p.containsKey("fields")) {       // a form: save is the profile's
                    p.remove("save");
                }
                if (p.containsKey("place") && p.containsKey("panels")) {      // a children group's source is an entity kind: the profile's
                    p.remove("source");
                }
            }
            if (m.get("required") instanceof List<?> r) {                  // a set in the reference (PanelKind.required): its order means nothing
                m.put("required", r.stream().map(String::valueOf).sorted().toList());
            }
            if (m.get("enum") instanceof List<?> l && l.containsAll(PROFILE_KINDS) && l.contains("table")) {
                m.put("enum", l.stream().filter(x -> !PROFILE_KINDS.contains(x)).toList());
            }
            if (m.get("allOf") instanceof List<?> l) {
                m.put("allOf", l.stream().filter(x -> !(x instanceof Map<?, ?> c && c.get("if") instanceof Map<?, ?> i
                        && i.get("properties") instanceof Map<?, ?> ip && ip.get("kind") instanceof Map<?, ?> kk && PROFILE_KINDS.contains(kk.get("const")))).toList());
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

    static Map<String, Object> entry(String name, String profile) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("name", name);
        e.put("profile", profile);
        e.put("since", SINCE);
        return e;
    }

    static Object field(String cls, String name) throws Exception {
        Field f = Class.forName(P + cls).getDeclaredField(name);
        f.setAccessible(true);
        return f.get(null);
    }

    static Set<String> set(String cls, String name) {
        try {
            return stringSet(field(cls, name));
        } catch (Exception e) {
            throw new IllegalStateException(cls + "." + name + ": " + e, e);
        }
    }

    static Set<String> stringSet(Object o) {
        Set<String> s = new TreeSet<>();
        ((Collection<?>) o).forEach(x -> s.add(String.valueOf(x)));
        return s;
    }

    static Object call(String cls, String method, Class<?>[] types, Object... args) throws Exception {
        Method m = Class.forName(P + cls).getMethod(method, types);
        return m.invoke(null, args);
    }

    @SuppressWarnings("unchecked")
    static Collection<String> names(Object formats) throws Exception {
        return (Collection<String>) formats.getClass().getMethod("names").invoke(formats);
    }

    static String implVersion() throws Exception {
        String v = Class.forName(P + "parse.SutraParser").getPackage().getImplementationVersion();
        if (v != null) { return v; }
        String loc = Class.forName(P + "parse.SutraParser").getProtectionDomain().getCodeSource().getLocation().getPath();
        Matcher m = Pattern.compile("drishti-rachana-([^/]+)\\.jar").matcher(loc);
        return m.find() ? m.group(1) : "unknown";
    }

    static void write(Path p, Object v) throws Exception {
        Object tree = JSON.readValue(JSON.writeValueAsString(v), Object.class);          // a second pass sorts every nested map
        Files.writeString(p, JSON.writeValueAsString(tree).replace("\r\n", "\n") + "\n", StandardCharsets.UTF_8);
    }
}
