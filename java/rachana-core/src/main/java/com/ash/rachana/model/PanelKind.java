/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.model;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The twenty-two panel kinds of the grammar. Each declares which options it requires and which it accepts,
 * so the validator can reject a Sutra before it ever meets data.
 */
public enum PanelKind {
    KV(Set.of(), Set.of("rows", "columns", "fields")),
    TABLE(Set.of("rows"), Set.of("totalLabel", "limit", "moreLabel", "link", "highlight", "search", "pivot", "children", "expand", "levels", "crumb", "root", "empty")),
    TABS(Set.of("each"), Set.of("tabTitle", "layout")),
    LINE(Set.of(), Set.of("rows", "source", "x", "y", "mark", "footer", "unit", "fmt", "step", "values", "labels")),
    AREA(Set.of("rows"), Set.of("x", "series", "limit", "limitLabel", "unit")),
    /** Bars, one per row; {@code shape: donut} draws the same rows as a ring of shares of their total. */
    HBAR(Set.of("rows"), Set.of("label", "value", "fmt", "tone", "shape")),
    LADDER(Set.of("rows"), Set.of("totalLabel", "highlight", "search", "pivot", "children", "expand", "levels", "crumb", "root", "empty")),
    LINKS(Set.of(), Set.of()),
    STATUS(Set.of(), Set.of("fields")),
    PROVENANCE(Set.of(), Set.of()),
    MARKDOWN(Set.of("text"), Set.of()),
    GAUGE(Set.of("value"), Set.of("max", "label", "fmt")),
    /** A grid of values over two axes (volatility surfaces, correlation matrices): heatmap, or 3D on request. */
    SURFACE(Set.of("rows", "y"), Set.of("fmt", "unit", "view")),
    /**
     * Ordered signed steps as floating bars (P&amp;L attribution): rises up, falls down, totals as full bars. Rises are
     * the theme's good colour and falls its bad one ({@code colors: gain-loss}, the default), or its positive and
     * negative ones ({@code colors: theme}, blue and orange, colour-blind friendly); totals are neutral.
     */
    WATERFALL(Set.of("rows"), Set.of("label", "value", "total", "sum", "fmt", "unit", "colors")),
    /** A distribution of numbers, binned on the server, with optional marker lines (VaR, ES, mean). */
    HISTOGRAM(Set.of("rows"), Set.of("value", "bins", "markers", "fmt", "unit")),
    /** Two measures per row as points (risk against return), optionally sized and coloured by a group. */
    SCATTER(Set.of("rows", "x", "y"), Set.of("size", "label", "group", "fmt", "xFmt", "xLabel", "yLabel")),
    /** Open, high, low and close by date, with optional volume bars. */
    CANDLESTICK(Set.of("rows"), Set.of("x", "open", "high", "low", "close", "volume", "fmt", "unit")),
    /** Entities and the relations between them (a legal-entity hierarchy); a node that names an entity opens it. */
    GRAPH(Set.of("nodes"), Set.of("edges", "label", "group", "layout")),
    /** Dated events in order, each with a status tone and a short description. */
    TIMELINE(Set.of("rows"), Set.of("date", "label", "detail", "status", "tone")),
    /** A two-dimensional aggregate of the rows (by one field, across another) with totals and an optional heat scale. */
    PIVOT(Set.of("rows", "by", "across"), Set.of("value", "agg", "fmt", "tone", "heat", "totals", "expand")),
    /**
     * One large formatted, toned figure (a KPI tile), with an optional small change beside it ({@code delta}, with its own
     * format and tone), a {@code unit} and a {@code caption} under it.
     */
    METRIC(Set.of("value"), Set.of("label", "fmt", "tone", "delta", "deltaFmt", "deltaTone", "unit", "caption")),
    /**
     * A map of the world with a country highlighted ({@code highlight}) and a click on any country opening that country's entity
     * ({@code link}), optionally shaded by a field of every entity of a kind ({@code shade}); or, with {@code region}, the regions
     * of one country. Drawn from vendored Natural Earth shapes: nothing is fetched from outside.
     */
    MAP(Set.of(), Set.of("highlight", "region", "link", "shade", "fmt", "unit")),
    /**
     * Language 1.1 (ViewModel contract 1.1): the rows in a grid of typed columns, from a dataset the view carries. {@code columns} say
     * the fields (each column's {@code bind} read per row, raw; its {@code fmt} printed by the painter); without them every plain field
     * of the rows. {@code dataset} names it (panels naming one dataset share it), {@code rowKey} the fields that identify a row,
     * {@code group} and {@code sort} the grouping and order, {@code limit} the most rows sent (10,000; at most 50,000).
     */
    GRID(Set.of("rows"), Set.of("dataset", "rowKey", "group", "sort", "limit", "search", "empty")),
    /** Language 1.1: a hierarchy of the rows ({@code node} names a row's node, {@code parent} its parent or {@code path} its path). */
    TREE(Set.of("rows", "node"), Set.of("parent", "path", "label", "mode", "open", "dataset", "rowKey", "limit")),
    /** Language 1.1: a chart of the rows: {@code x}, the figures {@code y} (a field or a list), {@code by}, {@code agg}, {@code type}. */
    CHART(Set.of("rows", "x", "y"), Set.of("type", "by", "agg", "stack", "horizontal", "xLabel", "yLabel", "bins", "top", "fmt", "dataset",
            "rowKey", "limit"));

    /** The language level that brought each kind not of level 1.0 (SUTRA_EXPANSION 6.2). */
    private static final java.util.Map<PanelKind, String> SINCE = java.util.Map.of(GRID, "1.1", TREE, "1.1", CHART, "1.1");

    /** The language level that brought this kind: {@code 1.0}, or {@code 1.1} for {@code grid}, {@code tree} and {@code chart}. */
    public String since() {
        return SINCE.getOrDefault(this, "1.0");
    }

    /** Whether the kind exists at this language level ({@code major.minor}). */
    public boolean at(String level) {
        return compareLevels(since(), level) <= 0;
    }

    /** The order of two language levels {@code 1.minor}. */
    public static int compareLevels(String a, String b) {
        return Integer.compare(minor(a), minor(b));
    }

    private static int minor(String level) {
        int dot = level == null ? -1 : level.indexOf('.');
        try {
            return dot < 0 ? 0 : Integer.parseInt(level.substring(dot + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private final Set<String> required;
    private final Set<String> optional;

    PanelKind(Set<String> required, Set<String> optional) {
        this.required = required;
        this.optional = optional;
    }

    public Set<String> required() {
        return required;
    }

    /** Options this kind accepts but does not require. */
    public Set<String> optional() {
        return optional;
    }

    public boolean accepts(String option) {
        return required.contains(option) || optional.contains(option) || ("source".equals(option) && readsData()) || Panel.WHEN.equals(option)
                || (Panel.WHERE.equals(option) && (required.contains("rows") || optional.contains("rows")) && this != KV)
                || (Panel.ON_SELECT.equals(option) && (this == TABLE || this == LADDER || this == HBAR));
    }

    /**
     * Whether panels of this kind read the entity's data, and so may read a linked entity's instead ({@code source}):
     * every kind but {@code links} and {@code provenance} (which describe the view). A markdown panel reads data through
     * the {@code ${...}} parts of its text, so it may name a source too.
     */
    public boolean readsData() {
        return this != LINKS && this != PROVENANCE;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<PanelKind> parse(String s) {
        for (PanelKind k : values()) {
            if (k.id().equals(s)) {
                return Optional.of(k);
            }
        }
        return Optional.empty();
    }
}
