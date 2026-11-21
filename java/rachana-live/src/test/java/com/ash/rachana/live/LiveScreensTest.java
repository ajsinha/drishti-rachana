/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.live;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ash.rachana.Rachana;
import com.ash.rachana.lib.LibraryResolver;
import com.ash.rachana.lib.LibrarySet;
import com.ash.rachana.live.spi.ReaderView;
import com.ash.rachana.live.spi.SnapshotSource;
import com.ash.rachana.model.Sutra;
import com.ash.rachana.table.LiveScreen;
import com.ash.rachana.table.LiveTableSpec;
import com.ash.rachana.view.ViewJson;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** LiveTables.screen (LIVE_TABLES 2.2, LT-12): the Sutra a declaration gives compiles against the bundled core library at 1.2. */
class LiveScreensTest {

    private static final String BLOTTER = """
            rachana: 1
            live-table: trade
            title: Trade blotter
            snapshot: trades
            stream: trades.live
            key: TRADE_ID
            version: TRADE_VERSION
            changed-by: UPDATED_BY
            changed-at: UPDATED_AT
            columns:
              - { name: TRADE_ID, label: Trade, type: string }
              - { name: TRADE_VERSION, type: integer }
              - { name: QUANTITY, label: Quantity, type: decimal, fmt: amount0 }
              - { name: STATUS, type: string }
              - { name: UPDATED_BY, type: string }
              - { name: UPDATED_AT, type: timestamp }
              - { name: SCENARIO_PVS, type: list, items: decimal, heavy: true }
            sort: { col: UPDATED_AT, dir: desc }
            tray: true
            edit:
              edit: cells
              fields:
                - { id: qty, bind: QUANTITY, type: number, min: 0 }
                - { id: status, bind: STATUS, type: enum, options: [NEW, DONE] }
            """;

    private static final String READ_ONLY = """
            rachana: 1
            live-table: prices
            snapshot: prices
            key: [CURVE, TENOR]
            columns:
              - { name: CURVE }
              - { name: TENOR }
              - { name: RATE, type: decimal }
            """;

    private static LiveTables tables() {
        LiveTables t = new LiveTables(new LiveTables.Resolver() {
            @Override
            public SnapshotSource snapshot(String name) {
                throw new IllegalStateException("not read");
            }

            @Override
            public com.ash.rachana.live.spi.LiveStream stream(String name) {
                return null;
            }
        }, LiveTables.Options.DEFAULTS.withUpkeepMs(0), null).authoriser((caller, table) -> ReaderView.all());
        t.declare(LiveTableSpec.read(BLOTTER, "trade.live.yaml"));
        t.declare(LiveTableSpec.read(READ_ONLY, "prices.live.yaml"));
        return t;
    }

    private static String view(String sutraText) throws Exception {
        Path libs = Path.of(System.getProperty("rachana.repo.root")).toAbsolutePath().normalize().resolve("libraries");
        Rachana rachana = Rachana.core().language("1.2").libraries(LibrarySet.of(LibraryResolver.folder(libs)));
        Sutra sutra = rachana.parser().parse(sutraText, "screen.sutra.yaml", "screen");
        assertEquals(java.util.List.of(), sutra.advisories(), sutraText);
        return ViewJson.write(new LiveScreen(rachana, sutra, LocalDate.of(2026, 11, 21), null, Map.of()).view());
    }

    @Test
    void aBlotterGivesTheLiveTableWithEditsAndItsTray() throws Exception {
        try (LiveTables t = tables()) {
            String sutra = t.screen("trade");
            assertTrue(sutra.contains("use: core/live-table@1") && sutra.contains("use: core/held-changes@1"), sutra);
            assertFalse(sutra.contains("SCENARIO_PVS"), "a heavy column is never in a list page");
            String view = view(sutra);
            assertTrue(view.contains("\"table\":\"trade\"") && view.contains("\"edit\":\"cells\"") && view.contains("\"top\":true"), view);
            assertTrue(view.contains("\"table\":\"trade.changes\""), view);
            assertTrue(view.contains("\"field\":\"UPDATED_AT\",\"desc\":true") || view.contains("\"desc\":true,\"field\":\"UPDATED_AT\""), view);
            assertTrue(view.contains("\"label\":\"Quantity\"") && view.contains("\"fmt\":\"amount0\""), view);
        }
    }

    @Test
    void aReadOnlyTableGivesTheLiveTableAlone() throws Exception {
        try (LiveTables t = tables()) {
            String sutra = t.screen("prices");
            assertFalse(sutra.contains("held-changes") || sutra.contains("edit:"), sutra);
            assertTrue(sutra.contains("key: [\"CURVE\",\"TENOR\"]") && sutra.contains("direction: asc"), sutra);
            String view = view(sutra);
            assertTrue(view.contains("\"table\":\"prices\"") && !view.contains("\"edit\""), view);
        }
    }
}
