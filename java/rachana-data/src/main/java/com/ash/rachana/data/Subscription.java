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
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * One reader's subscription to a table's changes ({@link MemoryHost#subscribe}): its {@code msg} frames go to a sink, in {@code seq} order.
 * With a <b>filter</b> (PROTOCOL 8.2 {@code changes.filter}) each patch is restated for the rows the reader may see: a row that stops
 * matching is a {@code remove}, a row that starts matching an {@code update} carrying every field, a matching row an {@code update} of
 * the whole row; a patch that touches none of them is still sent, empty, so the numbering has no holes. With <b>conflation</b>
 * ({@code conflate: {everyMs}}, PROTOCOL 8.4) the frames arriving within {@code everyMs} are merged by key into one message whose
 * {@code seq} is the last merged and {@code first} the first (a reader takes {@code first} as the message's start: it is no gap).
 * With a <b>window</b> (LIVE_TABLES 1.5, PROTOCOL 16 {@code changes.window}) it is sent changes of the keys the reader's page shows
 * only, each patch carrying the reader's {@code total}; with {@code top} in a newest-first order a new or changed row is sent to insert
 * on top (the patch's {@code top}); a reader more than {@code capacity} keys behind gets one {@code gap} frame ({@code recoverable:
 * false}) and the subscription ends. With a <b>redaction</b> every row sent is first shown as the reader may see it (masks).
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
    private volatile UnaryOperator<Map<String, Object>> redact;
    private Set<String> window;                      // null: no window (guarded by this)
    private boolean top;
    private boolean newestFirst;
    private int capacity = Integer.MAX_VALUE;
    private volatile LongSupplier total;
    private final Set<String> pendingKeys = new java.util.HashSet<>();

    /**
     * A subscription for a provider of another module ({@link TableProvider#subscribe}): the provider calls {@link #start}, then
     * {@link #offer(Map, Function)} for every frame under its own lock and {@link #drain()} outside it, and {@link #onClose} to forget it.
     */
    public static Subscription open(String table, List<String> key, Predicate<Map<String, Object>> filter, long everyMs, boolean recovered,
            Consumer<Map<String, Object>> sink) {
        return new Subscription(table, key, filter, everyMs, recovered, sink);
    }

    /** What closing it also does (the provider forgets it). */
    public void onClose(Runnable r) {
        onClose = r == null ? () -> { } : r;
    }

    /** Every row sent is first shown as the reader may see it (their masks); the filter is the provider's (it sees the stored row). */
    public Subscription redact(UnaryOperator<Map<String, Object>> r) {
        redact = r;
        return this;
    }

    /**
     * Sets or changes the window: the keys the reader's page shows (each the list of key values), whether that page starts at the first row
     * of the table's own order ({@code top}) and whether that order is newest first; {@code capacity} keys pending at most; {@code total}
     * the reader's count, read when a patch is delivered (never a read of the source).
     */
    public synchronized Subscription window(java.util.Collection<? extends List<?>> keys, boolean top, boolean newestFirst, int capacity,
            LongSupplier total) {
        Set<String> w = new java.util.HashSet<>();
        keys.forEach(k -> w.add(KeyedRows.keyText(k)));
        this.window = w;
        this.top = top;
        this.newestFirst = newestFirst;
        this.capacity = Math.max(1, capacity);
        this.total = total;
        return this;
    }

    /** Changes the keys and {@code top} of a window already set (a {@code win} message); its other settings stay. */
    public synchronized Subscription window(java.util.Collection<? extends List<?>> keys, boolean top) {
        return window(keys, top, newestFirst, capacity, total);
    }

    /** Ends it with one frame (a {@code gap}: the provider started a new epoch), delivered now; nothing pending is sent. */
    public void end(Map<String, Object> frame) {
        synchronized (this) {
            if (closed) {
                return;
            }
            pending.clear();
            pendingKeys.clear();
            sink.accept(frame);
            closed = true;
        }
        onClose.run();
    }

    /** Whether it has a window. */
    public synchronized boolean windowed() {
        return window != null;
    }

    /** Whether it was ended (closed, or behind by more than its capacity). */
    public boolean closed() {
        return closed;
    }

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
    public void start(java.util.Collection<Map<String, Object>> rows) {
        if (filter != null) {
            KeyedRows kr = new KeyedRows(key, List.of(), 0);
            rows.stream().filter(filter).forEach(r -> visible.add(kr.keyOf(r)));
        }
    }

    /** A published frame, the table's rows as they are after it. Called under the host's lock, in seq order. */
    void offer(Map<String, Object> frame, List<Map<String, Object>> rowsAfter) {
        if (filter == null && redact == null && !windowed()) {
            offer(frame, (Function<String, Map<String, Object>>) null);
            return;
        }
        KeyedRows kr = new KeyedRows(key, List.of(), 0);
        Map<String, Map<String, Object>> now = new LinkedHashMap<>();
        rowsAfter.forEach(r -> now.put(kr.keyOf(r), r));
        offer(frame, now::get);
    }

    /**
     * A published frame; {@code rowAfter} answers a key text's row as it is after the frame (null: not in the table). Called under the
     * provider's lock, in seq order.
     */
    @SuppressWarnings("unchecked")
    public void offer(Map<String, Object> frame, Function<String, Map<String, Object>> rowAfter) {
        if (closed) {
            return;
        }
        Map<String, Object> f = frame;
        synchronized (this) {
            if (filter != null || redact != null || window != null) {
                f = new LinkedHashMap<>(frame);
                f.put("data", restate((Map<String, Object>) frame.get("data"), rowAfter));
            }
            pending.add(f);
            if (window != null && f.get("data") instanceof Map<?, ?> d) {
                for (String op : List.of("update", "remove")) {
                    for (Object o : d.get(op) instanceof List<?> l ? l : List.of()) {
                        pendingKeys.add(op.equals("remove") ? KeyedRows.keyText(o) : new KeyedRows(key, List.of(), 0).keyOf((Map<String, Object>) o));
                    }
                }
                if (pendingKeys.size() > capacity) {
                    Map<String, Object> gap = new LinkedHashMap<>();
                    gap.put("t", "gap");
                    gap.put("topic", frame.get("topic"));
                    gap.put("stream", frame.get("stream"));
                    gap.put("epoch", frame.get("epoch"));
                    gap.put("seq", frame.get("seq"));
                    gap.put("recoverable", false);
                    pending.clear();
                    pendingKeys.clear();
                    pending.add(gap);
                    overflow = true;
                }
            }
        }
    }

    private boolean overflow;

    /** Delivers what is pending: now, or (conflated) once the window that started with the first pending frame ends. */
    public void drain() {
        List<Map<String, Object>> out;
        long tot = everyMs > 0 ? -1 : totalNow();
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
            pendingKeys.clear();
            out.forEach(f -> sink.accept(withTotal(f, tot)));   // under this subscription's lock: two publishers never interleave one reader's frames
            if (overflow) {
                closed = true;
            }
        }
        if (overflow) {
            onClose.run();
        }
    }

    /** The reader's total, read before this subscription's lock is taken (the provider's lock comes first); -1 for none. */
    private long totalNow() {
        LongSupplier t = total;
        return t == null || closed ? -1 : t.getAsLong();
    }

    /** A windowed patch carries the reader's total as it is when delivered. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> withTotal(Map<String, Object> f, long tot) {
        if (window == null || total == null || tot < 0 || !(f.get("data") instanceof Map<?, ?> d)) {
            return f;
        }
        Map<String, Object> data = new LinkedHashMap<>((Map<String, Object>) d);
        data.put("total", tot);
        Map<String, Object> out = new LinkedHashMap<>(f);
        out.put("data", data);
        return out;
    }

    /** The conflation window ended: the pending frames merged into one message. */
    @SuppressWarnings("unchecked")
    void flush() {
        long tot = totalNow();
        synchronized (this) {
            scheduled = false;
            if (closed || pending.isEmpty()) {
                return;
            }
            if (overflow) {
                pending.forEach(sink);
                pending.clear();
                closed = true;
            } else {
                List<Map<String, Object>> patches = new ArrayList<>();
                List<Object> tops = new ArrayList<>();
                pending.forEach(p -> {
                    Map<String, Object> d = (Map<String, Object>) p.get("data");
                    patches.add(d);
                    if (d.get("top") instanceof List<?> t) {
                        tops.addAll(t);
                    }
                });
                Map<String, Object> merged = new LinkedHashMap<>(pending.get(pending.size() - 1));
                if (pending.size() > 1) {
                    merged.put("first", pending.get(0).get("seq"));
                    Map<String, Object> data = merge(table, key, patches);
                    if (!tops.isEmpty()) {
                        data.put("top", tops);
                    }
                    merged.put("data", data);
                }
                pending.clear();
                pendingKeys.clear();
                sink.accept(withTotal(merged, tot));
                return;
            }
        }
        onClose.run();
    }

    @Override
    public void close() {
        closed = true;
        onClose.run();
    }

    // ---- filtering -------------------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private Map<String, Object> restate(Map<String, Object> patch, Function<String, Map<String, Object>> rowAfter) {
        KeyedRows kr = new KeyedRows(key, List.of(), 0);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("table", patch.get("table"));
        if (patch.get("reset") instanceof List<?> reset) {
            visible.clear();
            List<Object> rows = new ArrayList<>();
            for (Object o : reset) {
                Map<String, Object> r = rowAfter.apply(kr.keyOf((Map<String, Object>) o));
                if (r != null && (filter == null || filter.test(r))) {
                    visible.add(kr.keyOf(r));
                    rows.add(shown(r));
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
        List<Object> top = new ArrayList<>();
        for (Map.Entry<String, Object> e : touched.entrySet()) {
            Map<String, Object> row = rowAfter.apply(e.getKey());
            boolean match = row != null && (filter == null || filter.test(row));
            boolean was = window != null ? window.contains(e.getKey()) : filter == null || visible.contains(e.getKey());
            if (window != null && !was) {
                if (match && this.top && newestFirst) {
                    update.add(shown(row));                   // new or changed in a newest-first order: on top of the reader's page
                    top.add(e.getValue());
                    window.add(e.getKey());
                }
                continue;                                     // not on the reader's page: only the total moves
            }
            if (was && !match) {
                remove.add(e.getValue());
                visible.remove(e.getKey());
                if (window != null) {
                    window.remove(e.getKey());
                }
            } else if (match) {
                update.add(shown(row));
                visible.add(e.getKey());
            }
        }
        if (!remove.isEmpty()) {
            out.put("remove", remove);
        }
        if (!update.isEmpty()) {
            out.put("update", update);
        }
        if (!top.isEmpty()) {
            out.put("top", top);
        }
        return out;
    }

    private Map<String, Object> shown(Map<String, Object> row) {
        UnaryOperator<Map<String, Object>> r = redact;
        return r == null ? row : r.apply(row);
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
