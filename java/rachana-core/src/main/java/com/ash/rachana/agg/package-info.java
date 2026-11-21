/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
/**
 * The aggregate kernel (split step A9): each aggregate's state and maths ({@link com.ash.rachana.agg.Aggregate}: start, add a
 * columnar {@link com.ash.rachana.agg.Batch}, merge, finish, encode), Core's list aggregates and list analytics over them
 * ({@link com.ash.rachana.agg.CoreAggregates}), and the catalogue by name. A host's pivot engine wraps the same classes, so a list
 * and a pivot cell are computed by one implementation.
 */
package com.ash.rachana.agg;
