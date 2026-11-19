/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.hostcompat;

/** The single exception type crossing module boundaries. It always carries an {@link ErrorCode}. */
public class HostException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private final transient ErrorCode errorCode;

    /** An error of the given code with its message. */
    public HostException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /** An error of the given code with its message and the cause. */
    public HostException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /** The error code this exception carries. */
    public ErrorCode errorCode() {
        return errorCode;
    }

    /** The code, a space and the message. */
    @Override
    public String getMessage() {
        return errorCode.code() + " " + super.getMessage();
    }
}
