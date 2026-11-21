/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import com.ash.rachana.api.Node;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * The values a screen's state holds (SUTRA_EXPANSION S1, level 1.2), one rule for every source: a default written in the Sutra, the
 * text a bind request carries ({@code state.<id>=...}) and the value an effect computes ({@code set: { state, to }}). By type:
 * {@code text} is text; {@code number} an exact decimal (SPEC 4.16); {@code boolean} {@code true} or {@code false}; {@code date} an ISO
 * date text ({@code 2026-11-21}); {@code list} a list of text (in a request, its items joined by commas). Empty text and null are empty
 * (null) for every type. A value that does not fit its type is refused ({@link IllegalArgumentException}); the caller keeps the default
 * (a request) or sets nothing (an effect).
 */
public final class StateTypes {

    private StateTypes() {}

    /** The value {@code v} as state of {@code type}: a value an expression gives, a default, or a request's text. */
    public static Object of(String type, Object v) {
        if (Values.masked(v)) {
            throw new IllegalArgumentException("a masked value is never written into state");
        }
        Object x = plain(v);
        if (x == null || Values.isNull(x) || x instanceof String s && s.isEmpty() && !"list".equals(type)) {
            return null;
        }
        switch (type) {
            case "text":
                if (x instanceof List<?> || x instanceof java.util.Map<?, ?>) {
                    throw new IllegalArgumentException("a text state takes text, not a list or a record");
                }
                return Values.text(x);
            case "number":
                if (x instanceof Boolean || x instanceof List<?> || x instanceof java.util.Map<?, ?>) {
                    throw new IllegalArgumentException("a number state takes a number");
                }
                String t = x instanceof Number n ? Values.text(n) : String.valueOf(x).trim();
                try {
                    return Exact.literal(t, 0, t.length());
                } catch (NumberFormatException | ElException e) {
                    throw new IllegalArgumentException("'" + t + "' is not a number");
                }
            case "boolean":
                if (x instanceof Boolean b) {
                    return b;
                }
                if ("true".equals(x) || "false".equals(x)) {
                    return Boolean.valueOf((String) x);
                }
                throw new IllegalArgumentException("a boolean state is true or false, not '" + Values.text(x) + "'");
            case "date":
                String d = x instanceof LocalDate ld ? ld.toString() : x instanceof String s ? s.trim() : null;
                try {
                    return d == null ? fail("a date state takes an ISO date (2026-11-21)") : LocalDate.parse(d).toString();
                } catch (DateTimeParseException e) {
                    throw new IllegalArgumentException("'" + d + "' is not an ISO date (2026-11-21)");
                }
            case "list":
                List<String> out = new ArrayList<>();
                if (x instanceof List<?> l) {
                    for (Object o : l) {
                        Object e = plain(o);
                        if (e instanceof List<?> || e instanceof java.util.Map<?, ?>) {
                            throw new IllegalArgumentException("a list state holds text, not lists or records");
                        }
                        out.add(e == null ? "" : Values.text(e));
                    }
                } else if (x instanceof String s) {
                    if (!s.isEmpty()) {
                        for (String part : s.split(",", -1)) {
                            out.add(part);
                        }
                    }
                } else {
                    out.add(Values.text(x));
                }
                return Node.of(out);
            default:
                throw new IllegalArgumentException("unknown state type '" + type + "'");
        }
    }

    /** The value as the ViewModel writes it ({@code state[].value}): text, a number, a boolean, a list of text, or null. */
    public static Object json(Object v) {
        Object x = plain(v);
        if (x instanceof List<?> l) {
            List<String> out = new ArrayList<>();
            l.forEach(o -> {
                Object e = plain(o);
                out.add(e == null ? "" : Values.text(e));
            });
            return out;
        }
        return x;
    }

    /** A node as a plain value: a scalar's value, an array's elements as a list, a record as a map; anything else as itself. */
    private static Object plain(Object v) {
        if (v instanceof Node n) {
            if (n.isMissing() || n.isNull()) {
                return null;
            }
            if (n.isArray()) {
                return new ArrayList<Object>(n.elements());
            }
            Object s = Values.simplify(n);
            return s instanceof Node ? n.fields() : s;
        }
        return v;
    }

    /** Whether {@code type} is a state type. */
    public static boolean known(String type) {
        return com.ash.rachana.model.StateEntry.TYPES.contains(type);
    }

    private static Object fail(String why) {
        throw new IllegalArgumentException(why);
    }
}
