/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import com.ash.rachana.internal.NumberText;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * One section of an Excel number code ({@code #,##0.00}, {@code 0.0%}, {@code "USD "#,##0}, {@code 0.00E+00}) printed in
 * decimal by Core, with no {@code java.text}. The grammar: literal text first (double-quoted text and any character that is not
 * {@code 0 # . , %}), then the number ({@code #} digits before {@code 0} digits in the whole part, commas in it to group by the
 * digits after the last comma, a full stop, then {@code 0} digits before {@code #} digits; or a scientific number:
 * {@code 0} digits, an optional fraction, {@code E+} or {@code E-} and {@code 0} digits; the exponent's sign is printed
 * either way), then literal text. A {@code %}
 * anywhere in the literal text multiplies by 100. Rounding is half away from zero at the last digit the code shows; a negative
 * number is preceded by {@code -}, also when it rounds to zero (the section for negatives, when the code has one, is chosen
 * before and gets the number's size). The output equals the {@code java.text.DecimalFormat} the reference used for every code
 * in this grammar ({@code NumberCodeTest}); a code outside it is refused.
 */
final class NumberCode {

    private final String prefix;
    private final String suffix;
    private final boolean percent;
    private final int minInt;
    private final int minFrac;
    private final int maxFrac;
    private final int grouping;
    private final boolean dotAlways;
    private final int expDigits;          // 0 when not scientific

    private NumberCode(String prefix, String suffix, boolean percent, int minInt, int minFrac, int maxFrac, int grouping,
            boolean dotAlways, int expDigits) {
        this.prefix = prefix;
        this.suffix = suffix;
        this.percent = percent;
        this.minInt = minInt;
        this.minFrac = minFrac;
        this.maxFrac = maxFrac;
        this.grouping = grouping;
        this.dotAlways = dotAlways;
        this.expDigits = expDigits;
    }

    /** The section read; null when it is not in the grammar. */
    static NumberCode read(String f) {
        StringBuilder pre = new StringBuilder();
        StringBuilder suf = new StringBuilder();
        boolean percent = false;
        int i = literal(f, 0, pre);
        if (i < 0) {
            return null;
        }
        percent |= pre.indexOf("%") >= 0;
        int hashes = 0;
        int zeros = 0;
        int lastComma = -1;
        int intDigits = 0;
        int n = f.length();
        while (i < n && "#0,".indexOf(f.charAt(i)) >= 0) {
            char ch = f.charAt(i++);
            if (ch == '#') {
                if (zeros > 0) {
                    return null;
                }
                hashes++;
                intDigits++;
            } else if (ch == '0') {
                zeros++;
                intDigits++;
            } else {
                lastComma = intDigits;
            }
        }
        if (intDigits == 0 && (i >= n || f.charAt(i) != '.')) {
            return null;
        }
        int grouping = lastComma < 0 ? 0 : intDigits - lastComma;
        if (lastComma >= 0 && grouping == 0) {
            return null;
        }
        int minFrac = 0;
        int maxFrac = 0;
        boolean dot = false;
        if (i < n && f.charAt(i) == '.') {
            dot = true;
            i++;
            while (i < n && (f.charAt(i) == '0' || f.charAt(i) == '#')) {
                if (f.charAt(i) == '0') {
                    if (maxFrac > minFrac) {
                        return null;
                    }
                    minFrac++;
                }
                maxFrac++;
                i++;
            }
        }
        int expDigits = 0;
        if (i + 1 < n && (f.charAt(i) == 'E' || f.charAt(i) == 'e') && (f.charAt(i + 1) == '+' || f.charAt(i + 1) == '-')) {
            i += 2;
            while (i < n && f.charAt(i) == '0') {
                expDigits++;
                i++;
            }
            if (expDigits == 0 || hashes > 0 || zeros == 0 || grouping > 0) {
                return null;
            }
        }
        if (literal(f, i, suf) != n) {
            return null;
        }
        if (zeros == 0 && dot && hashes + maxFrac > 0) {
            if (hashes > 0) {
                zeros = 1;                                              // "#.##" reads as "0.##"
            } else if (minFrac == 0) {
                minFrac = 1;                                            // ".##" reads as ".0#"
            }
        }
        percent |= suf.indexOf("%") >= 0;
        return new NumberCode(pre.toString(), suf.toString(), percent, zeros, minFrac, maxFrac, grouping,
                dot && maxFrac == 0, expDigits);
    }

    /** Reads literal text from {@code i} into {@code out}; returns where the number starts (or the end), -1 for an open quote. */
    private static int literal(String f, int i, StringBuilder out) {
        int n = f.length();
        while (i < n) {
            char ch = f.charAt(i);
            if (ch == '"') {
                int end = f.indexOf('"', i + 1);
                out.append(f, i + 1, end < 0 ? n : end);
                i = end < 0 ? n : end + 1;
            } else if ("0#.,".indexOf(ch) >= 0) {
                return i;
            } else {
                out.append(ch);
                i++;
            }
        }
        return i;
    }

    String format(BigDecimal value) {
        BigDecimal v = percent ? value.movePointRight(2) : value;
        boolean negative = v.signum() < 0;
        String number = expDigits > 0 ? scientific(v.abs()) : plain(v.abs());
        return (negative ? "-" : "") + prefix + number + suffix;
    }

    private String plain(BigDecimal a) {
        BigDecimal r = a.setScale(maxFrac, RoundingMode.HALF_UP);
        String digits = r.toPlainString();
        int dot = digits.indexOf('.');
        String whole = dot < 0 ? digits : digits.substring(0, dot);
        String frac = dot < 0 ? "" : digits.substring(dot + 1);
        int keep = frac.length();
        while (keep > minFrac && frac.charAt(keep - 1) == '0') {
            keep--;
        }
        frac = frac.substring(0, keep);
        if (whole.equals("0")) {
            whole = "";
        }
        StringBuilder w = new StringBuilder();
        for (int k = whole.length(); k < minInt; k++) {
            w.append('0');
        }
        w.append(whole);
        if (w.length() == 0 && frac.isEmpty()) {
            w.append('0');                                              // no digits at all: a zero
        }
        String grouped = grouping > 0 ? group(w.toString()) : w.toString();
        return frac.isEmpty() && !dotAlways ? grouped : grouped + "." + frac;
    }

    private String group(String whole) {
        StringBuilder b = new StringBuilder();
        int lead = whole.length() % grouping;
        for (int k = 0; k < whole.length(); k++) {
            if (k > 0 && (k - lead) % grouping == 0) {
                b.append(',');
            }
            b.append(whole.charAt(k));
        }
        return b.toString();
    }

    private String scientific(BigDecimal a) {
        int exponent;
        BigDecimal mantissa;
        if (a.signum() == 0) {
            exponent = 0;
            mantissa = BigDecimal.ZERO;
        } else {
            BigDecimal r = a.round(new MathContext(minInt + maxFrac, RoundingMode.HALF_UP));
            exponent = r.precision() - r.scale() - minInt;
            mantissa = r.movePointLeft(exponent);
        }
        String m = mantissa.setScale(maxFrac, RoundingMode.HALF_UP).toPlainString();
        int dot = m.indexOf('.');
        String whole = dot < 0 ? m : m.substring(0, dot);
        String frac = dot < 0 ? "" : m.substring(dot + 1);
        int keep = frac.length();
        while (keep > minFrac && frac.charAt(keep - 1) == '0') {
            keep--;
        }
        frac = frac.substring(0, keep);
        StringBuilder b = new StringBuilder();
        for (int k = whole.length(); k < minInt; k++) {
            b.append('0');
        }
        b.append(whole);
        if (!frac.isEmpty() || dotAlways) {
            b.append('.').append(frac);
        }
        b.append('E');
        if (exponent < 0) {
            b.append('-');
        } else {
            b.append('+');                                              // E+ and E- alike, as the reference printed them
        }
        return b.append(NumberText.pad(Math.abs(exponent), expDigits)).toString();
    }
}
