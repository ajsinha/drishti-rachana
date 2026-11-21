/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.lib;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Where a library is found (LIBRARIES.md 10, EL_EXPANSION X42): a host hook, consulted only for what the host configured. No shipped
 * resolver opens a network connection; fetching is a separate, explicit step ({@code rachana lib add}, {@code restore}). Shipped:
 * {@link #of} (memory: tests, the playground, a host's own store), {@link #folder} ({@code rachana_libs/<name>/}), {@link #archives}
 * (a folder of {@code .rlib} files) and {@link #first} (several, in order). The classpath and package resolvers come with the carriers
 * (LIBRARIES.md 11.2).
 */
public interface LibraryResolver {

    /**
     * The library of this name satisfying the constraint, or empty.
     *
     * @param constraint a major ({@code 1}: the newest {@code 1.x.y} it has) or an exact version ({@code 1.4.2}, as a lock pins it)
     */
    Optional<LibraryBundle> resolve(String name, String constraint);

    /** What it can offer: for the palette, the AI kit and {@code rachana lib list}. */
    List<LibraryRef> list();

    /** No libraries. */
    static LibraryResolver none() {
        return of(List.of());
    }

    /** An in-memory set of library versions. */
    static LibraryResolver of(Collection<LibraryBundle> bundles) {
        List<LibraryBundle> all = List.copyOf(bundles);
        return new LibraryResolver() {
            @Override
            public Optional<LibraryBundle> resolve(String name, String constraint) {
                return best(all.stream().filter(b -> b.name().equals(name)).toList(), constraint);
            }

            @Override
            public List<LibraryRef> list() {
                return all.stream().map(b -> new LibraryRef(b.name(), b.version(), b.source())).toList();
            }
        };
    }

    /** Unpacked libraries: {@code <dir>/<name>/} holds one library version (the folder beside the lock is {@code rachana_libs}). */
    static LibraryResolver folder(Path dir) {
        return new LibraryResolver() {
            @Override
            public Optional<LibraryBundle> resolve(String name, String constraint) {
                Path d = dir.resolve(name);
                if (!name.matches("[a-z][a-z0-9-]{0,31}") || !Files.isDirectory(d)) {
                    return Optional.empty();
                }
                return best(List.of(LibraryBundle.ofBytes(name, read(d), "folder:" + d)), constraint);
            }

            @Override
            public List<LibraryRef> list() {
                List<LibraryRef> out = new ArrayList<>();
                try (Stream<Path> s = Files.isDirectory(dir) ? Files.list(dir) : Stream.empty()) {
                    for (Path d : s.filter(Files::isDirectory).sorted().toList()) {
                        String n = d.getFileName().toString();
                        resolve(n, "").ifPresent(b -> out.add(new LibraryRef(n, b.version(), b.source())));
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                return out;
            }
        };
    }

    /** Packed libraries: {@code <dir>/<name>-<version>.rlib}. */
    static LibraryResolver archives(Path dir) {
        return new LibraryResolver() {
            @Override
            public Optional<LibraryBundle> resolve(String name, String constraint) {
                List<LibraryBundle> found = new ArrayList<>();
                for (LibraryRef r : list()) {
                    if (r.name().equals(name) && Versions.matches(r.version(), constraint)) {
                        try {
                            found.add(LibraryBundle.archive(name, Files.readAllBytes(dir.resolve(name + "-" + r.version() + ".rlib")), r.source()));
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                }
                return best(found, constraint);
            }

            @Override
            public List<LibraryRef> list() {
                List<LibraryRef> out = new ArrayList<>();
                try (Stream<Path> s = Files.isDirectory(dir) ? Files.list(dir) : Stream.empty()) {
                    for (Path f : s.sorted().toList()) {
                        java.util.regex.Matcher m = java.util.regex.Pattern.compile("([a-z][a-z0-9-]{0,31})-([0-9]+\\.[0-9]+\\.[0-9]+)\\.rlib")
                                .matcher(f.getFileName().toString());
                        if (m.matches()) {
                            out.add(new LibraryRef(m.group(1), m.group(2), "release:" + f.getFileName()));
                        }
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                return out;
            }
        };
    }

    /**
     * Several resolvers in the host's order: the first that has the name answers. The same name and version found by two of them with
     * different bytes is never a silent choice.
     *
     * @throws IllegalStateException from {@code resolve} when two resolvers hold different bytes for the version chosen
     */
    static LibraryResolver first(List<LibraryResolver> resolvers) {
        List<LibraryResolver> all = List.copyOf(resolvers);
        return new LibraryResolver() {
            @Override
            public Optional<LibraryBundle> resolve(String name, String constraint) {
                LibraryBundle chosen = null;
                for (LibraryResolver r : all) {
                    Optional<LibraryBundle> b = r.resolve(name, constraint);
                    if (b.isEmpty()) {
                        continue;
                    }
                    if (chosen == null) {
                        chosen = b.get();
                    } else if (java.util.Objects.equals(chosen.version(), b.get().version()) && !chosen.sha384().equals(b.get().sha384())) {
                        throw new IllegalStateException("library " + name + " " + chosen.version() + " is in " + chosen.source() + " and in "
                                + b.get().source() + " with different contents");
                    }
                }
                return Optional.ofNullable(chosen);
            }

            @Override
            public List<LibraryRef> list() {
                List<LibraryRef> out = new ArrayList<>();
                all.forEach(r -> out.addAll(r.list()));
                return out;
            }
        };
    }

    /** The resource every library jar lists its libraries in, one name per line (the classpath resolver's index). */
    String CLASSPATH_INDEX = "META-INF/rachana/libraries";
    /** Where a jar holds library {@code <name>}: {@code META-INF/rachana/libs/<name>/}, the folder of section 3.1. */
    String CLASSPATH_ROOT = "META-INF/rachana/libs/";

    /**
     * Libraries in jars (LIBRARIES.md 10, 11.2: the Maven carrier): {@code META-INF/rachana/libs/<name>/} holds the library's folder; its
     * {@code MANIFEST.sha384} names every file, so nothing is listed from the jar itself; {@code META-INF/rachana/libraries} lists the
     * names a jar carries (for {@link #list()}). No I/O beyond the class loader.
     */
    static LibraryResolver classpath(ClassLoader loader) {
        return new LibraryResolver() {
            @Override
            public Optional<LibraryBundle> resolve(String name, String constraint) {
                if (!name.matches("[a-z][a-z0-9-]{0,31}")) {
                    return Optional.empty();
                }
                String root = CLASSPATH_ROOT + name + "/";
                byte[] manifest = resource(loader, root + Library.HASHES);
                if (manifest == null) {
                    return Optional.empty();
                }
                Map<String, byte[]> files = new java.util.TreeMap<>();
                files.put(Library.HASHES, manifest);
                for (String line : new String(manifest, java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
                    int at = line.indexOf("  ");
                    if (at > 0) {
                        String path = line.substring(at + 2).trim();
                        byte[] b = resource(loader, root + path);
                        if (b == null) {
                            throw new IllegalStateException("library " + name + " in the class path lacks " + path + ", which its " + Library.HASHES + " names");
                        }
                        files.put(path, b);
                    }
                }
                byte[] sig = resource(loader, root + Library.SIGNATURE);
                if (sig != null) {
                    files.put(Library.SIGNATURE, sig);
                }
                return best(List.of(LibraryBundle.ofBytes(name, files, "classpath:" + root)), constraint);
            }

            @Override
            public List<LibraryRef> list() {
                java.util.TreeSet<String> names = new java.util.TreeSet<>();
                try {
                    for (java.net.URL u : java.util.Collections.list(loader.getResources(CLASSPATH_INDEX))) {
                        try (java.io.InputStream in = u.openStream()) {
                            for (String n : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
                                if (!n.isBlank()) {
                                    names.add(n.trim());
                                }
                            }
                        }
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                List<LibraryRef> out = new ArrayList<>();
                names.forEach(n -> resolve(n, "").ifPresent(b -> out.add(new LibraryRef(n, b.version(), b.source()))));
                return out;
            }
        };
    }

    /**
     * Libraries installed as packages (LIBRARIES.md 10, 11.2: the npm and PyPI carriers). Each root is a {@code node_modules} folder or a
     * Python {@code site-packages} folder. An npm package declares {@code "rachana": { "library": "<name>", "folder": "<path>" }} in its
     * {@code package.json} (the folder relative to the package, default {@code lib}); a Python distribution declares the entry point group
     * {@code rachana.libraries} in its {@code *.dist-info/entry_points.txt}, {@code <name> = <package.module>}, the module's folder being
     * the library's folder. Nothing is run: both are read as data.
     */
    static LibraryResolver packages(List<Path> roots) {
        List<Path> all = List.copyOf(roots);
        return new LibraryResolver() {
            Map<String, Path> found() {
                Map<String, Path> out = new java.util.TreeMap<>();
                for (Path r : all) {
                    npm(r, out);
                    python(r, out);
                }
                return out;
            }

            @Override
            public Optional<LibraryBundle> resolve(String name, String constraint) {
                Path d = found().get(name);
                return d == null ? Optional.empty() : best(List.of(LibraryBundle.ofBytes(name, read(d), "package:" + d)), constraint);
            }

            @Override
            public List<LibraryRef> list() {
                List<LibraryRef> out = new ArrayList<>();
                found().forEach((n, d) -> resolve(n, "").ifPresent(b -> out.add(new LibraryRef(n, b.version(), b.source()))));
                return out;
            }
        };
    }

    private static byte[] resource(ClassLoader loader, String path) {
        try (java.io.InputStream in = loader.getResourceAsStream(path)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void npm(Path root, Map<String, Path> out) {
        List<Path> pkgs = new ArrayList<>();
        try (Stream<Path> s = Files.isDirectory(root) ? Files.list(root) : Stream.empty()) {
            for (Path p : s.sorted().toList()) {
                if (p.getFileName().toString().startsWith("@") && Files.isDirectory(p)) {
                    try (Stream<Path> scoped = Files.list(p)) {
                        pkgs.addAll(scoped.sorted().toList());
                    }
                } else {
                    pkgs.add(p);
                }
            }
            for (Path p : pkgs) {
                Path json = p.resolve("package.json");
                if (!Files.isRegularFile(json)) {
                    continue;
                }
                Object doc = com.ash.rachana.parse.Json.read(Files.readString(json));
                if (doc instanceof Map<?, ?> m && m.get("rachana") instanceof Map<?, ?> r && r.get("library") instanceof String n) {
                    Path dir = p.resolve(r.get("folder") instanceof String f ? f : "lib").normalize();
                    if (dir.startsWith(p) && Files.isDirectory(dir)) {
                        out.putIfAbsent(n, dir);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void python(Path root, Map<String, Path> out) {
        try (Stream<Path> s = Files.isDirectory(root) ? Files.list(root) : Stream.empty()) {
            for (Path info : s.filter(p -> p.getFileName().toString().endsWith(".dist-info")).sorted().toList()) {
                Path ep = info.resolve("entry_points.txt");
                if (!Files.isRegularFile(ep)) {
                    continue;
                }
                boolean in = false;
                for (String line : Files.readAllLines(ep)) {
                    String t = line.trim();
                    if (t.startsWith("[")) {
                        in = t.equals("[rachana.libraries]");
                    } else if (in && t.contains("=")) {
                        String n = t.substring(0, t.indexOf('=')).trim();
                        String module = t.substring(t.indexOf('=') + 1).trim().replaceAll(":.*", "");
                        Path dir = root.resolve(module.replace('.', '/')).normalize();
                        if (dir.startsWith(root) && Files.isDirectory(dir)) {
                            out.putIfAbsent(n, dir);
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Optional<LibraryBundle> best(List<LibraryBundle> candidates, String constraint) {
        return candidates.stream().filter(b -> Versions.matches(b.version(), constraint)).max(Comparator.comparing(b -> Versions.semver(b.version())));
    }

    private static Map<String, byte[]> read(Path d) {
        Map<String, byte[]> files = new TreeMap<>();
        try (Stream<Path> s = Files.walk(d)) {
            for (Path f : s.filter(Files::isRegularFile).toList()) {
                files.put(d.relativize(f).toString().replace('\\', '/'), Files.readAllBytes(f));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return files;
    }
}
