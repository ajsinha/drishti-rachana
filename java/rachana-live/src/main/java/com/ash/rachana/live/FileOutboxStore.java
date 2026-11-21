/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.live;

import com.ash.rachana.live.spi.ChangeSink;
import com.ash.rachana.live.spi.OutboxStore;
import com.ash.rachana.parse.Json;
import java.io.IOException;
import java.nio.ByteBuffer;
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

/**
 * The outbox's file journal: one JSON record per line, each forced to the disk before {@link #append} returns. On open the file is
 * compacted: the last record of every change still to do, and of the newest {@code keepDone} done ones (so a repeated change id answers
 * its first answer again), written to a new file that replaces the old in one move. A torn last line (a crash while writing it) is
 * dropped: its change was never acknowledged.
 */
public final class FileOutboxStore implements OutboxStore {

    private final Path file;
    private final FileChannel out;

    /** Opens (or creates) the journal at {@code file}, keeping the newest {@code keepDone} finished changes. */
    public FileOutboxStore(Path file, int keepDone) throws IOException {
        this.file = file;
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        if (Files.exists(file)) {
            compact(keepDone);
        }
        out = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
    }

    @Override
    public synchronized void append(Map<String, Object> record) throws IOException {
        ByteBuffer b = ByteBuffer.wrap((Json.write(record) + "\n").getBytes(StandardCharsets.UTF_8));
        while (b.hasRemaining()) {
            out.write(b);
        }
        out.force(false);
    }

    @Override
    public synchronized List<Map<String, Object>> replay() throws IOException {
        return read(file);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> read(Path file) throws IOException {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (!Files.exists(file)) {
            return rows;
        }
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            try {
                rows.add((Map<String, Object>) Json.read(line));
            } catch (RuntimeException torn) {
                // a line cut by a crash: never acknowledged
            }
        }
        return rows;
    }

    private void compact(int keepDone) throws IOException {
        Map<String, Map<String, Object>> last = new LinkedHashMap<>();
        for (Map<String, Object> r : read(file)) {
            String id = String.valueOf(((Map<?, ?>) r.get("change")).get("id"));
            last.remove(id);
            last.put(id, r);
        }
        List<Map<String, Object>> keep = new ArrayList<>();
        int done = 0;
        List<Map<String, Object>> all = new ArrayList<>(last.values());
        for (int i = all.size() - 1; i >= 0; i--) {
            Map<String, Object> r = all.get(i);
            boolean terminal = ChangeSink.State.of(String.valueOf(r.get("state"))).terminal();
            if (!terminal || done++ < keepDone) {
                keep.add(0, r);
            }
        }
        Path tmp = file.resolveSibling(file.getFileName() + ".compact");
        StringBuilder b = new StringBuilder();
        keep.forEach(r -> b.append(Json.write(r)).append('\n'));
        Files.writeString(tmp, b.toString(), StandardCharsets.UTF_8);
        try (FileChannel c = FileChannel.open(tmp, StandardOpenOption.WRITE)) {
            c.force(true);
        }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    @Override
    public synchronized void close() throws IOException {
        out.close();
    }
}
