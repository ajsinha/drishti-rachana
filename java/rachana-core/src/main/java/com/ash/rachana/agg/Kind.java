/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.agg;

import com.ash.rachana.internal.CoreText;

/** How an aggregate's partial states combine: exactly (mergeable), approximately (a sketch), or not at all (holistic). */
public enum Kind {
    MERGEABLE, SKETCH, HOLISTIC;

    public String word() {
        return CoreText.lower(name());
    }
}
