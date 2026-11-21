/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.server;

import com.ash.rachana.data.ArrowRows;
import com.ash.rachana.live.spi.SnapshotSource;
import com.ash.rachana.parse.Json;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A configured table's rows, read whole ({@link SnapshotSource.Tier#WHOLE}; the engine filters, pages and diffs): a file, a JDBC query or
 * a REST answer. Each read gives every row in the protocol's form for the table's column types ({@link #types}), at most {@code max-rows}
 * of them (more is a refused read, never a silently cut table).
 */
abstract class TableSource implements SnapshotSource {

    /** A read that cannot be used: the reason, safe to log (no row values, no secret). */
    static final class ReadFailed extends Exception {
        ReadFailed(String message) {
            super(message, null, false, false);
        }
    }

    final ServerConfig.Table table;
    private volatile Map<String, String> types;

    TableSource(ServerConfig.Table table) {
        this.table = table;
    }

    /** The rows as the source holds them (cells as read). */
    abstract List<Map<String, Object>> load() throws Exception;

    /** The column types ({@code name -> type}) every read is shaped to; set once, from the declaration or the first read. */
    void types(Map<String, String> t) {
        types = Map.copyOf(new LinkedHashMap<>(t));
    }

    Map<String, String> types() {
        return types;
    }

    /** One read, checked against {@code max-rows}. */
    List<Map<String, Object>> rows() throws Exception {
        List<Map<String, Object>> rows = load();
        if (rows.size() > table.maxRows()) {
            throw new ReadFailed(table.name() + ": " + rows.size() + " rows is more than max-rows " + table.maxRows());
        }
        return rows;
    }

    @Override
    public Tier tier() {
        return Tier.WHOLE;
    }

    @Override
    public void read(Object filter, Consumer<Map<String, Object>> sink) throws Exception {
        Map<String, String> t = types;
        if (t == null) {
            throw new IllegalStateException(table.name() + ": read before its columns were known");
        }
        Rows.shape(rows(), t).forEach(sink);
    }

    /** The source a table's configuration names. */
    static TableSource of(ServerConfig.Table t, JdbcSource.Drivers drivers) {
        if (t.file() != null) {
            return new FileSource(t);
        }
        if (t.jdbc() != null) {
            return new JdbcSource(t, drivers);
        }
        return new RestSource(t);
    }

    /** Rows as a JSON document holds them: an array of objects, or an object with {@code rows} (or a {@code path}'s list). */
    static List<Map<String, Object>> fromJson(Object doc, String path, String what) throws ReadFailed {
        Object at = doc;
        if (path != null) {
            for (String part : path.substring(1).split("\\.")) {
                if (part.isEmpty()) {
                    continue;
                }
                at = at instanceof Map<?, ?> m ? m.get(part) : null;
            }
        } else if (doc instanceof Map<?, ?> m && m.get("rows") instanceof List<?> l) {
            at = l;
        }
        if (!(at instanceof List<?> list)) {
            throw new ReadFailed(what + ": no list of rows" + (path == null ? "" : " at " + path));
        }
        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (Object o : list) {
            if (!(o instanceof Map<?, ?>)) {
                throw new ReadFailed(what + ": a row is not an object");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> r = (Map<String, Object>) o;
            out.add(r);
        }
        return out;
    }

    /** A file: CSV, a JSON array, JSON lines, or an Arrow IPC stream; read again each time (the engine diffs). */
    static final class FileSource extends TableSource {
        private final Path file;

        FileSource(ServerConfig.Table t) {
            super(t);
            this.file = t.file();
        }

        @Override
        List<Map<String, Object>> load() throws Exception {
            String name = file.getFileName().toString();
            if (name.endsWith(".arrow")) {
                try (InputStream in = Files.newInputStream(file)) {
                    Map<String, Object> page = ArrowRows.read(in);
                    List<?> cols = (List<?>) page.get("columns");
                    List<Map<String, Object>> out = new ArrayList<>();
                    for (Object v : (List<?>) page.get("values")) {
                        List<?> cells = (List<?>) v;
                        Map<String, Object> r = new LinkedHashMap<>();
                        for (int i = 0; i < cols.size(); i++) {
                            r.put(String.valueOf(cols.get(i)), cells.get(i));
                        }
                        out.add(r);
                    }
                    return out;
                }
            }
            String text;
            try {
                text = Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new ReadFailed(table.name() + ": " + file + " could not be read (" + e.getClass().getSimpleName() + ")");
            }
            if (name.endsWith(".csv")) {
                return Rows.csv(text);
            }
            try {
                if (name.endsWith(".jsonl") || name.endsWith(".ndjson")) {
                    List<Object> docs = new ArrayList<>();
                    for (String line : text.split("\n")) {
                        if (!line.isBlank()) {
                            docs.add(Json.read(line));
                        }
                    }
                    return fromJson(docs, null, table.name());
                }
                return fromJson(Json.read(text), null, table.name());
            } catch (Json.JsonException e) {
                throw new ReadFailed(table.name() + ": " + file + " is not JSON");
            }
        }
    }
}
