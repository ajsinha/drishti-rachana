/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.api;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The contract of {@link Node} as Rachana's own nodes keep it. */
class NodeTest {

    static Node doc() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", "T-1");
        m.put("n", 3L);
        m.put("ok", true);
        m.put("none", null);
        m.put("legs", List.of(Map.of("pv", 1.5), Map.of("pv", 2L)));
        m.put("cp", Node.masked());
        return Node.of(m);
    }

    @Test
    void kindsAndScalars() {
        Node d = doc();
        assertEquals(Node.Kind.OBJECT, d.kind());
        assertEquals(List.of("id", "n", "ok", "none", "legs", "cp"), List.copyOf(d.fields().keySet()));
        assertEquals("T-1", d.get("id").value());
        assertEquals(Node.Kind.NUMBER, d.get("n").kind());
        assertTrue(d.get("ok").asBoolean());
        assertTrue(d.get("none").isNull());
        assertFalse(d.get("none").isMissing());
        assertTrue(d.get("absent").isMissing());
        assertEquals(Node.Kind.ARRAY, d.get("legs").kind());
        assertTrue(d.get("legs").isArray());
        assertEquals(2, d.get("legs").elements().size());
    }

    @Test
    void navigationNeverThrowsAndAMaskCoversWhatIsUnderIt() {
        Node d = doc();
        assertEquals(1.5, d.at("$.legs[0].pv").value());
        assertEquals(2L, d.at("legs[-1].pv").value());
        assertTrue(d.at("legs[5].pv").isMissing());
        assertTrue(d.at("id.x").isMissing());
        assertTrue(d.at("cp.name").isMasked());
        assertEquals(Node.MASK, d.get("cp").asText());
    }

    @Test
    void ofReturnsANodeAsItIsAndUnwrapGivesPlainJava() {
        Node d = doc();
        assertSame(d, Node.of(d));
        Object plain = d.get("legs").unwrap();
        assertEquals(List.of(Map.of("pv", 1.5), Map.of("pv", 2L)), plain);
        assertEquals(Node.of("a"), Node.of("a"));
        assertTrue(Node.list(List.of(Node.of(1L))).isArray());
    }
}
