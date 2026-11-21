/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.table;

import com.ash.rachana.api.Node;
import com.ash.rachana.el.ElCompiler;
import com.ash.rachana.el.EvalContext;
import com.ash.rachana.el.Expr;
import com.ash.rachana.el.Values;
import com.ash.rachana.model.DataEntry;
import com.ash.rachana.model.Sutra;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The screen's record {@code $} with its {@code data:} entries (SPEC 2.4): each entry becomes a field of {@code $}, a table a list of
 * rows and a record an object. The host hands the rows it read for each {@code table} and {@code url} entry (by entry name, in their
 * JSON form, decoded here by the table's schema); {@code rows} and {@code record} entries are the file's own. The entry's {@code where}
 * and {@code limit} are applied here (again, when the host already applied them through the plan: a condition is idempotent), so the
 * view is the same whether the host pushed them down or not.
 */
public final class DataRecord {

    private DataRecord() {
    }

    /** Rows of one entry as the host read them: the schema (may be untyped) and the rows in their JSON form. */
    public record Rows(TableSchema schema, List<Map<String, Object>> rows) {}

    /**
     * The record: the host's own fields, then one field per entry.
     *
     * @param host the host's own record (plain values or a {@link Node}; null for none)
     * @param read the rows per entry name the host read (an entry missing here is an empty list)
     * @param ctx controls and today, for {@code where}
     */
    public static Node record(Sutra sutra, Object host, Map<String, Rows> read, ElCompiler el, EvalContext ctx) {
        return com.ash.rachana.el.Declarations.within(sutra.declarations(), () -> recordDeclared(sutra, host, read, el, ctx));   // level 1.2: the Sutra's let and functions in force
    }

    private static Node recordDeclared(Sutra sutra, Object host, Map<String, Rows> read, ElCompiler el, EvalContext ctx) {
        Map<String, Object> out = new LinkedHashMap<>();
        Node h = host instanceof Node n ? n : Node.of(host == null ? new LinkedHashMap<String, Object>() : host);
        if (h.fields() != null) {
            h.fields().forEach(out::put);
        }
        for (DataEntry e : sutra.data()) {
            out.put(e.name(), value(e, read == null ? null : read.get(e.name()), el, ctx));
        }
        return Node.of(out);
    }

    /** One entry's value. */
    public static Object value(DataEntry e, Rows rows, ElCompiler el, EvalContext ctx) {
        List<Object> list = new ArrayList<>();
        switch (e.form()) {
            case ROWS -> list.addAll(e.rows());
            case RECORD -> list.add(e.record());
            default -> {
                if (rows != null) {
                    TableSchema s = rows.schema() == null ? TableSchema.untyped(e.table(), e.key()) : rows.schema();
                    for (Map<String, Object> r : rows.rows()) {
                        list.add(s.decode(r));
                    }
                }
            }
        }
        if (e.where() != null) {
            Expr w = el.compile(e.where());
            List<Object> kept = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                if (Values.truthy(w.eval(ctx.withRow(Node.of(list.get(i)), i)))) {
                    kept.add(list.get(i));
                }
            }
            list = kept;
        }
        if (e.limit() != null && list.size() > e.limit()) {
            list = new ArrayList<>(list.subList(0, e.limit()));
        }
        if (e.isRecord()) {
            return list.isEmpty() ? new LinkedHashMap<String, Object>() : list.get(0);
        }
        return list;
    }
}
