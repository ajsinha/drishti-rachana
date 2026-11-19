/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ash.rachana.SutraLayoutEditor.Placement;
import com.ash.rachana.model.Area;
import com.ash.rachana.model.Panel;
import com.ash.rachana.model.Sutra;
import com.ash.rachana.parse.SutraParser;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;

class SutraLayoutEditorTest {

    private final SutraLayoutEditor editor = new SutraLayoutEditor();
    private final SutraParser parser = new SutraParser();

    private Sutra parse(String text) {
        return parser.parse(text, "t.sutra.yaml", "rates");
    }

    @Test
    void commentsAbovePanelsTravelWithThemAndAFlowListIsRefused() {
        String src = """
                rachana: 1
                sutra: c-test
                version: 1
                match: { kind: trade }
                panels:
                  # the terms first
                  - { id: a, kind: markdown, text: A }
                  # then the notes
                  - id: b
                    kind: markdown
                    text: B   # inline

                # ---- keys ----
                keys: { F9: raw }
                """;
        var e = editor.apply(src, List.of(new Placement("b", Area.MAIN, 5, 3, false), new Placement("a", Area.MAIN, null, null, false)), false);
        assertThat(e.text()).contains("panels:\n  # then the notes\n  - id: b\n    kind: markdown\n    span: 5\n    height: 3\n"
                + "    text: B   # inline\n  # the terms first\n  - { id: a, kind: markdown, text: A }\n");
        assertThat(e.text()).contains("\n\n# ---- keys ----\nkeys: { F9: raw }");
        String flowList = "rachana: 1\nsutra: f-test\nversion: 1\nmatch: { kind: trade }\npanels: [ { id: a, kind: markdown, text: A } ]\n";
        assertThatThrownBy(() -> editor.apply(flowList, List.of(), false)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("flow list");
    }

    @Test
    void flowEntriesAreFoundAtTheirOwnDepthOnly() {
        String t = "  - { id: x, kind: table, rows: $.r, columns: [{ label: span, bind: \"@.span\" }], area: right }";
        assertThat(SutraText.flowSet(t, "area", null)).isEqualTo("  - { id: x, kind: table, rows: $.r, columns: [{ label: span, bind: \"@.span\" }] }");
        assertThat(SutraText.flowSet(t, "span", "4")).endsWith("area: right, span: 4 }");
        assertThat(SutraText.flowSet(t, "area", "main")).endsWith("area: main }");
    }

    @Test
    void theGrammarChecksSpanAndHeight() {
        String base = "rachana: 1\nsutra: s-test\nversion: 1\nmatch: { kind: trade }\npanels:\n";
        Sutra ok = parse(base + "  - { id: a, kind: markdown, text: A, span: 6, height: 24 }\n");
        assertThat(ok.panels().get(0).span()).contains(6);
        assertThat(ok.panels().get(0).height()).contains(24);
        for (String bad : List.of("span: 0", "span: 13", "span: wide", "height: 25", "height: 2.5")) {
            assertThatThrownBy(() -> parse(base + "  - { id: a, kind: markdown, text: A, " + bad + " }\n"))
                    .satisfies(e -> assertThat(((SutraException) e).problems()).extracting(SutraProblem::code).containsExactly("DRS-2030"));
        }
        assertThatThrownBy(() -> parse(base + "  - { id: t, kind: tabs, each: $.legs, body: { kind: kv, span: 4 } }\n"))
                .satisfies(e -> assertThat(((SutraException) e).problems().get(0).message()).contains("a tabs body takes the size of its panel"));
    }
}
