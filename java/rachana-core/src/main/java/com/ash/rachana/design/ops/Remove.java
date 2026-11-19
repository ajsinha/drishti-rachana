/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.design.ops;

/**
 * Removes a panel, with the comment just above it.
 *
 * @param panel the panel's id
 */
public record Remove(String panel) implements Op {

    @Override
    public String name() {
        return "remove";
    }

    @Override
    public void applyTo(SutraDoc doc) {
        doc.removePanel(panel);
    }
}
