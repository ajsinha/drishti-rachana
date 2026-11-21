/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.parse;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Where {@code use: name@version} finds its fragment (docs/design/CORE_AND_PROFILES.md section 3.4): a host hook, given per instance
 * through a profile ({@code Profile.Builder.fragments}): a {@link BlockLibrary} read from folders, an in-memory set ({@link #of}) or the
 * host's own. Core alone has none ({@link #none()}): every use is refused as unknown. Where the files are found (a UI kit, pack folders and their precedence) is
 * the host's.
 */
public interface FragmentResolver {

    /** The block {@code ref} names ({@code name@N}, or {@code name} for its newest version), if there is one. */
    Optional<SutraBlocks.Block> find(String ref);

    /** The names it knows, for the words of a use that does not resolve. */
    Collection<String> names();

    /** No blocks: Core's default when no profile gives a resolver. */
    static FragmentResolver none() {
        return of(List.of());
    }

    /** An in-memory set of blocks (every version of a name kept; {@code name} alone is the newest). */
    static FragmentResolver of(Collection<SutraBlocks.Block> blocks) {
        Map<String, TreeMap<Integer, SutraBlocks.Block>> byName = new TreeMap<>();
        for (SutraBlocks.Block b : blocks) {
            byName.computeIfAbsent(b.name(), n -> new TreeMap<>()).put(b.version(), b);
        }
        return new FragmentResolver() {
            @Override
            public Optional<SutraBlocks.Block> find(String ref) {
                return SutraBlocks.find(byName, ref);
            }

            @Override
            public Collection<String> names() {
                return List.copyOf(byName.keySet());
            }
        };
    }
}
