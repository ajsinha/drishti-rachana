/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.model;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The one way Rachana builds a fixed map or set: in the order written. {@code Map.of}, {@code Set.of} and their {@code copyOf}
 * draw a new iteration order in every JVM run (and TeaVM's differs again), so anything built from them that reaches output
 * bytes (a ViewModel, an answer, a page, a file) differs from run to run and between the JVM and the bundles. These factories
 * keep {@code Map.of}'s other promises: unmodifiable, no null key or value, no duplicate key. The guard
 * {@code OrderedCollectionsGuardTest} keeps the unordered factories out of every module's main sources.
 */
public final class Ordered {

    private Ordered() {}

    /** An unmodifiable map of the pairs (key, value, key, value, ...) in the order written. */
    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> map(Object... keysAndValues) {
        if (keysAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("Ordered.map takes key, value pairs: " + keysAndValues.length + " arguments");
        }
        Map<K, V> m = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            K k = (K) Objects.requireNonNull(keysAndValues[i], "key");
            V v = (V) Objects.requireNonNull(keysAndValues[i + 1], "value");
            if (m.put(k, v) != null) {
                throw new IllegalArgumentException("duplicate key: " + k);
            }
        }
        return Collections.unmodifiableMap(m);
    }

    /** An unmodifiable copy of a map in its own iteration order (sort it first when that order is not fixed). */
    public static <K, V> Map<K, V> copy(Map<? extends K, ? extends V> source) {
        Map<K, V> m = new LinkedHashMap<>();
        source.forEach((k, v) -> m.put(Objects.requireNonNull(k, "key"), Objects.requireNonNull(v, "value")));
        return Collections.unmodifiableMap(m);
    }

    /** An unmodifiable set of the items in the order written. */
    @SafeVarargs
    public static <T> Set<T> set(T... items) {
        Set<T> s = new LinkedHashSet<>();
        for (T t : items) {
            if (!s.add(Objects.requireNonNull(t, "item"))) {
                throw new IllegalArgumentException("duplicate item: " + t);
            }
        }
        return Collections.unmodifiableSet(s);
    }

    /** An unmodifiable copy of a collection's distinct items in its iteration order. */
    public static <T> Set<T> copySet(Collection<? extends T> source) {
        Set<T> s = new LinkedHashSet<>();
        for (T t : source) {
            s.add(Objects.requireNonNull(t, "item"));
        }
        return Collections.unmodifiableSet(s);
    }
}
