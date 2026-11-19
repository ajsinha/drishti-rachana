/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ValuesNumberTest {

    @Test
    void textThatIsNotANumberIsRejectedWithoutParsingAndNumbersStillRead() {
        for (String no : new String[] {"2026-09-09", "CP-0042", "ENDUR", "", "  ", "1-2", "MX-50000042", "2026-09-09T10:00:00Z"}) {
            assertThat(Values.couldBeNumber(no)).as(no).isFalse();
            assertThat(Values.number(no)).as(no).isNaN();
        }
        assertThat(Values.number("42")).isEqualTo(42.0);
        assertThat(Values.number(" -1.5e-3 ")).isEqualTo(-1.5e-3);
        assertThat(Values.number("+7")).isEqualTo(7.0);
        assertThat(Values.number(".5")).isEqualTo(0.5);
        assertThat(Values.number("1E+6")).isEqualTo(1e6);
        assertThat(Values.number("Infinity")).isInfinite();
        assertThat(Values.number("1.5x")).isNaN();                 // still parsed, still rejected
    }
}
