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

    public ElCompiler(long maxEntries, ElLimits limits, FunctionTable table) {
        this(maxEntries, limits, table, com.ash.rachana.profile.Grammar.LEVEL);
    }

    /**
     * A compiler at a language level (SUTRA_EXPANSION 6.2). Rachana 1.x has one level, {@code Grammar.LEVEL} (1.2): every expression is
     * read at it, through the one parse ({@code Parser.parseWithSpans}), whole (text left after an expression is refused), and a problem
     * is worded {@code expression '<source>': <reason>}.
     *
     * @throws IllegalArgumentException for a level this runtime does not implement ({@code Grammar.LEVELS})
     */
    public ElCompiler(long maxEntries, ElLimits limits, FunctionTable table, String level) {
        this.exprs = new Lru<>(maxEntries);
        this.templates = new Lru<>(maxEntries);
        this.limits = limits;
        this.table = table == null ? FunctionTable.core() : table;
        this.level = level == null ? com.ash.rachana.profile.Grammar.LEVEL : level;
        if (!com.ash.rachana.profile.Grammar.LEVELS.contains(this.level)) {
            throw new IllegalArgumentException("language level '" + this.level + "' is not one of "
                    + String.join(", ", com.ash.rachana.profile.Grammar.LEVELS));
        }
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
        try {
            return bare(source);
        } catch (ElException e) {
            throw whole("expression", source, e);
        }
    }

    /** The expression without the prefix on a problem (a template's part: the template words it). */
    Expr bare(String source) {
        String d = Declarations.inForce().id();                  // a Sutra's declared names (level 1.2): cached apart from every other Sutra's
        return exprs.get(d.isEmpty() ? source : d + "\u0000" + source, s -> Parser.parseWithSpans(source, limits, table).expr());
    }

    /** The words of a problem (SPEC 6.3): {@code expression '<source>': <reason> at <offset>}; an evaluation problem is left as it is. */
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
        String d = Declarations.inForce().id();
        String key = d.isEmpty() ? source : d + "\u0000" + source;
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
