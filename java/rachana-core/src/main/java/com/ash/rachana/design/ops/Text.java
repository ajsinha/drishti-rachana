/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.design.ops;

/**
 * Replaces the whole Sutra text (the YAML tab). Comments and order are then whatever the new text has; it must be a valid
 * Sutra, or the operation is refused and the text stays as it was.
 *
 * @param yaml the new text
 */
public record Text(String yaml) implements Op {

    @Override
    public String name() {
        return "text";
    }

    @Override
    public void applyTo(SutraDoc doc) {
        if (yaml == null) {
            throw new OpException(OpException.MALFORMED, "text needs 'yaml': the whole Sutra text");
        }
        doc.replaceAll(yaml);
    }
}
