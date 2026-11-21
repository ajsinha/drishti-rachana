/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.parse;

import com.ash.rachana.SutraProblem;
import com.ash.rachana.model.SourceLocation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The typed form of a fragment (SUTRA_EXPANSION C1, LIBRARIES.md 4; language level 1.2): a fragment whose every {@code params:} entry is
 * a mapping with a {@code type}. A parameter is of one of three classes (LIBRARIES.md 4.2):
 *
 * <ul>
 *   <li><b>value</b> ({@code text}, {@code number}, {@code integer}, {@code decimal}, {@code boolean}, {@code date}, {@code datetime},
 *       {@code duration}, {@code money}, {@code list<T>}, {@code record{...}}, any of them followed by {@code ?}): written at the use as an
 *       expression, compiled in the user's context, read inside the fragment as {@code arg.<name>}. A YAML number or {@code true}/{@code
 *       false} at the use is a literal and is checked against the type, {@code min} and {@code max}; a default is always a literal. A
 *       {@code text} parameter takes its text as written (a label, a title). {@code arg.x} as a whole value becomes the expression or
 *       the literal itself; inside a longer expression or template it becomes the expression in parentheses or the literal as
 *       Rachana-EL text.</li>
 *   <li><b>table</b> ({@code type: table} with {@code columns:}): an expression giving rows, read inside as {@code arg.<name>};
 *       {@code optional: true} lets a use leave it out, and a panel with {@code when-bound: <name>} is then left out.</li>
 *   <li><b>name</b> ({@code id}, {@code format}, {@code tone}, {@code icon}, {@code variant}, {@code area}, {@code palette}, {@code key},
 *       {@code enum} with {@code options:}, {@code columns}): a word fixed when the Sutra compiles, put in as a <b>checked token</b> where the
 *       fragment writes {@code %{name}}. Free text substitution is the text form's alone.</li>
 * </ul>
 *
 * {@code slots:} (with {@code accepts: panels}) are holes the use fills with its own panels ({@code { slot: footer }} in the fragment's
 * panels). Expansion stays a compile-time step: the panels a typed use brings carry no trace of the fragment. Stateless; thread-safe.
 */
public final class TypedFragment {

    /** A parameter, a slot or an event at a use that the fragment does not declare. */
    public static final String UNKNOWN = "RCH-2114";
    /** A required parameter that the use does not give. */
    public static final String MISSING = "RCH-2115";
    /** A parameter of the wrong type, or outside its constraints. */
    public static final String WRONG_TYPE = "RCH-2116";
    /** A fragment that reaches outside what its use hands it (a public library's fragment reads the screen). */
    public static final String SCOPE = "RCH-2117";
    /** Slot content of a kind the slot does not accept, or more of it than its {@code max}. */
    public static final String SLOT = "RCH-2118";
    /** The language level that brings the typed form. */
    public static final String SINCE = "1.2";

    /** The value types (LIBRARIES.md 4.2; X8's type language without {@code fn}, which waits for lambdas, X13). */
    public static final Set<String> VALUE_TYPES = Set.of("text", "number", "integer", "decimal", "boolean", "date", "datetime", "duration", "money");
    /** The name types: checked tokens. */
    public static final Set<String> NAME_TYPES = Set.of("id", "format", "tone", "icon", "variant", "area", "palette", "key", "enum", "columns");
    /** The tone words a {@code tone} parameter may take (SPEC 2.6). */
    public static final Set<String> TONES = Set.of("pos", "neg", "ok", "warn", "bad", "link", "accent");
    /** What a slot may accept at this level. */
    public static final Set<String> SLOT_KINDS = Set.of("panels");
    /** The keys a typed fragment file may have at this level. */
    public static final Set<String> KEYS = Set.of("rachana", "fragment", "version", "description", "doc", "params", "slots", "emits", "panels",
            "budget", "examples");
    /** The most parameters and slots a fragment declares (LIBRARIES.md 18). */
    public static final int MAX_PARAMS = 40;
    public static final int MAX_SLOTS = 8;

    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9-]{0,39}");
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{0,63}");
    private static final Pattern FKEY = Pattern.compile("F([1-9]|1[0-2])");
    private static final Pattern MASKED = Pattern.compile("pXq\\{([a-z][a-z0-9-]*)}");
    private static final Pattern ARG = Pattern.compile("(?<![A-Za-z0-9_.$@])arg\\.([a-z][a-z0-9-]*)");
    private static final Pattern VALUE_TYPE = Pattern.compile("(list<)?([a-z]+)(>)?\\??|record\\{[^}]*}\\??");

    /** One declared parameter. {@code dflt} is the default node (null for none). */
    public record Param(String name, String kind, String type, boolean required, PNode dflt, String doc, Number min, Number max,
            List<String> options, List<String> columns, PNode at) {

        /** {@code value}, {@code table} or {@code name}. */
        public String kind() {
            return kind;
        }

        /** Whether a use may leave it out with nothing in its place (an optional table, a nullable value without default). */
        public boolean optional() {
            return !required && dflt == null;
        }
    }

    /** One declared slot. */
    public record Slot(String name, String accepts, int max, String doc) { }

    private final String name;
    private final String file;
    private final PNode root;
    private final Map<String, Param> params;
    private final Map<String, Slot> slots;
    private final List<SutraProblem> problems;

    private TypedFragment(String name, String file, PNode root, Map<String, Param> params, Map<String, Slot> slots, List<SutraProblem> problems) {
        this.name = name;
        this.file = file;
        this.root = root;
        this.params = params;
        this.slots = slots;
        this.problems = problems;
    }

    /**
     * Whether a fragment file's root (read with {@code %{} masked) is in the typed form: it has {@code params:} and every entry is a
     * mapping with a {@code type}. A file with none of them typed is the text form; a mixture is reported by {@link #of}.
     */
    public static boolean isTyped(PNode root) {
        PNode ps = root == null ? null : root.map().get("params");
        if (ps == null || !ps.isMap() || ps.map().isEmpty()) {
            return false;
        }
        return ps.map().values().stream().anyMatch(v -> v.isMap() && v.map().containsKey("type"));
    }

    /** The typed fragment a block holds (its declarations read and checked; {@link #problems()} lists what is wrong). */
    public static TypedFragment of(SutraBlocks.Block b) {
        PNode root = SutraBlocks.parse(b.text().replace("%{", "pXq{"), b.file());
        return read(b.name(), b.file(), root);
    }

    static TypedFragment read(String name, String file, PNode root) {
        List<SutraProblem> out = new ArrayList<>();
        Map<String, Param> params = new LinkedHashMap<>();
        Map<String, Slot> slots = new LinkedHashMap<>();
        root.map().forEach((k, v) -> {
            if (!KEYS.contains(k)) {
                out.add(problem(SutraBlocks.BLOCK, "a typed fragment takes " + new java.util.TreeSet<>(KEYS) + ", not '" + k + "' at this level", file, v));
            }
        });
        PNode ps = root.map().get("params");
        for (Map.Entry<String, PNode> e : ps == null ? List.<Map.Entry<String, PNode>>of() : ps.map().entrySet()) {
            Param p = param(name, e.getKey(), e.getValue(), file, out);
            if (p != null) {
                params.put(p.name(), p);
            }
        }
        if (params.size() > MAX_PARAMS) {
            out.add(problem(SutraBlocks.BLOCK, "fragment " + name + " has " + params.size() + " parameters; at most " + MAX_PARAMS + " (make it two fragments)", file, ps));
        }
        PNode ss = root.map().get("slots");
        for (Map.Entry<String, PNode> e : ss == null ? List.<Map.Entry<String, PNode>>of() : ss.map().entrySet()) {
            Map<String, PNode> d = e.getValue().map();
            String accepts = text(d.get("accepts"));
            if (!NAME.matcher(e.getKey()).matches() || !SLOT_KINDS.contains(accepts)) {
                out.add(problem(SutraBlocks.BLOCK, "slot " + e.getKey() + " of fragment " + name + " accepts one of " + SLOT_KINDS + " at this level", file, e.getValue()));
                continue;
            }
            int max = d.get("max") != null && d.get("max").value() instanceof Long l ? l.intValue() : 8;
            if (blank(text(d.get("doc")))) {
                out.add(problem("RCH-2055", "fragment " + name + ": slot " + e.getKey() + " has a doc of one line", file, e.getValue()));
            }
            slots.put(e.getKey(), new Slot(e.getKey(), accepts, max, text(d.get("doc"))));
        }
        if (slots.size() > MAX_SLOTS) {
            out.add(problem(SutraBlocks.BLOCK, "fragment " + name + " has more than " + MAX_SLOTS + " slots", file, ss));
        }
        PNode doc = root.map().get("doc");
        if (doc == null || blank(text(doc.map().get("summary")))) {
            out.add(problem("RCH-2055", "fragment " + name + " has doc.summary: one line saying what it shows", file, doc == null ? root : doc));
        } else if (text(doc.map().get("summary")).length() > 120) {
            out.add(problem("RCH-2055", "fragment " + name + ": doc.summary is at most 120 characters", file, doc.map().get("summary")));
        }
        TypedFragment t = new TypedFragment(name, file, root, params, slots, out);
        t.checkBody();
        return t;
    }

    private static Param param(String fragment, String name, PNode d, String file, List<SutraProblem> out) {
        if (!d.isMap() || !d.map().containsKey("type")) {
            out.add(problem(SutraBlocks.BLOCK, "fragment " + fragment + " mixes the typed and the text form: parameter " + name
                    + " is a mapping with a type in the typed form", file, d));
            return null;
        }
        if (!NAME.matcher(name).matches()) {
            out.add(problem(SutraBlocks.BLOCK, "parameter '" + name + "' of fragment " + fragment + " is a lower-case kebab name", file, d));
            return null;
        }
        Map<String, PNode> m = d.map();
        String type = text(m.get("type"));
        String kind = "table".equals(type) ? "table" : NAME_TYPES.contains(type) ? "name" : "value";
        if ("value".equals(kind) && !valueType(type)) {
            out.add(problem(SutraBlocks.BLOCK, "parameter " + name + " of fragment " + fragment + " has type '" + type + "': a value type ("
                    + new java.util.TreeSet<>(VALUE_TYPES) + ", list<T>, record{...}, T?), a name type (" + new java.util.TreeSet<>(NAME_TYPES)
                    + ") or table", file, m.get("type")));
            return null;
        }
        boolean optional = Boolean.TRUE.equals(m.get("optional") == null ? null : m.get("optional").value());
        boolean required = Boolean.TRUE.equals(m.get("required") == null ? null : m.get("required").value());
        PNode dflt = m.get("default");
        if (required && dflt != null) {
            out.add(problem(SutraBlocks.BLOCK, "parameter " + name + " of fragment " + fragment + " is required or has a default, not both", file, d));
        }
        if (!required && dflt == null && !optional && !type.endsWith("?")) {
            out.add(problem(SutraBlocks.BLOCK, "parameter " + name + " of fragment " + fragment + " says required: true, a default, or optional: true", file, d));
        }
        if (blank(text(m.get("doc")))) {
            out.add(problem("RCH-2055", "fragment " + fragment + ": parameter " + name + " has a doc of one line", file, d));
        }
        List<String> options = new ArrayList<>();
        if (m.get("options") != null) {
            m.get("options").list().forEach(o -> options.add(o.text()));
        }
        if ("enum".equals(type) && options.isEmpty()) {
            out.add(problem(SutraBlocks.BLOCK, "parameter " + name + " of fragment " + fragment + " is an enum: it lists its options", file, d));
        }
        List<String> columns = new ArrayList<>(m.get("columns") == null ? List.of() : m.get("columns").map().keySet());
        if ("table".equals(kind) && columns.isEmpty()) {
            out.add(problem(SutraBlocks.BLOCK, "table parameter " + name + " of fragment " + fragment + " names its columns (columns: { name: { type } })", file, d));
        }
        Param p = new Param(name, kind, type, required, dflt, text(m.get("doc")), number(m.get("min")), number(m.get("max")), List.copyOf(options),
                List.copyOf(columns), d);
        if (dflt != null) {
            String wrong = literal(p, dflt, true);
            if (wrong != null) {
                out.add(problem(WRONG_TYPE, "the default of parameter " + name + " of fragment " + fragment + " " + wrong, file, dflt));
            }
        }
        return p;
    }

    private static boolean valueType(String type) {
        Matcher vm = type == null ? null : VALUE_TYPE.matcher(type);
        if (vm == null || !vm.matches()) {
            return false;
        }
        return type.startsWith("record{") || VALUE_TYPES.contains(vm.group(2)) && (vm.group(1) == null) == (vm.group(3) == null);
    }

    /** Every {@code %{x}} names a name parameter, every {@code arg.x} a value or table parameter, every {@code slot:} a slot. */
    private void checkBody() {
        PNode panels = root.map().get("panels");
        if (panels == null || !panels.isList()) {
            return;
        }
        walk(panels, n -> {
            if (n.value() instanceof String s) {
                Matcher m = MASKED.matcher(s);
                while (m.find()) {
                    Param p = params.get(m.group(1));
                    if (p == null || !"name".equals(p.kind())) {
                        problems.add(problem(SutraBlocks.BLOCK, "fragment " + name + " writes %{" + m.group(1) + "}: in the typed form %{...} names a "
                                + "name-class parameter (" + new java.util.TreeSet<>(NAME_TYPES) + "); read a value as arg." + m.group(1), file, n));
                    }
                }
                Matcher a = ARG.matcher(s);
                while (a.find()) {
                    String arg = longest(a.group(1));
                    Param p = arg == null ? null : params.get(arg);
                    if (p == null || "name".equals(p.kind())) {
                        problems.add(problem(SutraBlocks.BLOCK, "fragment " + name + " reads arg." + a.group(1) + ", which is not a value or table "
                                + "parameter it declares (" + params.keySet() + ")", file, n));
                    }
                }
            } else if (n.isMap() && n.map().containsKey("slot") && n.map().get("slot").text() != null && !slots.containsKey(n.map().get("slot").text())) {
                problems.add(problem(SutraBlocks.BLOCK, "fragment " + name + " places slot " + n.map().get("slot").text() + ", which it does not declare",
                        file, n));
            }
        });
    }

    /** The declared parameter that {@code arg.<text>} names: the longest declared name the text starts with ({@code arg.warn-at - 1}). */
    private String longest(String text) {
        String best = null;
        for (String p : params.keySet()) {
            if (text.startsWith(p) && (text.length() == p.length() || text.charAt(p.length()) == '-') && (best == null || p.length() > best.length())) {
                best = p;
            }
        }
        return best;
    }

    /** Its name. */
    public String name() {
        return name;
    }

    /** Its parameters, in declared order. */
    public Map<String, Param> params() {
        return params;
    }

    /** Its slots, in declared order. */
    public Map<String, Slot> slots() {
        return slots;
    }

    /** What is wrong with its declarations and body (empty when it is sound). */
    public List<SutraProblem> problems() {
        return problems;
    }

    /**
     * Where it reads outside what a use hands it (LIBRARIES.md 4.4, L-6): {@code $}, {@code ctl.}, {@code state.}, {@code sel.}, a
     * {@code source:}, {@code data:} or {@code url:}. A fragment of a public library must have none ({@code RCH-2117}).
     */
    public List<SutraProblem> outsideScope() {
        List<SutraProblem> out = new ArrayList<>();
        PNode panels = root.map().get("panels");
        Pattern screen = Pattern.compile("\\$(?!\\{)|(?<![A-Za-z0-9_.])(ctl|state|sel)\\.");
        walk(panels, n -> {
            if (n.value() instanceof String s && screen.matcher(s).find()) {
                out.add(problem(SCOPE, "fragment " + name + " reads the screen around it ('" + s + "'): a library fragment reads only its parameters "
                        + "(arg.), its tables and its slots", file, n));
            } else if (n.isMap()) {
                for (String k : List.of("source", "data", "url")) {
                    if (n.map().containsKey(k)) {
                        out.add(problem(SCOPE, "fragment " + name + " names its own " + k + ": a library fragment is handed its tables as parameters",
                                file, n.map().get(k)));
                    }
                }
            }
        });
        return out;
    }

    /**
     * The panels one use brings: {@code with:} checked against the declarations ({@code RCH-2114}, {@code 2115}, {@code 2116}), name
     * parameters put in as tokens, {@code arg.x} replaced by the use's expression (in parentheses inside a longer text) or by the literal,
     * slots filled, panels whose {@code when-bound} table was not given left out. Problems go to {@code problems}; null when the use
     * cannot be expanded.
     */
    public List<PNode> expand(PNode use, String sutraFile, List<SutraProblem> out) {
        int before = out.size();
        Map<String, PNode> with = use.map().get("with") == null ? Map.of() : use.map().get("with").map();
        for (Map.Entry<String, PNode> e : with.entrySet()) {
            if (!params.containsKey(e.getKey())) {
                out.add(problem(UNKNOWN, "fragment " + name + " has no parameter '" + e.getKey() + "' (it has " + String.join(", ", params.keySet()) + ")",
                        sutraFile, e.getValue()));
            }
        }
        Map<String, String> exprs = new LinkedHashMap<>();
        Map<String, PNode> literals = new LinkedHashMap<>();
        Map<String, PNode> tokens = new LinkedHashMap<>();
        Set<String> unbound = new LinkedHashSet<>();
        for (Param p : params.values()) {
            PNode v = with.get(p.name());
            boolean fromDefault = v == null;
            if (v == null) {
                v = p.dflt();
            }
            if (v == null) {
                if (p.required()) {
                    out.add(problem(MISSING, "fragment " + name + " needs parameter '" + p.name() + "' (" + p.type() + "): " + nz(p.doc()), sutraFile, use));
                }
                unbound.add(p.name());
                continue;
            }
            String wrong = literal(p, v, fromDefault);
            if (wrong != null) {
                out.add(problem(WRONG_TYPE, "parameter " + p.name() + " of fragment " + name + " " + wrong, sutraFile, v));
                continue;
            }
            switch (p.kind()) {
                case "name" -> tokens.put(p.name(), v);
                default -> {
                    if (v.value() instanceof String s && !fromDefault && !isText(p)) {
                        exprs.put(p.name(), s);
                    } else {
                        literals.put(p.name(), v);
                    }
                }
            }
        }
        Map<String, PNode> slotted = use.map().get("slots") == null ? Map.of() : use.map().get("slots").map();
        for (Map.Entry<String, PNode> e : slotted.entrySet()) {
            Slot s = slots.get(e.getKey());
            if (s == null) {
                out.add(problem(UNKNOWN, "fragment " + name + " has no slot '" + e.getKey() + "' (it has " + String.join(", ", slots.keySet()) + ")",
                        sutraFile, e.getValue()));
            } else if (!e.getValue().isList()) {
                out.add(problem(SLOT, "slot " + s.name() + " of fragment " + name + " accepts a list of panels", sutraFile, e.getValue()));
            } else if (e.getValue().list().size() > s.max()) {
                out.add(problem(SLOT, "slot " + s.name() + " of fragment " + name + " takes at most " + s.max() + (s.max() == 1 ? " panel" : " panels") + ", not " + e.getValue().list().size(),
                        sutraFile, e.getValue()));
            }
        }
        if (out.size() > before) {
            return null;
        }
        List<PNode> panels = new ArrayList<>();
        for (PNode p : root.map().get("panels").list()) {
            if (p.isMap() && p.map().containsKey("slot") && p.map().size() == 1) {
                PNode filled = slotted.get(p.map().get("slot").text());
                if (filled != null) {
                    panels.addAll(filled.list());                // the user's own panels: their context, untouched
                }
                continue;
            }
            PNode bound = p.map().get("when-bound");
            if (bound != null && unbound.contains(bound.text())) {
                continue;
            }
            if (bound != null) {
                Map<String, PNode> m = new LinkedHashMap<>(p.map());
                m.remove("when-bound");
                p = new PNode(m, p.line(), p.column());
            }
            panels.add(fill(p, exprs, literals, tokens, sutraFile, out));
        }
        return panels;
    }

    private PNode fill(PNode n, Map<String, String> exprs, Map<String, PNode> literals, Map<String, PNode> tokens, String sutraFile, List<SutraProblem> out) {
        if (n.isMap()) {
            Map<String, PNode> m = new LinkedHashMap<>();
            n.map().forEach((k, v) -> m.put(k, fill(v, exprs, literals, tokens, sutraFile, out)));
            return new PNode(m, n.line(), n.column());
        }
        if (n.isList()) {
            List<PNode> l = new ArrayList<>();
            n.list().forEach(v -> l.add(fill(v, exprs, literals, tokens, sutraFile, out)));
            return new PNode(l, n.line(), n.column());
        }
        if (!(n.value() instanceof String s)) {
            return n;
        }
        Matcher whole = MASKED.matcher(s);
        if (whole.matches() && tokens.containsKey(whole.group(1))) {
            PNode t = tokens.get(whole.group(1));
            return new PNode(t.value(), n.line(), n.column());    // a whole-value token: a word, or a list of columns
        }
        String arg = s.startsWith("arg.") ? longest(s.substring(4)) : null;
        if (arg != null && s.length() == 4 + arg.length()) {          // a whole value: the expression or the literal itself
            if (exprs.containsKey(arg)) {
                return new PNode(exprs.get(arg), n.line(), n.column());
            }
            if (literals.containsKey(arg)) {
                return new PNode(literals.get(arg).value(), n.line(), n.column());
            }
        }
        StringBuilder sb = new StringBuilder();
        Matcher m = MASKED.matcher(s);
        int copied = 0;
        while (m.find()) {
            PNode t = tokens.get(m.group(1));
            sb.append(s, copied, m.start());
            if (t == null || !t.isScalar()) {
                out.add(problem(WRONG_TYPE, "fragment " + name + " puts %{" + m.group(1) + "} inside a text: a list parameter fills a whole value", sutraFile, n));
            } else {
                sb.append(t.text());
            }
            copied = m.end();
        }
        sb.append(s, copied, s.length());
        String text = sb.toString();
        StringBuilder done = new StringBuilder();
        Matcher a = ARG.matcher(text);
        copied = 0;
        while (a.find()) {
            String p = longest(a.group(1));
            if (p == null) {
                continue;
            }
            done.append(text, copied, a.start());
            done.append(exprs.containsKey(p) ? "(" + exprs.get(p) + ")" : literals.containsKey(p) ? el(literals.get(p)) : "null");
            copied = a.start() + 4 + p.length();
        }
        done.append(text, copied, text.length());
        return new PNode(done.toString(), n.line(), n.column());
    }

    /** Whether a parameter takes text: its value at a use is the text as written (a literal), not an expression. */
    private static boolean isText(Param p) {
        return "text".equals(p.type()) || "text?".equals(p.type());
    }

    /** A literal as Rachana-EL text: a number, {@code true}/{@code false}, {@code null} or a quoted text. */
    private static String el(PNode v) {
        Object x = v.value();
        if (x == null) {
            return "null";
        }
        if (x instanceof String s) {
            return "'" + s.replace("'", "''") + "'";
        }
        return x.toString();
    }

    /**
     * What is wrong with a value for this parameter, or null: a name must be a token of its type; a value parameter's YAML number or flag
     * must fit the type and the bounds (a text at the use is an expression and is checked where it is compiled); a default is a literal.
     */
    static String literal(Param p, PNode v, boolean isDefault) {
        Object x = v.value();
        String base = p.type().endsWith("?") ? p.type().substring(0, p.type().length() - 1) : p.type();
        switch (p.kind()) {
            case "name" -> {
                if ("columns".equals(base)) {
                    return v.isList() ? null : "is a list of columns";
                }
                if (!(x instanceof String s)) {
                    return "is a " + base + " name, not " + shown(v);
                }
                return switch (base) {
                    case "tone" -> TONES.contains(s) ? null : "is a tone (" + String.join(", ", new java.util.TreeSet<>(TONES)) + "), not '" + s + "'";
                    case "enum" -> p.options().contains(s) ? null : "is one of " + String.join(", ", p.options()) + ", not '" + s + "'";
                    case "key" -> FKEY.matcher(s).matches() ? null : "is a function key (F1-F12), not '" + s + "'";
                    default -> TOKEN.matcher(s).matches() ? null : "is a " + base + " name (a word), not '" + s + "'";
                };
            }
            case "table" -> {
                if (v.isMap()) {
                    return "is an expression giving rows; a column mapping ({ from, columns }) is not in this level: shape the table upstream";
                }
                return x instanceof String || x == null && !isDefault ? null : "is an expression giving rows, not " + shown(v);
            }
            default -> {
                if (v.isMap() || v.isList()) {
                    return "is a value: write it as an expression";
                }
                if (x == null) {
                    return p.type().endsWith("?") || !p.required() ? null : "is required and may not be empty";
                }
                if (x instanceof String) {
                    return isDefault && !("text".equals(base) || base.startsWith("date") || "duration".equals(base))
                            ? "is a literal of type " + base + ", not a text" : null;
                }
                boolean numeric = "number".equals(base) || "decimal".equals(base) || "money".equals(base) || "integer".equals(base);
                if (x instanceof Boolean) {
                    return "boolean".equals(base) ? null : "is of type " + base + ", not a true/false";
                }
                if (!(x instanceof Number num)) {
                    return "is of type " + base;
                }
                if (!numeric) {
                    return "is of type " + base + ", not a number";
                }
                if ("integer".equals(base) && !(x instanceof Long)) {
                    return "is a whole number, not " + x;
                }
                java.math.BigDecimal d = decimal(num);
                if (p.min() != null && d.compareTo(decimal(p.min())) < 0) {
                    return "is at least " + p.min() + ", not " + x;
                }
                if (p.max() != null && d.compareTo(decimal(p.max())) > 0) {
                    return "is at most " + p.max() + ", not " + x;
                }
                return null;
            }
        }
    }

    private static java.math.BigDecimal decimal(Number n) {
        return n instanceof Long l ? java.math.BigDecimal.valueOf(l) : new java.math.BigDecimal(n.toString());
    }

    private static String shown(PNode v) {
        return v.isMap() ? "a mapping" : v.isList() ? "a list" : v.value() == null ? "empty" : "'" + v.value() + "'";
    }

    private static Number number(PNode n) {
        return n != null && n.value() instanceof Number x ? x : null;
    }

    private static void walk(PNode n, java.util.function.Consumer<PNode> f) {
        if (n == null) {
            return;
        }
        f.accept(n);
        if (n.isMap()) {
            n.map().values().forEach(v -> walk(v, f));
        } else if (n.isList()) {
            n.list().forEach(v -> walk(v, f));
        }
    }

    private static String text(PNode n) {
        return n == null ? null : n.text();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    static SutraProblem problem(String code, String message, String file, PNode at) {
        return new SutraProblem(code, message, new SourceLocation(file, at == null ? 1 : at.line(), at == null ? 1 : at.column()));
    }
}
