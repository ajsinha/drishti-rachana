/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Core's YAML reader against the reference's answers, recorded by {@code tools/yaml-diff} (plan A8a): every vector's
 * tree (types, values, positions), issues and error text and place must be the same, byte for byte. The vectors are
 * this repository's own inputs: the edge inputs, every text of the conformance case files, and three mutations of each.
 * The one accepted difference: Core adds an issue for an anchor or alias, which the reference read silently.
 */
class PositionalYamlReaderTest {

    private static final PositionalYamlReader READER = new PositionalYamlReader();

    @Test
    void everyVectorReadsAsTheReferenceReadIt() throws Exception {
        List<String> failures = new ArrayList<>();
        int n = 0;
        try (InputStream in = PositionalYamlReaderTest.class.getResourceAsStream("/yaml/reference-vectors.jsonl");
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                n++;
                String input = input(line);
                String expected = line.substring(line.indexOf(",\"expect\":") + 10, line.length() - 1);
                String actual = ascii(withoutAnchorIssues(core(input)));
                if (!expected.equals(actual)) {
                    failures.add("vector " + n + ": " + Json.quote(input.length() > 120 ? input.substring(0, 120) : input) + "\n  expected " + expected
                            + "\n  actual   " + actual);
                }
            }
        }
        assertTrue(n > 1000, "the vectors were not found");
        assertTrue(failures.isEmpty(), failures.size() + " of " + n + " differ:\n" + String.join("\n", failures.subList(0, Math.min(10, failures.size()))));
    }

    @Test
    void anchorsAndAliasesAreIssues() {
        List<PositionalYamlReader.Issue> issues = new ArrayList<>();
        PNode n = READER.read("a: &x 1\nb: *x\n", issues);
        assertEquals(2, issues.size());
        assertTrue(issues.get(0).message().contains("anchor '&x'"));
        assertTrue(issues.get(1).message().contains("alias '*x'"));
        assertEquals("x", n.map().get("b").value(), "the tree is the reference's: an alias reads as its name");
    }

    @Test
    void typesFollowYaml11() {
        Map<String, PNode> m = READER.read("a: yes\nb: 1.0\nc: 0x1F\nd: '12'\ne: 2026-11-19\nf: ~\ng: 123456789012345678901234567890\n").map();
        assertEquals(Boolean.TRUE, m.get("a").value());
        assertEquals(1.0, m.get("b").value());
        assertEquals(31L, m.get("c").value());
        assertEquals("12", m.get("d").value());
        assertEquals("2026-11-19", m.get("e").value());
        assertEquals(null, m.get("f").value());
        assertEquals(new BigInteger("123456789012345678901234567890"), m.get("g").value());
    }

    @Test
    void syntaxErrorsCarryTheReferenceText() {
        YamlSyntaxException e = assertThrows(YamlSyntaxException.class, () -> READER.read("rachana: 1\nsutra: ["));
        assertEquals("while parsing a flow node\n in 'reader', line 2, column 9:\n    sutra: [\n            ^\n"
                + "expected the node content, but found '<stream end>'\n in 'reader', line 2, column 9:\n    sutra: [\n            ^\n", e.getMessage());
        assertEquals(2, e.line());
        assertEquals(9, e.column());
    }

    @Test
    void plainValuesDropPositions() {
        Object plain = READER.read("a: [1, {b: x}]\n").plain();
        assertEquals(Map.of("a", List.of(1L, Map.of("b", "x"))), plain);
        assertFalse(plain.toString().contains("PNode"));
    }

    // ---- the vectors' canonical form (as tools/yaml-diff writes it) ----

    private static String input(String line) {
        int start = "{\"input\":".length();
        int i = start + 1;
        while (line.charAt(i) != '"') {
            i += line.charAt(i) == '\\' ? 2 : 1;
        }
        return (String) Json.read(line.substring(start, i + 1));
    }

    private static String core(String text) {
        List<PositionalYamlReader.Issue> issues = new ArrayList<>();
        try {
            PNode n = READER.read(text, issues);
            StringBuilder sb = new StringBuilder("{\"tree\":");
            dump(n, sb);
            sb.append(",\"issues\":[");
            for (int i = 0; i < issues.size(); i++) {
                var x = issues.get(i);
                if (i > 0) {
                    sb.append(',');
                }
                sb.append("{\"message\":").append(Json.quote(x.message())).append(",\"line\":").append(x.line()).append(",\"column\":")
                        .append(x.column()).append('}');
            }
            return sb.append("]}").toString();
        } catch (YamlSyntaxException e) {
            return "{\"error\":{\"message\":" + Json.quote(e.getMessage()) + ",\"line\":" + e.line() + ",\"column\":" + e.column() + "}}";
        }
    }

    private static String withoutAnchorIssues(String dump) {
        return dump.replaceAll(",?\\{\"message\":\"YAML (anchor|alias) [^\"]*\"[^}]*\\}", "").replace("[,", "[");
    }

    private static void dump(PNode node, StringBuilder sb) {
        Object v = node.value();
        sb.append("{\"at\":[").append(node.line()).append(',').append(node.column()).append("],");
        if (v instanceof Map<?, ?>) {
            sb.append("\"map\":[");
            boolean first = true;
            for (Map.Entry<String, PNode> e : node.map().entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append('[').append(Json.quote(e.getKey())).append(',');
                dump(e.getValue(), sb);
                sb.append(']');
            }
            sb.append(']');
        } else if (v instanceof List<?>) {
            sb.append("\"list\":[");
            List<PNode> l = node.list();
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                dump(l.get(i), sb);
            }
            sb.append(']');
        } else if (v == null) {
            sb.append("\"null\":true");
        } else if (v instanceof String s) {
            sb.append("\"str\":").append(Json.quote(s));
        } else if (v instanceof Long l) {
            sb.append("\"long\":").append(l);
        } else if (v instanceof BigInteger b) {
            sb.append("\"big\":").append(b);
        } else if (v instanceof Double d) {
            sb.append("\"double\":\"").append(d).append('"');
        } else if (v instanceof Boolean b) {
            sb.append("\"bool\":").append(b);
        } else {
            sb.append("\"other\":").append(Json.quote(v.getClass().getName() + ":" + v));
        }
        sb.append('}');
    }

    private static String ascii(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x7F) {
                sb.append(c);
            } else {
                sb.append(String.format("\\u%04x", (int) c));
            }
        }
        return sb.toString();
    }
}
