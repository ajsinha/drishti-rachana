/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.wire;

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
import com.ash.rachana.control.StateValues;
import com.ash.rachana.el.BusinessCalendar;
import com.ash.rachana.model.Sutra;
import com.ash.rachana.view.ViewModel;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A small screen for {@link ScreenWire}'s JUnit tests: a Sutra (Core's grammar at the newest level) over a plain record, bound as a host
 * binds it ({@link ViewBuilder#screen}, {@link ViewBuilder#build}) with no table provider, the business date fixed, control and state
 * values as a bind request carries them.
 */
final class ScreenHarness {

    static final LocalDate TODAY = LocalDate.of(2026, 1, 15);

    private final Sutra sutra;
    private final ViewBuilder views;
    private final Node record;
    private final Map<String, String> ctl = new LinkedHashMap<>();
    private final Map<String, String> state = new LinkedHashMap<>();

    private ScreenHarness(Sutra sutra, ViewBuilder views, Node record) {
        this.sutra = sutra;
        this.views = views;
        this.record = record;
    }

    /** A screen of {@code yaml} (a Core Sutra, level 1.2) over {@code record} (plain values: maps, lists, text, numbers). */
    static ScreenHarness of(String yaml, Map<String, Object> record) {
        Rachana r = Rachana.core().language("1.2");
        Sutra s = r.parser().parse(yaml, "harness.sutra.yaml", "harness");
        ViewBuilder v = r.views(BindHooks.core(), PivotEngine.NONE, TableProvider.NONE, BusinessDays.of(TODAY, BusinessCalendar.of("WEEKENDS")),
                Duration.ofSeconds(2));
        return new ScreenHarness(s, v, Node.of(record == null ? Map.of() : record));
    }

    /** Sets a control value, as the next bind request carries it. */
    ScreenHarness ctl(String id, String value) {
        ctl.put(id, value);
        return this;
    }

    /** Sets a state value, as the next bind request carries it ({@code state.<id>=<text>}). */
    ScreenHarness state(String id, String value) {
        state.put(id, value);
        return this;
    }

    Sutra sutra() {
        return sutra;
    }

    ScreenWire wire() {
        return views.wire();
    }

    /** The screen of the current request (no event fired yet). */
    ScreenWire.Screen screen() {
        return views.screen(request());
    }

    /** The ViewModel of the current request. */
    ViewModel view() {
        return views.build(request());
    }

    private ViewBuilder.Request request() {
        return new ViewBuilder.Request(new Entity(EntityRef.of("record", "case"), record), sutra, null, AsOf.of(TODAY), null,
                new ControlValues(Map.copyOf(ctl), Set.of()), null, state.isEmpty() ? null : new StateValues(Map.copyOf(state)));
    }
}
