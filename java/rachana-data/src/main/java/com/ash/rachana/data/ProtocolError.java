/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A Data Protocol error (section 10.1): the code, the HTTP status and the error object's other fields ({@code table}, {@code column},
 * {@code expected}, {@code operation}, {@code retry}). Its message never contains row values.
 */
public final class ProtocolError extends RuntimeException {

    private final String code;
    private final int status;
    private final Map<String, Object> fields;

    public ProtocolError(String code, int status, String message, Map<String, Object> fields) {
        super(message, null, false, false);
        this.code = code;
        this.status = status;
        this.fields = fields == null ? Map.of() : fields;
    }

    public static ProtocolError of(String code, int status, String message, Object... kv) {
        Map<String, Object> f = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            f.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return new ProtocolError(code, status, message, f);
    }

    public String code() {
        return code;
    }

    public int status() {
        return status;
    }

    /** The error object: {@code {code, message, ...fields}}. */
    public Map<String, Object> error() {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("code", code);
        e.put("message", getMessage());
        e.putAll(fields);
        return e;
    }

    /** The HTTP body: {@code {"error": {...}}}. */
    public Map<String, Object> body() {
        return Map.of("error", error());
    }
}
