/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import com.ash.rachana.internal.Plain;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The literal arguments of a call checked against the function catalogue ({@code spec/1/functions.yaml}, built in as
 * {@code rachana/el/functions.yaml}): the one place, read from the contract, so no function needs a hand-written check for the closed
 * set of words or the literal its parameter takes (SPEC 4.10 rule 2). From language level 1.1, when an expression compiles:
 *
 * <ul>
 *   <li>a text argument written in the expression is one of its parameter's {@code enum} ({@code group(..., by = 'decade')} does not
 *       compile); an argument that is not a literal is left to the evaluation;</li>
 *   <li>a parameter typed {@code boolean-literal}, {@code integer-literal}, {@code number-literal} or {@code string-literal} (the
 *       measure-level functions of the catalogue's profile section: {@code rank(agg, desc = true)}, {@code lag(agg, n, order)}) takes that literal;</li>
 *   <li>an engine's function (runs {@code E}) takes its keyword-only parameters by name only: {@code rank(sum(x), 'sideways')} gives
 *       {@code rank} a second argument by place, which it has not.</li>
 * </ul>
 *
 * A function's own hand-written check runs first (its words stay as they are); this one catches every case the catalogue states and no
 * hand-written check does. Level 1.0 is frozen and keeps the reference's acceptance.
 */
final class CatalogueArgs {

    private CatalogueArgs() {
    }

    /** A parameter as the catalogue states it. */
    record Param(String name, String type, List<String> options, boolean keywordOnly, boolean variadic, boolean optional) {}

    /** A function as the catalogue states it. */
    record Entry(String name, String runs, String signature, List<Param> params) {
        /** The parameters an argument by place binds to: every one, but an engine's keyword-only ones. */
        List<Param> byPlace() {
            return "E".equals(runs) ? params.stream().filter(p -> !p.keywordOnly()).toList() : params;
        }
    }

    /** A refusal: its words (after the function's name) and the argument it points at (null: the call). */
    record Refusal(String message, Expr at) {}

    private static final class Loaded {
        static final Map<String, Entry> BY_NAME = load();
    }

    private static Map<String, Entry> load() {
        InputStream own = CatalogueArgs.class.getResourceAsStream("/" + CoreParams.RESOURCE);
        try (InputStream in = own != null ? own : CatalogueArgs.class.getClassLoader().getResourceAsStream(CoreParams.RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(CoreParams.RESOURCE + " is missing from the class path");
            }
            Object root = Plain.yaml(in);
            Map<String, Entry> out = new LinkedHashMap<>();
            for (String section : List.of("core", "drishti_profile")) {
                for (Object f : Plain.elements(Plain.path(root, section))) {
                    List<Param> params = new ArrayList<>();
                    for (Object p : Plain.elements(Plain.path(f, "params"))) {
                        List<String> options = new ArrayList<>();
                        for (Object o : Plain.elements(Plain.path(p, "enum"))) {
                            options.add(Plain.text(o));
                        }
                        params.add(new Param(Plain.text(Plain.path(p, "name")), Plain.text(Plain.path(p, "type"), ""), List.copyOf(options),
                                Plain.asBoolean(Plain.path(p, "keyword_only"), false), Plain.asBoolean(Plain.path(p, "variadic"), false),
                                Plain.asBoolean(Plain.path(p, "optional"), false) || Plain.hasNonNull(p, "default")));
                    }
                    String name = Plain.text(Plain.path(f, "name"));
                    out.putIfAbsent(name, new Entry(name, Plain.text(Plain.path(f, "runs"), ""), Plain.text(Plain.path(f, "signature"), name),
                            List.copyOf(params)));
                }
            }
            return java.util.Collections.unmodifiableMap(out);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + CoreParams.RESOURCE, e);
        }
    }

    /** The catalogue's entry of {@code name}, or null. */
    static Entry of(String name) {
        return Loaded.BY_NAME.get(name);
    }

    /**
     * The first refusal of a call's literal arguments, or null. {@code args} are by place (named ones already moved to their place),
     * {@code named} the keyword-only ones an engine's function keeps by name; {@code written} gives an argument's text as written.
     */
    static Refusal check(String name, List<Expr> args, Map<String, Expr> named, Function<Expr, String> written) {
        Entry f = of(name);
        if (f == null) {
            return null;
        }
        List<Param> byPlace = f.byPlace();
        boolean repeats = !byPlace.isEmpty() && byPlace.get(byPlace.size() - 1).variadic();
        if (!repeats && args.size() > byPlace.size()) {
            Param kw = f.params().stream().filter(Param::keywordOnly).findFirst().orElse(null);
            int n = byPlace.size();
            return new Refusal("takes at most " + n + " argument" + (n == 1 ? "" : "s") + " by place" + (kw == null ? "" : " before its named ones")
                    + "; the signature is " + f.signature() + (kw == null ? "" : " (name it: " + kw.name() + " = ...)"), args.get(n));
        }
        for (int i = 0; i < args.size() && !byPlace.isEmpty(); i++) {
            Refusal r = argument(f, byPlace.get(Math.min(i, byPlace.size() - 1)), args.get(i), written);
            if (r != null) {
                return r;
            }
        }
        for (Map.Entry<String, Expr> e : named.entrySet()) {
            Param p = f.params().stream().filter(x -> x.name().equals(e.getKey())).findFirst().orElse(null);
            Refusal r = p == null ? null : argument(f, p, e.getValue(), written);
            if (r != null) {
                return r;
            }
        }
        return null;
    }

    private static Refusal argument(Entry f, Param p, Expr a, Function<Expr, String> written) {
        if (a instanceof Expr.Absent) {
            return null;
        }
        Expr lit = a instanceof Expr.Neg n ? n.e() : a instanceof Expr.Minus m ? m.e() : a;
        Object v = lit instanceof Expr.Lit l ? l.value() : NOT_LITERAL;
        switch (p.type()) {
            case "boolean-literal" -> {
                if (!(v instanceof Boolean) || lit != a) {
                    return new Refusal(p.name() + " is true or false, written in the expression", a);
                }
            }
            case "integer-literal", "number-literal" -> {
                boolean whole = "integer-literal".equals(p.type());
                String w = v instanceof Number ? written.apply(lit) : null;
                if (!(v instanceof Number) || whole && (v instanceof Double || w == null || w.contains(".") || w.contains("e") || w.contains("E"))) {
                    return new Refusal(p.name() + " is a " + (whole ? "whole number" : "number") + " written in the expression", a);
                }
            }
            case "string-literal" -> {
                if (!(v instanceof String) && !(v == null && p.optional()) || lit != a) {
                    return new Refusal(p.name() + " is a text written in the expression ('...')", a);
                }
            }
            default -> {
            }
        }
        if (!p.options().isEmpty() && v instanceof String s && lit == a && !p.options().contains(s)) {
            return new Refusal(p.name() + " is one of " + String.join(", ", p.options()) + ", not '" + s + "'", a);
        }
        return null;
    }

    private static final Object NOT_LITERAL = new Object();
}
