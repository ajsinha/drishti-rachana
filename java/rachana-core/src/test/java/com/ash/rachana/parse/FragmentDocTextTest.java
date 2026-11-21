/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.parse;

import static org.assertj.core.api.Assertions.assertThat;

import com.ash.rachana.SutraProblem;
import com.ash.rachana.lib.LibraryExports;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A fragment's doc texts reach the library manifest whole (G4: the generated manifests had cut {@code key-figure}'s {@code delta} doc
 * at its comma). The cause was the fragment's own flow mapping: {@code { doc: The change, written beside it }} is the doc "The change"
 * and a key "written beside it". A parameter or column declaration now takes only its keys, so a cut text is refused with the fix;
 * a quoted text with commas, colons, quotes, a hash, a backslash and characters beyond ASCII goes through {@code rachana lib build}'s
 * writer and back unchanged.
 */
class FragmentDocTextTest {

    static final String HARD = "The change, against: a \\\"reference\\\" (it's # not a comment) \\\\ é 日本 📈, written beside";
    static final String HARD_PLAIN = "The change, against: a \"reference\" (it's # not a comment) \\ é 日本 📈, written beside";

    static String fragment(String deltaDoc, String columnDoc) {
        return """
                rachana: 1.2
                fragment: doc-texts
                version: 1
                doc:
                  summary: "One figure, with commas, colons: and quotes in its docs."
                  category: figures
                params:
                  value: { type: number, required: true, doc: The figure }
                  delta: { type: number, optional: true, doc: %s }
                  rows:
                    type: table
                    required: true
                    doc: "One row per point"
                    columns:
                      x: { type: number, doc: %s }
                panels:
                  - id: figure
                    kind: metric
                    value: arg.value
                    delta: arg.delta
                """.formatted(deltaDoc, columnDoc);
    }

    static List<SutraProblem> problems(String text) {
        return TypedFragment.read("doc-texts", "doc-texts.v1.fragment.yaml", new PositionalYamlReader().read(text)).problems();
    }

    @Test
    void aDocCutAtItsCommaIsRefusedWithTheFix() {
        List<SutraProblem> p = problems(fragment("The change against a reference, written beside the figure", "\"The position\""));
        assertThat(p).anySatisfy(x -> assertThat(x.message()).contains("parameter delta of fragment doc-texts takes")
                .contains("not 'written beside the figure'").contains("quote it"));
        List<SutraProblem> c = problems(fragment("\"The change\"", "The position, a number or a label"));
        assertThat(c).anySatisfy(x -> assertThat(x.message()).contains("column x of parameter rows").contains("not 'a number or a label'"));
    }

    @Test
    void quotedDocsRoundTripThroughTheManifest() {
        String text = fragment("\"" + HARD + "\"", "\"x, the position: \\\"left\\\" to right, ü\"");
        assertThat(problems(text)).noneMatch(x -> x.message().contains("takes ["));
        Map<String, byte[]> files = Map.of("fragments/doc-texts.v1.fragment.yaml", text.getBytes(StandardCharsets.UTF_8));
        List<String> lines = LibraryExports.lines(files);
        assertThat(lines).hasSize(1);
        String manifest = "library: x\nexports:\n  fragments:\n    " + lines.get(0) + "\n";
        PNode root = new PositionalYamlReader().read(manifest);
        @SuppressWarnings("unchecked")
        Map<String, Object> entry = (Map<String, Object>) ((List<Object>) LibraryExports.plain(root.map().get("exports").map().get("fragments"))).get(0);
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> params = (Map<String, Map<String, Object>>) entry.get("params");
        assertThat(params.get("delta")).containsOnlyKeys("type", "optional", "doc").containsEntry("doc", HARD_PLAIN);
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> cols = (Map<String, Map<String, Object>>) params.get("rows").get("columns");
        assertThat(cols.get("x")).containsEntry("doc", "x, the position: \"left\" to right, ü");
        assertThat(entry.get("summary")).isEqualTo("One figure, with commas, colons: and quotes in its docs.");
        assertThat(LibraryExports.stale(root.map().get("exports"), files)).isNull();
    }
}
