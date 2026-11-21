/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import com.ash.rachana.RachanaException;
import com.ash.rachana.RachanaCode;

/** A Rachana-EL expression failed to compile ({@code RCH-2101}) or to evaluate ({@code RCH-2102}). */
public class ElException extends RachanaException {

    private static final long serialVersionUID = 1L;
    private final int position;
    private final int end;

    public ElException(String message, int position) {
        this(message, position, -1);
    }

    /** A syntax problem over {@code position} to {@code end} (the offending token, level 1.1), at {@code position}. */
    ElException(String message, int position, int end) {
        super(RachanaCode.EL_SYNTAX, message + " at " + position);
        this.position = position;
        this.end = end;
    }

    /** A syntax problem whose message is already whole (level 1.1's {@code expression '<source>': <reason> at <offset>}). */
    ElException(int position, int end, String whole) {
        super(RachanaCode.EL_SYNTAX, whole);
        this.position = position;
        this.end = end;
    }

    public ElException(RachanaCode code, String message) {
        super(code, message);
        this.position = -1;
        this.end = -1;
    }

    /** Where the offending part ends (exclusive), when known (level 1.1); -1 otherwise (it is then one character). */
    public int end() {
        return end;
    }

    /** 0-based offset in the expression, or -1 for evaluation errors. */
    public int position() {
        return position;
    }
}
