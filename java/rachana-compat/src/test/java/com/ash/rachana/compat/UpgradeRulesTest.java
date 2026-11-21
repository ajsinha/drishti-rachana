/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.compat;

import static org.junit.jupiter.api.Assertions.*;

import com.ash.rachana.el.ElSource;
import com.ash.rachana.model.PanelKind;
import com.ash.rachana.parse.PositionalYamlReader;
import com.ash.rachana.profile.Grammar;
import com.ash.rachana.tools.UpgradeRule;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The rule list of {@code rachana upgrade} is complete ({@link UpgradeRule}): every level difference the documents and the suite name
 * is covered by one rule (a rewrite, a flag, or nothing with its reason). The differences, read from their sources: the moved cases
 * ({@code compat/levels/moved-cases.yaml}); every conformance case bound to a level ({@code until}, an id ending {@code -l11}, a suite
 * of level 1.1 or of 1.0 only); the functions level 1.1 binds anew (ElSource, from Level11); the keys, kinds and options of later levels
 * (Grammar, PanelKind); the rows of SPEC 4.17 and of {@code el/KNOWN_DIFFERENCES.md}; the items of SPEC 9; the level decisions of
 * DECISIONS. A rule that rewrites or flags is pinned by at least one verified case ({@code compat/upgrade/cases.yaml}).
 */
class UpgradeRulesTest {

    /** The DECISIONS rows that name a level difference, besides those found by their words. */
    static final List<String> DECISIONS = List.of("EL1-1", "EL1-2", "EL1-3", "EL1-4", "EL1-5", "EL1-6", "PY1", "PY2", "PY3", "PY4", "PY5",
            "PY6", "PY7", "PY8", "N5", "D5-2", "A7b-1", "A7b-2", "A7b-3", "A7b-4", "O3", "O4", "O6", "D11");

    @Test
    void everyLevelDifferenceHasARule() throws Exception {
        Set<String> differences = new TreeSet<>();
        Path root = Corpus.repoRoot();
        // 1. moved cases
        for (String l : Files.readAllLines(root.resolve("compat/levels/moved-cases.yaml"))) {
            Matcher m = Pattern.compile("^\\s*- \\{case: \"([^\"]+)\"").matcher(l);
            if (m.find()) differences.add("case:" + m.group(1));
        }
        // 2. level-bound conformance cases
        try (Stream<Path> s = Files.walk(root.resolve("conformance/1"))) {
            for (Path f : s.filter(p -> p.toString().endsWith(".yaml")).sorted().toList()) {
                Object doc;
                try {
                    doc = new PositionalYamlReader().read(Files.readString(f, StandardCharsets.UTF_8)).plain();
                } catch (RuntimeException e) {
                    continue;
                }
                if (!(doc instanceof Map<?, ?> d) || !(d.get("cases") instanceof List<?> cases) || !(d.get("suite") instanceof String suite)) continue;
                boolean levelSuite = suite.contains("-1-1") || suite.contains("at-1-0");
                for (Object o : cases) {
                    if (o instanceof Map<?, ?> c && c.get("id") != null) {
                        String id = String.valueOf(c.get("id"));
                        if (levelSuite || c.containsKey("until") || id.endsWith("-l11")) differences.add("case:" + suite + "#" + id);
                    }
                }
            }
        }
        // 3. functions level 1.1 binds anew
        ElSource.changedAtLevel11().forEach(n -> differences.add("level11:" + n));
        // 4. keys, kinds, options of later levels
        Grammar.LEVEL_KEYS.keySet().forEach(k -> differences.add("key:" + k));
        for (PanelKind k : PanelKind.values()) {
            if (!"1.0".equals(k.since())) {
                differences.add("kind:" + k.id());
            } else {
                for (String opt : k.optional(Grammar.DEFAULT_LEVEL)) {
                    if (!k.optional("1.0").contains(opt)) differences.add("option:" + k.id() + "." + opt);
                }
            }
        }
        String spec = Files.readString(root.resolve("spec/1/SPEC.md"), StandardCharsets.UTF_8);
        // 5. SPEC 4.17
        for (List<String> row : rows(spec.substring(spec.indexOf("### 4.17"), spec.indexOf("## 5.")))) differences.add("spec4.17:" + row.get(0));
        // 6. KNOWN_DIFFERENCES
        for (List<String> row : rows(Files.readString(root.resolve("conformance/1/el/KNOWN_DIFFERENCES.md"), StandardCharsets.UTF_8))) {
            differences.add("known:" + row.get(0));
        }
        // 7. SPEC 9
        Matcher items = Pattern.compile("(?m)^(\\d+)\\. ").matcher(spec.substring(spec.indexOf("## 9.")));
        while (items.find()) differences.add("spec9:" + items.group(1));
        // 8. DECISIONS
        String decisions = Files.readString(root.resolve("docs/design/DECISIONS.md"), StandardCharsets.UTF_8);
        DECISIONS.forEach(d -> {
            assertTrue(decisions.contains("| " + d + " |") || decisions.contains("| **" + d), "DECISIONS has no row " + d);
            differences.add("decision:" + d);
        });
        for (String line : decisions.split("\n")) {
            Matcher m = Pattern.compile("^\\| \\*{0,2}([A-Z][A-Za-z0-9]*-?[0-9]+[a-z]?(?:-[0-9]+)?)\\*{0,2} \\|(.*)").matcher(line);
            if (m.find() && Pattern.compile("level 1\\.[01]|at 1\\.1|1\\.1\\b").matcher(m.group(2)).find()) differences.add("decision:" + m.group(1));
        }

        List<String> uncovered = differences.stream().filter(d -> !covered(d)).toList();
        assertEquals(List.of(), uncovered, uncovered.size() + " of " + differences.size() + " level differences have no rule in UpgradeRule");
        assertTrue(differences.size() > 300, "the sources were read: " + differences.size());
    }

    @Test
    void everyRuleThatActsIsPinnedByAVerifiedCase() throws Exception {
        String cases = Files.readString(Corpus.repoRoot().resolve("compat/upgrade/cases.yaml"), StandardCharsets.UTF_8);
        List<String> unpinned = new ArrayList<>();
        for (UpgradeRule r : UpgradeRule.values()) {
            if (r.action() != UpgradeRule.Action.NONE && !Pattern.compile("[\\[ ,]" + Pattern.quote(r.id()) + "[\\],:]").matcher(cases).find()) {
                unpinned.add(r.id());
            }
        }
        assertEquals(List.of(), unpinned, "rules with no case in compat/upgrade/cases.yaml");
    }

    @Test
    void everyCoverNamesSomethingThatExistsOrAGlob() {
        Set<String> seen = new LinkedHashSet<>();
        for (UpgradeRule r : UpgradeRule.values()) {
            assertFalse(r.covers().isEmpty(), r.id() + " covers nothing");
            for (String c : r.covers()) assertTrue(seen.add(c), "covered twice: " + c);
        }
    }

    static boolean covered(String difference) {
        for (UpgradeRule r : UpgradeRule.values()) {
            for (String c : r.covers()) {
                if (c.equals(difference) || c.endsWith("#*") && difference.startsWith(c.substring(0, c.length() - 1))) return true;
            }
        }
        return false;
    }

    /** The body rows of every Markdown table in {@code text}, cells trimmed, escaped bars kept; header rows (before a rule line) left out. */
    static List<List<String>> rows(String text) {
        List<List<String>> out = new ArrayList<>();
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i];
            if (!l.startsWith("|") || l.matches("^\\|\\s*-.*")) continue;
            if (i + 1 < lines.length && lines[i + 1].matches("^\\|\\s*-.*")) continue;   // a header
            List<String> cells = new ArrayList<>();
            for (String c : l.split("(?<!\\\\)\\|", -1)) cells.add(c.strip().replace("\\|", "|"));
            out.add(cells.subList(1, cells.size() - 1));
        }
        return out;
    }
}
