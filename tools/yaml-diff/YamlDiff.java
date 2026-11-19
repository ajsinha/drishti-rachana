// Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
// Drishti Rachana. Proprietary: see LICENSE.
//
// The YAML and JSON differential of plan A8a: Core's own reader (com.ash.rachana.parse.YamlReader, rachana-core) against
// the reference's PositionalYamlReader (Drishti's i7a jars, Jackson 2.21.4 on SnakeYAML 2.4), node for node with
// positions, issues and error texts; Core's Json against Jackson's ObjectMapper. Read-only over Drishti.
//
// Inputs: every file named on the list (one path per line), every multi-line string value inside a conformance case file
// that reads as YAML on either side, the synthetic edge inputs below, and deterministic mutations of every file (cut at a
// point, a character deleted or doubled, a tab put in) so that refusals are compared as well as trees.
//
// Run:  java -cp <rachana-core classes>:<drishti-rachana jar>:<jackson jars> tools/yaml-diff/YamlDiff.java
//            --files LIST [--cases DIR] [--mutations N] [--record OUT.jsonl] [--report OUT.txt]
// (tools/yaml-diff/yaml_diff.py builds the class path and the lists.)
import com.ash.drishti.rachana.parse.PNode;
import com.ash.drishti.rachana.parse.PositionalYamlReader;
import com.ash.rachana.parse.Json;
import com.ash.rachana.parse.YamlNode;
import com.ash.rachana.parse.YamlReader;
import com.ash.rachana.parse.YamlSyntaxException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class YamlDiff {
    static final PositionalYamlReader REF = new PositionalYamlReader();
    static final YamlReader CORE = new YamlReader();
    static final ObjectMapper JSON = new ObjectMapper().configure(DeserializationFeature.USE_LONG_FOR_INTS, true);

    static int compared, same, differ, refusedOnly, treesCompared, errorsCompared;
    static final Map<String, Integer> classes = new TreeMap<>();
    static final List<String> report = new ArrayList<>();

    public static void main(String[] a) throws Exception {
        Map<String, String> opt = new HashMap<>();
        for (int i = 0; i + 1 < a.length; i += 2) opt.put(a[i], a[i + 1]);
        List<String[]> inputs = new ArrayList<>();                          // {label, text}
        if (opt.containsKey("--files")) {
            for (String f : Files.readAllLines(Path.of(opt.get("--files")))) {
                if (f.isBlank()) continue;
                inputs.add(new String[] {f, Files.readString(Path.of(f), StandardCharsets.UTF_8)});
            }
        }
        int files = inputs.size();
        if (opt.containsKey("--cases")) embedded(Path.of(opt.get("--cases")), inputs);
        int embedded = inputs.size() - files;
        List<String[]> edges = edges();
        int mutations = Integer.parseInt(opt.getOrDefault("--mutations", "0"));
        List<String[]> mutated = new ArrayList<>();
        Random rnd = new Random(20261119L);
        for (int k = 0; k < files && mutations > 0; k++) {
            String t = inputs.get(k)[1];
            if (t.isEmpty()) continue;
            for (int m = 0; m < mutations; m++) mutated.add(new String[] {inputs.get(k)[0] + "~m" + m, mutate(t, rnd)});
        }
        for (String[] in : inputs) one(in[0], in[1]);
        for (String[] in : edges) one(in[0], in[1]);
        for (String[] in : mutated) one(in[0], in[1]);
        int jsonCases = json();
        if (opt.containsKey("--record")) {
            // the committed vectors: this repository's own inputs only (edge inputs, the case files' texts and their mutations)
            List<String[]> own = new ArrayList<>(edges);
            Random r2 = new Random(7L);
            for (int k = files; k < files + embedded; k++) {
                own.add(inputs.get(k));
                for (int m = 0; m < 3; m++) own.add(new String[] {inputs.get(k)[0] + "~m" + m, mutate(inputs.get(k)[1], r2)});
            }
            record(Path.of(opt.get("--record")), own);
        }
        String summary = String.format("files %d, embedded %d, edge %d, mutations %d: compared %d, same %d, differ %d (refused constructs only %d); "
                + "trees %d, refusals %d; json %d%nclasses %s", files, embedded, edges.size(), mutated.size(), compared, same, differ, refusedOnly,
                treesCompared, errorsCompared, jsonCases, classes);
        report.add(0, summary);
        if (opt.containsKey("--report")) Files.write(Path.of(opt.get("--report")), report);
        System.out.println(summary);
        for (int i = 1; i < Math.min(report.size(), 40); i++) System.out.println(report.get(i));
        System.exit(differ - refusedOnly == 0 ? 0 : 1);
    }

    // ---- one comparison ----

    static void one(String label, String text) {
        compared++;
        String ref = ref(text), core = core(text);
        if (ref.equals(core)) {
            same++;
            if (ref.startsWith("{\"error\"")) errorsCompared++; else treesCompared++;
            return;
        }
        // Core refuses anchors and aliases with an issue the reference does not raise: the only accepted difference.
        String coreLess = core.replaceAll(",?\\{\"message\":\"YAML (anchor|alias) [^\"]*\"[^}]*\\}", "").replace("[,", "[");
        if (ref.equals(coreLess)) {
            differ++;
            refusedOnly++;
            classes.merge("refused anchor or alias", 1, Integer::sum);
            report.add("REFUSED anchor or alias " + label);
            return;
        }
        differ++;
        String cls = ref.startsWith("{\"error\"") && core.startsWith("{\"error\"") ? "error text" : ref.startsWith("{\"error\"") ? "reference refuses"
                : core.startsWith("{\"error\"") ? "core refuses" : "tree";
        classes.merge(cls, 1, Integer::sum);
        report.add("DIFF " + cls + " " + label + "\n  input " + Json.quote(text.length() > 300 ? text.substring(0, 300) + "..." : text)
                + "\n  ref   " + clip(ref) + "\n  core  " + clip(core) + "\n  at    " + firstDiff(ref, core));
    }

    static String clip(String s) {
        return s.length() > 600 ? s.substring(0, 600) + "..." : s;
    }

    static String firstDiff(String a, String b) {
        int i = 0;
        while (i < a.length() && i < b.length() && a.charAt(i) == b.charAt(i)) i++;
        return "char " + i + ": ref ..." + a.substring(Math.max(0, i - 40), Math.min(a.length(), i + 60)) + " | core ..."
                + b.substring(Math.max(0, i - 40), Math.min(b.length(), i + 60));
    }

    static String ref(String text) {
        List<PositionalYamlReader.Issue> issues = new ArrayList<>();
        try {
            PNode n = REF.read(text, issues);
            StringBuilder sb = new StringBuilder("{\"tree\":");
            dump(n.value(), n.line(), n.column(), sb);
            sb.append(",\"issues\":[");
            for (int i = 0; i < issues.size(); i++) {
                var x = issues.get(i);
                if (i > 0) sb.append(',');
                sb.append("{\"message\":").append(Json.quote(x.message())).append(",\"line\":").append(x.line()).append(",\"column\":").append(x.column()).append('}');
            }
            return sb.append("]}").toString();
        } catch (JsonProcessingException e) {
            var l = e.getLocation();
            return error(e.getOriginalMessage(), l == null ? 0 : l.getLineNr(), l == null ? 0 : l.getColumnNr());
        } catch (Throwable e) {
            return "{\"crash\":" + Json.quote(e.getClass().getName() + ": " + e.getMessage()) + "}";
        }
    }

    static String core(String text) {
        List<YamlReader.Issue> issues = new ArrayList<>();
        try {
            YamlNode n = CORE.read(text, issues);
            StringBuilder sb = new StringBuilder("{\"tree\":");
            dump(n.value(), n.line(), n.column(), sb);
            sb.append(",\"issues\":[");
            for (int i = 0; i < issues.size(); i++) {
                var x = issues.get(i);
                if (i > 0) sb.append(',');
                sb.append("{\"message\":").append(Json.quote(x.message())).append(",\"line\":").append(x.line()).append(",\"column\":").append(x.column()).append('}');
            }
            return sb.append("]}").toString();
        } catch (YamlSyntaxException e) {
            return error(e.getMessage(), e.line(), e.column());
        } catch (Throwable e) {
            return "{\"crash\":" + Json.quote(e.getClass().getName() + ": " + e.getMessage()) + "}";
        }
    }

    static String error(String message, int line, int column) {
        return "{\"error\":{\"message\":" + Json.quote(String.valueOf(message)) + ",\"line\":" + line + ",\"column\":" + column + "}}";
    }

    /** A node as canonical text: type, value, position; map keys in written order. */
    static void dump(Object v, int line, int col, StringBuilder sb) {
        sb.append("{\"at\":[").append(line).append(',').append(col).append("],");
        if (v instanceof Map<?, ?> m) {
            sb.append("\"map\":[");
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append('[').append(Json.quote(String.valueOf(e.getKey()))).append(',');
                child(e.getValue(), sb);
                sb.append(']');
            }
            sb.append(']');
        } else if (v instanceof List<?> l) {
            sb.append("\"list\":[");
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) sb.append(',');
                child(l.get(i), sb);
            }
            sb.append(']');
        } else if (v == null) {
            sb.append("\"null\":true");
        } else if (v instanceof String s) {
            sb.append("\"str\":").append(Json.quote(s));
        } else if (v instanceof Long n) {
            sb.append("\"long\":").append(n);
        } else if (v instanceof BigInteger n) {
            sb.append("\"big\":").append(n);
        } else if (v instanceof Double d) {
            sb.append("\"double\":\"").append(d).append('"');
        } else if (v instanceof Boolean b) {
            sb.append("\"bool\":").append(b);
        } else {
            sb.append("\"other\":").append(Json.quote(v.getClass().getName() + ":" + v));
        }
        sb.append('}');
    }

    static void child(Object node, StringBuilder sb) {
        if (node instanceof PNode p) dump(p.value(), p.line(), p.column(), sb);
        else if (node instanceof YamlNode y) dump(y.value(), y.line(), y.column(), sb);
        else sb.append("{\"bad\":").append(Json.quote(String.valueOf(node))).append('}');
    }

    // ---- inputs ----

    /** Multi-line string values of the conformance case files: Sutras, operation texts, fragments. */
    static void embedded(Path dir, List<String[]> into) throws IOException {
        ObjectMapper yaml = new ObjectMapper(new com.fasterxml.jackson.dataformat.yaml.YAMLFactory());
        List<Path> files;
        try (var s = Files.walk(dir)) {
            files = s.filter(p -> p.toString().endsWith(".yaml")).sorted().toList();
        }
        for (Path f : files) {
            JsonNode root = yaml.readTree(f.toFile());
            Set<String> seen = new HashSet<>();
            walk(root, f.toString(), into, seen);
        }
    }

    static void walk(JsonNode n, String label, List<String[]> into, Set<String> seen) {
        if (n == null) return;
        if (n.isTextual()) {
            String t = n.asText();
            if (t.contains("\n") && t.contains(":") && seen.add(t)) into.add(new String[] {label + "#" + into.size(), t});
        } else {
            for (JsonNode c : n) walk(c, label, into, seen);
        }
    }

    static String mutate(String t, Random r) {
        int at = r.nextInt(t.length() + 1);
        return switch (r.nextInt(6)) {
            case 0 -> t.substring(0, at);                                           // cut
            case 1 -> at < t.length() ? t.substring(0, at) + t.substring(at + 1) : t; // delete one
            case 2 -> at < t.length() ? t.substring(0, at) + t.charAt(at) + t.substring(at) : t; // double one
            case 3 -> t.substring(0, at) + "\t" + t.substring(at);                  // a tab
            case 4 -> t.substring(0, at) + "\n" + t.substring(at);                  // a line break
            default -> t.substring(0, at) + "  " + t.substring(at);                  // indentation
        };
    }

    /** Synthetic inputs over every corner of the reader: types, styles, positions, refusals. */
    static List<String[]> edges() {
        String[] in = {
            "", "# only a comment\n", "---\n", "---\n...\n", "a: 1\n", "a: 1\n---\nb: 2\n", "a: 1\n---\n", "- a\n- b\n", "plain\n", "'q'\n",
            // booleans and nulls (YAML 1.1)
            "a: yes\nb: No\nc: ON\nd: off\ne: true\nf: False\ng: y\nh: n\ni: YES\nj: tRue\n",
            "a: ~\nb: null\nc: Null\nd: NULL\ne:\nf: ''\ng: nULL\n",
            // integers
            "a: 0\nb: -0\nc: +0\nd: 12\ne: -12\nf: +12\ng: 1_000\nh: 0b1010\ni: -0b11\nj: 017\nk: 0_17\nl: 0x1F\nm: -0xff\nn: 0x_1F\no: 09\np: 1:30\nq: 190:20:30\n",
            "a: 2147483647\nb: 2147483648\nc: 9223372036854775807\nd: 9223372036854775808\ne: -9223372036854775808\nf: -9223372036854775809\ng: 123456789012345678901234567890\nh: +9223372036854775807\n",
            "a: 0x7FFFFFFFFFFFFFF\nb: 0x7FFFFFFFFFFFFFFF\nc: 0x0000000000000001\nd: 0b111111111111111111111111111111111111111111111111111111111111111\ne: 0b1111111111111111111111111111111111111111111111111111111111111111\nf: 0777777777777777777777\ng: 01777777777777777777777\n",
            // floats
            "a: 1.0\nb: -1.5\nc: .5\nd: 1.\ne: 1e5\nf: 1E-5\ng: 1_000.5\nh: +1.5\ni: 6.02e+23\nj: .inf\nk: -.Inf\nl: .nan\nm: 1:30.5\nn: 1.2.3\no: 0.1\np: 5e-324\nq: 1e400\n",
            "a: ._\n", "a: .inf\n", "a: 1:20.5\n", "a: .NaN\n",
            // quoted and block scalars
            "a: \"x\\ty\\n\\u00e9\\x41\\U0001F600 \\\"q\\\" \\\\ \\/\"\nb: 'it''s'\nc: \"multi\n  line\n\n  folded\"\n",
            "a: |\n  one\n  two\nb: >\n  one\n  two\n\n  three\nc: |-\n  x\nd: |+\n  y\n\ne: >-\n  folded\n   more\n  back\n",
            "a: |2\n   indented\n  b\n", "a: >\n\n  lead\n", "k: |\n\n", "k: >1\n  x\n",
            // plain scalars
            "a: x y z\nb: multi\n  line plain\nc: http://x.y/z?a=b\nd: a:b\ne: -x\nf: ?x\ng: x#y\nh: x #c\ni: 2026-11-19\nj: 2026-11-19T10:00:00Z\nk: '2026-01-01'\n",
            // flow collections
            "a: {x: 1, y: [1, 2, {z: 3}], 'q': \"r\"}\nb: [a, b, ]\nc: {}\nd: []\ne: [a: 1, b]\nf: {a, b: 2}\ng: [? x : y]\n",
            "a: { x: 1,\n  y: 2 }\nb: [\n  1,\n  2\n]\n",
            // keys
            "1: a\ntrue: b\nnull: c\n~: d\n1.5: e\n'x y': f\n\"z\": g\n? k\n: v\n? [a]\n: v\n",
            "? x\n", "a:\n  b:\n    c: 1\n  d: 2\ne: 3\n",
            "a:\n- 1\n- 2\nb: x\n", "- - a\n  - b\n- c: 1\n  d: 2\n-\n- - \n",
            // duplicates, tags, anchors
            "a: 1\nb: 2\na: 3\n", "x: { id: a, id: b }\n", "a: !panel { id: a }\n", "a: !!str 12\nb: !!int '12'\nc: !!float '1'\nd: !!bool yes\ne: !!null x\nf: !!binary aGk=\ng: !!map {a: 1}\nh: ! x\ni: !<tag:yaml.org,2002:str> s\nj: !!int x\nk: !!bool y\nl: !!null ''\nm: !foo\n",
            "%YAML 1.1\n---\na: 1\n", "%TAG !e! tag:example.com,2000:\n---\na: !e!foo 1\n", "%FOO bar\n---\na: 1\n",
            "a: &x 1\nb: *x\n", "&a k: v\n", "a: &anc\n  b: 1\n", "- *x\n",
            // positions: unicode, BOM, CRLF, long lines
            "\uFEFFa: 1\n", "a: 1\r\nb:\r\n  c: 2\r\n", "a: 1\rb: 2\r", "é: ü\nb: \"\uD83D\uDE00x\"\nc: \uD83D\uDE00\n", "a: x\u2028b: y\n", "a: x\u0085b: y\n",
            "k: " + "x".repeat(2000) + "\n", "k:\n" + "  - " + "y".repeat(50) + "\n".repeat(1) + ("  - v\n").repeat(400),
            // errors
            "rachana: 1\nsutra: [", "a: [1, 2", "a: {x: 1", "a: 'open", "a: \"open", "a: b: c\n", "a:\n\tb: 1\n", "\tx\n", "- a\nb: 1\n",
            "a: 1\n - b\n", "a: [1 2]\n", "a: {x 1}\n", "a: \"\\q\"\n", "a: \"\\xZZ\"\n", "a: @x\n", "a: `x\n", "a: %x\n", "a: |0\n x\n",
            "a: |x\n", "a: !<x\n", "a: *\n", "a: &\n", "{a: 1}: b\n", "[a]: b\n", "a: 1\n... x\n", "a: \"x\n---\ny\"\n", "a: 'x\n...\n'\n",
            "a:\n  b: 1\n c: 2\n", "  a: 1\nb: 2\n", "a: x\n  b: y\n", "- a\n  - b\n", "key with no colon\nb: 1\n", "a: \u0001\n", "a: \"\\uD800\"\n",
            "%YAML 2.0\n---\na: 1\n", "%YAML 1.1\n%YAML 1.1\n---\na: 1\n", "a: !x!y 1\n", "--- a\n--- b\n", "a: 1\n---\nb: [\n",
            "x" + "\n".repeat(3) + "y: [", "a: " + "[".repeat(1001) + "]".repeat(1001) + "\n", "a: " + "[".repeat(999) + "]".repeat(999) + "\n",
            "a: " + "9".repeat(1001) + "\n", "a: 1" + "_".repeat(10) + "\n", "a: " + "1".repeat(1030) + "\n",
            " ".repeat(1000) + "a: [\n", "a: \"" + "z".repeat(1500) + "\n", "x: " + "w".repeat(1020) + "\ny: {\n",
        };
        List<String[]> out = new ArrayList<>();
        for (int i = 0; i < in.length; i++) out.add(new String[] {"edge#" + i, in[i]});
        return out;
    }

    /** The edge inputs with the reference's answer, as the committed vectors of Core's unit test. */
    static void record(Path to, List<String[]> edges) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String[] e : edges) lines.add(ascii("{\"input\":" + Json.quote(e[1]) + ",\"expect\":" + ref(e[1]) + "}"));
        System.out.println("recorded " + lines.size() + " vectors to " + to);
        Files.write(to, lines, StandardCharsets.UTF_8);
    }

    /** Non-ASCII characters (only ever inside JSON strings here) as escapes: the file stays ASCII, lone surrogates included. */
    static String ascii(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x7F) sb.append(c); else sb.append(String.format("\\u%04x", (int) c));
        }
        return sb.toString();
    }

    // ---- JSON ----

    static int json() throws Exception {
        String[] in = {"{}", "[]", "1", "-0", "12.5", "1e3", "-1.5E-3", "9223372036854775807", "9223372036854775808", "-9223372036854775808",
            "123456789012345678901234567890", "\"x\\u00e9\\n\\t\\\"\\\\\\/\"", "true", "false", "null", "{\"a\":1,\"b\":[1,2,{\"c\":null}],\"a\":2}",
            " [ 1 , 2 ] ", "[1] trailing", "{\"op\":\"remove\",\"panel\":\"legs\"}", "\"\\ud83d\\ude00\"", "0.1", "5e-324", "1.7976931348623157E308"};
        int n = 0;
        for (String s : in) {
            n++;
            String a, b;
            try { a = Json.write(Json.read(s)); } catch (Json.JsonException e) { a = "error"; }
            try { b = JSON.writeValueAsString(plain(JSON.readTree(s))); } catch (JsonProcessingException e) { b = "error"; }
            if (!a.equals(b)) {
                differ++;
                classes.merge("json", 1, Integer::sum);
                report.add("DIFF json " + s + "\n  ref  " + b + "\n  core " + a);
            }
        }
        Object[] values = {"tab\there", "ctl\u0001\u001f\u007f", "quote\" back\\ slash/", "é😀", 1.0, 0.1, 1e21, 1e-7, 5e-324, Double.NaN,
            Double.POSITIVE_INFINITY, -0.0, 12L, new BigInteger("123456789012345678901234567890"), true, null,
            new LinkedHashMap<>(Map.of("k", List.of(1L, "x"))), List.of()};
        for (Object v : values) {
            n++;
            String a = Json.write(v), b = JSON.writeValueAsString(v);
            if (!a.equals(b)) {
                differ++;
                classes.merge("json", 1, Integer::sum);
                report.add("DIFF json write " + v + "\n  ref  " + b + "\n  core " + a);
            }
        }
        return n;
    }

    static Object plain(JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode()) return null;
        if (n.isObject()) {
            Map<String, Object> m = new LinkedHashMap<>();
            n.fields().forEachRemaining(e -> m.put(e.getKey(), plain(e.getValue())));
            return m;
        }
        if (n.isArray()) {
            List<Object> l = new ArrayList<>();
            n.forEach(x -> l.add(plain(x)));
            return l;
        }
        if (n.isBigInteger()) return n.bigIntegerValue();
        if (n.isIntegralNumber()) return n.asLong();
        if (n.isNumber()) return n.asDouble();
        if (n.isBoolean()) return n.asBoolean();
        return n.asText();
    }
}
