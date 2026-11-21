/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ash.rachana.parse.Json;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A filter tree pushed into a JDBC query: bound values, allow-listed columns, never text from the request. */
class FilterSqlTest {

    static final Map<String, String> COLS = Map.of("ID", "integer", "REGION", "string", "AMOUNT", "decimal", "DAY", "date", "OK", "boolean");

    @TempDir
    Path tmp;

    static FilterSql.Where of(String json) {
        return FilterSql.of(Json.read(json), COLS);
    }

    @Test
    void aComparisonIsOneBoundValue() {
        FilterSql.Where w = of("{\"op\":\"==\",\"args\":[{\"col\":\"REGION\"},{\"lit\":\"North\"}]}");
        assertEquals("\"REGION\" = ?", w.sql());
        assertEquals(List.of("North"), w.params());
        w = of("{\"op\":\"<\",\"args\":[{\"lit\":5},{\"col\":\"ID\"}]}");
        assertEquals("\"ID\" > ?", w.sql(), "a literal on the left turns the comparison round");
    }

    @Test
    void anUnknownOrOddColumnNameIsNeverInTheSql() {
        assertNull(of("{\"op\":\"==\",\"args\":[{\"col\":\"NOPE\"},{\"lit\":\"x\"}]}"));
        assertNull(of("{\"op\":\"==\",\"args\":[{\"col\":\"REGION\\\" = 'x' OR \\\"1\"},{\"lit\":\"x\"}]}"));
        assertNull(of("{\"op\":\"==\",\"args\":[{\"col\":\"REGION; drop table t\"},{\"lit\":\"x\"}]}"));
        assertNull(of("{\"op\":\"==\",\"args\":[{\"col\":\"a.b\"},{\"lit\":\"x\"}]}"));
    }

    @Test
    void aLiteralIsAValueNeverSql() {
        FilterSql.Where w = of("{\"op\":\"==\",\"args\":[{\"col\":\"REGION\"},{\"lit\":\"x' OR '1'='1\"}]}");
        assertEquals("\"REGION\" = ?", w.sql());
        assertEquals(List.of("x' OR '1'='1"), w.params());
        assertFalse(w.sql().contains("OR"));
    }

    @Test
    void whatCannotBeSaidExactlyIsLeftOut() {
        assertNull(of("{\"op\":\"contains\",\"args\":[{\"col\":\"REGION\"},{\"lit\":\"o\"}]}"));
        assertNull(of("{\"op\":\"<\",\"args\":[{\"col\":\"REGION\"},{\"lit\":\"o\"}]}"), "text order is a collation's, not Rachana's");
        assertNull(of("{\"op\":\"==\",\"args\":[{\"col\":\"ID\"},{\"lit\":\"7\"}]}"), "a text against a number");
        assertNull(of("{\"op\":\"!\",\"args\":[{\"op\":\"==\",\"args\":[{\"col\":\"ID\"},{\"lit\":7}]}]}"));
        assertNull(of("{\"op\":\"||\",\"args\":[{\"op\":\"==\",\"args\":[{\"col\":\"ID\"},{\"lit\":7}]},{\"op\":\"contains\",\"args\":[{\"col\":\"REGION\"},{\"lit\":\"o\"}]}]}"),
                "an or with a side that stays local keeps every row");
        FilterSql.Where and = of("{\"op\":\"&&\",\"args\":[{\"op\":\"==\",\"args\":[{\"col\":\"ID\"},{\"lit\":7}]},{\"op\":\"contains\",\"args\":[{\"col\":\"REGION\"},{\"lit\":\"o\"}]}]}");
        assertEquals("(\"ID\" = ?)", and.sql(), "an and keeps what it can say");
        assertEquals(List.of(7L), and.params());
    }

    @Test
    void emptyAndNotEqualAreAsRachanaSaysThem() {
        assertEquals("\"ID\" IS NULL", of("{\"op\":\"==\",\"args\":[{\"col\":\"ID\"},{\"lit\":null}]}").sql());
        assertEquals("(\"ID\" <> ? OR \"ID\" IS NULL)", of("{\"op\":\"!=\",\"args\":[{\"col\":\"ID\"},{\"lit\":1}]}").sql());
        assertEquals("(\"ID\" NOT IN (?, ?) OR \"ID\" IS NULL)",
                of("{\"op\":\"not-in\",\"args\":[{\"col\":\"ID\"},{\"list\":[{\"lit\":1},{\"lit\":2}]}]}").sql());
        assertTrue(of("{\"op\":\">=\",\"args\":[{\"col\":\"DAY\"},{\"lit\":\"2026-01-02\",\"type\":\"date\"}]}").params().get(0) instanceof java.sql.Date);
        assertNull(of("{\"op\":\">=\",\"args\":[{\"col\":\"DAY\"},{\"lit\":\"2026-13-02\",\"type\":\"date\"}]}"));
    }

    @Test
    void aJdbcTableReadsOnlyTheRowsTheFilterKeeps() throws Exception {
        String url = "jdbc:h2:mem:push" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        JdbcSource.Drivers drivers = new JdbcSource.Drivers(List.of());
        Driver d = drivers.driver(url);
        try (Connection keep = d.connect(url, new Properties()); Statement st = keep.createStatement()) {
            st.execute("create table orders(id int primary key, region varchar(20), amount decimal(10,2))");
            st.execute("insert into orders values (1, 'North', 10.50), (2, 'South', 20.00), (3, 'North', 5.25), (4, null, 1.00)");
            ServerConfig c = ServerConfig.parse("rachana-server: 1\nproject: { dir: \"${config}\" }\ntables:\n  o:\n    key: ID\n    jdbc: { url: \"" + url + "\" }\n"
                    + "    query: \"select id, region, amount from orders\"\n    max-rows: 3\n", tmp.resolve("rachana-server.yaml"));
            TableSource o = TableSource.of(c.tables().get("o"), drivers);
            o.types(Map.of("ID", "integer", "REGION", "string", "AMOUNT", "decimal"));
            assertEquals(4, o.load().size());
            List<Map<String, Object>> got = new java.util.ArrayList<>();
            o.read(Json.read("{\"op\":\"==\",\"args\":[{\"col\":\"REGION\"},{\"lit\":\"North\"}]}"), got::add);
            assertEquals(2, got.size(), "pushed down: the table has 4 rows and max-rows is 3, so a whole read would have been refused");
            got.clear();
            o.read(Json.read("{\"op\":\"==\",\"args\":[{\"col\":\"REGION\"},{\"lit\":\"x' OR '1'='1\"}]}"), got::add);
            assertEquals(0, got.size(), "an injection attempt is a value that matches nothing");
            got.clear();
            o.read(Json.read("{\"op\":\"==\",\"args\":[{\"col\":\"REGION\\\" OR 1=1 --\"},{\"lit\":\"x\"}]}"), got::add);
            assertEquals(0, got.size(), "a column that is not in the table is not pushed and the engine's own read is refused as too large or filtered");
        } catch (TableSource.ReadFailed e) {
            assertTrue(e.getMessage().contains("max-rows"), e.getMessage());
        }
    }
}
