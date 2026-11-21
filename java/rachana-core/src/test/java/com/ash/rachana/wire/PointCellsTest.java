/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.wire;

import static org.assertj.core.api.Assertions.assertThat;

import com.ash.rachana.bind.PivotEngine;
import com.ash.rachana.control.Fire;
import com.ash.rachana.view.PanelData;
import com.ash.rachana.view.ViewModel;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Point-click on a pivot's cells and a surface's points (level 1.2, contract 1.2): each cell or point with a figure is a mark ({@code ev}
 * its place row by row), its {@code set} evaluated with {@code @} the mark ({@code {row, column, value}}, {@code {x, y, z}}), a click
 * fires the panel's event with the payload read over the mark, and {@link Fire} finds the mark by the same place.
 */
class PointCellsTest {

    /** A host's pivot engine for the tests: sums one level of rows by one column (keys {@code b0=…}, {@code x=…}, totals {@code *}). */
    static final PivotEngine SUMS = new PivotEngine() {
        @Override
        public Map<String, Map<String, Object>> cells(Map<String, Object> view, List<Field> fields, List<Object[]> rows) {
            Map<String, Double> sums = new LinkedHashMap<>();
            for (Object[] r : rows) {
                String rk = "b0=" + r[0], ck = "x=" + r[1];
                double v = ((Number) r[2]).doubleValue();
                for (String k : List.of(rk + "|" + ck, rk + "|*", "*|" + ck, "*|*")) {
                    sums.merge(k, v, Double::sum);
                }
            }
            Map<String, Map<String, Object>> out = new LinkedHashMap<>();
            sums.forEach((k, v) -> out.put(k, Map.of("values", Map.of("v", v))));
            return out;
        }

        @Override
        public String key(Map<String, String> coordinates) {
            StringBuilder b = new StringBuilder();
            new TreeMap<>(coordinates).forEach((k, v) -> b.append(b.length() > 0 ? "|" : "").append(k).append('=').append(v));
            return b.toString();
        }
    };

    static final String PIVOT = """
            rachana: 1.2
            sutra: matrix
            version: 1
            title: { id: "'Scenarios'" }
            state:
              picked: { type: text }
            panels:
              - id: m
                kind: pivot
                rows: $.pnl
                by: scenario
                across: desk
                value: pnl
                emits: [ { name: cell, on: point-click, payload: { scenario: "@.row", desk: "@.column", pnl: "@.value" } } ]
                on:
                  point-click: [ { set: { state: picked, to: "@.row + '/' + @.column + '@' + #index" } } ]
            """;

    static final Map<String, Object> PNL = Map.of("pnl", List.of(
            Map.of("scenario", "UP", "desk", "FX", "pnl", 10), Map.of("scenario", "UP", "desk", "RATES", "pnl", -4),
            Map.of("scenario", "DOWN", "desk", "FX", "pnl", -7)));

    @Test
    void aPivotsCellsAreMarksWithTheirPlaceAndTheirSet() {
        ViewModel vm = ScreenHarness.of(PIVOT, PNL, SUMS).view();
        assertThat(vm.contract()).isEqualTo("1.2");
        PanelData.Pivot p = (PanelData.Pivot) vm.panels().get(0).data();
        assertThat(p.columns()).containsExactly("FX", "RATES");
        ViewModel.Cell upRates = p.rows().get(0).cells().get(1), downRates = p.rows().get(1).cells().get(1);
        assertThat(upRates.ev()).isEqualTo("1");
        assertThat(upRates.set()).isEqualTo(Map.of("picked", "UP/RATES@1"));
        assertThat(p.rows().get(1).cells().get(0).ev()).isEqualTo("2");
        assertThat(downRates.ev()).isNull();                                  // no figure: the place is kept, nothing is carried
        assertThat(downRates.set()).isNull();
    }

    @Test
    void aClickOnAPivotCellFiresTheEventWithTheCellsPayload() {
        ScreenHarness h = ScreenHarness.of(PIVOT, PNL, SUMS);
        ScreenWire.Result r = h.wire().event(h.screen(), "m", "cell", 2);
        assertThat(r.selection().seen().get("scenario").asText()).isEqualTo("DOWN");
        assertThat(r.selection().seen().get("desk").asText()).isEqualTo("FX");
        assertThat(r.selection().seen().get("pnl").asDouble()).isEqualTo(-7.0);
    }

    @Test
    void fireFindsAPivotCellByItsPlace() {
        ScreenHarness h = ScreenHarness.of(PIVOT, PNL, SUMS);
        Fire.Result r = Fire.fire(h.view(), "m", "point-click", 1);
        assertThat(r.state()).containsEntry("picked", "UP/RATES@1");
    }

    @Test
    void withoutAnEventACellCarriesItsSetAndNoPlace() {
        String noEmits = String.join("\n", PIVOT.lines().filter(l -> !l.contains("emits:")).toList());
        PanelData.Pivot p = (PanelData.Pivot) ScreenHarness.of(noEmits, PNL, SUMS).view().panels().get(0).data();
        assertThat(p.rows().get(0).cells().get(0).ev()).isNull();
        assertThat(p.rows().get(0).cells().get(0).set()).isEqualTo(Map.of("picked", "UP/FX@0"));
    }

    static final String SURFACE = """
            rachana: 1.2
            sutra: vols
            version: 1
            title: { id: "'Vols'" }
            state:
              at: { type: text }
            panels:
              - id: s
                kind: surface
                rows: $.smile
                y: expiry
                columns: [ { label: "90", bind: "@.k90" }, { label: "110", bind: "@.k110" } ]
                emits: [ { name: point, on: point-click, payload: { expiry: "@.y", strike: "@.x" } } ]
                on:
                  point-click: [ { set: { state: at, to: "@.y + ' ' + @.x + ' ' + @.z" } } ]
            """;

    static final Map<String, Object> SMILE = Map.of("smile", List.of(Map.of("expiry", "1M", "k90", 0.25, "k110", 0.2),
            Map.of("expiry", "1Y", "k110", 0.22)));

    @Test
    void aSurfacesPointsAreMarksBesideZ() {
        ViewModel vm = ScreenHarness.of(SURFACE, SMILE).view();
        assertThat(vm.contract()).isEqualTo("1.2");
        PanelData.Surface s = (PanelData.Surface) vm.panels().get(0).data();
        assertThat(s.ev()).containsExactly(java.util.Arrays.asList("0", "1"), java.util.Arrays.asList(null, "3"));
        assertThat(s.set().get(1).get(1)).isEqualTo(Map.of("at", "1Y 110 0.22"));
        assertThat(s.set().get(1).get(0)).isNull();
    }

    @Test
    void aClickOnASurfacePointFiresTheEventAndFireFindsIt() {
        ScreenHarness h = ScreenHarness.of(SURFACE, SMILE);
        ScreenWire.Result r = h.wire().event(h.screen(), "s", "point", 1);
        assertThat(r.selection().seen().get("expiry").asText()).isEqualTo("1M");
        assertThat(r.selection().seen().get("strike").asText()).isEqualTo("110");
        assertThat(Fire.fire(h.view(), "s", "point-click", 3).state()).containsEntry("at", "1Y 110 0.22");
    }
}
