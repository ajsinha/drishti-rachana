/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.bind;

import java.util.List;
import java.util.Map;

/**
 * The host hook for a pivot panel's figures (split step A13): every cell, subtotal and total of rows held in memory, computed by
 * the host's pivot engine so that nothing else aggregates. Core alone has none ({@link #NONE}: a pivot panel says so); the aggregate
 * kernel of split step A9 is Core's own.
 */
public interface PivotEngine {

    /** One input column: its name, type ({@code string}, {@code number}), label and description. */
    record Field(String name, String type, String label, String description) {}

    /** No engine: a pivot panel shows that this host computes no pivots. */
    PivotEngine NONE = new PivotEngine() {
        @Override
        public Map<String, Map<String, Object>> cells(Map<String, Object> view, List<Field> fields, List<Object[]> rows) {
            throw new IllegalStateException("this host has no pivot engine for pivot panels");
        }

        @Override
        public String key(Map<String, String> coordinates) {
            return "";
        }
    };

    /**
     * The cells of a pivot view (source, rows, columns, values, expandTo) over the rows given, by {@code rowKey|colKey}; each entry's
     * {@code values} map holds the figures by the name the view gave them ({@code *} keys: totals).
     */
    Map<String, Map<String, Object>> cells(Map<String, Object> view, List<Field> fields, List<Object[]> rows);

    /** A cell key as {@link #cells} writes it, from its coordinates (dimension to value). */
    String key(Map<String, String> coordinates);
}
