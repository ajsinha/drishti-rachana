/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The functions one function table holds from {@link FunctionProvider}s (docs/architecture/ARCHITECTURE.md parts 1 and 2): the
 * providers a {@link com.ash.rachana.profile.Profile} hands over, per {@link com.ash.rachana.Rachana} instance; nothing is discovered by
 * scanning the class path. Core's own aggregates come first ({@code com.ash.rachana.agg.CoreAggregates}, split step A9); the names
 * below that only a host computes ({@code measure}, {@code cell}, the {@code _once} family) compile whether or not a provider gives
 * them; without one, evaluating one is refused with the reason ({@code RCH-2102}), never computed another way.
 */
final class Aggregates {

    /** The names Rachana asks the pivot engine for (the engine may add more): aggregates over a list, and figures of other engines. */
    static final List<String> EXPECTED = List.of("sum", "count", "avg", "min", "max", "median", "percentile", "distinct", "wavg",
            "variance", "stddev", "variance_pop", "stddev_pop", "sum_once", "count_once", "avg_once", "measure", "cell");

    /**
     * The functions of a model's measures the engine computes over an answer, never over a list (docs/architecture/ARCHITECTURE.md §28 2.5, runs E):
     * the filter context ({@code calc}, {@code share_of}, {@code parent_of}, {@code as_of}, {@code avg_by}, {@code avg_over}), time
     * intelligence, windows, balances, conversion and lookup. Rachana parses and checks them; the engine's compiler lowers them.
     */
    static final List<String> MEASURE_LEVEL = List.of("ratio", "convert", "related", "related_rows", "fx", "avg_by", "sum_by", "min_by", "max_by", "avg_over", "calc", "share_of",
            "parent_of", "as_of", "ytd", "mtd", "qtd", "prior_period", "same_period_last_year", "running", "fiscal_ytd", "lag", "lead",
            "rank", "dense_rank", "percent_rank", "moving_avg", "moving_sum", "moving_min", "moving_max", "latest", "earliest", "opening",
            "closing");

    /** Names whose one-list form a provider computes while Rachana keeps their several-argument form (old least/greatest). */
    static final List<String> SHARED = List.of("min", "max");

    private final Map<String, FunctionProvider.Provided> provided;
    private final Map<String, String> source;
    private final FunctionProvider.Grouping grouping;
    private final String groupingSource;

    /** No provider: every aggregate and figure of another engine refused with the reason. */
    static final Aggregates NONE = new Aggregates(List.of());

    /**
     * The functions of {@code providers}, in order; a name two providers give is refused (nothing is built). The first provider that
     * groups gives {@code group(...)}.
     */
    Aggregates(List<FunctionProvider> providers) {
        Map<String, FunctionProvider.Provided> byName = new LinkedHashMap<>();
        Map<String, String> from = new LinkedHashMap<>();
        FunctionProvider.Grouping g = null;
        String gFrom = null;
        for (FunctionProvider p : providers) {
            for (FunctionProvider.Provided f : p.functions()) {
                if (byName.putIfAbsent(f.name(), f) != null) {
                    throw new IllegalStateException("Rachana-EL function " + f.name() + " is provided twice (" + from.get(f.name())
                            + ", " + p.source() + ")");
                }
                from.put(f.name(), p.source());
            }
            if (g == null && p.grouping() != null) {
                g = p.grouping();
                gFrom = p.source();
            }
        }
        this.provided = Collections.unmodifiableMap(byName);
        this.source = Collections.unmodifiableMap(from);
        this.grouping = g;
        this.groupingSource = gFrom;
    }

    /**
     * Registers the provided functions into the table, and every expected name no provider gives as a refusal; {@code min} and
     * {@code max} stay Rachana's entries ({@link MathFunctions}), which hand their one-list form to {@link #call}. A provided name that
     * Rachana's own families already hold is refused by the table ({@code registered twice}): a profile never redefines a Core name.
     */
    void install(Functions.Table t) {
        t.family("provided");
        for (FunctionProvider.Provided f : provided.values()) {
            if (SHARED.contains(f.name())) {
                continue;
            }
            if (t.arity.containsKey(f.name())) {
                throw new IllegalStateException("Rachana-EL function " + f.name() + " is Core's: a profile cannot redefine it ("
                        + source.get(f.name()) + ")");
            }
            t.fn(f.name(), f.minArgs(), f.maxArgs(), f.function());
        }
        for (String name : EXPECTED) {
            if (!provided.containsKey(name) && !SHARED.contains(name)) {
                t.fn(name, 0, 64, (a, c) -> {
                    throw refused(name);
                });
            }
        }
        for (String name : MEASURE_LEVEL) {
            if (!provided.containsKey(name)) {
                t.fn(name, 0, 64, (a, c) -> {
                    throw refused(name);
                });
            }
        }
    }

    /** A provided function, evaluated; refused with the reason when no provider gives it. */
    Object call(String name, List<Object> args, EvalContext c) {
        FunctionProvider.Provided f = provided.get(name);
        if (f == null) {
            throw refused(name);
        }
        return f.function().apply(args, c);
    }

    /** The grouping behind {@code group(...)}; refused with the reason when no provider groups. */
    FunctionProvider.Grouping grouping() {
        if (grouping == null) {
            throw refused("group");
        }
        return grouping;
    }

    /** True when {@code name} is a provided aggregate (one {@code group(list, key, aggregate(@...))} may name). */
    boolean provided(String name) {
        return provided.containsKey(name);
    }

    /** The provided functions, by name, with who provides them. */
    Map<String, FunctionProvider.Provided> all() {
        return provided;
    }

    /** Who provides {@code name} ({@code group}: the grouping's provider); null when none does. */
    String source(String name) {
        return "group".equals(name) ? groupingSource : source.get(name);
    }

    private static ElException refused(String name) {
        return Args.error(name, "an aggregate (and a figure of another engine) comes only from the pivot engine (drishti-bicompute), "
                + "which is not on the class path here; Rachana computes no aggregate itself (docs/architecture/ARCHITECTURE.md)");
    }
}
