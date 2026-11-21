/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import java.util.Set;

/**
 * The Core function names held for later minors (SPEC 4.15 rule 2, {@code spec/1/reserved.yaml}: its {@code names}, less {@code range}, a Core aggregate since split step A9
 * and Python's range at level 1.1, PY4). From language level 1.1, a call of one that no loaded table has is {@code RCH-2101}
 * "'name' is reserved for a later version of Rachana" in place of "unknown function". {@code ReservedNamesTest} keeps this list equal to
 * the file.
 */
final class ReservedNames {

    private ReservedNames() {}

    static final Set<String> NAMES = Set.of(
            "add_tenor", "adjust", "age", "ago", "all", "amount", "annualise", "any", "append", "approx_distinct", "approx_percentile", "approx_top",
            "arg_max", "arg_min", "asof_join", "at_zone", "avedev", "avg_once", "bachelier_price", "backtest_exceptions", "badge", "bar", "base64",
            "bbox", "bearing", "beta", "between", "between_time", "bin", "black76_price", "bond_duration", "bond_price", "bond_yield", "bottom",
            "bounds", "bs_delta", "bs_gamma", "bs_price", "bs_rho", "bs_theta", "bs_vega", "bucket", "bullet", "casefold", "centroid", "changed",
            "cholesky", "chunk", "component_var", "control_label", "controls", "convert", "corr", "correlation_matrix", "count_by", "count_once",
            "coupon_dates", "coupon_days", "covar", "covar_pop", "covariance_matrix", "cummax", "cummin", "cumprod", "cumsum", "currency", "cv",
            "date_bucket", "date_spine", "date_trunc", "delta", "dense_rank", "devsq", "diff", "direction", "discount_factor", "distinct_rows",
            "dot", "drawdown", "drill_level", "drill_path", "drop_while", "duration", "ecdf", "edit_distance", "entries", "es_hist", "es_parametric",
            "ewma", "extend", "extent", "fill_gaps", "filter", "first_value", "fiscal_period", "fiscal_quarter", "fiscal_year", "fiscal_ytd",
            "flat_map", "forward_rate", "from_entries", "from_local", "geo_distance", "geohash", "geomean", "get", "goal_seek", "graphemes", "grid",
            "group_by", "harmean", "hash", "heat", "histogram", "hit_rate", "holidays_between", "icon", "imm_date", "implied_vol", "in_bbox",
            "in_polygon", "incremental_var", "index_by", "index_of", "information_ratio", "initials", "intercept", "iqr", "is_holiday", "is_masked",
            "is_set", "isoutlier", "join", "keys", "kth_largest", "kth_smallest", "kupiec", "kurtosis", "lag", "last_value", "lead", "linear_fit",
            "list_and", "local_date", "locale", "lookup", "mask", "matrix", "max_drawdown", "maxk", "merge", "mink", "mmult", "mode", "mode_multi",
            "money", "moving_avg", "moving_max", "moving_median", "moving_min", "moving_stddev", "moving_sum", "movmean", "movmedian", "movstd",
            "movsum", "msg", "mtd", "next_business_day", "nice_ticks", "nmiss", "normalize", "nth_value", "ntile", "nullif", "omit", "ordinal",
            "over", "overlap_days", "pairwise", "panel_rows", "parse_json", "pct_change", "percent_rank", "percent_rank_of", "percentile_exc",
            "pick", "pivot", "plural", "polyfit", "previous", "prior_period", "product", "qtd", "qty", "quad_form", "quantiles", "random_normal",
            "rank", "rank_avg", "rebase", "regex_count", "regex_extract_all", "regex_match", "regex_split", "region_of", "resample", "rescale",
            "returns", "rollup", "round_money", "row_age", "row_count", "row_number", "rsq", "running", "safe_div", "same_period_last_year",
            "sample", "scale_horizon", "scan", "schedule", "seconds", "select", "selection", "sensitivity", "sharpe", "similarity", "skewness",
            "skewness_pop", "sliding", "slope", "slug", "solve", "sortino", "spark", "squeeze", "stable_index", "starts_with_any", "stderr", "steyx",
            "string_agg", "strip_accents", "sum_once", "sumproduct", "sumsq", "suppress_small", "take_while", "text_join", "timestamp_add",
            "to_json", "to_unit", "top", "tracking_error", "traffic_light", "transpose", "trimmean", "truncate", "unpivot", "url_encode", "values",
            "var_hist", "var_parametric", "vec", "viewer", "volatility", "what_if", "width_class", "window", "winsorize", "ytd", "zero_rate", "zip",
            "zip_with", "zone", "zone_offset", "zscore");

    static boolean contains(String name) {
        return NAMES.contains(name);
    }
}
