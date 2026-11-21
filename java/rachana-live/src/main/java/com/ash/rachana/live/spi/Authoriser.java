/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.live.spi;

import java.util.Map;

/**
 * The host's rights hook (LIVE_TABLES 5): the only source of "may read", row scope and masks (and, from the edit path, "may edit").
 * Rachana never decides rights; the engine calls it and obeys. {@code caller} is the opaque object the host's transport attached to
 * the request. Edits: {@link #mayEdit} (asked after the declaration, the site switch and the reader's editable columns all allow the change);
 * a {@link Decision.Refuse} is answered 403 and audited ({@code write-refused}).
 */
public interface Authoriser {

    /**
     * What the caller may see of a table.
     *
     * @throws Refusal when the caller may not read it ({@link Refusal#unknown()}: answer as if the table did not exist)
     */
    ReaderView view(Object caller, String table) throws Refusal;

    /** Whether the caller may make this change to this row; never allows by default (the edit path, LIVE_TABLES 1.6). */
    default Decision mayEdit(Object caller, String table, Map<String, Object> change, Map<String, Object> row) {
        return new Decision.Refuse("this host decides no edits");
    }

    /**
     * The caller's name as changes and the tray record it ({@code by}), or null when the caller has none (then the caller may not write).
     * By default the caller's text.
     */
    default String who(Object caller) {
        return caller == null ? null : String.valueOf(caller);
    }

    /** An edit decision. */
    sealed interface Decision permits Decision.Allow, Decision.Refuse {
        record Allow() implements Decision {}

        record Refuse(String reason) implements Decision {}
    }

    /** A refusal to read: forbidden (403), or unknown (404, so as not to leak the table's existence). */
    final class Refusal extends Exception {
        private final boolean unknown;

        public Refusal(String reason, boolean unknown) {
            super(reason, null, false, false);
            this.unknown = unknown;
        }

        public boolean unknown() {
            return unknown;
        }
    }
}
