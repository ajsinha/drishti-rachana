/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.parse;

import java.util.List;
import java.util.Map;

/**
 * A YAML node that remembers where it was written. Values are {@code Map<String, PNode>},
 * {@code List<PNode>} or a scalar ({@code String}, {@code Long}, {@code Double}, {@code Boolean}, null).
 *
 * @param value the value
 * @param line 1-based line
 * @param column 1-based column
 */
public record PNode(Object value, int line, int column) {

    @SuppressWarnings("unchecked")
    public Map<String, PNode> map() {
        return value instanceof Map<?, ?> m ? (Map<String, PNode>) m : Map.of();
    }

    @SuppressWarnings("unchecked")
    public List<PNode> list() {
        return value instanceof List<?> l ? (List<PNode>) l : List.of();
    }

    public boolean isMap() {
        return value instanceof Map;
    }

    public boolean isList() {
        return value instanceof List;
    }

    public boolean isScalar() {
        return !isMap() && !isList();
    }

    public String text() {
        return value == null || !isScalar() ? null : value.toString();
    }
}
