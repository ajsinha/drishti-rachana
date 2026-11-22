/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.parse;

import com.ash.rachana.internal.NumberText;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Core's JSON reader and writer, over plain values: {@code Map<String, Object>} (written order), {@code List<Object>},
 * {@code String}, {@code Long} (a {@code BigInteger} beyond a {@code long}), {@code Double}, {@code Boolean} and null
 * (the writer also takes {@code Integer} and {@code BigDecimal}).
 *
 * <p>The reader takes one JSON value (RFC 8259) and ignores what follows it; a key written twice keeps its first place
 * and its last value. The writer writes compact JSON: no spaces, keys in the map's order, {@code "} and {@code \} and
 * control characters escaped ({@code \b \t \n \f \r}, others as {@code \\u00XX}), other characters as they are; a
 * non-finite double is written as a string ({@code "NaN"}). Both are the forms the language's JSON had from the start.
 * Thread-safe: static methods, no shared state.
 */
public final class Json {

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();
    private static final int MAX_DEPTH = 1000;

    private Json() {}

    /** JSON that cannot be read; the message says what and where (1-based line and column). */
    public static final class JsonException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final int line;
        private final int column;

        JsonException(String message, int line, int column) {
            super(message, null, false, false);
            this.line = line;
            this.column = column;
        }

        public int line() {
            return line;
        }

        public int column() {
            return column;
        }
    }

    // ---- writing ----

    /** The value as compact JSON. */
    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out);
        return out.toString();
    }

    /** Appends the value as compact JSON. */
    public static void write(Object value, StringBuilder out) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String s) {
            quote(s, out);
        } else if (value instanceof Boolean b) {
            out.append(b.booleanValue());
        } else if (value instanceof Double || value instanceof Float) {
            double d = NumberText.toDouble((Number) value);
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                quote(value instanceof Float f ? NumberText.text(f) : NumberText.text(d), out);
            } else {
                out.append(value instanceof Float f ? NumberText.text(f) : NumberText.text(d));
            }
        } else if (value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte
                || value instanceof BigInteger) {
            out.append(value);
        } else if (value instanceof BigDecimal bd) {
            out.append(bd.toString());
        } else if (value instanceof Map<?, ?> m) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                quote(String.valueOf(e.getKey()), out);
                out.append(':');
                write(e.getValue(), out);
            }
            out.append('}');
        } else if (value instanceof List<?> l) {
            out.append('[');
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                write(l.get(i), out);
            }
            out.append(']');
        } else if (value instanceof Character c) {
            quote(String.valueOf(c), out);
        } else {
            throw new IllegalArgumentException("not a JSON value: " + value.getClass().getName());
        }
    }

    /** The text as a JSON string, quotes included. */
    public static String quote(String s) {
        StringBuilder out = new StringBuilder(s.length() + 2);
        quote(s, out);
        return out.toString();
    }

    /** Appends the text as a JSON string, quotes included. */
    public static void quote(String s, StringBuilder out) {
        out.append('"');
        escape(s, out);
        out.append('"');
    }

    /** Appends the text escaped for inside a JSON string, without quotes. */
    public static void escape(String s, StringBuilder out) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\t' -> out.append("\\t");
                case '\n' -> out.append("\\n");
                case '\f' -> out.append("\\f");
                case '\r' -> out.append("\\r");
                default -> {
                    if (c < 0x20) {
                        out.append("\\u00").append(HEX[c >> 4]).append(HEX[c & 0xF]);
                    } else {
                        out.append(c);
                    }
                }
            }
        }
    }

    // ---- reading ----

    /**
     * Reads one JSON value.
     *
     * @return the value, or null when the text holds only white space (as well as for {@code null})
     * @throws JsonException when the text is not JSON, or holds more than one value
     */
    public static Object read(String text) {
        Reader r = new Reader(text);
        r.space();
        if (r.pos >= text.length()) {
            return null;
        }
        Object v = r.value(0);
        r.space();
        if (r.pos < text.length()) {                              // the whole text is one value: what follows it is refused, never dropped
            throw r.fail("unexpected " + r.shown(text.charAt(r.pos)) + " after the JSON value");
        }
        return v;
    }

    private static final class Reader {
        private final String s;
        private int pos;
        private int line = 1;
        private int lineStart;

        Reader(String s) {
            this.s = s;
        }

        private JsonException fail(String message) {
            return new JsonException(message, line, pos - lineStart + 1);
        }

        private String shown(int c) {
            return c < 0 ? "end of input" : "'" + (char) c + "' (code " + c + ")";
        }

        private int peek() {
            return pos < s.length() ? s.charAt(pos) : -1;
        }

        void space() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == '\n') {
                    line++;
                    lineStart = pos + 1;
                } else if (c != ' ' && c != '\t' && c != '\r') {
                    return;
                }
                pos++;
            }
        }

        Object value(int depth) {
            if (depth > MAX_DEPTH) {
                throw fail("nesting deeper than " + MAX_DEPTH);
            }
            int c = peek();
            switch (c) {
                case '{' -> {
                    pos++;
                    return object(depth);
                }
                case '[' -> {
                    pos++;
                    return array(depth);
                }
                case '"' -> {
                    pos++;
                    return string();
                }
                case 't' -> {
                    word("true");
                    return Boolean.TRUE;
                }
                case 'f' -> {
                    word("false");
                    return Boolean.FALSE;
                }
                case 'n' -> {
                    word("null");
                    return null;
                }
                default -> {
                    if (c == '-' || c >= '0' && c <= '9') {
                        return number();
                    }
                    throw fail("Unexpected character (" + shown(c)
                            + "): expected a valid value (JSON String, Number, Array, Object or token 'null', 'true' or 'false')");
                }
            }
        }

        private void word(String w) {
            if (!s.startsWith(w, pos) || pos + w.length() < s.length() && Character.isLetterOrDigit(s.charAt(pos + w.length()))) {
                int end = pos;
                while (end < s.length() && Character.isLetterOrDigit(s.charAt(end))) {
                    end++;
                }
                throw fail("Unrecognized token '" + s.substring(pos, Math.max(end, pos + 1))
                        + "': was expecting (JSON String, Number, Array, Object or token 'null', 'true' or 'false')");
            }
            pos += w.length();
        }

        private Map<String, Object> object(int depth) {
            Map<String, Object> m = new LinkedHashMap<>();
            space();
            if (peek() == '}') {
                pos++;
                return m;
            }
            while (true) {
                space();
                if (peek() != '"') {
                    throw fail("Unexpected character (" + shown(peek()) + "): was expecting double-quote to start field name");
                }
                pos++;
                String key = string();
                space();
                if (peek() != ':') {
                    throw fail("Unexpected character (" + shown(peek()) + "): was expecting a colon to separate field name and value");
                }
                pos++;
                space();
                m.put(key, value(depth + 1));
                space();
                int c = peek();
                if (c == ',') {
                    pos++;
                } else if (c == '}') {
                    pos++;
                    return m;
                } else {
                    throw fail("Unexpected character (" + shown(c) + "): was expecting comma to separate Object entries");
                }
            }
        }

        private List<Object> array(int depth) {
            List<Object> l = new ArrayList<>();
            space();
            if (peek() == ']') {
                pos++;
                return l;
            }
            while (true) {
                space();
                l.add(value(depth + 1));
                space();
                int c = peek();
                if (c == ',') {
                    pos++;
                } else if (c == ']') {
                    pos++;
                    return l;
                } else {
                    throw fail("Unexpected character (" + shown(c) + "): was expecting comma to separate Array entries");
                }
            }
        }

        private String string() {
            StringBuilder out = new StringBuilder();
            while (true) {
                if (pos >= s.length()) {
                    throw fail("Unexpected end-of-input: was expecting closing quote for a string value");
                }
                char c = s.charAt(pos++);
                if (c == '"') {
                    return out.toString();
                }
                if (c == '\\') {
                    if (pos >= s.length()) {
                        throw fail("Unexpected end-of-input in character escape sequence");
                    }
                    char e = s.charAt(pos++);
                    switch (e) {
                        case '"', '\\', '/' -> out.append(e);
                        case 'b' -> out.append('\b');
                        case 'f' -> out.append('\f');
                        case 'n' -> out.append('\n');
                        case 'r' -> out.append('\r');
                        case 't' -> out.append('\t');
                        case 'u' -> {
                            int v = 0;
                            for (int k = 0; k < 4; k++) {
                                int d = pos < s.length() ? Character.digit(s.charAt(pos), 16) : -1;
                                if (d < 0 || s.charAt(pos) > 'f') {
                                    throw fail("Unexpected character (" + shown(peek()) + "): expected a hex-digit for character escape sequence");
                                }
                                v = v * 16 + d;
                                pos++;
                            }
                            out.append((char) v);
                        }
                        default -> throw fail("Unrecognized character escape " + shown(e));
                    }
                } else if (c < 0x20) {
                    pos--;
                    throw fail("Illegal unquoted character (" + shown(c) + "): has to be escaped using backslash to be included in string value");
                } else {
                    out.append(c);
                }
            }
        }

        private Object number() {
            int start = pos;
            if (peek() == '-') {
                pos++;
            }
            int intStart = pos;
            while (peek() >= '0' && peek() <= '9') {
                pos++;
            }
            if (pos == intStart) {
                throw fail("Unexpected character (" + shown(peek()) + ") in numeric value: expected digit (0-9) to follow minus sign, for valid numeric value");
            }
            if (s.charAt(intStart) == '0' && pos - intStart > 1) {
                throw fail("Invalid numeric value: Leading zeroes not allowed");
            }
            boolean decimal = false;
            if (peek() == '.') {
                decimal = true;
                pos++;
                int f = pos;
                while (peek() >= '0' && peek() <= '9') {
                    pos++;
                }
                if (pos == f) {
                    throw fail("Unexpected character (" + shown(peek()) + ") in numeric value: Decimal point not followed by a digit");
                }
            }
            if (peek() == 'e' || peek() == 'E') {
                decimal = true;
                pos++;
                if (peek() == '+' || peek() == '-') {
                    pos++;
                }
                int e = pos;
                while (peek() >= '0' && peek() <= '9') {
                    pos++;
                }
                if (pos == e) {
                    throw fail("Unexpected character (" + shown(peek()) + ") in numeric value: Exponent indicator not followed by a digit");
                }
            }
            String text = s.substring(start, pos);
            if (decimal) {
                return NumberText.parse(text);
            }
            int digits = pos - intStart;
            if (digits <= 18) {
                return Long.parseLong(text);
            }
            BigInteger n = new BigInteger(text);
            return n.bitLength() <= 63 ? (Object) n.longValue() : n;
        }
    }
}
