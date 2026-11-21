/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.parse;

import java.util.Set;
import com.ash.rachana.model.Ordered;

/**
 * The type of an untagged plain scalar, decided from its text by the YAML 1.1 rules the language was defined with:
 * {@code bool} ({@code yes}, {@code No}, {@code ON}, {@code true} ...), {@code int} (binary {@code 0b}, octal
 * {@code 0...}, decimal, hexadecimal {@code 0x}, base 60), {@code float} (with {@code .}, an exponent, base 60,
 * {@code .inf}, {@code .nan}), {@code null} ({@code ~}, {@code null}, empty) or {@code str}. Written out by hand: Core
 * uses no regular-expression engine. Underscores are allowed in numbers as YAML 1.1 allows them.
 */
final class YamlScalars {

    private static final Set<String> BOOL = Ordered.set("yes", "Yes", "YES", "no", "No", "NO", "true", "True", "TRUE", "false", "False",
            "FALSE", "on", "On", "ON", "off", "Off", "OFF");
    private static final Set<String> NULL = Ordered.set("~", "null", "Null", "NULL");
    private static final int NUMBER_LIMIT = 1024;

    private YamlScalars() {}

    /** {@code bool}, {@code int}, {@code float}, {@code null} or {@code str}. */
    static String resolve(String v) {
        if (v.isEmpty()) {
            return "null";
        }
        char first = v.charAt(0);
        if ("yYnNtTfFoO".indexOf(first) >= 0 && BOOL.contains(v)) {
            return "bool";
        }
        if ("-+0123456789".indexOf(first) >= 0 && v.length() <= NUMBER_LIMIT && isInt(v)) {
            return "int";
        }
        if ("-+0123456789.".indexOf(first) >= 0 && v.length() <= NUMBER_LIMIT && isFloat(v)) {
            return "float";
        }
        if (NULL.contains(v)) {
            return "null";
        }
        return "str";
    }

    private static boolean digit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean hex(char c) {
        return digit(c) || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F';
    }

    /** Index after a run of characters accepted by {@code accept}, starting at {@code i}. */
    private interface Accept {
        boolean ok(char c);
    }

    private static int run(String v, int i, Accept accept) {
        while (i < v.length() && accept.ok(v.charAt(i))) {
            i++;
        }
        return i;
    }

    private static int sign(String v) {
        return !v.isEmpty() && (v.charAt(0) == '-' || v.charAt(0) == '+') ? 1 : 0;
    }

    static boolean isInt(String v) {
        int s = sign(v);
        int n = v.length();
        if (s >= n) {
            return false;
        }
        // [-+]?0b_*[0-1][0-1_]*
        if (v.startsWith("0b", s)) {
            int i = run(v, s + 2, c -> c == '_');
            return i < n && (v.charAt(i) == '0' || v.charAt(i) == '1') && run(v, i + 1, c -> c == '0' || c == '1' || c == '_') == n;
        }
        // [-+]?0x_*[0-9a-fA-F][0-9a-fA-F_]*
        if (v.startsWith("0x", s)) {
            int i = run(v, s + 2, c -> c == '_');
            return i < n && hex(v.charAt(i)) && run(v, i + 1, c -> hex(c) || c == '_') == n;
        }
        if (v.charAt(s) == '0') {
            // [-+]?0 or [-+]?0_*[0-7][0-7_]*
            if (s + 1 == n) {
                return true;
            }
            int i = run(v, s + 1, c -> c == '_');
            return i < n && v.charAt(i) >= '0' && v.charAt(i) <= '7' && run(v, i + 1, c -> c >= '0' && c <= '7' || c == '_') == n;
        }
        if (v.charAt(s) < '1' || v.charAt(s) > '9') {
            return false;
        }
        // [-+]?[1-9][0-9_]* optionally followed by (:[0-5]?[0-9])+
        int i = run(v, s + 1, c -> digit(c) || c == '_');
        return i == n || sexagesimal(v, i) == n;
    }

    /** Consumes {@code (:[0-5]?[0-9])+} from {@code i}; the index after it, or -1. */
    private static int sexagesimal(String v, int i) {
        int n = v.length();
        int groups = 0;
        while (i < n && v.charAt(i) == ':') {
            int j = i + 1;
            if (j + 1 < n && v.charAt(j) >= '0' && v.charAt(j) <= '5' && digit(v.charAt(j + 1))) {
                // greedy [0-5]? then [0-9]; backtrack when the second digit is followed by a third that makes the group fail
                int afterTwo = j + 2;
                if (afterTwo == n || v.charAt(afterTwo) == ':' || v.charAt(afterTwo) == '.') {
                    i = afterTwo;
                } else {
                    return -1;
                }
            } else if (j < n && digit(v.charAt(j))) {
                i = j + 1;
            } else {
                return -1;
            }
            groups++;
        }
        return groups == 0 ? -1 : i;
    }

    /** {@code [eE][-+]?[0-9]+} from {@code i}; the index after it, or -1. */
    private static int exponent(String v, int i) {
        int n = v.length();
        if (i >= n || v.charAt(i) != 'e' && v.charAt(i) != 'E') {
            return -1;
        }
        i++;
        if (i < n && (v.charAt(i) == '-' || v.charAt(i) == '+')) {
            i++;
        }
        int j = run(v, i, YamlScalars::digit);
        return j > i ? j : -1;
    }

    static boolean isFloat(String v) {
        int n = v.length();
        // \.(?:nan|NaN|NAN)
        if (v.equals(".nan") || v.equals(".NaN") || v.equals(".NAN")) {
            return true;
        }
        int s = sign(v);
        if (s >= n) {
            return false;
        }
        // [-+]?\.(?:inf|Inf|INF)
        String rest = v.substring(s);
        if (rest.equals(".inf") || rest.equals(".Inf") || rest.equals(".INF")) {
            return true;
        }
        if (v.charAt(s) == '.') {
            // [-+]?\.[0-9_]+(?:[eE][-+]?[0-9]+)?
            int i = run(v, s + 1, c -> digit(c) || c == '_');
            return i > s + 1 && (i == n || exponent(v, i) == n);
        }
        if (!digit(v.charAt(s))) {
            return false;
        }
        int i = run(v, s + 1, c -> digit(c) || c == '_');
        if (i < n && v.charAt(i) == '.') {
            // [-+]?[0-9][0-9_]*\.[0-9_]*(?:[eE][-+]?[0-9]+)?
            int j = run(v, i + 1, c -> digit(c) || c == '_');
            return j == n || exponent(v, j) == n;
        }
        if (i < n && (v.charAt(i) == 'e' || v.charAt(i) == 'E')) {
            // [-+]?[0-9][0-9_]*[eE][-+]?[0-9]+
            return exponent(v, i) == n;
        }
        if (i < n && v.charAt(i) == ':') {
            // [-+]?[0-9][0-9_]*(?::[0-5]?[0-9])+\.[0-9_]*
            int j = sexagesimal(v, i);
            return j > 0 && j < n && v.charAt(j) == '.' && run(v, j + 1, c -> digit(c) || c == '_') == n;
        }
        return false;
    }
}
