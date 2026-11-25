/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** The one spelling, printed (spec/1/SPEC.md §28 Q4): printing a parsed expression gives its text, parentheses only where needed. */
class PrinterTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "calc(sum(trades.MTM), where=trades.TRADE_STATUS != 'MATURED')",
        "ratio(sum(trades.MTM), sum(trades.NOTIONAL)) * 1000000",
        "payments.AMOUNT * (1 / 148.2 if payments.CURRENCY == 'JPY' else 1.174 if payments.CURRENCY == 'EUR' else null)",
        "$.DESK in ['DESK-COMM', 'DESK-RATES'] and not $.QUANTITY > 0",
        "$.DESK not in []",
        "(a + b) * c - d / (e - f)",
        "a - (b - c)",
        "a or b and c",
        "(a or b) and c",
        "-$.MTM % 7",
        "$.LEGS[?^.DESK == 'X'][*].LEG_ID",
        "^^.TRADE_ID",
        "calc(measure('MTM'), ignore=ALL, via='r_5')",
        "rank(sum(trades.PNL), desc=false, by=[trades.DESK])",
        "let(q=$.QUANTITY, q * 2)",
        "'it\\'s'",
        "'legs' if size($.LEGS) > 0 else 'none'",
    })
    void printsWhatItParses(String text) {
        Expr e = new ElCompiler().compile(text);
        assertThat(Printer.print(e)).isEqualTo(text);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "((a + b))         | a + b",
        "a + (b * c)       | a + b * c",
        "(a == b) == c     | a == b == c",
        "not (a)            | not a",
        "f( 1 ,2 )         | f(1, 2)",
    })
    void dropsParenthesesTheGrammarDoesNotNeed(String in, String out) {
        String text = in.strip().replace("f(", "coalesce(");
        assertThat(Printer.print(new ElCompiler().compile(text))).isEqualTo(out.strip().replace("f(", "coalesce("));
    }
}
