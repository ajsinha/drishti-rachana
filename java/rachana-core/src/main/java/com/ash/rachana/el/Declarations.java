/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * The names a Sutra declares for its expressions (EL_EXPANSION X41, SUTRA_EXPANSION S2, level 1.2): its {@code let:} values, its
 * {@code functions:}, and the functions of the libraries it names (X42, called {@code <library>.<function>(...)}). The parser inlines
 * them: a call of a declared function is its body with the parameters bound to the arguments (evaluated where the call is written),
 * read in a scope of its own (a body reads only its parameters and other functions); a {@code let} name is its value, evaluated once
 * per bind with no current row. Immutable.
 *
 * <p>An expression is compiled with a Sutra's declarations while {@link #within} runs (load-time checks and a bind call it); its
 * compiled form is cached under the declarations' {@link #id()} beside the source, so two Sutras never share a compiled expression
 * that reads their own names.
 */
public final class Declarations {

    /**
     * A declared function.
     *
     * @param name its name ({@code share}) or, from a library, {@code <library>.<name>}
     * @param params its parameter names in call order, each with its type text ({@code decimal}, {@code any})
     * @param body the expression it stands for ({@code is:}), reading only its parameters and other functions
     * @param returns the declared result type, or null
     * @param doc its documentation, or null
     */
    public record Function(String name, Map<String, String> params, String body, String returns, String doc) {
        public Function {
            params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
        }

        public List<String> paramNames() {
            return List.copyOf(params.keySet());
        }
    }

    /** No declarations: what every expression outside a declaring Sutra is compiled with. */
    public static final Declarations NONE = new Declarations(Map.of(), Map.of());

    private final Map<String, Function> functions;
    private final Map<String, String> lets;
    private final Map<String, String> states;
    private final Set<String> libraries;
    private final String id;
    /** The language level the Sutra declares below its host's ({@code rachana: 1.N}, SPEC 2.1), or null: the compiler's own. */
    private final String level;

    /**
     * @param functions the functions by name ({@code share}, {@code treasury.carry})
     * @param lets the {@code let:} values in written order (each may read the ones before it)
     */
    public Declarations(Map<String, Function> functions, Map<String, String> lets) {
        this(functions, lets, Map.of());
    }

    /**
     * @param functions the functions by name ({@code share}, {@code treasury.carry})
     * @param lets the {@code let:} values in written order (each may read the ones before it)
     * @param states the state values a Sutra holds (SUTRA_EXPANSION S1), id to type ({@code order: text}; a fragment's private state as
     *     {@code <as>.<name>}): with any, {@code state.<id>} reads one ({@link Expr.State}) and an undeclared id does not compile
     */
    public Declarations(Map<String, Function> functions, Map<String, String> lets, Map<String, String> states) {
        this(functions, lets, states, null);
    }

    private Declarations(Map<String, Function> functions, Map<String, String> lets, Map<String, String> states, String level) {
        this.level = level;
        this.functions = Collections.unmodifiableMap(new LinkedHashMap<>(functions));
        this.lets = Collections.unmodifiableMap(new LinkedHashMap<>(lets));
        this.states = Collections.unmodifiableMap(new LinkedHashMap<>(states));
        Set<String> libs = new TreeSet<>();
        functions.keySet().forEach(n -> {
            int dot = n.indexOf('.');
            if (dot > 0) {
                libs.add(n.substring(0, dot));
            }
        });
        this.libraries = Collections.unmodifiableSet(libs);
        StringBuilder sb = new StringBuilder();
        this.functions.values().forEach(f -> sb.append("f\u001f").append(f.name()).append('\u001f').append(f.params()).append('\u001f')
                .append(f.body()).append('\u001e'));
        this.lets.forEach((k, v) -> sb.append("l\u001f").append(k).append('\u001f').append(v).append('\u001e'));
        this.states.forEach((k, v) -> sb.append("s\u001f").append(k).append('\u001f').append(v).append('\u001e'));
        String names = sb.length() == 0 ? "" : Integer.toHexString(sb.toString().hashCode()) + ":" + sb.length();
        this.id = level == null ? names : "@" + level + (names.isEmpty() ? "" : ":" + names);
    }

    /**
     * These declarations read at a Sutra's own language level (SPEC 2.1: {@code rachana: 1.N}; {@code rachana: 1} is 1.0) where it is
     * below its host's: while they are in force ({@link #within}) every expression compiles at that level, not the compiler's.
     */
    public Declarations atLevel(String sutraLevel) {
        return java.util.Objects.equals(sutraLevel, level) ? this : new Declarations(functions, lets, states, sutraLevel);
    }

    /** The level of the Sutra these declarations belong to when it is below its host's, else null (the compiler's own level). */
    public String level() {
        return level;
    }

    public Map<String, Function> functions() {
        return functions;
    }

    public Map<String, String> lets() {
        return lets;
    }

    /** The state values a Sutra holds, id to type, in order (empty when it holds none). */
    public Map<String, String> states() {
        return states;
    }

    /** The libraries whose functions are declared ({@code treasury} for {@code treasury.carry}). */
    public Set<String> libraries() {
        return libraries;
    }

    public boolean isEmpty() {
        return functions.isEmpty() && lets.isEmpty() && states.isEmpty();
    }

    /** What the compile cache keys an expression under beside its source (empty for none). */
    public String id() {
        return id;
    }

    /** Whether {@code name} is a Core function's (the catalogue's, any level this runtime reads): a declared function may not take it. */
    public static boolean coreName(String name) {
        return CoreParams.of(name) != null || Level11.has(name);
    }

    /** Whether a later level of Rachana holds {@code name} back: a declared name may take it, with a warning (X1 d). */
    public static boolean reserved(String name) {
        return ReservedNames.contains(name);
    }

    /** The let names written before {@code name} (the ones its value may read). */
    List<String> before(String name) {
        List<String> out = new ArrayList<>();
        for (String k : lets.keySet()) {
            if (k.equals(name)) {
                return out;
            }
            out.add(k);
        }
        return out;
    }

    // ---- the scope of one load check or one bind --------------------------------------------------------------------------------

    /** The declarations of the Sutra being checked or bound, and its let values memoised for that bind. */
    static final class Scope {
        final Declarations declarations;
        final Map<String, Object[]> memo = new LinkedHashMap<>();

        Scope(Declarations declarations) {
            this.declarations = declarations;
        }
    }

    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    static {
        // a walk the stack guard runs again on its own thread reads the same declarations and let values (one bind)
        com.ash.rachana.internal.Deep.carry(work -> {
            Scope s = CURRENT.get();
            return s == null ? work : () -> {
                Scope outer = CURRENT.get();
                CURRENT.set(s);
                try {
                    return work.get();
                } finally {
                    if (outer == null) {
                        CURRENT.remove();
                    } else {
                        CURRENT.set(outer);
                    }
                }
            };
        });
    }

    /** The scope open on this thread, or null. */
    static Scope current() {
        return CURRENT.get();
    }

    /** The declarations in force on this thread ({@link #NONE} outside {@link #within}). */
    public static Declarations inForce() {
        Scope s = CURRENT.get();
        return s == null ? NONE : s.declarations;
    }

    /**
     * Runs {@code work} with {@code d} in force: the expressions compiled meanwhile read its names, and each {@code let} value is
     * evaluated at most once per document (one bind). Nested calls with the same declarations keep the outer scope.
     */
    public static <T> T within(Declarations d, Supplier<T> work) {
        if (d == null || d.isEmpty() && d.level == null) {
            Scope outer = CURRENT.get();
            if (outer == null) {
                return work.get();
            }
            CURRENT.remove();
            try {
                return work.get();
            } finally {
                CURRENT.set(outer);
            }
        }
        Scope outer = CURRENT.get();
        if (outer != null && outer.declarations.id.equals(d.id)) {
            return work.get();
        }
        CURRENT.set(new Scope(d));
        try {
            return work.get();
        } finally {
            if (outer == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(outer);
            }
        }
    }
}
