/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana;

import static org.assertj.core.api.Assertions.assertThat;

import com.ash.rachana.model.PanelKind;
import org.junit.jupiter.api.Test;

/** Every panel kind that reads data may read a linked entity's instead ({@code source}); links and provenance (which describe the view) may not; markdown reads data through its text. */
class PanelSourceTest {

    @Test
    void everyKindThatReadsDataTakesASource() {
        for (PanelKind k : PanelKind.values()) {
            boolean describesTheView = k == PanelKind.LINKS || k == PanelKind.PROVENANCE;
            assertThat(k.accepts("source")).as(k.id()).isEqualTo(!describesTheView);
            assertThat(SutraExpressions.expressionOptions(k).contains("source")).as(k.id()).isEqualTo(!describesTheView);
        }
    }
}
