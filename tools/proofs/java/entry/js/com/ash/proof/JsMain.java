/*
 * Copyright (c) 2026 Ashutosh Sinha. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.proof;

import org.teavm.jso.JSExport;

/** TeaVM entry for the JavaScript build: the exports the runners and benches call. */
public final class JsMain {
    private JsMain() {
    }

    public static void main(String[] args) {
    }

    @JSExport
    public static String runAll(String jsonl) {
        return Harness.runAll(jsonl);
    }

    @JSExport
    public static String runLine(String line) {
        return Harness.runLine(line);
    }

    @JSExport
    public static int compileSutra(String pnodeDoc) {
        return Bench.compileSutra(pnodeDoc);
    }

    @JSExport
    public static int load(String json) {
        return Bench.load(json);
    }

    @JSExport
    public static int evalLoop(String expr, int reps) {
        return Bench.evalLoop(expr, reps);
    }

    @JSExport
    public static String evalOne(String expr, int row) {
        return Bench.evalOne(expr, row);
    }

    @JSExport
    public static int liveChange(String expr, int changed, int seed) {
        return Bench.liveChange(expr, changed, seed);
    }

    @JSExport
    public static int liveChangeRowsOnly(String expr, int changed, int seed) {
        return Bench.liveChangeRowsOnly(expr, changed, seed);
    }
}
