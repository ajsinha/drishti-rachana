/*
 * Copyright (c) 2026 Ashutosh Sinha. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.proof;

/** Proof-only stand-in for Caffeine's builder (cut C2). */
public final class Caffeine<K, V> {
    private long max = Long.MAX_VALUE;

    public static Caffeine<Object, Object> newBuilder() {
        return new Caffeine<>();
    }

    public Caffeine<K, V> maximumSize(long n) {
        this.max = n;
        return this;
    }

    @SuppressWarnings("unchecked")
    public <K1 extends K, V1 extends V> Cache<K1, V1> build() {
        return new Cache<>(max);
    }
}
