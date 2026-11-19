/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana;

import com.ash.rachana.model.SourceLocation;

/**
 * One thing wrong with a Sutra file.
 *
 * @param code stable {@code RCH-2nnn} code
 * @param message what is wrong and how to fix it
 * @param location where
 */
public record SutraProblem(String code, String message, SourceLocation location) {

    @Override
    public String toString() {
        return location + " " + code + " " + message;
    }
}
