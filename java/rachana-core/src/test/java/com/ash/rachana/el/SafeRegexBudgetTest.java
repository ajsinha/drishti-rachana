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

    /** Gate finding F2: one budget per call. The review's 8 KB case: each match rescans the rest of the value, n x n steps in all. */
    @Test
    void aReplaceAllDrawsOnOneBudgetForAllItsMatches() {
        String value = "a".repeat(8192);
        for (String p : new String[] {"a(.*b)?", "(a|aa)*c|a", "a(a|b)*c|a"}) {
            SafeRegex re = SafeRegex.compile(p, false, SafeRegex.Limits.DEFAULTS);
            long t0 = System.nanoTime();
            RachanaException e = assertThrows(RachanaException.class, () -> re.replaceAll(value, "x"), p);
            assertEquals(RachanaCode.REGEX_TIMEOUT, e.code(), p);
            assertTrue(System.nanoTime() - t0 < 1_500_000_000L, p + ": one call is bounded by one budget");   // was 0.6 to 1.7 s per call, unbounded in n
        }
        SafeRegex.Limits small = new SafeRegex.Limits(200, 4000, 8192, 1_000);
        SafeRegex one = SafeRegex.compile("a", false, small);
        assertEquals("x".repeat(200), one.replaceAll("a".repeat(200), "x"));                       // about 400 steps: under the budget
        assertEquals(RachanaCode.REGEX_TIMEOUT, assertThrows(RachanaException.class, () -> one.replaceAll("a".repeat(800), "x")).code());
        assertEquals("b".repeat(800), SafeRegex.compile("a", false, small).replaceAll("b".repeat(800), "x"));   // no match: one scan, the call's budget
    }
}
