/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

/**
 * How big a Rachana-EL expression may be. Parsing and evaluation are recursive, so an unbounded expression (thousands
 * of nested parentheses, or a chain of thousands of {@code +}) would overflow the thread's stack; within these bounds
 * both are safe on any thread. An expression beyond them is a compile error ({@code RCH-2101}) with its position.
 *
 * @param maxDepth the deepest nesting allowed: parentheses, brackets, function calls, unary operators, the branches of
 *     {@code ?:} and the operands of a chain of binary operators all count ({@code a + b + c} is three deep)
 * @param maxLength the longest expression allowed, in characters
 */
public record ElLimits(int maxDepth, int maxLength) {

    /** {@code rachana.max-expression-depth} and {@code rachana.max-expression-length} defaults ({@link com.ash.rachana.RachanaOptions}). */
    public static final ElLimits DEFAULTS = new ElLimits(200, 10_000);

    public ElLimits {
        if (maxDepth < 1 || maxLength < 1) {
            throw new IllegalArgumentException("expression limits must be positive: depth " + maxDepth + ", length " + maxLength);
        }
    }
}
