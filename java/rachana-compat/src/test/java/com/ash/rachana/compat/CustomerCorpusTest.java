/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.compat;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * CG3, the customer corpus (SUTRA_EXPANSION 6.5, 6.6). Per corpus under compat/customers/: it is exactly as exported (manifest); every
 * Sutra and block it lists is present; every Sutra compiles, with the corpus's blocks, to the recorded problem list, canonical model
 * text and plan, byte for byte (this is the part that needs a runtime: Core's compiler, until v0.9.0 the reference bridge); the
 * recorded binds replay through a RecordedHost (none before v0.10.0). No re-record flag exists here: a corpus arrives only from a release
 * of the customer.
 */
class CustomerCorpusTest {

    @Test
    void thereIsAtLeastOneCorpus() throws Exception {
        assertFalse(Corpus.all().isEmpty(), "compat/customers/ holds no corpus");
    }

    @Test
    void everyCorpusIsExactlyAsExported() throws Exception {
        for (Corpus c : Corpus.all()) assertEquals(List.of(), c.manifestDifferences(), c.name() + " differs from its manifest");
    }

    @Test
    void everyListedFileIsPresentWithItsCompiledDocument() throws Exception {
        for (Corpus c : Corpus.all()) {
            Map<String, Object> groups = Json.object(Json.parse(Files.readString(c.dir().resolve("groups.json"))));
            int sutras = 0;
            for (Object g : Json.array(groups.get("groups"))) {
                Map<String, Object> m = Json.object(g);
                for (Object f : Json.array(m.get("sutras"))) {
                    sutras++;
                    assertTrue(Files.isRegularFile(c.dir().resolve("sutras").resolve((String) f)), c.name() + " lacks " + f);
                    assertTrue(Files.isRegularFile(c.dir().resolve("compiled").resolve(f + ".json")), c.name() + " has no compiled document for " + f);
                }
                for (Object f : Json.array(m.get("blocks"))) assertTrue(Files.isRegularFile(c.dir().resolve("blocks").resolve((String) f)), c.name() + " lacks " + f);
            }
            Map<String, Object> v = Json.object(Json.parse(Files.readString(c.dir().resolve("VERSION.json"))));
            assertEquals(((java.math.BigDecimal) v.get("sutras")).intValue(), sutras, c.name() + " VERSION.json count");
        }
    }

    @Test
    void theShippedSutrasCompileToTheRecordedDocuments() throws Exception {
        CorpusRuntime rt = CorpusRuntime.choose(Map.of("rachana.compat.runtime", System.getProperty("rachana.compat.runtime", "auto"),
                "rachana.reference.classpath", System.getProperty("rachana.reference.classpath", "")));
        assumeTrue(rt != null, "no runtime: Core cannot compile yet and -Drachana.reference.classpath is not set (the first replay is in CG8)");
        for (Corpus c : Corpus.all()) {
            Path out = Files.createTempDirectory("rachana-compat-out-");
            try {
                rt.compileAll(c, out);
                List<String> diffs = new ArrayList<>();
                Path recorded = c.dir().resolve("compiled");
                try (Stream<Path> s = Files.walk(recorded)) {
                    for (Path f : s.filter(Files::isRegularFile).toList()) {
                        Path rel = recorded.relativize(f);
                        Path got = out.resolve("compiled").resolve(rel.toString());
                        if (!Files.exists(got)) diffs.add("not produced: " + rel);
                        else if (!Arrays.equals(Files.readAllBytes(f), Files.readAllBytes(got))) diffs.add("differs: " + rel);
                    }
                }
                try (Stream<Path> s = Files.walk(out.resolve("compiled"))) {
                    for (Path f : s.filter(Files::isRegularFile).toList())
                        if (!Files.exists(recorded.resolve(out.resolve("compiled").relativize(f).toString()))) diffs.add("not recorded: " + f.getFileName());
                }
                assertEquals(List.of(), diffs, c.name() + " via " + rt.name());
            } finally {
                ReferenceBridgeRuntime.deleteTree(out);
            }
        }
    }

    @Test
    void recordedBindsReplayThroughARecordedHost() throws Exception {
        for (Corpus c : Corpus.all()) {
            Path binds = c.dir().resolve("binds");
            if (!Files.isDirectory(binds)) continue;   // the binder moves into Core at v0.10.0; until then CG8 covers binding
            try (Stream<Path> s = Files.list(binds)) {
                for (Path f : s.filter(p -> p.toString().endsWith(".json")).toList()) {
                    Map<String, Object> bind = Json.object(Json.parse(Files.readString(f)));
                    RecordedHost host = RecordedHost.fromJson(Json.canonical(Map.of("calls", bind.get("hostCalls"))));
                    assertNotNull(host, f.toString());
                    fail(f + ": a bind is recorded but Core's binder is not wired to the corpus test yet");
                }
            }
        }
    }

    @Test
    void theDrishtiProfileCaseFilesAreInTheCorpus() throws Exception {
        for (Corpus c : Corpus.all()) {
            for (String kind : List.of("cases", "problems")) {
                Path d = c.dir().resolve(kind);
                assertTrue(Files.isDirectory(d), c.name() + " has no " + kind + "/");
                try (Stream<Path> s = Files.walk(d)) {
                    for (Path f : s.filter(Files::isRegularFile).toList()) {
                        String head = String.join("\n", Files.readAllLines(f).stream().limit(12).toList());
                        assertTrue(head.matches("(?s).*\\nprofile:.*drishti.*"), f + " is not a Drishti-profile case file");
                    }
                }
            }
        }
    }
}
