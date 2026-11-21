/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.model;

import java.util.List;
import java.util.Map;

/**
 * One entry of a Sutra's {@code data:} block (SPEC 2.4, decision D10; language level 1.1): a name that becomes a field of {@code $},
 * and where its value comes from. Exactly one of {@code table}, {@code url}, {@code rows}, {@code record} is set ({@link #form}).
 *
 * @param name the field of {@code $} ({@code sales} is read as {@code $.sales})
 * @param form where the value comes from
 * @param table the host table's name (Data Protocol), for {@link Form#TABLE}
 * @param url the address read whole, for {@link Form#URL}; checked against the host's allow-list, never built by an expression
 * @param format {@code json}, {@code csv}, {@code ndjson} or {@code arrow} for a url (null: from the extension)
 * @param path where the rows are inside a wrapped JSON answer (a url), or null
 * @param rows the rows written in the file (plain values), for {@link Form#ROWS}
 * @param record the record written in the file (plain values), for {@link Form#RECORD}
 * @param key the fields that identify a row, in key order (empty: none)
 * @param as {@code rows} or {@code record} (null: rows, but a {@code record:} entry)
 * @param where a row condition over {@code @} (may read controls), or null
 * @param limit the most rows read, or null
 * @param refresh seconds between re-reads, or null
 * @param live whether the entry follows the table's change stream (needs a key)
 * @param mode {@code auto}, {@code client} or {@code server}
 * @param fields declared field types {@code {name: {type, label?, fmt?}}} (plain values), empty when none
 * @param location where the entry is written
 */
public record DataEntry(String name, Form form, String table, String url, String format, String path, List<Object> rows,
        Map<String, Object> record, List<String> key, String as, String where, Integer limit, Double refresh, boolean live, String mode,
        Map<String, Object> fields, SourceLocation location) {

    /** The key a Sutra writes the block under. */
    public static final String KEY = "data";

    /** Where an entry's value comes from: exactly one per entry. */
    public enum Form { TABLE, URL, ROWS, RECORD }

    public DataEntry {
        rows = rows == null ? null : List.copyOf(rows);
        key = key == null ? List.of() : List.copyOf(key);
        fields = fields == null ? Map.of() : fields;
        mode = mode == null ? "auto" : mode;
    }

    /** Whether the entry is one record (an object in {@code $}) rather than a list of rows. */
    public boolean isRecord() {
        return as != null ? "record".equals(as) : form == Form.RECORD;
    }
}
