/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.design.ops;

import java.util.List;
import java.util.Map;

/**
 * Sets the header key figures (at most eight; an empty list removes the strip).
 *
 * @param items each {@code { label, bind, fmt, tone, emphasis }}
 */
public record SetStrip(List<Map<String, Object>> items) implements Op {

    @Override
    public String name() {
        return "setStrip";
    }

    @Override
    public void applyTo(SutraDoc doc) {
        if (items == null) {
            throw new OpException(OpException.MALFORMED, "setStrip needs 'items': a list of { label, bind } (empty to remove the strip)");
        }
        doc.setTop("strip", OpChecks.plain(items));
    }
}
