/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.bind;

import com.ash.rachana.api.Node;
import com.ash.rachana.el.EvalContext;
import com.ash.rachana.el.Link;
import com.ash.rachana.el.Values;
import com.ash.rachana.model.Column;
import com.ash.rachana.model.Panel;
import com.ash.rachana.model.PanelKind;
import com.ash.rachana.model.PanelOptions;
import com.ash.rachana.view.PanelData;
import com.ash.rachana.view.ViewModel;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The panels of language 1.1 that read a dataset ({@code grid}, {@code tree}, {@code chart}; ViewModel contract 1.1). A panel's rows
 * become a dataset of the view (raw values, each field with its type and format; the painter formats them) put into the binding's
 * {@link BindContext#datasets()} under the panel's {@code dataset} name (else its id); the panel's data names it. Panels naming one
 * dataset share it: its rows are those of the first such panel bound. Stateless.
 */
final class DatasetBinder {

    /** A value the reader may not see, as the Data Protocol writes it. */
    static final Map<String, Object> MASKED = Map.of("$masked", true);

    private static final Pattern FIELD_BIND = Pattern.compile("@\\.([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern ISO_TIME = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}.*");
    /** How many rows the fields of a dataset without columns are read from. */
    private static final int INFER_ROWS = 50;

    private final Binder binder;

    DatasetBinder(Binder binder) {
        this.binder = binder;
    }

    /** The panel's data; its dataset put into {@code c.datasets()} (when no panel put one of that name before). */
    PanelData bind(Panel p, BindContext c) {
        String name = p.options().get("dataset") instanceof String s ? s : p.id();
        ViewModel.Dataset ds = c.datasets().get(name);
        if (ds == null) {
            ds = dataset(p, c);
            c.datasets().put(name, ds);
        }
        return switch (p.kind()) {
            case GRID, LIVE -> grid(p, name, ds);
            case TREE -> tree(p, name, ds);
            case CHART -> chart(p, name);
            default -> throw new IllegalArgumentException("not a dataset panel: " + p.kind().id());
        };
    }

    /** Whether the dataset a dataset panel reads has no rows. */
    static boolean empty(PanelData data, BindContext c) {
        String name = switch (data) {
            case PanelData.Grid g -> g.dataset();
            case PanelData.Tree t -> t.dataset();
            case PanelData.DataChart d -> d.dataset();
            default -> null;
        };
        ViewModel.Dataset ds = name == null ? null : c.datasets().get(name);
        if (ds != null && ds.live() != null) {
            return false;                                              // a live table's rows are the host's: never known empty here
        }
        return ds == null || ds.values() == null || ds.values().isEmpty();
    }

    // ---- the dataset ---------------------------------------------------------------------------

    private ViewModel.Dataset dataset(Panel p, BindContext c) {
        Node all = binder.eval(p.option("rows").orElseThrow(), c.eval()) instanceof Node n ? n : Node.missing();
        Node rows = all.isArray() ? binder.where(p, all, c.eval()) : Node.list(List.of());
        int limit = p.options().get("limit") instanceof Number n ? n.intValue() : PanelOptions.DATASET_ROWS;
        int count = Math.min(rows.size(), limit);
        List<String> names = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<String> fmts = new ArrayList<>();
        List<String> binds = new ArrayList<>();
        List<Column> cols = p.columns();
        if (cols.isEmpty()) {
            Set<String> seen = new LinkedHashSet<>();
            for (int i = 0; i < Math.min(count, INFER_ROWS); i++) {
                Node r = rows.get(i);
                if (r.kind() == Node.Kind.OBJECT) {
                    r.fields().forEach((k, v) -> {
                        if (!k.startsWith("_") && v.kind() != Node.Kind.OBJECT && v.kind() != Node.Kind.ARRAY) {
                            seen.add(k);
                        }
                    });
                }
            }
            for (String k : seen) {
                names.add(k);
                labels.add(null);
                fmts.add(null);
                binds.add(null);
            }
        } else {
            Set<String> used = new LinkedHashSet<>();
            for (int i = 0; i < cols.size(); i++) {
                Column col = cols.get(i);
                names.add(unique(fieldName(col, i), used));
                labels.add(col.label());
                fmts.add(col.fmt());
                binds.add(col.bind());
            }
        }
        List<List<Object>> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Node row = rows.get(i);
            EvalContext rc = c.eval().withRow(row, i);
            List<Object> out = new ArrayList<>(names.size());
            for (int k = 0; k < names.size(); k++) {
                Object v;
                if (binds.get(k) == null) {
                    v = row.get(names.get(k));
                } else {
                    try {
                        v = binder.eval(binds.get(k), rc);
                    } catch (RuntimeException e) {
                        v = null;                                   // one cell that cannot be read is empty, never the panel
                    }
                }
                out.add(raw(v));
            }
            values.add(out);
        }
        List<ViewModel.DataField> fields = new ArrayList<>();
        for (int k = 0; k < names.size(); k++) {
            String label = labels.get(k) != null && !labels.get(k).equals(names.get(k)) ? labels.get(k) : null;
            fields.add(new ViewModel.DataField(names.get(k), type(values, k), label, fmts.get(k)));
        }
        if (p.kind() == PanelKind.LIVE) {                               // language 1.2: the rows read only type the fields; the painter follows the table
            return new ViewModel.Dataset(liveKey(p, names), fields, null, null, 0, null, null, null, null, live(p, c));
        }
        return ViewModel.Dataset.of(key(p, names), fields, values, rows.size() - count);
    }

    /** A live panel's table: the entry the parser put under {@code $live} (its table, key and condition) and the panel's own condition. */
    private ViewModel.LiveRef live(Panel p, BindContext c) {
        Map<?, ?> entry = p.options().get(LIVE_ENTRY) instanceof Map<?, ?> m ? m : Map.of();
        List<Map<String, Object>> trees = new ArrayList<>();
        for (Object w : new Object[] {entry.get("where"), p.options().get(com.ash.rachana.model.Panel.WHERE)}) {
            if (w instanceof String text) {
                Map<String, Object> tree = com.ash.rachana.table.FilterTree.of(binder.el().compile(text), c.eval());
                if (tree == null) {
                    throw new IllegalArgumentException("RCH-2029 the condition '" + text + "' of live panel '" + p.id()
                            + "' is outside the push-down subset of the Data Protocol (PROTOCOL 7): the host filters a live table");
                }
                trees.add(tree);
            }
        }
        Map<String, Object> filter = null;
        if (trees.size() == 1) {
            filter = trees.get(0);
        } else if (trees.size() > 1) {
            filter = new java.util.LinkedHashMap<>();
            filter.put("op", "&&");
            filter.put("args", trees);
        }
        int window = p.options().get("window") instanceof Number n ? n.intValue() : PanelOptions.WINDOW;
        return new ViewModel.LiveRef(String.valueOf(entry.get("table")), window, Boolean.TRUE.equals(p.options().get("top")), filter);
    }

    /** The option the parser hands a live panel's data entry under (SutraBuilder). */
    private static final String LIVE_ENTRY = "$live";

    private static List<String> liveKey(Panel p, List<String> names) {
        return words(p.options().get(LIVE_ENTRY) instanceof Map<?, ?> m ? m.get("key") : null);   // the host's rows carry every field
    }

    /** A column's field: the field its bind reads ({@code @.notional}), else its label in letters and digits, else {@code c<n>}. */
    static String fieldName(Column col, int index) {
        Matcher m = col.bind() == null ? null : FIELD_BIND.matcher(com.ash.rachana.internal.CoreText.strip(col.bind()));
        if (m != null && m.matches()) {
            return m.group(1);
        }
        String label = col.label() == null ? "" : col.label().replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (!label.isEmpty() && (label.charAt(0) >= 'A' && label.charAt(0) <= 'Z' || label.charAt(0) >= 'a' && label.charAt(0) <= 'z')) {
            return label.length() > 64 ? label.substring(0, 64) : label;
        }
        return "c" + (index + 1);
    }

    private static String unique(String name, Set<String> used) {
        String n = name;
        for (int i = 2; !used.add(n); i++) {
            n = name + "_" + i;
        }
        return n;
    }

    private static List<String> key(Panel p, List<String> names) {
        List<String> key = words(p.options().get("rowKey"));
        return key.stream().allMatch(names::contains) ? key : List.of();
    }

    /** A value as the dataset holds it: numbers, text, booleans, null; a link its id; masked as the Data Protocol's tag. */
    static Object raw(Object v) {
        if (Values.masked(v)) {
            return MASKED;
        }
        Object s = Values.simplify(v);
        if (s == null) {
            return null;
        }
        if (s instanceof Double d) {
            return d.isNaN() || d.isInfinite() ? null : d;
        }
        if (s instanceof Number || s instanceof String || s instanceof Boolean) {
            return s;
        }
        if (s instanceof Link l) {
            return l.id();
        }
        if (s instanceof Node) {
            return null;                                            // a list or a record has no place in a field
        }
        return Values.text(s);
    }

    /** A field's type from its values: number, boolean, date, datetime, else string (an empty field is a string). */
    private static String type(List<List<Object>> values, int k) {
        String t = null;
        for (List<Object> row : values) {
            Object v = row.get(k);
            if (v == null || v == MASKED) {
                continue;
            }
            String vt = v instanceof Number ? "number" : v instanceof Boolean ? "boolean"
                    : v instanceof String s && ISO_DATE.matcher(s).matches() ? "date"
                    : v instanceof String s && ISO_TIME.matcher(s).matches() ? "datetime" : "string";
            if (t == null) {
                t = vt;
            } else if (!t.equals(vt)) {
                return "string";
            }
        }
        return t == null ? "string" : t;
    }

    // ---- the kinds -----------------------------------------------------------------------------

    private static PanelData.Grid grid(Panel p, String name, ViewModel.Dataset ds) {
        List<PanelData.GridColumn> columns = new ArrayList<>();
        List<Column> cols = p.columns();
        List<String> bars = words(p.options().get("bars"));            // level 1.2: number fields drawn with a bar in the cell
        for (int i = 0; i < cols.size(); i++) {
            Column col = cols.get(i);
            String tone = col.tone() != null && col.tone().matches("[a-z]+") ? col.tone() : null;
            String field = ds.fields().size() > i ? ds.fields().get(i).name() : fieldName(col, i);
            columns.add(new PanelData.GridColumn(field, null, null, tone, 0, null, col.total() ? "sum" : null, false, bars.contains(field)));
        }
        if (cols.isEmpty() && !bars.isEmpty()) {                          // fields from the rows: each named, the barred ones marked
            ds.fields().forEach(f -> columns.add(new PanelData.GridColumn(f.name(), null, null, null, 0, null, null, false, bars.contains(f.name()))));
        }
        List<PanelData.SortKey> sort = new ArrayList<>();
        for (String s : words(p.options().get("sort"))) {
            String[] parts = s.split(" ");
            sort.add(new PanelData.SortKey(parts[0], parts.length > 1 && "desc".equals(parts[1])));
        }
        boolean search = !(p.options().get("search") instanceof Boolean b) || b;
        return new PanelData.Grid(name, columns, words(p.options().get("group")), sort, null, search,
                p.options().get("empty") instanceof String e ? e : null);
    }

    private static PanelData.Tree tree(Panel p, String name, ViewModel.Dataset ds) {
        Map<String, Object> o = p.options();
        int open = o.get("open") instanceof Number n ? n.intValue() : 0;
        Set<String> structure = new LinkedHashSet<>();
        for (String k : List.of("node", "parent", "path", "label")) {
            if (o.get(k) instanceof String s) {
                structure.add(s);
            }
        }
        String fmt = o.get("fmt") instanceof String s ? s : null;          // level 1.2: the measures' format where a column names none
        List<PanelData.Measure> measures = new ArrayList<>();             // the number fields: a node no row names adds them up
        for (ViewModel.DataField f : ds.fields()) {
            if ("number".equals(f.type()) && !structure.contains(f.name())) {
                measures.add(new PanelData.Measure(f.name(), "sum", null, f.fmt() == null ? fmt : null));
            }
        }
        return new PanelData.Tree(name, (String) o.get("node"), (String) o.get("parent"), (String) o.get("path"), (String) o.get("label"),
                measures, o.get("mode") instanceof String m ? m : null, open);
    }

    private static PanelData.DataChart chart(Panel p, String name) {
        Map<String, Object> o = p.options();
        String agg = o.get("agg") instanceof String a ? a : null;
        String fmt = o.get("fmt") instanceof String f ? f : null;
        List<PanelData.Measure> y = new ArrayList<>();
        for (String f : words(o.get("y"))) {
            y.add(new PanelData.Measure(f, agg, null, fmt));
        }
        return new PanelData.DataChart(name, o.get("type") instanceof String t ? t : "bar", (String) o.get("x"), y, (String) o.get("by"),
                o.get("stack") instanceof String s ? s : null, Boolean.TRUE.equals(o.get("horizontal")), (String) o.get("xLabel"),
                (String) o.get("yLabel"), o.get("bins") instanceof Number b ? b.intValue() : 0, o.get("top") instanceof Number t ? t.intValue() : 0);
    }

    /** A word or a list of words, as a list. */
    private static List<String> words(Object v) {
        if (v instanceof String s) {
            return List.of(s);
        }
        if (v instanceof List<?> l) {
            List<String> out = new ArrayList<>();
            l.forEach(x -> out.add(String.valueOf(x)));
            return out;
        }
        return List.of();
    }

    /** A dataset panel's kinds. */
    static boolean reads(PanelKind k) {
        return k == PanelKind.GRID || k == PanelKind.TREE || k == PanelKind.CHART || k == PanelKind.LIVE;
    }

}
