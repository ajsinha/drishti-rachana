/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import com.ash.rachana.internal.NumberText;
import com.ash.rachana.internal.CoreMath;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A Rachana tree as text, in the one spelling (docs/architecture/ARCHITECTURE.md §28 Q4): {@code &&}, {@code ||}, {@code !}, {@code c ? a : b},
 * {@code true}, {@code false}, {@code null}, named arguments {@code name = value}, lists {@code [a, b]}, {@code in} and {@code not in}.
 * Parentheses only where the grammar needs them, so printing then parsing gives the same tree. Used by the migration of part B to write
 * the expressions it rewrites, and by the tests that read the shipped DEL expressions as Rachana.
 */
public final class Printer {

    private Printer() {}

    // precedence, loosest first: ternary 1, ?? 2, || 3, && 4, == != 5, < <= > >= in 6, + - 7, * / % 8, unary 9, ** 10, postfix 11
    public static String print(Expr e) {
        return new Printer.Run(false).p(e, 0);
    }

    /**
     * {@code e} as text at language level 1.1 when {@code v11}: a double literal keeps an exponent ({@code 1e-3}), so that parsing the
     * text at that level gives the same tree; the level 1.1 nodes print in their one spelling (<code>{a: 1, ...r}</code>, {@code [...a]},
     * {@code a ?? b}, {@code a ** b}, {@code t[a:b]}, {@code lambda x: ...}). A comment is not part of the tree and is not printed.
     */
    public static String print(Expr e, boolean v11) {
        return new Printer.Run(v11).p(e, 0);
    }

    private record Run(boolean v11) {

        String p(Expr e, int outer) {
            return switch (e) {
                case Expr.Lit l -> v11 ? literal11(l.value()) : literal(l.value());
                case Expr.Absent a -> "null";
                case Expr.Root r -> "$";
                case Expr.Row r -> "@";
                case Expr.Parent r -> "^".repeat(r.up());
                case Expr.Src r -> "$src";
                case Expr.Index r -> "#index";
                case Expr.Ctl c -> "ctl." + c.name();
                case Expr.State c -> "state." + c.id();
                case Expr.Event v -> "@" + v.panel();
                case Expr.Local l -> l.name().equals(ListFunctions.ACC) ? "@acc" : l.name();
                case Expr.RowOrRoot r -> "";
                case Expr.Field f -> f.target() instanceof Expr.RowOrRoot ? f.name() : p(f.target(), 11) + "." + f.name();
                case Expr.Pluck f -> p(f.list(), 11) + "." + f.name();
                case Expr.Each x -> p(x.target(), 11) + "[*]";
                case Expr.At a -> p(a.target(), 11) + "[" + p(a.index(), 0) + "]";
                case Expr.Filter f -> p(f.target(), 11) + "[?" + p(f.predicate(), 0) + "]";
                case Expr.ListLit l -> "[" + String.join(", ", l.items().stream().map(x -> p(x, 0)).toList()) + "]";
                case Expr.Not n -> wrap("!" + p(n.e(), 9), 9, outer);
                case Expr.Neg n -> wrap("-" + p(n.e(), 9), 9, outer);
                case Expr.Minus n -> wrap("-" + p(n.e(), 9), 9, outer);
                case Expr.And a -> wrap(p(a.l(), 4) + " && " + p(a.r(), 5), 4, outer);
                case Expr.Or o -> wrap(p(o.l(), 3) + " || " + p(o.r(), 4), 3, outer);
                case Expr.Coalesce o -> wrap(p(o.l(), 2) + " ?? " + p(o.r(), 3), 2, outer);
                case Expr.Ternary t -> wrap(p(t.cond(), 2) + " ? " + p(t.then(), 1) + " : " + p(t.otherwise(), 1), 1, outer);
                case Expr.In i -> wrap(p(i.value(), 7) + (i.negated() ? " not in " : " in ") + p(i.list(), 7), 6, outer);
                case Expr.Binary b -> {
                    int level = level(b.op());
                    yield wrap(p(b.l(), level) + " " + b.op() + " " + p(b.r(), level + 1), level, outer);
                }
                case Expr.Op b -> {
                    int level = level(b.op());
                    yield wrap(p(b.l(), level) + " " + b.op() + " " + p(b.r(), level + 1), level, outer);
                }
                case Expr.Power w -> wrap(p(w.l(), 11) + " ** " + p(w.r(), 9), 10, outer);
                case Expr.Call c -> call(c.name(), c.args(), c.named());
                case Expr.Form f -> call(f.name(), f.args(), Map.of());
                case Expr.LocalCall c -> call(c.name(), c.args(), Map.of());
                case Expr.FnRef f -> f.name();
                case Expr.SutraLet l -> l.name();                                  // a declared name prints as written (level 1.2)
                case Expr.UserCall c -> call(c.name(), c.args(), Map.of());
                case Expr.Lambda l -> "lambda" + (l.params().isEmpty() ? "" : " " + String.join(", ", l.params())) + ": " + p(l.body(), 0);
                case Expr.Spread x -> "..." + p(x.e(), 0);
                case Expr.Pair x -> key(x.key()) + ": " + p(x.value(), 0);
                case Expr.RecordLit r -> "{" + String.join(", ", r.entries().stream().map(x -> p(x, 0)).toList()) + "}";
                case Expr.Slice x -> p(x.target(), 11) + "[" + (x.start() == null ? "" : p(x.start(), 0)) + ":"
                        + (x.stop() == null ? "" : p(x.stop(), 0)) + (x.step() == null ? "" : ":" + p(x.step(), 0)) + "]";
                case Expr.Let l -> {
                    List<String> parts = new ArrayList<>();
                    for (int i = 0; i < l.names().size(); i++) {
                        parts.add(l.names().get(i) + " = " + p(l.values().get(i), 0));
                    }
                    parts.add(p(l.body(), 0));
                    yield "let(" + String.join(", ", parts) + ")";
                }
                default -> throw new IllegalArgumentException("cannot print " + e.getClass().getSimpleName());
            };
        }

        private String call(String name, List<Expr> args, Map<String, Expr> named) {
            List<String> parts = new ArrayList<>();
            args.forEach(a -> parts.add(p(a, 0)));
            named.forEach((k, v) -> parts.add(k + " = " + p(v, 0)));
            return name + "(" + String.join(", ", parts) + ")";
        }
    }

    /** A record's key: bare when it is a name, else quoted. */
    private static String key(String k) {
        boolean name = !k.isEmpty() && (Character.isLetter(k.charAt(0)) || k.charAt(0) == '_');
        for (int i = 1; name && i < k.length(); i++) {
            name = Character.isLetterOrDigit(k.charAt(i)) || k.charAt(i) == '_';
        }
        return name ? k : literal(k);
    }

    /** A literal at level 1.1: an exact number plain, a double with an exponent. */
    static String literal11(Object v) {
        if (v instanceof java.math.BigDecimal b) {
            return b.toPlainString();
        }
        if (v instanceof Double d && !d.isNaN() && !d.isInfinite()) {
            java.math.BigDecimal b = NumberText.decimal(d).stripTrailingZeros();
            String digits = b.unscaledValue().abs().toString();
            int exponent = digits.length() - 1 - b.scale();
            return (b.signum() < 0 ? "-" : "") + digits.charAt(0) + (digits.length() > 1 ? "." + digits.substring(1) : "") + "e" + exponent;
        }
        return literal(v);
    }

    private static int level(String op) {
        return switch (op) {
            case "==", "!=" -> 5;
            case "<", "<=", ">", ">=" -> 6;
            case "+", "-" -> 7;
            default -> 8;
        };
    }

    private static String wrap(String s, int level, int outer) {
        return level < outer ? "(" + s + ")" : s;
    }

    static String literal(Object v) {
        if (v == null) {
            return "null";
        }
        if (v instanceof String s) {
            return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
        }
        if (v instanceof Double d && d == CoreMath.rint(d) && Math.abs(d) < 1e15) {
            return Long.toString(d.longValue());
        }
        if (v instanceof Double d) {
            String s = NumberText.decimal(d).toPlainString();
            return s;
        }
        return v.toString();
    }
}
