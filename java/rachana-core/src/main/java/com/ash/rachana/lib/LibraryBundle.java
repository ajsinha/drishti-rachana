/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.lib;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The files of one library version as a resolver found them (a folder, an archive, a map), before they are checked: path (with
 * {@code /}) to bytes. Its identity is the SHA-384 of its deterministic archive ({@link Rlib#write}), the same whether it was read
 * from a folder or from the {@code .rlib} that was packed from that folder (DESIGN.md (libraries) 12).
 *
 * @param name the library's name
 * @param files its files, sorted by path
 * @param source where it was found, for messages and the lock ({@code folder:rachana_libs/finance}, {@code release:finance-1.4.2.rlib})
 */
public record LibraryBundle(String name, Map<String, byte[]> files, String source) {

    private static final Pattern VERSION = Pattern.compile("(?m)^version:\\s*[\"']?([0-9]+\\.[0-9]+\\.[0-9]+)");

    public LibraryBundle {
        files = Collections.unmodifiableMap(new TreeMap<>(files));
    }

    /** A bundle of text files (path to UTF-8 text). */
    public static LibraryBundle ofText(String name, Map<String, String> files, String source) {
        Map<String, byte[]> b = new TreeMap<>();
        files.forEach((k, v) -> b.put(k, v.getBytes(StandardCharsets.UTF_8)));
        return new LibraryBundle(name, b, source);
    }

    /** A bundle of files as read (path to bytes). */
    public static LibraryBundle ofBytes(String name, Map<String, byte[]> files, String source) {
        return new LibraryBundle(name, files, source);
    }

    /**
     * The bundle an archive holds. Only the canonical archive of its files is accepted: an archive written by any other tool, or changed
     * after packing, has another identity than the one its files give.
     *
     * @throws IllegalArgumentException when the bytes are not a canonical {@code .rlib}
     */
    public static LibraryBundle archive(String name, byte[] rlib, String source) {
        Map<String, byte[]> files = Rlib.read(rlib);
        if (!java.util.Arrays.equals(Rlib.write(files), rlib)) {
            throw new IllegalArgumentException(source + " is not a canonical .rlib (pack it with rachana lib pack)");
        }
        return new LibraryBundle(name, files, source);
    }

    /** The manifest's file name: {@code <name>.rachana-lib.yaml} at the root. */
    public String manifestPath() {
        return name + ".rachana-lib.yaml";
    }

    /** The manifest's text, or null when there is none. */
    public String manifest() {
        byte[] m = files.get(manifestPath());
        return m == null ? null : new String(m, StandardCharsets.UTF_8);
    }

    /** The version the manifest states ({@code 1.4.2}), read without checking the rest; null when it states none. */
    public String version() {
        String m = manifest();
        Matcher v = m == null ? null : VERSION.matcher(m);
        return v != null && v.find() ? v.group(1) : null;
    }

    /** Its identity: SHA-384 of its canonical archive, 96 hex digits. */
    public String sha384() {
        return Sha384.hex(Rlib.write(files));
    }

    /** The canonical archive of its files. */
    public byte[] archive() {
        return Rlib.write(files);
    }
}
