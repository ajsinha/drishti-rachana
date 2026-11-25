/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.wire;

import com.ash.rachana.api.Node;
import com.ash.rachana.bind.Masker;
import java.time.LocalDate;
import java.util.List;

/**
 * A virtual table's rows for a panel's {@code source: { table }} (spec/1/SPEC.md V13): read for one viewer on a business date, the source as
 * they may see it, then the table's rules, masks and hidden columns. The server provides it (the table layer); the engine knows nothing of
 * tables beyond this port.
 */
@FunctionalInterface
public interface TableRows {

    /**
     * The rows, each an object of the table's columns by name (masked values as the mask).
     *
     * @param date the business date, or null for the table's latest
     * @throws Refused when the viewer may not read the table, or it cannot be read; its message is what the panel says
     */
    List<Node> rows(Masker who, String table, LocalDate date);

    /** A read refused, in words the panel shows. */
    final class Refused extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public Refused(String message) {
            super(message);
        }
    }

    /** No table layer on this node: every table source says so. */
    TableRows NONE = (who, table, date) -> {
        throw new Refused("virtual tables are not served here");
    };
}
