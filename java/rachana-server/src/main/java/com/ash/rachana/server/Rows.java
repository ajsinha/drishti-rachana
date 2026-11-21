/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.server;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Rows read from a source, shaped for the Data Protocol: CSV text parsed (RFC 4180), column types inferred when the configuration declares
 * none, and each cell given the protocol's JSON form for its type (an integer a number, a decimal its exact text, a date its ISO text).
 * Stateless.
 */
final class Rows {

    private static final Pattern INTEGER = Pattern.compile("-?[0-9]{1,18}");
    private static final Pattern DECIMAL = Pattern.compile("-?(?:[0-9]+\\.?[0-9]*|\\.[0-9]+)(?:[eE][-+]?[0-9]+)?");
    private static final Pattern DATE = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");
    private static final Pattern TIMESTAMP = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}[T ][0-9]{2}:[0-9]{2}(:[0-9]{2}(\\.[0-9]+)?)?(Z|[+-][0-9]{2}:?[0-9]{2})?");

    private Rows() {
    }

    /** CSV text as rows of text cells (RFC 4180: quotes, doubled quotes, line breaks in quotes); the first line names the columns. */
    static List<Map<String, Object>> csv(String text) {
        List<List<String>> lines = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean q = false;
        int i = text.startsWith("﻿") ? 1 : 0;
        for (; i < text.length(); i++) {
            char c = text.charAt(i);
            if (q) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        q = false;
                    }
                } else {
                    cell.append(c);
                }
            } else if (c == '"') {
                q = true;
            } else if (c == ',') {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(cell.toString());
                cell.setLength(0);
                if (row.size() > 1 || !row.get(0).isEmpty()) {
                    lines.add(row);
                }
                row = new ArrayList<>();
            } else {
                cell.append(c);
            }
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            lines.add(row);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        if (lines.isEmpty()) {
            return out;
        }
        List<String> head = lines.get(0);
        for (int r = 1; r < lines.size(); r++) {
            List<String> l = lines.get(r);
            Map<String, Object> m = new LinkedHashMap<>();
            for (int j = 0; j < head.size(); j++) {
                String v = j < l.size() ? l.get(j) : "";
                m.put(head.get(j).trim(), v.isEmpty() ? null : v);
            }
            out.add(m);
        }
        return out;
    }

    /** The column names of some rows, in first-seen order. */
    static List<String> names(List<Map<String, Object>> rows) {
        java.util.LinkedHashSet<String> n = new java.util.LinkedHashSet<>();
        rows.forEach(r -> n.addAll(r.keySet()));
        return new ArrayList<>(n);
    }

    /** The narrowest protocol type every non-null value of a column fits: integer, decimal, boolean, date, timestamp, else string. */
    static String infer(List<Map<String, Object>> rows, String column) {
        boolean integer = true, decimal = true, bool = true, date = true, ts = true, any = false;
        for (Map<String, Object> r : rows) {
            Object v = r.get(column);
            if (v == null) {
                continue;
            }
            any = true;
            if (v instanceof Map || v instanceof List) {
                return v instanceof List ? "list" : "object";
            }
            String s = String.valueOf(v);
            boolean isInt = v instanceof Long || v instanceof Integer || v instanceof BigInteger || INTEGER.matcher(s).matches();
            integer &= isInt;
            decimal &= isInt || v instanceof Double || v instanceof BigDecimal || DECIMAL.matcher(s).matches();
            bool &= v instanceof Boolean || s.equalsIgnoreCase("true") || s.equalsIgnoreCase("false");
            date &= DATE.matcher(s).matches();
            ts &= TIMESTAMP.matcher(s).matches();
        }
        if (!any) {
            return "string";
        }
        return integer ? "integer" : decimal ? "decimal" : bool ? "boolean" : date ? "date" : ts ? "timestamp" : "string";
    }

    /** One cell in the protocol's JSON form for its column type (a value that does not fit stays as given). */
    static Object cell(String type, Object v) {
        if (v == null) {
            return null;
        }
        String s = v instanceof String t ? t.trim() : null;
        switch (type == null ? "" : type) {
            case "integer" -> {
                if (v instanceof Integer n) {
                    return n.longValue();
                }
                if (s != null && INTEGER.matcher(s).matches()) {
                    return Long.parseLong(s);
                }
                return v;
            }
            case "decimal", "number" -> {
                if (v instanceof Double d) {
                    return BigDecimal.valueOf(d).toPlainString();
                }
                if (v instanceof BigDecimal d) {
                    return d.toPlainString();
                }
                if (v instanceof Long || v instanceof Integer || v instanceof BigInteger) {
                    return v.toString();
                }
                if (s != null && DECIMAL.matcher(s).matches()) {
                    return new BigDecimal(s).toPlainString();
                }
                return v;
            }
            case "boolean" -> {
                if (s != null && (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("false"))) {
                    return Boolean.parseBoolean(s);
                }
                return v;
            }
            case "date", "timestamp", "time", "string" -> {
                if (v instanceof java.sql.Timestamp t) {
                    return t.toInstant().toString();
                }
                if (v instanceof java.sql.Date d) {
                    return d.toLocalDate().toString();
                }
                if (v instanceof java.time.temporal.TemporalAccessor) {
                    return v.toString();
                }
                return v instanceof String ? v : type.equals("string") ? String.valueOf(v) : v;
            }
            default -> {
                return v;
            }
        }
    }

    /** Every row's cells in the protocol's form for the columns' types ({@code name -> type}). */
    static List<Map<String, Object>> shape(List<Map<String, Object>> rows, Map<String, String> types) {
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            for (Map.Entry<String, String> c : types.entrySet()) {
                m.put(c.getKey(), cell(c.getValue(), r.get(c.getKey())));
            }
            out.add(m);
        }
        return out;
    }
}
