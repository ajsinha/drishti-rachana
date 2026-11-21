/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ash.rachana.parse.Json;
import com.ash.rachana.table.TableSchema;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Arrow IPC and NDJSON (PROTOCOL 5.2, 5.3): every type through both forms gives back the JSON page; the client reads all three alike. */
class RowFormsTest {

    static final String SCHEMA = "{\"name\":\"all\",\"key\":[\"id\"],\"can\":[\"page\",\"sort\",\"filter\",\"arrow\",\"count\"],\"columns\":["
            + "{\"name\":\"id\",\"type\":\"string\"},{\"name\":\"n\",\"type\":\"integer\"},{\"name\":\"amt\",\"type\":\"decimal\",\"scale\":2},"
            + "{\"name\":\"x\",\"type\":\"number\"},{\"name\":\"ok\",\"type\":\"boolean\"},{\"name\":\"d\",\"type\":\"date\"},"
            + "{\"name\":\"ts\",\"type\":\"timestamp\"},{\"name\":\"tm\",\"type\":\"time\"},{\"name\":\"tags\",\"type\":\"list\",\"items\":\"string\"},"
            + "{\"name\":\"o\",\"type\":\"object\"},{\"name\":\"sal\",\"type\":\"decimal\",\"fmt\":\"amount0\"}]}";
    static final String ROWS = "[{\"id\":\"a\",\"n\":1,\"amt\":\"1250.50\",\"x\":0.25,\"ok\":true,\"d\":\"2026-11-17\","
            + "\"ts\":\"2026-11-17T09:30:00.000Z\",\"tm\":\"09:30:00\",\"tags\":[\"p\",\"q\"],\"o\":{\"k\":1},\"sal\":{\"$masked\":true}},"
            + "{\"id\":\"b\",\"n\":\"9007199254740993\",\"amt\":null,\"x\":null,\"ok\":false,\"d\":null,\"ts\":null,\"tm\":\"23:59:59.250\","
            + "\"tags\":null,\"o\":null,\"sal\":\"3.5\"}]";

    @SuppressWarnings("unchecked")
    static Map<String, Object> page() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("table", "all");
        p.put("version", Map.of("epoch", "t1", "seq", 7L));
        List<Map<String, Object>> rows = (List<Map<String, Object>>) Json.read(ROWS);
        List<String> cols = TableSchema.of(Json.read(SCHEMA)).names();
        List<Object> values = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            List<Object> v = new ArrayList<>();
            cols.forEach(c -> v.add(r.get(c)));
            values.add(v);
        }
        p.put("columns", cols);
        p.put("values", values);
        p.put("total", 2L);
        p.put("next", "c-1");
        return p;
    }

    @Test
    void everyTypeRoundTripsThroughArrowInSmallBatches() {
        Map<String, Object> p = page();
        byte[] bytes = ArrowRows.write(p, TableSchema.of(Json.read(SCHEMA)), 1);
        Map<String, Object> back = ArrowRows.read(new ByteArrayInputStream(bytes));
        assertEquals(Json.write(p.get("columns")), Json.write(back.get("columns")));
        assertEquals("[[\"a\",1,\"1250.50\",0.25,true,\"2026-11-17\",\"2026-11-17T09:30:00.000Z\",\"09:30:00\",[\"p\",\"q\"],{\"k\":1},{\"$masked\":true}],"
                + "[\"b\",\"9007199254740993\",null,null,false,null,null,\"23:59:59.250\",null,null,\"3.5\"]]", Json.write(back.get("values")));
        assertEquals("{\"epoch\":\"t1\",\"seq\":7}", Json.write(back.get("version")));
        assertEquals(2L, back.get("total"));
        assertEquals("c-1", back.get("next"));
    }

    @Test
    void ndjsonRoundTrips() throws Exception {
        Map<String, Object> p = page();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        NdjsonRows.write(p, out);
        String text = out.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(3, text.split("\n").length);
        assertEquals(Json.write(p), Json.write(NdjsonRows.read(new ByteArrayInputStream(out.toByteArray()))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void theClientReadsTheSameRowsInEveryForm() throws Exception {
        try (ReferenceServer s = new ReferenceServer(0, "/rdp/v1", null)) {
            s.host().put(Json.read(SCHEMA), (List<Map<String, Object>>) Json.read(ROWS), "e1");
            TableClient c = new TableClient(null, s.url());
            Map<String, Object> q = Map.of("sort", List.of(Map.of("col", "id")), "limit", 1L);
            String json = Json.write(c.read("all", q, TableClient.Form.JSON).rows());
            assertEquals(json, Json.write(c.read("all", q, TableClient.Form.NDJSON).rows()));
            assertEquals(json, Json.write(c.read("all", q, TableClient.Form.ARROW).rows()));
            s.host().put(Json.read(SCHEMA.replace("\"arrow\",", "")), (List<Map<String, Object>>) Json.read(ROWS), "e1");
            assertEquals(json, Json.write(c.read("all", q, TableClient.Form.ARROW).rows()));   // not offered: the client reads JSON
            java.net.http.HttpResponse<String> r = java.net.http.HttpClient.newHttpClient().send(java.net.http.HttpRequest.newBuilder(
                    java.net.URI.create(s.url() + "/tables/all/rows")).header("Accept", ArrowRows.MEDIA).build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(406, r.statusCode());
        }
    }
}
