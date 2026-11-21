/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ash.rachana.api.Node;
import com.ash.rachana.parse.Json;
import com.ash.rachana.parse.PositionalYamlReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The host-node conformance fixture: every case of the corpus whose documents a host hands over answers the same over a host's own
 * node type ({@link HostNodes}: covariant navigation, its own masked and missing values, its own factory) as over Rachana's nodes. A
 * host value Core cannot read, or a list Core builds in its own type where the host reads its type, shows here as a different answer.
 * The same check runs end to end with {@code tools/conformance/rachana_conformance.py --jvm --host-nodes}.
 */
class HostNodeConformanceTest {

    static final Path ROOT = Path.of("..", "..").toAbsolutePath().normalize();
    static final List<String> SKIP = List.of("expect", "verified", "source", "note", "tags", "id");

    @AfterEach
    void ownNodesAgain() {
        Cases.documents = Node::of;
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyCaseAnswersAlikeOverAHostsNodes() throws Exception {
        Cases.init("core,drishti", List.of(ROOT.resolve("spec/1/formats.yaml").toString(),
                ROOT.resolve("tools/conformance/profile/drishti-formats.yaml").toString()));
        List<String> lines = new ArrayList<>();
        Path cases = ROOT.resolve("conformance/1");
        try (Stream<Path> s = Files.walk(cases)) {
            for (Path p : s.filter(f -> f.toString().endsWith(".yaml")).sorted().toList()) {
                Map<String, Object> doc = (Map<String, Object>) new PositionalYamlReader().read(Files.readString(p, StandardCharsets.UTF_8)).plain();
                String suite = cases.relativize(p).toString().replace('\\', '/').replaceAll("\\.yaml$", "");
                for (Object o : (List<Object>) doc.get("cases")) {
                    Map<String, Object> c = new LinkedHashMap<>((Map<String, Object>) o);
                    if (!(c.get("kind") instanceof String k) || !List.of("expr", "expr-error", "check").contains(k)) {
                        continue;
                    }
                    for (String key : List.of("since", "profile")) {
                        if (!c.containsKey(key) && doc.containsKey(key)) {
                            c.put(key, doc.get(key));
                        }
                    }
                    Map<String, Object> given = new LinkedHashMap<>();
                    if (doc.get("given") instanceof Map<?, ?> g) {
                        given.putAll((Map<String, Object>) g);
                    }
                    if (c.get("given") instanceof Map<?, ?> g) {
                        given.putAll((Map<String, Object>) g);
                    }
                    if (!given.isEmpty()) {
                        c.put("given", given);
                    }
                    String id = suite + "#" + c.get("id");
                    SKIP.forEach(c::remove);
                    c.put("case", id);
                    lines.add(Json.write(c));
                }
            }
        }
        List<String> differ = new ArrayList<>();
        for (String line : lines) {
            Cases.documents = Node::of;
            String own = Cases.answer(line);
            Cases.documents = HostNodes::of;
            String host = Cases.answer(line);
            if (!own.equals(host) && differ.size() < 20) {
                differ.add(own + "\n   host: " + host);
            }
        }
        assertTrue(lines.size() > 1500, "the corpus's expression cases: " + lines.size());
        assertEquals(List.of(), differ, "answers that differ over a host's nodes");
    }
}
