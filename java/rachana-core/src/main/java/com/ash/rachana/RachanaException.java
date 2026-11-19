/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana;

/**
 * The one exception type that leaves Rachana: it always carries a {@link RachanaCode}. Its message is the code, a space and the
 * words, so a log line names the code; {@link #words()} is the message without it. A host maps it to its own errors at its edge.
 */
public class RachanaException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private final transient RachanaCode code;

    /** An error of the given code with its words. */
    public RachanaException(RachanaCode code, String message) {
        super(message);
        this.code = code;
    }

    /** An error of the given code with its words and the cause. */
    public RachanaException(RachanaCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /** The code this exception carries. */
    public RachanaCode code() {
        return code;
    }

    /** The words, without the code. */
    public String words() {
        return super.getMessage();
    }

    /** The code, a space and the words. */
    @Override
    public String getMessage() {
        return code.code() + " " + super.getMessage();
    }
}
