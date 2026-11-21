/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.parse;

import com.ash.rachana.SutraProblem;
import com.ash.rachana.el.Declarations;
import com.ash.rachana.model.SourceLocation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import com.ash.rachana.model.Ordered;

/**
 * Reads a Sutra's {@code let:} and {@code functions:} (level 1.2; EL_EXPANSION X41, SUTRA_EXPANSION S2) and a library's
 * {@code functions:} (X42) into {@link Declarations.Function}s and let texts, with a problem for each wrong entry.
 *
 * <pre>
 * let:                                  # named values, in order; each may read the ones before it
 *   fx:    "$.trades[?@.desk == 'FX']"
 * functions:                            # pure: a body reads only its parameters and other functions
 *   share:
 *     doc: The part as a share of the whole.
 *     params: { part: decimal, whole: decimal }
 *     returns: decimal
 *     is: "safe_div(part, whole)"
 *     examples: [ { expr: "share(1, 4)", result: 0.25 } ]
 * </pre>
 */
public final class DeclarationsReader {

    /** A declared name: letters, digits and {@code _}, starting with a letter (an EL name). */
    public static final Pattern NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");
    /** The keys of one function. */
    public static final Set<String> FUNCTION_KEYS = Ordered.set("doc", "params", "returns", "is", "examples");
    /** At most this many functions and lets per Sutra or library. */
    public static final int MAX = 200;
    /** A declared name that a later level reserves (the local one wins): an advisory. */
    public static final String RESERVED = "RCH-2092";

    private final String file;
    private final List<SutraProblem> problems;
    private final List<SutraProblem> advisories;

    public DeclarationsReader(String file, List<SutraProblem> problems, List<SutraProblem> advisories) {
        this.file = file;
        this.problems = problems;
        this.advisories = advisories;
    }

    /** The {@code functions:} mapping, each name prefixed with {@code prefix} ({@code ""} for a Sutra's, {@code "<library>."} for a library's). */
    public Map<String, Declarations.Function> functions(PNode n, String prefix) {
        Map<String, Declarations.Function> out = new LinkedHashMap<>();
        if (n == null) {
            return out;
        }
        if (!n.isMap()) {
            problem("RCH-2012", "functions is a mapping of name to { params, is, doc, returns, examples }", n);
            return out;
        }
        if (n.map().size() > MAX) {
            problem("RCH-2012", "at most " + MAX + " functions", n);
        }
        n.map().forEach((name, f) -> {
            if (!name(name, "function", f)) {
                return;
            }
            if (Declarations.coreName(name)) {
                problem("RCH-2020", "the function '" + name + "' is a Core function's name: pick another (a declared function never hides a Core one)", f);
                return;
            }
            if (!f.isMap()) {
                problem("RCH-2012", "the function '" + name + "' is { params, is, doc, returns, examples }", f);
                return;
            }
            f.map().forEach((k, v) -> {
                if (!FUNCTION_KEYS.contains(k)) {
                    problem("RCH-2011", "unknown key '" + k + "' in the function '" + name + "' (it takes " + String.join(", ", new java.util.TreeSet<>(FUNCTION_KEYS)) + ")", v);
                }
            });
            PNode body = f.map().get("is");
            if (body == null || !(body.value() instanceof String) || body.text().isBlank()) {
                problem("RCH-2010", "the function '" + name + "' needs 'is': the expression it stands for", body == null ? f : body);
                return;
            }
            Map<String, String> params = new LinkedHashMap<>();
            PNode ps = f.map().get("params");
            if (ps != null && !ps.isMap()) {
                problem("RCH-2012", "params of the function '" + name + "' is a mapping of name to type", ps);
                return;
            }
            boolean ok = true;
            for (Map.Entry<String, PNode> p : ps == null ? Map.<String, PNode>of().entrySet() : ps.map().entrySet()) {
                ok &= name(p.getKey(), "parameter", p.getValue());
                params.put(p.getKey(), p.getValue().value() == null ? "any" : String.valueOf(p.getValue().value()));
            }
            PNode returns = f.map().get("returns");
            PNode doc = f.map().get("doc");
            if (ok) {
                reserved(name, f);
                out.put(prefix + name, new Declarations.Function(prefix + name, params, body.text(), returns == null ? null : String.valueOf(returns.value()),
                        doc == null ? null : String.valueOf(doc.value())));
            }
        });
        return out;
    }

    /** The {@code let:} mapping in written order: name to expression text. */
    public Map<String, String> lets(PNode n, Set<String> taken) {
        Map<String, String> out = new LinkedHashMap<>();
        if (n == null) {
            return out;
        }
        if (!n.isMap()) {
            problem("RCH-2012", "let is a mapping of name to expression", n);
            return out;
        }
        if (n.map().size() > MAX) {
            problem("RCH-2012", "at most " + MAX + " lets", n);
        }
        n.map().forEach((name, v) -> {
            if (!name(name, "let", v)) {
                return;
            }
            if (taken.contains(name)) {
                problem("RCH-2024", "the let '" + name + "' has the name of a control or a data entry of this Sutra: a let shadows nothing", v);
                return;
            }
            Object value = v.value();
            if (value instanceof Map || value instanceof List || value == null) {
                problem("RCH-2012", "the let '" + name + "' is one expression (text, a number or true/false)", v);
                return;
            }
            reserved(name, v);
            out.put(name, value instanceof String s ? s : String.valueOf(value));
        });
        return out;
    }

    private boolean name(String name, String what, PNode at) {
        if (!NAME.matcher(name).matches() || "lambda".equals(name) || "true".equals(name) || "false".equals(name) || "null".equals(name)
                || "and".equals(name) || "or".equals(name) || "not".equals(name) || "in".equals(name) || "if".equals(name) || "else".equals(name)
                || "is".equals(name) || "arg".equals(name) || "ctl".equals(name)) {
            problem("RCH-2020", "the " + what + " name '" + name + "' is letters, digits and _, starting with a letter, and no word of the language", at);
            return false;
        }
        return true;
    }

    private void reserved(String name, PNode at) {
        if (Declarations.reserved(name)) {
            advisories.add(new SutraProblem(RESERVED, "'" + name + "' is a name a later level of Rachana reserves: this Sutra's own one wins here",
                    new SourceLocation(file, at.line(), at.column())));
        }
    }

    private void problem(String code, String message, PNode at) {
        problems.add(new SutraProblem(code, message, new SourceLocation(file, at.line(), at.column())));
    }
}
