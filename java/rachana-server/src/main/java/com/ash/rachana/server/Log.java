/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.server;

import com.ash.rachana.parse.Json;
import java.io.PrintStream;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Structured logs: one JSON object per line ({@code at}, {@code level}, {@code event}, then the fields given). No logging framework.
 * A field whose name speaks of a secret (password, token, cookie, session, secret, authorization, csrf) is never written: its value is
 * replaced by {@code "[redacted]"}, so a careless call cannot leak one. Thread-safe.
 */
final class Log {

    static final Pattern SECRET = Pattern.compile("(?i).*(password|passwd|token|cookie|session|secret|authorization|csrf|hash).*");

    private final PrintStream out;
    private final Clock clock;

    Log(PrintStream out, Clock clock) {
        this.out = out;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    void info(String event, Object... fields) {
        write("info", event, fields);
    }

    void warn(String event, Object... fields) {
        write("warn", event, fields);
    }

    void error(String event, Object... fields) {
        write("error", event, fields);
    }

    /** One line; {@code fields} are name, value pairs. */
    String line(String level, String event, Object... fields) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("at", clock.instant().toString());
        m.put("level", level);
        m.put("event", event);
        for (int i = 0; i + 1 < fields.length; i += 2) {
            String k = String.valueOf(fields[i]);
            Object v = fields[i + 1];
            m.put(k, SECRET.matcher(k).matches() ? "[redacted]" : v instanceof Number || v instanceof Boolean || v == null ? v
                    : String.valueOf(v));
        }
        return Json.write(m);
    }

    private void write(String level, String event, Object... fields) {
        String l = line(level, event.toLowerCase(Locale.ROOT), fields);
        synchronized (out) {
            out.println(l);
        }
    }
}
