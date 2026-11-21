/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.js;

import com.ash.rachana.Rachana;
import com.ash.rachana.SutraException;
import com.ash.rachana.SutraExpressions;
import com.ash.rachana.api.Node;
import com.ash.rachana.el.ElCompiler;
import com.ash.rachana.el.EvalContext;
import com.ash.rachana.el.Expr;
import com.ash.rachana.el.Template;
import com.ash.rachana.el.Values;
import com.ash.rachana.parse.Json;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.teavm.jso.JSExport;

/**
 * The measuring entry (A15's re-measure of P1's T3 to T5; never shipped): {@code rachana.bench.js}, built by
 * {@code tools/js/build_js.py --bench}, run by js/packages/rachana/bench/bench-node.mjs with tools/proofs/runners/bench.mjs.
 * The same functions as the P1 harness, over Rachana's own Core.
 */
public final class BenchMain {

    private static final Rachana RACHANA = Rachana.core();
    private static final List<Node> ROWS = new ArrayList<>();
    private static Node root = Node.of(Map.of());
    private static ElCompiler compiler = RACHANA.compiler();
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 17);

    private BenchMain() {
    }

    public static void main(String[] args) {
    }

    private static EvalContext base() {
        return new EvalContext(root, null, -1, RACHANA.formats(), Map.of(), TODAY);
    }

    /** T3: parse and check a Sutra's YAML text; the number of problems (0 when it compiled). */
    @JSExport
    public static int compileSutra(String yaml) {
        try {
            return new SutraExpressions(RACHANA.compiler()).check(RACHANA.parser().parse(yaml, "bench.sutra.yaml", "bench")).size();
        } catch (SutraException e) {
            return e.problems().size();
        }
    }

    /** {@code {"record": {...}, "rows": [...]}} with {@code {"$f64": hex}} doubles: converted once, not timed. */
    @JSExport
    public static int load(String json) {
        Map<?, ?> m = (Map<?, ?>) Json.read(json);
        root = Node.of(plain(m.get("record")));
        ROWS.clear();
        for (Object r : (List<?>) m.get("rows")) {
            ROWS.add(Node.of(plain(r)));
        }
        compiler = RACHANA.compiler();
        return ROWS.size();
    }

    private static Object plain(Object v) {
        if (v instanceof Map<?, ?> m) {
            if (m.size() == 1 && m.containsKey("$f64")) {
                return Double.longBitsToDouble(new java.math.BigInteger((String) m.get("$f64"), 16).longValue());
            }
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, x) -> out.put(String.valueOf(k), plain(x)));
            return out;
        }
        if (v instanceof List<?> l) {
            List<Object> out = new ArrayList<>();
            l.forEach(x -> out.add(plain(x)));
            return out;
        }
        if (v instanceof java.math.BigDecimal d) {
            return d.scale() <= 0 ? (Object) d.longValueExact() : com.ash.rachana.internal.NumberText.toDouble(d);
        }
        return v;
    }

    /** T4: every row evaluated {@code reps} times with one compiled expression (a template when it starts with {@code T:}). */
    @JSExport
    public static int evalLoop(String expr, int reps) {
        EvalContext base = base();
        int nonEmpty = 0;
        if (expr.startsWith("T:")) {
            Template t = compiler.template(expr.substring(2));
            for (int k = 0; k < reps; k++) {
                for (int i = 0; i < ROWS.size(); i++) {
                    nonEmpty += t.render(base.withRow(ROWS.get(i), i)).isEmpty() ? 0 : 1;
                }
            }
        } else {
            Expr e = compiler.compile(expr);
            for (int k = 0; k < reps; k++) {
                for (int i = 0; i < ROWS.size(); i++) {
                    nonEmpty += e.eval(base.withRow(ROWS.get(i), i)) == null ? 0 : 1;
                }
            }
        }
        return nonEmpty;
    }

    /** One evaluation's text (the samples the bench prints). */
    @JSExport
    public static String evalOne(String expr, int row) {
        EvalContext c = base().withRow(ROWS.get(row), row);
        if (expr.startsWith("T:")) {
            return compiler.template(expr.substring(2)).render(c);
        }
        return Values.text(compiler.compile(expr).eval(c));
    }

    /** T5: {@code changed} rows re-evaluated, the table untouched (Core's Node list is the host's own, A1). */
    @JSExport
    public static int liveChangeRowsOnly(String expr, int changed, int seed) {
        Expr e = compiler.compile(expr);
        EvalContext base = base();
        int n = 0;
        for (int k = 0; k < changed; k++) {
            int i = (seed + k * 7919) % ROWS.size();
            n += e.eval(base.withRow(ROWS.get((i + 1) % ROWS.size()), i)) == null ? 0 : 1;
        }
        return n;
    }

    /** P1's copying variant has no counterpart: Core no longer copies a table per change (A1); the same as {@link #liveChangeRowsOnly}. */
    @JSExport
    public static int liveChange(String expr, int changed, int seed) {
        return liveChangeRowsOnly(expr, changed, seed);
    }
}
