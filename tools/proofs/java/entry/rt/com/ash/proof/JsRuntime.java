/*
 * Copyright (c) 2026 Ashutosh Sinha. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.proof;

import java.util.Map;
import org.teavm.jso.JSExport;

/**
 * T2's runtime-shaped entry: evaluation only (compile an expression or template and evaluate it over a context), the share of
 * the P1 cut a page needs to evaluate. No Sutra parser, model, checker or problem path is reachable from here.
 */
public final class JsRuntime {
    private JsRuntime() {
    }

    public static void main(String[] args) {
    }

    @JSExport
    @SuppressWarnings("unchecked")
    public static String evalLine(String line) {
        Map<String, Object> c = (Map<String, Object>) Json.parse(line);
        StringBuilder a = new StringBuilder();
        Harness.expr(c, c.get("given") instanceof Map ? (Map<String, Object>) c.get("given") : Map.of(), a);
        return a.toString();
    }
}
