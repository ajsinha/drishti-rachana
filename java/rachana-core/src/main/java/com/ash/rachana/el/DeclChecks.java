/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import java.util.ArrayList;
import java.util.List;

/**
 * What a declared name may read (EL_EXPANSION X41, SUTRA_EXPANSION S2). A function's body reads only its parameters and other
 * functions: not the document ({@code $}, a bare field), a control, an event, {@code $src}, nor a current row ({@code @}, {@code ^},
 * {@code #index}) outside the elements a form or filter of its own walks. A Sutra {@code let} value reads the document and controls but
 * no current row: it is evaluated once per bind, with no row.
 */
final class DeclChecks {

    private DeclChecks() {}

    /** What is wrong with a function's body, or null. */
    static String function(Expr body) {
        return walk(body, false, true);
    }

    /** What is wrong with a Sutra {@code let} value, or null. */
    static String let(Expr value) {
        return walk(value, false, false);
    }

    /**
     * @param element whether {@code @} is an element here (inside a filter's condition or a form's per-element argument)
     * @param function whether a function's body is being read
     */
    private static String walk(Expr e, boolean element, boolean function) {
        if (e == null) {
            return null;
        }
        switch (e) {
            case Expr.Root r -> {
                return function ? "reads $ (the document): a function reads only its parameters" : null;
            }
            case Expr.Ctl c -> {
                return function ? "reads ctl." + c.name() + ": a function reads only its parameters" : null;
            }
            case Expr.Event ev -> {
                return function ? "reads @" + ev.panel() + ": a function reads only its parameters" : null;
            }
            case Expr.Src s -> {
                return function ? "reads $src: a function reads only its parameters" : null;
            }
            case Expr.Row r -> {
                return element ? null : "reads @ outside a list it walks: " + (function ? "a function has no current row" : "a let value has no current row");
            }
            case Expr.RowOrRoot r -> {
                return element || !function ? null : "reads a name that is not one of its parameters (a function reads only its parameters)";
            }
            case Expr.Parent p -> {
                return element ? null : "reads ^ outside a list it walks";
            }
            case Expr.Index i -> {
                return element ? null : "reads #index outside a list it walks";
            }
            case Expr.Field f -> {
                if (!element && f.target() instanceof Expr.RowOrRoot) {
                    return function ? "reads '" + f.name() + "', which is not one of its parameters (a function reads only its parameters)" : null;
                }
                return walk(f.target(), element, function);
            }
            case Expr.Filter f -> {
                String t = walk(f.target(), element, function);
                return t != null ? t : walk(f.predicate(), true, function);
            }
            case Expr.Form f -> {
                for (int i = 0; i < f.args().size(); i++) {
                    String t = walk(f.args().get(i), element || i > 0 && perElement(f.name()), function);
                    if (t != null) {
                        return t;
                    }
                }
                return null;
            }
            default -> {
                for (Expr c : children(e)) {
                    String t = walk(c, element, function);
                    if (t != null) {
                        return t;
                    }
                }
                return null;
            }
        }
    }

    private static boolean perElement(String form) {
        return "map".equals(form) || "sort".equals(form) || "reduce".equals(form) || "group".equals(form);
    }

    /** The expressions directly inside {@code e}. */
    private static List<Expr> children(Expr e) {
        List<Expr> out = new ArrayList<>();
        switch (e) {
            case Expr.Each x -> out.add(x.target());
            case Expr.Pluck x -> out.add(x.list());
            case Expr.At x -> {
                out.add(x.target());
                out.add(x.index());
            }
            case Expr.Not x -> out.add(x.e());
            case Expr.Neg x -> out.add(x.e());
            case Expr.And x -> {
                out.add(x.l());
                out.add(x.r());
            }
            case Expr.Or x -> {
                out.add(x.l());
                out.add(x.r());
            }
            case Expr.Ternary x -> {
                out.add(x.cond());
                out.add(x.then());
                out.add(x.otherwise());
            }
            case Expr.Binary x -> {
                out.add(x.l());
                out.add(x.r());
            }
            case Expr.Call x -> {
                out.addAll(x.args());
                out.addAll(x.named().values());
            }
            case Expr.Let x -> {
                out.addAll(x.values());
                out.add(x.body());
            }
            case Expr.ListLit x -> out.addAll(x.items());
            case Expr.In x -> {
                out.add(x.value());
                out.add(x.list());
            }
            case Expr.Op x -> {
                out.add(x.l());
                out.add(x.r());
            }
            case Expr.Minus x -> out.add(x.e());
            case Expr.Power x -> {
                out.add(x.l());
                out.add(x.r());
            }
            case Expr.Coalesce x -> {
                out.add(x.l());
                out.add(x.r());
            }
            case Expr.Spread x -> out.add(x.e());
            case Expr.Pair x -> out.add(x.value());
            case Expr.RecordLit x -> out.addAll(x.entries());
            case Expr.Slice x -> {
                out.add(x.target());
                out.add(x.start());
                out.add(x.stop());
                out.add(x.step());
            }
            case Expr.Lambda x -> out.add(x.body());
            case Expr.LocalCall x -> out.addAll(x.args());
            case Expr.UserCall x -> out.addAll(x.args());
            default -> { }
        }
        out.removeIf(java.util.Objects::isNull);
        return out;
    }
}
