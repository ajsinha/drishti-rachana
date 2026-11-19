/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana;

/**
 * The codes a {@link RachanaException} carries. Every code is {@code RCH-} and four digits; the numbers are the ones the compiler
 * has always used, so a host maps a code to its own by the number alone (SPEC section 7). Problems in a Sutra carry their codes as
 * text ({@link SutraProblem#code()}), from the same {@code RCH-} space.
 */
public enum RachanaCode {
    /** The Sutra parsed but is not valid: the exception carries every problem found. */
    SUTRA_INVALID("RCH-2002"),
    /** An expression has a syntax error. */
    EL_SYNTAX("RCH-2101"),
    /** An expression could not be evaluated over the values given. */
    EL_EVAL("RCH-2102"),
    /** A regular expression refused: too long, too large once expanded, or a feature the linear-time engine does not offer. */
    REGEX_REFUSED("RCH-4010"),
    /** A regular expression ran over its time budget on one value. */
    REGEX_TIMEOUT("RCH-4011"),
    /** A checked expression that does not fit its scope, its types or the function catalogue. */
    EXPR_INVALID("RCH-9127"),
    /** A key, function or value of a later language level used before that level. */
    LATER_WAVE("RCH-9129"),
    /** A checked expression names a field or function its scope does not have. */
    NAME_UNKNOWN("RCH-9152"),
    /** A checked expression filters, sorts or ranks by a field that is masked for the reader. */
    MASKED_FIELD("RCH-9161");

    private final String code;

    RachanaCode(String code) {
        this.code = code;
    }

    /** The code text, {@code RCH-} and four digits. */
    public String code() {
        return code;
    }
}
