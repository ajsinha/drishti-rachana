/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.control;

import static org.assertj.core.api.Assertions.assertThat;

import com.ash.rachana.model.Sutra;
import com.ash.rachana.parse.SutraParser;
import org.junit.jupiter.api.Test;

/** The parts a control change rebuilds: a panel reads a control through its columns, its options or its own source. */
class ControlReadsTest {

    private static Sutra parse(String text) {
        return new SutraParser().parse(text, "t.sutra.yaml", "x");
    }

    @Test
    void aPanelWhoseSourceParameterReadsAControlIsRebuiltWhenItChanges() {
        Sutra s = parse("""
                rachana: 1
                sutra: corr
                version: 1
                match: { kind: correlation-matrix }
                controls:
                  - { id: estimator, kind: segmented, options: ["pearson", "ledoit-wolf"], default: "pearson" }
                  - { id: unused, kind: segmented, options: ["a", "b"], default: "a" }
                panels:
                  - id: grid
                    kind: kv
                    source: { kind: correlation-result, id: "$.MATRIX_ID", params: { estimator: ctl.estimator } }
                    columns: [ { label: Pairs, bind: $src.PAIRS } ]
                  - { id: plain, kind: kv, columns: [ { label: Name, bind: $.NAME } ] }
                """);
        assertThat(ControlReads.affected(s, "estimator")).containsExactly("grid");
        assertThat(ControlReads.affected(s, "unused")).isEmpty();
    }
}
