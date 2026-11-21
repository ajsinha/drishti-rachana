/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.internal;

import java.math.BigDecimal;
import java.math.BigInteger;
import org.teavm.jso.JSBody;

/**
 * Core's one door between binary doubles and text, in the JavaScript build (split step A15): the same bytes as the JVM's
 * {@code NumberText} (java/rachana-core), which this class replaces in the bundles (the build reads this module's classes before
 * Core's). TeaVM's own {@code Double.toString} is not the shortest round trip for subnormals and its {@code parseDouble} misrounds
 * near the smallest normal and reads no hexadecimal (P2 classes D3, D4); the engine's number conversions are exact, so the
 * digits come from the engine and the layout and grammar are Java's here:
 *
 * <ul>
 * <li>text: the engine's shortest round-trip digits ({@code toExponential()}, ECMAScript Number::toString), with Java's rule for a
 * one-digit result (the closest of the one- and two-digit decimals that read back: {@code 4.9E-324}, not {@code 5e-324}),
 * laid out as {@code Double.toString} does (plain from 10<sup>-3</sup> to 10<sup>7</sup>, else {@code d.dddE±n});</li>
 * <li>parse: Java's grammar (white space at most U+0020 trimmed, sign, {@code NaN}, {@code Infinity}, digits, fraction, exponent,
 * a {@code d}/{@code f} suffix, hexadecimal significands with a binary exponent), the value read by the engine, which rounds
 * correctly; a hexadecimal number becomes its exact decimal first.</li>
 * </ul>
 */
public final class NumberText {

    private NumberText() {
    }

    @JSBody(params = "d", script = "return d.toExponential();")
    private static native String shortestExponential(double d);

    @JSBody(params = { "d", "f" }, script = "return d.toExponential(f);")
    private static native String exponential(double d, int f);

    @JSBody(params = { "d", "p" }, script = "return d.toPrecision(p);")
    private static native String precision(double d, int p);

    @JSBody(params = "s", script = "return Number(s);")
    private static native double number(String s);

    @JSBody(params = "d", script = "return Math.fround(d);")
    private static native double fround(double d);

    /** The shortest decimal text that reads back to {@code d}: {@code 0.1}, {@code 1.0E-5}, {@code 1.0E21}, {@code NaN}. */
    public static String text(double d) {
        if (d != d) {
            return "NaN";
        }
        if (d == Double.POSITIVE_INFINITY) {
            return "Infinity";
        }
        if (d == Double.NEGATIVE_INFINITY) {
            return "-Infinity";
        }
        if (d == 0) {
            return 1 / d < 0 ? "-0.0" : "0.0";
        }
        String e = shortestExponential(d);
        if (digitsOf(e) == 1) {                                 // Java: the closest decimal of length 1 or 2 that reads back
            String two = exponential(d, 1);
            if (number(two) == d) {
                e = two;
            }
        }
        return layout(e);
    }

    /** A float's shortest text (a JSON value that arrived as a float), as {@code Float.toString}. */
    public static String text(float f) {
        double d = f;
        if (d != d || Double.isInfinite(d) || d == 0) {
            return d != d ? "NaN" : d == 0 ? (1 / d < 0 ? "-0.0" : "0.0") : d > 0 ? "Infinity" : "-Infinity";
        }
        for (int p = 1; p <= 9; p++) {
            String s = precision(d, p);
            if (fround(number(s)) == d) {
                String e = exponential(number(s), p - 1);
                if (p == 1) {
                    String two = exponential(d, 1);
                    if (fround(number(two)) == d) {
                        e = two;
                    }
                }
                return layout(e);
            }
        }
        return layout(shortestExponential(d));
    }

    /** The number of significant digits of an engine exponential text ({@code -1.25e+3}: 3). */
    private static int digitsOf(String e) {
        int n = 0;
        for (int i = 0; i < e.length() && e.charAt(i) != 'e'; i++) {
            char c = e.charAt(i);
            if (c >= '0' && c <= '9') {
                n++;
            }
        }
        return n;
    }

    /** {@code -1.2345e+2} laid out as Java's {@code Double.toString}: {@code -123.45}. */
    private static String layout(String e) {
        int at = e.indexOf('e');
        boolean negative = e.charAt(0) == '-';
        StringBuilder digits = new StringBuilder();
        for (int i = negative ? 1 : 0; i < at; i++) {
            char c = e.charAt(i);
            if (c != '.') {
                digits.append(c);
            }
        }
        while (digits.length() > 1 && digits.charAt(digits.length() - 1) == '0') {
            digits.setLength(digits.length() - 1);
        }
        int exp = Integer.parseInt(e.substring(at + 1).replace("+", ""));
        StringBuilder out = new StringBuilder(negative ? "-" : "");
        int n = digits.length();
        if (exp >= -3 && exp < 7) {
            if (exp >= 0) {
                for (int i = 0; i <= exp; i++) {
                    out.append(i < n ? digits.charAt(i) : '0');
                }
                out.append('.');
                if (n > exp + 1) {
                    out.append(digits, exp + 1, n);
                } else {
                    out.append('0');
                }
            } else {
                out.append("0.");
                for (int i = -1; i > exp; i--) {
                    out.append('0');
                }
                out.append(digits);
            }
        } else {
            out.append(digits.charAt(0)).append('.');
            if (n > 1) {
                out.append(digits, 1, n);
            } else {
                out.append('0');
            }
            out.append('E').append(exp);
        }
        return out.toString();
    }

    /** The double a number text names, correctly rounded; NumberFormatException for text that is not one (Java's grammar). */
    public static double parse(String s) {
        if (s == null) {
            throw new NullPointerException();
        }
        int from = 0;
        int to = s.length();
        while (from < to && s.charAt(from) <= ' ') {
            from++;
        }
        while (to > from && s.charAt(to - 1) <= ' ') {
            to--;
        }
        if (from == to) {
            throw new NumberFormatException(s.isEmpty() ? "empty String" : "For input string: \"" + s + "\"");
        }
        String t = s.substring(from, to);
        int i = 0;
        boolean negative = false;
        if (t.charAt(0) == '+' || t.charAt(0) == '-') {
            negative = t.charAt(0) == '-';
            i = 1;
        }
        String body = t.substring(i);
        if (body.equals("NaN")) {
            return Double.NaN;
        }
        if (body.equals("Infinity")) {
            return negative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        }
        if (body.startsWith("0x") || body.startsWith("0X")) {
            BigDecimal exact = hexadecimal(body.substring(2), s);
            double v = exact == null ? 0 : number(exact.toString());
            return negative ? -v : v;
        }
        int end = body.length();
        if (end > 0 && "dDfF".indexOf(body.charAt(end - 1)) >= 0) {
            end--;
        }
        int k = 0;
        int whole = 0;
        while (k < end && isDigit(body.charAt(k))) {
            k++;
            whole++;
        }
        int fraction = 0;
        if (k < end && body.charAt(k) == '.') {
            k++;
            while (k < end && isDigit(body.charAt(k))) {
                k++;
                fraction++;
            }
        }
        if (whole + fraction == 0) {
            throw bad(s);
        }
        if (k < end && (body.charAt(k) == 'e' || body.charAt(k) == 'E')) {
            k++;
            if (k < end && (body.charAt(k) == '+' || body.charAt(k) == '-')) {
                k++;
            }
            int expDigits = 0;
            while (k < end && isDigit(body.charAt(k))) {
                k++;
                expDigits++;
            }
            if (expDigits == 0) {
                throw bad(s);
            }
        }
        if (k != end) {
            throw bad(s);
        }
        double v = number(body.substring(0, end));
        return negative ? -v : v;
    }

    /** The exact value of a hexadecimal significand and binary exponent ({@code 1.8p3}), null for zero. */
    private static BigDecimal hexadecimal(String h, String s) {
        int end = h.length();
        if (end > 0 && "dDfF".indexOf(h.charAt(end - 1)) >= 0) {
            end--;
        }
        int p = -1;
        for (int i = 0; i < end; i++) {
            if (h.charAt(i) == 'p' || h.charAt(i) == 'P') {
                p = i;
                break;
            }
        }
        if (p < 0) {
            throw bad(s);
        }
        StringBuilder digits = new StringBuilder();
        int afterPoint = 0;
        boolean point = false;
        for (int i = 0; i < p; i++) {
            char c = h.charAt(i);
            if (c == '.' && !point) {
                point = true;
            } else if (Character.digit(c, 16) >= 0) {
                digits.append(c);
                if (point) {
                    afterPoint++;
                }
            } else {
                throw bad(s);
            }
        }
        if (digits.length() == 0) {
            throw bad(s);
        }
        String e = h.substring(p + 1, end);
        int k = e.startsWith("+") || e.startsWith("-") ? 1 : 0;
        if (k == e.length()) {
            throw bad(s);
        }
        for (int i = k; i < e.length(); i++) {
            if (!isDigit(e.charAt(i))) {
                throw bad(s);
            }
        }
        BigInteger mantissa = new BigInteger(digits.toString(), 16);
        if (mantissa.signum() == 0) {
            return null;
        }
        long exp = Long.parseLong(e.startsWith("+") ? e.substring(1) : e) - 4L * afterPoint;
        if (exp > 2000) {
            return new BigDecimal("1e400");                     // above the largest double: reads as infinity
        }
        if (exp < -2200) {
            return new BigDecimal("1e-400");                    // below half the smallest subnormal: reads as zero
        }
        if (exp >= 0) {
            return new BigDecimal(mantissa.shiftLeft((int) exp));
        }
        return new BigDecimal(mantissa.multiply(BigInteger.valueOf(5).pow((int) -exp)), (int) -exp);
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static NumberFormatException bad(String s) {
        return new NumberFormatException("For input string: \"" + s + "\"");
    }

    /** A number as a double, correctly rounded: a decimal or big integer through its text (TeaVM's {@code BigDecimal.doubleValue} misrounds). */
    public static double toDouble(Number n) {
        if (n instanceof BigDecimal || n instanceof BigInteger) {
            return number(n.toString());
        }
        return n.doubleValue();
    }

    /** The double as a decimal with the digits of its shortest text: {@code decimal(2.675)} is {@code 2.675}. */
    public static BigDecimal decimal(double d) {
        return new BigDecimal(text(d));
    }

    /** A whole number zero-padded to at least {@code width} digits ({@code String.format("%0" + width + "d")}). */
    public static String pad(long n, int width) {
        String digits = Long.toString(Math.abs(n));
        StringBuilder b = new StringBuilder(width + 1);
        if (n < 0) {
            b.append('-');
            width--;
        }
        for (int k = digits.length(); k < width; k++) {
            b.append('0');
        }
        return b.append(digits).toString();
    }

    /** Lower-case hexadecimal zero-padded to {@code width} digits ({@code String.format("%04x")} for a char). */
    public static String hex(int n, int width) {
        String h = Integer.toHexString(n);
        StringBuilder b = new StringBuilder(width);
        for (int k = h.length(); k < width; k++) {
            b.append('0');
        }
        return b.append(h).toString();
    }

    /** Plain decimal text with its whole part grouped by three with commas: {@code -1234567.5} is {@code -1,234,567.5}. */
    public static String group(String plain) {
        int signs = plain.startsWith("-") || plain.startsWith("+") ? 1 : 0;
        int dot = plain.indexOf('.');
        String whole = plain.substring(signs, dot < 0 ? plain.length() : dot);
        StringBuilder sb = new StringBuilder(plain.length() + whole.length() / 3);
        sb.append(plain, 0, signs);
        int lead = whole.length() % 3;
        for (int i = 0; i < whole.length(); i++) {
            if (i > 0 && (i - lead) % 3 == 0) {
                sb.append(',');
            }
            sb.append(whole.charAt(i));
        }
        if (dot >= 0) {
            sb.append(plain, dot, plain.length());
        }
        return sb.toString();
    }
}
