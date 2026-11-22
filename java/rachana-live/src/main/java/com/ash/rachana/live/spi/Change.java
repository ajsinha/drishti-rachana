/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.live.spi;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One change of one row of a live table (LIVE_TABLES 1.6, PROTOCOL 16), as the engine passed it after every check: the client-made id
 * (the same id is the same change, end to end), the operation, the row's key values in key order, the version it was made on, the
 * columns set, the values the writer saw, the reason, who made it ({@link Authoriser#who}) and when the engine took it.
 *
 * @param op {@code update}, {@code add} or {@code remove}
 */
public record Change(String id, String table, String op, List<Object> key, Object basedOn, Map<String, Object> set, Map<String, Object> from,
        String reason, String by, Instant at) {

    public Change {
        key = key == null ? List.of() : java.util.Collections.unmodifiableList(new ArrayList<>(key));
        set = set == null ? Map.of() : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(set));
        from = from == null ? Map.of() : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(from));
    }

    /** The change as JSON reads it: {@code {id, table, op, key, basedOn, set, from?, reason?, by, at}} (a journal's or a host's record). */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("table", table);
        m.put("op", op);
        m.put("key", key);
        m.put("basedOn", basedOn);
        m.put("set", set);
        if (!from.isEmpty()) {
            m.put("from", from);
        }
        if (reason != null) {
            m.put("reason", reason);
        }
        m.put("by", by);
        m.put("at", at == null ? null : at.toString());
        return m;
    }

    /**
     * The change's content as one value (PROTOCOL 16, a repeated id): the SHA-256, in lower-case hex, of a canonical text of {@code table},
     * {@code op}, {@code key}, {@code basedOn}, {@code set}, {@code from} and {@code reason}; not of {@code id}, {@code by} or {@code at}. The
     * same message sent again gives the same digest, also after a journal's round trip: object keys are sorted and a number is written
     * by its value ({@code 9}, {@code 9.0} and {@code 9L} are one). A sink keeps it with its first answer ({@link ChangeSink#priorDigest}).
     */
    public String digest() {
        StringBuilder b = new StringBuilder();
        canon(b, table);
        canon(b, op);
        canon(b, key);
        canon(b, basedOn);
        canon(b, set);
        canon(b, from);
        canon(b, reason);
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(b.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every Java runtime", e);
        }
    }

    private static void canon(StringBuilder b, Object v) {
        if (v == null) {
            b.append('n');
        } else if (v instanceof Map<?, ?> m) {
            java.util.TreeMap<String, Object> sorted = new java.util.TreeMap<>();
            m.forEach((k, x) -> sorted.put(String.valueOf(k), x));
            b.append('{').append(sorted.size()).append(':');
            sorted.forEach((k, x) -> {
                canon(b, k);
                canon(b, x);
            });
            b.append('}');
        } else if (v instanceof java.util.Collection<?> c) {
            b.append('[').append(c.size()).append(':');
            c.forEach(x -> canon(b, x));
            b.append(']');
        } else if (v instanceof Number n) {
            String t;
            try {
                t = new java.math.BigDecimal(n.toString()).stripTrailingZeros().toPlainString();
            } catch (NumberFormatException e) {
                t = n.toString();                                       // NaN, infinities
            }
            b.append('d').append(t).append(';');
        } else if (v instanceof Boolean x) {
            b.append(x ? 't' : 'f');
        } else {
            String t = v.toString();
            b.append('s').append(t.length()).append(':').append(t);
        }
    }

    /** A change from its {@link #toMap()} form. */
    @SuppressWarnings("unchecked")
    public static Change of(Map<String, Object> m) {
        Object at = m.get("at");
        return new Change(str(m.get("id")), str(m.get("table")), str(m.get("op")), m.get("key") instanceof List<?> l ? (List<Object>) l : null,
                m.get("basedOn"), m.get("set") instanceof Map<?, ?> s ? (Map<String, Object>) s : null,
                m.get("from") instanceof Map<?, ?> f ? (Map<String, Object>) f : null, str(m.get("reason")), str(m.get("by")),
                at == null ? null : Instant.parse(at.toString()));
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
