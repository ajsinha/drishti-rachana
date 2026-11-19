/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.model;

/**
 * A position in a Sutra file, for error messages and Studio highlighting.
 *
 * @param file the file name
 * @param line 1-based line
 * @param column 1-based column
 */
public record SourceLocation(String file, int line, int column) {

    public static final SourceLocation UNKNOWN = new SourceLocation("?", 0, 0);

    @Override
    public String toString() {
        return file + ":" + line + ":" + column;
    }
}
