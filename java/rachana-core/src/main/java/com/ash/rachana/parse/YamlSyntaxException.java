/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.parse;

/**
 * Text that is not YAML the reader can read. The message is the reader's own text (for a syntax error: the context, the
 * problem and a snippet of each place); the line and column are where reading had got to, 1-based, or 0 when the
 * failure has no place (a limit).
 */
public final class YamlSyntaxException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final int line;
    private final int column;

    public YamlSyntaxException(String message, int line, int column) {
        super(message, null, false, false);
        this.line = line;
        this.column = column;
    }

    /** 1-based line, or 0 when unknown. */
    public int line() {
        return line;
    }

    /** 1-based column, or 0 when unknown. */
    public int column() {
        return column;
    }
}
