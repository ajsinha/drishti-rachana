/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.tools;

import java.util.List;

/**
 * Every way language level 1.0 and the later levels differ, and what {@code rachana upgrade} does about each ({@link Upgrade}): rewrite
 * the text so it keeps its 1.0 meaning, flag what may differ and cannot be kept by a rewrite, or nothing, with the reason. The list is
 * complete against the documents that name the differences: {@code UpgradeRulesTest} checks that every moved case
 * ({@code compat/levels/moved-cases.yaml}), every level-bound conformance case, every function level 1.1 binds anew, every key, kind and
 * option of a later level, every row of SPEC 4.17, every row of {@code el/KNOWN_DIFFERENCES.md}, every item of SPEC 9 and every
 * level decision of DECISIONS is covered by one rule's {@link #covers()}.
 */
public enum UpgradeRule {

    DECLARE(Action.REWRITE, "info", "The Sutra or fragment declares the level it is written for",
            "rachana: 1 becomes rachana: 1.2 (SPEC 2.1 rule 4), in a Sutra and a fragment alike: a fragment's rachana: key is its level; a "
                    + "fragment without the key is 1.0 and gains rachana: 1.2 as its first key; a file already at the target level is left untouched",
            List.of("spec2.1:4", "decision:UP-0", "case:problem/level-declaration#*", "case:sutra/level-declaration#*")),

    SPAN(Action.REWRITE, "info", "range(list, field?) is span(list, field?)",
            "level 1.0's range is the aggregate max - min; from 1.1 range is Python's sequence and the aggregate is spelled span (EL1-1): "
                    + "every call of range in a level-1.0 expression is the aggregate, renamed span with its arguments unchanged",
            List.of("decision:EL1-1", "decision:PY4", "spec4.17:`span(list, field?)`", "spec4.17:`range(stop)`, `range(start, stop, step?)`",
                    "level11:range", "level11:span")),

    NAMED(Action.REWRITE, "info", "A named argument takes the catalogue's name",
            "from 1.1 a Core function's arguments are named as the function catalogue names them (SPEC 4.10 rule 3); a level-1.0 name is "
                    + "renamed to the catalogue's name of the parameter it binds at 1.0 (the place is kept); a keyword-only argument 1.0 kept by name, or a repeatable parameter, cannot be given by "
                    + "name at 1.1: flagged, the file is not rewritten",
            List.of("spec4.10:3", "case:el/named-arguments-1-1#*")),

    EXACT(Action.FLAG, "review", "Arithmetic that 1.2 computes exactly",
            "level 1.0 computes every operator in doubles; from 1.1 integers and decimals (literals, whole numbers from the host, number-like "
                    + "text) are exact (SPEC 4.16, EL1-3): / and % of exact operands, and + - * with a fractional literal, may give other "
                    + "digits (1 / 3, 3 * 0.1); sum, avg, min and max over a list of exact values are exact too (avg([1, 2, 2])). Flagged, not rewritten (decision UP-1): the exact answer is the one 1.1 was made for, and "
                    + "writing * 1e0 on an operand keeps the double where it matters",
            List.of("decision:EL1-3", "decision:UP-1", "spec4.16", "spec4.17:exact numbers", "known:`el/numbers-exact` (34)",
                    "known:22 cases tagged `changes-with-xd3` in `el/examples/arithmetic` and `el/paths-operators`", "level11:sum",
                    "level11:min", "level11:max",
                    "case:el/numbers-exact#*")),

    NUMBER_TEXT(Action.FLAG, "review", "Number-like text that 1.2 does not read as a number",
            "level 1.0 reads text as a number by Java's grammar (hexadecimal, Infinity, NaN, d and f suffixes); from 1.1 by the literal "
                    + "grammar (EL1-5, D5-2): a quoted literal of that kind in an operator or comparison is flagged; text from the host is the "
                    + "same unless it holds such forms",
            List.of("decision:EL1-5", "decision:D5-2")),

    STANDARDIZE(Action.FLAG, "review", "standardize returns a number",
            "level 1.0's standardize returns a decimal, 1.1's a number (N5): its text may differ in the last digits",
            List.of("level11:standardize", "known:`el/distributions#standardize-42-40-1-5`", "case:el/distributions#standardize-42-40-1-5-l11")),

    KEYWORD(Action.FLAG, "blocking", "A name that is a word of the 1.1 grammar",
            "a bare name lambda reads a field at 1.0 and is the start of a lambda from 1.1 (PY3, EL1-6): it cannot be kept by a rewrite of "
                    + "the expression alone; write @.lambda or $.lambda by hand",
            List.of("decision:PY3", "decision:EL1-6", "spec4.17:`lambda x: body`, `lambda acc, x: body`")),

    TEXT_CODE_POINTS(Action.FLAG, "info", "Texts ordered and counted by code point",
            "from 1.1 text order and text positions count code points, not UTF-16 units (SPEC 4.13, XD4): sort, least, greatest, left, "
                    + "right, mid, find, search, pad and the ordering operators over texts give another answer only for a text that holds a "
                    + "character beyond U+FFFF (a literal that holds one is flagged review)",
            List.of("spec4.13", "spec4.17:texts by code point", "spec9:4", "known:`el/text-code-points` (17)", "case:el/text-code-points#*",
                    "level11:left", "level11:right", "level11:mid", "level11:find", "level11:search", "level11:pad", "level11:least",
                    "level11:greatest", "level11:sort")),

    AGGREGATE_NOT_A_LIST(Action.FLAG, "review", "An aggregate over something that is not a list",
            "level 1.0 answers an aggregate whose first argument is not a list with empty (0 for a sum); from 1.1 it is RCH-2102 (SPEC 5.1, "
                    + "G4 rehearsal P4): a first argument written as a number, a text, a truth value or arithmetic is flagged; a path that "
                    + "holds a scalar in the host's data is the same unless the data holds one",
            List.of("case:el/aggregates-host-values#*", "level11:avedev", "level11:avg", "level11:corr", "level11:count", "level11:covar", "level11:covar_pop", "level11:cv", "level11:devsq", "level11:distinct", "level11:geomean", "level11:harmean", "level11:intercept", "level11:iqr", "level11:kth_largest", "level11:kth_smallest", "level11:kurtosis", "level11:median", "level11:mode", "level11:mode_multi", "level11:nmiss", "level11:percent_rank_of", "level11:percentile", "level11:percentile_exc", "level11:product", "level11:rank_avg", "level11:rsq", "level11:skewness", "level11:skewness_pop", "level11:slope", "level11:stddev", "level11:stddev_pop", "level11:stderr", "level11:steyx", "level11:string_agg", "level11:sumproduct", "level11:sumsq", "level11:trimmean", "level11:variance", "level11:variance_pop", "level11:wavg")),

    REGEX_CODE(Action.FLAG, "info", "A refused pattern met at evaluation is RCH-2102",
            "level 1.0 raises RCH-4010 for a regular expression refused at evaluation, 1.1 RCH-2102 (N5): only a pattern computed at run time "
                    + "can be refused there",
            List.of("known:`el/text#regex-extract-ab-trade-id`", "case:el/text#regex-extract-ab-trade-id-l11", "level11:regex_extract",
                    "level11:regex_replace", "level11:matches", "level11:matches_case")),

    SYNTAX_1_1(Action.NONE, "none", "The 1.1 syntax batch",
            "records, spread, slices, the pipe, ??, a if c else b, comments, digit separators and the reserved tokens are refused at 1.0 "
                    + "(el/syntax-at-1-0): a level-1.0 Sutra holds none of them, so nothing changes",
            List.of("spec4.15", "spec4.17:record <code>{k: v, 'k-2': v, ...r}</code>", "spec4.17:list spread `[...a, b]`",
                    "spec4.17:slice `t[a:b]`, `t[a:b:c]`", "spec4.17:pipe `x |> f(a)`", "spec4.17:`a ?? b`", "spec4.17:`a if c else b`",
                    "spec4.17:comment `/* … */`", "spec4.17:digit separator `1_000_000`", "decision:PY2", "decision:PY7", "decision:EL1-4",
                    "known:`el/reserved` (25), `grammar/reserved` (16)", "case:el/syntax-1-1#*", "case:el/syntax-at-1-0#*",
                    "case:grammar/reserved#*", "case:el/reserved#*")),

    POWER_SIGN(Action.NONE, "none", "-x ^ y and -x ** y",
            "level 1.0 has no infix ^ (^ is the parent) and refuses ** (write power(x, y)), so a level-1.0 Sutra writes power(-x, y) or "
                    + "-power(x, y), whose meaning no level changes; from 1.1 ** and ^ share Python's precedence, -x ^ y does not compile "
                    + "(O6) and -x ** y is -(x ** y) with the hint RCH-2091 (EL1-2)",
            List.of("decision:EL1-2", "decision:O6", "decision:PY6", "spec4.17:`a ** b`, `a ^ b`", "case:el/sign-power-1-1#*")),

    CATALOGUE_ARGUMENTS(Action.NONE, "none", "Literal arguments checked against the function catalogue",
            "from 1.1 a literal argument is checked against functions.yaml when the expression compiles (SPEC 4.10 rule 2, G4 P14): a word "
                    + "outside its parameter's enum, a parameter that takes a literal given something else, an engine function's keyword-only "
                    + "parameter given by place. At 1.0 each of these is refused when it runs (the function's own evaluation, or the engine "
                    + "that lowers the measure), so a level-1.0 Sutra that works holds none of them: nothing changes",
            List.of("spec4.10:2", "case:el/catalogue-arguments-1-1#*")),

    RESERVED_NAMES(Action.NONE, "none", "Names a later level reserves",
            "a call of a reserved name that no loaded table has does not compile at 1.0 either (an unknown function); a profile's function of "
                    + "that name keeps working; a field of that name is not held; let names are refused alike at both levels (SPEC 4.15 rule 2)",
            List.of("spec4.17:reserved names", "spec4.15:2")),

    PYTHON_WORDS(Action.NONE, "none", "and, or, not, in, not in, Excel's names",
            "read alike at every level (PY1, PY5, PY8; the form profile's words, A7b)",
            List.of("decision:PY1", "decision:PY5", "decision:PY8", "decision:A7b-1", "decision:A7b-2", "decision:A7b-3", "decision:A7b-4",
                    "case:el/python-words#*")),

    PROBLEM_WORDS(Action.NONE, "none", "Problem words, paths, ranges and the complete list",
            "from 1.1 a problem says expression '<source>': ..., carries path and range, and the list is complete (N5, O4, SPEC 9 items 8 "
                    + "and 9): only a Sutra with problems has any, and a Sutra is upgraded only when it compiles",
            List.of("decision:N5", "decision:O4", "spec9:8", "spec9:9", "known:43 `bad-*`, `expr-error` and limit cases",
                    "case:el/messages-level-1-1#*", "case:problem/examples/problem#problems-in-position-order-l11",
                    "case:problem/examples/problem#profile-key-without-the-profile")),

    ADDITIONS(Action.NONE, "none", "Keys, kinds, options and formats of later levels",
            "additions (SPEC 7.2 rule 1): data (1.1), libraries, let, functions, state (1.2); the kinds grid, tree, chart (1.1), live, treemap, "
                    + "bullet, form (1.2); bars, layers, by and the tree's fmt (1.2); a control's on: and the actions (1.2); pct4, bp1, df4 and "
                    + "price4 promoted from the Drishti profile with the same definitions (1.2): a level-1.0 Sutra holds none of them",
            List.of("decision:O3", "decision:D11", "decision:L-16", "key:data", "key:libraries", "key:let", "key:functions", "key:state",
                    "kind:grid", "kind:tree", "kind:chart", "kind:live", "kind:treemap", "kind:bullet", "kind:form", "option:grid.bars",
                    "option:line.layers", "option:line.by", "option:tree.fmt", "case:problem/levels#*", "case:problem/tree-format-level#*",
                    "case:problem/control-change#*", "case:problem/wiring#*")),

    LEVEL_1_0_RECORDINGS(Action.NONE, "none", "Level 1.0 recorded again before its release",
            "D5-1, D9-4, D15-4, G1-2 and O5 changed level 1.0 itself (before v1.0.0): the corpus was recorded on those answers; not a "
                    + "difference between levels",
            List.of("decision:D5-1", "decision:D9-4", "decision:D15-4", "decision:D15-6", "decision:G1-2", "decision:O5")),

    SAME_AT_EVERY_LEVEL(Action.NONE, "none", "Known differences from the draft that no level changes",
            "SPEC 9 items 1, 2, 3, 5, 6, 7, 10, 11 and 12 describe the reference against the draft text, the canonical and of 1.2 (a text "
                    + "written with and is kept as written) and the last digit of geomean and skewness_pop against Drishti's retired kernel; "
                    + "every level answers alike",
            List.of("spec9:1", "spec9:2", "spec9:3", "spec9:5", "spec9:6", "spec9:7", "spec9:10", "spec9:11", "spec9:12")),

    WITHDRAWN(Action.NONE, "none", "Withdrawn cases",
            "cases withdrawn from level 1.0 (placeholders, a protocol case, the ** messages): no language difference",
            List.of("case:edit/examples/edit#*", "case:protocol/examples/bridge#*",
                    "case:el/messages-level-1-1#expr-error-double-star-is-not-rachana-l11", "case:el/messages-level-1-1#math-bad-quantity-2-l11"));

    /** What the tool does about a difference. */
    public enum Action { REWRITE, FLAG, NONE }

    private final Action action;
    private final String severity;
    private final String title;
    private final String why;
    private final List<String> covers;

    UpgradeRule(Action action, String severity, String title, String why, List<String> covers) {
        this.action = action;
        this.severity = severity;
        this.title = title;
        this.why = why;
        this.covers = covers;
    }

    /** The rule's id in reports: its name in lower case with hyphens ({@code exact}, {@code number-text}). */
    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }

    public Action action() {
        return action;
    }

    /** {@code info}, {@code review} or {@code blocking} (a blocking flag stops the file), {@code none} for a rule that does nothing. */
    public String severity() {
        return severity;
    }

    public String title() {
        return title;
    }

    public String why() {
        return why;
    }

    /** The places in the documents and the suite where this difference is named. */
    public List<String> covers() {
        return covers;
    }
}
