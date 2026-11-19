/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.design.ops;

import com.ash.rachana.parse.Json;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The JSON form of operations: {@code {"op":"addPanel","kind":"table","at":{"area":"main","after":"legs"},"options":{...}}}
 * and so on, one object per operation, as plain JSON values ({@link Json}: maps, lists, text, {@code Long}, {@code Double},
 * {@code Boolean}, null). Reading is strict and says which operation (0-based) and which field is wrong. Stateless and
 * thread-safe.
 */
public final class Ops {

    /** A malformed list of operations; the message names the operation. */
    public static final class FormatException extends IllegalArgumentException {

        private static final long serialVersionUID = 1L;
        private final int op;

        FormatException(int op, String message) {
            super(op < 0 ? message : "operation " + op + ": " + message);
            this.op = op;
        }

        /** The operation's index, or -1 when the list itself is wrong. */
        public int op() {
            return op;
        }
    }

    private static final Map<String, Set<String>> FIELDS = Map.ofEntries(
            Map.entry("addPanel", Set.of("op", "id", "kind", "at", "options")),
            Map.entry("move", Set.of("op", "panel", "area", "before", "after", "span", "height")),
            Map.entry("setOption", Set.of("op", "panel", "option", "value")),
            Map.entry("bind", Set.of("op", "panel", "path", "role")),
            Map.entry("remove", Set.of("op", "panel")),
            Map.entry("setTitle", Set.of("op", "title")),
            Map.entry("setStrip", Set.of("op", "items")),
            Map.entry("setControls", Set.of("op", "items")),
            Map.entry("setKeys", Set.of("op", "keys")),
            Map.entry("setMatch", Set.of("op", "match")),
            Map.entry("setBlock", Set.of("op", "use", "with", "remove")),
            Map.entry("text", Set.of("op", "yaml")));
    private static final Set<String> AT = Set.of("area", "before", "after", "span", "height");
    private Ops() {}

    /** Reads a JSON array of operations. */
    public static List<Op> parse(String json) {
        Object tree;
        try {
            tree = Json.read(json);
        } catch (Json.JsonException e) {
            throw new FormatException(-1, "the operations are not JSON: " + e.getMessage());
        }
        BigInteger big = big(tree);
        if (big != null) {
            throw new FormatException(-1, "the operations are not JSON: Numeric value (" + big + ") out of range of long (" + Long.MIN_VALUE + " - "
                    + Long.MAX_VALUE + ")");
        }
        return parse(tree);
    }

    /** A whole number beyond a {@code long} anywhere in the tree: operations hold none. */
    private static BigInteger big(Object v) {
        if (v instanceof BigInteger b) {
            return b;
        }
        if (v instanceof Map<?, ?> m) {
            for (Object x : m.values()) {
                BigInteger b = big(x);
                if (b != null) {
                    return b;
                }
            }
        } else if (v instanceof List<?> l) {
            for (Object x : l) {
                BigInteger b = big(x);
                if (b != null) {
                    return b;
                }
            }
        }
        return null;
    }

    /** Reads operations from their JSON value: a list of objects ({@link Json#read}). */
    public static List<Op> parse(Object array) {
        if (!(array instanceof List<?> list)) {
            throw new FormatException(-1, "'ops' must be a list of operations such as {\"op\":\"remove\",\"panel\":\"legs\"}");
        }
        List<Op> out = new ArrayList<>();
        int i = 0;
        for (Object n : list) {
            out.add(one(i++, n));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Op one(int i, Object node) {
        if (!(node instanceof Map<?, ?> raw) || !(raw.get("op") instanceof String)) {
            throw new FormatException(i, "an operation is an object with an \"op\" such as " + String.join(", ", FIELDS.keySet().stream().sorted().toList()));
        }
        Map<String, Object> n = (Map<String, Object>) raw;
        String op = (String) n.get("op");
        Set<String> known = FIELDS.get(op);
        if (known == null) {
            throw new FormatException(i, "unknown operation '" + op + "'; expected one of " + String.join(", ", FIELDS.keySet().stream().sorted().toList()));
        }
        for (String f : n.keySet()) {
            if (!known.contains(f)) {
                throw new FormatException(i, "'" + op + "' has no field '" + f + "'; it takes " + String.join(", ", known.stream().filter(k -> !k.equals("op")).sorted().toList()));
            }
        }
        return switch (op) {
            case "addPanel" -> new AddPanel(text(i, n, "id", false), text(i, n, "kind", true), at(i, n.get("at")), map(i, n, "options", false));
            case "move" -> new Move(text(i, n, "panel", true), text(i, n, "area", false), text(i, n, "before", false), text(i, n, "after", false),
                    whole(i, n, "span"), whole(i, n, "height"));
            case "setOption" -> {
                if (!n.containsKey("value")) {
                    throw new FormatException(i, "'setOption' needs a \"value\" (null removes the option)");
                }
                yield new SetOption(text(i, n, "panel", true), text(i, n, "option", true), copy(n.get("value")));
            }
            case "bind" -> new Bind(text(i, n, "panel", true), text(i, n, "path", true), text(i, n, "role", false));
            case "remove" -> new Remove(text(i, n, "panel", true));
            case "setTitle" -> new SetTitle(map(i, n, "title", true));
            case "setStrip" -> new SetStrip(items(i, n));
            case "setControls" -> new SetControls(items(i, n));
            case "setKeys" -> new SetKeys(map(i, n, "keys", true));
            case "setMatch" -> new SetMatch(map(i, n, "match", true));
            case "setBlock" -> {
                Integer use = whole(i, n, "use");
                if (use == null || use < 0) {
                    throw new FormatException(i, "'setBlock' needs \"use\": which use of a block (0 is the first)");
                }
                yield new SetBlock(use, map(i, n, "with", false), bool(n.get("remove"), false));
            }
            default -> new Text(text(i, n, "yaml", true));
        };
    }

    private static String text(int i, Map<String, Object> n, String field, boolean required) {
        Object v = n.get(field);
        if (v == null) {
            if (required) {
                throw new FormatException(i, "'" + n.get("op") + "' needs \"" + field + "\"");
            }
            return null;
        }
        if (!(v instanceof String s)) {
            throw new FormatException(i, "\"" + field + "\" must be text");
        }
        return s;
    }

    private static Integer whole(int i, Map<String, Object> n, String field) {
        Object v = n.get(field);
        if (v == null) {
            return null;
        }
        boolean integral = v instanceof Long || v instanceof Integer || v instanceof Short || v instanceof Byte || v instanceof BigInteger;
        if (!integral || new BigInteger(v.toString()).bitLength() > 31) {
            throw new FormatException(i, "\"" + field + "\" must be a whole number");
        }
        return ((Number) v).intValue();
    }

    /** JSON's reading of a flag: a boolean, the text {@code true} or {@code false}, a whole number other than 0; else the default. */
    private static boolean bool(Object v, boolean otherwise) {
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof String s) {
            String t = s.trim();
            return "true".equals(t) || !"false".equals(t) && otherwise;
        }
        if (v instanceof Long l) {
            return l != 0;
        }
        return otherwise;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(int i, Map<String, Object> n, String field, boolean required) {
        Object v = n.get(field);
        if (v == null) {
            if (required) {
                throw new FormatException(i, "'" + n.get("op") + "' needs \"" + field + "\" (an object)");
            }
            return null;
        }
        if (!(v instanceof Map)) {
            throw new FormatException(i, "\"" + field + "\" must be an object");
        }
        return (Map<String, Object>) copy(v);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(int i, Map<String, Object> n) {
        Object v = n.get("items");
        if (!(v instanceof List<?> l)) {
            throw new FormatException(i, "'" + n.get("op") + "' needs \"items\" (a list of objects)");
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object x : l) {
            if (!(x instanceof Map)) {
                throw new FormatException(i, "each item must be an object such as {\"label\":\"PV\",\"bind\":\"$.pv\"} (a strip item) or {\"id\":\"leg\",\"kind\":\"dropdown\"} (a control)");
            }
            out.add((Map<String, Object>) copy(x));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static AddPanel.At at(int i, Object a) {
        if (a == null) {
            return null;
        }
        if (!(a instanceof Map<?, ?>)) {
            throw new FormatException(i, "\"at\" must be an object: { area, before | after, span, height }");
        }
        Map<String, Object> m = (Map<String, Object>) a;
        for (String f : m.keySet()) {
            if (!AT.contains(f)) {
                throw new FormatException(i, "\"at\" has no field '" + f + "'; it takes area, before, after, span, height");
            }
        }
        Map<String, Object> withOp = new LinkedHashMap<>(m);
        withOp.put("op", "addPanel");
        return new AddPanel.At(text(i, withOp, "area", false), text(i, withOp, "before", false), text(i, withOp, "after", false),
                whole(i, withOp, "span"), whole(i, withOp, "height"));
    }

    /**
     * An operation as JSON, {@code "op"} first, then its fields in their declared order; absent fields and null map
     * entries are left out (a {@code setOption}'s {@code value} is always written, null when it removes the option).
     */
    public static Map<String, Object> toJson(Op op) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("op", op.name());
        switch (op) {
            case AddPanel o -> {
                put(out, "id", o.id());
                put(out, "kind", o.kind());
                if (o.at() != null) {
                    Map<String, Object> at = new LinkedHashMap<>();
                    put(at, "area", o.at().area());
                    put(at, "before", o.at().before());
                    put(at, "after", o.at().after());
                    put(at, "span", o.at().span());
                    put(at, "height", o.at().height());
                    out.put("at", at);
                }
                put(out, "options", o.options());
            }
            case Move o -> {
                put(out, "panel", o.panel());
                put(out, "area", o.area());
                put(out, "before", o.before());
                put(out, "after", o.after());
                put(out, "span", o.span());
                put(out, "height", o.height());
            }
            case SetOption o -> {
                put(out, "panel", o.panel());
                put(out, "option", o.option());
                out.put("value", clean(o.value()));
            }
            case Bind o -> {
                put(out, "panel", o.panel());
                put(out, "path", o.path());
                put(out, "role", o.role());
            }
            case Remove o -> put(out, "panel", o.panel());
            case SetTitle o -> put(out, "title", o.title());
            case SetStrip o -> put(out, "items", o.items());
            case SetControls o -> put(out, "items", o.items());
            case SetKeys o -> put(out, "keys", o.keys());
            case SetMatch o -> put(out, "match", o.match());
            case SetBlock o -> {
                out.put("use", o.use());
                put(out, "with", o.with());
                out.put("remove", o.remove());
            }
            case Text o -> put(out, "yaml", o.yaml());
        }
        return out;
    }

    public static List<Map<String, Object>> toJson(List<? extends Op> ops) {
        List<Map<String, Object>> out = new ArrayList<>();
        ops.forEach(o -> out.add(toJson(o)));
        return out;
    }

    private static void put(Map<String, Object> into, String key, Object value) {
        if (value != null) {
            into.put(key, clean(value));
        }
    }

    /** A deep copy of a JSON value (maps in order, nulls kept), so an operation never shares the caller's objects. */
    private static Object copy(Object v) {
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, x) -> out.put(String.valueOf(k), copy(x)));
            return out;
        }
        if (v instanceof List<?> l) {
            List<Object> out = new ArrayList<>();
            l.forEach(x -> out.add(copy(x)));
            return out;
        }
        return v;
    }

    /** A copy without the null entries of maps, at any depth (list items stay, null included). */
    private static Object clean(Object v) {
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, x) -> {
                if (x != null) {
                    out.put(String.valueOf(k), clean(x));
                }
            });
            return out;
        }
        if (v instanceof List<?> l) {
            List<Object> out = new ArrayList<>();
            l.forEach(x -> out.add(clean(x)));
            return out;
        }
        return v;
    }
}
