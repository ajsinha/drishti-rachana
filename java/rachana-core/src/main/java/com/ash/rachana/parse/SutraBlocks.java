/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.parse;

import com.ash.rachana.SutraException;
import com.ash.rachana.SutraProblem;
import com.ash.rachana.model.SourceLocation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Shared panel blocks and the fragment library: panels written once in a block file ({@code <name>.v<N>.block.yaml} beside the Sutras) and used by many
 * Sutras, so a pack whose products share a layout (a deal ticket, legs side by side, events) keeps it in one place.
 *
 * <pre>
 * # sutras/common/deal-ticket.v1.block.yaml
 * rachana: 1
 * block: deal-ticket
 * version: 1
 * description: The deal ticket of a trade.
 * params: { rate-fmt: price4 }          # defaults; the text reads them as %{rate-fmt}
 * panels:
 *   - { id: ticket, kind: kv, title: Deal ticket, columns: [ { label: Strike, bind: $.STRIKE_PRICE, fmt: "%{rate-fmt}" } ] }
 *
 * # a Sutra's panels:, or a tabs panel's panels:
 *   - { use: deal-ticket@1, with: { rate-fmt: pct4 } }
 * </pre>
 *
 * A use is replaced by the block's panels, in order, before the Sutra is checked, so the panels are linted, matched and drawn as if
 * they had been written in place. A fragment ({@code <name>.v<N>.fragment.yaml}, {@code fragment: <name>}, the shared library in
 * the host's UI kit) is a block whose {@code doc:} is complete ({@link SutraDocstring}, {@code RCH-2055}); it is
 * used the same way. The platform's fragments are the host's UI kit ({@link BlockLibrary#kit(Path)}), read by every
 * load whatever packs are enabled; a pack's own blocks are namespaced by the pack ({@code trading/legs@1}). {@code @1} names the block's version (without one, the newest); {@code with} sets its parameters
 * (an unknown one, an unknown block or a block that uses another is {@code RCH-2048}). This class holds no state: the blocks a use
 * finds are a {@link FragmentResolver}'s, a host's {@link BlockLibrary} per instance (split step A6). Thread-safe.
 */
public final class SutraBlocks {

    /** A block's problem: unknown, badly written, a parameter it does not declare. */
    public static final String BLOCK = "RCH-2048";
    private static final Pattern PARAM = Pattern.compile("%\\{([a-z][a-z0-9-]*)}");
    private static final Pattern FILE = Pattern.compile("(.+)\\.v(\\d+)\\.(block|fragment)\\.yaml");
    private static final PositionalYamlReader READER = new PositionalYamlReader();

    /**
     * One block: its text (parameters not yet filled), its default parameters, its file name and the path it was read from (the
     * file name when it was not read from disk).
     */
    public record Block(String name, int version, String text, Map<String, String> params, String file, String path) {

        public Block(String name, int version, String text, Map<String, String> params, String file) {
            this(name, version, text, params, file, file);
        }

        /** The block's {@code description:} (empty when it has none). */
        public String description() {
            try {
                PNode d = parse(text.replace("%{", "pXq{"), file).map().get("description");
                return d == null || d.text() == null ? "" : d.text().replace("pXq{", "%{");
            } catch (SutraException e) {
                return "";
            }
        }

        /** Whether it is a fragment of the library ({@code <name>.v<N>.fragment.yaml}, {@code fragment: <name>}): its doc is checked strictly. */
        public boolean fragment() {
            return file != null && file.endsWith(".fragment.yaml") || text.startsWith("fragment:") || text.contains("\nfragment:");
        }

        /** Its {@code doc:} as plain maps, lists and text (empty when it has none). */
        public Map<String, Object> doc() {
            try {
                return SutraDocstring.plain(parse(text.replace("%{", "pXq{"), file));
            } catch (SutraException e) {
                return Map.of();
            }
        }

        /** {@code name@version}. */
        public String ref() {
            return name + "@" + version;
        }
    }


    private SutraBlocks() {}


    /** Whether a file is a block file ({@code .block.yaml}) or a fragment of the library ({@code .fragment.yaml}). */
    public static boolean isBlockFile(Path f) {
        String n = f.getFileName() == null ? "" : f.getFileName().toString();
        return n.endsWith(".block.yaml") || n.endsWith(".fragment.yaml");
    }


    /**
     * The pack a Sutra folder belongs to: the {@code name:} of the {@code pack.yaml} beside it ({@code <pack>/sutras}), else the pack
     * folder's name; null for a site folder (its blocks stay unqualified, as before packs were namespaced).
     */
    public static String packOf(Path dir) {
        Path parent = dir == null ? null : dir.toAbsolutePath().normalize().getParent();
        if (parent == null || !Files.isRegularFile(parent.resolve("pack.yaml"))) {
            return null;
        }
        try (Stream<String> lines = Files.lines(parent.resolve("pack.yaml"), StandardCharsets.UTF_8)) {
            String name = lines.filter(l -> l.startsWith("name:")).map(l -> l.substring(5).trim().replace("'", "").replace("\"", ""))
                    .findFirst().orElse("");
            if (name.matches("[a-z][a-z0-9-]{0,63}")) {
                return name;
            }
        } catch (IOException | java.io.UncheckedIOException e) {
            // the folder's name below
        }
        String folder = parent.getFileName().toString();
        return folder.matches("[a-z][a-z0-9-]{0,63}") ? folder : null;
    }

    /** One block file's content, checked: {@code rachana: 1}, {@code block}, {@code version}, {@code panels} (a list), {@code params} (scalars). */
    public static Block read(String text, String file) {
        return read(text, file, file);
    }

    /** As {@link #read(String, String)}, remembering the path the file was read from. */
    public static Block read(String text, String file, String path) {
        PNode root = parse(text.replace("%{", "pXq{"), file);         // parameters are filled later; read the shape with them masked
        Map<String, PNode> m = root.map();
        Matcher fm = FILE.matcher(file);
        boolean fragment = file.endsWith(".fragment.yaml") || m.containsKey("fragment");
        String head = fragment ? "fragment" : "block";
        String name = m.get(head) == null ? null : m.get(head).text();
        if (name == null || !name.matches("[a-z][a-z0-9-]{1,63}")) {
            throw new SutraException(List.of(problem("a " + head + " file names its " + head + ": " + head + ": <lower-case-kebab name>", file, 1, 1)));
        }
        int version = m.get("version") == null || m.get("version").text() == null ? 1 : Integer.parseInt(m.get("version").text());
        if (fm.matches() && (!fm.group(1).equals(name) || Integer.parseInt(fm.group(2)) != version)) {
            throw new SutraException(List.of(problem("the file " + file + " holds block " + name + " version " + version
                    + ": name it " + name + ".v" + version + "." + head + ".yaml", file, 1, 1)));
        }
        PNode panels = m.get("panels");
        if (panels == null || !panels.isList() || panels.list().isEmpty()) {
            throw new SutraException(List.of(problem("block " + name + " needs panels: a list of panels", file, 1, 1)));
        }
        List<String> inner = new ArrayList<>();
        for (PNode p : panels.list()) {
            if (p.map().containsKey("use")) {
                String ref = p.map().get("use").text() == null ? "" : p.map().get("use").text().trim();
                if (!fragment) {
                    throw new SutraException(List.of(problem("block " + name + " uses another block: a block holds panels only (a fragment may use fragments)",
                            file, p.line(), p.column())));
                }
                if (!ref.matches("([a-z][a-z0-9-]{0,63}/)?[a-z][a-z0-9-]{1,63}@[0-9]+")) {
                    throw new SutraException(List.of(problem("fragment " + name + " uses " + ref + " without a version: a composite pins its inner "
                            + "fragments (use: name@N)", file, p.line(), p.column())));
                }
                inner.add(ref);
            }
        }
        Map<String, String> params = new LinkedHashMap<>();
        PNode ps = m.get("params");
        if (ps != null) {
            ps.map().forEach((k, v) -> params.put(k, flow(v).replace("pXq{", "%{")));
        }
        if (fragment && TypedFragment.isTyped(root)) {                 // the typed form (C1, level 1.2): its declarations are its doc
            List<SutraProblem> typed = new ArrayList<>(inFile(TypedFragment.read(name, file, root).problems(), text, file));
            if (!typed.isEmpty()) {
                throw new SutraException(typed);
            }
        } else if (fragment) {                                         // a fragment of the library carries a complete doc: (strict)
            List<SutraProblem> doc = new ArrayList<>(SutraDocstring.checkFragment(root, name, params, file));
            doc.addAll(SutraDocstring.checkUses(root, name, inner, file));
            if (!doc.isEmpty()) {
                throw new SutraException(doc);
            }
        }
        return new Block(name, version, text, Map.copyOf(params), file, path);
    }

    /** The problems placed in the fragment file they were found in (SPEC 6.1: {@code path} and {@code range} of that file's text). */
    static List<SutraProblem> inFile(List<SutraProblem> problems, String text, String file) {
        return problems.isEmpty() ? problems : ProblemPlaces.of(text, file).place(problems);
    }

    /**
     * A parameter's value as the text filled in for its {@code %{name}}: a scalar as written (empty for none), a list or a map
     * (a {@code columns} parameter: {@code [ { label: Trade, bind: "@.TRADE_ID" }, ... ]}) as one line of YAML flow (JSON), so
     * {@code columns: %{columns}} in the fragment reads back as the same list.
     */
    public static String flow(PNode v) {
        if (v == null || v.value() == null) {
            return "";
        }
        if (v.isScalar()) {
            return v.text();
        }
        StringBuilder out = new StringBuilder();
        json(v, out);
        return out.toString();
    }

    private static void json(PNode v, StringBuilder out) {
        if (v == null || v.value() == null) {
            out.append("null");
        } else if (v.isMap()) {
            out.append('{');
            String sep = "";
            for (Map.Entry<String, PNode> e : v.map().entrySet()) {
                out.append(sep);
                Json.quote(e.getKey(), out);
                out.append(": ");
                json(e.getValue(), out);
                sep = ", ";
            }
            out.append('}');
        } else if (v.isList()) {
            out.append('[');
            String sep = "";
            for (PNode e : v.list()) {
                out.append(sep);
                json(e, out);
                sep = ", ";
            }
            out.append(']');
        } else if (v.value() instanceof String s) {
            Json.quote(s, out);
        } else {
            out.append(v.value());
        }
    }


    static java.util.Optional<Block> find(Map<String, TreeMap<Integer, Block>> library, String ref) {
        String r = ref == null ? "" : ref.trim();
        String name = r.contains("@") ? r.substring(0, r.indexOf('@')) : r;
        TreeMap<Integer, Block> versions = library.get(name);
        if (versions == null || versions.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.ofNullable(r.contains("@") ? versions.get(parseVersion(r.substring(r.indexOf('@') + 1))) : versions.lastEntry().getValue());
    }

    /** Whether an item of a panels list is a use of a block ({@code { use: name@N, with: {...} }}). */
    public static boolean isUse(PNode item) {
        return item != null && item.isMap() && item.map().containsKey("use");
    }

    /**
     * How many panels each item of a written panels list stands for once expanded: 1 for a panel, the block's panel count for a use
     * (0 when the use does not resolve). The design operations map a panel's place among the expanded panels to its written item.
     */
    public static List<Integer> sizes(List<PNode> panels, FragmentResolver fragments) {
        List<Integer> out = new ArrayList<>();
        for (PNode p : panels) {
            out.add(isUse(p) ? expand(List.of(p), "studio", new ArrayList<>(), fragments).size() : 1);
        }
        return out;
    }

    /**
     * The panels list with every {@code use} replaced by the panels of the block {@code fragments} finds (parameters filled); problems
     * go to {@code problems}. A list without a use comes back as it is.
     */
    public static List<PNode> expand(List<PNode> panels, String file, List<SutraProblem> problems, FragmentResolver fragments) {
        return expand(panels, file, problems, new ArrayList<>(), fragments, null, new Calls(null));
    }

    /**
     * As {@link #expand(List, String, List, FragmentResolver)} for a Sutra read at a language level: a use of a typed fragment below
     * level {@value TypedFragment#SINCE} is {@code RCH-2008}. A null level checks no level (the design tools' view of a written list).
     */
    public static List<PNode> expand(List<PNode> panels, String file, List<SutraProblem> problems, FragmentResolver fragments, String level) {
        return expand(panels, file, problems, fragments, level, new Calls(null));
    }

    /**
     * What the uses of one panels list share (decision L-21, "fragments are functions"): what the caller knows about its own tables, and
     * what the uses give back: the public events of each {@code as:} name and the advisories ({@code RCH-2120}).
     */
    public static final class Calls implements TypedFragment.Caller {
        private final java.util.function.Function<String, Map<String, String>> columns;
        private final Map<String, String> events = new LinkedHashMap<>();
        private final List<SutraProblem> advisories = new ArrayList<>();
        private FragmentResolver fragments = FragmentResolver.none();

        /** @param columns the columns (name to type) of the table an expression gives, or null when unknown; null for none known */
        public Calls(java.util.function.Function<String, Map<String, String>> columns) {
            this.columns = columns;
        }

        @Override
        public Map<String, String> columns(String expression) {
            return columns == null ? null : columns.apply(expression);
        }

        @Override
        public PNode contract(Block fragment, String name) {
            return fragments.contract(fragment, name).map(t -> parse(t, "contracts/" + name + ".json")).orElse(null);
        }

        /** The advisories the uses gave ({@code RCH-2120}), in order. */
        public List<SutraProblem> advisories() {
            return advisories;
        }

        private final List<com.ash.rachana.model.StateEntry> state = new ArrayList<>();
        private final Map<String, TypedFragment.Stateful> stateful = new LinkedHashMap<>();

        /** The private state the uses brought ({@code <as>.<name>}, S1), in order. */
        public List<com.ash.rachana.model.StateEntry> state() {
            return state;
        }

        private final Set<String> nested = new java.util.LinkedHashSet<>();
        /** The Sutra's state, id to type, once read (the checks of {@link #checkNested} use it). */
        Map<String, String> states = Map.of();
        private final List<java.util.function.Consumer<List<SutraProblem>>> checks = new ArrayList<>();

        /**
         * The stateful uses made inside a fragment ({@code <outer-as>-<inner-as>}, LIBRARIES.md 7.2): the screen reads none of them; only the
         * fragment that holds the use reads its outputs.
         */
        Set<String> nested() {
            return java.util.Collections.unmodifiableSet(nested);
        }

        /** Adds to {@code out} what the fragments read of their nested uses that is not an output ({@code RCH-2117}); run once all are bound. */
        void checkNested(List<SutraProblem> out) {
            int from = out.size();
            checks.forEach(c -> c.accept(out));
            placeIn(out, from);
        }

        /** The texts of the fragment files read by the uses, by file name: where their problems are placed (SPEC 6.1). */
        private final Map<String, String> texts = new LinkedHashMap<>();

        /** The problems of {@code out} from {@code from} on that are located in a fragment file read, placed in that file's text. */
        void placeIn(List<SutraProblem> out, int from) {
            for (int i = from; i < out.size(); i++) {
                SutraProblem p = out.get(i);
                String text = p.path() == null && p.location() != null ? texts.get(p.location().file()) : null;
                if (text != null) {
                    out.set(i, ProblemPlaces.of(text, p.location().file()).place(p));
                }
            }
        }

        /** The uses that hold private state, by their {@code as}: what the screen may read of each (its outputs). */
        public Map<String, TypedFragment.Stateful> stateful() {
            return stateful;
        }
    }

    /**
     * As {@link #expand(List, String, List, FragmentResolver, String)} with what the caller knows of its tables, collecting what the uses
     * give back into {@code calls}; a read of {@code @<as>.<event>} anywhere in the list is moved to the panel that raises the event.
     */
    public static List<PNode> expand(List<PNode> panels, String file, List<SutraProblem> problems, FragmentResolver fragments, String level, Calls calls) {
        calls.fragments = fragments;
        int from = problems.size();
        List<PNode> out = expand(panels, file, problems, new ArrayList<>(), fragments, level, calls);
        calls.placeIn(problems, from);
        return calls.events.isEmpty() ? out : out.stream().map(p -> events(p, calls.events)).toList();
    }

    /** {@code @<as>.<event>} read as {@code @<panel>.<event>}, the panel of the use named {@code as} that raises it. */
    private static PNode events(PNode n, Map<String, String> events) {
        if (n.isMap()) {
            Map<String, PNode> m = new LinkedHashMap<>();
            n.map().forEach((k, v) -> m.put(k, events(v, events)));
            return new PNode(m, n.line(), n.column());
        }
        if (n.isList()) {
            List<PNode> l = new ArrayList<>();
            n.list().forEach(v -> l.add(events(v, events)));
            return new PNode(l, n.line(), n.column());
        }
        if (!(n.value() instanceof String s) || s.indexOf('@') < 0) {
            return n;
        }
        Matcher m = java.util.regex.Pattern.compile("(?<![A-Za-z0-9_$@.-])@([a-z][a-z0-9-]*\\.[a-z][a-z0-9-]*)(\\.[A-Za-z_][A-Za-z0-9_]*)?(?![A-Za-z0-9_-])")
                .matcher(s);
        StringBuilder sb = new StringBuilder();
        int copied = 0;
        while (m.find()) {
            String field = m.group(2) == null ? "" : m.group(2);
            String whole = events.get(m.group(1) + field);
            String to = whole != null ? whole : events.containsKey(m.group(1)) ? events.get(m.group(1)) + field : null;
            sb.append(s, copied, m.start()).append((to == null ? m.group() : "@" + to));
            copied = m.end();
        }
        sb.append(s, copied, s.length());
        return new PNode(sb.toString(), n.line(), n.column());
    }

    /** Whether language level {@code level} is at least {@code since} ({@code 1.10} after {@code 1.9}); a null level is any level. */
    public static boolean atLeast(String level, String since) {
        if (level == null) {
            return true;
        }
        String[] a = level.split("\\."), b = since.split("\\.");
        int major = Integer.parseInt(a[0]) - Integer.parseInt(b[0]);
        return major != 0 ? major > 0 : Integer.parseInt(a[1]) >= Integer.parseInt(b[1]);
    }

    /** The deepest a use may nest: a Sutra's use of a composite fragment that uses fragments that use fragments. */
    public static final int MAX_DEPTH = 3;

    private static List<PNode> expand(List<PNode> panels, String file, List<SutraProblem> problems, List<String> chain, FragmentResolver fragments,
            String level, Calls calls) {
        if (panels.stream().noneMatch(p -> p.map().containsKey("use"))) {
            return panels;
        }
        List<PNode> out = new ArrayList<>();
        for (PNode p : panels) {
            PNode use = p.map().get("use");
            if (use == null) {
                out.add(p);
                continue;
            }
            String ref = use.text() == null ? "" : use.text().trim();
            Block b = fragments.find(ref).orElse(null);
            if (b != null && chain.contains(b.name())) {
                problems.add(problem("fragment " + b.name() + " uses itself through " + String.join(" > ", chain) + " > " + b.name(), file, p.line(), p.column()));
                continue;
            }
            if (b != null && chain.size() >= MAX_DEPTH) {
                problems.add(problem("fragments nest at most " + MAX_DEPTH + " deep: " + String.join(" > ", chain) + " > " + b.name(), file, p.line(), p.column()));
                continue;
            }
            if (b == null) {
                problems.add(problem("no block " + ref + (fragments.names().isEmpty() ? " (no block files are loaded)" : " (blocks: "
                        + String.join(", ", fragments.names()) + ")"), file, p.line(), p.column()));
                continue;
            }
            calls.texts.put(b.file(), b.text());
            PNode typedRoot = typedRoot(b);
            if (typedRoot != null) {                                   // the typed form: checked parameters, tokens, slots (C1)
                if (!atLeast(level, TypedFragment.SINCE)) {
                    problems.add(new SutraProblem("RCH-2008", "fragment " + b.ref() + " is in the typed form, which needs Rachana " + TypedFragment.SINCE
                            + "; this host runs " + level + " (raise the host's language level)", new SourceLocation(file, p.line(), p.column())));
                    continue;
                }
                TypedFragment t = TypedFragment.read(b.name(), b.file(), typedRoot);
                if (!t.problems().isEmpty() || !t.outsideScope().isEmpty()) {     // hermetic like a function (L-21): parameters only
                    problems.addAll(inFile(t.problems(), b.text(), b.file()));
                    problems.addAll(inFile(t.outsideScope(), b.text(), b.file()));
                    continue;
                }
                TypedFragment.Bound bound = t.bind(b, p, file, problems, calls);
                if (bound == null) {
                    continue;
                }
                List<PNode> brought = bound.panels();
                calls.events.putAll(bound.events());
                if (!bound.state().isEmpty()) {                           // S1: the use's private state, <as>.<name>
                    calls.state.addAll(bound.state());
                    calls.stateful.put(bound.state().get(0).owner(), new TypedFragment.Stateful(b.name(), t.stateNames(), t.outputs().keySet()));
                    if (!chain.isEmpty()) {
                        calls.nested.add(bound.state().get(0).owner());
                    }
                }
                PNode with = p.map().get("with");
                t.stateParams().forEach((param, of) -> {               // LIBRARIES 6.3: the handed name is a state the Sutra holds, of that type
                    PNode v = with != null && with.isMap() && with.map().get(param) != null ? with.map().get(param) : t.params().get(param).dflt();
                    if (v != null && v.text() != null) {
                        calls.checks.add(problemsOut -> {
                            String type = calls.states.get(v.text());
                            String why = type == null ? "names state '" + v.text() + "', which the Sutra does not hold" + (calls.states.isEmpty() ? " (declare "
                                    + "it under state:)" : " (it holds " + String.join(", ", calls.states.keySet()) + ")")
                                    : of != null && !of.equals(type) ? "names state '" + v.text() + "', a " + type + "; it takes a state of type " + of : null;
                            if (why != null) {
                                problemsOut.add(TypedFragment.problem(TypedFragment.WRONG_TYPE, "parameter " + param + " of fragment " + b.name() + " " + why,
                                        file, v));
                            }
                        });
                    }
                });
                Set<String> inner = NestedUses.names(typedRoot.map().get("panels"));
                if (!inner.isEmpty()) {
                    String outer = p.map().get("as") == null ? null : p.map().get("as").text();       // what it reads of its nested uses, checked once every use is bound
                    calls.checks.add(problemsOut -> NestedUses.checkReads(b.name(), typedRoot.map().get("panels"), outer, inner, calls.stateful, b.file(),
                            problemsOut));
                }
                calls.advisories.addAll(bound.advisories());
                List<String> deeper = new ArrayList<>(chain);
                deeper.add(b.name());
                Map<String, PNode> wiring = new LinkedHashMap<>(p.map());
                wiring.remove("slots");
                wiring.remove("as");
                wiring.remove("on");
                out.addAll(wired(new PNode(wiring, p.line(), p.column()), expand(phoned(brought, typedRoot), b.file(), problems, deeper, fragments, level, calls),
                        file, problems));
                continue;
            }
            Map<String, String> values = new LinkedHashMap<>(b.params());
            PNode with = p.map().get("with");
            boolean ok = true;
            if (with != null) {
                for (Map.Entry<String, PNode> e : with.map().entrySet()) {
                    if (!b.params().containsKey(e.getKey())) {
                        problems.add(problem("block " + b.name() + " has no parameter '" + e.getKey() + "' (it has " + b.params().keySet() + ")",
                                file, e.getValue().line(), e.getValue().column()));
                        ok = false;
                    } else {
                        values.put(e.getKey(), flow(e.getValue()));
                    }
                }
            }
            if (!ok) {
                continue;
            }
            Matcher pm = PARAM.matcher(b.text());
            StringBuilder filled = new StringBuilder();
            int copied = 0;
            while (pm.find()) {
                String v = values.get(pm.group(1));
                if (v == null) {
                    problems.add(problem("block " + b.name() + " reads %{" + pm.group(1) + "}, which it does not declare under params", b.file(), 1, 1));
                    v = "";
                }
                filled.append(b.text(), copied, pm.start()).append(v);
                copied = pm.end();
            }
            filled.append(b.text(), copied, b.text().length());
            try {
                PNode root = parse(filled.toString(), b.file());
                List<PNode> brought = phoned(root.map().get("panels").list(), root);
                List<String> deeper = new ArrayList<>(chain);
                deeper.add(b.name());
                out.addAll(wired(p, expand(brought, b.file(), problems, deeper, fragments, level, calls), file, problems));   // a composite's own uses, one level down
            } catch (SutraException e) {
                problems.addAll(e.problems());
            }
        }
        return out;
    }

    /** A fragment's root read with {@code %{} masked when it is in the typed form; null for the text form or a file that does not read. */
    private static PNode typedRoot(Block b) {
        if (!b.fragment() || !b.text().contains("type")) {
            return null;
        }
        try {
            PNode root = parse(b.text().replace("%{", "pXq{"), b.file());
            return TypedFragment.isTyped(root) ? root : null;
        } catch (SutraException e) {
            return null;
        }
    }

    /**
     * A fragment's {@code doc.phone} (docs/guides/BUILD_GUIDE.md 5.4) put on each panel it brings that sets no phone tier of its own, marked
     * {@code from: fragment} so the view can say which layer chose the tier; a use's own {@code phone:} still wins (it is put on after).
     */
    private static List<PNode> phoned(List<PNode> brought, PNode root) {
        PNode doc = root.map().get("doc");
        PNode phone = doc == null ? null : doc.map().get("phone");
        if (phone == null || phone.value() == null) {
            return brought;
        }
        Map<String, PNode> tier = new LinkedHashMap<>();
        if (phone.isMap()) {
            tier.putAll(phone.map());
        } else {
            tier.put("tier", phone);
        }
        tier.put("from", new PNode("fragment", phone.line(), phone.column()));
        PNode marked = new PNode(tier, phone.line(), phone.column());
        List<PNode> out = new ArrayList<>(brought.size());
        for (PNode q : brought) {
            if (!q.isMap() || q.map().containsKey("phone") || q.map().containsKey("use")) {
                out.add(q);
                continue;
            }
            Map<String, PNode> m = new LinkedHashMap<>(q.map());
            m.put("phone", marked);
            out.add(new PNode(m, q.line(), q.column()));
        }
        return out;
    }

    /** Keys a use may carry beside use and with (RACHANA_REFERENCE.md 6 to 8): put on every panel it brings. */
    private static final java.util.Set<String> USE_ON_ALL = java.util.Set.of("source", "when", "waits", "refresh", "section", "collapsible", "order", "phone");
    /** Keys a use may carry only when it brings one panel: that panel's events, children and size. */
    private static final java.util.Set<String> USE_ON_ONE = java.util.Set.of("emits", "children", "span", "height", "area", "form");

    /**
     * The panels a use brings with the use's own wiring and size on them ({@code { use: series-line@1, with: {...}, source: {...} }}): source,
     * when and waits on each, emits, children, span, height and area only on a use that brings one panel ({@code RCH-2048} otherwise).
     */
    private static List<PNode> wired(PNode use, List<PNode> brought, String file, List<SutraProblem> problems) {
        Map<String, PNode> extra = new LinkedHashMap<>();
        use.map().forEach((k, v) -> {
            if ("use".equals(k) || "with".equals(k)) {
                return;
            }
            if (!USE_ON_ALL.contains(k) && !USE_ON_ONE.contains(k)) {
                problems.add(problem("a use takes use, with, and " + String.join(", ", new java.util.TreeSet<>(USE_ON_ALL)) + ", "
                        + String.join(", ", new java.util.TreeSet<>(USE_ON_ONE)) + ", not '" + k + "'", file, v.line(), v.column()));
            } else if (USE_ON_ONE.contains(k) && brought.size() != 1) {
                problems.add(problem("'" + k + "' on a use that brings " + brought.size() + " panels: it wires one panel; write the panel, or use a fragment that brings one",
                        file, v.line(), v.column()));
            } else {
                extra.put(k, v);
            }
        });
        if (extra.isEmpty()) {
            return brought;
        }
        List<PNode> out = new ArrayList<>(brought.size());
        for (PNode q : brought) {
            if (!q.isMap()) {
                out.add(q);
                continue;
            }
            Map<String, PNode> m = new LinkedHashMap<>(q.map());
            m.putAll(extra);
            out.add(new PNode(m, q.line(), q.column()));
        }
        return out;
    }

    private static int parseVersion(String v) {
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static PNode parse(String text, String file) {
        try {
            return READER.read(text, new ArrayList<>());
        } catch (YamlSyntaxException e) {
            throw new SutraException(List.of(new SutraProblem("RCH-2001", "YAML syntax: " + e.getMessage(),
                    new SourceLocation(file, e.line(), e.column()))));
        }
    }

    static SutraProblem problem(String message, String file, int line, int column) {
        return new SutraProblem(BLOCK, message, new SourceLocation(file, line, column));
    }
}
