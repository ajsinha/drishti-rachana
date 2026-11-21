/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.live;

import com.ash.rachana.live.spi.LiveStream;
import com.ash.rachana.live.spi.SnapshotSource;
import com.ash.rachana.parse.Json;
import com.ash.rachana.table.KeyedRows;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * The one live stream {@code rachana-live} ships (LIVE_TABLES 1.2): it reads a whole-tier snapshot source every {@code refreshMs} and
 * sends what changed since the last read, by key (a row whose JSON differs is an upsert, a key no longer there a delete). For a source
 * with no feed of its own. {@link #poll()} runs one round (tests and hosts with their own timer call it; {@code refreshMs} 0 starts no
 * timer). A failed read pauses the stream until a read succeeds again.
 */
public final class PollingDiff implements LiveStream {

    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "rachana-live-poll");
        t.setDaemon(true);
        return t;
    });

    private final SnapshotSource source;
    private final KeyedRows keys;
    private final long refreshMs;
    private final List<Listener> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private Map<String, String> last;                                // key text -> the row's JSON
    private Map<String, List<Object>> lastKeys = new HashMap<>();

    public PollingDiff(SnapshotSource source, List<String> key, long refreshMs) {
        this.source = source;
        this.keys = new KeyedRows(key, List.of(), 0);
        this.refreshMs = refreshMs;
    }

    @Override
    public AutoCloseable subscribe(Listener listener) {
        listeners.add(listener);
        ScheduledFuture<?> f = refreshMs > 0 ? TIMER.scheduleWithFixedDelay(this::poll, refreshMs, refreshMs, TimeUnit.MILLISECONDS) : null;
        return () -> {
            listeners.remove(listener);
            if (f != null) {
                f.cancel(false);
            }
        };
    }

    /** One round: reads the source and sends the differences from the last round (the first round only remembers). */
    public synchronized void poll() {
        Map<String, String> now = new LinkedHashMap<>();
        Map<String, Map<String, Object>> rows = new LinkedHashMap<>();
        Map<String, List<Object>> nowKeys = new HashMap<>();
        try {
            source.read(null, r -> {
                String k = keys.keyOf(r);
                now.put(k, Json.write(r));
                rows.put(k, r);
                List<Object> kv = new ArrayList<>();
                keys.key().forEach(c -> kv.add(r.get(c)));
                nowKeys.put(k, kv);
            });
        } catch (Exception e) {
            listeners.forEach(l -> l.state(false, "the source could not be read: " + e.getClass().getSimpleName()));
            return;
        }
        listeners.forEach(l -> l.state(true, null));
        if (last != null) {
            for (Map.Entry<String, String> e : now.entrySet()) {
                if (!e.getValue().equals(last.get(e.getKey()))) {
                    Map<String, Object> row = rows.get(e.getKey());
                    listeners.forEach(l -> l.upsert(row, null));
                }
            }
            for (String k : last.keySet()) {
                if (!now.containsKey(k)) {
                    List<Object> kv = lastKeys.get(k);
                    listeners.forEach(l -> l.delete(kv, null));
                }
            }
        }
        last = now;
        lastKeys = nowKeys;
    }
}
