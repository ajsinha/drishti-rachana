/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.data;

import com.ash.rachana.table.FilterTree;
import com.ash.rachana.table.KeyedRows;
import com.ash.rachana.table.TableSchema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The in-memory Data Protocol host (spec/1/data-protocol): named tables held as {@link KeyedRows} in their JSON form, each with its schema,
 * its change stream ({@code epoch}, {@code seq}, a replay log) and the protocol's answers. It is a {@link TableProvider}; its routes and
 * bridge are a {@link ProtocolHost} over it ({@link #protocol}), transport-free: {@link #handle} takes a request ({@code method}, a path
 * below the base, query parameters, a parsed JSON body) and answers a status and a JSON body, so the same code serves HTTP
 * ({@link ReferenceServer}), the in-process bridge ({@link #bridge}) and the tests. Filters are checked by
 * {@link FilterTree} and evaluated as the Rachana-EL condition they spell, so the semantics of section 7 are Rachana-EL's by construction.
 * Thread-safe: every operation holds the host's lock; a subscriber is called outside it.
 */
public final class MemoryHost implements TableProvider {

    /** Default limits (section 13). */
    public static final int PAGE_ROWS = RowQuery.PAGE_ROWS;
    public static final int FILTER_DEPTH = RowQuery.FILTER_DEPTH;
    public static final int LOG = 10_000;

    /** One answer: an HTTP status, a JSON body (null for none) and, for the change stream, the frames. */
    public record Answer(int status, Object body) {}

    private final RowQuery queries = new RowQuery();
    private final ProtocolHost protocol;
    private final Map<String, Table> tables = new LinkedHashMap<>();
    private final List<Consumer<Map<String, Object>>> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    private static final class Table {
        TableSchema schema;
        KeyedRows rows;
        String epoch = "t1";
        long seq;
        final List<Map<String, Object>> log = new ArrayList<>();
        final List<Subscription> subs = new java.util.concurrent.CopyOnWriteArrayList<>();
    }

    /** @param base the base address the host's documents name ({@code /dash/data/v1}); "" for none */
    public MemoryHost(String base) {
        this.protocol = new ProtocolHost(base, this);
    }

    // ---- tables ---------------------------------------------------------------------------------------------------

    /** Adds or replaces a table: its schema document and rows in JSON form; its stream starts again at {@code {epoch, 0}}. */
    public synchronized void put(Object schema, List<Map<String, Object>> rows, String epoch) {
        TableSchema s = TableSchema.of(schema);
        Table t = new Table();
        t.schema = s;
        int window = s.doc().get("append") instanceof Map<?, ?> a && a.get("window") instanceof Number w ? w.intValue() : 0;
        t.rows = new KeyedRows(s.key(), rows, window);
        t.epoch = epoch == null ? "t1" : epoch;
        tables.put(s.name(), t);
    }

    /** Removes every table. */
    public synchronized void clear() {
        tables.clear();
    }

    /** Listens to every published change frame (the {@code msg} frames of section 8.2, with {@code epoch}). */
    public void subscribe(Consumer<Map<String, Object>> listener) {
        listeners.add(listener);
    }

    public void unsubscribe(Consumer<Map<String, Object>> listener) {
        listeners.remove(listener);
    }

    /**
     * Applies a patch to a table and publishes it as the next message of its stream.
     *
     * @return the {@code msg} frame
     */
    public Map<String, Object> publish(Map<String, Object> patch) {
        Map<String, Object> frame;
        List<Subscription> subs;
        synchronized (this) {
            String name = String.valueOf(patch.get("table"));
            Table t = table(name);
            if (t.schema.key().isEmpty() && (patch.get("remove") != null || patch.get("update") != null)) {
                throw ProtocolError.of("rdp.table.no-key", 422, name + " has no key: it takes only reset and add", "table", name);
            }
            t.rows.apply(patch);
            t.seq++;
            frame = new LinkedHashMap<>();
            frame.put("t", "msg");
            frame.put("topic", "rdp." + name);
            frame.put("stream", "rdp." + name);
            frame.put("epoch", t.epoch);
            frame.put("seq", t.seq);
            frame.put("kind", "rows");
            frame.put("data", patch);
            t.log.add(frame);
            if (t.log.size() > LOG) {
                t.log.remove(0);
            }
            subs = t.subs;
            for (Subscription s : subs) {
                s.offer(frame, t.rows.rows());
            }
        }
        for (Subscription s : subs) {
            s.drain();
        }
        for (Consumer<Map<String, Object>> l : listeners) {
            l.accept(frame);
        }
        return frame;
    }

    /**
     * The frames of a table's stream after a position ({@code epoch:seq}; null for the start of this epoch): its {@code msg} frames in
     * order, or one {@code gap} frame ({@code recoverable: false}) when the position cannot be resumed (another epoch, or before the log).
     */
    public synchronized List<Map<String, Object>> changes(String name, String after) {
        Table t = table(name);
        if (t.schema.key().isEmpty() && !t.schema.doc().containsKey("append")) {
            throw ProtocolError.of("rdp.table.no-key", 422, name + " has no key: it cannot be live", "table", name);
        }
        String epoch = t.epoch;
        long seq = 0;
        if (after != null && !after.isEmpty()) {
            int colon = after.lastIndexOf(':');
            if (colon < 0) {
                throw ProtocolError.of("rdp.query.invalid", 422, "after is <epoch>:<seq>");
            }
            epoch = after.substring(0, colon);
            try {
                seq = Long.parseLong(after.substring(colon + 1));
            } catch (NumberFormatException e) {
                throw ProtocolError.of("rdp.query.invalid", 422, "after is <epoch>:<seq>");
            }
        }
        long first = t.log.isEmpty() ? t.seq + 1 : (long) t.log.get(0).get("seq");
        boolean replay = t.schema.can("replay");
        if (!epoch.equals(t.epoch) || seq > t.seq || seq + 1 < first || !replay && seq < t.seq) {
            Map<String, Object> gap = new LinkedHashMap<>();
            gap.put("t", "gap");
            gap.put("topic", "rdp." + name);
            gap.put("stream", "rdp." + name);
            gap.put("epoch", t.epoch);
            gap.put("seq", t.seq);
            gap.put("recoverable", false);
            return List.of(gap);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> f : t.log) {
            if ((long) f.get("seq") > seq) {
                out.add(f);
            }
        }
        return out;
    }

    /**
     * Subscribes to a table's changes after a position (PROTOCOL 8.2, 8.3): the frames after {@code after} are replayed, then every new
     * one follows, to {@code sink}. {@code filter} (a filter tree; the table must declare {@code changes.filter}) restates each patch for
     * the matching rows; a filtered subscription resumes only from the table's current position. {@code everyMs} above 0 conflates
     * (PROTOCOL 8.4). When the position cannot be resumed the subscription is not {@link Subscription#recovered()} and nothing is sent:
     * the reader reads the rows again and subscribes from their version.
     *
     * @throws ProtocolError {@code rdp.table.unknown}, {@code rdp.table.no-key}, {@code rdp.query.unsupported} (a filter the table cannot
     *     take), {@code rdp.query.invalid} or {@code rdp.column.unknown} (a bad filter tree)
     */
    public Subscription subscribe(String name, String after, Object filter, long everyMs, Consumer<Map<String, Object>> sink) {
        Subscription sub;
        synchronized (this) {
            Table t = table(name);
            java.util.function.Predicate<Map<String, Object>> match = null;
            if (filter != null) {
                if (!t.schema.can("changes.filter")) {
                    throw ProtocolError.of("rdp.query.unsupported", 422, name + " cannot filter its changes", "table", name, "operation",
                            "changes.filter");
                }
                try {
                    match = matcher(t.schema, filter);
                } catch (FilterTree.Refused r) {
                    throw new ProtocolError(r.code(), "rdp.forbidden".equals(r.code()) ? 403 : 422, r.getMessage(), r.fields());
                }
            }
            List<Map<String, Object>> backlog = changes(name, after);
            boolean gap = !backlog.isEmpty() && "gap".equals(backlog.get(0).get("t"));
            boolean recovered = !gap && (match == null || backlog.isEmpty());
            sub = new Subscription(name, t.schema.key(), match, everyMs, recovered, sink);
            if (!recovered) {
                return sub;
            }
            sub.start(t.rows.rows());
            for (Map<String, Object> f : backlog) {
                sub.offer(f, t.rows.rows());                 // no filter here: a filtered subscription has no backlog
            }
            t.subs.add(sub);
            Subscription s = sub;
            sub.onClose = () -> t.subs.remove(s);
        }
        sub.drain();
        return sub;
    }

    /** A filter tree as a row predicate: checked, then evaluated as the Rachana-EL condition it spells (PROTOCOL 7). */
    private java.util.function.Predicate<Map<String, Object>> matcher(TableSchema s, Object filter) {
        return queries.matcher(s, filter);
    }

    /** The schema of a table ({@code rdp.table.unknown} when there is none). */
    public synchronized TableSchema schemaOf(String name) {
        return table(name).schema;
    }

    private Table table(String name) {
        Table t = tables.get(name);
        if (t == null) {
            throw ProtocolError.of("rdp.table.unknown", 404, "no table '" + name + "'", "table", name);
        }
        return t;
    }

    // ---- the provider -----------------------------------------------------------------------------------------------

    @Override
    public synchronized boolean has(String name) {
        return tables.containsKey(name);
    }

    @Override
    public synchronized List<Map<String, Object>> tables(Object caller) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Table t : tables.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", t.schema.name());
            if (t.schema.doc().get("title") != null) {
                m.put("title", t.schema.doc().get("title"));
            }
            if (!t.schema.key().isEmpty()) {
                m.put("key", t.schema.key());
            }
            if (t.schema.can("count")) {
                m.put("rows", (long) t.rows.rows().size());
            }
            m.put("can", t.schema.doc().get("can") instanceof List<?> l ? l.stream().map(String::valueOf).toList() : List.of());
            list.add(m);
        }
        return list;
    }

    @Override
    public TableSchema schema(String name, Object caller) {
        return schemaOf(name);
    }

    @Override
    public synchronized Map<String, Object> schemaDoc(String name, Object caller) {
        Table t = table(name);
        Map<String, Object> m = new LinkedHashMap<>(t.schema.doc());
        m.put("version", version(t));
        return m;
    }

    @Override
    public synchronized Map<String, Object> version(String name, Object caller) {
        return version(table(name));
    }

    private static Map<String, Object> version(Table t) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("epoch", t.epoch);
        v.put("seq", t.seq);
        return v;
    }

    @Override
    public synchronized Map<String, Object> rows(String name, Map<String, Object> query, Object caller) {
        Table t = table(name);
        return queries.page(t.schema, t.rows.rows(), query, t.epoch, version(t));
    }

    @Override
    public List<Map<String, Object>> changes(String name, String after, Object caller) {
        return changes(name, after);
    }

    @Override
    public Subscription subscribe(String name, Subscribe request, Consumer<Map<String, Object>> sink, Object caller) {
        if (request.window() != null) {
            throw ProtocolError.of("rdp.query.unsupported", 422, name + " cannot window its changes", "table", name, "operation", "changes.window");
        }
        return subscribe(name, request.after(), request.filter(), request.everyMs(), sink);
    }

    // ---- requests -------------------------------------------------------------------------------------------------

    /** The protocol's routes and bridge over this host ({@link ProtocolHost}); add other providers to it to serve their tables too. */
    public ProtocolHost protocol() {
        return protocol;
    }

    /**
     * One request below the base: {@code GET /}, {@code GET /tables}, {@code GET /tables/{name}}, {@code GET /tables/{name}/rows},
     * {@code POST /tables/{name}/query}, {@code GET /changes} (the frames as a JSON array; the HTTP server streams them).
     */
    public Answer handle(String method, String path, Map<String, String> query, Object body) {
        ProtocolHost.Answer a = protocol.handle(method, path, query, body);
        return new Answer(a.status(), a.body());
    }

    // ---- the bridge ------------------------------------------------------------------------------------------------

    /**
     * One view → host bridge message (section 12) answered with the host → view message it produces: {@code welcome} for {@code hello},
     * {@code res} for a request, {@code pong} for {@code ping}. Without a channel back there are no subscriptions: {@code sub} is
     * refused; {@link #bridge(Consumer)} opens a channel that has them.
     */
    public Map<String, Object> bridge(Map<String, Object> m) {
        return protocol.bridge(m);
    }

    /** A bridge channel to one view: {@link Bridge#handle} answers its messages and {@code push} receives the {@code msg} frames of its subscriptions. */
    public Bridge bridge(Consumer<Map<String, Object>> push) {
        return new Bridge(protocol.bridge(push));
    }

    /** One view's bridge channel (section 12): its subscriptions by request id, closed with it. */
    public final class Bridge implements AutoCloseable {
        private final ProtocolHost.Bridge channel;

        Bridge(ProtocolHost.Bridge channel) {
            this.channel = channel;
        }

        /** Answers one view → host message (exactly one answer per request); {@code sub} and {@code unsub} as well. */
        public Map<String, Object> handle(Map<String, Object> m) {
            return channel.handle(m);
        }

        @Override
        public void close() {
            channel.close();
        }
    }
}
