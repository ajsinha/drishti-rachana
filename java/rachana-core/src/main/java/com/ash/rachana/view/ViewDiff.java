/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.view;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The patches that turn one ViewModel into the next of the same screen (SPEC.md (ViewModel contract), live frames): a {@code strip} patch per changed
 * figure, a {@code panel} patch per changed panel, a {@code rows} patch (a reset) per changed dataset. Parts are compared by their
 * canonical JSON ({@link ViewJson}), so "changed" means a reader would see a difference. A part that did not change sends nothing; a
 * change of the screen's shape (a different number of figures or panels, another title) is not a patch: {@link #frame} answers null and
 * the host sends the whole ViewModel again.
 */
public final class ViewDiff {

    private ViewDiff() {
    }

    /**
     * The patches from {@code before} to {@code after}, in the order strip, panels, datasets; empty when nothing a reader sees changed;
     * null when the shape changed.
     */
    public static List<Patch> patches(ViewModel before, ViewModel after) {
        List<Patch> out = new ArrayList<>();
        if (before.strip().size() != after.strip().size() || before.panels().size() != after.panels().size()
                || !same(before.title(), after.title()) || !same(before.controls(), after.controls())
                || !same(before.state(), after.state())) {                        // contract 1.2: the state values, as the controls
            return null;
        }
        for (int i = 0; i < after.strip().size(); i++) {
            if (!same(before.strip().get(i), after.strip().get(i))) {
                out.add(Patch.strip(i, after.strip().get(i)));
            }
        }
        for (int i = 0; i < after.panels().size(); i++) {
            ViewModel.PanelView a = after.panels().get(i);
            if (!a.id().equals(before.panels().get(i).id())) {
                return null;
            }
            if (!same(before.panels().get(i), a)) {
                out.add(Patch.panel(a));
            }
        }
        Map<String, ViewModel.Dataset> b = before.datasets() == null ? Map.of() : before.datasets();
        Map<String, ViewModel.Dataset> a = after.datasets() == null ? Map.of() : after.datasets();
        if (!b.keySet().equals(a.keySet())) {
            return null;
        }
        for (Map.Entry<String, ViewModel.Dataset> e : a.entrySet()) {
            if (!same(b.get(e.getKey()), e.getValue())) {
                out.add(Patch.rows(e.getKey(), rows(e.getValue()), null, null, null));
            }
        }
        return out;
    }

    /** One frame of the patches, or an empty list when nothing changed, or null when the shape changed (send the whole ViewModel). */
    public static List<Frame> frame(ViewModel before, ViewModel after, long seq) {
        List<Patch> p = patches(before, after);
        if (p == null) {
            return null;
        }
        return p.isEmpty() ? List.of() : List.of(new Frame(seq, 0, p, 0, 0));
    }

    private static boolean same(Object a, Object b) {
        return a == b || a != null && b != null && ViewJson.write(a).equals(ViewJson.write(b));
    }

    private static List<Map<String, Object>> rows(ViewModel.Dataset d) {
        if (d.rows() != null) {
            return d.rows();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        if (d.values() != null) {
            for (List<Object> v : d.values()) {
                Map<String, Object> r = new LinkedHashMap<>();
                for (int i = 0; i < d.fields().size() && i < v.size(); i++) {
                    r.put(d.fields().get(i).name(), v.get(i));
                }
                out.add(r);
            }
        }
        return out;
    }
}
