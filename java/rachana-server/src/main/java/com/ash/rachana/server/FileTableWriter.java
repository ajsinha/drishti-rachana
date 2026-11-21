/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.server;

import com.ash.rachana.live.spi.Change;
import com.ash.rachana.live.spi.ChangeWriter;
import com.ash.rachana.parse.Json;
import com.ash.rachana.table.KeyedRows;
import com.ash.rachana.table.LiveTableSpec;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedHashSet;

/**
 * The server's {@link ChangeWriter} over a file table (LIVE_TABLES 10): a change to a live table whose snapshot is a CSV, JSON (an array, or
 * an object with {@code rows}) or JSON lines file with {@code write: {journal}} is applied by writing the whole file again beside it
 * ({@code <file>.writing}, forced to disk) and moving it over the old one atomically, so a reader sees the old file or the new one, never
 * half. <b>Exactly once</b>: each change id is recorded in the journal (JSON lines, a visible file the operator names) as {@code writing}
 * before the file is replaced and {@code applied} after; an id recorded {@code applied} is already applied (an outbox resend after a
 * crash), and an id left at {@code writing} is applied when the file already holds its result (the row at the next version by its maker, or
 * gone for a remove). The version guard runs at the file too: a row that changed since {@code basedOn} refuses the change
 * ({@link ChangeWriter.Refused}). Changes to one file are applied one at a time; the file's other columns, rows and order are kept as read
 * (a CSV's cells as their text, a JSON row's other fields as they were).
 */
final class FileTableWriter implements ChangeWriter {

    /** Where one live table's changes go. */
    record Target(LiveTableSpec spec, ServerConfig.Table table, Path journal) {}

    private final Map<String, Target> targets = new ConcurrentHashMap<>();
    private final Map<Path, Set<String>> applied = new ConcurrentHashMap<>();
    private final Map<Path, Set<String>> writing = new ConcurrentHashMap<>();

    void target(Target t) {
        targets.put(t.spec().name(), t);
    }

    boolean writes(String table) {
        return targets.containsKey(table);
    }

    @Override
    public Object write(Change ch) throws Exception {
        Target t = targets.get(ch.table());
        if (t == null) {
            throw new Refused(ch.table() + " has no writer");
        }
        Path file = t.table().file();
        synchronized (lock(file)) {
            Set<String> done = journal(t.journal());
            if (done.contains(ch.id())) {
                return null;                                              // applied before: the same change again
            }
            LiveTableSpec spec = t.spec();
            Doc doc = Doc.read(file);
            int at = find(doc, spec, ch.key(), t);
            Map<String, Object> cur = at < 0 ? null : doc.rows.get(at);
            if (writing.get(t.journal()).contains(ch.id()) && landed(ch, spec, cur)) {
                record(t.journal(), ch.id(), "applied");                  // replaced before a crash, not yet recorded
                done.add(ch.id());
                return cur == null ? null : cur.get(spec.version());
            }
            Object next;
            switch (ch.op()) {
                case "update", "remove" -> {
                    if (cur == null) {
                        throw new Refused("no row " + ch.key() + " in the file");
                    }
                    if (KeyedRows.versionOrder(number(cur.get(spec.version())), ch.basedOn()) != 0) {
                        throw new Refused("the row changed at the source since version " + ch.basedOn());
                    }
                    if (ch.op().equals("remove")) {
                        doc.rows.remove(at);
                        next = null;
                    } else {
                        Map<String, Object> row = new LinkedHashMap<>(cur);
                        ch.set().forEach((k, v) -> row.put(k, doc.cell(v)));
                        next = number(cur.get(spec.version())) instanceof Long n ? n + 1 : 1L;
                        stamp(row, spec, ch, next, doc);
                        doc.rows.set(at, row);
                    }
                }
                case "add" -> {
                    if (cur != null) {
                        throw new Refused("the source refused it: the row exists");
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    doc.columns.forEach(c -> row.put(c, null));
                    for (int i = 0; i < spec.key().size(); i++) {
                        row.put(spec.key().get(i), doc.cell(ch.key().get(i)));
                    }
                    ch.set().forEach((k, v) -> row.put(k, doc.cell(v)));
                    next = 1L;
                    stamp(row, spec, ch, next, doc);
                    doc.rows.add(row);
                }
                default -> throw new Refused("no operation '" + ch.op() + "'");
            }
            record(t.journal(), ch.id(), "writing");
            writing.get(t.journal()).add(ch.id());
            doc.replace(file);
            record(t.journal(), ch.id(), "applied");
            done.add(ch.id());
            return next;
        }
    }

    private static void stamp(Map<String, Object> row, LiveTableSpec spec, Change ch, Object version, Doc doc) {
        row.put(spec.version(), doc.cell(version));
        if (spec.changedBy() != null) {
            row.put(spec.changedBy(), doc.cell(ch.by()));
        }
        if (spec.changedAt() != null && ch.at() != null) {
            row.put(spec.changedAt(), doc.cell(ch.at().toString()));
        }
    }

    /** Whether the file already holds a change's result (it was replaced before the crash that left the id at writing). */
    private static boolean landed(Change ch, LiveTableSpec spec, Map<String, Object> cur) {
        if (ch.op().equals("remove")) {
            return cur == null;
        }
        if (cur == null || spec.changedBy() != null && !ch.by().equals(String.valueOf(cur.get(spec.changedBy())))) {
            return false;
        }
        Object v = number(cur.get(spec.version()));
        Object want = ch.op().equals("add") ? (Object) 1L : ch.basedOn() instanceof Number n ? (Object) (n.longValue() + 1) : null;
        return want != null && KeyedRows.versionOrder(v, want) == 0;
    }

    /** The row of a key: cells compared as the table's types read them (a CSV's text "7" is the key 7). */
    private static int find(Doc doc, LiveTableSpec spec, List<Object> key, Target t) {
        Map<String, String> types = new LinkedHashMap<>();
        for (LiveTableSpec.Column c : spec.columns()) {
            types.put(c.name(), c.type() == null ? "string" : c.type());
        }
        String want = KeyedRows.keyText(key);
        for (int i = 0; i < doc.rows.size(); i++) {
            Map<String, Object> r = doc.rows.get(i);
            List<Object> k = new ArrayList<>();
            for (String c : spec.key()) {
                k.add(Rows.cell(types.getOrDefault(c, "string"), r.get(c)));
            }
            if (KeyedRows.keyText(k).equals(want)) {
                return i;
            }
        }
        return -1;
    }

    /** A version cell as a number (a CSV holds its text). */
    private static Object number(Object v) {
        if (v instanceof String s && s.matches("-?[0-9]{1,18}")) {
            return Long.parseLong(s);
        }
        return v;
    }

    private final Map<Path, Object> locks = new ConcurrentHashMap<>();

    private Object lock(Path file) {
        return locks.computeIfAbsent(file, k -> new Object());
    }

    /** The ids recorded applied (and, aside, those left at writing), read once from the journal. */
    private Set<String> journal(Path journal) throws IOException {
        Set<String> done = applied.get(journal);
        if (done != null) {
            return done;
        }
        Set<String> ok = ConcurrentHashMap.newKeySet();
        Set<String> started = ConcurrentHashMap.newKeySet();
        if (Files.isRegularFile(journal)) {
            for (String line : Files.readAllLines(journal, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                Object o;
                try {
                    o = Json.read(line);
                } catch (RuntimeException torn) {
                    continue;                                            // a line torn by a crash: its change is checked again
                }
                if (o instanceof Map<?, ?> m && m.get("change") instanceof String id) {
                    ("applied".equals(m.get("state")) ? ok : started).add(id);
                }
            }
        }
        started.removeAll(ok);
        writing.put(journal, started);
        applied.put(journal, ok);
        return ok;
    }

    /** One journal line, forced to disk before the file is touched (writing) or the change is answered (applied). */
    private static void record(Path journal, String id, String state) throws IOException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("change", id);
        m.put("state", state);
        m.put("at", java.time.Instant.now().toString());
        if (journal.getParent() != null) {
            Files.createDirectories(journal.getParent());
        }
        try (FileChannel ch = FileChannel.open(journal, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            ch.write(java.nio.ByteBuffer.wrap((Json.write(m) + "\n").getBytes(StandardCharsets.UTF_8)));
            ch.force(true);
        }
    }

    /** A file's rows as read, its columns in order, and how to write it back in the same form. */
    static final class Doc {
        enum Form { CSV, JSON, JSON_ROWS, JSONL }

        final Form form;
        final List<String> columns;
        final List<Map<String, Object>> rows;
        final Map<String, Object> wrapper;

        private Doc(Form form, List<String> columns, List<Map<String, Object>> rows, Map<String, Object> wrapper) {
            this.form = form;
            this.columns = columns;
            this.rows = rows;
            this.wrapper = wrapper;
        }

        @SuppressWarnings("unchecked")
        static Doc read(Path file) throws IOException {
            String name = file.getFileName().toString();
            String text = Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
            if (name.endsWith(".csv")) {
                List<String> head = header(text);
                List<Map<String, Object>> rows = new ArrayList<>(Rows.csv(text));
                return new Doc(Form.CSV, head, rows, null);
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            Map<String, Object> wrapper = null;
            Form form;
            if (name.endsWith(".jsonl") || name.endsWith(".ndjson")) {
                form = Form.JSONL;
                for (String line : text.split("\n")) {
                    if (!line.isBlank()) {
                        rows.add(new LinkedHashMap<>((Map<String, Object>) Json.read(line)));
                    }
                }
            } else {
                Object doc = text.isBlank() ? List.of() : Json.read(text);
                if (doc instanceof Map<?, ?> m && m.get("rows") instanceof List<?>) {
                    form = Form.JSON_ROWS;
                    wrapper = new LinkedHashMap<>((Map<String, Object>) m);
                    ((List<?>) m.get("rows")).forEach(r -> rows.add(new LinkedHashMap<>((Map<String, Object>) r)));
                } else if (doc instanceof List<?> l) {
                    form = Form.JSON;
                    l.forEach(r -> rows.add(new LinkedHashMap<>((Map<String, Object>) r)));
                } else {
                    throw new IOException(file + " is neither a JSON array of rows nor an object with rows");
                }
            }
            Set<String> cols = new java.util.LinkedHashSet<>();
            rows.forEach(r -> cols.addAll(r.keySet()));
            return new Doc(form, new ArrayList<>(cols), rows, wrapper);
        }

        /** The CSV's column names from its first line (the file may hold no row yet). */
        private static List<String> header(String text) {
            int nl = text.indexOf('\n');
            String first = nl < 0 ? text : text.substring(0, nl);
            if (first.isBlank()) {
                return new ArrayList<>();
            }
            List<Map<String, Object>> one = Rows.csv(first.replace("\r", "") + "\n\"\"\n");
            return one.isEmpty() ? new ArrayList<>() : new ArrayList<>(one.get(0).keySet());
        }

        /** A value as this file holds it: a CSV's text, a JSON value as it is. */
        Object cell(Object v) {
            if (form != Form.CSV || v == null) {
                return v;
            }
            if (v instanceof Double || v instanceof Float || v instanceof BigDecimal) {
                return new BigDecimal(v.toString()).stripTrailingZeros().toPlainString();
            }
            return String.valueOf(v);
        }

        /** Writes the rows beside the file, forces them to disk and moves them over it in one step. */
        void replace(Path file) throws IOException {
            String text = switch (form) {
                case CSV -> csv();
                case JSONL -> {
                    StringBuilder b = new StringBuilder();
                    rows.forEach(r -> b.append(Json.write(r)).append('\n'));
                    yield b.toString();
                }
                case JSON -> Json.write(rows) + "\n";
                case JSON_ROWS -> {
                    Map<String, Object> w = new LinkedHashMap<>(wrapper);
                    w.put("rows", rows);
                    yield Json.write(w) + "\n";
                }
            };
            Path tmp = file.resolveSibling(file.getFileName() + ".writing");
            try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ch.write(java.nio.ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8)));
                ch.force(true);
            }
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }

        private String csv() {
            List<String> cols = new ArrayList<>(columns);
            Set<String> seen = new LinkedHashSet<>(cols);
            rows.forEach(r -> r.keySet().forEach(k -> {
                if (seen.add(k)) {
                    cols.add(k);
                }
            }));
            StringBuilder b = new StringBuilder();
            b.append(String.join(",", cols.stream().map(Doc::quote).toList())).append('\n');
            for (Map<String, Object> r : rows) {
                List<String> cells = new ArrayList<>();
                cols.forEach(c -> cells.add(r.get(c) == null ? "" : quote(String.valueOf(r.get(c)))));
                b.append(String.join(",", cells)).append('\n');
            }
            return b.toString();
        }

        private static String quote(String s) {
            return s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
        }
    }
}
