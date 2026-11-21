/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.bind;

import java.util.Map;

/** The layout a view binds with: its label, whether the host inferred it, and the per-panel explanations of an inference. */
public record Layout(String label, boolean inferred, Map<String, String> explanations) {

    public Layout {
        explanations = explanations == null ? Map.of() : explanations;
    }

    /** A Sutra written by hand: no inference, no explanations. */
    public static Layout written(String label) {
        return new Layout(label, false, Map.of());
    }
}
