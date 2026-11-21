/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.live;

import com.ash.rachana.api.Node;
import com.ash.rachana.data.ProtocolError;
import com.ash.rachana.data.RowQuery;
import com.ash.rachana.data.Subscription;
import com.ash.rachana.el.ElCompiler;
import com.ash.rachana.el.EvalContext;
import com.ash.rachana.el.Expr;
import com.ash.rachana.el.Values;
import com.ash.rachana.live.spi.LiveStream;
import com.ash.rachana.live.spi.SnapshotSource;
import com.ash.rachana.table.KeyedRows;
import com.ash.rachana.table.LiveTableSpec;
import com.ash.rachana.table.TableSchema;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * One live table (LIVE_TABLES 1.1 to 1.4): a declaration's snapshot merged by key and record version with its stream's changes, shared by
 * every reader. The stream is subscribed before the snapshot is read and what arrived meanwhile is applied after it, by version: nothing
 * is lost and nothing applied twice. Every applied change takes the next {@code seq} of the table's stream ({@code epoch}, {@code seq});
 * a gap, a reconnect that cannot resume or a trimmed paged table starts a new epoch and every reader resyncs. A whole-tier table holds
 * its rows; a paged one ({@link StoreOverlay}) holds only the live side: rows changed since it opened and keys removed. Nothing here
 * belongs to a person: readers' views are applied by {@link LiveTables} on every read and in every subscription. Thread-safe.
 */
final class LiveTable implements LiveStream.Listener {

    /** One row held: as the source holds it, its record version, when it arrived here. */
    record Row(Map<String, Object> row, Object version, Instant arrived) {}

    final LiveTableSpec spec;
    final TableSchema schema;
    final SnapshotSource source;
    final LiveStream stream;
    final LiveTables.Options options;
    final Clock clock;
    final RowQuery queries;
    final Predicate<Map<String, Object>> where;
    final boolean paged;
    final StoreOverlay overlay;
    private final KeyedRows keys;

    private final Map<String, Row> rows = new LinkedHashMap<>();     // whole: every row; paged: the live side
    private final Set<String> removed = new HashSet<>();               // paged: keys the live side deleted or that left the condition
    private final List<Map<String, Object>> log = new ArrayList<>();
    final List<Subscription> subs = new CopyOnWriteArrayList<>();
    private final Deque<Object[]> early = new ArrayDeque<>();
    private boolean ready;
    private int epochs;
    private String epoch;
    private long seq;
    private volatile boolean capped;
    private volatile String paused;
    private AutoCloseable handle;
    volatile long lastRead;
    final AtomicLong applied = new AtomicLong();
    final AtomicLong stale = new AtomicLong();
    final AtomicLong skipped = new AtomicLong();
    final AtomicLong gaps = new AtomicLong();
    final AtomicLong expired = new AtomicLong();

    LiveTable(LiveTableSpec spec, SnapshotSource source, LiveStream stream, LiveTables.Options options, Clock clock, RowQuery queries) {
        this.spec = spec;
        this.schema = TableSchema.of(spec.schema());
        this.source = source;
        this.stream = stream;
        this.options = options;
        this.clock = clock;
        this.queries = queries;
        this.keys = new KeyedRows(spec.key(), List.of(), 0);
        this.where = condition(spec.where());
        this.paged = source.tier() != SnapshotSource.Tier.WHOLE;
        this.overlay = paged ? new StoreOverlay(this) : null;
        if (paged && spec.where() != null && spec.whereTree() == null) {
            throw ProtocolError.of("rdp.unavailable", 503, spec.name() + ": its condition cannot be pushed down to a paged source (RCH-2074)",
                    "table", spec.name());
        }
        this.lastRead = clock.millis();
    }

    private Predicate<Map<String, Object>> condition(String el) {
        if (el == null) {
            return r -> true;
        }
        Expr cond = new ElCompiler(16).compile(el);
        EvalContext ctx = EvalContext.of(Node.of(Map.of()), null);
        return row -> Values.truthy(cond.eval(ctx.withRow(Node.of(schema.decode(row)), 0)));
    }

    // ---- lifecycle -------------------------------------------------------------------------------------------------

    /** Subscribes to the stream, reads the snapshot, then applies what arrived meanwhile. */
    void open() throws Exception {
        synchronized (this) {
            epoch = "e" + (++epochs);
            seq = 0;
        }
        if (stream != null) {
            handle = stream.subscribe(this);
        }
        readSnapshot();
        synchronized (this) {
            while (!early.isEmpty()) {
                Object[] e = early.pollFirst();
                if (e[0] instanceof Map<?, ?>) {
                    apply(cast(e[0]), false);
                } else {
                    remove(KeyedRows.keyText(e[1]), false);
                }
            }
            ready = true;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object o) {
        return (Map<String, Object>) o;
    }

    private void readSnapshot() throws Exception {
        if (paged) {
            overlay.drop();
            return;
        }
        Map<String, Row> read = new LinkedHashMap<>();
        boolean[] cap = {false};
        Instant now = Instant.now(clock);
        source.read(spec.whereTree(), r -> {
            if (!where.test(r)) {
                return;
            }
            if (read.size() >= options.maxSnapshotRows()) {
                cap[0] = true;
                return;
            }
            read.put(keys.keyOf(r), new Row(new LinkedHashMap<>(r), version(r), now));
        });
        synchronized (this) {
            rows.clear();
            rows.putAll(read);
            capped = cap[0];
        }
    }

    void close() {
        AutoCloseable h = handle;
        handle = null;
        if (h != null) {
            try {
                h.close();
            } catch (Exception e) {
                // the stream's own business
            }
        }
        subs.forEach(Subscription::close);
        subs.clear();
    }

    // ---- the stream ------------------------------------------------------------------------------------------------

    @Override
    public void upsert(Map<String, Object> row, String position) {
        synchronized (this) {
            if (!ready) {
                early.addLast(new Object[] {new LinkedHashMap<>(row), null});
                return;
            }
            apply(new LinkedHashMap<>(row), true);
        }
        drain();
    }

    @Override
    public void delete(List<Object> key, String position) {
        synchronized (this) {
            if (!ready) {
                early.addLast(new Object[] {null, key});
                return;
            }
            remove(KeyedRows.keyText(key), true);
        }
        drain();
    }

    @Override
    public void gap(String reason) {
        gaps.incrementAndGet();
        try {
            synchronized (this) {
                ready = false;
            }
            readSnapshot();
            synchronized (this) {
                if (paged) {
                    rows.clear();
                    removed.clear();
                }
                ready = true;
                newEpoch();
            }
        } catch (Exception e) {
            synchronized (this) {
                ready = true;
                paused = "the snapshot could not be read again after a gap: " + e.getClass().getSimpleName();
            }
        }
    }

    @Override
    public void state(boolean up, String reason) {
        paused = up ? null : (reason == null ? "paused" : reason);
    }

    /** A new epoch: the log is dropped and every reader is sent a gap (it reads again and subscribes from the new position). */
    private void newEpoch() {
        epoch = "e" + (++epochs);
        seq = 0;
        log.clear();
        Map<String, Object> gap = new LinkedHashMap<>();
        gap.put("t", "gap");
        gap.put("topic", "rdp." + spec.name());
        gap.put("stream", "rdp." + spec.name());
        gap.put("epoch", epoch);
        gap.put("seq", 0L);
        gap.put("recoverable", false);
        for (Subscription s : subs) {
            s.end(gap);
        }
        subs.clear();
    }

    /** Merges one whole row by key and version (under the lock). */
    private void apply(Map<String, Object> row, boolean push) {
        String k = keys.keyOf(row);
        Row old = rows.get(k);
        Object v = version(row);
        if (old != null && spec.version() != null) {
            if (KeyedRows.versionOrder(old.version(), v) <= 0) {
                stale.incrementAndGet();                              // a duplicate or an older version: never back in time
                return;
            }
            if (old.version() instanceof Number a && v instanceof Number b && b.longValue() > a.longValue() + 1) {
                skipped.incrementAndGet();                            // versions skipped: the row is whole, so the newest is still right
            }
        }
        if (!where.test(row)) {
            if (old != null || paged) {
                remove(k, push);                                      // it stops holding the condition: it leaves
            }
            return;
        }
        rows.put(k, new Row(row, v, Instant.now(clock)));
        removed.remove(k);
        applied.incrementAndGet();
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("table", spec.name());
        patch.put("update", List.of(row));
        publish(patch, push);
    }

    private void remove(String k, boolean push) {
        Row old = rows.remove(k);
        if (old == null && !paged) {
            return;
        }
        if (paged) {
            removed.add(k);
        }
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("table", spec.name());
        patch.put("remove", List.of(keyValues(k, old)));
        publish(patch, push);
    }

    private List<Object> keyValues(String k, Row old) {
        if (old != null) {
            List<Object> kv = new ArrayList<>();
            spec.key().forEach(c -> kv.add(old.row().get(c)));
            return kv;
        }
        return KeyText.values(k, spec.key().size());
    }

    private Object version(Map<String, Object> row) {
        return spec.version() == null ? null : row.get(spec.version());
    }

    private void publish(Map<String, Object> patch, boolean push) {
        seq++;
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("t", "msg");
        frame.put("topic", "rdp." + spec.name());
        frame.put("stream", "rdp." + spec.name());
        frame.put("epoch", epoch);
        frame.put("seq", seq);
        frame.put("kind", "rows");
        frame.put("data", patch);
        log.add(frame);
        if (log.size() > options.log()) {
            log.remove(0);
        }
        if (overlay != null) {
            overlay.changed();
        }
        for (Subscription s : subs) {
            s.offer(frame, this::current);
        }
    }

    /** Delivers what the subscriptions hold (outside the lock). */
    void drain() {
        for (Subscription s : subs) {
            s.drain();
        }
    }

    /**
     * The row of a key as the table shows it now, as stored (the edit path's version guard): the held row, or for a paged table the live
     * side's, else the source's own (fetched by key, or a one-row page of a query-tier source) when it holds the condition; null when
     * the table has no such row.
     */
    Map<String, Object> row(List<Object> key) throws Exception {
        String k = KeyedRows.keyText(key);
        synchronized (this) {
            Row r = rows.get(k);
            if (r != null) {
                return r.row();
            }
            if (!paged || removed.contains(k)) {
                return null;
            }
        }
        List<Map<String, Object>> got;
        try {
            got = source.fetch(List.of(key));
        } catch (UnsupportedOperationException e) {
            List<Object> eq = new ArrayList<>();
            for (int i = 0; i < spec.key().size(); i++) {
                eq.add(Map.of("op", "==", "args", List.of(Map.of("col", spec.key().get(i)), Map.of("lit", key.get(i)))));
            }
            Map<String, Object> page = source.page(Map.of("filter", eq.size() == 1 ? eq.get(0) : Map.of("op", "&&", "args", eq), "limit", 1L));
            got = com.ash.rachana.data.TableClient.rows(page);
        }
        if (got.isEmpty() || !where.test(got.get(0))) {
            return null;
        }
        synchronized (this) {                                         // it may have arrived on the stream meanwhile
            Row r = rows.get(k);
            return r != null ? r.row() : removed.contains(k) ? null : got.get(0);
        }
    }

    /** A key's row as the table holds it now (the live side's for a paged table; null when not held or removed). */
    synchronized Map<String, Object> current(String keyText) {
        Row r = rows.get(keyText);
        return r == null ? null : r.row();
    }

    // ---- retention ---------------------------------------------------------------------------------------------------

    /** Drops rows of the live side past the retention (age by its column, else arrival) and beyond its row cap, oldest first. */
    int trim() {
        LiveTableSpec.Retention ret = spec.retention();
        if (ret == null) {
            return 0;
        }
        int dropped = 0;
        synchronized (this) {
            Instant now = Instant.now(clock);
            List<String> drop = new ArrayList<>();
            if (ret.hasTtl()) {
                Instant cut = now.minusMillis(ret.ttlMs());
                rows.forEach((k, r) -> {
                    if (age(r).isBefore(cut)) {
                        drop.add(k);
                    }
                });
            }
            if (ret.hasMaxRows() && rows.size() - drop.size() > ret.maxRows()) {
                List<Map.Entry<String, Row>> all = new ArrayList<>(rows.entrySet());
                all.removeIf(e -> drop.contains(e.getKey()));
                all.sort(Comparator.comparing(e -> age(e.getValue())));
                for (int i = 0; i < all.size() - ret.maxRows(); i++) {
                    drop.add(all.get(i).getKey());
                }
            }
            for (String k : drop) {
                if (paged) {
                    rows.remove(k);                                   // the source's row shows again: readers resync below
                } else {
                    remove(k, true);
                }
                dropped++;
            }
            if (paged && dropped > 0) {
                newEpoch();
            }
        }
        expired.addAndGet(dropped);
        drain();
        return dropped;
    }

    private Instant age(Row r) {
        String on = spec.retention().on();
        Instant t = on == null ? null : instant(r.row().get(on));
        return t == null ? r.arrived() : t;
    }

    static Instant instant(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            long x = n.longValue();
            return x > 100_000_000_000L ? Instant.ofEpochMilli(x) : Instant.ofEpochSecond(x);
        }
        String s = String.valueOf(v).trim();
        for (Function<String, Instant> f : List.<Function<String, Instant>>of(Instant::parse, t -> OffsetDateTime.parse(t).toInstant(),
                t -> LocalDateTime.parse(t).toInstant(ZoneOffset.UTC), t -> LocalDate.parse(t.length() >= 10 ? t.substring(0, 10) : t)
                        .atStartOfDay().toInstant(ZoneOffset.UTC))) {
            try {
                return f.apply(s);
            } catch (RuntimeException e) {
                // the next shape
            }
        }
        return null;
    }

    // ---- reading ---------------------------------------------------------------------------------------------------

    synchronized Map<String, Object> version() {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("epoch", epoch);
        v.put("seq", seq);
        return v;
    }

    synchronized String epoch() {
        return epoch;
    }

    String pausedReason() {
        return paused;
    }

    boolean capped() {
        return capped;
    }

    /** The whole-tier rows (a copy), or the live side of a paged table, as the source holds them. */
    synchronized List<Map<String, Object>> held() {
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        rows.values().forEach(r -> out.add(r.row()));
        return out;
    }

    /** The live side by key text, and the removed keys (a paged table's page merges both). */
    synchronized Object[] liveSide() {
        Map<String, Map<String, Object>> live = new LinkedHashMap<>();
        rows.forEach((k, r) -> live.put(k, r.row()));
        return new Object[] {live, new HashSet<>(removed)};
    }

    String keyOf(Map<String, Object> row) {
        return keys.keyOf(row);
    }

    /** The frames after a position, or one gap frame (as {@code MemoryHost.changes}). */
    synchronized List<Map<String, Object>> changes(String after) {
        String e = epoch;
        long s = 0;
        if (after != null && !after.isEmpty()) {
            int colon = after.lastIndexOf(':');
            if (colon < 0) {
                throw ProtocolError.of("rdp.query.invalid", 422, "after is <epoch>:<seq>");
            }
            e = after.substring(0, colon);
            try {
                s = Long.parseLong(after.substring(colon + 1));
            } catch (NumberFormatException x) {
                throw ProtocolError.of("rdp.query.invalid", 422, "after is <epoch>:<seq>");
            }
        }
        long first = log.isEmpty() ? seq + 1 : (long) log.get(0).get("seq");
        if (!e.equals(epoch) || s > seq || s + 1 < first) {
            Map<String, Object> gap = new LinkedHashMap<>();
            gap.put("t", "gap");
            gap.put("topic", "rdp." + spec.name());
            gap.put("stream", "rdp." + spec.name());
            gap.put("epoch", epoch);
            gap.put("seq", seq);
            gap.put("recoverable", false);
            return List.of(gap);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> f : log) {
            if ((long) f.get("seq") > s) {
                out.add(f);
            }
        }
        return out;
    }

    /** Figures for a host's health page. */
    synchronized Map<String, Object> describe() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("table", spec.name());
        m.put("tier", source.tier().name().toLowerCase(java.util.Locale.ROOT));
        m.put("rows", rows.size());
        m.put("removed", removed.size());
        m.put("epoch", epoch);
        m.put("seq", seq);
        m.put("applied", applied.get());
        m.put("staleDropped", stale.get());
        m.put("versionGaps", skipped.get());
        m.put("gaps", gaps.get());
        m.put("expired", expired.get());
        m.put("readers", subs.size());
        m.put("capped", capped);
        m.put("paused", paused);
        if (overlay != null) {
            m.put("indexes", overlay.indexes());
        }
        return m;
    }

    /** Key texts back to key values (a removal of a key the table does not hold). */
    static final class KeyText {
        private KeyText() {
        }

        static List<Object> values(String keyText, int parts) {
            List<Object> out = new ArrayList<>();
            for (String p : keyText.split(String.valueOf(KeyedRows.SEP), -1)) {
                out.add(com.ash.rachana.parse.Json.read(p));
            }
            return out;
        }
    }
}
