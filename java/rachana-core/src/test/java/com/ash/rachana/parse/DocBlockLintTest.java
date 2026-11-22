/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.parse;

import static org.assertj.core.api.Assertions.assertThat;

import com.ash.rachana.SutraProblem;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * RCH-2093 (SPEC 2.9): a thin doc block is reported field by field, at its place, with the JSON Pointer of the field; a complete one gives
 * nothing. The same cases run against the GenUI kit's Python mirror (tools/sajha/tests/test_doc_blocks.py), which must agree.
 */
class DocBlockLintTest {

    static final String THIN = """
            rachana: 1.2
            fragment: thin-one
            version: 1
            doc:
              summary: Bars.
              description: Bars.
              use-when: [bars]
              see-also: [Not A Name]
              surprise: 1
            params:
              rows: { type: table, required: true, doc: The rows, columns: { label: { type: text, doc: L }, value: { type: number, doc: V } } }
            panels: []
            """;

    static final String COMPLETE = """
            rachana: 1.2
            fragment: good-bars
            version: 1
            doc:
              summary: One horizontal bar a row, labelled and sized by a value.
              description: Rows are drawn in the order given; sort them largest first to rank them.
              category: charts
              use-when: ["Named items are ranked by one measure (top desks)."]
              avoid-when: ["The parts make up a whole: use core/donut-breakdown."]
              data: { table: rows, in-words: "One row per bar: a label and a value.", sample-row: { label: Rates, value: 1250000 } }
              example: { use: core/good-bars@1, with: { rows: "$.t" } }
              see-also: [core/donut-breakdown]
              keywords: [bars, ranking, top]
            params:
              rows: { type: table, required: true, doc: The rows, columns: { label: { type: text, doc: L }, value: { type: number, doc: V } } }
            panels: []
            """;

    static List<String> paths(String text) {
        return DocBlockLint.check(text, "f.yaml", SutraProblem.ERROR).stream().map(SutraProblem::path).collect(Collectors.toList());
    }

    @Test
    void aThinFragmentDocIsReportedFieldByField() {
        List<SutraProblem> got = DocBlockLint.check(THIN, "thin.yaml", SutraProblem.ERROR);
        assertThat(got).allMatch(p -> p.code().equals("RCH-2093") && p.error());
        assertThat(paths(THIN)).contains("/doc/summary", "/doc/description", "/doc/category", "/doc/use-when/0", "/doc/avoid-when",
                "/doc/keywords", "/doc/data", "/doc/example", "/doc/see-also/0", "/doc/surprise");
        SutraProblem summary = got.stream().filter(p -> p.path().equals("/doc/summary")).findFirst().orElseThrow();
        assertThat(summary.location().line()).isEqualTo(5);
    }

    @Test
    void aCompleteDocGivesNothingAndItsPartsAreChecked() {
        assertThat(paths(COMPLETE)).isEmpty();
        assertThat(paths(COMPLETE.replace("sample-row: { label: Rates, value: 1250000 }", "sample-row: { label: Rates }")))
                .containsExactly("/doc/data/sample-row");
        assertThat(paths(COMPLETE.replace("core/good-bars@1", "core/other@1"))).containsExactly("/doc/example/use");
        assertThat(paths(COMPLETE.replace("with: { rows: \"$.t\" }", "with: { nope: 1 }")))
                .containsExactlyInAnyOrder("/doc/example/with/nope", "/doc/example/with");
    }

    @Test
    void aSutraNeedsLessAndWarnsByDefault() {
        List<SutraProblem> none = DocBlockLint.check("rachana: 1.2\nsutra: s\nversion: 1\npanels: []\n", "s.yaml", null);
        assertThat(none).extracting(SutraProblem::path).containsExactly("/doc");
        assertThat(none.get(0).severity()).isEqualTo(SutraProblem.WARNING);
        String screen = """
                rachana: 1.2
                sutra: s
                version: 1
                doc:
                  summary: The desk's risk at a glance today
                  description: Firm VaR, VaR by desk and limit use for the morning meeting.
                  category: market-risk
                  use-when: [The morning risk meeting reads it]
                  avoid-when: ["Intraday trading: use the live blotter"]
                  keywords: [var, desk, limits]
                panels: []
                """;
        assertThat(DocBlockLint.check(screen, "s.yaml", null)).isEmpty();
    }

    @Test
    void aPartsDocMayBeALineOrABlock() {
        assertThat(TypedFragment.docLine(new PositionalYamlReader().read("doc: { summary: The rows }").map().get("doc"))).isEqualTo("The rows");
        assertThat(TypedFragment.docLine(new PositionalYamlReader().read("doc: The rows").map().get("doc"))).isEqualTo("The rows");
    }
}
