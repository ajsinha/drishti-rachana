/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.table;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ash.rachana.SutraException;
import com.ash.rachana.SutraProblem;
import com.ash.rachana.parse.Json;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** A live table's declaration (LIVE_TABLES section 2, step L1); ports the declaration checks of the liveset engine's validation tests. */
class LiveTableSpecTest {

    static final String BLOTTER = """
            rachana: 1
            live-table: trade
            title: Trade blotter
            snapshot: trades
            stream: trades.live
            where: "@.TRADE_STATUS != 'CANCELLED'"
            key: TRADE_ID
            version: TRADE_VERSION
            changed-by: UPDATED_BY
            changed-at: SOURCE_UPDATE_TIME
            columns:
              - { name: TRADE_ID, label: Trade, type: string, width: 120 }
              - { name: TRADE_VERSION, type: integer }
              - { name: TRADE_STATUS, type: string }
              - { name: PORTFOLIO, type: string }
              - { name: PRICE, type: decimal }
              - { name: QUANTITY, label: Quantity, type: decimal }
              - { name: UPDATED_BY, type: string }
              - { name: SOURCE_UPDATE_TIME, type: timestamp }
              - { name: SCENARIO_PVS, type: list, items: decimal, heavy: true }
            sort: { col: SOURCE_UPDATE_TIME, dir: desc }
            subtotals: [QUANTITY]
            narrow: { any-of: [PORTFOLIO, TRADE_ID] }
            retention: { ttl: 1d, on: SOURCE_UPDATE_TIME, max-rows: 50000 }
            tray: true
            edit:
              edit: cells
              fields:
                - { id: price, bind: PRICE, type: number, min: 0 }
                - { id: status, bind: TRADE_STATUS, type: enum, options: [NEW, VALIDATED, AMENDED, CANCELLED] }
              reason: optional
            """;

    private static List<SutraProblem> problems(String yaml) {
        return assertThrows(SutraException.class, () -> LiveTableSpec.read(yaml, "x.live.yaml")).problems();
    }

    private static List<String> codes(String yaml) {
        return problems(yaml).stream().map(SutraProblem::code).toList();
    }

    @Test
    void theDesignsBlotterReadsWithEveryPart() {
        LiveTableSpec s = LiveTableSpec.read(BLOTTER, "trade.live.yaml");
        assertEquals("trade", s.name());
        assertEquals(List.of("TRADE_ID"), s.key());
        assertEquals("TRADE_VERSION", s.version());
        assertTrue(s.newestFirst());
        assertEquals(86_400_000L, s.retention().ttlMs());
        assertEquals(50_000, s.retention().maxRows());
        assertEquals(List.of("PRICE", "TRADE_STATUS"), s.editableColumns());
        assertEquals("optional", s.reason());
        assertFalse(s.listColumns().contains("SCENARIO_PVS"));
        assertNotNull(s.whereTree(), "the condition is pushed down as a filter tree");
        Map<String, Object> schema = s.schema();
        assertEquals("TRADE_VERSION", schema.get("rowVersion"));
        assertEquals("{\"anyOf\":[\"PORTFOLIO\",\"TRADE_ID\"]}", Json.write(schema.get("narrow")));
        assertTrue(((List<?>) schema.get("can")).containsAll(List.of("changes.window", "narrow")));
        assertEquals("{\"sort\":[{\"col\":\"SOURCE_UPDATE_TIME\",\"dir\":\"desc\"}],\"subtotals\":[\"QUANTITY\"],\"widths\":{\"TRADE_ID\":120}}",
                Json.write(schema.get("view")));
    }

    @Test
    void aReadOnlyTableNeedsNoVersion() {
        LiveTableSpec s = LiveTableSpec.read("""
                rachana: 1
                live-table: prices
                snapshot: px
                key: [CCY, TENOR]
                columns: [ { name: CCY }, { name: TENOR }, { name: RATE, type: number } ]
                """, "prices.live.yaml");
        assertNull(s.edit());
        assertNull(s.stream());
        assertFalse(s.newestFirst());
        assertEquals(List.of("CCY", "TENOR"), s.key());
    }

    @Test
    void aDeclarationWithProblemsSaysThemAllInOrder() {
        List<SutraProblem> ps = problems("""
                rachana: 1
                live-table: Bad Id
                snapshot: trades
                columns: [ { name: A }, { name: A }, { name: B, type: money } ]
                sort: { col: C }
                retention: { ttl: soon }
                links: [ { label: x } ]
                """);
        assertEquals(List.of("RCH-2072", "RCH-2020", "RCH-2024", "RCH-2023", "RCH-2071", "RCH-2083", "RCH-2060"),
                ps.stream().map(SutraProblem::code).toList(), ps.toString());
        assertTrue(ps.get(4).message().contains("'sort' names the column 'C'"), ps.get(4).message());
    }

    @Test
    void anEditNeedsAVersionAndNeverSetsTheKeyOrTheChangeColumns() {
        assertEquals(List.of("RCH-2080", "RCH-2081"), codes("""
                rachana: 1
                live-table: t
                snapshot: s
                key: K
                columns: [ { name: K }, { name: V, type: integer } ]
                edit: { fields: [ { id: k, bind: K, type: text } ] }
                """));
        assertEquals(List.of("RCH-2081"), codes("""
                rachana: 1
                live-table: t
                snapshot: s
                key: K
                version: V
                columns: [ { name: K }, { name: V, type: integer } ]
                edit: { fields: [ { id: v, bind: V, type: number } ] }
                """));
    }

    @Test
    void aHeavyColumnIsNeverTheKeyTheOrderOrANarrowing() {
        assertEquals(List.of("RCH-2082", "RCH-2082"), codes("""
                rachana: 1
                live-table: t
                snapshot: s
                key: K
                columns: [ { name: K }, { name: P, type: list, heavy: true } ]
                sort: { col: P }
                narrow: { any-of: [P] }
                """));
    }

    @Test
    void profileKeysNeedTheirProfile() {
        String yaml = """
                rachana: 1
                live-table: t
                kind: trade
                snapshot: s
                key: K
                version: V
                columns: [ { name: K }, { name: V, type: integer }, { name: P, type: number } ]
                edit: { save: { target: overlay }, fields: [ { id: p, bind: P, type: number } ] }
                """;
        assertEquals(List.of("RCH-2060", "RCH-2058"), codes(yaml));
        LiveTableSpec s = LiveTableSpec.read(yaml, "t.live.yaml", Set.of("kind", "edit.save"), null);
        assertEquals("trade", s.profile().get("kind"));
    }

    @Test
    void theLanguageVersionTheConditionAndFormatsAreChecked() {
        assertEquals(List.of("RCH-2009"), codes("live-table: t\nsnapshot: s\nkey: K\ncolumns: [ { name: K } ]\n"));
        assertEquals(List.of("RCH-2101"), codes("rachana: 1\nlive-table: t\nsnapshot: s\nkey: K\ncolumns: [ { name: K } ]\nwhere: \"@.K ==\"\n"));
        SutraException e = assertThrows(SutraException.class, () -> LiveTableSpec.read(
                "rachana: 1\nlive-table: t\nsnapshot: s\nkey: K\ncolumns: [ { name: K, fmt: nope } ]\n", "t.live.yaml", Set.of(), f -> !f.equals("nope")));
        assertEquals("RCH-2020", e.problems().get(0).code());
    }

    @Test
    void aDefaultDeclarationIsMadeInCodeWithTheSameChecks() {
        LiveTableSpec s = LiveTableSpec.of(Map.of("live-table", "positions", "snapshot", "pos", "key", "ID",
                "columns", List.of(Map.of("name", "ID"), Map.of("name", "QTY", "type", "decimal"))), Set.of(), null);
        assertEquals("positions", s.name());
        assertThrows(SutraException.class, () -> LiveTableSpec.of(Map.of("live-table", "x", "snapshot", "s", "columns", List.of(Map.of("name", "A"))),
                Set.of(), null));
    }

    @Test
    void durations() {
        assertEquals(500, LiveTableSpec.durationMs("500ms"));
        assertEquals(600_000, LiveTableSpec.durationMs("10m"));
        assertEquals(30_000, LiveTableSpec.durationMs(30));
        assertEquals(0, LiveTableSpec.durationMs("soon"));
    }
}
