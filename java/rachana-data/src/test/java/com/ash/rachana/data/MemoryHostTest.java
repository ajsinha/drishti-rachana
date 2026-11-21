/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ash.rachana.parse.Json;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The in-memory host's answers and the reference server's change stream (the conformance cases run them all: protocol_check.py). */
class MemoryHostTest {

    @SuppressWarnings("unchecked")
    private static MemoryHost board() {
        MemoryHost h = new MemoryHost("/rdp/v1");
        h.put(Json.read("{\"name\":\"board\",\"key\":[\"id\"],\"columns\":[{\"name\":\"id\",\"type\":\"string\"},{\"name\":\"px\",\"type\":\"decimal\"}],"
                + "\"can\":[\"page\",\"sort\",\"filter\",\"changes\",\"replay\",\"count\"]}"),
                (List<Map<String, Object>>) Json.read("[{\"id\":\"a\",\"px\":\"1.50\"},{\"id\":\"b\",\"px\":\"0.75\"},{\"id\":\"c\",\"px\":null}]"), "e1");
        return h;
    }

    @Test
    void pagesFollowTheCursorToTheEnd() {
        MemoryHost h = board();
        MemoryHost.Answer a = h.handle("GET", "/tables/board/rows", Map.of("limit", "2", "sort", "-px"), null);
        assertEquals(200, a.status());
        Map<?, ?> page = (Map<?, ?>) a.body();
        assertEquals("[[\"a\",\"1.50\"],[\"b\",\"0.75\"]]", Json.write(page.get("values")));
        assertEquals(3L, page.get("total"));
        Map<?, ?> rest = (Map<?, ?>) h.handle("GET", "/tables/board/rows", Map.of("limit", "2", "sort", "-px", "cursor", (String) page.get("next")),
                null).body();
        assertEquals("[[\"c\",null]]", Json.write(rest.get("values")));
        assertTrue(!rest.containsKey("next"));
    }

    @Test
    void aFilterIsRachanaElsMeaning() {
        Map<?, ?> page = (Map<?, ?>) board().handle("POST", "/tables/board/query", Map.of(),
                Json.read("{\"filter\":{\"op\":\"!=\",\"args\":[{\"col\":\"px\"},{\"lit\":\"0.75\",\"type\":\"decimal\"}]},\"columns\":[\"id\"]}")).body();
        assertEquals("[[\"a\"],[\"c\"]]", Json.write(page.get("values")));     // empty != 0.75 is true (SPEC 4.6)
    }

    @Test
    void theStreamSendsTheBacklogThenLiveFrames() throws Exception {
        try (ReferenceServer s = new ReferenceServer(0, "/rdp/v1", null)) {
            board().handle("GET", "/", Map.of(), null);
            s.host().put(Json.read("{\"name\":\"t\",\"key\":[\"k\"],\"columns\":[{\"name\":\"k\",\"type\":\"string\"}],\"can\":[\"changes\",\"replay\"]}"),
                    List.of(), "e1");
            s.host().publish(Map.of("table", "t", "add", List.of(Map.of("k", "x"))));
            HttpResponse<java.io.InputStream> r = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(s.url() + "/changes?table=t&after=e1:0")).build(), HttpResponse.BodyHandlers.ofInputStream());
            assertEquals(200, r.statusCode());
            try (BufferedReader in = new BufferedReader(new InputStreamReader(r.body(), StandardCharsets.UTF_8))) {
                assertEquals("id: e1:1", in.readLine());
                assertEquals("event: msg", in.readLine());
                assertTrue(in.readLine().contains("\"seq\":1"));
                s.host().publish(Map.of("table", "t", "remove", List.of(List.of("x"))));
                String line;
                while ((line = in.readLine()) != null && !line.startsWith("id: ")) {
                    // the heartbeat and the blank line
                }
                assertEquals("id: e1:2", line);
            }
        }
    }
}
