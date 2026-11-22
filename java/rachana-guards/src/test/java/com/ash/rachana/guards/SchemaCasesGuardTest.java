/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/**
 * The published schema never drifts from the compiler: every Sutra of the conformance cases and the examples that compiles must
 * validate against spec/1/sutra.schema.json (tools/conformance/schema_cases.py). Needs python3 with PyYAML and jsonschema
 * (tools/conformance/requirements.txt); the test is skipped, and says so, on a machine without them.
 */
class SchemaCasesGuardTest {
    @Test
    void everyCompilingSutraValidatesAgainstThePublishedSchema() throws Exception {
        Path repo = Paths.get(System.getProperty("rachana.repo.root")).toAbsolutePath().normalize();
        Process probe;
        try {
            probe = new ProcessBuilder("python3", "-c", "import yaml, jsonschema").redirectErrorStream(true).start();
        } catch (java.io.IOException e) {
            assumeTrue(false, "python3 is not installed: tools/conformance/schema_cases.py was not run");
            return;
        }
        probe.getInputStream().readAllBytes();
        assumeTrue(probe.waitFor() == 0, "python3 lacks PyYAML or jsonschema (pip install -r tools/conformance/requirements.txt): schema_cases.py was not run");
        Process p = new ProcessBuilder("python3", repo.resolve("tools/conformance/schema_cases.py").toString())
                .directory(repo.toFile()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        assertEquals(0, p.waitFor(), out);
    }
}
