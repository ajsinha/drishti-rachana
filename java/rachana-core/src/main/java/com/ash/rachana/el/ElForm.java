/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import java.util.List;

/**
 * A Rachana-EL function given its arguments unevaluated: it evaluates them lazily ({@code iferror}, {@code switch}) or once
 * per element of a list with {@code @} bound to it ({@code map}, {@code sort}, {@code reduce}). Pure and thread-safe; it
 * decides itself what a masked argument means.
 */
@FunctionalInterface
interface ElForm {

    Object apply(List<Expr> args, EvalContext context);
}
