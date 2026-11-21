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
 * A Rachana tree as text, in the one spelling of a language level ({@link #print(Expr, String)}): at 1.0 {@code &&}, {@code ||},
 * {@code !}, {@code c ? a : b}, named arguments {@code name = value}; from 1.1 Python's {@code and}, {@code or}, {@code not},
 * {@code a if c else b}, {@code name=value}; at every level {@code true}, {@code false}, {@code null}, lists {@code [a, b]}, {@code in}
 * and {@code not in}.
 * Parentheses only where the grammar needs them, so printing then parsing gives the same tree. Used by the migration of part B to write
 * the expressions it rewrites, and by the tests that read the shipped DEL expressions as Rachana.
 */
public final class Printer {

    private Printer() {}

    // precedence, loosest first (even numbers; an odd one sits between two): ternary 2, ?? 4, || 6, && 8, Python's not 9, == != 10,
    // < <= > >= in 12, + - 14, * / % 16, unary 18, ** 20, postfix 22
    /** {@code e} as text at language level 1.0: {@code print(e, "1.0")}. */
    public static String print(Expr e) {
        return print(e, "1.0");
    }

    /** {@code e} as text at level 1.1 when {@code v11}, else at 1.0: {@code print(e, v11 ? "1.1" : "1.0")}. */
    public static String print(Expr e, boolean v11) {
        return print(e, v11 ? "1.1" : "1.0");
    }

    /**
     * {@code e} as text at a Sutra's language level (SUTRA_EXPANSION 6.2), the forms that level writes, so that parsing the text at
     * that level gives the same tree:
     *
     * <ul>
     *   <li>{@code 1.0}: {@code &&}, {@code ||}, {@code !}, {@code c ? a : b}, named arguments {@code where = v} (the frozen reference's
     *       text, byte for byte);</li>
     *   <li>{@code 1.1} and later: Python's forms (DECISIONS PY1, PY2): {@code and}, {@code or}, {@code not} (below the comparisons, as
     *       Python reads it), {@code a if c else b}, named arguments {@code where=v}; a double literal keeps an exponent ({@code 1e-3});
     *       the level 1.1 nodes in their one spelling (<code>{a: 1, ...r}</code>, {@code [...a]}, {@code a ?? b}, {@code a ** b},
     *       {@code t[a:b]}, {@code lambda x: ...}).</li>
     * </ul>
     *
     * A comment is not part of the tree and is not printed.
     *
     * @throws IllegalArgumentException for a level this runtime does not implement
     */
    public static String print(Expr e, String level) {
        if (level == null || !com.ash.rachana.profile.Grammar.LEVELS.contains(level)) {
            throw new IllegalArgumentException("language level '" + level + "' is not one of "
                    + String.join(", ", com.ash.rachana.profile.Grammar.LEVELS));
        }
        boolean v11 = !"1.0".equals(level);
        // the stack guard: a starved host thread prints again on one with the stack the depth bound needs
        return com.ash.rachana.internal.Deep.call(() -> new Printer.Run(v11).p(e, 0), () -> {
            throw new ElException("the expression is nested too deeply to print", 0);
        });
    }

    /** One print: {@code v11} is level 1.1 and later (Python's forms, exponent literals). */
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
                case Expr.Field f -> f.target() instanceof Expr.RowOrRoot ? f.name() : p(f.target(), 22) + "." + f.name();
                case Expr.Pluck f -> p(f.list(), 22) + "." + f.name();
                case Expr.Each x -> p(x.target(), 22) + "[*]";
                case Expr.At a -> p(a.target(), 22) + "[" + p(a.index(), 0) + "]";
                case Expr.Filter f -> p(f.target(), 22) + "[?" + p(f.predicate(), 0) + "]";
                case Expr.ListLit l -> "[" + String.join(", ", l.items().stream().map(x -> p(x, 0)).toList()) + "]";
                case Expr.Not n -> v11 ? wrap("not " + p(n.e(), 9), 9, outer) : wrap("!" + p(n.e(), 18), 18, outer);
                case Expr.Neg n -> wrap("-" + p(n.e(), 18), 18, outer);
                case Expr.Minus n -> wrap("-" + p(n.e(), 18), 18, outer);
                case Expr.And a -> v11 ? wrap(p(a.l(), 8) + " and " + p(a.r(), 9), 8, outer)
                        : wrap(p(a.l(), 8) + " && " + p(a.r(), 10), 8, outer);
                case Expr.Or o -> wrap(p(o.l(), 6) + (v11 ? " or " : " || ") + p(o.r(), 8), 6, outer);
                case Expr.Coalesce o -> wrap(p(o.l(), 4) + " ?? " + p(o.r(), 6), 4, outer);
                case Expr.Ternary t -> v11 ? wrap(p(t.then(), 4) + " if " + p(t.cond(), 4) + " else " + p(t.otherwise(), 2), 2, outer)
                        : wrap(p(t.cond(), 4) + " ? " + p(t.then(), 2) + " : " + p(t.otherwise(), 2), 2, outer);
                case Expr.In i -> wrap(p(i.value(), 14) + (i.negated() ? " not in " : " in ") + p(i.list(), 14), 12, outer);
                case Expr.Binary b -> {
                    int level = level(b.op());
                    yield wrap(p(b.l(), level) + " " + b.op() + " " + p(b.r(), level + 2), level, outer);
                }
                case Expr.Op b -> {
                    int level = level(b.op());
                    yield wrap(p(b.l(), level) + " " + b.op() + " " + p(b.r(), level + 2), level, outer);
                }
                case Expr.Power w -> wrap(p(w.l(), 22) + " ** " + p(w.r(), 18), 20, outer);
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
                case Expr.Slice x -> p(x.target(), 22) + "[" + (x.start() == null ? "" : p(x.start(), 0)) + ":"
                        + (x.stop() == null ? "" : p(x.stop(), 0)) + (x.step() == null ? "" : ":" + p(x.step(), 0)) + "]";
                case Expr.Let l -> {
                    List<String> parts = new ArrayList<>();
                    for (int i = 0; i < l.names().size(); i++) {
                        parts.add(l.names().get(i) + (v11 ? "=" : " = ") + p(l.values().get(i), 0));
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
            named.forEach((k, v) -> parts.add(k + (v11 ? "=" : " = ") + p(v, 0)));
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
            case "==", "!=" -> 10;
            case "<", "<=", ">", ">=" -> 12;
            case "+", "-" -> 14;
            default -> 16;
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
