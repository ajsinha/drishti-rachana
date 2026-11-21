/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.live;

import com.ash.rachana.SutraException;
import com.ash.rachana.SutraProblem;
import com.ash.rachana.data.ProtocolError;
import com.ash.rachana.data.RowQuery;
import com.ash.rachana.data.Subscription;
import com.ash.rachana.data.TableProvider;
import com.ash.rachana.live.spi.AuditSink;
import com.ash.rachana.live.spi.Authoriser;
import com.ash.rachana.live.spi.ChangeSink;
import com.ash.rachana.live.spi.LiveStream;
import com.ash.rachana.live.spi.ReaderView;
import com.ash.rachana.live.spi.SnapshotSource;
import com.ash.rachana.table.FilterTree;
import com.ash.rachana.table.LiveTableSpec;
import com.ash.rachana.table.TableSchema;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * The live tables of a host (LIVE_TABLES): a Data Protocol {@link TableProvider} that keeps each declared table current from its snapshot
 * source and live stream ({@link LiveTable}), pages it ({@link StoreOverlay} when the source is paged), and sends each reader the
 * patches of their own window. Rights are the host's: every request asks the {@link Authoriser} for the reader's {@link ReaderView} and
 * keeps to it (row scope pushed down, masks applied before a value can pick, order or filter a row; LIVE_TABLES 3, 5). Without an
 * authoriser every read is refused unless the host says it is a single-application bridge host ({@link #openReads()}). Writes (the
 * guarded edit path, LIVE_TABLES 1.6) need a declaration with {@code edit:}, an authoriser and a bound {@link ChangeSink} ({@link #sink});
 * a table with edits and {@code tray: true} also serves its pending changes as {@code <name>.changes}, where an approver (when the sink holds
 * changes) also sees and decides the others' held changes. One {@code LiveTable} per declaration however many readers; tables open on their first read and close when
 * nobody has read them for {@code idleCloseMs}. Mount it on a {@code ProtocolHost}. Thread-safe.
 */
public final class LiveTables implements TableProvider, AutoCloseable {

    /** The engine's settings (LIVE_TABLES 1.4 to 1.5, 7). */
    public record Options(long flushMs, int windowCapacity, int maxIndexes, int maxSnapshotRows, long indexTtlMs, long idleCloseMs, int log,
            long upkeepMs) {

        public static final Options DEFAULTS = new Options(50, 5_000, 4, 2_000_000, 60_000, 600_000, 10_000, 1_000);

        public Options withFlushMs(long ms) {
            return new Options(ms, windowCapacity, maxIndexes, maxSnapshotRows, indexTtlMs, idleCloseMs, log, upkeepMs);
        }

        public Options withWindowCapacity(int n) {
            return new Options(flushMs, n, maxIndexes, maxSnapshotRows, indexTtlMs, idleCloseMs, log, upkeepMs);
        }

        public Options withMaxSnapshotRows(int n) {
            return new Options(flushMs, windowCapacity, maxIndexes, n, indexTtlMs, idleCloseMs, log, upkeepMs);
        }

        public Options withIdleCloseMs(long ms) {
            return new Options(flushMs, windowCapacity, maxIndexes, maxSnapshotRows, indexTtlMs, ms, log, upkeepMs);
        }

        /** 0: no upkeep thread (the host calls {@link LiveTables#upkeep()}). */
        public Options withUpkeepMs(long ms) {
            return new Options(flushMs, windowCapacity, maxIndexes, maxSnapshotRows, indexTtlMs, idleCloseMs, log, ms);
        }
    }

    /** How the host resolves the names a declaration gives ({@code snapshot:}, {@code stream:}). */
    public interface Resolver {
        /** The snapshot source of that name (never null: a missing one is the host's error). */
        SnapshotSource snapshot(String name);

        /** The live stream of that name, or null when there is none (the table is static). */
        LiveStream stream(String name);
    }

    /**
     * One reader of one table for one request: their audience, the row scope (on the stored row), how a row is shown to them (masks,
     * redaction), their schema (masked columns marked) and the row filter pushed to the source.
     */
    record Reader(String audience, Predicate<Map<String, Object>> scope, UnaryOperator<Map<String, Object>> shown, TableSchema schema,
            Object rowFilter, boolean unrestricted, Set<String> editable, String readOnlyReason) {}

    private final Resolver resolver;
    private final Options options;
    private final Clock clock;
    private final RowQuery queries = new RowQuery();
    private final Map<String, LiveTableSpec> specs = new ConcurrentHashMap<>();
    private final Map<String, LiveTable> open = new ConcurrentHashMap<>();
    private final Map<String, List<SutraProblem>> problems = new ConcurrentHashMap<>();
    private volatile Authoriser authoriser;
    private volatile AuditSink audit = AuditSink.NONE;
    private volatile boolean openReads;
    private final ScheduledExecutorService upkeep;
    private final WriteGuard guard = new WriteGuard(this);
    private final Map<String, String> trays = new ConcurrentHashMap<>();          // a tray's name -> its table's

    public LiveTables(Resolver resolver, Options options, Clock clock) {
        this.resolver = resolver;
        this.options = options == null ? Options.DEFAULTS : options;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        if (this.options.upkeepMs() > 0) {
            upkeep = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "rachana-live-upkeep");
                t.setDaemon(true);
                return t;
            });
            upkeep.scheduleWithFixedDelay(this::upkeep, this.options.upkeepMs(), this.options.upkeepMs(), TimeUnit.MILLISECONDS);
        } else {
            upkeep = null;
        }
    }

    /** The host's rights hook (LIVE_TABLES 5). */
    public LiveTables authoriser(Authoriser a) {
        authoriser = a;
        return this;
    }

    /** Where audit events go. */
    public LiveTables audit(AuditSink a) {
        audit = a == null ? AuditSink.NONE : a;
        return this;
    }

    /**
     * Allows every read without an authoriser: only for a single-application bridge host (PROTOCOL 12), where the view and the host
     * are one application. Writes are never allowed this way.
     */
    public LiveTables openReads() {
        openReads = true;
        return this;
    }

    /**
     * Binds the host's write path (LT-8): its own write-back, or {@link Outbox} for a host without one. Without a sink no table takes
     * writes ({@code can} has no {@code write}).
     */
    public LiveTables sink(ChangeSink s) {
        guard.sink(s);
        return this;
    }

    /** The site switch of a table's edits (on by default): off, every write is refused ({@code 403}) and audited. */
    public LiveTables edits(String table, boolean on) {
        guard.edits(table, on);
        return this;
    }

    /** The host's decision for a held change (four eyes is the host's, LT-9): send it. Audited {@code write-released}. */
    public ChangeSink.Outcome release(String table, String changeId, String by) {
        return guard.release(table, changeId, by);
    }

    /** The host's decision for a held change: drop it with the reason. Audited {@code write-rejected}. */
    public ChangeSink.Outcome reject(String table, String changeId, String by, String reason) {
        return guard.reject(table, changeId, by, reason);
    }

    // ---- declarations ------------------------------------------------------------------------------------------------

    /** Adds or replaces a declaration (a file's, or a host's default made in code); an open table of that name is closed. */
    public void declare(LiveTableSpec spec) {
        if (spec.edit() != null && authoriser == null) {
            throw new IllegalStateException(spec.name() + " has 'edit:' and no authoriser is installed: a table with edits needs one (LT-19)");
        }
        specs.put(spec.name(), spec);
        problems.remove(spec.name());
        LiveTable t = open.remove(spec.name());
        if (t != null) {
            t.close();
        }
        String tray = spec.name() + WriteGuard.TRAY;
        if (spec.edit() != null && spec.tray()) {
            specs.put(tray, WriteGuard.traySpec(spec));
            trays.put(tray, spec.name());
        } else if (trays.remove(tray) != null) {
            specs.remove(tray);
        }
        LiveTable tt = open.remove(tray);
        if (tt != null) {
            tt.close();
        }
    }

    boolean isTray(String name) {
        return trays.containsKey(name);
    }

    LiveTableSpec spec(String name) {
        LiveTableSpec spec = specs.get(name);
        if (spec == null) {
            throw ProtocolError.of("rdp.table.unknown", 404, "no table '" + name + "'", "table", name);
        }
        return spec;
    }

    Authoriser authoriserOrNull() {
        return authoriser;
    }

    Clock clock() {
        return clock;
    }

    /**
     * Reads every {@code *.live.yaml} of a folder; a declaration with problems is set aside and listed ({@link #problems()}), never
     * half-loaded.
     */
    public void load(Path dir, Set<String> profileKeys, Predicate<String> formats) throws IOException {
        List<Path> files;
        try (var s = Files.list(dir)) {
            files = s.filter(p -> p.getFileName().toString().endsWith(LiveTableSpec.SUFFIX)).sorted().toList();
        }
        for (Path f : files) {
            String file = f.getFileName().toString();
            try {
                declare(LiveTableSpec.read(Files.readString(f), file, profileKeys, formats));
            } catch (SutraException e) {
                problems.put(file, e.problems());
            }
        }
    }

    /**
     * The screen a declaration gives (LIVE_TABLES 2.2, LT-12): a Sutra (text) that calls {@code core/live-table@1} with the declaration's
     * columns, order and edit mode, and {@code core/held-changes@1} over {@code <name>.changes} for a table with edits and a tray.
     *
     * @throws com.ash.rachana.data.ProtocolError {@code rdp.table.unknown} when no such table is declared
     */
    public String screen(String name) {
        return LiveScreens.screen(spec(name));
    }

    /** The declarations set aside, by file, with their problems. */
    public Map<String, List<SutraProblem>> problems() {
        return Map.copyOf(problems);
    }

    public List<LiveTableSpec> specs() {
        return List.copyOf(specs.values());
    }

    /** Figures for a host's health page: one entry per open table. */
    public List<Map<String, Object>> describe() {
        List<Map<String, Object>> out = new ArrayList<>();
        open.values().forEach(t -> out.add(t.describe()));
        return out;
    }

    // ---- the tables ----------------------------------------------------------------------------------------------------

    private LiveTable table(String name) {
        LiveTableSpec spec = specs.get(name);
        if (spec == null) {
            throw ProtocolError.of("rdp.table.unknown", 404, "no table '" + name + "'", "table", name);
        }
        LiveTable t = open.get(name);
        if (t == null) {
            synchronized (this) {
                t = open.get(name);
                if (t == null) {
                    String base = trays.get(name);
                    LiveTable made = base != null
                            ? new LiveTable(spec, guard.traySource(base), guard.trayStream(name), options, clock, queries)
                            : new LiveTable(spec, resolver.snapshot(spec.snapshot()),
                                    spec.stream() == null ? null : resolver.stream(spec.stream()), options, clock, queries);
                    try {
                        made.open();
                    } catch (ProtocolError e) {
                        made.close();
                        throw e;
                    } catch (Exception e) {
                        made.close();
                        throw ProtocolError.of("rdp.unavailable", 503, name + "'s snapshot could not be read", "table", name, "retry", true);
                    }
                    open.put(name, made);
                    t = made;
                }
            }
        }
        t.lastRead = clock.millis();
        return t;
    }

    /** Trims each open table's live side and closes the tables nobody has read for {@code idleCloseMs} (the upkeep thread's job). */
    public void upkeep() {
        long now = clock.millis();
        for (Map.Entry<String, LiveTable> e : open.entrySet()) {
            LiveTable t = e.getValue();
            try {
                t.trim();
            } catch (RuntimeException x) {
                // the next round tries again
            }
            if (t.subs.isEmpty() && now - t.lastRead > options.idleCloseMs()) {
                open.remove(e.getKey(), t);
                t.close();
            }
        }
    }

    /** Pushes a source change in: a test adapter's or a host callback's stream. Opens the table when it is not open. */
    LiveTable opened(String name) {
        return table(name);
    }

    @Override
    public void close() {
        if (upkeep != null) {
            upkeep.shutdownNow();
        }
        open.values().forEach(LiveTable::close);
        open.clear();
    }

    // ---- readers -------------------------------------------------------------------------------------------------------

    /** The reader's view of a table, or the refusal (audited) as the protocol's error. */
    Reader reader(Object caller, String name) {
        LiveTableSpec spec = specs.get(name);
        if (spec == null) {
            throw ProtocolError.of("rdp.table.unknown", 404, "no table '" + name + "'", "table", name);
        }
        ReaderView v;
        Authoriser a = authoriser;
        String base = trays.get(name);
        if (base != null) {
            return trayReader(caller, name, base, spec);
        }
        if (a != null) {
            try {
                v = a.view(caller, name);
            } catch (Authoriser.Refusal r) {
                event(caller, name, "read-refused", r.getMessage());
                throw r.unknown() ? ProtocolError.of("rdp.table.unknown", 404, "no table '" + name + "'", "table", name)
                        : ProtocolError.of("rdp.forbidden", 403, "you may not read " + name + ": " + r.getMessage(), "table", name);
            }
            if (v == null) {
                event(caller, name, "read-refused", "no view");
                throw ProtocolError.of("rdp.forbidden", 403, "you may not read " + name, "table", name);
            }
        } else if (openReads) {
            v = ReaderView.all();
        } else {
            event(caller, name, "read-refused", "no authoriser is installed");
            throw ProtocolError.of("rdp.forbidden", 403, "no authoriser is installed: reads are refused", "table", name);
        }
        return reader(spec, v);
    }

    /**
     * A tray's reader: whoever may read the table reads their own pending changes of it (by their name). An approver of the table
     * ({@link Authoriser#approves}) also reads the {@code held} changes of others whose rows are in their scope of the table, with the
     * values of columns masked for them masked in {@code set}, and may decide them (the column {@code state}).
     */
    private Reader trayReader(Object caller, String name, String base, LiveTableSpec spec) {
        Reader b = reader(caller, base);                                // may they read the table at all (refusals as for it)
        Authoriser a = authoriser;
        String who = a == null ? null : a.who(caller);
        if (who == null) {
            event(caller, name, "read-refused", "the caller has no name");
            throw ProtocolError.of("rdp.forbidden", 403, "a tray lists the caller's own changes: this caller has no name", "table", name);
        }
        Object mine = Map.of("op", "==", "args", List.of(Map.of("col", "by"), Map.of("lit", who)));
        if (!a.approves(caller, base)) {
            return reader(spec, new ReaderView("by:" + who, mine, Set.of(), null, null, Set.of(), "a tray is read only"));
        }
        Predicate<Map<String, Object>> test = row -> who.equals(row.get("by"))
                || "held".equals(row.get("state")) && inScope(base, b, row.get("key"));
        Set<String> masked = new java.util.HashSet<>();
        for (String c : b.schema().names()) {
            if (b.schema().column(c).masked()) {
                masked.add(c);
            }
        }
        UnaryOperator<Map<String, Object>> redact = masked.isEmpty() ? null : row -> {
            if (!(row.get("set") instanceof Map<?, ?> set)) {
                return row;
            }
            Map<String, Object> shown = new LinkedHashMap<>();
            set.forEach((k, v) -> shown.put(String.valueOf(k), masked.contains(String.valueOf(k)) ? Map.of("$masked", true) : v));
            Map<String, Object> out = new LinkedHashMap<>(row);
            out.put("set", shown);
            out.put("summary", WriteGuard.summary((String) row.get("op"), row.get("key") instanceof List<?> k ? new ArrayList<>(k) : List.of(), shown));
            return out;
        };
        return reader(spec, new ReaderView("approve:" + who + "|" + b.audience(), null, Set.of(), test, redact, Set.of(WriteGuard.STATE),
                null));
    }

    /** Whether the row a change names is in the reader's scope of the table (an unreadable or absent row is not). */
    private boolean inScope(String base, Reader b, Object key) {
        if (!(key instanceof List<?> k)) {
            return false;
        }
        try {
            Map<String, Object> row = table(base).row(new ArrayList<>(k));
            return row != null && b.scope().test(row);
        } catch (Exception e) {
            return false;
        }
    }

    /** The table a tray lists the changes of, or null when the name is not a tray. */
    String trayBase(String name) {
        return trays.get(name);
    }

    private Reader reader(LiveTableSpec spec, ReaderView v) {
        Map<String, Object> doc = spec.schema();
        if (!v.maskedColumns().isEmpty()) {
            List<Object> cols = new ArrayList<>();
            for (Object c : (List<?>) doc.get("columns")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cm = new LinkedHashMap<>((Map<String, Object>) c);
                if (v.maskedColumns().contains(String.valueOf(cm.get("name")))) {
                    cm.put("masked", true);
                }
                cols.add(cm);
            }
            doc = new LinkedHashMap<>(doc);
            doc.put("columns", cols);
        }
        TableSchema schema = TableSchema.of(doc);
        Predicate<Map<String, Object>> scope = r -> true;
        if (v.rowFilter() != null) {
            Predicate<Map<String, Object>> f = queries.matcher(TableSchema.of(spec.schema()), v.rowFilter());
            scope = scope.and(f);
        }
        if (v.rowTest() != null) {
            scope = scope.and(v.rowTest());
        }
        Set<String> masked = v.maskedColumns();
        UnaryOperator<Map<String, Object>> redact = v.redact();
        UnaryOperator<Map<String, Object>> shown = masked.isEmpty() && redact == null ? r -> r : r -> {
            Map<String, Object> m = new LinkedHashMap<>(r);
            for (String c : masked) {
                if (m.containsKey(c)) {
                    m.put(c, Map.of("$masked", true));
                }
            }
            return redact == null ? m : redact.apply(m);
        };
        return new Reader(v.audience(), scope, shown, schema, v.rowFilter(), v.unrestricted(), v.editableColumns(), v.readOnlyReason());
    }

    private void event(Object caller, String table, String event, String reason) {
        event(caller, table, event, reason, Map.of());
    }

    /** One audit event ({@link AuditSink}): its time, caller, table, name, reason and the edit path's fields. */
    void event(Object caller, String table, String event, String reason, Map<String, Object> more) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("at", clock.instant().toString());
        e.put("caller", caller);
        e.put("table", table);
        e.put("event", event);
        e.put("reason", reason);
        e.putAll(more);
        audit.record(e);
    }

    // ---- the provider -----------------------------------------------------------------------------------------------

    @Override
    public boolean has(String name) {
        return specs.containsKey(name);
    }

    @Override
    public List<Map<String, Object>> tables(Object caller) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (LiveTableSpec s : specs.values().stream().sorted(java.util.Comparator.comparing(LiveTableSpec::name)).toList()) {
            try {
                reader(caller, s.name());
            } catch (ProtocolError e) {
                continue;                                             // a table the caller may not read is not listed
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", s.name());
            if (s.title() != null) {
                m.put("title", s.title());
            }
            m.put("key", s.key());
            m.put("can", can(s));
            out.add(m);
        }
        return out;
    }

    @Override
    public TableSchema schema(String name, Object caller) {
        return reader(caller, name).schema();
    }

    @Override
    public Map<String, Object> schemaDoc(String name, Object caller) {
        Reader r = reader(caller, name);
        LiveTable t = table(name);
        Map<String, Object> doc = new LinkedHashMap<>(r.schema().doc());
        doc.put("can", can(t.spec));
        Map<String, Object> w = guard.schemaWrite(t.spec, r);
        if (w != null) {
            doc.put("write", w);
        }
        if (t.stream != null && !trays.containsKey(name)) {
            String p = t.pausedReason();
            Map<String, Object> live = new LinkedHashMap<>();
            live.put("state", p == null ? "up" : "paused");
            if (p != null) {
                live.put("reason", p);
            }
            doc.put("live", live);
        }
        if (t.capped()) {
            doc.put("capped", true);
        }
        doc.put("version", t.version());
        return doc;
    }

    /** A table's {@code can}: the declaration's, with {@code write} when it takes writes. */
    private Object can(LiveTableSpec spec) {
        Object c = spec.schema().get("can");
        if ((guard.writable(spec) || trays.containsKey(spec.name()) && guard.decides()) && c instanceof List<?> l) {
            List<Object> out = new ArrayList<>(l);
            out.add("write");
            return out;
        }
        return c;
    }

    /**
     * A write (PROTOCOL 16; LIVE_TABLES 1.6): {@code {id, op, key, basedOn, set, from?, reason?}} checked by the guard and passed to the bound
     * sink; answered {@code {change, state, message, version?}}.
     */
    @Override
    public Map<String, Object> write(String name, Object change, Object caller) {
        return guard.write(name, change, caller);
    }

    @Override
    public Map<String, Object> version(String name, Object caller) {
        reader(caller, name);
        return table(name).version();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> rows(String name, Map<String, Object> query, Object caller) {
        Reader r = reader(caller, name);
        LiveTable t = table(name);
        LiveTableSpec spec = t.spec;
        if (!spec.narrow().isEmpty() && !fixes(query.get("filter"), spec.narrow(), false)) {
            throw ProtocolError.of("rdp.query.narrow", 422, name + " is read narrowed: filter on one of " + String.join(", ", spec.narrow()),
                    "table", name, "anyOf", spec.narrow());
        }
        if (query.get("sort") instanceof List<?> sl) {                // a masked value never orders a row (PROTOCOL 11)
            for (Object o : sl) {
                if (o instanceof Map<?, ?> m && r.schema().column(String.valueOf(m.get("col"))) != null
                        && r.schema().column(String.valueOf(m.get("col"))).masked()) {
                    throw ProtocolError.of("rdp.forbidden", 403, "the column '" + m.get("col") + "' is masked for this reader", "table", name,
                            "column", m.get("col"));
                }
            }
        }
        Map<String, Object> doc = new LinkedHashMap<>(query);
        if (doc.get("sort") == null && doc.get("group") == null && !spec.sort().isEmpty()) {
            doc.put("sort", spec.sort());                             // the table's own order when the query names none
        }
        List<String> columns;
        if (doc.get("columns") instanceof List<?> l) {
            columns = l.stream().map(String::valueOf).toList();
        } else {
            columns = fixes(query.get("filter"), spec.key(), true) ? r.schema().names() : spec.listColumns();
        }
        Predicate<Map<String, Object>> filter = doc.get("filter") == null ? null : queries.matcher(r.schema(), doc.get("filter"));
        if (t.paged) {
            if (doc.get("group") != null) {
                throw ProtocolError.of("rdp.query.unsupported", 422, name + " is paged from its source: it cannot group", "table", name,
                        "operation", "group");
            }
            return t.overlay.page(doc, columns, r, filter);
        }
        List<Map<String, Object>> visible = new ArrayList<>();
        for (Map<String, Object> raw : t.held()) {
            if (r.scope().test(raw)) {
                visible.add(r.shown().apply(raw));
            }
        }
        doc.put("columns", columns);
        Map<String, Object> page = queries.page(r.schema(), visible, doc, t.epoch(), t.version());
        if (!page.containsKey("total") && page.containsKey("values") && doc.get("group") == null) {
            page.put("total", (long) (doc.get("filter") == null ? visible.size() : visible.stream().filter(filter).count()));
        }
        return page;
    }

    /**
     * Whether a filter tree fixes one of the columns ({@code all}: every one of them) with {@code ==} or {@code in} on the column, at the
     * top or and-ed at the top.
     */
    @SuppressWarnings("unchecked")
    static boolean fixes(Object filter, List<String> columns, boolean all) {
        Set<String> fixed = new java.util.HashSet<>();
        collectFixed(filter, fixed);
        return all ? !columns.isEmpty() && fixed.containsAll(columns) : columns.stream().anyMatch(fixed::contains);
    }

    private static void collectFixed(Object node, Set<String> out) {
        if (!(node instanceof Map<?, ?> m) || !(m.get("args") instanceof List<?> args)) {
            return;
        }
        Object op = m.get("op");
        if ("&&".equals(op)) {
            args.forEach(a -> collectFixed(a, out));
        } else if (("==".equals(op) || "in".equals(op)) && !args.isEmpty() && args.get(0) instanceof Map<?, ?> c && c.get("col") instanceof String col) {
            out.add(col);
        }
    }

    @Override
    public List<Map<String, Object>> changes(String name, String after, Object caller) {
        Reader r = reader(caller, name);
        LiveTable t = table(name);
        if (!r.unrestricted()) {
            List<Map<String, Object>> all = t.changes(after);
            if (!all.isEmpty() && "gap".equals(all.get(0).get("t"))) {
                return all;
            }
            // a reader with a scope or masks resumes only from now: the replay log holds rows as stored
            return all.isEmpty() ? all : t.changes(t.version().get("epoch") + ":" + t.version().get("seq"));
        }
        return t.changes(after);
    }

    @Override
    public Subscription subscribe(String name, Subscribe request, Consumer<Map<String, Object>> sink, Object caller) {
        Reader r = reader(caller, name);
        LiveTable t = table(name);
        Predicate<Map<String, Object>> match = null;
        if (request.filter() != null) {
            try {
                Predicate<Map<String, Object>> q = queries.matcher(r.schema(), request.filter());
                match = raw -> q.test(r.shown().apply(raw));
            } catch (FilterTree.Refused x) {
                throw new ProtocolError(x.code(), "rdp.forbidden".equals(x.code()) ? 403 : 422, x.getMessage(), x.fields());
            }
        }
        if (!r.unrestricted()) {
            Predicate<Map<String, Object>> scope = r.scope();
            match = match == null ? scope : scope.and(match);
        }
        boolean restated = match != null || request.window() != null || !r.unrestricted();
        Subscription sub;
        synchronized (t) {
            List<Map<String, Object>> backlog = t.changes(request.after());
            boolean gap = !backlog.isEmpty() && "gap".equals(backlog.get(0).get("t"));
            boolean recovered = !gap && (!restated || backlog.isEmpty());
            long every = request.window() != null ? Math.max(request.everyMs(), options.flushMs()) : request.everyMs();
            Predicate<Map<String, Object>> filter = match;
            sub = Subscription.open(name, t.spec.key(), match, every, recovered, sink);
            if (!r.unrestricted()) {
                sub.redact(r.shown());
            }
            if (!recovered) {
                return sub;
            }
            if (request.window() != null) {
                Predicate<Map<String, Object>> qf = request.filter() == null ? null : queries.matcher(r.schema(), request.filter());
                if (t.paged) {
                    t.overlay.prepare(request.filter(), r, qf);
                }
                Object tree = request.filter();
                sub.window(request.window(), request.top(), t.spec.newestFirst(), options.windowCapacity(),
                        () -> t.paged ? t.overlay.count(tree, r, qf) : t.held().stream().filter(x -> filter == null || filter.test(x)).count());
            }
            sub.start(t.held());
            for (Map<String, Object> f : backlog) {
                sub.offer(f, t::current);
            }
            t.subs.add(sub);
            Subscription s = sub;
            sub.onClose(() -> t.subs.remove(s));
        }
        sub.drain();
        return sub;
    }
}
