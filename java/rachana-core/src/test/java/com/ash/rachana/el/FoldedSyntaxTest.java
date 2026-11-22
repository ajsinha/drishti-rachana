/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import static com.ash.rachana.el.Fx.EL;
import static com.ash.rachana.el.Fx.compileError;
import static com.ash.rachana.el.Fx.ctx;
import static com.ash.rachana.el.Fx.eval;
import static com.ash.rachana.el.Fx.evalError;
import static com.ash.rachana.el.Fx.list;
import static com.ash.rachana.el.Fx.masked;
import static org.assertj.core.api.Assertions.assertThat;

import com.ash.rachana.api.Node;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The syntax Rachana gains from DEL (docs/architecture/ARCHITECTURE.md §28 2.1 and 2.4): list literals, strict {@code in} and {@code not in}, the
 * parent path {@code ^}, named arguments, {@code power} and {@code floor} for {@code **} and {@code //}, {@code today()} read as
 * {@code asof()}, and the null rule (Q1: {@code null != 'X'} is true; arithmetic and the text functions null in, null out). Each case
 * normal, null, masked, and wrong.
 */
class FoldedSyntaxTest {

    @Test
    void inIsStrictAndEmptyHoldsNothing() {
        assertThat(eval("$.DESK in ['DESK-COMM', 'DESK-RATES']")).isEqualTo(true);
        assertThat(eval("$.DESK not in ['DESK-COMM']")).isEqualTo(false);
        assertThat(eval("$.DESK in $.DESKS")).isEqualTo(true);
        assertThat(eval("$.DESK in []")).isEqualTo(false);                         // strict: an empty list holds nothing (Q3)
        assertThat(eval("$.DESK not in []")).isEqualTo(true);
        assertThat(eval("$.DESK in $.NOTHING")).isEqualTo(false);                  // nothing at all holds nothing
        assertThat(eval("one_of($.DESK, [])")).isEqualTo(true);                    // one_of stays a control's: an empty choice holds everything
        assertThat(eval("$.STRATEGY in [null, 'X']")).isEqualTo(true);            // null equals only null (Q1)
        assertThat(eval("$.STRATEGY in ['X']")).isEqualTo(false);
        assertThat(eval("$.STRATEGY not in ['X']")).isEqualTo(true);
        assertThat(eval("$.QUANTITY in [242000000]")).isEqualTo(true);
        assertThat(eval("$.DESK in 'DESK-COMM'")).isEqualTo(true);                 // a value that is not a list is a list of one
        assertThat(masked("$.MTM in [1, 2]")).isTrue();
        assertThat(masked("$.DESK in $.MASKED_LIST")).isTrue();
        assertThat(eval("($.DESK in ['DESK-COMM']) && $.QUANTITY > 0")).isEqualTo(true);
    }

    @Test
    void theParentPathReadsTheElementAbove() {
        assertThat(list("$.LEGS[?^.DESK == 'DESK-COMM'][*].LEG_ID")).containsExactly(1L, 2L);
        assertThat(list("$.LEGS[?@.LEG_ID == ^.LEG_ID][*].LEG_TYPE")).isEmpty();    // ^.LEG_ID is the trade's ('7')
        assertThat(eval("map($.LEGS, ^.TRADE_ID)")).isInstanceOf(Node.class);
        assertThat(list("map($.LEGS, ^.TRADE_ID)")).containsExactly("MX-20000001", "MX-20000001");
        EvalContext deep = ctx().withRows(List.of(Node.of(java.util.Map.of("LEG_ID", 9)), Node.of(java.util.Map.of("AMOUNT", 5))));
        assertThat(Values.simplify(EL.compile("^.LEG_ID").eval(deep))).isEqualTo(9);
        assertThat(Values.simplify(EL.compile("^^.TRADE_ID").eval(deep))).isEqualTo("MX-20000001");
        assertThat(Values.simplify(EL.compile("@.AMOUNT").eval(deep))).isEqualTo(5);
        assertThat(eval("^^^.X")).isNull();                                          // no such parent: nothing
        assertThat(eval("^.TRADE_ID")).isNull();                                     // the document itself has no parent
    }

    @Test
    void todayIsReadAsAsOf() {
        assertThat(eval("asof()")).isEqualTo("2026-10-16");
        assertThat(eval("today()")).isEqualTo("2026-10-16");
        assertThat(Functions.oneName("today")).isEqualTo("asof");
        assertThat(Values.simplify(EL.compile("asof()").eval(EvalContext.of(Fx.DOC, Fx.F)))).isNull();
    }

    @Test
    void theNullRule() {
        assertThat(eval("$.STRATEGY != 'X'")).isEqualTo(true);                   // Q1: Rachana's rule
        assertThat(eval("$.STRATEGY == 'X'")).isEqualTo(false);
        assertThat(eval("$.STRATEGY == null")).isEqualTo(true);
        assertThat(eval("$.STRATEGY + 1")).isNull();                              // arithmetic on nothing is nothing
        assertThat(eval("1 - $.STRATEGY")).isNull();
        assertThat(eval("$.STRATEGY * 2")).isNull();
        assertThat(eval("-$.STRATEGY")).isNull();
        assertThat(eval("'x' + $.STRATEGY")).isEqualTo("x");                      // '+' still joins a text with nothing
        assertThat(eval("upper($.STRATEGY)")).isNull();                          // null in, null out
        assertThat(eval("lower($.NOTHING)")).isNull();
        assertThat(eval("len($.STRATEGY)")).isNull();
        assertThat(eval("year($.STRATEGY)")).isNull();
        assertThat(eval("$.STRATEGY < 1")).isEqualTo(false);
    }

    @Test
    void theDateParts() {
        assertThat(eval("year($.TRADE_DATE)")).isEqualTo(2025L);
        assertThat(eval("quarter($.TRADE_DATE)")).isEqualTo(4L);
        assertThat(eval("month($.TRADE_DATE)")).isEqualTo(11L);
        assertThat(eval("week('2026-01-01')")).isEqualTo(1L);                    // ISO week
        assertThat(eval("week('2027-01-01')")).isEqualTo(53L);
        assertThat(eval("day($.TRADE_DATE)")).isEqualTo(21L);
        assertThat(eval("date('2026-10-09')")).isEqualTo("2026-10-09");
        assertThat(eval("timestamp('2026-10-09 09:30')")).isEqualTo("2026-10-09T09:30");
        assertThat(eval("len('ab€')")).isEqualTo(3L);
        assertThat(eval("round(-2.5)")).isEqualTo(-3L);                          // a half away from zero
        assertThat(eval("business_days_between('2026-10-19', '2026-10-16')")).isEqualTo(-1L);
        evalError("year('soon')", "year", "is not a date");
        compileError("shift_date('2026-10-16', 1, 'fortnight')", "unit is one of");
        compileError("shift_date('2026-10-16', 1, unit = 'fortnight')", "unit is one of");
        assertThat(masked("year($.MTM)")).isTrue();
    }
}
