/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.agg;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** G4 binder pass R-2: a host stores and reads finished figures through Core's public API, with Core's bytes, and keeps no copy. */
class FinalTest {

    @Test
    void aFinishedFigureRoundTripsThroughItsPublicBytes() {
        for (Double v : new Double[] {null, 0.0, -1.5, 1234567.890123, Double.MAX_VALUE}) {
            byte[] b = new Final(v).encode();
            assertThat(b).hasSize(10);
            assertThat(Final.isEncoded(b)).isTrue();
            assertThat(Final.decode(b).value()).isEqualTo(v);
        }
        assertThat(Final.isEncoded(null)).isFalse();
        assertThat(Final.isEncoded(new byte[0])).isFalse();
        assertThat(Final.isEncoded(new byte[] {1, 0})).isFalse();
    }

    @Test
    void theNativeMeasureFunctionReadsTheSameBytes() {
        Aggregate<Object> f = Final.function("VAR", false);
        byte[] b = new Final(42.0).encode();
        assertThat(f.encode(f.decode(b))).isEqualTo(b);
        assertThat(f.finish(f.decode(b))).isEqualTo(42.0);
    }
}
