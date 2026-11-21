/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.internal;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * A small bounded cache whose entries expire a fixed time after they were written (the binder's map lookups): Core's own, so no
 * cache library is needed. The least recently used entry goes first when it is full. Safe for many threads; a value is computed
 * outside the lock, and a computation that throws stores nothing.
 */
public final class TtlCache<K, V> {

    private record Held<V>(V value, long writtenAt) {}

    private final LinkedHashMap<K, Held<V>> map;
    private final long ttlNanos;
    private final LongSupplier clock;

    public TtlCache(int maxEntries, Duration ttl) {
        this(maxEntries, ttl, System::nanoTime);
    }

    TtlCache(int maxEntries, Duration ttl, LongSupplier clock) {
        int cap = Math.max(1, maxEntries);
        this.ttlNanos = Math.max(0, ttl.toNanos());
        this.clock = clock;
        this.map = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, Held<V>> eldest) {
                return size() > cap;
            }
        };
    }

    /** The value of the key, computed and kept when it is not held or has expired. */
    public V get(K key, Function<? super K, ? extends V> compute) {
        synchronized (map) {
            Held<V> h = map.get(key);
            if (h != null) {
                if (clock.getAsLong() - h.writtenAt() < ttlNanos) {
                    return h.value();
                }
                map.remove(key);
            }
        }
        V made = compute.apply(key);
        if (made == null) {
            return null;
        }
        synchronized (map) {
            map.put(key, new Held<>(made, clock.getAsLong()));
        }
        return made;
    }
}
