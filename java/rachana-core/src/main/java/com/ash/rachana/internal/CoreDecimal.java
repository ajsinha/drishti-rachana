/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.internal;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

/**
 * Decimal operations Core needs that not every runtime's {@code BigDecimal} has: the square root, written with whole-number
 * arithmetic only. Its results equal Java 21's {@code BigDecimal.sqrt(MathContext)} for half-even rounding, scale included
 * ({@code CoreDecimalTest}). Static, stateless, thread-safe.
 */
public final class CoreDecimal {

    private CoreDecimal() {
    }

    /**
     * The square root of a non-negative decimal rounded half to even to {@code mc}'s precision; an exact root is given with
     * the scale nearest half the argument's scale that the precision allows ({@code sqrt(0.0144)} is {@code 0.12}).
     */
    public static BigDecimal sqrt(BigDecimal x, MathContext mc) {
        if (x.signum() < 0) {
            throw new ArithmeticException("the square root of a negative number");
        }
        int preferred = x.scale() / 2;
        if (x.signum() == 0) {
            return BigDecimal.valueOf(0L, preferred);
        }
        int prec = mc.getPrecision();
        int s = x.scale();
        int e = Math.max(0, 2 * prec + 2 - x.precision());
        if (((s + e) & 1) != 0) {
            e++;
        }
        BigInteger big = x.unscaledValue().multiply(BigInteger.TEN.pow(e));
        BigInteger r = isqrt(big);
        int scale = (s + e) / 2;
        BigDecimal result = r.multiply(r).equals(big)
                ? new BigDecimal(r, scale).round(mc)
                : new BigDecimal(r.multiply(BigInteger.TEN).add(BigInteger.ONE), scale + 1).round(mc);  // a sticky 1: never a false tie
        BigDecimal stripped = result.stripTrailingZeros();
        if (stripped.scale() >= preferred) {
            return stripped;
        }
        int room = prec - stripped.precision();
        return stripped.setScale(Math.min(preferred, stripped.scale() + Math.max(0, room)));
    }

    /** The whole square root (the floor) by Newton's method. */
    static BigInteger isqrt(BigInteger n) {
        if (n.signum() == 0) {
            return n;
        }
        BigInteger x = BigInteger.ONE.shiftLeft((n.bitLength() + 1) / 2);   // at or above the root
        while (true) {
            BigInteger y = x.add(n.divide(x)).shiftRight(1);
            if (y.compareTo(x) >= 0) {
                return x;
            }
            x = y;
        }
    }
}
