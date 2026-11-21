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
                if (x.isMap() && x.map().containsKey("when")) {
                    problem("RCH-2023", "an effect's 'when' comes with a later step of the effect list", x.map().get("when"));
                    continue;
                }
                if (!x.isMap() || x.map().size() != 1 && !(x.map().containsKey("set") && x.map().keySet().stream().allMatch(k -> Set.of("set", "field").contains(k)))) {
                    problem("RCH-2012", "an effect is a mapping with one effect word (" + String.join(", ", Effects.EFFECTS) + ")", x);
                    continue;
                }
                String word = x.map().keySet().iterator().next();
                if (!"set".equals(word)) {
                    problem("RCH-2023", Effects.EFFECTS.contains(word) || "when".equals(word)
                            ? "the effect '" + word + "' comes with a later step of the effect list; this level has set"
                            : "unknown effect '" + word + "'; the effects are " + String.join(", ", Effects.EFFECTS), x);
                    continue;
                }
                PNode set = x.map().get("set");
                if (set.isMap()) {
                    Map<String, Object> st = stateSet(set, x);
                    if (st != null) {
                        list.add(st);
                    }
                } else if (set.text() != null && !set.text().isBlank()) {
                    if (onSelect != null) {
                        problem("RCH-2038", "a panel's click sets one control; this one sets two", x);
                        continue;
                    }
                    onSelect = new LinkedHashMap<>();
                    onSelect.put("by", set.text());
                    PNode f = x.map().get("field");
                    if (f != null) {
                        if (f.text() == null) {
                            problem("RCH-2012", "'field' is the row field whose value is set", f);
                            continue;
                        }
                        onSelect.put("field", f.text());
                    }
                } else {
                    problem("RCH-2012", "set is { state: <id>, to: <expression> } or the id of a control (set: region, field: REGION)", set);
                }
            }
            if (!list.isEmpty()) {
                sets.put(trigger, list);
            }
        }
        return problems.size() > before ? null : new Read(sets, onSelect);
    }

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
            for (String trigger : Effects.BUILT.keySet()) {
                for (Effects.SetState s : Effects.sets(p, trigger)) {
                    if (!ids.contains(s.state())) {
                        problems.add(new SutraProblem("RCH-2020", "panel '" + p.id() + "' sets state '" + s.state() + "', which the Sutra does not hold"
                                + (ids.isEmpty() ? " (declare it under state:)" : " (it holds " + String.join(", ", ids) + ")"), p.location()));
                    }
                }
            }
            check(p.parts(), state);
        }
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
