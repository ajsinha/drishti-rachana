/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.design.ops;

import java.util.Map;

/**
 * Sets which entities the Sutra applies to ({@code kind}, optionally {@code where} and {@code priority}).
 *
 * @param match the match mapping
 */
public record SetMatch(Map<String, Object> match) implements Op {

    @Override
    public String name() {
        return "setMatch";
    }

    @Override
    public void applyTo(SutraDoc doc) {
        if (match == null || match.isEmpty()) {
            throw new OpException(OpException.MALFORMED, "setMatch needs a 'match' mapping such as { kind: trade }");
        }
        doc.setTop("match", OpChecks.plain(match));
    }
}
