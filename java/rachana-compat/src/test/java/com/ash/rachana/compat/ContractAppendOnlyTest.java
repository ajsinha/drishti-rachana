/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.compat;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * CG4, the contract is append-only (SUTRA_EXPANSION 6.3, 6.5). For every released schema {@code spec/1/released/viewmodel-<minor>.schema.json}
 * the schema of this commit holds every definition, property and enum word with the same type, requires nothing new, and keeps the old
 * properties in their canonical order. The released files are written by {@code tools/regime/freeze.py} when a release creates a
 * contract minor. (The golden's validation and re-serialisation to itself is a conformance case of the protocol suite.)
 */
class ContractAppendOnlyTest {

    /** What changed incompatibly from {@code released} to {@code current}: empty when current only adds. */
    static List<String> breaks(Object released, Object current) { 
        List<String> out = new ArrayList<>();
        walk("#", released, current, out);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void walk(String at, Object r, Object c, List<String> out) {
        if (r instanceof Map<?, ?> rm) {
            if (!(c instanceof Map<?, ?> cm)) { out.add(at + ": was an object, now " + (c == null ? "absent" : c.getClass().getSimpleName())); return; }
            Map<String, Object> rmap = (Map<String, Object>) rm, cmap = (Map<String, Object>) cm;
            if (rmap.containsKey("type") && !Objects.equals(rmap.get("type"), cmap.get("type"))) out.add(at + "/type: " + rmap.get("type") + " is now " + cmap.get("type"));
            if (rmap.get("enum") instanceof List<?> re) {
                Object ce = cmap.get("enum");
                if (!(ce instanceof List<?> cl) || !cl.containsAll(re)) out.add(at + "/enum: a released word is gone");
            }
            Object rr = rmap.get("required"), cr = cmap.get("required");
            Set<Object> was = rr instanceof List<?> l ? new HashSet<>(l) : Set.of();
            if (cr instanceof List<?> l) for (Object n : l) if (!was.contains(n)) out.add(at + "/required: '" + n + "' is newly required");
            if (rmap.get("properties") instanceof Map<?, ?> rp) {
                Map<String, Object> cp = cmap.get("properties") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
                List<String> order = new ArrayList<>(cp.keySet());
                int last = -1;
                for (var e : ((Map<String, Object>) rp).entrySet()) {
                    if (!cp.containsKey(e.getKey())) { out.add(at + "/properties/" + e.getKey() + ": removed"); continue; }
                    int pos = order.indexOf(e.getKey());
                    if (pos < last) out.add(at + "/properties/" + e.getKey() + ": moved before an older property (canonical order)");
                    last = Math.max(last, pos);
                    walk(at + "/properties/" + e.getKey(), e.getValue(), cp.get(e.getKey()), out);
                }
            }
            for (String k : List.of("$defs", "definitions")) {
                if (rmap.get(k) instanceof Map<?, ?> rd) {
                    Map<String, Object> cd = cmap.get(k) instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
                    for (var e : ((Map<String, Object>) rd).entrySet()) {
                        if (!cd.containsKey(e.getKey())) out.add(at + "/" + k + "/" + e.getKey() + ": definition removed");
                        else walk(at + "/" + k + "/" + e.getKey(), e.getValue(), cd.get(e.getKey()), out);
                    }
                }
            }
            for (String k : List.of("items", "additionalProperties")) if (rmap.get(k) instanceof Map<?, ?>) walk(at + "/" + k, rmap.get(k), cmap.get(k), out);
        }
    }

    static Object read(Path p) throws Exception { return Json.parse(Files.readString(p)); }

    @Test
    void everyReleasedContractMinorIsContainedInTheCurrentSchema() throws Exception {
        Path dir = Corpus.repoRoot().resolve("spec/1/released");
        if (!Files.isDirectory(dir)) return;   // none released before v0.9.0
        Object current = read(Corpus.repoRoot().resolve("spec/1/viewmodel.schema.json"));
        try (Stream<Path> s = Files.list(dir)) {
            for (Path f : s.filter(p -> p.getFileName().toString().matches("viewmodel-.*\\.schema\\.json")).sorted().toList())
                assertEquals(List.of(), breaks(read(f), current), f.getFileName() + " is not contained in the current contract (CG4)");
        }
    }

    @Test
    void theCurrentSchemaContainsItself() throws Exception {
        Object current = read(Corpus.repoRoot().resolve("spec/1/viewmodel.schema.json"));
        assertEquals(List.of(), breaks(current, current));
    }

    @Test
    void theCheckSeesEveryKindOfBreak() {
        Object old = Json.parse("{\"type\":\"object\",\"required\":[\"a\"],\"properties\":{\"a\":{\"type\":\"string\",\"enum\":[\"x\",\"y\"]},\"b\":{\"type\":\"number\"}},\"$defs\":{\"D\":{\"type\":\"object\"}}}");
        assertEquals(List.of(), breaks(old, Json.parse("{\"type\":\"object\",\"required\":[\"a\"],\"properties\":{\"a\":{\"type\":\"string\",\"enum\":[\"x\",\"y\",\"z\"]},\"b\":{\"type\":\"number\"},\"c\":{\"type\":\"string\"}},\"$defs\":{\"D\":{\"type\":\"object\"},\"E\":{}}}")), "additions are allowed");
        assertEquals(1, breaks(old, Json.parse("{\"type\":\"object\",\"required\":[\"a\",\"c\"],\"properties\":{\"a\":{\"type\":\"string\",\"enum\":[\"x\",\"y\"]},\"b\":{\"type\":\"number\"},\"c\":{}},\"$defs\":{\"D\":{\"type\":\"object\"}}}")).size(), "a new required property");
        assertEquals(1, breaks(old, Json.parse("{\"type\":\"object\",\"required\":[\"a\"],\"properties\":{\"a\":{\"type\":\"string\",\"enum\":[\"x\"]},\"b\":{\"type\":\"number\"}},\"$defs\":{\"D\":{\"type\":\"object\"}}}")).size(), "a word gone");
        assertEquals(1, breaks(old, Json.parse("{\"type\":\"object\",\"required\":[\"a\"],\"properties\":{\"a\":{\"type\":\"string\",\"enum\":[\"x\",\"y\"]},\"b\":{\"type\":\"string\"}},\"$defs\":{\"D\":{\"type\":\"object\"}}}")).size(), "a type changed");
        assertEquals(1, breaks(old, Json.parse("{\"type\":\"object\",\"required\":[\"a\"],\"properties\":{\"b\":{\"type\":\"number\"},\"a\":{\"type\":\"string\",\"enum\":[\"x\",\"y\"]}},\"$defs\":{\"D\":{\"type\":\"object\"}}}")).size(), "order changed");
        assertEquals(1, breaks(old, Json.parse("{\"type\":\"object\",\"required\":[\"a\"],\"properties\":{\"a\":{\"type\":\"string\",\"enum\":[\"x\",\"y\"]},\"b\":{\"type\":\"number\"}},\"$defs\":{}}")).size(), "a definition removed");
        assertEquals(1, breaks(old, Json.parse("{\"type\":\"object\",\"required\":[\"a\"],\"properties\":{\"a\":{\"type\":\"string\",\"enum\":[\"x\",\"y\"]}},\"$defs\":{\"D\":{\"type\":\"object\"}}}")).size(), "a property removed");
    }
}
