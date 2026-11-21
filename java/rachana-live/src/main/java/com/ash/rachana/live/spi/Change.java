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
