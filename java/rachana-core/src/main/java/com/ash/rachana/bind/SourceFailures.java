/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.bind;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/** The sources a read could not use, and why (the first reason per source); thread-safe. */
public final class SourceFailures {

    private final Map<String, String> bySource = new ConcurrentHashMap<>();

    public void add(String source, String reason) {
        bySource.putIfAbsent(source == null ? "a source" : source, reason == null ? "failed" : reason);
    }

    public boolean isEmpty() {
        return bySource.isEmpty();
    }

    /** By source name, sorted. */
    public Map<String, String> asMap() {
        return java.util.Collections.unmodifiableMap(new TreeMap<>(bySource));
    }
}
