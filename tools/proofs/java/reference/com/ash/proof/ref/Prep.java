/*
 * Copyright (c) 2026 Ashutosh Sinha. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.proof.ref;

import com.ash.drishti.rachana.parse.PNode;
import com.ash.drishti.rachana.parse.PositionalYamlReader;
import com.ash.proof.Canon;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The prep step, run on the JVM with Drishti's own jars and Jackson (the reference's YAML reading): conformance case files to
 * {@code cases.jsonl}, the three bundled YAML resources to JSON, and each Sutra text to the tree Drishti's PositionalYamlReader
 * makes of it. Every non-integer number is written as its raw IEEE bits ({@code $f64}) so no other runtime parses decimal text.
 *
 * <p>Usage: {@code Prep <conformance/1 dir> <out dir> [more case dirs…]}
 */
public final class Prep {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    public static void main(String[] args) throws Exception {
        Path cases = Path.of(args[0]);
        Path out = Path.of(args[1]);
        Files.createDirectories(out.resolve("resources"));
        for (String r : new String[] {"formats.yaml", "world-regions.yaml", "rachana/el/signatures.yaml"}) {
            try (InputStream in = Prep.class.getClassLoader().getResourceAsStream(r)) {
                StringBuilder b = new StringBuilder();
                json(YAML.readTree(in), b);
                Files.writeString(out.resolve("resources").resolve(r.replace('/', '_') + ".json"), b, StandardCharsets.UTF_8);
            }
        }
        List<Path> files = new ArrayList<>();
        for (int k = 0; k < args.length; k++) {
            if (k == 1) {
                continue;
            }
            Path root = Path.of(args[k]);
            try (Stream<Path> s = Files.walk(root)) {
                s.filter(p -> p.toString().endsWith(".yaml")).forEach(files::add);
            }
        }
        List<String[]> named = new ArrayList<>();
        for (Path f : files) {
            JsonNode doc = YAML.readTree(Files.readString(f));
            named.add(new String[] {doc.path("suite").asText(), f.toString()});
        }
        named.sort((x, y) -> compareUtf8(x[0], y[0]));
        StringBuilder all = new StringBuilder();
        for (String[] n : named) {
            JsonNode doc = YAML.readTree(Files.readString(Path.of(n[1])));
            JsonNode fileGiven = doc.path("given");
            for (JsonNode c : doc.path("cases")) {
                ObjectNode line = YAML.createObjectNode();
                line.put("case", n[0] + "#" + c.path("id").asText());
                line.put("kind", c.path("kind").asText());
                ObjectNode given = YAML.createObjectNode();
                if (fileGiven.isObject()) {
                    given.setAll((ObjectNode) fileGiven);
                }
                if (c.path("given").isObject()) {
                    given.setAll((ObjectNode) c.path("given"));
                }
                line.set("given", given);
                for (String k : new String[] {"expr", "template", "scope", "types", "value", "format", "tone", "text", "rule", "sutra", "file"}) {
                    if (c.has(k)) {
                        line.set(k, c.get(k));
                    }
                }
                if (c.has("sutra")) {
                    line.put("pnode", pnode(c.get("sutra").asText()));
                }
                json(line, all);
                all.append('\n');
            }
        }
        Files.writeString(out.resolve("cases.jsonl"), all, StandardCharsets.UTF_8);
        System.out.println("prep: " + named.size() + " case files");
    }

    static int compareUtf8(String a, String b) {
        byte[] x = a.getBytes(StandardCharsets.UTF_8);
        byte[] y = b.getBytes(StandardCharsets.UTF_8);
        return java.util.Arrays.compareUnsigned(x, y);
    }

    /** Drishti's own reader over the text, written as the proof's PNode JSON. */
    static String pnode(String yaml) {
        List<PositionalYamlReader.Issue> issues = new ArrayList<>();
        StringBuilder b = new StringBuilder("{\"$pnode\":");
        try {
            PNode root = new PositionalYamlReader().read(yaml, issues);
            node(root, b);
        } catch (Exception e) {
            b.append("null");
        }
        b.append(",\"issues\":[");
        for (int i = 0; i < issues.size(); i++) {
            PositionalYamlReader.Issue is = issues.get(i);
            if (i > 0) {
                b.append(',');
            }
            b.append('[');
            Canon.string(is.message(), b);
            b.append(',').append(is.line()).append(',').append(is.column()).append(']');
        }
        return b.append("]}").toString();
    }

    static void node(PNode n, StringBuilder b) {
        b.append("{\"l\":").append(n.line()).append(",\"c\":").append(n.column()).append(',');
        if (n.isMap()) {
            b.append("\"m\":{");
            boolean first = true;
            for (Map.Entry<String, PNode> e : n.map().entrySet()) {
                if (!first) {
                    b.append(',');
                }
                first = false;
                Canon.string(e.getKey(), b);
                b.append(':');
                node(e.getValue(), b);
            }
            b.append("}}");
        } else if (n.isList()) {
            b.append("\"a\":[");
            for (int i = 0; i < n.list().size(); i++) {
                if (i > 0) {
                    b.append(',');
                }
                node(n.list().get(i), b);
            }
            b.append("]}");
        } else {
            b.append("\"v\":");
            scalar(n.value(), b);
            b.append('}');
        }
    }

    static void scalar(Object v, StringBuilder b) {
        if (v == null) {
            b.append("null");
        } else if (v instanceof Double d) {
            b.append("{\"$f64\":\"").append(String.format("%016x", Double.doubleToRawLongBits(d))).append("\"}");
        } else if (v instanceof BigInteger g) {
            b.append("{\"$big\":\"").append(g).append("\"}");
        } else if (v instanceof Number || v instanceof Boolean) {
            b.append(v);
        } else {
            Canon.string(v.toString(), b);
        }
    }

    /** A Jackson tree as the proof's JSON (order kept; doubles as raw bits). */
    static void json(JsonNode n, StringBuilder b) {
        if (n.isObject()) {
            b.append('{');
            boolean first = true;
            Iterator<Map.Entry<String, JsonNode>> it = n.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                if (!first) {
                    b.append(',');
                }
                first = false;
                Canon.string(e.getKey(), b);
                b.append(':');
                json(e.getValue(), b);
            }
            b.append('}');
        } else if (n.isArray()) {
            b.append('[');
            for (int i = 0; i < n.size(); i++) {
                if (i > 0) {
                    b.append(',');
                }
                json(n.get(i), b);
            }
            b.append(']');
        } else if (n.isNull() || n.isMissingNode()) {
            b.append("null");
        } else if (n.isBoolean()) {
            b.append(n.booleanValue());
        } else if (n.isBigInteger()) {
            BigInteger g = n.bigIntegerValue();
            if (g.bitLength() < 64) {
                b.append(g);
            } else {
                b.append("{\"$big\":\"").append(g).append("\"}");
            }
        } else if (n.isIntegralNumber()) {
            b.append(n.longValue());
        } else if (n.isNumber()) {
            scalar(n.doubleValue(), b);
        } else {
            Canon.string(n.asText(), b);
        }
    }
}
