/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.parse;

import java.util.Arrays;

/**
 * The characters of a YAML text as code points, with the line, column and offset of the reading position.
 *
 * <p>The text is taken in chunks of 1,023 UTF-16 units (one more when a chunk ends inside a surrogate pair), and a
 * {@link Mark}'s snippet sees only the chunk window that was loaded when the mark was taken: this is how the reader the
 * language was first defined against reported errors (the snippet of an error far into a long file can start mid-line),
 * and error texts are part of the language at level 1.0. Code points that are not printable are refused when their chunk
 * is loaded. Not thread-safe: one per read.
 */
final class YamlStream {

    /** The name an error text gives the input. */
    static final String NAME = "'reader'";

    private static final int CHUNK = 1023;

    private final String src;
    private int consumed;
    private int[] data = new int[64];
    private int loaded;
    private int windowStart;
    private int pointer;
    private boolean eof;
    private int index;
    private int line;
    private int column;

    YamlStream(String src) {
        this.src = src;
    }

    /** A place in the text, 0-based, with the window its snippet is cut from. */
    static final class Mark {
        final int index;
        final int line;
        final int column;
        private final int[] data;
        private final int windowStart;
        private final int windowEnd;
        private final int pointer;

        Mark(int index, int line, int column, int[] data, int windowStart, int windowEnd, int pointer) {
            this.index = index;
            this.line = line;
            this.column = column;
            this.data = data;
            this.windowStart = windowStart;
            this.windowEnd = windowEnd;
            this.pointer = pointer;
        }

        private static boolean isLineBreak(int c) {
            return c == 0 || c == '\r' || c == '\n' || c == 0x85 || c == 0x2028 || c == 0x2029;
        }

        private int at(int i) {
            return data[windowStart + i];
        }

        String snippet() {
            int length = windowEnd - windowStart;
            int p = pointer - windowStart;
            float half = 75 / 2f - 1f;
            int start = p;
            String head = "";
            while (start > 0 && !isLineBreak(at(start - 1))) {
                start -= 1;
                if (p - start > half) {
                    head = " ... ";
                    start += 5;
                    break;
                }
            }
            String tail = "";
            int end = p;
            while (end < length && !isLineBreak(at(end))) {
                end += 1;
                if (end - p > half) {
                    tail = " ... ";
                    end -= 5;
                    break;
                }
            }
            StringBuilder out = new StringBuilder("    ").append(head);
            for (int i = start; i < end; i++) {
                out.appendCodePoint(at(i));
            }
            out.append(tail).append('\n');
            for (int i = 0; i < 4 + p - start + head.length(); i++) {
                out.append(' ');
            }
            return out.append('^').toString();
        }

        @Override
        public String toString() {
            return " in " + NAME + ", line " + (line + 1) + ", column " + (column + 1) + ":\n" + snippet();
        }
    }

    /** A failure of the reader itself (not a marked syntax error). */
    static final class Failure extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Failure(String message) {
            super(message, null, false, false);
        }
    }

    static boolean isPrintable(int c) {
        return c >= 0x20 && c <= 0x7E || c == 0x9 || c == 0xA || c == 0xD || c == 0x85 || c >= 0xA0 && c <= 0xD7FF
                || c >= 0xE000 && c <= 0xFFFD || c >= 0x10000 && c <= 0x10FFFF;
    }

    Mark mark() {
        return new Mark(index, line, column, data, windowStart, loaded, pointer);
    }

    int index() {
        return index;
    }

    int line() {
        return line;
    }

    int column() {
        return column;
    }

    int peek() {
        return ensure(0) ? data[pointer] : 0;
    }

    int peek(int at) {
        return ensure(at) ? data[pointer + at] : 0;
    }

    String prefix(int length) {
        if (length == 0) {
            return "";
        }
        int n = ensure(length) ? length : Math.min(length, loaded - pointer);
        return new String(data, pointer, n);
    }

    String prefixForward(int length) {
        String prefix = prefix(length);
        pointer += length;
        index += length;
        column += length;
        return prefix;
    }

    void forward() {
        forward(1);
    }

    void forward(int length) {
        for (int i = 0; i < length && ensure(0); i++) {
            int c = data[pointer++];
            index++;
            if (c == '\n' || c == 0x85 || c == 0x2028 || c == 0x2029 || c == '\r' && ensure(0) && data[pointer] != '\n') {
                line++;
                column = 0;
            } else if (c != 0xFEFF) {
                column++;
            }
        }
    }

    private boolean ensure(int size) {
        if (!eof && pointer + size >= loaded) {
            update();
        }
        return pointer + size < loaded;
    }

    private void update() {
        if (consumed >= src.length()) {
            eof = true;
            return;
        }
        int start = consumed;
        consumed = Math.min(src.length(), consumed + CHUNK);
        windowStart = pointer;
        if (Character.isHighSurrogate(src.charAt(consumed - 1))) {
            if (consumed >= src.length()) {
                throw new Failure("The last char is HighSurrogate (no LowSurrogate detected).");
            }
            consumed++;
        }
        for (int i = start; i < consumed;) {
            int cp = Character.codePointAt(src, i);
            if (Character.isSupplementaryCodePoint(cp) && i + 1 >= consumed) {
                cp = src.charAt(i);
            }
            if (loaded == data.length) {
                data = Arrays.copyOf(data, Math.max(64, data.length * 2));
            }
            data[loaded++] = cp;
            if (!isPrintable(cp)) {
                throw new Failure("special characters are not allowed");
            }
            i += Character.charCount(cp);
        }
    }
}
