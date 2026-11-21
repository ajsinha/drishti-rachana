/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.view;


/**
 * One change to a rendered view.
 *
 * @param op {@code strip} (one header figure), {@code panel} (a whole panel), {@code provenance}, {@code deleted} (the
 *     source deleted the entity: the client keeps what it shows but says so) or {@code restored} (a deleted entity came
 *     back; the patches after it bring the view up to date); contract 1.1 adds {@code rows} (row changes of a dataset by key)
 * @param index strip position for {@code strip}
 * @param cell the new strip cell
 * @param panel the new panel for {@code panel}
 * @param provenance the new provenance
 * @param at when the source deleted the entity, for {@code deleted}
 * @param dataset the dataset a {@code rows} patch changes
 * @param reset every row of the dataset, replaced (applied first)
 * @param remove the keys of rows removed
 * @param update rows merged by key (the fields given)
 * @param add rows added (appended)
 */
@Include(Include.Rule.NON_NULL)
public record Patch(String op, Integer index, ViewModel.Cell cell, ViewModel.PanelView panel, ViewModel.Provenance provenance,
        java.time.Instant at, String dataset, @Include(Include.Rule.NON_EMPTY) java.util.List<java.util.Map<String, Object>> reset,
        @Include(Include.Rule.NON_EMPTY) java.util.List<String> remove, @Include(Include.Rule.NON_EMPTY) java.util.List<java.util.Map<String, Object>> update,
        @Include(Include.Rule.NON_EMPTY) java.util.List<java.util.Map<String, Object>> add) {

    public Patch(String op, Integer index, ViewModel.Cell cell, ViewModel.PanelView panel, ViewModel.Provenance provenance, java.time.Instant at) {
        this(op, index, cell, panel, provenance, at, null, null, null, null, null);
    }

    /**
     * Row changes of a dataset by key (contract 1.1, op {@code rows}): {@code reset} first (every row replaced), then {@code remove} (keys;
     * a compound key's fields joined by U+001F), {@code update} (the fields given merged into the row of that key; an unknown key is
     * added), {@code add} (appended; an existing key is updated).
     */
    public static Patch rows(String dataset, java.util.List<java.util.Map<String, Object>> reset, java.util.List<String> remove,
            java.util.List<java.util.Map<String, Object>> update, java.util.List<java.util.Map<String, Object>> add) {
        return new Patch("rows", null, null, null, null, null, dataset, reset, remove, update, add);
    }

    public Patch(String op, Integer index, ViewModel.Cell cell, ViewModel.PanelView panel, ViewModel.Provenance provenance) {
        this(op, index, cell, panel, provenance, null);
    }

    static Patch strip(int i, ViewModel.Cell c) {
        return new Patch("strip", i, c, null, null);
    }

    static Patch panel(ViewModel.PanelView p) {
        return new Patch("panel", null, null, p, null);
    }

    static Patch provenance(ViewModel.Provenance p) {
        return new Patch("provenance", null, null, null, p);
    }

    static Patch deleted(java.time.Instant at) {
        return new Patch("deleted", null, null, null, null, at);
    }

    static Patch restored() {
        return new Patch("restored", null, null, null, null);
    }
}
