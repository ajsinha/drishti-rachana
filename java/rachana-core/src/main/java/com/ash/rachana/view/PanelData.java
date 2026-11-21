/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.view;

import com.ash.rachana.view.ViewModel.Cell;
import com.ash.rachana.view.ViewModel.LinkView;
import java.util.List;
import java.util.Map;

/** Kind-specific panel content. One record per panel kind (some kinds share one). */
public sealed interface PanelData {

    /** Label/value fields for {@code kv}, {@code status} and {@code provenance}. */
    record Fields(List<Cell> fields, @Include(Include.Rule.NON_NULL) String ev, @Include(Include.Rule.NON_EMPTY) Map<String, Object> set) implements PanelData {

        /** As it was before {@code set} (contract 1.2): {@code set} is what a point-click on the mark writes into the screen's state. */
        public Fields(List<Cell> fields, String ev) {
            this(fields, ev, null);
        }

        /** As it was before {@code ev} (contract 1.2): a record nobody clicks; {@code ev} is "0" on a panel that emits on point-click. */
        public Fields(List<Cell> fields) {
            this(fields, null);
        }
    }

    /**
     * {@code table} and {@code ladder}.
     *
     * @param columns header labels
     * @param numeric per column, whether it is right-aligned
     * @param rows the rows
     * @param total total row, or null
     * @param more "N more trades", or null
     * @param search the panel offers a filter box in its heading (Sutra option {@code search: false} turns it off)
     * @param pivot Pivot (the pivot screen) the Sutra opts into ({@code pivot:}): its fields and the arrangement it opens with; null without one
     * @param expand levels shown open at first (Sutra {@code expand}); present only on a tree, a table whose Sutra says {@code children}
     * @param levels the deeper levels a row drills into (Sutra {@code levels}): each level's title and columns; null for a plain table
     * @param root the first step of the breadcrumb (Sutra {@code root}, read against the document), or null
     * @param emptyLabel the words shown when there are no rows (Sutra {@code empty}), or null for the usual "No data available"
     */
    @Include(Include.Rule.NON_NULL)
    record Table(List<String> columns, List<Boolean> numeric, List<Row> rows, Row total, String more, boolean search,
            java.util.Map<String, Object> pivot, Integer expand, List<Level> levels, String root, String emptyLabel) implements PanelData {

        public Table(List<String> columns, List<Boolean> numeric, List<Row> rows, Row total, String more, boolean search,
                java.util.Map<String, Object> pivot, Integer expand) {
            this(columns, numeric, rows, total, more, search, pivot, expand, null, null, null);
        }

        public Table(List<String> columns, List<Boolean> numeric, List<Row> rows, Row total, String more) {
            this(columns, numeric, rows, total, more, true, null, null);
        }

        public Table(List<String> columns, List<Boolean> numeric, List<Row> rows, Row total, String more, boolean search) {
            this(columns, numeric, rows, total, more, search, null, null);
        }

        public Table(List<String> columns, List<Boolean> numeric, List<Row> rows, Row total, String more, boolean search,
                java.util.Map<String, Object> pivot) {
            this(columns, numeric, rows, total, more, search, pivot, null);
        }
    }

    /**
     * @param cells one per column
     * @param highlight draw this row highlighted
     * @param path document path of the row, for live patches
     * @param children the rows nested under this one (Sutra {@code children}), each with its own children; absent for a leaf
     * @param pick the value a click on the row sets in a control ({@code on-select}); absent when the panel does not select
     * @param crumb on a table with {@code levels}: the row's name in the breadcrumb ("Leg 2 · Float"), else absent
     * @param kidsTotal on a table with {@code levels}: the total row of the rows this one drills into, else absent
     * @param set what a click on the row writes into the screen's state (contract 1.2, I1 {@code set: { state, to }}): state id to the
     *     value, evaluated for this row; absent when the panel's {@code on: row-click} writes no state
     */
    record Row(List<Cell> cells, boolean highlight, String path, @Include(Include.Rule.NON_EMPTY) List<Row> children,
            @Include(Include.Rule.NON_NULL) String pick, @Include(Include.Rule.NON_NULL) String crumb,
            @Include(Include.Rule.NON_NULL) Row kidsTotal, @Include(Include.Rule.NON_NULL) String ev,
            @Include(Include.Rule.NON_EMPTY) Map<String, Object> set) {

        /** A row that writes no state. */
        public Row(List<Cell> cells, boolean highlight, String path, List<Row> children, String pick, String crumb, Row kidsTotal, String ev) {
            this(cells, highlight, path, children, pick, crumb, kidsTotal, ev, null);
        }

        /** The same row writing {@code next} into the screen's state when clicked (none: null or empty). */
        public Row withSet(Map<String, Object> next) {
            return new Row(cells, highlight, path, children, pick, crumb, kidsTotal, ev, next);
        }

        /** A row without an event key ({@code ev}: on a panel that emits on row-click, the row's place in the data, sent back by a click). */
        public Row(List<Cell> cells, boolean highlight, String path, List<Row> children, String pick, String crumb, Row kidsTotal) {
            this(cells, highlight, path, children, pick, crumb, kidsTotal, null);
        }

        public Row(List<Cell> cells, boolean highlight, String path, List<Row> children, String pick) {
            this(cells, highlight, path, children, pick, null, null);
        }

        public Row(List<Cell> cells, boolean highlight, String path, List<Row> children) {
            this(cells, highlight, path, children, null);
        }

        public Row(List<Cell> cells, boolean highlight, String path) {
            this(cells, highlight, path, null, null);
        }
    }

    /**
     * One level a table's rows drill into (Sutra {@code levels}).
     *
     * @param title what the rows of the level are ("Cash flows")
     * @param columns header labels of the level
     * @param numeric per column, whether it is right-aligned
     */
    record Level(String title, List<String> columns, List<Boolean> numeric) {}

    /**
     * {@code tabs}.
     *
     * @param layout {@code tabs} or {@code columns}
     * @param tabs one per element
     */
    record Tabs(String layout, List<Tab> tabs) implements PanelData {}

    /**
     * @param title the tab's heading
     * @param fields the tab's own fields (the tabs panel's {@code body})
     * @param panels the panels inside the tab ({@code panels:}), bound with {@code @} the tab's element; absent without any
     */
    record Tab(String title, List<Cell> fields, @Include(Include.Rule.NON_EMPTY) List<ViewModel.PanelView> panels) {

        public Tab {
            panels = panels == null ? List.of() : panels;
        }

        public Tab(String title, List<Cell> fields) {
            this(title, fields, List.of());
        }
    }

    /**
     * {@code line} and {@code area}.
     *
     * @param x axis labels
     * @param series one or more series
     * @param mark x label to highlight, or null
     * @param markText caption for the mark ({@code 5Y point 3.60%}), or null
     * @param limit horizontal limit line value, or null
     * @param limitLabel caption for the limit
     * @param source the entity the points came from, or null
     * @param unit axis unit
     * @param step drawn as steps (a value holds until the next point: a notional schedule), or null for a plain line
     */
    @Include(Include.Rule.NON_NULL)
    record Chart(List<String> x, List<Series> series, String mark, String markText, Double limit, String limitLabel,
            LinkView source, String unit, Boolean step, @Include(Include.Rule.NON_EMPTY) List<String> ev,
            @Include(Include.Rule.NON_EMPTY) List<Map<String, Object>> set) implements PanelData {

        /** As it was before {@code set} (contract 1.2): beside {@code x}, what a point-click on each point writes into the screen's state. */
        public Chart(List<String> x, List<Series> series, String mark, String markText, Double limit, String limitLabel, LinkView source, String unit,
                Boolean step, List<String> ev) {
            this(x, series, mark, markText, limit, limitLabel, source, unit, step, ev, null);
        }


        public Chart(List<String> x, List<Series> series, String mark, String markText, Double limit, String limitLabel, LinkView source, String unit) {
            this(x, series, mark, markText, limit, limitLabel, source, unit, null);
        }

        /** As it was before {@code ev} (contract 1.2): {@code ev}, beside {@code x}, is each point's row in the data on a panel that emits on point-click. */
        public Chart(List<String> x, List<Series> series, String mark, String markText, Double limit, String limitLabel, LinkView source, String unit,
                Boolean step) {
            this(x, series, mark, markText, limit, limitLabel, source, unit, step, null);
        }
    }

    record Series(String label, List<Double> values, String tone, @Include(Include.Rule.NON_NULL) String mark,
            @Include(Include.Rule.NON_NULL) String axis, @Include(Include.Rule.NON_EMPTY) List<String> ev) {

        /**
         * A series of contract 1.0: a line on the left axis.
         *
         * <p>Contract 1.2 (a line panel's {@code layers} or {@code by}): {@code mark} {@code bar}, {@code area} or {@code point} (none: a
         * line), {@code axis} {@code right} (none: the left), {@code ev} beside {@code x} each point's row in the data (null where the
         * series has no point) on a panel split {@code by} a field that emits on point-click.
         */
        public Series(String label, List<Double> values, String tone) {
            this(label, values, tone, null, null, null);
        }
    }

    /** {@code hbar}: bars scaled to the largest absolute value. */
    @Include(Include.Rule.NON_NULL)
    record Bars(List<Bar> bars, String shape, String total) implements PanelData {

        public Bars(List<Bar> bars) {
            this(bars, null, null);
        }
    }

    record Bar(String label, double value, String text, String tone, @Include(Include.Rule.NON_NULL) String ev,
            @Include(Include.Rule.NON_EMPTY) Map<String, Object> set) {

        /** A bar that writes no state ({@code set}: what a click on it writes into the screen's state, contract 1.2, I1). */
        public Bar(String label, double value, String text, String tone, String ev) {
            this(label, value, text, tone, ev, null);
        }

        /** The same bar writing {@code next} into the screen's state when clicked (none: null or empty). */
        public Bar withSet(Map<String, Object> next) {
            return new Bar(label, value, text, tone, ev, next);
        }

        /** A bar without an event key ({@code ev}: on an hbar that emits on bar-click, the bar's row in the data). */
        public Bar(String label, double value, String text, String tone) {
            this(label, value, text, tone, null);
        }
    }

    /** {@code links}. */
    record Links(List<LinkItem> links) implements PanelData {}

    /**
     * @param label {@code Netting set}
     * @param text the target id or name
     * @param link the target
     * @param badge short summary from the target ({@code EE 4.1m})
     * @param status {@code resolved}, {@code pending} or {@code missing}
     */
    record LinkItem(String label, String text, LinkView link, String badge, String status) {}

    /** {@code markdown}. */
    record Text(String text) implements PanelData {}

    /**
     * {@code surface}: {@code z[row][column]} over {@code x} (the columns' labels) and {@code y} (one per row);
     * null where the document has no number. {@code view} is {@code heatmap} (default) or {@code 3d}.
     */
    @Include(Include.Rule.NON_NULL)
    record Surface(List<String> x, List<String> y, List<List<Double>> z, Double min, Double max, String fmt, String unit, String view)
            implements PanelData {}

    /**
     * {@code metric}: one headline figure.
     *
     * @param value the figure (its label is the panel's {@code label})
     * @param delta the change against a previous value, or null
     * @param unit a short unit, or null
     * @param caption small text under the figure, or null
     */
    record Metric(Cell value, Cell delta, String unit, String caption, @Include(Include.Rule.NON_NULL) String ev, @Include(Include.Rule.NON_EMPTY) Map<String, Object> set) implements PanelData {

        /** As it was before {@code set} (contract 1.2): {@code set} is what a point-click on the mark writes into the screen's state. */
        public Metric(Cell value, Cell delta, String unit, String caption, String ev) {
            this(value, delta, unit, caption, ev, null);
        }

        /** As it was before {@code ev} (contract 1.2): a figure nobody clicks; {@code ev} is "0" on a panel that emits on point-click. */
        public Metric(Cell value, Cell delta, String unit, String caption) {
            this(value, delta, unit, caption, null);
        }
    }

    /**
     * {@code map}: the world, or one country's regions, with a place highlighted, shaded by a measure, and clickable.
     *
     * @param mode {@code world} or {@code region}
     * @param highlight the country to highlight as the document names it (an ISO code or a name), or null
     * @param region for {@code region} mode, the country whose regions are drawn, as the document names it
     * @param link what a click opens, or null when the map is only a picture
     * @param shade the per-country figures that colour the map, or null
     * @param unit the unit of the figures, or null
     * @param points places drawn as bubbles over the map (contract 1.1), left out when there are none
     */
    @Include(Include.Rule.NON_NULL)
    record GeoMap(String mode, String highlight, String region, GeoLink link, GeoShade shade, String unit,
            @Include(Include.Rule.NON_EMPTY) List<GeoPoint> points) implements PanelData {

        /** A map without points (contract 1.0). */
        public GeoMap(String mode, String highlight, String region, GeoLink link, GeoShade shade, String unit) {
            this(mode, highlight, region, link, shade, unit, null);
        }
    }

    /**
     * A place drawn as a bubble over a map (contract 1.1): where it is, its figure (the bubble's size) and the figure's text.
     *
     * @param label its name (the bubble's tooltip)
     * @param lat its latitude in degrees
     * @param lon its longitude in degrees
     * @param value its figure (sizes the bubble), or none
     * @param text the figure as the reader reads it, or none
     * @param tone its tone, or none
     * @param link what a click opens, or none
     */
    @Include(Include.Rule.NON_DEFAULT)
    record GeoPoint(String label, @Include(Include.Rule.ALWAYS) double lat, @Include(Include.Rule.ALWAYS) double lon, Double value, String text, String tone, LinkView link) {}

    /**
     * What a click on the map opens.
     *
     * @param kind the entity kind
     * @param template the id template ({@code JUR-{iso3}})
     * @param codes the codes (of the template's token) for which an entity exists: the others are not clickable
     * @param note why nothing is clickable (no access to the kind, or none listed), or null
     */
    @Include(Include.Rule.NON_NULL)
    record GeoLink(String kind, String template, List<String> codes, String note) {}

    /**
     * The figures that shade a map.
     *
     * @param label the legend's title
     * @param items one figure per place that has one
     * @param note why the map is not shaded (masked, no access, not read in time), or null
     */
    @Include(Include.Rule.NON_NULL)
    record GeoShade(String label, String unit, List<ShadeItem> items, String note) {}

    /** One place's figure: its code, the number and the formatted text. */
    record ShadeItem(String code, double value, String text) {}

    /** {@code gauge}. */
    record Gauge(double value, double max, String text, String label, @Include(Include.Rule.NON_NULL) String ev, @Include(Include.Rule.NON_EMPTY) Map<String, Object> set) implements PanelData {

        /** As it was before {@code set} (contract 1.2): {@code set} is what a point-click on the mark writes into the screen's state. */
        public Gauge(double value, double max, String text, String label, String ev) {
            this(value, max, text, label, ev, null);
        }

        /** As it was before {@code ev} (contract 1.2): a gauge nobody clicks; {@code ev} is "0" on a panel that emits on point-click. */
        public Gauge(double value, double max, String text, String label) {
            this(value, max, text, label, null);
        }
    }

    /**
     * {@code waterfall}: ordered steps as floating bars.
     *
     * @param steps in order; a total step is drawn from zero
     * @param unit axis unit, or null
     */
    @Include(Include.Rule.NON_NULL)
    record Waterfall(List<Step> steps, String unit) implements PanelData {}

    /**
     * @param label {@code Carry}
     * @param value the signed contribution, or the level of a total
     * @param from where the bar starts (the running total before a contribution, zero for a total)
     * @param to where the bar ends (the running total after it)
     * @param text the value formatted
     * @param tone {@code pos} (up), {@code neg} (down) or {@code link} (a total)
     * @param total drawn as a full bar from zero
     */
    record Step(String label, double value, double from, double to, String text, String tone, boolean total, @Include(Include.Rule.NON_NULL) String ev,
            @Include(Include.Rule.NON_EMPTY) Map<String, Object> set) {

        /** As it was before {@code set} (contract 1.2): {@code set} is what a point-click on the mark writes into the screen's state. */
        public Step(String label, double value, double from, double to, String text, String tone, boolean total, String ev) {
            this(label, value, from, to, text, tone, total, ev, null);
        }

        /** As it was before {@code ev} (contract 1.2): {@code ev} is the step's row in the data on a panel that emits on point-click. */
        public Step(String label, double value, double from, double to, String text, String tone, boolean total) {
            this(label, value, from, to, text, tone, total, null);
        }
    }

    /**
     * {@code histogram}: the distribution of a list of numbers.
     *
     * @param bins equal-width bins from the smallest value to the largest
     * @param markers vertical lines (VaR, expected shortfall, mean)
     * @param count how many numbers were binned
     * @param dropped values left out (not numbers, or beyond the configured maximum)
     * @param fmt the format of bin edges and markers
     * @param unit axis unit
     */
    @Include(Include.Rule.NON_NULL)
    record Histogram(List<Bin> bins, List<Marker> markers, int count, int dropped, String fmt, String unit) implements PanelData {}

    /** One bin: {@code [from, to)}, the last one closed; {@code label} is its range formatted. */
    record Bin(double from, double to, int count, String label) {}

    /** A marker line at {@code value}, {@code text} formatted, {@code tone} its colour. */
    record Marker(String label, double value, String text, String tone) {}

    /**
     * {@code scatter}: one point per row.
     *
     * @param points the points
     * @param groups the distinct groups, in first-seen order (a colour each)
     * @param xLabel horizontal axis title
     * @param yLabel vertical axis title
     * @param sized whether points carry a size
     * @param more points left out beyond the configured maximum
     */
    @Include(Include.Rule.NON_NULL)
    record Scatter(List<Point> points, List<String> groups, String xLabel, String yLabel, boolean sized, int more) implements PanelData {}

    /** A point: its measures and their text, an optional size, label, group and the entity the label names. */
    @Include(Include.Rule.NON_NULL)
    record Point(double x, double y, Double size, String xText, String yText, String label, String group, LinkView link, String ev,
            @Include(Include.Rule.NON_EMPTY) Map<String, Object> set) {

        /** As it was before {@code set} (contract 1.2): {@code set} is what a point-click on the mark writes into the screen's state. */
        public Point(double x, double y, Double size, String xText, String yText, String label, String group, LinkView link, String ev) {
            this(x, y, size, xText, yText, label, group, link, ev, null);
        }

        /** As it was before {@code ev} (contract 1.2): {@code ev} is the point's row in the data on a panel that emits on point-click. */
        public Point(double x, double y, Double size, String xText, String yText, String label, String group, LinkView link) {
            this(x, y, size, xText, yText, label, group, link, null);
        }
    }

    /**
     * {@code candlestick}: open, high, low and close by date, oldest first.
     *
     * @param candles the bars
     * @param volume whether candles carry volume
     * @param last the last close, formatted
     * @param change the change over the last bar, formatted, with its {@code tone}
     */
    @Include(Include.Rule.NON_NULL)
    record Candles(List<Candle> candles, boolean volume, String last, String change, String tone, String fmt, String unit)
            implements PanelData {}

    /**
     * One bar: the four prices and the volume as numbers (for the chart) and as text (for the Data table: a client formats nothing).
     *
     * @param openText the open price in the panel's {@code fmt}; likewise {@code highText}, {@code lowText}, {@code closeText}
     * @param volumeText the volume as text, or null without a volume
     */
    @Include(Include.Rule.NON_NULL)
    record Candle(String x, double open, double high, double low, double close, Double volume,
            String openText, String highText, String lowText, String closeText, String volumeText, String ev, @Include(Include.Rule.NON_EMPTY) Map<String, Object> set) {

        /** As it was before {@code set} (contract 1.2): {@code set} is what a point-click on the mark writes into the screen's state. */
        public Candle(String x, double open, double high, double low, double close, Double volume, String openText, String highText, String lowText,
                String closeText, String volumeText, String ev) {
            this(x, open, high, low, close, volume, openText, highText, lowText, closeText, volumeText, ev, null);
        }

        /** As it was before {@code ev} (contract 1.2): {@code ev} is the candle's row in the data on a panel that emits on point-click. */
        public Candle(String x, double open, double high, double low, double close, Double volume, String openText, String highText, String lowText,
                String closeText, String volumeText) {
            this(x, open, high, low, close, volume, openText, highText, lowText, closeText, volumeText, null);
        }

        /** A bar without its texts (a client then prints the numbers as they are). */
        public Candle(String x, double open, double high, double low, double close, Double volume) {
            this(x, open, high, low, close, volume, null, null, null, null, null);
        }
    }

    /**
     * {@code graph}: nodes and the edges between them.
     *
     * @param nodes the entities
     * @param edges the relations; an edge to a node that is not listed is left out
     * @param layout {@code tree} (layered from the roots) or {@code force}
     * @param more nodes left out beyond the configured maximum
     */
    record Graph(List<Node> nodes, List<Edge> edges, String layout, int more) implements PanelData {}

    /**
     * @param id the node's identifier (the entity id when it names one)
     * @param label what the node shows
     * @param group its category (colour and legend)
     * @param link the entity it opens, or null
     * @param focus the node is the entity this view shows
     */
    @Include(Include.Rule.NON_NULL)
    record Node(String id, String label, String group, LinkView link, boolean focus) {}

    @Include(Include.Rule.NON_NULL)
    record Edge(String from, String to, String label) {}

    /** {@code timeline}: dated events, oldest first; {@code more} events left out beyond the configured maximum. */
    record Timeline(List<Event> events, int more) implements PanelData {}

    /**
     * @param date the date or timestamp as written
     * @param label {@code Confirmed}
     * @param detail a short description, or null
     * @param status the status text, or null
     * @param tone the status tone ({@code ok}, {@code warn}, {@code bad}), or null
     */
    @Include(Include.Rule.NON_NULL)
    record Event(String date, String label, String detail, String status, String tone, String ev, @Include(Include.Rule.NON_EMPTY) Map<String, Object> set) {

        /** As it was before {@code set} (contract 1.2): {@code set} is what a point-click on the mark writes into the screen's state. */
        public Event(String date, String label, String detail, String status, String tone, String ev) {
            this(date, label, detail, status, tone, ev, null);
        }

        /** As it was before {@code ev} (contract 1.2): {@code ev} is the event's row in the data on a panel that emits on point-click. */
        public Event(String date, String label, String detail, String status, String tone) {
            this(date, label, detail, status, tone, null);
        }
    }

    /**
     * {@code pivot}: an aggregate of the rows by one field (down) and across another.
     *
     * @param by the row field's name (the corner heading)
     * @param columns the column keys, in first-seen order
     * @param rows one per row key, in first-seen order
     * @param totals the column totals and the grand total (last), or null when totals are off
     * @param agg the aggregation
     * @param heat colour cells by value
     * @param min the smallest cell value (heat scale)
     * @param max the largest cell value
     * @param more row keys left out beyond the configured maximum
     * @param levels the fields the rows group by, outermost first; present only when there is more than one (nested groups)
     * @param expand levels shown open at first (Sutra {@code expand}); present only with {@code levels}
     * @param across the column field's name; present only with {@code levels}
     */
    @Include(Include.Rule.NON_NULL)
    record Pivot(String by, List<String> columns, List<PivotRow> rows, List<Cell> totals, String agg, boolean heat, Double min, Double max,
            int more, List<String> levels, Integer expand, String across) implements PanelData {

        public Pivot(String by, List<String> columns, List<PivotRow> rows, List<Cell> totals, String agg, boolean heat, Double min,
                Double max, int more) {
            this(by, columns, rows, totals, agg, heat, min, max, more, null, null, null);
        }
    }

    /**
     * @param label the row key
     * @param cells one per column: the aggregate formatted and toned (text empty where no row falls)
     * @param values the aggregates (null where no row falls), for the heat scale and export
     * @param total the row total, or null when totals are off
     * @param path in a nested pivot, the keys from the outermost level down to this row; null in a single-level pivot
     * @param group in a nested pivot, true for a group row (its cells are the subtotals of the rows after it that extend its path)
     */
    @Include(Include.Rule.NON_NULL)
    record PivotRow(String label, List<Cell> cells, List<Double> values, Cell total, List<String> path, Boolean group) {

        public PivotRow(String label, List<Cell> cells, List<Double> values, Cell total) {
            this(label, cells, values, total, null, null);
        }
    }

    // ---- contract 1.1: panels that read a dataset (ViewModel.datasets) -------------------------------------------------------------

    /**
     * {@code grid} (contract 1.1): a dataset's rows in a grid of typed columns (virtual rows, sort, filter, row groups, a pivot computed
     * by the painter, export). Its figures are formatted by the painter with each column's {@code fmt}.
     *
     * @param dataset the dataset it reads (ViewModel.datasets)
     * @param columns the columns, in order; none: every field of the dataset
     * @param group the fields the rows are grouped by, outermost first
     * @param sort the order of the rows
     * @param pivot a pivot of the rows, or none
     * @param search whether the panel offers a filter box
     * @param emptyLabel the words shown when the dataset has no rows, or none
     */
    @Include(Include.Rule.NON_DEFAULT)
    record Grid(String dataset, List<GridColumn> columns, List<String> group, List<SortKey> sort, GridPivot pivot, boolean search,
            String emptyLabel) implements PanelData {}

    /**
     * A column of a {@code grid}.
     *
     * @param field the dataset's field it shows
     * @param label its heading, or none (the field's label, else its name)
     * @param fmt its format, or none (the field's)
     * @param tone {@code sign} (positive and negative toned) or a tone word for every value, or none
     * @param width its width in pixels, or 0 (the painter's)
     * @param pinned {@code left} or {@code right}, or none
     * @param agg how a group's figure is made of its rows' ({@code sum}, {@code count}, {@code min}, {@code max}, {@code avg},
     *     {@code first}, {@code last}, {@code distinct}), or none
     * @param hidden whether it starts hidden
     * @param bar whether each number is drawn with a bar in its cell, as long as the value is large against the column's largest (contract
     *     1.2; the text still says the value)
     */
    @Include(Include.Rule.NON_DEFAULT)
    record GridColumn(String field, String label, String fmt, String tone, int width, String pinned, String agg, boolean hidden, boolean bar) {

        /** A column without a bar (contract 1.1). */
        public GridColumn(String field, String label, String fmt, String tone, int width, String pinned, String agg, boolean hidden) {
            this(field, label, fmt, tone, width, pinned, agg, hidden, false);
        }
    }

    /**
     * One key of an order.
     *
     * @param field the field
     * @param desc whether descending
     */
    @Include(Include.Rule.NON_DEFAULT)
    record SortKey(String field, boolean desc) {}

    /**
     * A pivot of a dataset, computed by the painter: rows by {@code rows}, columns by {@code columns}, cells the {@code values}.
     *
     * @param rows the fields down the side, outermost first
     * @param columns the fields across the top, outermost first
     * @param values the figures
     */
    @Include(Include.Rule.NON_DEFAULT)
    record GridPivot(List<String> rows, List<String> columns, List<Measure> values) {}

    /**
     * A figure made of a field's values: a pivot's value, a tree's measure, a chart's series.
     *
     * @param field the field
     * @param agg how rows are combined ({@code sum}, {@code count}, {@code min}, {@code max}, {@code avg}, {@code first}, {@code last},
     *     {@code distinct}), or none: the value as it is
     * @param label its name, or none (the field's)
     * @param fmt its format, or none (the field's)
     */
    @Include(Include.Rule.NON_DEFAULT)
    record Measure(String field, String agg, String label, String fmt) {}

    /**
     * {@code tree} (contract 1.1): a hierarchy made of a dataset's rows, each naming its node and its parent (or its path), drawn as a
     * tree grid, an org chart, a sunburst or an icicle. A node's measures are its row's; a node no row names adds up its children's.
     *
     * @param dataset the dataset it reads
     * @param node the field that names a row's node
     * @param parent the field that names its parent (empty at the top), or none when {@code path} is given
     * @param path the field that holds a row's path from the top (a list, or text with {@code /} between the steps), or none
     * @param label the field shown as a node's name, or none ({@code node})
     * @param measures the figures shown on each node
     * @param mode {@code grid} (the default), {@code org}, {@code sunburst} or {@code icicle}
     * @param open the depth open at first (0: the painter's), -1 for all
     */
    @Include(Include.Rule.NON_DEFAULT)
    record Tree(String dataset, String node, String parent, String path, String label, List<Measure> measures, String mode, int open)
            implements PanelData {}

    /**
     * {@code chart} (contract 1.1): a chart of a dataset. {@code x} the category or time down the axis, {@code y} the figures (one series
     * each), {@code by} a field whose values split the series; with an {@code agg} the rows are combined per {@code x} and {@code by}.
     *
     * @param dataset the dataset it reads
     * @param type {@code bar}, {@code line}, {@code area}, {@code scatter}, {@code pie}, {@code donut}, {@code heatmap} or {@code histogram}
     * @param x the field along the horizontal axis (a pie's and a donut's category; a heatmap's columns)
     * @param y the figures (a heatmap's and a histogram's: the first)
     * @param by the field whose values split the series (a heatmap's rows), or none
     * @param stack {@code sum} or {@code percent} to stack bars and areas, or none
     * @param horizontal whether bars lie along the vertical axis
     * @param xLabel the horizontal axis's title, or none
     * @param yLabel the vertical axis's title, or none
     * @param bins a histogram's number of bins, or 0 (the painter's)
     * @param limit the most categories drawn, largest first (the rest left out), or 0 (all)
     */
    @Include(Include.Rule.NON_DEFAULT)
    record DataChart(String dataset, String type, String x, List<Measure> y, String by, String stack, boolean horizontal, String xLabel,
            String yLabel, int bins, int limit) implements PanelData {}

    /**
     * {@code treemap} (contract 1.2): rectangles sized by a figure, nested by {@code group}.
     *
     * @param cells the rows with a positive figure, in their order
     * @param total the figures' sum, formatted, or none
     */
    @Include(Include.Rule.NON_NULL)
    record Treemap(List<TreemapCell> cells, String total) implements PanelData {}

    /**
     * One rectangle of a treemap.
     *
     * @param label its name
     * @param group its outer level, or none
     * @param value its size
     * @param text the size formatted
     * @param tone {@code pos} or {@code neg} by the sign of the panel's {@code color} figure, or none
     * @param ev its row in the panel's data on a panel that emits on point-click, or none
     * @param set what a point-click on it writes into the state, or none
     */
    @Include(Include.Rule.NON_NULL)
    record TreemapCell(String label, String group, double value, String text, String tone, String ev, Map<String, Object> set) {}

    /**
     * {@code bullet} (contract 1.2): one figure on a band, against one bound ({@code upper}) or two.
     *
     * @param value the figure, or null when it is not a number
     * @param text the figure formatted
     * @param label its caption, or none
     * @param min the scale's start
     * @param max the scale's end
     * @param bands the zones behind it ({@code ok}, {@code warn}), in order
     * @param lower the lower bound, or none
     * @param lowerText the lower bound formatted, or none
     * @param upper the upper bound (the limit), or none
     * @param upperText the upper bound formatted, or none
     * @param tone the figure's zone: {@code ok}, {@code warn} or {@code bad} (past a bound)
     * @param caption words under it, or none
     */
    @Include(Include.Rule.NON_NULL)
    record Bullet(Double value, String text, String label, double min, double max, List<Band> bands, Double lower, String lowerText, Double upper,
            String upperText, String tone, String caption) implements PanelData {}

    /**
     * A zone of a bullet's band.
     *
     * @param from where it starts
     * @param to where it ends
     * @param tone {@code ok} or {@code warn}
     */
    record Band(double from, double to, String tone) {}
}
