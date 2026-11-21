/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import com.ash.rachana.internal.CoreMath;
import com.ash.rachana.api.Node;

/**
 * Coercions between evaluation results. A result is a {@link Node}, a {@code String}, a
 * {@code Number}, a {@code Boolean}, a {@link Link} or null. Missing and null behave the same.
 */
public final class Values {

    private Values() {}

    /** Unwraps scalar nodes to Java values; containers stay nodes; missing becomes null. */
    public static Object simplify(Object v) {
        if (v instanceof Node n) {
            return switch (n.kind()) {
                case MISSING, NULL -> null;
                case STRING, NUMBER, BOOLEAN -> n.value();
                default -> n;
            };
        }
        return v;
    }

    /**
     * True for the masked value ({@link Node#MASK}): a field the caller's role may not see, or anything computed from
     * one. Expressions carry the mask through (arithmetic, comparisons, functions), so a value derived from a masked field
     * is masked too, and a condition on one is never true.
     */
    public static boolean masked(Object v) {
        return v instanceof Node n ? n.isMasked() : Node.MASK.equals(v);
    }

    /** The masked value, as an expression result. */
    public static Node mask() {
        return Node.masked();
    }

    public static boolean isNull(Object v) {
        return simplify(v) == null;
    }

    public static double number(Object v) {
        Object s = simplify(v);
        if (s instanceof Number n) {
            return n.doubleValue();
        }
        if (s instanceof String str) {
            if (!couldBeNumber(str)) {
                return Double.NaN;                                // a date, a code or a name: no exception per row
            }
            try {
                return Double.parseDouble(str.trim());
            } catch (NumberFormatException e) {
                return Double.NaN;
            }
        }
        if (s instanceof Boolean b) {
            return b ? 1 : 0;
        }
        return Double.NaN;
    }

    /**
     * False for text {@link Double#parseDouble} certainly rejects: one that starts with something no number starts with
     * ("CP-0042", "ENDUR") or has a minus sign inside that is not an exponent's ("2026-09-09"). Comparisons over a million
     * rows of such text then cost a check each, not a thrown exception each.
     */
    static boolean couldBeNumber(String str) {
        int n = str.length();
        int i = 0;
        while (i < n && Character.isWhitespace(str.charAt(i))) {
            i++;
        }
        if (i == n) {
            return false;
        }
        char first = str.charAt(i);
        if (!(first >= '0' && first <= '9' || first == '+' || first == '-' || first == '.' || first == 'N' || first == 'I')) {
            return false;
        }
        for (int k = i + 1; k < n; k++) {
            char ch = str.charAt(k);
            if ((ch == '-' || ch == '+') && "eEpP".indexOf(str.charAt(k - 1)) < 0) {
                return false;
            }
        }
        return true;
    }

    public static boolean isNumber(Object v) {
        return simplify(v) instanceof Number;
    }

    public static String text(Object v) {
        Object s = simplify(v);
        if (s == null) {
            return "";
        }
        if (s instanceof Double d && d == CoreMath.rint(d) && Math.abs(d) < 1e15) {
            return Long.toString(d.longValue());
        }
        if (s instanceof java.math.BigDecimal b) {
            return b.toPlainString();                             // a decimal as written, never 1E+3
        }
        if (s instanceof Link l) {
            return l.display();
        }
        if (s instanceof Node n) {
            return n.kind() == Node.Kind.ARRAY ? "[" + n.size() + "]" : "{" + n.size() + "}";
        }
        return s.toString();
    }

    public static boolean truthy(Object v) {
        Object s = simplify(v);
        if (s == null || masked(s)) {
            return false;
        }
        if (s instanceof Boolean b) {
            return b;
        }
        if (s instanceof Number n) {
            return n.doubleValue() != 0 && !Double.isNaN(n.doubleValue());
        }
        if (s instanceof String str) {
            return !str.isEmpty();
        }
        if (s instanceof Node n) {
            return n.size() > 0;
        }
        return true;
    }

    /** Numeric results that are whole become {@code Long}, so {@code 1 + 1} prints {@code 2}. */
    public static Number normalise(double d) {
        return d == CoreMath.rint(d) && Math.abs(d) < 1e15 && !Double.isInfinite(d) ? (Number) CoreMath.toLong(d) : (Number) d;
    }

    public static boolean equal(Object a, Object b) {
        Object x = simplify(a);
        Object y = simplify(b);
        if (x == null || y == null) {
            return x == y;
        }
        if (x instanceof Number || y instanceof Number) {
            return number(x) == number(y);
        }
        return text(x).equals(text(y));
    }
}
