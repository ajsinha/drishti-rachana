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
