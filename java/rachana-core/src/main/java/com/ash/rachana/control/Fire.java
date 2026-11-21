/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.control;

import com.ash.rachana.view.ViewJson;
import com.ash.rachana.view.ViewModel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A trigger fired on a ViewModel as a painter fires it (SUTRA_EXPANSION I1, level 1.2; conformance kind {@code fire}): the mark the trigger
 * lands on (a table's rows, depth first through a tree's or a drill table's children; an {@code hbar}'s bars; a chart's points, steps,
 * events, candles or x positions; the one mark of a metric, a gauge or a record), the effects that mark carries, run in order, and the
 * state the screen holds after them. Rachana runs only the effects on its own state; the others ({@code host}, {@code go}, ...) are
 * handed to the host, which decides. A trigger the panel does not fire, or a mark it does not show, is a refusal ({@code RCH-5001}):
 * nothing runs.
 */
public final class Fire {

    private Fire() {}

    /** The refusal code: a panel, trigger or mark the screen does not have. */
    public static final String REFUSED = "RCH-5001";

    /**
     * What a fired trigger did.
     *
     * @param state every state value of the screen after the effects (id to value), in the ViewModel's order
     * @param effects the effects run, in order: {@code {do: set, state, value}}, and those handed to the host
     * @param refusals the refusals, each {@code {code, message}}; when there is one nothing ran
     */
    public record Result(Map<String, Object> state, List<Map<String, Object>> effects, List<Map<String, Object>> refusals) {}

    /**
     * Fires {@code trigger} on mark {@code mark} (0-based, in the order the ViewModel lists the marks) of panel {@code panel} of {@code vm}.
     */
    public static Result fire(ViewModel vm, String panel, String trigger, int mark) {
        Map<String, Object> tree = map(ViewJson.tree(vm));
        Map<String, Object> state = state(tree);
        Map<String, Object> p = find(tree.get("panels"), panel);
        if (p == null) {
            return refused(state, "no panel '" + panel + "' on the screen");
        }
        Map<String, Object> data = map(p.get("data"));
        List<Map<String, Object>> marks = marks(trigger, data);
        if (marks == null) {
            return refused(state, "panel '" + panel + "' (" + p.get("kind") + ") does not fire " + trigger);
        }
        if (mark < 0 || mark >= marks.size()) {
            return refused(state, "panel '" + panel + "' shows no mark " + mark + " (it shows " + marks.size() + ")");
        }
        List<Map<String, Object>> effects = new ArrayList<>();
        map(marks.get(mark).get("set")).forEach((id, value) -> {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("do", "set");
            e.put("state", id);
            e.put("value", value);
            effects.add(e);
            state.put(id, value);
        });
        return new Result(state, effects, List.of());
    }

    /** The state values {@code vm} holds (id to value, as its {@code state[]} lists them). */
    public static Map<String, Object> state(ViewModel vm) {
        return state(map(ViewJson.tree(vm)));
    }

    private static Map<String, Object> state(Map<String, Object> tree) {
        Map<String, Object> state = new LinkedHashMap<>();
        if (tree.get("state") instanceof List<?> l) {
            for (Object o : l) {
                Map<String, Object> s = map(o);
                state.put(String.valueOf(s.get("id")), s.get("value"));
            }
        }
        return state;
    }

    /** The marks {@code trigger} lands on in a panel's data, each a map that may carry {@code set}; null when the panel does not fire it. */
    static List<Map<String, Object>> marks(String trigger, Map<String, Object> data) {
        List<Map<String, Object>> out = new ArrayList<>();
        switch (trigger) {
            case "row-click", "row-dblclick" -> {
                if (!(data.get("rows") instanceof List<?> rows) || !data.containsKey("columns")) {
                    return null;
                }
                rows(rows, out);
            }
            case "bar-click" -> {
                if (!(data.get("bars") instanceof List<?> bars)) {
                    return null;
                }
                bars.forEach(b -> out.add(map(b)));
            }
            case "point-click" -> {
                for (String k : List.of("points", "steps", "events", "candles", "cells")) {
                    if (data.get(k) instanceof List<?> l) {
                        l.forEach(x -> out.add(map(x)));
                        return out;
                    }
                }
                if (data.get("series") instanceof List<?> && data.get("x") instanceof List<?> x) {      // a line or area chart: one mark per x
                    List<?> sets = data.get("set") instanceof List<?> s ? s : List.of();
                    for (int i = 0; i < x.size(); i++) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        if (i < sets.size()) {
                            m.put("set", sets.get(i));
                        }
                        out.add(m);
                    }
                    return out;
                }
                if (data.containsKey("fields") || data.containsKey("value") && !data.containsKey("rows")) {   // a record, a metric, a gauge
                    out.add(data);
                    return out;
                }
                return null;
            }
            default -> {
                return null;
            }
        }
        return out;
    }

    private static void rows(List<?> rows, List<Map<String, Object>> out) {
        for (Object o : rows) {
            Map<String, Object> r = map(o);
            out.add(r);
            if (r.get("children") instanceof List<?> kids) {
                rows(kids, out);
            }
        }
    }

    /** The panel {@code id} among {@code panels}, looked for through tabs and children too. */
    static Map<String, Object> find(Object panels, String id) {
        if (panels instanceof List<?> l) {
            for (Object o : l) {
                Map<String, Object> p = map(o);
                if (id.equals(p.get("id")) && p.containsKey("kind")) {
                    return p;
                }
                Map<String, Object> data = map(p.get("data"));
                if (data.get("tabs") instanceof List<?> tabs) {
                    for (Object t : tabs) {
                        Map<String, Object> in = find(map(t).get("panels"), id);
                        if (in != null) {
                            return in;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static Result refused(Map<String, Object> state, String message) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", REFUSED);
        r.put("message", message);
        return new Result(state, List.of(), List.of(r));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }
}
