/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import com.ash.rachana.internal.NumberText;
import com.ash.rachana.internal.CoreText;
import com.ash.rachana.el.Token.Type;
import com.ash.rachana.el.check.Signatures;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Recursive-descent parser for Rachana-EL. Grammar (EBNF):
 *
 * <pre>
 * expr     = or [ "?" expr ":" expr ] ;
 * or       = and { ("||" | "or") and } ;
 * and      = not { ("&amp;&amp;" | "and") not } ;
 * not      = "not" not | equality ;          (Python's precedence: not a == b is !(a == b))
 * equality = compare { ("==" | "!=") compare } ;
 * compare  = sum { ("&lt;" | "&lt;=" | "&gt;" | "&gt;=") sum | ["not"] "in" sum } ;
 * sum      = product { ("+" | "-") product } ;
 * product  = unary { ("*" | "/" | "%") unary } ;
 * unary    = ("!" | "-") unary | postfix ;
 * postfix  = primary { "." ident | "[" expr "]" | "[?" expr "]" } ;
 * primary  = number | string | "true" | "false" | "null" | "$" | "@" | "^" { "^" } | "#index"
 *          | "[" [ expr { "," expr } [","] ] "]"
 *          | ident "(" [ arg { "," arg } ] ")" | ident | "(" expr ")" ;
 * arg      = [ ident "=" ] expr ;
 * </pre>
 *
 * A bare identifier is a field of the current row inside a row, otherwise of the document. {@code ^} is the current element's parent
 * ({@code ^^} its parent). {@code [a, b]} is a list; {@code x in list} and {@code x not in list} test membership strictly (an empty
 * list holds nothing). A named argument {@code name = expr} is bound by the function's signature: to its place for a positional
 * parameter, kept by name for a keyword-only one ({@code where}, {@code by}, {@code ignore} ...) (docs/architecture/ARCHITECTURE.md §28 2.1).
 *
 * <p>Both the parse and the tree it builds are bounded by {@link ElLimits}: the parser counts how deep it has recursed, and every node
 * records how deep its subtree is (a chain {@code a + b + c} parses in a loop but builds a tree as deep as it is long, and evaluation
 * walks that tree recursively). Past either bound the expression is a compile error ({@code RCH-2101}) at the offending position, never
 * a {@link StackOverflowError}. Every node records where it was written ({@link #spans}), so the checker can point at it.
 */
final class Parser {

    private final List<Token> tokens;
    private final ElLimits limits;
    private final String src;
    /** Depth of each composite node built so far (leaves are 1); identity, since equal records may differ in place. */
    private final Map<Expr, Integer> depths = new IdentityHashMap<>();
    /** Where each node was written: {start, end} character offsets. */
    private final Map<Expr, int[]> spans = new IdentityHashMap<>();
    private int p;
    private int nesting;
    /** The names {@code let} has bound around the part being parsed, innermost first. */
    private final java.util.ArrayDeque<String> letNames = new java.util.ArrayDeque<>();
    /** Above zero inside {@code reduce}'s expression, where {@code @acc} is the value so far. */
    private int accDepth;

    private Parser(String src, List<Token> tokens, ElLimits limits) {
        this(src, tokens, limits, false);
    }

    private Parser(String src, List<Token> tokens, ElLimits limits, boolean v11) {
        this.src = src;
        this.tokens = tokens;
        this.limits = limits;
        this.v11 = v11;
    }

    /** Language level 1.1 or later: the syntax batch (EL_EXPANSION X6, X7, X11 to X14; DECISIONS PY2 to PY6) and exact numbers (X2). */
    private final boolean v11;

    /** The {@code let} names bound to a lambda around the part being parsed, with the lambda's count of parameters. */
    private final Map<String, Integer> letFns = new LinkedHashMap<>();

    /** The most values a list written in an expression holds. */
    static final int MAX_LIST_ITEMS = 1_000;

    /** The most fields a record holds (EL_EXPANSION X6). */
    static final int MAX_RECORD_FIELDS = 1_000;

    /** The forms a lambda may be passed to, with the argument places that take one and its count of parameters (X13). */
    private static int lambdaParams(String form, int place) {
        return switch (form) {
            case "map", "group" -> place == 1 ? 1 : -1;
            case "sort" -> place % 2 == 1 ? 1 : -1;
            case "reduce" -> place == 2 ? 2 : -1;
            default -> -1;
        };
    }

    /** The message when the expression stops where a value is still needed ({@code $.mtm >}). */
    static final String ENDS_EARLY = "the expression ends early: a value is missing";

    /** A parsed expression and where each of its nodes was written. */
    record Parsed(Expr expr, Map<Expr, int[]> spans) {}

    static Expr parse(String src) {
        return parse(src, ElLimits.DEFAULTS);
    }

    static Expr parse(String src, ElLimits limits) {
        return parseWithSpans(src, limits).expr();
    }

    /** Parses against one instance's function table (split step A6). */
    static Expr parse(String src, ElLimits limits, FunctionTable table) {
        return parseWithSpans(src, limits, table).expr();
    }

    /** Parses with extra function names (measure functions) that are later expanded: arguments are parsed, nothing else is checked. */
    static Expr parse(String src, Set<String> macros) {
        return parse(src, macros, FunctionTable.core());
    }

    /** {@link #parse(String, Set)} against one instance's function table. */
    static Expr parse(String src, Set<String> macros, FunctionTable table) {
        Parser parser = new Parser(src, Lexer.tokens(src), ElLimits.DEFAULTS);
        parser.macros = macros;
        parser.table = table;
        Expr e = parser.expr();
        return e;
    }

    private Set<String> macros = Set.of();
    private FunctionTable table = FunctionTable.core();
    /** The Sutra's declared names in force (level 1.2, X41, X42, S2); {@link Declarations#NONE} elsewhere. */
    private Declarations decls = Declarations.NONE;
    /** The let names a let value may read (the ones written before it); null: all. */
    private List<String> visibleLets;
    /** The declared function whose body is read, or null. */
    private String inFunction;
    /** The declared functions being inlined, outermost first (a call graph with a cycle is refused). */
    private java.util.Deque<String> inlining = new java.util.ArrayDeque<>();

    static Parsed parseWithSpans(String src, ElLimits limits) {
        return parseWithSpans(src, limits, FunctionTable.core());
    }

    static Parsed parseWithSpans(String src, ElLimits limits, FunctionTable table) {
        return parseWithSpans(src, limits, table, false);
    }

    /** {@link #parseWithSpans(String, ElLimits, FunctionTable)} at language level 1.1 when {@code v11}. */
    static Parsed parseWithSpans(String src, ElLimits limits, FunctionTable table, boolean v11) {
        if (src.length() > limits.maxLength()) {
            throw new ElException("expression is longer than " + limits.maxLength()
                    + " characters (drishti.rachana.max-expression-length)", limits.maxLength());
        }
        Parser parser = new Parser(src, Lexer.tokens(src, v11), limits, v11);
        parser.table = table;
        parser.decls = Declarations.inForce();
        Expr e = parser.expr();
        if (parser.peek().type() != Type.EOF) {
            throw new ElException("unexpected '" + parser.peek().text() + "'", parser.peek().pos());
        }
        return new Parsed(e, parser.spans);
    }

    private Token peek() {
        return tokens.get(p);
    }

    private Token next() {
        return tokens.get(p++);
    }

    private boolean accept(Type t, String text) {
        if (peek().is(t, text)) {
            p++;
            return true;
        }
        return false;
    }

    private Token expect(Type t, String what) {
        if (peek().type() != t) {
            throw new ElException("expected " + what + (peek().type() == Type.EOF ? " but the expression ends" : " but found '" + peek().text() + "'"),
                    peek().pos());
        }
        return next();
    }

    /** Enters one level of recursion; past the bound, a located compile error instead of a stack overflow. */
    private void descend() {
        if (++nesting > limits.maxDepth()) {
            throw tooDeep();
        }
    }

    private ElException tooDeep() {
        return new ElException("expression nested deeper than " + limits.maxDepth()
                + " levels (drishti.rachana.max-expression-depth)", peek().pos());
    }

    /** Records {@code e}'s subtree depth (one more than its deepest part), refusing a tree deeper than the bound. */
    private Expr node(Expr e, Expr... parts) {
        int d = 0;
        for (Expr part : parts) {
            d = Math.max(d, depths.getOrDefault(part, 1));
        }
        if (d + 1 > limits.maxDepth()) {
            throw tooDeep();
        }
        depths.put(e, d + 1);
        return e;
    }

    /** Records that {@code e} was written from {@code start} to where the parser now is (trailing blanks left out). */
    private Expr at(Expr e, int start) {
        int end = p > 0 ? Math.min(src.length(), peek().type() == Type.EOF ? src.length() : peek().pos()) : start;
        while (end > start && CoreText.isWhitespace(src.charAt(end - 1))) {
            end--;
        }
        spans.putIfAbsent(e, new int[] {start, Math.max(start, end)});
        return e;
    }

    private Expr expr() {
        if (v11) {
            return pipe();
        }
        descend();
        int s = peek().pos();
        try {
            Expr c = or();
            if (peek().type() == Type.QUESTION) {
                next();
                Expr a = expr();
                expect(Type.COLON, "':'");
                Expr b = expr();
                return at(node(new Expr.Ternary(c, a, b), c, a, b), s);
            }
            return c;
        } finally {
            nesting--;
        }
    }

    /** Level 1.1: {@code x |> f(a)} is {@code f(x, a)} (X11), the lowest precedence; the right side is a call. */
    private Expr pipe() {
        descend();
        int s = peek().pos();
        try {
            Expr l = conditional();
            while (peek().is(Type.OP, "|>")) {
                Token bar = next();
                Token name = peek();
                if (name.type() != Type.IDENT || tokens.get(p + 1).type() != Type.LPAREN || "let".equals(name.text())
                        || "lambda".equals(name.text())) {
                    throw new ElException("the right side of '|>' is a call, as in x |> round(2)", name.type() == Type.EOF ? bar.pos() : name.pos());
                }
                next();
                l = at(call(name, l), s);
            }
            return l;
        } finally {
            nesting--;
        }
    }

    /** Level 1.1: {@code c ? a : b} and Python's {@code a if c else b} (PY2) over {@code ??}. */
    private Expr conditional() {
        descend();
        int s = peek().pos();
        try {
            Expr c = coalesce();
            if (peek().type() == Type.QUESTION) {
                next();
                Expr a = expr();
                expect(Type.COLON, "':'");
                Expr b = expr();
                return at(node(new Expr.Ternary(c, a, b), c, a, b), s);
            }
            if (peek().is(Type.IDENT, "if")) {
                next();
                Expr test = coalesce();
                if (!peek().is(Type.IDENT, "else")) {
                    throw new ElException("expected 'else'" + (peek().type() == Type.EOF ? " but the expression ends" : " but found '" + peek().text() + "'")
                            + ": write a if c else b", peek().pos());
                }
                next();
                Expr b = conditional();
                return at(node(new Expr.Ternary(test, c, b), test, c, b), s);
            }
            return c;
        } finally {
            nesting--;
        }
    }

    /** Level 1.1: {@code a ?? b} (X12), between the conditional and {@code ||}. */
    private Expr coalesce() {
        int s = peek().pos();
        Expr l = or();
        while (accept(Type.OP, "??")) {
            Expr r = or();
            l = at(node(new Expr.Coalesce(l, r), l, r), s);
        }
        return l;
    }

    /** A binary operator: level 1.0's {@link Expr.Binary}, level 1.1's {@link Expr.Op} (exact numbers, code point order). */
    private Expr binary(String op, Expr l, Expr r) {
        return v11 ? new Expr.Op(op, l, r) : new Expr.Binary(op, l, r);
    }

    private Expr or() {
        int s = peek().pos();
        Expr l = and();
        while (accept(Type.OP, "||") || accept(Type.IDENT, "or")) {        // PY1: or is first-class
            Expr r = and();
            l = at(node(new Expr.Or(l, r), l, r), s);
        }
        return l;
    }

    private Expr and() {
        int s = peek().pos();
        Expr l = notWord();
        while (accept(Type.OP, "&&") || accept(Type.IDENT, "and")) {       // PY1: and is first-class
            Expr r = notWord();
            l = at(node(new Expr.And(l, r), l, r), s);
        }
        return l;
    }

    /** PY1: {@code not x} as Python reads it, below the comparisons ({@code not a == b} is {@code !(a == b)}). */
    private Expr notWord() {
        int s = peek().pos();
        if (peek().is(Type.IDENT, "not") && !tokens.get(p + 1).is(Type.IDENT, "in")) {
            next();
            descend();
            try {
                Expr e = notWord();
                return at(node(new Expr.Not(e), e), s);
            } finally {
                nesting--;
            }
        }
        return equality();
    }

    private Expr equality() {
        int s = peek().pos();
        Expr l = compare();
        while (peek().is(Type.OP, "==") || peek().is(Type.OP, "!=")) {
            String op = next().text();
            Expr r = compare();
            l = at(node(binary(op, l, r), l, r), s);
        }
        return l;
    }

    private Expr compare() {
        int s = peek().pos();
        Expr l = sum();
        while (true) {
            if (peek().type() == Type.OP && List.of("<", "<=", ">", ">=").contains(peek().text())) {
                String op = next().text();
                Expr r = sum();
                l = at(node(binary(op, l, r), l, r), s);
            } else if (peek().is(Type.IDENT, "in")) {
                next();
                Expr r = sum();
                l = at(node(new Expr.In(l, r, false), l, r), s);
            } else if (peek().is(Type.IDENT, "not") && tokens.get(p + 1).is(Type.IDENT, "in")) {
                p += 2;
                Expr r = sum();
                l = at(node(new Expr.In(l, r, true), l, r), s);
            } else {
                return l;
            }
        }
    }

    private Expr sum() {
        int s = peek().pos();
        Expr l = product();
        while (peek().is(Type.OP, "+") || peek().is(Type.OP, "-")) {
            String op = next().text();
            Expr r = product();
            l = at(node(binary(op, l, r), l, r), s);
        }
        return l;
    }

    private Expr product() {
        int s = peek().pos();
        Expr l = unary();
        while (peek().is(Type.OP, "*") || peek().is(Type.OP, "/") || peek().is(Type.OP, "%")) {
            String op = next().text();
            Expr r = unary();
            l = at(node(binary(op, l, r), l, r), s);
        }
        return l;
    }

    private Expr unary() {
        int s = peek().pos();
        boolean not = accept(Type.OP, "!");
        if (!not && !accept(Type.OP, "-")) {
            return v11 ? power() : postfix();
        }
        descend();
        try {
            Expr e = unary();
            return at(node(not ? new Expr.Not(e) : v11 ? new Expr.Minus(e) : new Expr.Neg(e), e), s);
        } finally {
            nesting--;
        }
    }

    /** Level 1.1: {@code a ** b} and {@code a ^ b} (PY6), right-associative, tighter than a sign on its left. */
    private Expr power() {
        int s = peek().pos();
        Expr e = postfix();
        if (peek().is(Type.OP, "**") || peek().type() == Type.CARET) {
            next();
            descend();
            try {
                Expr r = unary();
                return at(node(new Expr.Power(e, r), e, r), s);
            } finally {
                nesting--;
            }
        }
        return e;
    }

    private Expr postfix() {
        int s = peek().pos();
        Expr e = primary();
        String path = e instanceof Expr.Root ? "$" : null;
        while (true) {
            Token t = peek();
            if (t.type() == Type.DOT) {
                next();
                String name = expect(Type.IDENT, "a field name").text();
                path = path == null ? null : path + "." + name;
                e = e instanceof Expr.Each || e instanceof Expr.Pluck ? node(new Expr.Pluck(e, name), e) : node(new Expr.Field(e, name, path), e);
            } else if (t.type() == Type.LBRACKET && tokens.get(p + 1).is(Type.OP, "*") && tokens.get(p + 2).type() == Type.RBRACKET) {
                p += 3;                                          // [*]: every element; a field after it is that field of each
                e = node(new Expr.Each(e), e);
                path = null;
            } else if (v11 && t.type() == Type.LBRACKET && (tokens.get(p + 1).type() == Type.COLON || sliceAhead())) {
                next();                                          // level 1.1: t[a:b], t[a:b:c], parts optional (PY4)
                Expr a = peek().type() == Type.COLON ? null : expr();
                expect(Type.COLON, "':'");
                Expr b = peek().type() == Type.COLON || peek().type() == Type.RBRACKET ? null : expr();
                Expr step = null;
                if (accept(Type.COLON, ":")) {
                    step = peek().type() == Type.RBRACKET ? null : expr();
                }
                expect(Type.RBRACKET, "']'");
                List<Expr> parts = new ArrayList<>(List.of(e));
                for (Expr x : new Expr[] {a, b, step}) {
                    if (x != null) {
                        parts.add(x);
                    }
                }
                e = node(new Expr.Slice(e, a, b, step), parts.toArray(Expr[]::new));
                path = null;
            } else if (t.type() == Type.LBRACKET) {
                next();
                Expr idx = expr();
                expect(Type.RBRACKET, "']'");
                path = path != null && idx instanceof Expr.Lit lit ? path + "[" + Values.text(lit.value()) + "]" : null;
                e = node(new Expr.At(e, idx, path), e, idx);
            } else if (t.type() == Type.FILTER) {
                next();
                Expr pred = expr();
                expect(Type.RBRACKET, "']'");
                e = node(new Expr.Filter(e, pred), e, pred);
                path = null;
            } else {
                return e;
            }
            at(e, s);
        }
    }

    private Expr primary() {
        Token t = next();
        int s = t.pos();
        switch (t.type()) {
            case NUMBER:
                if (v11) {
                    if (t.text().indexOf('e') < 0 && t.text().indexOf('E') < 0) {
                        try {
                            return at(new Expr.Lit(Exact.literal(t.text(), t.pos(), t.pos() + t.length())), s);    // X2: exact, as written
                        } catch (NumberFormatException e) {
                            throw new ElException("bad number '" + t.text() + "'", t.pos());
                        }
                    }
                    try {
                        return at(new Expr.Lit(NumberText.parse(t.text())), s);             // an exponent: a double
                    } catch (NumberFormatException e) {
                        throw new ElException("bad number '" + t.text() + "'", t.pos());
                    }
                }
                try {
                    double d = NumberText.parse(t.text());
                    return at(new Expr.Lit(Values.normalise(d)), s);
                } catch (NumberFormatException e) {
                    throw new ElException("bad number '" + t.text() + "'", t.pos());
                }
            case STRING:
                return at(new Expr.Lit(t.text()), s);
            case DOLLAR:
                return at(new Expr.Root(), s);
            case AT:
                return at(new Expr.Row(), s);
            case CARET: {
                int up = 1;
                while (peek().type() == Type.CARET && peek().pos() == s + up) {
                    next();
                    up++;
                }
                return at(new Expr.Parent(up), s);
            }
            case EVENT:
                if ("acc".equals(t.text()) && accDepth > 0) {
                    return at(new Expr.Local(ListFunctions.ACC), s);    // @acc inside reduce: the value so far, never a panel's events
                }
                return at(new Expr.Event(t.text()), s);
            case SRC:
                return at(new Expr.Src(), s);
            case HASH_INDEX:
                return at(new Expr.Index(), s);
            case LBRACKET:
                return list(s);
            case LBRACE:
                return record(s);
            case LPAREN: {
                Expr e = expr();
                expect(Type.RPAREN, "')'");
                return e;
            }
            case IDENT:
                return identifier(t);
            case EOF:
                throw new ElException(ENDS_EARLY, t.pos());
            default:
                throw new ElException("unexpected '" + t.text() + "'", t.pos());
        }
    }

    /** {@code [a, b, c]} (the opening bracket read): a list literal; a trailing comma is allowed, {@code []} is the empty list. */
    private Expr list(int s) {
        List<Expr> items = new ArrayList<>();
        while (peek().type() != Type.RBRACKET) {
            if (peek().type() == Type.SPREAD) {
                int at = next().pos();
                Expr e = expr();
                items.add(at(node(new Expr.Spread(e), e), at));
            } else {
                items.add(expr());
            }
            if (peek().type() != Type.COMMA) {
                break;
            }
            next();
        }
        expect(Type.RBRACKET, "']' to close the list");
        if (items.size() > MAX_LIST_ITEMS) {
            throw new ElException("a list written in an expression holds at most " + MAX_LIST_ITEMS + " values", s);
        }
        return at(node(new Expr.ListLit(items), items.toArray(Expr[]::new)), s);
    }

    private Expr identifier(Token t) {
        int s = t.pos();
        switch (t.text()) {
            case "true":
                return at(new Expr.Lit(Boolean.TRUE), s);
            case "false":
                return at(new Expr.Lit(Boolean.FALSE), s);
            case "null":
                return at(new Expr.Lit(null), s);
            default:
                break;
        }
        if ("ctl".equals(t.text()) && peek().type() == Type.DOT && tokens.get(p + 1).type() == Type.IDENT
                && tokens.get(p + 2).type() != Type.LPAREN) {
            next();                                              // ctl.<id>: the value of a control, never a field of the data
            return at(new Expr.Ctl(next().text()), s);
        }
        if ("state".equals(t.text()) && !decls.states().isEmpty() && peek().type() == Type.DOT && !letNames.contains(t.text())) {
            return at(state(t), s);                              // level 1.2, S1: state.<id> when the Sutra holds state, never a field
        }
        if (v11 && "lambda".equals(t.text())) {
            throw new ElException("a lambda is passed to map, sort, reduce or group, or bound by let(...); it is not a value", t.pos());
        }
        if (peek().type() != Type.LPAREN && letFns.containsKey(t.text()) && letNames.contains(t.text())) {
            throw new ElException("'" + t.text() + "' is a function: call it, " + t.text() + "(...)", t.pos());
        }
        if (!decls.isEmpty() && decls.libraries().contains(t.text()) && peek().type() == Type.DOT && tokens.get(p + 1).type() == Type.IDENT
                && tokens.get(p + 2).type() == Type.LPAREN && !letNames.contains(t.text())) {
            next();
            Token f = next();
            next();
            String full = t.text() + "." + f.text();
            if (!decls.functions().containsKey(full)) {
                throw new ElException("the library " + t.text() + " has no function '" + f.text() + "'; it has: "
                        + String.join(", ", decls.functions().keySet().stream().filter(n -> n.startsWith(t.text() + ".")).sorted().toList()), f.pos());
            }
            return userCall(full, t, null);
        }
        if (peek().type() != Type.LPAREN) {
            if (letNames.contains(t.text())) {
                return at(new Expr.Local(t.text()), s);            // a name let(...) bound: it hides a field of the same name
            }
            if (!decls.isEmpty() && decls.lets().containsKey(t.text())) {
                return at(sutraLet(t), s);
            }
            Expr row = at(new Expr.RowOrRoot(), s);
            return at(new Expr.Field(row, t.text(), null), s);
        }
        next();
        return call(t, null);
    }

    /**
     * {@code state.<id>}, {@code state} read and the dot next (SUTRA_EXPANSION S1): the longest declared id written there, its parts
     * joined by {@code -} or {@code .} with no space ({@code state.md.query}, a fragment use's output); one that is not declared does not
     * compile.
     */
    private Expr state(Token t) {
        Token dot = next();
        int end = dot.pos() + dot.length();
        StringBuilder sb = new StringBuilder();
        String best = null;
        int bestAt = p;
        int i = p;
        while (i < tokens.size()) {
            Token k = tokens.get(i);
            boolean part = k.type() == Type.IDENT || sb.length() > 0 && k.type() == Type.NUMBER && !k.text().contains(".");
            if (k.pos() != end || !part) {
                break;
            }
            sb.append(k.text());
            end = k.pos() + k.length();
            i++;
            if (decls.states().containsKey(sb.toString())) {
                best = sb.toString();
                bestAt = i;
            }
            Token j = i < tokens.size() ? tokens.get(i) : null;
            if (j == null || j.pos() != end || !(j.type() == Type.DOT || j.is(Type.OP, "-"))) {
                break;
            }
            sb.append(j.text());
            end = j.pos() + j.length();
            i++;
        }
        if (best == null) {
            Token at = peek();
            throw new ElException("unknown state '" + (sb.length() == 0 ? at.text() : sb.toString().replaceAll("[-.]$", "")) + "': this Sutra "
                    + "holds " + String.join(", ", decls.states().keySet()) + " (a field named state is read as $.state or @.state)", at.pos());
        }
        p = bestAt;
        return new Expr.State(best);
    }

    /**
     * The call {@code t(...)}, the opening parenthesis read; {@code piped} is the left side of {@code |>}, its first argument (null when
     * not piped).
     */
    private Expr call(Token t, Expr piped) {
        int s = t.pos();
        if (piped != null) {
            next();                                              // the '(' after the name
        }
        String name = t.text();
        if (letFns.containsKey(name) && letNames.contains(name)) {
            return localCall(t, piped);
        }
        if (!decls.isEmpty() && decls.functions().containsKey(name)) {
            return userCall(name, t, piped);
        }
        int[] arity = macros.contains(name) ? new int[] {0, 64} : v11 && Level11.has(name) ? Level11.arity(name) : table.arityOf(name);
        if (arity == null && v11 && ReservedNames.contains(name)) {
            throw new ElException("'" + name + "' is reserved for a later version of Rachana", t.pos(), t.pos() + name.length());
        }
        if (arity == null) {
            throw new ElException("unknown function '" + name + "'; known: " + String.join(", ", new java.util.TreeSet<>(table.arity().keySet())),
                    t.pos());
        }
        if ("let".equals(name)) {
            if (piped != null) {
                throw new ElException("let cannot be piped into", t.pos());
            }
            return at(let(t, arity), s);
        }
        List<Expr> args = new ArrayList<>();
        if (piped != null) {
            args.add(piped);
        }
        Map<String, Expr> named = new LinkedHashMap<>();
        List<Token> names = new ArrayList<>();
        if (peek().type() != Type.RPAREN) {
            do {
                if (peek().type() == Type.RPAREN) {
                    break;                                        // a trailing comma
                }
                if (peek().type() == Type.IDENT && tokens.get(p + 1).is(Type.OP, "=")) {
                    Token n = next();
                    next();
                    if (named.containsKey(n.text())) {
                        throw new ElException(name + ": " + n.text() + " is named twice", n.pos());
                    }
                    named.put(n.text(), expr());
                    names.add(n);
                    continue;
                }
                if (!named.isEmpty()) {
                    throw new ElException(name + ": a value without a name follows a named one; write the named arguments last", peek().pos());
                }
                int fnParams = v11 ? lambdaParams(name, args.size()) : -1;
                if (v11 && peek().is(Type.IDENT, "lambda")) {
                    if (fnParams < 0) {
                        throw new ElException("a lambda is passed to map, sort, reduce or group, or bound by let(...); " + name
                                + " takes none here", peek().pos());
                    }
                    args.add(lambda(fnParams, name));
                    continue;
                }
                if (fnParams > 0 && peek().type() == Type.IDENT && letFns.containsKey(peek().text()) && letNames.contains(peek().text())
                        && (tokens.get(p + 1).type() == Type.COMMA || tokens.get(p + 1).type() == Type.RPAREN)) {
                    Token f = next();
                    if (letFns.get(f.text()) != fnParams) {
                        throw new ElException(name + ": '" + f.text() + "' takes " + letFns.get(f.text()) + " value(s); here a function of "
                                + fnParams + " is needed", f.pos());
                    }
                    args.add(at(new Expr.FnRef(f.text()), f.pos()));
                    continue;
                }
                boolean accumulator = "reduce".equals(name) && args.size() == 2;
                accDepth += accumulator ? 1 : 0;
                try {
                    args.add(expr());
                } finally {
                    accDepth -= accumulator ? 1 : 0;
                }
            } while (peek().type() == Type.COMMA && next() != null);
        }
        expect(Type.RPAREN, "')'");
        if (macros.contains(name)) {
            return at(new Expr.Call(name, null, args, named), s);
        }
        if (!named.isEmpty()) {
            if (v11 && CoreParams.of(name) != null) {
                bindCore(name, args, named, names);
            } else {
                bind(name, args, named, names);
            }
        }
        if (args.size() < arity[0] || args.size() > arity[1]) {
            throw new ElException(name + " takes " + arity[0] + (arity[0] == arity[1] ? "" : "-" + arity[1])
                    + " argument(s), got " + args.size(), t.pos());
        }
        String level11 = v11 ? Level11.check(name, args) : null;
        if (level11 != null) {
            throw new ElException(name + ": " + level11, t.pos(), t.pos() + name.length());
        }
        String problem = table.check(name, args);
        if (problem != null) {
            throw new ElException(name + ": " + problem, t.pos());
        }
        List<Expr> all = new ArrayList<>(args);
        all.addAll(named.values());
        Expr[] parts = all.toArray(Expr[]::new);
        switch (name) {
            case "if":
                return at(node(new Expr.Ternary(args.get(0), args.get(1), args.size() > 2 ? args.get(2) : new Expr.Lit(null)), parts), s);
            case "ifs":
                return ifs(args, s);
            case "reduce":
                if (addsUp(args.get(2))) {
                    throw new ElException("reduce does not add up a list: use sum(...) for a total (aggregates run in the pivot engine), "
                            + "concat(...) to join text", t.pos());
                }
                break;
            default:
                break;
        }
        ElForm form = v11 && Level11.form(name) != null && table.form(name) != null ? Level11.form(name) : table.form(name);
        if (form != null) {
            if (!named.isEmpty()) {
                throw new ElException(name + " takes no named arguments", t.pos());
            }
            return at(node(new Expr.Form(name, form, List.copyOf(args)), parts), s);
        }
        ElFunction fn = v11 && Level11.has(name) ? Level11.function(name) : table.get(name);
        return at(node(new Expr.Call(name, fn, List.copyOf(args), named), parts), s);
    }

    /**
     * {@code lambda a, b: body} (X13 in Python's spelling, PY3), the word {@code lambda} next: {@code params} names, for the form
     * {@code fn}; inside the body the names are read as {@code let}'s are.
     */
    private Expr lambda(int params, String fn) {
        Token word = next();
        List<String> names = new ArrayList<>();
        while (peek().type() == Type.IDENT) {
            Token n = next();
            if (RESERVED.contains(n.text()) || "lambda".equals(n.text())) {
                throw new ElException("lambda: '" + n.text() + "' cannot be a name", n.pos());
            }
            if (names.contains(n.text()) || letFns.containsKey(n.text()) && letNames.contains(n.text())) {
                throw new ElException("lambda: '" + n.text() + "' is " + (names.contains(n.text()) ? "named twice" : "already a function here"), n.pos());
            }
            names.add(n.text());
            if (!accept(Type.COMMA, ",")) {
                break;
            }
        }
        expect(Type.COLON, "':' after the lambda's names");
        if (params >= 0 && names.size() != params) {
            throw new ElException((fn == null ? "this" : fn) + " needs a lambda of " + params + " name" + (params == 1 ? "" : "s") + " (lambda "
                    + (params == 1 ? "x" : "acc, x") + ": ...); this one has " + names.size(), word.pos());
        }
        names.forEach(letNames::push);
        try {
            Expr body = expr();
            return at(node(new Expr.Lambda(names, body), body), word.pos());
        } finally {
            for (int i = 0; i < names.size(); i++) {
                letNames.pop();
            }
        }
    }

    /** {@code f(a, ...)} where {@code let} bound {@code f} to a lambda (the {@code (} read). */
    private Expr localCall(Token t, Expr piped) {
        List<Expr> args = new ArrayList<>();
        if (piped != null) {
            args.add(piped);
        }
        while (peek().type() != Type.RPAREN) {
            args.add(expr());
            if (!accept(Type.COMMA, ",")) {
                break;
            }
        }
        expect(Type.RPAREN, "')'");
        int want = letFns.get(t.text());
        if (args.size() != want) {
            throw new ElException(t.text() + " takes " + want + " value(s), got " + args.size(), t.pos());
        }
        return at(node(new Expr.LocalCall(t.text(), args), args.toArray(Expr[]::new)), t.pos());
    }

    /** True when the bracket ahead holds a slice: a ':' at its own depth before its closing ']'. */
    private boolean sliceAhead() {
        int depth = 0;
        for (int k = p + 1; k < tokens.size(); k++) {
            Type ty = tokens.get(k).type();
            if (ty == Type.LBRACKET || ty == Type.FILTER || ty == Type.LPAREN || ty == Type.LBRACE) {
                depth++;
            } else if (ty == Type.RPAREN || ty == Type.RBRACE) {
                depth--;
            } else if (ty == Type.RBRACKET) {
                if (depth == 0) {
                    return false;
                }
                depth--;
            } else if (ty == Type.QUESTION && depth == 0) {
                return false;                                    // a conditional's ':' is not a slice's
            } else if (ty == Type.COLON && depth == 0) {
                return true;
            } else if (ty == Type.EOF) {
                return false;
            }
        }
        return false;
    }

    /** <code>{ key: value, ...r }</code> (the brace read): a record literal (X6). */
    private Expr record(int s) {
        List<Expr> entries = new ArrayList<>();
        java.util.Set<String> keys = new java.util.HashSet<>();
        while (peek().type() != Type.RBRACE) {
            if (peek().type() == Type.SPREAD) {
                int at = next().pos();
                Expr e = expr();
                entries.add(at(node(new Expr.Spread(e), e), at));
            } else {
                Token k = next();
                if (k.type() != Type.IDENT && k.type() != Type.STRING) {
                    throw new ElException(k.type() == Type.EOF ? "the record is not closed: '}' is missing"
                            : "a record's key is a name or a quoted text, not '" + k.text() + "'", k.pos());
                }
                if (!keys.add(k.text())) {
                    throw new ElException("the record names '" + k.text() + "' twice", k.pos());
                }
                expect(Type.COLON, "':' after the key '" + k.text() + "'");
                Expr v = expr();
                entries.add(at(node(new Expr.Pair(k.text(), v), v), k.pos()));
            }
            if (entries.size() > MAX_RECORD_FIELDS) {
                throw new ElException("a record written in an expression holds at most " + MAX_RECORD_FIELDS + " fields", s);
            }
            if (!accept(Type.COMMA, ",")) {
                break;
            }
        }
        expect(Type.RBRACE, "'}' to close the record");
        return at(node(new Expr.RecordLit(entries), entries.toArray(Expr[]::new)), s);
    }

    /**
     * Binds the named arguments by the function's signature: a positional parameter's value moves to its place ({@link Expr.Absent}
     * filling the optional places before it); a keyword-only one stays in {@code named}.
     */
    private static void bind(String fn, List<Expr> args, Map<String, Expr> named, List<Token> names) {
        Signatures.Signature sig = Signatures.get().function(Functions.oneName(fn)).orElse(null);
        if (sig == null) {
            throw new ElException(fn + " takes no named arguments", names.get(0).pos());
        }
        // Rachana's own functions (runs R) take every argument by place, in the signature's order; the engine's keep their keyword-only ones by name
        boolean rachana = "R".equals(sig.runs());
        List<Signatures.Param> positional = rachana ? sig.params() : sig.params().stream().filter(x -> !x.keywordOnly()).toList();
        int k = 0;
        for (Map.Entry<String, Expr> e : List.copyOf(named.entrySet())) {
            Token at = names.get(k++);
            Signatures.Param param = sig.param(e.getKey()).orElse(null);
            if (param == null) {
                List<String> known = sig.params().stream().map(Signatures.Param::name).toList();
                throw new ElException(fn + " has no argument named " + e.getKey() + (known.isEmpty() ? "" : "; its arguments: " + known), at.pos());
            }
            if (param.keywordOnly() && !rachana) {
                continue;
            }
            int place = positional.indexOf(param);
            if (place < args.size() && !(args.get(place) instanceof Expr.Absent)) {
                throw new ElException(fn + ": " + e.getKey() + " is given twice (by place and by name)", at.pos());
            }
            while (args.size() <= place) {
                args.add(new Expr.Absent());
            }
            args.set(place, e.getValue());
            named.remove(e.getKey());
        }
    }

    /**
     * Level 1.1: binds the named arguments of a Core function by the catalogue's parameter names ({@link CoreParams}): each moves to its
     * place ({@link Expr.Absent} filling the optional places before it); a repeatable parameter is given by place only.
     */
    private static void bindCore(String fn, List<Expr> args, Map<String, Expr> named, List<Token> names) {
        List<String> plain = CoreParams.names(fn);
        int k = 0;
        for (Map.Entry<String, Expr> e : List.copyOf(named.entrySet())) {
            Token at = names.get(k++);
            int place = plain.indexOf(e.getKey());
            if (place < 0) {
                throw new ElException(fn + " has no argument named " + e.getKey() + (plain.isEmpty() ? "" : "; its arguments: " + plain), at.pos());
            }
            if (CoreParams.of(fn).get(place).repeatable()) {
                throw new ElException(fn + ": " + e.getKey() + " is repeatable; give its values by place", at.pos());
            }
            if (place < args.size() && !(args.get(place) instanceof Expr.Absent)) {
                throw new ElException(fn + ": " + e.getKey() + " is given twice (by place and by name)", at.pos());
            }
            while (args.size() <= place) {
                args.add(new Expr.Absent());
            }
            args.set(place, e.getValue());
            named.remove(e.getKey());
        }
    }

    /** A Sutra {@code let:} name (S2): its value, read once per bind; only the lets written before a let value are visible to it. */
    private Expr sutraLet(Token t) {
        String name = t.text();
        if (inFunction != null) {
            throw new ElException(inFunction + ": reads the let '" + name + "'; a function reads only its parameters and other functions", t.pos());
        }
        if (visibleLets != null && !visibleLets.contains(name)) {
            throw new ElException("reads the let '" + name + "', which is not written before it: a let reads only the ones before it (no cycles)",
                    t.pos());
        }
        Parser inner = nested(decls.lets().get(name));
        inner.visibleLets = decls.before(name);
        Expr value;
        try {
            value = inner.whole();
        } catch (ElException e) {
            throw new ElException("the let '" + name + "' ('" + decls.lets().get(name) + "'): " + reason(e), t.pos());
        }
        String wrong = DeclChecks.let(value);
        if (wrong != null) {
            throw new ElException("the let '" + name + "' " + wrong, t.pos());
        }
        return new Expr.SutraLet(name, value);
    }

    /**
     * A call of a declared function (X41) or a library's ({@code lib.f(...)}, X42), its opening parenthesis read; {@code piped} is the
     * left side of {@code |>}: the arguments by place or by the parameters' names, exactly as many as it has; the body read in its own
     * scope with the parameters as names, inlined at the call.
     */
    private Expr userCall(String name, Token t, Expr piped) {
        Declarations.Function f = decls.functions().get(name);
        List<String> params = f.paramNames();
        List<Expr> args = new ArrayList<>();
        if (piped != null) {
            args.add(piped);
        }
        Map<String, Expr> named = new LinkedHashMap<>();
        if (peek().type() != Type.RPAREN) {
            do {
                if (peek().type() == Type.RPAREN) {
                    break;
                }
                if (peek().type() == Type.IDENT && tokens.get(p + 1).is(Type.OP, "=")) {
                    Token n = next();
                    next();
                    if (!params.contains(n.text())) {
                        throw new ElException(name + " has no argument named " + n.text() + "; its arguments: " + params, n.pos());
                    }
                    if (named.put(n.text(), expr()) != null) {
                        throw new ElException(name + ": " + n.text() + " is named twice", n.pos());
                    }
                    continue;
                }
                if (!named.isEmpty()) {
                    throw new ElException(name + ": a value without a name follows a named one; write the named arguments last", peek().pos());
                }
                args.add(expr());
            } while (peek().type() == Type.COMMA && next() != null);
        }
        expect(Type.RPAREN, "')'");
        for (Map.Entry<String, Expr> e : named.entrySet()) {
            int place = params.indexOf(e.getKey());
            if (place < args.size() && args.get(place) != null) {
                throw new ElException(name + ": " + e.getKey() + " is given twice (by place and by name)", t.pos());
            }
            while (args.size() < place) {
                args.add(null);
            }
            if (args.size() == place) {
                args.add(e.getValue());
            } else {
                args.set(place, e.getValue());
            }
        }
        if (args.size() != params.size() || args.contains(null)) {
            throw new ElException(name + " takes " + params.size() + " argument(s) " + params + ", got " + args.stream().filter(a -> a != null).count(),
                    t.pos());
        }
        if (inFunction != null && inFunction.indexOf('.') > 0 && name.indexOf('.') < 0) {
            throw new ElException(inFunction + " calls " + name + ", a Sutra's function: a library function calls only library functions, by"
                    + " their library's name", t.pos());
        }
        if (inlining.contains(name)) {
            throw new ElException(name + " calls itself (through " + String.join(" -> ", inlining) + "): functions have no recursion", t.pos());
        }
        Parser inner = nested(f.body());
        inner.inFunction = name;
        inner.visibleLets = List.of();
        params.forEach(inner.letNames::push);
        inner.inlining.addLast(name);
        Expr body;
        try {
            body = inner.whole();
        } catch (ElException e) {
            throw new ElException("in " + name + " ('" + f.body() + "'): " + reason(e), t.pos());
        } finally {
            inner.inlining.removeLast();
        }
        String wrong = DeclChecks.function(body);
        if (wrong != null) {
            throw new ElException(name + " " + wrong, t.pos());
        }
        return at(node(new Expr.UserCall(name, params, args, body), args.toArray(Expr[]::new)), t.pos());
    }

    /** A nested problem's words without its code and offset (they belong to the declared text, not to the expression being read). */
    private static String reason(ElException e) {
        String m = e.getMessage();
        String code = e.code() == null ? "" : e.code().code() + " ";
        if (m.startsWith(code)) {
            m = m.substring(code.length());
        }
        return m.replaceFirst(" at [0-9]+$", "");
    }

    /** A parser of a declared text (a body or a let value) with this one's table, level and declarations. */
    private Parser nested(String src) {
        Parser inner = new Parser(src, Lexer.tokens(src, v11), limits, v11);
        inner.table = table;
        inner.decls = decls;
        inner.inlining = inlining;
        return inner;
    }

    /** The whole text as one expression. */
    private Expr whole() {
        Expr e = expr();
        if (peek().type() != Type.EOF) {
            throw new ElException("unexpected '" + peek().text() + "'", peek().pos());
        }
        return e;
    }

    /** {@code ifs(c1, a1, c2, a2, ..., [else])}: the conditional {@code c1 ? a1 : c2 ? a2 : ... : else} (else empty). */
    private Expr ifs(List<Expr> args, int s) {
        int pairs = args.size() / 2;
        Expr out = args.size() % 2 == 1 ? args.get(args.size() - 1) : new Expr.Lit(null);
        for (int i = pairs - 1; i >= 0; i--) {
            Expr c = args.get(2 * i);
            Expr a = args.get(2 * i + 1);
            out = at(node(new Expr.Ternary(c, a, out), c, a, out), s);
        }
        return out;
    }

    /** True when {@code e} adds or subtracts {@code @acc} (directly, or in a branch of a conditional): a sum written by hand. */
    private static boolean addsUp(Expr e) {
        if (e instanceof Expr.Binary b && ("+".equals(b.op()) || "-".equals(b.op()))) {
            return isAcc(b.l()) || isAcc(b.r()) || addsUp(b.l()) || addsUp(b.r());
        }
        if (e instanceof Expr.Op b && ("+".equals(b.op()) || "-".equals(b.op()))) {
            return isAcc(b.l()) || isAcc(b.r()) || addsUp(b.l()) || addsUp(b.r());
        }
        if (e instanceof Expr.Ternary t) {
            return addsUp(t.then()) || addsUp(t.otherwise());
        }
        return false;
    }

    private static boolean isAcc(Expr e) {
        return e instanceof Expr.Local l && ListFunctions.ACC.equals(l.name());
    }

    private static final java.util.Set<String> RESERVED = java.util.Set.of("true", "false", "null", "ctl", "in", "not");

    /**
     * {@code let(name = expr, ..., result)}: each {@code name = expr} binds a name the later values and the result read as a
     * bare word (hiding a field of that name); the last argument is the result.
     */
    private Expr let(Token t, int[] arity) {
        List<String> names = new ArrayList<>();
        List<Expr> values = new ArrayList<>();
        List<Integer> shadowed = new ArrayList<>();
        List<String> fnNames = new ArrayList<>();
        int bound = 0;
        try {
            while (peek().type() == Type.IDENT && tokens.get(p + 1).is(Type.OP, "=")) {
                Token n = next();
                next();
                if (RESERVED.contains(n.text())) {
                    throw new ElException("let: '" + n.text() + "' cannot be a name", n.pos());
                }
                if (names.contains(n.text())) {
                    throw new ElException("let: '" + n.text() + "' is named twice", n.pos());
                }
                if (names.size() + 1 >= arity[1]) {
                    throw new ElException("let takes at most " + (arity[1] - 1) + " names", n.pos());
                }
                if (v11 && peek().is(Type.IDENT, "lambda")) {
                    Expr.Lambda f = (Expr.Lambda) lambda(-1, null);
                    values.add(f);
                    shadowed.add(letFns.put(n.text(), f.params().size()));
                } else {
                    values.add(expr());
                    shadowed.add(letFns.remove(n.text()));
                }
                fnNames.add(n.text());
                names.add(n.text());
                letNames.push(n.text());
                bound++;
                expect(Type.COMMA, "',' and then the result");
            }
            if (names.isEmpty()) {
                throw new ElException("let needs a name = value before its result, as in let(q = $.QUANTITY, q * 2)", t.pos());
            }
            Expr body = expr();
            if (peek().type() == Type.COMMA) {
                throw new ElException("let: only the last argument is the result; each before it is written name = value", peek().pos());
            }
            expect(Type.RPAREN, "')'");
            List<Expr> parts = new ArrayList<>(values);
            parts.add(body);
            return node(new Expr.Let(List.copyOf(names), List.copyOf(values), body), parts.toArray(Expr[]::new));
        } finally {
            for (int i = 0; i < bound; i++) {
                letNames.pop();
            }
            for (int i = fnNames.size() - 1; i >= 0; i--) {
                if (shadowed.get(i) == null) {
                    letFns.remove(fnNames.get(i));
                } else {
                    letFns.put(fnNames.get(i), shadowed.get(i));
                }
            }
        }
    }
}
