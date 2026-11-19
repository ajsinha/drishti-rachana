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

    /** {@link #expand(String, Map)} against one instance's function table (a profile's functions known). */
    public static String expand(String expr, Map<String, Def> defs, FunctionTable table) {
        if (expr == null || defs.isEmpty()) {
            return expr;
        }
        return Printer.print(rewrite(Parser.parse(expr, defs.keySet(), table), Map.of(), defs, new LinkedHashSet<>(), 0, table));
    }

    private static Expr rewrite(Expr e, Map<String, Expr> bind, Map<String, Def> defs, Set<String> trail, int depth, FunctionTable table) {
        return switch (e) {
            case Expr.Field f when f.target() instanceof Expr.RowOrRoot && bind.containsKey(f.name()) -> bind.get(f.name());
            case Expr.Field f -> new Expr.Field(rewrite(f.target(), bind, defs, trail, depth, table), f.name(), f.rootPath());
            case Expr.Local l when bind.containsKey(l.name()) -> bind.get(l.name());
            case Expr.Call c when defs.containsKey(c.name()) -> call(c, bind, defs, trail, depth, table);
            case Expr.Call c -> {
                Map<String, Expr> named = new LinkedHashMap<>();
                c.named().forEach((k, v) -> named.put(k, rewrite(v, bind, defs, trail, depth, table)));
                yield new Expr.Call(c.name(), c.fn(), all(c.args(), bind, defs, trail, depth, table), named);
            }
            case Expr.Binary b -> new Expr.Binary(b.op(), rewrite(b.l(), bind, defs, trail, depth, table), rewrite(b.r(), bind, defs, trail, depth, table));
            case Expr.And a -> new Expr.And(rewrite(a.l(), bind, defs, trail, depth, table), rewrite(a.r(), bind, defs, trail, depth, table));
            case Expr.Or o -> new Expr.Or(rewrite(o.l(), bind, defs, trail, depth, table), rewrite(o.r(), bind, defs, trail, depth, table));
            case Expr.Not n -> new Expr.Not(rewrite(n.e(), bind, defs, trail, depth, table));
            case Expr.Neg n -> new Expr.Neg(rewrite(n.e(), bind, defs, trail, depth, table));
            case Expr.Ternary t -> new Expr.Ternary(rewrite(t.cond(), bind, defs, trail, depth, table), rewrite(t.then(), bind, defs, trail, depth, table), rewrite(t.otherwise(), bind, defs, trail, depth, table));
            case Expr.ListLit l -> new Expr.ListLit(all(l.items(), bind, defs, trail, depth, table));
            case Expr.In i -> new Expr.In(rewrite(i.value(), bind, defs, trail, depth, table), rewrite(i.list(), bind, defs, trail, depth, table), i.negated());
            default -> e;
        };
    }

    private static List<Expr> all(List<Expr> list, Map<String, Expr> bind, Map<String, Def> defs, Set<String> trail, int depth, FunctionTable table) {
        List<Expr> out = new ArrayList<>();
        list.forEach(x -> out.add(rewrite(x, bind, defs, trail, depth, table)));
        return out;
    }

    private static Expr call(Expr.Call c, Map<String, Expr> bind, Map<String, Def> defs, Set<String> trail, int depth, FunctionTable table) {
        Def d = defs.get(c.name());
        if (depth >= MAX_DEPTH || trail.contains(c.name())) {
            throw new IllegalArgumentException("measure function '" + c.name() + "' calls itself (" + String.join(" -> ", trail) + " -> " + c.name() + ")");
        }
        if (c.args().size() != d.params().size()) {
            throw new IllegalArgumentException("measure function '" + c.name() + "' takes " + d.params().size() + " argument" + (d.params().size() == 1 ? "" : "s") + " " + d.params()
                    + "; this call gives " + c.args().size());
        }
        Map<String, Expr> inner = new LinkedHashMap<>();
        for (int i = 0; i < d.params().size(); i++) {
            inner.put(d.params().get(i), rewrite(c.args().get(i), bind, defs, trail, depth, table));
        }
        Set<String> deeper = new LinkedHashSet<>(trail);
        deeper.add(c.name());
        Expr body;
        try {
            body = Parser.parse(d.expr(), defs.keySet(), table);
        } catch (RuntimeException x) {
            throw new IllegalArgumentException("measure function '" + c.name() + "': " + x.getMessage());
        }
        return rewrite(body, inner, defs, deeper, depth + 1, table);
    }
}
