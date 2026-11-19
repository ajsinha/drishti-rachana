/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.compat;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RecordedHostTest {
    static final String REC = "{\"calls\":[{\"hook\":\"calendar.today\",\"args\":[],\"answer\":\"2026-09-30\"},"
            + "{\"hook\":\"fn\",\"args\":[\"fx\",{\"b\":1,\"a\":2}],\"answer\":1.25},{\"hook\":\"fn\",\"args\":[\"fx\",{\"a\":2,\"b\":1}],\"answer\":1.5}]}";

    @Test
    void answersFromTheRecordingInTheOrderRecordedAndKeysIgnoreObjectOrder() {
        RecordedHost h = RecordedHost.fromJson(REC);
        assertEquals("2026-09-30", h.answer("calendar.today"));
        assertEquals(new java.math.BigDecimal("1.25"), h.answer("fn", "fx", java.util.Map.of("a", new java.math.BigDecimal(2), "b", new java.math.BigDecimal(1))));
        assertEquals(new java.math.BigDecimal("1.5"), h.answer("fn", "fx", java.util.Map.of("b", new java.math.BigDecimal(1), "a", new java.math.BigDecimal(2))));
        assertEquals(new java.math.BigDecimal("1.5"), h.answer("fn", "fx", java.util.Map.of("a", new java.math.BigDecimal(2), "b", new java.math.BigDecimal(1))), "the last answer stands");
        assertTrue(h.unused().isEmpty());
    }

    @Test
    void anUnrecordedCallFailsAndIsAFinding() {
        RecordedHost h = RecordedHost.fromJson(REC);
        RecordedHost.UnrecordedCall e = assertThrows(RecordedHost.UnrecordedCall.class, () -> h.answer("mask.decide", "TRADE_ID"));
        assertTrue(e.getMessage().contains("mask.decide"));
        assertEquals(2, h.unused().size());
    }
}
