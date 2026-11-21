/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Locale;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** CoreText equals Java 21's case mapping, white space and strip for every code point and for random mixed text. */
class CoreTextTest {

    private static final String ALPHABET = "aAzZßİıΣσςΑαΒβ ﬀŉǅǈ'.\u0301\u0345ª1-\t\u2003\u00a0ÉéДдᾳᾼİ";

    @Test
    void everyCodePointMapsAsJava21() {
        assumeTrue(Runtime.version().feature() == 21, "the tables are Java 21's (Unicode 15.0)");
        for (int cp = 0; cp <= 0x10FFFF; cp++) {
            if (cp >= 0xD800 && cp <= 0xDFFF) {
                continue;
            }
            String s = new String(Character.toChars(cp));
            assertEquals(s.toUpperCase(Locale.ROOT), CoreText.upper(s), () -> "upper " + Integer.toHexString(s.codePointAt(0)));
            assertEquals(s.toLowerCase(Locale.ROOT), CoreText.lower(s), () -> "lower " + Integer.toHexString(s.codePointAt(0)));
            assertEquals(Character.toUpperCase(cp), CoreText.upper(cp), s);
            assertEquals(Character.toLowerCase(cp), CoreText.lower(cp), s);
            assertEquals(Character.isWhitespace(cp), CoreText.isWhitespace(cp), s);
        }
    }

    @Test
    void mixedTextAsJava21() {
        assumeTrue(Runtime.version().feature() == 21, "the tables are Java 21's (Unicode 15.0)");
        SplittableRandom r = new SplittableRandom(7);
        for (int n = 0; n < 200_000; n++) {
            StringBuilder b = new StringBuilder();
            int len = r.nextInt(1, 9);
            for (int k = 0; k < len; k++) {
                b.append(ALPHABET.charAt(r.nextInt(ALPHABET.length())));
            }
            String s = b.toString();
            assertEquals(s.toUpperCase(Locale.ROOT), CoreText.upper(s), s);
            assertEquals(s.toLowerCase(Locale.ROOT), CoreText.lower(s), s);
            assertEquals(s.strip(), CoreText.strip(s), s);
            assertEquals(s.stripLeading(), CoreText.stripLeading(s), s);
            assertEquals(s.stripTrailing(), CoreText.stripTrailing(s), s);
        }
    }

    @Test
    void reverseKeepsSurrogatePairs() {
        assertEquals("c😀ba", CoreText.reverse("ab😀c"));
        assertEquals("", CoreText.reverse(""));
    }
}
