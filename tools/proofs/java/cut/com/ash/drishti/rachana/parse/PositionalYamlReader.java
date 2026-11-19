/*
 * Copyright (c) 2026 Ashutosh Sinha. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.drishti.rachana.parse;

import com.ash.proof.Json;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PROOF CUT C4: the Jackson-based reader is replaced by a reader of the tree the reference's own reader produced (the prep step runs
 * Drishti's PositionalYamlReader on the JVM and writes {@code {"$pnode": node, "issues": [[message, line, column]…]}}). Same
 * signatures, same PNode values (Map, List, String, Long, Double, BigInteger, Boolean, null), same issues.
 */
public final class PositionalYamlReader {

    public record Issue(String message, int line, int column) {}

    public PNode read(String yaml) throws IOException {
        return read(yaml, new ArrayList<>());
    }

    @SuppressWarnings("unchecked")
    public PNode read(String yaml, List<Issue> issues) throws IOException {
        Map<String, Object> doc = (Map<String, Object>) Json.parse(yaml);
        for (Object o : (List<Object>) doc.get("issues")) {
            List<Object> i = (List<Object>) o;
            issues.add(new Issue((String) i.get(0), ((Long) i.get(1)).intValue(), ((Long) i.get(2)).intValue()));
        }
        return node(doc.get("$pnode"));
    }

    @SuppressWarnings("unchecked")
    private static PNode node(Object o) {
        Map<String, Object> n = (Map<String, Object>) o;
        int line = ((Long) n.get("l")).intValue();
        int col = ((Long) n.get("c")).intValue();
        if (n.containsKey("m")) {
            Map<String, PNode> m = new LinkedHashMap<>();
            ((Map<String, Object>) n.get("m")).forEach((k, v) -> m.put(k, node(v)));
            return new PNode(m, line, col);
        }
        if (n.containsKey("a")) {
            List<PNode> l = new ArrayList<>();
            for (Object e : (List<Object>) n.get("a")) {
                l.add(node(e));
            }
            return new PNode(l, line, col);
        }
        return new PNode(com.ash.proof.Values.scalar(n.get("v")), line, col);
    }
}
