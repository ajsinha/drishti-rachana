/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.bind;

/** One entity a listing or search found: its reference and the title and subtitle it shows with. */
public record EntityHit(EntityRef ref, String title, String subtitle) {}
