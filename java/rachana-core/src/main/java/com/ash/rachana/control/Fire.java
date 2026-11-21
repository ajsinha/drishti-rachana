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
 * state the screen holds after them. Rachana runs only the effects on its own state ({@code set}, {@code toggle}, {@code clear}); the
 * others ({@code notify}, {@code host}) are handed to the host, which decides. A trigger the panel does not fire, or a mark it does not show, is a refusal ({@code RCH-5001}):
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
        return fire(vm, null, panel, trigger, mark, null, null);
    }

    /** The refusal code of a form whose own rules do not pass: nothing runs; one refusal per problem, with its {@code field}. */
    public static final String FORM_REFUSED = "RCH-5003";

    /**
     * As {@link #fire(ViewModel, String, String, int)}, with a form's values ({@code submit}, {@code cancel}: {@code values} the fields'
     * values, read by the effects as {@code event}) and the Sutra the view was built from, whose form rules a {@code submit} must pass
     * first (each failing check a refusal {@link #FORM_REFUSED}, its message the form's, and nothing runs). The trigger {@code action} presses the panel's button
     * {@code mark} (its place among the panel's actions).
     */
    public static Result fire(ViewModel vm, com.ash.rachana.model.Sutra sutra, String panel, String trigger, int mark, Map<String, Object> values,
            java.time.LocalDate today) {
        Map<String, Object> tree = map(ViewJson.tree(vm));
        Map<String, Object> state = state(tree);
        Map<String, Object> p = find(tree.get("panels"), panel);
        if (p == null) {
            return refused(state, "no panel '" + panel + "' on the screen");
        }
        Map<String, Object> data = map(p.get("data"));
        if (com.ash.rachana.model.Effects.ACTION.equals(trigger)) {             // a button of the panel's header
            List<?> actions = p.get("actions") instanceof List<?> l ? l : List.of();
            if (mark < 0 || mark >= actions.size()) {
                return refused(state, "panel '" + panel + "' has no action " + mark + " (it has " + actions.size() + ")");
            }
            return new Result(state, run(map(actions.get(mark)).get("effects"), state, Map.of()), List.of());
        }
        if (com.ash.rachana.model.Effects.AT_FIRE.contains(trigger)) {           // a standalone form: submit, cancel
            if (!"form".equals(p.get("kind")) || mark != 0) {
                return refused(state, "panel '" + panel + "' (" + p.get("kind") + ") does not fire " + trigger + (mark != 0 ? " on mark " + mark : ""));
            }
            Map<String, Object> event = values == null ? Map.of() : values;
            if ("submit".equals(trigger)) {
                List<Map<String, Object>> problems = formProblems(sutra, panel, event, today);
                if (!problems.isEmpty()) {
                    return new Result(state, List.of(), problems);
                }
            }
            return new Result(state, run(map(data.get("on")).get(trigger), state, event), List.of());
        }
        List<Map<String, Object>> marks = marks(trigger, data);
        if (marks == null) {
            return refused(state, "panel '" + panel + "' (" + p.get("kind") + ") does not fire " + trigger);
        }
        if (mark < 0 || mark >= marks.size()) {
            return refused(state, "panel '" + panel + "' shows no mark " + mark + " (it shows " + marks.size() + ")");
        }
        if (map(marks.get(mark).get("on")).get(trigger) instanceof List<?> list) {     // the whole list: state writes run here, the rest is handed over
            return new Result(state, run(list, state, Map.of()), List.of());
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

    /**
     * A control's {@code change} (level 1.2, I1) as a painter fires it: control {@code control} of {@code vm} takes {@code value} (a text, a
     * list for a multi-select, {@code from..to} for a range) and the effects its {@code on.change} carries run in order, {@code event} the
     * new value. A control the screen does not have is a refusal ({@link #REFUSED}): nothing runs. The new value itself goes into the next
     * bind request with the state the effects wrote.
     */
    public static Result change(ViewModel vm, String control, Object value) {
        Map<String, Object> tree = map(ViewJson.tree(vm));
        Map<String, Object> state = state(tree);
        if (tree.get("controls") instanceof List<?> l) {
            for (Object o : l) {
                Map<String, Object> c = map(o);
                if (control.equals(c.get("id"))) {
                    return new Result(state, runEvent(map(c.get("on")).get("change"), state, value), List.of());
                }
            }
        }
        return refused(state, "no control '" + control + "' on the screen");
    }

    /**
     * Runs an effect list as the view carries it (EffectView): {@code set}, {@code toggle} and {@code clear} change {@code state}; a value
     * read from the event ({@code event}, {@code withEvent}) is filled in from {@code event}; the others are handed over as they are.
     *
     * @return the effects run, in order, each with its value
     */
    public static List<Map<String, Object>> run(Object list, Map<String, Object> state, Map<String, Object> event) {
        return runEvent(list, state, event);
    }

    /**
     * As {@link #run(Object, Map, Map)} with any event: a form's values (a mapping), or a control's new value ({@code change}), read whole
     * as {@code event}.
     */
    public static List<Map<String, Object>> runEvent(Object list, Map<String, Object> state, Object event) {
        List<Map<String, Object>> effects = new ArrayList<>();
        if (!(list instanceof List<?> l)) {
            return effects;
        }
        for (Object o : l) {
            Map<String, Object> e = new LinkedHashMap<>(map(o));
            String id = String.valueOf(e.get("state"));
            if (e.get("event") instanceof String path) {
                e.remove("event");
                e.put("value", read(event, path));
            }
            if (e.get("withEvent") instanceof Map<?, ?> we) {
                Map<String, Object> w = new LinkedHashMap<>(map(e.get("with")));
                we.forEach((k, path) -> w.put(String.valueOf(k), read(event, String.valueOf(path))));
                e.remove("withEvent");
                e.put("with", w);
            }
            switch (String.valueOf(e.get("do"))) {
                case "set" -> state.put(id, e.get("value"));
                case "toggle" -> {
                    boolean v = !Boolean.TRUE.equals(state.get(id));
                    e.put("value", v);
                    state.put(id, v);
                }
                case "clear" -> {
                    e.put("value", null);
                    state.put(id, null);
                }
                default -> { }
            }
            effects.add(e);
        }
        return effects;
    }

    /** The value at {@code path} (dotted; empty: the whole event) of {@code event}, or null. */
    static Object read(Object event, String path) {
        Object at = event;
        if (path.isEmpty()) {
            return event instanceof Map<?, ?> m ? new LinkedHashMap<>(m) : event instanceof List<?> l ? new ArrayList<>(l) : event;
        }
        for (String part : path.split("\\.")) {
            at = at instanceof Map<?, ?> m ? m.get(part) : null;
        }
        return at;
    }

    /** The refusals of a submitted form ({@link #FORM_REFUSED}, one per failing check), or none. */
    private static List<Map<String, Object>> formProblems(com.ash.rachana.model.Sutra sutra, String panel, Map<String, Object> values,
            java.time.LocalDate today) {
        com.ash.rachana.model.Form f = sutra == null ? null : formOf(sutra.panels(), panel);
        List<Map<String, Object>> out = new ArrayList<>();
        if (f == null) {
            return out;
        }
        for (String k : values.keySet()) {
            if (f.field(k).isEmpty()) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("code", REFUSED);
                r.put("message", "the form of panel '" + panel + "' has no field '" + k + "'");
                out.add(r);
            }
        }
        java.util.Set<String> all = new java.util.LinkedHashSet<>();
        f.fields().forEach(x -> all.add(x.id()));
        for (com.ash.rachana.model.Form.Issue i : f.check(values, all, today == null ? java.time.LocalDate.now(java.time.ZoneOffset.UTC) : today)) {
            if (!i.error()) {
                continue;                                                  // a warning is said, never stops the form
            }
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("code", FORM_REFUSED);
            r.put("message", i.message());
            out.add(r);
        }
        return out;
    }

    /** The form of panel {@code id} among {@code panels} (children and tab parts included), or null. */
    public static com.ash.rachana.model.Form formOf(List<com.ash.rachana.model.Panel> panels, String id) {
        for (com.ash.rachana.model.Panel p : panels) {
            if (id.equals(p.id())) {
                return p.form().orElse(null);
            }
            List<com.ash.rachana.model.Panel> inner = new ArrayList<>(p.parts());
            p.wiring().children().forEach(g -> inner.addAll(g.panels()));
            com.ash.rachana.model.Form f = inner.isEmpty() ? null : formOf(inner, id);
            if (f != null) {
                return f;
            }
        }
        return null;
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
                if (data.get("rows") instanceof List<?> prow && data.containsKey("columns") && !data.containsKey("dataset")) {   // a pivot: its cells row by row
                    for (Object r : prow) {
                        if (map(r).get("cells") instanceof List<?> cs) {
                            cs.forEach(x -> out.add(map(x)));
                        }
                    }
                    return out;
                }
                if (data.get("z") instanceof List<?> z && data.get("x") instanceof List<?> xs) {         // a surface: its points row by row
                    for (int j = 0; j < z.size(); j++) {
                        for (int i = 0; i < xs.size(); i++) {
                            Map<String, Object> m = new LinkedHashMap<>();
                            Object set = cell(data.get("set"), j, i), on = cell(data.get("on"), j, i);
                            if (set != null) {
                                m.put("set", set);
                            }
                            if (on != null) {
                                m.put("on", on);
                            }
                            out.add(m);
                        }
                    }
                    return out;
                }
                for (String k : List.of("points", "steps", "events", "candles", "cells")) {
                    if (data.get(k) instanceof List<?> l) {
                        l.forEach(x -> out.add(map(x)));
                        return out;
                    }
                }
                if (data.get("series") instanceof List<?> && data.get("x") instanceof List<?> x) {      // a line or area chart: one mark per x
                    List<?> sets = data.get("set") instanceof List<?> s ? s : List.of();
                    List<?> ons = data.get("on") instanceof List<?> o ? o : List.of();
                    for (int i = 0; i < x.size(); i++) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        if (i < sets.size()) {
                            m.put("set", sets.get(i));
                        }
                        if (i < ons.size()) {
                            m.put("on", ons.get(i));
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

    /** {@code m[j][i]} of a list of lists, or null. */
    private static Object cell(Object m, int j, int i) {
        return m instanceof List<?> l && j < l.size() && l.get(j) instanceof List<?> r && i < r.size() ? r.get(i) : null;
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
