/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.live.spi;

import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * What one reader may see of one table (LIVE_TABLES 5), made by the host's {@link Authoriser} for each request and never kept beyond it
 * and the reader's own subscription.
 *
 * @param audience everything that decides what the reader sees, as text: two readers with equal audiences share an index; no one else reads it
 * @param rowFilter a filter tree and-ed with every query and subscription and pushed to the source, or null for every row
 * @param maskedColumns columns sent as {@code {"$masked": true}}; filter, sort and group run on the masked row
 * @param rowTest an in-process row rule (on the stored row) for a rule that is code, or null; not pushed down
 * @param redact an in-process redactor (on the row after the masks), or null
 * @param editableColumns the columns this reader may edit (empty: read-only)
 * @param readOnlyReason why the reader may not edit, in words, or null
 */
public record ReaderView(String audience, Object rowFilter, Set<String> maskedColumns, Predicate<Map<String, Object>> rowTest,
        UnaryOperator<Map<String, Object>> redact, Set<String> editableColumns, String readOnlyReason) {

    public ReaderView {
        audience = audience == null ? "" : audience;
        maskedColumns = maskedColumns == null ? Set.of() : Set.copyOf(maskedColumns);
        editableColumns = editableColumns == null ? Set.of() : Set.copyOf(editableColumns);
    }

    /** A reader who sees every row as stored and may edit nothing. */
    public static ReaderView all() {
        return new ReaderView("", null, Set.of(), null, null, Set.of(), "no rights were given");
    }

    /** True when the reader sees every row and every value as stored. */
    public boolean unrestricted() {
        return rowFilter == null && rowTest == null && redact == null && maskedColumns.isEmpty();
    }
}
