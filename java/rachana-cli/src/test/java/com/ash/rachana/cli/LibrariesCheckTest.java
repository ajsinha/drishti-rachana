/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import com.ash.rachana.Rachana;
import com.ash.rachana.SutraExpressions;
import com.ash.rachana.lib.LibraryResolver;
import com.ash.rachana.lib.LibrarySet;
import com.ash.rachana.model.Sutra;
import com.ash.rachana.table.LiveScreen;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

/**
 * The shipped fragment libraries (libraries/) cannot break silently: tools/libraries/LibCheck.java compiles every example of every
 * library at language level 1.2 with no problem and no advisory and compares its ViewModel with the golden snapshot, against the
 * Rachana classes of this very build.
 */
class LibrariesCheckTest {
    @Test
    void everyLibraryExampleCompilesAndMatchesItsSnapshot() throws Exception {
        Path repo = Paths.get(System.getProperty("rachana.repo.root")).toAbsolutePath().normalize();
        Path libs = repo.resolve("libraries");
        Path check = repo.resolve("tools/libraries/LibCheck.java");
        assertTrue(Files.isDirectory(libs) && Files.isRegularFile(check), "libraries/ or LibCheck.java missing under " + repo);
        String java = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        Process p = new ProcessBuilder(java, "-cp", classpath(), check.toString(), libs.toString())
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        assertEquals(0, p.waitFor(), out);
    }

    /** The Rachana classes of this build, from where they were loaded (the surefire class path may be a manifest-only jar). */
    private static String classpath() throws Exception {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        for (Class<?> c : List.of(com.ash.rachana.api.Node.class, Rachana.class, com.ash.rachana.view.ViewJson.class, LiveScreen.class, LibraryResolver.class,
                com.ash.rachana.SutraException.class, com.ash.rachana.el.ElCompiler.class)) {
            out.add(Paths.get(c.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
        }
        return String.join(java.io.File.pathSeparator, out);
    }

    private static Path library(Path root, String name, String licence) throws Exception {
        Path dir = root.resolve(name);
        Files.createDirectories(dir.resolve("fragments"));
        Files.writeString(dir.resolve(name + ".rachana-lib.yaml"), MainTest.MANIFEST.replace("library: demo", "library: " + name));
        Files.writeString(dir.resolve("fragments/figure.v1.fragment.yaml"), MainTest.FIGURE);
        Files.writeString(dir.resolve("LICENSE"), licence);
        assertEquals(0, Main.run(new String[] {"lib", "build", dir.toString()}));
        return dir;
    }

    /** Users add fragments without touching or rebuilding Rachana: an external folder and an external .rlib sit beside bundled core. */
    @Test
    void externalFolderAndArchiveLibrariesWorkBesideTheBundledCore(@TempDir Path tmp) throws Exception {
        Path repo = Paths.get(System.getProperty("rachana.repo.root")).toAbsolutePath().normalize();
        Path folders = tmp.resolve("folders");
        library(folders, "extfolder", "Tests only.\n");
        Path packed = library(tmp.resolve("src"), "extpack", "Tests only.\n");
        assertEquals(0, Main.run(new String[] {"lib", "pack", packed.toString(), "--out", tmp.resolve("rlibs").toString()}));
        LibraryResolver all = LibraryResolver.first(List.of(LibraryResolver.folder(repo.resolve("libraries")),
                LibraryResolver.folder(folders), LibraryResolver.archives(tmp.resolve("rlibs"))));
        String sutra = """
                rachana: 1
                sutra: mixed
                version: 1
                libraries: [core@1, extfolder@1, extpack@1]
                title: { id: "'Mixed'" }
                data:
                  sales: { rows: [ { REGION: North, REVENUE_USD: 1200 } ] }
                panels:
                  - use: core/key-figure@1
                    with: { value: "sum($.sales, 'REVENUE_USD')", label: Total, fmt: amount0 }
                  - use: extfolder/figure@1
                    as: fromfolder
                    with: { value: "sum($.sales, 'REVENUE_USD')" }
                  - use: extpack/figure@1
                    as: other
                    with: { value: "sum($.sales, 'REVENUE_USD')" }
                """;
        Rachana rachana = Rachana.core().language("1.2").libraries(LibrarySet.of(all));
        Sutra parsed = rachana.parser().parse(sutra, "mixed.sutra.yaml", "library");
        assertEquals(List.of(), new SutraExpressions(rachana.compiler()).check(parsed));
        assertEquals(List.of(), parsed.advisories());
        String view = com.ash.rachana.view.ViewJson.write(new LiveScreen(rachana, parsed, LocalDate.of(2026, 1, 15), null, Map.of()).view());
        assertTrue(view.contains("1200") || view.contains("1,200"), view);
    }

    /** A same-name library with other contents is refused, never silently shadowing the bundled one. */
    @Test
    void aSameNameLibraryWithOtherContentsIsRefused(@TempDir Path tmp) throws Exception {
        Path repo = Paths.get(System.getProperty("rachana.repo.root")).toAbsolutePath().normalize();
        Path other = tmp.resolve("other");
        Files.createDirectories(other);
        Files.writeString(other.resolve("x"), "");
        Path core = tmp.resolve("ext/core");
        Files.createDirectories(core.getParent());
        copyTree(repo.resolve("libraries/core"), core);
        Files.writeString(core.resolve("LICENSE"), "changed\n");
        assertEquals(0, Main.run(new String[] {"lib", "build", core.toString()}));
        LibraryResolver both = LibraryResolver.first(List.of(LibraryResolver.folder(repo.resolve("libraries")), LibraryResolver.folder(tmp.resolve("ext"))));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> both.resolve("core", "1"));
        assertTrue(e.getMessage().contains("different contents"), e.getMessage());
    }

    private static void copyTree(Path from, Path to) throws java.io.IOException {
        try (var s = Files.walk(from)) {
            for (Path p : s.toList()) {
                Path t = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(t); else Files.copy(p, t);
            }
        }
    }
}
