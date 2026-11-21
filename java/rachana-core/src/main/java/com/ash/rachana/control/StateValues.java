/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.control;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The state values a bind request carries (SUTRA_EXPANSION S1, decision S1-OPEN a), exactly as {@link ControlValues} carries the
 * controls': {@code state.<id>=text} parameters, kept by the host per reader between binds and sent back with each one. The text of a
 * value is its type's request form ({@code StateTypes}: a list's items joined by commas). A value for an id the Sutra does not hold, or
 * one that does not fit its type, is ignored (the default stands).
 *
 * @param raw the text per state id, in request order
 */
public record StateValues(Map<String, String> raw) {

    /** The prefix of a request parameter that carries a state value. */
    public static final String PREFIX = "state.";
    /** No values: every state at its default. */
    public static final StateValues NONE = new StateValues(Map.of());
    /** The longest value a request may carry for one state (the rest is dropped). */
    public static final int MAX_VALUE = ControlValues.MAX_VALUE;
    /** The most state values a request carries (a screen holds at most 40, {@code StateEntry.MAX}). */
    public static final int MAX_STATES = com.ash.rachana.model.StateEntry.MAX;
    /** The longest parameter name ({@code state.} and the id, a fragment's private state {@code <as>.<name>}) a request may carry. */
    public static final int MAX_KEY = 90;

    public StateValues {
        raw = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(raw));
    }

    /** Reads {@code state.<id>=value} parameters from a request's parameters (a repeated name's values joined by commas). */
    public static StateValues of(Map<String, String[]> params) {
        Map<String, String> raw = new LinkedHashMap<>();
        params.forEach((k, v) -> {
            if (v == null || v.length == 0 || raw.size() >= MAX_STATES) {
                return;
            }
            if (k.startsWith(PREFIX) && k.length() > PREFIX.length() && k.length() <= MAX_KEY) {
                String joined = String.join(",", v);
                raw.put(k.substring(PREFIX.length()), joined.length() > MAX_VALUE ? joined.substring(0, MAX_VALUE) : joined);
            }
        });
        return raw.isEmpty() ? NONE : new StateValues(raw);
    }

    /** The text the request carried for state {@code id}, or null. */
    public String get(String id) {
        return raw.get(id);
    }

    /** The values as request parameters ({@code state.order=O-7}), for a link or a stream. */
    public Map<String, String> asParams() {
        Map<String, String> out = new LinkedHashMap<>();
        raw.forEach((k, v) -> out.put(PREFIX + k, v));
        return out;
    }
}
