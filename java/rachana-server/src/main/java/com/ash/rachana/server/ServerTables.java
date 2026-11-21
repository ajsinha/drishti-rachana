/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.server;

import com.ash.rachana.SutraException;
import com.ash.rachana.live.LiveTables;
import com.ash.rachana.live.PollingDiff;
import com.ash.rachana.live.spi.Authoriser;
import com.ash.rachana.live.spi.LiveStream;
import com.ash.rachana.live.spi.SnapshotSource;
import com.ash.rachana.table.LiveTableSpec;
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
    private final ScheduledExecutorService retry;

    ServerTables(ServerConfig config, Authoriser authoriser, Log log) {
        this.log = log;
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
                return streams.get(name);
            }
        }, LiveTables.Options.DEFAULTS, null).authoriser(authoriser).audit(e -> {
            if ("read-refused".equals(e.get("event"))) {
                log.info("read-refused", "table", e.get("table"), "reason", e.get("reason"));
            }
        });
        JdbcSource.Drivers drivers = new JdbcSource.Drivers(config.drivers());
        for (ServerConfig.Table t : config.tables().values()) {
            TableSource s = TableSource.of(t, drivers);
            sources.put(t.name(), s);
            declare(s);                                              // a failed first read is pending (declare says why) and retried
        }
        retry = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread th = new Thread(r, "rachana-server-tables");
            th.setDaemon(true);
            return th;
        });
        retry.scheduleWithFixedDelay(() -> pending.keySet().forEach(n -> {
            if (declare(sources.get(n))) {
                pending.remove(n);
            }
        }), RETRY_MS, RETRY_MS, TimeUnit.MILLISECONDS);
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
