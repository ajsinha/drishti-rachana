/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.tools;

import com.ash.rachana.lib.Rlib;
import com.ash.rachana.lib.SignatureVerifier;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.HexFormat;

/**
 * Ed25519 signatures of libraries (DESIGN.md (libraries) 12) on the JVM's own implementation: keys are plain 32-byte values written
 * {@code ed25519:<hex>} (public) and 64 hex digits (the private seed); no certificate authority. The verifier is the one a JVM host
 * hands to {@code Library.Trust}. Stateless; thread-safe.
 */
public final class Ed25519 implements SignatureVerifier {

    /** The verifier. */
    public static final Ed25519 VERIFIER = new Ed25519();

    private static final HexFormat HEX = HexFormat.of();
    private static final byte[] X509 = HEX.parseHex("302a300506032b6570032100");
    private static final byte[] PKCS8 = HEX.parseHex("302e020100300506032b657004220420");

    private Ed25519() {
    }

    /** A new key pair: {@code [public "ed25519:<hex>", private seed <hex>]}. */
    public static String[] newKeyPair() {
        try {
            KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            byte[] pub = kp.getPublic().getEncoded();
            byte[] priv = kp.getPrivate().getEncoded();
            return new String[] {"ed25519:" + HEX.formatHex(tail(pub)), HEX.formatHex(tail(priv))};
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The signature (64 bytes, hex) of {@code message} by a private seed. */
    public static String sign(String privateSeed, byte[] message) {
        try {
            Signature s = Signature.getInstance("Ed25519");
            s.initSign(KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(concat(PKCS8, HEX.parseHex(privateSeed)))));
            s.update(message);
            return Rlib.hex(s.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("not an Ed25519 private seed", e);
        }
    }

    @Override
    public boolean verify(String publicKey, byte[] message, byte[] signature) {
        if (publicKey == null || !publicKey.matches("ed25519:[0-9a-f]{64}") || signature == null || signature.length != 64) {
            return false;
        }
        try {
            Signature s = Signature.getInstance("Ed25519");
            s.initVerify(KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(concat(X509, HEX.parseHex(publicKey.substring(8))))));
            s.update(message);
            return s.verify(signature);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    private static byte[] tail(byte[] encoded) {
        byte[] out = new byte[32];
        System.arraycopy(encoded, encoded.length - 32, out, 0, 32);
        return out;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
