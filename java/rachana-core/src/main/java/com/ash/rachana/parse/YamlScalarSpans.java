/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.parse;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Every scalar of a YAML text with where it was written, for tools that rewrite a value in place and leave the rest of the file, its
 * comments and its layout, as they were (the {@code rachana upgrade} of rachana-tools). Read by the language's own YAML reader, so a
 * text it refuses is refused here too. Stateless, thread-safe.
 */
public final class YamlScalarSpans {

    private YamlScalarSpans() {}

    /**
     * One scalar.
     *
     * @param path the JSON Pointer of the node (a key's own path is its mapping's path plus the key)
     * @param value the scalar's value, as the reader gives it
     * @param start offset in the text (UTF-16 units) of its first character (a quote or a block indicator included)
     * @param end offset just after its last character
     * @param style {@code 0} plain, {@code '\''}, {@code '"'}, {@code '|'} or {@code '>'}
     * @param key true for a mapping key
     * @param line 1-based line of its start
     * @param column 1-based column of its start
     */
    public record Scalar(String path, String value, int start, int end, char style, boolean key, int line, int column) {}

    /**
     * The scalars of {@code yaml}, in the order written (aliases are not scalars).
     *
     * @throws IllegalArgumentException when the text is not YAML the language reads
     */
    public static List<Scalar> of(String yaml) {
        List<Scalar> out = new ArrayList<>();
        YamlEvents events = new YamlEvents(new YamlScanner(new YamlStream(yaml)));
        Deque<Frame> stack = new ArrayDeque<>();
        try {
            while (true) {
                YamlEvents.Event e = events.next();
                if (e == null || e.type() == YamlEvents.Type.STREAM_END) {
                    break;
                }
                switch (e.type()) {
                    case MAPPING_START, SEQUENCE_START -> {
                        String path = stack.isEmpty() ? "" : stack.peek().childPath();
                        if (!stack.isEmpty()) {
                            stack.peek().consumed();
                        }
                        stack.push(new Frame(path, e.type() == YamlEvents.Type.MAPPING_START));
                    }
                    case MAPPING_END, SEQUENCE_END -> stack.pop();
                    case SCALAR -> {
                        Frame f = stack.peek();
                        boolean key = f != null && f.map && f.expectKey;
                        String path = f == null ? "" : key ? f.path + "/" + escape(e.value()) : f.childPath();
                        out.add(new Scalar(path, e.value(), offset(yaml, e.start().index), offset(yaml, e.end().index), e.style(), key,
                                e.start().line + 1, e.start().column + 1));
                        if (f != null) {
                            if (key) {
                                f.key = e.value();
                                f.expectKey = false;
                            } else {
                                f.consumed();
                            }
                        }
                    }
                    case ALIAS -> {
                        if (!stack.isEmpty()) {
                            stack.peek().consumed();
                        }
                    }
                    default -> { }
                }
            }
        } catch (YamlScanner.Problem | YamlStream.Failure e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
        return Collections.unmodifiableList(out);
    }

    /** The UTF-16 offset of the {@code codePoints}-th code point (the reader counts code points). */
    private static int offset(String text, int codePoints) {
        try {
            return text.offsetByCodePoints(0, Math.min(codePoints, text.codePointCount(0, text.length())));
        } catch (IndexOutOfBoundsException e) {
            return text.length();
        }
    }

    private static String escape(String key) {
        return key == null ? "" : key.replace("~", "~0").replace("/", "~1");
    }

    /** One open mapping or sequence. */
    private static final class Frame {
        final String path;
        final boolean map;
        boolean expectKey = true;
        String key;
        int index;

        Frame(String path, boolean map) {
            this.path = path;
            this.map = map;
        }

        String childPath() {
            return path + "/" + (map ? escape(key) : String.valueOf(index));
        }

        void consumed() {
            if (map) {
                expectKey = true;
            } else {
                index++;
            }
        }
    }
}
