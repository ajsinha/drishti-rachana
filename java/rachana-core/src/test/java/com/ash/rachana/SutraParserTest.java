/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ash.rachana.model.Area;
import com.ash.rachana.model.PanelKind;
import com.ash.rachana.model.Sutra;
import com.ash.rachana.parse.SutraParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class SutraParserTest {

    private final SutraParser parser = com.ash.rachana.TestGrammar.parser();

    @Test
    void everySutraDeclaresTheLanguageVersionItIsWrittenIn() {
        String body = "sutra: v-check\nversion: 1\nmatch: { kind: trade }\n";
        assertThat(parser.parse("rachana: 1\n" + body, "v.sutra.yaml", "rates").id()).isEqualTo("v-check@1");
        assertThatThrownBy(() -> parser.parse(body, "v.sutra.yaml", "rates"))
                .satisfies(e -> assertThat(((SutraException) e).problems()).extracting(SutraProblem::code).contains("RCH-2009"));
        assertThatThrownBy(() -> parser.parse("rachana: 2\n" + body, "v.sutra.yaml", "rates"))
                .satisfies(e -> assertThat(((SutraException) e).problems().get(0).message()).contains("not a language version this server reads"));
        assertThatThrownBy(() -> parser.parse("rachana: 1\nnotes: [a, b]\n" + body, "v.sutra.yaml", "rates"))
                .satisfies(e -> assertThat(((SutraException) e).problems()).extracting(SutraProblem::message).anySatisfy(m -> assertThat(m).contains("'notes' is plain text")));
    }

    @Test
    void reportsEveryProblemWithLineAndColumn() {
        String yaml = """
                rachana: 1
                sutra: Bad_Name
                version: 0
                match: { kind: trade }
                colour: red
                strip:
                  - { label: X }
                panels:
                  - { id: a, kind: table, key: F2 }
                  - { id: a, kind: pie }
                  - { id: b, kind: kv, key: F2, wobble: 1 }
                """;
        assertThatThrownBy(() -> parser.parse(yaml, "bad.yaml", "x"))
                .isInstanceOfSatisfying(SutraException.class, e -> {
                    assertThat(e.problems()).extracting(SutraProblem::code).contains(
                            "RCH-2020", "RCH-2011", "RCH-2010", "RCH-2022", "RCH-2021", "RCH-2023", "RCH-2025");
                    assertThat(e.problems()).filteredOn(p -> p.code().equals("RCH-2011"))
                            .first().satisfies(p -> assertThat(p.location().line()).isEqualTo(5));
                });
    }

    @Test
    void yamlSyntaxErrorsHaveAPosition() {
        assertThatThrownBy(() -> parser.parse("rachana: 1\nsutra: x\n  version: [1,\n", "broken.yaml", "x"))
                .isInstanceOfSatisfying(SutraException.class,
                        e -> assertThat(e.problems().get(0).code()).isEqualTo("RCH-2001"));
    }

    @Test
    void aTablePanelMayTurnItsSearchOffAndOnlyTablesHaveOne() {
        String ok = """
                rachana: 1
                sutra: quiet-table
                version: 1
                match: { kind: trade }
                panels:
                  - id: legs
                    kind: table
                    rows: $.legs
                    search: false
                    columns:
                      - { label: Leg, bind: "@.leg" }
                """;
        var s = parser.parse(ok, "quiet.yaml", "x");
        assertThat(s.panels().get(0).option("search")).contains("false");
        String bad = """
                rachana: 1
                sutra: noisy-kv
                version: 1
                match: { kind: trade }
                panels:
                  - { id: terms, kind: kv, search: false }
                """;
        assertThatThrownBy(() -> parser.parse(bad, "noisy.yaml", "x"))
                .isInstanceOfSatisfying(SutraException.class, e -> assertThat(e.problems()).extracting(SutraProblem::message)
                        .anySatisfy(m -> assertThat(m).contains("'search' applies only to panels that show a table")));
    }

    @Test
    void theChartAndAggregateKindsParseAndRejectValuesTheyDoNotAllow() {
        String ok = """
                rachana: 1
                sutra: chart-kinds
                version: 1
                match: { kind: trade }
                panels:
                  - { id: w, kind: waterfall, rows: $.explain, label: step, value: pnl, sum: Closing }
                  - id: h
                    kind: histogram
                    rows: $.scenarios
                    bins: 30
                    markers:
                      - { label: VaR 99%, value: "-$.var99", tone: neg }
                  - { id: s, kind: scatter, rows: $.books, x: var, y: pnl, group: desk }
                  - { id: c, kind: candlestick, rows: $.ohlc, volume: volume }
                  - { id: g, kind: graph, nodes: $.tree.nodes, edges: $.tree.edges, layout: force }
                  - { id: t, kind: timeline, rows: $.events }
                  - { id: p, kind: pivot, rows: $.positions, by: book, across: currency, value: mtm, agg: avg, heat: true }
                """;
        var s = parser.parse(ok, "charts.yaml", "x");
        assertThat(s.panels()).extracting(x -> x.kind().id())
                .containsExactly("waterfall", "histogram", "scatter", "candlestick", "graph", "timeline", "pivot");
        assertThat(s.panels().get(1).options().get("markers")).isInstanceOf(List.class);
        String bad = """
                rachana: 1
                sutra: bad-charts
                version: 1
                match: { kind: trade }
                panels:
                  - { id: p, kind: pivot, rows: $.positions, by: book, across: currency, agg: median, heat: yes please }
                  - { id: h, kind: histogram, rows: $.scenarios, bins: 0, markers: [ { label: VaR } ] }
                  - { id: g, kind: graph, nodes: $.n, layout: circle }
                  - { id: s, kind: scatter, rows: $.books, x: var }
                  - { id: w, kind: waterfall, rows: $.explain, colors: rainbow }
                """;
        assertThatThrownBy(() -> parser.parse(bad, "bad-charts.yaml", "x"))
                .isInstanceOfSatisfying(SutraException.class, e -> {
                    assertThat(e.problems()).filteredOn(p -> p.code().equals("RCH-2029")).extracting(SutraProblem::message).hasSize(6)
                            .anySatisfy(m -> assertThat(m).contains("must be one of sum, count, avg, min, max, not 'median'"))
                            .anySatisfy(m -> assertThat(m).contains("'heat' of 'pivot' panels must be true or false"))
                            .anySatisfy(m -> assertThat(m).contains("'bins' of 'histogram' panels must be a whole number from 1 to 200"))
                            .anySatisfy(m -> assertThat(m).contains("each histogram marker must be a mapping with a 'value'"))
                            .anySatisfy(m -> assertThat(m).contains("must be one of tree, force, not 'circle'"))
                            .anySatisfy(m -> assertThat(m).contains("option 'colors' of 'waterfall' panels must be one of gain-loss, theme, not 'rainbow'"));
                    assertThat(e.problems()).extracting(SutraProblem::message).contains("'scatter' panel 's' needs option 'y'");
                });
    }

    /** {@code refresh:} (seconds) on the Sutra goes on every top-level panel without its own (RACHANA_REFERENCE.md 9); a wrong one is RCH-2012. */
    @Test
    void refreshIsWholeSeconds() {
        String ok = "rachana: 1\nsutra: my-desk\nversion: 1\nmatch: { kind: trade }\nrefresh: 30\ntitle: { id: $.id }\npanels:\n  - { id: refs, kind: links }\n"
                + "  - { id: mine, kind: links, refresh: { every: 60, user-can-pause: false } }\n";
        var s = parser.parse(ok, "ok.yaml", "x");
        assertThat(s.name()).isEqualTo("my-desk");
        assertThat(s.panels().get(0).refresh()).contains(new com.ash.rachana.model.Refresh(30, true));
        assertThat(s.panels().get(1).refresh()).contains(new com.ash.rachana.model.Refresh(60, false));
        for (String bad : new String[] {"refresh: soon", "refresh: 0", "refresh: 2.5", "refresh: { every: 5, often: true }", "refresh: { user-can-pause: true }",
                "refresh: { every: 5, user-can-pause: maybe }"}) {
            assertThatThrownBy(() -> parser.parse(ok.replace("refresh: 30", bad), "bad.yaml", "x"))
                    .as(bad).isInstanceOfSatisfying(SutraException.class, e -> assertThat(e.problems()).extracting(SutraProblem::code).contains("RCH-2012"));
        }
    }

    /** A panel's own {@code refresh:} (a number or {every, user-can-pause}) is checked; a panel inside a tab cannot refresh alone. */
    @Test
    void panelRefresh() {
        String ok = "rachana: 1\nsutra: my-desk\nversion: 1\nmatch: { kind: trade }\ntitle: { id: $.id }\npanels:\n  - { id: refs, kind: links, refresh: 15 }\n"
                + "  - { id: other, kind: links }\n";
        var s = parser.parse(ok, "ok.yaml", "x");
        assertThat(s.panels().get(0).refresh()).contains(new com.ash.rachana.model.Refresh(15, true));
        assertThat(s.panels().get(1).refresh()).isEmpty();
        assertThatThrownBy(() -> parser.parse(ok.replace("refresh: 15", "refresh: { every: 90000 }"), "bad.yaml", "x"))
                .isInstanceOfSatisfying(SutraException.class, e -> assertThat(e.problems()).extracting(SutraProblem::message)
                        .anySatisfy(m -> assertThat(m).contains("refresh.every")));
        String tabs = "rachana: 1\nsutra: my-desk\nversion: 1\nmatch: { kind: trade }\ntitle: { id: $.id }\npanels:\n"
                + "  - { id: t, kind: tabs, each: $.legs, body: { kind: kv, refresh: 5 } }\n";
        assertThatThrownBy(() -> parser.parse(tabs, "bad.yaml", "x"))
                .isInstanceOfSatisfying(SutraException.class, e -> assertThat(e.problems()).extracting(SutraProblem::message)
                        .anySatisfy(m -> assertThat(m).contains("put it on the tabs panel")));
    }
}
