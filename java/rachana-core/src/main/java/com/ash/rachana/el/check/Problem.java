/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el.check;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One finding about an expression in checked mode (moved from DEL): its code ({@link #LANGUAGE} {@code RCH-9127} for the type or
 * scope, {@link #LATER} {@code RCH-9129} for what is not yet built, {@link #NAME} {@code RCH-9152} for an unknown name, {@link #MASKED}
 * {@code RCH-9161} for a masked field filtered), what is wrong, how to fix it, and where ({@code start} to {@code end}, character
 * offsets in the text).
 *
 * @param level {@code error} or {@code warning}
 * @param fix what to write instead (null when there is no mechanical fix)
 */
public record Problem(String code, String level, String message, String fix, int start, int end) {

    public static final String ERROR = "error";
    public static final String WARNING = "warning";

    /** A type or scope the expression may not have (DEL's code, kept). */
    public static final String LANGUAGE = "RCH-9127";
    /** What is not yet built (a function of the next release, Q23). */
    public static final String LATER = "RCH-9129";
    /** A name the table or model does not have. */
    public static final String NAME = "RCH-9152";
    /** A filter, sort, {@code in} or grouping on a masked field. */
    public static final String MASKED = "RCH-9161";
    /**
     * A style hint at level 1.1 (DECISIONS EL1-2, a warning): {@code -x ** n} or {@code -x ^ n} without parentheses, which is
     * {@code -(x ** n)} by Python's rule where Excel reads {@code (-x) ^ n}.
     */
    public static final String SIGN_POWER = "RCH-2091";

    public boolean error() {
        return ERROR.equals(level);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("level", level);
        m.put("message", message);
        m.put("fix", fix);
        Map<String, Object> at = new LinkedHashMap<>();                  // never Map.of: its order changes from run to run
        at.put("start", start);
        at.put("end", end);
        m.put("at", at);
        return m;
    }
}
