// Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
// Drishti Rachana. Proprietary: see LICENSE.
//
// Compatibility regime, CG3 (SUTRA_EXPANSION 6.6, gate finding F13): the bind half of the customer-corpus export. Written against
// Drishti's reference jars at the corpus's release (the engine class path; read only: nothing of Drishti is changed). Reads the
// scratch tree corpus_binds.py made (tree/, kit/fragments/, groups.json, records.json: per Sutra file the synthetic record it is bound
// over) and, per Sutra, binds it as tools/reference-bridge/RefBridgeSutra.java binds a `sutra` case (the reference's view pipeline steps
// over its binder, controls, wiring and ViewModel records; no sources, links or masks; business date 2026-01-15; no control values) and
// writes <out>/<file>.view.json, the ViewModel exactly as the reference's plain ObjectMapper wrote it, or <out>/<file>.error.txt when
// the reference could not bind it (the exception's class and message).
//
// Run:  java -cp <engine class path> tools/regime/CorpusBind.java <scratch dir> <out dir> <spec formats.yaml> <drishti formats.yaml>
import com.ash.drishti.api.DataNode;
import com.ash.drishti.rachana.el.*;
import com.ash.drishti.rachana.format.Formats;
import com.ash.drishti.rachana.parse.SutraBlocks;
import com.ash.drishti.rachana.parse.SutraParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

public class CorpusBind {
    static final ObjectMapper M = new ObjectMapper();
    static final ElCompiler EL = new ElCompiler();
    static final LocalDate TODAY = LocalDate.of(2026, 1, 15);
    static Formats formats;

    public static void main(String[] a) throws Exception {
        Path scratch = Path.of(a[0]), out = Path.of(a[1]);
        formats = Formats.load(null, List.of(a[2], a[3]));
        JsonNode groups = M.readTree(Files.readString(scratch.resolve("groups.json")));
        JsonNode records = M.readTree(Files.readString(scratch.resolve("records.json")));
        SutraParser parser = new SutraParser();
        int bound = 0, failed = 0;
        for (JsonNode g : groups.get("groups")) {
            SutraBlocks.kit(scratch.resolve("kit"));
            Path gd = scratch.resolve("tree").resolve(g.get("dir").asText());
            SutraBlocks.load(List.of(Files.isDirectory(gd.resolve("sutras")) ? gd.resolve("sutras") : gd));
            for (JsonNode f : g.get("sutras")) {
                String rel = f.asText();
                Path p = scratch.resolve("tree").resolve(rel);
                Path o = out.resolve(rel);
                Files.createDirectories(o.getParent());
                try {
                    var s = parser.parse(Files.readString(p, StandardCharsets.UTF_8), p.getFileName().toString(), g.get("pack").asText());
                    String vm = bind(s, records.path(rel));
                    Files.writeString(o.resolveSibling(o.getFileName() + ".view.json"), vm + "\n", StandardCharsets.UTF_8);
                    bound++;
                } catch (Throwable t) {
                    Files.writeString(o.resolveSibling(o.getFileName() + ".error.txt"), t.getClass().getSimpleName() + ": " + t.getMessage() + "\n",
                            StandardCharsets.UTF_8);
                    failed++;
                }
            }
            SutraBlocks.kit(null);
            SutraBlocks.load(List.of());
        }
        System.out.println("bound " + bound + " Sutras, " + failed + " the reference could not bind");
    }

    /** The reference's view of {@code s} over {@code record}: RefBridgeSutra.sutra's pipeline, no control values. */
    static String bind(com.ash.drishti.rachana.model.Sutra s, JsonNode record) throws Exception {
        var values = new com.ash.drishti.engine.control.ControlValues(Map.of(), Set.of());
        var gp = new com.ash.drishti.graph.GraphProperties(null, null, null, null, null);
        var catalog = new com.ash.drishti.graph.ReferenceCatalog(gp);
        var binder = new com.ash.drishti.engine.bind.Binder(EL, formats, catalog, new com.ash.drishti.graph.BadgeRenderer(gp, EL, formats),
                new com.ash.drishti.engine.command.Mnemonics(new com.ash.drishti.engine.command.CommandsProperties(null, null, null, null)));
        var dates = new com.ash.drishti.engine.time.BusinessDates(new com.ash.drishti.engine.time.BusinessDateProperties("WEEKENDS", "UTC", null, null),
                java.time.Clock.systemUTC());
        var budget = java.time.Duration.ofSeconds(2);
        var controls = new com.ash.drishti.engine.control.ControlResolver(EL, dates, null, budget);
        var maps = new com.ash.drishti.engine.bind.MapSupport(null, EL, formats, binder.limits());
        var wire = new com.ash.drishti.engine.wire.ScreenWire(null, binder, EL, budget);
        DataNode data = DataNode.of(plain(record.isMissingNode() ? M.createObjectNode() : record));
        var ref = com.ash.drishti.api.EntityRef.of("record", "case");
        var doc = new com.ash.drishti.api.EntityDocument(ref, data, new com.ash.drishti.api.Provenance("conformance", 0, java.time.Instant.EPOCH, false));
        var asOf = com.ash.drishti.api.AsOf.of(TODAY);
        var resolved = controls.resolve(s, EvalContext.of(data, formats), values, asOf, k -> true);
        EvalContext eval = resolved.apply(EvalContext.of(data, formats)).withToday(TODAY);
        Set<com.ash.drishti.api.EntityRef> wanted = new LinkedHashSet<>();
        Set<com.ash.drishti.api.EntityRef> denied = new HashSet<>();
        for (var p : s.panels()) {
            if (p.kind().readsData() && values.builds(p.id())) { binder.chartSource(p, eval).ifPresent(wanted::add); }
        }
        denied.removeAll(wanted);
        var lookups = maps.resolve(s.panels(), values::builds, asOf, java.util.function.UnaryOperator.identity(), k -> true);
        var layout = new com.ash.drishti.inference.EffectiveLayout(s, s.name(), false, Map.of());
        var ctx = new com.ash.drishti.engine.bind.BindContext(doc, layout, new com.ash.drishti.common.Fingerprint(0), eval, List.of(), Map.of(), Set.of(), denied, lookups);
        var screen = new com.ash.drishti.engine.wire.ScreenWire.Screen(s, com.ash.drishti.rachana.SutraWiring.nodes(s), ctx, eval.withRoot(data), asOf,
                com.ash.drishti.engine.ViewerContext.SYSTEM, Map.of(), null);
        List<com.ash.drishti.engine.view.ViewModel.PanelView> panels = new ArrayList<>();
        for (var p : s.panels()) {
            boolean wired = com.ash.drishti.engine.wire.ScreenWire.wired(p);
            if (values.builds(p.id()) && (wired ? wire.shown(screen, p) : binder.shown(p, eval))) {
                panels.add(wired ? wire.bind(screen, p) : wire.formed(screen, p, com.ash.drishti.engine.wire.ScreenWire.refreshed(p, binder.bind(p, ctx))));
            }
        }
        List<com.ash.drishti.engine.view.ViewModel.Cell> strip = new ArrayList<>();
        for (var i : s.strip()) {
            Object v;
            try { v = EL.compile(i.bind()).eval(eval); } catch (RuntimeException | StackOverflowError e) { v = null; }
            strip.add(binder.cell(i.label(), v, i.fmt(), i.tone(), i.emphasis(), binder.pathOf(i.bind())));
        }
        var view = new com.ash.drishti.engine.view.ViewModel(null, null, title(s, eval, ref, binder), strip, panels, keys(s, panels, eval, binder), null, null)
                .withControls(resolved.views(), selects(s))
                .withLayout(com.ash.drishti.engine.view.ScreenGrid.of(s))
                .withPhone(com.ash.drishti.engine.view.PhoneView.of(s, s.phone() == null || s.phone().summary() == null ? null
                        : soft(() -> EL.template(s.phone().summary()).render(eval))));
        return M.writeValueAsString(view);
    }

    static Object plain(JsonNode n) {
        if (n.isNull()) { return null; }
        if (n.isBoolean()) { return n.booleanValue(); }
        if (n.isIntegralNumber()) { return n.longValue(); }
        if (n.isNumber()) { return n.doubleValue(); }
        if (n.isTextual()) { return n.textValue(); }
        if (n.isArray()) { List<Object> l = new ArrayList<>(); n.forEach(x -> l.add(plain(x))); return l; }
        Map<String, Object> m = new LinkedHashMap<>();
        n.fields().forEachRemaining(f -> m.put(f.getKey(), plain(f.getValue())));
        return m;
    }

    static List<com.ash.drishti.engine.view.ViewModel.SelectView> selects(com.ash.drishti.rachana.model.Sutra s) {
        List<com.ash.drishti.engine.view.ViewModel.SelectView> out = new ArrayList<>();
        for (var p : s.panels()) {
            if (p.options().get(com.ash.drishti.rachana.model.Panel.ON_SELECT) instanceof Map<?, ?> m && m.get("by") instanceof String by) {
                List<String> filter = m.get("filter") instanceof List<?> l ? l.stream().map(String::valueOf).toList() : List.of();
                out.add(new com.ash.drishti.engine.view.ViewModel.SelectView(p.id(), by, filter));
            }
        }
        return out;
    }

    static com.ash.drishti.engine.view.ViewModel.TitleView title(com.ash.drishti.rachana.model.Sutra s, EvalContext eval,
            com.ash.drishti.api.EntityRef ref, com.ash.drishti.engine.bind.Binder binder) {
        var t = s.title();
        String id = t == null || t.id() == null ? null : soft(() -> Values.text(EL.compile(t.id()).eval(eval)));
        com.ash.drishti.engine.view.ViewModel.Cell with = null;
        if (t != null && t.with() != null) {
            Object w = soft(() -> Values.simplify(EL.compile(t.with()).eval(eval)));
            with = w == null ? null : binder.cell(null, w, null, null, false, null);
        }
        String pill = t == null || t.pill() == null ? null : soft(() -> EL.template(t.pill()).render(eval));
        return new com.ash.drishti.engine.view.ViewModel.TitleView(pill, id == null || id.isBlank() ? ref.id() : id, with);
    }

    static List<com.ash.drishti.engine.view.ViewModel.KeyView> keys(com.ash.drishti.rachana.model.Sutra s,
            List<com.ash.drishti.engine.view.ViewModel.PanelView> panels, EvalContext eval, com.ash.drishti.engine.bind.Binder binder) {
        List<com.ash.drishti.engine.view.ViewModel.KeyView> keys = new ArrayList<>();
        for (var p : panels) {
            if (p.key() != null) { keys.add(new com.ash.drishti.engine.view.ViewModel.KeyView(p.key(), shortTitle(p.title()), "panel", p.id(), null)); }
        }
        s.keys().forEach((key, action) -> {
            switch (action) {
                case "raw" -> keys.add(new com.ash.drishti.engine.view.ViewModel.KeyView(key, "Raw JSON", "raw", null, null));
                case "impact" -> keys.add(new com.ash.drishti.engine.view.ViewModel.KeyView(key, "Impact", "impact", null, null));
                default -> {
                    if (action.startsWith("link(")) {
                        Object v = Values.simplify(EL.compile(action).eval(eval));
                        if (v instanceof Link l) {
                            var lv = binder.linkView(l);
                            if (lv != null) {
                                String k = lv.kind().replace('-', ' ');
                                keys.add(new com.ash.drishti.engine.view.ViewModel.KeyView(key, Character.toUpperCase(k.charAt(0)) + k.substring(1), "link", null, lv));
                            }
                        }
                    } else {
                        keys.add(new com.ash.drishti.engine.view.ViewModel.KeyView(key, action, "panel", action, null));
                    }
                }
            }
        });
        keys.sort(Comparator.comparingInt(k -> Integer.parseInt(k.key().substring(1))));
        return keys;
    }

    static String shortTitle(String t) {
        if (t == null) { return ""; }
        String s = t;
        for (String cut : new String[] {" · ", " ("}) { int i = s.indexOf(cut); if (i > 0) { s = s.substring(0, i); } }
        String[] words = s.split(" ");
        return words.length > 2 ? words[0] + " " + words[1] : s;
    }

    static <T> T soft(java.util.function.Supplier<T> f) {
        try { return f.get(); } catch (RuntimeException | StackOverflowError e) { return null; }
    }
}
