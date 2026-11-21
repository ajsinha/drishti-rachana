/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** Core's number-code printer equals the DecimalFormat the reference built from the same code, for every code in its grammar. */
class NumberCodeTest {

    private static final String[] CODES = {"0", "0.00", "#,##0", "#,##0.00", "0.0%", "0%", "#,##0.0#", "#.##", "#", "0.###",
        "\"USD \"#,##0", "#,##0\" bn\"", "0.00E+00", "0.0E+0", "00.00", "000", "#,##0.00%", "0.", "#,###", "#,##,##0", "0.00E-00",
        "(0)", "0\" kg\"", "$#,##0.00", "0.0000000000"};

    /** The reference's way: the Excel code turned into a DecimalFormat pattern (TextParts before A5). */
    private static String reference(BigDecimal v, String f) {
        StringBuilder pattern = new StringBuilder();
        boolean scientific = false;
        for (int i = 0; i < f.length(); i++) {
            char ch = f.charAt(i);
            if (ch == '"') {
                int end = f.indexOf('"', i + 1);
                String lit = f.substring(i + 1, end < 0 ? f.length() : end);
                pattern.append('\'').append(lit.replace("'", "''")).append('\'');
                i = end < 0 ? f.length() : end;
            } else if ((ch == 'E' || ch == 'e') && i + 1 < f.length() && (f.charAt(i + 1) == '+' || f.charAt(i + 1) == '-')) {
                pattern.append('E');
                scientific = true;
                i++;
            } else if (ch == '\'') {
                pattern.append("''");
            } else if ("0#.,%".indexOf(ch) >= 0) {
                pattern.append(ch);
            } else {
                pattern.append('\'').append(ch).append('\'');
            }
        }
        DecimalFormat df = new DecimalFormat(pattern.toString(), DecimalFormatSymbols.getInstance(Locale.ROOT));
        df.setRoundingMode(RoundingMode.HALF_UP);
        String s = df.format(v);
        return scientific ? s.replaceAll("E(\\d)", "E+$1") : s;
    }

    @Test
    void sameAsTheReference() {
        SplittableRandom r = new SplittableRandom(11);
        for (String code : CODES) {
            NumberCode c = NumberCode.read(code);
            assertNotNull(c, code);
            for (int k = 0; k < 4000; k++) {
                BigDecimal v = switch (k % 4) {
                    case 0 -> BigDecimal.valueOf(r.nextLong(-2_000_000, 2_000_000), r.nextInt(0, 7));
                    case 1 -> BigDecimal.valueOf(r.nextLong(-999, 999), r.nextInt(-12, 12));
                    case 2 -> new BigDecimal(r.nextInt(0, 1000) + ".5").movePointLeft(r.nextInt(0, 5));
                    default -> BigDecimal.valueOf(r.nextInt(-5, 5), r.nextInt(0, 3));
                };
                assertEquals(reference(v, code), c.format(v), code + " of " + v.toPlainString());
            }
        }
    }

    /** Two literals side by side: the reference's pattern ("' ''kg'") printed a stray quote; Core prints the text. */
    @Test
    void adjacentLiterals() {
        assertEquals("-42 kg", NumberCode.read("0 \"kg\"").format(new BigDecimal("-41.8399")));
    }
}
