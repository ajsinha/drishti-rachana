/*
 * Copyright (c) 2026 Ashutosh Sinha. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.proof;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/** Proof-only stand-in for Caffeine's cache (cut C2): a bounded LRU over an access-ordered LinkedHashMap. */
public final class Cache<K, V> {
    private final Map<K, V> map;

    Cache(long max) {
        this.map = new LinkedHashMap<>(16, 0.75f, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> e) {
                return size() > max;
            }
        };
    }

    public synchronized V get(K k, Function<? super K, ? extends V> f) {
        V v = map.get(k);
        if (v == null) {
            v = f.apply(k);
            map.put(k, v);
        }
        return v;
    }

    public synchronized long estimatedSize() {
        return map.size();
    }
}
