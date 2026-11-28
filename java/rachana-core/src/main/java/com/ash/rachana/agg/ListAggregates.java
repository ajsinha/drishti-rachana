/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.agg;

import com.ash.rachana.internal.CoreText;
import com.ash.rachana.internal.NumberText;
import com.ash.rachana.api.Node;
import com.ash.rachana.RachanaCode;
import com.ash.rachana.el.ElException;
import com.ash.rachana.el.Values;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.ash.rachana.model.Ordered;

/**
 * The engine's aggregates over a Rachana list (spec/1/SPEC.md part 2): the list's values made a {@link Batch} and run through the very
 * {@link Aggregate} a pivot cell runs (start, add, finish), so a sum in a Sutra and a sum in a pivot are one implementation with one
 * treatment of nulls and masks. Written {@code f(list)} (the list's values), {@code f(list, 'FIELD', ...)} (a field of each element, a
 * dotted path), or {@code f(xs, ys)} (parallel lists, for the functions of two inputs: {@code wavg(prices, quantities)},
 * {@code sum_once(quantities, tradeIds)}); a number argument is the function's parameter ({@code percentile(list, 0.95)}).
 *
 * <p>Rules (FUNCTION_CATALOGUE.md): a value that is not a number (text that is not number-like, a boolean, an object) is not counted;
 * a masked value makes the result masked (it is never added up); {@code sum} and {@code count} of nothing are 0 (as Excel's), every
 * other aggregate of nothing is empty. A whole result is a whole number ({@code 1 + 1} prints 2).
 */
final class ListAggregates {

    private ListAggregates() {}

    /** Rachana's name to the engine's built-in name. */
    static final Map<String, String> ENGINE_NAME = Ordered.map("sum", "sum", "count", "count", "avg", "avg", "min", "min", "max", "max", "median", "median", "percentile", "percentile", "distinct", "count_distinct", "wavg", "wavg", "variance", "var_samp", "stddev", "stddev_samp", "variance_pop", "var_pop", "stddev_pop", "stddev_pop", "sum_once", "sum_once", "count_once", "count_once", "avg_once", "avg_once", "nmiss", "nmiss", "range", "range", "mode", "mode", "iqr", "iqr", "stderr", "stderr", "cv", "cv", "skewness", "skewness", "kurtosis", "kurtosis", "geomean", "geomean", "harmean", "harmean", "trimmean", "trimmean", "corr", "corr", "covar", "covar", "slope", "slope", "intercept", "intercept", "rsq", "rsq", "kth_largest", "kth_largest", "kth_smallest", "kth_smallest", "string_agg", "string_agg", "skewness_pop", "skewness_pop", "devsq", "devsq", "covar_pop", "covar_pop", "steyx", "steyx", "sumproduct", "sumproduct", "sumsq", "sumsq", "product", "product", "avedev", "avedev", "percentile_exc", "percentile_exc", "rank_avg", "rank_avg", "percent_rank_of", "percent_rank_of", "mode_multi", "mode_multi");

    /** {@code name} over the evaluated arguments of a call. */
    static Object apply(String name, List<Object> args) {
        if (args.isEmpty()) {
            throw error(name, "count() counts the rows of a pivot cell or of a measure's table; over a list write count(list)");
        }
        Object first = Values.simplify(args.get(0));
        if (first == null) {
            return empty(name);                                   // of nothing: 0 for a sum and a count, empty for the rest
        }
        if (Values.masked(first)) {
            return Values.mask();
        }
        if (!(first instanceof Node list && list.isArray())) {
            if (first instanceof Node || first instanceof String || first instanceof Number || first instanceof Boolean) {
                return empty(name);                               // level 1.0 as recorded; from 1.1 refused before the call (requireList)
            }
            throw notAList(name, first);                          // a host value Core cannot read: never a silent 0, at any level
        }
        List<String> fields = new ArrayList<>();
        List<Node> parallels = new ArrayList<>();
        List<Double> numbers = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        int sep = "string_agg".equals(name) ? separator(args) : -1;
        for (int i = 1; i < args.size(); i++) {
            Object a = Values.simplify(args.get(i));
            if (i == sep) {
                texts.add(Values.text(a));
            } else if (a instanceof Boolean b && "string_agg".equals(name)) {
                numbers.add(b ? 1.0 : 0.0);
            } else if (a instanceof Node arr && arr.isArray()) {
                parallels.add(arr);
            } else if (a instanceof Number n) {
                numbers.add(NumberText.toDouble(n));
            } else if (a instanceof String s && !s.isBlank()) {
                if (!numbers.isEmpty()) {
                    throw error(name, "the fields come before the numbers");
                }
                fields.add(s);
            } else if (a != null) {
                throw error(name, "argument " + (i + 1) + " is neither a field, a list nor a number");
            }
        }
        Aggregate<Object> f = function(name, numbers, texts);
        int inputs = "count".equals(name) ? 1 : f.inputs();
        List<List<Node>> columns = columns(name, list, fields, parallels, inputs);
        Object[] batch = new Object[inputs];
        for (int c = 0; c < inputs; c++) {
            List<Node> col = columns.get(c);
            if (f.numeric()) {
                double[] d = new double[col.size()];
                for (int r = 0; r < d.length; r++) {
                    Node x = col.get(r);
                    if (x.isMasked()) {
                        return Values.mask();                     // a masked value is never added up: the result is masked
                    }
                    d[r] = number(x);
                }
                batch[c] = d;
            } else {
                String[] s = new String[col.size()];
                for (int r = 0; r < s.length; r++) {
                    Node x = col.get(r);
                    if (x.isMasked()) {
                        return Values.mask();
                    }
                    s[r] = x.isNull() || x.isMissing() ? null : Values.text(x);
                }
                batch[c] = s;
            }
        }
        Object state = f.add(f.start(), new Batch(list.size(), batch));
        if (f instanceof com.ash.rachana.agg.MoreAggregates.ModeMulti mm) {
            List<Object> values = mm.values(state);                // a Rachana list gets mode_multi's values as a list
            if (values == null) {
                return null;
            }
            List<Node> out = new ArrayList<>();
            values.forEach(x -> out.add(Node.of(x instanceof Double d ? Values.normalise(d) : x)));
            return Node.list(out);
        }
        Object v = f.result(state);
        if (v instanceof Double d && (d.isNaN() || d.isInfinite())) {
            v = null;
        }
        return v == null ? empty(name) : v instanceof Double d ? Values.normalise(d) : v;
    }

    /** {@code string_agg}'s separator: its last text argument after the list ({@code string_agg($.LEGS, 'CURRENCY', ', ')}). */
    private static int separator(List<Object> args) {
        for (int i = args.size() - 1; i >= 1; i--) {
            if (Values.simplify(args.get(i)) instanceof String) {
                return i;
            }
        }
        throw error("string_agg", "write the separator: string_agg($.LEGS[*].CURRENCY, ', ')");
    }

    /** The engine's function of that name and parameters; a parameter out of range is a problem in words. */
    private static Aggregate<Object> function(String name, List<Double> numbers, List<String> texts) {
        if ("count".equals(name)) {
            return new Builtins.Count(false);
        }
        try {
            return Catalogue.of(ENGINE_NAME.get(name), List.of(), numbers, texts)
                    .orElseThrow(() -> error(name, "Core has no such aggregate"));
        } catch (IllegalArgumentException e) {
            throw error(name, e.getMessage());
        }
    }

    /** The function's input columns, one value per element: the elements, fields of them, or parallel lists. */
    private static List<List<Node>> columns(String name, Node list, List<String> fields, List<Node> parallels,
            int inputs) {
        List<List<Node>> out = new ArrayList<>();
        if (!parallels.isEmpty()) {
            if (!fields.isEmpty()) {
                throw error(name, "write either fields of one list or parallel lists, not both");
            }
            out.add(elements(list));
            for (Node p : parallels) {
                if (p.size() != list.size()) {
                    throw error(name, "the lists differ in length (" + list.size() + " and " + p.size() + ")");
                }
                out.add(elements(p));
            }
        } else if (!fields.isEmpty()) {
            for (String field : fields) {
                List<Node> col = new ArrayList<>(list.size());
                elements(list).forEach(e -> col.add(e.at(field)));
                out.add(col);
            }
        } else {
            out.add(elements(list));
        }
        if (out.size() != inputs) {
            throw error(name, "takes " + inputs + " input" + (inputs == 1 ? "" : "s") + " (a field each, or as many lists), got " + out.size());
        }
        return out;
    }

    /** A value as a number: numbers, and number-like text (spaces around allowed); anything else is not a number. */
    static double number(Node x) {
        Object s = Values.simplify(x);
        if (s instanceof Number n) {
            return NumberText.toDouble(n);
        }
        if (s instanceof String str && !str.isBlank()) {
            try {
                return NumberText.parse(CoreText.strip(str));
            } catch (NumberFormatException e) {
                return Double.NaN;
            }
        }
        return Double.NaN;
    }

    /**
     * Level 1.1 and later (SPEC 5.1: a wrong type is {@code RCH-2102}): the first argument of an aggregate over a list is a list, empty
     * or masked; anything else is refused naming the function and the value, never added up as 0.
     */
    static void requireList(String name, List<Object> args) {
        if (args.isEmpty()) {
            return;
        }
        Object first = Values.simplify(args.get(0));
        if (first != null && !Values.masked(first) && !(first instanceof Node n && n.isArray())) {
            throw notAList(name, first);
        }
    }

    private static ElException notAList(String name, Object first) {
        return error(name, describe(first) + " is not a list (SPEC 5.1: a wrong type is a problem, never a silent "
                + (empty(name) == null ? "empty" : "0") + ")");
    }

    /** A value as a problem names it: text in quotes, a number as itself, a record by its kind, a host's value by its class. */
    private static String describe(Object v) {
        if (v instanceof String str) {
            return "'" + (str.length() > 40 ? str.substring(0, 40) + "…" : str) + "'";
        }
        if (v instanceof Node n) {
            return n.kind() == Node.Kind.OBJECT ? "an object" : "a list";
        }
        if (v instanceof Number || v instanceof Boolean) {
            return Values.text(v);
        }
        return "a value of type " + v.getClass().getName();
    }

    /** What an aggregate of nothing is: 0 for a sum, a count and nmiss (as Excel's), empty for the rest. */
    static Object empty(String name) {
        return "sum".equals(name) || "count".equals(name) || "nmiss".equals(name) || "sumproduct".equals(name) || "sumsq".equals(name)
                ? (Object) 0L : null;
    }

    /** The elements of a list node, in order. */
    static List<Node> elements(Node list) {
        List<Node> out = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            out.add(list.get(i));
        }
        return out;
    }

    static ElException error(String name, String why) {
        return new ElException(RachanaCode.EL_EVAL, name + ": " + why);
    }
}
