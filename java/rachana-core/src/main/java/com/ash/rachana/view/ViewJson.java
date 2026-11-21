/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.view;

import com.ash.rachana.api.Node;
import com.ash.rachana.parse.Json;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Core's canonical writer of the ViewModel (split steps A13, A14): a record is an object of its fields in declaration order, each
 * written unless its {@link Include} rule leaves the value out; lists and maps as they are (map entries in their order); an enum by its
 * name; a {@link Node} as its plain value. {@link #tree} gives the plain tree (maps, lists, texts, numbers, booleans, null),
 * {@link #write} the compact JSON text. Records are read by the generated, non-reflective writers of {@code ViewRecords} (split step A15:
 * the JavaScript build takes no reflection), written by {@code ViewCodeGenerator} in the test sources.
 */
public final class ViewJson {

    private ViewJson() {}

    /** The value as compact JSON. */
    public static String write(Object value) {
        return Json.write(tree(value));
    }

    /** The value as a plain tree. */
    public static Object tree(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Character c) {
            return String.valueOf(c);
        }
        if (value instanceof Enum<?> e) {
            return e.name();
        }
        if (value instanceof Node n) {
            return n.isMissing() ? null : n.unwrap();
        }
        if (value instanceof Optional<?> o) {
            return o.map(ViewJson::tree).orElse(null);
        }
        if (value instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, v) -> out.put(String.valueOf(k), tree(v)));
            return out;
        }
        if (value instanceof Collection<?> l) {
            List<Object> out = new ArrayList<>(l.size());
            l.forEach(v -> out.add(tree(v)));
            return out;
        }
        if (value instanceof Object[] a) {
            List<Object> out = new ArrayList<>(a.length);
            for (Object v : a) {
                out.add(tree(v));
            }
            return out;
        }
        if (value instanceof double[] a) {
            List<Object> out = new ArrayList<>(a.length);
            for (double v : a) {
                out.add(v);
            }
            return out;
        }
        Map<String, Object> record = ViewRecords.tree(value);
        if (record != null) {
            return record;
        }
        throw new IllegalArgumentException("not a ViewModel value: " + value.getClass().getName());
    }

    /** Puts a record's field into its object unless the field's {@link Include} rule leaves the value out (the generated writers). */
    static void put(Map<String, Object> out, String name, Object v, Include.Rule rule) {
        if (!leftOut(rule, v)) {
            out.put(name, tree(v));
        }
    }

    private static boolean leftOut(Include.Rule rule, Object v) {
        return switch (rule) {
            case ALWAYS -> false;
            case NON_NULL -> v == null;
            case NON_EMPTY -> empty(v);
            case NON_DEFAULT -> empty(v) || Boolean.FALSE.equals(v) || v instanceof Number n && n.doubleValue() == 0 && !(v instanceof Double d && d.isNaN());
        };
    }

    private static boolean empty(Object v) {
        return v == null || v instanceof String s && s.isEmpty() || v instanceof Collection<?> c && c.isEmpty() || v instanceof Map<?, ?> m && m.isEmpty()
                || v instanceof Optional<?> o && o.isEmpty() || v instanceof Object[] a && a.length == 0;
    }
}
