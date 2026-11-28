/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.lib;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The {@code .rlib} archive (DESIGN.md (libraries) 11.1): a ZIP of a library folder written <b>deterministically</b>, so that the same files
 * give the same bytes, and so the same SHA-384, on every machine and in every runtime. Entries are sorted by path, stored without
 * compression (no deflater whose output could differ between implementations), with the fixed time 1980-01-01 00:00, the UTF-8 flag,
 * no extra fields and no comment. Any ZIP tool reads it. The reader takes only this shape (stored entries, no ZIP64): a library is
 * at most 5 MB. Stateless; thread-safe.
 */
public final class Rlib {

    /** The largest library, unpacked (DESIGN.md (libraries) 18). */
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final int[] CRC = new int[256];

    static {
        for (int n = 0; n < 256; n++) {
            int c = n;
            for (int k = 0; k < 8; k++) {
                c = (c & 1) != 0 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
            }
            CRC[n] = c;
        }
    }

    private Rlib() {
    }

    /** The archive of these files (path to bytes; paths use {@code /}). */
    public static byte[] write(Map<String, byte[]> files) {
        SortedMap<String, byte[]> sorted = new TreeMap<>(files);
        Out out = new Out();
        Out central = new Out();
        for (Map.Entry<String, byte[]> e : sorted.entrySet()) {
            byte[] name = e.getKey().getBytes(StandardCharsets.UTF_8);
            byte[] data = e.getValue();
            int crc = crc32(data);
            int offset = out.size;
            out.u32(0x04034b50).u16(10).u16(0x0800).u16(0).u16(0).u16(0x21).u32(crc).u32(data.length).u32(data.length)
                    .u16(name.length).u16(0).bytes(name).bytes(data);
            central.u32(0x02014b50).u16(20).u16(10).u16(0x0800).u16(0).u16(0).u16(0x21).u32(crc).u32(data.length).u32(data.length)
                    .u16(name.length).u16(0).u16(0).u16(0).u16(0).u32(0).u32(offset).bytes(name);
        }
        int cdOffset = out.size;
        out.bytes(central.toArray());
        out.u32(0x06054b50).u16(0).u16(0).u16(sorted.size()).u16(sorted.size()).u32(central.size).u32(cdOffset).u16(0);
        return out.toArray();
    }

    /**
     * The files of an archive, in its order.
     *
     * @throws IllegalArgumentException when the bytes are not an archive of this shape (the message says what is wrong)
     */
    public static Map<String, byte[]> read(byte[] zip) {
        int eocd = zip.length - 22;
        if (eocd < 0 || u32(zip, eocd) != 0x06054b50) {
            throw new IllegalArgumentException("not a .rlib archive (no end record, or a comment after it)");
        }
        int count = u16(zip, eocd + 10);
        int at = u32(zip, eocd + 16);
        Map<String, byte[]> files = new LinkedHashMap<>();
        long total = 0;
        for (int i = 0; i < count; i++) {
            if (at < 0 || at + 46 > zip.length || u32(zip, at) != 0x02014b50) {
                throw new IllegalArgumentException("not a .rlib archive (a damaged directory entry)");
            }
            int method = u16(zip, at + 10);
            int size = u32(zip, at + 24);
            int nameLen = u16(zip, at + 28);
            int extra = u16(zip, at + 30);
            int comment = u16(zip, at + 32);
            int local = u32(zip, at + 42);
            String name = new String(zip, at + 46, nameLen, StandardCharsets.UTF_8);
            if (method != 0 || size != u32(zip, at + 20)) {
                throw new IllegalArgumentException("the entry " + name + " is compressed: a .rlib stores its files (rachana lib pack)");
            }
            total += size;
            if (size < 0 || total > MAX_BYTES) {
                throw new IllegalArgumentException("the library is larger than " + MAX_BYTES + " bytes unpacked");
            }
            if (local < 0 || local + 30 > zip.length || u32(zip, local) != 0x04034b50) {
                throw new IllegalArgumentException("not a .rlib archive (a damaged entry " + name + ")");
            }
            int data = local + 30 + u16(zip, local + 26) + u16(zip, local + 28);
            if (data + size > zip.length) {
                throw new IllegalArgumentException("not a .rlib archive (the entry " + name + " is cut short)");
            }
            byte[] bytes = new byte[size];
            System.arraycopy(zip, data, bytes, 0, size);
            if (crc32(bytes) != u32(zip, at + 16)) {
                throw new IllegalArgumentException("the entry " + name + " does not match its checksum");
            }
            if (files.put(name, bytes) != null) {
                throw new IllegalArgumentException("the entry " + name + " is in the archive twice");
            }
            at += 46 + nameLen + extra + comment;
        }
        return files;
    }

    /** CRC-32 (ISO 3309), as ZIP uses. */
    static int crc32(byte[] data) {
        int c = 0xffffffff;
        for (byte b : data) {
            c = CRC[(c ^ b) & 0xff] ^ (c >>> 8);
        }
        return ~c;
    }

    /** Lower-case hex. */
    public static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
        }
        return sb.toString();
    }

    private static int u16(byte[] b, int at) {
        return at + 2 > b.length ? -1 : (b[at] & 0xff) | (b[at + 1] & 0xff) << 8;
    }

    private static int u32(byte[] b, int at) {
        return at < 0 || at + 4 > b.length ? -1 : (b[at] & 0xff) | (b[at + 1] & 0xff) << 8 | (b[at + 2] & 0xff) << 16 | (b[at + 3] & 0xff) << 24;
    }

    private static final class Out {
        private byte[] buf = new byte[1024];
        private int size;

        Out u16(int v) {
            return bytes(new byte[] {(byte) v, (byte) (v >>> 8)});
        }

        Out u32(int v) {
            return bytes(new byte[] {(byte) v, (byte) (v >>> 8), (byte) (v >>> 16), (byte) (v >>> 24)});
        }

        Out bytes(byte[] b) {
            if (size + b.length > buf.length) {
                byte[] n = new byte[Math.max(buf.length * 2, size + b.length)];
                System.arraycopy(buf, 0, n, 0, size);
                buf = n;
            }
            System.arraycopy(b, 0, buf, size, b.length);
            size += b.length;
            return this;
        }

        byte[] toArray() {
            byte[] r = new byte[size];
            System.arraycopy(buf, 0, r, 0, size);
            return r;
        }
    }
}
