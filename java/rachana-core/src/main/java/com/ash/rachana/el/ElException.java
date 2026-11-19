/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import com.ash.rachana.hostcompat.HostException;
import com.ash.rachana.hostcompat.ErrorCode;

/** A Rachana-EL expression failed to compile ({@code DRS-2101}) or to evaluate ({@code DRS-2102}). */
public class ElException extends HostException {

    private static final long serialVersionUID = 1L;
    private final int position;

    public ElException(String message, int position) {
        super(ErrorCode.EL_SYNTAX, message + " at " + position);
        this.position = position;
    }

    public ElException(ErrorCode code, String message) {
        super(code, message);
        this.position = -1;
    }

    /** 0-based offset in the expression, or -1 for evaluation errors. */
    public int position() {
        return position;
    }
}
