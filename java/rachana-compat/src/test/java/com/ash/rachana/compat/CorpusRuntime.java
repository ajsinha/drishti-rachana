/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.compat;

import java.nio.file.Path;
import java.util.Map;

/**
 * What the corpus test runs the corpus's Sutras through: compile every file of the corpus (blocks and the UI kit as the corpus gives
 * them) and write the per-file documents the corpus holds under {@code compiled/}, into a folder, same relative paths, same bytes.
 * Two implementations: Core's own compiler once v0.9.0 has moved it here, and until then the reference bridge over Drishti's jars.
 */
interface CorpusRuntime {
    String name();

    /** Writes {@code compiled/<file>.json} for every Sutra of the corpus under {@code out}. */
    void compileAll(Corpus corpus, Path out) throws Exception;

    /**
     * The runtime named by {@code -Drachana.compat.runtime}: {@code core}, {@code bridge}, or {@code auto} (core when it can compile,
     * else the bridge when {@code -Drachana.reference.classpath} names Drishti's jars, else none).
     */
    static CorpusRuntime choose(Map<String, String> props) {
        String want = props.getOrDefault("rachana.compat.runtime", "auto");
        boolean coreReady = false; // flips when rachana-core exposes the compile entry (plan A13); then CoreRuntime is wired here
        if (want.equals("core") && !coreReady) throw new IllegalStateException("rachana-core cannot compile Sutras yet (v0.9.0 moves the compiler)");
        if (want.equals("bridge") || want.equals("auto")) {
            String cp = props.getOrDefault("rachana.reference.classpath", "");
            if (!cp.isBlank()) return new ReferenceBridgeRuntime(cp);
        }
        return null;
    }
}
