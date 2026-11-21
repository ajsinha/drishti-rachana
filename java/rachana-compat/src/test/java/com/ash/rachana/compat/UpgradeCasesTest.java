/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.compat;

import static org.junit.jupiter.api.Assertions.*;

import com.ash.rachana.conformance.Cases;
import com.ash.rachana.parse.Json;
import com.ash.rachana.parse.PositionalYamlReader;
import com.ash.rachana.tools.Upgrade;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The verified cases of {@code rachana upgrade} ({@code compat/upgrade/cases.yaml}): per case the text written, the rules of its changes
 * and flags, and the meaning: the original (read at 1.0, as its {@code rachana: 1} declares) and the upgraded Sutra (at 1.2) are bound
 * on one host of the newest level over the case's record; their ViewModels are the same where the case says so, and provably not where a
 * flag says they may differ. Upgrading the written text again changes nothing.
 */
class UpgradeCasesTest {

    @Test
    @SuppressWarnings("unchecked")
    void everyCaseUpgradesAsVerified() throws Exception {
        Map<String, Object> doc = (Map<String, Object>) new PositionalYamlReader().read(
                Files.readString(Corpus.repoRoot().resolve("compat/upgrade/cases.yaml"), StandardCharsets.UTF_8)).plain();
        assertEquals(Boolean.TRUE, doc.get("verified"));
        List<String> failures = new ArrayList<>();
        int n = 0;
        for (Object o : (List<Object>) doc.get("cases")) {
            Map<String, Object> c = (Map<String, Object>) o;
            String id = (String) c.get("id");
            String before = (String) c.get("before");
            String file = c.get("file") instanceof String f ? f : id + ".sutra.yaml";
            Upgrade.Result r = Upgrade.upgrade(file, before, UpgradeCorpusTest.TO, null);
            n++;
            if (c.get("refused") instanceof String why) {
                if (r.refused() == null || !r.refused().contains(why)) {
                    failures.add(id + ": not refused for '" + why + "' (" + r.refused() + ")");
                }
                continue;
            }
            if (r.refused() != null) {
                failures.add(id + ": refused: " + r.refused());
                continue;
            }
            List<String> changes = r.changes().stream().map(Upgrade.Change::rule).toList();
            List<String> flags = r.flags().stream().map(f -> f.rule() + ":" + f.severity()).toList();
            if (!changes.equals(c.get("changes"))) {
                failures.add(id + ": changes " + changes);
            }
            if (!flags.equals(c.get("flags"))) {
                failures.add(id + ": flags " + flags);
            }
            if (Boolean.TRUE.equals(c.get("blocked"))) {
                if (r.writable()) {
                    failures.add(id + ": not blocked");
                }
                continue;
            }
            if (c.get("after") instanceof String after && !after.equals(r.text())) {
                failures.add(id + ": wrote\n" + r.text());
            }
            Upgrade.Result again = Upgrade.upgrade(file, r.text(), UpgradeCorpusTest.TO, null);
            if (!again.text().equals(r.text()) || !again.changes().isEmpty()) {
                failures.add(id + ": upgrading again changed it");
            }
            if (c.get("same") instanceof Boolean same) {
                Object record = c.get("record");
                String was = view(before, record);
                String is = view(r.text(), record);
                if (same != was.equals(is)) {
                    failures.add(id + ": the views are " + (same ? "not " : "") + "the same\n  1.0: " + was + "\n  1.2: " + is);
                }
            }
        }
        assertEquals(List.of(), failures, failures.size() + " of " + n + " cases");
        assertTrue(n >= 10, "the cases are there");
    }

    /** The ViewModel of a Sutra over a record on a host of the newest level (it reads the Sutra at the level the Sutra declares). */
    static String view(String sutra, Object record) {
        Cases.init("core", List.of(Corpus.repoRoot().resolve("spec/1/formats.yaml").toString()));
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("sutra", sutra);
        req.put("since", UpgradeCorpusTest.TO);
        req.put("profile", "core");
        req.put("given", Map.of("record", record == null ? Map.of() : record, "today", "2026-11-21"));
        return Cases.run(Cases::sutra, Json.write(req));
    }
}
