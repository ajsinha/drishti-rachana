/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.compat;

import com.ash.rachana.Rachana;
import com.ash.rachana.RachanaOptions;
import com.ash.rachana.api.Node;
import com.ash.rachana.bind.AsOf;
import com.ash.rachana.bind.BindHooks;
import com.ash.rachana.bind.Entity;
import com.ash.rachana.bind.EntityRef;
import com.ash.rachana.bind.PivotEngine;
import com.ash.rachana.bind.TableProvider;
import com.ash.rachana.bind.ViewBuilder;
import com.ash.rachana.conformance.Cases;
import com.ash.rachana.control.BusinessDays;
import com.ash.rachana.el.BusinessCalendar;
import com.ash.rachana.model.Sutra;
import com.ash.rachana.parse.BlockLibrary;
import com.ash.rachana.view.ViewJson;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The corpus's recorded binds replayed through Core's binder (CG3, gate finding F13): per Sutra of the corpus, the recording
 * ({@code binds/<file>.json}: the synthetic record and the business date the reference was asked for, {@link RecordedHost}) is answered
 * to Core's view pipeline over the same Sutra (parsed with the corpus's blocks and the conformance suite's Drishti profile, its formats
 * Core's table and the profile's names) and the ViewModel Core writes ({@link ViewJson}) is compared byte for byte with the one the
 * reference wrote ({@code binds/<file>.view.json}). A Sutra the reference could not bind ({@code .error.txt}) must not bind on Core
 * either. No source, link or mask is read: the reference recorded none.
 */
final class CoreBinds {

    private CoreBinds() {}

    /** One file's outcome: null when identical, else what differs (the first differing place, both texts cut around it). */
    record Outcome(String file, String difference) {}

    static List<Outcome> replay(Corpus corpus) throws Exception {
        Path root = Corpus.repoRoot();
        Path scratch = ReferenceBridgeRuntime.scratch(corpus);
        List<Outcome> out = new ArrayList<>();
        try {
            BlockLibrary blocks = new BlockLibrary();
            blocks.kit(scratch.resolve("kit"));
            Rachana rachana = Rachana.with(RachanaOptions.DEFAULTS.withFormats(null, List.of(root.resolve("spec/1/formats.yaml").toString())),
                    Cases.profile("drishti", blocks).formats(root.resolve("tools/conformance/profile/drishti-formats.yaml").toString()).build());
            Map<String, Object> groups = Json.object(Json.parse(Files.readString(scratch.resolve("groups.json"))));
            for (Object go : Json.array(groups.get("groups"))) {
                Map<String, Object> g = Json.object(go);
                Path gd = scratch.resolve("tree").resolve((String) g.get("dir"));
                blocks.load(List.of(Files.isDirectory(gd.resolve("sutras")) ? gd.resolve("sutras") : gd));
                for (Object fo : Json.array(g.get("sutras"))) {
                    String rel = (String) fo;
                    out.add(new Outcome(rel, one(rachana, corpus.dir().resolve("binds"), rel, scratch.resolve("tree").resolve(rel), (String) g.get("pack"))));
                }
            }
        } finally {
            ReferenceBridgeRuntime.deleteTree(scratch);
        }
        return out;
    }

    private static String one(Rachana rachana, Path binds, String rel, Path sutraFile, String pack) throws Exception {
        Path recording = binds.resolve(rel + ".json");
        if (!Files.isRegularFile(recording)) {
            return "no recording";
        }
        Map<String, Object> rec = Json.object(Json.parse(Files.readString(recording)));
        RecordedHost host = new RecordedHost(Json.array(rec.get("hostCalls")));
        Object record = host.answer("record");
        LocalDate today = LocalDate.parse((String) host.answer("calendar.today"));
        Path view = binds.resolve(rel + ".view.json");
        Path error = binds.resolve(rel + ".error.txt");
        String got;
        try {
            Sutra s = rachana.parser().parse(Files.readString(sutraFile, StandardCharsets.UTF_8), sutraFile.getFileName().toString(), pack);
            ViewBuilder views = rachana.views(hostPanels(host), PivotEngine.NONE, TableProvider.NONE,
                    BusinessDays.of(today, BusinessCalendar.of("WEEKENDS")), Duration.ofSeconds(2));
            got = ViewJson.write(views.build(new ViewBuilder.Request(new Entity(EntityRef.of("record", "case"), Node.of(plain(record))), s, null,
                    AsOf.of(today), null, null, null))) + "\n";
        } catch (RecordedHost.UnrecordedCall e) {                         // Core asked the host something the recording does not hold
            return Files.isRegularFile(error) ? "the reference could not bind it (" + Files.readString(error).strip() + "), Core did (" + e.getMessage() + ")"
                    : e.getMessage();
        } catch (RuntimeException | StackOverflowError e) {
            return Files.isRegularFile(error) ? null : "Core could not bind it, the reference did: " + e;
        }
        String dump = System.getProperty("rachana.compat.binds.out", "");
        if (!dump.isBlank()) {                                           // for a person looking at the differences: Core's views beside the recorded
            Path d = Path.of(dump).resolve(rel + ".core.json");
            Files.createDirectories(d.getParent());
            Files.writeString(d, got, StandardCharsets.UTF_8);
        }
        if (!host.unused().isEmpty()) {
            return "recorded calls Core never asked: " + host.unused();
        }
        if (!Files.isRegularFile(view)) {
            return "the reference could not bind it (" + (Files.isRegularFile(error) ? Files.readString(error).strip() : "no view") + "), Core did";
        }
        String want = Files.readString(view, StandardCharsets.UTF_8);
        if (want.equals(got)) {
            return null;
        }
        String w = Json.ordered(unscrambled(Json.parse(want))), c = Json.ordered(unscrambled(Json.parse(got)));
        if (w.equals(c)) {
            return null;                                                 // the same document but for the reference's scrambled objects
        }
        want = w;
        got = c;
        int i = 0;
        while (i < Math.min(want.length(), got.length()) && want.charAt(i) == got.charAt(i)) {
            i++;
        }
        int from = Math.max(0, i - 120);
        return "differs at " + i + "\n  reference: " + want.substring(from, Math.min(want.length(), i + 160)) + "\n  core:      "
                + got.substring(from, Math.min(got.length(), i + 160));
    }

    /**
     * The document with the objects the reference wrote from {@code Map.of} in one canonical key order, sorted by name, and every
     * other object as it is. The reference (the i7a binder) built these objects with {@code Map.of}, whose key order is chosen afresh
     * in every JVM run, so the order recorded in {@code binds/} is a draw and carries no meaning; Core writes them in declaration
     * order. Comparing them in a fixed order makes the outcome the same in every run: identical when the keys and values are, never
     * flipping between "differs" and "identical". The objects: a wire's {@code emits[]} and {@code children[]}, a form's
     * {@code rules[]} and {@code steps[]}, a form field's {@code options[]} (DRISHTI_MIGRATION_TODO 7.2). Everything else keeps its
     * order and is compared byte for byte.
     */
    static Object unscrambled(Object v) {
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                String k = (String) e.getKey();
                Object x = unscrambled(e.getValue());
                if (k.equals("wire") && x instanceof Map<?, ?> wire) {
                    x = sortedIn(wire, "emits", "children");
                } else if (k.equals("form") && x instanceof Map<?, ?> form) {
                    x = sortedIn(form, "rules", "steps");
                } else if (k.equals("fields") && x instanceof List<?> fields) {
                    x = fields.stream().map(f -> f instanceof Map<?, ?> fm ? sortedIn(fm, "options") : f).toList();
                }
                out.put(k, x);
            }
            return out;
        }
        if (v instanceof List<?> l) {
            return l.stream().map(CoreBinds::unscrambled).toList();
        }
        return v;
    }

    /** The object with each named list's objects in sorted key order. */
    private static Map<String, Object> sortedIn(Map<?, ?> m, String... lists) {
        Map<String, Object> out = new LinkedHashMap<>();
        m.forEach((k, x) -> out.put((String) k, List.of(lists).contains(k) && x instanceof List<?> l
                ? l.stream().map(o -> o instanceof Map<?, ?> om ? (Object) new java.util.TreeMap<>(om) : o).toList() : x));
        return out;
    }

    /**
     * Core's hooks with the host's own panel binders answering from the recording (the profile's kinds {@code provenance} and
     * {@code links}: what the reference's profile bound for each such panel, {@code panel [kind, id]}).
     */
    @SuppressWarnings("unchecked")
    static BindHooks hostPanels(RecordedHost host) {
        return BindHooks.core()
                .withPanel(com.ash.rachana.model.PanelKind.PROVENANCE, (p, c, b) -> {
                    Map<String, Object> data = Json.object(host.answer("panel", "provenance", p.id()));
                    List<com.ash.rachana.view.ViewModel.Cell> cells = new ArrayList<>();
                    for (Object o : Json.array(data.get("fields"))) {
                        Map<String, Object> f = Json.object(o);
                        cells.add(new com.ash.rachana.view.ViewModel.Cell((String) f.get("label"), (String) f.get("text"), (String) f.get("tone"), null,
                                Boolean.TRUE.equals(f.get("emphasis")), (String) f.get("path")));
                    }
                    return new com.ash.rachana.view.PanelData.Fields(cells);
                })
                .withPanel(com.ash.rachana.model.PanelKind.LINKS, (p, c, b) -> {
                    Map<String, Object> data = Json.object(host.answer("panel", "links", p.id()));
                    if (!Json.array(data.get("links")).isEmpty()) {
                        throw new AssertionError("a recorded links panel with links: replay its items (" + p.id() + ")");
                    }
                    return new com.ash.rachana.view.PanelData.Links(List.of());
                });
    }

    /** The recording's values as the reference read them: whole numbers as long, others as double (RefBridgeSutra.plain). */
    static Object plain(Object v) {
        if (v instanceof BigDecimal d) {
            if (d.scale() <= 0) {
                return d.longValueExact();
            }
            return d.doubleValue();
        }
        if (v instanceof List<?> l) {
            List<Object> out = new ArrayList<>();
            l.forEach(x -> out.add(plain(x)));
            return out;
        }
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, x) -> out.put(String.valueOf(k), plain(x)));
            return out;
        }
        return v;
    }
}
