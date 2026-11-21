/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import com.ash.rachana.internal.NumberText;
import com.ash.rachana.internal.CoreText;
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
                case STRING, NUMBER, BOOLEAN -> {
                    Object x = n.value();
                    yield x != null ? x : scalar(n);
                }
                default -> n;
            };
        }
        return v;
    }

    /**
     * A host's scalar node that does not give its {@link Node#value()} (the interface's default is null): read through the accessor of
     * its kind, never taken as empty. A number that gives no number at all is a problem ({@code RCH-2102}), never a silent 0 or empty.
     */
    private static Object scalar(Node n) {
        return switch (n.kind()) {
            case STRING -> n.asText();
            case BOOLEAN -> n.asBoolean();
            default -> {
                double d = n.asDouble();
                if (Double.isNaN(d)) {
                    throw new ElException(com.ash.rachana.RachanaCode.EL_EVAL, "a host value (" + n.getClass().getName()
                            + ") is a number but gives no number: implement value() or asDouble() of com.ash.rachana.api.Node");
                }
                yield normalise(d);
            }
        };
    }

    /**
     * A list or record an expression built, in the type of the document's tree ({@link com.ash.rachana.api.NodeFactory}): as it is for
     * Rachana's own documents and for a node the host already owns; through the host's factory otherwise.
     */
    static Object adopt(Object v, EvalContext c) {
        if (c == null || !(v instanceof Node n) || !(n.kind() == Node.Kind.ARRAY || n.kind() == Node.Kind.OBJECT)) {
            return v;
        }
        com.ash.rachana.api.NodeFactory f = c.nodes();
        return f == com.ash.rachana.api.NodeFactory.core() || n.nodes() == f ? v : f.of(n);
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
            return NumberText.toDouble(n);
        }
        if (s instanceof String str) {
            if (!couldBeNumber(str)) {
                return Double.NaN;                                // a date, a code or a name: no exception per row
            }
            try {
                return NumberText.parse(str.trim());
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
        while (i < n && CoreText.isWhitespace(str.charAt(i))) {
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
        if (s instanceof Double d) {
            return NumberText.text(d);
        }
        if (s instanceof Float f) {
            return NumberText.text(f);
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
            return NumberText.toDouble(n) != 0 && !Double.isNaN(NumberText.toDouble(n));
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
