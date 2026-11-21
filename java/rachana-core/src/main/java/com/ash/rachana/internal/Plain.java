/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.internal;

import com.ash.rachana.parse.PositionalYamlReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reading Core's own data files (the function catalogue, formats, world regions) as plain values, with the lenient
 * accessors those readers were written against: a missing key reads as its default, text and numbers convert where
 * the file's author could write either. Static, stateless, thread-safe.
 */
public final class Plain {

    /** Stands for a key that is not there (as opposed to a key whose value is null). */
    public static final Object MISSING = new Object() {
        @Override
        public String toString() {
            return "(missing)";
        }
    };

    private Plain() {}

    /** A YAML text as plain values (maps in written order, lists, scalars). */
    public static Object yaml(String text) {
        return new PositionalYamlReader().read(text).plain();
    }

    /** A YAML stream (UTF-8) as plain values. */
    public static Object yaml(InputStream in) throws IOException {
        return yaml(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    }

    /** The value under a key of a map, or {@link #MISSING}. */
    public static Object path(Object node, String key) {
        return node instanceof Map<?, ?> m && m.containsKey(key) ? m.get(key) : MISSING;
    }

    public static boolean has(Object node, String key) {
        return path(node, key) != MISSING;
    }

    public static boolean hasNonNull(Object node, String key) {
        Object v = path(node, key);
        return v != MISSING && v != null;
    }

    /** The items of a list or the values of a map; nothing for anything else. */
    public static Collection<?> elements(Object node) {
        if (node instanceof List<?> l) {
            return l;
        }
        if (node instanceof Map<?, ?> m) {
            return m.values();
        }
        return List.of();
    }

    /** The entries of a map; nothing for anything else. */
    @SuppressWarnings("unchecked")
    public static Set<Map.Entry<String, Object>> entries(Object node) {
        return node instanceof Map<?, ?> m ? ((Map<String, Object>) m).entrySet() : Set.of();
    }

    /** A scalar as text: {@code ""} when missing or a collection, {@code "null"} for null, else the value's text. */
    public static String text(Object v) {
        if (v == MISSING || v instanceof Map || v instanceof List) {
            return "";
        }
        return String.valueOf(v);
    }

    /** As {@link #text(Object)}, the default when missing or null. */
    public static String text(Object v, String otherwise) {
        return v == MISSING || v == null ? otherwise : text(v);
    }

    public static int asInt(Object v, int otherwise) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v instanceof Boolean b) {
            return b ? 1 : 0;
        }
        if (v instanceof String s) {
            String t = s.trim();
            try {
                return Integer.parseInt(t);
            } catch (NumberFormatException e) {
                try {
                    return CoreMath.toInt(NumberText.parse(t));
                } catch (NumberFormatException e2) {
                    return otherwise;
                }
            }
        }
        return otherwise;
    }

    public static double asDouble(Object v, double otherwise) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v instanceof Boolean b) {
            return b ? 1.0 : 0.0;
        }
        if (v instanceof String s) {
            try {
                return NumberText.parse(s.trim());
            } catch (NumberFormatException e) {
                return otherwise;
            }
        }
        return otherwise;
    }

    public static boolean asBoolean(Object v, boolean otherwise) {
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof String s) {
            String t = s.trim();
            if ("true".equals(t)) {
                return true;
            }
            if ("false".equals(t)) {
                return false;
            }
            return otherwise;
        }
        if (v instanceof Long || v instanceof Integer) {
            return ((Number) v).longValue() != 0;
        }
        return otherwise;
    }
}
