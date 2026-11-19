/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import static org.assertj.core.api.Assertions.assertThat;

import com.ash.rachana.api.Node;
import com.ash.rachana.format.Formats;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A masked field ({@link Node#MASK}) stands for the whole field: what lies under it reads masked, anything computed
 * from it is masked, and a condition on it is never true, so it can be neither seen nor probed.
 */
class MaskTest {

    static final ElCompiler EL = new ElCompiler();
    static final Formats F = Formats.load(null, List.of(com.ash.rachana.TestFormats.file()));
    static final Node DOC;

    static {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", "T-1");
        doc.put("notional", 100);
        doc.put("rows", List.of(Map.of("pv", 1), Map.of("pv", 2)));
        Map<String, Node> fields = new LinkedHashMap<String, Node>(Node.of(doc).fields());
        fields.put("mtm", Node.masked());
        fields.put("counterparty", Node.masked());
        fields.put("legs", Node.of(List.of(Map.of("pv", 5), Map.of("pv", Node.masked()))));
        DOC = Node.of(fields);
    }

    private static Object eval(String src) {
        return Values.simplify(EL.compile(src).eval(EvalContext.of(DOC, F)));
    }

    @Test
    void whatLiesUnderAMaskIsMasked() {
        assertThat(eval("$.counterparty.name")).isEqualTo(Node.MASK);
        assertThat(eval("$.counterparty['name']")).isEqualTo(Node.MASK);
        assertThat(DOC.at("counterparty.address.city").isMasked()).isTrue();
        assertThat(DOC.at("notional.x").isMissing()).isTrue();          // under a plain value: missing, as before
    }

    @Test
    void valuesDerivedFromAMaskAreMasked() {
        assertThat(eval("$.mtm * 2")).isEqualTo(Node.MASK);
        assertThat(eval("-$.mtm")).isEqualTo(Node.MASK);
        assertThat(eval("abs($.mtm)")).isEqualTo(Node.MASK);
        assertThat(eval("fmt($.mtm, 'signed0')")).isEqualTo(Node.MASK);
        assertThat(eval("link($.counterparty.id, 'counterparty', $.counterparty.name)")).isEqualTo(Node.MASK);
        assertThat(((Number) eval("coalesce($.missing, $.notional, $.mtm)")).longValue()).isEqualTo(100L);
        assertThat(eval("$.mtm > 0 ? 'gain' : 'loss'")).isEqualTo(Node.MASK);
        assertThat(EL.template("MTM ${fmt($.mtm, 'signed0')}").render(EvalContext.of(DOC, F))).isEqualTo("MTM " + Node.MASK);
        assertThat(F.format("signed0", Node.masked())).isEqualTo(Node.MASK);
        assertThat(F.format("date", Node.MASK)).isEqualTo(Node.MASK);
    }

    @Test
    void aConditionOnAMaskIsNeverTrue() {
        for (String c : List.of("$.mtm > 0", "$.mtm <= 0", "!($.mtm > 0)", "$.mtm == '" + Node.MASK + "'", "$.counterparty.name != 'x'",
                "contains($.counterparty.name, 'Mer')", "$.mtm > 0 && $.notional > 0", "$.mtm > 0 || $.notional < 0")) {
            assertThat(Values.truthy(EL.compile(c).eval(EvalContext.of(DOC, F)))).as(c).isFalse();
        }
        assertThat(Values.truthy(eval("$.mtm > 0 || $.notional > 0"))).isTrue();   // true for a reason that is not the mask
    }
}
