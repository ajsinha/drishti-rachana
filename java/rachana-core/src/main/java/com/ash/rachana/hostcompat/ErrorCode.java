/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.hostcompat;

/**
 * TEMPORARY (split step A10 to A1): the error codes the moved compiler raises, with the code text and HTTP status the host gave
 * them, so behaviour is byte-identical to the reference. Retired by A1 (RachanaException and RCH- codes).
 */
public enum ErrorCode {
    /** The JSON given could not be read. */
    INVALID_JSON("DRS-1005", 422),
    /** The Sutra parsed but is not valid. */
    SUTRA_INVALID("DRS-2002", 422),
    /** An expression has a syntax error. */
    EL_SYNTAX("DRS-2101", 422),
    /** An expression could not be evaluated over the values given. */
    EL_EVAL("DRS-2102", 422),
    /** A regular expression refused: too long, too large once expanded, or a feature the linear-time engine does not offer. */
    REGEX_REFUSED("DRS-4010", 400),
    /** A regular expression ran over its time budget on one value. */
    REGEX_TIMEOUT("DRS-4011", 408),
    /** An expression that does not fit its scope, its types or the catalogue. */
    EXPR_INVALID("DRS-9127", 422),
    /** A key, function or value of a later wave used before that wave. */
    LATER_WAVE("DRS-9129", 422),
    /** A field, measure or name the model lacks. */
    MQ_NAME("DRS-9152", 422),
    /** A filter, sort or top on a masked field. */
    MQ_MASKED("DRS-9161", 403);

    private final String code;
    private final int httpStatus;

    ErrorCode(String code, int httpStatus) {
        this.code = code;
        this.httpStatus = httpStatus;
    }

    /** The code text. */
    public String code() {
        return code;
    }

    /** The HTTP status a host answers with for this error. */
    public int httpStatus() {
        return httpStatus;
    }
}
