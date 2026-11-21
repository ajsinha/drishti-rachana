/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.bind;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ash.rachana.SutraException;
import com.ash.rachana.parse.SutraParser;
import com.ash.rachana.profile.Grammar;
import com.ash.rachana.view.PanelData;
import com.ash.rachana.view.Patch;
import com.ash.rachana.view.ViewJson;
import com.ash.rachana.view.ViewModel;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Language 1.1 and ViewModel contract 1.1: the dataset panels, the language level, the minimal stamp, map points and the rows patch. */
class DatasetPanelsTest {

    private static final String GRID = """
            rachana: 1
            sutra: ss
            version: 1
            panels:
              - { id: g, kind: grid, rows: $.rows }
            """;

    @Test
    void aGridIsALevel11NameRefusedInWordsAtLevel10() {
        assertThatThrownBy(() -> new SutraParser().parse(GRID, "ss.sutra.yaml", "t"))
                .isInstanceOf(SutraException.class)
                .satisfies(e -> assertThat(((SutraException) e).problems()).anySatisfy(p -> {
                    assertThat(p.code()).isEqualTo("RCH-2008");
                    assertThat(p.message()).isEqualTo("'grid' panels need Rachana 1.1; this host runs 1.0 (raise the host's language level)");
                }));
        assertThat(new SutraParser(Grammar.core().atLevel("1.1")).parse(GRID.replace("rachana: 1\n", "rachana: 1.1\n"), "ss.sutra.yaml", "t").panels()).hasSize(1);
    }

    @Test
    void anUnknownKindListsOnlyTheKindsOfTheLevel() {
        String bad = GRID.replace("kind: grid", "kind: hologram");
        assertThatThrownBy(() -> new SutraParser().parse(bad, "ss.sutra.yaml", "t"))
                .satisfies(e -> assertThat(((SutraException) e).problems().get(0).message()).doesNotContain("grid").endsWith("metric, map"));
        assertThatThrownBy(() -> new SutraParser(Grammar.core().atLevel("1.1")).parse(bad.replace("rachana: 1\n", "rachana: 1.1\n"), "ss.sutra.yaml", "t"))
                .satisfies(e -> assertThat(((SutraException) e).problems().get(0).message()).endsWith("map, grid, tree, chart"));
        assertThatThrownBy(() -> Grammar.core().atLevel("1.9")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theStampIsTheSmallestMinorThatDescribesTheDocument() {
        ViewModel plain = new ViewModel(null, null, null, List.of(), List.of(), List.of(), null, null);
        assertThat(plain.contract()).isEqualTo("1.0");
        assertThat(ViewJson.write(plain)).doesNotContain("datasets");
        ViewModel.Dataset ds = ViewModel.Dataset.of(List.of("id"), List.of(new ViewModel.DataField("id", "string", null, null)),
                List.of(List.of("A")), 0);
        ViewModel with = plain.withDatasets(Map.of("d", ds));
        assertThat(with.contract()).isEqualTo("1.1");
        assertThat(ViewJson.write(with)).contains("\"datasets\":{\"d\":{\"key\":[\"id\"],\"fields\":[{\"name\":\"id\",\"type\":\"string\"}],\"values\":[[\"A\"]]}}");
        assertThat(with.withDatasets(null).contract()).isEqualTo("1.1");          // a stamp is never lowered once written
        ViewModel.PanelView map = new ViewModel.PanelView("m", "map", null, null, null, "main", false, null,
                new PanelData.GeoMap("world", null, null, null, null, null, List.of(new PanelData.GeoPoint("London", 51.5, -0.12, 3.0, "3", null, null))),
                null, false);
        assertThat(new ViewModel(null, null, null, List.of(), List.of(map), List.of(), null, null).contract()).isEqualTo("1.1");
        ViewModel.PanelView flat = new ViewModel.PanelView("m", "map", null, null, null, "main", false, null,
                new PanelData.GeoMap("world", null, null, null, null, null), null, false);
        ViewModel v10 = new ViewModel(null, null, null, List.of(), List.of(flat), List.of(), null, null);
        assertThat(v10.contract()).isEqualTo("1.0");
        assertThat(ViewJson.write(v10)).doesNotContain("points");
    }

    @Test
    void aRowsPatchWritesOnlyWhatItCarries() {
        assertThat(ViewJson.write(Patch.rows("trades", null, List.of("T-1"), List.of(Map.of("pv", 5)), null)))
                .isEqualTo("{\"op\":\"rows\",\"dataset\":\"trades\",\"remove\":[\"T-1\"],\"update\":[{\"pv\":5}]}");
        assertThat(ViewJson.write(new Patch("restored", null, null, null, null))).isEqualTo("{\"op\":\"restored\"}");
    }

    @Test
    void aColumnsFieldIsTheFieldItReadsElseItsLabel() {
        assertThat(DatasetBinder.fieldName(new com.ash.rachana.model.Column("Notional", "@.notional", null, null, false, false), 0)).isEqualTo("notional");
        assertThat(DatasetBinder.fieldName(new com.ash.rachana.model.Column("P&L today", "@.a + @.b", null, null, false, false), 0)).isEqualTo("P_L_today");
        assertThat(DatasetBinder.fieldName(new com.ash.rachana.model.Column(null, "1 + 1", null, null, false, false), 2)).isEqualTo("c3");
        assertThat(DatasetBinder.raw(Double.NaN)).isNull();
        assertThat(DatasetBinder.raw(com.ash.rachana.api.Node.masked())).isEqualTo(Map.of("$masked", true));
    }
}
