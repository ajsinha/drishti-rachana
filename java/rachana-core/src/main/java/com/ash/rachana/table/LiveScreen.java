/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.table;

import com.ash.rachana.Rachana;
import com.ash.rachana.api.Node;
import com.ash.rachana.bind.AsOf;
import com.ash.rachana.bind.BindHooks;
import com.ash.rachana.bind.Entity;
import com.ash.rachana.bind.EntityRef;
import com.ash.rachana.bind.PivotEngine;
import com.ash.rachana.bind.TableProvider;
import com.ash.rachana.bind.ViewBuilder;
import com.ash.rachana.control.BusinessDays;
import com.ash.rachana.control.ControlValues;
import com.ash.rachana.el.BusinessCalendar;
import com.ash.rachana.el.EvalContext;
import com.ash.rachana.model.DataEntry;
import com.ash.rachana.model.Sutra;
import com.ash.rachana.view.Frame;
import com.ash.rachana.view.ViewDiff;
import com.ash.rachana.view.ViewModel;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One screen kept current from its tables (SPEC 2.4; Data Protocol 8): the Sutra, the host's record, the control values and the rows of
 * each host table it reads, held as {@link KeyedRows}. {@link #view} builds the ViewModel with Core's hooks alone; {@link #patch}
 * applies a keyed patch to a table and answers the ViewModel frames that bring the last view up to date. It is what a runtime that
 * embeds a view runs (the JavaScript bundle's {@code screen*} functions, the conformance {@code patch} kind): one implementation.
 * Not thread-safe; one per open view.
 */
public final class LiveScreen {

    private final Rachana rachana;
    private final Sutra sutra;
    private final LocalDate today;
    private final Object host;
    private final Map<String, Object> ctl = new LinkedHashMap<>();
    private final Map<String, TableSchema> schemas = new LinkedHashMap<>();
    private final Map<String, KeyedRows> tables = new LinkedHashMap<>();
    private ViewModel last;
    private long seq;

    /**
     * @param host the host's own record (plain values or a node; null: none)
     * @param ctl control values (text; a list for a multi-valued control)
     */
    public LiveScreen(Rachana rachana, Sutra sutra, LocalDate today, Object host, Map<String, ?> ctl) {
        this.rachana = rachana;
        this.sutra = sutra;
        this.today = today == null ? LocalDate.of(2026, 1, 15) : today;
        this.host = host;
        if (ctl != null) {
            this.ctl.putAll(ctl);
        }
    }

    public Sutra sutra() {
        return sutra;
    }

    private PivotEngine pivots = PivotEngine.NONE;

    /** The host's pivot engine for the screen's pivot panels (default {@link PivotEngine#NONE}: a pivot panel says it has none). */
    public LiveScreen pivots(PivotEngine engine) {
        this.pivots = engine == null ? PivotEngine.NONE : engine;
        return this;
    }

    /** Sets (or replaces) a host table's rows, in their JSON form; the key is the schema's, else the first entry's that reads it. */
    public void table(String name, TableSchema schema, List<Map<String, Object>> rows) {
        List<String> entryKey = sutra.data().stream().filter(d -> name.equals(d.table())).findFirst().map(DataEntry::key).orElse(List.of());
        TableSchema s = schema == null ? TableSchema.untyped(name, entryKey) : schema;
        int window = s.doc().get("append") instanceof Map<?, ?> a && a.get("window") instanceof Number w ? w.intValue() : 0;
        schemas.put(name, s);
        tables.put(name, new KeyedRows(s.key().isEmpty() ? entryKey : s.key(), rows, window));
    }

    /** The table's rows as held (null when the host has not handed it). */
    public KeyedRows rows(String name) {
        return tables.get(name);
    }

    /** The host tables the screen reads ({@code table} entries), by table name, in the order written. */
    public List<String> tableNames() {
        List<String> out = new ArrayList<>();
        sutra.data().stream().filter(d -> d.form() == DataEntry.Form.TABLE && !out.contains(d.table())).forEach(d -> out.add(d.table()));
        return out;
    }

    private final Map<String, String> state = new LinkedHashMap<>();

    /**
     * Changes state values (level 1.2, S1), as a bind request carries them: text per state id (a list's items joined by commas; null: the value is no longer carried and its default stands); the next
     * {@link #view} or {@link #patch} reads them.
     */
    public void state(Map<String, ?> next) {
        next.forEach((k, v) -> {
            if (v == null) {
                state.remove(k);                                           // not carried: its default stands (a clear effect)
            } else {
                state.put(k, v instanceof List<?> l ? String.join(",", l.stream().map(String::valueOf).toList()) : String.valueOf(v));
            }
        });
    }

    /** Changes control values (the next {@link #view} or {@link #patch} reads them). */
    public void controls(Map<String, ?> next) {
        ctl.putAll(next);
    }

    /** What to ask of each table (the plan document of {@link DataPlan}). */
    public Map<String, Object> plan(Map<String, TableSchema> known) {
        return DataPlan.plan(sutra, known, rachana.compiler(), context(record(false)), rachana.language());
    }

    /** Builds the ViewModel now (and remembers it as the base of the next frames). */
    public ViewModel view() {
        var views = rachana.views(BindHooks.core(), pivots, TableProvider.NONE,
                BusinessDays.of(today, BusinessCalendar.of("WEEKENDS")), Duration.ofSeconds(2));
        Map<String, String> texts = new LinkedHashMap<>();
        ctl.forEach((k, v) -> texts.put(k, v instanceof List<?> l ? String.join(",", l.stream().map(String::valueOf).toList()) : v == null ? "" : String.valueOf(v)));
        var doc = new Entity(EntityRef.of("record", "case"), record(true));
        last = views.build(new ViewBuilder.Request(doc, sutra, null, AsOf.of(today), null, new ControlValues(texts, java.util.Set.of()), null,
                state.isEmpty() ? null : new com.ash.rachana.control.StateValues(state)));
        return last;
    }

    /**
     * Applies a keyed patch ({@code {reset?, remove?, update?, add?}}) to a table and builds the view again.
     *
     * @return the frames from the last view (empty when no reader sees a change), or null when the screen's shape changed and the
     *     whole ViewModel ({@link #last}) must be sent again
     * @throws IllegalArgumentException for a table the host has not handed
     */
    public List<Frame> patch(String table, Map<String, Object> patch) {
        KeyedRows t = tables.get(table);
        if (t == null) {
            throw new IllegalArgumentException("no table '" + table + "' on this screen");
        }
        ViewModel before = last == null ? view() : last;
        if (!t.apply(patch)) {
            return List.of();
        }
        ViewModel after = view();
        List<Frame> frames = ViewDiff.frame(before, after, seq + 1);
        if (frames != null && !frames.isEmpty()) {
            seq++;
        }
        return frames;
    }

    /** The last ViewModel built (null before the first). */
    public ViewModel last() {
        return last;
    }

    private Node record(boolean withData) {
        if (!withData || sutra.data().isEmpty()) {
            return host instanceof Node n ? n : Node.of(host == null ? new LinkedHashMap<String, Object>() : host);
        }
        Map<String, DataRecord.Rows> read = new LinkedHashMap<>();
        for (DataEntry e : sutra.data()) {
            String name = e.table() != null ? e.table() : e.name();
            if (tables.containsKey(name)) {
                read.put(e.name(), new DataRecord.Rows(schemas.get(name), tables.get(name).rows()));
            }
        }
        return DataRecord.record(sutra, host, read, rachana.compiler(), context(record(false)));
    }

    private EvalContext context(Node root) {
        Map<String, Object> values = new LinkedHashMap<>();
        ctl.forEach((k, v) -> values.put(k, v instanceof List<?> ? Node.of(v) : v));
        return EvalContext.of(root, rachana.formats()).withToday(today).withControls(values)
                .withState(com.ash.rachana.control.ScreenState.resolve(sutra, new com.ash.rachana.control.StateValues(state)));
    }
}
