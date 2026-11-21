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
    public static final Map<String, List<String>> BUILT = Ordered.map("row-click", List.of("table", "ladder"), "bar-click", List.of("hbar"),
            "point-click", Wiring.POINT_KINDS.stream().sorted().toList(), "submit", List.of("form"), "cancel", List.of("form"));

    /**
     * The triggers whose effects run with the event's values in hand ({@code submit}, {@code cancel}: the form's values): an effect reads
     * them as {@code event.<field>} (a whole {@code to:} or {@code with:} value, filled in when the trigger fires, {@link #EVENT_REF}).
     */
    public static final List<String> AT_FIRE = List.of("submit", "cancel");

    /**
     * The triggers a control fires (level 1.2, on the control: {@code controls: [ { id, kind, ..., on: { change: [ effects ] } } ]}):
     * {@code change}, when the reader picks a new value. Its effects run with that value in hand, read as {@code event} (the whole value:
     * a text, a list of a multi-select's choices, a range as {@code from..to}); the control itself takes the value as any change does.
     */
    public static final List<String> CONTROL_TRIGGERS = List.of("change");

    /** The compile result's key of the controls' effect lists ({@link #control}). */
    public static final String CONTROLS_KEY = "$controlEffects";

    /**
     * The effects of control {@code control} of {@code s} on {@code trigger} as the Sutra keeps them, in order ({@code {do: <word>, ...}}
     * or a plain state write {@code {state, to}}); empty when it has none.
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> control(com.ash.rachana.model.Sutra s, String control, String trigger) {
        if (s.extension(CONTROLS_KEY) instanceof Map<?, ?> all && all.get(control) instanceof Map<?, ?> on && on.get(trigger) instanceof List<?> l) {
            List<Map<String, Object>> out = new ArrayList<>();
            l.forEach(o -> out.add((Map<String, Object>) o));
            return out;
        }
        return List.of();
    }

    /** The controls of {@code s} that hold an effect list, by id: trigger to effects (empty when none does). */
    @SuppressWarnings("unchecked")
    public static Map<String, Map<String, List<Map<String, Object>>>> controls(com.ash.rachana.model.Sutra s) {
        return s.extension(CONTROLS_KEY) instanceof Map<?, ?> all ? (Map<String, Map<String, List<Map<String, Object>>>>) all : Map.of();
    }

    /**
     * A value filled in when the trigger fires: {@code event} (every value) or {@code event.<field>} (one, dotted for a value inside a
     * value), parentheses and spaces aside (a fragment's event put in as its payload's expression: {@code (event).qty}).
     */
    public static final java.util.regex.Pattern EVENT_REF = java.util.regex.Pattern.compile("^event((?:\\.[A-Za-z_][A-Za-z0-9_-]*)*)$");

    /** The path {@code text} reads from the event ({@code ""} for the whole event, {@code "qty"}, {@code "values.qty"}), or null. */
    public static String eventPath(Object text) {
        if (!(text instanceof String s)) {
            return null;
        }
        StringBuilder bare = new StringBuilder();                          // (event).qty, as a fragment's payload put in, reads event.qty
        int depth = 0;
        for (char c : s.toCharArray()) {
            if (c == '(' || c == ')') {
                depth += c == '(' ? 1 : -1;
                if (depth < 0) {
                    return null;
                }
            } else if (!Character.isWhitespace(c)) {
                bare.append(c);
            }
        }
        java.util.regex.Matcher m = EVENT_REF.matcher(bare.toString());
        if (depth != 0 || !m.matches()) {
            return null;
        }
        return m.group(1).isEmpty() ? "" : m.group(1).substring(1);
    }

    /** The panel key of the buttons in the panel's header ({@code actions:}), and the option they are kept under. */
    public static final String ACTIONS = "actions";

    /** The keys of one action: {@code label} and {@code do} (its effects) required. */
    public static final java.util.Set<String> ACTION_KEYS = Ordered.set("id", "label", "tone", "confirm", "requires", "do");

    /** The most actions a panel has. */
    public static final int MAX_ACTIONS = 8;

    /** The trigger a press of an action fires (conformance {@code fire}: the mark is the action's place). */
    public static final String ACTION = "action";

    /**
     * The actions of {@code p} as the panel keeps them, in order: {@code {id, label, tone?, confirm?, requires?, do: [effect, ...]}} (empty
     * when it has none).
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> actions(Panel p) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (p.options().get(ACTIONS) instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Map<?, ?> m) {
                    out.add((Map<String, Object>) m);
                }
            }
        }
        return out;
    }

    /** Every effect of {@code p}: its {@code on:} lists, trigger by trigger, then its actions' effects, in order. */
    public static List<Map<String, Object>> every(Panel p) {
        List<Map<String, Object>> out = new ArrayList<>();
        BUILT.keySet().stream().sorted().forEach(t -> out.addAll(list(p, t)));
        actions(p).forEach(a -> out.addAll(effectsOf(a)));
        return out;
    }

    /** The effects of one action as the panel keeps it (empty when it has none). */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> effectsOf(Map<String, Object> action) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (action.get("do") instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Map<?, ?> m) {
                    out.add((Map<String, Object>) m);
                }
            }
        }
        return out;
    }

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
    public static final Map<String, java.util.Set<String>> EXTRAS_OF = Ordered.map("set", java.util.Set.of("field"), "host", java.util.Set.of("with"),
            "notify", java.util.Set.of("tone"));

    /** Every key of {@link #EXTRAS_OF}. */
    public static final java.util.Set<String> EXTRAS = Ordered.set("field", "with", "tone");

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
        return AT_FIRE.contains(trigger) && !list(p, trigger).isEmpty() || list(p, trigger).stream().anyMatch(m -> m.containsKey("do"));
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
