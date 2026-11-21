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

    /** The quality gates LG1 to LG5 and LG8 (LIBRARIES.md 15.2) hold for every bundled library, by the one implementation `rachana lib gate` runs. */
    @Test
    void everyBundledLibraryPassesTheQualityGates() throws Exception {
        Path libs = Paths.get(System.getProperty("rachana.repo.root")).toAbsolutePath().normalize().resolve("libraries");
        for (String name : com.ash.rachana.tools.LibraryGates.libraries(libs)) {
            for (var r : com.ash.rachana.tools.LibraryGates.run(libs, name, false)) {
                assertEquals(List.of(), r.failures(), name + " " + r.gate() + " " + r.title());
            }
        }
    }

    /** A released library is protected: an edited golden (LG2), a removed parameter or column (LG5) and a size regression (LG8) each fail. */
    @Test
    void theFrozenBaselineCatchesBreakingChanges(@TempDir Path tmp) throws Exception {
        Path repo = Paths.get(System.getProperty("rachana.repo.root")).toAbsolutePath().normalize();
        Path root = tmp.resolve("libs");
        Path core = root.resolve("core");
        copyTree(repo.resolve("libraries/core"), core);
        assertEquals(0, Main.run(new String[] {"lib", "build", core.toString()}));
        assertEquals(0, Main.run(new String[] {"lib", "freeze", core.toString()}));
        assertEquals(0, Main.run(new String[] {"lib", "build", core.toString()}));
        assertEquals(0, Main.run(new String[] {"lib", "gate", core.toString()}));
        assertTrue(Files.isRegularFile(core.resolve("FROZEN.sha384")) && Files.isRegularFile(core.resolve("released/ranked-bars.v1.interface.json")));
        // LG5: the contract column "value" of ranked-bars is renamed
        Path f = core.resolve("fragments/ranked-bars.v1.fragment.yaml");
        String original = Files.readString(f);
        Files.writeString(f, original.replace("      value: { type: number, doc: The bar's value }", "      amount: { type: number, doc: The bar's value }"));
        assertEquals(0, Main.run(new String[] {"lib", "build", core.toString()}));
        var lg5 = com.ash.rachana.tools.LibraryGates.run(root, "core", false).stream().filter(r -> r.gate().equals("LG5")).findFirst().orElseThrow();
        assertTrue(lg5.failures().stream().anyMatch(m -> m.contains("ranked-bars") && m.contains("value")), lg5.failures().toString());
        Files.writeString(f, original);
        // LG2: a released golden is edited
        Path g = core.resolve("goldens/key-figure.viewmodel.json");
        String golden = Files.readString(g);
        Files.writeString(g, golden + " ");
        assertEquals(0, Main.run(new String[] {"lib", "build", core.toString()}));
        var lg2 = com.ash.rachana.tools.LibraryGates.run(root, "core", false).stream().filter(r -> r.gate().equals("LG2")).findFirst().orElseThrow();
        assertTrue(lg2.failures().stream().anyMatch(m -> m.contains("key-figure") && m.contains("edited")), lg2.failures().toString());
        Files.writeString(g, golden);
        // LG8: a declared budget the example exceeds
        Path k = core.resolve("fragments/key-figure.v1.fragment.yaml");
        Files.writeString(k, Files.readString(k) + "budget: { panels: 0 }\n");
        assertEquals(0, Main.run(new String[] {"lib", "build", core.toString()}));
        var lg8 = com.ash.rachana.tools.LibraryGates.run(root, "core", false).stream().filter(r -> r.gate().equals("LG8")).findFirst().orElseThrow();
        assertTrue(lg8.failures().stream().anyMatch(m -> m.contains("key-figure") && m.contains("budget")), lg8.failures().toString());
    }

    /** `lib build` writes the whole interface of each fragment into `exports`. */
    @Test
    void exportsCarryTheFragmentMetadata() throws Exception {
        Path repo = Paths.get(System.getProperty("rachana.repo.root")).toAbsolutePath().normalize();
        String m = Files.readString(repo.resolve("libraries/core/core.rachana-lib.yaml"));
        assertTrue(m.contains("\"summary\"") && m.contains("\"params\"") && m.contains("\"events\"") && m.contains("\"contract\""), "exports lack metadata");
    }

    /** The Rachana classes of this build, from where they were loaded (the surefire class path may be a manifest-only jar). */
    private static String classpath() throws Exception {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        for (Class<?> c : List.of(com.ash.rachana.api.Node.class, Rachana.class, com.ash.rachana.view.ViewJson.class, LiveScreen.class, LibraryResolver.class,
                com.ash.rachana.SutraException.class, com.ash.rachana.el.ElCompiler.class,
                com.ash.rachana.tools.LibraryGates.class)) {
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
                rachana: 1.2
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
