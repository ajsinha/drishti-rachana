/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.compat;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import org.junit.jupiter.api.Test;

/**
 * CG6, reserved names (SUTRA_EXPANSION 6.2). {@code compat/reserved-names.yaml} lists every name a registered customer's profile declares
 * (tools/regime/reserved_names.py writes it from spec/1/rachana.lang.json: the entries whose profile is not Core). No Core name equals a
 * reserved one in the same category unless {@code compat/promotions.yaml} has an entry naming the level; a level switch has an entry
 * in {@code compat/level-switches.yaml} that carries the owner's approval.
 */
class ReservedNamesTest {
    static final Pattern ITEM = Pattern.compile("^\\s+- \"?([^\"]+?)\"?\\s*$");

    static Path compat() { return Corpus.repoRoot().resolve("compat"); }

    /** category -> names, from the simple form the tool writes: {@code category:} then {@code   - "name"} lines. */
    static Map<String, Set<String>> read(Path f) throws Exception {
        Map<String, Set<String>> m = new TreeMap<>();
        String cat = null;
        for (String l : Files.readAllLines(f)) {
            if (l.isBlank() || l.startsWith("#")) continue;
            Matcher it = ITEM.matcher(l);
            if (l.matches("^[A-Za-z][A-Za-z0-9]*:\\s*(\\[\\])?$")) { cat = l.substring(0, l.indexOf(':')); m.computeIfAbsent(cat, k -> new TreeSet<>()); }
            else if (it.matches() && cat != null) m.get(cat).add(it.group(1));
            else if (!l.matches("^[a-z_]+: .*")) throw new AssertionError(f.getFileName() + ": cannot read: " + l);
        }
        return m;
    }

    /** category -> Core names, from the language definition. */
    @SuppressWarnings("unchecked")
    static Map<String, Set<String>> coreNames() throws Exception {
        Map<String, Object> lang = Json.object(Json.parse(Files.readString(Corpus.repoRoot().resolve("spec/1/rachana.lang.json"))));
        Map<String, Set<String>> core = new TreeMap<>();
        for (var e : lang.entrySet()) {
            if (e.getValue() instanceof List<?> l) {
                for (Object x : l) if (x instanceof Map<?, ?> mm && isCore(mm.get("profile"))) core.computeIfAbsent(e.getKey(), k -> new TreeSet<>()).add(String.valueOf(((Map<String, Object>) mm).getOrDefault("name", ((Map<String, Object>) mm).get("code"))));
            }
        }
        Map<String, Object> keys = Json.object(lang.get("keys"));
        for (var e : keys.entrySet())
            for (Object x : Json.array(e.getValue())) { Map<String, Object> mm = Json.object(x); if (isCore(mm.get("profile"))) core.computeIfAbsent("keys", k -> new TreeSet<>()).add(e.getKey() + "." + mm.get("name")); }
        return core;
    }

    static boolean isCore(Object profile) { return "core".equals(profile) || (profile instanceof List<?> l && l.contains("core")); }

    static List<String> clashes(Map<String, Set<String>> reserved, Map<String, Set<String>> core, Set<String> promoted) {
        List<String> out = new ArrayList<>();
        for (var e : reserved.entrySet())
            for (String n : e.getValue())
                if (core.getOrDefault(e.getKey(), Set.of()).contains(n) && !promoted.contains(e.getKey() + ":" + n)) out.add(e.getKey() + ": " + n);
        return out;
    }

    static Set<String> promotions() throws Exception {
        Path f = compat().resolve("promotions.yaml");
        Set<String> s = new TreeSet<>();
        Pattern p = Pattern.compile("^\\s*- \\{ category: (\\w+), name: \"([^\"]+)\", level: \"(\\d+\\.\\d+)\" \\}\\s*$");
        for (String l : Files.readAllLines(f)) {
            if (l.isBlank() || l.startsWith("#") || l.startsWith("promotions:")) continue;
            Matcher m = p.matcher(l);
            if (!m.matches()) throw new AssertionError("promotions.yaml: each entry is '- { category: <c>, name: \"<n>\", level: \"<L>\" }': " + l);
            s.add(m.group(1) + ":" + m.group(2));
        }
        return s;
    }

    @Test
    void noCoreNameEqualsAReservedOneWithoutAPromotion() throws Exception {
        assertEquals(List.of(), clashes(read(compat().resolve("reserved-names.yaml")), coreNames(), promotions()));
    }

    @Test
    void theReservedListIsNotEmptyAndMatchesTheLanguageDefinition() throws Exception {
        Map<String, Set<String>> r = read(compat().resolve("reserved-names.yaml"));
        assertFalse(r.values().stream().allMatch(Set::isEmpty), "no reserved names: the generator was not run");
        assertFalse(coreNames().isEmpty());
    }

    @Test
    void theCheckSeesAClashAndAPromotionClearsIt() {
        Map<String, Set<String>> reserved = Map.of("functions", Set.of("fx"));
        Map<String, Set<String>> core = Map.of("functions", Set.of("fx", "abs"));
        assertEquals(List.of("functions: fx"), clashes(reserved, core, Set.of()));
        assertEquals(List.of(), clashes(reserved, core, Set.of("functions:fx")));
    }

    /**
     * A promoted format is the customer's definition, unchanged: Core's table at the promotion's level prints every probe value exactly
     * as the customer's profile file does (so a Drishti screen reads the same whichever defines it), and below that level Core does
     * not know the name (it prints the text form, SPEC 4.2).
     */
    @Test
    void aPromotedFormatIsTheCustomersDefinitionAndUnknownBelowItsLevel() throws Exception {
        String profile = Corpus.repoRoot().resolve("tools/conformance/profile/drishti-formats.yaml").toString();
        com.ash.rachana.format.Formats theirs = com.ash.rachana.format.Formats.load(null, List.of(profile));
        com.ash.rachana.format.Formats core = com.ash.rachana.format.Formats.defaults();
        Object[] probes = {0, 1, -1, 0.5, -0.04, 0.0012345, 0.0123456, 12.34, -12.36, 0.987654, 1234.56789, -98765.4321, 1e9, "abc", "12.5", null};
        Pattern p = Pattern.compile("^\\s*- \\{ category: formats, name: \"([^\"]+)\", level: \"(\\d+\\.\\d+)\" \\}\\s*$");
        int promoted = 0;
        for (String l : Files.readAllLines(compat().resolve("promotions.yaml"))) {
            Matcher m = p.matcher(l);
            if (!m.matches()) continue;
            promoted++;
            String name = m.group(1), level = m.group(2);
            assertTrue(theirs.has(name), name + " is not the customer's format");
            assertEquals(level, core.since(name), name + ": Core's table does not bring it at " + level);
            assertTrue(core.atLevel(level).has(name), name + " unknown at " + level);
            for (Object v : probes) assertEquals(theirs.format(name, v), core.atLevel(level).format(name, v), name + " of " + v);
            for (String below : com.ash.rachana.profile.Grammar.LEVELS) {
                if (com.ash.rachana.model.PanelKind.compareLevels(below, level) >= 0) break;
                assertFalse(core.atLevel(below).has(name), name + " known at " + below);
                assertEquals(String.valueOf(12.34), core.atLevel(below).format(name, 12.34), name + " at " + below + " is text");
            }
        }
        assertTrue(promoted > 0, "promotions.yaml names no format");
    }
}
