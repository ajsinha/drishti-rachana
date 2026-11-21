/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.table;

import com.ash.rachana.parse.Json;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The rows of one table held in their JSON form and kept current by keyed patches (Data Protocol section 8.1; ViewModel contract 1.1,
 * op {@code rows}). Within one patch: {@code reset} (every row replaced), {@code remove} (by key; a key is always the list of the key
 * values in key order), {@code update} (the fields given merged into the row of that key; an unknown key is added), {@code add} (an
 * existing key is updated). Keys compare as the text of each value's JSON form ({@code "1.50"} is not {@code "1.5"}). A table with
 * {@code append: { window }} keeps only its newest {@code window} rows. The same code runs on the JVM and, compiled, in JavaScript.
 */
public final class KeyedRows {

    /** The separator of a compound key's parts in a key text (U+001F, as contract 1.1's {@code rows} patch joins them). */
    public static final char SEP = '\u001f';

    private final List<String> key;
    private final int window;
    private final List<Map<String, Object>> rows = new ArrayList<>();
    private final Map<String, Integer> index = new LinkedHashMap<>();

    /**
     * @param key the key columns, in key order (empty: rows can only be reset or appended)
     * @param window the newest rows kept for an appending table, 0 for none
     */
    public KeyedRows(List<String> key, List<Map<String, Object>> initial, int window) {
        this.key = List.copyOf(key);
        this.window = window;
        reset(initial);
    }

    public List<String> key() {
        return key;
    }

    /** The rows in order, as the live list (do not change it). */
    public List<Map<String, Object>> rows() {
        return rows;
    }

    /** The key text of a row: each key value's JSON text, joined by {@link #SEP}. */
    public String keyOf(Map<String, Object> row) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < key.size(); i++) {
            if (i > 0) {
                b.append(SEP);
            }
            Json.write(row.get(key.get(i)), b);
        }
        return b.toString();
    }

    /** The key text of a {@code remove} entry: a list of the key values in key order (a single value is taken as a one-part key). */
    public static String keyText(Object entry) {
        StringBuilder b = new StringBuilder();
        List<?> parts = entry instanceof List<?> l ? l : java.util.Collections.singletonList(entry);
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                b.append(SEP);
            }
            Json.write(parts.get(i), b);
        }
        return b.toString();
    }

    /** Replaces every row. */
    public void reset(List<Map<String, Object>> next) {
        rows.clear();
        if (next != null) {
            for (Map<String, Object> r : next) {
                rows.add(new LinkedHashMap<>(r));
            }
        }
        trim();
        reindex();
    }

    /**
     * Applies one patch ({@code {reset?, remove?, update?, add?}}, plain JSON values) in the order of section 8.1.
     *
     * @return whether any row changed
     * @throws IllegalArgumentException when the table has no key and the patch removes or updates ({@code rdp.table.no-key})
     */
    @SuppressWarnings("unchecked")
    public boolean apply(Map<String, Object> patch) {
        boolean changed = false;
        if (patch.get("reset") instanceof List<?> r) {
            List<Map<String, Object>> next = new ArrayList<>();
            r.forEach(x -> next.add((Map<String, Object>) x));
            reset(next);
            changed = true;
        }
        List<?> remove = patch.get("remove") instanceof List<?> l ? l : List.of();
        List<?> update = patch.get("update") instanceof List<?> l ? l : List.of();
        if (key.isEmpty() && (!remove.isEmpty() || !update.isEmpty())) {
            throw new IllegalArgumentException("rdp.table.no-key: a table without a key takes only reset and add");
        }
        if (!remove.isEmpty()) {
            java.util.Set<String> gone = new java.util.HashSet<>();
            remove.forEach(k -> gone.add(keyText(k)));
            int before = rows.size();
            rows.removeIf(row -> gone.contains(keyOf(row)));
            if (rows.size() != before) {
                changed = true;
                reindex();
            }
        }
        for (Object u : update) {
            changed |= merge((Map<String, Object>) u);
        }
        for (Object a : patch.get("add") instanceof List<?> l ? l : List.of()) {
            Map<String, Object> row = (Map<String, Object>) a;
            if (key.isEmpty()) {
                rows.add(new LinkedHashMap<>(row));
                changed = true;
            } else {
                changed |= merge(row);
            }
        }
        if (window > 0 && rows.size() > window) {
            trim();
            reindex();
        }
        return changed;
    }

    private boolean merge(Map<String, Object> fields) {
        String k = keyOf(fields);
        Integer at = index.get(k);
        if (at == null) {
            rows.add(new LinkedHashMap<>(fields));
            index.put(k, rows.size() - 1);
            return true;
        }
        Map<String, Object> row = rows.get(at);
        boolean changed = false;
        for (Map.Entry<String, Object> e : fields.entrySet()) {
            if (!row.containsKey(e.getKey()) || !Json.write(row.get(e.getKey())).equals(Json.write(e.getValue()))) {
                row.put(e.getKey(), e.getValue());
                changed = true;
            }
        }
        return changed;
    }

    private void trim() {
        if (window > 0 && rows.size() > window) {
            rows.subList(0, rows.size() - window).clear();
        }
    }

    private void reindex() {
        index.clear();
        if (!key.isEmpty()) {
            for (int i = 0; i < rows.size(); i++) {
                index.put(keyOf(rows.get(i)), i);
            }
        }
    }
}
