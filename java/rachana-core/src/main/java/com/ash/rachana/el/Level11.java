/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What language level 1.1 changes in Core's functions, bound by the parser at that level (level 1.0 keeps the functions as they were):
 * {@code range}, Python's sequence only, and {@code span}, the aggregate max − min that 1.0 spells {@code range} (DECISIONS PY4, EL1-1), positions and orders counted by code point (SPEC 4.13, XD4: {@code left}, {@code right}, {@code mid},
 * {@code find}, {@code search}, {@code pad}, {@code least}, {@code greatest}, {@code sort}), and list sums exact over integers and
 * decimals (SPEC 4.16 rule 7).
 */
final class Level11 {

    private Level11() {}

    private record Fn(int min, int max, ElFunction fn) {}

    private static final Map<String, Fn> FUNCTIONS = Map.ofEntries(
            Map.entry("range", new Fn(1, 3, (a, c) -> Ranges.range(a))),
            Map.entry("span", new Fn(1, 2, (a, c) -> FunctionTable.core().get("range").apply(a, c))),
            Map.entry("left", new Fn(2, 2, (a, c) -> part("left", a, true))),
            Map.entry("right", new Fn(2, 2, (a, c) -> part("right", a, false))),
            Map.entry("mid", new Fn(3, 3, (a, c) -> mid(a))),
            Map.entry("find", new Fn(2, 3, (a, c) -> position("find", a))),
            Map.entry("search", new Fn(2, 3, (a, c) -> position("search", a))),
            Map.entry("pad", new Fn(2, 4, Level11::pad)),
            Map.entry("least", new Fn(1, 64, (a, c) -> pick("least", a, true))),
            Map.entry("greatest", new Fn(1, 64, (a, c) -> pick("greatest", a, false))),
            Map.entry("sum", new Fn(1, 2, Level11::sum)),
            Map.entry("standardize", new Fn(3, 3, Level11::standardize)),
            Map.entry("regex_extract", regexAsValueError("regex_extract", 2, 3)),
            Map.entry("regex_replace", regexAsValueError("regex_replace", 3, 3)),
            Map.entry("matches", regexAsValueError("matches", 2, 2)),
            Map.entry("matches_case", regexAsValueError("matches_case", 2, 2)));

    /** A refused pattern met at evaluation is the function's value error {@code RCH-2102} at level 1.1 (decision N5), not {@code RCH-4010}. */
    private static Fn regexAsValueError(String name, int min, int max) {
        return new Fn(min, max, (a, c) -> {
            try {
                return FunctionTable.core().get(name).apply(a, c);
            } catch (com.ash.rachana.RachanaException e) {
                if (e.code() != com.ash.rachana.RachanaCode.REGEX_REFUSED) {
                    throw e;
                }
                String m = e.getMessage();
                String code = e.code().code();
                throw new ElException(com.ash.rachana.RachanaCode.EL_EVAL, name + ": " + (m.startsWith(code + " ") ? m.substring(code.length() + 1) : m));
            }
        });
    }

    /** {@code standardize} is a real-number function at level 1.1: it returns a number (SPEC 4.16 rule 5; decision N5). */
    private static Object standardize(List<Object> a, EvalContext c) {
        Object r = Values.simplify(FunctionTable.core().get("standardize").apply(a, c));
        return r instanceof Number n && !(r instanceof Double) ? (Object) com.ash.rachana.internal.NumberText.toDouble(n) : r;
    }

    /** The forms level 1.1 binds instead of 1.0's. */
    static ElForm form(String name) {
        return "sort".equals(name) ? ListFunctions::sortByCodePoint : null;
    }

    /** True when level 1.1 binds its own {@code name}. */
    static boolean has(String name) {
        return FUNCTIONS.containsKey(name);
    }

    /** The argument counts: Core's own where level 1.1 only changes how a function computes; {@code range}'s widened (PY4). */
    static int[] arity(String name) {
        Fn f = FUNCTIONS.get(name);
        int[] core = FunctionTable.core().arityOf(name);
        return core == null ? new int[] {f.min(), f.max()} : new int[] {Math.min(core[0], f.min()), Math.max(core[1], f.max())};
    }

    static ElFunction function(String name) {
        return FUNCTIONS.get(name).fn();
    }

    /**
     * The literal-argument check of level 1.1 (null when none): {@code range} is only Python's sequence (EL1-1), so a list or a text
     * written as its argument, the 1.0 aggregate's {@code range(list, field)}, is refused with {@code span} named.
     */
    static String check(String name, List<Expr> args) {
        if ("range".equals(name)) {
            for (Expr e : args) {
                if (e instanceof Expr.ListLit || e instanceof Expr.Lit l && l.value() instanceof String) {
                    return Ranges.NOT_AN_AGGREGATE;
                }
            }
        }
        return null;
    }

    /** {@code left(s, n)}, {@code right(s, n)} by code point. */
    private static Object part(String fn, List<Object> a, boolean fromLeft) {
        String s = Args.text(fn, a.get(0));
        Integer n = Args.whole(fn, a.get(1));
        if (s == null || n == null) {
            return null;
        }
        if (n < 0) {
            throw Args.error(fn, "the count " + n + " is negative");
        }
        int len = TextOrder.length(s);
        int k = Math.min(n, len);
        return fromLeft ? TextOrder.sub(s, 0, k) : TextOrder.sub(s, len - k, len);
    }

    /** {@code mid(s, start, n)} by code point. */
    private static Object mid(List<Object> a) {
        String s = Args.text("mid", a.get(0));
        Integer start = Args.whole("mid", a.get(1));
        Integer n = Args.whole("mid", a.get(2));
        if (s == null || start == null || n == null) {
            return null;
        }
        if (start < 1) {
            throw Args.error("mid", "the start " + start + " is before the first character (1)");
        }
        if (n < 0) {
            throw Args.error("mid", "the count " + n + " is negative");
        }
        return TextOrder.sub(s, start - 1, (int) Math.min((long) start - 1 + n, Integer.MAX_VALUE));
    }

    /** {@code find}, {@code search}: the 1.0 function over UTF-16 units, its start and answer moved to code points. */
    private static Object position(String fn, List<Object> a) {
        String s = Args.text(fn, a.get(1));
        Integer start = a.size() > 2 ? Args.whole(fn, a.get(2)) : Integer.valueOf(1);
        if (s == null || start == null || Values.isNull(a.get(0))) {
            return null;
        }
        int len = TextOrder.length(s);
        if (start < 1 || start > len + 1) {
            throw Args.error(fn, "the start " + start + " is not from 1 to the text's length + 1");
        }
        List<Object> units = new ArrayList<>(a);
        if (a.size() > 2) {
            units.set(2, (long) TextOrder.offset(s, start - 1) + 1);
        }
        Object r = Values.simplify(FunctionTable.core().get(fn).apply(units, null));
        if (!(r instanceof Long at) || at <= 0) {
            return r;
        }
        return (long) s.codePointCount(0, (int) (at - 1)) + 1;
    }

    /** {@code pad} widened by code point. */
    private static Object pad(List<Object> a, EvalContext c) {
        String s = Args.text("pad", a.get(0));
        Integer n = Args.whole("pad", a.get(1));
        if (s == null || n == null) {
            return FunctionTable.core().get("pad").apply(a, c);
        }
        List<Object> units = new ArrayList<>(a);
        int extra = s.length() - TextOrder.length(s);
        if (extra > 0 && n >= 0) {
            units.set(1, (long) n + extra);
        }
        return FunctionTable.core().get("pad").apply(units, c);
    }

    /** {@code least}, {@code greatest}: texts by code point; numbers as at level 1.0. */
    private static Object pick(String fn, List<Object> a, boolean least) {
        for (Object v : a) {
            Object s = Values.simplify(v);
            if (s instanceof Number || s instanceof String str && !Double.isNaN(Values.number(str))) {
                return FunctionTable.core().get(fn).apply(a, null);
            }
        }
        String best = null;
        for (Object v : a) {
            if (Args.empty(v)) {
                continue;
            }
            Object s = Values.simplify(v);
            if (!(s instanceof String str)) {
                throw Args.typeError(fn, s, "a number, a date or text");
            }
            if (best == null || (least ? TextOrder.compare(str, best) < 0 : TextOrder.compare(str, best) > 0)) {
                best = str;
            }
        }
        return best;
    }

    /** {@code sum(list)}: exact over integers and decimals (SPEC 4.16 rule 7); with a double among the elements, the 1.0 sum. */
    private static Object sum(List<Object> a, EvalContext c) {
        Object v = Values.simplify(a.get(0));
        String field = a.size() > 1 && Values.simplify(a.get(1)) instanceof String f ? f : null;
        if (a.size() > 1 && field == null) {
            return FunctionTable.core().get("sum").apply(a, c);
        }
        if (v instanceof com.ash.rachana.api.Node list && list.isArray()) {
            java.math.BigDecimal total = java.math.BigDecimal.ZERO;
            boolean any = false;
            for (int i = 0; i < list.size(); i++) {
                com.ash.rachana.api.Node at = field == null ? list.get(i) : list.get(i).get(field);
                Object e = Values.simplify(at);
                if (e == null || Values.masked(at)) {
                    if (Values.masked(at)) {
                        return Values.mask();
                    }
                    continue;
                }
                if (!(e instanceof java.math.BigDecimal || e instanceof Long || e instanceof Integer)) {
                    return FunctionTable.core().get("sum").apply(a, c);
                }
                total = total.add(e instanceof java.math.BigDecimal d ? d : java.math.BigDecimal.valueOf(((Number) e).longValue()));
                any = true;
            }
            return any ? Exact.held(total) : FunctionTable.core().get("sum").apply(a, c);
        }
        return FunctionTable.core().get("sum").apply(a, c);
    }
}
