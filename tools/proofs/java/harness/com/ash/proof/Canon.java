/*
 * Copyright (c) 2026 Ashutosh Sinha. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.proof;

import com.ash.drishti.api.DataNode;
import com.ash.drishti.api.NodeType;
import com.ash.drishti.rachana.el.Link;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * Canonical JSON of conformance FORMAT section 8 for the proof's results lines. Numbers go through the copied compiler's own
 * text form ({@code Values.text}): that is exactly the code path whose bytes P2 compares across runtimes.
 */
public final class Canon {
    private Canon() {
    }

    public static void value(Object v, StringBuilder b) {
        Object s = com.ash.drishti.rachana.el.Values.simplify(v);
        if (s == null) {
            b.append("null");
        } else if (com.ash.drishti.rachana.el.Values.masked(s)) {
            b.append("{\"$masked\":true}");
        } else if (s instanceof Boolean x) {
            b.append(x ? "true" : "false");
        } else if (s instanceof Double d) {
            number(d, b);
        } else if (s instanceof Float f) {
            number(f.doubleValue(), b);
        } else if (s instanceof BigDecimal d) {
            b.append(d.signum() == 0 ? "0" : d.stripTrailingZeros().toPlainString());
        } else if (s instanceof BigInteger || s instanceof Long || s instanceof Integer || s instanceof Short || s instanceof Byte) {
            b.append(s.toString());
        } else if (s instanceof String t) {
            string(t, b);
        } else if (s instanceof Link l) {
            b.append("{\"$link\":{\"id\":");
            string(l.id(), b);
            if (l.kind() != null) {
                b.append(",\"kind\":");
                string(l.kind(), b);
            }
            if (l.label() != null) {
                b.append(",\"label\":");
                string(l.label(), b);
            }
            b.append("}}");
        } else if (s instanceof DataNode n) {
            if (n.type() == NodeType.ARRAY) {
                b.append('[');
                for (int i = 0; i < n.size(); i++) {
                    if (i > 0) {
                        b.append(',');
                    }
                    value(n.get(i), b);
                }
                b.append(']');
            } else {
                b.append('{');
                boolean first = true;
                for (Map.Entry<String, DataNode> e : ((DataNode.Obj) n).fields().entrySet()) {
                    if (!first) {
                        b.append(',');
                    }
                    first = false;
                    string(e.getKey(), b);
                    b.append(':');
                    value(e.getValue(), b);
                }
                b.append('}');
            }
        } else if (s instanceof List<?> l) {
            b.append('[');
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) {
                    b.append(',');
                }
                value(l.get(i), b);
            }
            b.append(']');
        } else if (s instanceof Map<?, ?> m) {
            b.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) {
                    b.append(',');
                }
                first = false;
                string(String.valueOf(e.getKey()), b);
                b.append(':');
                value(e.getValue(), b);
            }
            b.append('}');
        } else {
            b.append("{\"$other\":");
            string(com.ash.drishti.rachana.el.Values.text(s), b);
            b.append('}');
        }
    }

    static void number(double d, StringBuilder b) {
        if (Double.isNaN(d)) {
            b.append("{\"$num\":\"NaN\"}");
        } else if (Double.isInfinite(d)) {
            b.append(d > 0 ? "{\"$num\":\"Infinity\"}" : "{\"$num\":\"-Infinity\"}");
        } else {
            b.append(com.ash.drishti.rachana.el.Values.text(d));
        }
    }

    public static String type(Object v) {
        Object s = com.ash.drishti.rachana.el.Values.simplify(v);
        if (s == null) {
            return "empty";
        }
        if (com.ash.drishti.rachana.el.Values.masked(s)) {
            return "masked";
        }
        if (s instanceof Boolean) {
            return "boolean";
        }
        if (s instanceof BigDecimal) {
            return "decimal";
        }
        if (s instanceof Number) {
            return "number";
        }
        if (s instanceof String) {
            return "text";
        }
        if (s instanceof Link) {
            return "link";
        }
        if (s instanceof DataNode n) {
            return n.type() == NodeType.ARRAY ? "list" : "object";
        }
        if (s instanceof List) {
            return "list";
        }
        if (s instanceof Map) {
            return "object";
        }
        return "other";
    }

    private static final String HEX = "0123456789abcdef";

    public static void string(String s, StringBuilder b) {
        b.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\b': b.append("\\b"); break;
                case '\t': b.append("\\t"); break;
                case '\n': b.append("\\n"); break;
                case '\f': b.append("\\f"); break;
                case '\r': b.append("\\r"); break;
                default:
                    boolean lone = Character.isHighSurrogate(c) ? !(i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1)))
                            : Character.isLowSurrogate(c) && !(i > 0 && Character.isHighSurrogate(s.charAt(i - 1)));
                    if (c < 0x20 || c == 0x7f || lone) {
                        b.append("\\u").append(HEX.charAt(c >> 12)).append(HEX.charAt((c >> 8) & 15)).append(HEX.charAt((c >> 4) & 15))
                                .append(HEX.charAt(c & 15));
                    } else {
                        b.append(c);
                    }
            }
        }
        b.append('"');
    }
}
