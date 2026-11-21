/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.live;

import com.ash.rachana.data.ProtocolError;
import com.ash.rachana.data.ProtocolHost;
import com.ash.rachana.data.ReferenceServer;
import com.ash.rachana.data.RowQuery;
import com.ash.rachana.live.spi.Authoriser;
import com.ash.rachana.live.spi.LiveStream;
import com.ash.rachana.live.spi.ReaderView;
import com.ash.rachana.live.spi.SnapshotSource;
import com.ash.rachana.table.KeyedRows;
import com.ash.rachana.table.LiveTableSpec;
import com.ash.rachana.table.TableSchema;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * The live tables' test adapter (PROTOCOL 15 and 16, LIVE_TABLES 9): the reference server ({@link ReferenceServer}) with a
 * {@link LiveTables} provider, for the {@code protocol/window}, {@code protocol/narrow} and {@code live/*} conformance cases. Not a
 * production server: everything in memory, loopback only. Its conventions, below {@code base}, beside the reference server's:
 *
 * <ul>
 *   <li>{@code POST /_test/live} {@code {name, declaration, rows, tier?: whole|columns|query, sourceColumns?, options?: {flushMs (default 0:
 *       every patch at once), windowCapacity, maxSnapshotRows, idleCloseMs, maxIndexes, log}, readers?: {<name>: <view>}, authoriser?: false, stream?: false}}: a new case; the
 *       declaration (a {@code .live.yaml} text) served from an in-memory source holding {@code rows}, with a stream the case drives;
 *       requests are made as no reader (who reads every row) until {@code /_test/as}</li>
 *   <li>{@code POST /_test/as} {@code {reader, view?}}: later requests are made as that reader; {@code view} is {@code {audience?,
 *       rowFilter?, masked?, refuse?: forbidden|unknown}} (a reader never named is refused); {@code {reader: null}} is no reader</li>
 *   <li>{@code POST /_test/stream} {@code {source?: {put?, remove?, version?}, upsert?, delete?, gap?, state?: {up, reason?}}}: changes
 *       the source's rows or version, then pushes the stream's changes, in that order; {@code 204}</li>
 *   <li>{@code POST /_test/upkeep} {@code {waitMs?, advanceMs?}}: waits (real time, for windows that flush on a timer), moves the clock
 *       (it starts at 2026-11-21T12:00:00Z) and runs the upkeep
 *       (retention, idle close); answers {@code {open: [names]}}</li>
 *   <li>{@code POST /_test/audit}: the audit events since the last read, as a JSON array (their {@code at} left out)</li>
 *   <li>The edit path (PROTOCOL 16 Writes): a fixture's {@code edit: {sink?: outbox|applied|refuse|none (default outbox), hold?: bool,
 *       switch?: on|off, writerFails?: n}}; a reader's view takes {@code editable: [columns]}, {@code mayEdit: <reason to refuse>}, {@code approve: true} (the approve right: the
 *       tray lists others' held changes in the reader's scope, decided by writing {@code state}) and {@code mayDecide: <reason to refuse>}
 *       (the adapter's host always refuses a decision on one's own change: four eyes). The
 *       case's writer applies a change to the source (the version plus one, {@code changed-by} the writer, {@code changed-at} the clock) and
 *       pushes it on the stream, once per change id. {@code POST /_test/outbox} {@code {flush?, release?, reject?, by?, reason?}}: sends the
 *       outbox's queued changes, or decides a held one as the host; answers {@code {sent, entries: [{change, state}]}}</li>
 * </ul>
 *
 * <p>Usage: {@code java ... -m com.ash.rachana.live/com.ash.rachana.live.LiveReferenceServer [--port 0] [--base /rdp/v1]}; prints
 * {@code rdp-reference-server listening on http://127.0.0.1:<port><base>} when ready.
 */
public final class LiveReferenceServer implements AutoCloseable {

    /** A clock the cases move. */
    static final class TestClock extends Clock {
        final AtomicLong millis = new AtomicLong(Instant.parse("2026-11-21T12:00:00Z").toEpochMilli());

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public long millis() {
            return millis.get();
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis.get());
        }
    }

    /** The case's source: its rows in memory, read whole, by columns or paged by itself, as the fixture says. */
    static final class TestSource implements SnapshotSource {
        final Tier tier;
        final TableSchema schema;
        final KeyedRows keys;
        final Set<String> columns;
        final Map<String, Map<String, Object>> rows = new LinkedHashMap<>();
        volatile String version = "v1";

        TestSource(Tier tier, TableSchema schema, Collection<String> columns) {
            this.tier = tier;
            this.schema = schema;
            this.keys = new KeyedRows(schema.key(), List.of(), 0);
            this.columns = columns == null ? null : Set.copyOf(columns);
        }

        synchronized void put(Map<String, Object> r) {
            rows.put(keys.keyOf(r), new LinkedHashMap<>(r));
        }

        synchronized void remove(Object key) {
            rows.remove(KeyedRows.keyText(key));
        }

        synchronized List<Map<String, Object>> all() {
            return new ArrayList<>(rows.values());
        }

        @Override
        public Tier tier() {
            return tier;
        }

        @Override
        public String version() {
            return version;
        }

        @Override
        public void read(Object filter, Consumer<Map<String, Object>> sink) {
            all().forEach(sink);
        }

        @Override
        public void scan(List<String> cols, Object filter, Consumer<Object[]> sink) {
            for (Map<String, Object> r : all()) {
                Object[] v = new Object[cols.size()];
                for (int i = 0; i < v.length; i++) {
                    v[i] = column(cols.get(i)) || schema.key().contains(cols.get(i)) ? r.get(cols.get(i)) : null;
                }
                sink.accept(v);
            }
        }

        @Override
        public boolean column(String name) {
            return columns == null || columns.contains(name);
        }

        @Override
        public synchronized List<Map<String, Object>> fetch(Collection<List<Object>> want) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (List<Object> k : want) {
                Map<String, Object> r = rows.get(KeyedRows.keyText(k));
                if (r != null) {
                    out.add(new LinkedHashMap<>(r));
                }
            }
            return out;
        }

        @Override
        public Map<String, Object> page(Map<String, Object> query) {
            return new RowQuery().page(schema, all(), query, "source", Map.of());
        }
    }

    /** The case's stream: the engine's listener, driven by {@code /_test/stream}. */
    static final class TestStream implements LiveStream {
        volatile Listener listener;

        @Override
        public AutoCloseable subscribe(Listener l) {
            listener = l;
            return () -> listener = null;
        }
    }

    private final ReferenceServer server;
    private final Map<String, Map<String, Object>> readers = new ConcurrentHashMap<>();
    private final List<Map<String, Object>> audit = new CopyOnWriteArrayList<>();
    private volatile LiveTables tables;
    private volatile TestSource source;
    private volatile TestStream stream;
    private volatile TestClock clock;
    private volatile String name;
    private volatile Outbox outbox;
    private volatile LiveTableSpec spec;
    private final Set<String> written = ConcurrentHashMap.newKeySet();
    private final AtomicLong failures = new AtomicLong();

    public LiveReferenceServer(int port, String base) throws IOException {
        server = new ReferenceServer(port, base, null);
        server.onReset(this::dropTables);
        server.testPath("live", this::live);
        server.testPath("as", this::as);
        server.testPath("stream", this::stream);
        server.testPath("upkeep", this::upkeep);
        server.testPath("outbox", this::outbox);
        server.testPath("audit", b -> {
            List<Object> out = new ArrayList<>();
            synchronized (audit) {
                for (Map<String, Object> e : audit) {
                    Map<String, Object> m = new LinkedHashMap<>(e);
                    m.remove("at");
                    out.add(m);
                }
                audit.clear();
            }
            return new ProtocolHost.Answer(200, out);
        });
    }

    /** The reference server underneath (its {@code url()}, {@code host()}). */
    public ReferenceServer server() {
        return server;
    }

    /** The base address: {@code http://127.0.0.1:<port><base>}. */
    public String url() {
        return server.url();
    }

    @Override
    public void close() {
        LiveTables t = tables;
        if (t != null) {
            t.close();
        }
        server.close();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> body(Object b) {
        return b instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    @SuppressWarnings("unchecked")
    private synchronized ProtocolHost.Answer live(Object raw) {
        Map<String, Object> b = body(raw);
        server.reset();
        LiveTableSpec spec;
        try {
            spec = LiveTableSpec.read(String.valueOf(b.get("declaration")), "case.live.yaml");
        } catch (com.ash.rachana.SutraException e) {
            List<Object> ps = new ArrayList<>();
            e.problems().forEach(p -> ps.add(Map.of("code", p.code(), "message", p.message())));
            return new ProtocolHost.Answer(422, Map.of("error", Map.of("code", "rdp.query.invalid", "message", "the declaration has problems",
                    "problems", ps)));
        }
        SnapshotSource.Tier tier = SnapshotSource.Tier.valueOf(String.valueOf(b.getOrDefault("tier", "whole")).toUpperCase(java.util.Locale.ROOT));
        TestSource src = new TestSource(tier, TableSchema.of(spec.schema()),
                b.get("sourceColumns") instanceof List<?> l ? l.stream().map(String::valueOf).toList() : null);
        for (Object r : b.get("rows") instanceof List<?> l ? l : List.of()) {
            src.put((Map<String, Object>) r);
        }
        TestStream feed = Boolean.FALSE.equals(b.get("stream")) ? null : new TestStream();
        Map<String, Object> o = body(b.get("options"));
        LiveTables.Options opts = new LiveTables.Options(num(o, "flushMs", 0), num(o, "windowCapacity", 5_000), num(o, "maxIndexes", 4),
                num(o, "maxSnapshotRows", 2_000_000), 60_000, num(o, "idleCloseMs", 600_000), num(o, "log", 10_000), 0);
        TestClock c = new TestClock();
        LiveTables lt = new LiveTables(new LiveTables.Resolver() {
            @Override
            public SnapshotSource snapshot(String n) {
                return src;
            }

            @Override
            public LiveStream stream(String n) {
                return feed;
            }
        }, opts, c);
        lt.audit(audit::add);
        if (!Boolean.FALSE.equals(b.get("authoriser"))) {
            lt.authoriser(new Authoriser() {
                @Override
                public ReaderView view(Object caller, String table) throws Refusal {
                    return LiveReferenceServer.this.view(caller);
                }

                @Override
                public Decision mayEdit(Object caller, String table, Map<String, Object> change, Map<String, Object> row) {
                    Map<String, Object> v = caller == null ? null : readers.get(String.valueOf(caller));
                    return v != null && v.get("mayEdit") != null ? new Decision.Refuse(String.valueOf(v.get("mayEdit"))) : new Decision.Allow();
                }

                @Override
                public boolean approves(Object caller, String table) {
                    Map<String, Object> v = caller == null ? null : readers.get(String.valueOf(caller));
                    return v != null && Boolean.TRUE.equals(v.get("approve"));
                }

                @Override
                public Decision mayDecide(Object caller, String table, Map<String, Object> change) {
                    Map<String, Object> v = readers.get(String.valueOf(caller));
                    if (String.valueOf(caller).equals(change.get("by"))) {
                        return new Decision.Refuse("four eyes: never your own change");
                    }
                    return v != null && v.get("mayDecide") != null ? new Decision.Refuse(String.valueOf(v.get("mayDecide"))) : new Decision.Allow();
                }
            });
        }
        written.clear();
        Map<String, Object> edit = body(b.get("edit"));
        failures.set(edit.get("writerFails") instanceof Number n ? n.longValue() : 0);
        Outbox ob = null;
        String sinkKind = String.valueOf(edit.getOrDefault("sink", "outbox"));
        boolean hold = Boolean.TRUE.equals(edit.get("hold"));
        try {
            switch (sinkKind) {
                case "outbox" -> ob = new Outbox(new MemoryStore(), this::writeBack, hold ? ch -> "waiting for a second person" : null, c, 3, 1_000);
                case "applied" -> lt.sink(new com.ash.rachana.live.spi.ChangeSink() {
                    @Override
                    public Outcome accept(com.ash.rachana.live.spi.Change ch) throws Exception {
                        return new Outcome(State.APPLIED, "applied", writeBack(ch));
                    }
                });
                case "refuse" -> lt.sink(ch -> new com.ash.rachana.live.spi.ChangeSink.Outcome(
                        com.ash.rachana.live.spi.ChangeSink.State.REFUSED, "the source is closed for the day", null));
                default -> { }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        if (ob != null) {
            lt.sink(ob);
        }
        outbox = ob;
        if ("off".equals(edit.get("switch"))) {
            lt.edits(spec.name(), false);
        }
        body(b.get("readers")).forEach((k, v) -> readers.put(k, body(v)));
        try {
            lt.declare(spec);
        } catch (IllegalStateException e) {
            lt.close();
            return new ProtocolHost.Answer(422, Map.of("error", Map.of("code", "rdp.query.invalid", "message", e.getMessage())));
        }
        server.host().protocol().add(lt);
        tables = lt;
        this.spec = spec;
        source = src;
        stream = feed;
        clock = c;
        name = spec.name();
        return new ProtocolHost.Answer(204, null);
    }

    /** A new case: the live table of the last one gone (a reference-server fixture resets too). */
    private synchronized void dropTables() {
        readers.clear();
        audit.clear();
        LiveTables old = tables;
        if (old != null) {
            server.host().protocol().remove(old);
            old.close();
            tables = null;
        }
    }

    private static int num(Map<String, Object> o, String k, int dflt) {
        return o.get(k) instanceof Number n ? (int) Math.min(Integer.MAX_VALUE, n.longValue()) : dflt;
    }

    /** A named reader's view (no reader: every row as stored); a reader never named is refused. */
    private ReaderView view(Object caller) throws Authoriser.Refusal {
        if (caller == null) {
            return ReaderView.all();
        }
        Map<String, Object> v = readers.get(String.valueOf(caller));
        if (v == null) {
            throw new Authoriser.Refusal("no view for " + caller, false);
        }
        if (v.get("refuse") != null) {
            throw new Authoriser.Refusal("refused by the case", "unknown".equals(v.get("refuse")));
        }
        Set<String> masked = new LinkedHashSet<>();
        if (v.get("masked") instanceof List<?> l) {
            l.forEach(x -> masked.add(String.valueOf(x)));
        }
        String audience = v.get("audience") != null ? String.valueOf(v.get("audience"))
                : com.ash.rachana.parse.Json.write(v.get("rowFilter")) + "|" + masked;
        Set<String> editable = new LinkedHashSet<>();
        if (v.get("editable") instanceof List<?> l) {
            l.forEach(x -> editable.add(String.valueOf(x)));
        }
        return new ReaderView(audience, v.get("rowFilter"), masked, null, null, editable, editable.isEmpty() ? "read only" : null);
    }

    /**
     * The case's writer (the source system): applies a change once per id to the source's rows (version plus one, {@code changed-by},
     * {@code changed-at}) and pushes it on the stream, as a source's own change arrives. Fails the first {@code writerFails} times.
     */
    private Object writeBack(com.ash.rachana.live.spi.Change ch) throws IOException {
        if (failures.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
            throw new IOException("the source did not answer");
        }
        LiveTableSpec sp = spec;
        TestSource src = source;
        Map<String, Object> row;
        synchronized (src) {
            Map<String, Object> cur = src.rows.get(KeyedRows.keyText(ch.key()));
            if (!written.add(ch.id())) {                              // applied before: the same change again is already applied
                return cur == null ? null : cur.get(sp.version());
            }
            if (ch.op().equals("remove")) {
                src.remove(ch.key());
                row = null;
            } else {
                row = new LinkedHashMap<>(cur == null ? Map.of() : cur);
                for (int i = 0; i < sp.key().size(); i++) {
                    row.put(sp.key().get(i), ch.key().get(i));
                }
                row.putAll(ch.set());
                Object v = cur == null ? null : cur.get(sp.version());
                row.put(sp.version(), v instanceof Number n ? n.longValue() + 1 : 1L);
                if (sp.changedBy() != null) {
                    row.put(sp.changedBy(), ch.by());
                }
                if (sp.changedAt() != null) {
                    row.put(sp.changedAt(), clock.instant().toString());
                }
                src.put(row);
            }
        }
        LiveStream.Listener l = stream == null ? null : stream.listener;
        if (l != null) {
            if (row == null) {
                l.delete(ch.key(), null);
            } else {
                l.upsert(row, null);
            }
        }
        return row == null ? null : row.get(sp.version());
    }

    /** {@code /_test/outbox}: send the queued changes, or decide a held one as the host would. */
    private ProtocolHost.Answer outbox(Object raw) {
        Map<String, Object> b = body(raw);
        Outbox ob = outbox;
        LiveTables lt = tables;
        if (ob == null || lt == null) {
            throw ProtocolError.of("rdp.query.unsupported", 422, "the case's table has no outbox", "operation", "outbox");
        }
        String by = b.get("by") == null ? "host" : String.valueOf(b.get("by"));
        if (b.get("release") != null) {
            lt.release(name, String.valueOf(b.get("release")), by);
        }
        if (b.get("reject") != null) {
            lt.reject(name, String.valueOf(b.get("reject")), by, b.get("reason") == null ? null : String.valueOf(b.get("reason")));
        }
        int sent = Boolean.TRUE.equals(b.get("flush")) ? ob.flush() : 0;
        List<Object> entries = new ArrayList<>();
        ob.pending(name).forEach(e -> entries.add(Map.of("change", e.change().id(), "state", e.state().wire())));
        return new ProtocolHost.Answer(200, Map.of("sent", (long) sent, "entries", entries));
    }

    /** The outbox's journal in memory (the case's host has no disk to keep). */
    static final class MemoryStore implements com.ash.rachana.live.spi.OutboxStore {
        private final List<Map<String, Object>> records = new ArrayList<>();

        @Override
        public synchronized void append(Map<String, Object> record) {
            records.add(record);
        }

        @Override
        public synchronized List<Map<String, Object>> replay() {
            return new ArrayList<>(records);
        }
    }

    private ProtocolHost.Answer as(Object raw) {
        Map<String, Object> b = body(raw);
        Object reader = b.get("reader");
        if (reader != null && b.get("view") != null) {
            readers.put(String.valueOf(reader), body(b.get("view")));
        }
        server.actAs(reader == null ? null : String.valueOf(reader));
        return new ProtocolHost.Answer(204, null);
    }

    @SuppressWarnings("unchecked")
    private ProtocolHost.Answer stream(Object raw) {
        Map<String, Object> b = body(raw);
        LiveTables lt = tables;
        if (lt == null) {
            throw ProtocolError.of("rdp.table.unknown", 404, "no live table: POST /_test/live first");
        }
        Map<String, Object> s = body(b.get("source"));
        for (Object r : s.get("put") instanceof List<?> l ? l : List.of()) {
            source.put((Map<String, Object>) r);
        }
        for (Object k : s.get("remove") instanceof List<?> l ? l : List.of()) {
            source.remove(k);
        }
        if (s.get("version") != null) {
            source.version = String.valueOf(s.get("version"));
        }
        if (b.get("upsert") == null && b.get("delete") == null && b.get("gap") == null && b.get("state") == null) {
            return new ProtocolHost.Answer(204, null);
        }
        lt.opened(name);                                              // a change reaches an open table (it opens on its first read)
        LiveStream.Listener l = stream == null ? null : stream.listener;
        if (l == null) {
            throw ProtocolError.of("rdp.query.unsupported", 422, "the case's table has no stream", "operation", "stream");
        }
        for (Object r : b.get("upsert") instanceof List<?> u ? u : List.of()) {
            l.upsert((Map<String, Object>) r, null);
        }
        for (Object k : b.get("delete") instanceof List<?> d ? d : List.of()) {
            l.delete(k instanceof List<?> kl ? new ArrayList<>(kl) : java.util.Collections.singletonList(k), null);
        }
        if (b.get("state") instanceof Map<?, ?> st) {
            l.state(Boolean.TRUE.equals(st.get("up")), st.get("reason") == null ? null : String.valueOf(st.get("reason")));
        }
        if (b.get("gap") != null) {
            l.gap(String.valueOf(b.get("gap")));
        }
        return new ProtocolHost.Answer(204, null);
    }

    private ProtocolHost.Answer upkeep(Object raw) {
        Map<String, Object> b = body(raw);
        LiveTables lt = tables;
        if (lt == null) {
            throw ProtocolError.of("rdp.table.unknown", 404, "no live table: POST /_test/live first");
        }
        if (b.get("waitMs") instanceof Number w) {                  // real time: windows flush on a timer
            try {
                Thread.sleep(Math.min(5_000, w.longValue()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (b.get("advanceMs") instanceof Number n) {
            clock.millis.addAndGet(n.longValue());
        }
        lt.upkeep();
        List<Object> open = new ArrayList<>();
        lt.describe().forEach(d -> open.add(d.get("table")));
        return new ProtocolHost.Answer(200, Map.of("open", open));
    }

    public static void main(String[] args) throws Exception {
        int port = 0;
        String base = "/rdp/v1";
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--base" -> base = args[++i];
                default -> throw new IllegalArgumentException("unknown argument " + args[i]);
            }
        }
        LiveReferenceServer s = new LiveReferenceServer(port, base);
        System.out.println("rdp-reference-server listening on " + s.url());
        System.out.flush();
        Thread.currentThread().join();
    }
}
