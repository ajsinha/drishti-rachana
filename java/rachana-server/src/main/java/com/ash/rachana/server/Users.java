/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.server;

import com.ash.rachana.parse.PositionalYamlReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * The users file of {@code auth.mode: users}: each user's salted password hash and roles.
 *
 * <pre>
 * users:
 *   alice: { password: "pbkdf2-sha256$210000$&lt;salt&gt;$&lt;hash&gt;", roles: [sales] }
 * </pre>
 *
 * Hashes are PBKDF2 with HMAC-SHA-256 (the JDK's), a random 16-byte salt and 210,000 iterations by default; {@link #hash} makes one
 * ({@code java -jar rachana-server.jar hash-password}). A password is never stored, logged or compared as text: {@link #check} compares
 * derived keys in constant time, and spends the same work for an unknown name. The file is read again when it changes.
 */
final class Users {

    /** A signed-in caller: the opaque object the Data Protocol hands to the authoriser. */
    record Principal(String name, Set<String> roles) {
        Principal {
            roles = Set.copyOf(roles);
        }

        /** The caller of {@code auth.mode: none}: no name, no roles. */
        static final Principal ANONYMOUS = new Principal("", Set.of());

        @Override
        public String toString() {
            return name.isEmpty() ? "anonymous" : name;
        }
    }

    static final String SCHEME = "pbkdf2-sha256";
    static final int ITERATIONS = 210_000;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String DUMMY = hash("not a password".toCharArray(), ITERATIONS);

    private record Entry(String hash, Set<String> roles) {}

    private final Path file;
    private volatile Map<String, Entry> users = Map.of();
    private volatile FileTime read;

    Users(Path file) throws IOException, ServerConfig.Invalid {
        this.file = file;
        load();
    }

    /** Makes a hash of a password for the users file. */
    static String hash(char[] password, int iterations) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        Base64.Encoder b = Base64.getUrlEncoder().withoutPadding();
        return SCHEME + "$" + iterations + "$" + b.encodeToString(salt) + "$" + b.encodeToString(derive(password, salt, iterations));
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2WithHmacSHA256 is not available", e);
        } finally {
            spec.clearPassword();
        }
    }

    /** Whether a password matches a stored hash (constant-time over the derived keys); false for a malformed hash. */
    static boolean matches(char[] password, String stored) {
        String[] p = stored.split("\\$");
        if (p.length != 4 || !p[0].equals(SCHEME)) {
            return false;
        }
        try {
            int it = Integer.parseInt(p[1]);
            byte[] salt = Base64.getUrlDecoder().decode(p[2]);
            byte[] want = Base64.getUrlDecoder().decode(p[3]);
            return it >= 10_000 && MessageDigest.isEqual(derive(password, salt, it), want);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** The principal for a name and password, or null. The same work is spent for an unknown name. */
    Principal check(String name, char[] password) {
        reloadIfChanged();
        Entry e = name == null ? null : users.get(name);
        boolean ok = matches(password, e == null ? DUMMY : e.hash()) && e != null;
        return ok ? new Principal(name, e.roles()) : null;
    }

    /** How many users the file has. */
    int size() {
        return users.size();
    }

    private void reloadIfChanged() {
        try {
            if (!Files.getLastModifiedTime(file).equals(read)) {
                load();
            }
        } catch (IOException | ServerConfig.Invalid e) {
            // keep the users last read; the next sign-in tries again
        }
    }

    @SuppressWarnings("unchecked")
    private synchronized void load() throws IOException, ServerConfig.Invalid {
        FileTime t = Files.getLastModifiedTime(file);
        Object root = new PositionalYamlReader().read(Files.readString(file, StandardCharsets.UTF_8)).plain();
        List<String> problems = new ArrayList<>();
        Map<String, Entry> out = new LinkedHashMap<>();
        if (!(root instanceof Map<?, ?> m) || !(m.get("users") instanceof Map<?, ?> u)) {
            throw new ServerConfig.Invalid(List.of(file + ": a users: map (name: {password, roles})"));
        }
        for (Map.Entry<?, ?> e : u.entrySet()) {
            String name = String.valueOf(e.getKey());
            if (!(e.getValue() instanceof Map<?, ?> one) || !(one.get("password") instanceof String h) || !h.startsWith(SCHEME + "$")) {
                problems.add(file + ": users." + name + ": a password hash (" + SCHEME + "$…, from hash-password)");
                continue;
            }
            Set<String> roles = new LinkedHashSet<>();
            if (one.get("roles") instanceof List<?> l) {
                l.forEach(r -> roles.add(String.valueOf(r)));
            } else if (one.get("roles") != null) {
                roles.add(String.valueOf(one.get("roles")));
            }
            out.put(name, new Entry(h, roles));
        }
        if (!problems.isEmpty()) {
            throw new ServerConfig.Invalid(problems);
        }
        users = Map.copyOf(out);
        read = t;
    }
}
