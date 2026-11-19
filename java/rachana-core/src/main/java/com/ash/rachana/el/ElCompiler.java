/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Compiles Rachana-EL source to {@link Expr} trees, caching by source text: the same few hundred
 * expressions are evaluated millions of times, so they are parsed once. Thread-safe.
 */
public final class ElCompiler {

    private final Cache<String, Expr> exprs;
    private final Cache<String, Template> templates;
    private final ElLimits limits;

    public ElCompiler(long maxEntries, ElLimits limits) {
        this.exprs = Caffeine.newBuilder().maximumSize(maxEntries).build();
        this.templates = Caffeine.newBuilder().maximumSize(maxEntries).build();
        this.limits = limits;
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
        return exprs.get(source, s -> Parser.parse(s, limits));
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
        return exprs.estimatedSize();
    }
}
