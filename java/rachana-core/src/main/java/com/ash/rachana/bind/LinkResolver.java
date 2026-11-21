/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.bind;

import java.util.Optional;

/**
 * The host hook for links (split step A13): which kind an id names (so a bare id renders as a link) and the short code a kind
 * opens with. Core alone knows no kinds: {@link #NONE}.
 */
public interface LinkResolver {

    /** No kinds: an id stays text, a link carries no code. */
    LinkResolver NONE = new LinkResolver() {};

    /** The kind whose entity has this id, if the host knows one. */
    default Optional<String> kindOf(String id) {
        return Optional.empty();
    }

    /** The code that opens a kind (a mnemonic), or null. */
    default String codeFor(String kind) {
        return null;
    }
}
