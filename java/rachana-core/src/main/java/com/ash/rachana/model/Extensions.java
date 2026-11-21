/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.model;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A Sutra's {@code extensions}: the other profiles' keys, listed (iteration, size, equality, a JSON writer see only these), and what the
 * compile keeps for itself ({@code $} keys, Core's {@code data} entries), which {@link #get(Object)} reads but which are not listed.
 */
final class Extensions extends AbstractMap<String, Object> {

    private static final Extensions EMPTY = new Extensions(Map.of(), Map.of());

    private final Map<String, Object> listed;
    private final Map<String, Object> all;

    private Extensions(Map<String, Object> listed, Map<String, Object> all) {
        this.listed = listed;
        this.all = all;
    }

    static Map<String, Object> of(Map<String, Object> m) {
        if (m == null || m.isEmpty()) {
            return EMPTY;
        }
        if (m instanceof Extensions e) {
            return e;
        }
        Map<String, Object> listed = new LinkedHashMap<>();
        Map<String, Object> all = new LinkedHashMap<>(m);
        all.forEach((k, v) -> {
            if (!isCompiled(k)) {
                listed.put(k, v);
            }
        });
        return new Extensions(Collections.unmodifiableMap(listed), Collections.unmodifiableMap(all));
    }

    /** A key the compile keeps for itself: a {@code $} key, or Core's {@code data} entries. */
    static boolean isCompiled(String key) {
        return key.startsWith("$") || DataEntry.KEY.equals(key);
    }

    @Override
    public Object get(Object key) {
        return all.get(key);
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return listed.entrySet();
    }
}
