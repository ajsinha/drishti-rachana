/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.model;

/**
 * The view title line: {@code [pill] ID with Counterparty}.
 *
 * @param pill the pill text, for example {@code Trade · Interest rate swap}
 * @param id expression for the identifier
 * @param with optional expression for the counterparty (usually a {@code link(...)})
 */
public record Title(String pill, String id, String with) {}
