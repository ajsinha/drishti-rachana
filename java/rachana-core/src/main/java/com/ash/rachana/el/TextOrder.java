/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

/**
 * Texts at language level 1.1 (SPEC 4.13, decision XD4): a character is a code point. Order is code point by code point, then the
 * shorter first; lengths and positions count code points (a lone surrogate counts as one).
 */
final class TextOrder {

    private TextOrder() {}

    /** The order of two texts by code point. */
    static int compare(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            int x = a.codePointAt(i);
            int y = b.codePointAt(j);
            if (x != y) {
                return Integer.compare(x, y);
            }
            i += Character.charCount(x);
            j += Character.charCount(y);
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }

    /** The number of code points. */
    static int length(String s) {
        return s.codePointCount(0, s.length());
    }

    /** The UTF-16 index of code point {@code n} (0-based), clamped to the text's end. */
    static int offset(String s, int n) {
        int i = 0;
        for (int k = 0; k < n && i < s.length(); k++) {
            i += Character.charCount(s.codePointAt(i));
        }
        return i;
    }

    /** Code points {@code from} (0-based, inclusive) to {@code to} (exclusive), clamped. */
    static String sub(String s, int from, int to) {
        int a = offset(s, Math.max(0, from));
        int b = offset(s, Math.max(0, to));
        return a >= b ? "" : s.substring(a, b);
    }
}
