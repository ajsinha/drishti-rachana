/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana;

import com.ash.rachana.api.Node;
import com.ash.rachana.parse.Json;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Test helper: JSON text as a {@link Node} tree, read by Core's {@link Json}. */
public final class JsonCodec {

    public Node read(String json) {
        if (json.isBlank()) {
            return Node.missing();
        }
        try {
            return node(Json.read(json));
        } catch (Json.JsonException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private static Node node(Object v) {
        if (v == null) {
            return Node.nullValue();
        }
        if (v instanceof Map<?, ?> m) {
            Map<String, Node> fields = new LinkedHashMap<>();
            m.forEach((k, x) -> fields.put((String) k, node(x)));
            return Node.of(fields);
        }
        if (v instanceof List<?> l) {
            ArrayList<Node> items = new ArrayList<>();
            l.forEach(x -> items.add(node(x)));
            return Node.list(items);
        }
        return Node.of(v);
    }
}
