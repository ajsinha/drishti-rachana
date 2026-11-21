/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.data;

import com.ash.rachana.table.TableSchema;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * What answers for some named tables of the Data Protocol (spec/1/data-protocol) behind a {@link ProtocolHost}: their listing, schemas,
 * pages, change frames and subscriptions. The host owns the routes, the bridge and the encodings and checks every query document
 * ({@link RowQuery#validate}) before a provider sees it; a provider owns its rows. {@link MemoryHost} is one provider (tables held in
 * memory); {@code rachana-live}'s {@code LiveTables} is another (a snapshot source merged with a live stream).
 *
 * <p>{@code caller} is the opaque object a host's transport attaches to a request (null when it attaches none). A provider may hand it to
 * the host's own rights hook; it never looks inside it. Errors are {@link ProtocolError}s ({@code rdp.table.unknown} for a table it does
 * not answer for, or one the caller may not know of).
 */
public interface TableProvider {

    /**
     * What a subscription asks for (PROTOCOL 8.2 to 8.4).
     *
     * @param after the position to resume after ({@code epoch:seq}); null or empty for the start of the current epoch
     * @param filter a filter tree ({@code changes.filter}), or null
     * @param everyMs the conflation window in milliseconds (8.4), 0 for none
     * @param window the keys the reader's page shows ({@code changes.window}; each a list of key values in key order), or null for none
     * @param top whether the reader's page starts at the first row of the table's own order ({@code changes.window})
     */
    record Subscribe(String after, Object filter, long everyMs, List<List<Object>> window, boolean top) {

        /** A plain subscription: no filter, no conflation, no window. */
        public static Subscribe after(String after) {
            return new Subscribe(after, null, 0, null, false);
        }
    }

    /** Whether this provider answers for a table of that name (for some caller). */
    boolean has(String name);

    /** The listing entries ({@code name}, {@code title?}, {@code key?}, {@code rows?}, {@code can}) of the tables the caller may see. */
    List<Map<String, Object>> tables(Object caller);

    /** A table's schema as the caller sees it (masked columns marked). */
    TableSchema schema(String name, Object caller);

    /** A table's schema document as the caller sees it, with its {@code version} ({@code {epoch, seq}}). */
    Map<String, Object> schemaDoc(String name, Object caller);

    /** A table's current position: {@code {epoch, seq}}. */
    Map<String, Object> version(String name, Object caller);

    /**
     * One page of a table for a checked query document ({@link RowQuery#validate} has passed): {@code {table, version, columns, values,
     * total?, next?}}, or a grouped answer ({@code columns}, {@code values}, {@code totals?}). The host shapes it ({@code shape=objects},
     * {@code schema=true}) and encodes it.
     */
    Map<String, Object> rows(String name, Map<String, Object> query, Object caller);

    /** The frames of a table's stream after a position (its {@code msg} frames, or one {@code gap} frame), as {@link MemoryHost#changes}. */
    List<Map<String, Object>> changes(String name, String after, Object caller);

    /**
     * Subscribes to a table's changes ({@link MemoryHost#subscribe} says how). A provider that cannot do what is asked (a filter, a window)
     * refuses with {@code rdp.query.unsupported}.
     */
    Subscription subscribe(String name, Subscribe request, Consumer<Map<String, Object>> sink, Object caller);
}
