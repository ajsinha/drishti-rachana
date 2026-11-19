/*
 * Copyright (c) 2026 Ashutosh Sinha. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.drishti.rachana.el;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

/**
 * PROOF GAP G1 (T1): TeaVM's class library has no {@code BigDecimal.sqrt(MathContext)}. Core's own square root, correctly
 * rounded half-even to the context's precision, which is what the JDK's method returns for a half-even context (its contract:
 * within half an ulp). Integer square root by Newton's method on {@code BigInteger}, one guard digit and a sticky bit.
 */
final class CoreDecimal {
    private CoreDecimal() {
    }

    static BigDecimal sqrt(BigDecimal x, MathContext mc) {
        if (x.signum() == 0) {
            return BigDecimal.ZERO;
        }
        int p = mc.getPrecision();
        BigInteger u = x.unscaledValue();
        int s = x.scale();
        if ((s & 1) != 0) {
            u = u.multiply(BigInteger.TEN);
            s++;
        }
        int k = 0;
        while (u.toString().length() + 2 * k < 2 * (p + 2)) {
            k++;
        }
        BigInteger n = u.multiply(BigInteger.TEN.pow(2 * k));
        BigInteger q = isqrt(n);
        boolean sticky = !q.multiply(q).equals(n);
        int scale = s / 2 + k;
        int extra = q.toString().length() - (p + 1);
        if (extra > 0) {
            BigInteger[] qr = q.divideAndRemainder(BigInteger.TEN.pow(extra));
            sticky |= qr[1].signum() != 0;
            q = qr[0];
            scale -= extra;
        }
        BigInteger[] qr = q.divideAndRemainder(BigInteger.TEN);
        int last = qr[1].intValue();
        BigInteger r = qr[0];
        scale -= 1;
        if (last > 5 || last == 5 && (sticky || r.testBit(0))) {
            r = r.add(BigInteger.ONE);
        }
        return new BigDecimal(r, scale);
    }

    private static BigInteger isqrt(BigInteger n) {
        BigInteger x = BigInteger.ONE.shiftLeft((n.bitLength() + 1) / 2);
        while (true) {
            BigInteger y = x.add(n.divide(x)).shiftRight(1);
            if (y.compareTo(x) >= 0) {
                return x;
            }
            x = y;
        }
    }
}
