/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Core's JSON: the reader's values and refusals, and the writer's bytes (as the reference's JSON wrote them; tools/yaml-diff checks both). */
class JsonTest {

    @Test
    void readsPlainValues() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("a", 2L);
        m.put("b", List.of(1L, 2.5, true, "xé\n"));
        m.put("c", null);
        assertEquals(m, Json.read(" {\"a\":1,\"b\":[1,2.5,true,\"x\\u00e9\\n\"],\"c\":null,\"a\":2} \n"));
        assertNull(Json.read("  "));
        assertEquals(Long.MIN_VALUE, Json.read("-9223372036854775808"));
        assertEquals(new BigInteger("9223372036854775808"), Json.read("9223372036854775808"));
    }

    @Test
    void refusesWhatIsNotJson() {
        assertThrows(Json.JsonException.class, () -> Json.read("[1, 2"));
        assertThrows(Json.JsonException.class, () -> Json.read("{a:1}"));
        assertThrows(Json.JsonException.class, () -> Json.read("01"));
        Json.JsonException e = assertThrows(Json.JsonException.class, () -> Json.read("{\n  \"a\" 1}"));
        assertEquals(2, e.line());
        // the whole text is one value: what follows it is refused at its place, never dropped (G4 gate F25)
        Json.JsonException after = assertThrows(Json.JsonException.class, () -> Json.read("{\"a\":1} trailing"));
        assertEquals(1, after.line());
        assertThrows(Json.JsonException.class, () -> Json.read("[1] [2]"));
        assertThrows(Json.JsonException.class, () -> Json.read("1 2"));
    }

    @Test
    void writesCompactJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("s", "tab\t\"q\" \\ / \u0001 \u007f é");
        m.put("n", List.of(1L, 0.1, 1e21, -0.0, Double.NaN, new BigInteger("123456789012345678901234567890")));
        m.put("z", null);
        assertEquals("{\"s\":\"tab\\t\\\"q\\\" \\\\ / \\u0001 \u007f é\",\"n\":[1,0.1,1.0E21,-0.0,\"NaN\",123456789012345678901234567890],\"z\":null}",
                Json.write(m));
    }
}
