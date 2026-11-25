/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.tools;

import com.ash.rachana.Rachana;
import com.ash.rachana.SutraException;
import com.ash.rachana.SutraExpressions;
import com.ash.rachana.SutraProblem;
import com.ash.rachana.lib.Library;
import com.ash.rachana.lib.LibraryExports;
import com.ash.rachana.lib.LibraryResolver;
import com.ash.rachana.lib.LibrarySet;
import com.ash.rachana.lib.Sha384;
import com.ash.rachana.model.Sutra;
import com.ash.rachana.parse.PNode;
import com.ash.rachana.parse.PositionalYamlReader;
import com.ash.rachana.table.LiveScreen;
import com.ash.rachana.view.ViewJson;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The library quality gates (DESIGN.md (libraries) 15.2) that run on a checkout: LG1 library valid, LG2 frozen fragments, LG3 definition of done,
 * LG4 level replay, LG5 interface append-only and LG8 budgets (LG6, LG7 and LG9 need the other runtimes, a browser and the AI kit; they
 * run in release). One implementation: {@code rachana lib gate} and the repository's library check both call {@link #run}.
 *
 * <p>The released baseline lives in the library folder, written by {@link #freeze} and never by hand: {@code FROZEN.sha384} (the hash of
 * each released golden) and {@code released/<fragment>.v<N>.interface.json} (the interface and the measured size of each released
 * fragment major). A library that has released nothing has no baseline, and LG2, LG5 and the regression half of LG8 pass for it.
 */
public final class LibraryGates {

    /** The date every example is bound at. */
    public static final LocalDate DAY = LocalDate.of(2026, 1, 15);
    /** The names a public library never carries (a host, a product, a real source); a copyright line is not text of the fragment. */
    static final Pattern HOSTS = Pattern.compile("(?i)\\b(drishti|endur|calypso|murex|bloomberg|reuters|refinitiv)\\b");
    private static final Pattern FRAGMENT = Pattern.compile("fragments/([a-z][a-z0-9-]{1,63})\\.v([0-9]+)\\.fragment\\.yaml");

    private LibraryGates() {
    }

    /** One gate's outcome: failures make it fail; notes are what it could not check yet (or advises) and never fail it unless strict. */
    public record Result(String gate, String title, List<String> failures, List<String> notes) {

        public boolean pass() {
            return failures.isEmpty();
        }
    }

    /** What an example gave: its ViewModel (canonical JSON, null on failure), the problems and advisories, panels and the bind time. */
    public record Example(String json, List<String> problems, int panels, int uses, double bindMs) { }

    /** Runs the gates on one library folder of {@code root}. {@code strict} turns the notes of LG3 (a second example) into failures. */
    public static List<Result> run(Path root, String library, boolean strict) {
        Path dir = root.resolve(library);
        List<Result> out = new ArrayList<>();
        out.add(lg1(dir));
        if (!out.get(0).pass()) {
            return out;                                   // nothing else is meaningful on a library that does not load
        }
        Map<String, byte[]> files = LibraryTool.read(dir);
        Map<String, Map<String, Object>> entries = LibraryExports.entries(files);
        Map<String, Example> examples = new TreeMap<>();
        Rachana rachana = rachana(root, "1.2");
        for (String f : entries.keySet()) {
            Path p = dir.resolve("examples/" + f + ".sutra.yaml");
            if (Files.isRegularFile(p)) {
                examples.put(f, example(rachana, p, library + "/" + f + "@", true));
            }
        }
        out.add(lg2(dir, files, examples));
        out.add(lg3(library, dir, files, entries, examples, strict));
        out.add(lg4(root, library, dir, entries, examples));
        out.add(lg5(dir, files, entries));
        out.add(lg8(dir, files, entries, examples));
        return out;
    }

    /** The Rachana of one language level over the libraries beside each other in {@code root}. */
    public static Rachana rachana(Path root, String level) {
        return Rachana.core().language(level).libraries(LibrarySet.of(LibraryResolver.folder(root)));
    }

    /** Compiles an example Sutra: problems and advisories, and the ViewModel when there are none. */
    public static Example example(Rachana rachana, Path file, String usePrefix, boolean measure) {
        List<String> problems = new ArrayList<>();
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        int uses = 0;
        for (Matcher m = Pattern.compile("use:\\s*" + Pattern.quote(usePrefix)).matcher(text); m.find();) {
            uses++;
        }
        try {
            Sutra sutra = rachana.parser().parse(text, file.getFileName().toString(), "library");
            for (SutraProblem p : new SutraExpressions(rachana.compiler()).check(sutra)) {
                problems.add("problem " + p);
            }
            for (SutraProblem p : sutra.advisories()) {
                problems.add("advisory " + p);
            }
            if (!problems.isEmpty()) {
                return new Example(null, problems, sutra.panels().size(), uses, 0);
            }
            String json = null;
            double ms = 0;
            for (int i = 0; i < (measure ? 4 : 1); i++) {
                long t0 = System.nanoTime();
                String j = ViewJson.write(new LiveScreen(rachana, sutra, DAY, null, Map.of()).view());
                double took = (System.nanoTime() - t0) / 1e6;
                json = canonical(j) + "\n";
                ms = i == 0 ? took : Math.min(ms, took);          // the best of the warm runs: the cost of the bind, not of the JIT
            }
            return new Example(json, problems, sutra.panels().size(), uses, ms);
        } catch (SutraException e) {
            e.problems().forEach(p -> problems.add("problem " + p));
        } catch (RuntimeException e) {
            problems.add(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return new Example(null, problems, 0, uses, 0);
    }

    // LG1 ------------------------------------------------------------------------------------------------------------------------

    private static Result lg1(Path dir) {
        List<String> fail = new ArrayList<>();
        LibraryTool.verify(dir, Library.Trust.NONE).forEach(p -> fail.add(p.toString()));
        return new Result("LG1", "Library valid", fail, List.of());
    }

    // LG2 ------------------------------------------------------------------------------------------------------------------------

    private static Result lg2(Path dir, Map<String, byte[]> files, Map<String, Example> examples) {
        List<String> fail = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        byte[] frozen = files.get("FROZEN.sha384");
        if (frozen == null) {
            notes.add("nothing released yet: no FROZEN.sha384");
            return new Result("LG2", "Frozen fragments", fail, notes);
        }
        for (String line : new String(frozen, StandardCharsets.UTF_8).split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("  ", 2);
            String golden = parts[1].trim();
            byte[] now = files.get(golden);
            if (now == null) {
                fail.add(golden + " was released and is gone");
                continue;
            }
            if (!Sha384.hex(now).equals(parts[0])) {
                fail.add(golden + " was released and has been edited (a golden is added, never edited)");
                continue;
            }
            String name = golden.replaceAll("^goldens/|\\.viewmodel\\.json$", "");
            Example ex = examples.get(name);
            if (ex == null || ex.json() == null) {
                fail.add(golden + ": its example no longer compiles");
            } else if (!ex.json().trim().equals(canonical(new String(now, StandardCharsets.UTF_8).trim()))) {
                fail.add(golden + ": this commit no longer reproduces the released ViewModel");
            }
        }
        return new Result("LG2", "Frozen fragments", fail, notes);
    }

    // LG3 ------------------------------------------------------------------------------------------------------------------------

    private static Result lg3(String library, Path dir, Map<String, byte[]> files, Map<String, Map<String, Object>> entries,
            Map<String, Example> examples, boolean strict) {
        List<String> fail = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        for (String f : entries.keySet()) {
            Map<String, Object> e = entries.get(f);
            String summary = String.valueOf(e.getOrDefault("summary", ""));
            if (summary.isBlank() || summary.length() > 120) {
                fail.add(f + ": doc.summary is required and at most 120 characters (it is " + summary.length() + ")");
            }
            String text = new String(files.get("fragments/" + f + ".v" + latest(e) + ".fragment.yaml"), StandardCharsets.UTF_8);
            PNode root = new PositionalYamlReader().read(text.replace("%{", "pXq{"));
            PNode doc = root.map().get("doc");
            PNode description = doc == null ? null : doc.map().get("description");
            if (description == null || String.valueOf(description.value()).isBlank()) {
                fail.add(f + ": doc.description (when to use it and when not) is required");
            }
            documented(f, "parameter", root.map().get("params"), fail);
            documented(f, "slot", root.map().get("slots"), fail);
            PNode emits = root.map().get("emits");
            for (PNode ev : emits == null ? List.<PNode>of() : emits.list()) {
                if (ev.map().get("doc") == null) {
                    fail.add(f + ": event " + ev.map().get("name").value() + " has no doc");
                }
            }
            Example ex = examples.get(f);
            if (ex == null) {
                fail.add(f + ": no example (examples/" + f + ".sutra.yaml)");
            } else {
                ex.problems().forEach(p -> fail.add(f + ": example " + p));
                if (ex.uses() < 2) {
                    (strict ? fail : notes).add(f + ": " + ex.uses() + " use in its example; two are required, a minimal and a full one");
                }
                String name = f;
                if (!files.containsKey("goldens/" + name + ".viewmodel.json")) {
                    fail.add(f + ": no golden ViewModel (goldens/" + name + ".viewmodel.json)");
                }
                Matcher h = HOSTS.matcher(stripComments(new String(files.getOrDefault("examples/" + f + ".sutra.yaml", new byte[0]), StandardCharsets.UTF_8)));
                if (h.find()) {
                    fail.add(f + ": its example names a host or a product (" + h.group() + ")");
                }
            }
            Matcher h = HOSTS.matcher(stripComments(text));
            if (h.find()) {
                fail.add(f + ": names a host or a product (" + h.group() + ")");
            }
        }
        notes.add("not checked yet: edge tests (tests/), live and window cases, chart accessibility summaries, table samples (sizes)");
        return new Result("LG3", "Definition of done", fail, notes);
    }

    private static void documented(String f, String what, PNode node, List<String> fail) {
        if (node == null || !node.isMap()) {
            return;
        }
        node.map().forEach((n, v) -> {
            if (v.isMap() && v.map().get("doc") == null) {
                fail.add(f + ": " + what + " " + n + " has no doc");
            }
        });
    }

    private static String stripComments(String text) {
        return Pattern.compile("(?m)^\\s*#.*$").matcher(text).replaceAll("");
    }

    private static long latest(Map<String, Object> entry) {
        List<?> majors = (List<?>) entry.get("majors");
        return ((Number) majors.get(majors.size() - 1)).longValue();
    }

    // LG4 ------------------------------------------------------------------------------------------------------------------------

    private static Result lg4(Path root, String library, Path dir, Map<String, Map<String, Object>> entries, Map<String, Example> examples) {
        List<String> fail = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> en : entries.entrySet()) {
            String f = en.getKey();
            Object needs = en.getValue().get("needs");
            String level = needs instanceof Map<?, ?> n ? String.valueOf(n.get("language")) : "1.0";
            Example ex = examples.get(f);
            if (ex != null && ex.json() == null) {
                fail.add(f + ": does not compile at its stated level " + level);
            }
        }
        long listedAt11 = entries.values().stream().filter(e -> e.get("needs") instanceof Map<?, ?> n && "1.2".compareTo(String.valueOf(n.get("language"))) < 0).count();
        if (listedAt11 > 0) {
            fail.add(listedAt11 + " fragment(s) need a level above 1.2, which no host runs");
        }
        return new Result("LG4", "Level replay", fail, List.of("the library index for a host at level L lists only fragments whose needs.language is at most L"));
    }

    /** The fragments a host at language level {@code level} may be offered (the library index of that host). */
    public static List<String> indexAt(Path libraryDir, String level) {
        List<String> out = new ArrayList<>();
        LibraryExports.entries(LibraryTool.read(libraryDir)).forEach((n, e) -> {
            String need = e.get("needs") instanceof Map<?, ?> m ? String.valueOf(m.get("language")) : "1.0";
            if (need.compareTo(level) <= 0) {
                out.add(n);
            }
        });
        return out;
    }

    // LG5 ------------------------------------------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Result lg5(Path dir, Map<String, byte[]> files, Map<String, Map<String, Object>> entries) {
        List<String> fail = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        boolean any = false;
        for (Map.Entry<String, byte[]> f : files.entrySet()) {
            Matcher m = Pattern.compile("released/([a-z][a-z0-9-]*)\\.v([0-9]+)\\.interface\\.json").matcher(f.getKey());
            if (!m.matches()) {
                continue;
            }
            any = true;
            String name = m.group(1);
            Map<String, Object> was = (Map<String, Object>) com.ash.rachana.parse.Json.read(new String(f.getValue(), StandardCharsets.UTF_8));
            byte[] cur = files.get("fragments/" + name + ".v" + m.group(2) + ".fragment.yaml");
            if (cur == null) {
                fail.add(name + "@" + m.group(2) + " was released and its file is gone");
                continue;
            }
            Map<String, Object> now = LibraryExports.interfaceOf(new String(cur, StandardCharsets.UTF_8));
            compareParams(name, was.get("params"), now.get("params"), fail);
            compareNames(name, "slot", was.get("slots"), now.get("slots"), fail);
            compareEvents(name, was.get("events"), now.get("events"), fail);
        }
        if (!any) {
            notes.add("nothing released yet: no released/*.interface.json");
        }
        return new Result("LG5", "Interface append-only", fail, notes);
    }

    @SuppressWarnings("unchecked")
    private static void compareParams(String f, Object was, Object now, List<String> fail) {
        Map<String, Object> w = was instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        Map<String, Object> n = now instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        for (Map.Entry<String, Object> p : w.entrySet()) {
            if (!n.containsKey(p.getKey())) {
                fail.add(f + ": parameter " + p.getKey() + " was removed or renamed");
                continue;
            }
            Map<String, Object> a = (Map<String, Object>) p.getValue(), b = (Map<String, Object>) n.get(p.getKey());
            if (!String.valueOf(a.get("type")).equals(String.valueOf(b.get("type")))) {
                fail.add(f + ": parameter " + p.getKey() + " was retyped " + a.get("type") + " to " + b.get("type"));
            }
            if (!Boolean.TRUE.equals(a.get("required")) && Boolean.TRUE.equals(b.get("required"))) {
                fail.add(f + ": parameter " + p.getKey() + " was made required");
            }
            if (a.get("columns") instanceof Map<?, ?> wc) {
                Map<String, Object> nc = b.get("columns") instanceof Map<?, ?> x ? (Map<String, Object>) x : Map.of();
                for (Map.Entry<?, ?> c : wc.entrySet()) {
                    Object nv = nc.get(String.valueOf(c.getKey()));
                    if (nv == null) {
                        fail.add(f + ": contract column " + p.getKey() + "." + c.getKey() + " was removed or renamed");
                    } else if (c.getValue() instanceof Map<?, ?> cw && nv instanceof Map<?, ?> cn) {
                        if (!String.valueOf(cw.get("type")).equals(String.valueOf(cn.get("type")))) {
                            fail.add(f + ": contract column " + p.getKey() + "." + c.getKey() + " was retyped");
                        }
                        if (!Boolean.TRUE.equals(cw.get("optional")) && Boolean.TRUE.equals(cn.get("optional")) == false
                                && Boolean.TRUE.equals(cw.get("optional")) != Boolean.TRUE.equals(cn.get("optional"))) {
                            fail.add(f + ": contract column " + p.getKey() + "." + c.getKey() + " changed");
                        }
                    }
                }
            }
            if (a.get("options") instanceof List<?> wo && b.get("options") instanceof List<?> no && !no.containsAll(wo)) {
                fail.add(f + ": parameter " + p.getKey() + " lost an option");
            }
        }
        for (Map.Entry<String, Object> p : n.entrySet()) {
            if (!w.containsKey(p.getKey()) && p.getValue() instanceof Map<?, ?> b && Boolean.TRUE.equals(b.get("required")) && b.get("default") == null) {
                fail.add(f + ": new parameter " + p.getKey() + " is required (a new parameter has a default or is optional)");
            }
        }
    }

    private static void compareNames(String f, String what, Object was, Object now, List<String> fail) {
        if (was instanceof Map<?, ?> w) {
            Map<?, ?> n = now instanceof Map<?, ?> m ? m : Map.of();
            for (Object k : w.keySet()) {
                if (!n.containsKey(k)) {
                    fail.add(f + ": " + what + " " + k + " was removed or renamed");
                }
            }
        }
    }

    private static void compareEvents(String f, Object was, Object now, List<String> fail) {
        Map<String, Object> w = byName(was), n = byName(now);
        for (Map.Entry<String, Object> e : w.entrySet()) {
            if (!n.containsKey(e.getKey())) {
                fail.add(f + ": event " + e.getKey() + " was removed or renamed");
            } else if (e.getValue() instanceof Map<?, ?> a && n.get(e.getKey()) instanceof Map<?, ?> b
                    && a.get("payload") instanceof Map<?, ?> pa) {
                Map<?, ?> pb = b.get("payload") instanceof Map<?, ?> x ? x : Map.of();
                for (Map.Entry<?, ?> c : pa.entrySet()) {
                    if (!String.valueOf(c.getValue()).equals(String.valueOf(pb.get(c.getKey())))) {
                        fail.add(f + ": event " + e.getKey() + " payload " + c.getKey() + " was removed or retyped");
                    }
                }
            }
        }
    }

    private static Map<String, Object> byName(Object list) {
        Map<String, Object> out = new TreeMap<>();
        if (list instanceof List<?> l) {
            l.forEach(o -> {
                if (o instanceof Map<?, ?> m) {
                    out.put(String.valueOf(m.get("name")), m);
                }
            });
        }
        return out;
    }

    // LG8 ------------------------------------------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Result lg8(Path dir, Map<String, byte[]> files, Map<String, Map<String, Object>> entries, Map<String, Example> examples) {
        List<String> fail = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        int declared = 0;
        for (Map.Entry<String, Map<String, Object>> en : entries.entrySet()) {
            String f = en.getKey();
            Example ex = examples.get(f);
            if (ex == null || ex.json() == null) {
                continue;
            }
            if (en.getValue().get("budget") instanceof Map<?, ?> b) {
                declared++;
                int uses = Math.max(1, ex.uses());
                if (b.get("panels") instanceof Number n && ex.panels() > n.intValue() * uses) {
                    fail.add(f + ": " + ex.panels() + " panels for " + uses + " use(s); the budget is " + n + " each");
                }
                if (b.get("bind-ms") instanceof Number n && ex.bindMs() > n.doubleValue() * uses) {
                    fail.add(f + String.format(": bind took %.2f ms for %d use(s); the budget is %s each", ex.bindMs(), uses, n));
                }
                if (b.get("payload-kb") instanceof Number n && ex.json().length() / 1024.0 > n.doubleValue() * uses) {
                    fail.add(f + ": payload " + ex.json().length() / 1024 + " KB; the budget is " + n + " KB each");
                }
            }
            byte[] base = files.get("released/" + f + ".v" + latest(en.getValue()) + ".interface.json");
            if (base != null) {
                Map<String, Object> was = (Map<String, Object>) com.ash.rachana.parse.Json.read(new String(base, StandardCharsets.UTF_8));
                if (was.get("measured") instanceof Map<?, ?> m) {
                    regress(f, "panels", m.get("panels"), ex.panels(), fail);
                    regress(f, "payload bytes", m.get("payload-bytes"), ex.json().length(), fail);
                }
            }
        }
        notes.add(declared + " fragment(s) declare a budget; one without is measured against its released baseline only (a regression above 10% fails)");
        notes.add("not checked yet: bind-ms on the medium and large table samples (the samples are not built), the three-runtime and library-load budgets");
        return new Result("LG8", "Budgets", fail, notes);
    }

    private static void regress(String f, String what, Object was, double now, List<String> fail) {
        if (was instanceof Number w && w.doubleValue() > 0 && now > w.doubleValue() * 1.10) {
            fail.add(f + ": " + what + " " + (long) now + " is more than 10% above the released " + w);
        }
    }

    // freeze ---------------------------------------------------------------------------------------------------------------------

    /**
     * Records the library's released baseline (run when a version is released, never otherwise): {@code FROZEN.sha384} lists the hash
     * of every golden, and {@code released/<fragment>.v<N>.interface.json} the interface and measured size of each fragment major. A
     * golden or interface already recorded is left as it is (added, never edited). Run {@code rachana lib build} after.
     */
    public static void freeze(Path root, String library) {
        Path dir = root.resolve(library);
        Map<String, byte[]> files = LibraryTool.read(dir);
        Rachana rachana = rachana(root, "1.2");
        try {
            TreeMap<String, String> frozen = new TreeMap<>();
            byte[] old = files.get("FROZEN.sha384");
            if (old != null) {
                for (String line : new String(old, StandardCharsets.UTF_8).split("\n")) {
                    if (!line.isBlank()) {
                        String[] p = line.split("  ", 2);
                        frozen.put(p[1].trim(), p[0]);
                    }
                }
            }
            for (String path : files.keySet()) {
                if (path.startsWith("goldens/") && path.endsWith(".viewmodel.json")) {
                    frozen.putIfAbsent(path, Sha384.hex(files.get(path)));
                }
                Matcher m = FRAGMENT.matcher(path);
                if (m.matches()) {
                    Path target = dir.resolve("released/" + m.group(1) + ".v" + m.group(2) + ".interface.json");
                    if (Files.exists(target)) {
                        continue;
                    }
                    Map<String, Object> iface = new java.util.LinkedHashMap<>(LibraryExports.interfaceOf(new String(files.get(path), StandardCharsets.UTF_8)));
                    Path ex = dir.resolve("examples/" + m.group(1) + ".sutra.yaml");
                    if (Files.isRegularFile(ex)) {
                        Example e = example(rachana, ex, library + "/" + m.group(1) + "@", false);
                        if (e.json() != null) {
                            Map<String, Object> measured = new java.util.LinkedHashMap<>();
                            measured.put("panels", (long) e.panels());
                            measured.put("payload-bytes", (long) e.json().length());
                            iface.put("measured", measured);
                        }
                    }
                    Files.createDirectories(target.getParent());
                    Files.writeString(target, LibraryExports.json(iface, false) + "\n", StandardCharsets.UTF_8);
                }
            }
            StringBuilder sb = new StringBuilder();
            frozen.forEach((p, h) -> sb.append(h).append("  ").append(p).append('\n'));
            Files.writeString(dir.resolve("FROZEN.sha384"), sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // canonical JSON -------------------------------------------------------------------------------------------------------------

    /**
     * The same JSON with every object's keys in sorted order. The compiler writes the keys of an event's wire entry in an order that
     * varies between runs; a snapshot compares what the ViewModel says, not the order of a mapping's keys.
     */
    public static String canonical(String json) {
        int[] at = {0};
        StringBuilder out = new StringBuilder();
        value(json, at, out);
        return out.toString();
    }

    private static void value(String s, int[] at, StringBuilder out) {
        char c = s.charAt(at[0]);
        if (c == '{') {
            at[0]++;
            TreeMap<String, String> members = new TreeMap<>();
            while (s.charAt(at[0]) != '}') {
                if (s.charAt(at[0]) == ',') {
                    at[0]++;
                }
                StringBuilder key = new StringBuilder();
                string(s, at, key);
                at[0]++;
                StringBuilder v = new StringBuilder();
                value(s, at, v);
                members.put(key.toString(), v.toString());
            }
            at[0]++;
            out.append('{');
            boolean first = true;
            for (Map.Entry<String, String> m : members.entrySet()) {
                out.append(first ? "" : ",").append(m.getKey()).append(':').append(m.getValue());
                first = false;
            }
            out.append('}');
        } else if (c == '[') {
            at[0]++;
            out.append('[');
            boolean first = true;
            while (s.charAt(at[0]) != ']') {
                if (s.charAt(at[0]) == ',') {
                    at[0]++;
                }
                out.append(first ? "" : ",");
                first = false;
                value(s, at, out);
            }
            at[0]++;
            out.append(']');
        } else if (c == '"') {
            string(s, at, out);
        } else {
            int from = at[0];
            while (at[0] < s.length() && ",]}".indexOf(s.charAt(at[0])) < 0) {
                at[0]++;
            }
            out.append(s, from, at[0]);
        }
    }

    private static void string(String s, int[] at, StringBuilder out) {
        int from = at[0]++;
        while (s.charAt(at[0]) != '"') {
            at[0] += s.charAt(at[0]) == '\\' ? 2 : 1;
        }
        at[0]++;
        out.append(s, from, at[0]);
    }

    /** The library folders of a root, by name. */
    public static List<String> libraries(Path root) {
        try (Stream<Path> s = Files.list(root)) {
            return s.filter(Files::isDirectory).map(p -> p.getFileName().toString()).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
