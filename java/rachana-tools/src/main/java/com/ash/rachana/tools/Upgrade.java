/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.tools;

import com.ash.rachana.el.ElException;
import com.ash.rachana.el.ElSource;
import com.ash.rachana.el.Expr;
import com.ash.rachana.el.FunctionTable;
import com.ash.rachana.parse.PNode;
import com.ash.rachana.parse.PositionalYamlReader;
import com.ash.rachana.parse.SutraParser;
import com.ash.rachana.parse.YamlScalarSpans;
import com.ash.rachana.profile.Grammar;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code rachana upgrade}: a Sutra written for language level 1.0 (and the fragments it uses) rewritten for a later level with the same
 * meaning ({@link UpgradeRule}). Every expression and template is read as level 1.0 reads it; what a later level reads differently is
 * rewritten to keep the 1.0 meaning ({@code range} to {@code span}, a named argument to the catalogue's name), or flagged where a
 * rewrite cannot keep it; the Sutra then declares the new level ({@code rachana: 1.2}). Only the characters of a rewritten word change:
 * comments, layout, quoting and every other byte stay as written, and the rewrite is checked by reading the file again. Upgrading an
 * upgraded file changes nothing. A file that does not read (YAML, an expression that does not compile at 1.0) is refused, never
 * guessed at. One implementation: the Node command line and {@code python -m drishti_rachana upgrade} run this class.
 */
public final class Upgrade {

    private Upgrade() {}

    /** One rewrite: the rule, where (the scalar's JSON Pointer and position), the expression before and after. */
    public record Change(String rule, String path, int line, int column, String before, String after) {}

    /** One flag: the rule, its severity ({@code info}, {@code review}, {@code blocking}), where, the expression and why. */
    public record Flag(String rule, String severity, String path, int line, int column, String expression, String message) {}

    /**
     * The outcome for one file.
     *
     * @param kind {@code sutra} or {@code fragment}
     * @param from the level it was written for
     * @param to the level it is upgraded to
     * @param text the upgraded text (the original when refused or blocked)
     * @param refused why the file was not upgraded, or null
     */
    public record Result(String file, String kind, String from, String to, String text, boolean changed, List<Change> changes, List<Flag> flags,
            String refused) {

        /** Whether the upgraded text may be written: not refused and no blocking flag. */
        public boolean writable() {
            return refused == null && flags.stream().noneMatch(f -> "blocking".equals(f.severity()));
        }
    }

    /** The expression options a Sutra and a fragment write (SutraExpressions' tables, by key name). */
    static final Set<String> EXPRESSION_KEYS = Set.of("bind", "rows", "where", "when", "value", "delta", "max", "min", "highlight", "each",
            "nodes", "edges", "limit", "children", "crumb", "root", "across", "region", "from", "to", "field", "by", "tabTitle", "source", "mark",
            "moreLabel");
    /** The template options. */
    static final Set<String> TEMPLATE_KEYS = Set.of("title", "text", "pill", "waits", "confirm");
    /** Subtrees that hold prose or another profile's expressions (the form profile reads alike at every level, DECISIONS A7b). */
    static final Set<String> SKIPPED = Set.of("doc", "description", "notes", "form", "label", "examples", "params", "tone", "fmt", "kind", "id",
            "code", "key", "area", "keys", "phone", "layout", "options");

    /** Functions whose value is a whole number however their arguments are written. */
    private static final Set<String> WHOLE = Set.of("size", "len", "count", "days", "year", "quarter", "month", "week", "weekday", "day", "find",
            "search", "code", "days_between", "business_days_between", "days360");
    /** The functions whose positions or order of texts count code points from 1.1 (Level11), less the others rules name. */
    private static final Set<String> TEXT_FUNCTIONS = Set.of("left", "right", "mid", "find", "search", "pad", "least", "greatest", "sort");
    private static final Set<String> REGEX = Set.of("regex_extract", "regex_replace", "matches", "matches_case");

    /** As {@link #upgrade(String, String, String, String, FunctionTable)} for a Sutra (or a fragment of level 1.0) over Core's functions. */
    public static Result upgrade(String file, String text, String to) {
        return upgrade(file, text, Grammar.LEVEL_1_0, to, FunctionTable.core());
    }

    /**
     * Upgrades one file.
     *
     * @param file its name (reports; a fragment is told by its {@code fragment:} or {@code block:} key)
     * @param fragmentFrom the level a fragment was written for (a fragment declares none; a Sutra's own {@code rachana:} key is read)
     * @param to the level to upgrade to
     * @param table the functions its expressions call (Core's and the host's profiles')
     */
    public static Result upgrade(String file, String text, String fragmentFrom, String to, FunctionTable table) {
        String scan = Work.placeholders(text);                       // a fragment's %{param} places read as names of the same length
        PNode root;
        try {
            root = new PositionalYamlReader().read(scan);
        } catch (RuntimeException e) {
            return refused(file, "?", "?", to, text, "it does not read as YAML: " + e.getMessage());
        }
        Map<String, PNode> top = root == null ? Map.of() : root.map();
        String kind = top.containsKey("sutra") ? "sutra" : top.containsKey("fragment") || top.containsKey("block") ? "fragment" : null;
        if (kind == null) {
            return refused(file, "?", "?", to, text, "it is neither a Sutra (sutra:) nor a fragment (fragment:, block:)");
        }
        String from = "sutra".equals(kind) ? SutraParser.declaredLevel(text) : fragmentFrom;
        if (from == null || !Grammar.LEVELS.contains(from)) {
            return refused(file, kind, String.valueOf(from), to, text, "its rachana: key names no level this runtime reads (rachana: 1 is 1.0)");
        }
        if (!Grammar.LEVELS.contains(to)) {
            return refused(file, kind, from, to, text, "level " + to + " is not one of " + String.join(", ", Grammar.LEVELS));
        }
        if (!com.ash.rachana.parse.SutraBlocks.atLeast(to, from)) {
            return refused(file, kind, from, to, text, "it is written for " + from + ", later than " + to + ": a Sutra is never moved down");
        }
        if (from.equals(to)) {
            return new Result(file, kind, from, to, text, false, List.of(), List.of(), null);
        }
        List<YamlScalarSpans.Scalar> scalars;
        try {
            scalars = YamlScalarSpans.of(scan);
        } catch (RuntimeException e) {
            return refused(file, kind, from, to, text, "it does not read as YAML: " + e.getMessage());
        }
        boolean from10 = Grammar.LEVEL_1_0.equals(from);
        Work w = new Work(text, scan, table == null ? FunctionTable.core() : table);
        for (YamlScalarSpans.Scalar s : scalars) {
            if (s.key()) {
                continue;
            }
            if ("sutra".equals(kind) && "/rachana".equals(s.path())) {
                w.edit(s, 0, s.value().length(), to, UpgradeRule.DECLARE, s.value(), to);
                continue;
            }
            String role = role(s.path(), kind);
            if (role == null || !from10) {
                continue;                                             // every difference is between 1.0 and 1.1 (UpgradeRule)
            }
            if ("maybe".equals(role) && !looksLikeExpression(s.value())) {
                continue;                                             // a text argument ("Buy / sell-back"), not an expression
            }
            String refusal = "template".equals(role) ? w.template(s) : w.expression(s, s.value(), 0, !"maybe".equals(role));
            if (refusal != null) {
                return refused(file, kind, from, to, text, refusal);
            }
        }
        String out;
        try {
            out = w.apply();
        } catch (IllegalStateException e) {
            return refused(file, kind, from, to, text, e.getMessage());
        }
        return new Result(file, kind, from, to, out, !out.equals(text), List.copyOf(w.changes), List.copyOf(w.flags), null);
    }

    /** Whether an argument of a fragment use reads as an expression: it names data ($, @, ctl., #index), calls, indexes or quotes. */
    static boolean looksLikeExpression(String v) {
        return v.contains("$") || v.contains("@") || v.contains("(") || v.contains("[") || v.contains("#index") || v.contains("ctl.")
                || v.contains("'") || v.contains("\"");
    }

    private static Result refused(String file, String kind, String from, String to, String text, String why) {
        return new Result(file, kind, from, to, text, false, List.of(), List.of(), why);
    }

    /**
     * What a scalar holds, by its place: {@code expression}, {@code template}, {@code maybe} (a fragment's argument or parameter default:
     * an expression when it reads as one), or null (prose, names, formats, the form profile's rules).
     */
    static String role(String path, String kind) {
        String[] parts = path.split("/", -1);
        if (parts.length < 2) {
            return null;
        }
        String last = parts[parts.length - 1];
        for (int i = 1; i < parts.length - 1; i++) {
            if (SKIPPED.contains(parts[i]) && !("params".equals(parts[i]) && "fragment".equals(kind) && i == 1)) {
                return null;
            }
            if ("with".equals(parts[i]) && i + 1 == parts.length - 1 && !"title".equals(parts[i - 1])) {
                return "maybe";                                       // { use: f@1, with: { rows: $.x } }: a fragment's argument
            }
        }
        if ("fragment".equals(kind) && parts.length == 3 && "params".equals(parts[1])) {
            return "maybe";
        }
        if (parts.length == 3 && "title".equals(parts[1])) {
            return "pill".equals(last) ? "template" : "expression";    // title: { pill, id, with }
        }
        if (parts.length == 3 && "match".equals(parts[1])) {
            return "where".equals(last) ? "expression" : null;
        }
        if (parts.length >= 2 && "payload".equals(parts[parts.length - 2])) {
            return "expression";
        }
        if (EXPRESSION_KEYS.contains(last)) {
            return "expression";
        }
        if (TEMPLATE_KEYS.contains(last) && parts.length > 2) {
            return "template";
        }
        return null;
    }

    /** The rewrites and flags of one file, and their application. */
    private static final class Work {
        final String text;
        /** The text as read: {@code text} with each {@code %{param}} as a name of the same length (offsets are the same). */
        final String scan;
        final FunctionTable table;
        final List<Change> changes = new ArrayList<>();
        final List<Flag> flags = new ArrayList<>();
        /** Per scalar (by start): its edits in value offsets. */
        final Map<Integer, List<int[]>> edits = new LinkedHashMap<>();
        final Map<Integer, List<String>> replacements = new LinkedHashMap<>();
        final Map<Integer, YamlScalarSpans.Scalar> scalars = new LinkedHashMap<>();

        Work(String text, String scan, FunctionTable table) {
            this.text = text;
            this.scan = scan;
            this.table = table;
        }

        void edit(YamlScalarSpans.Scalar s, int from, int to, String with, UpgradeRule rule, String before, String after) {
            scalars.put(s.start(), s);
            edits.computeIfAbsent(s.start(), k -> new ArrayList<>()).add(new int[] {from, to});
            replacements.computeIfAbsent(s.start(), k -> new ArrayList<>()).add(with);
            if (rule != null) {
                changes.add(new Change(rule.id(), s.path(), s.line(), s.column(), before, after));
            }
        }

        void flag(YamlScalarSpans.Scalar s, UpgradeRule rule, String severity, String expr, String message) {
            flags.add(new Flag(rule.id(), severity, s.path(), s.line(), s.column(), expr, message));
        }

        /** A template's {@code ${...}} parts, each an expression (brace matching as Template's). */
        String template(YamlScalarSpans.Scalar s) {
            String v = s.value();
            int i = 0;
            while (i < v.length()) {
                int start = v.indexOf("${", i);
                if (start < 0) {
                    break;
                }
                int depth = 0;
                int end = -1;
                for (int k = start + 2; k < v.length(); k++) {
                    char c = v.charAt(k);
                    if (c == '{') {
                        depth++;
                    } else if (c == '}') {
                        if (depth == 0) {
                            end = k;
                            break;
                        }
                        depth--;
                    }
                }
                if (end < 0) {
                    return "the template at " + s.path() + " (line " + s.line() + ") does not compile at 1.0: unclosed '${'";
                }
                String refusal = expression(s, v.substring(start + 2, end), start + 2, true);
                if (refusal != null) {
                    return refusal;
                }
                i = end + 1;
            }
            return null;
        }

        /**
         * One expression ({@code src}, at {@code at} in the scalar's value), read as level 1.0 reads it. A fragment's {@code %{param}}
         * places are read as names of the same length. Returns why the file is refused, or null.
         */
        String expression(YamlScalarSpans.Scalar s, String written, int at, boolean certain) {
            String src = placeholders(written);
            List<ElSource.Tok> toks;
            ElSource.Parsed parsed = null;
            try {
                toks = ElSource.tokens(src, false);
                parsed = ElSource.parse(src, table, false);
            } catch (ElException e) {
                if (!certain) {
                    return null;                                      // not an expression after all (a text argument)
                }
                if (e.getMessage() != null && e.getMessage().contains("unknown function") && !src.equals(written)) {
                    return null;                                      // a fragment's expression completed only at its use
                }
                return "the expression at " + s.path() + " (line " + s.line() + ") does not compile at 1.0: " + e.getMessage() + " (in '"
                        + written + "')";
            }
            tokenRules(s, written, at, toks);
            if (parsed != null) {
                treeRules(s, written, src, parsed);
            }
            return null;
        }

        /** {@code %{name}} as a name of the same length ({@code __name_}), so a fragment's expression reads before its use. */
        static String placeholders(String src) {
            StringBuilder b = new StringBuilder(src);
            int i = src.indexOf("%{");
            while (i >= 0) {
                int end = src.indexOf('}', i + 2);
                if (end < 0) {
                    break;
                }
                b.setCharAt(i, '_');
                b.setCharAt(i + 1, '_');
                b.setCharAt(end, '_');
                i = src.indexOf("%{", end + 1);
            }
            return b.toString();
        }

        void tokenRules(YamlScalarSpans.Scalar s, String written, int at, List<ElSource.Tok> toks) {
            List<String> calls = new ArrayList<>();                   // the open brackets: a function's name, "(" or "["
            for (int i = 0; i < toks.size(); i++) {
                ElSource.Tok t = toks.get(i);
                ElSource.Tok prev = i > 0 ? toks.get(i - 1) : null;
                ElSource.Tok next = i + 1 < toks.size() ? toks.get(i + 1) : null;
                boolean afterDot = prev != null && "DOT".equals(prev.type());
                switch (t.type()) {
                    case "IDENT" -> {
                        boolean call = next != null && "LPAREN".equals(next.type()) && !afterDot;
                        if (call && "range".equals(t.text())) {
                            String after = written.substring(0, t.start()) + "span" + written.substring(t.end());
                            edit(s, at + t.start(), at + t.end(), "span", UpgradeRule.SPAN, written, after);
                        }
                        if (call) {
                            calls.add(t.text());
                            i++;                                          // the '(' is opened here
                            continue;
                        }
                        if (!afterDot && "lambda".equals(t.text())) {
                            flag(s, UpgradeRule.KEYWORD, "blocking", written, "'lambda' reads a field at 1.0 and starts a lambda from 1.1: write @.lambda "
                                    + "or $.lambda (whichever the bare name reads here)");
                        }
                        String fn = calls.isEmpty() ? null : calls.get(calls.size() - 1);
                        if (next != null && "OP".equals(next.type()) && "=".equals(next.text()) && fn != null && !fn.equals("(") && !fn.equals("[")
                                && !fn.equals("let") && prev != null && ("LPAREN".equals(prev.type()) || "COMMA".equals(prev.type()))) {
                            String renamed = ElSource.namedArgumentAtLevel11(fn, t.text());
                            if (renamed == null) {
                                flag(s, UpgradeRule.NAMED, "blocking", written, fn + ": the argument named " + t.text() + " cannot be given by that "
                                        + "name from 1.1 (SPEC 4.10 rule 3): give it by place");
                            } else if (!renamed.equals(t.text())) {
                                String after = written.substring(0, t.start()) + renamed + written.substring(t.end());
                                edit(s, at + t.start(), at + t.end(), renamed, UpgradeRule.NAMED, written, after);
                            }
                        }
                    }
                    case "LPAREN" -> calls.add("(");
                    case "LBRACKET", "FILTER" -> calls.add("[");
                    case "RPAREN", "RBRACKET" -> {
                        if (!calls.isEmpty()) {
                            calls.remove(calls.size() - 1);
                        }
                    }
                    case "STRING" -> {
                        boolean operand = isOperator(prev) || isOperator(next);
                        if (operand && ElSource.readsAsNumber(t.text(), false) != ElSource.readsAsNumber(t.text(), true)) {
                            flag(s, UpgradeRule.NUMBER_TEXT, "review", written, "'" + t.text() + "' reads as a number at 1.0 (Java's grammar) but not "
                                    + "from 1.1 (EL1-5): write the number itself");
                        }
                        if (t.text().codePoints().anyMatch(c -> c > 0xFFFF)) {
                            flag(s, UpgradeRule.TEXT_CODE_POINTS, "review", written, "a text with a character beyond U+FFFF: its order and positions "
                                    + "count code points from 1.1 (SPEC 4.13)");
                        }
                    }
                    default -> { }
                }
            }
        }

        static boolean isOperator(ElSource.Tok t) {
            return t != null && "OP".equals(t.type()) && Set.of("+", "-", "*", "/", "%", "<", "<=", ">", ">=", "==", "!=").contains(t.text());
        }

        void treeRules(YamlScalarSpans.Scalar s, String written, String src, ElSource.Parsed p) {
            List<String> exact = new ArrayList<>();
            boolean[] textOrder = {false};
            walk(p.expr(), e -> {
                if (e instanceof Expr.Binary b) {
                    String why = exactWhy(b, src, p);
                    if (why != null) {
                        exact.add(why);
                    }
                    if (Set.of("<", "<=", ">", ">=").contains(b.op()) && !numeric(b.l(), src, p) && !numeric(b.r(), src, p)) {
                        textOrder[0] = true;
                    }
                } else if (e instanceof Expr.Call c) {
                    if ("standardize".equals(c.name())) {
                        flag(s, UpgradeRule.STANDARDIZE, "review", written, "standardize returns a decimal at 1.0 and a number from 1.1 (N5): its "
                                + "text may differ in the last digits");
                    }
                    if (REGEX.contains(c.name()) && c.args().size() > 1 && !(c.args().get(1) instanceof Expr.Lit)) {
                        flag(s, UpgradeRule.REGEX_CODE, "info", written, c.name() + " with a pattern computed at run time: a refused pattern is "
                                + "RCH-4010 at 1.0, RCH-2102 from 1.1 (N5)");
                    }
                    if ("sum".equals(c.name()) && !c.args().isEmpty() && c.args().get(0) instanceof Expr.ListLit l
                            && l.items().stream().anyMatch(x -> "frac".equals(kindOf(x, src, p)))) {
                        exact.add("sum of a list with fractional literals");
                    }
                    if (TEXT_FUNCTIONS.contains(c.name())) {
                        textOrder[0] = true;
                    }
                } else if (e instanceof Expr.Form f && TEXT_FUNCTIONS.contains(f.name())) {
                    textOrder[0] = true;
                }
            });
            if (!exact.isEmpty()) {
                flag(s, UpgradeRule.EXACT, "review", written, "computed exactly from 1.1 (SPEC 4.16): " + String.join("; ", exact)
                        + ": where the operands are exact numbers the digits may differ from 1.0's double; write * 1e0 on an operand to keep the double");
            }
            if (textOrder[0]) {
                flag(s, UpgradeRule.TEXT_CODE_POINTS, "info", written, "orders or counts texts: the same answer unless a text holds a character "
                        + "beyond U+FFFF (SPEC 4.13)");
            }
        }

        /** Why a 1.0 binary operator may give other digits from 1.1, or null when it cannot. */
        static String exactWhy(Expr.Binary b, String src, ElSource.Parsed p) {
            String op = b.op();
            String l = kindOf(b.l(), src, p);
            String r = kindOf(b.r(), src, p);
            if ("double".equals(l) || "double".equals(r)) {
                return null;
            }
            String shown = written(b, src, p);
            switch (op) {
                case "/" -> {
                    if (b.r() instanceof Expr.Lit lit && lit.value() instanceof Number && powerOfTen(written(b.r(), src, p))) {
                        return null;                                  // x / 100: the double prints the exact quotient's digits
                    }
                    return "'" + shown + "' divides";
                }
                case "%" -> {
                    return "whole".equals(l) && "whole".equals(r) ? null : "'" + shown + "' takes a remainder";
                }
                case "+", "-", "*" -> {
                    return "frac".equals(l) || "frac".equals(r) ? "'" + shown + "' has a fractional literal" : null;
                }
                default -> {
                    return null;
                }
            }
        }

        /** {@code double} (written with an exponent), {@code whole}, {@code frac} (a fractional literal), or {@code unknown} (from the host). */
        static String kindOf(Expr e, String src, ElSource.Parsed p) {
            if (e instanceof Expr.Lit lit) {
                String w = written(e, src, p);
                if (lit.value() instanceof Number) {
                    if (w.indexOf('e') >= 0 || w.indexOf('E') >= 0) {
                        return "double";
                    }
                    int dot = w.indexOf('.');
                    return dot < 0 || w.substring(dot + 1).chars().allMatch(c -> c == '0') ? "whole" : "frac";
                }
                if (lit.value() instanceof String t && ElSource.readsAsNumber(t, true)) {
                    return t.contains(".") ? "frac" : "whole";
                }
                return "unknown";
            }
            if (e instanceof Expr.Index) {
                return "whole";
            }
            if (e instanceof Expr.Call c && WHOLE.contains(c.name())) {
                return "whole";
            }
            return "unknown";
        }

        static boolean numeric(Expr e, String src, ElSource.Parsed p) {
            if (e instanceof Expr.Lit lit) {
                return lit.value() instanceof Number || lit.value() instanceof String t && ElSource.readsAsNumber(t, true);
            }
            return e instanceof Expr.Index || e instanceof Expr.Binary b && Set.of("+", "-", "*", "/", "%").contains(b.op())
                    || e instanceof Expr.Neg || e instanceof Expr.Call c && (WHOLE.contains(c.name()) || Set.of("sum", "avg", "min", "max", "abs",
                    "round", "floor", "ceiling", "median", "percentile", "wavg").contains(c.name()));
        }

        static boolean powerOfTen(String w) {
            return w.matches("1(0)*(\\.0*)?");
        }

        static String written(Expr e, String src, ElSource.Parsed p) {
            int[] sp = p.spans().get(e);
            return sp == null ? src : src.substring(Math.max(0, sp[0]), Math.min(src.length(), sp[1]));
        }

        /** Every node of the tree, parents first. */
        static void walk(Expr e, java.util.function.Consumer<Expr> visit) {
            if (e == null) {
                return;
            }
            visit.accept(e);
            for (java.lang.reflect.RecordComponent c : e.getClass().getRecordComponents()) {
                Object v;
                try {
                    v = c.getAccessor().invoke(e);
                } catch (ReflectiveOperationException x) {
                    continue;
                }
                if (v instanceof Expr x) {
                    walk(x, visit);
                } else if (v instanceof List<?> l) {
                    l.forEach(o -> {
                        if (o instanceof Expr x) {
                            walk(x, visit);
                        }
                    });
                } else if (v instanceof Map<?, ?> m) {
                    m.values().forEach(o -> {
                        if (o instanceof Expr x) {
                            walk(x, visit);
                        }
                    });
                }
            }
        }

        /** The text with every edit made in place, then read again: each edited scalar holds what was meant, every other one is untouched. */
        String apply() {
            if (edits.isEmpty()) {
                return text;
            }
            List<int[]> file = new ArrayList<>();                     // {start, end, index into texts}
            List<String> texts = new ArrayList<>();
            Map<String, String> expected = new LinkedHashMap<>();
            for (Map.Entry<Integer, List<int[]>> en : edits.entrySet()) {
                YamlScalarSpans.Scalar s = scalars.get(en.getKey());
                int[] map = sourceMap(scan, s);
                if (map == null) {
                    throw new IllegalStateException("the value at " + s.path() + " (line " + s.line() + ") is written in a form this tool does not "
                            + "rewrite in place (a folded or multi-line quoted scalar): rewrite it by hand");
                }
                List<int[]> es = new ArrayList<>(en.getValue());
                List<String> rs = replacements.get(en.getKey());
                Integer[] order = new Integer[es.size()];
                for (int i = 0; i < order.length; i++) {
                    order[i] = i;
                }
                java.util.Arrays.sort(order, (a, b) -> Integer.compare(es.get(b)[0], es.get(a)[0]));
                StringBuilder value = new StringBuilder(s.value());
                int last = Integer.MAX_VALUE;
                for (int i : order) {
                    int[] e = es.get(i);
                    if (e[1] > last) {
                        throw new IllegalStateException("two rewrites overlap at " + s.path());
                    }
                    last = e[0];
                    value.replace(e[0], e[1], rs.get(i));
                    file.add(new int[] {map[e[0]], map[e[1]], texts.size()});
                    texts.add(rs.get(i));
                }
                expected.put(s.path(), value.toString());
            }
            file.sort((a, b) -> Integer.compare(b[0], a[0]));
            StringBuilder out = new StringBuilder(text);
            for (int[] f : file) {
                out.replace(f[0], f[1], texts.get(f[2]));
            }
            String result = out.toString();
            List<YamlScalarSpans.Scalar> before = YamlScalarSpans.of(scan);
            List<YamlScalarSpans.Scalar> after;
            try {
                after = YamlScalarSpans.of(placeholders(result));
            } catch (RuntimeException e) {
                throw new IllegalStateException("the rewritten text does not read as YAML (" + e.getMessage() + "): nothing written");
            }
            if (before.size() != after.size()) {
                throw new IllegalStateException("the rewritten text reads as another document: nothing written");
            }
            for (int i = 0; i < before.size(); i++) {
                YamlScalarSpans.Scalar b = before.get(i);
                YamlScalarSpans.Scalar a = after.get(i);
                String want = !b.key() && expected.containsKey(b.path()) ? expected.get(b.path()) : b.value();
                if (!b.path().equals(a.path()) || b.key() != a.key() || !java.util.Objects.equals(want, a.value())) {
                    throw new IllegalStateException("the rewrite of " + b.path() + " did not read back as meant: nothing written");
                }
            }
            return result;
        }
    }

    /**
     * For each offset of a scalar's value (and its end), the offset in the file text, or null for a form not rewritten in place: a plain
     * or quoted scalar on one line, or a literal block ({@code |}).
     */
    static int[] sourceMap(String text, YamlScalarSpans.Scalar s) {
        String v = s.value();
        int[] map = new int[v.length() + 1];
        int start = s.start();
        int end = s.end();
        String raw = text.substring(start, end);
        switch (s.style()) {
            case 0 -> {
                if (!raw.equals(v)) {
                    return null;
                }
                for (int i = 0; i <= v.length(); i++) {
                    map[i] = start + i;
                }
                return map;
            }
            case '\'' -> {
                int j = start + 1;
                for (int i = 0; i < v.length(); i++) {
                    char c = text.charAt(j);
                    if (c == '\n' || c == '\r') {
                        return null;
                    }
                    map[i] = j;
                    j += c == '\'' ? 2 : 1;
                }
                map[v.length()] = j;
                return text.charAt(j) == '\'' ? map : null;
            }
            case '"' -> {
                int j = start + 1;
                int i = 0;
                while (i < v.length()) {
                    char c = text.charAt(j);
                    if (c == '\n' || c == '\r') {
                        return null;
                    }
                    map[i] = j;
                    if (c == '\\') {
                        char x = text.charAt(j + 1);
                        int len = x == 'x' ? 4 : x == 'u' ? 6 : x == 'U' ? 10 : 2;
                        String decoded = x == 'U' ? new String(Character.toChars(Integer.parseInt(text.substring(j + 2, j + 10), 16))) : "?";
                        if (x == 'U' && decoded.length() == 2) {
                            map[++i] = j;
                        }
                        j += len;
                    } else {
                        j++;
                    }
                    i++;
                }
                map[v.length()] = j;
                return text.charAt(j) == '"' ? map : null;
            }
            case '|' -> {
                int nl = text.indexOf('\n', start);
                if (nl < 0) {
                    return null;
                }
                int j = nl + 1;
                int indent = -1;
                int i = 0;
                while (i < v.length() && j < end) {
                    int lineEnd = text.indexOf('\n', j);
                    if (lineEnd < 0 || lineEnd > end) {
                        lineEnd = end;
                    }
                    String line = text.substring(j, lineEnd);
                    if (indent < 0 && !line.isBlank()) {
                        indent = line.length() - line.stripLeading().length();
                    }
                    int skip = line.isBlank() ? Math.min(line.length(), Math.max(0, indent)) : Math.max(0, indent);
                    for (int k = j + skip; k < lineEnd && i < v.length(); k++) {
                        if (v.charAt(i) != text.charAt(k)) {
                            return null;
                        }
                        map[i++] = k;
                    }
                    if (i < v.length() && v.charAt(i) == '\n') {
                        map[i++] = lineEnd;
                    }
                    j = lineEnd + 1;
                }
                if (i != v.length()) {
                    return null;
                }
                map[v.length()] = i == 0 ? start : map[v.length() - 1] + 1;
                return map;
            }
            default -> {
                return null;
            }
        }
    }

    /** A unified diff of two texts (three lines of context), for {@code --diff}; empty when they are equal. */
    public static String diff(String file, String before, String after) {
        if (before.equals(after)) {
            return "";
        }
        String[] a = before.split("\n", -1);
        String[] b = after.split("\n", -1);
        int n = a.length;
        int m = b.length;
        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                lcs[i][j] = a[i].equals(b[j]) ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }
        List<String[]> ops = new ArrayList<>();                       // {" "|"-"|"+", line, aIndex, bIndex}
        int i = 0;
        int j = 0;
        while (i < n || j < m) {
            if (i < n && j < m && a[i].equals(b[j])) {
                ops.add(new String[] {" ", a[i], String.valueOf(i), String.valueOf(j)});
                i++;
                j++;
            } else if (i < n && (j == m || lcs[i + 1][j] >= lcs[i][j + 1])) {
                ops.add(new String[] {"-", a[i], String.valueOf(i), String.valueOf(j)});
                i++;
            } else {
                ops.add(new String[] {"+", b[j], String.valueOf(i), String.valueOf(j)});
                j++;
            }
        }
        StringBuilder out = new StringBuilder("--- a/" + file + "\n+++ b/" + file + "\n");
        int k = 0;
        while (k < ops.size()) {
            if (" ".equals(ops.get(k)[0])) {
                k++;
                continue;
            }
            int from = Math.max(0, k - 3);
            int to = k;
            int quiet = 0;
            while (to < ops.size() && quiet <= 6) {
                quiet = " ".equals(ops.get(to)[0]) ? quiet + 1 : 0;
                to++;
            }
            to = Math.min(ops.size(), to - Math.max(0, quiet - 3));
            int aStart = Integer.parseInt(ops.get(from)[2]);
            int bStart = Integer.parseInt(ops.get(from)[3]);
            int aCount = 0;
            int bCount = 0;
            for (int x = from; x < to; x++) {
                aCount += "+".equals(ops.get(x)[0]) ? 0 : 1;
                bCount += "-".equals(ops.get(x)[0]) ? 0 : 1;
            }
            out.append("@@ -").append(aStart + 1).append(',').append(aCount).append(" +").append(bStart + 1).append(',').append(bCount).append(" @@\n");
            for (int x = from; x < to; x++) {
                out.append(ops.get(x)[0]).append(ops.get(x)[1]).append('\n');
            }
            k = to;
        }
        return out.toString();
    }

    /** A report of results as JSON-ready maps: per file its outcome, every change and every flag; totals by rule. */
    public static Map<String, Object> report(List<Result> results) {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Integer> byRule = new java.util.TreeMap<>();
        List<Object> files = new ArrayList<>();
        int changed = 0;
        int refused = 0;
        int blocked = 0;
        for (Result r : results) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("file", r.file());
            f.put("kind", r.kind());
            f.put("from", r.from());
            f.put("to", r.to());
            f.put("changed", r.changed());
            f.put("refused", r.refused());
            f.put("changes", r.changes().stream().map(c -> (Object) mapOf("rule", c.rule(), "path", c.path(), "line", c.line(), "column", c.column(),
                    "before", c.before(), "after", c.after())).toList());
            f.put("flags", r.flags().stream().map(x -> (Object) mapOf("rule", x.rule(), "severity", x.severity(), "path", x.path(), "line", x.line(),
                    "column", x.column(), "expression", x.expression(), "message", x.message())).toList());
            files.add(f);
            changed += r.changed() ? 1 : 0;
            refused += r.refused() != null ? 1 : 0;
            blocked += r.refused() == null && !r.writable() ? 1 : 0;
            r.changes().forEach(c -> byRule.merge("change " + c.rule(), 1, Integer::sum));
            r.flags().forEach(x -> byRule.merge("flag " + x.rule() + " (" + x.severity() + ")", 1, Integer::sum));
        }
        out.put("files", results.size());
        out.put("changed", changed);
        out.put("refused", refused);
        out.put("blocked", blocked);
        out.put("byRule", byRule);
        out.put("results", files);
        return out;
    }

    private static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    /** The rules as a table for {@code --rules}: id, action, severity, title. */
    public static List<String> rules() {
        List<String> out = new ArrayList<>();
        for (UpgradeRule r : UpgradeRule.values()) {
            out.add(r.id() + "\t" + r.action().name().toLowerCase(java.util.Locale.ROOT) + "\t" + r.severity() + "\t" + r.title());
        }
        return Collections.unmodifiableList(out);
    }
}
