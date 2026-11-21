/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.internal;

import java.util.regex.PatternSyntaxException;

/**
 * Why a pattern is not a regular expression, in the JDK's words, in the JavaScript build (split step A15): TeaVM's class library
 * gives no description, so the pattern is scanned left to right as the JDK's parser meets its first error, for the classes a
 * Sutra author makes (an unclosed class or group, a dangling quantifier, an unmatched closing parenthesis); anything else keeps
 * the library's own words. Replaces Core's {@code RegexText} in the bundles.
 */
public final class RegexText {

    private RegexText() {
    }

    /** The description of the pattern's syntax error. */
    public static String describe(String pattern, PatternSyntaxException e) {
        String own = e.getDescription();
        if (own != null && !own.isEmpty()) {
            return own;
        }
        int depth = 0;
        boolean start = true;                              // at the start of the pattern, a group or an alternative
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '\\') {
                i++;
                start = false;
                continue;
            }
            if (c == '[') {
                int end = classEnd(pattern, i);
                if (end < 0) {
                    return "Unclosed character class";
                }
                i = end;
                start = false;
                continue;
            }
            if (c == '(') {
                depth++;
                start = true;
                if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '?') {
                    i++;
                    start = false;
                }
                continue;
            }
            if (c == ')') {
                if (--depth < 0) {
                    return "Unmatched closing ')'";
                }
                start = false;
                continue;
            }
            if ((c == '*' || c == '+' || c == '?') && start) {
                return "Dangling meta character '" + c + "'";
            }
            start = c == '|';
        }
        return depth > 0 ? "Unclosed group" : own == null ? "" : own;
    }

    /** The index of the {@code ]} closing the class opened at {@code open}, or -1. */
    private static int classEnd(String p, int open) {
        int nest = 0;
        int i = open + 1;
        if (i < p.length() && p.charAt(i) == '^') {
            i++;
        }
        if (i < p.length() && p.charAt(i) == ']') {
            i++;                                           // a ']' first in the class is a member
        }
        for (; i < p.length(); i++) {
            char c = p.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == '[') {
                nest++;
            } else if (c == ']') {
                if (nest == 0) {
                    return i;
                }
                nest--;
            }
        }
        return -1;
    }
}
