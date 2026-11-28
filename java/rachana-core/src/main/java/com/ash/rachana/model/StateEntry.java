/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.model;

/**
 * One named state value of a screen (SUTRA_EXPANSION S1, level 1.2): a value with no widget, read in expressions as
 * {@code state.<id>} and written only by an effect ({@code set: { state: <id>, to: <expression> }}, I1). A fragment's private state
 * (DESIGN.md (libraries) 4.4, 7.2) is listed with the screen's under the id {@code <as>.<name>} of the use that brought it.
 *
 * @param id the state's id ({@code order}; {@code md.query} for a fragment's private state)
 * @param type one of {@link #TYPES}
 * @param dflt its value when the request carries none: a text, a number, a boolean, an ISO date text or a list of text; null for empty
 * @param url whether the host keeps it in the address (S4; refused until the view-state document exists, always false today)
 * @param owner the {@code as} of the fragment use whose private state it is, or null for the screen's own
 * @param output whether a private state is one of its fragment's {@code outputs:} (the screen may read it, never write it)
 */
public record StateEntry(String id, String type, Object dflt, boolean url, String owner, boolean output) {

    /** The types a state value may have (SUTRA_EXPANSION S1). */
    public static final java.util.List<String> TYPES = java.util.List.of("text", "number", "boolean", "date", "list");

    /** The most state values a screen holds, its fragments' private ones included (SUTRA_EXPANSION S1). */
    public static final int MAX = 40;

    /** Whether the screen's own expressions and effects may name it: its own state, or a fragment's output (read only). */
    public boolean visible() {
        return owner == null || output;
    }
}
