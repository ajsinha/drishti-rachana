/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.conformance;

import com.ash.rachana.bind.PivotEngine;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The conformance runner's pivot engine (FORMAT 5.7): a host hook Core leaves to its host ({@link PivotEngine}), kept small so that every
 * runtime (the JVM, both JavaScript bundles and Python over the runtime bundle) binds a {@code pivot} panel the same way. Every cell,
 * subtotal and total of the rows by the view's row and column dimensions, each from its own rows, with {@code sum}, {@code count},
 * {@code avg}, {@code min} or {@code max}; the keys are the coordinates sorted by dimension name ({@code b0=UP|b1=FX}), {@code *} a total.
 * Another measure is refused in words (the case is the host's, not Core's).
 */
public final class CasePivots implements PivotEngine {

    /** The one engine (it holds nothing). */
    public static final PivotEngine ENGINE = new CasePivots();

    private CasePivots() { }

    @Override
    public Map<String, Map<String, Object>> cells(Map<String, Object> view, List<Field> fields, List<Object[]> rows) {
        List<String> names = new ArrayList<>();
        fields.forEach(f -> names.add(f.name()));
        List<String> rowDims = texts(view.get("rows"));
        List<String> colDims = texts(view.get("columns"));
        Map<String, Object> value = Cases.obj(Cases.items(view.get("values")).isEmpty() ? null : Cases.items(view.get("values")).get(0));
        String measure = Cases.text(value.get("measure"));
        String as = value.get("as") == null ? "v" : Cases.text(value.get("as"));
        int lp = measure.indexOf('(');
        String fn = lp < 0 ? measure : measure.substring(0, lp);
        if (!List.of("sum", "count", "avg", "min", "max").contains(fn)) {
            throw new IllegalArgumentException("the conformance pivot engine computes sum, count, avg, min and max, not " + measure);
        }
        String input = lp < 0 || measure.indexOf(')') <= lp + 1 ? null : measure.substring(lp + 1, measure.indexOf(')')).trim();
        int vi = input == null ? -1 : names.indexOf(input);
        Map<String, double[]> acc = new LinkedHashMap<>();         // key: {sum, count, min, max}
        for (Object[] r : rows) {
            double v = vi < 0 ? 1 : ((Number) r[vi]).doubleValue();
            List<String> rks = prefixes(rowDims, names, r);
            List<String> cks = prefixes(colDims, names, r);
            for (String rk : rks) {
                for (String ck : cks) {
                    double[] a = acc.computeIfAbsent(rk + "|" + ck, k -> new double[] {0, 0, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY});
                    a[0] += v;
                    a[1]++;
                    a[2] = Math.min(a[2], v);
                    a[3] = Math.max(a[3], v);
                }
            }
        }
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        acc.forEach((k, a) -> {
            double fig = switch (fn) {
                case "count" -> a[1];
                case "avg" -> a[0] / a[1];
                case "min" -> a[2];
                case "max" -> a[3];
                default -> a[0];
            };
            Map<String, Object> values = new LinkedHashMap<>();
            values.put(as, fig);
            Map<String, Object> cell = new LinkedHashMap<>();
            cell.put("values", values);
            out.put(k, cell);
        });
        return out;
    }

    @Override
    public String key(Map<String, String> coordinates) {
        StringBuilder b = new StringBuilder();
        new TreeMap<>(coordinates).forEach((k, v) -> b.append(b.length() > 0 ? "|" : "").append(k).append('=').append(v));
        return b.toString();
    }

    /** The keys of a row's groups along {@code dims}: the total {@code *}, then each prefix of its coordinates. */
    private List<String> prefixes(List<String> dims, List<String> names, Object[] r) {
        List<String> out = new ArrayList<>();
        out.add("*");
        Map<String, String> coords = new LinkedHashMap<>();
        for (String d : dims) {
            coords.put(d, String.valueOf(r[names.indexOf(d)]));
            out.add(key(coords));
        }
        return out;
    }

    private static List<String> texts(Object o) {
        List<String> out = new ArrayList<>();
        Cases.items(o).forEach(x -> out.add(Cases.text(x)));
        return out;
    }
}
