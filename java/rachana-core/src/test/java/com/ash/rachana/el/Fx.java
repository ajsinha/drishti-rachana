/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ash.rachana.api.Node;
import com.ash.rachana.RachanaCode;
import com.ash.rachana.JsonCodec;
import com.ash.rachana.format.Formats;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the function-family tests share: a trade written with the demo data's names (and a few fields made for the edges),
 * masked fields beside it, the view's business date 2026-10-16, and short ways to evaluate and to expect a problem.
 */
final class Fx {

    static final ElCompiler EL = new ElCompiler();
    static final Formats F = Formats.load(null, List.of(com.ash.rachana.TestFormats.file()));
    static final Node DOC;

    static {
        Node plain = new JsonCodec().read("""
                {"TRADE_ID":"MX-20000001","PRODUCT_TYPE":"ComSwap","DESK":"DESK-COMM","TT_BOOK":"BOOK-RATES-3","QUANTITY":242000000,
                 "PRICE":0.040829,"DIRECTION":"SHORT","TRADE_DATE":"2025-11-21","START_DATE":"2025-11-25","MATURITY_DATE":"2032-06-25",
                 "INSTRUMENT":"USD 2026-10 swap","TAGS":"ir,swap,,aud","LEG_ID":"7","STRATEGY":null,"BLANK":"","TEXTNUM":"12.5",
                 "BASIS":"ACT/999","DESKS":["DESK-COMM","DESK-RATES"],
                 "LEGS":[{"LEG_ID":1,"LEG_TYPE":"FIXED","NOTIONAL":242000000,"CURRENCY":"AUD"},
                         {"LEG_ID":2,"LEG_TYPE":"FLOAT","NOTIONAL":242000000,"CURRENCY":"AUD"}],
                 "EVENTS":[{"EVENT_TYPE":"BOOKED","EVENT_DATE":"2025-11-21"},{"EVENT_TYPE":"AMENDED","EVENT_DATE":"2026-03-02"},
                           {"EVENT_TYPE":"CASH_SETTLEMENT","EVENT_DATE":"2026-01-15","AMOUNT":-1500.25}],
                 "NUMS":[3, 1.5, "2", 10],"MIXED":[1, "1", 1.0, "a", "a"],
                 "UNDATED":[{"ID":"a","D":"2026-02-01"},{"ID":"b"},{"ID":"c","D":"2026-01-01"}]}""");
        Map<String, Node> fields = new LinkedHashMap<String, Node>(plain.fields());
        fields.put("MTM", Node.masked());
        fields.put("MASKED_LIST", Node.masked());
        fields.put("PVS", Node.of(List.of(Map.of("PV", 5), Map.of("PV", Node.masked()))));
        DOC = Node.of(fields);
    }

    private Fx() {}

    static EvalContext ctx() {
        return EvalContext.of(DOC, F).withToday(LocalDate.of(2026, 10, 16));
    }

    /** The expression's value, simplified (a list stays a node). */
    static Object eval(String src) {
        return Values.simplify(EL.compile(src).eval(ctx()));
    }

    static boolean masked(String src) {
        return Values.masked(EL.compile(src).eval(ctx()));
    }

    static BigDecimal dec(String text) {
        return new BigDecimal(text);
    }

    /** The elements of a list result, simplified. */
    static List<Object> list(String src) {
        Object v = eval(src);
        assertThat(v).as(src).isInstanceOf(Node.class);
        return ((Node) v).elements().stream().map(Values::simplify).toList();
    }

    /** Evaluating fails with RCH-2102 and words naming the problem. */
    static void evalError(String src, String... words) {
        assertThatThrownBy(() -> eval(src)).as(src).isInstanceOfSatisfying(ElException.class, e -> {
            assertThat(e.code()).isEqualTo(RachanaCode.EL_EVAL);
            for (String w : words) {
                assertThat(e.getMessage()).contains(w);
            }
        });
    }

    /** Compiling fails with RCH-2101 and words naming the problem. */
    static void compileError(String src, String... words) {
        assertThatThrownBy(() -> EL.compile(src)).as(src).isInstanceOfSatisfying(ElException.class, e -> {
            assertThat(e.code()).isEqualTo(RachanaCode.EL_SYNTAX);
            for (String w : words) {
                assertThat(e.getMessage()).contains(w);
            }
        });
    }
}
