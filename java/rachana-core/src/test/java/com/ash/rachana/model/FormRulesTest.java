/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Split step A7b: a form's expressions are Rachana-EL read with the {@code form} profile, and every answer is the one the form
 * language gave before (FormExprBeforeA7b, kept here): the same value over many expressions and many drafts, and the same names.
 */
class FormRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 21);

    private static final List<String> EXPRESSIONS = List.of(
            "notional <= 30_000_000", "notional * 0.01", "notional / 3", "notional / 0", "-notional + 1.50", "rate * 100 / 7",
            "notional > counterparty.LIMIT", "counterparty.LIMIT - notional", "maturity > today", "maturity >= trade_date",
            "side == 'Buy' and notional > 1000", "side == 'Buy' or notional > 1000", "not (side == 'Sell')", "not side == 'Sell'",
            "not empty(counterparty)", "empty(note) or len(note) <= 20", "len(note)", "abs(pnl) < 5000", "min(notional, 1e6)",
            "max(rate, 0.5) * 2", "book + '-' + side", "note + 1", "notional + note", "rate == 0.05", "rate != null", "note == null",
            "note != ''", "notional == '25,000,000'", "true and missing > 3", "false or missing > 3", "missing > 3 and false",
            "missing > 3 or true", "not missing", "(notional + 1) * (rate - 0.01)", "text_1 == 'x' and leg_2_1 > 0",
            "\"double\" + side", "1_000 + 2_000.5", "counterparty.NAME + ' (' + book + ')'", "notional >= 10 and notional <= 1e9 and side != 'Hold'");

    @Test
    void everyAnswerIsTheOneTheFormLanguageGave() {
        Random r = new Random(7);
        int compared = 0;
        for (String text : EXPRESSIONS) {
            FormExpr now = FormExpr.parse(text);
            FormExprBeforeA7b then = FormExprBeforeA7b.parse(text);
            assertThat(now.names()).as(text).containsExactlyElementsOf(then.names());
            for (int i = 0; i < 200; i++) {
                Map<String, Object> draft = draft(r);
                Object a = now.eval(draft, TODAY);
                Object b = then.eval(draft, TODAY);
                assertThat(String.valueOf(a)).as(text + " over " + draft).isEqualTo(String.valueOf(b));
                assertThat(a == null ? null : a.getClass()).as(text).isEqualTo(b == null ? null : b.getClass());
                assertThat(now.holds(draft, TODAY)).isEqualTo(then.holds(draft, TODAY));
                compared++;
            }
        }
        assertThat(compared).isEqualTo(EXPRESSIONS.size() * 200);
    }

    @Test
    void whatAFormDoesNotReadIsRefusedInWords() {
        assertThatThrownBy(() -> FormExpr.parse("upper(side) == 'BUY'")).hasMessageContaining("'upper' is not a function here");
        assertThatThrownBy(() -> FormExpr.parse("min(a)")).hasMessage("min() takes 2 values");
        assertThatThrownBy(() -> FormExpr.parse("a.b.c > 1")).hasMessageContaining("'a.b.c' is a field");
        assertThatThrownBy(() -> FormExpr.parse("a % 2")).hasMessageContaining("'%' is not part of the form expressions");
        assertThatThrownBy(() -> FormExpr.parse("a > 1 ? 1 : 2")).hasMessageContaining("a part of Rachana-EL a form does not read");
        assertThatThrownBy(() -> FormExpr.parse(" ")).hasMessage("an expression is empty");
    }

    @Test
    void andOrAndNotAreRachanaElsOwnOperators() {
        assertThat(FormExpr.parse("a > 1 and b > 1").eval(Map.of("a", 2, "b", 2), TODAY)).isEqualTo(true);
        assertThat(FormExpr.parse("a > 1 && b > 1").eval(Map.of("a", 2, "b", 0), TODAY)).isEqualTo(false);
        assertThat(FormExpr.parse("not a > 1 or b > 1").eval(Map.of("a", 2, "b", 2), TODAY)).isEqualTo(true);
        assertThat(FormExpr.parse("notional / 3").eval(Map.of("notional", 10), TODAY)).isEqualTo(new BigDecimal("3.333333333333333"));
    }

    private static Map<String, Object> draft(Random r) {
        Map<String, Object> d = new HashMap<>();
        put(d, r, "notional", pick(r, 25_000_000, 1000.5, new BigDecimal("31000000"), "25,000,000", "", null, 0, -7));
        put(d, r, "rate", pick(r, 0.05, new BigDecimal("0.050"), 2, "x", null, ""));
        put(d, r, "pnl", pick(r, -6000, 4999.99, null, "abc"));
        put(d, r, "side", pick(r, "Buy", "Sell", "Hold", "", null));
        put(d, r, "book", pick(r, "B1", "", null, 12));
        put(d, r, "note", pick(r, "short", "a note longer than twenty chars", "", null, List.of(), List.of("a")));
        put(d, r, "maturity", pick(r, "2027-01-01", "2026-11-21", "2025-12-31", "", null));
        put(d, r, "trade_date", pick(r, "2026-11-20", null));
        put(d, r, "text_1", pick(r, "x", "y", null));
        put(d, r, "leg_2_1", pick(r, 1, 0, null));
        Map<String, Object> cp = new LinkedHashMap<>();
        put(cp, r, "LIMIT", pick(r, 30_000_000, "20,000,000", null, ""));
        put(cp, r, "NAME", pick(r, "Acme", null));
        Object counterparty = pick(r, cp, cp, "", null);
        if (counterparty != null) {
            d.put("counterparty", counterparty);
        }
        return d;
    }

    private static void put(Map<String, Object> m, Random r, String k, Object v) {
        if (v != null || r.nextBoolean()) {
            m.put(k, v);
        }
    }

    private static Object pick(Random r, Object... xs) {
        List<Object> l = new ArrayList<>(List.of());
        for (Object x : xs) {
            l.add(x);
        }
        return l.get(r.nextInt(l.size()));
    }
}
