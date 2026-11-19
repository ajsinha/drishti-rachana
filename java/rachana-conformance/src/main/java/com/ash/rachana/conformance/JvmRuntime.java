/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.conformance;

import com.ash.rachana.hostcompat.DataNode;
import com.ash.rachana.el.*;
import com.ash.rachana.format.Formats;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

/**
 * The JVM runtime program of the conformance suite (conformance/RUNNER.md section 5) over Rachana's own compiler: cases as JSON lines on
 * standard input, the actual document of each on standard output. It is the reference bridge (tools/reference-bridge/RefBridge.java)
 * moved onto this repository's code with its logic unchanged, so the two answer byte for byte alike (split step A10). Arguments:
 * {@code [--profiles a,b] <formats file>...}: the Core format table first, then a profile's names when given. Run by
 * {@code tools/conformance/rachana_conformance.py --jvm}.
 */
public final class JvmRuntime {
    private JvmRuntime() { }

    static final String VERSION = "0.10.0-SNAPSHOT";
    static final ObjectMapper M = new ObjectMapper();
    static Formats formats;
    static final ElCompiler EL = new ElCompiler();

    public static void main(String[] a) throws Exception {
        List<String> files = new ArrayList<>(List.of(a));
        String profiles = "core";
        if (files.size() >= 2 && files.get(0).equals("--profiles")) {
            profiles = files.get(1);
            files = files.subList(2, files.size());
        }
        formats = Formats.load(null, files);               // the Core table first, then a profile's names when given
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), false, StandardCharsets.UTF_8);
        in.readLine();                                                   // the driver's greeting
        out.println("{\"runtime\":\"jvm\",\"runtimeVersion\":\"rachana-" + VERSION + "\",\"language\":\"1.0\",\"profiles\":" + jsonList(profiles) + ",\"kinds\":[\"expr\",\"expr-error\",\"format\",\"problem\",\"edit\"]}");
        out.flush();
        String line;
        while ((line = in.readLine()) != null) {
            if (line.isBlank()) { continue; }
            JsonNode c = M.readTree(line);
            StringBuilder sb = new StringBuilder("{\"case\":").append(str(c.get("case").asText()));
            try {
                String kind = c.get("kind").asText();
                sb.append(",\"actual\":");
                switch (kind) {
                    case "expr" -> expr(c, sb);
                    case "expr-error" -> exprError(c, sb);
                    case "format" -> format(c, sb);
                    case "problem" -> problem(c, sb);
                    case "edit" -> edit(c, sb);
                    default -> throw new IllegalStateException("kind not implemented: " + kind);
                }
            } catch (Throwable t) {
                sb.setLength(sb.indexOf(",\"actual\":") < 0 ? sb.length() : sb.indexOf(",\"actual\":"));
                sb.append(",\"error\":").append(str(t.getClass().getSimpleName() + ": " + t.getMessage()));
            }
            out.println(sb.append('}'));
            out.flush();
        }
    }

    // ---- expr ----------------------------------------------------------------------------------------------------

    static void expr(JsonNode c, StringBuilder sb) {
        JsonNode given = c.path("given");
        EvalContext ctx = context(given);
        Object v;
        List<String> reads = new ArrayList<>();
        try {
            if (c.has("template")) {
                Template t = EL.template(c.get("template").asText());
                v = t.render(ctx);
                LinkedHashSet<String> s = new LinkedHashSet<>();
                t.paths(s::add);
                reads.addAll(s);
            } else {
                Expr e = EL.compile(c.get("expr").asText());
                v = e.eval(ctx);
                LinkedHashSet<String> s = new LinkedHashSet<>();
                e.paths(s::add);
                reads.addAll(s);
            }
        } catch (com.ash.rachana.RachanaException e) {
            sb.append("{\"error\":{\"code\":").append(str(code(e))).append(",\"message\":").append(str(words(e))).append("}}");
            return;
        }
        Object s = Values.simplify(v);
        sb.append("{\"value\":");
        value(s, sb);
        sb.append(",\"type\":").append(str(type(s)));
        sb.append(",\"text\":").append(str(Values.masked(s) ? "•••" : Values.text(s)));
        sb.append(",\"reads\":[");
        boolean first = true;
        for (String r : reads) { if (!first) { sb.append(','); } first = false; sb.append(str(r.startsWith("$") ? r : "$." + r)); }
        sb.append("]}");
    }

    static void exprError(JsonNode c, StringBuilder sb) {
        String src = c.has("template") ? c.get("template").asText() : c.get("expr").asText();
        int depth = c.path("given").path("limits").path("depth").asInt(200), len = c.path("given").path("limits").path("length").asInt(10_000);
        ElCompiler comp = new ElCompiler(1000, new ElLimits(depth, len));
        try {
            if (c.has("template")) { comp.template(src); } else { comp.compile(src); }
            sb.append("{\"problems\":[]}");
        } catch (ElException e) {
            int at = Math.max(0, e.position());
            sb.append("{\"problems\":[{\"code\":").append(str(code(e))).append(",\"severity\":\"error\",\"message\":").append(str(words(e)))
                    .append(",\"expr\":{\"source\":").append(str(src)).append(",\"start\":").append(at).append(",\"end\":").append(Math.min(src.length(), at + 1)).append("}}]}");
        }
    }

    static void format(JsonNode c, StringBuilder sb) {
        Object v = plain(c.path("value"));
        Object node = v instanceof DataNode ? v : (v == null ? null : v);
        String text = c.has("format") ? formats.format(c.get("format").asText(), node) : Values.text(node);
        String tone = c.has("tone") && !Values.masked(node) ? com.ash.rachana.format.Tones.resolve(c.get("tone").asText(), v) : null;
        sb.append("{\"text\":").append(str(text)).append(",\"tone\":").append(tone == null ? "null" : str(tone)).append('}');
    }

    /** The Sutra is parsed, then its expressions are checked: the problems in the order found. */
    static void problem(JsonNode c, StringBuilder sb) {
        List<com.ash.rachana.SutraProblem> ps = new ArrayList<>();
        String file = c.path("file").asText("case.sutra.yaml");
        try {
            var sutra = new com.ash.rachana.parse.SutraParser().parse(c.get("sutra").asText(), file, "conformance");
            ps.addAll(new com.ash.rachana.SutraExpressions(new ElCompiler()).check(sutra));
        } catch (com.ash.rachana.SutraException e) {
            ps.addAll(e.problems());
        }
        sb.append("{\"ok\":").append(ps.isEmpty()).append(",\"problems\":[");
        boolean f = true;
        for (var p : ps) {
            if (!f) { sb.append(','); } f = false;
            sb.append("{\"code\":").append(str(p.code())).append(",\"severity\":\"error\",\"message\":").append(str(p.message()))
                    .append(",\"range\":{\"start\":{\"line\":").append(p.location().line()).append(",\"column\":").append(p.location().column()).append("}}}");
        }
        sb.append("]}");
    }

    /**
     * FORMAT 5.11: one editing operation of the reference (design/ops), its name in kebab case in the case ({@code set-option} is the
     * reference's {@code setOption}), its other fields as the reference names them. Applied: the new text and no problems. Refused: the
     * text unchanged and the reference's problem, with its line when it has one (plan H2).
     */
    static void edit(JsonNode c, StringBuilder sb) {
        String sutra = c.get("sutra").asText();
        com.fasterxml.jackson.databind.node.ObjectNode op = ((com.fasterxml.jackson.databind.node.ObjectNode) c.get("operation")).deepCopy();
        StringBuilder name = new StringBuilder();
        boolean up = false;
        for (char ch : op.get("op").asText().toCharArray()) { if (ch == '-') { up = true; } else { name.append(up ? Character.toUpperCase(ch) : ch); up = false; } }
        op.put("op", name.toString());
        String text = sutra;
        StringBuilder ps = new StringBuilder();
        try {
            var r = new com.ash.rachana.design.ops.OpApplier().apply(sutra, com.ash.rachana.design.ops.Ops.parse(M.createArrayNode().add(op)));
            text = r.yaml();
            for (var p : r.problems()) { editProblem(ps, p.code(), p.message(), p.line()); }
        } catch (com.ash.rachana.design.ops.Ops.FormatException e) {
            editProblem(ps, "RCH-5020", e.getMessage(), 0);
        }
        sb.append("{\"text\":").append(str(text)).append(",\"problems\":[").append(ps).append("]}");
    }

    static void editProblem(StringBuilder ps, String code, String message, int line) {
        if (ps.length() > 0) { ps.append(','); }
        ps.append("{\"code\":").append(str(code)).append(",\"severity\":\"error\",\"message\":").append(str(message));
        if (line > 0) { ps.append(",\"range\":{\"start\":{\"line\":").append(line).append("}}"); }
        ps.append('}');
    }

    static String words(com.ash.rachana.RachanaException e) {
        String m = e.getMessage(), c = e.code() == null ? "" : e.code().code();
        return m.startsWith(c + " ") ? m.substring(c.length() + 1) : m;
    }

    static String code(com.ash.rachana.RachanaException e) {
        String c = e.code() == null ? "" : e.code().code();
        return c;
    }

    // ---- context -------------------------------------------------------------------------------------------------

    static EvalContext context(JsonNode g) {
        DataNode root = node(g.has("record") ? g.get("record") : M.createObjectNode());
        EvalContext c = EvalContext.of(root, formats);
        if (g.has("today")) { c = c.withToday(LocalDate.parse(g.get("today").asText())); }
        Map<String, Object> ctl = new LinkedHashMap<>();
        // a multi-valued control (multi-select, the choices of oneOf/within) reaches the evaluator as a document list, as the host's
        // ControlValues hands it over (H2: ControlsTest passes DataNode.of(List.of(...))); a scalar as itself
        if (g.has("ctl")) { g.get("ctl").fields().forEachRemaining(f -> { Object v = plain(f.getValue()); ctl.put(f.getKey(), v instanceof List<?> ? DataNode.of(v) : v); }); c = c.withControls(ctl); }
        Map<String, DataNode> events = new LinkedHashMap<>();
        if (g.has("events")) { g.get("events").fields().forEachRemaining(f -> events.put(f.getKey(), node(f.getValue()))); }
        Object src = g.has("src") ? node(g.get("src")) : null;
        if (!events.isEmpty() || src != null) { c = c.withScope(new EvalContext.Scope(events, null, src)); }
        List<Object> chain = new ArrayList<>();
        if (g.has("parents")) { List<Object> ps = new ArrayList<>(); g.get("parents").forEach(p -> ps.add(node(p))); Collections.reverse(ps); chain.addAll(ps); }
        if (g.has("row")) { chain.add(node(g.get("row"))); }
        if (!chain.isEmpty()) {
            EvalContext base = c;
            int idx = g.path("index").asInt(-1);
            c = new EvalContext(base.root(), base.row(), idx, base.formats(), base.ctl(), base.today(), base.scope(), base.locals()).withRows(chain);
        }
        return c;
    }

    static DataNode node(JsonNode n) { return DataNode.of(plain(n)); }

    /** JSON to Java values; the tags of FORMAT 4 become the reference's own values. */
    static Object plain(JsonNode n) {
        if (n.isNull()) { return null; }
        if (n.isBoolean()) { return n.booleanValue(); }
        if (n.isIntegralNumber()) { return n.longValue(); }
        if (n.isNumber()) { return n.doubleValue(); }
        if (n.isTextual()) { return n.textValue(); }
        if (n.isArray()) { List<Object> l = new ArrayList<>(); n.forEach(x -> l.add(plain(x))); return l; }
        if (n.size() == 1 && n.has("$masked")) { return DataNode.masked(); }
        if (n.size() == 1 && n.has("$dec")) { return new BigDecimal(n.get("$dec").asText()); }
        if (n.size() == 1 && n.has("$num")) { return switch (n.get("$num").asText()) { case "NaN" -> Double.NaN; case "Infinity" -> Double.POSITIVE_INFINITY; default -> Double.NEGATIVE_INFINITY; }; }
        Map<String, Object> m = new LinkedHashMap<>();
        n.fields().forEachRemaining(f -> m.put(f.getKey(), plain(f.getValue())));
        return m;
    }

    // ---- results -------------------------------------------------------------------------------------------------

    static String type(Object s) {
        if (s == null) { return "empty"; }
        if (Values.masked(s)) { return "masked"; }
        if (s instanceof Boolean) { return "boolean"; }
        if (s instanceof BigDecimal) { return "decimal"; }
        if (s instanceof Number) { return "number"; }
        if (s instanceof String) { return "text"; }
        if (s instanceof Link) { return "link"; }
        if (s instanceof DataNode d) { return d.type().toString().equals("ARRAY") ? "list" : "object"; }
        if (s instanceof Collection) { return "list"; }
        if (s instanceof Map) { return "object"; }
        return "text";
    }

    static void value(Object s, StringBuilder sb) {
        if (s == null) { sb.append("null"); }
        else if (Values.masked(s)) { sb.append("{\"$masked\":true}"); }
        else if (s instanceof Boolean b) { sb.append(b); }
        else if (s instanceof BigDecimal d) { decimal(d, sb); }
        else if (s instanceof Double d) { number(d, sb); }
        else if (s instanceof Number n) { sb.append(n.longValue()); }
        else if (s instanceof String t) { sb.append(str(t)); }
        else if (s instanceof Link l) { sb.append("{\"$link\":{\"id\":").append(str(l.id())).append(l.kind() == null ? "" : ",\"kind\":" + str(l.kind())).append(l.label() == null ? "" : ",\"label\":" + str(l.label())).append("}}"); }
        else if (s instanceof DataNode d) { value(Values.simplify(d.unwrap()), sb); }
        else if (s instanceof Map<?, ?> m) {
            sb.append('{'); boolean f = true;
            for (Map.Entry<?, ?> e : m.entrySet()) { if (!f) { sb.append(','); } f = false; sb.append(str(String.valueOf(e.getKey()))).append(':'); value(Values.simplify(e.getValue()), sb); }
            sb.append('}');
        } else if (s instanceof Collection<?> l) {
            sb.append('['); boolean f = true;
            for (Object e : l) { if (!f) { sb.append(','); } f = false; value(Values.simplify(e), sb); }
            sb.append(']');
        } else { sb.append(str(s.toString())); }
    }

    /** FORMAT 9, N1 to N3 and N5. */
    static void number(double d, StringBuilder sb) {
        if (Double.isNaN(d)) { sb.append("{\"$num\":\"NaN\"}"); }
        else if (Double.isInfinite(d)) { sb.append("{\"$num\":\"").append(d > 0 ? "Infinity" : "-Infinity").append("\"}"); }
        else if (d == Math.rint(d) && Math.abs(d) < 1e15) { sb.append((long) d); }
        else { sb.append(Double.toString(d)); }
    }

    /** N4: plain digits, trailing zeros of the fraction removed; $dec when a double cannot hold it. */
    static void decimal(BigDecimal b, StringBuilder sb) {
        BigDecimal s = b.stripTrailingZeros();
        String plain = s.scale() < 0 ? s.setScale(0).toPlainString() : s.toPlainString();
        double d = b.doubleValue();
        if (!Double.isInfinite(d) && new BigDecimal(Double.toString(d)).compareTo(b) == 0) { number(d, sb); }
        else { sb.append("{\"$dec\":").append(str(plain)).append('}'); }
    }

    static String jsonList(String commaSeparated) {
        StringBuilder b = new StringBuilder("[");
        for (String p : commaSeparated.split(",")) { if (b.length() > 1) { b.append(','); } b.append(str(p.strip())); }
        return b.append(']').toString();
    }

    static String str(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\b' -> b.append("\\b");
                case '\t' -> b.append("\\t");
                case '\n' -> b.append("\\n");
                case '\f' -> b.append("\\f");
                case '\r' -> b.append("\\r");
                default -> {
                    boolean lone = Character.isSurrogate(ch) && !(Character.isHighSurrogate(ch) && i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1)))
                            && !(Character.isLowSurrogate(ch) && i > 0 && Character.isHighSurrogate(s.charAt(i - 1)));
                    if (ch < 0x20 || ch == 0x7f || lone) { b.append(String.format("\\u%04x", (int) ch)); } else { b.append(ch); }
                }
            }
        }
        return b.append('"').toString();
    }
}
