/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.js;

import org.teavm.jso.JSExport;

/**
 * The entry of {@code rachana.runtime.js}: compile and check a Sutra, evaluate an expression or template, format a value, build a
 * view with Core's hooks. Every function takes and returns JSON text ({@link Facade}); call {@link #init} once first.
 */
public final class RuntimeMain {

    private RuntimeMain() {
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
    public static String library(String request) {
        return Facade.library(request);
    }

    @JSExport
    public static String patch(String request) {
        return Facade.patch(request);
    }

    @JSExport
    public static String plan(String request) {
        return Facade.plan(request);
    }

    /** An open screen (B3, {@code <rachana-view>}): {@code op} is open, plan, table, view, patch or close (see {@code Screens}). */
    @JSExport
    public static String screen(String op, String request) {
        return Screens.call(op, request);
    }
}
