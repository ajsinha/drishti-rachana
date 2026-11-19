/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.hostcompat;

import com.ash.rachana.parse.Json;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Test helper: JSON text as a {@link DataNode} tree, read by Core's {@link Json}. */
public final class JsonCodec {

    public DataNode read(String json) {
        if (json.isBlank()) {
            return DataNode.missing();
        }
        try {
            return node(Json.read(json));
        } catch (Json.JsonException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private static DataNode node(Object v) {
        if (v == null) {
            return DataNode.nullValue();
        }
        if (v instanceof Map<?, ?> m) {
            Map<String, DataNode> fields = new LinkedHashMap<>();
            m.forEach((k, x) -> fields.put((String) k, node(x)));
            return new DataNode.Obj(fields);
        }
        if (v instanceof List<?> l) {
            ArrayList<DataNode> items = new ArrayList<>();
            l.forEach(x -> items.add(node(x)));
            return new DataNode.Arr(items);
        }
        return new DataNode.Val(v);
    }
}
