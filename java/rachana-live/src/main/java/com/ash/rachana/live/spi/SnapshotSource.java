/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.live.spi;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The durable rows of one live table (LIVE_TABLES 1.1), resolved by the host by name. Three tiers; the engine uses the one the source
 * declares. Every {@code filter} is a filter tree (PROTOCOL 7) the engine pushes down: the declaration's {@code where} and-ed with the
 * reader's row filter (null: every row). A source never sees a reader. Rows are maps in the Data Protocol's JSON form.
 */
public interface SnapshotSource {

    /** How a source is read: whole into memory, by columns with rows fetched by key, or paged by the source itself. */
    enum Tier { WHOLE, COLUMNS, QUERY }

    Tier tier();

    /** The source's data version (a new one drops the engine's indexes); null when it cannot say (indexes are read again after a while). */
    default String version() {
        return null;
    }

    /** {@link Tier#WHOLE}: every row holding {@code filter}, to the sink. */
    default void read(Object filter, Consumer<Map<String, Object>> sink) throws Exception {
        throw new UnsupportedOperationException("not a whole-tier source");
    }

    /**
     * {@link Tier#COLUMNS}: for every row holding {@code filter}, the values of {@code columns} (the key's first, then the others asked), in
     * one pass. {@link #column} says which columns the source can give so; others arrive null.
     */
    default void scan(List<String> columns, Object filter, Consumer<Object[]> sink) throws Exception {
        throw new UnsupportedOperationException("not a columns-tier source");
    }

    /** {@link Tier#COLUMNS}: whether {@link #scan} gives this column (else an order by it keeps the key order). */
    default boolean column(String name) {
        return true;
    }

    /** {@link Tier#COLUMNS} (and a single row of any tier): the rows of these keys (each the list of key values), whole; absent ones left out. */
    default List<Map<String, Object>> fetch(Collection<List<Object>> keys) throws Exception {
        throw new UnsupportedOperationException("this source cannot fetch rows by key");
    }

    /**
     * {@link Tier#QUERY}: one page for a query document (PROTOCOL 6; its {@code filter} already and-ed with the pushed-down filter):
     * {@code {columns, values, total?, next?}}.
     */
    default Map<String, Object> page(Map<String, Object> query) throws Exception {
        throw new UnsupportedOperationException("not a query-tier source");
    }
}
