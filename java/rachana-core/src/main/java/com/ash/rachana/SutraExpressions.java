/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana;

import com.ash.rachana.el.ElCompiler;
import com.ash.rachana.el.ElException;
import com.ash.rachana.model.Column;
import com.ash.rachana.model.Control;
import com.ash.rachana.model.ControlRules;
import com.ash.rachana.model.Panel;
import com.ash.rachana.model.PanelKind;
import com.ash.rachana.model.SourceLocation;
import com.ash.rachana.model.Sutra;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Knows which Sutra values are Rachana-EL and compiles them all at load time, so a typo in an expression
 * is reported against the file (with {@code RCH-2101}) instead of surfacing as a broken view.
 */
public final class SutraExpressions {

    /** Per panel kind, the options that hold expressions (the rest are field names or literals). */
    static final Map<PanelKind, Set<String>> EL_OPTIONS = Map.ofEntries(
            Map.entry(PanelKind.KV, Set.of("rows")), Map.entry(PanelKind.TABLE, Set.of("rows", "where", "limit", "moreLabel", "highlight", "children", "crumb", "root")),
            Map.entry(PanelKind.TABS, Set.of("each", "tabTitle")), Map.entry(PanelKind.LINE, Set.of("rows", "where", "source", "mark")),
            Map.entry(PanelKind.AREA, Set.of("where", "rows", "limit")), Map.entry(PanelKind.HBAR, Set.of("where", "rows")),
            Map.entry(PanelKind.LADDER, Set.of("rows", "where", "highlight", "children", "crumb", "root")), Map.entry(PanelKind.GAUGE, Set.of("value", "max")),
            Map.entry(PanelKind.LINKS, Set.of()), Map.entry(PanelKind.STATUS, Set.of()),
            Map.entry(PanelKind.PROVENANCE, Set.of()), Map.entry(PanelKind.MARKDOWN, Set.of()), Map.entry(PanelKind.SURFACE, Set.of("where", "rows")),
            Map.entry(PanelKind.WATERFALL, Set.of("where", "rows")), Map.entry(PanelKind.HISTOGRAM, Set.of("where", "rows")),
            Map.entry(PanelKind.SCATTER, Set.of("where", "rows")), Map.entry(PanelKind.CANDLESTICK, Set.of("where", "rows")),
            Map.entry(PanelKind.GRAPH, Set.of("nodes", "edges")), Map.entry(PanelKind.TIMELINE, Set.of("where", "rows")),
            Map.entry(PanelKind.PIVOT, Set.of("where", "rows", "across", "value")), Map.entry(PanelKind.METRIC, Set.of("value", "delta")),
            Map.entry(PanelKind.MAP, Set.of("highlight", "region")),
            Map.entry(PanelKind.GRID, Set.of("rows", "where")), Map.entry(PanelKind.TREE, Set.of("rows", "where")),
            Map.entry(PanelKind.CHART, Set.of("rows", "where")));

    /** The expression options of a panel kind: its own, and {@code source} for every kind that reads data. */
    static Set<String> expressionOptions(PanelKind kind) {
        Set<String> own = new java.util.HashSet<>(EL_OPTIONS.getOrDefault(kind, Set.of()));
        own.add(com.ash.rachana.model.Panel.WHEN);                        // every kind may be shown only when ...
        if (!kind.readsData() || own.contains("source")) {
            return own;
        }
        Set<String> all = new java.util.HashSet<>(own);
        all.add("source");
        return all;
    }

    /**
     * The options of panel kind {@code kind} (its written word) whose values are expressions: its own, {@code when}, and {@code source}
     * for a kind that reads data; empty for a word that is no panel kind.
     */
    public static Set<String> expressionOptions(String kind) {
        return PanelKind.parse(kind).map(SutraExpressions::expressionOptions).orElse(Set.of());
    }

    /** The message without its leading RCH-nnnn: the problem carries the code itself. */
    /** The problem's words with the source in front, once (a compiler at level 1.1 has put it there already). */
    private static String whole(String what, String src, ElException e) {
        String words = withoutCode(e.getMessage());
        return words.startsWith(what + " '" + src + "': ") ? words : what + " '" + src + "': " + words;
    }

    private static String withoutCode(String message) {
        return message == null ? "" : message.replaceFirst("^RCH-\\d+\\s*:?\\s*", "");
    }

    /** The problems of one check, and the control ids the Sutra declares (for {@code ctl.<id>} references). */
    private static final class Run {
        private final List<SutraProblem> problems = new ArrayList<>();
        private final Set<String> declared;

        Run(Set<String> declared) {
            this.declared = declared;
        }

        void add(SutraProblem p) {
            problems.add(p);
        }

        void references(String src, SourceLocation at) {
            for (String id : ControlRules.references(src)) {
                if (!declared.contains(id)) {
                    problems.add(new SutraProblem("RCH-2036", "'ctl." + id + "' reads a control the Sutra does not declare"
                            + (declared.isEmpty() ? " (it has no controls:)" : " (it has " + String.join(", ", new java.util.TreeSet<>(declared)) + ")"), at));
                }
            }
        }
    }

    private final ElCompiler compiler;

    public SutraExpressions(ElCompiler compiler) {
        this.compiler = compiler;
    }

    /** Compiles every expression in {@code s}; returns the problems (empty when all compile). */
    public List<SutraProblem> check(Sutra s) {
        return com.ash.rachana.el.Declarations.within(s.declarations(), () -> checked(s));
    }

    private List<SutraProblem> checked(Sutra s) {
        Run out = new Run(s.controls().stream().map(Control::id).collect(java.util.stream.Collectors.toSet()));
        s.controls().forEach(c -> {
            expr(c.from(), c.location(), out);
            expr(c.min(), c.location(), out);
            expr(c.max(), c.location(), out);
        });
        if (s.match() != null) {
            expr(s.match().where(), s.location(), out);
        }
        if (s.title() != null) {
            expr(s.title().id(), s.location(), out);
            expr(s.title().with(), s.location(), out);
        }
        s.strip().forEach(i -> expr(i.bind(), i.location(), out));
        s.panels().forEach(p -> panel(p, out));
        s.keys().values().forEach(a -> {
            if (a.startsWith("link(")) {
                expr(a, s.location(), out);
            }
        });
        SutraWiring.check(s).forEach(out::add);         // events, sources, children: RACHANA_REFERENCE.md 14
        com.ash.rachana.el.Declarations d = s.declarations();   // level 1.2: every let value and function body, used or not (X41, S2)
        d.lets().keySet().forEach(l -> expr(l, s.location(), out));
        d.functions().values().forEach(f -> expr(f.name() + "(" + String.join(", ", java.util.Collections.nCopies(f.params().size(), "null")) + ")",
                s.location(), out));
        return out.problems;
    }

    private void panel(Panel p, Run out) {
        template(p.title(), p.location(), out);
        for (Column c : p.columns()) {
            expr(c.bind(), p.location(), out);
        }
        for (String opt : expressionOptions(p.kind())) {
            Object v = p.options().get(opt);
            if (v instanceof String str) {
                expr(str, p.location(), out);
            }
        }
        if (p.options().get("fields") instanceof List<?> fields) {
            for (Object f : fields) {
                if (f instanceof Map<?, ?> m && m.get("bind") != null) {
                    expr(m.get("bind").toString(), p.location(), out);
                }
            }
        }
        if (p.options().get("levels") instanceof List<?> levels) {        // a drill table: each level's rows, crumb and column binds
            for (Object l : levels) {
                if (l instanceof Map<?, ?> m) {
                    for (String k : List.of("rows", "crumb")) {
                        if (m.get(k) instanceof String s) {
                            expr(s, p.location(), out);
                        }
                    }
                    if (m.get("columns") instanceof List<?> cols) {
                        cols.forEach(c -> {
                            if (c instanceof Map<?, ?> cm && cm.get("bind") instanceof String b) {
                                expr(b, p.location(), out);
                            }
                        });
                    }
                }
            }
        }
        // an interactive pivot's fields: the expressions it reads per row
        p.pivot().ifPresent(spec -> spec.fields().forEach(f -> expr(f.bind(), p.location(), out)));
        if (p.kind() == PanelKind.HISTOGRAM && p.options().get("markers") instanceof List<?> markers) {
            for (Object mk : markers) {
                if (mk instanceof Map<?, ?> m && m.get("value") != null) {
                    expr(m.get("value").toString(), p.location(), out);
                }
            }
        }
        if (p.kind() == PanelKind.MAP && p.options().get("shade") instanceof Map<?, ?> sh) {
            for (String key : List.of("field", "by")) {
                if (sh.get(key) != null) {
                    expr(sh.get(key).toString(), p.location(), out);
                }
            }
        }
        if (p.kind() == PanelKind.MARKDOWN) {
            p.option("text").ifPresent(t -> template(t, p.location(), out));
        }
        for (Map<String, Object> a : com.ash.rachana.model.Effects.actions(p)) {   // level 1.2, I1: an action's label and question
            template(String.valueOf(a.get("label")), p.location(), out);
            if (a.get("confirm") instanceof String q) {
                template(q, p.location(), out);
            }
        }
        {
            for (Map<String, Object> e : com.ash.rachana.model.Effects.every(p)) {   // level 1.2, I1: each effect's expressions, @ the clicked row
                for (String k : List.of("to", "when")) {
                    if (e.get(k) instanceof String x && !("to".equals(k) && com.ash.rachana.model.Effects.eventPath(x) != null)) {
                        expr(x, p.location(), out);                         // event.<field>: filled in when the trigger fires
                    }
                }
                if (e.get("with") instanceof Map<?, ?> w) {
                    w.values().stream().filter(x -> com.ash.rachana.model.Effects.eventPath(x) == null).forEach(x -> expr(String.valueOf(x), p.location(), out));
                }
                if ("notify".equals(e.get("do")) && e.get("text") instanceof String t) {
                    template(t, p.location(), out);
                }
            }
        }
        if (p.body() != null) {
            panel(p.body(), out);
        }
        p.parts().forEach(part -> panel(part, out));
        wiring(p.wiring(), p.location(), out);
    }

    /** A panel's source and payload expressions, and its children's panels (RACHANA_REFERENCE.md 6 to 8). */
    private void wiring(com.ash.rachana.model.Wiring w, SourceLocation at, Run out) {
        if (w.source() != null) {
            w.source().expressions().forEach(e -> expr(e, at, out));
        }
        w.emits().forEach(e -> e.payload().values().forEach(f -> expr(f.bind(), at, out)));
        if (w.waits() != null) {
            template(w.waits(), at, out);
        }
        for (var g : w.children()) {
            if (g.source() != null) {
                g.source().expressions().forEach(e -> expr(e, at, out));
            }
            g.panels().forEach(c -> panel(c, out));
        }
    }

    private void expr(String src, SourceLocation at, Run out) {
        if (src == null) {
            return;
        }
        out.references(src, at);
        try {
            compiler.compile(src);
        } catch (ElException e) {
            out.add(new SutraProblem("RCH-2101", whole("expression", src, e), at));
        }
    }

    private void template(String src, SourceLocation at, Run out) {
        if (src == null) {
            return;
        }
        out.references(src, at);
        try {
            compiler.template(src);
        } catch (ElException e) {
            out.add(new SutraProblem("RCH-2101", whole("template", src, e), at));
        }
    }
}
