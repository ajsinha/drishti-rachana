/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.live;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ash.rachana.data.ProtocolError;
import com.ash.rachana.data.TableClient;
import com.ash.rachana.parse.Json;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The live test adapter over HTTP (PROTOCOL 15, 16): the minimal SSE form's windowed stream ({@code subd} first, then the window's patches
 * and totals), {@code POST /changes/{sub}/window} through {@link TableClient#window} (only the stream's own caller), and the adapter's
 * conventions ({@code /_test/live}, {@code /_test/as}, {@code /_test/stream}).
 */
class LiveReferenceServerTest {

    static final String DECL = """
            rachana: 1
            live-table: orders
            snapshot: s
            stream: f
            key: ID
            version: V
            columns: [ { name: ID, type: string }, { name: V, type: integer }, { name: QTY, type: integer } ]
            """;

    LiveReferenceServer s;
    final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void start() throws Exception {
        s = new LiveReferenceServer(0, "/rdp/v1");
        post("/_test/live", Map.of("declaration", DECL, "rows", List.of(Map.of("ID", "a", "V", 1, "QTY", 1), Map.of("ID", "b", "V", 1, "QTY", 2),
                Map.of("ID", "c", "V", 1, "QTY", 3))));
    }

    @AfterEach
    void stop() {
        s.close();
    }

    int post(String path, Object body) throws Exception {
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(s.url() + path)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.write(body))).build(), HttpResponse.BodyHandlers.ofString());
        return r.statusCode();
    }

    @Test
    @SuppressWarnings("unchecked")
    void aWindowedStreamStartsWithSubdSendsItsKeysAndTotalsAndMovesWithItsWindow() throws Exception {
        TableClient c = new TableClient(http, s.url());
        TableClient.Read read = c.read("orders", Map.of());
        assertEquals(3, read.rows().size());
        BlockingQueue<Map<String, Object>> frames = new LinkedBlockingQueue<>();
        Thread t = Thread.ofVirtual().start(() -> {
            try {
                c.follow("orders", read.epoch(), read.seq(), null, 0, List.of(List.of("a")), false, f -> {
                    frames.add(f);
                    return true;
                });
            } catch (Exception e) {
                frames.add(Map.of("t", "error", "message", String.valueOf(e)));
            }
        });
        Map<String, Object> subd = frames.poll(5, TimeUnit.SECONDS);
        assertEquals("subd", subd.get("t"));
        String sub = String.valueOf(subd.get("sub"));
        assertEquals(204, post("/_test/stream", Map.of("upsert", List.of(Map.of("ID", "b", "V", 2, "QTY", 20)))));
        Map<String, Object> f = frames.poll(5, TimeUnit.SECONDS);
        assertEquals("{\"table\":\"orders\",\"total\":3}", Json.write(f.get("data")), "a row outside the window: the total only");
        assertEquals(204, post("/_test/stream", Map.of("upsert", List.of(Map.of("ID", "a", "V", 2, "QTY", 10)))));
        f = frames.poll(5, TimeUnit.SECONDS);
        assertEquals(List.of(Map.of("ID", "a", "V", 2L, "QTY", 10L)), ((Map<String, Object>) f.get("data")).get("update"));
        c.window(sub, List.of(List.of("b")), false);
        assertEquals(204, post("/_test/stream", Map.of("upsert", List.of(Map.of("ID", "b", "V", 3, "QTY", 30)))));
        f = frames.poll(5, TimeUnit.SECONDS);
        assertEquals(List.of(Map.of("ID", "b", "V", 3L, "QTY", 30L)), ((Map<String, Object>) f.get("data")).get("update"),
                "the window moved to b");
        assertEquals(204, post("/_test/as", Map.of("reader", "eve", "view", Map.of())));
        ProtocolError e = assertThrows(ProtocolError.class, () -> c.window(sub, List.of(List.of("a")), false));
        assertEquals("rdp.table.unknown", e.code(), "another caller may not move the window");
        e = assertThrows(ProtocolError.class, () -> c.window("nope", List.of(), false));
        assertEquals("rdp.table.unknown", e.code());
        t.interrupt();
    }

    @Test
    void aGapEndsAWindowedStreamWithAnUnrecoverableGap() throws Exception {
        TableClient c = new TableClient(http, s.url());
        TableClient.Read read = c.read("orders", Map.of());
        BlockingQueue<Map<String, Object>> frames = new LinkedBlockingQueue<>();
        Thread t = Thread.ofVirtual().start(() -> {
            try {
                c.follow("orders", read.epoch(), read.seq(), null, 0, List.of(List.of("a")), false, f -> {
                    frames.add(f);
                    return true;
                });
                frames.add(Map.of("t", "ended"));
            } catch (Exception e) {
                frames.add(Map.of("t", "error", "message", String.valueOf(e)));
            }
        });
        assertEquals("subd", frames.poll(5, TimeUnit.SECONDS).get("t"));
        assertEquals(204, post("/_test/stream", Map.of("gap", "broker reconnected")));
        Map<String, Object> gap = frames.poll(5, TimeUnit.SECONDS);
        assertEquals("gap", gap.get("t"));
        assertEquals(false, gap.get("recoverable"));
        assertEquals("ended", frames.poll(5, TimeUnit.SECONDS).get("t"), "the stream ends after its gap");
        assertTrue(!t.isAlive() || t.join(java.time.Duration.ofSeconds(2)));
    }
}
