/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.api.internal;

import com.ash.rachana.api.Node;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Rachana's own nodes (behind {@link Node#of(Object)}): immutable objects, lists, scalars and the absent node. */
public final class Nodes {

    private Nodes() {
    }

    /** The absent node. */
    public static final Node MISSING = new Missing();
    /** The JSON null. */
    public static final Node NULL = new Val(null);
    /** The masked value. */
    public static final Node MASKED = new Val(Node.MASK);

    /** A tree of a plain Java value; a node is returned as it is. */
    public static Node of(Object value) {
        if (value instanceof Node n) {
            return n;
        }
        if (value == null) {
            return NULL;
        }
        if (value instanceof Map<?, ?> m) {
            LinkedHashMap<String, Node> fields = new LinkedHashMap<>(Math.max(4, m.size() * 2));
            m.forEach((k, v) -> fields.put(String.valueOf(k), of(v)));
            return new Obj(fields);
        }
        if (value instanceof List<?> l) {
            return new Arr(l.stream().map(Nodes::of).toList());
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof String) {
            return new Val(value);
        }
        return new Val(value.toString());
    }

    /** Follows a dotted path with optional indexes from a node. */
    public static Node at(Node root, String path) {
        if (path == null || path.isEmpty() || "$".equals(path)) {
            return root;
        }
        int i = path.startsWith("$.") ? 2 : path.startsWith("$") ? 1 : 0;
        Node node = root;
        int n = path.length();
        while (i < n && !node.isMissing()) {
            char c = path.charAt(i);
            if (c == '.') {
                i++;
            } else if (c == '[') {
                int end = path.indexOf(']', i);
                if (end < 0) {
                    return MISSING;
                }
                String inner = path.substring(i + 1, end).trim();
                if (!inner.isEmpty() && (inner.charAt(0) == '\'' || inner.charAt(0) == '"')) {
                    node = node.get(inner.substring(1, inner.length() - 1));
                } else {
                    try {
                        node = node.get(Integer.parseInt(inner));
                    } catch (NumberFormatException e) {
                        return MISSING;
                    }
                }
                i = end + 1;
            } else {
                int j = i;
                while (j < n && path.charAt(j) != '.' && path.charAt(j) != '[') {
                    j++;
                }
                node = node.get(path.substring(i, j));
                i = j;
            }
        }
        return node;
    }

    /** An object: field order is the order the source gave. */
    public record Obj(Map<String, Node> fields) implements Node {
        public Obj {
            fields = Collections.unmodifiableMap(fields);
        }

        @Override
        public Kind kind() {
            return Kind.OBJECT;
        }

        @Override
        public Node get(String field) {
            Node n = fields.get(field);
            return n == null ? MISSING : n;
        }

        @Override
        public int size() {
            return fields.size();
        }

        @Override
        public Object unwrap() {
            LinkedHashMap<String, Object> m = new LinkedHashMap<>();
            fields.forEach((k, v) -> m.put(k, v.unwrap()));
            return m;
        }
    }

    /** A list. */
    public record Arr(List<Node> elements) implements Node {
        public Arr {
            elements = List.copyOf(elements);
        }

        @Override
        public Kind kind() {
            return Kind.ARRAY;
        }

        @Override
        public Node get(int index) {
            int i = index < 0 ? elements.size() + index : index;
            return i >= 0 && i < elements.size() ? elements.get(i) : MISSING;
        }

        @Override
        public int size() {
            return elements.size();
        }

        @Override
        public Object unwrap() {
            return elements.stream().map(Node::unwrap).toList();
        }
    }

    /** A scalar: string, number, boolean or null. */
    public record Val(Object value) implements Node {

        @Override
        public Kind kind() {
            if (value == null) {
                return Kind.NULL;
            }
            if (value instanceof Number) {
                return Kind.NUMBER;
            }
            if (value instanceof Boolean) {
                return Kind.BOOLEAN;
            }
            return Kind.STRING;
        }

        @Override
        public boolean isMasked() {
            return Node.MASK.equals(value);
        }

        /** A masked value stands for the whole field: whatever lies under it is masked too. */
        @Override
        public Node get(String field) {
            return isMasked() ? this : MISSING;
        }

        @Override
        public Node get(int index) {
            return isMasked() ? this : MISSING;
        }

        @Override
        public String asText() {
            if (value == null) {
                return "";
            }
            if (value instanceof Double d && d == Math.rint(d) && Math.abs(d) < 1e15) {
                return Long.toString(d.longValue());
            }
            if (value instanceof BigDecimal b) {
                return b.toPlainString();
            }
            return value.toString();
        }

        @Override
        public double asDouble() {
            if (value instanceof Number n) {
                return n.doubleValue();
            }
            if (value instanceof String s) {
                try {
                    return Double.parseDouble(s);
                } catch (NumberFormatException e) {
                    return Double.NaN;
                }
            }
            return Double.NaN;
        }

        @Override
        public boolean asBoolean() {
            return value instanceof Boolean b ? b : value != null && "true".equalsIgnoreCase(value.toString());
        }

        @Override
        public Object unwrap() {
            return value;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Val v && Objects.equals(value, v.value);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(value);
        }
    }

    /** The absent node: the result of navigating to something that does not exist. */
    static final class Missing implements Node {
        private Missing() {}

        @Override
        public Kind kind() {
            return Kind.MISSING;
        }

        @Override
        public Object unwrap() {
            return null;
        }

        /** {@code MISSING}. */
        @Override
        public String toString() {
            return "MISSING";
        }
    }
}
