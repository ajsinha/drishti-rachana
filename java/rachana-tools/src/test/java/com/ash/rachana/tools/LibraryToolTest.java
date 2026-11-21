/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.tools;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ash.rachana.Rachana;
import com.ash.rachana.SutraException;
import com.ash.rachana.SutraProblem;
import com.ash.rachana.lib.Library;
import com.ash.rachana.lib.LibraryBundle;
import com.ash.rachana.lib.LibraryLock;
import com.ash.rachana.lib.LibraryResolver;
import com.ash.rachana.lib.LibrarySet;
import com.ash.rachana.lib.Rlib;
import com.ash.rachana.lib.Sha384;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The library machinery end to end on the JVM: hashes, the archive, build, sign, pack, verify, resolvers, lock and a compile. */
class LibraryToolTest {

    static final String MANIFEST = """
            rachana: 1
            library: demo
            version: 1.2.0
            title: Demo
            summary: A tiny synthetic library for the tests.
            publisher: { name: Tests, key: %s }
            licence: LicenseRef-Rachana-Test
            scope: public
            profiles: []
            depends: []
            """;
    static final String FIGURE = """
            rachana: 1
            fragment: figure
            version: 1
            doc: { summary: One figure. }
            params:
              value: { type: number, required: true, doc: The figure }
              label: { type: text, default: Value, doc: Its label }
            panels:
              - { id: figure, kind: metric, label: arg.label, value: arg.value }
            """;

    @Test
    void sha384IsTheStandardOne() throws Exception {
        Random r = new Random(7);
        for (int n : new int[] {0, 1, 111, 112, 127, 128, 129, 1000, 70_000}) {
            byte[] b = new byte[n];
            r.nextBytes(b);
            assertArrayEquals(MessageDigest.getInstance("SHA-384").digest(b), Sha384.digest(b), "length " + n);
        }
    }

    @Test
    void theArchiveIsDeterministicAndAnyZipToolReadsIt() throws Exception {
        Map<String, byte[]> files = new TreeMap<>(Map.of("b.yaml", "b: 1\n".getBytes(StandardCharsets.UTF_8), "a/x.json", "{}".getBytes(StandardCharsets.UTF_8)));
        byte[] one = Rlib.write(files);
        assertArrayEquals(one, Rlib.write(new TreeMap<>(files)));
        Map<String, byte[]> back = Rlib.read(one);
        assertEquals(files.keySet(), back.keySet());
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(one))) {
            ZipEntry e = z.getNextEntry();
            assertEquals("a/x.json", e.getName());
            assertArrayEquals("{}".getBytes(StandardCharsets.UTF_8), z.readAllBytes());
        }
        one[one.length - 30] ^= 1;
        assertThrows(IllegalArgumentException.class, () -> LibraryBundle.archive("demo", tamper(one), "x"));
    }

    private static byte[] tamper(byte[] b) {
        return b;
    }

    @Test
    void buildSignPackVerifyLockAndCompile(@TempDir Path tmp) throws Exception {
        String[] keys = Ed25519.newKeyPair();
        Path dir = library(tmp.resolve("src"), keys[0]);
        LibraryTool.build(dir);
        assertTrue(Files.readString(dir.resolve("demo.rachana-lib.yaml")).contains("- { name: figure, majors: [1] }"));
        assertEquals(List.of(), LibraryTool.verify(dir, Library.Trust.NONE));

        Library.Trust strict = new Library.Trust(Ed25519.VERIFIER, Set.of(keys[0]), true);
        assertTrue(codes(LibraryTool.verify(dir, strict)).contains("RCH-2113"), "unsigned refused when signatures are required");
        LibraryTool.sign(dir, keys[0], keys[1]);
        assertEquals(List.of(), LibraryTool.verify(dir, strict));
        String[] other = Ed25519.newKeyPair();
        assertTrue(codes(LibraryTool.verify(dir, new Library.Trust(Ed25519.VERIFIER, Set.of(other[0]), true))).contains("RCH-2113"),
                "a publisher the host does not trust is refused");

        Path rlib = LibraryTool.pack(dir, tmp.resolve("out"), strict);
        assertEquals("demo-1.2.0.rlib", rlib.getFileName().toString());
        assertEquals(List.of(), LibraryTool.verify(rlib, strict));
        LibraryLock.Entry entry = LibraryTool.lockEntry(rlib, strict);
        assertEquals(keys[0], entry.signedBy());
        assertEquals(LibraryTool.lockEntry(dir, strict).sha384(), entry.sha384(), "a folder and its packed archive have one identity");
        LibraryLock lock = LibraryLock.read(LibraryLock.of(List.of(entry)).write());
        assertEquals(entry.sha384(), lock.get("demo").sha384());

        Files.writeString(dir.resolve("LICENSE"), "changed\n");
        assertTrue(codes(LibraryTool.verify(dir, strict)).contains("RCH-2113"), "a changed file breaks the hashes");

        LibrarySet set = LibrarySet.of(LibraryResolver.archives(tmp.resolve("out"))).locked(lock).trusting(strict);
        Rachana r = Rachana.core().language("1.2").libraries(set);
        var sutra = r.parser().parse("""
                rachana: 1
                sutra: demo
                version: 1
                libraries: [demo@1]
                panels:
                  - { use: demo/figure@1, with: { value: 3, label: Three } }
                """, "demo.sutra.yaml", "test");
        assertEquals("figure", sutra.panels().get(0).id());
    }

    @Test
    void resolversAgreeOrSaySo(@TempDir Path tmp) throws Exception {
        Path dir = library(tmp.resolve("libs"), null);
        LibraryTool.build(dir);
        LibraryBundle a = LibraryResolver.folder(tmp.resolve("libs")).resolve("demo", "1").orElseThrow();
        assertEquals("1.2.0", a.version());
        assertTrue(LibraryResolver.folder(tmp.resolve("libs")).resolve("demo", "2").isEmpty());
        Map<String, byte[]> changed = new TreeMap<>(a.files());
        changed.put("LICENSE", "other\n".getBytes(StandardCharsets.UTF_8));
        LibraryResolver both = LibraryResolver.first(List.of(LibraryResolver.of(List.of(a)), LibraryResolver.of(List.of(LibraryBundle.ofBytes("demo", changed, "mem")))));
        assertThrows(IllegalStateException.class, () -> both.resolve("demo", "1"));
        SutraException e = assertThrows(SutraException.class, () -> LibrarySet.of(both).load("demo", 1));
        assertEquals("RCH-2113", e.problems().get(0).code());
    }

    @Test
    void classpathAndPackageResolversFindTheSameBytes(@TempDir Path tmp) throws Exception {
        Path dir = library(tmp.resolve("libs"), null);
        LibraryTool.build(dir);
        String sha = LibraryResolver.folder(tmp.resolve("libs")).resolve("demo", "1").orElseThrow().sha384();
        Path jar = tmp.resolve("jar");                                   // a jar's layout: META-INF/rachana/libs/demo/ and the index
        copy(dir, jar.resolve(LibraryResolver.CLASSPATH_ROOT + "demo"));
        Files.writeString(jar.resolve(LibraryResolver.CLASSPATH_INDEX), "demo\n");
        try (java.net.URLClassLoader cl = new java.net.URLClassLoader(new java.net.URL[] {jar.toUri().toURL()}, null)) {
            LibraryResolver cp = LibraryResolver.classpath(cl);
            assertEquals(sha, cp.resolve("demo", "1").orElseThrow().sha384());
            assertEquals("demo 1.2.0", cp.list().get(0).name() + " " + cp.list().get(0).version());
            assertTrue(cp.resolve("other", "1").isEmpty());
        }
        Path nm = tmp.resolve("node_modules");                          // npm: package.json declares the library and its folder
        copy(dir, nm.resolve("@drishti-rachana/lib-demo/lib"));
        Files.writeString(nm.resolve("@drishti-rachana/lib-demo/package.json"), "{\"name\": \"@drishti-rachana/lib-demo\", \"rachana\": {\"library\": \"demo\"}}");
        assertEquals(sha, LibraryResolver.packages(List.of(nm)).resolve("demo", "1").orElseThrow().sha384());
        Path site = tmp.resolve("site-packages");                       // PyPI: the entry point group rachana.libraries
        copy(dir, site.resolve("drishti_rachana_lib_demo/library"));
        Files.createDirectories(site.resolve("drishti_rachana_lib_demo-1.2.0.dist-info"));
        Files.writeString(site.resolve("drishti_rachana_lib_demo-1.2.0.dist-info/entry_points.txt"),
                "[console_scripts]\nx = y:z\n\n[rachana.libraries]\ndemo = drishti_rachana_lib_demo.library\n");
        LibraryResolver py = LibraryResolver.packages(List.of(site));
        assertEquals(sha, py.resolve("demo", "1").orElseThrow().sha384());
        assertEquals(1, py.list().size());
    }

    private static void copy(Path from, Path to) throws Exception {
        try (var s = Files.walk(from)) {
            for (Path p : s.toList()) {
                Path t = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(t);
                } else {
                    Files.createDirectories(t.getParent());
                    Files.copy(p, t);
                }
            }
        }
    }

    private static Path library(Path root, String key) throws Exception {
        Path dir = root.resolve("demo");
        Files.createDirectories(dir.resolve("fragments"));
        Files.writeString(dir.resolve("demo.rachana-lib.yaml"), MANIFEST.formatted(key == null ? "null" : key).replace("key: null", "key: ~"));
        Files.writeString(dir.resolve("fragments/figure.v1.fragment.yaml"), FIGURE);
        Files.writeString(dir.resolve("LICENSE"), "Tests only.\n");
        return dir;
    }

    private static List<String> codes(List<SutraProblem> ps) {
        return ps.stream().map(SutraProblem::code).toList();
    }

    @Test
    void ed25519RejectsGarbage() {
        assertFalse(Ed25519.VERIFIER.verify("ed25519:00", new byte[1], new byte[64]));
    }
}
