/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.bind;

import com.ash.rachana.SutraWiring;
import com.ash.rachana.api.Node;
import com.ash.rachana.control.BusinessDays;
import com.ash.rachana.control.ControlResolver;
import com.ash.rachana.control.ControlValues;
import com.ash.rachana.control.ScreenState;
import com.ash.rachana.control.StateValues;
import com.ash.rachana.el.ElCompiler;
import com.ash.rachana.el.EvalContext;
import com.ash.rachana.el.Link;
import com.ash.rachana.el.Values;
import com.ash.rachana.format.Formats;
import com.ash.rachana.internal.CoreText;
import com.ash.rachana.model.Panel;
import com.ash.rachana.model.StripItem;
import com.ash.rachana.model.Sutra;
import com.ash.rachana.view.MaskCount;
import com.ash.rachana.view.PhoneView;
import com.ash.rachana.view.ScreenGrid;
import com.ash.rachana.view.ViewModel;
import com.ash.rachana.view.ViewModel.Cell;
import com.ash.rachana.view.ViewModel.KeyView;
import com.ash.rachana.view.ViewModel.PanelView;
import com.ash.rachana.wire.ScreenWire;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Core's view pipeline (split step A14): one Sutra over one entity's data into a {@link ViewModel}, in the steps a host's pipeline
 * takes: the controls resolved, the evaluation context with the controls and the date, the entities the panels name with
 * {@code source} fetched through the {@link TableProvider} (a kind the viewer may not open never is), the map panels' lookups, each
 * panel shown by its {@code when} bound (wired panels through {@link ScreenWire}), the strip, the title, the keys, the controls and
 * selects, the screen grid and the phone. What only a host knows is left out: the reference, mnemonic, provenance and timings are
 * null (a host's pipeline sets them), linked entities other than panel sources are a host's (its profile's {@code links}). Stateless
 * and thread-safe; panels are bound in order on the calling thread.
 */
public final class ViewBuilder {

    /**
     * One view to build: the entity, its Sutra and layout, the point in time, the viewer, the control values, the host's object, and
     * the state values the host kept for this reader (level 1.2, SUTRA_EXPANSION S1; decision S1-OPEN a).
     */
    public record Request(Entity doc, Sutra sutra, Layout layout, AsOf asOf, Masker viewer, ControlValues values, Object host, StateValues state) {

        public Request {
            layout = layout == null ? Layout.written(sutra == null ? null : sutra.name()) : layout;
            asOf = asOf == null ? AsOf.LATEST : asOf;
            viewer = viewer == null ? Masker.NONE : viewer;
            values = values == null ? ControlValues.NONE : values;
            state = state == null ? StateValues.NONE : state;
        }

        /** A request with no state values (every state at its default). */
        public Request(Entity doc, Sutra sutra, Layout layout, AsOf asOf, Masker viewer, ControlValues values, Object host) {
            this(doc, sutra, layout, asOf, viewer, values, host, null);
        }

        public static Request of(Entity doc, Sutra sutra) {
            return new Request(doc, sutra, null, null, null, null, null);
        }

        public Request withValues(ControlValues next) {
            return new Request(doc, sutra, layout, asOf, viewer, next, host, state);
        }

        /** The same request with the state values the host kept for this reader. */
        public Request withState(StateValues next) {
            return new Request(doc, sutra, layout, asOf, viewer, values, host, next);
        }

        public Request withAsOf(AsOf next) {
            return new Request(doc, sutra, layout, next, viewer, values, host, state);
        }
    }

    private final ElCompiler el;
    private final Formats formats;
    private final Binder binder;
    private final TableProvider tables;
    private final BusinessDays dates;
    private final Duration budget;
    private final ControlResolver controls;
    private final MapSupport maps;
    private final ScreenWire wire;

    public ViewBuilder(ElCompiler el, Formats formats, Binder binder, TableProvider tables, BusinessDays dates, Duration budget) {
        this.el = el;
        this.formats = formats;
        this.binder = binder;
        this.tables = tables == null ? TableProvider.NONE : tables;
        this.dates = dates;
        this.budget = budget == null ? Duration.ofSeconds(2) : budget;
        this.controls = new ControlResolver(el, dates, this.tables, this.budget);
        this.maps = new MapSupport(this.tables, el, formats, binder.limits());
        this.wire = new ScreenWire(this.tables, binder, el, this.budget);
    }

    /** The binder this pipeline binds with. */
    public Binder binder() {
        return binder;
    }

    /** The event wiring this pipeline binds wired panels with (events and children of a built view go through it). */
    public ScreenWire wire() {
        return wire;
    }

    public ViewModel build(Request r) {
        return com.ash.rachana.el.Declarations.within(r.sutra().declarations(), () -> built(r));   // level 1.2: its let and functions
    }

    /**
     * The screen a request binds (its Sutra, documents raw and as the viewer sees them, contexts, business date and viewer; no event fired
     * yet), as {@link #build} binds it: what {@link ScreenWire}'s events, children, form sends and control changes take.
     */
    public ScreenWire.Screen screen(Request r) {
        return com.ash.rachana.el.Declarations.within(r.sutra().declarations(), () -> prepared(r).screen());
    }

    /** One request made ready to bind: the state, the controls, the evaluation context, the binding context and the screen. */
    private record Prepared(Map<String, Object> state, ControlResolver.Resolved resolved, EvalContext eval, BindContext ctx, ScreenWire.Screen screen) {}

    private Prepared prepared(Request r) {
        Sutra s = r.sutra();
        Masker viewer = r.viewer();
        Node raw = r.doc() == null ? Node.missing() : r.doc().data();
        Node seenData = viewer.mask(raw);
        Entity seen = r.doc() == null ? null : seenData == raw ? r.doc()
                : new Entity(r.doc().ref(), seenData, r.doc().deleted(), r.doc().origin(), r.doc().host());
        AsOf at = r.asOf().businessDate() == null ? AsOf.of(dates.today(r.asOf())) : r.asOf();
        Map<String, Object> state = ScreenState.resolve(s, r.state());   // level 1.2, S1: each state at the request's value or its default
        ControlResolver.Resolved resolved = controls.resolve(s, EvalContext.of(seenData, formats).withState(state), r.values(), r.asOf(), viewer::mayOpen);
        LocalDate today = r.asOf().businessDate() != null ? r.asOf().businessDate() : dates.today(r.asOf());
        EvalContext eval = resolved.apply(EvalContext.of(seenData, formats).withState(state)).withToday(today);
        Set<EntityRef> wanted = new LinkedHashSet<>();
        Set<EntityRef> denied = new HashSet<>();
        for (Panel p : s.panels()) {
            if (p.kind().readsData() && r.values().builds(p.id())) {
                binder.chartSource(p, eval).ifPresent(src -> {
                    if (viewer.mayOpen(src.kind())) {
                        wanted.add(src);
                    } else {
                        denied.add(src);
                    }
                });
            }
        }
        denied.removeAll(wanted);
        Map<EntityRef, Entity> linked = wanted.isEmpty() ? Map.of() : tables.fetchAll(wanted, budget, at, new SourceFailures());
        if (!linked.isEmpty()) {
            Map<EntityRef, Entity> masked = new LinkedHashMap<>();
            linked.forEach((ref, d) -> {
                Node m = viewer.mask(d.data());
                masked.put(ref, m == d.data() ? d : new Entity(d.ref(), m, d.deleted(), d.origin(), d.host()));
            });
            linked = masked;
        }
        Map<String, MapSupport.Lookup> lookups = maps.resolve(s.panels(), r.values()::builds, at, viewer);
        BindContext ctx = new BindContext(seen, r.layout(), eval, linked, Set.of(), denied, lookups, r.host());
        ScreenWire.Screen screen = new ScreenWire.Screen(s, SutraWiring.nodes(s), ctx, eval.withRoot(raw), at, viewer, Map.of(), null);
        return new Prepared(state, resolved, eval, ctx, screen);
    }

    private ViewModel built(Request r) {
        Sutra s = r.sutra();
        Prepared pr = prepared(r);
        Map<String, Object> state = pr.state();
        ControlResolver.Resolved resolved = pr.resolved();
        EvalContext eval = pr.eval();
        BindContext ctx = pr.ctx();
        ScreenWire.Screen screen = pr.screen();
        List<PanelView> panels = new ArrayList<>();
        for (Panel p : s.panels()) {
            boolean wired = ScreenWire.wired(p);
            if (r.values().builds(p.id()) && (wired ? wire.shown(screen, p) : binder.shown(p, eval))) {
                panels.add(wired ? wire.bind(screen, p) : wire.formed(screen, p, ScreenWire.refreshed(p, binder.bind(p, ctx))));
            }
        }
        List<Cell> strip = new ArrayList<>();
        for (StripItem i : s.strip()) {
            Object v;
            try {
                v = el.compile(i.bind()).eval(eval);
            } catch (RuntimeException | StackOverflowError e) {
                v = null;
            }
            strip.add(binder.cell(i.label(), v, i.fmt(), i.tone(), i.emphasis(), binder.pathOf(i.bind())));
        }
        ViewModel.TitleView title = title(s, eval, r.doc() == null ? null : r.doc().ref());
        ViewModel view = new ViewModel(null, null, title, strip, panels, keys(s, panels, eval), null, null)
                .withControls(controlViews(s, resolved.views(), eval), selects(s))
                .withLayout(ScreenGrid.of(s))
                .withPhone(PhoneView.of(s, s.phone() == null || s.phone().summary() == null ? null
                        : soft(() -> el.template(s.phone().summary()).render(eval))))
                .withDatasets(ctx.datasets())                  // contract 1.1: only when a dataset panel was bound (the stamp follows)
                .withState(ScreenState.views(s, state));       // contract 1.2: only when the Sutra holds state (the stamp follows)
        return view;
    }

    /** How many values the viewer's masks replaced in a built view, and in which panels (zero when nothing was masked). */
    public static MaskCount.Result masked(ViewModel v) {
        return MaskCount.of(v.title(), v.strip(), v.panels());
    }

    /** The controls' views, each with its effect lists (level 1.2, I1: {@code change}) evaluated over the screen. */
    private List<ViewModel.ControlView> controlViews(Sutra s, List<ViewModel.ControlView> views, EvalContext eval) {
        if (com.ash.rachana.model.Effects.controls(s).isEmpty()) {
            return views;
        }
        return views.stream().map(v -> v.withOn(binder.controlOn(s, v.id(), eval))).toList();
    }

    /** The panels of {@code s} whose rows or bars set a control when clicked. */
    static List<ViewModel.SelectView> selects(Sutra s) {
        List<ViewModel.SelectView> out = new ArrayList<>();
        for (Panel p : s.panels()) {
            if (p.options().get(Panel.ON_SELECT) instanceof Map<?, ?> m && m.get("by") instanceof String by) {
                List<String> filter = m.get("filter") instanceof List<?> l ? l.stream().map(String::valueOf).toList() : List.of();
                out.add(new ViewModel.SelectView(p.id(), by, filter));
            }
        }
        return out;
    }

    private ViewModel.TitleView title(Sutra s, EvalContext eval, EntityRef ref) {
        var t = s.title();
        String id = t == null || t.id() == null ? null : soft(() -> Values.text(el.compile(t.id()).eval(eval)));
        Cell with = null;
        if (t != null && t.with() != null) {
            Object w = soft(() -> Values.simplify(el.compile(t.with()).eval(eval)));
            with = w == null ? null : binder.cell(null, w, null, null, false, null);
        }
        String pill = t == null || t.pill() == null ? null : soft(() -> el.template(t.pill()).render(eval));
        return new ViewModel.TitleView(pill, id == null || id.isBlank() ? (ref == null ? null : ref.id()) : id, with);
    }

    private List<KeyView> keys(Sutra s, List<PanelView> panels, EvalContext eval) {
        List<KeyView> keys = new ArrayList<>();
        for (PanelView p : panels) {
            if (p.key() != null) {
                keys.add(new KeyView(p.key(), shortTitle(p.title()), "panel", p.id(), null));
            }
        }
        s.keys().forEach((key, action) -> {
            switch (action) {
                case "raw" -> keys.add(new KeyView(key, "Raw JSON", "raw", null, null));
                case "impact" -> keys.add(new KeyView(key, "Impact", "impact", null, null));
                default -> {
                    if (action.startsWith("link(")) {
                        Object v = Values.simplify(el.compile(action).eval(eval));
                        if (v instanceof Link l) {
                            var lv = binder.linkView(l);
                            if (lv != null) {
                                keys.add(new KeyView(key, kindWords(lv.kind()), "link", null, lv));
                            }
                        }
                    } else {
                        keys.add(new KeyView(key, action, "panel", action, null));
                    }
                }
            }
        });
        keys.sort(Comparator.comparingInt(k -> Integer.parseInt(k.key().substring(1))));
        return keys;
    }

    private static String shortTitle(String t) {
        if (t == null) {
            return "";
        }
        String s = t;
        for (String cut : new String[] {" · ", " ("}) {
            int i = s.indexOf(cut);
            if (i > 0) {
                s = s.substring(0, i);
            }
        }
        String[] words = s.split(" ");
        return words.length > 2 ? words[0] + " " + words[1] : s;
    }

    private static String kindWords(String kind) {
        String s = kind.replace('-', ' ');
        return (char) CoreText.upper(s.charAt(0)) + s.substring(1);
    }

    private static <T> T soft(Supplier<T> f) {
        try {
            return f.get();
        } catch (RuntimeException | StackOverflowError e) {
            return null;
        }
    }
}
