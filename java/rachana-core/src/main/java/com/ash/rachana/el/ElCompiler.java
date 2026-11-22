/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import com.ash.rachana.internal.Lru;

/**
 * Compiles Rachana-EL source to {@link Expr} trees, caching by source text: the same few hundred
 * expressions are evaluated millions of times, so they are parsed once. Thread-safe. Each compiler reads one
 * {@link FunctionTable} (Core's alone unless given another: a {@code Rachana} instance gives its own) and holds its own cache, so
 * two instances never share a compiled expression.
 */
public final class ElCompiler {

    private final Lru<String, Expr> exprs;
    private final Lru<String, Template> templates;
    private final ElLimits limits;
    private final FunctionTable table;
    private final String level;
    private final boolean v11;

    public ElCompiler(long maxEntries, ElLimits limits, FunctionTable table) {
        this(maxEntries, limits, table, com.ash.rachana.profile.Grammar.LEVEL);
    }

    /**
     * A compiler at a language level (SUTRA_EXPANSION 6.2): {@code 1.0} reads what the reference read; {@code 1.1} adds the syntax batch
     * (records, spread, slices, {@code range}, the pipe, {@code ??}, lambdas, comments, digit separators, {@code **}, {@code a if c else b}),
     * exact numbers (SPEC 4.16), texts by code point (SPEC 4.13), and words its problems as {@code expression '<source>': <reason>}.
     */
    public ElCompiler(long maxEntries, ElLimits limits, FunctionTable table, String level) {
        this.exprs = new Lru<>(maxEntries);
        this.templates = new Lru<>(maxEntries);
        this.limits = limits;
        this.table = table == null ? FunctionTable.core() : table;
        this.level = level == null ? com.ash.rachana.profile.Grammar.LEVEL : level;
        this.v11 = !"1.0".equals(this.level);
    }

    /** The language level this compiler reads. */
    public String level() {
        return level;
    }

    public ElCompiler(long maxEntries, ElLimits limits) {
        this(maxEntries, limits, FunctionTable.core());
    }

    public ElCompiler(long maxEntries) {
        this(maxEntries, ElLimits.DEFAULTS);
    }

    public ElCompiler() {
        this(10_000);
    }

    /**
     * @throws ElException ({@code RCH-2101}) with the offset of the problem, including an expression beyond the
     *     {@linkplain ElLimits limits} (too deeply nested or too long)
     */
    public Expr compile(String source) {
        Declarations in = Declarations.inForce();
        String d = in.id();
        boolean v = v11(in);
        if (!d.isEmpty()) {                                     // a Sutra's declared names (level 1.2) or level: cached apart from every other Sutra's
            if (!v) {
                return exprs.get(d + "\u0000" + source, k -> Parser.parse(source, limits, table));
            }
            try {
                return exprs.get(d + "\u0000" + source, k -> Parser.parseWithSpans(source, limits, table, true).expr());
            } catch (ElException e) {
                throw whole("expression", source, e);
            }
        }
        if (!v) {
            return exprs.get(source, s -> Parser.parse(s, limits, table));
        }
        try {
            return bare(source);
        } catch (ElException e) {
            throw whole("expression", source, e);
        }
    }

    /** Whether expressions compile at level 1.1 or later: the level of the Sutra in force when it declares one below this compiler's. */
    private boolean v11(Declarations in) {
        return in.level() == null ? v11 : !"1.0".equals(in.level());
    }

    /** The expression without level 1.1's prefix on a problem (a template's part: the template words it). */
    Expr bare(String source) {
        Declarations in = Declarations.inForce();
        String d = in.id();
        boolean v = v11(in);
        return exprs.get(d.isEmpty() ? source : d + "\u0000" + source, s -> v ? Parser.parseWithSpans(source, limits, table, true).expr()
                : Parser.parse(source, limits, table));
    }

    /** Level 1.1's words (SPEC 6.3): {@code expression '<source>': <reason> at <offset>}; an evaluation problem is left as it is. */
    private static ElException whole(String what, String source, ElException e) {
        if (e.position() < 0) {
            return e;
        }
        String m = e.getMessage();
        String code = e.code() == null ? "" : e.code().code();
        String words = m.startsWith(code + " ") ? m.substring(code.length() + 1) : m;
        return new ElException(e.position(), e.end(), what + " '" + source + "': " + words);
    }

    /** The function table expressions are compiled against. */
    public FunctionTable functions() {
        return table;
    }

    /** The bounds every compiled expression is held to. */
    public ElLimits limits() {
        return limits;
    }

    /** Compiles text with embedded {@code ${expr}} parts. Text without {@code ${} is returned as is. */
    public Template template(String source) {
        Declarations in = Declarations.inForce();
        String d = in.id();
        boolean v = v11(in);
        String key = d.isEmpty() ? source : d + "\u0000" + source;
        if (!v) {
            return templates.get(key, k -> Template.parse(source, this));
        }
        try {
            return templates.get(key, k -> Template.parse(source, this));
        } catch (ElException e) {
            throw whole("template", source, e);
        }
    }

    public long size() {
        return exprs.size();
    }
}
