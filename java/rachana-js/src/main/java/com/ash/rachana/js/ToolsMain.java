/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.js;

import com.ash.rachana.conformance.Cases;
import com.ash.rachana.parse.Json;
import com.ash.rachana.view.ViewModelSchema;
import org.teavm.jso.JSExport;

/**
 * The entry of {@code rachana.tools.js}: the runtime's functions, the editing operations (design/ops), the ViewModel schema, and the
 * conformance harness (one case line in, its results line out), so that a runner checks this bundle against the JVM case by case.
 */
public final class ToolsMain {

    private ToolsMain() {
    }

    public static void main(String[] args) {
    }

    @JSExport
    public static String version() {
        return Facade.VERSION;
    }

    @JSExport
    public static void init(String profiles, String formats) {
        Facade.init(profiles, formats);
    }

    @JSExport
    public static String evaluate(String request) {
        return Facade.expr(request);
    }

    @JSExport
    public static String evaluateError(String request) {
        return Facade.exprError(request);
    }

    @JSExport
    public static String format(String request) {
        return Facade.format(request);
    }

    @JSExport
    public static String check(String request) {
        return Facade.problem(request);
    }

    @JSExport
    public static String view(String request) {
        return Facade.view(request);
    }

    @JSExport
    public static String edit(String request) {
        return Cases.run(Cases::edit, request);
    }

    @JSExport
    public static String viewModelSchema() {
        return Json.write(ViewModelSchema.build());
    }

    @JSExport
    public static String hello(String runtime) {
        return Cases.hello(runtime);
    }

    @JSExport
    public static String answer(String caseLine) {
        return Cases.answer(caseLine);
    }
}
