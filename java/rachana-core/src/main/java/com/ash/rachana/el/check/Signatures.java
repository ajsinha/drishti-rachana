/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el.check;

import com.ash.rachana.internal.Plain;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The signatures of the functions checked mode knows (docs/architecture/ARCHITECTURE.md §28 2.1 and 2.5): names, parameters (positional and named),
 * the scopes a function may be written in, its types, and whether Rachana ({@code R}) or the pivot engine ({@code E}) computes it.
 * Loaded once from {@code rachana/el/signatures.yaml} on the class path (the shipped catalogue, {@link #get()}); {@link #parse} reads
 * any file of the same shape. Immutable
 * and shared. The parser binds named arguments by these signatures; the {@link Checker} validates calls against them.
 */
public final class Signatures {

    /** The shipped catalogue's resource. */
    public static final String RESOURCE = "rachana/el/signatures.yaml";
    /** A function that works now; any other status names the release or wave that builds it. */
    public static final String BUILT = "built";

    /** A parameter: its name, type, default text (null: required), keyword-only, variadic. */
    public record Param(String name, String type, String defaultText, boolean keywordOnly, boolean variadic) {
        public boolean optional() {
            return defaultText != null || variadic;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", name);
            m.put("type", type);
            if (defaultText != null) {
                m.put("default", defaultText);
            }
            if (keywordOnly) {
                m.put("keywordOnly", true);
            }
            if (variadic) {
                m.put("variadic", true);
            }
            return m;
        }
    }

    /** An example: the expression and what it says. */
    public record Example(String expr, String says) {}

    /**
     * One function. {@code runs}:
     * {@code R} or {@code E}; {@code edgeCases} by topic: nulls, empty, masked, currencies, types.
     */
    public record Signature(String name, String family, String runs, String signature, List<Param> params, String returns, List<String> scopes,
            String status, String since, String description, List<Example> examples, Map<String, String> edgeCases) {
        public Signature {
            params = List.copyOf(params);
            scopes = List.copyOf(scopes);
            examples = List.copyOf(examples);
            edgeCases = Collections.unmodifiableMap(new LinkedHashMap<>(edgeCases));
        }

        public boolean built() {
            return BUILT.equals(status);
        }

        public boolean aggregate() {
            return "aggregate".equals(family);
        }

        /** An aggregate-scope function that is not itself an aggregate but takes one (time intelligence, windows, balances, context). */
        public boolean takesAggregate() {
            return params.stream().anyMatch(p -> "aggregate".equals(p.type()));
        }

        public boolean allowedIn(Scope s) {
            return scopes.contains(s.word());
        }

        public Optional<Param> param(String name) {
            return params.stream().filter(p -> p.name().equals(name)).findFirst();
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", name);
            m.put("family", family);
            if (runs != null && !runs.isEmpty()) {
                m.put("runs", runs);
            }
            m.put("signature", signature);
            m.put("params", params.stream().map(Param::toMap).toList());
            m.put("returns", returns);
            m.put("scopes", scopes);
            m.put("status", status);
            m.put("since", since);
            m.put("description", description);
            m.put("examples", examples.stream().map(e -> Map.of("expr", e.expr(), "says", e.says())).toList());
            m.put("edgeCases", edgeCases);
            return m;
        }
    }

    /** The families whose functions edit the filter context or the time of an aggregate (BI_DESIGN 3.3.5: nesting at most three deep). */
    public static final Set<String> CONTEXT_FAMILIES = Set.of("filter context", "time travel");
    /** How deep {@code calc}, {@code share_of}, {@code parent_of} and {@code as_of} nest (rule 2 of BI_DESIGN 3.3.5). */
    public static final int MAX_CONTEXT_DEPTH = 3;

    private static final class Shipped {
        static final Signatures INSTANCE = load(RESOURCE);
    }

    private final Map<String, Signature> byName;

    private Signatures(Map<String, Signature> byName) {
        this.byName = Collections.unmodifiableMap(new LinkedHashMap<>(byName));
    }

    /** The shipped catalogue. */
    public static Signatures get() {
        return Shipped.INSTANCE;
    }

    public Optional<Signature> function(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    public List<Signature> all() {
        return List.copyOf(byName.values());
    }

    public Set<String> names() {
        return byName.keySet();
    }

    /** The families in catalogue order. */
    public List<String> families() {
        List<String> out = new ArrayList<>();
        byName.values().forEach(f -> {
            if (!out.contains(f.family())) {
                out.add(f.family());
            }
        });
        return out;
    }

    /** The functions as an endpoint serves them. */
    public List<Map<String, Object>> toMaps() {
        return byName.values().stream().map(Signature::toMap).toList();
    }

    // ---- loading -----------------------------------------------------------------------------------------------------

    /** A catalogue from a resource of this module (a named module reads its own resources only this way), else on the class path. */
    public static Signatures load(String resource) {
        InputStream own = Signatures.class.getResourceAsStream("/" + resource);
        try (InputStream in = own != null ? own : Signatures.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException(resource + " is missing from the class path");
            }
            return parse(Plain.yaml(in), resource);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + resource, e);
        }
    }

    /** A catalogue from its YAML tree as plain values ({@code functions:} entries); {@code what} names it in a problem. */
    public static Signatures parse(Object root, String what) {
        Map<String, Signature> out = new LinkedHashMap<>();
        for (Object f : Plain.elements(Plain.path(root, "functions"))) {
            String name = Plain.text(Plain.path(f, "name"));
            List<Param> params = new ArrayList<>();
            for (Object p : Plain.elements(Plain.path(f, "params"))) {
                Object d = Plain.path(p, "default");
                String def = d == Plain.MISSING ? null : d == null ? "None" : Plain.text(d);
                params.add(new Param(Plain.text(Plain.path(p, "name")), Plain.text(Plain.path(p, "type"), "any"), def,
                        Plain.asBoolean(Plain.path(p, "keyword-only"), false), Plain.asBoolean(Plain.path(p, "variadic"), false)));
            }
            List<String> scopes = new ArrayList<>();
            Plain.elements(Plain.path(f, "scopes")).forEach(s -> scopes.add(Plain.text(s)));
            List<Example> examples = new ArrayList<>();
            Plain.elements(Plain.path(f, "examples")).forEach(e -> examples.add(new Example(Plain.text(Plain.path(e, "expr")),
                    Plain.text(Plain.path(e, "says")))));
            Map<String, String> edges = new LinkedHashMap<>();
            Plain.entries(Plain.path(f, "edge-cases")).forEach(e -> edges.put(e.getKey(), Plain.text(e.getValue())));
            if (out.put(name, new Signature(name, Plain.text(Plain.path(f, "family")), Plain.text(Plain.path(f, "runs"), ""),
                    Plain.text(Plain.path(f, "signature")), params, Plain.text(Plain.path(f, "returns"), "any"), scopes,
                    Plain.text(Plain.path(f, "status"), BUILT), Plain.text(Plain.path(f, "since"), ""),
                    Plain.text(Plain.path(f, "description"), ""), examples, edges)) != null) {
                throw new IllegalStateException(what + ": the function " + name + " is listed twice");
            }
        }
        return new Signatures(out);
    }
}
