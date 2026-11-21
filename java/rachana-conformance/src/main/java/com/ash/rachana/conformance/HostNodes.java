/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.conformance;

import com.ash.rachana.api.Node;
import com.ash.rachana.api.NodeFactory;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The host-node fixture of the conformance runtime ({@code JvmRuntime --host-nodes}): a host's own document type, shaped as a real
 * host's is (a sealed tree of records with covariant navigation, its own masked and missing values, its own {@link NodeFactory}), shares
 * nothing with Rachana's nodes. Every case answered over it must answer as over Rachana's own nodes (HostNodeConformanceTest), and every
 * list and record an expression builds comes back in this type.
 */
public sealed interface HostNodes extends Node permits HostNodes.Obj, HostNodes.Arr, HostNodes.Val, HostNodes.Missing {

    /** The fixture's factory: foreign nodes converted with what they hold, its own kept. */
    NodeFactory NODES = new NodeFactory() {
        @Override
        public Node of(Object value) {
            return HostNodes.of(value);
        }

        @Override
        public Node list(List<? extends Node> elements) {
            return new Arr(elements.stream().map(HostNodes::of).toList());
        }
    };

    @Override
    default NodeFactory nodes() {
        return NODES;
    }

    @Override
    default HostNodes get(String field) {
        return Missing.INSTANCE;
    }

    @Override
    default HostNodes get(int index) {
        return Missing.INSTANCE;
    }

    /** The fixture's tree of a plain value or of any node. */
    static HostNodes of(Object value) {
        if (value instanceof HostNodes h) {
            return h;
        }
        if (value instanceof Node n) {
            if (n.isMasked()) {
                return Val.MASKED;
            }
            return switch (n.kind()) {
                case MISSING -> Missing.INSTANCE;
                case NULL -> Val.NULL;
                case OBJECT -> {
                    Map<String, HostNodes> fields = new LinkedHashMap<>();
                    n.fields().forEach((k, v) -> fields.put(k, of(v)));
                    yield new Obj(fields);
                }
                case ARRAY -> new Arr(n.elements().stream().map(HostNodes::of).toList());
                default -> new Val(n.value());
            };
        }
        if (value == null) {
            return Val.NULL;
        }
        if (value instanceof Map<?, ?> m) {
            Map<String, HostNodes> fields = new LinkedHashMap<>();
            m.forEach((k, v) -> fields.put(String.valueOf(k), of(v)));
            return new Obj(fields);
        }
        if (value instanceof List<?> l) {
            return new Arr(l.stream().map(HostNodes::of).toList());
        }
        return new Val(value instanceof Number || value instanceof Boolean || value instanceof String ? value : value.toString());
    }

    /** An object. */
    record Obj(Map<String, HostNodes> fields) implements HostNodes {
        public Obj {
            fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        }

        @Override
        public Kind kind() {
            return Kind.OBJECT;
        }

        @Override
        public HostNodes get(String field) {
            HostNodes n = fields.get(field);
            return n == null ? Missing.INSTANCE : n;
        }

        @Override
        public int size() {
            return fields.size();
        }

        @Override
        public Object unwrap() {
            Map<String, Object> m = new LinkedHashMap<>();
            fields.forEach((k, v) -> m.put(k, v.unwrap()));
            return m;
        }
    }

    /** A list. */
    record Arr(List<HostNodes> elements) implements HostNodes {
        public Arr {
            elements = List.copyOf(elements);
        }

        @Override
        public Kind kind() {
            return Kind.ARRAY;
        }

        @Override
        public HostNodes get(int index) {
            int i = index < 0 ? elements.size() + index : index;
            return i >= 0 && i < elements.size() ? elements.get(i) : Missing.INSTANCE;
        }

        @Override
        public int size() {
            return elements.size();
        }

        @Override
        public Object unwrap() {
            return elements.stream().map(HostNodes::unwrap).toList();
        }
    }

    /** A scalar, null, or the masked value. */
    record Val(Object value) implements HostNodes {
        static final Val NULL = new Val(null);
        static final Val MASKED = new Val(new Object() {
            @Override
            public String toString() {
                return MASK;
            }
        });

        @Override
        public Kind kind() {
            return value == null ? Kind.NULL : value instanceof Number ? Kind.NUMBER : value instanceof Boolean ? Kind.BOOLEAN : Kind.STRING;
        }

        @Override
        public boolean isMasked() {
            return this == MASKED;
        }

        @Override
        public Object value() {
            return this == MASKED ? MASK : value;
        }

        @Override
        public HostNodes get(String field) {
            return isMasked() ? this : Missing.INSTANCE;
        }

        @Override
        public HostNodes get(int index) {
            return isMasked() ? this : Missing.INSTANCE;
        }

        @Override
        public String asText() {
            return value == null ? "" : value().toString();
        }

        @Override
        public double asDouble() {
            return value instanceof Number n ? n.doubleValue() : Double.NaN;
        }

        @Override
        public boolean asBoolean() {
            return value instanceof Boolean b ? b : value != null && "true".equalsIgnoreCase(value.toString());
        }

        @Override
        public Object unwrap() {
            return value();
        }
    }

    /** Nothing there. */
    final class Missing implements HostNodes {
        static final Missing INSTANCE = new Missing();

        private Missing() {}

        @Override
        public Kind kind() {
            return Kind.MISSING;
        }

        @Override
        public Object unwrap() {
            return null;
        }
    }
}
