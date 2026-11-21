/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.tools;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Writes a tree of maps, lists, strings, numbers, booleans and nulls as the language files are written: two-space indent, {@code "key" : value},
 * object keys in name order (every map, as the reference's generator sorted them), lists on one line as {@code [ a, b ]} (an empty one
 * {@code [ ]}), an object inside a list opening on the list's line, and a final newline. The same bytes as the first generator wrote.
 */
final class LangJson {

    private LangJson() {}

    static String write(Object tree) {
        StringBuilder out = new StringBuilder();
        value(out, tree, 0);
        return out.append('\n').toString();
    }

    private static void value(StringBuilder out, Object v, int depth) {
        if (v == null) {
            out.append("null");
        } else if (v instanceof Map<?, ?> m) {
            object(out, m, depth);
        } else if (v instanceof Collection<?> c) {
            list(out, c, depth);
        } else if (v instanceof Boolean || v instanceof Integer || v instanceof Long) {
            out.append(v);
        } else if (v instanceof Number n) {
            out.append(n.doubleValue() == Math.rint(n.doubleValue()) && !Double.isInfinite(n.doubleValue()) ? String.valueOf(n.longValue()) : String.valueOf(n));
        } else {
            string(out, String.valueOf(v));
        }
    }

    private static void object(StringBuilder out, Map<?, ?> m, int depth) {
        if (m.isEmpty()) {
            out.append("{ }");
            return;
        }
        Map<String, Object> sorted = new TreeMap<>();
        m.forEach((k, x) -> sorted.put(String.valueOf(k), x));
        out.append("{\n");
        boolean first = true;
        for (Map.Entry<String, Object> e : sorted.entrySet()) {
            if (!first) {
                out.append(",\n");
            }
            first = false;
            out.append("  ".repeat(depth + 1));
            string(out, e.getKey());
            out.append(" : ");
            value(out, e.getValue(), depth + 1);
        }
        out.append('\n').append("  ".repeat(depth)).append('}');
    }

    private static void list(StringBuilder out, Collection<?> c, int depth) {
        if (c.isEmpty()) {
            out.append("[ ]");
            return;
        }
        List<Object> items = new ArrayList<>(c);
        out.append("[ ");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            value(out, items.get(i), depth);
        }
        out.append(" ]");
    }

    private static void string(StringBuilder out, String s) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (ch < 0x20) {
                        out.append(String.format("\\u%04X", (int) ch));
                    } else {
                        out.append(ch);
                    }
                }
            }
        }
        out.append('"');
    }
}
