/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.design.ops;

import java.util.List;
import java.util.Map;

/** Replaces the Sutra's {@code controls:} (an empty list removes them): each item is {@code { id, kind, label, options, default, ... }}. */
public record SetControls(List<Map<String, Object>> items) implements Op {

    @Override
    public String name() {
        return "setControls";
    }

    @Override
    public void applyTo(SutraDoc doc) {
        if (items == null) {
            throw new OpException(OpException.MALFORMED, "setControls needs 'items': a list of { id, kind, ... } (empty to remove the controls)");
        }
        doc.setTop("controls", OpChecks.plain(items));
    }
}
