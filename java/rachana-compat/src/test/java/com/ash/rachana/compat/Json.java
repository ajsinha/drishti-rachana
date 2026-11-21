/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.compat;

import java.math.BigDecimal;
import java.util.*;

/** A small JSON reader and canonical writer for the gates' own files (no library: the modules carry none). Objects keep their order. */
final class Json {
    private final String s;
    private int i;

    private Json(String s) { this.s = s; }

    static Object parse(String text) {
        Json j = new Json(text);
        j.ws();
        Object v = j.value();
        j.ws();
        if (j.i != j.s.length()) throw j.err("trailing text");
        return v;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Object o) { return (Map<String, Object>) o; }

    @SuppressWarnings("unchecked")
    static List<Object> array(Object o) { return (List<Object>) o; }

    /** Canonical text: object keys sorted, no white space; numbers as written by BigDecimal.toPlainString for integers. */
    static String canonical(Object v) {
        StringBuilder b = new StringBuilder();
        write(v, b);
        return b.toString();
    }

    /** Text in the objects' own key order (no white space): for comparing two documents where key order matters. */
    static String ordered(Object v) {
        StringBuilder b = new StringBuilder();
        write(v, b, false);
        return b.toString();
    }

    private static void write(Object v, StringBuilder b) {
        write(v, b, true);
    }

    private static void write(Object v, StringBuilder b, boolean sorted) {
        if (v == null) b.append("null");
        else if (v instanceof Map<?, ?> m) {
            b.append('{');
            boolean first = true;
            for (Object k : sorted ? new TreeSet<>(m.keySet().stream().map(String::valueOf).toList()) : m.keySet()) {
                if (!first) b.append(',');
                first = false;
                quote(k.toString(), b);
                b.append(':');
                write(m.get(k), b, sorted);
            }
            b.append('}');
        } else if (v instanceof List<?> l) {
            b.append('[');
            for (int n = 0; n < l.size(); n++) {
                if (n > 0) b.append(',');
                write(l.get(n), b, sorted);
            }
            b.append(']');
        } else if (v instanceof String str) quote(str, b);
        else if (v instanceof BigDecimal d && !sorted) b.append(d);   // as written: 1.0 stays 1.0
        else if (v instanceof BigDecimal d) b.append(d.stripTrailingZeros().scale() <= 0 ? d.toBigInteger().toString() : d.toPlainString());
        else b.append(v);
    }

    private static void quote(String str, StringBuilder b) {
        b.append('"');
        for (char c : str.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> { if (c < 0x20) b.append(String.format("\\u%04x", (int) c)); else b.append(c); }
            }
        }
        b.append('"');
    }

    private Object value() {
        if (i >= s.length()) throw err("unexpected end");
        char c = s.charAt(i);
        switch (c) {
            case '{': {
                i++;
                Map<String, Object> m = new LinkedHashMap<>();
                ws();
                if (s.charAt(i) == '}') { i++; return m; }
                while (true) {
                    ws();
                    String k = string();
                    ws(); expect(':'); ws();
                    if (m.put(k, value()) != null) throw err("duplicate key " + k);
                    ws();
                    if (s.charAt(i) == ',') { i++; continue; }
                    expect('}');
                    return m;
                }
            }
            case '[': {
                i++;
                List<Object> l = new ArrayList<>();
                ws();
                if (s.charAt(i) == ']') { i++; return l; }
                while (true) {
                    ws();
                    l.add(value());
                    ws();
                    if (s.charAt(i) == ',') { i++; continue; }
                    expect(']');
                    return l;
                }
            }
            case '"': return string();
            case 't': lit("true"); return Boolean.TRUE;
            case 'f': lit("false"); return Boolean.FALSE;
            case 'n': lit("null"); return null;
            default: {
                int st = i;
                while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
                if (st == i) throw err("unexpected character " + c);
                return new BigDecimal(s.substring(st, i));
            }
        }
    }

    private String string() {
        expect('"');
        StringBuilder b = new StringBuilder();
        while (true) {
            char c = s.charAt(i++);
            if (c == '"') return b.toString();
            if (c != '\\') { b.append(c); continue; }
            char e = s.charAt(i++);
            switch (e) {
                case 'n' -> b.append('\n');
                case 'r' -> b.append('\r');
                case 't' -> b.append('\t');
                case 'b' -> b.append('\b');
                case 'f' -> b.append('\f');
                case 'u' -> { b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; }
                default -> b.append(e);
            }
        }
    }

    private void lit(String w) {
        if (!s.startsWith(w, i)) throw err("expected " + w);
        i += w.length();
    }

    private void expect(char c) {
        if (i >= s.length() || s.charAt(i) != c) throw err("expected '" + c + "'");
        i++;
    }

    private void ws() { while (i < s.length() && " \t\r\n".indexOf(s.charAt(i)) >= 0) i++; }

    private IllegalArgumentException err(String m) { return new IllegalArgumentException("JSON: " + m + " at " + i); }
}
