/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ash.rachana.parse.Json;
import com.ash.rachana.table.KeyedRows;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

/** Subscriptions (PROTOCOL 8.2 to 8.4, 12): the bridge's sub/msg, filtered restating, conflation merged by key, and the JVM client. */
class SubscriptionTest {

    static final String SCHEMA = "{\"name\":\"t\",\"key\":[\"id\"],\"columns\":[{\"name\":\"id\",\"type\":\"string\"},{\"name\":\"n\",\"type\":\"integer\"}],"
            + "\"can\":[\"page\",\"filter\",\"changes\",\"changes.filter\",\"replay\"]}";

    @SuppressWarnings("unchecked")
    static MemoryHost host() {
        MemoryHost h = new MemoryHost("");
        h.put(Json.read(SCHEMA), (List<Map<String, Object>>) Json.read("[{\"id\":\"a\",\"n\":1},{\"id\":\"b\",\"n\":5}]"), "e1");
        return h;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> patch(String json) {
        return (Map<String, Object>) Json.read(json);
    }

    @Test
    void mergedPatchesHaveTheEffectOfThePatchesOneByOne() {
        List<Map<String, Object>> patches = List.of(patch("{\"table\":\"t\",\"update\":[{\"id\":\"a\",\"n\":2}]}"),
                patch("{\"table\":\"t\",\"add\":[{\"id\":\"c\",\"n\":9}],\"remove\":[[\"b\"]]}"),
                patch("{\"table\":\"t\",\"remove\":[[\"c\"]],\"add\":[{\"id\":\"b\",\"n\":7}]}"),
                patch("{\"table\":\"t\",\"update\":[{\"id\":\"a\",\"n\":3}]}"));
        KeyedRows one = new KeyedRows(List.of("id"), rows(), 0);
        patches.forEach(one::apply);
        KeyedRows merged = new KeyedRows(List.of("id"), rows(), 0);
        Map<String, Object> m = Subscription.merge("t", List.of("id"), patches);
        merged.apply(m);
        assertEquals("{\"table\":\"t\",\"remove\":[[\"b\"],[\"c\"]],\"update\":[{\"id\":\"a\",\"n\":3},{\"id\":\"b\",\"n\":7}]}", Json.write(m));
        assertEquals(sorted(one), sorted(merged));
        Map<String, Object> r = Subscription.merge("t", List.of("id"), List.of(patch("{\"table\":\"t\",\"add\":[{\"id\":\"x\",\"n\":0}]}"),
                patch("{\"table\":\"t\",\"reset\":[{\"id\":\"a\",\"n\":1}]}"), patch("{\"table\":\"t\",\"update\":[{\"id\":\"a\",\"n\":4}]}")));
        assertEquals("{\"table\":\"t\",\"reset\":[{\"id\":\"a\",\"n\":4}]}", Json.write(r));
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> rows() {
        return (List<Map<String, Object>>) Json.read("[{\"id\":\"a\",\"n\":1},{\"id\":\"b\",\"n\":5}]");
    }

    static String sorted(KeyedRows r) {
        List<String> s = new ArrayList<>();
        r.rows().forEach(x -> s.add(Json.write(x)));
        s.sort(null);
        return String.join(",", s);
    }

    @Test
    void aBridgeChannelSubscribesFiltersAndUnsubscribes() {
        MemoryHost h = host();
        List<Map<String, Object>> pushed = new CopyOnWriteArrayList<>();
        try (MemoryHost.Bridge b = h.bridge(pushed::add)) {
            Map<String, Object> res = b.handle(patch("{\"rdp\":1,\"t\":\"sub\",\"id\":\"s1\",\"table\":\"t\","
                    + "\"filter\":{\"op\":\">\",\"args\":[{\"col\":\"n\"},{\"lit\":2}]}}"));
            assertEquals("{\"rdp\":1,\"t\":\"res\",\"re\":\"s1\",\"body\":{\"recovered\":true,\"version\":{\"epoch\":\"e1\",\"seq\":0}}}", Json.write(res));
            h.publish(patch("{\"table\":\"t\",\"update\":[{\"id\":\"a\",\"n\":3},{\"id\":\"b\",\"n\":1}]}"));
            assertEquals("{\"rdp\":1,\"t\":\"msg\",\"sub\":\"s1\",\"table\":\"t\",\"epoch\":\"e1\",\"seq\":1,\"data\":{\"table\":\"t\","
                    + "\"remove\":[[\"b\"]],\"update\":[{\"id\":\"a\",\"n\":3}]}}", Json.write(pushed.get(0)));
            b.handle(patch("{\"rdp\":1,\"t\":\"unsub\",\"id\":\"s1\"}"));
            h.publish(patch("{\"table\":\"t\",\"update\":[{\"id\":\"a\",\"n\":4}]}"));
            assertEquals(1, pushed.size());
        }
        assertEquals("rdp.query.unsupported", ((Map<?, ?>) h.bridge(patch("{\"rdp\":1,\"t\":\"sub\",\"id\":\"s1\",\"table\":\"t\"}"))
                .get("error")).get("code"));
    }

    @Test
    void aConflatedSubscriptionSendsOneMergedMessagePerWindow() throws Exception {
        MemoryHost h = host();
        List<Map<String, Object>> got = new CopyOnWriteArrayList<>();
        try (Subscription s = h.subscribe("t", "e1:0", null, 150, got::add)) {
            assertTrue(s.recovered());
            for (int i = 2; i <= 6; i++) {
                h.publish(patch("{\"table\":\"t\",\"update\":[{\"id\":\"a\",\"n\":" + i + "}]}"));
            }
            assertEquals(0, got.size());
            long until = System.currentTimeMillis() + 5000;
            while (got.isEmpty() && System.currentTimeMillis() < until) {
                Thread.sleep(20);
            }
            assertEquals("{\"t\":\"msg\",\"topic\":\"rdp.t\",\"stream\":\"rdp.t\",\"epoch\":\"e1\",\"seq\":5,\"kind\":\"rows\",\"data\":"
                    + "{\"table\":\"t\",\"update\":[{\"id\":\"a\",\"n\":6}]},\"first\":1}", Json.write(got.get(0)));
        }
        assertFalse(h.subscribe("t", "e1:0", Json.read("{\"op\":\"==\",\"args\":[{\"col\":\"id\"},{\"lit\":\"a\"}]}"), 0, f -> { }).recovered());
    }

    @Test
    void theJvmClientFollowsAFilteredStream() throws Exception {
        try (ReferenceServer s = new ReferenceServer(0, "/rdp/v1", null)) {
            s.host().put(Json.read(SCHEMA), rows(), "e1");
            TableClient c = new TableClient(null, s.url());
            List<Map<String, Object>> got = new CopyOnWriteArrayList<>();
            Thread reader = Thread.ofVirtual().start(() -> {
                try {
                    c.follow("t", "e1", 0, Json.read("{\"op\":\">\",\"args\":[{\"col\":\"n\"},{\"lit\":2}]}"), 0, f -> got.add(f) && got.size() < 2);
                } catch (Exception e) {
                    got.add(Map.of("error", String.valueOf(e)));
                }
            });
            long until = System.currentTimeMillis() + 5000;
            Thread.sleep(200);
            s.host().publish(patch("{\"table\":\"t\",\"update\":[{\"id\":\"a\",\"n\":3}]}"));
            s.host().publish(patch("{\"table\":\"t\",\"update\":[{\"id\":\"b\",\"n\":0}]}"));
            reader.join(Math.max(1, until - System.currentTimeMillis()));
            assertEquals("{\"table\":\"t\",\"update\":[{\"id\":\"a\",\"n\":3}]}", Json.write(got.get(0).get("data")));
            assertEquals("{\"table\":\"t\",\"remove\":[[\"b\"]]}", Json.write(got.get(1).get("data")));
        }
    }
}
