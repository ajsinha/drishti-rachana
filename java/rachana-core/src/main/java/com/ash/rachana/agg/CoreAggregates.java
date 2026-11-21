/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.agg;

import com.ash.rachana.el.FunctionProvider;
import java.util.ArrayList;
import java.util.List;

/**
 * Core's aggregates over a list and the list functions resting on them (split step A9): {@code sum}, {@code avg}, {@code median},
 * {@code percentile}, the statistics and the rest SPEC 5.7 and 5.8 list, and {@code group(...)}'s grouping, computed by the aggregate
 * kernel of this package. Every function table holds it first; a host's provider adds what only it computes (the {@code _once} family,
 * {@code measure}, {@code cell}, a model's measure-level functions) and cannot give these names again.
 */
public final class CoreAggregates implements FunctionProvider {

    /** The one instance (stateless). */
    public static final CoreAggregates INSTANCE = new CoreAggregates();

    /** How a refusal names this provider. */
    public static final String SOURCE = "Rachana Core";

    private CoreAggregates() {
    }

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public Grouping grouping() {
        return new ListGrouping();
    }

    @Override
    public List<Provided> functions() {
        List<Provided> out = new ArrayList<>();
        agg(out, "sum", 1, 2, "list, [field]", "mergeable", "the total (compensated); 0 when there is no number",
                "sum($.LEGS[*].NOTIONAL)");
        agg(out, "count", 0, 2, "[list, [field]]", "mergeable", "the elements (or the field's values) that are not empty; count() with no argument "
                + "is the rows of a pivot cell (a pivot formula) or of a measure's table",
                "count($.EVENTS[?@.EVENT_TYPE == 'BOOKED'])");
        agg(out, "avg", 1, 2, "list, [field]", "mergeable", "the mean, Σx / n from the list's own sum and count", "avg($.LEGS, 'NOTIONAL')");
        agg(out, "min", 1, 2, "list, [field]", "mergeable", "the smallest number (several arguments: least)", "min($.LEGS[*].NOTIONAL)");
        agg(out, "max", 1, 2, "list, [field]", "mergeable", "the largest number (several arguments: greatest)", "max($.TRADES[*].NOTIONAL)");
        agg(out, "median", 1, 2, "list, [field]", "holistic", "the exact middle value", "median($.TRADES, 'NOTIONAL')");
        agg(out, "percentile", 2, 3, "list, [field], p", "holistic", "the exact p-quantile (linear interpolation), p in 0..1",
                "percentile($.TRADES[*].NOTIONAL, 0.95)");
        agg(out, "distinct", 1, 2, "list, [field]", "holistic", "how many different values (exact)", "distinct($.LEGS[*].CURRENCY)");
        agg(out, "wavg", 2, 3, "list, xField, wField | xs, ws", "mergeable", "Σwx / Σw, the weighted mean",
                "wavg($.TRADES, 'PRICE', 'QUANTITY')");
        agg(out, "variance", 1, 2, "list, [field]", "mergeable", "the sample variance (Welford)", "variance($.TRADES, 'PNL')");
        agg(out, "stddev", 1, 2, "list, [field]", "mergeable", "the sample standard deviation", "stddev($.TRADES[*].PNL)");
        agg(out, "variance_pop", 1, 2, "list, [field]", "mergeable", "the population variance", "variance_pop($.TRADES[*].PNL)");
        agg(out, "stddev_pop", 1, 2, "list, [field]", "mergeable", "the population standard deviation", "stddev_pop($.TRADES[*].PNL)");
        agg(out, "nmiss", 1, 2, "list, [field]", "mergeable", "how many values are missing (null or absent); 0 for an empty list",
                "nmiss($.LEGS, 'DISCOUNT_INDEX')");
        agg(out, "range", 1, 2, "list, [field]", "mergeable", "max − min; empty when there is no number", "range($.TRADES[*].PRICE)");
        agg(out, "stderr", 1, 2, "list, [field]", "mergeable", "the standard error of the mean, s / √n; empty under two numbers",
                "stderr($.TRADES[*].PNL)");
        agg(out, "cv", 1, 2, "list, [field]", "mergeable", "the coefficient of variation, s / mean; empty under two numbers or a mean of 0",
                "cv($.TRADES[*].PNL)");
        agg(out, "skewness", 1, 2, "list, [field]", "mergeable", "the sample skewness (Excel's SKEW); empty under three numbers or no spread",
                "skewness($.TRADES[*].PNL)");
        agg(out, "kurtosis", 1, 2, "list, [field]", "mergeable",
                "the sample excess kurtosis (Excel's KURT); empty under four numbers or no spread", "kurtosis($.TRADES[*].PNL)");
        agg(out, "geomean", 1, 2, "list, [field]", "mergeable", "the geometric mean; empty when a number is 0 or less (Excel's #NUM!)",
                "geomean($.RETURNS[*].GROWTH)");
        agg(out, "harmean", 1, 2, "list, [field]", "mergeable", "the harmonic mean; empty when a number is 0 or less (Excel's #NUM!)",
                "harmean($.LEGS[*].PRICE)");
        agg(out, "corr", 2, 3, "list, xField, yField | xs, ys", "mergeable", "Pearson's correlation over the pairs where both are numbers",
                "corr($.TRADES, 'PNL', 'NOTIONAL')");
        agg(out, "covar", 2, 3, "list, xField, yField | xs, ys", "mergeable", "the sample covariance (Excel's COVARIANCE.S)",
                "covar($.TRADES, 'PNL', 'NOTIONAL')");
        agg(out, "slope", 2, 3, "list, yField, xField | ys, xs", "mergeable", "the least-squares slope of y on x (Excel's SLOPE, y first)",
                "slope($.TRADES, 'PNL', 'QUANTITY')");
        agg(out, "intercept", 2, 3, "list, yField, xField | ys, xs", "mergeable", "the least-squares intercept of y on x (Excel's INTERCEPT)",
                "intercept($.TRADES, 'PNL', 'QUANTITY')");
        agg(out, "rsq", 2, 3, "list, yField, xField | ys, xs", "mergeable", "r², the square of the correlation (Excel's RSQ)",
                "rsq($.TRADES, 'PNL', 'QUANTITY')");
        agg(out, "mode", 1, 2, "list, [field]", "holistic",
                "the most frequent value, a number or a name; ties to the smallest; empty when no value repeats", "mode($.LEGS[*].CURRENCY)");
        agg(out, "iqr", 1, 2, "list, [field]", "holistic", "p75 − p25 (Excel's QUARTILE.INC)", "iqr($.TRADES[*].PRICE)");
        agg(out, "trimmean", 2, 3, "list, [field], pct", "holistic", "the mean without ⌊n·pct/2⌋ values at each end (Excel's TRIMMEAN)",
                "trimmean($.TRADES[*].PRICE, 0.1)");
        agg(out, "kth_largest", 2, 3, "list, [field], k", "holistic", "the k-th largest number (Excel's LARGE); empty when fewer than k",
                "kth_largest($.TRADES[*].NOTIONAL, 3)");
        agg(out, "kth_smallest", 2, 3, "list, [field], k", "holistic", "the k-th smallest number (Excel's SMALL); empty when fewer than k",
                "kth_smallest($.TRADES[*].NOTIONAL, 3)");
        agg(out, "string_agg", 2, 4, "list, [field], sep, [distinct]", "holistic",
                "the values joined by sep in text order (distinct true: each once); empty past 1,000 values",
                "string_agg($.LEGS[*].CURRENCY, ', ', true)");
        agg(out, "skewness_pop", 1, 2, "list, [field]", "mergeable", "the population skewness (Excel's SKEW.P); empty without spread",
                "skewness_pop($.TRADES[*].PNL)");
        agg(out, "devsq", 1, 2, "list, [field]", "mergeable", "Σ(x − mean)² (Excel's DEVSQ, SAS's CSS)", "devsq($.TRADES[*].PNL)");
        agg(out, "covar_pop", 2, 3, "list, aField, bField | as, bs", "mergeable", "the population covariance (Excel's COVARIANCE.P)",
                "covar_pop($.TRADES, 'PNL', 'NOTIONAL')");
        agg(out, "steyx", 2, 3, "list, yField, xField | ys, xs", "mergeable",
                "the standard error of y predicted from x (Excel's STEYX); empty under three pairs", "steyx($.TRADES, 'PNL', 'QUANTITY')");
        agg(out, "sumproduct", 2, 3, "list, aField, bField | as, bs", "mergeable", "Σ a·b over the pairs both present (Excel's SUMPRODUCT)",
                "sumproduct($.LEGS, 'NOTIONAL', 'RATE')");
        agg(out, "sumsq", 1, 2, "list, [field]", "mergeable", "Σ x² (Excel's SUMSQ, SAS's USS)", "sumsq($.TRADES[*].PNL)");
        agg(out, "product", 1, 2, "list, [field]", "mergeable", "Π x (Excel's PRODUCT); empty for no number",
                "product(map($.RETURNS, 1 + @)) - 1");
        agg(out, "avedev", 1, 2, "list, [field]", "holistic", "the mean absolute deviation from the mean (Excel's AVEDEV)",
                "avedev($.TRADES[*].PRICE)");
        agg(out, "percentile_exc", 2, 3, "list, [field], p", "holistic",
                "the p-quantile at rank p·(n + 1) (Excel's PERCENTILE.EXC; QUARTILE.EXC at k/4); empty outside 1..n",
                "percentile_exc($.TRADES[*].PNL, 0.25)");
        agg(out, "rank_avg", 2, 4, "list, [field], value, [ascending]", "holistic",
                "the value's rank among the list's, ties averaged, largest first unless ascending is 1 (Excel's RANK.AVG)",
                "rank_avg($.TRADES[*].PNL, $.PNL)");
        agg(out, "percent_rank_of", 2, 4, "list, [field], value, [digits]", "holistic",
                "the value's rank as a fraction 0..1, cut to 3 significant digits (Excel's PERCENTRANK.INC)",
                "percent_rank_of($.TRADES[*].PNL, 1000)");
        agg(out, "mode_multi", 1, 2, "list, [field]", "holistic", "every most frequent value, ascending, as a list (Excel's MODE.MULT)",
                "mode_multi($.LEGS[*].CURRENCY)");
        ListAnalytics.register(out);
        return out;
    }

    private static void agg(List<Provided> out, String name, int min, int max, String args, String form, String description,
            String example) {
        out.add(new Provided(name, min, max, args, form, description, example, (a, c) -> ListAggregates.apply(name, a)));
    }
}
