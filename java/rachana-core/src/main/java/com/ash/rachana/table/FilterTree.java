/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.table;

import com.ash.rachana.api.Node;
import com.ash.rachana.el.EvalContext;
import com.ash.rachana.el.Expr;
import com.ash.rachana.el.check.Checker;
import com.ash.rachana.el.Values;
import com.ash.rachana.parse.Json;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.ash.rachana.model.Ordered;

/**
 * The filter syntax tree of the Data Protocol (section 7, {@code spec/1/data-protocol/filter.json}) in both directions:
 * <ul>
 *   <li>{@link #of}: a compiled row condition as a tree, controls and every part that does not read the row replaced by their values
 *       (a runtime never sends expression text); null when the condition uses something outside the push-down subset of v1, so it stays
 *       local;</li>
 *   <li>{@link #check} and {@link #toEl}: a received tree checked (operations, arity, columns) and spelt as the Rachana-EL condition of
 *       the same meaning, which a host built on Core evaluates with Core itself, so its semantics are Rachana-EL's by construction.</li>
 * </ul>
 */
public final class FilterTree {

    private FilterTree() {
    }

    /** Operations of exactly two arguments. */
    public static final Set<String> TWO = Ordered.set("==", "!=", "<", "<=", ">", ">=", "-", "/", "%", "contains", "starts_with", "ends_with", "has",
            "any_of", "all_of", "in", "not-in");
    /** Operations of two or more. */
    public static final Set<String> MANY = Ordered.set("&&", "||", "+", "*");
    /** Operations of exactly one. */
    public static final Set<String> ONE = Ordered.set("!", "neg", "is-null", "lower", "upper", "len", "abs", "floor", "ceiling", "year", "quarter",
            "month", "day");
    /** Functions of the subset (the calls that may appear in a tree by name). */
    public static final Set<String> FUNCTIONS = Ordered.set("coalesce", "lower", "upper", "contains", "starts_with", "ends_with", "len", "abs",
            "round", "floor", "ceiling", "year", "quarter", "month", "day", "has", "any_of", "all_of");
    private static final Set<String> BINARY = Ordered.set("==", "!=", "<", "<=", ">", ">=", "+", "-", "*", "/", "%");

    /** A refusal of a received tree: the Data Protocol code ({@code rdp.query.invalid}, {@code rdp.column.unknown}) and its fields. */
    public static final class Refused extends RuntimeException {
        private final String code;
        private final Map<String, Object> fields;

        public Refused(String code, String message, Map<String, Object> fields) {
            super(message, null, false, false);
            this.code = code;
            this.fields = fields == null ? Map.of() : fields;
        }

        public String code() {
            return code;
        }

        public Map<String, Object> fields() {
            return fields;
        }
    }

    // ---- a row condition as a tree ------------------------------------------------------------------------------------

    /**
     * The tree of a compiled row condition, or null when some part of it cannot be pushed down.
     *
     * @param ctx the context the condition's non-row parts are evaluated in (controls, today), never the row
     */
    public static Map<String, Object> of(Expr e, EvalContext ctx) {
        try {
            return node(e, ctx);
        } catch (Unpushable u) {
            return null;
        }
    }

    /** The columns a row condition reads (top-level names, in the order met), or null when it reads the row in another way. */
    public static List<String> columns(Expr e) {
        List<String> out = new ArrayList<>();
        try {
            collect(e, out);
            return out;
        } catch (Unpushable u) {
            return null;
        }
    }

    private static final class Unpushable extends RuntimeException {
        Unpushable() {
            super(null, null, false, false);
        }
    }

    private static Map<String, Object> node(Expr e, EvalContext ctx) {
        if (!readsRow(e)) {
            return literal(e.eval(ctx));
        }
        String col = column(e);
        if (col != null) {
            return Map.of("col", col);
        }
        return switch (e) {
            case Expr.Not n -> op("!", node(n.e(), ctx));
            case Expr.Neg n -> op("neg", node(n.e(), ctx));
            case Expr.And a -> chain("&&", a, ctx);
            case Expr.Or o -> chain("||", o, ctx);
            case Expr.Binary b when BINARY.contains(b.op()) -> isNullTest(b, ctx);
            case Expr.Op b when BINARY.contains(b.op()) -> isNullTest(new Expr.Binary(b.op(), b.l(), b.r()), ctx);   // language level 1.1
            case Expr.Minus n -> op("neg", node(n.e(), ctx));
            case Expr.Coalesce c -> op("coalesce", node(c.l(), ctx), node(c.r(), ctx));
            case Expr.In i -> op(i.negated() ? "not-in" : "in", node(i.value(), ctx), list(i.list(), ctx));
            case Expr.Call c when FUNCTIONS.contains(c.name()) && c.named().isEmpty() && arity(c.name(), c.args().size()) -> {
                List<Object> args = new ArrayList<>();
                c.args().forEach(x -> args.add(node(x, ctx)));
                yield op(c.name(), args.toArray());
            }
            default -> throw new Unpushable();
        };
    }

    private static Map<String, Object> isNullTest(Expr.Binary b, EvalContext ctx) {
        if (b.op().equals("==") && b.r() instanceof Expr.Lit l && l.value() == null) {
            return op("is-null", node(b.l(), ctx));
        }
        if (b.op().equals("+") || b.op().equals("*")) {
            List<Object> args = new ArrayList<>();
            flatten(b.op(), b, args, ctx);
            return op(b.op(), args.toArray());
        }
        return op(b.op(), node(b.l(), ctx), node(b.r(), ctx));
    }

    private static void flatten(String op, Expr e, List<Object> args, EvalContext ctx) {
        if (e instanceof Expr.Binary b && b.op().equals(op) && readsRow(b)) {
            flatten(op, b.l(), args, ctx);
            args.add(node(b.r(), ctx));
        } else if (e instanceof Expr.Op b && b.op().equals(op) && readsRow(b)) {
            flatten(op, b.l(), args, ctx);
            args.add(node(b.r(), ctx));
        } else {
            args.add(node(e, ctx));
        }
    }

    private static Map<String, Object> chain(String op, Expr e, EvalContext ctx) {
        List<Object> args = new ArrayList<>();
        flattenLogic(op, e, args, ctx);
        return op(op, args.toArray());
    }

    private static void flattenLogic(String op, Expr e, List<Object> args, EvalContext ctx) {
        if (op.equals("&&") && e instanceof Expr.And a) {
            flattenLogic(op, a.l(), args, ctx);
            args.add(node(a.r(), ctx));
        } else if (op.equals("||") && e instanceof Expr.Or o) {
            flattenLogic(op, o.l(), args, ctx);
            args.add(node(o.r(), ctx));
        } else {
            args.add(node(e, ctx));
        }
    }

    private static Map<String, Object> list(Expr e, EvalContext ctx) {
        if (e instanceof Expr.ListLit l) {
            List<Object> items = new ArrayList<>();
            for (Expr x : l.items()) {
                items.add(node(x, ctx));
            }
            return Map.of("list", items);
        }
        if (!readsRow(e)) {
            Map<String, Object> lit = literal(e.eval(ctx));
            if (lit.containsKey("list")) {
                return lit;
            }
            List<Object> one = new ArrayList<>();                         // a value that is not a list is a list of one (Expr.In)
            if (lit.get("lit") != null) {
                one.add(lit);
            }
            return Map.of("list", one);
        }
        throw new Unpushable();
    }

    private static boolean arity(String name, int n) {
        if (TWO.contains(name)) {
            return n == 2;
        }
        if (ONE.contains(name)) {
            return n == 1;
        }
        if (name.equals("round")) {
            return n == 1 || n == 2;
        }
        return name.equals("coalesce") && n >= 1;
    }

    private static Map<String, Object> op(String name, Object... args) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("op", name);
        m.put("args", List.of(args));
        return m;
    }

    /** {@code @.a.b} (or {@code a.b} inside a row) as the dotted column path, else null. */
    private static String column(Expr e) {
        if (e instanceof Expr.Field f) {
            if (f.target() instanceof Expr.Row || f.target() instanceof Expr.RowOrRoot) {
                return f.name();
            }
            String parent = column(f.target());
            return parent == null ? null : parent + "." + f.name();
        }
        return null;
    }

    /** Whether the expression reads the row (or anything a host cannot be handed as a value): then it must be translated, not folded. */
    private static boolean readsRow(Expr e) {
        return switch (e) {
            case Expr.Lit l -> false;
            case Expr.Ctl c -> false;
            case Expr.State c -> false;
            case Expr.Absent a -> false;
            case Expr.Not n -> readsRow(n.e());
            case Expr.Neg n -> readsRow(n.e());
            case Expr.And a -> readsRow(a.l()) || readsRow(a.r());
            case Expr.Or o -> readsRow(o.l()) || readsRow(o.r());
            case Expr.Binary b -> readsRow(b.l()) || readsRow(b.r());
            case Expr.Op b -> readsRow(b.l()) || readsRow(b.r());
            case Expr.Minus n -> readsRow(n.e());
            case Expr.Coalesce c -> readsRow(c.l()) || readsRow(c.r());
            case Expr.Ternary t -> readsRow(t.cond()) || readsRow(t.then()) || readsRow(t.otherwise());
            case Expr.In i -> readsRow(i.value()) || readsRow(i.list());
            case Expr.ListLit l -> l.items().stream().anyMatch(FilterTree::readsRow);
            case Expr.Call c -> c.args().stream().anyMatch(FilterTree::readsRow) || c.named().values().stream().anyMatch(FilterTree::readsRow);
            default -> true;
        };
    }

    private static void collect(Expr e, List<String> out) {
        String col = column(e);
        if (col != null) {
            String top = col.indexOf('.') < 0 ? col : col.substring(0, col.indexOf('.'));
            if (!out.contains(top)) {
                out.add(top);
            }
            return;
        }
        switch (e) {
            case Expr.Lit l -> { }
            case Expr.Ctl c -> { }
            case Expr.State c -> { }
            case Expr.Not n -> collect(n.e(), out);
            case Expr.Neg n -> collect(n.e(), out);
            case Expr.And a -> { collect(a.l(), out); collect(a.r(), out); }
            case Expr.Or o -> { collect(o.l(), out); collect(o.r(), out); }
            case Expr.Binary b -> { collect(b.l(), out); collect(b.r(), out); }
            case Expr.Op b -> { collect(b.l(), out); collect(b.r(), out); }
            case Expr.Minus n -> collect(n.e(), out);
            case Expr.Coalesce c -> { collect(c.l(), out); collect(c.r(), out); }
            case Expr.Ternary t -> { collect(t.cond(), out); collect(t.then(), out); collect(t.otherwise(), out); }
            case Expr.In i -> { collect(i.value(), out); collect(i.list(), out); }
            case Expr.ListLit l -> l.items().forEach(x -> collect(x, out));
            case Expr.Call c -> { c.args().forEach(x -> collect(x, out)); c.named().values().forEach(x -> collect(x, out)); }
            default -> throw new Unpushable();
        }
    }

    /** A value as a literal node (section 7): a decimal as text with {@code type: decimal}, a date as ISO text with {@code type: date}. */
    public static Map<String, Object> literal(Object value) {
        Object v = Values.simplify(value);
        if (Values.masked(v)) {
            throw new Unpushable();
        }
        Map<String, Object> m = new LinkedHashMap<>();
        if (v instanceof Node n && n.isArray()) {
            List<Object> items = new ArrayList<>();
            for (Node x : n.elements()) {
                items.add(literal(x));
            }
            return Map.of("list", items);
        }
        if (v instanceof List<?> l) {
            List<Object> items = new ArrayList<>();
            l.forEach(x -> items.add(literal(x)));
            return Map.of("list", items);
        }
        if (v instanceof BigDecimal d && d.stripTrailingZeros().scale() <= 0 && d.abs().compareTo(BigDecimal.valueOf(9007199254740991L)) <= 0) {
            m.put("lit", d.longValueExact());                         // an exact integer (level 1.1's literals) that a JSON number holds
        } else if (v instanceof BigDecimal d) {
            m.put("lit", d.toPlainString());
            m.put("type", "decimal");
        } else if (v instanceof java.time.LocalDate d) {
            m.put("lit", d.toString());
            m.put("type", "date");
        } else if (v instanceof Double d && (d.isNaN() || d.isInfinite())) {
            m.put("lit", null);
        } else if (v == null || v instanceof String || v instanceof Boolean || v instanceof Long || v instanceof Integer || v instanceof Double) {
            m.put("lit", v instanceof Integer i ? (Object) i.longValue() : v);
        } else {
            throw new Unpushable();
        }
        return m;
    }

    // ---- a received tree ----------------------------------------------------------------------------------------------

    /**
     * Checks a received tree against section 7 and the table's columns, and returns the columns it reads.
     *
     * @throws Refused {@code rdp.query.invalid} for a malformed tree or an operation outside v1; {@code rdp.column.unknown} for a column the
     *     table lacks; {@code rdp.forbidden} for a masked column
     */
    public static List<String> check(Object tree, TableSchema schema, int maxDepth) {
        List<String> cols = new ArrayList<>();
        check(tree, schema, cols, 0, maxDepth);
        return cols;
    }

    private static void check(Object tree, TableSchema schema, List<String> cols, int depth, int maxDepth) {
        if (depth > maxDepth) {
            throw invalid("the filter is deeper than " + maxDepth);
        }
        if (!(tree instanceof Map<?, ?> m)) {
            throw invalid("a filter node is an object");
        }
        if (m.containsKey("col")) {
            if (!(m.get("col") instanceof String c) || c.isEmpty()) {
                throw invalid("col is a column name");
            }
            String top = c.indexOf('.') < 0 ? c : c.substring(0, c.indexOf('.'));
            TableSchema.Column column = schema.column(top);
            if (column == null && !schema.columns().isEmpty()) {
                throw unknownColumn(schema, top);
            }
            if (column != null && column.masked()) {
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("table", schema.name());
                f.put("column", top);
                throw new Refused("rdp.forbidden", "the column '" + top + "' is masked for this reader: it cannot be filtered on", f);
            }
            if (!cols.contains(top)) {
                cols.add(top);
            }
            return;
        }
        if (m.containsKey("lit")) {
            Object type = m.get("type");
            if (type != null) {
                if (!(m.get("lit") instanceof String s) || !Ordered.set("date", "timestamp", "decimal").contains(type)) {
                    throw invalid("a literal's type is date, timestamp or decimal, on a text literal");
                }
                boolean ok = switch ((String) type) {
                    case "date" -> s.matches("\\d{4}-\\d{2}-\\d{2}");
                    case "decimal" -> s.matches("-?\\d+(\\.\\d+)?");
                    default -> s.matches("\\d{4}-\\d{2}-\\d{2}T.*(Z|[+-]\\d{2}:\\d{2})");
                };
                if (!ok) {
                    throw invalid("'" + s + "' is not a " + type + " literal");
                }
            }
            Object v = m.get("lit");
            if (v instanceof Map || v instanceof List) {
                throw invalid("a literal is a text, number, flag or null");
            }
            return;
        }
        if (m.containsKey("list")) {
            if (!(m.get("list") instanceof List<?> l)) {
                throw invalid("list is an array of nodes");
            }
            for (Object x : l) {
                check(x, schema, cols, depth + 1, maxDepth);
            }
            return;
        }
        if (!(m.get("op") instanceof String op) || !(m.get("args") instanceof List<?> args)) {
            throw invalid("a filter node is col, lit, list or op with args");
        }
        boolean known = TWO.contains(op) || MANY.contains(op) || ONE.contains(op) || op.equals("round") || op.equals("coalesce");
        if (!known) {
            throw invalid("'" + op + "' is not an operation of the Data Protocol v1");
        }
        int n = args.size();
        boolean fits = TWO.contains(op) ? n == 2 : MANY.contains(op) ? n >= 2 : ONE.contains(op) ? n == 1 : op.equals("round") ? n == 1 || n == 2 : n >= 1;
        if (!fits) {
            throw invalid("'" + op + "' does not take " + n + " argument" + (n == 1 ? "" : "s"));
        }
        if ((op.equals("in") || op.equals("not-in")) && !(args.get(1) instanceof Map<?, ?> lm && lm.containsKey("list"))) {
            throw invalid("'" + op + "' takes a value then a list");
        }
        for (Object x : args) {
            check(x, schema, cols, depth + 1, maxDepth);
        }
    }

    private static Refused invalid(String message) {
        return new Refused("rdp.query.invalid", message, null);
    }

    /** {@code rdp.column.unknown} with {@code expected}: the table's columns, nearest first. */
    public static Refused unknownColumn(TableSchema schema, String column) {
        List<String> names = new ArrayList<>(schema.names());
        names.sort(java.util.Comparator.comparingInt((String x) -> Checker.distance(x, column)));
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("table", schema.name());
        f.put("column", column);
        f.put("expected", names);
        return new Refused("rdp.column.unknown", schema.name() + " has no column '" + column + "'", f);
    }

    /** A checked tree as the Rachana-EL condition of the same meaning (section 7: {@code neg} is unary minus, {@code is-null(x)} is {@code x == null}). */
    public static String toEl(Object tree) {
        Map<?, ?> m = (Map<?, ?>) tree;
        if (m.containsKey("col")) {
            StringBuilder b = new StringBuilder("@");
            for (String part : ((String) m.get("col")).split("\\.")) {
                b.append(part.matches("[A-Za-z_][A-Za-z0-9_]*") ? "." + part : "[" + quote(part) + "]");
            }
            return b.toString();
        }
        if (m.containsKey("lit")) {
            Object v = m.get("lit");
            if (v == null) {
                return "null";
            }
            if (v instanceof String s) {
                return "decimal".equals(m.get("type")) ? s : quote(s);
            }
            return v instanceof Double || v instanceof Long ? Json.write(v) : String.valueOf(v);
        }
        if (m.containsKey("list")) {
            List<String> items = new ArrayList<>();
            ((List<?>) m.get("list")).forEach(x -> items.add(toEl(x)));
            return "[" + String.join(", ", items) + "]";
        }
        String op = (String) m.get("op");
        List<String> a = new ArrayList<>();
        ((List<?>) m.get("args")).forEach(x -> a.add(toEl(x)));
        return switch (op) {
            case "!" -> "!(" + a.get(0) + ")";
            case "neg" -> "-(" + a.get(0) + ")";
            case "is-null" -> "(" + a.get(0) + " == null)";
            case "in" -> "(" + a.get(0) + " in " + a.get(1) + ")";
            case "not-in" -> "(" + a.get(0) + " not in " + a.get(1) + ")";
            default -> MANY.contains(op) || BINARY.contains(op) ? "(" + String.join(" " + op + " ", a) + ")" : op + "(" + String.join(", ", a) + ")";
        };
    }

    private static String quote(String s) {
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }
}
