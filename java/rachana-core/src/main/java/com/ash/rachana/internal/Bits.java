/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.internal;

/**
 * The two 32-bit words of a double, as fdlibm reads and writes them ({@link CoreMath}). On the JVM through
 * {@code doubleToRawLongBits}; the JavaScript build replaces this class with a same-named one that reads the words through a
 * {@code DataView}, because a {@code long} there is a {@code BigInt} (P1's T4 profile: 28% of the time in long arithmetic;
 * fallback 2b, split step A15). Static, stateless.
 */
public final class Bits {

    private Bits() {
    }

    /** The high word: sign, exponent and the top 20 bits of the significand. */
    public static int hi(double x) {
        return (int) (Double.doubleToRawLongBits(x) >>> 32);
    }

    /** The low word: the bottom 32 bits of the significand. */
    public static int lo(double x) {
        return (int) Double.doubleToRawLongBits(x);
    }

    /** The double of two words. */
    public static double fromWords(int hi, int lo) {
        return Double.longBitsToDouble(((long) hi << 32) | (lo & 0xffffffffL));
    }
}
