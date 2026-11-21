/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.live;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ash.rachana.data.TableProvider;
import com.ash.rachana.live.spi.Authoriser;
import com.ash.rachana.live.spi.Change;
import com.ash.rachana.live.spi.ChangeSink;
import com.ash.rachana.live.spi.LiveStream;
import com.ash.rachana.live.spi.ReaderView;
import com.ash.rachana.live.spi.SnapshotSource;
import com.ash.rachana.table.LiveTableSpec;
import com.ash.rachana.table.TableSchema;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * The live tables' scale run (LIVE_TABLES 7, 9; G3 half of L11), within a local memory budget: 100,000 rows (whole and paged), 200
 * windowed readers of 50 keys each, a burst of 1,000 writes through the guard as fast as 8 writers go, then 1,000 writes a second for 5
 * seconds; each write applied by the source and sent on the stream, so the patch reaches the windows whose keys it changes. Prints the
 * numbers (recorded in LIVE_TABLES's build status). Run: {@code ./mvnw -o test -pl rachana-live -am -Dtest=LiveScaleRun
 * -Drachana.live.scale=true -Dsurefire.failIfNoSpecifiedTests=false}.
 */
class LiveScaleRun {

    static final int ROWS = 100_000;
    static final int WINDOWS = 200;
    static final int KEYS = 50;

    static final String DECL = """
            rachana: 1
            live-table: trades
            snapshot: trades
            stream: trades.live
            key: id
            version: v
            changed-by: by
            sort: { col: id }
            columns: [ { name: id, type: integer }, { name: v, type: integer }, { name: book }, { name: qty, type: integer }, { name: by } ]
            edit:
              fields: [ { id: qty, bind: qty, type: number, min: 0 } ]
            """;

    static long heapMb() {
        Runtime r = Runtime.getRuntime();
        for (int i = 0; i < 3; i++) {
            System.gc();
        }
        return (r.totalMemory() - r.freeMemory()) >> 20;
    }

    @Test
    void scale() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("rachana.live.scale"), "the scale run: -Drachana.live.scale=true");
        for (SnapshotSource.Tier tier : List.of(SnapshotSource.Tier.WHOLE, SnapshotSource.Tier.COLUMNS)) {
            run(tier);
        }
    }

    void run(SnapshotSource.Tier tier) throws Exception {
        long heap0 = heapMb();
        LiveTableSpec spec = LiveTableSpec.read(DECL, "trades.live.yaml");
        LiveReferenceServer.TestSource src = new LiveReferenceServer.TestSource(tier, TableSchema.of(spec.schema()), null);
        for (int i = 0; i < ROWS; i++) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", (long) i);
            r.put("v", 1L);
            r.put("book", "B" + (i % 100));
            r.put("qty", (long) (i % 1000));
            r.put("by", "system");
            src.put(r);
        }
        long heapSource = heapMb();
        LiveReferenceServer.TestStream feed = new LiveReferenceServer.TestStream();
        LiveTables lt = new LiveTables(new LiveTables.Resolver() {
            @Override
            public SnapshotSource snapshot(String n) {
                return src;
            }

            @Override
            public LiveStream stream(String n) {
                return feed;
            }
        }, LiveTables.Options.DEFAULTS.withUpkeepMs(0), null);
        lt.authoriser(new Authoriser() {
            @Override
            public ReaderView view(Object caller, String table) {
                return new ReaderView("", null, Set.of(), null, null, Set.of("qty"), null);
            }

            @Override
            public Decision mayEdit(Object caller, String table, Map<String, Object> change, Map<String, Object> row) {
                return new Decision.Allow();
            }
        });
        Map<Long, Long> versions = new ConcurrentHashMap<>();
        lt.sink(new ChangeSink() {
            @Override
            public Outcome accept(Change ch) {
                long id = ((Number) ch.key().get(0)).longValue();
                Map<String, Object> row;
                synchronized (src) {
                    row = new LinkedHashMap<>(src.rows.get(String.valueOf(id)));
                    long v = ((Number) row.get("v")).longValue() + 1;
                    row.putAll(ch.set());
                    row.put("v", v);
                    row.put("by", ch.by());
                    src.put(row);
                    versions.put(id, v);
                }
                feed.listener.upsert(row, null);                             // the source's own change arrives on the stream
                return new Outcome(State.APPLIED, "applied", row.get("v"));
            }
        });
        lt.declare(spec);
        long t0 = System.nanoTime();
        Map<String, Object> first = lt.rows("trades", Map.of("limit", 100L), "w");
        long openMs = (System.nanoTime() - t0) / 1_000_000;
        assertEquals(ROWS, ((Number) first.get("total")).intValue());
        long t1 = System.nanoTime();
        lt.rows("trades", Map.of("limit", 100L, "offset", 90_000L), "w");
        long farPageMs = (System.nanoTime() - t1) / 1_000_000;
        long heapOpen = heapMb();

        AtomicLong patches = new AtomicLong();
        AtomicLong lagMax = new AtomicLong();
        Map<Long, Long> sentAt = new ConcurrentHashMap<>();
        List<AutoCloseable> subs = new ArrayList<>();
        for (int w = 0; w < WINDOWS; w++) {
            List<List<Object>> keys = new ArrayList<>();
            for (int k = 0; k < KEYS; k++) {
                keys.add(List.of((long) (w * KEYS + k)));
            }
            Map<String, Object> ver = lt.version("trades", "w");
            subs.add(lt.subscribe("trades", new TableProvider.Subscribe(ver.get("epoch") + ":" + ver.get("seq"), null, 0, keys, false), f -> {
                if (f.get("data") instanceof Map<?, ?> d && d.get("update") instanceof List<?> u) {
                    long now = System.nanoTime();
                    for (Object o : u) {
                        patches.incrementAndGet();
                        Long at = sentAt.get(((Number) ((Map<?, ?>) o).get("id")).longValue());
                        if (at != null) {
                            lagMax.accumulateAndGet((now - at) / 1_000_000, Math::max);
                        }
                    }
                }
            }, "w"));
        }
        long heapWindows = heapMb();

        // the burst: 1,000 writes, 8 writers, each to a windowed key
        ExecutorService pool = Executors.newFixedThreadPool(8);
        long[] lat = new long[1_000];
        AtomicLong n = new AtomicLong();
        long b0 = System.nanoTime();
        for (int i = 0; i < 1_000; i++) {
            int at = i;
            pool.submit(() -> {
                long id = (at * 7L) % (WINDOWS * KEYS);
                long s = System.nanoTime();
                sentAt.put(id, s);
                long base = versions.getOrDefault(id, 1L);                     // distinct keys: 7 and 10,000 share no factor
                lt.write("trades", Map.of("id", "b" + at, "key", List.of(id), "basedOn", base, "set", Map.of("qty", at)), "w");
                lat[at] = System.nanoTime() - s;
                n.incrementAndGet();
            });
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS));
        long burstMs = (System.nanoTime() - b0) / 1_000_000;
        assertEquals(1_000, n.get());
        Thread.sleep(300);                                                     // two flushes
        long burstPatches = patches.get();

        // 1,000 writes a second for 5 seconds, one writer, paced
        long[] paced = new long[5_000];
        long p0 = System.nanoTime();
        for (int i = 0; i < 5_000; i++) {
            long due = p0 + i * 1_000_000L;
            while (System.nanoTime() < due) {
                Thread.onSpinWait();
            }
            long id = (i * 13L) % ROWS;
            long s = System.nanoTime();
            sentAt.put(id, s);
            long base = versions.getOrDefault(id, 1L);
            lt.write("trades", Map.of("id", "p" + i, "key", List.of(id), "basedOn", base, "set", Map.of("qty", i)), "w");
            paced[i] = System.nanoTime() - s;
        }
        long pacedMs = (System.nanoTime() - p0) / 1_000_000;
        Thread.sleep(300);
        long heapAfter = heapMb();
        for (AutoCloseable s : subs) {
            s.close();
        }
        lt.close();
        Arrays.sort(lat);
        Arrays.sort(paced);
        System.out.printf("SCALE %s rows=%d: open+first page %d ms, page at 90,000 %d ms; heap MB: base %d, source %d, open %d, %d windows %d, after %d%n",
                tier, ROWS, openMs, farPageMs, heap0, heapSource, heapOpen, WINDOWS, heapWindows, heapAfter);
        System.out.printf("SCALE %s burst 1,000 writes (8 writers): %d ms (%.0f/s), write p50 %.2f ms p99 %.2f ms; window patches %d%n",
                tier, burstMs, 1_000_000.0 / Math.max(1, burstMs), lat[500] / 1e6, lat[990] / 1e6, burstPatches);
        System.out.printf("SCALE %s paced 1,000/s for 5 s: %d ms, write p50 %.2f ms p99 %.2f ms max %.2f ms; window patches %d, write-to-patch lag max %d ms%n",
                tier, pacedMs, paced[2_500] / 1e6, paced[4_950] / 1e6, paced[4_999] / 1e6, patches.get(), lagMax.get());
    }
}
