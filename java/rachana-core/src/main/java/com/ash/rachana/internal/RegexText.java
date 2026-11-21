/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.internal;

import java.util.regex.PatternSyntaxException;

/**
 * Why a pattern is not a regular expression, in the JDK's words ({@code Unclosed character class}). On the JVM it is the JDK's
 * own description; the JavaScript build replaces this class with a same-named one (java/rachana-js) that names the same classes
 * of error, because TeaVM's class library gives no description (split step A15).
 */
public final class RegexText {

    private RegexText() {
    }

    /** The description of the pattern's syntax error. */
    public static String describe(String pattern, PatternSyntaxException e) {
        return e.getDescription();
    }
}
