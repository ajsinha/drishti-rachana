/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.view;

import java.util.List;
import java.util.Map;

/**
 * A bound view: everything the console renders, and nothing it must compute. Immutable, serialised as
 * JSON. Every value is pre-formatted text plus a tone, so all clients render numbers identically.
 *
 * @param ref the entity
 * @param mnemonic the command mnemonic for the kind ({@code TRD})
 * @param title the title line
 * @param strip header key figures
 * @param panels panels in layout order; {@link PanelView#area()} says which column
 * @param keys function keys
 * @param provenance how the view was built
 * @param timings stage timings in milliseconds (fetch, layout, bind, links, total)
 * @param controls the Sutra's controls with their options and current values (empty when it declares none)
 * @param selects the panels whose rows or bars set a control when clicked
 * @param layout the Sutra's {@code layout:} (RACHANA_REFERENCE.md 13: areas, columns, split, tabs, sections, sticky, arrange, phone) with each
 *     top-level panel's place ({@code place: {id: {area, order, section, collapsible}}}), or null: the panels flow
 * @param phone what the Sutra says about the phone (docs/guides/BUILD_GUIDE.md section 5): {@code panels: {id: {tier, from, rows, columns, sort,
 *     series, figures, tab}}}, {@code order}, {@code default} and the {@code summary} line read against the document; null when it says
 *     nothing (the console then uses each panel kind's default tier)
 * @param contract the ViewModel contract this view speaks, {@code major.minor} ({@link #CONTRACT}; VIEWMODEL.md section 8): a client
 *     refuses a major it does not know (the renderer's unknown-contract error) and ignores what a newer minor adds. It is the smallest
 *     minor that describes this document (SUTRA_EXPANSION 6.3 rule 2): {@code 1.1} only when the view has datasets or a panel of a
 *     contract-1.1 kind ({@link #KINDS_1_1}) or a map with points, else {@code 1.0}; a stamp given lower than that is raised
 * @param datasets rows by name that panels of the kinds {@code grid}, {@code tree} and {@code chart} read (contract 1.1); left out
 *     when there are none
 * @param state the screen's state values (contract 1.2, SUTRA_EXPANSION S1): each with its value now, whether the host keeps it in the
 *     address, and the parts that read it; left out when the Sutra holds none. A painter keeps the values, writes them as a row's or a
 *     bar's {@code set} says when it is clicked, and sends them back with the next bind ({@code state.<id>=...}); an older painter
 *     ignores it
 */
@Include(Include.Rule.NON_NULL)
public record ViewModel(
        Ref ref,
        String mnemonic,
        TitleView title,
        List<Cell> strip,
        List<PanelView> panels,
        List<KeyView> keys,
        Provenance provenance,
        Map<String, Double> timings,
        @Include(Include.Rule.NON_EMPTY) List<ControlView> controls,
        @Include(Include.Rule.NON_EMPTY) List<SelectView> selects,
        Map<String, Object> layout,
        String contract,
        @Include(Include.Rule.NON_EMPTY) Map<String, Object> phone,
        @Include(Include.Rule.NON_EMPTY) Map<String, Dataset> datasets,
        @Include(Include.Rule.NON_EMPTY) List<StateView> state) {

    /** The contract a ViewModel that uses nothing newer speaks (VIEWMODEL.md section 8): the minor moves for additions, the major for a break. */
    public static final String CONTRACT = "1.0";

    /** The newest contract minor this library writes. */
    public static final String NEWEST = "1.2";

    /** The panel kinds contract 1.1 adds: panels that read a dataset. */
    public static final List<String> KINDS_1_1 = List.of("grid", "tree", "chart");

    /** The panel kinds contract 1.2 adds. */
    public static final List<String> KINDS_1_2 = List.of("live");

    /** A view with its datasets and no state. */
    public ViewModel(Ref ref, String mnemonic, TitleView title, List<Cell> strip, List<PanelView> panels, List<KeyView> keys,
            Provenance provenance, Map<String, Double> timings, List<ControlView> controls, List<SelectView> selects, Map<String, Object> layout,
            String contract, Map<String, Object> phone, Map<String, Dataset> datasets) {
        this(ref, mnemonic, title, strip, panels, keys, provenance, timings, controls, selects, layout, contract, phone, datasets, null);
    }

    /** A view with its contract and phone settings, and no datasets. */
    public ViewModel(Ref ref, String mnemonic, TitleView title, List<Cell> strip, List<PanelView> panels, List<KeyView> keys,
            Provenance provenance, Map<String, Double> timings, List<ControlView> controls, List<SelectView> selects, Map<String, Object> layout,
            String contract, Map<String, Object> phone) {
        this(ref, mnemonic, title, strip, panels, keys, provenance, timings, controls, selects, layout, contract, phone, null);
    }

    /** A view with its contract and no phone settings. */
    public ViewModel(Ref ref, String mnemonic, TitleView title, List<Cell> strip, List<PanelView> panels, List<KeyView> keys,
            Provenance provenance, Map<String, Double> timings, List<ControlView> controls, List<SelectView> selects, Map<String, Object> layout,
            String contract) {
        this(ref, mnemonic, title, strip, panels, keys, provenance, timings, controls, selects, layout, contract, null);
    }

    /** A view with its layout, stamped with this server's {@link #CONTRACT}. */
    public ViewModel(Ref ref, String mnemonic, TitleView title, List<Cell> strip, List<PanelView> panels, List<KeyView> keys,
            Provenance provenance, Map<String, Double> timings, List<ControlView> controls, List<SelectView> selects, Map<String, Object> layout) {
        this(ref, mnemonic, title, strip, panels, keys, provenance, timings, controls, selects, layout, CONTRACT);
    }

    /** A view whose panels flow (no {@code layout:}). */
    public ViewModel(Ref ref, String mnemonic, TitleView title, List<Cell> strip, List<PanelView> panels, List<KeyView> keys,
            Provenance provenance, Map<String, Double> timings, List<ControlView> controls, List<SelectView> selects) {
        this(ref, mnemonic, title, strip, panels, keys, provenance, timings, controls, selects, null);
    }

    public ViewModel {
        controls = controls == null ? List.of() : List.copyOf(controls);
        selects = selects == null ? List.of() : List.copyOf(selects);
        datasets = datasets == null || datasets.isEmpty() ? null : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(datasets));
        state = state == null || state.isEmpty() ? null : List.copyOf(state);
        contract = stamp(contract == null || contract.isBlank() ? CONTRACT : contract, panels, datasets, state);
    }

    /** The stamp: the one given, raised to {@code 1.1} when the document uses what contract 1.1 adds (the smallest minor that describes it). */
    static String stamp(String given, List<PanelView> panels, Map<String, Dataset> datasets) {
        return stamp(given, panels, datasets, null);
    }

    /**
     * The stamp: the one given, raised to the smallest minor that describes the document: {@code 1.2} when it has state, else {@code 1.1}
     * when it uses what contract 1.1 adds.
     */
    static String stamp(String given, List<PanelView> panels, Map<String, Dataset> datasets, List<StateView> state) {
        int needed = state != null && !state.isEmpty() || uses12(panels) ? 2 : datasets != null && !datasets.isEmpty() || uses11(panels) ? 1 : 0;
        if (needed == 0 || !given.startsWith("1.")) {
            return given;
        }
        int minor;
        try {
            minor = Integer.parseInt(given.substring(2));
        } catch (NumberFormatException e) {
            return given;
        }
        return minor >= needed ? given : "1." + needed;
    }

    /** Whether a panel's marks carry what contract 1.2 adds: an {@code ev} beyond rows and bars (I1 point-click). */
    private static boolean uses12(List<PanelView> panels) {
        for (PanelView p : panels == null ? List.<PanelView>of() : panels) {
            Object d = p == null ? null : p.data();
            if (d == null) {
                continue;
            }
            if (d instanceof PanelData.Tabs t && t.tabs() != null && t.tabs().stream().anyMatch(tab -> tab != null && uses12(tab.panels()))) {
                return true;
            }
            if (KINDS_1_2.contains(p.kind()) || d instanceof PanelData.Grid g && g.columns().stream().anyMatch(PanelData.GridColumn::bar)) {
                return true;
            }
            boolean ev = switch (d) {
                case PanelData.Metric m -> m.ev() != null;
                case PanelData.Gauge g -> g.ev() != null;
                case PanelData.Fields f -> f.ev() != null;
                case PanelData.Chart c -> c.ev() != null && !c.ev().isEmpty();
                case PanelData.Waterfall w -> w.steps().stream().anyMatch(s -> s.ev() != null);
                case PanelData.Scatter s -> s.points().stream().anyMatch(x -> x.ev() != null);
                case PanelData.Candles c -> c.candles().stream().anyMatch(x -> x.ev() != null);
                case PanelData.Timeline t -> t.events().stream().anyMatch(x -> x.ev() != null);
                default -> false;
            };
            if (ev) {
                return true;
            }
        }
        return false;
    }

    private static boolean uses11(List<PanelView> panels) {
        if (panels == null) {
            return false;
        }
        for (PanelView p : panels) {
            if (p == null) {
                continue;
            }
            if (KINDS_1_1.contains(p.kind()) || p.data() instanceof PanelData.GeoMap g && g.points() != null && !g.points().isEmpty()) {
                return true;
            }
            if (p.data() instanceof PanelData.Tabs t && t.tabs() != null) {
                for (PanelData.Tab tab : t.tabs()) {
                    if (tab != null && uses11(tab.panels())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** A view without controls. */
    public ViewModel(Ref ref, String mnemonic, TitleView title, List<Cell> strip, List<PanelView> panels, List<KeyView> keys,
            Provenance provenance, Map<String, Double> timings) {
        this(ref, mnemonic, title, strip, panels, keys, provenance, timings, List.of(), List.of(), null);
    }

    /** The same view with another provenance. */
    public ViewModel withProvenance(Provenance p) {
        return new ViewModel(ref, mnemonic, title, strip, panels, keys, p, timings, controls, selects, layout, contract, phone, datasets, state);
    }

    /** The same view with its controls and selects. */
    public ViewModel withControls(List<ControlView> controls, List<SelectView> selects) {
        return new ViewModel(ref, mnemonic, title, strip, panels, keys, provenance, timings, controls, selects, layout, contract, phone, datasets, state);
    }

    /** The same view with its screen layout (RACHANA_REFERENCE.md 13). */
    public ViewModel withLayout(Map<String, Object> layout) {
        return new ViewModel(ref, mnemonic, title, strip, panels, keys, provenance, timings, controls, selects, layout, contract, phone, datasets, state);
    }

    /** The same view with its datasets (contract 1.1), or none (null or empty); the stamp follows. */
    public ViewModel withDatasets(Map<String, Dataset> datasets) {
        return new ViewModel(ref, mnemonic, title, strip, panels, keys, provenance, timings, controls, selects, layout, contract, phone, datasets, state);
    }

    /** The same view with what its Sutra says about the phone (docs/guides/BUILD_GUIDE.md section 5), or none (null). */
    public ViewModel withPhone(Map<String, Object> phone) {
        return new ViewModel(ref, mnemonic, title, strip, panels, keys, provenance, timings, controls, selects, layout, contract, phone, datasets, state);
    }

    /** The same view with its state values (contract 1.2), or none (null or empty); the stamp follows. */
    public ViewModel withState(List<StateView> state) {
        return new ViewModel(ref, mnemonic, title, strip, panels, keys, provenance, timings, controls, selects, layout, contract, phone, datasets, state);
    }

    /**
     * A state value of the screen (contract 1.2, SUTRA_EXPANSION S1).
     *
     * @param id the state's id ({@code state.<id>}; a fragment use's private state {@code <as>.<name>})
     * @param value its value now: text, a number, a boolean, an ISO date text, a list of text, or null when empty
     * @param url whether the host keeps it in the address (the view-state document, S4)
     * @param reads ids of the panels (and {@code strip} or {@code title}) whose expressions read it (only those are rebuilt when it changes)
     */
    public record StateView(String id, @Include(Include.Rule.ALWAYS) Object value, boolean url, List<String> reads) {}

    /** One option of a control: the value that is set and the text shown. */
    public record Opt(String value, String label) {}

    /**
     * A control as the reader sees it: what it is, the options they may pick (never a masked value), its value now and the panels
     * that read it (only those are rebuilt when it changes).
     *
     * @param id the control's id ({@code ctl.<id>})
     * @param kind {@code dropdown}, {@code multi-select}, {@code cascading}, {@code toggle}, {@code segmented}, {@code date},
     *     {@code date-range}, {@code number-range}, {@code top-n} or {@code search}
     * @param label the text beside it
     * @param placement {@code strip}, {@code header} or {@code side}
     * @param panel the panel whose header holds it, for {@code header}
     * @param parent the control a cascading one depends on
     * @param options the choices (a date-range's are its presets, resolved on the calendar)
     * @param value the current value (a range is {@code from..to}; empty when none)
     * @param values the current choices of a multi-select
     * @param required whether it cannot be cleared
     * @param remember whether the reader's last choice is kept
     * @param min a number range's lower bound (from the data or fixed), or null
     * @param max its upper bound
     * @param step a slider's step, or null
     * @param range a slider with two handles (its value is {@code from..to})
     * @param reads ids of the panels (and {@code strip} or {@code title}) whose expressions read it
     * @param truncated whether the options were cut at the limit
     */
    @Include(Include.Rule.NON_EMPTY)
    public record ControlView(String id, String kind, String label, String placement, String panel, String parent, List<Opt> options,
            String value, List<String> values, boolean required, boolean remember, String min, String max, String step, boolean range,
            List<String> reads, boolean truncated) {}

    /**
     * A panel whose click sets a control: a table row sets {@code by} to the value of its {@code field}; a bar sets it to its label.
     *
     * @param panel the panel clicked
     * @param by the control set
     * @param filter the panels the author says it filters (those that read the control are rebuilt)
     */
    public record SelectView(String panel, String by, List<String> filter) {}

    /** An entity address as sent to clients. */
    public record Ref(String kind, String id) {}

    /**
     * A navigable link.
     *
     * @param kind target kind
     * @param id target id
     * @param mnemonic command mnemonic for the target kind, when one exists
     */
    public record LinkView(String kind, String id, String mnemonic) {}

    /**
     * One formatted value.
     *
     * @param label label (strip, kv) or null (table cell)
     * @param text formatted text
     * @param tone theme tone ({@code pos}, {@code neg}, ...) or null
     * @param link when the value opens another entity
     * @param emphasis draw highlighted
     * @param path document path the value came from, for live patches
     */
    @Include(Include.Rule.NON_DEFAULT)
    public record Cell(String label, String text, String tone, LinkView link, boolean emphasis, String path) {

        public static Cell of(String label, String text) {
            return new Cell(label, text, null, null, false, null);
        }
    }

    /**
     * @param pill pill text
     * @param id the identifier
     * @param with counterparty text and link, or null
     */
    public record TitleView(String pill, String id, Cell with) {}

    /**
     * @param key {@code F2}
     * @param label what it does ({@code Legs}, {@code Netting set}, {@code Raw JSON})
     * @param action {@code panel}, {@code link}, {@code raw} or {@code impact}
     * @param panel target panel id for {@code panel}
     * @param link target for {@code link}
     */
    @Include(Include.Rule.NON_NULL)
    public record KeyView(String key, String label, String action, String panel, LinkView link) {}

    /**
     * @param layout {@code Sutra irs-vanilla v3 + inference}
     * @param fingerprint short shape fingerprint
     * @param source source system
     * @param generation source generation
     * @param fetchedAt ISO instant
     * @param live whether the source pushes updates
     */
    /** @param businessDate the business date the data is for, or {@code null} when the source is not dated */
    /**
     * Where a view came from. {@code updatedAt}: when its source last received new data (null when it cannot tell);
     * {@code staleAfter}: the source's threshold (ISO-8601 duration) or null; {@code stale}: older than that.
     */
    /** {@code sutra}: the name of the Sutra that laid the view out, or null (inference alone); personal layouts are kept by it. */
    /**
     * {@code masked}: how many values in the view the caller's field masks replaced ({@code •••}), 0 when nothing is masked for them;
     * {@code maskedPanels}: the ids of the panels that hold some, so an embedding page can say "some values are hidden".
     */
    public record Provenance(String layout, String fingerprint, String source, long generation, String fetchedAt, boolean live,
            String businessDate, String updatedAt, String staleAfter, boolean stale, String sutra, int masked, List<String> maskedPanels,
            @Include(Include.Rule.NON_DEFAULT) boolean partial, @Include(Include.Rule.NON_EMPTY) List<String> missing) {

        public Provenance {
            maskedPanels = maskedPanels == null ? List.of() : List.copyOf(maskedPanels);
            missing = missing == null ? List.of() : List.copyOf(missing);
        }

        public Provenance(String layout, String fingerprint, String source, long generation, String fetchedAt, boolean live,
                String businessDate, String updatedAt, String staleAfter, boolean stale, String sutra, int masked, List<String> maskedPanels) {
            this(layout, fingerprint, source, generation, fetchedAt, live, businessDate, updatedAt, staleAfter, stale, sutra, masked, maskedPanels,
                    false, List.of());
        }

        public Provenance(String layout, String fingerprint, String source, long generation, String fetchedAt, boolean live,
                String businessDate, String updatedAt, String staleAfter, boolean stale, String sutra) {
            this(layout, fingerprint, source, generation, fetchedAt, live, businessDate, updatedAt, staleAfter, stale, sutra, 0, List.of());
        }

        /**
         * The same provenance marked partial: the document says some of what makes it did not answer (a grouped page gathered across
         * a cluster with a node down: {@code partial: true} and {@code missing}, one line per node), so the page says so.
         */
        public Provenance withPartial(List<String> what) {
            return new Provenance(layout, fingerprint, source, generation, fetchedAt, live, businessDate, updatedAt, staleAfter, stale, sutra, masked,
                    maskedPanels, true, what);
        }

        public Provenance(String layout, String fingerprint, String source, long generation, String fetchedAt, boolean live, String businessDate) {
            this(layout, fingerprint, source, generation, fetchedAt, live, businessDate, null, null, false, null);
        }

        /** The same provenance with the mask counts of a built view. */
        public Provenance withMasked(int count, List<String> panels) {
            return new Provenance(layout, fingerprint, source, generation, fetchedAt, live, businessDate, updatedAt, staleAfter, stale, sutra, count, panels,
                    partial, missing);
        }
    }

    /**
     * @param id panel id
     * @param kind panel kind
     * @param title resolved title
     * @param code header tag
     * @param key function key
     * @param area {@code main} or {@code right}
     * @param inferred whether inference built or completed it
     * @param explanation why inference chose it
     * @param data kind-specific content
     * @param error set when binding this panel failed; the rest of the view is unaffected
     */
    /**
     * What the console needs of a panel's Sutra Next wiring (RACHANA_REFERENCE.md 7, 8): the events a click fires, the words shown while it waits on
     * an event (set only while it waits), its groups of children (where and when they show) and the source it reads.
     *
     * @param emits the events, each {@code name} and {@code on}
     * @param waits set while the panel waits on an event not fired: what it says instead of data
     * @param children per group of child panels: {@code place} and {@code show}
     * @param source the kind (and pack) of the source it reads, or null
     * @param parent the parent panel's id for a child panel, or null
     */
    @Include(Include.Rule.NON_EMPTY)
    public record Wire(List<Map<String, String>> emits, String waits, List<Map<String, String>> children, Map<String, String> source, String parent) {}

    /**
     * How often the console fetches a panel again ({@code refresh:} in the Sutra, RACHANA_REFERENCE.md 9).
     *
     * @param every seconds between fetches: the fastest the reader may choose
     * @param userCanPause whether the reader may pause it
     */
    public record RefreshView(int every, boolean userCanPause) {}

    /**
     * How fresh a panel with its own {@code source:} is (RACHANA_REFERENCE.md 9), apart from the page's: the connector that answered it and
     * the kind it read, when it was fetched, when that connector last received new data, its threshold and whether it is past it. The
     * console says "updated N s ago", "stale" and "X isn't answering" for that panel from these, falling back to the page's provenance.
     *
     * @param source the connector that answered (or the source the document names), or null
     * @param kind the kind the panel's source reads
     * @param fetchedAt when the source's document was fetched (ISO-8601), or null when it cannot tell (a list of entities)
     * @param updatedAt when the connector last received new data, or null
     * @param staleAfter the connector's threshold (ISO-8601 duration), or null
     * @param stale older than {@code staleAfter}
     */
    @Include(Include.Rule.NON_NULL)
    public record PanelProvenance(String source, String kind, String fetchedAt, String updatedAt, String staleAfter, boolean stale) {}

    /**
     * The state of a panel whose source is a computed kind (DATA_ARCHITECTURE.md › Reactive connectors, section 12.4): never a blank panel. The console draws
     * the result when there is one (dimmed while {@code computing}, amber when {@code stale}), the progress, and the message in words
     * ({@code failed}, {@code refused}); it listens to {@code requestId} on the live channel for progress and for the result when it lands.
     *
     * @param state {@code ready}, {@code computing}, {@code stale}, {@code failed} or {@code refused}
     * @param requestId the correlation id of this panel's read (its events go to this viewer only)
     * @param connector the reactive connector that computes it
     * @param message why it failed or was refused, or what it waits for; null when ready
     * @param progress the latest progress ({@code done}, {@code of}, a partial estimate), or null
     * @param hasResult whether a result (current or last good) is shown
     * @param stamp the result's stamp (pin, computedAt, engine, inputs), or null
     */
    @Include(Include.Rule.NON_NULL)
    public record PanelCompute(String state, String requestId, String connector, String message, Map<String, Object> progress, boolean hasResult,
            Map<String, Object> stamp) {}

    @Include(Include.Rule.NON_NULL)
    /**
     * @param empty nothing to show (the document lacks what the panel asks for); the console says "No data available"
     * @param span the Sutra's width for the panel in columns of a 12-column grid, or null (the whole column)
     * @param height the Sutra's height for the panel in grid rows, or null (as tall as its content)
     */
    public record PanelView(
            String id, String kind, String title, String code, String key, String area, boolean inferred,
            String explanation, PanelData data, String error, boolean empty, Integer span, Integer height, String denied, Wire wire, RefreshView refresh,
            Map<String, Object> form, PanelProvenance provenance, PanelCompute compute) {

        /** A panel whose source is not computed. */
        public PanelView(String id, String kind, String title, String code, String key, String area, boolean inferred,
                String explanation, PanelData data, String error, boolean empty, Integer span, Integer height, String denied, Wire wire, RefreshView refresh,
                Map<String, Object> form, PanelProvenance provenance) {
            this(id, kind, title, code, key, area, inferred, explanation, data, error, empty, span, height, denied, wire, refresh, form, provenance, null);
        }

        /** The same panel with the state of its computed source (DATA_ARCHITECTURE.md › Reactive connectors, section 12.4), or none (null). */
        public PanelView withCompute(PanelCompute next) {
            return new PanelView(id, kind, title, code, key, area, inferred, explanation, data, error, empty, span, height, denied, wire, refresh, form, provenance, next);
        }

        /** A panel without a provenance of its own (it reads the page's record). */
        public PanelView(String id, String kind, String title, String code, String key, String area, boolean inferred,
                String explanation, PanelData data, String error, boolean empty, Integer span, Integer height, String denied, Wire wire, RefreshView refresh,
                Map<String, Object> form) {
            this(id, kind, title, code, key, area, inferred, explanation, data, error, empty, span, height, denied, wire, refresh, form, null);
        }

        /** The same panel with the provenance of its own {@code source:} (RACHANA_REFERENCE.md 9), or none (null). */
        public PanelView withProvenance(PanelProvenance next) {
            return new PanelView(id, kind, title, code, key, area, inferred, explanation, data, error, empty, span, height, denied, wire, refresh, form, next, compute);
        }

        /** A panel without a form (RACHANA_REFERENCE.md 10). */
        public PanelView(String id, String kind, String title, String code, String key, String area, boolean inferred,
                String explanation, PanelData data, String error, boolean empty, Integer span, Integer height, String denied, Wire wire, RefreshView refresh) {
            this(id, kind, title, code, key, area, inferred, explanation, data, error, empty, span, height, denied, wire, refresh, null);
        }

        /**
         * The same panel with its form for the console ({@code form:}, RACHANA_REFERENCE.md 10): the fields as this viewer may edit them,
         * the rules, the hints, the lookups' choices and, for a table, each row's values; null for none.
         */
        public PanelView withForm(Map<String, Object> next) {
            return new PanelView(id, kind, title, code, key, area, inferred, explanation, data, error, empty, span, height, denied, wire, refresh, next, provenance, compute);
        }

        /** A panel that does not refresh on its own. */
        public PanelView(String id, String kind, String title, String code, String key, String area, boolean inferred,
                String explanation, PanelData data, String error, boolean empty, Integer span, Integer height, String denied, Wire wire) {
            this(id, kind, title, code, key, area, inferred, explanation, data, error, empty, span, height, denied, wire, null);
        }

        /** The same panel refreshing as the Sutra says ({@code refresh:}, RACHANA_REFERENCE.md 9), or not at all (null). */
        public PanelView withRefresh(RefreshView next) {
            return new PanelView(id, kind, title, code, key, area, inferred, explanation, data, error, empty, span, height, denied, wire, next, form, provenance, compute);
        }

        /** The same panel showing {@code next} (a mask, a filter), everything else kept. */
        public PanelView withData(PanelData next) {
            return new PanelView(id, kind, title, code, key, area, inferred, explanation, next, error, empty, span, height, denied, wire, refresh, form, provenance, compute);
        }

        /** A panel without Sutra Next wiring. */
        public PanelView(String id, String kind, String title, String code, String key, String area, boolean inferred,
                String explanation, PanelData data, String error, boolean empty, Integer span, Integer height, String denied) {
            this(id, kind, title, code, key, area, inferred, explanation, data, error, empty, span, height, denied, null);
        }

        /** The same panel with its wiring for the console. */
        public PanelView withWire(Wire next) {
            return new PanelView(id, kind, title, code, key, area, inferred, explanation, data, error, empty, span, height, denied, next, refresh, form, provenance, compute);
        }

        /** {@code denied} is set when the panel's {@code source} is an entity the caller may not open (no data, no error). */
        public PanelView(String id, String kind, String title, String code, String key, String area, boolean inferred,
                String explanation, PanelData data, String error, boolean empty, Integer span, Integer height) {
            this(id, kind, title, code, key, area, inferred, explanation, data, error, empty, span, height, null);
        }

        public PanelView(String id, String kind, String title, String code, String key, String area, boolean inferred,
                String explanation, PanelData data, String error, boolean empty) {
            this(id, kind, title, code, key, area, inferred, explanation, data, error, empty, null, null);
        }
    }

    /**
     * Rows by name (contract 1.1): what panels of the kinds {@code grid}, {@code tree} and {@code chart} read, so that two panels can read
     * one dataset and a {@code rows} patch reaches both. Unlike the rest of the document its values are raw (numbers, text, booleans,
     * null; a value the reader may not see is {@code {"$masked": true}}, as in the Data Protocol): the painter formats them with each
     * field's {@code fmt}. Exactly one source: {@code values} (rows in the order of {@code fields}) or {@code rows} (objects) written in
     * the document, {@code name} (rows the page hands the painter), {@code arrow}, {@code pages} or {@code server} (read from an address
     * the host allows).
     *
     * @param key the fields that identify a row (needed by {@code rows} patches, selection and links), or none
     * @param fields the fields in order: name, type, label and format
     * @param values the rows as lists in the order of {@code fields}
     * @param rows the rows as objects
     * @param more how many rows the producer left out (the dataset's limit), or 0
     * @param name a dataset the page hands the painter by this name
     * @param arrow an Arrow IPC stream or file
     * @param pages REST pages read as the reader scrolls
     * @param server a server-side row model (sort, filter, group and pivot answered by the server)
     * @param live a live table of the Data Protocol read a window at a time and followed by key (contract 1.2, panel kind {@code live})
     */
    @Include(Include.Rule.NON_DEFAULT)
    public record Dataset(List<String> key, List<DataField> fields, List<List<Object>> values, List<Map<String, Object>> rows, int more,
            String name, ArrowRef arrow, PagesRef pages, ServerRef server, LiveRef live) {

        /** A dataset of contract 1.1 (no live table). */
        public Dataset(List<String> key, List<DataField> fields, List<List<Object>> values, List<Map<String, Object>> rows, int more,
                String name, ArrowRef arrow, PagesRef pages, ServerRef server) {
            this(key, fields, values, rows, more, name, arrow, pages, server, null);
        }

        /** Inline rows in the order of their fields. */
        public static Dataset of(List<String> key, List<DataField> fields, List<List<Object>> values, int more) {
            return new Dataset(key, fields, values, null, more, null, null, null, null);
        }
    }

    /**
     * One field of a dataset.
     *
     * @param name the field's name (a row's property; the column of {@code values})
     * @param type {@code string}, {@code number}, {@code boolean}, {@code date} or {@code datetime}
     * @param label the words a heading shows, or none (the name)
     * @param fmt the format its values print with (a format name: {@code amount0}, {@code pct2}, {@code date}), or none
     */
    @Include(Include.Rule.NON_DEFAULT)
    public record DataField(String name, String type, String label, String fmt) {}

    /**
     * An Arrow IPC stream or file: read from {@code url}, or carried in the document as {@code base64}.
     *
     * @param url its address
     * @param base64 its bytes
     */
    @Include(Include.Rule.NON_DEFAULT)
    public record ArrowRef(String url, String base64) {}

    /**
     * REST pages ({@code GET <url>?limit=&offset=}, or {@code &cursor=}), answered {@code {rows, total?, next?}}.
     *
     * @param url the address
     * @param size rows per page (0: the painter's)
     * @param mode {@code offset} or {@code cursor}
     * @param total the rows in all, when known
     * @param sort {@code server}: sorting travels to the server; else the pages read are sorted
     * @param filter {@code server}: filters travel to the server; else the pages read are filtered
     */
    @Include(Include.Rule.NON_DEFAULT)
    public record PagesRef(String url, int size, String mode, long total, String sort, String filter) {}

    /**
     * A server-side row model: the painter POSTs {@code {startRow, endRow, sortModel, filterModel, rowGroupCols, groupKeys, valueCols}}
     * and the server answers {@code {rows, rowCount}} (CONTRACTS of Drishti Elements).
     *
     * @param url the address
     */
    @Include(Include.Rule.NON_DEFAULT)
    public record ServerRef(String url) {}

    /**
     * A live table of the Data Protocol (contract 1.2, PROTOCOL 16): the painter reads the first {@code window} rows of the query (the
     * panel's order, this filter) through the data client the page hands it, subscribes with those keys as its window ({@code top}: new rows
     * of a newest-first order inserted on top) and applies the patches; a gap reads the page again.
     *
     * @param table the host's table
     * @param window the rows read and followed at a time
     * @param top whether new rows are inserted on top
     * @param filter the rows' condition as a filter tree (PROTOCOL 7), or none
     */
    @Include(Include.Rule.NON_DEFAULT)
    public record LiveRef(String table, int window, boolean top, Map<String, Object> filter) {}
}
