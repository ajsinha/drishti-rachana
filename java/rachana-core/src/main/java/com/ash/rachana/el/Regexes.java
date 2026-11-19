/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import java.util.concurrent.ConcurrentHashMap;

/**
 * The compiled patterns of one function table's text functions ({@code matches}, {@code regex_extract}, {@code regex_replace}), held
 * with that table's caps ({@code RachanaOptions.regexLimits}): two {@link com.ash.rachana.Rachana} instances with different caps
 * never share a compiled pattern.
 */
final class Regexes {

    private static final int MAX_HELD = 512;

    private final SafeRegex.Limits limits;
    private final ConcurrentHashMap<String, SafeRegex> held = new ConcurrentHashMap<>();

    Regexes(SafeRegex.Limits limits) {
        this.limits = limits == null ? SafeRegex.Limits.DEFAULTS : limits;
    }

    /** The caps every pattern of this table is compiled under. */
    SafeRegex.Limits limits() {
        return limits;
    }

    /** A compiled pattern, held (at most {@value #MAX_HELD}, then forgotten all at once): a refused one raises RCH-4010. */
    SafeRegex pattern(String pattern, boolean ignoreCase) {
        if (held.size() > MAX_HELD) {
            held.clear();
        }
        return held.computeIfAbsent((ignoreCase ? "i:" : "c:") + pattern, k -> SafeRegex.compile(pattern, ignoreCase, limits));
    }

    /** A pattern compiled without holding it (the check of a written pattern when an expression compiles). */
    SafeRegex compile(String pattern, boolean ignoreCase) {
        return SafeRegex.compile(pattern, ignoreCase, limits);
    }
}
