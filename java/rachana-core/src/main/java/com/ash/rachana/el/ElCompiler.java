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

    public ElCompiler(long maxEntries, ElLimits limits, FunctionTable table) {
        this.exprs = new Lru<>(maxEntries);
        this.templates = new Lru<>(maxEntries);
        this.limits = limits;
        this.table = table == null ? FunctionTable.core() : table;
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
        return exprs.get(source, s -> Parser.parse(s, limits, table));
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
        return templates.get(source, s -> Template.parse(s, this));
    }

    public long size() {
        return exprs.size();
    }
}
