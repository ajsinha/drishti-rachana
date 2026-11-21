/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.server;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Signed-in sessions, in memory: a random 256-bit id (the cookie), the principal, a CSRF token for the session's writes, an idle and an
 * absolute limit. A new id at every sign-in; sign-out forgets it. Also the sign-in throttle: after {@link #FREE_FAILURES} failures from an
 * address, each further try waits twice as long (up to {@link #MAX_WAIT_MS}). Thread-safe.
 */
final class Sessions {

    static final int FREE_FAILURES = 5;
    static final long MAX_WAIT_MS = 300_000;
    static final int MAX_SESSIONS = 100_000;

    /** One session. */
    record Session(String id, Users.Principal principal, String csrf, long created, long seen) {}

    private record Failures(int count, long until) {}

    private static final SecureRandom RANDOM = new SecureRandom();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, Failures> failures = new ConcurrentHashMap<>();
    private final long idleMs;
    private final long maxMs;
    private final Clock clock;

    Sessions(long idleMs, long maxMs, Clock clock) {
        this.idleMs = idleMs;
        this.maxMs = maxMs;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    /** A random token: 32 bytes, base64url. */
    static String token() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    /** Whether two tokens are equal, in constant time (false when either is missing). */
    static boolean same(String a, String b) {
        return a != null && b != null && MessageDigest.isEqual(a.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                b.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    /** A new session for a principal (a fresh id; the oldest sessions are dropped beyond {@link #MAX_SESSIONS}). */
    Session open(Users.Principal p) {
        sweep();
        long now = clock.millis();
        Session s = new Session(token(), p, token(), now, now);
        sessions.put(s.id(), s);
        return s;
    }

    /** The live session of a cookie value, its idle clock reset; null when unknown or expired. */
    Session get(String id) {
        if (id == null) {
            return null;
        }
        Session s = sessions.get(id);
        long now = clock.millis();
        if (s == null) {
            return null;
        }
        if (now - s.seen() > idleMs || now - s.created() > maxMs) {
            sessions.remove(id);
            return null;
        }
        Session t = new Session(s.id(), s.principal(), s.csrf(), s.created(), now);
        sessions.replace(id, s, t);
        return t;
    }

    void close(String id) {
        if (id != null) {
            sessions.remove(id);
        }
    }

    int size() {
        return sessions.size();
    }

    /** How long an address must wait before its next sign-in try (0: now). */
    long waitMs(String address) {
        Failures f = failures.get(address);
        return f == null ? 0 : Math.max(0, f.until() - clock.millis());
    }

    /** Records a failed sign-in from an address. */
    void failed(String address) {
        long now = clock.millis();
        failures.compute(address, (k, f) -> {
            int n = f == null ? 1 : f.count() + 1;
            long wait = n <= FREE_FAILURES ? 0 : Math.min(MAX_WAIT_MS, 1000L << Math.min(20, n - FREE_FAILURES - 1));
            return new Failures(n, now + wait);
        });
        if (failures.size() > MAX_SESSIONS) {
            failures.entrySet().removeIf(e -> e.getValue().until() < now);
        }
    }

    /** A successful sign-in clears the address's failures. */
    void succeeded(String address) {
        failures.remove(address);
    }

    private void sweep() {
        long now = clock.millis();
        if (sessions.size() >= MAX_SESSIONS / 2) {
            sessions.values().removeIf(s -> now - s.seen() > idleMs || now - s.created() > maxMs);
        }
        while (sessions.size() >= MAX_SESSIONS) {
            sessions.values().stream().min(java.util.Comparator.comparingLong(Session::seen)).ifPresent(s -> sessions.remove(s.id()));
        }
    }
}
