/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.compat;

import static org.junit.jupiter.api.Assertions.*;

import com.ash.rachana.tools.Upgrade;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The proof of {@code rachana upgrade} (owner 2026-11-21): every Sutra and fragment of every customer corpus is upgraded from level 1.0
 * to the newest level, then compiled and bound on a host of that level, and the answers are compared with the 1.0 originals' recorded
 * ones (CG3: {@code compiled/}, {@code binds/}). Byte for byte, except the level stamp ({@code rachana: 1.2} in the canonical model text)
 * and the expressions the tool rewrote (compared as written before the rewrite); every other difference must be a review flag the tool
 * raised on that Sutra. Upgrading an upgraded file changes nothing. The outcome is the committed report
 * {@code compat/upgrade/<corpus>.report.json} (counts, every change, every flag, every difference and the flag that explains it), written
 * with {@code -Drachana.upgrade.report.write=true} and otherwise compared.
 */
class UpgradeCorpusTest {

    static final String TO = com.ash.rachana.profile.Grammar.DEFAULT_LEVEL;

    @Test
    void everyCorpusUpgradesWithTheSameAnswers() throws Exception {
        for (Corpus c : Corpus.all()) {
            Map<String, Upgrade.Result> results = new TreeMap<>();
            CoreRuntime.Prepare upgrade = scratch -> upgradeTree(scratch, results);

            // compile: the recorded documents, less the stamp and the rewritten expressions
            Path out = Files.createTempDirectory("rachana-upgrade-out-");
            List<String> compileDiffs = new ArrayList<>();
            int compiled = 0;
            try {
                CoreRuntime.compileAll(c, out, TO, upgrade);
                Path recorded = c.dir().resolve("compiled");
                try (Stream<Path> s = Files.walk(recorded)) {
                    for (Path f : s.filter(Files::isRegularFile).sorted().toList()) {
                        String rel = recorded.relativize(f).toString().replace('\\', '/');
                        Path got = out.resolve("compiled").resolve(rel);
                        compiled++;
                        if (!Files.exists(got)) {
                            compileDiffs.add(rel + ": not produced");
                            continue;
                        }
                        String want = Files.readString(f, StandardCharsets.UTF_8);
                        String have = normalised(Files.readString(got, StandardCharsets.UTF_8), results.get("tree/" + rel.replaceFirst("\\.json$", "")));
                        if (!want.equals(have)) {
                            compileDiffs.add(rel + ": " + firstDifference(want, have));
                        }
                    }
                }
            } finally {
                ReferenceBridgeRuntime.deleteTree(out);
            }

            // bind: the recorded ViewModels; a difference needs a review flag on its Sutra
            Map<String, Upgrade.Result> bound = new TreeMap<>();
            List<CoreBinds.Outcome> outcomes = CoreBinds.replay(c, TO, scratch -> upgradeTree(scratch, bound));
            List<Map<String, Object>> bindDiffs = new ArrayList<>();
            List<String> unexplained = new ArrayList<>();
            int identical = 0;
            for (CoreBinds.Outcome o : outcomes) {
                if (o.difference() == null) {
                    identical++;
                    continue;
                }
                Upgrade.Result r = bound.get("tree/" + o.file());
                List<String> because = r == null ? List.of() : r.flags().stream().filter(f -> !"info".equals(f.severity()))
                        .map(f -> f.rule() + " " + f.path()).toList();
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("file", o.file());
                d.put("difference", o.difference());
                d.put("explainedBy", because);
                bindDiffs.add(d);
                if (because.isEmpty()) {
                    unexplained.add(o.file() + ": " + o.difference());
                }
            }

            // idempotent: the upgraded text upgrades to itself
            List<String> notIdempotent = new ArrayList<>();
            for (Map.Entry<String, Upgrade.Result> e : results.entrySet()) {
                Upgrade.Result again = Upgrade.upgrade(e.getKey(), e.getValue().text(), TO, null);
                if (again.changed() || !again.changes().isEmpty() || again.refused() != null) {
                    notIdempotent.add(e.getKey());
                }
            }

            Map<String, Object> report = new LinkedHashMap<>();
            report.put("corpus", c.name());
            report.put("to", TO);
            Map<String, Object> summary = Upgrade.report(new ArrayList<>(results.values()));
            report.put("files", summary.get("files"));
            report.put("sutras", results.values().stream().filter(r -> "sutra".equals(r.kind())).count());
            report.put("fragments", results.values().stream().filter(r -> "fragment".equals(r.kind())).count());
            report.put("changedFiles", summary.get("changed"));
            report.put("refused", summary.get("refused"));
            report.put("blocked", summary.get("blocked"));
            report.put("byRule", summary.get("byRule"));
            report.put("compiledCompared", compiled);
            report.put("compileDifferences", compileDiffs);
            report.put("bindsReplayed", outcomes.size());
            report.put("bindsIdentical", identical);
            report.put("bindDifferences", bindDiffs);
            report.put("results", summary.get("results"));
            String json = com.ash.rachana.parse.Json.write(report) + "\n";
            Path committed = Corpus.repoRoot().resolve("compat/upgrade/" + c.name() + ".report.json");
            if (Boolean.getBoolean("rachana.upgrade.report.write")) {
                Files.createDirectories(committed.getParent());
                Files.writeString(committed, json, StandardCharsets.UTF_8);
            }

            assertEquals(0, ((Number) summary.get("refused")).intValue() + ((Number) summary.get("blocked")).intValue(),
                    c.name() + ": refused or blocked files: " + results.values().stream().filter(r -> !r.writable()).map(Upgrade.Result::file).toList());
            assertEquals(List.of(), compileDiffs, c.name() + ": the upgraded Sutras compile to other documents");
            assertEquals(List.of(), unexplained, c.name() + ": binds differ with no flag to explain them");
            assertEquals(List.of(), notIdempotent, c.name() + ": upgrading again changed these");
            assertTrue(Files.isRegularFile(committed), "no committed report " + committed + " (write it with -Drachana.upgrade.report.write=true)");
            assertEquals(Files.readString(committed, StandardCharsets.UTF_8), json, c.name() + ": the report differs from the committed one");
        }
    }

    /** Upgrades every Sutra and fragment of a scratch copy in place; the results by path relative to the scratch folder. */
    static void upgradeTree(Path scratch, Map<String, Upgrade.Result> results) throws Exception {
        try (Stream<Path> s = Files.walk(scratch)) {
            for (Path f : s.filter(Files::isRegularFile).sorted().toList()) {
                String n = f.getFileName().toString();
                if (!(n.endsWith(".sutra.yaml") || n.endsWith(".fragment.yaml") || n.endsWith(".block.yaml"))) {
                    continue;
                }
                String rel = scratch.relativize(f).toString().replace('\\', '/');
                Upgrade.Result r = Upgrade.upgrade(rel, Files.readString(f, StandardCharsets.UTF_8), TO, null);
                results.put(rel, r);
                if (r.writable() && r.changed()) {
                    Files.writeString(f, r.text(), StandardCharsets.UTF_8);
                }
            }
        }
    }

    /** A compiled document with the level stamp and the tool's rewrites put back as level 1.0 wrote them. */
    static String normalised(String doc, Upgrade.Result r) {
        String out = doc.replace("\"model\": \"rachana: " + TO + "\\n", "\"model\": \"rachana: 1\\n");
        if (r != null) {
            for (Upgrade.Change ch : r.changes()) {
                if (!"declare".equals(ch.rule())) {
                    out = out.replace(CoreRuntime.str(ch.after()).replaceAll("^\"|\"$", ""), CoreRuntime.str(ch.before()).replaceAll("^\"|\"$", ""));
                }
            }
        }
        return out;
    }

    static String firstDifference(String a, String b) {
        int i = 0;
        while (i < Math.min(a.length(), b.length()) && a.charAt(i) == b.charAt(i)) {
            i++;
        }
        int from = Math.max(0, i - 80);
        return "at " + i + ": recorded '" + a.substring(from, Math.min(a.length(), i + 80)) + "' upgraded '" + b.substring(from, Math.min(b.length(), i + 80)) + "'";
    }
}
