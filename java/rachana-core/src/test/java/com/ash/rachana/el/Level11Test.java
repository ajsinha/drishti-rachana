/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ash.rachana.el.check.ElType;
import com.ash.rachana.el.check.Fields;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Language level 1.1, the syntax batch (EL_EXPANSION X2, X6, X7, X11 to X14; DECISIONS PY1 to PY8): the printer's round trip, the
 * checker's types, the reserved names against {@code spec/1/reserved.yaml}, and level 1.0 left as it was. The behaviour itself is pinned
 * by the conformance suites {@code el/syntax-1-1}, {@code el/syntax-at-1-0}, {@code el/numbers-exact} and {@code el/text-code-points}.
 */
class Level11Test {

    private static final ElCompiler V11 = new ElCompiler(1000, ElLimits.DEFAULTS, FunctionTable.core(), "1.1");

    @ParameterizedTest
    @ValueSource(strings = {
        "{a: 1, 'day-count': 'ACT/360', ...@}",
        "[...$.a, ...$.b, 3]",
        "{}",
        "$.legs[1:3]",
        "$.legs[:2]",
        "$.legs[::-1]",
        "'abc'[1:]",
        "range(2, 10, 2)",
        "map($.legs, lambda l: l.n * 2)",
        "reduce($.legs, 0, lambda acc, l: max(acc, l.n))",
        "let(bp = lambda x: x * 10000, bp($.spread))",
        "let(f = lambda x: x, map($.l, f))",
        "$.a ?? $.b ?? 0",
        "($.a ?? 1) + 2",
        "2 ** 3 ** 2",
        "(2 ** 3) ** 2",
        "-2 ** 2",
        "(-2) ** 2",
        "0.1 + 0.2",
        "1e-3 * 2",
        "1.5e3",
        "1000000",
        "c ? a : b",
        "$.x in range(5) && $.y not in [1, 2]",
    })
    void printsWhatItParsesAtLevel11(String text) {
        Expr e = V11.compile(text);
        assertThat(Printer.print(e, true)).isEqualTo(text);
        assertThat(V11.compile(Printer.print(e, true))).isEqualTo(e);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "$.a |> round(2)",
        "'big' if $.x > 5 else 'small'",
        "2 ^ 3",
        "1_000 /* a thousand */ + 1",
        "{a: 1,}",
        "x if a ?? b else y",
    })
    void sugarPrintsInTheOneSpellingAndParsesToTheSameTree(String text) {
        Expr e = V11.compile(text);
        assertThat(V11.compile(Printer.print(e, true))).isEqualTo(e);
    }

    @Test
    void thePipeIsTheNestedCall() {
        assertThat(V11.compile("$.a |> round(2)")).isEqualTo(V11.compile("round($.a, 2)"));
        assertThat(V11.compile("$.l |> sort() |> take(3)")).isEqualTo(V11.compile("take(sort($.l), 3)"));
    }

    @Test
    void theCheckerTypesTheNewOperators() {
        assertThat(type("1 + 2")).isEqualTo(ElType.INTEGER);
        assertThat(type("1.5 * 2")).isEqualTo(ElType.DECIMAL);
        assertThat(type("1e3 + 1")).isEqualTo(ElType.NUMBER);
        assertThat(type("2 ** 3")).isEqualTo(ElType.INTEGER);
        assertThat(type("1 ?? 2")).isEqualTo(ElType.INTEGER);
        assertThat(type("1 < 2")).isEqualTo(ElType.BOOLEAN);
        assertThat(CheckedMode.check("'a' ?? 1", null, Fields.NONE, "1.1").problems()).isNotEmpty();
        assertThat(CheckedMode.check("{a: 1}", null, Fields.NONE, "1.1").problems()).isNotEmpty();
        assertThat(CheckedMode.check("1e3", null, Fields.NONE, "1.0").type()).isEqualTo(ElType.DECIMAL);
    }

    private static ElType type(String text) {
        return CheckedMode.check(text, null, Fields.NONE, "1.1").type();
    }

    @Test
    void levelOneZeroIsUnchanged() {
        ElCompiler v10 = new ElCompiler();
        assertThat(Values.text(v10.compile("0.1 + 0.2").eval(EvalContext.of(com.ash.rachana.api.Node.missing(), null))))
                .isEqualTo("0.30000000000000004");
        assertThat(Values.text(V11.compile("0.1 + 0.2").eval(EvalContext.of(com.ash.rachana.api.Node.missing(), null)))).isEqualTo("0.3");
        assertThatThrownBy(() -> v10.compile("{a: 1}")).hasMessageContaining("unexpected character '{' at 0");
        assertThatThrownBy(() -> v10.compile("2 ** 3")).hasMessageContaining("'**' is not Rachana");
        assertThat(Printer.print(v10.compile("1e-3"))).isEqualTo("0.001");
    }

    /** EL1-1: at 1.1 range is only the sequence and the aggregate is span; at 1.0 range is the aggregate and span is not a name. */
    @Test
    void rangeIsTheSequenceAndSpanTheAggregateAtLevel11() {
        ElCompiler v10 = new ElCompiler();
        EvalContext none = EvalContext.of(com.ash.rachana.api.Node.missing(), null);
        assertThat(Values.text(v10.compile("range([3, 9, 4])").eval(none))).isEqualTo("6");
        assertThat(Values.text(V11.compile("span([3, 9, 4])").eval(none))).isEqualTo("6");
        assertThat(Values.text(V11.compile("range(3)[2] + size(range(3))").eval(none))).isEqualTo("5");
        assertThatThrownBy(() -> v10.compile("span([1])")).hasMessageContaining("unknown function 'span'");
        assertThatThrownBy(() -> V11.compile("range([3, 9, 4])")).hasMessageContaining("write span(list, field?)");
        assertThatThrownBy(() -> V11.compile("range($.t, 'PRICE')")).hasMessageContaining("write span(list, field?)");
        com.ash.rachana.api.Node rec = com.ash.rachana.api.Node.of(java.util.Map.of("l", java.util.List.of(1, 2)));
        assertThatThrownBy(() -> V11.compile("range($.l)").eval(EvalContext.of(rec, null))).hasMessageContaining("write span(list, field?)");
    }

    /** EL1-2: {@code -x ** n} and {@code -x ^ n} without parentheses are a warning at 1.1; parentheses on either side silence it. */
    @Test
    void aSignBeforeAPowerIsAHint() {
        var p = CheckedMode.check("-2 ** 2", null, Fields.NONE, "1.1").problems();
        assertThat(p).hasSize(1);
        assertThat(p.get(0).code()).isEqualTo("RCH-2091");
        assertThat(p.get(0).error()).isFalse();
        assertThat(p.get(0).message()).isEqualTo("'-2 ** 2' is -(2 ** 2): a power binds tighter than the sign before it (Python's rule; "
                + "Excel reads (-x) ^ n)");
        assertThat(p.get(0).fix()).isEqualTo("write -(2 ** 2) to keep this meaning, or (-2) ** 2 for the power of the negative");
        assertThat(CheckedMode.check("1 + -$.x ^ 2", null, Fields.NONE, "1.1").problems())
                .extracting(com.ash.rachana.el.check.Problem::code).containsExactly("RCH-2091");
        assertThat(CheckedMode.check("-(2 ** 2)", null, Fields.NONE, "1.1").problems()).isEmpty();
        assertThat(CheckedMode.check("(-2) ** 2", null, Fields.NONE, "1.1").problems()).isEmpty();
        assertThat(CheckedMode.check("-2 * 2", null, Fields.NONE, "1.1").problems()).isEmpty();
    }

    @Test
    void theReservedNamesAreTheFilesLessRange() throws Exception {
        String yaml = Files.readString(Path.of("../../spec/1/reserved.yaml"));
        String names = yaml.substring(yaml.indexOf("\nnames:"), yaml.indexOf("\npromoted:") < 0 ? yaml.length() : yaml.indexOf("\npromoted:"));
        Set<String> listed = new TreeSet<>();
        Matcher m = Pattern.compile("functions: \\[([^\\]]*)\\]").matcher(names);
        while (m.find()) {
            for (String n : m.group(1).split(",")) {
                if (!n.isBlank()) {
                    listed.add(n.strip());
                }
            }
        }
        listed.remove("range");
        assertThat(new TreeSet<>(ReservedNames.NAMES)).isEqualTo(listed);
    }
}
