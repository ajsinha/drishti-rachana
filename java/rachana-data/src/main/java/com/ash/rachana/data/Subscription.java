/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.data;

import com.ash.rachana.table.KeyedRows;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * One reader's subscription to a table's changes ({@link MemoryHost#subscribe}): its {@code msg} frames go to a sink, in {@code seq} order.
 * With a <b>filter</b> (PROTOCOL 8.2 {@code changes.filter}) each patch is restated for the rows the reader may see: a row that stops
 * matching is a {@code remove}, a row that starts matching an {@code update} carrying every field, a matching row an {@code update} of
 * the whole row; a patch that touches none of them is still sent, empty, so the numbering has no holes. With <b>conflation</b>
 * ({@code conflate: {everyMs}}, PROTOCOL 8.4) the frames arriving within {@code everyMs} are merged by key into one message whose
 * {@code seq} is the last merged and {@code first} the first (a reader takes {@code first} as the message's start: it is no gap).
 * Thread-safe; the sink is called outside the host's lock, one frame at a time.
 */
public final class Subscription implements AutoCloseable {

    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "rdp-conflation");
        t.setDaemon(true);
        return t;
    });

    private final String table;
    private final List<String> key;
    private final Predicate<Map<String, Object>> filter;
    private final long everyMs;
    private final Consumer<Map<String, Object>> sink;
    private final Set<String> visible = new LinkedHashSet<>();
    private final List<Map<String, Object>> pending = new ArrayList<>();
    private boolean scheduled;
    private volatile boolean closed;
    final boolean recovered;
    Runnable onClose = () -> { };

    Subscription(String table, List<String> key, Predicate<Map<String, Object>> filter, long everyMs, boolean recovered,
                 Consumer<Map<String, Object>> sink) {
        this.table = table;
        this.key = key;
        this.filter = filter;
        this.everyMs = Math.max(0, everyMs);
        this.recovered = recovered;
        this.sink = sink;
    }

    /** Whether the position asked for could be resumed; false: the reader reads the rows again and subscribes from their version. */
    public boolean recovered() {
        return recovered;
    }

    public String table() {
        return table;
    }

    /** The rows the reader sees at the start (a filtered subscription), by key text. Called under the host's lock. */
    void start(List<Map<String, Object>> rows) {
        if (filter != null) {
            KeyedRows kr = new KeyedRows(key, List.of(), 0);
            rows.stream().filter(filter).forEach(r -> visible.add(kr.keyOf(r)));
        }
    }

    /** A published frame, the table's rows as they are after it. Called under the host's lock, in seq order. */
    @SuppressWarnings("unchecked")
    void offer(Map<String, Object> frame, List<Map<String, Object>> rowsAfter) {
        if (closed) {
            return;
        }
        Map<String, Object> f = frame;
        if (filter != null) {
            f = new LinkedHashMap<>(frame);
            f.put("data", restate((Map<String, Object>) frame.get("data"), rowsAfter));
        }
        synchronized (this) {
            pending.add(f);
        }
    }

    /** Delivers what is pending: now, or (conflated) once the window that started with the first pending frame ends. */
    void drain() {
        List<Map<String, Object>> out;
        synchronized (this) {
            if (closed || pending.isEmpty()) {
                return;
            }
            if (everyMs > 0) {
                if (!scheduled) {
                    scheduled = true;
                    TIMER.schedule(this::flush, everyMs, TimeUnit.MILLISECONDS);
                }
                return;
            }
            out = new ArrayList<>(pending);
            pending.clear();
            out.forEach(sink);      // under this subscription's lock: two publishers never interleave one reader's frames
        }
    }

    /** The conflation window ended: the pending frames merged into one message. */
    @SuppressWarnings("unchecked")
    void flush() {
        synchronized (this) {
            scheduled = false;
            if (closed || pending.isEmpty()) {
                return;
            }
            List<Map<String, Object>> patches = new ArrayList<>();
            pending.forEach(p -> patches.add((Map<String, Object>) p.get("data")));
            Map<String, Object> merged = new LinkedHashMap<>(pending.get(pending.size() - 1));
            if (pending.size() > 1) {
                merged.put("first", pending.get(0).get("seq"));
                merged.put("data", merge(table, key, patches));
            }
            pending.clear();
            sink.accept(merged);
        }
    }

    @Override
    public void close() {
        closed = true;
        onClose.run();
    }

    // ---- filtering -------------------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private Map<String, Object> restate(Map<String, Object> patch, List<Map<String, Object>> rowsAfter) {
        KeyedRows kr = new KeyedRows(key, List.of(), 0);
        Map<String, Map<String, Object>> now = new LinkedHashMap<>();
        rowsAfter.forEach(r -> now.put(kr.keyOf(r), r));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("table", patch.get("table"));
        if (patch.get("reset") instanceof List<?>) {
            visible.clear();
            List<Object> rows = new ArrayList<>();
            for (Map.Entry<String, Map<String, Object>> e : now.entrySet()) {
                if (filter.test(e.getValue())) {
                    visible.add(e.getKey());
                    rows.add(e.getValue());
                }
            }
            out.put("reset", rows);
            return out;
        }
        Map<String, Object> touched = new LinkedHashMap<>();             // key text -> the key values, in key order
        for (Object k : patch.get("remove") instanceof List<?> l ? l : List.of()) {
            touched.putIfAbsent(KeyedRows.keyText(k), k instanceof List<?> ? k : List.of(k));
        }
        for (String op : List.of("update", "add")) {
            for (Object r : patch.get(op) instanceof List<?> l ? l : List.of()) {
                Map<String, Object> row = (Map<String, Object>) r;
                List<Object> kv = new ArrayList<>();
                key.forEach(c -> kv.add(row.get(c)));
                touched.putIfAbsent(kr.keyOf(row), kv);
            }
        }
        List<Object> remove = new ArrayList<>();
        List<Object> update = new ArrayList<>();
        for (Map.Entry<String, Object> e : touched.entrySet()) {
            Map<String, Object> row = now.get(e.getKey());
            boolean match = row != null && filter.test(row);
            boolean was = visible.contains(e.getKey());
            if (was && !match) {
                remove.add(e.getValue());
                visible.remove(e.getKey());
            } else if (match) {
                update.add(row);
                visible.add(e.getKey());
            }
        }
        if (!remove.isEmpty()) {
            out.put("remove", remove);
        }
        if (!update.isEmpty()) {
            out.put("update", update);
        }
        return out;
    }

    // ---- conflation ------------------------------------------------------------------------------------------------

    /**
     * Patches of one table merged by key in order (PROTOCOL 8.4): a later {@code remove} cancels an earlier {@code add} or {@code update}
     * of that key, a later {@code add} after a {@code remove} stands, later fields win; a {@code reset} starts the merged state again
     * from its rows. The result has the same effect as the patches applied one by one. A table without a key concatenates its adds.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> merge(String table, List<String> key, List<Map<String, Object>> patches) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("table", table);
        KeyedRows reset = null;
        KeyedRows kr = new KeyedRows(key, List.of(), 0);
        Map<String, Object[]> ops = new LinkedHashMap<>();                 // key text -> {removed key values or null, fields or null}
        List<Object> adds = new ArrayList<>();
        for (Map<String, Object> p : patches) {
            if (p.get("reset") instanceof List<?> r) {
                List<Map<String, Object>> rows = new ArrayList<>();
                r.forEach(x -> rows.add((Map<String, Object>) x));
                reset = new KeyedRows(key, rows, 0);
                ops.clear();
                adds.clear();
                Map<String, Object> rest = new LinkedHashMap<>(p);
                rest.remove("reset");
                reset.apply(rest);
                continue;
            }
            if (reset != null) {
                reset.apply(p);
                continue;
            }
            if (key.isEmpty()) {
                adds.addAll(p.get("add") instanceof List<?> l ? l : List.of());
                continue;
            }
            for (Object k : p.get("remove") instanceof List<?> l ? l : List.of()) {
                ops.put(KeyedRows.keyText(k), new Object[] {k instanceof List<?> ? k : List.of(k), null});
            }
            for (String op : List.of("update", "add")) {
                for (Object r : p.get(op) instanceof List<?> l ? l : List.of()) {
                    Map<String, Object> row = (Map<String, Object>) r;
                    Object[] e = ops.computeIfAbsent(kr.keyOf(row), x -> new Object[2]);
                    Map<String, Object> fields = e[1] == null ? new LinkedHashMap<>() : (Map<String, Object>) e[1];
                    fields.putAll(row);
                    e[1] = fields;
                }
            }
        }
        if (reset != null) {
            out.put("reset", new ArrayList<>(reset.rows()));
            return out;
        }
        if (key.isEmpty()) {
            out.put("add", adds);
            return out;
        }
        List<Object> remove = new ArrayList<>();
        List<Object> update = new ArrayList<>();
        for (Object[] e : ops.values()) {
            if (e[0] != null) {
                remove.add(e[0]);
            }
            if (e[1] != null) {
                update.add(e[1]);
            }
        }
        if (!remove.isEmpty()) {
            out.put("remove", remove);
        }
        if (!update.isEmpty()) {
            out.put("update", update);
        }
        return out;
    }
}
