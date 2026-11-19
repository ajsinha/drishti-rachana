/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import static org.assertj.core.api.Assertions.assertThat;

import com.ash.rachana.api.Node;
import com.ash.rachana.format.Formats;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A host passes its own document type by implementing {@link Node}, never by copying into Rachana's (split step A2): the evaluator
 * gives the same answers over a foreign implementation as over {@link Node#of(Object)}.
 */
class ForeignNodeTest {

    /** A host's node: a plain Java value behind the interface, nothing shared with Rachana's own classes. */
    record HostNode(Object v) implements Node {
        @Override
        public Kind kind() {
            if (v instanceof Map<?, ?>) return Kind.OBJECT;
            if (v instanceof List<?>) return Kind.ARRAY;
            if (v == null) return Kind.NULL;
            if (v instanceof Number) return Kind.NUMBER;
            if (v instanceof Boolean) return Kind.BOOLEAN;
            return Kind.STRING;
        }

        @Override
        public Node get(String field) {
            return v instanceof Map<?, ?> m && m.containsKey(field) ? new HostNode(m.get(field)) : Node.missing();
        }

        @Override
        public Node get(int i) {
            if (!(v instanceof List<?> l)) return Node.missing();
            int k = i < 0 ? l.size() + i : i;
            return k >= 0 && k < l.size() ? new HostNode(l.get(k)) : Node.missing();
        }

        @Override
        public int size() {
            return v instanceof Map<?, ?> m ? m.size() : v instanceof List<?> l ? l.size() : 0;
        }

        @Override
        public Object value() {
            return kind().isScalar() ? v : null;
        }

        @Override
        public String asText() {
            return v == null || !kind().isScalar() ? "" : v.toString();
        }

        @Override
        public double asDouble() {
            return v instanceof Number n ? n.doubleValue() : Double.NaN;
        }

        @Override
        public List<? extends Node> elements() {
            return v instanceof List<?> l ? l.stream().map(HostNode::new).toList() : List.of();
        }

        @Override
        public Map<String, ? extends Node> fields() {
            Map<String, HostNode> m = new LinkedHashMap<>();
            if (v instanceof Map<?, ?> src) src.forEach((k, x) -> m.put(String.valueOf(k), new HostNode(x)));
            return m;
        }

        @Override
        public Object unwrap() {
            return v;
        }
    }

    static Map<String, Object> plain() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ID", "T-1");
        m.put("QTY", 4L);
        m.put("PX", 2.5);
        m.put("LEGS", List.of(Map.of("PV", 1.5, "CCY", "USD"), Map.of("PV", -3L, "CCY", "EUR"), Map.of("PV", 10L, "CCY", "USD")));
        return m;
    }

    @Test
    void theEvaluatorAnswersAlikeOverAHostsNodes() {
        ElCompiler el = new ElCompiler();
        Formats f = Formats.defaults();
        for (String e : List.of("$.QTY * $.PX", "upper($.ID)", "$.LEGS[*].PV", "$.LEGS[?(@.CCY == 'USD')][1].PV",
                "$.LEGS[1].PV", "coalesce($.MISSING, 'none')", "if($.QTY > 3, 'big', 'small')", "$.LEGS[-1].CCY")) {
            Object own = Values.simplify(el.compile(e).eval(EvalContext.of(Node.of(plain()), f)));
            Object host = Values.simplify(el.compile(e).eval(EvalContext.of(new HostNode(plain()), f)));
            assertThat(Values.text(host)).as(e).isEqualTo(Values.text(own));
        }
    }
}
