/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.table;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ash.rachana.api.Node;
import com.ash.rachana.el.ElCompiler;
import com.ash.rachana.el.EvalContext;
import com.ash.rachana.el.Values;
import com.ash.rachana.parse.Json;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Keyed patches, the filter tree in both directions, schema decoding (the Core half of the Data Protocol). */
class TablesTest {

    private static final ElCompiler EL = new ElCompiler();

    @SuppressWarnings("unchecked")
    private static Map<String, Object> json(String s) {
        return (Map<String, Object>) Json.read(s);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(String s) {
        return (List<Map<String, Object>>) Json.read(s);
    }

    @Test
    void patchesApplyInTheOrderOfSection81() {
        KeyedRows t = new KeyedRows(List.of("k"), rows("[{\"k\":\"a\",\"v\":1},{\"k\":\"b\",\"v\":2}]"), 0);
        assertThat(t.apply(json("{\"remove\":[[\"a\"]],\"add\":[{\"k\":\"a\",\"v\":9}],\"update\":[{\"k\":\"b\",\"v\":3}]}"))).isTrue();
        assertThat(Json.write(t.rows())).isEqualTo("[{\"k\":\"b\",\"v\":3},{\"k\":\"a\",\"v\":9}]");
        assertThat(t.apply(json("{\"update\":[{\"k\":\"b\",\"v\":3}]}"))).isFalse();
        assertThat(t.apply(json("{\"remove\":[[\"zz\"]]}"))).isFalse();
    }

    @Test
    void keysCompareAsJsonText() {
        KeyedRows t = new KeyedRows(List.of("d"), rows("[{\"d\":\"1.50\"}]"), 0);
        t.apply(json("{\"remove\":[[\"1.5\"]]}"));
        assertThat(t.rows()).hasSize(1);
        t.apply(json("{\"remove\":[\"1.50\"]}"));
        assertThat(t.rows()).isEmpty();
    }

    @Test
    void anAppendingTableKeepsItsWindowAndATableWithoutKeyRefusesUpdates() {
        KeyedRows t = new KeyedRows(List.of(), List.of(), 2);
        t.apply(json("{\"add\":[{\"v\":1},{\"v\":2},{\"v\":3}]}"));
        assertThat(Json.write(t.rows())).isEqualTo("[{\"v\":2},{\"v\":3}]");
        assertThatThrownBy(() -> t.apply(json("{\"update\":[{\"v\":1}]}"))).hasMessageContaining("rdp.table.no-key");
    }

    @Test
    void aConditionBecomesATreeWithControlsReplaced() {
        EvalContext ctx = EvalContext.of(Node.of(Map.of()), null).withControls(Map.of("region", "North"));
        Map<String, Object> t = FilterTree.of(EL.compile("@.region == ctl.region && -@.v < 3 + 1"), ctx);
        assertThat(Json.write(t)).isEqualTo("{\"op\":\"&&\",\"args\":[{\"op\":\"==\",\"args\":[{\"col\":\"region\"},{\"lit\":\"North\"}]},"
                + "{\"op\":\"<\",\"args\":[{\"op\":\"neg\",\"args\":[{\"col\":\"v\"}]},{\"lit\":4}]}]}");
        assertThat(FilterTree.of(EL.compile("matches(@.a, 'x')"), ctx)).isNull();
        assertThat(FilterTree.of(EL.compile("@.a == $.b"), ctx)).isNull();
    }

    @Test
    void aTreeEvaluatesAsTheRachanaElItSpells() {
        TableSchema s = TableSchema.of(json("{\"name\":\"t\",\"columns\":[{\"name\":\"a\",\"type\":\"string\"},{\"name\":\"n\",\"type\":\"string\"}]}"));
        String tree = "{\"op\":\"&&\",\"args\":[{\"op\":\"!=\",\"args\":[{\"col\":\"n\"},{\"lit\":\"X\"}]},"
                + "{\"op\":\"not-in\",\"args\":[{\"col\":\"a\"},{\"list\":[{\"lit\":\"q\"}]}]}]}";
        assertThat(FilterTree.check(Json.read(tree), s, 32)).containsExactly("n", "a");
        String el = FilterTree.toEl(Json.read(tree));
        List<String> kept = new ArrayList<>();
        for (Map<String, Object> r : rows("[{\"a\":\"p\",\"n\":null},{\"a\":\"q\",\"n\":\"Y\"},{\"a\":\"r\",\"n\":\"X\"}]")) {
            if (Values.truthy(EL.compile(el).eval(EvalContext.of(Node.of(Map.of()), null).withRow(Node.of(r), 0)))) {
                kept.add((String) r.get("a"));
            }
        }
        assertThat(kept).containsExactly("p");                    // empty != 'X' is true (SPEC 4.6)
    }

    @Test
    void aBadTreeIsRefusedWithTheProtocolCode() {
        TableSchema s = TableSchema.of(json("{\"name\":\"t\",\"columns\":[{\"name\":\"revenue\",\"type\":\"decimal\"}]}"));
        assertThatThrownBy(() -> FilterTree.check(Json.read("{\"op\":\"matches\",\"args\":[{\"col\":\"revenue\"},{\"lit\":\"x\"}]}"), s, 32))
                .isInstanceOfSatisfying(FilterTree.Refused.class, r -> assertThat(r.code()).isEqualTo("rdp.query.invalid"));
        assertThatThrownBy(() -> FilterTree.check(Json.read("{\"op\":\"==\",\"args\":[{\"col\":\"reveune\"},{\"lit\":1}]}"), s, 32))
                .isInstanceOfSatisfying(FilterTree.Refused.class, r -> {
                    assertThat(r.code()).isEqualTo("rdp.column.unknown");
                    assertThat(r.fields().get("expected")).isEqualTo(List.of("revenue"));
                });
        assertThatThrownBy(() -> FilterTree.check(Json.read("{\"op\":\"==\",\"args\":[{\"col\":\"revenue\"}]}"), s, 32))
                .isInstanceOf(FilterTree.Refused.class);
    }

    @Test
    void cellsDecodeByType() {
        assertThat(TableSchema.decode("decimal", "0.10").toString()).isEqualTo("0.10");
        assertThat(TableSchema.decode("integer", "9007199254740993")).isEqualTo(9007199254740993L);
        assertThat(Values.masked(TableSchema.decode("string", Map.of("$masked", true)))).isTrue();
    }

    @Test
    void aPlanIsCheckedAgainstTheSchemasFromLevel12() {
        com.ash.rachana.Rachana r = com.ash.rachana.Rachana.with(com.ash.rachana.RachanaOptions.DEFAULTS).language("1.2");
        com.ash.rachana.model.Sutra sutra = r.parser().parse("""
                rachana: 1.2
                sutra: checked
                version: 1
                data:
                  sales: { table: sales, where: "@.units + 'x' > 3 && @.salary > 1" }
                panels:
                  - { id: n, kind: metric, label: N, value: "count($.sales)" }
                """, "checked.sutra.yaml", "test");
        Map<String, TableSchema> schemas = Map.of("sales", TableSchema.of(json("{\"name\":\"sales\",\"key\":[\"id\"],\"can\":[\"filter\"],"
                + "\"columns\":[{\"name\":\"id\",\"type\":\"string\"},{\"name\":\"units\",\"type\":\"integer\"},"
                + "{\"name\":\"salary\",\"type\":\"decimal\",\"masked\":true}]}")));
        EvalContext ctx = EvalContext.of(Node.of(Map.of()), null);
        List<?> at12 = (List<?>) DataPlan.plan(sutra, schemas, r.compiler(), ctx, "1.2").get("problems");
        assertThat(at12.stream().map(x -> String.valueOf(((Map<?, ?>) x).get("code"))).sorted().toList()).containsExactly("RCH-9127", "RCH-9161");
        assertThat((List<?>) DataPlan.plan(sutra, schemas, r.compiler(), ctx, "1.1").get("problems")).isEmpty();
        assertThat((List<?>) DataPlan.plan(sutra, schemas, r.compiler(), ctx).get("problems")).isEmpty();
    }
}
