/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.control;

import com.ash.rachana.el.StateTypes;
import com.ash.rachana.model.Column;
import com.ash.rachana.model.Panel;
import com.ash.rachana.model.StateEntry;
import com.ash.rachana.model.StripItem;
import com.ash.rachana.model.Sutra;
import com.ash.rachana.view.ViewModel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A screen's state values for one bind (SUTRA_EXPANSION S1, level 1.2): each declared state at the value the request carried
 * ({@link StateValues}) when it fits the state's type, else at its default; and the ViewModel's {@code state[]} ({@code id}, the value,
 * {@code url}, and {@code reads}: the parts to rebuild when it changes, as for a control, from the {@code state.<id>} its texts name,
 * directly or through a {@code let}). Stateless.
 */
public final class ScreenState {

    private ScreenState() {}

    /** The value of every state of {@code s}, id to value (null for empty), in declared order. */
    public static Map<String, Object> resolve(Sutra s, StateValues values) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (StateEntry e : s.state()) {
            Object v = dflt(e);
            String text = values == null ? null : values.get(e.id());
            if (text != null) {
                try {
                    v = StateTypes.of(e.type(), text);
                } catch (IllegalArgumentException ignored) {
                    // a value that does not fit the type: the default stands
                }
            }
            out.put(e.id(), v);
        }
        return out;
    }

    /** The ViewModel's {@code state[]} for the values {@link #resolve} gave; empty when the Sutra holds no state. */
    public static List<ViewModel.StateView> views(Sutra s, Map<String, Object> values) {
        if (s.state().isEmpty()) {
            return List.of();
        }
        Map<String, Set<String>> reads = reads(s);
        List<ViewModel.StateView> out = new ArrayList<>();
        for (StateEntry e : s.state()) {
            out.add(new ViewModel.StateView(e.id(), StateTypes.json(values.get(e.id())), e.url(), List.copyOf(reads.getOrDefault(e.id(), Set.of()))));
        }
        return out;
    }

    private static Object dflt(StateEntry e) {
        try {
            return StateTypes.of(e.type(), e.dflt());
        } catch (IllegalArgumentException ignored) {
            return null;                                               // checked when the Sutra loads; never here
        }
    }

    /** State id to the parts (panel ids, {@code strip}, {@code title}) whose texts read it, directly or through a let. */
    public static Map<String, Set<String>> reads(Sutra s) {
        Map<String, Pattern> ids = new LinkedHashMap<>();
        for (StateEntry e : s.state()) {
            ids.put(e.id(), Pattern.compile("(?<![A-Za-z0-9_.$@])state\\." + Pattern.quote(e.id()) + "(?![A-Za-z0-9_-])"));
        }
        Map<String, Set<String>> viaLet = new LinkedHashMap<>();       // a let to the state ids its value reads (through earlier lets too)
        s.declarations().lets().forEach((name, value) -> {
            Set<String> got = new LinkedHashSet<>(mentioned(List.of(value), ids));
            viaLet.forEach((earlier, its) -> {
                if (word(earlier).matcher(value).find()) {
                    got.addAll(its);
                }
            });
            viaLet.put(name, got);
        });
        Map<String, List<String>> parts = new LinkedHashMap<>();
        List<String> strip = new ArrayList<>();
        for (StripItem i : s.strip()) {
            add(strip, i.bind());
            add(strip, i.label());
        }
        parts.put(ControlReads.STRIP, strip);
        List<String> title = new ArrayList<>();
        if (s.title() != null) {
            add(title, s.title().id());
            add(title, s.title().with());
            add(title, s.title().pill());
        }
        parts.put(ControlReads.TITLE, title);
        for (Panel p : s.panels()) {
            List<String> texts = new ArrayList<>();
            panel(p, texts);
            parts.put(p.id(), texts);
        }
        Map<String, Set<String>> out = new LinkedHashMap<>();
        parts.forEach((part, texts) -> {
            Set<String> read = new LinkedHashSet<>(mentioned(texts, ids));
            viaLet.forEach((name, its) -> {
                Pattern w = word(name);
                if (!its.isEmpty() && texts.stream().anyMatch(t -> w.matcher(t).find())) {
                    read.addAll(its);
                }
            });
            read.forEach(id -> out.computeIfAbsent(id, k -> new LinkedHashSet<>()).add(part));
        });
        return out;
    }

    private static Set<String> mentioned(List<String> texts, Map<String, Pattern> ids) {
        Set<String> out = new LinkedHashSet<>();
        ids.forEach((id, p) -> {
            if (texts.stream().anyMatch(t -> p.matcher(t).find())) {
                out.add(id);
            }
        });
        return out;
    }

    private static Pattern word(String name) {
        return Pattern.compile("(?<![A-Za-z0-9_.$@])" + Pattern.quote(name) + "(?![A-Za-z0-9_])");
    }

    private static void panel(Panel p, List<String> texts) {
        add(texts, p.title());
        for (Column c : p.columns()) {
            add(texts, c.bind());
            add(texts, c.label());
        }
        p.options().values().forEach(v -> walk(v, texts));
        if (p.wiring().source() != null) {
            p.wiring().source().expressions().forEach(e -> add(texts, e));
        }
        if (p.body() != null) {
            panel(p.body(), texts);
        }
        p.parts().forEach(part -> panel(part, texts));
    }

    private static void walk(Object v, List<String> texts) {
        if (v instanceof String s) {
            texts.add(s);
        } else if (v instanceof Map<?, ?> m) {
            m.values().forEach(x -> walk(x, texts));
        } else if (v instanceof List<?> l) {
            l.forEach(x -> walk(x, texts));
        }
    }

    private static void add(List<String> texts, String s) {
        if (s != null) {
            texts.add(s);
        }
    }
}
