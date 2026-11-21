/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.bind;

import com.ash.rachana.api.Node;

/**
 * The host hook for the viewer (split step A13): the masks applied to what a viewer sees and the kinds the viewer may open. Core
 * itself masks nothing: {@link #NONE}. A host passes its own implementation and reads it back where it is handed one
 * ({@link com.ash.rachana.wire.TableRows}).
 */
public interface Masker {

    /** Masks nothing, opens everything. */
    Masker NONE = new Masker() {};

    /** The data as this viewer may see it. */
    default Node mask(Node data) {
        return data;
    }

    /** Whether this viewer may open entities of the kind. */
    default boolean mayOpen(String kind) {
        return true;
    }
}
