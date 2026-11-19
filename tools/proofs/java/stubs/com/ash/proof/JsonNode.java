/*
 * Copyright (c) 2026 Ashutosh Sinha. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.proof;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Proof-only stand-in for the few members of Jackson's {@code JsonNode} the copied sources call (cut C3 of CUTS.md), with
 * Jackson's documented results for each: {@code path} never null, {@code asText()} empty for containers and missing, the
 * defaults of {@code asText/asInt/asBoolean/asDouble} for missing and null nodes.
 */
public final class JsonNode implements Iterable<JsonNode> {

    private static final JsonNode MISSING = new JsonNode(null, true);
    private final Object v;
    private final boolean missing;

    private JsonNode(Object v, boolean missing) {
        this.v = v;
        this.missing = missing;
    }

    static JsonNode of(Object v) {
        if (v instanceof Map<?, ?> m && m.size() == 1 && m.containsKey("$f64")) {
            return new JsonNode(Double.longBitsToDouble(Json.hex((String) m.get("$f64"))), false);
        }
        return new JsonNode(v, false);
    }

    public JsonNode path(String name) {
        if (v instanceof Map<?, ?> m && m.containsKey(name)) {
            return of(m.get(name));
        }
        return MISSING;
    }

    public JsonNode get(String name) {
        if (v instanceof Map<?, ?> m && m.containsKey(name)) {
            return of(m.get(name));
        }
        return null;
    }

    public boolean hasNonNull(String name) {
        JsonNode n = get(name);
        return n != null && !n.isNull();
    }

    public boolean isNull() {
        return !missing && v == null;
    }

    public String asText() {
        if (missing || v instanceof Map || v instanceof List) {
            return "";
        }
        return v == null ? "null" : v.toString();
    }

    public String asText(String def) {
        return missing || v == null ? def : asText();
    }

    public int asInt(int def) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v instanceof Boolean b) {
            return b ? 1 : 0;
        }
        if (v instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                return def;
            }
        }
        return def;
    }

    public double asDouble(double def) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v instanceof Boolean b) {
            return b ? 1 : 0;
        }
        if (v instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException e) {
                return def;
            }
        }
        return def;
    }

    public boolean asBoolean(boolean def) {
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof Number n) {
            return n.intValue() != 0;
        }
        if (v instanceof String s) {
            String t = s.trim();
            if ("true".equals(t)) {
                return true;
            }
            if ("false".equals(t)) {
                return false;
            }
        }
        return def;
    }

    public Iterator<Map.Entry<String, JsonNode>> fields() {
        return properties().iterator();
    }

    public Set<Map.Entry<String, JsonNode>> properties() {
        Set<Map.Entry<String, JsonNode>> out = new LinkedHashSet<>();
        if (v instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                out.add(new AbstractMap.SimpleImmutableEntry<>((String) e.getKey(), of(e.getValue())));
            }
        }
        return out;
    }

    @Override
    public Iterator<JsonNode> iterator() {
        List<JsonNode> out = new ArrayList<>();
        if (v instanceof List<?> l) {
            for (Object o : l) {
                out.add(of(o));
            }
        } else if (v instanceof Map<?, ?> m) {
            for (Object o : m.values()) {
                out.add(of(o));
            }
        } else {
            return Collections.emptyIterator();
        }
        return out.iterator();
    }
}
