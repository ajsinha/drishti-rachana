/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The table sources: files of each form, a JDBC query with bound parameters on a read-only connection, a REST answer. */
class SourcesTest {

    @TempDir
    Path tmp;

    ServerConfig config(String tables) throws Exception {
        return ServerConfig.parse("rachana-server: 1\nproject: { dir: \"${config}\" }\ntables:\n" + tables, tmp.resolve("rachana-server.yaml"));
    }

    @Test
    void filesOfEveryFormGiveTheSameRows() throws Exception {
        Files.writeString(tmp.resolve("a.csv"), "id,v\n1,x\n2,y\n");
        Files.writeString(tmp.resolve("a.json"), "[{\"id\":1,\"v\":\"x\"},{\"id\":2,\"v\":\"y\"}]");
        Files.writeString(tmp.resolve("b.json"), "{\"rows\":[{\"id\":1,\"v\":\"x\"},{\"id\":2,\"v\":\"y\"}]}");
        Files.writeString(tmp.resolve("a.jsonl"), "{\"id\":1,\"v\":\"x\"}\n\n{\"id\":2,\"v\":\"y\"}\n");
        ServerConfig c = config("""
                  csv: { file: "${config}/a.csv", key: id }
                  json: { file: "${config}/a.json", key: id }
                  wrapped: { file: "${config}/b.json", key: id }
                  lines: { file: "${config}/a.jsonl", key: id }
                """);
        for (ServerConfig.Table t : c.tables().values()) {
            TableSource s = TableSource.of(t, new JdbcSource.Drivers(List.of()));
            s.types(Map.of("id", "integer", "v", "string"));
            List<Map<String, Object>> got = new java.util.ArrayList<>();
            s.read(null, got::add);
            assertEquals(List.of(Map.of("id", 1L, "v", "x"), Map.of("id", 2L, "v", "y")), got, t.name());
        }
    }

    @Test
    void moreRowsThanMaxRowsIsARefusedRead() throws Exception {
        Files.writeString(tmp.resolve("a.csv"), "id\n1\n2\n3\n");
        ServerConfig c = config("  t: { file: \"${config}/a.csv\", key: id, max-rows: 2 }\n");
        TableSource s = TableSource.of(c.tables().get("t"), new JdbcSource.Drivers(List.of()));
        assertThrows(TableSource.ReadFailed.class, s::rows);
    }

    @Test
    void aJdbcQueryBindsItsParametersAndReadsReadOnly() throws Exception {
        String url = "jdbc:h2:mem:sources" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        JdbcSource.Drivers drivers = new JdbcSource.Drivers(List.of());
        Driver d = drivers.driver(url);
        try (Connection keep = d.connect(url, new Properties()); Statement st = keep.createStatement()) {
            st.execute("create table orders(id int primary key, region varchar(20), amount decimal(10,2))");
            st.execute("insert into orders values (1, 'North', 10.50), (2, 'South', 20.00), (3, 'North', 5.25)");
            ServerConfig c = config("  o:\n    key: id\n    jdbc: { url: \"" + url + "\" }\n"
                    + "    query: \"select id, region, amount from orders where region = :region order by id\"\n"
                    + "    params: { region: \"North' or '1'='1\" }\n"
                    + "  n:\n    key: id\n    jdbc: { url: \"" + url + "\" }\n"
                    + "    query: \"select id, region, amount from orders where region = :region order by id\"\n"
                    + "    params: { region: North }\n");
            TableSource injected = TableSource.of(c.tables().get("o"), drivers);
            assertEquals(0, injected.rows().size(), "a parameter is a value, never SQL");
            TableSource north = TableSource.of(c.tables().get("n"), drivers);
            List<Map<String, Object>> rows = north.rows();
            assertEquals(2, rows.size());
            assertEquals("decimal", Rows.infer(rows, "AMOUNT"));
            north.types(Map.of("ID", "integer", "REGION", "string", "AMOUNT", "decimal"));
            List<Map<String, Object>> shaped = new java.util.ArrayList<>();
            north.read(null, shaped::add);
            assertEquals(Map.of("ID", 1L, "REGION", "North", "AMOUNT", "10.50"), shaped.get(0));
        }
    }

    @Test
    void aFailedQueryNamesTheStateNeverTheText() throws Exception {
        String url = "jdbc:h2:mem:fails" + System.nanoTime();
        ServerConfig c = config("  o:\n    key: id\n    jdbc: { url: \"" + url + "\", password-env: RACHANA_TEST_UNSET_PASSWORD_VARIABLE }\n"
                + "    query: \"select * from missing_table\"\n");
        TableSource.ReadFailed e = assertThrows(TableSource.ReadFailed.class, () -> TableSource.of(c.tables().get("o"),
                new JdbcSource.Drivers(List.of())).rows());
        assertTrue(e.getMessage().contains("RACHANA_TEST_UNSET_PASSWORD_VARIABLE"), e.getMessage());
        ServerConfig c2 = config("  o:\n    key: id\n    jdbc: { url: \"" + url + "\" }\n    query: \"select * from missing_table\"\n");
        e = assertThrows(TableSource.ReadFailed.class, () -> TableSource.of(c2.tables().get("o"), new JdbcSource.Drivers(List.of())).rows());
        assertTrue(e.getMessage().contains("SQL state"), e.getMessage());
        assertFalse(e.getMessage().toLowerCase().contains("missing_table"), e.getMessage());
    }

    @Test
    void aRestSourceReadsTheRowsAtItsPath() throws Exception {
        HttpServer h = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        h.createContext("/quotes", x -> {
            byte[] b = "{\"data\":{\"items\":[{\"symbol\":\"ABC\",\"px\":1.5},{\"symbol\":\"XYZ\",\"px\":2}]}}".getBytes(StandardCharsets.UTF_8);
            x.getResponseHeaders().set("Content-Type", "application/json");
            x.sendResponseHeaders("yes".equals(x.getRequestHeaders().getFirst("X-Test")) ? 200 : 500, b.length);
            x.getResponseBody().write(b);
            x.close();
        });
        h.start();
        try {
            String u = "http://127.0.0.1:" + h.getAddress().getPort() + "/quotes";
            ServerConfig c = config("  q: { key: symbol, rest: { url: \"" + u + "\", path: $.data.items, headers: { X-Test: \"yes\" } } }\n"
                    + "  bad: { key: symbol, rest: { url: \"" + u + "\", path: $.data.items } }\n");
            List<Map<String, Object>> rows = TableSource.of(c.tables().get("q"), null).rows();
            assertEquals(2, rows.size());
            assertEquals("decimal", Rows.infer(rows, "px"));
            TableSource.ReadFailed e = assertThrows(TableSource.ReadFailed.class, () -> TableSource.of(c.tables().get("bad"), null).rows());
            assertTrue(e.getMessage().contains("answered 500"));
        } finally {
            h.stop(0);
        }
    }
}
