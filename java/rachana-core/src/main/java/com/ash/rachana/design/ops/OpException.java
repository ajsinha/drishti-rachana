/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.design.ops;

/**
 * An operation that cannot be applied: carries a stable code and, when the Sutra's text is to blame, the 1-based line.
 * Codes: {@code RCH-5020} the operation itself is malformed, {@code RCH-5021} no such panel, {@code RCH-5022} the panel
 * kind or option is unknown or not accepted, {@code RCH-5023} the value is not valid for the option, {@code RCH-5024} the
 * text cannot be edited in place (for example panels written as one flow list). A result that is not a valid Sutra keeps the
 * parser's own {@code RCH-2nnn} code.
 */
public final class OpException extends RuntimeException {

    public static final String MALFORMED = "RCH-5020";
    public static final String NO_PANEL = "RCH-5021";
    public static final String NOT_ACCEPTED = "RCH-5022";
    public static final String BAD_VALUE = "RCH-5023";
    public static final String UNEDITABLE = "RCH-5024";

    private static final long serialVersionUID = 1L;
    private final String code;
    private final int line;

    public OpException(String code, String message) {
        this(code, message, 0);
    }

    public OpException(String code, String message, int line) {
        super(message);
        this.code = code;
        this.line = line;
    }

    public String code() {
        return code;
    }

    /** The 1-based line in the Sutra's text, or 0 when the problem is not tied to one. */
    public int line() {
        return line;
    }
}
