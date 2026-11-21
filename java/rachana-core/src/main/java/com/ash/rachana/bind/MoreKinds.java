/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.bind;

import com.ash.rachana.api.Node;
import com.ash.rachana.el.EvalContext;
import com.ash.rachana.el.Values;
import com.ash.rachana.format.Formats;
import com.ash.rachana.format.Tones;
import com.ash.rachana.model.Effects;
import com.ash.rachana.model.Panel;
import com.ash.rachana.model.Wiring;
import com.ash.rachana.view.PanelData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The panel kinds of language 1.2's kinds batch that draw formatted figures (SPEC 2.3): {@code treemap} (rows as nested rectangles sized
 * by a figure, toned by another's sign) and {@code bullet} (one figure on a band with its bounds and warning zones). Stateless.
 */
final class MoreKinds {

    private final Binder binder;
    private final Formats formats;

    MoreKinds(Binder binder, Formats formats) {
        this.binder = binder;
        this.formats = formats;
    }

    private String text(String fmt, double v) {
        return formats.format(fmt, Values.normalise(v));
    }

    /** {@code treemap}: a cell per row with a positive figure, in the rows' order; {@code group} the outer level; ev and set per cell. */
    PanelData.Treemap treemap(Panel p, BindContext c) {
        Node all = binder.eval(p.option("rows").orElseThrow(), c.eval()) instanceof Node n ? n : Node.missing();
        boolean clicks = p.wiring().fires(Wiring.POINT_CLICK);
        List<Effects.SetState> sets = Effects.sets(p, Wiring.POINT_CLICK);
        int[] origin = binder.originals(p, all, c.eval());
        Node rows = origin == null ? all : Binder.keep(all, origin);
        String label = p.option("label").orElse("label"), value = p.option("value").orElse("value");
        String group = p.option("group").orElse(null), color = p.option("color").orElse(null), fmt = p.option("fmt").orElse(null);
        List<PanelData.TreemapCell> cells = new ArrayList<>();
        double total = 0;
        for (int i = 0; i < rows.size(); i++) {
            Node r = rows.get(i);
            double v = Values.number(r.get(value));
            if (!Double.isFinite(v) || v <= 0) {
                continue;                                              // a rectangle needs a positive size
            }
            total += v;
            double t = color == null ? Double.NaN : Values.number(r.get(color));
            String tone = Double.isFinite(t) && t != 0 ? (t > 0 ? "pos" : "neg") : null;
            int at = origin == null ? i : origin[i];
            EvalContext rc = c.eval().withRow(r, at);
            cells.add(new PanelData.TreemapCell(r.get(label).asText(), group == null ? null : r.get(group).asText(), v, text(fmt, v), tone,
                    clicks ? String.valueOf(at) : null, sets.isEmpty() ? null : binder.sets(sets, rc)));
        }
        return new PanelData.Treemap(cells, cells.isEmpty() ? null : text(fmt, total));
    }

    /**
     * {@code bullet}: {@code value} against {@code limit} (one-sided: from {@code min}, 0 by default) or between {@code lower} and
     * {@code upper} (two-sided). Zones: within {@code warnAt} (0.8) of a bound is {@code warn}, past a bound {@code bad}, else {@code ok};
     * the figure's tone is its zone's. The scale holds the bounds and the figure with a tenth of the span to spare.
     */
    PanelData.Bullet bullet(Panel p, BindContext c) {
        EvalContext ctx = c.eval();
        double v = num(p.option("value").orElseThrow(), ctx);
        String fmt = p.option("fmt").orElse(null);
        double warn = p.options().get("warnAt") instanceof Number n ? Values.number(n) : 0.8;
        Double lower = p.option("lower").map(e -> num(e, ctx)).filter(Double::isFinite).orElse(null);
        Double upper = p.option("upper").or(() -> p.option("limit")).map(e -> num(e, ctx)).filter(Double::isFinite).orElse(null);
        Double base = p.option("min").map(e -> num(e, ctx)).filter(Double::isFinite).orElse(lower == null ? 0.0 : null);
        List<PanelData.Band> bands = new ArrayList<>();
        double lo = lower != null ? lower : base, hi = upper != null ? upper : Math.max(v, lo);
        if (lower != null) {
            bands.add(new PanelData.Band(lower, lower * warn, "warn"));
        }
        if (upper != null) {
            bands.add(new PanelData.Band(lower != null ? lower * warn : lo, upper * warn, "ok"));
            bands.add(new PanelData.Band(upper * warn, upper, "warn"));
        }
        String tone = !Double.isFinite(v) ? null : upper != null && v > upper || lower != null && v < lower ? "bad"
                : upper != null && v > upper * warn || lower != null && v < lower * warn ? "warn" : "ok";
        if (p.option("tone").isPresent()) {
            tone = Tones.resolve(p.option("tone").get(), v);
        }
        double min = Math.min(lo, Double.isFinite(v) ? v : lo), max = Math.max(hi, Double.isFinite(v) ? v : hi);
        double pad = (max - min) * 0.1;
        return new PanelData.Bullet(Double.isFinite(v) ? v : null, Double.isFinite(v) ? text(fmt, v) : "", p.option("label").orElse(null),
                lower == null && base != null && base == 0 && min >= 0 ? 0 : min - pad, max + pad, bands,
                lower, lower == null ? null : text(fmt, lower), upper, upper == null ? null : text(fmt, upper), tone, p.option("caption").orElse(null));
    }

    private double num(String expr, EvalContext ctx) {
        return Values.number(binder.eval(expr, ctx));
    }
}
