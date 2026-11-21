/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana;

import com.ash.rachana.parse.SutraParser;
import com.ash.rachana.profile.Entities;
import com.ash.rachana.profile.Grammar;
import com.ash.rachana.profile.Profile;
import java.util.Collection;
import java.util.List;

/** The grammar most moved tests were written for: Core's with the entity additions (split step A7), as a host's profile brings them. */
public final class TestGrammar {

    private static final com.ash.rachana.parse.BlockLibrary BLOCKS = new com.ash.rachana.parse.BlockLibrary();
    private static final Grammar ENTITIES = grammar(List.of());
    private static final SutraParser PARSER = new SutraParser(ENTITIES);

    private TestGrammar() {}

    /** Core's grammar with the entity additions. */
    public static Grammar entities() {
        return ENTITIES;
    }

    /** The same, the schema's {@code match.kind} listing these kinds. */
    public static Grammar grammar(Collection<String> kinds) {
        return Grammar.of(List.of(Profile.core(), Entities.addTo(Profile.named("host"), () -> kinds, BLOCKS).build()));
    }

    /** The block library of {@link #entities()} (the tests' host's, one per instance in a real host). */
    public static com.ash.rachana.parse.BlockLibrary blocks() {
        return BLOCKS;
    }

    /** A parser of {@link #entities()}. */
    public static SutraParser parser() {
        return PARSER;
    }
}
