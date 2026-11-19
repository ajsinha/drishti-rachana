/*
 * Copyright (c) 2026 Ashutosh Sinha. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.proof;

import com.ash.drishti.api.DataNode;
import com.ash.drishti.rachana.SutraException;
import com.ash.drishti.rachana.el.ElCompiler;
import com.ash.drishti.rachana.el.EvalContext;
import com.ash.drishti.rachana.el.Expr;
import com.ash.drishti.rachana.el.Template;
import com.ash.drishti.rachana.parse.SutraParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * T3, T4 and T5 workloads, the same source on the JVM, in JavaScript and in WebAssembly; the caller times them. Inputs are the
 * prep step's JSON (no runtime decimal parsing).
 */
public final class Bench {
    private Bench() {
    }

    private static final List<DataNode> ROWS = new ArrayList<>();
    private static DataNode root = DataNode.of(Map.of());
    private static ElCompiler compiler = new ElCompiler();

    /** Parse and build a Sutra from the prepared tree; the number of problems (0 when it compiled). */
    public static int compileSutra(String pnodeDoc) {
        try {
            return new com.ash.drishti.rachana.SutraExpressions(new ElCompiler()).check(new SutraParser().parse(pnodeDoc, "bench.sutra.yaml", "bench")).size();
        } catch (SutraException e) {
            return e.problems().size();
        }
    }

    /** {@code {"record": {...}, "rows": [...]}}: the document and the rows, converted once (not timed). */
    @SuppressWarnings("unchecked")
    public static int load(String json) {
        Map<String, Object> m = (Map<String, Object>) Json.parse(json);
        root = DataNode.of(Harness.plain(m.get("record")));
        ROWS.clear();
        for (Object r : (List<Object>) m.get("rows")) {
            ROWS.add(DataNode.of(Harness.plain(r)));
        }
        compiler = new ElCompiler();
        return ROWS.size();
    }

    /** Every row evaluated {@code reps} times with one compiled expression (or a template when it starts with {@code T:}). */
    public static int evalLoop(String expr, int reps) {
        EvalContext base = new EvalContext(root, null, -1, Harness.formats(), Map.of(), java.time.LocalDate.of(2026, 11, 17));
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

    /** The text of one evaluation (to check the hand-written evaluator computes the same thing). */
    public static String evalOne(String expr, int row) {
        EvalContext c = new EvalContext(root, null, -1, Harness.formats(), Map.of(), java.time.LocalDate.of(2026, 11, 17))
                .withRow(ROWS.get(row), row);
        if (expr.startsWith("T:")) {
            return compiler.template(expr.substring(2)).render(c);
        }
        return com.ash.drishti.rachana.el.Values.text(compiler.compile(expr).eval(c));
    }

    /**
     * T5: replace {@code changed} rows of the loaded table the way today's {@code DataNode.Arr} forces (a new list, a copy of every
     * element reference) and re-evaluate {@code expr} on the changed rows only. Returns the evaluations done.
     */
    public static int liveChange(String expr, int changed, int seed) {
        Expr e = compiler.compile(expr);
        List<DataNode> next = new ArrayList<>(ROWS);
        EvalContext base = new EvalContext(root, null, -1, Harness.formats(), Map.of(), java.time.LocalDate.of(2026, 11, 17));
        int n = 0;
        for (int k = 0; k < changed; k++) {
            int i = (seed + k * 7919) % next.size();
            next.set(i, next.get((i + 1) % next.size()));
            n += e.eval(base.withRow(next.get(i), i)) == null ? 0 : 1;
        }
        DataNode.Arr table = new DataNode.Arr(next);
        return n + table.size() - next.size();
    }

    /** T5 counterpart without the table copy: only the changed rows are touched. */
    public static int liveChangeRowsOnly(String expr, int changed, int seed) {
        Expr e = compiler.compile(expr);
        EvalContext base = new EvalContext(root, null, -1, Harness.formats(), Map.of(), java.time.LocalDate.of(2026, 11, 17));
        int n = 0;
        for (int k = 0; k < changed; k++) {
            int i = (seed + k * 7919) % ROWS.size();
            n += e.eval(base.withRow(ROWS.get((i + 1) % ROWS.size()), i)) == null ? 0 : 1;
        }
        return n;
    }
}
