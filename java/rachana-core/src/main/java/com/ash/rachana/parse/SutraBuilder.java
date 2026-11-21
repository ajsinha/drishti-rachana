/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.parse;

import com.ash.rachana.SutraProblem;
import com.ash.rachana.model.Area;
import com.ash.rachana.model.Column;
import com.ash.rachana.model.Panel;
import com.ash.rachana.model.PanelKind;
import com.ash.rachana.model.PanelOptions;
import com.ash.rachana.model.PivotSpec;
import com.ash.rachana.model.ScreenPhone;
import com.ash.rachana.model.SourceLocation;
import com.ash.rachana.model.StripItem;
import com.ash.rachana.model.Sutra;
import com.ash.rachana.model.Title;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a {@link PNode} tree into a {@link Sutra}, collecting every problem with its line and column.
 * One builder per file; not thread-safe.
 *
 * <p>Codes: 2001 YAML syntax, 2010 missing key, 2011 unknown key, 2012 wrong type, 2020 bad name or
 * version, 2021 unknown panel kind, 2022 missing kind option, 2023 option not valid for kind, 2024
 * duplicate panel id, 2025 duplicate or invalid function key, 2026 strip too long, 2027 bad area, 2029 an option
 * value the kind does not allow ({@link PanelOptions}), 2030 a bad layout size ({@code span} or {@code height}).
 */
final class SutraBuilder {

    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9-]{1,63}");
    private static final Pattern FKEY = Pattern.compile("F([1-9]|1[0-2])");
    private static final int LANGUAGE = SutraParser.LANGUAGE;
    private static final Set<String> PANEL = Set.of("id", "kind", "title", "key", "code", "area", "infer", "columns", "body", "panels", "description",
            Panel.SPAN, Panel.HEIGHT, com.ash.rachana.model.Refresh.KEY, Panel.ORDER, Panel.SECTION, Panel.COLLAPSIBLE, com.ash.rachana.model.Form.KEY,
            ScreenPhone.KEY);
    private static final Set<String> STRIP = Set.of("label", "bind", "fmt", "tone", "emphasis");
    private static final Set<String> COLUMN = Set.of("label", "bind", "fmt", "tone", "total", "link");

    private final String file;
    /** Where the text it builds from places a problem (SPEC 6.1); null when it was not read from a text. */
    ProblemPlaces places;
    private final com.ash.rachana.profile.Grammar grammar;
    private final List<SutraProblem> problems = new ArrayList<>();
    /** The level its {@code rachana:} key declares ({@link SutraParser#declaredLevel}), or null for none or another form. */
    String declared;
    private FragmentResolver fragments;
    private SutraBlocks.Calls calls = new SutraBlocks.Calls(null);

    SutraBuilder(String file, com.ash.rachana.profile.Grammar grammar) {
        this.file = file;
        this.grammar = grammar;
    }

    List<SutraProblem> problems() {
        return problems;
    }

    Sutra build(PNode root, String domain) {
        if (!root.isMap()) {
            problem("RCH-2012", "a Sutra must be a mapping", root);
            return null;
        }
        Map<String, PNode> m = root.map();
        m.forEach((k, v) -> {
            if (!grammar.hasKey(k) && com.ash.rachana.profile.Grammar.keySince(k) != null) {    // a later level's key, used below it
                problem("RCH-2008", "'" + k + "' needs Rachana " + com.ash.rachana.profile.Grammar.keySince(k) + "; "
                        + grammar.levelWords(com.ash.rachana.profile.Grammar.keySince(k)), v);
            } else if (!grammar.hasKey(k) && (k.equals("match") || k.equals("domain")) && SutraBlocks.atLeast(grammar.level(), "1.1")) {
                problem("RCH-2060", "'" + k + "' belongs to the profile of a data platform; a Core screen reads its data from 'data:'", v);   // SPEC 2.2
            } else if (!grammar.hasKey(k)) {
                problem("RCH-2011", "unknown key '" + k + "' in top level", v);
            }
        });
        List<com.ash.rachana.model.UsedLibrary> used = new ArrayList<>();
        Map<String, com.ash.rachana.el.Declarations.Function> libraryFunctions = new LinkedHashMap<>();
        fragments = SutraBlocks.atLeast(grammar.level(), TypedFragment.SINCE)     // level 1.2: libraries: and library uses (LIBRARIES.md 7)
                ? grammar.libraries().fragments(grammar.fragments(), root, file, problems,
                        l -> {
                            used.add(new com.ash.rachana.model.UsedLibrary(l.name(), l.version(), l.sha384()));
                            libraryFunctions.putAll(l.functions());                 // X42: called <library>.<function>(...)
                        }) : grammar.fragments();
        PNode lang = m.get("rachana");
        if (lang == null) {
            problem("RCH-2009", "missing 'rachana: " + LANGUAGE + "' (the Rachana language version) at the top", root);
        } else if (declared != null && com.ash.rachana.profile.Grammar.LEVELS.contains(declared)) {
            if (!SutraBlocks.atLeast(grammar.level(), declared)) {           // SPEC 2.1: a Sutra of a later level than its host's
                problem("RCH-2008", "this Sutra is written for Rachana " + declared + "; this host runs " + grammar.level()
                        + " (raise the host's language level)", lang);
            }
        } else if (declared != null) {                                       // a minor this runtime does not know
            problem("RCH-2009", "'rachana: " + declared + "' is a language level this host does not read; the newest it reads is "
                    + grammar.level() + " (rachana: 1 is 1.0)", lang);
        } else if (!(lang.value() instanceof Long l) || l != LANGUAGE) {
            problem("RCH-2009", "'rachana: " + lang.value() + "' is not a language version this server reads (it reads " + LANGUAGE + ")", lang);
        }
        for (String doc : new String[] {"description", "notes"}) {
            PNode d = m.get(doc);
            if (d != null && !(d.value() instanceof String)) {
                problem("RCH-2012", "'" + doc + "' is plain text", d);
            }
        }
        Object screenRefresh = refresh(m.get("refresh"));   // RACHANA_REFERENCE.md 9: on every top-level panel without its own
        String name = requiredText(m, "sutra", root);
        if (name != null && !NAME.matcher(name).matches()) {
            problem("RCH-2020", "name '" + name + "' must be lower-case kebab, 2-64 characters", m.get("sutra"));
        }
        int version = 0;
        PNode v = m.get("version");
        if (v == null) {
            problem("RCH-2010", "missing 'version'", root);
        } else if (!(v.value() instanceof Long l) || l < 1 || l > Integer.MAX_VALUE) {
            problem("RCH-2020", "version must be a positive integer up to " + Integer.MAX_VALUE + ", not '" + v.value() + "'", v);
        } else {
            version = l.intValue();
        }
        Map<String, Object> extensions = new LinkedHashMap<>();             // the profiles' keys (split step A7)
        com.ash.rachana.profile.SutraKey.Context keyContext = keyContext(domain, root);
        grammar.keys().forEach((keyName, key) -> {
            Object value = key.read(m.get(keyName), keyContext);
            if (value != null) {
                extensions.put(keyName, value);
            }
        });
        if (m.containsKey(com.ash.rachana.model.DataEntry.KEY) && grammar.hasKey(com.ash.rachana.model.DataEntry.KEY)) {   // level 1.1 (D10)
            extensions.put(com.ash.rachana.model.DataEntry.KEY, new DataReader(file, problems).read(m.get(com.ash.rachana.model.DataEntry.KEY)));
        }
        calls = new SutraBlocks.Calls(callerColumns(extensions.get(com.ash.rachana.model.DataEntry.KEY)));
        calls.levelWords = grammar::levelWords;
        Title title = title(m.get("title"));
        List<StripItem> strip = strip(m.get("strip"));
        List<Panel> panels = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Set<String> keys = new HashSet<>();
        List<PNode> own = new ArrayList<>(listOf(m.get("panels"), "panels"));
        if (m.get("panels") != null) {
            written.addAll(m.get("panels").list());
        }
        grammar.keys().forEach((keyName, key) -> {                               // the panels a key brings (match: the sections modules contribute)
            List<PNode> brought = key.panels(extensions.get(keyName));
            if (!brought.isEmpty()) {
                own.addAll(SutraBlocks.expand(brought, file, problems, fragments(), grammar.level(), calls));
            }
        });
        for (PNode p : own) {
            Panel panel = panel(p, false);
            if (panel != null) {
                if (!ids.add(panel.id())) {
                    problem("RCH-2024", "duplicate panel id '" + panel.id() + "'", idAt(p));
                }
                if (panel.key() != null && !keys.add(panel.key())) {
                    problem("RCH-2025", "function key " + panel.key() + " is used twice", p);
                }
                panels.add(panel);
            } else if (SutraBlocks.atLeast(grammar.level(), "1.1") && p.isMap() && p.map().get("id") != null && p.map().get("id").isScalar()) {
                if (!ids.add(p.map().get("id").text())) {     // level 1.1 (SPEC 6.1: all problems): a panel refused for its kind still holds its id
                    problem("RCH-2024", "duplicate panel id '" + p.map().get("id").text() + "'", idAt(p));
                }
            }
        }
        panels.replaceAll(p -> p.kind() == PanelKind.LIVE ? live(p, extensions.get(com.ash.rachana.model.DataEntry.KEY)) : p);
        if (screenRefresh != null) {
            panels.replaceAll(p -> p.options().containsKey(com.ash.rachana.model.Refresh.KEY) ? p : withOption(p, com.ash.rachana.model.Refresh.KEY, screenRefresh));
        }
        childIds(panels, new HashSet<>(ids));
        LayoutReader layoutReader = new LayoutReader(file, problems);          // layout: the grid, areas, split, tabs, sections (RACHANA_REFERENCE.md 13)
        com.ash.rachana.model.ScreenLayout layout = layoutReader.read(m.get("layout"));
        layoutReader.check(layout, panels, m.getOrDefault("layout", root));
        PhoneReader phoneReader = new PhoneReader(file, problems);            // phone: order, default tier, summary (docs/guides/BUILD_GUIDE.md 5.2)
        ScreenPhone phone = phoneReader.screen(m.get(ScreenPhone.KEY));
        phoneReader.check(phone, panels, m.getOrDefault(ScreenPhone.KEY, root));
        ControlsReader controlsReader = new ControlsReader(file, problems, SutraBlocks.atLeast(grammar.level(), "1.2"));
        List<com.ash.rachana.model.Control> controls = controlsReader.read(m.get("controls"), ids);
        controlsReader.checkSelects(panels, controls);
        Map<String, String> keyMap = new LinkedHashMap<>();
        PNode k = m.get("keys");
        if (k != null) {
            if (!k.isMap()) {
                problem("RCH-2012", "'keys' must be a mapping of F-key to action", k);
            } else {
                k.map().forEach((key, action) -> {
                    if (!FKEY.matcher(key).matches()) {
                        problem("RCH-2025", "'" + key + "' is not a function key (F1-F12)", action);
                    } else if (!keys.add(key)) {
                        problem("RCH-2025", "function key " + key + " is used twice", action);
                    } else if (action.text() == null) {
                        problem("RCH-2012", "action for " + key + " must be text", action);
                    } else {
                        keyMap.put(key, action.text());
                    }
                });
            }
        }
        List<SutraProblem> declAdvisories = new ArrayList<>();
        Map<String, com.ash.rachana.el.Declarations.Function> fns = new LinkedHashMap<>(libraryFunctions);
        Map<String, String> lets = new LinkedHashMap<>();
        Set<String> taken = new HashSet<>();
        controls.forEach(c -> taken.add(c.id()));
        if (extensions.get(com.ash.rachana.model.DataEntry.KEY) instanceof List<?> entries) {
            entries.forEach(e -> taken.add(((com.ash.rachana.model.DataEntry) e).name()));
        }
        if ((m.containsKey("let") || m.containsKey("functions")) && grammar.hasKey("let")) {   // level 1.2: X41, S2
            DeclarationsReader reader = new DeclarationsReader(file, problems, declAdvisories);
            fns.putAll(reader.functions(m.get("functions"), ""));
            lets.putAll(reader.lets(m.get("let"), taken));
            for (String l : lets.keySet()) {
                if (fns.containsKey(l)) {
                    problem("RCH-2024", "'" + l + "' is both a let and a function of this Sutra", m.get("let").map().get(l));
                }
            }
        }
        List<com.ash.rachana.model.StateEntry> state = new ArrayList<>();   // level 1.2: S1, the screen's own state, then its fragments' private state
        EffectsReader effects = new EffectsReader(file, problems);
        if (m.containsKey("state") && grammar.hasKey("state")) {
            Set<String> names = new HashSet<>(taken);
            names.addAll(lets.keySet());
            state.addAll(effects.state(m.get("state"), names));
        }
        state.addAll(calls.state());
        if (state.size() > com.ash.rachana.model.StateEntry.MAX) {
            problem("RCH-2012", "a Sutra holds at most " + com.ash.rachana.model.StateEntry.MAX + " state values, its fragments' private state included; "
                    + "this one holds " + state.size(), m.getOrDefault("state", root));
        }
        effects.check(panels, state);
        effects.checkControls(controlsReader.effects(), state, controlsReader.effectsAt());
        if (!controlsReader.effects().isEmpty()) {
            extensions.put(com.ash.rachana.model.Effects.CONTROLS_KEY, java.util.Collections.unmodifiableMap(new LinkedHashMap<>(controlsReader.effects())));
        }
        if (!calls.stateful().isEmpty()) {
            privateState(root, calls.stateful(), calls.nested());
        }
        Map<String, String> stateTypes = new LinkedHashMap<>();
        state.forEach(e -> stateTypes.put(e.id(), e.type()));
        calls.states = stateTypes;
        calls.checkNested(problems);
        com.ash.rachana.el.Declarations declarations = !fns.isEmpty() || !lets.isEmpty() || !stateTypes.isEmpty()
                ? new com.ash.rachana.el.Declarations(fns, lets, stateTypes) : com.ash.rachana.el.Declarations.NONE;
        if (grammar.declaredBelowHost()) {                                   // SPEC 2.1: its expressions compile at its own level
            declarations = declarations.atLevel(grammar.level());
        }
        if (declarations != com.ash.rachana.el.Declarations.NONE) {
            extensions.put(Sutra.DECLARATIONS, declarations);
        }
        extensions.put(Sutra.LEVEL, grammar.level());
        if (!state.isEmpty()) {
            extensions.put(Sutra.STATE, List.copyOf(state));
        }
        if (!problems.isEmpty()) {
            return null;
        }
        if (!used.isEmpty()) {
            extensions.put(Sutra.LIBRARIES, List.copyOf(used));
        }
        if (places != null) {
            extensions.put(Sutra.PLACES, places);
        }
        if (!calls.advisories().isEmpty() || !declAdvisories.isEmpty()) {
            List<SutraProblem> all = new ArrayList<>(declAdvisories);
            all.addAll(calls.advisories());
            all.replaceAll(SutraProblem::warning);                      // advisories never stop a Sutra from loading
            extensions.put(Sutra.ADVISORIES, List.copyOf(places == null ? all : places.place(all)));
        }
        return new Sutra(name, version, title, strip, panels, keyMap, loc(root), text(m.get("description"), null), controls, layout, phone,
                extensions);
    }

    /**
     * Where a duplicate id is reported: from level 1.1 the id itself (SPEC 6.1: the problem's path is {@code /panels/<n>/id}), at
     * level 1.0 the panel, as that level was recorded.
     */
    private PNode idAt(PNode panel) {
        PNode id = panel.map().get("id");
        return id != null && SutraBlocks.atLeast(grammar.level(), "1.1") && written.contains(panel) ? id : panel;
    }

    /** The panels written in this file (not brought by a fragment or a key), by identity: their id is where it is written. */
    private final Set<PNode> written = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    private static final Pattern PRIVATE_READ = Pattern.compile("(?<![A-Za-z0-9_.$@])state\\.([a-z][a-z0-9-]*)\\.([A-Za-z][A-Za-z0-9_]*)");

    /**
     * What the Sutra's own text (never a fragment's: those are not in it) does with a fragment use's private state (S1, LIBRARIES.md 7.2):
     * it may read an output, {@code state.<as>.<output>}; reading any other private state, or writing one (an output too), is
     * {@code RCH-2117}.
     */
    private void privateState(PNode n, Map<String, com.ash.rachana.parse.TypedFragment.Stateful> uses, Set<String> nested) {
        if (n.isMap()) {
            n.map().forEach((k, v) -> {
                if ("set".equals(k) && v.isMap() && v.map().get("state") != null && v.map().get("state").text() != null) {
                    String target = v.map().get("state").text();
                    int dot = target.indexOf('.');
                    if (dot > 0 && nested.contains(target.substring(0, dot))) {
                        problem("RCH-2117", "state '" + target + "' belongs to a fragment used inside another (" + uses.get(target.substring(0, dot)).fragment()
                                + "): the screen reads only the outputs of its own uses", v.map().get("state"));
                    } else if (dot > 0 && uses.containsKey(target.substring(0, dot))) {
                        problem("RCH-2117", "state '" + target + "' belongs to the use " + target.substring(0, dot) + " of fragment "
                                + uses.get(target.substring(0, dot)).fragment() + ": the screen reads its outputs, never writes them", v.map().get("state"));
                    }
                }
                privateState(v, uses, nested);
            });
        } else if (n.isList()) {
            n.list().forEach(v -> privateState(v, uses, nested));
        } else if (n.value() instanceof String s && s.contains("state.")) {
            Matcher m = PRIVATE_READ.matcher(s);
            while (m.find()) {
                var use = uses.get(m.group(1));
                if (use != null && nested.contains(m.group(1))) {
                    problem("RCH-2117", "state." + m.group(1) + "." + m.group(2) + " belongs to a use of fragment " + use.fragment() + " made inside "
                            + "another fragment: the screen reads only the outputs of its own uses", n);
                } else if (use != null && !use.outputs().contains(m.group(2))) {
                    problem("RCH-2117", "state." + m.group(1) + "." + m.group(2) + " is private to the use " + m.group(1) + " of fragment " + use.fragment()
                            + (use.outputs().isEmpty() ? ", which publishes no outputs" : "; the screen reads its outputs: " + String.join(", ", use.outputs())), n);
                }
            }
        }
    }

    private com.ash.rachana.profile.SutraKey.Context keyContext(String folder, PNode root) {
        return new com.ash.rachana.profile.SutraKey.Context() {
            @Override
            public String file() {
                return file;
            }

            @Override
            public String folder() {
                return folder;
            }

            @Override
            public PNode root() {
                return root;
            }

            @Override
            public void problem(String code, String message, PNode at) {
                SutraBuilder.this.problem(code, message, at);
            }
        };
    }

    private Title title(PNode n) {
        if (n == null) {
            return new Title(null, "$.id", null);
        }
        if (!n.isMap()) {
            problem("RCH-2012", "'title' must be a mapping (pill, id, with)", n);
            return null;
        }
        unknownKeys(n.map(), Set.of("pill", "id", "with"), "title");
        if (n.map().get("id") == null || n.map().get("id").value() == null) {
            // without 'title' the default is { id: $.id }; a title written out says which expression is the identifier
            problem("RCH-2010", "missing 'id' in title: the expression for the large identifier, such as $.TRADE_ID", n);
        }
        return new Title(text(n.map().get("pill"), null), text(n.map().get("id"), "$.id"), text(n.map().get("with"), null));
    }

    private List<StripItem> strip(PNode n) {
        List<StripItem> out = new ArrayList<>();
        List<PNode> items = listOf(n, "strip");
        if (items.size() > com.ash.rachana.model.Sutra.MAX_STRIP) {
            problem("RCH-2026", "the strip holds at most " + Sutra.MAX_STRIP + " figures, found " + items.size(), n);
        }
        for (PNode i : items) {
            if (!i.isMap()) {
                problem("RCH-2012", "a strip item must be a mapping (label, bind, fmt, tone, emphasis)", i);
                continue;
            }
            Map<String, PNode> m = i.map();
            unknownKeys(m, STRIP, "strip item");
            out.add(new StripItem(text(m.get("label"), null), requiredText(m, "bind", i), text(m.get("fmt"), null),
                    text(m.get("tone"), null), bool(m.get("emphasis")), loc(i)));
        }
        return out;
    }

    private Panel panel(PNode n, boolean nested) {
        if (!n.isMap()) {
            problem("RCH-2012", "a panel must be a mapping", n);
            return null;
        }
        Map<String, PNode> m = n.map();
        String kindText = requiredText(m, "kind", n);
        PanelKind kind = kindText == null ? null : PanelKind.parse(kindText).filter(grammar::accepts).orElse(null);
        PanelKind later = kindText == null || kind != null ? null : PanelKind.parse(kindText).filter(grammar::known).orElse(null);
        if (later != null) {                                        // SUTRA_EXPANSION 6.2: a later level's name, used below it
            problem("RCH-2008", "'" + later.id() + "' panels need Rachana " + later.since() + "; " + grammar.levelWords(later.since()),
                    m.get("kind"));
            return null;
        }
        if (kindText != null && kind == null) {
            problem("RCH-2021", "unknown panel kind '" + kindText + "'; expected one of " + kinds(), m.get("kind"));
            return null;
        }
        if (nested && kind == PanelKind.LIVE) {
            problem("RCH-2023", "a live panel is a panel of the screen, not of a tab", m.get("kind"));
            return null;
        }
        String id = nested ? text(m.get("id"), "body") : requiredText(m, "id", n);
        String key = text(m.get("key"), null);
        if (key != null && !FKEY.matcher(key).matches()) {
            problem("RCH-2025", "'" + key + "' is not a function key (F1-F12)", m.get("key"));
        }
        Area area = Area.MAIN;
        String gridArea = null;
        String areaText = text(m.get("area"), null);
        if (areaText != null) {
            if ("main".equals(areaText) || "right".equals(areaText)) {
                area = Area.valueOf(areaText.toUpperCase(Locale.ROOT));
            } else if (!nested && AREA_NAME.matcher(areaText).matches()) {
                gridArea = areaText;                                       // a named area of layout.areas, checked with the layout
            } else {
                problem("RCH-2027", "area must be 'main', 'right' or an area named in layout.areas", m.get("area"));
            }
        }
        List<Column> columns = new ArrayList<>();
        for (PNode c : listOf(m.get("columns"), "columns")) {
            if (!c.isMap()) {
                problem("RCH-2012", "a column must be a mapping (label, bind, fmt, tone, total, link)", c);
                continue;
            }
            unknownKeys(c.map(), COLUMN, "column");
            columns.add(new Column(text(c.map().get("label"), null), requiredText(c.map(), "bind", c),
                    text(c.map().get("fmt"), null), text(c.map().get("tone"), null), bool(c.map().get("total")),
                    bool(c.map().get("link"))));
        }
        Panel body = null;
        if (m.containsKey("body")) {
            if (kind != PanelKind.TABS) {
                problem("RCH-2023", "only 'tabs' panels take a 'body'", m.get("body"));
            } else {
                body = panel(m.get("body"), true);
            }
        }
        List<Panel> parts = new ArrayList<>();
        if (m.containsKey("panels")) {
            if (kind != PanelKind.TABS) {
                problem("RCH-2023", "only 'tabs' panels take 'panels'", m.get("panels"));
            } else if (nested) {
                problem("RCH-2023", "a panel inside a tab cannot hold tabs of its own", m.get("panels"));
            } else {
                parts = parts(m.get("panels"));
            }
        }
        Map<String, Object> options = new LinkedHashMap<>();
        size(m, Panel.SPAN, Panel.MAX_SPAN, "columns of the 12-column grid", nested, options);
        size(m, Panel.HEIGHT, Panel.MAX_HEIGHT, "grid rows", nested, options);
        if (m.containsKey(com.ash.rachana.model.Refresh.KEY)) {
            if (nested) {
                problem("RCH-2012", "'refresh' refreshes a whole panel: put it on the tabs panel", m.get(com.ash.rachana.model.Refresh.KEY));
            } else {
                Object r = refresh(m.get(com.ash.rachana.model.Refresh.KEY));
                if (r != null) {
                    options.put(com.ash.rachana.model.Refresh.KEY, r);
                }
            }
        }
        placement(m, nested, gridArea, options);
        PNode phone = m.get(ScreenPhone.KEY);                                // docs/guides/BUILD_GUIDE.md 5.1: the panel's phone tier
        if (phone != null && phone.value() != null) {
            if (nested) {
                problem(LayoutReader.CODE, "'phone' sets a whole panel's tier: put it on the tabs panel", phone);
            } else {
                Map<String, Object> tier = new PhoneReader(file, problems).panel(phone, kind == null ? null : kind.id());
                if (tier != null) {
                    options.put(ScreenPhone.KEY, tier);
                }
            }
        }
        PNode form = m.get(com.ash.rachana.model.Form.KEY);          // RACHANA_REFERENCE.md 10: how people edit its records
        if (form != null) {
            Object raw = form.isScalar() ? form.value() : plain(form);
            List<String> why = nested ? List.of("a form edits a whole panel's records: put it on the tabs panel")
                    : !grammar.formSave() && raw instanceof Map<?, ?> fm && fm.containsKey("save")
                    ? List.of("'form.save' (where a submitted form goes) comes with a profile that brings it; this grammar has none")
                    : com.ash.rachana.model.Form.problems(raw, kind == PanelKind.FORM);
            why.forEach(w -> problem("RCH-2058", w, form));
            if (why.isEmpty()) {
                options.put(com.ash.rachana.model.Form.KEY, raw);
            }
        }
        boolean on = m.containsKey(com.ash.rachana.model.Effects.KEY) && SutraBlocks.atLeast(grammar.level(), "1.2");   // I1: the effect list
        if (on && kind != null) {
            if (nested) {
                problem("RCH-2012", "a panel inside a tab cannot have on: of its own", m.get(com.ash.rachana.model.Effects.KEY));
            } else {
                EffectsReader.Read r = new EffectsReader(file, problems).panel(kind.id(), m.get(com.ash.rachana.model.Effects.KEY));
                if (r != null && !r.sets().isEmpty()) {
                    options.put(com.ash.rachana.model.Effects.KEY, r.sets());
                }
                if (r != null && r.onSelect() != null) {
                    if (m.containsKey(Panel.ON_SELECT)) {
                        problem("RCH-2038", "panel '" + id + "' sets a control in on: and in on-select; one click sets one control", m.get(Panel.ON_SELECT));
                    } else {
                        options.put(Panel.ON_SELECT, r.onSelect());          // set: <control> is the panel's on-select: one mechanism
                    }
                }
            }
        }
        boolean actions = m.containsKey(com.ash.rachana.model.Effects.ACTIONS) && SutraBlocks.atLeast(grammar.level(), "1.2");   // I1: buttons
        if (actions && kind != null) {
            if (nested) {
                problem("RCH-2012", "a panel inside a tab cannot have actions: of its own; put them on the tabs panel", m.get(com.ash.rachana.model.Effects.ACTIONS));
            } else {
                List<Map<String, Object>> a = new EffectsReader(file, problems).actions(m.get(com.ash.rachana.model.Effects.ACTIONS));
                if (a != null) {
                    options.put(com.ash.rachana.model.Effects.ACTIONS, a);
                }
            }
        }
        if (kind != null) {
            m.forEach((opt, val) -> {
                if (PANEL.contains(opt) || WiringReader.isWiring(opt, val) || on && com.ash.rachana.model.Effects.KEY.equals(opt)
                        || actions && com.ash.rachana.model.Effects.ACTIONS.equals(opt)) {
                    return;
                }
                if (kind.accepts(opt) && !SutraBlocks.atLeast(grammar.level(), kind.since(opt))) {   // a later level's option of an older kind
                    problem("RCH-2008", "option '" + opt + "' of '" + kind.id() + "' panels needs Rachana " + kind.since(opt) + "; "
                            + grammar.levelWords(kind.since(opt)), val);
                } else if (!kind.accepts(opt)) {
                    problem("RCH-2023", "search".equals(opt) || Panel.PIVOT.equals(opt)
                            ? "option '" + opt + "' applies only to panels that show a table (table, ladder), not '" + kind.id() + "'"
                            : "option '" + opt + "' is not valid for '" + kind.id() + "' panels", val);
                } else if (Panel.PIVOT.equals(opt)) {
                    Object v = val.isScalar() ? val.value() : plain(val);
                    PivotSpec.parse(v, PivotSpec.Mode.PANEL, PivotSpec.fromColumns(columns)).problems()
                            .forEach(why -> problem("RCH-2031", why, val));
                    options.put(opt, v);
                } else if (val.value() != null) {
                    Object v = val.isScalar() ? val.value() : plain(val);
                    PanelOptions.problem(kind, opt, v).ifPresent(why -> problem("RCH-2029", why, val));
                    options.put(opt, v);
                }
            });
            if (kind == PanelKind.FORM) {
                formPanel(id, n, m, options);
            }
            if (options.containsKey("levels") && (options.containsKey("children") || options.containsKey(Panel.PIVOT))) {
                problem("RCH-2029", "a table with 'levels' drills into its rows level by level; it cannot also have "
                        + (options.containsKey("children") ? "'children' (a tree)" : "Pivot (the pivot screen)"), m.get("levels"));
            }
            for (String req : kind.required()) {
                if (!m.containsKey(req)) {
                    problem("RCH-2022", "'" + kind.id() + "' panel '" + id + "' needs option '" + req + "'", n);
                }
            }
        }
        com.ash.rachana.model.Wiring wiring = com.ash.rachana.model.Wiring.NONE;
        if (m.keySet().stream().anyMatch(k -> WiringReader.isWiring(k, m.get(k)))) {
            if (nested) {
                problem(WiringReader.EVENTS, "a panel inside a tab cannot have source:, emits:, waits: or children: of its own; wire the tabs panel", n);
            } else {
                wiring = wiring().read(m, kind);
            }
        }
        return new Panel(id, kind, text(m.get("title"), null), key, text(m.get("code"), null), area, bool(m.get("infer")),
                columns, body, options, loc(n), parts, wiring);
    }

    /**
     * A standalone form panel (level 1.2): its {@code form:} is required and is the panel itself (no {@code edit}, no {@code new});
     * {@code initial} maps a field of the form to an expression (its starting value); the button labels are text.
     */
    private void formPanel(String id, PNode n, Map<String, PNode> m, Map<String, Object> options) {
        PNode form = m.get(com.ash.rachana.model.Form.KEY);
        if (form == null) {
            problem("RCH-2022", "'form' panel '" + id + "' needs option 'form' (its fields)", n);
            return;
        }
        if (!(options.get(com.ash.rachana.model.Form.KEY) instanceof Map<?, ?> f)) {
            return;                                                      // reported with the form
        }
        for (Map.Entry<String, PNode> e : form.isMap() ? form.map().entrySet() : Map.<String, PNode>of().entrySet()) {
            if (List.of("edit", "new", "new-label", "key").contains(e.getKey())) {
                problem("RCH-2058", "a form panel's form is the panel itself: it takes no '" + e.getKey() + "' (that edits a table's rows)", e.getValue());
            }
        }
        Set<String> fields = new HashSet<>();
        if (f.get("fields") instanceof List<?> l) {
            l.forEach(x -> {
                if (x instanceof Map<?, ?> fm && fm.get("id") != null) {
                    fields.add(String.valueOf(fm.get("id")));
                }
            });
        }
        PNode initial = m.get("initial");
        if (initial != null) {
            if (!initial.isMap()) {
                problem("RCH-2029", "initial maps a field of the form to the expression of its starting value ({ qty: state.qty })", initial);
                options.remove("initial");
                return;
            }
            Map<String, Object> exprs = new LinkedHashMap<>();
            for (Map.Entry<String, PNode> e : initial.map().entrySet()) {
                String x = EffectsReader.expression(e.getValue());
                if (!fields.contains(e.getKey())) {
                    problem("RCH-2029", "initial names '" + e.getKey() + "', which is not a field of the form (" + String.join(", ", new java.util.TreeSet<>(fields)) + ")", e.getValue());
                } else if (x == null) {
                    problem("RCH-2029", "initial." + e.getKey() + " is an expression or a literal", e.getValue());
                } else {
                    exprs.put(e.getKey(), x);
                }
            }
            options.put("initial", exprs);
        }
        for (String label : List.of("submitLabel", "cancelLabel")) {
            if (m.get(label) != null && (m.get(label).isMap() || m.get(label).isList())) {
                problem("RCH-2029", label + " is the button's text", m.get(label));
            }
        }
    }

    private WiringReader wiringReader;

    private WiringReader wiring() {
        if (wiringReader == null) {
            wiringReader = new WiringReader(file, problems, list -> listOf(list, "panels"), c -> panel(c, false), grammar);
        }
        return wiringReader;
    }

    /** Every panel id of the screen, children at any depth included, must be unique: an event names its panel by id. */
    private void childIds(List<Panel> panels, Set<String> ids) {
        for (Panel p : panels) {
            for (var g : p.wiring().children()) {
                for (Panel c : g.panels()) {
                    if (!ids.add(c.id())) {
                        problems.add(new SutraProblem("RCH-2024", "duplicate panel id '" + c.id() + "': a child panel's id is unique on the whole screen",
                                c.location()));
                    }
                }
                childIds(g.panels(), ids);
            }
        }
    }

    /**
     * Panels a tab may hold: those that read a record or a list, and the simple charts of one element (a leg's notional profile and
     * fixings as lines, its flows as bars), so legs side by side each show their own.
     */
    private static final Set<PanelKind> TAB_PARTS = Set.of(PanelKind.KV, PanelKind.STATUS, PanelKind.TABLE, PanelKind.LADDER,
            PanelKind.HBAR, PanelKind.MARKDOWN, PanelKind.METRIC, PanelKind.LINE, PanelKind.AREA, PanelKind.TIMELINE);

    /** The {@code panels:} of a tabs panel: each needs an id (unique among them) and a kind a tab can hold. */
    private List<Panel> parts(PNode list) {
        List<Panel> out = new ArrayList<>();
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (PNode n : listOf(list, "panels")) {
            if (n.isMap() && !n.map().containsKey("id")) {
                problem("RCH-2010", "a panel inside a tab needs an 'id'", n);
                continue;
            }
            Panel part = panel(n, true);
            if (part == null) {
                continue;
            }
            if (part.kind() != null && !TAB_PARTS.contains(part.kind())) {
                problem("RCH-2023", "a '" + part.kind().id() + "' panel cannot sit inside a tab; use one of kv, status, table, ladder, hbar, markdown, metric, line, area, timeline", n);
            }
            if (part.options().containsKey(Panel.PIVOT)) {
                problem("RCH-2023", "a panel inside a tab cannot have Pivot (the pivot screen); put the table in a panel of its own", n);
            }
            if (!ids.add(part.id())) {
                problem("RCH-2024", "two panels of this tab are called '" + part.id() + "'", n);
            }
            out.add(part);
        }
        return out;
    }

    /** {@code span} and {@code height}: whole numbers within bounds, on top-level panels only; kept with the options. */
    /** {@code refresh: 30} or {@code { every: 30, user-can-pause: true }} as a panel option ({@link com.ash.rachana.model.Refresh}); null when absent or wrong. */
    private Object refresh(PNode n) {
        if (n == null || n.value() == null && !n.isMap()) {
            return null;
        }
        Object raw = n.isScalar() ? n.value() : plain(n);
        var why = com.ash.rachana.model.Refresh.problem(raw);
        if (why.isPresent()) {
            problem("RCH-2012", why.get(), n);
            return null;
        }
        return com.ash.rachana.model.Refresh.fromOption(raw).map(com.ash.rachana.model.Refresh::asOption).orElse(null);
    }

    /**
     * Language 1.2: a {@code live} panel's {@code rows} names a {@code table} entry of {@code data:} ({@code $.<entry>}) and each column reads
     * one field; the entry's table, key and condition go with the panel under {@link #LIVE_ENTRY} for the binder (which sees no entries).
     */
    private Panel live(Panel p, Object entries) {
        String rows = p.option("rows").map(String::trim).orElse("");
        java.util.regex.Matcher m = Pattern.compile("\\$\\.([A-Za-z_][A-Za-z0-9_]*)").matcher(rows);
        com.ash.rachana.model.DataEntry entry = null;
        if (m.matches() && entries instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof com.ash.rachana.model.DataEntry e && e.name().equals(m.group(1)) && e.form() == com.ash.rachana.model.DataEntry.Form.TABLE) {
                    entry = e;
                }
            }
        }
        if (entry == null) {
            problems.add(new SutraProblem("RCH-2029", "option 'rows' of 'live' panels names a table entry of data: ($.<entry> with { table: <name> }), not '"
                    + rows + "'", p.location()));
            return p;
        }
        List<String> key = p.options().get("rowKey") instanceof String k ? List.of(k)
                : p.options().get("rowKey") instanceof List<?> kl ? kl.stream().map(String::valueOf).toList() : entry.key();
        if (key == null || key.isEmpty()) {
            problems.add(new SutraProblem("RCH-2072", "live panel '" + p.id() + "' reads data '" + entry.name() + "', which has no key: a live table is "
                    + "followed by key (give the entry a key, or the panel a rowKey)", p.location()));
        }
        for (Column c : p.columns()) {
            if (c.bind() == null || !Pattern.compile("@\\.[A-Za-z_][A-Za-z0-9_]*").matcher(c.bind().trim()).matches()) {
                problems.add(new SutraProblem("RCH-2029", "a column of live panel '" + p.id() + "' reads one field of the row (such as @.qty), not '"
                        + c.bind() + "': the painter shows the rows the host sends", p.location()));
            }
        }
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("table", entry.table());
        ref.put("key", key == null ? List.of() : key);
        if (entry.where() != null) {
            ref.put("where", entry.where());
        }
        return withOption(p, LIVE_ENTRY, ref);
    }

    /** The option a live panel's entry travels under (never written by an author: not a name). */
    static final String LIVE_ENTRY = "$live";

    private static Panel withOption(Panel p, String key, Object value) {
        Map<String, Object> o = new LinkedHashMap<>(p.options());
        o.put(key, value);
        return new Panel(p.id(), p.kind(), p.title(), p.key(), p.code(), p.area(), p.infer(), p.columns(), p.body(), o, p.location(), p.parts(), p.wiring());
    }

    private void size(Map<String, PNode> m, String key, int max, String unit, boolean nested, Map<String, Object> options) {
        PNode n = m.get(key);
        if (n == null || n.value() == null) {
            return;
        }
        if (nested) {
            problem("RCH-2030", "'" + key + "' sizes a whole panel: a tabs body takes the size of its panel", n);
        } else if (!(n.value() instanceof Long l) || l < 1 || l > max) {
            problem("RCH-2030", key + " must be a whole number from 1 to " + max + " (" + unit + "), not '" + n.value() + "'", n);
        } else {
            options.put(key, l);
        }
    }

    private static final Pattern AREA_NAME = Pattern.compile("[a-z][a-z0-9-]{0,31}");

    /**
     * A panel's place in a screen's layout (RACHANA_REFERENCE.md 13), kept with its options: a named {@code area}, {@code order} (its sort key
     * within the area or the flow), {@code section} and {@code collapsible}. Its height in grid rows stays {@code height} ({@code rows} is the rows a table reads).
     */
    private void placement(Map<String, PNode> m, boolean nested, String gridArea, Map<String, Object> options) {
        if (gridArea != null) {
            options.put(Panel.GRID_AREA, gridArea);
        }
        PNode order = m.get(Panel.ORDER);
        if (order != null && order.value() != null) {
            if (nested || !(order.value() instanceof Long l) || l < 0 || l > 999) {
                problem(LayoutReader.CODE, nested ? "'order' places a whole panel; a tab keeps the order it is written in"
                        : "order is a whole number from 0 to 999 (lower comes first), not '" + order.value() + "'", order);
            } else {
                options.put(Panel.ORDER, l);
            }
        }
        PNode section = m.get(Panel.SECTION);
        if (section != null && section.value() != null) {
            if (nested || section.text() == null || !AREA_NAME.matcher(section.text()).matches()) {
                problem(LayoutReader.CODE, "section names one of layout.sections by its id", section);
            } else {
                options.put(Panel.SECTION, section.text());
            }
        }
        PNode collapsible = m.get(Panel.COLLAPSIBLE);
        if (collapsible != null && collapsible.value() != null) {
            if (!(collapsible.value() instanceof Boolean b)) {
                problem(LayoutReader.CODE, "collapsible is true or false", collapsible);
            } else if (b) {
                options.put(Panel.COLLAPSIBLE, true);
            }
        }
    }

    /** Converts a structured option (for example a series list) to plain maps and lists. */
    private static Object plain(PNode n) {
        if (n.isMap()) {
            Map<String, Object> out = new LinkedHashMap<>();
            n.map().forEach((k, v) -> out.put(k, plain(v)));
            return out;
        }
        if (n.isList()) {
            return n.list().stream().map(SutraBuilder::plain).toList();
        }
        return n.value();
    }

    private List<PNode> listOf(PNode n, String what) {
        if (n == null) {
            return List.of();
        }
        if (!n.isList()) {
            problem("RCH-2012", "'" + what + "' must be a list", n);
            return List.of();
        }
        return "panels".equals(what) ? SutraBlocks.expand(n.list(), file, problems, fragments(), grammar.level(), calls) : n.list();   // { use: <block> } brings a block's panels
    }

    /**
     * What a fragment's caller knows of its tables (decision L-21): for {@code $.<name>} of a {@code data:} entry, its declared
     * {@code fields} with their types, else the fields of its written rows with the types their values have; unknown otherwise.
     */
    private static java.util.function.Function<String, Map<String, String>> callerColumns(Object data) {
        if (!(data instanceof List<?> entries) || entries.isEmpty()) {
            return null;
        }
        return expr -> {
            if (!expr.startsWith("$.")) {
                return null;
            }
            for (Object o : entries) {
                com.ash.rachana.model.DataEntry e = (com.ash.rachana.model.DataEntry) o;
                if (!e.name().equals(expr.substring(2))) {
                    continue;
                }
                Map<String, String> out = new LinkedHashMap<>();
                if (!e.fields().isEmpty()) {
                    e.fields().forEach((f, d) -> out.put(f, d instanceof Map<?, ?> dm && dm.get("type") != null ? String.valueOf(dm.get("type")) : ""));
                    return out;
                }
                if (e.rows() != null && !e.rows().isEmpty() && e.rows().get(0) instanceof Map<?, ?> first) {
                    first.forEach((f, v) -> out.put(String.valueOf(f), typeOf(v)));
                    return out;
                }
                return null;
            }
            return null;
        };
    }

    private static String typeOf(Object v) {
        if (v instanceof Boolean) {
            return "boolean";
        }
        if (v instanceof Long || v instanceof Integer || v instanceof java.math.BigInteger) {
            return "integer";
        }
        if (v instanceof Number) {
            return "number";
        }
        if (v instanceof String s) {
            return s.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") ? "date" : s.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T.*") ? "datetime" : "text";
        }
        return "";
    }

    /** The resolver this Sutra's uses read through (its libraries' adapter from level 1.2, else the host's). */
    private FragmentResolver fragments() {
        return fragments == null ? grammar.fragments() : fragments;
    }

    private void unknownKeys(Map<String, PNode> m, Set<String> allowed, String where) {
        m.forEach((k, v) -> {
            if (!allowed.contains(k)) {
                problem("RCH-2011", "unknown key '" + k + "' in " + where, v);
            }
        });
    }

    private String requiredText(Map<String, PNode> m, String key, PNode parent) {
        PNode n = m.get(key);
        if (n == null || n.value() == null) {
            problem("RCH-2010", "missing '" + key + "'", parent);
            return null;
        }
        if (!n.isScalar()) {
            problem("RCH-2012", "'" + key + "' must be text", n);
            return null;
        }
        return n.text();
    }

    private static String text(PNode n, String fallback) {
        return n == null || n.text() == null ? fallback : n.text();
    }

    private static boolean bool(PNode n) {
        return n != null && Boolean.TRUE.equals(n.value());
    }

    private String kinds() {
        StringBuilder sb = new StringBuilder();
        for (PanelKind k : grammar.panelKinds()) {
            sb.append(sb.isEmpty() ? "" : ", ").append(k.id());
        }
        return sb.toString();
    }

    private SourceLocation loc(PNode n) {
        return new SourceLocation(file, n.line(), n.column());
    }

    private void problem(String code, String message, PNode at) {
        problems.add(new SutraProblem(code, message, loc(at)));
    }

    /** For {@link KeyTables}. */
    static Set<String> panelKeys() {
        return PANEL;
    }

    /** For {@link KeyTables}. */
    static Set<String> stripKeys() {
        return STRIP;
    }

    /** For {@link KeyTables}. */
    static Set<String> columnKeys() {
        return COLUMN;
    }
}
