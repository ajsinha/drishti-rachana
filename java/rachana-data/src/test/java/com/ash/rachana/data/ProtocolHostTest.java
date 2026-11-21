/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ash.rachana.parse.Json;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The protocol's routes and bridge over several providers (LIVE_TABLES step L2): one host, each table answered by its own provider. */
class ProtocolHostTest {

    private static MemoryHost host(String table, String rows) {
        MemoryHost h = new MemoryHost("");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> r = (List<Map<String, Object>>) Json.read(rows);
        h.put(Json.read("{\"name\":\"" + table + "\",\"key\":[\"id\"],\"columns\":[{\"name\":\"id\",\"type\":\"string\"},{\"name\":\"n\",\"type\":\"integer\"}],"
                + "\"can\":[\"page\",\"sort\",\"count\",\"changes\",\"replay\"]}"), r, "e1");
        return h;
    }

    @Test
    void eachTableIsAnsweredByItsProviderAndTheListingIsEveryProviders() {
        MemoryHost a = host("a", "[{\"id\":\"x\",\"n\":1}]");
        MemoryHost b = host("b", "[{\"id\":\"y\",\"n\":2},{\"id\":\"z\",\"n\":3}]");
        ProtocolHost p = new ProtocolHost("/rdp/v1", a).add(b);
        Map<?, ?> list = (Map<?, ?>) p.handle("GET", "/tables", Map.of(), null).body();
        assertEquals("[\"a\",\"b\"]", Json.write(((List<?>) list.get("tables")).stream().map(t -> ((Map<?, ?>) t).get("name")).toList()));
        Map<?, ?> page = (Map<?, ?>) p.handle("GET", "/tables/b/rows", Map.of("sort", "-n", "shape", "objects"), null).body();
        assertEquals("[{\"id\":\"z\",\"n\":3},{\"id\":\"y\",\"n\":2}]", Json.write(page.get("rows")));
        assertEquals(2L, page.get("total"));
        assertEquals(404, p.handle("GET", "/tables/c", Map.of(), null).status());
    }

    @Test
    void aDocumentIsCheckedBeforeAProviderSeesIt() {
        List<Object> seen = new ArrayList<>();
        MemoryHost a = host("a", "[]");
        TableProvider spy = new TableProvider() {
            public boolean has(String n) { return a.has(n); }
            public List<Map<String, Object>> tables(Object c) { return a.tables(c); }
            public com.ash.rachana.table.TableSchema schema(String n, Object c) { return a.schema(n, c); }
            public Map<String, Object> schemaDoc(String n, Object c) { return a.schemaDoc(n, c); }
            public Map<String, Object> version(String n, Object c) { return a.version(n, c); }
            public Map<String, Object> rows(String n, Map<String, Object> q, Object c) { seen.add(c); return a.rows(n, q, c); }
            public List<Map<String, Object>> changes(String n, String after, Object c) { return a.changes(n, after, c); }
            public Subscription subscribe(String n, Subscribe r, java.util.function.Consumer<Map<String, Object>> s, Object c) {
                return a.subscribe(n, r, s, c);
            }
        };
        ProtocolHost p = new ProtocolHost("", spy);
        assertEquals(422, p.handle("me", "POST", "/tables/a/query", Map.of(), Json.read("{\"group\":{\"by\":[\"id\"]}}")).status());
        assertEquals(422, p.handle("me", "POST", "/tables/a/query", Map.of(), Json.read("{\"nope\":1}")).status());
        assertTrue(seen.isEmpty(), "the provider saw a document the host refuses");
        assertEquals(200, p.handle("me", "POST", "/tables/a/query", Map.of(), Map.of()).status());
        assertEquals(List.of("me"), seen, "the caller reaches the provider");
    }

    @Test
    void aMemoryTableRefusesAWindowedSubscription() {
        MemoryHost a = host("a", "[]");
        ProtocolError e = assertThrows(ProtocolError.class, () -> a.protocol().subscribe("a",
                new TableProvider.Subscribe(null, null, 0, List.of(List.of("x")), false), f -> { }, null));
        assertEquals("rdp.query.unsupported", e.code());
    }

    @Test
    void theBridgeSubscribesThroughTheTablesProvider() {
        MemoryHost a = host("a", "[]");
        MemoryHost b = host("b", "[]");
        ProtocolHost p = new ProtocolHost("", a, b);
        List<Map<String, Object>> out = new ArrayList<>();
        try (ProtocolHost.Bridge br = p.bridge(out::add)) {
            Map<String, Object> res = br.handle(Map.of("rdp", 1L, "t", "sub", "id", "s1", "table", "b"));
            assertEquals("{\"recovered\":true,\"version\":{\"epoch\":\"e1\",\"seq\":0}}", Json.write(res.get("body")));
            b.publish(Map.of("table", "b", "add", List.of(Map.of("id", "q", "n", 1L))));
            a.publish(Map.of("table", "a", "add", List.of(Map.of("id", "q", "n", 1L))));
            assertEquals(1, out.size());
            assertEquals("b", out.get(0).get("table"));
        }
    }
}
