/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The standalone server (COMPILER_AND_RUNTIMES 10) is a host at the top of the module graph: nothing depends on it, it depends on
 * rachana-live alone among the Rachana modules, and Core stays server-free (no HTTP server, HTTP client or JDBC in rachana-api or
 * rachana-core). The libraries and the Elements pack it bundles are the repository's own.
 */
class ServerModuleGuardTest {

    @Test
    void nothingDependsOnTheServer() throws Exception {
        List<String> hits = new ArrayList<>();
        try (var s = Files.list(Sources.javaRoot())) {
            for (Path dir : s.filter(Files::isDirectory).toList()) {
                if (dir.getFileName().toString().equals("rachana-server") || dir.getFileName().toString().equals("rachana-guards")) continue;
                try (var w = Files.walk(dir)) {
                    for (Path f : w.filter(p -> !p.toString().contains("/target/") && (p.toString().endsWith(".java")
                            || p.getFileName().toString().equals("pom.xml"))).toList()) {
                        String t = Files.readString(f);
                        if (t.contains("com.ash.rachana.server") || t.contains("<artifactId>rachana-server</artifactId>")) hits.add(f.toString());
                    }
                }
            }
        }
        assertTrue(hits.isEmpty(), "rachana-server is named by another module: " + hits);
    }

    @Test
    void theServerDependsOnLiveAloneAmongTheModules() throws Exception {
        String pom = Files.readString(Sources.javaRoot().resolve("rachana-server/pom.xml"));
        Matcher m = Pattern.compile("<groupId>com\\.ash\\.rachana</groupId><artifactId>([^<]+)</artifactId>").matcher(pom.replaceAll("\\s+", ""));
        List<String> deps = new ArrayList<>();
        while (m.find()) if (!m.group(1).equals("rachana-parent")) deps.add(m.group(1));
        assertEquals(List.of("rachana-live"), deps);
    }

    @Test
    void coreStaysServerFree() throws Exception {
        for (String module : List.of("rachana-api", "rachana-core")) {
            String info = Files.readString(Sources.javaRoot().resolve(module + "/src/main/java/module-info.java"));
            for (String banned : List.of("jdk.httpserver", "java.net.http", "java.sql")) {
                assertTrue(!info.contains("requires " + banned) && !info.contains("requires transitive " + banned), module + " requires " + banned);
            }
        }
    }

    @Test
    void theBundledLibrariesAreTheRepositorys() throws Exception {
        TreeSet<String> repo = new TreeSet<>();
        try (var s = Files.list(Sources.javaRoot().getParent().resolve("libraries"))) {
            s.filter(Files::isDirectory).forEach(d -> repo.add(d.getFileName().toString()));
        }
        TreeSet<String> listed = new TreeSet<>();
        for (String l : Files.readAllLines(Sources.javaRoot().resolve("rachana-server/src/main/resources/META-INF/rachana/libraries"))) {
            if (!l.isBlank()) listed.add(l.trim());
        }
        assertEquals(repo, listed, "rachana-server's META-INF/rachana/libraries lists the folders of libraries/");
    }

    @Test
    void theServerAndTheCliLoadTheSameElementsPack() throws Exception {
        Matcher pom = Pattern.compile("<rachana.elements.pack>([^<]+)</rachana.elements.pack>")
                .matcher(Files.readString(Sources.javaRoot().resolve("rachana-server/pom.xml")));
        Matcher cli = Pattern.compile("ELEMENTS_VERSION = \"([^\"]+)\"")
                .matcher(Files.readString(Sources.javaRoot().getParent().resolve("js/packages/rachana-cli/src/site.js")));
        assertTrue(pom.find() && cli.find());
        assertEquals(cli.group(1), pom.group(1));
        assertTrue(Files.isDirectory(Sources.javaRoot().getParent().resolve("js/vendor/drishti-elements/" + pom.group(1))));
    }
}
