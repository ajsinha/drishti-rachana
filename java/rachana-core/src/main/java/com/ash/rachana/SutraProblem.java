/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana;

import com.ash.rachana.model.SourceLocation;

/**
 * One thing wrong with a Sutra file, in the shape of SPEC 6.1: its code, severity, message, where it starts ({@code location}),
 * the JSON Pointer of the YAML node ({@code path}, {@code /panels/2/limit}) and the node's whole {@code range}.
 *
 * <p>A problem is raised with its code, message and location; the parser places it in the file it read ({@code path} and
 * {@code range}, {@link com.ash.rachana.parse.ProblemPlaces}) before a caller sees it. {@code path} and {@code range} are null
 * only for a problem located in another file than the one compiled (a fragment, a library's lock), whose text the compile did not
 * read. {@code doc}, {@code fix} and {@code expected} of SPEC 6.1 come in a later minor.
 *
 * @param code stable {@code RCH-2nnn} code
 * @param severity {@link #ERROR} (the Sutra does not load), {@link #WARNING} or {@link #INFO}
 * @param message what is wrong and how to fix it
 * @param location where it starts: file, 1-based line and column
 * @param path RFC 6901 JSON Pointer to the YAML node ({@code ""} for the document), or null (see above)
 * @param range the node's start and end (end exclusive), or null (see above)
 */
public record SutraProblem(String code, String severity, String message, SourceLocation location, String path, Range range) {

    /** The Sutra does not load. */
    public static final String ERROR = "error";
    /** The Sutra loads; the author should look. */
    public static final String WARNING = "warning";
    /** A note. */
    public static final String INFO = "info";

    /**
     * Where a problem's node is written: 1-based lines and columns (a column in code points, as the reader counts), 0-based offsets
     * in UTF-16 code units (SPEC 4.13 item 4); the end exclusive.
     *
     * @param line the start line
     * @param column the start column
     * @param offset the start offset
     * @param endLine the end line
     * @param endColumn the end column
     * @param endOffset the end offset
     */
    public record Range(int line, int column, int offset, int endLine, int endColumn, int endOffset) {}

    public SutraProblem {
        severity = severity == null ? ERROR : severity;
    }

    /** An error at {@code location}, not yet placed. */
    public SutraProblem(String code, String message, SourceLocation location) {
        this(code, ERROR, message, location, null, null);
    }

    /** True when the Sutra does not load because of it. */
    public boolean error() {
        return ERROR.equals(severity);
    }

    /** The same problem as a warning. */
    public SutraProblem warning() {
        return new SutraProblem(code, WARNING, message, location, path, range);
    }

    /** The same problem with its node's path and range. */
    public SutraProblem placed(String path, Range range) {
        return new SutraProblem(code, severity, message, location, path, range);
    }

    @Override
    public String toString() {
        return location + " " + code + " " + message;
    }
}
