/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import com.ash.rachana.RachanaCode;
import com.ash.rachana.api.Node;
import java.util.ArrayList;
import java.util.List;

/**
 * Ranges and slices of language level 1.1, as Python writes them (DECISIONS PY4, EL_EXPANSION X7): {@code range(stop)},
 * {@code range(start, stop[, step])} end-exclusive, a list of at most {@link #MAX} whole numbers; {@code x[start:stop:step]}.
 */
final class Ranges {

    private Ranges() {}

    /** The most elements {@code range} gives (EL_EXPANSION X7). */
    static final int MAX = 10_000;

    /** What {@code range} says of a list or a text: at level 1.1 it is only the sequence; the aggregate max − min is {@code span} (EL1-1). */
    static final String NOT_AN_AGGREGATE = "it gives whole numbers, as Python's range(start, stop, step) does; for the largest value "
            + "of a list minus its smallest write span(list, field?)";

    /** {@code range(stop)}, {@code range(start, stop[, step])}; a masked argument masks the result, an empty one gives empty. */
    static Object range(List<Object> a) {
        for (Object v : a) {
            if (Values.masked(v)) {
                return Values.mask();
            }
        }
        for (Object v : a) {
            if (Values.isNull(v)) {
                return null;
            }
            Object s = Values.simplify(v);
            if (s instanceof Node || s instanceof String) {
                throw Args.error("range", NOT_AN_AGGREGATE);
            }
        }
        long start = a.size() == 1 ? 0 : whole(a.get(0));
        long stop = whole(a.get(a.size() == 1 ? 0 : 1));
        long step = a.size() > 2 ? whole(a.get(2)) : 1;
        if (step == 0) {
            throw Args.error("range", "the step is 0");
        }
        long n = step > 0 ? (stop > start ? (stop - start + step - 1) / step : 0) : (start > stop ? (start - stop - step - 1) / -step : 0);
        if (n > MAX) {
            throw Args.error("range", "gives at most " + MAX + " values; this range has " + n);
        }
        List<Node> out = new ArrayList<>((int) n);
        for (long k = 0, v = start; k < n; k++, v += step) {
            out.add(Node.of(v));
        }
        return Node.list(out);
    }

    private static long whole(Object v) {
        Object s = Values.simplify(v);
        java.math.BigDecimal d = Args.decimal("range", s);
        if (d == null || d.stripTrailingZeros().scale() > 0) {
            throw Args.typeError("range", s, "a whole number");
        }
        if (d.abs().compareTo(java.math.BigDecimal.valueOf(1_000_000_000_000_000L)) > 0) {
            throw Args.error("range", Values.text(s) + " is too large");
        }
        return d.longValue();
    }

    /** {@code t[a:b:s]}: a list's elements or a text's code points, end-exclusive; empty for anything else. */
    static Object slice(Object t, Object a, Object b, Object s) {
        long step = s == null ? 1 : sliceWhole(s);
        if (step == 0) {
            throw new ElException(RachanaCode.EL_EVAL, "a slice's step is 0");
        }
        int n;
        String text = null;
        Node list = null;
        if (t instanceof Node l && l.isArray()) {
            list = l;
            n = l.size();
        } else if (t instanceof String str) {
            text = str;
            n = TextOrder.length(str);
        } else {
            return t == null ? null : Node.missing();
        }
        long lo;
        long hi;
        if (step > 0) {
            lo = a == null ? 0 : clamp(sliceWhole(a), n, 0, n);
            hi = b == null ? n : clamp(sliceWhole(b), n, 0, n);
        } else {
            lo = a == null ? n - 1 : clamp(sliceWhole(a), n, -1, n - 1);
            hi = b == null ? -1 : clamp(sliceWhole(b), n, -1, n - 1);
        }
        List<Integer> picked = new ArrayList<>();
        for (long i = lo; step > 0 ? i < hi : i > hi; i += step) {
            picked.add((int) i);
        }
        if (list != null) {
            List<Node> out = new ArrayList<>(picked.size());
            for (int i : picked) {
                out.add(list.get(i));
            }
            return Node.list(out);
        }
        StringBuilder sb = new StringBuilder();
        for (int i : picked) {
            sb.append(TextOrder.sub(text, i, i + 1));
        }
        return sb.toString();
    }

    private static long clamp(long i, int n, long min, long max) {
        long k = i < 0 ? i + n : i;
        return Math.max(min, Math.min(max, k));
    }

    private static long sliceWhole(Object v) {
        java.math.BigDecimal d = Args.decimal("slice", v);
        if (d == null) {
            throw Args.typeError("slice", v, "a whole number");
        }
        return d.setScale(0, java.math.RoundingMode.DOWN).max(java.math.BigDecimal.valueOf(-1_000_000_000L))
                .min(java.math.BigDecimal.valueOf(1_000_000_000L)).longValue();
    }
}
