/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.ash.rachana.model.Sutra;
import com.ash.rachana.parse.SutraParser;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The view model's phone (docs/WRITING_SUTRAS.md 5): each panel's tier with the layer that set it, the order, the default, the summary line. */
class PhoneViewTest {

    private static Sutra parse(String text) {
        return com.ash.rachana.TestGrammar.parser().parse(text, "t.sutra.yaml", "x");
    }

    @Test
    void thePanelsTiersTheBlockAndTheSummaryAreCarried() {
        Sutra s = parse("""
                rachana: 1
                sutra: nset
                version: 1
                match: { kind: netting-set }
                phone: { order: [b], default: more, summary: "Net {$.MTM}" }
                panels:
                  - { id: a, kind: kv, columns: [ { label: A, bind: $.A } ] }
                  - { id: b, kind: kv, phone: must, columns: [ { label: A, bind: $.A } ] }
                """);
        Map<String, Object> phone = PhoneView.of(s, "Net 12");
        assertThat(phone).containsEntry("order", List.of("b")).containsEntry("default", "more").containsEntry("summary", "Net 12");
        assertThat(phone.get("panels")).isEqualTo(Map.of("b", Map.of("tier", "must", "from", "sutra")));
    }

    @Test
    void aSutraThatSaysNothingAboutThePhoneCarriesNothing() {
        Sutra s = parse("rachana: 1\nsutra: plain\nversion: 1\nmatch: { kind: book }\npanels:\n  - { id: a, kind: kv, columns: [ { label: A, bind: $.A } ] }\n");
        assertThat(PhoneView.of(s, null)).isNull();
    }
}
