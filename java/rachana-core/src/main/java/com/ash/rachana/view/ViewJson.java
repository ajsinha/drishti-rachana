/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.view;

import com.ash.rachana.api.Node;
import com.ash.rachana.parse.Json;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core's canonical writer of the ViewModel (split steps A13, A14): a record is an object of its fields in declaration order, each
 * written unless its {@link Include} rule leaves the value out; lists and maps as they are (map entries in their order); an enum by its
 * name; a {@link Node} as its plain value. {@link #tree} gives the plain tree (maps, lists, texts, numbers, booleans, null),
 * {@link #write} the compact JSON text.
 */
public final class ViewJson {

    private static final Map<Class<?>, RecordComponent[]> COMPONENTS = new ConcurrentHashMap<>();

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
        Class<?> c = value.getClass();
        if (c.isRecord()) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (RecordComponent rc : COMPONENTS.computeIfAbsent(c, Class::getRecordComponents)) {
                Object v;
                try {
                    v = rc.getAccessor().invoke(value);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("cannot read " + c.getSimpleName() + "." + rc.getName(), e);
                }
                if (!leftOut(rule(c, rc), v)) {
                    out.put(rc.getName(), tree(v));
                }
            }
            return out;
        }
        throw new IllegalArgumentException("not a ViewModel value: " + c.getName());
    }

    /** The rule of a record's field: its own, else the record's, else {@link Include.Rule#ALWAYS}. */
    static Include.Rule rule(Class<?> record, RecordComponent rc) {
        Include own = rc.getAccessor().getAnnotation(Include.class);
        if (own == null) {
            own = rc.getAnnotation(Include.class);
        }
        if (own != null) {
            return own.value();
        }
        Include all = record.getAnnotation(Include.class);
        return all == null ? Include.Rule.ALWAYS : all.value();
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
