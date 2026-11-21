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
 * Two implementations: Core's own compiler with the conformance suite's Drishti profile ({@link CoreRuntime}, the default), and the
 * reference bridge over Drishti's jars (on request only: it compares the reference with its own recording).
 */
interface CorpusRuntime {
    String name();

    /** Writes {@code compiled/<file>.json} for every Sutra of the corpus under {@code out}. */
    void compileAll(Corpus corpus, Path out) throws Exception;

    /**
     * The runtime named by {@code -Drachana.compat.runtime}: {@code core} (the default; also for a blank value) or {@code bridge}
     * (needs {@code -Drachana.reference.classpath} naming Drishti's jars). Never null: a runtime that cannot run is a failure.
     *
     * @throws IllegalStateException for {@code bridge} without a class path, or an unknown name
     */
    static CorpusRuntime choose(Map<String, String> props) {
        String want = props.getOrDefault("rachana.compat.runtime", "core").trim();
        if (want.isEmpty() || want.equals("core")) return new CoreRuntime();
        if (want.equals("bridge")) {
            String cp = props.getOrDefault("rachana.reference.classpath", "");
            if (cp.isBlank()) throw new IllegalStateException("-Drachana.compat.runtime=bridge needs -Drachana.reference.classpath (Drishti's jars)");
            return new ReferenceBridgeRuntime(cp);
        }
        throw new IllegalStateException("unknown -Drachana.compat.runtime=" + want + " (core or bridge)");
    }
}
