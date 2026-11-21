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
    public static String checked(String request) {
        return Facade.checked(request);
    }

    @JSExport
    public static String grammar(String request) {
        return Facade.grammar(request);
    }

    @JSExport
    public static String view(String request) {
        return Facade.view(request);
    }

    @JSExport
    public static String patch(String request) {
        return Facade.patch(request);
    }

    /** A view, then each fired trigger's state, effects and refusals (FORMAT 5.15): {@code {viewModel, steps: [{state, effects, problems, viewModel}]}}. */
    @JSExport
    public static String fire(String request) {
        return Facade.fire(request);
    }

    @JSExport
    public static String plan(String request) {
        return Facade.plan(request);
    }

    /** The language levels and the default of the tools ({@link Facade#levels}). */
    @JSExport
    public static String levels() {
        return Facade.levels();
    }

    @JSExport
    public static String query(String request) {
        return Facade.query(request);
    }

    /** An open screen (B3, {@code <rachana-view>}): {@code op} is open, plan, table, view, patch or close (see {@code Screens}). */
    @JSExport
    public static String screen(String op, String request) {
        return Screens.call(op, request);
    }

    @JSExport
    public static String edit(String request) {
        return Cases.run(Cases::edit, request);
    }

    /**
     * A live table's declaration ({@code <name>.live.yaml}, LIVE_TABLES 2) read and checked as a host loads it, for Studio and
     * {@code rachana lint}: {@code {declaration, file?, profileKeys?}} answered {@code {ok, problems, schema}} (FORMAT 5.14). The tools
     * bundle's alone: the runtime bundle carries no declaration checker.
     */
    @JSExport
    public static String live(String request) {
        return Cases.run(Cases::live, request);
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
