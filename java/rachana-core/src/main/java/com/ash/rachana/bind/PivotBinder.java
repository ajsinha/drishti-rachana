/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.bind;

import com.ash.rachana.internal.CoreText;
import com.ash.rachana.api.Node;
import com.ash.rachana.view.ViewModel.LinkView;
import com.ash.rachana.el.ElCompiler;
import com.ash.rachana.el.EvalContext;
import com.ash.rachana.el.Expr;
import com.ash.rachana.el.Link;
import com.ash.rachana.el.Values;
import com.ash.rachana.model.Column;
import com.ash.rachana.model.Panel;
import com.ash.rachana.model.PivotSpec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The interactive pivot of a table or ladder: what the view says about it (its fields and the arrangement it opens
 * with, carried by the table's data) and, when a user opens Pivot (the pivot screen), the panel's rows as raw values, every row up
 * to {@link PivotLimits#maxRecords()} (a table's {@code limit} only shortens the table). Stateless and thread-safe.
 */
public final class PivotBinder {

    /**
     * A panel's rows for its pivot.
     *
     * @param panel the panel id
     * @param fields name, label, fmt and, for a field whose values are entity ids, the kind they open
     * @param rows one list of raw values per row, in field order
     * @param total rows the panel has
     * @param truncated true when only the first {@code limit} rows are given
     */
    public record Records(String panel, List<Map<String, Object>> fields, List<List<Object>> rows, int total, boolean truncated, int limit,
            Map<String, Object> arrangement) {}

    private final ElCompiler el;
    private final LinkResolver catalog;
    private final Binder binder;
    private final PivotLimits props;
    private final java.util.function.UnaryOperator<String> labels;

    PivotBinder(ElCompiler el, LinkResolver catalog, Binder binder, PivotLimits props, java.util.function.UnaryOperator<String> labels) {
        this.el = el;
        this.catalog = catalog;
        this.binder = binder;
        this.props = props;
        this.labels = labels;
    }

    /**
     * What the table's data carries: the fields offered, the arrangement the pivot opens with, and the pivot engine's source of the panel's
     * rows ({@code panel/<kind>/<panel>/<id>}, bi/PIVOT.md: Pivot opens the pivot screen on it); empty when not opted in.
     */
    Optional<Map<String, Object>> view(Panel p, com.ash.rachana.bind.EntityRef ref) {
        if (!props.enabled()) {
            return Optional.empty();
        }
        return p.pivot().map(spec -> {
            Map<String, Object> out = spec.toMap();
            List<Map<String, Object>> fields = new ArrayList<>();
            for (PivotSpec.Field f : spec.fields()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", f.name());
                m.put("label", label(f, p.columns(), labels));
                String fmt = fmt(f, p.columns());
                m.put("numeric", fmt != null && !fmt.equals("date") && !fmt.equals("text"));
                m.put("fmt", fmt);
                fields.add(m);
            }
            out.put("fields", fields);
            out.put("maxRows", props.maxRecords());
            if (ref != null) {
                out.put("source", "panel/" + ref.kind() + "/" + p.id() + "/" + ref.id());
            }
            return out;
        });
    }

    /** Every row of the panel (up to the limit) as raw values of the pivot's fields. */
    Records records(Panel p, BindContext c) {
        if (!props.enabled()) {
            throw new com.ash.rachana.RachanaException(com.ash.rachana.RachanaCode.FORBIDDEN,
                    "Pivot (the pivot screen) is switched off on this server (" + props.switchName() + ")");
        }
        PivotSpec spec = p.pivot().orElseThrow(() -> new IllegalArgumentException("panel '" + p.id() + "' does not offer a pivot"));
        Node rows = binder.eval(p.option("rows").orElseThrow(), c.eval()) instanceof Node n ? n : Node.missing();
        List<Expr> exprs = spec.fields().stream().map(f -> el.compile(f.bind())).toList();
        int limit = props.maxRecords();
        int total = rows.size();
        List<List<Object>> out = new ArrayList<>(Math.min(total, limit));
        String[] kinds = new String[exprs.size()];
        boolean[] ids = new boolean[exprs.size()];          // fields that hold entity ids: as a table column would link them
        for (int k = 0; k < ids.length; k++) {
            String bind = CoreText.strip(spec.fields().get(k).bind());
            ids[k] = Binder.namesAnId(bind) || p.columns().stream().anyMatch(col -> col.link() && bind.equals(col.bind() == null ? null : CoreText.strip(col.bind())));
        }
        for (int i = 0; i < total && i < limit; i++) {
            EvalContext rc = c.eval().withRow(rows.get(i), i);
            List<Object> row = new ArrayList<>(exprs.size());
            for (int k = 0; k < exprs.size(); k++) {
                Object v;
                try {
                    v = Values.simplify(exprs.get(k).eval(rc));
                } catch (RuntimeException e) {
                    v = null;                              // a row that cannot answer a field has no value for it
                }
                if (v instanceof Link l) {
                    LinkView lv = binder.linkView(l);
                    if (kinds[k] == null && lv != null) {
                        kinds[k] = lv.kind();
                    }
                    v = l.id();
                } else if (v instanceof Node n) {
                    v = n.size();                          // a list or an object counts as its size
                } else if (v instanceof String s && kinds[k] == null && ids[k]) {
                    kinds[k] = catalog.kindOf(s).orElse(null);
                }
                row.add(v);
            }
            out.add(row);
        }
        List<Map<String, Object>> fields = new ArrayList<>();
        for (int k = 0; k < spec.fields().size(); k++) {
            PivotSpec.Field f = spec.fields().get(k);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", f.name());
            m.put("label", label(f, p.columns(), labels));
            m.put("fmt", fmt(f, p.columns()));
            if (kinds[k] != null) {
                m.put("kind", kinds[k]);
            }
            fields.add(m);
        }
        return new Records(p.id(), fields, out, total, total > limit, limit, spec.arrangementMap());
    }

    /** The field's own label, else that of the column bound to the same expression, else its name in words. */
    private static String label(PivotSpec.Field f, List<Column> columns, java.util.function.UnaryOperator<String> labels) {
        if (f.label() != null) {
            return f.label();
        }
        for (Column c : columns) {
            if (c.label() != null && CoreText.strip(f.bind()).equals(c.bind() == null ? null : CoreText.strip(c.bind()))) {
                return c.label();
            }
        }
        String last = f.name().substring(f.name().lastIndexOf('.') + 1);
        return labels.apply(last);
    }

    private static String fmt(PivotSpec.Field f, List<Column> columns) {
        if (f.fmt() != null) {
            return f.fmt();
        }
        for (Column c : columns) {
            if (CoreText.strip(f.bind()).equals(c.bind() == null ? null : CoreText.strip(c.bind()))) {
                return c.fmt();
            }
        }
        return null;
    }
}
