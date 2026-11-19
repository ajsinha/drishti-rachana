/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.parse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A YAML node that remembers where it was written. Values are {@code Map<String, YamlNode>} (in written order, the
 * first of a repeated key), {@code List<YamlNode>} or a scalar: {@code String}, {@code Long}, {@code BigInteger} (a
 * whole number too large for a {@code long}), {@code Double}, {@code Boolean} or null.
 *
 * @param value the value
 * @param line 1-based line
 * @param column 1-based column, in code points
 */
public record YamlNode(Object value, int line, int column) {

    @SuppressWarnings("unchecked")
    public Map<String, YamlNode> map() {
        return value instanceof Map<?, ?> m ? (Map<String, YamlNode>) m : Map.of();
    }

    @SuppressWarnings("unchecked")
    public List<YamlNode> list() {
        return value instanceof List<?> l ? (List<YamlNode>) l : List.of();
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

    /** The value without positions: {@code LinkedHashMap}, {@code ArrayList} and the scalars. */
    public Object plain() {
        if (value instanceof Map<?, ?>) {
            Map<String, Object> out = new LinkedHashMap<>();
            map().forEach((k, v) -> out.put(k, v.plain()));
            return out;
        }
        if (value instanceof List<?>) {
            List<Object> out = new ArrayList<>();
            list().forEach(v -> out.add(v.plain()));
            return out;
        }
        return value;
    }
}
