/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import java.util.List;

/** A Rachana-EL function. Implementations must be pure and thread-safe. */
@FunctionalInterface
public interface ElFunction {

    Object apply(List<Object> args, EvalContext context);
}
