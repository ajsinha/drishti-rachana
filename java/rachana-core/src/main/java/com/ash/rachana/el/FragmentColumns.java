/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import com.ash.rachana.el.Token.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The column names a typed fragment writes in its expressions, renamed to the caller's at compile time (decision L-21, "fragments are
 * functions", rule 3). A fragment names a column of one of its table parameters in three ways, and all three reach the caller's mapping:
 * {@code @.<column>} where {@code @} is a row of the table (a panel that reads it, a filter of it, the per-element argument of
 * {@code map}, {@code sort}, {@code reduce}, {@code group} over it), the field of a lambda's element over it ({@code lambda r: r.amount}),
 * and the text argument of a Core function's <b>column parameter</b> over it ({@code sum(arg.rows, 'amount')}; the parameters marked
 * {@code role: column} in {@code spec/1/functions.yaml}, listed by {@link #COLUMN_PARAMS}). Anything else that would name a local
 * column is refused ({@code RCH-2121}): a column parameter over the table given a computed text, a text naming a local column where a
 * column is expected over a list that is not a table parameter, a column the table does not declare, and a bare name that is a local
 * column. The text keeps its spelling: only the column names change.
 */
public final class FragmentColumns {

    private FragmentColumns() {}

    /** The problem: a column named where the caller's mapping cannot reach it. */
    public static final String CODE = "RCH-2121";

    /**
     * A Core function's parameter whose argument names a column (or a dotted path from one) of the rows of the function's list:
     * {@code role: column} in {@code spec/1/functions.yaml}.
     *
     * @param function the function
     * @param place its 0-based place in the call
     * @param name its name (for a named argument)
     * @param textOnly whether it takes only text (a {@code text} parameter); an {@code any} parameter takes a text or an expression
     */
    public record ColumnParam(String function, int place, String name, boolean textOnly) { }

    /** Every Core parameter of role {@code column}, in catalogue order: the {@code role: column} marks of the catalogue itself ({@link CoreParams}). */
    public static final List<ColumnParam> COLUMN_PARAMS = columnParams();

    private static List<ColumnParam> columnParams() {
        List<ColumnParam> out = new java.util.ArrayList<>();
        CoreParams.all().forEach((f, params) -> {
            for (int i = 0; i < params.size(); i++) {
                if (params.get(i).column()) {
                    out.add(new ColumnParam(f, i, params.get(i).name(), params.get(i).text()));
                }
            }
        });
        return List.copyOf(out);
    }

    /** The functions whose result is rows of their first argument (a column of it is a column of them); {@code union} when both are one table's. */
    private static final Set<String> SAME_ROWS = Set.of("sort", "take", "drop", "unique", "difference", "intersect");

    /** Marks a filter's rows that come from a table the pass cannot follow (the tables it reads between two marks, then the text). */
    private static final String UNKNOWN_ROWS = "?";            // never a table name; not a control character (TeaVM reads \u0001 in a concatenation as an argument)

    private static final Set<String> WORDS = Set.of("true", "false", "null", "and", "or", "not", "in", "if", "else", "lambda", "is");

    /** What the pass knows about the fragment's tables. */
    public interface Tables {

        /** The table parameter that {@code arg.<text>} starts with (the longest declared name), or null. */
        String table(String afterArg);

        /** The local columns of table parameter {@code table}, or null when it is not one. */
        Set<String> columns(String table);

        /** The caller's name for local column {@code column} of {@code table} (the same name when not mapped). */
        String rename(String table, String column);

        /** Notes that the fragment reads {@code column} of {@code table} (for the advisory of an unused mapping). */
        void read(String table, String column);

        /** The fragment's table parameters, in declared order. */
        List<String> tableNames();
    }

    /**
     * {@code text} with its column names renamed, at language level 1.1 or later, or null when it is not an expression (it does not
     * split into tokens; the caller treats it as text).
     *
     * @param text an expression of the fragment (its {@code arg.} references not yet replaced)
     * @param row the table whose row {@code @} is at the top (a panel that reads it), or null
     * @param bare whether a bare name that is a local column is refused (the text is an expression, not a label)
     * @param tables the fragment's tables
     * @param problems where the problems' messages go (each under {@link #CODE})
     */
    public static String rename(String text, String row, boolean bare, Tables tables, List<String> problems) {
        List<Token> toks;
        try {
            toks = Lexer.tokens(text, true);
        } catch (ElException e) {
            return null;
        }
        Run r = new Run(text, toks, tables, bare, problems);
        r.span(0, toks.size() - 1, row, Map.of());
        return r.result();
    }

    /** The parts {@code ${...}} of a template rendered through {@link #rename}; the text between them kept. */
    public static String template(String text, String row, Tables tables, List<String> problems) {
        return parts(text, part -> {
            String inner = rename(part, row, true, tables, problems);
            return inner == null ? part : inner;
        });
    }

    /** A template with each part {@code ${...}} (its braces balanced, its texts skipped) replaced by {@code f} of it; the rest kept. */
    public static String parts(String text, java.util.function.UnaryOperator<String> f) {
        StringBuilder out = new StringBuilder();
        int copied = 0;
        int i = text.indexOf("${");
        while (i >= 0) {
            int end = close(text, i + 2);
            if (end < 0) {
                break;
            }
            out.append(text, copied, i + 2).append(f.apply(text.substring(i + 2, end)));
            copied = end;
            i = text.indexOf("${", end);
        }
        return out.append(text, copied, text.length()).toString();
    }

    /** The index of the brace closing a template part opened before {@code from}, or -1. */
    private static int close(String s, int from) {
        int depth = 0;
        char quote = 0;
        for (int i = from; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    quote = 0;
                }
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                if (depth == 0) {
                    return i;
                }
                depth--;
            }
        }
        return -1;
    }

    private static final class Run {
        private final String src;
        private final List<Token> toks;
        private final Tables tables;
        private final boolean bare;
        private final List<String> problems;
        private final int[] match;
        private final List<int[]> at = new ArrayList<>();
        private final List<String> with = new ArrayList<>();
        private final Set<String> named = new HashSet<>();

        Run(String src, List<Token> toks, Tables tables, boolean bare, List<String> problems) {
            this.src = src;
            this.toks = toks;
            this.tables = tables;
            this.bare = bare;
            this.problems = problems;
            this.match = new int[toks.size()];
            java.util.Arrays.fill(match, -1);
            List<Integer> open = new ArrayList<>();
            for (int i = 0; i < toks.size(); i++) {
                Type t = toks.get(i).type();
                if (t == Type.LPAREN || t == Type.LBRACKET || t == Type.FILTER || t == Type.LBRACE) {
                    open.add(i);
                } else if ((t == Type.RPAREN || t == Type.RBRACKET || t == Type.RBRACE) && !open.isEmpty()) {
                    int o = open.remove(open.size() - 1);
                    match[o] = i;
                    match[i] = o;
                }
            }
            for (int i = 0; i + 1 < toks.size(); i++) {           // let(x = ...): x is a name of the expression, not a column
                if (toks.get(i).type() == Type.IDENT && toks.get(i + 1).is(Type.OP, "=")) {
                    named.add(toks.get(i).text());
                }
            }
        }

        String result() {
            if (at.isEmpty()) {
                return src;
            }
            StringBuilder sb = new StringBuilder(src);
            for (int k = at.size() - 1; k >= 0; k--) {
                sb.replace(at.get(k)[0], at.get(k)[1], with.get(k));
            }
            return sb.toString();
        }

        private Token tok(int i) {
            return toks.get(i);
        }

        private void edit(int start, int end, String text) {
            int k = 0;
            while (k < at.size() && at.get(k)[0] < start) {
                k++;
            }
            at.add(k, new int[] {start, end});
            with.add(k, text);
        }

        /** The table parameter {@code arg.<name>} at {@code i} names, with the index after it; null when none. */
        private Object[] arg(int i, int end) {
            if (i + 2 >= end || !tok(i).is(Type.IDENT, "arg") || tok(i + 1).type() != Type.DOT || tok(i + 2).type() != Type.IDENT
                    || i > 0 && tok(i - 1).type() == Type.DOT) {
                return null;
            }
            int from = tok(i + 2).pos();
            int run = from;
            while (run < src.length() && (Character.isLetterOrDigit(src.charAt(run)) || src.charAt(run) == '-' || src.charAt(run) == '_')) {
                run++;
            }
            String t = tables.table(src.substring(from, run));
            int stop = from + (t == null ? tok(i + 2).length() : t.length());
            int j = i + 2;
            while (j < end && tok(j).pos() < stop) {
                j++;
            }
            return new Object[] {t, j};
        }

        /** The table whose rows {@code [s, e)} gives: {@code arg.<table>}, {@code @} over a table, a same-rows call, each with filters. */
        private String rows(int s, int e, String row) {
            int pipe = -1;
            for (int i = s; i < e; i++) {
                if (tok(i).is(Type.OP, "|>")) {
                    pipe = i;
                }
                if (match[i] > i) {
                    i = match[i];
                }
            }
            if (pipe >= 0) {                                       // the last stage of a pipe: a same-rows call over the stage before it
                int c = pipe + 1;
                return c + 1 < e && tok(c).type() == Type.IDENT && SAME_ROWS.contains(tok(c).text()) && tok(c + 1).type() == Type.LPAREN
                        && postfix(match[c + 1] + 1, e) ? rows(s, pipe, row) : null;
            }
            if (s >= e) {
                return null;
            }
            Object[] a = arg(s, e);
            if (a != null) {
                return a[0] != null && postfix((int) a[1], e) ? (String) a[0] : null;
            }
            if (tok(s).type() == Type.AT && postfix(s + 1, e)) {
                return row;
            }
            if (tok(s).type() == Type.LPAREN && match[s] > s && postfix(match[s] + 1, e)) {
                return rows(s + 1, match[s], row);
            }
            if (tok(s).type() == Type.IDENT && SAME_ROWS.contains(tok(s).text()) && s + 1 < e && tok(s + 1).type() == Type.LPAREN
                    && match[s + 1] > s && postfix(match[s + 1] + 1, e)) {
                List<int[]> args = args(s + 1);
                return args.isEmpty() ? null : rows(args.get(0)[0], args.get(0)[1], row);
            }
            if (tok(s).is(Type.IDENT, "union") && s + 1 < e && tok(s + 1).type() == Type.LPAREN && match[s + 1] > s
                    && postfix(match[s + 1] + 1, e)) {
                List<int[]> args = args(s + 1);
                String first = args.size() == 2 ? rows(args.get(0)[0], args.get(0)[1], row) : null;
                return first != null && first.equals(rows(args.get(1)[0], args.get(1)[1], row)) ? first : null;
            }
            return null;
        }

        /** The table parameters {@code [s, e)} reads ({@code arg.<table>} anywhere in it), in order. */
        private List<String> mentioned(int s, int e) {
            List<String> out = new ArrayList<>();
            for (int i = s; i < e; i++) {
                Object[] a = arg(i, e);
                if (a != null && a[0] != null && !out.contains((String) a[0])) {
                    out.add((String) a[0]);
                }
            }
            return out;
        }

        /** The table whose row (or each row, {@code [*]}) {@code [s, e)} gives: an index of its rows, or {@code first}/{@code last} of them. */
        private String rowOf(int s, int e, String row) {
            if (e - 1 > s && tok(e - 1).type() == Type.RBRACKET && match[e - 1] > s && tok(match[e - 1]).type() == Type.LBRACKET) {
                return rows(s, match[e - 1], row);
            }
            if (tok(s).type() == Type.IDENT && (tok(s).is(Type.IDENT, "first") || tok(s).is(Type.IDENT, "last")) && s + 1 < e
                    && tok(s + 1).type() == Type.LPAREN && match[s + 1] == e - 1) {
                List<int[]> args = args(s + 1);
                return args.size() == 1 ? rows(args.get(0)[0], args.get(0)[1], row) : null;
            }
            return null;
        }

        /** Whether {@code [s, e)} is only filters and indexes. */
        private boolean postfix(int s, int e) {
            int i = s;
            while (i < e) {
                Type t = tok(i).type();
                if ((t != Type.FILTER && t != Type.LBRACKET) || match[i] < 0 || match[i] >= e) {
                    return false;
                }
                i = match[i] + 1;
            }
            return true;
        }

        /** The arguments of the call whose {@code (} is at {@code lp}: each {@code [start, end)}, a named argument with its name first. */
        private List<int[]> args(int lp) {
            List<int[]> out = new ArrayList<>();
            int rp = match[lp];
            if (rp == lp + 1) {
                return out;
            }
            int start = lp + 1;
            boolean head = false;                                  // in the names of a lambda: lambda acc, x: ... is one argument
            for (int i = lp + 1; i <= rp; i++) {
                if (i == start && tok(i).is(Type.IDENT, "lambda")) {
                    head = true;
                } else if (head && tok(i).type() == Type.COLON) {
                    head = false;
                }
                if (i == rp || tok(i).type() == Type.COMMA && !head) {
                    out.add(new int[] {start, i});
                    start = i + 1;
                } else if (match[i] > i) {
                    i = match[i];
                }
            }
            return out;
        }

        /** The tokens of {@code [s, e)}: the column names in it renamed, with {@code @} a row of {@code row} and the lambdas' elements. */
        void span(int s, int e, String row, Map<String, String> elements) {
            int stage = s;
            for (int i = s; i < e; i++) {
                Token t = tok(i);
                if (t.is(Type.OP, "|>")) {
                    stage = i + 1;
                    continue;
                }
                Object[] a = arg(i, e);
                if (a != null) {
                    i = (int) a[1] - 1;
                    continue;
                }
                if (t.type() == Type.FILTER && match[i] > i) {   // a filter: @ is an element of the list before it
                    int from = primary(i, s);
                    String over = rows(from, i, row);
                    if (over == null) {                          // rows derived from a table in a way the mapping cannot follow
                        List<String> reach = mentioned(from, i);
                        over = reach.isEmpty() ? null : UNKNOWN_ROWS + String.join(",", reach) + UNKNOWN_ROWS + src.substring(tok(from).pos(), tok(i).pos());
                    }
                    span(i + 1, match[i], over, elements);
                    i = match[i];
                    continue;
                }
                if (t.type() == Type.AT && i + 2 < e && tok(i + 1).type() == Type.DOT && tok(i + 2).type() == Type.IDENT) {
                    column(row, tok(i + 2));
                    i += 2;
                    continue;
                }
                if (t.type() == Type.DOT && i + 1 < e && tok(i + 1).type() == Type.IDENT && i > s) {
                    column(rowOf(primary(i, s), i, row), tok(i + 1));   // first(arg.rows).desk, arg.rows[0].desk, arg.rows[*].desk
                    i++;
                    continue;
                }
                if (t.type() == Type.IDENT && i + 2 < e && elements.containsKey(t.text()) && tok(i + 1).type() == Type.DOT
                        && tok(i + 2).type() == Type.IDENT && (i == 0 || tok(i - 1).type() != Type.DOT)) {
                    column(elements.get(t.text()), tok(i + 2));
                    i += 2;
                    continue;
                }
                if (t.type() == Type.IDENT && i + 1 < e && tok(i + 1).type() == Type.LPAREN && match[i + 1] > i
                        && (i == 0 || tok(i - 1).type() != Type.DOT)) {
                    call(i, stage, s, row, elements);
                    i = match[i + 1];
                    continue;
                }
                if (t.is(Type.IDENT, "lambda")) {
                    i = lambda(i, e, null, -1, row, elements) - 1;
                    continue;
                }
                if (t.type() == Type.IDENT) {
                    bareName(i, e, elements);
                }
            }
        }

        /** The start of the primary that ends before {@code i} (a filter's list), not before {@code s}. */
        private int primary(int i, int s) {
            for (int a = i - 3; a >= s; a--) {                   // arg.<table>[?...], a kebab name included
                Object[] r = arg(a, i);
                if (r != null && (int) r[1] == i) {
                    return a;
                }
            }
            int k = i - 1;
            while (k >= s) {
                Type t = tok(k).type();
                if (t == Type.RBRACKET && match[k] >= s) {          // an index or a filter: the primary continues to its left
                    k = match[k] - 1;
                } else if (t == Type.RPAREN && match[k] >= s) {
                    int o = match[k];
                    return o - 1 >= s && tok(o - 1).type() == Type.IDENT ? o - 1 : o;
                } else if (t == Type.RBRACE && match[k] >= s) {
                    return match[k];
                } else if (t == Type.IDENT && k - 1 >= s && tok(k - 1).type() == Type.DOT) {
                    k -= 2;
                } else {
                    return k;
                }
            }
            return s;
        }

        /** {@code lambda a, b: body} at {@code i}, ending before {@code e}: the body read with the element's name bound. The end. */
        private int lambda(int i, int e, String table, int place, String row, Map<String, String> elements) {
            Map<String, String> inner = new HashMap<>(elements);
            int j = i + 1;
            int k = 0;
            while (j < e && tok(j).type() != Type.COLON) {
                if (tok(j).type() == Type.IDENT) {
                    if (k == place && table != null) {
                        inner.put(tok(j).text(), table);
                    } else {
                        inner.remove(tok(j).text());
                    }
                    named.add(tok(j).text());
                    k++;
                }
                j++;
            }
            span(j + 1, e, row, inner);
            return e;
        }

        /** A call {@code f(...)} at {@code i}: its column arguments renamed or refused; its arguments read in their own scope. */
        private void call(int i, int stage, int s, String row, Map<String, String> elements) {
            String f = tok(i).text();
            int lp = i + 1;
            List<int[]> args = args(lp);
            boolean piped = i > s && tok(i - 1).is(Type.OP, "|>");
            String list = piped ? rows(stage == i ? lastStage(i, s) : stage, i - 1, row) : args.isEmpty() ? null : rows(args.get(0)[0], args.get(0)[1], row);
            if (list != null && list.startsWith(UNKNOWN_ROWS)) {
                list = null;                                     // rows the mapping cannot follow: a column text over them is refused below
            }
            List<String> names = CoreParams.of(f) != null ? CoreParams.names(f) : List.of("list", "field");
            for (int k = 0; k < args.size(); k++) {
                int as = args.get(k)[0];
                int ae = args.get(k)[1];
                int place = k + (piped ? 1 : 0);
                if (ae - as >= 2 && tok(as).type() == Type.IDENT && tok(as + 1).is(Type.OP, "=")) {
                    place = names.indexOf(tok(as).text());
                    as += 2;
                }
                ColumnParam c = columnParam(f, place);
                int elementPlace = element(f, place);
                if (c != null && ae - as == 1 && tok(as).type() == Type.STRING) {
                    text(f, list, tok(as));
                } else if (c != null && c.textOnly() && list != null) {
                    problems.add("names a column of table " + list + " in " + f + "(...) by a computed text ('" + src.substring(tok(as).pos(), end(ae - 1))
                            + "'): the caller's mapping cannot rename it; write the column parameter as text (one of " + String.join(", ", tables.columns(list))
                            + ") or read the column as @.<name>");
                } else if (elementPlace >= 0 && tok(as).is(Type.IDENT, "lambda")) {
                    lambda(as, ae, list, elementPlace, row, elements);
                } else if (elementPlace >= 0 || c != null) {
                    span(as, ae, list, elements);                  // @ is an element of the list here
                } else {
                    span(as, ae, row, elements);
                }
            }
        }

        /** The first token of the pipe stage before the one at {@code i}. */
        private int lastStage(int i, int s) {
            int k = i - 2;
            int start = s;
            for (int j = s; j < k; j++) {
                if (tok(j).is(Type.OP, "|>")) {
                    start = j + 1;
                }
                if (match[j] > j) {
                    j = match[j];
                }
            }
            return start;
        }

        /** The place of a form's argument that is read per element, and the element's place among a lambda's names; -1 otherwise. */
        private static int element(String f, int place) {
            return switch (f) {
                case "map" -> place == 1 ? 0 : -1;
                case "group" -> place == 1 || place == 2 ? 0 : -1;
                case "sort" -> place % 2 == 1 ? 0 : -1;
                case "reduce" -> place == 2 ? 1 : -1;
                default -> -1;
            };
        }

        private static ColumnParam columnParam(String f, int place) {
            for (ColumnParam c : COLUMN_PARAMS) {
                if (c.function().equals(f) && c.place() == place) {
                    return c;
                }
            }
            return null;
        }

        /** A text argument of a column parameter: renamed over a table; refused where the mapping cannot reach a local column. */
        private void text(String f, String list, Token t) {
            String path = t.text();
            if (path.contains("pXq{")) {
                return;                                          // a column parameter of the fragment: the caller's own name
            }
            int dot = path.indexOf('.');
            String col = dot < 0 ? path : path.substring(0, dot);
            if (list != null) {
                Set<String> cols = tables.columns(list);
                if (cols.contains(col)) {
                    tables.read(list, col);
                    String theirs = tables.rename(list, col);
                    if (!theirs.equals(col)) {
                        char q = src.charAt(t.pos());
                        edit(t.pos(), end(toks.indexOf(t)), q + theirs + path.substring(col.length()) + q);
                    }
                } else {
                    problems.add("names column '" + col + "' of table " + list + " in " + f + "(...), which it does not declare (it has "
                            + String.join(", ", cols) + ")");
                }
                return;
            }
            String owner = owner(col);
            if (owner != null) {
                problems.add("names '" + col + "', a local column of table " + owner + ", in " + f + "(...) over a list that is not the table: the"
                        + " caller's mapping cannot rename it; call it over the table (" + f + "(arg." + owner + ", '" + col + "')) or read it as @." + col);
            }
        }

        /** The source offset after token {@code k} (a text token's closing quote included). */
        private int end(int k) {
            Token t = tok(k);
            if (t.type() != Type.STRING) {
                return t.pos() + t.length();
            }
            char q = src.charAt(t.pos());
            int j = t.pos() + 1;
            while (j < src.length() && src.charAt(j) != q) {
                j += src.charAt(j) == '\\' ? 2 : 1;
            }
            return j + 1;
        }

        /** {@code @.<name>} or a lambda element's field over {@code table}: renamed when it is a local column. */
        private void column(String table, Token name) {
            if (table == null) {
                return;
            }
            if (table.startsWith(UNKNOWN_ROWS)) {                   // never silent: a local column over rows the mapping cannot follow
                int end = table.indexOf(UNKNOWN_ROWS, 1);
                for (String t : table.substring(1, end).split(",")) {
                    Set<String> cols = tables.columns(t);
                    if (cols != null && cols.contains(name.text())) {
                        problems.add("reads @." + name.text() + " over '" + table.substring(end + 1).trim() + "', rows derived from table " + t
                                + " that the caller's mapping cannot follow: filter the table itself (arg." + t + "[?...]), or derive the rows"
                                + " with sort, take, drop, unique, difference or intersect over it");
                        return;
                    }
                }
                return;
            }
            Set<String> cols = tables.columns(table);
            if (cols != null && cols.contains(name.text())) {
                tables.read(table, name.text());
                String theirs = tables.rename(table, name.text());
                if (!theirs.equals(name.text())) {
                    edit(name.pos(), name.pos() + name.length(), theirs);
                }
            }
        }

        /** A bare name that is a local column: refused (the caller's mapping reaches {@code @.<name>}, not a bare name). */
        private void bareName(int i, int e, Map<String, String> elements) {
            Token t = tok(i);
            if (!bare || WORDS.contains(t.text()) || named.contains(t.text()) || elements.containsKey(t.text())
                    || i > 0 && (tok(i - 1).type() == Type.DOT || tok(i - 1).type() == Type.AT)
                    || i > 1 && tok(i - 1).type() == Type.LBRACE && tok(i - 2).is(Type.IDENT, "pXq")
                    || i + 1 < e && tok(i + 1).is(Type.OP, "=")
                    || i + 1 < toks.size() && tok(i + 1).type() == Type.COLON && inRecord(i)) {
                return;
            }
            String owner = owner(t.text());
            if (owner != null) {
                problems.add("reads '" + t.text() + "', a local column of table " + owner + ", as a bare name: the caller's mapping does not reach a"
                        + " bare name; write @." + t.text() + " (or the column as text in a Core function's column parameter, sum(arg." + owner + ", '" + t.text() + "'))");
            }
        }

        /** Whether the token at {@code i} is directly inside a record literal. */
        private boolean inRecord(int i) {
            for (int k = i - 1; k >= 0; k--) {
                Type t = tok(k).type();
                if (match[k] >= 0 && match[k] < k) {
                    k = match[k];
                } else if (t == Type.LBRACE) {
                    return true;
                } else if (t == Type.LPAREN || t == Type.LBRACKET || t == Type.FILTER || t == Type.QUESTION) {
                    return false;
                }
            }
            return false;
        }

        /** The table parameter whose local columns hold {@code col}, or null. */
        private String owner(String col) {
            return ownerOf(tables, col);
        }
    }


    /** The table parameter whose local columns hold {@code col}, or null. */
    private static String ownerOf(Tables tables, String col) {
        for (String t : tables.tableNames()) {
            Set<String> cols = tables.columns(t);
            if (cols != null && cols.contains(col)) {
                return t;
            }
        }
        return null;
    }
}
