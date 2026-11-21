/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import com.ash.rachana.api.Node;
import com.ash.rachana.RachanaCode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A compiled Rachana-EL expression: an immutable tree of closures, safe to share across threads.
 * {@link #paths} reports the document paths the expression reads, so live updates can re-evaluate only
 * what a change touches.
 */
public interface Expr {

    Object eval(EvalContext c);

    /** Reports each document path ({@code $.legs[0].rate}) this expression reads. */
    default void paths(Consumer<String> sink) {}

    record Lit(Object value) implements Expr {
        public Object eval(EvalContext c) {
            return value;
        }
    }

    record Root() implements Expr {
        public Object eval(EvalContext c) {
            return c.root();
        }

        public void paths(Consumer<String> sink) {
            sink.accept("$");
        }
    }

    record Row() implements Expr {
        public Object eval(EvalContext c) {
            return c.row();
        }
    }

    /** A bare identifier: a field of the row inside a row, otherwise of the document. */
    record RowOrRoot() implements Expr {
        public Object eval(EvalContext c) {
            return c.row() != null ? c.row() : c.root();
        }
    }

    /** {@code target[*]}: the elements of a list (a field after it, {@code $.legs[*].id}, is that field of each). */
    record Each(Expr target) implements Expr {
        public Object eval(EvalContext c) {
            Object t = target.eval(c);
            return Values.masked(t) ? Values.mask() : (t instanceof Node list && list.isArray()) ? t : Node.missing();
        }

        public void paths(Consumer<String> sink) {
            target.paths(sink);
        }
    }

    /** {@code list[*].name}: the field of every element, as a list. */
    record Pluck(Expr list, String name) implements Expr {
        public Object eval(EvalContext c) {
            Object t = list.eval(c);
            if (Values.masked(t)) {
                return Values.mask();
            }
            if (!(t instanceof Node a && a.isArray())) {
                return Node.missing();
            }
            List<Node> out = new ArrayList<>(a.size());
            for (int i = 0; i < a.size(); i++) {
                out.add(a.get(i).get(name));
            }
            return Node.list(out);
        }

        public void paths(Consumer<String> sink) {
            list.paths(sink);
        }
    }

    /** {@code ctl.<id>}: the current value of a control of the Sutra (null when the context carries none). */
    record Ctl(String name) implements Expr {
        public Object eval(EvalContext c) {
            return c == null ? null : c.ctl().get(name);
        }
    }

    /**
     * {@code state.<id>}: a state value of the screen (SUTRA_EXPANSION S1, level 1.2), parsed only while a Sutra that declares state
     * is in force ({@link Declarations#states()}); empty (null) when the bind carries none and it has no default.
     */
    record State(String id) implements Expr {
        public Object eval(EvalContext c) {
            return c == null ? null : c.state(id);
        }
    }

    /**
     * {@code @<panel>}: what a panel of the screen emitted, a mapping of event name to payload ({@code @trades.open.trade});
     * {@code @parent} is the parent panel's. Nothing (missing) until the event fires.
     */
    record Event(String panel) implements Expr {
        public Object eval(EvalContext c) {
            return c == null ? Node.missing() : c.event(panel);
        }
    }

    /** {@code $src}: the record (or the records) the panel's {@code source:} names; nothing outside a sourced panel. */
    record Src() implements Expr {
        public Object eval(EvalContext c) {
            return c == null || c.src() == null ? Node.missing() : c.src();
        }
    }

    record Index() implements Expr {
        public Object eval(EvalContext c) {
            return (long) c.index();
        }
    }

    /**
     * A field access.
     *
     * @param target the object
     * @param name the field
     * @param rootPath the full document path when the chain starts at {@code $}, else null
     */
    record Field(Expr target, String name, String rootPath) implements Expr {
        public Object eval(EvalContext c) {
            Object t = c == null ? null : target.eval(c);
            return t instanceof Node n ? n.get(name) : Values.masked(t) ? Values.mask() : Node.missing();
        }

        public void paths(Consumer<String> sink) {
            if (rootPath != null) {
                sink.accept(rootPath);
            } else {
                target.paths(sink);
            }
        }
    }

    record At(Expr target, Expr index, String rootPath) implements Expr {
        public Object eval(EvalContext c) {
            Object t = target.eval(c);
            if (Values.masked(t)) {
                return Values.mask();
            }
            if (!(t instanceof Node n)) {
                return Node.missing();
            }
            Object i = Values.simplify(index.eval(c));
            if (Values.masked(i)) {
                return Values.mask();
            }
            return i instanceof Number num ? n.get(num.intValue()) : n.get(Values.text(i));
        }

        public void paths(Consumer<String> sink) {
            if (rootPath != null) {
                sink.accept(rootPath);
            } else {
                target.paths(sink);
            }
            index.paths(sink);
        }
    }

    /** {@code target[?predicate]}: the elements for which the predicate, with {@code @} bound to each, is true. */
    record Filter(Expr target, Expr predicate) implements Expr {
        public Object eval(EvalContext c) {
            Object t = target.eval(c);
            if (Values.masked(t)) {
                return Values.mask();
            }
            if (!(t instanceof Node a && a.isArray())) {
                return Node.missing();
            }
            List<Node> kept = new ArrayList<>();
            for (int i = 0; i < a.size(); i++) {
                if (Values.truthy(predicate.eval(c.withRow(a.get(i), i)))) {
                    kept.add(a.get(i));
                }
            }
            return Node.list(kept);
        }

        public void paths(Consumer<String> sink) {
            target.paths(sink);
        }
    }

    record Not(Expr e) implements Expr {
        public Object eval(EvalContext c) {
            Object v = e.eval(c);
            return Values.masked(v) ? Values.mask() : (Object) !Values.truthy(v);
        }

        public void paths(Consumer<String> sink) {
            e.paths(sink);
        }
    }

    record Neg(Expr e) implements Expr {
        public Object eval(EvalContext c) {
            Object v = e.eval(c);
            return Values.masked(v) ? Values.mask() : Values.isNull(v) ? null : Values.normalise(-Values.number(v));
        }

        public void paths(Consumer<String> sink) {
            e.paths(sink);
        }
    }

    record And(Expr l, Expr r) implements Expr {
        public Object eval(EvalContext c) {
            Object a = l.eval(c);
            if (Values.masked(a)) {
                return Values.mask();
            }
            if (!Values.truthy(a)) {
                return false;
            }
            Object b = r.eval(c);
            return Values.masked(b) ? Values.mask() : (Object) Values.truthy(b);
        }

        public void paths(Consumer<String> sink) {
            l.paths(sink);
            r.paths(sink);
        }
    }

    record Or(Expr l, Expr r) implements Expr {
        public Object eval(EvalContext c) {
            Object a = l.eval(c);
            if (Values.truthy(a)) {
                return true;
            }
            Object b = r.eval(c);
            if (Values.truthy(b)) {
                return true;
            }
            return Values.masked(a) || Values.masked(b) ? Values.mask() : (Object) false;   // unknown, never true
        }

        public void paths(Consumer<String> sink) {
            l.paths(sink);
            r.paths(sink);
        }
    }

    record Ternary(Expr cond, Expr then, Expr otherwise) implements Expr {
        public Object eval(EvalContext c) {
            Object v = cond.eval(c);
            if (Values.masked(v)) {
                return Values.mask();                                 // either branch would tell what the value is
            }
            return Values.truthy(v) ? then.eval(c) : otherwise.eval(c);
        }

        public void paths(Consumer<String> sink) {
            cond.paths(sink);
            then.paths(sink);
            otherwise.paths(sink);
        }
    }

    record Binary(String op, Expr l, Expr r) implements Expr {
        public Object eval(EvalContext c) {
            Object a = l.eval(c);
            Object b = r.eval(c);
            if (Values.masked(a) || Values.masked(b)) {
                return Values.mask();                                 // derived from a masked field: masked, and never true
            }
            switch (op) {
                case "==":
                    return Values.equal(a, b);
                case "!=":
                    return !Values.equal(a, b);
                case "+":
                    if (Values.isNull(a) && (Values.isNull(b) || Values.isNumber(b)) || Values.isNull(b) && Values.isNumber(a)) {
                        return null;                                  // arithmetic on nothing is nothing; '+' still joins a text with nothing
                    }
                    if (!(Values.isNumber(a) && Values.isNumber(b))) {
                        return Values.text(a) + Values.text(b);
                    }
                    return Values.normalise(Values.number(a) + Values.number(b));
                case "-", "*", "/", "%":
                    if (Values.isNull(a) || Values.isNull(b)) {
                        return null;                                  // null in, null out (docs/architecture/ARCHITECTURE.md §28 2.6: as SQL computes it)
                    }
                    if ("-".equals(op) && Values.simplify(a) instanceof String sa && Values.simplify(b) instanceof String sb) {
                        java.time.LocalDate x = Args.parseDate(sa);
                        java.time.LocalDate y = Args.parseDate(sb);
                        if (x != null && y != null) {
                            return java.time.temporal.ChronoUnit.DAYS.between(y, x);    // date - date: the days between (DEL's rule)
                        }
                    }
                    break;
                default:
                    break;
            }
            double x = Values.number(a);
            double y = Values.number(b);
            if ((Double.isNaN(x) || Double.isNaN(y)) && Values.simplify(a) instanceof String sa && Values.simplify(b) instanceof String sb
                    && java.util.Set.of("<", "<=", ">", ">=").contains(op)) {
                int order = sa.compareTo(sb);                     // two texts that are not numbers order as text: ISO dates order
                return switch (op) {
                    case "<" -> order < 0;
                    case "<=" -> order <= 0;
                    case ">" -> order > 0;
                    default -> order >= 0;
                };
            }
            return switch (op) {
                case "-" -> Values.normalise(x - y);
                case "*" -> Values.normalise(x * y);
                case "/" -> y == 0 ? null : Values.normalise(x / y);
                case "%" -> y == 0 ? null : Values.normalise(x % y);
                case "<" -> x < y;
                case "<=" -> x <= y;
                case ">" -> x > y;
                case ">=" -> x >= y;
                default -> throw new ElException(RachanaCode.EL_EVAL, "unknown operator " + op);
            };
        }

        public void paths(Consumer<String> sink) {
            l.paths(sink);
            r.paths(sink);
        }
    }

    /**
     * A call of an eager function. {@code named} holds the named arguments a function keeps by name ({@code where = ...},
     * {@code by = [...]}, {@code ignore = ALL}: the keyword-only parameters of its signature, read by the engine's compiler); a named
     * argument of a positional parameter is bound to its place by the parser. Empty for most calls.
     */
    record Call(String name, ElFunction fn, List<Expr> args, java.util.Map<String, Expr> named) implements Expr {
        public Call {
            named = named == null || named.isEmpty() ? java.util.Map.of()
                    : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(named));
        }

        public Call(String name, ElFunction fn, List<Expr> args) {
            this(name, fn, args, null);
        }

        /** The named argument {@code key}, or null when the call does not name it. */
        public Expr arg(String key) {
            return named.get(key);
        }

        public Object eval(EvalContext c) {
            if (!named.isEmpty()) {
                throw new ElException(RachanaCode.EL_EVAL, name + ": " + String.join(", ", named.keySet().stream().map(k -> k + " =").toList())
                        + " belongs to a measure of a model or a column of a table, computed by the pivot engine; a list has no such argument");
            }
            if (fn == null) {
                throw new ElException(RachanaCode.EL_EVAL, name + " is not a function of Rachana");
            }
            List<Object> values = new ArrayList<>(args.size());
            boolean masked = false;
            for (Expr a : args) {
                Object v = a.eval(c);
                masked |= Values.masked(v);
                values.add(v);
            }
            // a function of a masked value is masked (coalesce only passes it on when it is the first value it has)
            return masked && !Functions.passesMask(name) ? Values.mask() : fn.apply(values, c);
        }

        public void paths(Consumer<String> sink) {
            args.forEach(a -> a.paths(sink));
            named.values().forEach(a -> a.paths(sink));
        }
    }

    /** A call of a form ({@link ElForm}): its arguments handed over unevaluated ({@code map}, {@code iferror} …). */
    record Form(String name, ElForm fn, List<Expr> args) implements Expr {
        public Object eval(EvalContext c) {
            return fn.apply(args, c);
        }

        /** The same form over other arguments (a compiler rewriting a tree). */
        public Form withArgs(List<Expr> next) {
            return new Form(name, fn, List.copyOf(next));
        }

        public void paths(Consumer<String> sink) {
            args.forEach(a -> a.paths(sink));
        }
    }

    /** {@code let(n = expr, ..., result)}: each name bound in turn (a later value may read an earlier name), then the result. */
    record Let(List<String> names, List<Expr> values, Expr body) implements Expr {
        public Object eval(EvalContext c) {
            EvalContext inner = c;
            for (int i = 0; i < names.size(); i++) {
                inner = inner.withLocal(names.get(i), values.get(i).eval(inner));
            }
            return body.eval(inner);
        }

        public void paths(Consumer<String> sink) {
            values.forEach(v -> v.paths(sink));
            body.paths(sink);
        }
    }

    /** A name bound by {@code let}, or {@code @acc} inside {@code reduce}. */
    record Local(String name) implements Expr {
        public Object eval(EvalContext c) {
            return c.local(name);
        }
    }

    /**
     * {@code [a, b, c]}: a list written in the expression (the right side of {@code in}, a {@code by = [...]} or {@code ignore = [...]},
     * an argument of a list function); a masked item stays masked in its place.
     */
    record ListLit(List<Expr> items) implements Expr {
        public ListLit {
            items = List.copyOf(items);
        }

        public Object eval(EvalContext c) {
            List<Object> values = new ArrayList<>(items.size());
            for (Expr e : items) {
                if (e instanceof Spread s) {                      // level 1.1: [...a, b] splices the list a
                    Object v = Values.simplify(s.e().eval(c));
                    if (Values.masked(v)) {
                        return Values.mask();
                    }
                    if (v == null) {
                        continue;
                    }
                    if (!(v instanceof Node n && n.isArray())) {
                        throw Args.typeError("spread", v, "a list (a list spreads into a list)");
                    }
                    for (int i = 0; i < n.size(); i++) {
                        values.add(n.get(i));
                    }
                    if (values.size() > Parser.MAX_RECORD_FIELDS * 10) {
                        throw new ElException(RachanaCode.EL_EVAL, "a list written in an expression holds at most " + Parser.MAX_RECORD_FIELDS * 10
                                + " values with its spreads");
                    }
                    continue;
                }
                values.add(e.eval(c));
            }
            return Args.list(values);
        }

        public void paths(Consumer<String> sink) {
            items.forEach(i -> i.paths(sink));
        }
    }

    /**
     * {@code x in list}, {@code x not in list}: strict membership (docs/architecture/ARCHITECTURE.md §28 Q3). An empty list, an empty value or nothing
     * at all holds nothing, so {@code x in []} is false and {@code x not in []} true (unlike {@code one_of}, whose empty choice holds
     * everything, for controls). Equality is the null rule's: {@code null} equals only {@code null}. A value that is not a list is a list
     * of one. A masked value or list gives the mask (a filter on it is never true).
     */
    record In(Expr value, Expr list, boolean negated) implements Expr {
        public Object eval(EvalContext c) {
            Object v = value.eval(c);
            Object l = list.eval(c);
            if (Values.masked(v) || Values.masked(l)) {
                return Values.mask();
            }
            return holds(v, Values.simplify(l)) != negated;
        }

        /** True when {@code list} holds {@code v}. */
        static boolean holds(Object v, Object list) {
            if (list instanceof Node n && n.isArray()) {
                for (int i = 0; i < n.size(); i++) {
                    if (!Values.masked(n.get(i)) && Values.equal(v, n.get(i))) {
                        return true;
                    }
                }
                return false;
            }
            if (list == null || list instanceof String s && s.isEmpty() || list instanceof Node) {
                return false;
            }
            return Values.equal(v, list);
        }

        public void paths(Consumer<String> sink) {
            value.paths(sink);
            list.paths(sink);
        }
    }

    /**
     * {@code ^}: the parent of the current element ({@code ^^} its parent, and so on): in a filter over a leg's cash flows,
     * {@code ^.LEG_TYPE} is the leg's; for a row of a table, the document. Missing when there is no such parent (docs/architecture/ARCHITECTURE.md §28
     * Q8).
     *
     * @param up 1 for {@code ^}, 2 for {@code ^^}
     */
    record Parent(int up) implements Expr {
        public Object eval(EvalContext c) {
            return c == null ? Node.missing() : c.parent(up);
        }
    }

    /** An optional positional argument left out before a named one: evaluates to nothing, read by a function as its default. */
    record Absent() implements Expr {
        public Object eval(EvalContext c) {
            return null;
        }
    }

    // ---- language level 1.1 (EL_EXPANSION X2, X6, X7, X11 to X13; DECISIONS PY2 to PY7) -----------------------------------------

    /**
     * A binary operator of level 1.1 ({@code + - * / %}, the comparisons, {@code == !=}): the rules of {@link Binary} with the numbers of
     * SPEC 4.16 (integers and decimals exact, {@code /} to 34 digits, a double on either side a double) and texts ordered by code point.
     */
    record Op(String op, Expr l, Expr r) implements Expr {
        public Object eval(EvalContext c) {
            Object a = l.eval(c);
            Object b = r.eval(c);
            if (Values.masked(a) || Values.masked(b)) {
                return Values.mask();
            }
            switch (op) {
                case "==":
                    return Exact.equal(a, b);
                case "!=":
                    return !Exact.equal(a, b);
                case "+":
                    if (Values.isNull(a) && (Values.isNull(b) || Values.isNumber(b)) || Values.isNull(b) && Values.isNumber(a)) {
                        return null;
                    }
                    if (!(Values.isNumber(a) && Values.isNumber(b))) {
                        return Values.text(a) + Values.text(b);
                    }
                    return Exact.arithmetic("+", Exact.operand(a), Exact.operand(b));
                case "-", "*", "/", "%":
                    if (Values.isNull(a) || Values.isNull(b)) {
                        return null;
                    }
                    if ("-".equals(op) && Values.simplify(a) instanceof String sa && Values.simplify(b) instanceof String sb) {
                        java.time.LocalDate x = Args.parseDate(sa);
                        java.time.LocalDate y = Args.parseDate(sb);
                        if (x != null && y != null) {
                            return java.time.temporal.ChronoUnit.DAYS.between(y, x);
                        }
                    }
                    return Exact.arithmetic(op, Exact.operand(a), Exact.operand(b));
                default:
                    break;
            }
            Integer order = Exact.compare(Exact.operand(a), Exact.operand(b));
            if (order == null) {
                if (Values.simplify(a) instanceof String sa && Values.simplify(b) instanceof String sb) {
                    order = TextOrder.compare(sa, sb);                // two texts: by code point (SPEC 4.13)
                } else {
                    return false;
                }
            }
            return switch (op) {
                case "<" -> order < 0;
                case "<=" -> order <= 0;
                case ">" -> order > 0;
                case ">=" -> order >= 0;
                default -> throw new ElException(RachanaCode.EL_EVAL, "unknown operator " + op);
            };
        }

        public void paths(Consumer<String> sink) {
            l.paths(sink);
            r.paths(sink);
        }
    }

    /** Unary minus at level 1.1: an exact number stays exact. */
    record Minus(Expr e) implements Expr {
        public Object eval(EvalContext c) {
            Object v = e.eval(c);
            return Values.masked(v) ? Values.mask() : Values.isNull(v) ? null : Exact.negate(Exact.operand(v));
        }

        public void paths(Consumer<String> sink) {
            e.paths(sink);
        }
    }

    /**
     * {@code a ** b} (PY6; {@code ^} between two values is the same operator, for Excel's users): right-associative, tighter than a
     * unary sign on its left ({@code -2 ** 2} is {@code -4}). A whole exponent of at most 64 on an exact base is exact, as {@code power}.
     */
    record Power(Expr l, Expr r) implements Expr {
        public Object eval(EvalContext c) {
            Object a = l.eval(c);
            Object b = r.eval(c);
            if (Values.masked(a) || Values.masked(b)) {
                return Values.mask();
            }
            if (Values.isNull(a) || Values.isNull(b)) {
                return null;
            }
            return Exact.power(Exact.operand(a), Exact.operand(b));
        }

        public void paths(Consumer<String> sink) {
            l.paths(sink);
            r.paths(sink);
        }
    }

    /** {@code a ?? b} (X12): {@code a} unless it is empty, else {@code b}, evaluated only then; {@code ''} and {@code 0} are values. */
    record Coalesce(Expr l, Expr r) implements Expr {
        public Object eval(EvalContext c) {
            Object a = l.eval(c);
            return Values.isNull(a) && !Values.masked(a) ? r.eval(c) : a;
        }

        public void paths(Consumer<String> sink) {
            l.paths(sink);
            r.paths(sink);
        }
    }

    /** {@code ...e} inside a list or a record literal (X6). */
    record Spread(Expr e) implements Expr {
        public Object eval(EvalContext c) {
            throw new ElException(RachanaCode.EL_EVAL, "'...' spreads only inside a list or a record");
        }

        public void paths(Consumer<String> sink) {
            e.paths(sink);
        }
    }

    /** {@code key: value} of a record literal. */
    record Pair(String key, Expr value) implements Expr {
        public Object eval(EvalContext c) {
            return value.eval(c);
        }

        public void paths(Consumer<String> sink) {
            value.paths(sink);
        }
    }

    /**
     * <code>{ key: value, ...r }</code> (X6): a record, its keys in written order; a later key or spread replaces an earlier one in its
     * place. A spread of empty adds nothing; of a masked value, the record is masked.
     */
    record RecordLit(List<Expr> entries) implements Expr {
        public RecordLit {
            entries = List.copyOf(entries);
        }

        public Object eval(EvalContext c) {
            java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
            for (Expr e : entries) {
                if (e instanceof Pair p) {
                    out.put(p.key(), Args.node(p.value().eval(c)));
                    continue;
                }
                Object v = Values.simplify(((Spread) e).e().eval(c));
                if (Values.masked(v)) {
                    return Values.mask();
                }
                if (v == null) {
                    continue;
                }
                if (!(v instanceof Node n && n.kind() == Node.Kind.OBJECT)) {
                    throw Args.typeError("spread", v, "a record (a record spreads into a record)");
                }
                n.fields().forEach((k, x) -> out.put(k, x));
                if (out.size() > Parser.MAX_RECORD_FIELDS) {
                    throw new ElException(RachanaCode.EL_EVAL, "a record holds at most " + Parser.MAX_RECORD_FIELDS + " fields");
                }
            }
            return Node.of(out);
        }

        public void paths(Consumer<String> sink) {
            entries.forEach(e -> e.paths(sink));
        }
    }

    /**
     * {@code target[start:stop]}, {@code target[start:stop:step]} (PY4, Python's slice): end-exclusive, a negative end counts from the
     * end, out of range is clamped; a list gives a list, a text a text (by code point); a step of 0 is {@code RCH-2102}.
     */
    record Slice(Expr target, Expr start, Expr stop, Expr step) implements Expr {
        public Object eval(EvalContext c) {
            Object t = Values.simplify(target.eval(c));
            Object a = start == null ? null : Values.simplify(start.eval(c));
            Object b = stop == null ? null : Values.simplify(stop.eval(c));
            Object s = step == null ? null : Values.simplify(step.eval(c));
            if (Values.masked(t) || Values.masked(a) || Values.masked(b) || Values.masked(s)) {
                return Values.mask();
            }
            return Ranges.slice(t, a, b, s);
        }

        public void paths(Consumer<String> sink) {
            target.paths(sink);
            if (start != null) {
                start.paths(sink);
            }
            if (stop != null) {
                stop.paths(sink);
            }
            if (step != null) {
                step.paths(sink);
            }
        }
    }

    /**
     * {@code lambda x: body}, {@code lambda acc, x: body} (X13 in Python's spelling, PY3): a function value, second class. It is passed to
     * {@code map}, {@code sort}, {@code reduce} or {@code group}, or bound by {@code let} and called; {@code @} inside keeps its outer
     * meaning. Evaluated, it is the function closed over its context.
     */
    record Lambda(List<String> params, Expr body) implements Expr {
        public Lambda {
            params = List.copyOf(params);
        }

        public Object eval(EvalContext c) {
            return new Closure(this, c);
        }

        public void paths(Consumer<String> sink) {
            body.paths(sink);
        }
    }

    /** A {@code let} name bound to a lambda, passed on as a function ({@code map($.legs, f)}). */
    record FnRef(String name) implements Expr {
        public Object eval(EvalContext c) {
            return c.local(name);
        }
    }

    /** {@code f(a, ...)} where {@code let} bound {@code f} to a lambda. */
    record LocalCall(String name, List<Expr> args) implements Expr {
        public LocalCall {
            args = List.copyOf(args);
        }

        public Object eval(EvalContext c) {
            List<Object> values = new ArrayList<>(args.size());
            for (Expr a : args) {
                values.add(a.eval(c));
            }
            return ((Closure) c.local(name)).call(values);
        }

        public void paths(Consumer<String> sink) {
            args.forEach(a -> a.paths(sink));
        }
    }

    // ---- level 1.2: a Sutra's declared names (EL_EXPANSION X41, X42; SUTRA_EXPANSION S2) ---------------------------------------------

    /** The context a declared name is evaluated in: the document, controls, date and events, with no current row and no outer names. */
    private static EvalContext bare(EvalContext c) {
        return c == null ? null : new EvalContext(c.root(), null, -1, c.formats(), c.ctl(), c.today(), c.scope(), null);
    }

    /** A Sutra's {@code let:} name: its value over the document, evaluated at most once per bind ({@link Declarations#within}). */
    record SutraLet(String name, Expr value) implements Expr {
        public Object eval(EvalContext c) {
            Declarations.Scope scope = Declarations.current();
            if (scope == null || c == null) {
                return value.eval(bare(c));
            }
            Object[] held = scope.memo.get(name);
            if (held != null && held[0] == c.root() && held[1] == c.ctl() && java.util.Objects.equals(held[2], c.today())) {
                return held[3];
            }
            Object v = value.eval(bare(c));
            scope.memo.put(name, new Object[] {c.root(), c.ctl(), c.today(), v});
            return v;
        }

        public void paths(Consumer<String> sink) {
            value.paths(sink);
        }
    }

    /** A call of a declared function: the arguments evaluated where the call is, the body in a scope of its own with the parameters. */
    record UserCall(String name, List<String> params, List<Expr> args, Expr body) implements Expr {
        public UserCall {
            params = List.copyOf(params);
            args = List.copyOf(args);
        }

        public Object eval(EvalContext c) {
            List<Object> values = new ArrayList<>(args.size());
            for (Expr a : args) {
                values.add(a.eval(c));
            }
            EvalContext inner = bare(c);
            if (inner == null) {
                inner = new EvalContext(null, null, -1, null);
            }
            for (int i = 0; i < params.size(); i++) {
                inner = inner.withLocal(params.get(i), values.get(i));
            }
            return body.eval(inner);
        }

        public void paths(Consumer<String> sink) {
            args.forEach(a -> a.paths(sink));
        }
    }
}
