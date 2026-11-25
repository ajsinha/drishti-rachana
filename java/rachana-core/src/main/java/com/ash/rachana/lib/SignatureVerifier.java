/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.lib;

/**
 * The host's check of a library's detached signature (DESIGN.md (libraries) 12): Ed25519 over the bytes of {@code MANIFEST.sha384}. Core ships
 * no implementation (the JavaScript build has no {@code java.security}); the JVM tools give one ({@code com.ash.rachana.tools.Ed25519}).
 * Without a verifier a library's {@code SIGNATURE} is not checked, and a host that requires signed libraries refuses every library.
 */
@FunctionalInterface
public interface SignatureVerifier {

    /**
     * Whether {@code signature} is a valid signature of {@code message} by {@code publicKey}.
     *
     * @param publicKey {@code ed25519:} followed by the 32-byte public key in lower-case hex
     */
    boolean verify(String publicKey, byte[] message, byte[] signature);
}
