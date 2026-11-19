/*
 * Copyright (c) 2026 Ashutosh Sinha. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.proof;

import com.ash.drishti.api.DataNode;
import com.ash.drishti.api.error.DrishtiException;
import com.ash.drishti.rachana.SutraException;
import com.ash.drishti.rachana.SutraProblem;
import com.ash.drishti.rachana.el.CheckedMode;
import com.ash.drishti.rachana.el.ElCompiler;
import com.ash.drishti.rachana.el.ElException;
import com.ash.drishti.rachana.el.ElLimits;
import com.ash.drishti.rachana.el.EvalContext;
import com.ash.drishti.rachana.el.Expr;
import com.ash.drishti.rachana.el.Link;
import com.ash.drishti.rachana.el.Template;
import com.ash.drishti.rachana.el.check.Checker;
import com.ash.drishti.rachana.el.check.ElType;
import com.ash.drishti.rachana.el.check.Fields;
import com.ash.drishti.rachana.el.check.Problem;
import com.ash.drishti.rachana.el.check.Scope;
import com.ash.drishti.rachana.format.Formats;
import com.ash.drishti.rachana.format.Tones;
import com.ash.drishti.rachana.parse.SutraParser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The proof's case runner: one prepared case (a JSON object written by the prep step) to one canonical results line
 * {@code {"case","kind","status","actual"}}. Identical source on the JVM, in JavaScript and in WebAssembly; the reference build
 * (Drishti's own jars) runs it too, with {@link #sutraText} reading YAML instead of the prepared tree.
 */
public final class Harness {

    /** How a {@code problem} case's Sutra reaches the parser: the prepared tree (proof builds) or the YAML (reference build). */
    public static boolean useYaml = false;

    private static Formats formats;

    private Harness() {
    }

    static Formats formats() {
        if (formats == null) {
            formats = Formats.defaults();
        }
        return formats;
    }

    /** Every line of {@code jsonl} run; the results joined with LF, ending with LF. */
    public static String runAll(String jsonl) {
        StringBuilder out = new StringBuilder();
        int start = 0;
        while (start < jsonl.length()) {
            int end = jsonl.indexOf('\n', start);
            if (end < 0) {
                end = jsonl.length();
            }
            String line = jsonl.substring(start, end);
            start = end + 1;
            if (!line.isBlank()) {
                out.append(runLine(line)).append('\n');
            }
        }
        return out.toString();
    }

    @SuppressWarnings("unchecked")
    public static String runLine(String line) {
        Map<String, Object> c = (Map<String, Object>) Json.parse(line);
        StringBuilder b = new StringBuilder();
        b.append("{\"case\":");
        Canon.string((String) c.get("case"), b);
        b.append(",\"kind\":");
        String kind = (String) c.get("kind");
        Canon.string(kind, b);
        StringBuilder a = new StringBuilder();
        String status;
        try {
            status = run(kind, c, a);
        } catch (RuntimeException | StackOverflowError e) {
            a.setLength(0);
            a.append("{\"crash\":");
            Canon.string(e.getClass().getName() + ": " + e.getMessage(), a);
            a.append('}');
            status = "error";
        }
        b.append(",\"status\":\"").append(status).append("\",\"actual\":").append(a).append('}');
        return b.toString();
    }

    @SuppressWarnings("unchecked")
    private static String run(String kind, Map<String, Object> c, StringBuilder a) {
        Map<String, Object> given = c.get("given") instanceof Map ? (Map<String, Object>) c.get("given") : Map.of();
        switch (kind) {
            case "expr":
                expr(c, given, a);
                return "ok";
            case "expr-error":
                exprError(c, given, a);
                return "ok";
            case "check":
                check(c, given, a);
                return "ok";
            case "grammar":
                grammar(c, a);
                return "ok";
            case "format":
                format(c, a);
                return "ok";
            case "problem":
                problem(c, a);
                return "ok";
            default:
                a.append("null");
                return "unsupported";
        }
    }

    static ElCompiler compiler(Map<String, Object> given) {
        if (given.get("limits") instanceof Map<?, ?> l) {
            int depth = l.get("depth") instanceof Long d ? d.intValue() : ElLimits.DEFAULTS.maxDepth();
            int length = l.get("length") instanceof Long n ? n.intValue() : ElLimits.DEFAULTS.maxLength();
            return new ElCompiler(10_000, new ElLimits(depth, length));
        }
        return new ElCompiler();
    }

    /** A case value to what a host hands the compiler: tags resolved, maps and lists kept as plain Java values. */
    @SuppressWarnings("unchecked")
    public static Object plain(Object v) {
        if (v instanceof Map<?, ?> m) {
            if (m.size() == 1) {
                if (Boolean.TRUE.equals(m.get("$masked"))) {
                    return DataNode.MASK;
                }
                if (m.containsKey("$num")) {
                    String n = (String) m.get("$num");
                    return "NaN".equals(n) ? Double.NaN : n.startsWith("-") ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
                }
                if (m.containsKey("$dec")) {
                    return new BigDecimal((String) m.get("$dec"));
                }
                if (m.containsKey("$link")) {
                    Map<String, Object> l = (Map<String, Object>) m.get("$link");
                    return new Link((String) l.get("id"), (String) l.get("kind"), (String) l.get("label"));
                }
                if (m.containsKey("$f64") || m.containsKey("$big")) {
                    return Values.scalar(m);
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, x) -> out.put((String) k, plain(x)));
            return out;
        }
        if (v instanceof List<?> l) {
            List<Object> out = new ArrayList<>();
            for (Object x : l) {
                out.add(plain(x));
            }
            return out;
        }
        return v;
    }

    static DataNode node(Object v) {
        return DataNode.of(plain(v));
    }

    static LocalDate date(Object t) {
        if (!(t instanceof String s) || s.length() != 10) {
            return null;
        }
        return LocalDate.of(Integer.parseInt(s.substring(0, 4)), Integer.parseInt(s.substring(5, 7)), Integer.parseInt(s.substring(8, 10)));
    }

    @SuppressWarnings("unchecked")
    public static EvalContext context(Map<String, Object> given) {
        DataNode root = given.containsKey("record") ? node(given.get("record")) : DataNode.of(Map.of());
        Map<String, Object> ctl = new LinkedHashMap<>();
        if (given.get("ctl") instanceof Map<?, ?> m) {
            m.forEach((k, x) -> ctl.put((String) k, plain(x)));
        }
        Map<String, DataNode> events = new LinkedHashMap<>();
        if (given.get("events") instanceof Map<?, ?> m) {
            m.forEach((k, x) -> events.put((String) k, node(x)));
        }
        Object src = given.containsKey("src") ? node(given.get("src")) : null;
        EvalContext ctx = new EvalContext(root, null, -1, formats(), ctl, date(given.get("today")),
                new EvalContext.Scope(events, events.get("parent"), src));
        int index = given.get("index") instanceof Long i ? i.intValue() : -1;
        if (given.containsKey("row") || given.containsKey("parents")) {
            List<Object> chain = new ArrayList<>();
            if (given.get("parents") instanceof List<?> ps) {
                List<Object> rev = new ArrayList<>(ps);
                Collections.reverse(rev);
                for (Object p : rev) {
                    chain.add(node(p));
                }
            }
            chain.add(given.containsKey("row") ? node(given.get("row")) : null);
            ctx = new EvalContext(root, null, index, formats(), ctl, ctx.today(), ctx.scope()).withRows(chain);
        } else if (index != -1) {
            ctx = new EvalContext(root, null, index, formats(), ctl, ctx.today(), ctx.scope());
        }
        return ctx;
    }

    private static void error(DrishtiException e, StringBuilder a) {
        a.append("{\"error\":{\"code\":");
        Canon.string(e.errorCode().code().replace("DRS-", "RCH-"), a);
        a.append(",\"message\":");
        String m = e.getMessage();
        Canon.string(m, a);
        a.append("}}");
    }

    static void expr(Map<String, Object> c, Map<String, Object> given, StringBuilder a) {
        ElCompiler compiler = compiler(given);
        Set<String> reads = new LinkedHashSet<>();
        Object v;
        try {
            EvalContext ctx = context(given);
            if (c.get("template") instanceof String t) {
                Template tp = compiler.template(t);
                tp.paths(reads::add);
                v = tp.render(ctx);
            } else {
                Expr e = compiler.compile((String) c.get("expr"));
                e.paths(reads::add);
                v = e.eval(ctx);
            }
        } catch (DrishtiException e) {
            error(e, a);
            return;
        }
        a.append("{\"value\":");
        Canon.value(v, a);
        a.append(",\"type\":\"").append(Canon.type(v)).append("\",\"text\":");
        Canon.string(com.ash.drishti.rachana.el.Values.text(v), a);
        a.append(",\"reads\":[");
        boolean first = true;
        for (String r : reads) {
            if (!first) {
                a.append(',');
            }
            first = false;
            Canon.string(r, a);
        }
        a.append("]}");
    }

    static void exprError(Map<String, Object> c, Map<String, Object> given, StringBuilder a) {
        ElCompiler compiler = compiler(given);
        a.append("{\"problems\":[");
        try {
            if (c.get("template") instanceof String t) {
                compiler.template(t);
            } else {
                compiler.compile((String) c.get("expr"));
            }
        } catch (ElException e) {
            a.append("{\"code\":");
            Canon.string(e.errorCode().code().replace("DRS-", "RCH-"), a);
            a.append(",\"message\":");
            Canon.string(e.getMessage(), a);
            a.append(",\"start\":").append(e.position()).append('}');
        } catch (DrishtiException e) {
            a.append("{\"code\":");
            Canon.string(e.errorCode().code().replace("DRS-", "RCH-"), a);
            a.append(",\"message\":");
            Canon.string(e.getMessage(), a);
            a.append('}');
        }
        a.append("]}");
    }

    @SuppressWarnings("unchecked")
    static void check(Map<String, Object> c, Map<String, Object> given, StringBuilder a) {
        Map<String, Col> cols = new LinkedHashMap<>();
        if (c.get("types") instanceof Map<?, ?> types) {
            for (String root : List.of("record", "row")) {
                if (types.get(root) instanceof Map<?, ?> t) {
                    String prefix = "record".equals(root) ? "$" : "@";
                    t.forEach((k, ty) -> cols.put(prefix + "." + k, new Col(prefix + "." + k, String.valueOf(ty))));
                }
            }
        }
        Fields fields = new Fields() {
            @Override
            public Optional<Fields.Field> field(List<String> parts) {
                Col f = cols.get(String.join(".", parts));
                return f == null ? Optional.empty() : Optional.of(new Fields.Field(f.path(), ElType.ofColumn(f.type()), false));
            }

            @Override
            public boolean alias(String name) {
                return false;
            }

            @Override
            public Optional<String> measure(String nameOrId) {
                return Optional.empty();
            }

            @Override
            public List<String> names() {
                return new ArrayList<>(cols.keySet());
            }
        };
        String scope = (String) c.get("scope");
        Checker.Checked r = CheckedMode.check((String) c.get("expr"), "data".equals(scope) ? Scope.ROW : null, fields);
        a.append("{\"type\":");
        if (r.ok()) {
            Canon.string(r.type().word(), a);
        } else {
            a.append("null");
        }
        a.append(",\"problems\":[");
        boolean first = true;
        for (Problem p : r.problems()) {
            if (!first) {
                a.append(',');
            }
            first = false;
            a.append("{\"code\":");
            Canon.string(p.code().replace("DRS-", "RCH-"), a);
            a.append(",\"severity\":");
            Canon.string(p.level(), a);
            a.append(",\"message\":");
            Canon.string(p.message(), a);
            if (p.fix() != null) {
                a.append(",\"fix\":");
                Canon.string(p.fix(), a);
            }
            a.append(",\"start\":").append(p.start()).append(",\"end\":").append(p.end()).append('}');
        }
        a.append("]}");
    }

    private record Col(String path, String type) {
    }

    static void grammar(Map<String, Object> c, StringBuilder a) {
        boolean ok = true;
        try {
            ElCompiler compiler = new ElCompiler();
            if ("template".equals(c.get("rule"))) {
                compiler.template((String) c.get("text"));
            } else {
                compiler.compile((String) c.get("text"));
            }
        } catch (DrishtiException e) {
            ok = false;
        }
        a.append("{\"accepted\":").append(ok).append('}');
    }

    static void format(Map<String, Object> c, StringBuilder a) {
        Object v = plain(c.get("value"));
        String f = (String) c.get("format");
        String text = f == null ? com.ash.drishti.rachana.el.Values.text(v) : formats().format(f, v);
        String tone = Tones.resolve((String) c.get("tone"), v);
        a.append("{\"text\":");
        Canon.string(text, a);
        a.append(",\"tone\":");
        if (tone == null) {
            a.append("null");
        } else {
            Canon.string(tone, a);
        }
        a.append('}');
    }

    static void problem(Map<String, Object> c, StringBuilder a) {
        String file = c.get("file") instanceof String f ? f : "case.sutra.yaml";
        String text = (String) (useYaml ? c.get("sutra") : c.get("pnode"));
        List<SutraProblem> problems = List.of();
        try {
            problems = new com.ash.drishti.rachana.SutraExpressions(new ElCompiler()).check(new SutraParser().parse(text, file, "cases"));
        } catch (SutraException e) {
            problems = e.problems();
        }
        a.append("{\"ok\":").append(problems.isEmpty()).append(",\"problems\":[");
        boolean first = true;
        for (SutraProblem p : problems) {
            if (!first) {
                a.append(',');
            }
            first = false;
            a.append("{\"code\":");
            Canon.string(p.code().replace("DRS-", "RCH-"), a);
            a.append(",\"message\":");
            Canon.string(p.message(), a);
            a.append(",\"line\":").append(p.location().line()).append(",\"column\":").append(p.location().column()).append('}');
        }
        a.append("]}");
    }
}
