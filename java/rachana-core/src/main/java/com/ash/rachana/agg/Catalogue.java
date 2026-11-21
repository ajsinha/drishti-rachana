/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.agg;

import com.ash.rachana.internal.CoreText;
import java.util.List;
import java.util.Optional;

/**
 * Core's aggregates by name (split step A9): the names SPEC 5.7 and 5.8 list, as the pivot engine's {@code Functions.builtIn} named
 * them before the move (the engine keeps its own catalogue for the rest: the sketches and the {@code _once} family, decision N7).
 * Static, stateless, thread-safe; each call gives a new function.
 */
public final class Catalogue {

    private Catalogue() {
    }

    /** An aggregate by name and arguments (fields, then numbers), or empty when Core has none of that name. */
    public static Optional<Aggregate<Object>> of(String name, List<String> fields, List<Double> numbers) {
        return of(name, fields, numbers, List.of());
    }

    /**
     * As {@link #of(String, List, List)}, with the text arguments a function takes ({@code string_agg}'s separator). A boolean is a
     * number, 1 or 0 ({@code string_agg(CURRENCY, ', ', true)}).
     */
    public static Optional<Aggregate<Object>> of(String name, List<String> fields, List<Double> numbers, List<String> texts) {
        String n = CoreText.lower(name);
        if (!texts.isEmpty() && !"string_agg".equals(n)) {
            throw new IllegalArgumentException(n + " takes no text argument ('" + texts.get(0) + "')");
        }
        Aggregate<Object> f = switch (n) {
            case "sum" -> new Builtins.Sum();
            case "count" -> new Builtins.Count(fields.isEmpty());
            case "avg", "mean" -> new Builtins.Avg();
            case "min" -> new Builtins.MinMax(false);
            case "max" -> new Builtins.MinMax(true);
            case "wavg", "weighted_avg" -> new Builtins.WeightedAvg();
            case "var", "variance", "var_samp" -> new Builtins.Welford("var_samp");
            case "var_pop" -> new Builtins.Welford("var_pop");
            case "stddev", "stddev_samp" -> new Builtins.Welford("stddev_samp");
            case "stddev_pop" -> new Builtins.Welford("stddev_pop");
            case "median" -> new Builtins.Quantile("median", 0.5);
            case "percentile", "quantile" -> new Builtins.Quantile("percentile", numbers.isEmpty() ? -1 : numbers.get(0));
            case "count_distinct", "distinct" -> new Builtins.Distinct();
            case "nmiss" -> new Moments.NMiss();
            case "range" -> new Moments.Range();
            case "stderr", "cv", "skewness", "kurtosis" -> new Moments(n);
            case "geomean", "harmean" -> new Moments.PositiveMean(n);
            case "corr", "covar", "slope", "intercept", "rsq" -> new Comoments(n);
            case "mode" -> new Holistics.Mode();
            case "iqr" -> new Holistics.Iqr();
            case "trimmean" -> new Holistics.TrimMean(numbers.isEmpty() ? -1 : numbers.get(0));
            case "kth_largest", "kth_smallest" -> new Holistics.Kth(n, numbers.isEmpty() ? -1 : numbers.get(0));
            case "string_agg" -> new Holistics.StringAgg(texts.isEmpty() ? null : texts.get(0), !numbers.isEmpty() && numbers.get(0) != 0);
            case "skewness_pop", "devsq" -> new Moments(n);
            case "covar_pop", "steyx" -> new Comoments(n);
            case "sumproduct" -> new MoreAggregates.SumProduct();
            case "sumsq" -> new MoreAggregates.SumSq();
            case "product" -> new MoreAggregates.Product();
            case "avedev" -> new MoreAggregates.AveDev();
            case "percentile_exc" -> new MoreAggregates.PercentileExc(numbers.isEmpty() ? -1 : numbers.get(0));
            case "rank_avg" -> new MoreAggregates.RankAvg(numbers.isEmpty() ? Double.NaN : numbers.get(0), numbers.size() > 1 ? numbers.get(1) : 0);
            case "percent_rank_of" -> new MoreAggregates.PercentRankOf(numbers.isEmpty() ? Double.NaN : numbers.get(0),
                    numbers.size() > 1 ? numbers.get(1) : Double.NaN);
            case "mode_multi" -> new MoreAggregates.ModeMulti();
            default -> null;
        };
        return Optional.ofNullable(f);
    }
}
