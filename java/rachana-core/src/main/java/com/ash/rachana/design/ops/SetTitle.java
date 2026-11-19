/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.design.ops;

import java.util.Map;

/**
 * Sets the Sutra's title line ({@code pill}, {@code id}, optionally {@code with}).
 *
 * @param title the title mapping; a title needs an {@code id} expression
 */
public record SetTitle(Map<String, Object> title) implements Op {

    @Override
    public String name() {
        return "setTitle";
    }

    @Override
    public void applyTo(SutraDoc doc) {
        if (title == null || title.isEmpty()) {
            throw new OpException(OpException.MALFORMED, "setTitle needs a 'title' mapping such as { pill: Trade, id: $.id }");
        }
        doc.setTop("title", OpChecks.plain(title));
    }
}
