/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.server;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A table's filter tree (PROTOCOL 7) as a parameterised SQL condition, used only to read fewer rows from a JDBC source. The condition is a
 * pre-filter: the live engine applies the table's own condition again to every row it receives, so what is translated here must only never
 * leave out a row the condition keeps (a superset), and anything not translated exactly is left out of the SQL (never approximated).
 *
 * <p>Nothing is built from the tree's text except structure: column names are checked against the table's column allow-list (and are a
 * plain identifier) and then quoted; every literal is a bound parameter; operators come from a fixed table. A tree that does not translate
 * gives {@code null} (read everything).
 */
final class FilterSql {

    /** The SQL condition and its values, in order of the {@code ?} marks. */
    record Where(String sql, List<Object> params) {}

    private static final Pattern IDENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,127}");
    private static final int MAX_IN = 500;
    private static final int MAX_DEPTH = 16;

    private final Map<String, String> columns;
    private final List<Object> params = new ArrayList<>();

    private FilterSql(Map<String, String> columns) {
        this.columns = columns;
    }

    /** The condition for a tree over columns {@code name -> type}, or null when none of it can be pushed down. */
    static Where of(Object tree, Map<String, String> columns) {
        if (tree == null || columns == null) {
            return null;
        }
        FilterSql f = new FilterSql(columns);
        String sql;
        try {
            sql = f.node(tree, 0);
        } catch (RuntimeException e) {
            return null;
        }
        return sql == null ? null : new Where(sql, List.copyOf(f.params));
    }

    private String node(Object n, int depth) {
        if (depth > MAX_DEPTH || !(n instanceof Map<?, ?> m) || !(m.get("op") instanceof String op) || !(m.get("args") instanceof List<?> args)) {
            return null;
        }
        switch (op) {
            case "&&": {
                List<String> parts = new ArrayList<>();
                int mark = params.size();
                for (Object a : args) {
                    int before = params.size();
                    String s = node(a, depth + 1);
                    if (s == null) {
                        while (params.size() > before) {          // a conjunct left out takes none of its values with it
                            params.remove(params.size() - 1);
                        }
                    } else {
                        parts.add(s);
                    }
                }
                if (parts.isEmpty()) {
                    while (params.size() > mark) {
                        params.remove(params.size() - 1);
                    }
                    return null;
                }
                return "(" + String.join(" AND ", parts) + ")";
            }
            case "||": {
                List<String> parts = new ArrayList<>();
                for (Object a : args) {
                    String s = node(a, depth + 1);
                    if (s == null) {
                        return null;                              // one side unknown: any row may hold
                    }
                    parts.add(s);
                }
                return parts.isEmpty() ? null : "(" + String.join(" OR ", parts) + ")";
            }
            case "is-null": {
                String c = column(args.size() == 1 ? args.get(0) : null);
                return c == null ? null : c + " IS NULL";
            }
            case "==", "!=", "<", "<=", ">", ">=":
                return args.size() == 2 ? compare(op, args.get(0), args.get(1)) : null;
            case "in", "not-in":
                return args.size() == 2 ? in(op.equals("not-in"), args.get(0), args.get(1)) : null;
            default:
                return null;
        }
    }

    private String compare(String op, Object l, Object r) {
        String col = column(l);
        Object lit = r;
        String o = op;
        if (col == null) {
            col = column(r);
            lit = l;
            o = switch (op) {
                case "<" -> ">";
                case "<=" -> ">=";
                case ">" -> "<";
                case ">=" -> "<=";
                default -> op;
            };
        }
        if (col == null || !(lit instanceof Map<?, ?> lm) || !lm.containsKey("lit")) {
            return null;
        }
        String type = columns.get(name(col));
        Object value = lm.get("lit");
        if (value == null) {                                      // empty == empty is true; an ordering against empty is false
            return switch (o) {
                case "==" -> col + " IS NULL";
                case "!=" -> col + " IS NOT NULL";
                default -> null;
            };
        }
        Object bound = bind(type, value, lm.get("type"), !o.equals("==") && !o.equals("!="));
        if (bound == null) {
            return null;
        }
        if (o.equals("!=")) {
            if (type.equals("string")) {
                return null;                                      // a collation may treat two texts as equal: not exact
            }
            params.add(bound);
            return "(" + col + " <> ? OR " + col + " IS NULL)";   // empty != 'X' is true
        }
        params.add(bound);
        return col + " " + (o.equals("==") ? "=" : o) + " ?";
    }

    private String in(boolean negated, Object value, Object list) {
        String col = column(value);
        if (col == null || !(list instanceof Map<?, ?> lm) || !(lm.get("list") instanceof List<?> items) || items.size() > MAX_IN) {
            return null;
        }
        String type = columns.get(name(col));
        if (items.isEmpty()) {
            return negated ? "1=1" : "1=0";
        }
        if (negated && type.equals("string")) {
            return null;
        }
        List<Object> bound = new ArrayList<>();
        for (Object it : items) {
            if (!(it instanceof Map<?, ?> m) || !m.containsKey("lit") || m.get("lit") == null) {
                return null;
            }
            Object b = bind(type, m.get("lit"), m.get("type"), false);
            if (b == null) {
                return null;
            }
            bound.add(b);
        }
        params.addAll(bound);
        String marks = String.join(", ", java.util.Collections.nCopies(bound.size(), "?"));
        return negated ? "(" + col + " NOT IN (" + marks + ") OR " + col + " IS NULL)" : col + " IN (" + marks + ")";
    }

    private String name(String quoted) {
        return quoted.substring(1, quoted.length() - 1);
    }

    /** A column node as a quoted identifier, only when it is a plain name in the allow-list. */
    private String column(Object n) {
        if (!(n instanceof Map<?, ?> m) || !(m.get("col") instanceof String c) || !IDENT.matcher(c).matches() || !columns.containsKey(c)) {
            return null;
        }
        return "\"" + c + "\"";
    }

    /** A literal as the value to bind for a column of this type; null when its meaning in SQL could differ from Rachana-EL's. */
    private static Object bind(String type, Object v, Object litType, boolean ordering) {
        switch (type) {
            case "integer", "decimal", "number":
                if (litType != null && !"decimal".equals(litType)) {
                    return null;
                }
                if (v instanceof Number n) {
                    return n instanceof Double || n instanceof Float ? BigDecimal.valueOf(n.doubleValue()) : n;
                }
                if ("decimal".equals(litType) && v instanceof String s) {
                    try {
                        return new BigDecimal(s);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                }
                return null;
            case "date":
                if ("date".equals(litType) && v instanceof String s) {
                    try {
                        return Date.valueOf(LocalDate.parse(s));
                    } catch (DateTimeParseException e) {
                        return null;
                    }
                }
                return null;
            case "boolean":
                return !ordering && v instanceof Boolean b ? b : null;
            case "string":
                return !ordering && litType == null && v instanceof String s ? s : null;
            default:
                return null;
        }
    }
}
