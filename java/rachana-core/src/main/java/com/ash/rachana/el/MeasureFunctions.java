/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A model's measure functions: {@code functions: { margin: { params: [a, b], expr: "a / b" } }}, called {@code margin(sum(x.V), sum(x.W))} in a measure. Each call is
 * expanded in the text of the measure before the expression is checked, so a measure function is exactly the expression it stands for: same scopes, same checks, no
 * second evaluator. A function may call an earlier one; a cycle or a wrong number of arguments is refused in words.
 */
public final class MeasureFunctions {

    /** A function's parameters and its body. */
    public record Def(List<String> params, String expr) {}

    private static final int MAX_DEPTH = 8;

    private MeasureFunctions() {}

    /** The expression with every call of a function of {@code defs} expanded; {@code IllegalArgumentException} says what is wrong. */
    public static String expand(String expr, Map<String, Def> defs) {
        return expand(expr, defs, FunctionTable.core());
    }

    /**
     * {@link #expand(String, Map)} against one instance's function table (a profile's functions known). The measure and every function's
     * body are read by the one parse of Rachana-EL, at the one level, whole: text after a complete expression is refused, never dropped
     * (G4 gate F25), so {@code net(x) if c else 0} expands to {@code (x - 1) if c else 0}, never to {@code x - 1}.
     */
    public static String expand(String expr, Map<String, Def> defs, FunctionTable table) {
        if (expr == null || defs.isEmpty()) {
            return expr;
        }
        Parser.Parsed tree;
        try {
            tree = Parser.parseWithSpans(expr, ElLimits.DEFAULTS, table, defs.keySet());
        } catch (ElException x) {
            throw refused("expression '" + expr + "': " + words(x), x.position(), x.end());
        }
        return Printer.print(new Expansion(defs, table, tree.spans()).rewrite(tree.expr(), Map.of(), Set.of(), new LinkedHashSet<>(), 0));
    }

    /**
     * A refusal in words, its cause an {@link ElException} ({@code RCH-2101}) at the place in the measure's text: the offending token, or
     * the call whose function is at fault.
     */
    private static IllegalArgumentException refused(String words, int at, int end) {
        return new IllegalArgumentException(words, new ElException(Math.max(0, at), end, words));
    }

    /** A problem's words without its code. */
    private static String words(ElException x) {
        String m = x.getMessage();
        String code = x.code() == null ? "" : x.code().code() + " ";
        return m.startsWith(code) ? m.substring(code.length()) : m;
    }

    /** One expansion: every node of the tree rebuilt, a call of a measure function replaced by its body with the arguments in place. */
    private static final class Expansion {
        private final Map<String, Def> defs;
        private final FunctionTable table;
        private final Map<Expr, int[]> spans;
        /** Where the outermost call being expanded was written in the measure ({start, end}): the place of a problem inside it. */
        private int[] site;

        Expansion(Map<String, Def> defs, FunctionTable table, Map<Expr, int[]> spans) {
            this.defs = defs;
            this.table = table;
            this.spans = spans;
        }

        /**
         * {@code e} rebuilt: {@code bind} maps a parameter of the function being inlined to its argument; {@code local} holds the names a
         * {@code let} or a lambda binds around {@code e} (such a name is the local, not the parameter it may share a spelling with).
         */
        Expr rewrite(Expr e, Map<String, Expr> bind, Set<String> local, Set<String> trail, int depth) {
            java.util.function.UnaryOperator<Expr> r = x -> x == null ? null : rewrite(x, bind, local, trail, depth);
            return switch (e) {
                case Expr.Field f when f.target() instanceof Expr.RowOrRoot && bind.containsKey(f.name()) -> bind.get(f.name());
                case Expr.Field f -> new Expr.Field(r.apply(f.target()), f.name(), f.rootPath());
                case Expr.Local l when bind.containsKey(l.name()) && !local.contains(l.name()) -> bind.get(l.name());
                case Expr.Call c when defs.containsKey(c.name()) -> call(c, bind, local, trail, depth);
                case Expr.Call c -> {
                    Map<String, Expr> named = new LinkedHashMap<>();
                    c.named().forEach((k, v) -> named.put(k, r.apply(v)));
                    yield new Expr.Call(c.name(), c.fn(), all(c.args(), r), named);
                }
                case Expr.Form f -> new Expr.Form(f.name(), f.fn(), all(f.args(), r));
                case Expr.LocalCall c -> new Expr.LocalCall(c.name(), all(c.args(), r));
                case Expr.UserCall c -> new Expr.UserCall(c.name(), c.params(), all(c.args(), r), c.body());
                case Expr.Pluck x -> new Expr.Pluck(r.apply(x.list()), x.name());
                case Expr.Each x -> new Expr.Each(r.apply(x.target()));
                case Expr.At x -> new Expr.At(r.apply(x.target()), r.apply(x.index()), x.rootPath());
                case Expr.Filter x -> new Expr.Filter(r.apply(x.target()), r.apply(x.predicate()));
                case Expr.Slice x -> new Expr.Slice(r.apply(x.target()), r.apply(x.start()), r.apply(x.stop()), r.apply(x.step()));
                case Expr.Binary b -> new Expr.Binary(b.op(), r.apply(b.l()), r.apply(b.r()));
                case Expr.Op b -> new Expr.Op(b.op(), r.apply(b.l()), r.apply(b.r()));
                case Expr.Power b -> new Expr.Power(r.apply(b.l()), r.apply(b.r()));
                case Expr.Coalesce b -> new Expr.Coalesce(r.apply(b.l()), r.apply(b.r()));
                case Expr.And a -> new Expr.And(r.apply(a.l()), r.apply(a.r()));
                case Expr.Or o -> new Expr.Or(r.apply(o.l()), r.apply(o.r()));
                case Expr.Not n -> new Expr.Not(r.apply(n.e()));
                case Expr.Neg n -> new Expr.Neg(r.apply(n.e()));
                case Expr.Minus n -> new Expr.Minus(r.apply(n.e()));
                case Expr.Ternary t -> new Expr.Ternary(r.apply(t.cond()), r.apply(t.then()), r.apply(t.otherwise()));
                case Expr.ListLit l -> new Expr.ListLit(all(l.items(), r));
                case Expr.In i -> new Expr.In(r.apply(i.value()), r.apply(i.list()), i.negated());
                case Expr.Spread x -> new Expr.Spread(r.apply(x.e()));
                case Expr.Pair x -> new Expr.Pair(x.key(), r.apply(x.value()));
                case Expr.RecordLit x -> new Expr.RecordLit(all(x.entries(), r));
                case Expr.Let l -> {
                    Set<String> inner = new LinkedHashSet<>(local);
                    List<Expr> values = new ArrayList<>();
                    for (int i = 0; i < l.names().size(); i++) {
                        values.add(rewrite(l.values().get(i), bind, inner, trail, depth));   // a value sees the names before it
                        inner.add(l.names().get(i));
                    }
                    yield new Expr.Let(l.names(), values, rewrite(l.body(), bind, inner, trail, depth));
                }
                case Expr.Lambda l -> {
                    Set<String> inner = new LinkedHashSet<>(local);
                    inner.addAll(l.params());
                    yield new Expr.Lambda(l.params(), rewrite(l.body(), bind, inner, trail, depth));
                }
                default -> e;                                    // a leaf: a literal, $, @, ^, ctl., #index ...
            };
        }

        private static List<Expr> all(List<Expr> list, java.util.function.UnaryOperator<Expr> r) {
            List<Expr> out = new ArrayList<>();
            list.forEach(x -> out.add(r.apply(x)));
            return out;
        }

        private Expr call(Expr.Call c, Map<String, Expr> bind, Set<String> local, Set<String> trail, int depth) {
            int[] outer = site;
            if (depth == 0 && trail.isEmpty()) {
                site = spans.get(c);
            }
            try {
                return expandCall(c, bind, local, trail, depth);
            } finally {
                site = outer;
            }
        }

        private IllegalArgumentException refuse(String words) {
            return site == null ? refused(words, 0, -1) : refused(words, site[0], site[1]);
        }

        private Expr expandCall(Expr.Call c, Map<String, Expr> bind, Set<String> local, Set<String> trail, int depth) {
            Def d = defs.get(c.name());
            if (depth >= MAX_DEPTH || trail.contains(c.name())) {
                throw refuse("measure function '" + c.name() + "' calls itself (" + String.join(" -> ", trail) + " -> " + c.name() + ")");
            }
            if (!c.named().isEmpty()) {
                throw refuse("measure function '" + c.name() + "' takes its arguments by place " + d.params() + "; this call names "
                        + String.join(", ", c.named().keySet()));
            }
            if (c.args().size() != d.params().size()) {
                throw refuse("measure function '" + c.name() + "' takes " + d.params().size() + " argument" + (d.params().size() == 1 ? "" : "s") + " "
                        + d.params() + "; this call gives " + c.args().size());
            }
            Map<String, Expr> inner = new LinkedHashMap<>();
            for (int i = 0; i < d.params().size(); i++) {
                inner.put(d.params().get(i), rewrite(c.args().get(i), bind, local, trail, depth));
            }
            Set<String> deeper = new LinkedHashSet<>(trail);
            deeper.add(c.name());
            Expr body;
            try {
                body = Parser.parse(d.expr(), defs.keySet(), table);
            } catch (ElException x) {
                throw refuse("measure function '" + c.name() + "': " + words(x));
            }
            return rewrite(body, inner, Set.of(), deeper, depth + 1);
        }
    }
}
