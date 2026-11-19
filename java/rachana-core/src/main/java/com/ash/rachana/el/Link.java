/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

/**
 * A reference to another entity produced by {@code link(...)}. The engine resolves it to a view link.
 *
 * @param id the target identifier
 * @param kind the target kind, or null to let the reference catalogue decide
 * @param label display text, or null to show the identifier
 */
public record Link(String id, String kind, String label) {

    public String display() {
        return label == null || label.isBlank() ? id : label;
    }
}
