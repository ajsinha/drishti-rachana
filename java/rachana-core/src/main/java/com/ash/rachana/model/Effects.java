/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A panel's effect list (SUTRA_EXPANSION I1, level 1.2): {@code on: { <trigger>: [ <effect>, ... ] }}, the effects run in order when the
 * trigger fires. Built so far: the triggers {@code row-click} (table, ladder), {@code bar-click} (hbar) and {@code point-click} (the marks of
 * {@link Wiring#POINT_KINDS}: a chart's points, steps, events and candles, a figure or a record as one mark), and the effect {@code set}, in
 * its two forms: {@code set: { state: <id>, to: <expression> }} writes a state value (the expression read with {@code @} the clicked
 * row, evaluated per row when the panel binds), and {@code set: <control>} with an optional {@code field:} sets a control from the
 * clicked row, which is the panel's {@code on-select} (one mechanism, one output). A panel keeps its state effects in its options
 * under {@link #KEY}, trigger to a list of {@code {state, to}}.
 */
public final class Effects {

    private Effects() {}

    /** The panel key, and the option the state effects are kept under. */
    public static final String KEY = "on";

    /**
     * The triggers of the closed list (I1); the ones not in {@link #BUILT} are refused until their step. {@code submit} and {@code cancel}
     * are the standalone form panel's: its effects run only after the form's own rules pass (a refused form runs none).
     */
    public static final List<String> TRIGGERS = List.of("row-click", "row-dblclick", "bar-click", "point-click", "cell-click", "change", "open",
            "close", "submit", "cancel");

    /** The triggers built at this level, each with the panel kinds that fire it. */
    public static final Map<String, List<String>> BUILT = Map.of("row-click", List.of("table", "ladder"), "bar-click", List.of("hbar"),
            "point-click", Wiring.POINT_KINDS.stream().sorted().toList());

    /** The effects of the closed list (SUTRA_EXPANSION 3.4); the ones not in {@link #BUILT_EFFECTS} are refused until their step. */
    public static final List<String> EFFECTS = List.of("set", "toggle", "clear", "open", "close", "go", "fire", "submit", "refresh", "export",
            "copy", "notify", "host");

    /**
     * The effects built at this level: {@code set}, {@code toggle: <boolean state>}, {@code clear: <state>} (back to its default),
     * {@code notify: <template>} (with {@code tone:}) and {@code host: <action>} (with {@code with:}), the host's own action, which Rachana
     * hands over and never runs: the host decides.
     */
    public static final List<String> BUILT_EFFECTS = List.of("set", "toggle", "clear", "notify", "host");

    /** The keys an effect takes beside its word and {@code when}, by word. */
    public static final Map<String, java.util.Set<String>> EXTRAS_OF = Map.of("set", java.util.Set.of("field"), "host", java.util.Set.of("with"),
            "notify", java.util.Set.of("tone"));

    /** Every key of {@link #EXTRAS_OF}. */
    public static final java.util.Set<String> EXTRAS = java.util.Set.of("field", "with", "tone");

    /** The tones of a {@code notify}. */
    public static final List<String> TONES = List.of("ok", "warn", "bad", "accent");

    /**
     * The effects of {@code p} on {@code trigger} as the panel keeps them, in order: a plain state write {@code {state, to}}, or
     * {@code {do: <word>, ..., when?}} (empty when it has none).
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> list(Panel p, String trigger) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (p.options().get(KEY) instanceof Map<?, ?> on && on.get(trigger) instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Map<?, ?> m) {
                    out.add((Map<String, Object>) m);
                }
            }
        }
        return out;
    }

    /**
     * Whether the list of {@code trigger} holds more than plain state writes (a {@code when}, or another effect): its marks then carry the
     * whole list ({@code on}) and no {@code set}.
     */
    public static boolean full(Panel p, String trigger) {
        return list(p, trigger).stream().anyMatch(m -> m.containsKey("do"));
    }

    /**
     * One effect that writes a state value.
     *
     * @param state the state's id
     * @param to the expression of its new value, {@code @} the clicked row
     */
    public record SetState(String state, String to) {}

    /** The state effects of {@code p} on {@code trigger}, in order (empty when it has none, or when its list is {@link #full}). */
    public static List<SetState> sets(Panel p, String trigger) {
        List<SetState> out = new ArrayList<>();
        if (full(p, trigger)) {
            return out;                                                    // the whole list is carried instead (on)
        }
        if (p.options().get(KEY) instanceof Map<?, ?> on && on.get(trigger) instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Map<?, ?> m && m.get("state") instanceof String s && m.get("to") instanceof String to) {
                    out.add(new SetState(s, to));
                }
            }
        }
        return out;
    }
}
