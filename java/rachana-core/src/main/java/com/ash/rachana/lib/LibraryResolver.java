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
