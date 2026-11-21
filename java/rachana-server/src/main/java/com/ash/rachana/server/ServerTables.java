/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.server;

import com.ash.rachana.SutraException;
import com.ash.rachana.live.FileOutboxStore;
import com.ash.rachana.live.LiveTables;
import com.ash.rachana.live.Outbox;
import com.ash.rachana.live.PollingDiff;
import com.ash.rachana.live.spi.Authoriser;
import com.ash.rachana.live.spi.LiveStream;
import com.ash.rachana.live.spi.SnapshotSource;
import com.ash.rachana.table.LiveTableSpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The configured tables as live tables ({@code rachana-live}): one {@link LiveTables} provider, each table a declaration made in code
 * whose snapshot is its {@link TableSource} and whose stream, when it has {@code refresh}, is a {@link PollingDiff} over the same source
 * (keyed patches of what changed). Columns are the declared ones, or inferred from a first read; a table whose first read fails is
 * retried every {@link #RETRY_MS} and served once it succeeds. Reads go through the {@link Authoriser} the server installs (fail-closed:
 * none installed, nothing is read).
 */
final class ServerTables implements AutoCloseable {

    static final long RETRY_MS = 10_000;

    private final Map<String, TableSource> sources = new ConcurrentHashMap<>();
    private final Map<String, LiveStream> streams = new ConcurrentHashMap<>();
    private final Map<String, String> pending = new ConcurrentHashMap<>();
    private final LiveTables live;
    private final Log log;
    private final ServerConfig config;
    private final JdbcWriter writer;
    private final Outbox outbox;
    private final ScheduledExecutorService retry;

    ServerTables(ServerConfig config, Authoriser authoriser, Log log) {
        this.log = log;
        this.config = config;
        this.live = new LiveTables(new LiveTables.Resolver() {
            @Override
            public SnapshotSource snapshot(String name) {
                SnapshotSource s = sources.get(name);
                if (s == null) {
                    throw new IllegalStateException("no source " + name);
                }
                return s;
            }

            @Override
            public LiveStream stream(String name) {
                LiveStream s = streams.get(name);
                return s != null ? s : streams.get(name + ".poll");     // a declaration may name the table whose poll it follows
            }
        }, LiveTables.Options.DEFAULTS, null).authoriser(authoriser).audit(e -> {
            String ev = String.valueOf(e.get("event"));
            if (ev.equals("read-refused") || ev.startsWith("write-")) {             // the audit log: who, what, why (never a value)
                log.info(ev, "table", e.get("table"), "user", e.get("caller") == null ? null : String.valueOf(e.get("caller")),
                        "change", e.get("change"), "columns", e.get("columns"), "reason", e.get("reason"));
            }
        });
        JdbcSource.Drivers drivers = new JdbcSource.Drivers(config.drivers());
        ServerConfig.Edits ed = config.edits();
        if (ed != null && ed.journal() != null) {                      // the edit path: the outbox over its journal, writing through JDBC
            writer = new JdbcWriter(drivers);
            try {
                outbox = new Outbox(new FileOutboxStore(ed.journal(), 10_000), writer, null, null, 5, 10_000).start(2_000);
            } catch (IOException e) {
                throw new IllegalStateException("edits.journal " + ed.journal() + " could not be opened", e);
            }
            live.sink(outbox);
            ed.off().forEach(t -> live.edits(t, false));
        } else {
            writer = null;
            outbox = null;
        }
        for (ServerConfig.Table t : config.tables().values()) {
            TableSource s = TableSource.of(t, drivers);
            sources.put(t.name(), s);
            declare(s);                                              // a failed first read is pending (declare says why) and retried
        }
        loadDeclarations(config);
        retry = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread th = new Thread(r, "rachana-server-tables");
            th.setDaemon(true);
            return th;
        });
        retry.scheduleWithFixedDelay(() -> pending.keySet().forEach(n -> {
            if (sources.containsKey(n) && declare(sources.get(n))) {
                pending.remove(n);
            }
        }), RETRY_MS, RETRY_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * The {@code *.live.yaml} declarations of the project folder (and its {@code live/} folder): each one's {@code snapshot} (and
     * {@code stream}) names a configured table, whose rows it serves under the declared name, columns and rules. A declaration with
     * problems, or naming a table that is not configured, is set aside and listed with the pending tables, never half-served.
     */
    private void loadDeclarations(ServerConfig config) {
        Path root = config.project().dir();
        if (root == null) {
            return;
        }
        for (Path dir : List.of(root, root.resolve("live"))) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            List<Path> files;
            try (var l = Files.list(dir)) {
                files = l.filter(f -> f.getFileName().toString().endsWith(LiveTableSpec.SUFFIX)).sorted().toList();
            } catch (IOException e) {
                log.warn("live-declarations-unreadable", "dir", dir.toString());
                continue;
            }
            for (Path f : files) {
                String file = f.getFileName().toString();
                try {
                    LiveTableSpec spec = LiveTableSpec.read(Files.readString(f), file, Set.of(), null);
                    TableSource src = sources.get(spec.snapshot());
                    if (src == null) {
                        refuse(file, spec.name() + ": snapshot '" + spec.snapshot() + "' is not a configured table");
                        continue;
                    }
                    if (spec.edit() != null) {                        // edits need the snapshot's writer and the outbox
                        ServerConfig.TableWrite w = config.edits().writes().get(spec.snapshot());
                        if (writer == null || w == null) {
                            refuse(file, spec.name() + ": 'edit:' needs tables." + spec.snapshot() + ".write and edits.journal");
                            continue;
                        }
                        writer.target(new JdbcWriter.Target(spec, config.tables().get(spec.snapshot()), w));
                    }
                    if (src.types() == null) {                         // its own first read may still be pending: the declared columns type it
                        Map<String, String> types = new LinkedHashMap<>();
                        for (Object c : (List<?>) spec.schema().get("columns")) {
                            Map<?, ?> cm = (Map<?, ?>) c;
                            types.put(String.valueOf(cm.get("name")), String.valueOf(cm.get("type")));
                        }
                        src.types(types);
                    }
                    live.declare(spec);
                    log.info("table", "table", spec.name(), "file", file, "snapshot", spec.snapshot());
                } catch (SutraException e) {
                    refuse(file, file + ": " + e.problems().stream().map(p -> p.code() + " " + p.message()).toList());
                } catch (IOException | IllegalStateException e) {
                    refuse(file, file + ": " + e.getClass().getSimpleName());
                }
            }
        }
    }

    private void refuse(String file, String why) {
        pending.put(file, why);
        log.warn("table-refused", "file", file, "reason", why);
    }

    /** The columns a live table's declaration edits (empty: none, or no such table). */
    List<String> declared(String table) {
        for (LiveTableSpec s : live.specs()) {
            if (s.name().equals(table)) {
                return s.editableColumns();
            }
        }
        return List.of();
    }

    /** The provider to mount on the protocol host. */
    LiveTables provider() {
        return live;
    }

    /** Tables not served yet (their first read failed), with the reason. */
    Map<String, String> pending() {
        return Map.copyOf(pending);
    }

    /** Declares a table once its columns are known; false (logged) when the first read failed. */
    private boolean declare(TableSource s) {
        ServerConfig.Table t = s.table;
        List<Map<String, Object>> columns = new ArrayList<>();
        Map<String, String> types = new LinkedHashMap<>();
        if (!t.columns().isEmpty()) {
            for (Map<String, Object> c : t.columns()) {
                Map<String, Object> col = new LinkedHashMap<>(c);
                col.putIfAbsent("type", "string");
                columns.add(col);
                types.put(String.valueOf(col.get("name")), String.valueOf(col.get("type")));
            }
        } else {
            List<Map<String, Object>> rows;
            try {
                rows = s.rows();
            } catch (Exception e) {
                String why = e instanceof TableSource.ReadFailed ? e.getMessage() : t.name() + ": " + e.getClass().getSimpleName();
                pending.put(t.name(), why);
                log.warn("table-unavailable", "table", t.name(), "reason", why, "retryMs", RETRY_MS);
                return false;
            }
            for (String n : Rows.names(rows)) {
                String type = Rows.infer(rows, n);
                types.put(n, type);
                Map<String, Object> col = new LinkedHashMap<>();
                col.put("name", n);
                col.put("type", type);
                columns.add(col);
            }
        }
        s.types(types);
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("live-table", t.name());
        if (t.title() != null) {
            doc.put("title", t.title());
        }
        doc.put("snapshot", t.name());
        if (t.refreshMs() > 0) {
            streams.put(t.name() + ".poll", new BaselinedPoll(new PollingDiff(s, t.key(), t.refreshMs())));
            doc.put("stream", t.name() + ".poll");
        }
        doc.put("key", t.key().size() == 1 ? t.key().get(0) : t.key());
        if (t.where() != null) {
            doc.put("where", t.where());
        }
        doc.put("columns", columns);
        try {
            live.declare(LiveTableSpec.of(doc, Set.of(), null));
        } catch (SutraException e) {
            String why = t.name() + ": " + e.problems().stream().map(p -> p.code() + " " + p.message()).toList();
            pending.put(t.name(), why);
            log.warn("table-refused", "table", t.name(), "reason", why);
            return false;
        }
        log.info("table", "table", t.name(), "columns", types.size(), "live", t.refreshMs() > 0);
        return true;
    }

    @Override
    public void close() {
        retry.shutdownNow();
        live.close();
        if (outbox != null) {
            try {
                outbox.close();
            } catch (IOException e) {
                log.warn("outbox-close-failed", "error", e.getClass().getSimpleName());
            }
        }
    }

    /**
     * A polling stream that takes its baseline when the engine subscribes (before the engine reads the snapshot), so a change made between
     * that read and the first timed poll is still sent.
     */
    private record BaselinedPoll(PollingDiff poll) implements LiveStream {
        @Override
        public AutoCloseable subscribe(Listener listener) {
            AutoCloseable h = poll.subscribe(listener);
            poll.poll();
            return h;
        }
    }
}
