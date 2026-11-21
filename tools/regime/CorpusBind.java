// Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
// Drishti Rachana. Proprietary: see LICENSE.
//
// Compatibility regime, CG3 (SUTRA_EXPANSION 6.6, gate findings F2, F13): the bind half of the customer-corpus export. Written against
// Drishti's reference jars at the corpus's release (the engine class path; read only: nothing of Drishti is changed). Two passes:
//
//   expand: per Sutra of the scratch tree corpus_binds.py made (tree/, kit/fragments/, groups.json), the Sutra as the reference parsed
//           it (fragments and blocks expanded, as JSON) into <out>/<file>.sutra.json, so the recorder makes its synthetic data from the
//           panels the reference binds, not from the Sutra's text alone; a Sutra that does not parse gets none.
//   bind:   per Sutra, its synthetic data (records.json: the record, the entities of each kind its panels read, the record's fields that
//           name entities) bound as the reference's view pipeline binds a view (ViewPipeline.assemble: controls, linked entities, map
//           lookups, wiring; business date 2026-01-15; no control values; no masks), over a RECORDING source router: one source
//           plugin serving every kind from the synthetic entities, each fetch and search it answers written down as a host call. Writes
//           <out>/<file>.view.json (the ViewModel exactly as the reference's plain ObjectMapper wrote it) or <out>/<file>.error.txt (the
//           exception's class and message), and <out>/<file>.calls.json (the host calls, sorted, for the recording).
//
// Run:  java -cp <engine class path> tools/regime/CorpusBind.java expand|bind <scratch dir> <out dir> <spec formats.yaml> <drishti formats.yaml>
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
        boolean expand = a[0].equals("expand");
        Path scratch = Path.of(a[1]), out = Path.of(a[2]);
        formats = Formats.load(null, List.of(a[3], a[4]));
        JsonNode groups = M.readTree(Files.readString(scratch.resolve("groups.json")));
        JsonNode records = expand ? M.createObjectNode() : M.readTree(Files.readString(scratch.resolve("records.json")));
        ROUTER = expand ? null : router(scratch);
        ObjectMapper dump = new ObjectMapper().disable(com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS).findAndRegisterModules();
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
                if (expand) {
                    try {
                        var s = parser.parse(Files.readString(p, StandardCharsets.UTF_8), p.getFileName().toString(), g.get("pack").asText());
                        Files.writeString(o.resolveSibling(o.getFileName() + ".sutra.json"), dump.writeValueAsString(s), StandardCharsets.UTF_8);
                        bound++;
                    } catch (Throwable t) {
                        failed++;
                    }
                    continue;
                }
                RECORDER.begin(records.path(rel).path("entities"));
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
                Files.writeString(o.resolveSibling(o.getFileName() + ".calls.json"), RECORDER.end(), StandardCharsets.UTF_8);
            }
            SutraBlocks.kit(null);
            SutraBlocks.load(List.of());
        }
        System.out.println((expand ? "expanded " : "bound ") + bound + " Sutras, " + failed + " the reference could not " + (expand ? "parse" : "bind"));
        System.exit(0);                                                  // the router's executor threads
    }

    /**
     * The recording source: one plugin serving every kind (its manifest names none) from the synthetic entities of the Sutra being
     * bound ({@code entities}: kind to [{id, data}]). A fetch of an id the kind holds answers that entity; of any other id of a kind it
     * holds, the kind's first entity under the id asked (a link the record names); of a kind it does not hold, nothing. A search
     * answers every entity of the kind, the text not applied (a synthetic source). Every answer is written down as a host call:
     * {@code source.fetch [kind, id]} (the document's data, source and read time, or null) and {@code source.search [kind, text]} (the hits).
     */
    static final class Recorder implements com.ash.drishti.api.SourcePlugin {
        private volatile JsonNode entities = M.createObjectNode();
        private final Map<String, String> calls = new java.util.concurrent.ConcurrentHashMap<>();

        void begin(JsonNode next) {
            entities = next == null || next.isMissingNode() ? M.createObjectNode() : next;
            calls.clear();
        }

        /** The calls answered since {@link #begin}, as a JSON list sorted by hook and arguments. */
        String end() {
            List<String> keys = new ArrayList<>(calls.keySet());
            Collections.sort(keys);
            StringBuilder b = new StringBuilder("[");
            for (String k : keys) { b.append(b.length() > 1 ? ",\n" : "\n").append(calls.get(k)); }
            return b.append(keys.isEmpty() ? "]\n" : "\n]\n").toString();
        }

        void note(String hook, List<?> args, Object answer) {
            try {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("hook", hook);
                c.put("args", args);
                c.put("answer", answer);
                calls.put(hook + M.writeValueAsString(args), M.writeValueAsString(c));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        /** The synthetic document of {@code ref}, null when its kind holds none. */
        JsonNode data(com.ash.drishti.api.EntityRef ref) {
            JsonNode list = entities.path(ref.kind());
            if (!list.isArray() || list.isEmpty()) { return null; }
            for (JsonNode e : list) { if (ref.id().equals(e.path("id").asText())) { return e.path("data"); } }
            return list.get(0).path("data");
        }

        @Override public com.ash.drishti.api.PluginManifest manifest() {
            return new com.ash.drishti.api.PluginManifest("recorded", "1", Set.of(), new com.ash.drishti.api.SourceCapabilities(false, false, true, false));
        }

        @Override public void start(com.ash.drishti.api.SourceContext context) {}

        @Override public Optional<com.ash.drishti.api.EntityDocument> fetch(com.ash.drishti.api.EntityRef ref) {
            JsonNode d = data(ref);
            Map<String, Object> answer = null;
            if (d != null) {
                answer = new LinkedHashMap<>();
                answer.put("data", plain(d));
                answer.put("source", "recorded");                         // the document's provenance (document(...))
                answer.put("fetchedAt", java.time.Instant.EPOCH.toString());
            }
            note("source.fetch", List.of(ref.kind(), ref.id()), answer);
            return Optional.ofNullable(d == null ? null : document(ref, d));
        }

        @Override public List<com.ash.drishti.api.EntityHit> search(String kind, String text, int limit) {
            List<com.ash.drishti.api.EntityHit> hits = new ArrayList<>();
            List<Object> answer = new ArrayList<>();
            for (JsonNode e : entities.path(kind)) {
                String id = e.path("id").asText(), title = e.path("title").asText(id);
                hits.add(new com.ash.drishti.api.EntityHit(com.ash.drishti.api.EntityRef.of(kind, id), title, "recorded"));
                Map<String, Object> h = new LinkedHashMap<>();
                h.put("id", id);
                h.put("title", title);
                h.put("subtitle", "recorded");
                answer.add(h);
            }
            note("source.search", List.of(kind, text == null ? "" : text), answer);
            return hits;
        }
    }

    static com.ash.drishti.api.EntityDocument document(com.ash.drishti.api.EntityRef ref, JsonNode d) {
        return new com.ash.drishti.api.EntityDocument(ref, DataNode.of(plain(d)), new com.ash.drishti.api.Provenance("recorded", 1, java.time.Instant.EPOCH, false));
    }

    static final Recorder RECORDER = new Recorder();
    static com.ash.drishti.engine.source.SourceRouter ROUTER;

    /** The router over the recording plugin alone (no connector files: their folder is an empty one in the scratch folder). */
    static com.ash.drishti.engine.source.SourceRouter router(Path scratch) throws java.io.IOException {
        String none = Files.createDirectories(scratch.resolve("connectors")).toString();
        var props = new com.ash.drishti.engine.source.SourcesProperties(Map.of(), "recorded", Map.of(), java.time.Duration.ofSeconds(5), null, Map.of(),
                none, "off", null, null);
        return new com.ash.drishti.engine.source.SourceRouter(new com.ash.drishti.engine.source.SourceRegistry(List.of(RECORDER), props,
                new com.ash.drishti.common.JsonCodec()), props, java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
    }

    /** The reference's view of {@code s} over {@code record} ({record, entities, links}): ViewPipeline.assemble's steps, no control values. */
    static String bind(com.ash.drishti.rachana.model.Sutra s, JsonNode record) throws Exception {
        var values = new com.ash.drishti.engine.control.ControlValues(Map.of(), Set.of());
        var gp = new com.ash.drishti.graph.GraphProperties(null, null, null, null, null);
        var catalog = new com.ash.drishti.graph.ReferenceCatalog(gp);
        var binder = new com.ash.drishti.engine.bind.Binder(EL, formats, catalog, new com.ash.drishti.graph.BadgeRenderer(gp, EL, formats),
                new com.ash.drishti.engine.command.Mnemonics(new com.ash.drishti.engine.command.CommandsProperties(null, null, null, null)));
        var dates = new com.ash.drishti.engine.time.BusinessDates(new com.ash.drishti.engine.time.BusinessDateProperties("WEEKENDS", "UTC", null, null),
                java.time.Clock.systemUTC());
        var budget = java.time.Duration.ofSeconds(2);
        var controls = new com.ash.drishti.engine.control.ControlResolver(EL, dates, ROUTER, budget);
        var maps = new com.ash.drishti.engine.bind.MapSupport(ROUTER, EL, formats, binder.limits());
        var wire = new com.ash.drishti.engine.wire.ScreenWire(ROUTER, binder, EL, budget);
        JsonNode rec = record.path("record");
        DataNode data = DataNode.of(plain(rec.isMissingNode() ? M.createObjectNode() : rec));
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
        // the linked entities as ViewPipeline.assemble fetches them: the panels' sources through the router (recorded); the record's
        // references (the host's graph settings: the fields that name entities, from the Sutra's link(...)s) for the links panel,
        // fetched from the same synthetic source but not recorded: the links panel is the host's, its answer is recorded whole
        Map<String, com.ash.drishti.graph.GraphProperties.FieldRef> refs = new LinkedHashMap<>();
        record.path("links").properties().forEach(e -> refs.put(e.getKey(), new com.ash.drishti.graph.GraphProperties.FieldRef(e.getValue().asText(), null, null)));
        List<com.ash.drishti.graph.LinkRef> links = new com.ash.drishti.graph.ReferenceCatalog(new com.ash.drishti.graph.GraphProperties(null, refs, null, null, null))
                .discover(data, ref);
        Map<com.ash.drishti.api.EntityRef, com.ash.drishti.api.EntityDocument> linked = new LinkedHashMap<>(wanted.isEmpty() ? Map.of()
                : ROUTER.fetchAll(wanted, budget, asOf, new com.ash.drishti.engine.source.SourceFailures()));
        for (var l : links) {
            JsonNode d = RECORDER.data(l.target());
            if (d != null) { linked.putIfAbsent(l.target(), document(l.target(), d)); }
        }
        Set<com.ash.drishti.api.EntityRef> pending = Set.of();             // nothing is waiting: what the source does not hold is missing
        var lookups = maps.resolve(s.panels(), values::builds, asOf, java.util.function.UnaryOperator.identity(), k -> true);
        var layout = new com.ash.drishti.inference.EffectiveLayout(s, s.name(), false, Map.of());
        var ctx = new com.ash.drishti.engine.bind.BindContext(doc, layout, new com.ash.drishti.common.Fingerprint(0), eval, links, linked, pending, denied, lookups);
        var screen = new com.ash.drishti.engine.wire.ScreenWire.Screen(s, com.ash.drishti.rachana.SutraWiring.nodes(s), ctx, eval.withRoot(data), asOf,
                com.ash.drishti.engine.ViewerContext.SYSTEM, Map.of(), null);
        for (var p : s.panels()) {
            if (p.kind() == com.ash.drishti.rachana.model.PanelKind.PIVOT && values.builds(p.id())) {
                try { pivot(p, eval, binder.limits()); } catch (RuntimeException e) { /* the panel says why itself: nothing was asked */ }
            }
        }
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

    /**
     * The host's pivot engine, recorded (gate finding F2): the reference binder computes a pivot panel's cells with Drishti's pivot engine
     * (bicompute's PivotCore, ChartBinder.engineCells), which Core leaves to its host ({@code PivotEngine}). The engine is asked here as
     * the reference's ChartBinder.pivot asks it (the same view, fields and rows, built as it builds them: plain rows, no where, no
     * masks) and its answer is written down: {@code pivot.cells [view, fields, rows]} (the cells) and {@code pivot.key [coordinates]}
     * (each group's row key as the engine writes it). Core's binder, given the same rows, asks its PivotEngine exactly these.
     */
    @SuppressWarnings("unchecked")
    static void pivot(com.ash.drishti.rachana.model.Panel p, EvalContext eval, com.ash.drishti.engine.bind.PanelLimits limits) {
        Object rv = EL.compile(p.option("rows").orElseThrow()).eval(eval);
        DataNode rows = rv instanceof DataNode d ? d : DataNode.missing();
        Object byOpt = p.options().get("by");
        List<String> bys = byOpt instanceof List<?> l ? l.stream().map(String::valueOf).toList() : List.of(String.valueOf(byOpt));
        String across = p.option("across").orElseThrow();
        String value = p.option("value").orElse(null);
        Functions.AggregateCall call = value != null && value.indexOf('(') > 0 ? Functions.aggregateCall(EL.compile(value)).orElse(null) : null;
        String agg = call != null ? call.name() : value == null ? "count" : p.option("agg").orElse("sum");
        String function;
        if (call != null) {
            String args = call.options().isEmpty() ? "" : ", " + String.join(", ", call.options().stream().map(String::valueOf).toList());
            function = "count".equals(call.name()) ? "sum(v)" : ("variance_pop".equals(call.name()) ? "var_pop" : call.name()) + "(v" + args + ")";
        } else {
            function = switch (agg) { case "count" -> "count()"; case "avg", "min", "max" -> agg + "(v)"; default -> "sum(v)"; };
        }
        int k = bys.size();
        List<com.ash.drishti.kernel.api.pivot.PivotProvider.Field> fields = new ArrayList<>();
        List<Object> fieldsJson = new ArrayList<>();
        for (int j = 0; j <= k + 1; j++) {
            String name = j < k ? "b" + j : j == k ? "x" : "v";
            String type = j <= k ? "string" : "number";
            String label = j < k ? bys.get(j) : j == k ? across : value == null ? "rows" : value;
            fields.add(new com.ash.drishti.kernel.api.pivot.PivotProvider.Field(name, type, label, ""));
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("name", name);
            f.put("type", type);
            f.put("label", label);
            f.put("description", "");
            fieldsJson.add(f);
        }
        LinkedHashSet<String> columns = new LinkedHashSet<>();
        List<Object[]> data = new ArrayList<>();
        LinkedHashSet<List<String>> groups = new LinkedHashSet<>();
        int n = Math.min(rows.size(), limits.maxValues());
        for (int i = 0; i < n; i++) {
            DataNode row = rows.get(i);
            Object x;
            if (call != null) {
                Object in = call.input().eval(eval.withRow(row, i));
                x = "count".equals(call.name()) && !Values.masked(in) ? (Values.isNull(in) ? 0 : 1) : in;
            } else {
                x = value == null ? 1 : field(value, row, i, eval);
            }
            double v = Values.masked(x) ? 0 : Values.number(x);
            if (!Double.isFinite(v)) { continue; }
            String col = key(field(across, row, i, eval));
            boolean shown = columns.contains(col) || columns.size() < limits.pivotColumns();
            if (shown) { columns.add(col); }
            Object[] r = new Object[k + 2];
            List<String> path = new ArrayList<>();
            for (int j = 0; j < k; j++) {
                r[j] = key(field(bys.get(j), row, i, eval));
                path.add((String) r[j]);
                groups.add(List.copyOf(path));
            }
            r[k] = shown ? col : "\u0000more";
            r[k + 1] = v;
            data.add(r);
        }
        Map<String, Object> view = new LinkedHashMap<>();
        List<Object> rowDims = new ArrayList<>();
        for (int j = 0; j < k; j++) { rowDims.add("b" + j); }
        view.put("source", "panel");
        view.put("rows", rowDims);
        view.put("columns", List.of("x"));
        Map<String, Object> val = new LinkedHashMap<>();
        val.put("measure", function);
        val.put("as", "v");
        Map<String, Object> expand = new LinkedHashMap<>();
        expand.put("rows", Math.max(1, k));
        expand.put("columns", 1);
        view.put("values", List.of(val));
        view.put("expandTo", expand);
        var core = new com.ash.drishti.bicompute.core.PivotCore(List.of(new com.ash.drishti.bicompute.rows.RowsProvider("panel", "panel", fields, data)),
                com.ash.drishti.bicompute.cache.StateCache.none(),
                new com.ash.drishti.bicompute.core.PivotCore.Settings(5_000_000, 5_000_000, Integer.MAX_VALUE, 1, 10, 1, 60, Long.MAX_VALUE));
        Map<String, Object> answer = core.query(com.ash.drishti.kernel.api.pivot.Viewer.of(""), com.ash.drishti.kernel.api.pivot.PivotView.parse(view));
        RECORDER.note("pivot.cells", List.of(view, fieldsJson, data.stream().map(Arrays::asList).toList()), answer.get("cells"));
        for (List<String> g : groups) {
            Map<String, String> m = new TreeMap<>();
            for (int j = 0; j < g.size(); j++) { m.put("b" + j, g.get(j)); }
            RECORDER.note("pivot.key", List.of(m), com.ash.drishti.kernel.api.pivot.CellKey.of(m).canonical());
        }
    }

    /** A row's value as the reference's ChartBinder.field reads it: an expression over the row, a dotted path, or a field (or its UPPER_SNAKE). */
    static Object field(String spec, DataNode row, int index, EvalContext eval) {
        if (spec == null) { return null; }
        if (spec.startsWith("@") || spec.startsWith("$") || spec.indexOf('(') >= 0 || spec.indexOf(' ') >= 0) {
            return EL.compile(spec).eval(eval.withRow(row, index));
        }
        if (spec.indexOf('.') >= 0) { return row.at(spec); }
        DataNode v = row.get(spec);
        return v.isMissing() ? row.get(spec.toUpperCase(java.util.Locale.ROOT)) : v;
    }

    static String key(Object v) {
        String s = Values.text(Values.simplify(v));
        return s.isEmpty() ? "(none)" : s;
    }

    static Object plain(JsonNode n) {
        if (n.isNull()) { return null; }
        if (n.isBoolean()) { return n.booleanValue(); }
        if (n.isIntegralNumber()) { return n.longValue(); }
        if (n.isNumber()) { return n.doubleValue(); }
        if (n.isTextual()) { return n.textValue(); }
        if (n.isArray()) { List<Object> l = new ArrayList<>(); n.forEach(x -> l.add(plain(x))); return l; }
        Map<String, Object> m = new LinkedHashMap<>();
        n.properties().forEach(f -> m.put(f.getKey(), plain(f.getValue())));
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
