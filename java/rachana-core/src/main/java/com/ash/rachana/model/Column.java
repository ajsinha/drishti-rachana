/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.model;

/**
 * A table, ladder or kv column.
 *
 * @param label header text
 * @param bind Rachana-EL expression evaluated per row ({@code @} is the row)
 * @param fmt named format, or null
 * @param tone colouring rule, or null
 * @param total sum this column into the total row
 * @param link wrap the value as a link to another entity
 */
public record Column(String label, String bind, String fmt, String tone, boolean total, boolean link) {}
