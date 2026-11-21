/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.parse;

import com.ash.rachana.SutraProblem;
import com.ash.rachana.model.Effects;
import com.ash.rachana.model.Panel;
import com.ash.rachana.model.SourceLocation;
import com.ash.rachana.model.StateEntry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads a panel's {@code on:} (SUTRA_EXPANSION I1, level 1.2) and the {@code state:} of a Sutra (S1). Codes: {@code RCH-2012} a wrong
 * shape, {@code RCH-2011} an unknown key, {@code RCH-2020} a bad name or value, {@code RCH-2023} a trigger, effect or word this level
 * does not have yet (named with the step that brings it), {@code RCH-2024} a state named like a control, a let or a {@code data:}
 * entry, {@code RCH-2038} a panel that sets a control twice.
 */
public final class EffectsReader {

    /** A state's id: an EL name (letters, digits, {@code _}, starting with a letter), as a control's. */
    public static final java.util.regex.Pattern ID = java.util.regex.Pattern.compile(com.ash.rachana.model.ControlRules.ID_PATTERN);
    /** The keys of one state entry. */
    public static final Set<String> STATE_KEYS = Set.of("type", "default", "url", "remember", "doc");

    private final String file;
    private final List<SutraProblem> problems;

    public EffectsReader(String file, List<SutraProblem> problems) {
        this.file = file;
        this.problems = problems;
    }

    /** What a panel's {@code on:} says: its state effects by trigger, and the control a click sets ({@code on-select}), or null. */
    public record Read(Map<String, List<Map<String, Object>>> sets, Map<String, Object> onSelect) {}

    /**
     * A panel's {@code on:}: each trigger built at this level for the panel's kind, each effect {@code set}; null when it has a problem
     * (reported).
     */
    public Read panel(String kind, PNode on) {
        int before = problems.size();
        if (!on.isMap()) {
            problem("RCH-2012", "on: maps a trigger to a list of effects ({ row-click: [ { set: { state: order, to: \"@.id\" } } ] })", on);
            return null;
        }
        Map<String, List<Map<String, Object>>> sets = new LinkedHashMap<>();
        Map<String, Object> onSelect = null;
        for (Map.Entry<String, PNode> e : on.map().entrySet()) {
            String trigger = e.getKey();
            List<String> kinds = Effects.BUILT.get(trigger);
            if (kinds == null) {
                problem("RCH-2023", Effects.TRIGGERS.contains(trigger)
                        ? "the trigger '" + trigger + "' comes with a later step of the effect list; this level has " + String.join(", ", Effects.BUILT.keySet().stream().sorted().toList())
                        : "unknown trigger '" + trigger + "'; the triggers are " + String.join(", ", Effects.TRIGGERS), e.getValue());
                continue;
            }
            if (!kinds.contains(kind)) {
                problem("RCH-2023", "'" + kind + "' panels do not fire " + trigger + " (" + trigger + ": " + String.join(", ", kinds) + ")", e.getValue());
                continue;
            }
            if (!e.getValue().isList()) {
                problem("RCH-2012", "on." + trigger + " is a list of effects, run in order", e.getValue());
                continue;
            }
            List<Map<String, Object>> list = new ArrayList<>();
            for (PNode x : e.getValue().list()) {
                Object one = effect(x, !Effects.AT_FIRE.contains(trigger), Effects.AT_FIRE.contains(trigger));
                if (one instanceof OnSelect sel) {
                    if (onSelect != null) {
                        problem("RCH-2038", "a panel's click sets one control; this one sets two", x);
                        continue;
                    }
                    onSelect = sel.value();
                } else if (one instanceof Map<?, ?>) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = (Map<String, Object>) one;
                    list.add(m);
                }
            }
            if (!list.isEmpty()) {
                sets.put(trigger, list);
            }
        }
        return problems.size() > before ? null : new Read(sets, onSelect);
    }

    /**
     * A control's {@code on:} (level 1.2): {@code change}, its effects run with the control's new value as {@code event} (the whole value;
     * {@code event.<name>} is {@code RCH-2012}, a control's value has no fields). A control's change never sets another control
     * ({@code RCH-2012}). Null when it has a problem (reported).
     */
    public Map<String, List<Map<String, Object>>> control(String id, PNode on) {
        int before = problems.size();
        if (!on.isMap()) {
            problem("RCH-2012", "a control's on: maps change to a list of effects ({ change: [ { set: { state: picked, to: event } } ] })", on);
            return null;
        }
        Map<String, List<Map<String, Object>>> out = new LinkedHashMap<>();
        for (Map.Entry<String, PNode> e : on.map().entrySet()) {
            String trigger = e.getKey();
            if (!Effects.CONTROL_TRIGGERS.contains(trigger)) {
                problem("RCH-2023", Effects.TRIGGERS.contains(trigger)
                        ? "a control fires " + String.join(", ", Effects.CONTROL_TRIGGERS) + ", not " + trigger + " (control '" + id + "')"
                        : "unknown trigger '" + trigger + "'; a control fires " + String.join(", ", Effects.CONTROL_TRIGGERS), e.getValue());
                continue;
            }
            if (!e.getValue().isList()) {
                problem("RCH-2012", "on." + trigger + " is a list of effects, run in order", e.getValue());
                continue;
            }
            List<Map<String, Object>> list = new ArrayList<>();
            for (PNode x : e.getValue().list()) {
                if (x.isMap() && x.map().get("set") != null && !x.map().get("set").isMap()) {
                    problem("RCH-2012", "a control's change writes state, never another control: set: { state: <id>, to: event }", x.map().get("set"));
                    continue;
                }
                Object one = effect(x, false, true);
                if (one instanceof Map<?, ?> m) {
                    boolean field = Effects.eventPath(m.get("to")) instanceof String path && !path.isEmpty()
                            || m.get("with") instanceof Map<?, ?> w && w.values().stream().anyMatch(v -> Effects.eventPath(v) instanceof String path && !path.isEmpty());
                    if (field) {
                        problem("RCH-2012", "a control's change event is its new value, read whole as event (it has no fields)", x);
                        continue;
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> ok = (Map<String, Object>) m;
                    list.add(ok);
                }
            }
            if (!list.isEmpty()) {
                out.put(trigger, list);
            }
        }
        return problems.size() > before ? null : out;
    }

    /** {@code set: <control>} read: the panel's on-select ({@code {by, field?}}). */
    private record OnSelect(Map<String, Object> value) {}

    /**
     * One effect of a list: a plain state write {@code {state, to}}, {@code {do: <word>, ..., when?}}, or ({@code control} true, a click's
     * list) {@code set: <control>} as an {@link OnSelect}; null when it has a problem (reported).
     */
    private Object effect(PNode x, boolean control, boolean atFire) {
        Object one = effect(x, control);
        if (!atFire && one instanceof Map<?, ?> m && (Effects.eventPath(m.get("to")) != null
                || m.get("with") instanceof Map<?, ?> w && w.values().stream().anyMatch(v -> Effects.eventPath(v) != null))) {
            problem("RCH-2012", "event" + " values are read by the effects of submit and cancel, filled in when the form is sent; here write an "
                    + "expression", x);
            return null;
        }
        return one;
    }

    private Object effect(PNode x, boolean control) {
        String word = x.isMap() ? x.map().keySet().stream().filter(k -> !"when".equals(k) && !Effects.EXTRAS.contains(k)).findFirst().orElse(null) : null;
        if (word == null || x.map().keySet().stream().filter(k -> !"when".equals(k) && !Effects.EXTRAS.contains(k)).count() != 1) {
            problem("RCH-2012", "an effect is a mapping with one effect word (" + String.join(", ", Effects.EFFECTS) + "), and an optional when", x);
            return null;
        }
        if (!Effects.BUILT_EFFECTS.contains(word)) {
            problem("RCH-2023", Effects.EFFECTS.contains(word)
                    ? "the effect '" + word + "' comes with a later step of the effect list; this level has " + String.join(", ", Effects.BUILT_EFFECTS)
                    : "unknown effect '" + word + "'; the effects are " + String.join(", ", Effects.EFFECTS), x);
            return null;
        }
        for (String k : x.map().keySet()) {
            if (!k.equals(word) && !"when".equals(k) && !Effects.EXTRAS_OF.getOrDefault(word, Set.of()).contains(k)) {
                problem("RCH-2011", "unknown key '" + k + "' in the effect " + word, x.map().get(k));
            }
        }
        String when = null;
        if (x.map().containsKey("when")) {
            when = expression(x.map().get("when"));
            if (when == null) {
                problem("RCH-2012", "an effect's when is an expression (the effect runs only where it is true)", x.map().get("when"));
                return null;
            }
        }
        PNode set = x.map().get(word);
        if (!"set".equals(word)) {
            Map<String, Object> g = general(word, set, x);
            if (g != null && when != null) {
                g.put("when", when);
            }
            return g;
        }
        if (set.isMap()) {
            Map<String, Object> st = stateSet(set, x);
            if (st != null && when != null) {
                Map<String, Object> g = new LinkedHashMap<>();
                g.put("do", "set");
                g.putAll(st);
                g.put("when", when);
                st = g;
            }
            return st;
        }
        if (!control) {
            problem("RCH-2012", "an action's set writes a state: set: { state: <id>, to: <expression> }", set);
            return null;
        }
        if (when != null) {
            problem("RCH-2012", "set: <control> takes no when (it is the panel's on-select)", x.map().get("when"));
            return null;
        }
        if (set.text() == null || set.text().isBlank()) {
            problem("RCH-2012", "set is { state: <id>, to: <expression> } or the id of a control (set: region, field: REGION)", set);
            return null;
        }
        Map<String, Object> onSelect = new LinkedHashMap<>();
        onSelect.put("by", set.text());
        PNode f = x.map().get("field");
        if (f != null) {
            if (f.text() == null) {
                problem("RCH-2012", "'field' is the row field whose value is set", f);
                return null;
            }
            onSelect.put("field", f.text());
        }
        return new OnSelect(onSelect);
    }

    /**
     * A panel's {@code actions:} (I1, level 1.2): buttons in its header, each {@code {id?, label, tone?, confirm?, requires?, do: [effects]}};
     * {@code label} and {@code confirm} are templates ({@code confirm: true} asks without words), {@code requires} a permission name the
     * host answers, {@code id} (default: the action's place) unique in the panel. Kept as {@code {id, label, tone?, confirm?, requires?, do}};
     * null when it has a problem (reported).
     */
    public List<Map<String, Object>> actions(PNode n) {
        int before = problems.size();
        if (!n.isList() || n.list().isEmpty()) {
            problem("RCH-2012", "actions: is a list of buttons ({ label: Cancel, do: [ { host: order.cancel } ] })", n);
            return null;
        }
        if (n.list().size() > Effects.MAX_ACTIONS) {
            problem("RCH-2029", "a panel has at most " + Effects.MAX_ACTIONS + " actions; this one has " + n.list().size(), n);
            return null;
        }
        List<Map<String, Object>> out = new ArrayList<>();
        Set<String> ids = new java.util.HashSet<>();
        for (PNode a : n.list()) {
            if (!a.isMap()) {
                problem("RCH-2012", "an action is { label, do: [ effects ] }", a);
                continue;
            }
            for (String k : a.map().keySet()) {
                if (!Effects.ACTION_KEYS.contains(k)) {
                    problem("RCH-2011", "unknown key '" + k + "' in an action; it takes " + String.join(", ", new java.util.TreeSet<>(Effects.ACTION_KEYS)), a.map().get(k));
                }
            }
            Map<String, Object> m = new LinkedHashMap<>();
            PNode label = a.map().get("label");
            if (label == null || label.text() == null || label.text().isBlank()) {
                problem("RCH-2012", "an action needs a label (a template: \"Cancel ${state.order}\")", label == null ? a : label);
                continue;
            }
            PNode id = a.map().get("id");
            String idText = id == null ? String.valueOf(n.list().indexOf(a)) : id.text();
            if (id != null && (idText == null || !ACTION.matcher(idText).matches())) {
                problem("RCH-2020", "an action's id is a name of letters, digits, _ and -", id);
                continue;
            }
            if (!ids.add(idText)) {
                problem("RCH-2024", "two actions of this panel are called '" + idText + "'", a);
                continue;
            }
            m.put("id", idText);
            m.put("label", label.text());
            PNode tone = a.map().get("tone");
            if (tone != null) {
                if (tone.text() == null || !Effects.TONES.contains(tone.text())) {
                    problem("RCH-2020", "an action's tone is one of " + String.join(", ", Effects.TONES), tone);
                    continue;
                }
                m.put("tone", tone.text());
            }
            PNode confirm = a.map().get("confirm");
            if (confirm != null) {
                if (confirm.isMap() || confirm.isList() || confirm.value() == null || Boolean.FALSE.equals(confirm.value())) {
                    problem("RCH-2012", "confirm is true (ask before running) or the question, a template", confirm);
                    continue;
                }
                m.put("confirm", confirm.value() instanceof Boolean ? Boolean.TRUE : confirm.text());
            }
            PNode requires = a.map().get("requires");
            if (requires != null) {
                if (requires.text() == null || !ACTION.matcher(requires.text()).matches()) {
                    problem("RCH-2020", "requires names a permission the host answers (a dotted name: order.cancel)", requires);
                    continue;
                }
                m.put("requires", requires.text());
            }
            PNode does = a.map().get("do");
            if (does == null || !does.isList() || does.list().isEmpty()) {
                problem("RCH-2012", "an action's do is a list of effects, run in order", does == null ? a : does);
                continue;
            }
            List<Map<String, Object>> effects = new ArrayList<>();
            for (PNode x : does.list()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> one = (Map<String, Object>) effect(x, false, false);
                if (one != null) {
                    effects.add(one);
                }
            }
            m.put("do", effects);
            out.add(m);
        }
        return problems.size() > before ? null : out;
    }

    /**
     * One effect other than {@code set} ({@code toggle: <state>}, {@code clear: <state>}, {@code host: <action>} with {@code with: { name:
     * <expression> }}, {@code notify: <template>} with {@code tone:}), as the panel keeps it: {@code {do: <word>, ...}}; null when it has a
     * problem (reported).
     */
    private Map<String, Object> general(String word, PNode v, PNode at) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("do", word);
        String text = v == null || v.isMap() || v.isList() ? null : v.text();
        if (text == null || text.isBlank()) {
            problem("RCH-2012", switch (word) {
                case "toggle", "clear" -> word + " names a state (" + word + ": <id>)";
                case "host" -> "host names the host's action (host: order.cancel, with: { id: \"@.id\" })";
                default -> "notify is a message template (notify: \"Saved ${@.id}\", tone: ok)";
            }, v == null ? at : v);
            return null;
        }
        switch (word) {
            case "toggle", "clear" -> out.put("state", text);
            case "host" -> {
                if (!ACTION.matcher(text).matches()) {
                    problem("RCH-2020", "host action '" + text + "' is a dotted name of letters, digits, _ and -", v);
                    return null;
                }
                out.put("action", text);
                PNode with = at.map().get("with");
                if (with != null) {
                    if (!with.isMap()) {
                        problem("RCH-2012", "with is a mapping of name to expression, evaluated when the panel binds", with);
                        return null;
                    }
                    Map<String, Object> w = new LinkedHashMap<>();
                    for (Map.Entry<String, PNode> f : with.map().entrySet()) {
                        String x = expression(f.getValue());
                        if (x == null) {
                            problem("RCH-2012", "with." + f.getKey() + " is an expression (or a literal)", f.getValue());
                            return null;
                        }
                        w.put(f.getKey(), x);
                    }
                    out.put("with", w);
                }
            }
            default -> {
                out.put("text", text);
                PNode tone = at.map().get("tone");
                if (tone != null) {
                    if (tone.text() == null || !Effects.TONES.contains(tone.text())) {
                        problem("RCH-2020", "notify tone is one of " + String.join(", ", Effects.TONES), tone);
                        return null;
                    }
                    out.put("tone", tone.text());
                }
            }
        }
        return out;
    }

    /** A host action's name: dotted words ({@code order.cancel}). */
    static final java.util.regex.Pattern ACTION = java.util.regex.Pattern.compile("[A-Za-z][A-Za-z0-9_-]*(\\.[A-Za-z][A-Za-z0-9_-]*)*");

    private Map<String, Object> stateSet(PNode set, PNode at) {
        for (String k : set.map().keySet()) {
            if (!"state".equals(k) && !"to".equals(k)) {
                problem("RCH-2011", "unknown key '" + k + "' in set; it is { state: <id>, to: <expression> }", set.map().get(k));
                return null;
            }
        }
        PNode state = set.map().get("state");
        PNode to = set.map().get("to");
        if (state == null || state.text() == null || to == null) {
            problem("RCH-2012", "set is { state: <id>, to: <expression> }", set);
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("state", state.text());
        out.put("to", expression(to));
        if (out.get("to") == null) {
            problem("RCH-2012", "set.to is an expression (or a literal: text, a number, true, false, null)", to);
            return null;
        }
        return out;
    }

    /** A literal or an expression as expression text ({@code 5}, {@code true}, {@code null}, {@code "@.id"}); null for a list or a map. */
    static String expression(PNode n) {
        if (n.isMap() || n.isList()) {
            return null;
        }
        Object v = n.value();
        return v == null ? "null" : v instanceof String s ? s : String.valueOf(v);
    }

    /**
     * Each state effect of {@code panels} names a state the Sutra holds ({@code RCH-2020} otherwise, with the ids it holds). Run once the
     * state is read.
     */
    public void check(List<Panel> panels, List<StateEntry> state) {
        Set<String> ids = new java.util.LinkedHashSet<>();
        state.forEach(e -> ids.add(e.id()));
        for (Panel p : panels) {
            for (Map<String, Object> e : Effects.every(p)) {
                {
                    if (!(e.get("state") instanceof String s)) {
                        continue;
                    }
                    if (!ids.contains(s)) {
                        problems.add(new SutraProblem("RCH-2020", "panel '" + p.id() + "' sets state '" + s + "', which the Sutra does not hold"
                                + (ids.isEmpty() ? " (declare it under state:)" : " (it holds " + String.join(", ", ids) + ")"), p.location()));
                    } else if ("toggle".equals(e.get("do")) && state.stream().anyMatch(x -> x.id().equals(s) && !"boolean".equals(x.type()))) {
                        problems.add(new SutraProblem("RCH-2020", "panel '" + p.id() + "' toggles state '" + s + "', which is not a boolean", p.location()));
                    }
                }
            }
            check(p.parts(), state);
        }
    }

    /**
     * Each state effect of the controls' lists ({@code controls}: control id to trigger to effects) names a state the Sutra holds
     * ({@code RCH-2020} otherwise), and a {@code toggle} a boolean one.
     */
    public void checkControls(Map<String, Map<String, List<Map<String, Object>>>> controls, List<StateEntry> state, Map<String, com.ash.rachana.model.SourceLocation> at) {
        Set<String> ids = new java.util.LinkedHashSet<>();
        state.forEach(e -> ids.add(e.id()));
        controls.forEach((id, on) -> on.values().forEach(list -> list.forEach(e -> {
            if (!(e.get("state") instanceof String s)) {
                return;
            }
            if (!ids.contains(s)) {
                problems.add(new SutraProblem("RCH-2020", "control '" + id + "' sets state '" + s + "', which the Sutra does not hold"
                        + (ids.isEmpty() ? " (declare it under state:)" : " (it holds " + String.join(", ", ids) + ")"), at.get(id)));
            } else if ("toggle".equals(e.get("do")) && state.stream().anyMatch(x -> x.id().equals(s) && !"boolean".equals(x.type()))) {
                problems.add(new SutraProblem("RCH-2020", "control '" + id + "' toggles state '" + s + "', which is not a boolean", at.get(id)));
            }
        })));
    }

    /**
     * A Sutra's {@code state:}: each id an EL name, not taken by a control, a let or a {@code data:} entry; a type of {@link StateEntry#TYPES};
     * a default of that type; {@code url} and {@code remember} wait for the view-state document (S4). At most {@link StateEntry#MAX}.
     */
    public List<StateEntry> state(PNode n, Set<String> taken) {
        List<StateEntry> out = new ArrayList<>();
        if (n == null) {
            return out;
        }
        if (!n.isMap()) {
            problem("RCH-2012", "state is a mapping of id to { type, default }", n);
            return out;
        }
        for (Map.Entry<String, PNode> e : n.map().entrySet()) {
            StateEntry s = entry(e.getKey(), e.getValue(), null, false, "state");
            if (s == null) {
                continue;
            }
            if (taken.contains(s.id())) {
                problem("RCH-2024", "state '" + s.id() + "' has the name of a control, a let or a data: entry of this Sutra", e.getValue());
                continue;
            }
            out.add(s);
        }
        return out;
    }

    /**
     * One state entry ({@code { type, default, url, remember, doc }}) named {@code name}, held as {@code id} (a fragment's private state:
     * {@code <as>.<name>}); null when it has a problem (reported).
     */
    StateEntry entry(String name, PNode d, String owner, boolean output, String where) {
        if (!ID.matcher(name).matches()) {
            problem("RCH-2020", where + " '" + name + "' is a name of letters, digits and _, starting with a letter, at most 32 characters", d);
            return null;
        }
        if (!d.isMap()) {
            problem("RCH-2012", where + " " + name + " is { type, default }", d);
            return null;
        }
        for (String k : d.map().keySet()) {
            if (!STATE_KEYS.contains(k)) {
                problem("RCH-2011", "unknown key '" + k + "' in " + where + " " + name + "; it takes " + String.join(", ", new java.util.TreeSet<>(STATE_KEYS)), d.map().get(k));
                return null;
            }
        }
        String type = d.map().get("type") == null ? null : d.map().get("type").text();
        if (type == null || !StateEntry.TYPES.contains(type)) {
            problem("RCH-2023", where + " " + name + ": type is one of " + String.join(", ", StateEntry.TYPES), d.map().getOrDefault("type", d));
            return null;
        }
        PNode url = d.map().get("url");
        if (url != null && !Boolean.FALSE.equals(url.value())) {
            problem("RCH-2023", where + " " + name + ": url: true keeps a state in the view-state document the host writes into the address, which "
                    + "comes with S4; not at this level", url);
            return null;
        }
        PNode remember = d.map().get("remember");
        if (remember != null) {
            problem("RCH-2023", where + " " + name + ": remember: user (kept by the host per reader) comes with the view-state document (S4); not at "
                    + "this level", remember);
            return null;
        }
        Object dflt = null;
        PNode dn = d.map().get("default");
        if (dn != null) {
            dflt = plain(dn);
            try {
                com.ash.rachana.el.StateTypes.of(type, dflt);
            } catch (IllegalArgumentException ex) {
                problem("RCH-2020", where + " " + name + ": the default does not fit the type " + type + ": " + ex.getMessage(), dn);
                return null;
            }
        }
        return new StateEntry(owner == null ? name : owner + "." + name, type, dflt, false, owner, output);
    }

    private static Object plain(PNode n) {
        if (n.isList()) {
            List<Object> out = new ArrayList<>();
            n.list().forEach(x -> out.add(plain(x)));
            return out;
        }
        if (n.isMap()) {
            Map<String, Object> out = new LinkedHashMap<>();
            n.map().forEach((k, v) -> out.put(k, plain(v)));
            return out;
        }
        return n.value();
    }

    private void problem(String code, String message, PNode at) {
        problems.add(new SutraProblem(code, message, new SourceLocation(file, at.line(), at.column())));
    }
}
