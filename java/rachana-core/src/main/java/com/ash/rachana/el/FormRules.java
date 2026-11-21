/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import com.ash.rachana.internal.NumberText;
import com.ash.rachana.RachanaException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.ash.rachana.model.Ordered;

/**
 * The {@code form} profile of Rachana-EL (split step A7b, owner 2026-11-21): a form's rules, hints and {@code =} defaults are Rachana-EL,
 * read by Rachana-EL's parser ({@code and}, {@code or}, {@code not} are its own operators, PY1), and evaluated here with the meaning
 * form rules always had: numbers are exact decimals ({@code /} to 16 digits, {@link MathContext#DECIMAL64}), {@code +} joins a text,
 * a value that is empty makes an arithmetic or a comparison empty, {@code and}/{@code or} decide when one side decides, and a rule
 * that comes out empty holds. The part of Rachana-EL a form reads: field names ({@code notional}), a lookup's value
 * ({@code counterparty.LIMIT}), numbers, texts, {@code true}, {@code false}, {@code null}, {@code today} (the ISO date), the operators
 * {@code + - * / == != < <= > >= and or not}, brackets, and the functions {@code empty(x) len(x) abs(x) min(a, b) max(a, b)};
 * anything else of the language is refused in words when the form is read.
 */
public final class FormRules {

    /** The most characters of one expression. */
    public static final int MAX_LENGTH = 400;

    private static final Map<String, Integer> FUNCTIONS = Ordered.map("empty", 1, "len", 1, "abs", 1, "min", 2, "max", 2);

    /** The profile's one function the Core table does not have: {@code empty(x)} (the others are Core's names, read with this meaning). */
    private static final FunctionProvider FORM = new FunctionProvider() {
        @Override
        public String source() {
            return "form";
        }

        @Override
        public List<Provided> functions() {
            return List.of(new Provided("empty", 1, 1, "x", null, "true when x is null, an empty text or an empty list (form rules)",
                    "empty(counterparty)", (args, c) -> empty(args.get(0))));
        }
    };

    private static final FunctionTable TABLE = FunctionTable.of(List.of(FORM), Calendars.core(), SafeRegex.Limits.DEFAULTS);

    /** One expression read: its text, its tree and where each node was written. */
    public static final class Compiled {
        private final String text;
        private final String source;
        private final Expr root;
        private final Map<Expr, int[]> spans;

        private Compiled(String text, String source, Expr root, Map<Expr, int[]> spans) {
            this.text = text;
            this.source = source;
            this.root = root;
            this.spans = spans;
        }

        public String text() {
            return text;
        }

        /** The names it reads: {@code notional}, {@code counterparty} for {@code counterparty.LIMIT}. */
        public void names(Set<String> out) {
            FormRules.names(root, out);
        }

        /** Its value over the form's values (a lookup's value is a map of what it carries); null when it cannot be told. */
        public Object eval(Map<String, Object> values, LocalDate today) {
            return ev(root, values, today);
        }

        private Object ev(Expr e, Map<String, Object> v, LocalDate today) {
            return switch (e) {
                case Expr.Lit l -> l.value() instanceof Number ? literal(l) : l.value();
                case Expr.Field f -> field(f, v, today);
                case Expr.Not n -> {
                    Object x = ev(n.e(), v, today);
                    yield x == null ? null : !truthy(x);
                }
                case Expr.Neg n -> {
                    BigDecimal x = number(ev(n.e(), v, today));
                    yield x == null ? null : x.negate();
                }
                case Expr.And a -> logic(true, ev(a.l(), v, today), ev(a.r(), v, today));
                case Expr.Or o -> logic(false, ev(o.l(), v, today), ev(o.r(), v, today));
                case Expr.Binary b -> binary(b.op(), ev(b.l(), v, today), ev(b.r(), v, today));
                case Expr.Call c -> call(c.name(), c.args().stream().map(a -> ev(a, v, today)).toList());
                default -> throw new IllegalStateException("not a form expression: " + e);
            };
        }

        /** A number as it was written (its scale kept: {@code 1.50} stays {@code 1.50}). */
        private BigDecimal literal(Expr.Lit l) {
            int[] at = spans.get(l);
            if (at != null) {
                try {
                    return new BigDecimal(source.substring(at[0], at[1]));
                } catch (NumberFormatException | IndexOutOfBoundsException e) {
                    // written another way (a sign, a suffix): its value
                }
            }
            return number(l.value());
        }
    }

    private FormRules() {}

    /** Reads {@code text}; an {@link IllegalArgumentException} says what is wrong, in words. */
    public static Compiled compile(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("an expression is empty");
        }
        if (text.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("an expression is at most " + MAX_LENGTH + " characters");
        }
        String src = separatorsOut(text);
        Parser.Parsed p;
        try {
            p = Parser.parseWithSpans(src, ElLimits.DEFAULTS, TABLE);
        } catch (RachanaException e) {
            String words = e.words();
            if (words.startsWith("unknown function '")) {
                String name = words.substring(18, words.indexOf('\'', 18));
                throw new IllegalArgumentException("'" + name + "' is not a function here (empty, len, abs, min, max)");
            }
            throw new IllegalArgumentException(words + " (in '" + text + "')");
        }
        check(p.expr(), text);
        return new Compiled(text, src, p.expr(), p.spans());
    }

    /**
     * Form rules write long numbers with {@code _} between digits ({@code 30_000_000}), as they always could; Rachana-EL 1.0 reads
     * them without (X14 brings the separator to the language): taken out here, outside texts.
     */
    private static String separatorsOut(String s) {
        StringBuilder out = new StringBuilder(s.length());
        char quote = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                quote = c == quote ? 0 : quote;
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == '_' && i > 0 && i + 1 < s.length() && isDigit(s.charAt(i - 1)) && isDigit(s.charAt(i + 1))
                    && !inName(s, i)) {
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    /** Whether the {@code _} at {@code i} is inside a name ({@code leg_1_2}): the run of letters, digits and {@code _} it is in starts with a letter. */
    private static boolean inName(String s, int i) {
        int j = i;
        while (j > 0 && (isDigit(s.charAt(j - 1)) || s.charAt(j - 1) == '_' || Character.isLetter(s.charAt(j - 1)))) {
            j--;
        }
        return !isDigit(s.charAt(j));
    }

    /** Whether a rule's value holds: true, or empty (it cannot be told yet). */
    public static boolean holds(Object value) {
        return value == null || truthy(value);
    }

    // ---- the part of the language a form reads ----------------------------------------------------------------------------------

    private static void check(Expr e, String text) {
        switch (e) {
            case Expr.Lit l -> { }
            case Expr.Field f -> {
                if (!(f.target() instanceof Expr.RowOrRoot)
                        && !(f.target() instanceof Expr.Field g && g.target() instanceof Expr.RowOrRoot)) {
                    throw new IllegalArgumentException("'" + path(f) + "' is a field (notional) or a lookup's value (counterparty.LIMIT)");
                }
            }
            case Expr.Not n -> check(n.e(), text);
            case Expr.Neg n -> check(n.e(), text);
            case Expr.And a -> { check(a.l(), text); check(a.r(), text); }
            case Expr.Or o -> { check(o.l(), text); check(o.r(), text); }
            case Expr.Binary b -> {
                if (b.op().equals("%")) {
                    throw new IllegalArgumentException("'%' is not part of the form expressions (in '" + text + "')");
                }
                check(b.l(), text);
                check(b.r(), text);
            }
            case Expr.Call c -> {
                Integer arity = FUNCTIONS.get(c.name());
                if (arity == null) {
                    throw new IllegalArgumentException("'" + c.name() + "' is not a function here (empty, len, abs, min, max)");
                }
                if (c.args().size() != arity || !c.named().isEmpty()) {
                    throw new IllegalArgumentException(c.name() + "() takes " + arity + (arity == 1 ? " value" : " values"));
                }
                c.args().forEach(a -> check(a, text));
            }
            default -> throw new IllegalArgumentException("'" + text + "' uses a part of Rachana-EL a form does not read (fields, numbers, "
                    + "texts, + - * /, comparisons, and, or, not, empty, len, abs, min, max)");
        }
    }

    private static String path(Expr e) {
        return e instanceof Expr.Field f ? (f.target() instanceof Expr.RowOrRoot ? "" : path(f.target()) + ".") + f.name() : "?";
    }

    private static void names(Expr e, Set<String> out) {
        switch (e) {
            case Expr.Field f -> {
                Expr.Field head = f;
                while (!(head.target() instanceof Expr.RowOrRoot) && head.target() instanceof Expr.Field g) {
                    head = g;
                }
                if (head != f || !"today".equals(f.name())) {        // today alone is the date, not a field
                    out.add(head.name());
                }
            }
            case Expr.Not n -> names(n.e(), out);
            case Expr.Neg n -> names(n.e(), out);
            case Expr.And a -> { names(a.l(), out); names(a.r(), out); }
            case Expr.Or o -> { names(o.l(), out); names(o.r(), out); }
            case Expr.Binary b -> { names(b.l(), out); names(b.r(), out); }
            case Expr.Call c -> c.args().forEach(a -> names(a, out));
            default -> { }
        }
    }

    // ---- values ---------------------------------------------------------------------------------------------------------------

    private static Object field(Expr.Field f, Map<String, Object> v, LocalDate today) {
        if (f.target() instanceof Expr.RowOrRoot) {
            if ("today".equals(f.name())) {
                return today == null ? null : today.toString();
            }
            return norm(v.get(f.name()));
        }
        Object head = v.get(((Expr.Field) f.target()).name());
        return head instanceof Map<?, ?> m ? norm(m.get(f.name())) : null;
    }

    private static Object norm(Object o) {
        return o instanceof Number n ? number(n) : o;
    }

    static boolean truthy(Object v) {
        if (v == null) {
            return false;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof BigDecimal d) {
            return d.signum() != 0;
        }
        return !v.toString().isEmpty();
    }

    /** A number when it reads as one (a Number, or text like 25,000,000), else null. */
    public static BigDecimal number(Object v) {
        if (v instanceof BigDecimal d) {
            return d;
        }
        if (v instanceof Number n) {
            return new BigDecimal(n instanceof Double || n instanceof Float ? com.ash.rachana.internal.NumberText.text(NumberText.toDouble(n)) : n.toString());
        }
        if (v instanceof String s && !s.isBlank()) {
            try {
                return new BigDecimal(s.replace(",", "").trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static boolean empty(Object v) {
        return v == null || v instanceof String s && s.isEmpty() || v instanceof Collection<?> c && c.isEmpty();
    }

    private static Object logic(boolean and, Object x, Object y) {
        if (x == null || y == null) {                      // and: false wins; or: true wins; else it cannot be told
            boolean decides = x != null && truthy(x) != and || y != null && truthy(y) != and;
            return decides ? (Boolean) !and : null;
        }
        return and ? truthy(x) && truthy(y) : truthy(x) || truthy(y);
    }

    private static Object binary(String op, Object x, Object y) {
        if (empty(x) || empty(y)) {
            return switch (op) {
                case "==" -> (Boolean) (empty(x) && empty(y));
                case "!=" -> (Boolean) (empty(x) != empty(y));
                default -> null;
            };
        }
        BigDecimal nx = number(x);
        BigDecimal ny = number(y);
        if ("+-*/".contains(op)) {
            if (nx == null || ny == null) {
                return op.equals("+") ? x.toString() + y : null;
            }
            return switch (op) {
                case "+" -> nx.add(ny);
                case "-" -> nx.subtract(ny);
                case "*" -> nx.multiply(ny);
                default -> ny.signum() == 0 ? null : nx.divide(ny, MathContext.DECIMAL64);
            };
        }
        int c = nx != null && ny != null ? nx.compareTo(ny) : x.toString().compareTo(y.toString());
        return switch (op) {
            case "==" -> c == 0;
            case "!=" -> c != 0;
            case "<" -> c < 0;
            case "<=" -> c <= 0;
            case ">" -> c > 0;
            default -> c >= 0;
        };
    }

    private static Object call(String fn, List<Object> xs) {
        switch (fn) {
            case "empty" -> {
                return empty(xs.get(0));
            }
            case "len" -> {
                Object x = xs.get(0);
                return x == null ? BigDecimal.ZERO : BigDecimal.valueOf(x instanceof Collection<?> c ? c.size() : x.toString().length());
            }
            case "abs" -> {
                BigDecimal x = number(xs.get(0));
                return x == null ? null : x.abs();
            }
            default -> {
                BigDecimal x = number(xs.get(0));
                BigDecimal y = number(xs.get(1));
                return x == null || y == null ? null : fn.equals("min") ? x.min(y) : x.max(y);
            }
        }
    }
}
