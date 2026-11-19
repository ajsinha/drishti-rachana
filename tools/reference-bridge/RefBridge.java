// Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
// Drishti Rachana. Proprietary: see LICENSE.
//
// The bootstrap reference program of the conformance suite (plan R0, H1): a "third-party runtime" in the sense of
// conformance/RUNNER.md section 5, written against Drishti's compute/drishti-rachana jars (the reference implementation
// until the code moves here at A10). It reads cases as JSON lines on stdin and answers with the actual document of each
// on stdout. It never reads case files and never compares. Retired when rachana-core runs the suite itself.
//
// Run:  java -cp <drishti jars and dependencies> tools/reference-bridge/RefBridge.java <spec formats.yaml>
// (tools/conformance/rachana_conformance.py does it: see tools/reference-bridge/README.md)
import com.ash.drishti.api.DataNode;
import com.ash.drishti.common.JsonCodec;
import com.ash.drishti.rachana.el.*;
import com.ash.drishti.rachana.format.Formats;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

public class RefBridge {
    static final ObjectMapper M = new ObjectMapper();
    static Formats formats;
    static final ElCompiler EL = new ElCompiler();

    public static void main(String[] a) throws Exception {
        formats = Formats.load(null, List.of(a));          // the Core table first, then the Drishti profile's names when given
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), false, StandardCharsets.UTF_8);
        in.readLine();                                                   // the driver's greeting
        out.println("{\"runtime\":\"jvm\",\"runtimeVersion\":\"bootstrap-2.9.11.i7a\",\"language\":\"1.0\",\"profiles\":[\"core\",\"drishti\"],\"kinds\":[\"expr\",\"expr-error\",\"format\",\"problem\"]}");
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
        } catch (com.ash.drishti.api.error.DrishtiException e) {
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
        String tone = c.has("tone") && !Values.masked(node) ? com.ash.drishti.rachana.format.Tones.resolve(c.get("tone").asText(), v) : null;
        sb.append("{\"text\":").append(str(text)).append(",\"tone\":").append(tone == null ? "null" : str(tone)).append('}');
    }

    /** The Sutra is parsed, then its expressions are checked: the problems in the order found. */
    static void problem(JsonNode c, StringBuilder sb) {
        List<com.ash.drishti.rachana.SutraProblem> ps = new ArrayList<>();
        String file = c.path("file").asText("case.sutra.yaml");
        try {
            var sutra = new com.ash.drishti.rachana.parse.SutraParser().parse(c.get("sutra").asText(), file, "conformance");
            ps.addAll(new com.ash.drishti.rachana.SutraExpressions(new ElCompiler()).check(sutra));
        } catch (com.ash.drishti.rachana.SutraException e) {
            ps.addAll(e.problems());
        }
        sb.append("{\"ok\":").append(ps.isEmpty()).append(",\"problems\":[");
        boolean f = true;
        for (var p : ps) {
            if (!f) { sb.append(','); } f = false;
            sb.append("{\"code\":").append(str(p.code().replaceFirst("^(DRS|DEL)-", "RCH-"))).append(",\"severity\":\"error\",\"message\":").append(str(p.message()))
                    .append(",\"range\":{\"start\":{\"line\":").append(p.location().line()).append(",\"column\":").append(p.location().column()).append("}}}");
        }
        sb.append("]}");
    }

    static String words(com.ash.drishti.api.error.DrishtiException e) {
        String m = e.getMessage(), c = e.errorCode() == null ? "" : e.errorCode().code();
        return m.startsWith(c + " ") ? m.substring(c.length() + 1) : m;
    }

    static String code(com.ash.drishti.api.error.DrishtiException e) {
        String c = e.errorCode() == null ? "" : e.errorCode().code();
        return c.replaceFirst("^(DRS|DEL)-", "RCH-");
    }

    // ---- context -------------------------------------------------------------------------------------------------

    static EvalContext context(JsonNode g) {
        DataNode root = node(g.has("record") ? g.get("record") : M.createObjectNode());
        EvalContext c = EvalContext.of(root, formats);
        if (g.has("today")) { c = c.withToday(LocalDate.parse(g.get("today").asText())); }
        Map<String, Object> ctl = new LinkedHashMap<>();
        if (g.has("ctl")) { g.get("ctl").fields().forEachRemaining(f -> ctl.put(f.getKey(), plain(f.getValue()))); c = c.withControls(ctl); }
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
