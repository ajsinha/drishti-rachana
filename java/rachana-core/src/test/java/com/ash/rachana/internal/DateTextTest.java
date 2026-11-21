/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** Core's date patterns print what DateTimeFormatter prints in English, for every date of four centuries. */
class DateTextTest {

    private static final String[] PATTERNS = {"dd MMM yyyy", "yyyy-MM-dd", "d/M/yy", "EEE d MMMM y", "EEEE, dd.MM.yyyy",
        "MMM-yy", "yyy", "'Q' M ''yy", "MMMMM d", "E dd", "d 'of' MMMM", "yyyyy"};

    @Test
    void sameAsDateTimeFormatterInEnglish() {
        for (String p : PATTERNS) {
            DateTimeFormatter f = DateTimeFormatter.ofPattern(p, Locale.ENGLISH);
            for (LocalDate d = LocalDate.of(1900, 1, 1); d.getYear() < 2300; d = d.plusDays(3)) {
                assertEquals(d.format(f), DateText.format(d, p), p + " " + d);
            }
            assertEquals(LocalDate.of(9999, 12, 31).format(f), DateText.format(LocalDate.of(9999, 12, 31), p), p);
            assertEquals(LocalDate.of(1, 1, 1).format(f), DateText.format(LocalDate.of(1, 1, 1), p), p);
        }
    }

    @Test
    void otherLettersAreNotPrinted() {
        assertNull(DateText.format(LocalDate.of(2026, 11, 17), "yyyy-MM-dd HH:mm"));
        assertNull(DateText.format(LocalDate.of(2026, 11, 17), "D"));
        assertNull(DateText.format(LocalDate.of(2026, 11, 17), "'open"));
    }
}
