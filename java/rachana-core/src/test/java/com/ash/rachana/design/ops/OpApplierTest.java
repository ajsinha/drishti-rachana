/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.design.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ash.rachana.model.Panel;
import com.ash.rachana.model.PanelKind;
import com.ash.rachana.model.Sutra;
import com.ash.rachana.parse.SutraParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Every operation on every panel kind, over the all-panels showcase and the ten other examples. */
class OpApplierTest {

    private final OpApplier applier = new com.ash.rachana.design.ops.OpApplier(com.ash.rachana.TestGrammar.parser());
    private final SutraParser parser = com.ash.rachana.TestGrammar.parser();

    /** The example with a comment above every panel and one inside it, so loss of comments shows. */
    private static String annotated(String text) {
        List<String> out = new ArrayList<>();
        boolean inPanels = false;
        int n = 0;
        for (String line : text.split("\n", -1)) {
            if (line.startsWith("panels:")) {
                inPanels = true;
            } else if (!line.isEmpty() && !line.startsWith(" ") && !line.startsWith("#") && !line.startsWith("-")) {
                inPanels = false;
            }
            if (inPanels && line.startsWith("  - ")) {
                out.add("  # note " + (n++));
            }
            out.add(line);
        }
        return String.join("\n", out);
    }

    private static List<String> comments(String text) {
        return text.lines().filter(l -> l.strip().startsWith("#")).map(String::strip).toList();
    }

    private Sutra parse(String text) {
        return parser.parse(text, SutraParser.STUDIO, "test");
    }

    private static String shape(Panel p) {
        return p.id() + "|" + p.kind() + "|" + p.title() + "|" + p.options() + "|" + p.columns();
    }

    private static Map<String, Object> required(PanelKind k) {
        Map<String, Object> o = new LinkedHashMap<>();
        for (String r : k.required()) {
            o.put(r, "text".equals(r) ? "hello" : "rows".equals(r) || "each".equals(r) || "nodes".equals(r) || "value".equals(r) ? "$.items" : "a");
        }
        if (k == PanelKind.TABS) {
            o.put("body", Map.of("kind", "kv", "columns", List.of(Map.of("label", "A", "bind", "@.a"))));
        }
        return o;
    }

    private void same(String before, OpResult r, String touched) {
        assertThat(r.problems()).isEmpty();
        Sutra a = parse(before);
        Sutra b = parse(r.yaml());
        for (Panel p : a.panels()) {
            if (!p.id().equals(touched)) {
                assertThat(b.panels().stream().filter(x -> x.id().equals(p.id())).map(OpApplierTest::shape).findFirst())
                        .as("panel %s is untouched", p.id()).contains(shape(p));
            }
        }
        assertThat(b.strip()).isEqualTo(a.strip());
        assertThat(b.keys()).isEqualTo(a.keys());
        assertThat(b.title()).isEqualTo(a.title());
    }

    @Test
    void setStripKeepsTheCommentsInsideTheStripBlock() {
        String text = "rachana: 1\nsutra: strip-keep\nversion: 1\nmatch: { kind: k }\ntitle: { id: $.id }\nstrip:\n  # figures the desk reads first\n"
                + "  - { label: A, bind: $.a }\n  # b is shown for the audit\n  - { label: B, bind: $.b }\npanels:\n  - id: t\n    kind: kv\n    columns:\n      - { label: A, bind: $.a }\n";
        OpResult r = applier.apply(text, List.of(new SetStrip(List.of(Map.of("label", "C", "bind", "$.c")))));
        assertThat(r.problems()).isEmpty();
        assertThat(r.yaml()).contains("# figures the desk reads first").contains("# b is shown for the audit").contains("label: C");
        assertThat(parse(r.yaml()).strip()).hasSize(1);
    }

    @Test
    void bindsPathsInTheRolesAKindTakes() {
        String text = String.join("\n", "rachana: 1", "sutra: bind-test", "version: 1", "match: { kind: trade }", "title: { pill: T, id: $.id }", "panels:",
                "  # the legs", "  - id: legs", "    kind: table", "    rows: $.legs", "    columns:", "      - { label: A, bind: \"@.a\" }",
                "  - { id: kvs, kind: kv }", "  - { id: g, kind: pivot, rows: $.legs, by: x, across: y }", "  - { id: st, kind: status }", "");
        OpResult r = applier.apply(text, List.of(new Bind("legs", "notional", null), new Bind("kvs", "tradeId", null), new Bind("st", "state", null), new Bind("g", "book", "by"),
                new Bind("g", "desk", "by"), new Bind("g", "ccy", "across"), new Bind("g", "pv", "value")));
        assertThat(r.problems()).isEmpty();
        Sutra s = parse(r.yaml());
        assertThat(s.panels().get(0).columns()).extracting(c -> c.bind()).containsExactly("@.a", "@.notional");
        assertThat(s.panels().get(1).columns()).extracting(c -> c.bind()).containsExactly("$.tradeId");      // a kv reads columns, not fields
        assertThat(s.panels().get(1).options()).doesNotContainKey("fields");
        assertThat(s.panels().get(3).options().get("fields")).isNotNull();                                      // a status reads fields
        assertThat(s.panels().get(2).options()).containsEntry("by", List.of("x", "book", "desk")).containsEntry("across", "ccy").containsEntry("value", "pv");
        assertThat(r.yaml()).contains("# the legs");
        assertThat(Bind.roles(PanelKind.TABLE)).contains("rows", "column");
        assertThat(Bind.roles(PanelKind.LINKS)).isEmpty();
    }

    @Test
    void operationsReadAndWriteJson() {
        String json = """
                [{"op":"addPanel","kind":"table","at":{"area":"right","after":"legs","span":6},"options":{"rows":"$.legs","limit":5}},
                 {"op":"move","panel":"legs","area":"main","before":"x"},
                 {"op":"setOption","panel":"legs","option":"title","value":null},
                 {"op":"bind","panel":"legs","path":"notional","role":"column"},
                 {"op":"remove","panel":"legs"},
                 {"op":"setTitle","title":{"pill":"T","id":"$.id"}},
                 {"op":"setStrip","items":[{"label":"A","bind":"$.a"}]},
                 {"op":"setKeys","keys":{"F2":"legs"}},
                 {"op":"setMatch","match":{"kind":"trade"}},
                 {"op":"text","yaml":"rachana: 1"}]""";
        List<Op> ops = Ops.parse(json);
        assertThat(ops).hasSize(10).hasOnlyElementsOfTypes(AddPanel.class, Move.class, SetOption.class, Bind.class, Remove.class, SetTitle.class,
                SetStrip.class, SetKeys.class, SetMatch.class, Text.class);
        assertThat(((AddPanel) ops.get(0)).options()).containsEntry("limit", 5L);
        assertThat(Ops.parse(Ops.toJson(ops))).isEqualTo(ops);
        assertThat(Ops.toJson(ops).get(2).containsKey("value")).isTrue();

        assertThatThrownBy(() -> Ops.parse("[{\"op\":\"remove\",\"panel\":\"a\"},{\"op\":\"zap\"}]")).isInstanceOf(Ops.FormatException.class)
                .hasMessageContaining("operation 1").hasMessageContaining("unknown operation 'zap'");
        assertThatThrownBy(() -> Ops.parse("[{\"op\":\"remove\"}]")).hasMessageContaining("operation 0").hasMessageContaining("\"panel\"");
        assertThatThrownBy(() -> Ops.parse("[{\"op\":\"remove\",\"panel\":\"a\",\"why\":1}]")).hasMessageContaining("no field 'why'");
        assertThatThrownBy(() -> Ops.parse("[{\"op\":\"move\",\"panel\":\"a\",\"span\":\"wide\"}]")).hasMessageContaining("\"span\" must be a whole number");
        assertThatThrownBy(() -> Ops.parse("{\"op\":\"remove\"}")).hasMessageContaining("'ops' must be a list");
        assertThatThrownBy(() -> Ops.parse("[{\"op\":\"setOption\",\"panel\":\"a\",\"option\":\"b\"}]")).hasMessageContaining("needs a \"value\"");
        assertThatThrownBy(() -> Ops.parse("nonsense")).hasMessageContaining("not JSON");
    }
}
