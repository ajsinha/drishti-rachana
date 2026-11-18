/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.conformance;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RunnerTest {
    @Test
    void works() {
        assertEquals("results", Runner.RESULTS_DIR);
    }
}
