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

/**
 * The parameters of every Core function, read from the function catalogue itself ({@code spec/1/functions.yaml}, the contract, built
 * into the jar and the JavaScript bundles as {@code rachana/el/functions.yaml}): one source, so code and catalogue cannot drift. From
 * level 1.1 a named argument binds by these names (SPEC 4.10 rule 3: every parameter may be given by name; none is keyword-only in
 * Core); a repeatable parameter is given by place only. The column parameters a fragment's mapping renames are the catalogue's
 * {@code role: column} marks. Level 1.0 binds named arguments as the reference did. Loaded once, on first use; immutable.
 */
final class CoreParams {

    /** The catalogue's resource. */
    static final String RESOURCE = "rachana/el/functions.yaml";

    private CoreParams() {}

    /** One parameter: its name, whether it repeats, whether its role is {@code column}, whether its type is {@code text}. */
    record Param(String name, boolean repeatable, boolean column, boolean text) {}

    private static final class Loaded {
        static final Map<String, List<Param>> BY_NAME = load();
    }

    private static Map<String, List<Param>> load() {
        InputStream own = CoreParams.class.getResourceAsStream("/" + RESOURCE);
        try (InputStream in = own != null ? own : CoreParams.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(RESOURCE + " is missing from the class path");
            }
            Map<String, List<Param>> out = new LinkedHashMap<>();
            for (Object f : Plain.elements(Plain.path(Plain.yaml(in), "core"))) {
                List<Param> params = new ArrayList<>();
                for (Object p : Plain.elements(Plain.path(f, "params"))) {
                    params.add(new Param(Plain.text(Plain.path(p, "name")), Plain.asBoolean(Plain.path(p, "variadic"), false),
                            "column".equals(Plain.text(Plain.path(p, "role"), "")), "text".equals(Plain.text(Plain.path(p, "type"), ""))));
                }
                out.put(Plain.text(Plain.path(f, "name")), List.copyOf(params));
            }
            return java.util.Collections.unmodifiableMap(out);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + RESOURCE, e);
        }
    }

    /** The parameters of Core function {@code name} in call order, or null when it is not one. */
    static List<Param> of(String name) {
        return Loaded.BY_NAME.get(name);
    }

    /** Every Core function's parameters, in catalogue order. */
    static Map<String, List<Param>> all() {
        return Loaded.BY_NAME;
    }

    /** The parameter names of {@code name} in call order (empty when it is not a Core function). */
    static List<String> names(String name) {
        List<Param> p = of(name);
        return p == null ? List.of() : p.stream().map(Param::name).toList();
    }
}
