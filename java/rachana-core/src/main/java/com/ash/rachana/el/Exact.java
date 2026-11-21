/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import com.ash.rachana.RachanaCode;
import com.ash.rachana.api.Node;
import com.ash.rachana.internal.CoreDecimal;
import com.ash.rachana.internal.NumberText;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * The numbers of language level 1.1 (SPEC 4.16, EL_EXPANSION X2, decision XD3): integers and decimals are exact (decimal128, 34
 * significant digits), {@code /} rounds half to even to 34 digits, a double on either side makes the operation binary floating point.
 * An exact value is a {@link BigDecimal} with no trailing zeros (or a {@link Long} a function gave: an integer); a double is a
 * {@link Double} and stays one. Core's own code on every runtime: {@link CoreDecimal} rounds, the quotient is long division.
 */
final class Exact {

    private Exact() {}

    /** decimal128: 34 significant digits, half to even. */
    static final MathContext MC = new MathContext(34, RoundingMode.HALF_EVEN);

    /** The value of a number literal without an exponent ({@code 1.50} is {@code 1.5}); more than 34 significant digits is refused. */
    static BigDecimal literal(String text, int pos) {
        return literal(text, pos, pos + 1);
    }

    /** {@link #literal(String, int)} with where the literal ends in the source. */
    static BigDecimal literal(String text, int pos, int end) {
        BigDecimal d = new BigDecimal(text.startsWith(".") ? "0" + text : text.endsWith(".") ? text + "0" : text);
        if (digits(d.stripTrailingZeros()) > 34) {
            throw new ElException("number '" + text + "' has more than 34 significant digits", pos, end);
        }
        return clean(d);
    }

    /** The value as an operand: an exact {@link BigDecimal}, or a {@link Double} (NaN for what is not a number). */
    static Object operand(Object v) {
        Object s = Values.simplify(v);
        if (s instanceof BigDecimal b) {
            return b;
        }
        if (s instanceof Long || s instanceof Integer || s instanceof Short || s instanceof Byte) {
            return BigDecimal.valueOf(((Number) s).longValue());
        }
        if (s instanceof Number n) {
            return NumberText.toDouble(n);
        }
        if (s instanceof Boolean b) {
            return b ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        if (s instanceof String str) {
            return read(str);
        }
        return Double.NaN;
    }

    /**
     * Number-like text read as a literal is (SPEC 4.16 rule 1): without an exponent exact (rounded to 34 digits when longer), with one
     * a double; anything else (a date, a code, {@code NaN}, hexadecimal) is not a number.
     */
    static Object read(String text) {
        String t = com.ash.rachana.internal.CoreText.strip(text);
        int i = 0;
        int n = t.length();
        if (i < n && (t.charAt(i) == '+' || t.charAt(i) == '-')) {
            i++;
        }
        int digits = 0;
        boolean point = false;
        boolean exponent = false;
        for (int k = i; k < n; k++) {
            char c = t.charAt(k);
            if (c >= '0' && c <= '9') {
                digits++;
            } else if (c == '.' && !point && !exponent) {
                point = true;
            } else if ((c == 'e' || c == 'E') && digits > 0 && !exponent) {
                exponent = true;
                if (k + 1 < n && (t.charAt(k + 1) == '+' || t.charAt(k + 1) == '-')) {
                    k++;
                }
                if (k + 1 >= n) {
                    return Double.NaN;
                }
            } else {
                return Double.NaN;
            }
        }
        if (digits == 0) {
            return Double.NaN;
        }
        if (exponent) {
            return NumberText.parse(t);
        }
        String body = t.charAt(0) == '+' ? t.substring(1) : t;
        body = body.replace("-.", "-0.");
        if (body.startsWith(".")) {
            body = "0" + body;
        }
        if (body.endsWith(".")) {
            body = body + "0";
        }
        return held(new BigDecimal(body));
    }

    /** {@code a op b} for {@code + - * / %} on two operands; the caller has handled empty, masked and text joining. */
    static Object arithmetic(String op, Object a, Object b) {
        if (a instanceof BigDecimal x && b instanceof BigDecimal y) {
            return switch (op) {
                case "+" -> held(x.add(y));
                case "-" -> held(x.subtract(y));
                case "*" -> held(x.multiply(y));
                case "/" -> y.signum() == 0 ? null : divide(x, y);
                case "%" -> y.signum() == 0 ? null : held(remainder(x, y));
                default -> throw new IllegalArgumentException(op);
            };
        }
        double x = toDouble(a);
        double y = toDouble(b);
        return switch (op) {
            case "+" -> x + y;
            case "-" -> x - y;
            case "*" -> x * y;
            case "/" -> y == 0 ? null : (Object) (x / y);
            case "%" -> y == 0 ? null : (Object) (x % y);
            default -> throw new IllegalArgumentException(op);
        };
    }

    /** The order of two numeric operands (exact when both are exact), or null when either is not a number. */
    static Integer compare(Object a, Object b) {
        if (a instanceof BigDecimal x && b instanceof BigDecimal y) {
            return x.compareTo(y);
        }
        double x = toDouble(a);
        double y = toDouble(b);
        if (Double.isNaN(x) || Double.isNaN(y)) {
            return null;
        }
        return Double.compare(x == 0 ? 0.0 : x, y == 0 ? 0.0 : y);
    }

    /** Equality at level 1.1 (SPEC 4.6): empty only equals empty; a number on either side compares numerically; else the texts. */
    static boolean equal(Object a, Object b) {
        Object x = Values.simplify(a);
        Object y = Values.simplify(b);
        if (x == null || y == null) {
            return x == y;
        }
        if (x instanceof Number || y instanceof Number) {
            Integer c = compare(operand(x), operand(y));
            return c != null && c == 0;
        }
        return Values.text(x).equals(Values.text(y));
    }

    /** True for an exact value (a decimal, or an integer a function gave). */
    static boolean isExact(Object v) {
        Object s = Values.simplify(v);
        return s instanceof BigDecimal || s instanceof Long || s instanceof Integer;
    }

    static double toDouble(Object operand) {
        return operand instanceof Number n ? NumberText.toDouble(n) : Double.NaN;
    }

    /** {@code -x}: exact stays exact (no negative zero), a double stays a double. */
    static Object negate(Object operand) {
        if (operand instanceof BigDecimal x) {
            return clean(x.negate());
        }
        return -toDouble(operand);
    }

    /**
     * A result held to decimal128: rounded half to even to 34 significant digits; a result whose integer part needs more than 34 digits
     * is {@code RCH-2102}, never a lost unit.
     */
    static BigDecimal held(BigDecimal r) {
        if (whole(r) > 34) {
            throw overflow();
        }
        BigDecimal out = digits(r) > 34 ? CoreDecimal.round(r, MC) : r;
        if (whole(out) > 34) {
            throw overflow();
        }
        return clean(out);
    }

    private static ElException overflow() {
        return new ElException(RachanaCode.EL_EVAL, "the result needs more than 34 digits before the point");
    }

    /** The quotient rounded half to even to 34 significant digits, by long division (a sticky digit: never a false tie). */
    static BigDecimal divide(BigDecimal x, BigDecimal y) {
        BigInteger ux = x.unscaledValue();
        BigInteger uy = y.unscaledValue();
        int k = Math.max(0, 36 + uy.abs().toString().length() - ux.abs().toString().length());
        BigInteger[] qr = ux.multiply(BigInteger.TEN.pow(k)).divideAndRemainder(uy);
        int scale = x.scale() - y.scale() + k;
        BigInteger q = qr[0];
        if (qr[1].signum() != 0) {
            q = q.multiply(BigInteger.TEN).add(BigInteger.valueOf(ux.signum() * uy.signum()));
            scale++;
        }
        return held(new BigDecimal(q, scale));
    }

    /** The remainder with the sign of {@code x} (truncated division), exact. */
    static BigDecimal remainder(BigDecimal x, BigDecimal y) {
        int s = Math.max(x.scale(), y.scale());
        BigInteger a = x.setScale(s).unscaledValue();
        BigInteger b = y.setScale(s).unscaledValue();
        return new BigDecimal(a.remainder(b), s);
    }

    /** No trailing zeros, zero as {@code 0}. */
    static BigDecimal clean(BigDecimal d) {
        if (d.signum() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal s = d.stripTrailingZeros();
        return s.scale() < 0 ? s.setScale(0) : s;
    }

    private static int digits(BigDecimal x) {
        return x.signum() == 0 ? 1 : x.unscaledValue().abs().toString().length();
    }

    /** The digits before the point. */
    private static int whole(BigDecimal x) {
        return x.signum() == 0 ? 0 : Math.max(0, digits(x) - x.scale());
    }

    /** {@code a ** b}: exact for an exact base and a whole exponent of at most 64 ({@code power}'s rule), else a double. */
    static Object power(Object a, Object b) {
        if (a instanceof BigDecimal && b instanceof BigDecimal) {
            Object r = MathFunctions.power(java.util.List.of(a, b));
            Object s = Values.simplify(r);
            if (s instanceof BigDecimal d) {
                return held(d);
            }
            if (s instanceof Long l) {
                return BigDecimal.valueOf(l);
            }
            return s;
        }
        return com.ash.rachana.internal.CoreMath.pow(toDouble(a), toDouble(b));
    }

    /** A value as an element of a list or a field of a record. */
    static Node node(Object v) {
        return Args.node(v);
    }
}
