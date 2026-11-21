/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.internal;

import java.math.BigDecimal;

/**
 * Core's one door between binary doubles and text. On the JVM it is Java's own: {@code Double.toString} prints the shortest
 * decimal that reads back to the double (Java 19 and later, Raffaello Giulietti's algorithm), and {@code Double.parseDouble}
 * reads correctly rounded, with Java's number grammar (at language level 1.0 that grammar stays: a hexadecimal significand,
 * a {@code d} or {@code f} suffix, {@code Infinity} and {@code NaN} read as numbers; the decimal-only grammar waits for level
 * 1.1). The JavaScript build replaces this class with a same-named one whose results are the same bytes, so nothing else in
 * Core prints or reads a double: the guard {@code CoreDeterminismGuardTest} refuses {@code Double.toString},
 * {@code Double.parseDouble} and {@code BigDecimal.valueOf(double)} anywhere else. Static, stateless, thread-safe.
 */
public final class NumberText {

    private NumberText() {
    }

    /** The shortest decimal text that reads back to {@code d}: {@code 0.1}, {@code 1.0E-5}, {@code 1.0E21}, {@code NaN}. */
    public static String text(double d) {
        return Double.toString(d);
    }

    /** A float's shortest text (a JSON value that arrived as a float). */
    public static String text(float f) {
        return Float.toString(f);
    }

    /** The double a number text names, correctly rounded; NumberFormatException for text that is not one. */
    public static double parse(String s) {
        return Double.parseDouble(s);
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
