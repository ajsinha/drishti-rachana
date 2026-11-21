/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
/**
 * Tables of a Sutra's {@code data:} (SPEC 2.4, language level 1.1) and the Core half of the Data Protocol (spec/1/data-protocol): table
 * schemas and the decoding of cells, keyed patches ({@link com.ash.rachana.table.KeyedRows}), the filter syntax tree in both directions
 * ({@link com.ash.rachana.table.FilterTree}), the plan of what each table is asked ({@link com.ash.rachana.table.DataPlan}) and the record
 * {@code $} with its entries ({@link com.ash.rachana.table.DataRecord}). One implementation: the JVM runs it, and the JavaScript bundles
 * are the same code compiled by TeaVM.
 */
package com.ash.rachana.table;
