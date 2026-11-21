/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.agg;

import com.ash.rachana.internal.NumberText;

/**
 * A columnar batch of rows: each column a {@code double[]} (NaN for no number) or a {@code String[]} (null for no value).
 *
 * @param rows the rows
 * @param columns one array per input
 */
public record Batch(int rows, Object[] columns) {

    /** Column {@code i} as numbers (text read as a number; NaN where it is none). */
    public double[] num(int i) {
        Object c = columns[i];
        if (c instanceof double[] d) {
            return d;
        }
        String[] s = (String[]) c;
        double[] out = new double[rows];
        for (int r = 0; r < rows; r++) {
            try {
                out[r] = s[r] == null ? Double.NaN : NumberText.parse(s[r]);
            } catch (NumberFormatException e) {
                out[r] = Double.NaN;
            }
        }
        return out;
    }

    /** Column {@code i} as text (a number's shortest text; null for NaN). */
    public String[] text(int i) {
        Object c = columns[i];
        if (c instanceof String[] s) {
            return s;
        }
        double[] d = (double[]) c;
        String[] out = new String[rows];
        for (int r = 0; r < rows; r++) {
            out[r] = Double.isNaN(d[r]) ? null : NumberText.text(d[r]);
        }
        return out;
    }

    /** One column of numbers. */
    public static Batch of(double[] values) {
        return new Batch(values.length, new Object[] {values});
    }

    /** Rows {@code from} (inclusive) to {@code to} (exclusive). */
    public Batch slice(int from, int to) {
        Object[] cols = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            cols[i] = columns[i] instanceof double[] d ? java.util.Arrays.copyOfRange(d, from, to)
                    : java.util.Arrays.copyOfRange((String[]) columns[i], from, to);
        }
        return new Batch(to - from, cols);
    }
}
