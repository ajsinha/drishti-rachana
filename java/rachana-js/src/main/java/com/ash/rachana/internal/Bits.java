/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.internal;

import org.teavm.jso.JSBody;

/**
 * The two 32-bit words of a double in the JavaScript build (fallback 2b, split step A15): read and written through one
 * {@code DataView} (big-endian offsets, so the platform's byte order does not matter), with no {@code long} (a {@code BigInt}
 * in TeaVM). Replaces Core's {@code Bits} in the bundles; the same words.
 */
public final class Bits {

    private Bits() {
    }

    private static final String VIEW = "(globalThis.__rachanaWords || (globalThis.__rachanaWords = new DataView(new ArrayBuffer(8))))";

    @JSBody(params = "x", script = "const v = " + VIEW + "; v.setFloat64(0, x); return v.getInt32(0);")
    public static native int hi(double x);

    @JSBody(params = "x", script = "const v = " + VIEW + "; v.setFloat64(0, x); return v.getInt32(4);")
    public static native int lo(double x);

    @JSBody(params = { "hi", "lo" }, script = "const v = " + VIEW + "; v.setInt32(0, hi); v.setInt32(4, lo); return v.getFloat64(0);")
    public static native double fromWords(int hi, int lo);
}
