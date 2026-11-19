/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.conformance;

import com.ash.rachana.api.Node;
import com.ash.rachana.el.*;
import com.ash.rachana.format.Formats;
import com.ash.rachana.parse.Json;
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
            Map<String, Object> c = obj(Json.read(line));
            StringBuilder sb = new StringBuilder("{\"case\":").append(str(text(c.get("case"))));
            try {
                String kind = text(c.get("kind"));
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

    static void expr(Map<String, Object> c, StringBuilder sb) {
        Map<String, Object> given = obj(c.get("given"));
        EvalContext ctx = context(given);
        Object v;
        List<String> reads = new ArrayList<>();
        try {
            if (c.containsKey("template")) {
                Template t = EL.template(text(c.get("template")));
                v = t.render(ctx);
                LinkedHashSet<String> s = new LinkedHashSet<>();
                t.paths(s::add);
                reads.addAll(s);
            } else {
                Expr e = EL.compile(text(c.get("expr")));
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

    static void exprError(Map<String, Object> c, StringBuilder sb) {
        String src = c.containsKey("template") ? text(c.get("template")) : text(c.get("expr"));
        Map<String, Object> limits = obj(obj(c.get("given")).get("limits"));
        int depth = whole(limits.get("depth"), 200), len = whole(limits.get("length"), 10_000);
        ElCompiler comp = new ElCompiler(1000, new ElLimits(depth, len));
        try {
            if (c.containsKey("template")) { comp.template(src); } else { comp.compile(src); }
            sb.append("{\"problems\":[]}");
        } catch (ElException e) {
            int at = Math.max(0, e.position());
            sb.append("{\"problems\":[{\"code\":").append(str(code(e))).append(",\"severity\":\"error\",\"message\":").append(str(words(e)))
                    .append(",\"expr\":{\"source\":").append(str(src)).append(",\"start\":").append(at).append(",\"end\":").append(Math.min(src.length(), at + 1)).append("}}]}");
        }
    }

    static void format(Map<String, Object> c, StringBuilder sb) {
        Object v = c.containsKey("value") ? plain(c.get("value")) : new LinkedHashMap<String, Object>();
        Object node = v instanceof Node ? v : (v == null ? null : v);
        String text = c.containsKey("format") ? formats.format(text(c.get("format")), node) : Values.text(node);
        String tone = c.containsKey("tone") && !Values.masked(node) ? com.ash.rachana.format.Tones.resolve(text(c.get("tone")), v) : null;
        sb.append("{\"text\":").append(str(text)).append(",\"tone\":").append(tone == null ? "null" : str(tone)).append('}');
    }

    /** The Sutra is parsed, then its expressions are checked: the problems in the order found. */
    static void problem(Map<String, Object> c, StringBuilder sb) {
        List<com.ash.rachana.SutraProblem> ps = new ArrayList<>();
        String file = c.get("file") == null ? "case.sutra.yaml" : text(c.get("file"));
        try {
            var sutra = new com.ash.rachana.parse.SutraParser().parse(text(c.get("sutra")), file, "conformance");
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
    static void edit(Map<String, Object> c, StringBuilder sb) {
        String sutra = text(c.get("sutra"));
        Map<String, Object> op = new LinkedHashMap<>(obj(c.get("operation")));
        StringBuilder name = new StringBuilder();
        boolean up = false;
        for (char ch : text(op.get("op")).toCharArray()) { if (ch == '-') { up = true; } else { name.append(up ? Character.toUpperCase(ch) : ch); up = false; } }
        op.put("op", name.toString());
        String text = sutra;
        StringBuilder ps = new StringBuilder();
        try {
            var r = new com.ash.rachana.design.ops.OpApplier().apply(sutra, com.ash.rachana.design.ops.Ops.parse((Object) List.of(op)));
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

    static EvalContext context(Map<String, Object> g) {
        Node root = node(g.containsKey("record") ? g.get("record") : new LinkedHashMap<String, Object>());
        EvalContext c = EvalContext.of(root, formats);
        if (g.containsKey("today")) { c = c.withToday(LocalDate.parse(text(g.get("today")))); }
        Map<String, Object> ctl = new LinkedHashMap<>();
        // a multi-valued control (multi-select, the choices of oneOf/within) reaches the evaluator as a document list, as the host's
        // ControlValues hands it over (H2: ControlsTest passes Node.of(List.of(...))); a scalar as itself
        if (g.containsKey("ctl")) { obj(g.get("ctl")).forEach((k, x) -> { Object v = plain(x); ctl.put(k, v instanceof List<?> ? Node.of(v) : v); }); c = c.withControls(ctl); }
        Map<String, Node> events = new LinkedHashMap<>();
        if (g.containsKey("events")) { obj(g.get("events")).forEach((k, x) -> events.put(k, node(x))); }
        Object src = g.containsKey("src") ? node(g.get("src")) : null;
        if (!events.isEmpty() || src != null) { c = c.withScope(new EvalContext.Scope(events, null, src)); }
        List<Object> chain = new ArrayList<>();
        if (g.containsKey("parents")) { List<Object> ps = new ArrayList<>(); items(g.get("parents")).forEach(p -> ps.add(node(p))); Collections.reverse(ps); chain.addAll(ps); }
        if (g.containsKey("row")) { chain.add(node(g.get("row"))); }
        if (!chain.isEmpty()) {
            EvalContext base = c;
            int idx = whole(g.get("index"), -1);
            c = new EvalContext(base.root(), base.row(), idx, base.formats(), base.ctl(), base.today(), base.scope(), base.locals()).withRows(chain);
        }
        return c;
    }

    static Node node(Object n) { return Node.of(plain(n)); }

    /** JSON to Java values; the tags of FORMAT 4 become the reference's own values. */
    static Object plain(Object n) {
        if (n == null) { return null; }
        if (n instanceof Boolean || n instanceof String || n instanceof Long || n instanceof Double) { return n; }
        if (n instanceof Number x) { return x.longValue(); }
        if (n instanceof List<?> a) { List<Object> l = new ArrayList<>(); a.forEach(x -> l.add(plain(x))); return l; }
        Map<String, Object> o = obj(n);
        if (o.size() == 1 && o.containsKey("$masked")) { return Node.masked(); }
        if (o.size() == 1 && o.containsKey("$dec")) { return new BigDecimal(text(o.get("$dec"))); }
        if (o.size() == 1 && o.containsKey("$num")) { return switch (text(o.get("$num"))) { case "NaN" -> Double.NaN; case "Infinity" -> Double.POSITIVE_INFINITY; default -> Double.NEGATIVE_INFINITY; }; }
        Map<String, Object> m = new LinkedHashMap<>();
        o.forEach((k, x) -> m.put(k, plain(x)));
        return m;
    }

    // ---- reading a case (plain JSON values) ----------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    static Map<String, Object> obj(Object v) { return v instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of(); }

    static List<?> items(Object v) { return v instanceof List<?> l ? l : v instanceof Map<?, ?> m ? new ArrayList<>(m.values()) : List.of(); }

    /** A value's text as the case wrote it: text as itself, a number or flag in its JSON form, a missing value empty. */
    static String text(Object v) { return v == null ? "" : v instanceof String s ? s : v instanceof Map || v instanceof List ? "" : String.valueOf(v); }

    static int whole(Object v, int otherwise) { return v instanceof Number n ? n.intValue() : v instanceof String s ? parseInt(s, otherwise) : otherwise; }

    static int parseInt(String s, int otherwise) { try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return otherwise; } }

    // ---- results -------------------------------------------------------------------------------------------------

    static String type(Object s) {
        if (s == null) { return "empty"; }
        if (Values.masked(s)) { return "masked"; }
        if (s instanceof Boolean) { return "boolean"; }
        if (s instanceof BigDecimal) { return "decimal"; }
        if (s instanceof Number) { return "number"; }
        if (s instanceof String) { return "text"; }
        if (s instanceof Link) { return "link"; }
        if (s instanceof Node d) { return d.kind() == Node.Kind.ARRAY ? "list" : "object"; }
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
        else if (s instanceof Node d) { value(Values.simplify(d.unwrap()), sb); }
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
