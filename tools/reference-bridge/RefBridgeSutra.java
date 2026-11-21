// Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
// Drishti Rachana. Proprietary: see LICENSE.
//
// The reference program of the conformance suite's `sutra` cases (split step A14): the reference's view pipeline steps over the
// host's engine (binder, controls, wiring, ViewModel records), written by a plain ObjectMapper. Separate from RefBridge.java because it
// needs the engine's class path, whose function providers change other kinds' answers (tools/reference-bridge/README.md).
//
// Run:  python3 tools/conformance/rachana_conformance.py --kinds sutra \
//         --cmd "java -cp <engine class path> tools/reference-bridge/RefBridgeSutra.java spec/1/formats.yaml <drishti formats.yaml>"
import com.ash.drishti.api.DataNode;
import com.ash.drishti.rachana.el.*;
import com.ash.drishti.rachana.format.Formats;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

public class RefBridgeSutra {
    static final ObjectMapper M = new ObjectMapper();
    static Formats formats;
    static final ElCompiler EL = new ElCompiler();

    public static void main(String[] a) throws Exception {
        formats = Formats.load(null, List.of(a));
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), false, StandardCharsets.UTF_8);
        in.readLine();
        out.println("{\"runtime\":\"jvm\",\"runtimeVersion\":\"bootstrap-2.9.11.i7a-engine\",\"language\":\"1.0\",\"profiles\":[\"core\",\"drishti\"],\"kinds\":[\"sutra\"]}");
        out.flush();
        String line;
        while ((line = in.readLine()) != null) {
            if (line.isBlank()) { continue; }
            JsonNode c = M.readTree(line);
            StringBuilder sb = new StringBuilder("{\"case\":").append(str(c.get("case").asText()));
            try {
                sb.append(",\"actual\":");
                sutra(c, sb);
            } catch (Throwable t) {
                sb.setLength(sb.indexOf(",\"actual\":") < 0 ? sb.length() : sb.indexOf(",\"actual\":"));
                sb.append(",\"error\":").append(str(t.getClass().getSimpleName() + ": " + t.getMessage()));
            }
            out.println(sb.append('}'));
            out.flush();
        }
    }

    static DataNode node(JsonNode n) { return DataNode.of(plain(n)); }

    static Object plain(JsonNode n) {
        if (n.isNull()) { return null; }
        if (n.isBoolean()) { return n.booleanValue(); }
        if (n.isIntegralNumber()) { return n.longValue(); }
        if (n.isNumber()) { return n.doubleValue(); }
        if (n.isTextual()) { return n.textValue(); }
        if (n.isArray()) { List<Object> l = new ArrayList<>(); n.forEach(x -> l.add(plain(x))); return l; }
        if (n.size() == 1 && n.has("$masked")) { return DataNode.masked(); }
        if (n.size() == 1 && n.has("$dec")) { return new BigDecimal(n.get("$dec").asText()); }
        if (n.size() == 1 && n.has("$num")) { return switch (n.get("$num").asText()) { case "NaN" -> Double.NaN; case "Infinity" -> Double.POSITIVE_INFINITY; default -> Double.NEGATIVE_INFINITY; }; }
        Map<String, Object> m = new LinkedHashMap<>();
        n.fields().forEachRemaining(f -> m.put(f.getKey(), plain(f.getValue())));
        return m;
    }

    static String str(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\b' -> b.append("\\b");
                case '\t' -> b.append("\\t");
                case '\n' -> b.append("\\n");
                case '\f' -> b.append("\\f");
                case '\r' -> b.append("\\r");
                default -> {
                    boolean lone = Character.isSurrogate(ch) && !(Character.isHighSurrogate(ch) && i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1)))
                            && !(Character.isLowSurrogate(ch) && i > 0 && Character.isHighSurrogate(s.charAt(i - 1)));
                    if (ch < 0x20 || ch == 0x7f || lone) { b.append(String.format("\\u%04x", (int) ch)); } else { b.append(ch); }
                }
            }
        }
        return b.append('"').toString();
    }

    // ---- sutra (split step A14) -------------------------------------------------------------------------------------
    // The reference's view pipeline steps (ViewPipeline.assemble) over the reference's binder, controls, wiring and ViewModel records,
    // with no sources, links or masks, written by a plain ObjectMapper: what Rachana's ViewBuilder and ViewJson must answer byte for byte.
    // Needs the engine's jars on the class path (drishti-engine and its dependencies).

    static void sutra(JsonNode c, StringBuilder sb) throws Exception {
        JsonNode g = c.path("given");
        String file = c.path("file").asText("case.sutra.yaml");
        var s = new com.ash.drishti.rachana.parse.SutraParser().parse(c.get("sutra").asText(), file, "conformance");
        LocalDate today = g.has("today") ? LocalDate.parse(g.get("today").asText()) : LocalDate.of(2026, 1, 15);
        Map<String, String> ctl = new LinkedHashMap<>();
        g.path("ctl").fields().forEachRemaining(e -> {
            List<String> parts = new ArrayList<>();
            if (e.getValue().isArray()) { e.getValue().forEach(x -> parts.add(x.asText())); }
            ctl.put(e.getKey(), e.getValue().isArray() ? String.join(",", parts) : e.getValue().asText());
        });
        var values = new com.ash.drishti.engine.control.ControlValues(ctl, Set.of());
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
        DataNode data = node(g.has("record") ? g.get("record") : M.createObjectNode());
        var ref = com.ash.drishti.api.EntityRef.of("record", "case");
        var doc = new com.ash.drishti.api.EntityDocument(ref, data, new com.ash.drishti.api.Provenance("conformance", 0, java.time.Instant.EPOCH, false));
        var asOf = com.ash.drishti.api.AsOf.of(today);
        var resolved = controls.resolve(s, EvalContext.of(data, formats), values, asOf, k -> true);
        EvalContext eval = resolved.apply(EvalContext.of(data, formats)).withToday(today);
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
        sb.append("{\"viewModel\":").append(M.writeValueAsString(view)).append('}');
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
        String id = soft(() -> Values.text(EL.compile(t.id()).eval(eval)));
        com.ash.drishti.engine.view.ViewModel.Cell with = null;
        if (t.with() != null) {
            Object w = soft(() -> Values.simplify(EL.compile(t.with()).eval(eval)));
            with = w == null ? null : binder.cell(null, w, null, null, false, null);
        }
        String pill = t.pill() == null ? null : soft(() -> EL.template(t.pill()).render(eval));
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
