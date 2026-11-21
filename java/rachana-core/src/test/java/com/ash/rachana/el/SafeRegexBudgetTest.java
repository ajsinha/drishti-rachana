/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ash.rachana.RachanaCode;
import com.ash.rachana.RachanaException;
import org.junit.jupiter.api.Test;

/** A match's budget is a count of steps, not a clock: the same value stops at the same place on every run and runtime. */
class SafeRegexBudgetTest {

    @Test
    void aMatchOverItsStepsIsRefusedTheSameWayEveryTime() {
        SafeRegex.Limits tight = new SafeRegex.Limits(200, 4000, 100_000, 5_000);
        SafeRegex re = SafeRegex.compile("(a|b)*c(a|b)*d", false, tight);
        String value = "ab".repeat(20_000);
        for (int run = 0; run < 3; run++) {
            RachanaException e = assertThrows(RachanaException.class, () -> re.find(value));
            assertEquals(RachanaCode.REGEX_TIMEOUT, e.code());
            assertTrue(e.getMessage().contains("took more than 5000 steps"), e.getMessage());
        }
        assertTrue(SafeRegex.compile("(a|b)*c(a|b)*d", false, SafeRegex.Limits.DEFAULTS).find("ab".repeat(4000) + "cd"));
    }
}
