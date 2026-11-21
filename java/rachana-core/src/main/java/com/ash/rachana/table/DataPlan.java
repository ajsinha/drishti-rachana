/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.table;

import com.ash.rachana.el.ElCompiler;
import com.ash.rachana.el.ElException;
import com.ash.rachana.el.EvalContext;
import com.ash.rachana.el.Expr;
import com.ash.rachana.model.Column;
import com.ash.rachana.model.DataEntry;
import com.ash.rachana.model.Panel;
import com.ash.rachana.model.StripItem;
import com.ash.rachana.model.Sutra;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * What a runtime asks of each host table of a Sutra's {@code data:} (SPEC 2.4; Data Protocol sections 6 and 7; FORMAT 5.10): the plan
 * document of {@code rachana explain --format json}. For each {@code table} entry, in the order written:
 * <pre>
 * { "entry": "sales", "name": "sales", "readers": ["by-month"], "live": false,
 *   "query": { "columns": [...], "filter": tree, "limit": n },      // what the host is asked (a query document, section 6)
 *   "local": [ { "panel"?: id, "where": text, "reason": words } ] }  // conditions the runtime applies itself
 * </pre>
 * Push-down rules: the entry's own {@code where} goes into {@code filter} when it is in the subset of section 7 and the table can
 * {@code filter}. A panel's {@code where} goes there too when that panel is the entry's only reader and reads it as its rows
 * ({@code rows: "$.sales"}): no other reader could see the rows it removes. {@code columns} is named only when every reader is such a
 * panel and its columns are plain fields; otherwise every column is read. Problems: {@code RCH-2070} a table the host does not have,
 * {@code RCH-2071} a column the table lacks (with {@code expected}).
 */
public final class DataPlan {

    private DataPlan() {
    }

    private static final Pattern FIELD = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /**
     * The plan document.
     *
     * @param schemas the host's schemas by table name (a table not in it is unknown); null when the host has not said (no problems then)
     * @param ctx controls and today, for the values a condition's non-row parts take
     */
    public static Map<String, Object> plan(Sutra sutra, Map<String, TableSchema> schemas, ElCompiler el, EvalContext ctx) {
        List<Object> tables = new ArrayList<>();
        List<Object> problems = new ArrayList<>();
        List<Panel> panels = new ArrayList<>();
        flatten(sutra.panels(), panels);
        for (DataEntry e : sutra.data()) {
            if (e.form() != DataEntry.Form.TABLE) {
                continue;
            }
            TableSchema schema = schemas == null ? null : schemas.get(e.table());
            if (schemas != null && schema == null) {
                problems.add(problem("RCH-2070", "the host has no table '" + e.table() + "' (data '" + e.name() + "')", null));
            }
            Pattern reads = Pattern.compile("\\$\\." + Pattern.quote(e.name()) + "(?![A-Za-z0-9_])");
            List<Panel> readers = panels.stream().filter(p -> mentions(p, reads)).toList();
            boolean stripReads = sutra.strip().stream().anyMatch(s -> s.bind() != null && reads.matcher(s.bind()).find())
                    || sutra.title() != null && sutra.title().id() != null && reads.matcher(sutra.title().id()).find();
            List<Object> local = new ArrayList<>();
            List<Map<String, Object>> filters = new ArrayList<>();
            List<String> columns = new ArrayList<>();
            boolean allColumns = stripReads || readers.isEmpty();
            boolean canFilter = schema == null || schema.can("filter");
            if (e.where() != null) {
                push(e.where(), null, el, ctx, canFilter, filters, columns, local);
            }
            String rowsText = "$." + e.name();
            boolean sole = readers.size() == 1 && !stripReads && rowsText.equals(readers.get(0).option("rows").map(String::trim).orElse(null))
                    && !mentionsOutsideRows(readers.get(0), reads);
            for (Panel p : readers) {
                String w = p.option(Panel.WHERE).orElse(null);
                if (w != null) {
                    if (sole) {
                        push(w, p.id(), el, ctx, canFilter, filters, columns, local);
                    } else {
                        local.add(local(p.id(), w, "the table has other readers"));
                    }
                }
                if (!sole || !plainColumns(p, columns)) {
                    allColumns = true;
                }
            }
            Map<String, Object> query = new LinkedHashMap<>();
            if (!allColumns && schema != null) {
                List<String> ordered = new ArrayList<>();
                schema.names().forEach(n -> {
                    if (columns.contains(n)) {
                        ordered.add(n);
                    }
                });
                columns.stream().filter(c -> !ordered.contains(c)).forEach(ordered::add);
                query.put("columns", ordered);
            }
            if (schema != null && !schema.columns().isEmpty()) {
                for (String c : columns) {
                    if (schema.column(c) == null) {
                        FilterTree.Refused r = FilterTree.unknownColumn(schema, c);
                        problems.add(problem("RCH-2071", "data '" + e.name() + "': " + r.getMessage(), r.fields().get("expected")));
                    }
                }
            }
            if (!filters.isEmpty()) {
                query.put("filter", filters.size() == 1 ? filters.get(0) : Map.of("op", "&&", "args", filters));
            }
            if (e.limit() != null) {
                query.put("limit", e.limit());
            }
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("entry", e.name());
            t.put("name", e.table());
            t.put("readers", readers.stream().map(Panel::id).toList());
            t.put("live", e.live());
            t.put("query", query);
            t.put("local", local);
            tables.add(t);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tables", tables);
        out.put("problems", problems);
        return out;
    }

    private static void push(String where, String panel, ElCompiler el, EvalContext ctx, boolean canFilter, List<Map<String, Object>> filters,
            List<String> columns, List<Object> local) {
        Expr x;
        try {
            x = el.compile(where);
        } catch (ElException ex) {
            local.add(local(panel, where, "the condition does not compile: " + ex.getMessage()));
            return;
        }
        List<String> cols = FilterTree.columns(x);
        if (cols != null) {
            cols.forEach(c -> {
                if (!columns.contains(c)) {
                    columns.add(c);
                }
            });
        }
        Map<String, Object> tree = FilterTree.of(x, ctx);
        if (tree == null) {
            local.add(local(panel, where, "outside the push-down subset of the Data Protocol v1"));
        } else if (!canFilter) {
            local.add(local(panel, where, "the table cannot filter"));
        } else {
            filters.add(tree);
        }
    }

    private static Map<String, Object> local(String panel, String where, String reason) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (panel != null) {
            m.put("panel", panel);
        }
        m.put("where", where);
        m.put("reason", reason);
        return m;
    }

    private static Map<String, Object> problem(String code, String message, Object expected) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("message", message);
        if (expected != null) {
            m.put("expected", expected);
        }
        return m;
    }

    /** Adds a table panel's plain column fields; false when a column is not a plain field (then every column is read). */
    private static boolean plainColumns(Panel p, List<String> columns) {
        if (p.columns().isEmpty()) {
            return false;
        }
        for (Column c : p.columns()) {
            String b = c.bind() == null ? "" : c.bind().trim();
            if (b.startsWith("@.")) {
                b = b.substring(2);
            }
            if (!FIELD.matcher(b).matches()) {
                return false;
            }
            if (!columns.contains(b)) {
                columns.add(b);
            }
        }
        return true;
    }

    private static void flatten(List<Panel> in, List<Panel> out) {
        for (Panel p : in) {
            out.add(p);
            if (p.parts() != null) {
                flatten(p.parts(), out);
            }
            if (p.body() != null) {
                flatten(List.of(p.body()), out);
            }
        }
    }

    private static boolean mentions(Panel p, Pattern reads) {
        if (p.title() != null && reads.matcher(p.title()).find()) {
            return true;
        }
        for (Column c : p.columns()) {
            if (c.bind() != null && reads.matcher(c.bind()).find()) {
                return true;
            }
        }
        return mentions(p.options(), reads);
    }

    /** Whether the panel reads the entry anywhere but its {@code rows} option. */
    private static boolean mentionsOutsideRows(Panel p, Pattern reads) {
        Map<String, Object> rest = new LinkedHashMap<>(p.options());
        rest.remove("rows");
        return mentions(rest, reads) || p.title() != null && reads.matcher(p.title()).find()
                || p.columns().stream().anyMatch(c -> c.bind() != null && reads.matcher(c.bind()).find());
    }

    private static boolean mentions(Object o, Pattern reads) {
        if (o instanceof String s) {
            return reads.matcher(s).find();
        }
        if (o instanceof Map<?, ?> m) {
            for (Object v : m.values()) {
                if (mentions(v, reads)) {
                    return true;
                }
            }
        }
        if (o instanceof List<?> l) {
            for (Object v : l) {
                if (mentions(v, reads)) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean strip(StripItem s, Pattern reads) {
        return s.bind() != null && reads.matcher(s.bind()).find();
    }
}
