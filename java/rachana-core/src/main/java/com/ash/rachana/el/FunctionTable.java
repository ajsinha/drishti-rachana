/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * One immutable table of Rachana-EL functions: Core's families, then the functions the profiles hand over through their
 * {@link FunctionProvider}s, with the calendars and the regular-expression caps of one {@link com.ash.rachana.Rachana} instance
 * (split step A6). Nothing in it is process-wide: two instances in one JVM never see each other's functions, calendars or patterns,
 * and a table never changes once built. A host whose functions come and go (a module started or stopped) builds a new instance and
 * swaps it in; the expression cache goes with the old one.
 *
 * <p>A profile cannot redefine a Core name: a provided function whose name Core's families hold is refused when the table is built
 * ({@code registered twice}), as is a name two providers give; only {@code min} and {@code max} hand their one-list form to a
 * provider. {@link #core()} is the table of Core alone: Core's aggregates over lists ({@code com.ash.rachana.agg.CoreAggregates}) and no
 * provider, so a figure only a host computes ({@code measure}, {@code cell}, the {@code _once} family) compiles and is refused with the
 * reason when evaluated.
 */
public final class FunctionTable {

    private static final FunctionTable CORE = new FunctionTable(List.of(), Calendars.core(), SafeRegex.Limits.DEFAULTS);

    private final Map<String, int[]> arity;
    private final Map<String, ElFunction> functions;
    private final Map<String, ElForm> forms;
    private final Set<String> syntax;
    private final Set<String> passMask;
    private final Map<String, Function<List<Expr>, String>> checks;
    private final Map<String, String> oldSpellings;
    private final Map<String, String> family;
    private final Aggregates aggregates;
    private final Calendars calendars;
    private final SafeRegex.Limits regexLimits;

    private FunctionTable(List<FunctionProvider> providers, Calendars calendars, SafeRegex.Limits regexLimits) {
        List<FunctionProvider> all = new java.util.ArrayList<>();
        all.add(com.ash.rachana.agg.CoreAggregates.INSTANCE);         // Core's aggregates first (A9); a provider adds the rest
        all.addAll(providers);
        this.aggregates = new Aggregates(all);
        this.calendars = calendars == null ? Calendars.core() : calendars;
        this.regexLimits = regexLimits == null ? SafeRegex.Limits.DEFAULTS : regexLimits;
        Functions.Table t = new Functions.Table(aggregates, this.calendars, new Regexes(this.regexLimits));
        LogicFunctions.register(t.family("logic"));
        InfoFunctions.register(t.family("logic"));
        MathFunctions.register(t.family("math"));
        NumberFunctions.register(t.family("math"));
        TextFunctions.register(t.family("text"));
        TextParts.register(t.family("text"));
        DateFunctions.register(t.family("dates"));
        DateParts.register(t.family("dates"));
        ListFunctions.register(t.family("lists"));
        ListMore.register(t.family("lists"));
        Distributions.register(t.family("statistics"));
        FinanceFunctions.register(t.family("finance"));
        aggregates.install(t);
        this.arity = Collections.unmodifiableMap(t.arity);
        this.functions = Map.copyOf(t.functions);
        this.forms = Map.copyOf(t.forms);
        this.syntax = Set.copyOf(t.syntax);
        this.passMask = Set.copyOf(t.passMask);
        this.checks = Map.copyOf(t.checks);
        this.oldSpellings = Collections.unmodifiableMap(new TreeMap<>(t.old));
        this.family = Map.copyOf(t.family);
    }

    /** Core's functions alone: no provider, Core's calendars, the default regular-expression caps. Shared; immutable. */
    public static FunctionTable core() {
        return CORE;
    }

    /**
     * A table of Core's functions and those of {@code providers}, in order, with these calendars and caps.
     *
     * @throws IllegalStateException when a provided name is Core's or is given twice (nothing is built)
     */
    public static FunctionTable of(List<FunctionProvider> providers, Calendars calendars, SafeRegex.Limits regexLimits) {
        List<FunctionProvider> ps = providers == null ? List.of() : List.copyOf(providers);
        if (ps.isEmpty() && (calendars == null || calendars == Calendars.core())
                && (regexLimits == null || regexLimits.equals(SafeRegex.Limits.DEFAULTS))) {
            return CORE;
        }
        return new FunctionTable(ps, calendars, regexLimits);
    }

    /** The calendars of this table's date functions. */
    public Calendars calendars() {
        return calendars;
    }

    /** The caps of this table's regular expressions. */
    public SafeRegex.Limits regexLimits() {
        return regexLimits;
    }

    /** Every function with its argument count: {name: [min, max]}, old spellings included, in name order. */
    public Map<String, int[]> arity() {
        return arity;
    }

    /** The spellings read for compatibility, each with the one name to write instead ({@code startsWith} → {@code starts_with}). */
    public Map<String, String> oldSpellings() {
        return oldSpellings;
    }

    /** The one name of {@code name}: itself, or the name an old spelling stands for. */
    public String oneName(String name) {
        return oldSpellings.getOrDefault(name, name);
    }

    /** True when the table has a function (or form, or syntax) of this name. */
    public boolean has(String name) {
        return arity.containsKey(name);
    }

    /** Who provides {@code name} when a profile's provider does ({@code group}: the grouping's provider); null for Core's own. */
    public String providerOf(String name) {
        return aggregates.source(name);
    }

    int[] arityOf(String name) {
        return arity.get(name);
    }

    ElFunction get(String name) {
        return functions.get(name);
    }

    ElForm form(String name) {
        return forms.get(name);
    }

    boolean syntax(String name) {
        return syntax.contains(name);
    }

    boolean passesMask(String name) {
        return passMask.contains(name);
    }

    /** True when {@code name} has a check of its own literal arguments (its words win over the catalogue's, {@link CatalogueArgs}). */
    boolean checksItself(String name) {
        return checks.containsKey(name);
    }

    String check(String name, List<Expr> args) {
        Function<List<Expr>, String> c = checks.get(name);
        return c == null ? null : c.apply(args);
    }

    /**
     * {@code e} as a call of a provided aggregate over expressions of {@code @} ({@code sum(@.MTM)}), or empty when it is not one; a
     * call whose further arguments are not literals is not one either.
     */
    public Optional<Functions.AggregateCall> aggregateCall(Expr e) {
        if (!(e instanceof Expr.Call call) || call.args().isEmpty() || "measure".equals(call.name()) || "cell".equals(call.name())
                || !(Aggregates.EXPECTED.contains(call.name()) || aggregates.all().containsKey(call.name()))) {
            return Optional.empty();
        }
        List<Object> options = new ArrayList<>();
        for (Expr a : call.args().subList(1, call.args().size())) {
            if (!(a instanceof Expr.Lit lit)) {
                return Optional.empty();
            }
            options.add(lit.value());
        }
        return Optional.of(new Functions.AggregateCall(call.name(), call.args().get(0), Collections.unmodifiableList(options)));
    }

    /**
     * Every function under its one name (old spellings left out), in name order: what the generated function catalogue lists.
     * {@code min} and {@code max} are listed once, as the engine's aggregates over one list; {@code group} as the engine's.
     */
    public List<Functions.Entry> catalogue() {
        List<Functions.Entry> out = new ArrayList<>();
        for (String name : arity.keySet()) {
            if (oldSpellings.containsKey(name)) {
                continue;
            }
            FunctionProvider.Provided p = aggregates.all().get(name);
            boolean engine = p != null || Aggregates.EXPECTED.contains(name) || Aggregates.MEASURE_LEVEL.contains(name) || "group".equals(name);
            if (!engine) {
                out.add(new Functions.Entry(name, family.getOrDefault(name, ""), "R", "Rachana", "", "", ""));
                continue;
            }
            String source = aggregates.source(name);
            String runs = com.ash.rachana.agg.CoreAggregates.SOURCE.equals(source) ? "R" : "E";   // Core's aggregates run here (A9)
            if ("group".equals(name)) {
                out.add(new Functions.Entry(name, "aggregates", runs, source == null ? "none on the class path" : source,
                        "list, key, [aggregate], [by]", "the engine's grouping", "one row {label, value, count} per key, the aggregate "
                        + "over each group (sum of a quoted field, or any aggregate over @)"));
                continue;
            }
            String fam = Aggregates.MEASURE_LEVEL.contains(name) ? "measures"
                    : p != null && "list".equals(p.form()) ? "lists" : "aggregates";
            out.add(new Functions.Entry(name, fam, runs, source == null ? "none on the class path" : source,
                    p == null ? "" : p.args(), p == null ? "" : p.form(), p == null ? "" : p.description()));
        }
        return out;
    }
}
