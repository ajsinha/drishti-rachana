/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.guards;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The Drishti Elements pin is one fact (G4 rehearsal R11): the property {@code drishti.elements.version} names the one vendored pack
 * folder; Core's jar bundles {@code formats.yaml} and {@code world-regions.yaml} from that folder and from nowhere else; no source names
 * another pack version. A second pin would let a server-formatted cell and a browser-formatted one differ.
 */
class ElementsPinGuardTest {
    private static final Pattern VERSION_PATH = Pattern.compile("drishti-elements/([0-9][0-9A-Za-z.+_-]*)");

    private static Path repo() {
        return Paths.get(System.getProperty("rachana.repo.root")).toAbsolutePath().normalize();
    }

    private static String pinned() throws Exception {
        Matcher m = Pattern.compile("<drishti.elements.version>([^<]+)</drishti.elements.version>").matcher(Files.readString(repo().resolve("java/pom.xml")));
        assertTrue(m.find(), "java/pom.xml has no drishti.elements.version");
        return m.group(1);
    }

    @Test
    void oneVendoredPackAndItIsTheOneThePropertyNames() throws Exception {
        try (Stream<Path> s = Files.list(repo().resolve("js/vendor/drishti-elements"))) {
            List<String> packs = s.filter(Files::isDirectory).map(p -> p.getFileName().toString()).toList();
            assertEquals(List.of(pinned()), packs, "js/vendor/drishti-elements holds exactly the pinned pack");
        }
    }

    @Test
    void coreBundlesItsTablesFromThePinnedFolderOnly() throws Exception {
        String pom = Files.readString(repo().resolve("java/rachana-core/pom.xml"));
        assertTrue(pom.contains("js/vendor/drishti-elements/${drishti.elements.version}/data"), "core's resources come from the pinned pack's data folder");
        assertEquals(1, Pattern.compile("drishti-elements").matcher(pom).results().count(), "core's pom names the pack once");
        Path built = repo().resolve("java/rachana-core/target/classes");      // present in a reactor build, absent in an IDE before the first build
        if (Files.isDirectory(built)) {
            for (String f : List.of("formats.yaml", "world-regions.yaml")) {
                assertArrayEquals(Files.readAllBytes(repo().resolve("js/vendor/drishti-elements").resolve(pinned()).resolve("data").resolve(f)),
                        Files.readAllBytes(built.resolve(f)), "the jar's " + f + " is the pinned pack's, byte for byte");
            }
        }
    }

    @Test
    void noSourceNamesAnotherPackVersion() throws Exception {
        String pin = pinned();
        List<String> hits = new ArrayList<>();
        List<Path> roots = List.of(repo().resolve("js/packages"), repo().resolve("java"), repo().resolve("tools"), repo().resolve("python/pyproject.toml"),
                repo().resolve("python/src"), repo().resolve("examples"));
        for (Path root : roots) {
            if (!Files.exists(root)) continue;
            try (Stream<Path> s = Files.walk(root)) {
                for (Path f : s.filter(Files::isRegularFile).filter(p -> !p.toString().contains("/node_modules/") && !p.toString().contains("/target/")
                        && !p.toString().contains("/.venv/") && !p.toString().contains("/bundle/") && !p.toString().contains("/web/")
                        && !p.toString().endsWith("ElementsPinGuardTest.java")).toList()) {
                    String text;
                    try {
                        text = Files.readString(f);
                    } catch (java.nio.charset.MalformedInputException e) {
                        continue;                                           // a binary file
                    }
                    Matcher m = VERSION_PATH.matcher(text);
                    while (m.find()) {
                        if (!m.group(1).equals(pin)) hits.add(repo().relativize(f) + ": " + m.group(1));
                    }
                }
            }
        }
        assertTrue(hits.isEmpty(), "sources naming a pack version other than " + pin + ":\n" + String.join("\n", hits));
    }
}
