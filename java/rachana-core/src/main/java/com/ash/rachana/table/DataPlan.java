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
 *   "local": [ { "panel"?: id, "where": text, "reason": words } ],   // conditions the runtime applies itself
 *   "window"?: n }                                                  // level 1.2: read only by live panels: one page of n rows, the painter follows
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
        return plan(sutra, schemas, el, ctx, null);
    }

    /** The level from which a plan over known schemas is also checked (SPEC 4.14; EL_EXPANSION X8, types over a table's schema). */
    public static final String CHECKED_SINCE = "1.2";

    /**
     * The plan document; at language level {@link #CHECKED_SINCE} and above, with the schemas known, every row condition over a table
     * (the entry's {@code where}, a reading panel's {@code where}) is also read in checked mode against the table's columns: a type
     * refusal is {@code RCH-9127}, a filter on a masked column {@code RCH-9161}, each problem with {@code entry}, {@code panel} (for a
     * panel's condition) and {@code where}. An unknown column stays {@code RCH-2071}; controls in a condition are the screen's values.
     *
     * @param level the Sutra's language level (null: not checked)
     */
    public static Map<String, Object> plan(Sutra sutra, Map<String, TableSchema> schemas, ElCompiler el, EvalContext ctx, String level) {
        return com.ash.rachana.el.Declarations.within(sutra.declarations(), () -> planDeclared(sutra, schemas, el, ctx, level));   // level 1.2: the Sutra's let and functions in force
    }

    private static Map<String, Object> planDeclared(Sutra sutra, Map<String, TableSchema> schemas, ElCompiler el, EvalContext ctx, String level) {
        boolean checked = level != null && schemas != null && com.ash.rachana.parse.SutraBlocks.atLeast(level, CHECKED_SINCE);
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
                if (checked && schema != null && !schema.columns().isEmpty()) {
                    check(e.where(), e.name(), null, schema, el, ctx, problems);
                }
            }
            String rowsText = "$." + e.name();
            boolean sole = readers.size() == 1 && !stripReads && rowsText.equals(readers.get(0).option("rows").map(String::trim).orElse(null))
                    && !mentionsOutsideRows(readers.get(0), reads);
            for (Panel p : readers) {
                String w = p.option(Panel.WHERE).orElse(null);
                if (w != null && checked && schema != null && !schema.columns().isEmpty()
                        && rowsText.equals(p.option("rows").map(String::trim).orElse(null))) {
                    check(w, e.name(), p.id(), schema, el, ctx, problems);
                }
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
                Map<String, Object> all = new LinkedHashMap<>();         // keys in a fixed order: the plan's bytes are the same on every run
                all.put("op", "&&");
                all.put("args", filters);
                query.put("filter", filters.size() == 1 ? filters.get(0) : all);
            }
            int window = 0;                                              // level 1.2: read only by live panels, the table is read a window at a time
            if (!readers.isEmpty() && !stripReads && readers.stream().allMatch(p -> p.kind() == com.ash.rachana.model.PanelKind.LIVE)) {
                for (Panel p : readers) {
                    window = Math.max(window, p.options().get("window") instanceof Number n ? n.intValue() : com.ash.rachana.model.PanelOptions.WINDOW);
                }
            }
            if (e.limit() != null || window > 0) {
                query.put("limit", window == 0 ? e.limit() : e.limit() == null ? window : Math.min(e.limit(), window));
            }
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("entry", e.name());
            t.put("name", e.table());
            t.put("readers", readers.stream().map(Panel::id).toList());
            t.put("live", e.live());
            t.put("query", query);
            t.put("local", local);
            if (window > 0) {
                t.put("window", window);                                 // the painter follows it; the runtime reads one page to type the fields
            }
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

    /**
     * Checked mode over one row condition of a table: the condition with the controls' values in place (its filter tree spelt again, so
     * controls, which a data scope does not name, are values), else the text itself with its control references not counted.
     */
    private static void check(String where, String entry, String panel, TableSchema schema, ElCompiler el, EvalContext ctx,
            List<Object> problems) {
        String text = where;
        try {
            Map<String, Object> tree = FilterTree.of(el.compile(where), ctx);
            if (tree != null) {
                text = FilterTree.toEl(tree);
            }
        } catch (ElException | IllegalArgumentException ex) {
            return;                                                   // not compiled: the condition stays local with its reason
        }
        com.ash.rachana.el.check.Checker.Checked c = com.ash.rachana.el.CheckedMode.check(text, com.ash.rachana.el.check.Scope.ROW,
                new TableFields(schema));
        for (com.ash.rachana.el.check.Problem p : c.problems()) {
            if (!p.error() || com.ash.rachana.el.check.Problem.NAME.equals(p.code()) || p.message().startsWith("ctl.")) {
                continue;                                             // unknown columns are RCH-2071; controls are the screen's
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", p.code());
            m.put("message", p.message());
            if (p.fix() != null) {
                m.put("fix", p.fix());
            }
            m.put("entry", entry);
            if (panel != null) {
                m.put("panel", panel);
            }
            m.put("where", where);
            problems.add(m);
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
