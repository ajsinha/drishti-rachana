/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ash.rachana.live.spi.Change;
import com.ash.rachana.live.spi.ChangeWriter;
import com.ash.rachana.parse.Json;
import com.ash.rachana.table.LiveTableSpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The file table writer (LIVE_TABLES 10): a change replaces the file atomically (the rest of it kept as read), its id is journalled so it is
 * applied once, a change left at {@code writing} by a crash after the file was replaced is recognised, the version guard runs at the file.
 */
class FileTableWriterTest {

    @TempDir
    Path tmp;

    static final String DECL = """
            rachana: 1
            live-table: orders
            snapshot: orders
            key: ID
            version: V
            changed-by: BY_USER
            changed-at: AT
            columns: [ { name: ID }, { name: V, type: integer }, { name: QTY, type: integer }, { name: NOTE }, { name: BY_USER }, { name: AT } ]
            edit:
              new: drawer
              fields: [ { id: qty, bind: QTY, type: number, min: 0 }, { id: note, bind: NOTE, type: text } ]
            """;

    FileTableWriter writer(Path file) {
        ServerConfig.Table t = new ServerConfig.Table("orders", null, List.of("ID"), file, null, null, Map.of(), null, 100, 10_000, 1_000,
                List.of(), null);
        FileTableWriter w = new FileTableWriter();
        w.target(new FileTableWriter.Target(LiveTableSpec.read(DECL, "orders.live.yaml"), t, tmp.resolve("journal/orders.applied.jsonl")));
        return w;
    }

    static Change change(String id, String op, Object key, Object basedOn, Map<String, Object> set) {
        return new Change(id, "orders", op, List.of(key), basedOn, set, null, null, "alice", Instant.parse("2026-11-21T10:00:00Z"));
    }

    @Test
    void aCsvChangeIsAppliedOnceAndTheRestOfTheFileIsKept() throws Exception {
        Path f = tmp.resolve("orders.csv");
        Files.writeString(f, "ID,V,QTY,NOTE,BY_USER,AT,EXTRA\na,1,1,\"first, with a comma\",system,,x\nb,4,2,,system,,y\n");
        FileTableWriter w = writer(f);
        assertEquals(2L, w.write(change("c1", "update", "a", 1L, Map.of("QTY", 5))));
        assertEquals("ID,V,QTY,NOTE,BY_USER,AT,EXTRA\na,2,5,\"first, with a comma\",alice,2026-11-21T10:00:00Z,x\nb,4,2,,system,,y\n",
                Files.readString(f));
        assertNull(w.write(change("c1", "update", "a", 1L, Map.of("QTY", 5))), "the same change again is already applied");
        ChangeWriter.Refused stale = assertThrows(ChangeWriter.Refused.class, () -> w.write(change("c2", "update", "a", 1L, Map.of("QTY", 6))));
        assertTrue(stale.getMessage().contains("since version 1"), stale.getMessage());
        assertEquals(1L, w.write(change("c3", "add", "z", null, Map.of("QTY", 9, "NOTE", "new \"one\""))));
        assertTrue(Files.readString(f).endsWith("z,1,9,\"new \"\"one\"\"\",alice,2026-11-21T10:00:00Z,\n"), Files.readString(f));
        assertNull(w.write(change("c4", "remove", "b", 4L, Map.of())));
        assertFalse(Files.readString(f).contains("\nb,"));
        assertFalse(Files.exists(tmp.resolve("orders.csv.writing")), "nothing left beside the file");
        String journal = Files.readString(tmp.resolve("journal/orders.applied.jsonl"));
        assertTrue(journal.contains("\"change\":\"c1\",\"state\":\"writing\"") && journal.contains("\"change\":\"c1\",\"state\":\"applied\""), journal);
        assertFalse(journal.contains("\"change\":\"c2\""), "a refused change is never journalled");
        assertEquals(2L, Rows.csv(Files.readString(f)).size());
    }

    /** A CSV that holds its header only keeps that column order for the first row added (the header read by Tools' CsvReader). */
    @Test
    void aHeaderOnlyCsvKeepsItsColumnOrder() throws Exception {
        Path f = tmp.resolve("orders.csv");
        Files.writeString(f, "ID,V,\"QTY\",NOTE,BY_USER,AT\n");
        assertEquals(1L, writer(f).write(change("c1", "add", "a", null, Map.of("NOTE", "=1+1, as typed", "QTY", 3))));
        assertEquals("ID,V,QTY,NOTE,BY_USER,AT\na,1,3,\"=1+1, as typed\",alice,2026-11-21T10:00:00Z\n", Files.readString(f));
    }

    @Test
    void aChangeLeftAtWritingByACrashIsRecognisedOnce() throws Exception {
        Path f = tmp.resolve("orders.jsonl");
        // the file was replaced with c1's result, then the process stopped before c1 was recorded applied
        Files.writeString(f, "{\"ID\":\"a\",\"V\":2,\"QTY\":5,\"BY_USER\":\"alice\"}\n");
        Files.createDirectories(tmp.resolve("journal"));
        Files.writeString(tmp.resolve("journal/orders.applied.jsonl"), "{\"change\":\"c0\",\"state\":\"applied\"}\n{\"change\":\"c1\",\"state\":\"writing\"}\n{\"chan");
        FileTableWriter w = writer(f);
        assertEquals(2L, w.write(change("c1", "update", "a", 1L, Map.of("QTY", 5))), "applied before the crash: answered, not applied again");
        assertEquals("{\"ID\":\"a\",\"V\":2,\"QTY\":5,\"BY_USER\":\"alice\"}\n", Files.readString(f));
        assertNull(w.write(change("c0", "update", "a", 7L, Map.of("QTY", 1))), "an applied id is never applied again");
        assertEquals(3L, w.write(change("c2", "update", "a", 2L, Map.of("QTY", 6))));
        assertTrue(Files.readString(f).contains("\"V\":3,\"QTY\":6"), Files.readString(f));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aJsonFileKeepsItsWrapperAndTypes() throws Exception {
        Path f = tmp.resolve("orders.json");
        Files.writeString(f, "{\"source\":\"desk\",\"rows\":[{\"ID\":\"a\",\"V\":1,\"QTY\":1.5,\"NOTE\":null}]}");
        assertEquals(2L, writer(f).write(change("c1", "update", "a", 1L, Map.of("QTY", 2.25))));
        Map<String, Object> doc = (Map<String, Object>) Json.read(Files.readString(f, StandardCharsets.UTF_8));
        assertEquals("desk", doc.get("source"));
        Map<String, Object> row = (Map<String, Object>) ((List<?>) doc.get("rows")).get(0);
        assertEquals(2.25, ((Number) row.get("QTY")).doubleValue());
        assertEquals(2L, ((Number) row.get("V")).longValue());
        assertEquals("alice", row.get("BY_USER"));
    }
}
