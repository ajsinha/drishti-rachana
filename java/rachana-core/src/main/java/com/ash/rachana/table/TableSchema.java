/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.table;

import com.ash.rachana.api.Node;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.ash.rachana.model.Ordered;

/**
 * A table's schema document (Data Protocol section 2, {@code spec/1/data-protocol/schema.json}): name, columns, key, capabilities, and
 * the decoding of the JSON form of a cell (section 3) into the values Rachana-EL reads: a {@code decimal} as an exact decimal, an
 * {@code integer} given as digits as a whole number, a masked cell ({@code {"$masked": true}}) as a masked value. Dates, times and
 * timestamps stay ISO text, as a host's records hold them.
 *
 * @param name the table name
 * @param columns the columns in schema order
 * @param key the key columns in key order (empty: no key)
 * @param can the capabilities
 * @param doc the whole schema document as read (plain JSON values)
 */
public record TableSchema(String name, List<Column> columns, List<String> key, Set<String> can, Map<String, Object> doc) {

    /** The types of section 3.1. */
    public static final Set<String> TYPES = Ordered.set("string", "integer", "decimal", "number", "boolean", "date", "timestamp", "time", "list", "object");

    /** One column: {@code name}, {@code type}, whether it may be null, and whether it is masked for this reader. */
    public record Column(String name, String type, boolean nullable, boolean masked) {}

    public TableSchema {
        columns = List.copyOf(columns);
        key = List.copyOf(key);
        can = Ordered.copySet(can);
    }

    /** A schema from its JSON document (plain values: maps, lists, text, numbers). Unknown fields are ignored (section 14). */
    @SuppressWarnings("unchecked")
    public static TableSchema of(Object json) {
        Map<String, Object> m = json instanceof Map<?, ?> x ? (Map<String, Object>) x : Map.of();
        List<Column> cols = new ArrayList<>();
        if (m.get("columns") instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Map<?, ?> c && c.get("name") instanceof String n) {
                    cols.add(new Column(n, c.get("type") instanceof String t ? t : "string", !Boolean.FALSE.equals(c.get("nullable")),
                            Boolean.TRUE.equals(c.get("masked"))));
                }
            }
        }
        List<String> key = new ArrayList<>();
        if (m.get("key") instanceof List<?> l) {
            l.forEach(k -> key.add(String.valueOf(k)));
        } else if (m.get("key") instanceof String k) {
            key.add(k);
        }
        List<String> can = new ArrayList<>();
        if (m.get("can") instanceof List<?> l) {
            l.forEach(k -> can.add(String.valueOf(k)));
        }
        return new TableSchema(m.get("name") instanceof String n ? n : "", cols, key, new java.util.LinkedHashSet<>(can), m);
    }

    /** A schema with no columns known (rows without a schema: every field read as given). */
    public static TableSchema untyped(String name, List<String> key) {
        return new TableSchema(name, List.of(), key == null ? List.of() : key, Set.of(), Map.of("name", name));
    }

    /** The column named {@code name}, or null. */
    public Column column(String name) {
        for (Column c : columns) {
            if (c.name().equals(name)) {
                return c;
            }
        }
        return null;
    }

    /** The column names in schema order. */
    public List<String> names() {
        return columns.stream().map(Column::name).toList();
    }

    /** Whether the table declares the capability. */
    public boolean can(String capability) {
        return can.contains(capability);
    }

    /** A row in its JSON form decoded for Rachana-EL (see the class comment); fields the schema does not name are kept as given. */
    public Map<String, Object> decode(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        row.forEach((k, v) -> {
            Column c = column(k);
            out.put(k, c != null && c.masked() ? Node.masked() : decode(c == null ? null : c.type(), v));
        });
        return out;
    }

    /** One cell's JSON form as the value Rachana-EL reads, by the column's type (null: as given). */
    public static Object decode(String type, Object v) {
        if (v instanceof Map<?, ?> m && m.size() == 1 && Boolean.TRUE.equals(m.get("$masked"))) {
            return Node.masked();
        }
        if (v == null || type == null) {
            return v;
        }
        switch (type) {
            case "decimal" -> {
                if (v instanceof String s) {
                    try {
                        return new BigDecimal(s);
                    } catch (NumberFormatException e) {
                        return v;
                    }
                }
                return v instanceof Double d ? BigDecimal.valueOf(d) : v instanceof Long l ? BigDecimal.valueOf(l) : v;
            }
            case "integer" -> {
                if (v instanceof String s) {
                    try {
                        return Long.parseLong(s);
                    } catch (NumberFormatException e) {
                        try {
                            return new BigDecimal(s);
                        } catch (NumberFormatException e2) {
                            return v;
                        }
                    }
                }
                return v;
            }
            default -> {
                return v;
            }
        }
    }
}
