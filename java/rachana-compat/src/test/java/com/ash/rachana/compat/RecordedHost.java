/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.compat;

import java.util.*;

/**
 * Hooks that answer from a recording (SUTRA_EXPANSION 6.6): the answers a host gave while a golden was made (records and rows from the
 * table provider and the source resolvers, link resolutions, mask decisions, calendar and clock, results of profile functions by call).
 * A call that was not recorded fails with {@link UnrecordedCall}: that is itself a finding, Core started asking the host something new.
 * A call recorded several times answers in the order recorded, the last answer standing for any call after.
 *
 * <p>Recording file: {@code {"calls":[{"hook":"table.rows","args":[...],"answer":...}, ...]}}. The adapters of the hook interfaces
 * (a method per hook) arrive with Core's public hooks; they call {@link #answer(String, Object...)}.
 */
final class RecordedHost {
    /** An answer asked for that the recording does not hold. */
    static final class UnrecordedCall extends AssertionError {
        UnrecordedCall(String m) { super(m); }
    }

    private final Map<String, List<Object>> answers = new LinkedHashMap<>();
    private final Map<String, Integer> asked = new HashMap<>();

    RecordedHost(List<Object> calls) {
        for (Object c : calls) {
            Map<String, Object> m = Json.object(c);
            String hook = (String) m.get("hook");
            if (hook == null || !m.containsKey("answer")) throw new IllegalArgumentException("a recorded call needs hook and answer: " + c);
            Object args = m.getOrDefault("args", List.of());
            answers.computeIfAbsent(key(hook, Json.array(args)), k -> new ArrayList<>()).add(m.get("answer"));
        }
    }

    static RecordedHost fromJson(String text) { return new RecordedHost(Json.array(Json.object(Json.parse(text)).get("calls"))); }

    Object answer(String hook, Object... args) {
        String k = key(hook, Arrays.asList(args));
        List<Object> a = answers.get(k);
        if (a == null) throw new UnrecordedCall("unrecorded host call: " + k);
        int n = asked.merge(k, 1, Integer::sum);
        return a.get(Math.min(n, a.size()) - 1);
    }

    /** Recorded calls never asked: a recording that holds more than the replay needs. */
    List<String> unused() { return answers.keySet().stream().filter(k -> !asked.containsKey(k)).toList(); }

    private static String key(String hook, List<?> args) { return hook + Json.canonical(args); }
}
