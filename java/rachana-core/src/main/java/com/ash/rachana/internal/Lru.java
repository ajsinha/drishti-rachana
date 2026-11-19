/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.internal;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * A bounded cache that drops the least recently used entry: Core's own, so the compiler needs no cache library. Safe for many
 * threads; a value is computed outside the lock (two threads may compute the same key once each; the first stored wins), and a
 * computation that throws stores nothing.
 */
public final class Lru<K, V> {

    private final LinkedHashMap<K, V> map;

    public Lru(long maxEntries) {
        int cap = (int) Math.max(1, Math.min(Integer.MAX_VALUE - 8, maxEntries));
        this.map = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > cap;
            }
        };
    }

    /** The value of the key, computed and kept when it is not held. */
    public V get(K key, Function<? super K, ? extends V> compute) {
        synchronized (map) {
            V v = map.get(key);
            if (v != null) {
                return v;
            }
        }
        V made = compute.apply(key);
        if (made == null) {
            return null;
        }
        synchronized (map) {
            V held = map.putIfAbsent(key, made);
            return held != null ? held : made;
        }
    }

    /** The number of entries held. */
    public long size() {
        synchronized (map) {
            return map.size();
        }
    }
}
