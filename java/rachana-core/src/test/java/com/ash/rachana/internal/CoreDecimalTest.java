/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** CoreDecimal.sqrt equals Java 21's BigDecimal.sqrt, digits and scale, exact squares and random decimals alike. */
class CoreDecimalTest {

    @Test
    void sameAsBigDecimalSqrt() {
        SplittableRandom r = new SplittableRandom(5);
        MathContext[] mcs = {MathContext.DECIMAL64, MathContext.DECIMAL128, new MathContext(1), new MathContext(3)};
        for (int k = 0; k < 200_000; k++) {
            BigDecimal x = switch (k % 3) {
                case 0 -> BigDecimal.valueOf(r.nextLong(0, Long.MAX_VALUE), r.nextInt(-20, 40));
                case 1 -> BigDecimal.valueOf(r.nextLong(0, 100_000)).pow(2).movePointLeft(r.nextInt(-6, 12));
                default -> BigDecimal.valueOf(r.nextInt(0, 1000), r.nextInt(0, 6));
            };
            MathContext mc = mcs[k % mcs.length];
            BigDecimal want = x.sqrt(mc);
            BigDecimal got = CoreDecimal.sqrt(x, mc);
            assertEquals(want.toString(), got.toString(), () -> x + " at " + mc.getPrecision());
        }
        assertEquals("0.12", CoreDecimal.sqrt(new BigDecimal("0.0144"), MathContext.DECIMAL64).toString());
    }
}
