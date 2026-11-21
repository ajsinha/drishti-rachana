/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.view;

import com.ash.rachana.parse.Json;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The JSON Schema (draft 2020-12) of the ViewModel, its panels' {@code data} and the live stream's frames
 * ({@code GET /api/v1/viewmodel/schema}, docs/architecture/VIEWMODEL.md section 9).
 *
 * <p>Generated from the records themselves at build time ({@code ViewCodeGenerator}, checked fresh by a test), so a field added to {@link ViewModel}, {@link PanelData}, {@link Frame} or
 * {@link Patch} is in the schema at once. Each record's {@link Include} rule decides what is required and what may be null:
 * a component that is always written is required, and nullable when it is a reference; one left out when null, empty or
 * default is optional and never null. On top of the generated shape sit the few rules the types cannot say: the panel
 * kind that goes with each {@code data} record ({@link #DATA_BY_KIND}), the closed sets of words ({@link #ENUMS}), the
 * tones, and the fields a ViewModel always carries ({@link #REQUIRED}). {@code ViewModelSchemaTest} validates real views
 * and frames against it, so these rules cannot drift from what the server sends.
 */
public final class ViewModelSchema {

    /** The record that carries a panel's {@code data}, per panel kind: every {@code PanelKind}, and every {@link PanelData} record. */
    public static final Map<String, Class<? extends PanelData>> DATA_BY_KIND = dataByKind();

    /** The tones a value may carry ({@code Cell.tone} and the tones of bars, steps, markers, events and candles). */
    public static final List<String> TONES = List.of("pos", "neg", "link", "accent", "ok", "warn", "bad", "muted", "inactive");

    /** How a contract-1.1 figure combines rows (a grid column's group figure, a measure). */
    public static final List<String> AGGS = List.of("sum", "count", "min", "max", "avg", "first", "last", "distinct");

    /** Closed sets of words, by {@code Record.component}. */
    static final Map<String, List<String>> ENUMS = Map.ofEntries(
            Map.entry("Patch.op", List.of("strip", "panel", "provenance", "deleted", "restored", "rows")),
            Map.entry("KeyView.action", List.of("panel", "link", "raw", "impact", "denied")),
            Map.entry("LinkItem.status", List.of("resolved", "pending", "missing", "denied", "inactive")),
            Map.entry("PanelCompute.state", List.of("ready", "computing", "stale", "failed", "refused")),
            Map.entry("Tabs.layout", List.of("tabs", "columns")),
            Map.entry("Graph.layout", List.of("tree", "force")),
            Map.entry("Surface.view", List.of("heatmap", "3d")),
            Map.entry("GeoMap.mode", List.of("world", "region")),
            // contract 1.1
            Map.entry("DataField.type", List.of("string", "number", "boolean", "date", "datetime")),
            Map.entry("PagesRef.mode", List.of("offset", "cursor")),
            Map.entry("PagesRef.sort", List.of("client", "server")),
            Map.entry("PagesRef.filter", List.of("client", "server")),
            Map.entry("GridColumn.pinned", List.of("left", "right")),
            Map.entry("GridColumn.agg", AGGS),
            Map.entry("Measure.agg", AGGS),
            Map.entry("Tree.mode", List.of("grid", "org", "sunburst", "icicle")),
            Map.entry("DataChart.type", List.of("bar", "line", "area", "scatter", "pie", "donut", "heatmap", "histogram")),
            Map.entry("DataChart.stack", List.of("sum", "percent")));

    /** Components written as a tone ({@link #TONES}); a Series' tone is the author's own word and stays free text. */
    static final List<String> TONED = List.of("Cell.tone", "Bar.tone", "Step.tone", "Marker.tone", "Event.tone", "Candles.tone", "GeoPoint.tone");

    /** Fields the server always sends although the record would let them be left out. */
    static final Map<String, List<String>> REQUIRED = Map.ofEntries(
            Map.entry("ViewModel", List.of("ref", "title", "strip", "panels", "keys", "provenance", "timings", "contract")),
            Map.entry("PanelView", List.of("id", "kind")),
            Map.entry("Ref", List.of("kind", "id")),
            Map.entry("LinkView", List.of("kind", "id")),
            Map.entry("Patch", List.of("op")),
            Map.entry("DataField", List.of("name")),
            Map.entry("Grid", List.of("dataset")),
            Map.entry("GridColumn", List.of("field")),
            Map.entry("SortKey", List.of("field")),
            Map.entry("Measure", List.of("field")),
            Map.entry("Tree", List.of("dataset", "node")),
            Map.entry("DataChart", List.of("dataset", "type")));

    /** One line on each record, carried into the schema as its description. */
    static final Map<String, String> ABOUT = Map.ofEntries(
            Map.entry("ViewModel", "A bound view: everything a client renders, and nothing it must compute"),
            Map.entry("Cell", "One formatted value: text, an optional label, tone, link and the document path it came from"),
            Map.entry("LinkView", "A navigable link to another entity"),
            Map.entry("PanelView", "One panel: its id, kind and place, and its kind-specific data"),
            Map.entry("Provenance", "How the view was built: layout, source, generation, business date, freshness and masks"),
            Map.entry("KeyView", "A function key"),
            Map.entry("Frame", "One live-stream frame event: the patches that turn the client's view into the current one"),
            Map.entry("Patch", "One change to a rendered view"),
            Map.entry("Fields", "data of kv, status and provenance"),
            Map.entry("Table", "data of table and ladder"),
            Map.entry("Tabs", "data of tabs"),
            Map.entry("Chart", "data of line and area: numeric series over x labels"),
            Map.entry("Bars", "data of hbar"),
            Map.entry("Links", "data of links"),
            Map.entry("Text", "data of markdown"),
            Map.entry("Gauge", "data of gauge"),
            Map.entry("Surface", "data of surface: z[row][column] over x and y"),
            Map.entry("Waterfall", "data of waterfall"),
            Map.entry("Histogram", "data of histogram: bins computed on the server"),
            Map.entry("Scatter", "data of scatter"),
            Map.entry("Candles", "data of candlestick"),
            Map.entry("Graph", "data of graph"),
            Map.entry("Timeline", "data of timeline"),
            Map.entry("Pivot", "data of pivot"),
            Map.entry("Metric", "data of metric"),
            Map.entry("GeoMap", "data of map"),
            Map.entry("Dataset", "Rows by name that grid, tree and chart panels read (contract 1.1): raw values, formatted by the painter; one source"),
            Map.entry("DataField", "One field of a dataset: name, type, label and format"),
            Map.entry("Grid", "data of grid (contract 1.1): a dataset in a grid of typed columns, with groups, an order and a pivot"),
            Map.entry("Tree", "data of tree (contract 1.1): a hierarchy made of a dataset's rows"),
            Map.entry("DataChart", "data of chart (contract 1.1): a chart of a dataset"),
            Map.entry("Measure", "A figure made of a field's values"),
            Map.entry("GeoPoint", "A place drawn as a bubble over a map (contract 1.1)"));

    public static final String ID = "https://ajsinha.github.io/drishti-rachana/spec/1/viewmodel.schema.json";

    private ViewModelSchema() {}

    private static Map<String, Class<? extends PanelData>> dataByKind() {
        Map<String, Class<? extends PanelData>> m = new LinkedHashMap<>();
        m.put("kv", PanelData.Fields.class);
        m.put("table", PanelData.Table.class);
        m.put("tabs", PanelData.Tabs.class);
        m.put("line", PanelData.Chart.class);
        m.put("area", PanelData.Chart.class);
        m.put("hbar", PanelData.Bars.class);
        m.put("ladder", PanelData.Table.class);
        m.put("links", PanelData.Links.class);
        m.put("status", PanelData.Fields.class);
        m.put("provenance", PanelData.Fields.class);
        m.put("markdown", PanelData.Text.class);
        m.put("gauge", PanelData.Gauge.class);
        m.put("surface", PanelData.Surface.class);
        m.put("waterfall", PanelData.Waterfall.class);
        m.put("histogram", PanelData.Histogram.class);
        m.put("scatter", PanelData.Scatter.class);
        m.put("candlestick", PanelData.Candles.class);
        m.put("graph", PanelData.Graph.class);
        m.put("timeline", PanelData.Timeline.class);
        m.put("pivot", PanelData.Pivot.class);
        m.put("metric", PanelData.Metric.class);
        m.put("map", PanelData.GeoMap.class);
        m.put("grid", PanelData.Grid.class);                  // contract 1.1
        m.put("tree", PanelData.Tree.class);
        m.put("chart", PanelData.DataChart.class);
        return java.util.Collections.unmodifiableMap(m);
    }

    /**
     * The schema: a ViewModel at the root, every record under {@code $defs} ({@code Frame} for the live stream's events). Built from the
     * records at build time by {@code ViewCodeGenerator} (test sources) into {@code ViewModelSchemaText}; Core reads no record by reflection.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> build() {
        return (Map<String, Object>) Json.read(ViewModelSchemaText.JSON);
    }
}
