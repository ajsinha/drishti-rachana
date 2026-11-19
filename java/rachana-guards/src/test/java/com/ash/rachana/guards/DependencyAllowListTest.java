/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.guards;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import org.junit.jupiter.api.Test;

/** Module poms may depend on Rachana modules and JUnit (test scope) only: no Spring, Jackson, Caffeine, logging. */
class DependencyAllowListTest {
    private static final Pattern DEP = Pattern.compile(
            "<dependency>\\s*<groupId>([^<]+)</groupId>\\s*<artifactId>([^<]+)</artifactId>(.*?)</dependency>", Pattern.DOTALL);
    private static final List<String> BANNED_PREFIXES = List.of(
            "org.springframework", "com.fasterxml", "com.github.ben-manes", "org.slf4j", "ch.qos.logback", "org.apache.logging", "org.yaml");

    /** The split exemptions (java/SPLIT_EXEMPTIONS.md): "module group:artifact" pairs allowed until the named step; the list only shrinks. */
    static Set<String> exemptions() throws Exception {
        Set<String> s = new LinkedHashSet<>();
        Matcher m = Pattern.compile("(?m)^- dependency: (\\S+) (\\S+:\\S+) until ").matcher(Files.readString(Sources.javaRoot().resolve("SPLIT_EXEMPTIONS.md")));
        while (m.find()) s.add(m.group(1) + " " + m.group(2));
        return s;
    }

    @Test
    void everyModuleDependencyIsAllowed() throws Exception {
        Set<String> exempt = exemptions(), used = new HashSet<>();
        for (Path pom : Sources.nonEmpty(Sources.files("pom.xml"))) {
            if (pom.equals(Sources.javaRoot().resolve("pom.xml"))) continue; // parent: management and enforcer only
            Matcher m = DEP.matcher(Files.readString(pom));
            while (m.find()) {
                String g = m.group(1).trim(), body = m.group(3);
                String module = pom.getParent().getFileName().toString();
                if (exempt.contains(module + " " + g + ":" + m.group(2).trim())) {
                    used.add(module + " " + g + ":" + m.group(2).trim());
                    continue;
                }
                for (String b : BANNED_PREFIXES) assertFalse(g.startsWith(b), pom + " depends on banned " + g);
                boolean rachana = g.equals("com.ash.rachana");
                boolean junitTest = g.equals("org.junit.jupiter") && body.contains("<scope>test</scope>");
                assertTrue(rachana || junitTest, pom + " has a dependency outside the allow-list: " + g + ":" + m.group(2));
            }
        }
        Set<String> stale = new LinkedHashSet<>(exempt);
        stale.removeAll(used);
        assertTrue(stale.isEmpty(), "exemptions no longer used: delete them from SPLIT_EXEMPTIONS.md: " + stale);
    }

    @Test
    void parentDeclaresNoRuntimeDependencies() throws Exception {
        String root = Files.readString(Sources.javaRoot().resolve("pom.xml"));
        String outsideManagement = root.replaceAll("(?s)<dependencyManagement>.*?</dependencyManagement>", "");
        assertFalse(outsideManagement.contains("<dependencies>"), "the parent pom must not add dependencies to every module");
    }
}
