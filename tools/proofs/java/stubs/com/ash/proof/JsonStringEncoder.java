/*
 * Copyright (c) 2026 Ashutosh Sinha. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.proof;

/** Proof-only stand-in for Jackson's JsonStringEncoder.quoteAsString (cut C5), with Jackson's escapes: upper-case hex. */
public final class JsonStringEncoder {
    private static final JsonStringEncoder I = new JsonStringEncoder();
    private static final String HEX = "0123456789ABCDEF";

    public static JsonStringEncoder getInstance() {
        return I;
    }

    public char[] quoteAsString(String s) {
        StringBuilder b = new StringBuilder();
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                case '\b': b.append("\\b"); break;
                case '\f': b.append("\\f"); break;
                default:
                    if (c < 0x20) {
                        b.append("\\u00").append(HEX.charAt(c >> 4)).append(HEX.charAt(c & 15));
                    } else {
                        b.append(c);
                    }
            }
        }
        return b.toString().toCharArray();
    }
}
