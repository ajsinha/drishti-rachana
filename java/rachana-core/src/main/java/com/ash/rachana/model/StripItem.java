/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.model;

/**
 * One key figure in the header strip.
 *
 * @param label the label
 * @param bind Rachana-EL expression for the value
 * @param fmt named format, or null
 * @param tone colouring rule ({@code sign}, {@code status}), or null
 * @param emphasis draw the value highlighted (MTM in the mockups)
 * @param location where it was declared
 */
public record StripItem(String label, String bind, String fmt, String tone, boolean emphasis, SourceLocation location) {}
