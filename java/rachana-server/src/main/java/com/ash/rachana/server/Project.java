/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.server;

import com.ash.rachana.Rachana;
import com.ash.rachana.SutraException;
import com.ash.rachana.SutraProblem;
import com.ash.rachana.lib.LibraryBundle;
import com.ash.rachana.lib.LibraryResolver;
import com.ash.rachana.lib.LibrarySet;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * The project folder the server serves: its Sutras ({@code sutras/**}{@code /*.sutra.yaml}, each checked at load with Core, problems
 * kept for the problems page) and the libraries its pages may use, gathered as {@code rachana build} and {@code rachana dev} gather them:
 * the project's {@code rachana_libs/}, each configured {@code libraries:} path (a library folder, an {@code .rlib}, or a folder of them),
 * then the libraries bundled in the jar. A name found twice with different contents is refused from both and reported, never silently
 * shadowed. With {@code watch}, {@link #refresh()} reloads whatever changed. Thread-safe: readers see one consistent state.
 */
final class Project {

    static final String SUFFIX = ".sutra.yaml";
    private static final Pattern MANIFEST = Pattern.compile("[^/]+\\.rachana-lib\\.yaml");
    private static final Pattern NAME = Pattern.compile("(?m)^library:\\s*\"?([^\"\\s#]+)");
    private static final Pattern VERSION = Pattern.compile("(?m)^version:\\s*\"?([^\"\\s#]+)");
    private static final Pattern USE = Pattern.compile("\\buse:\\s*[\"']?([\\w.-]+)/");
    private static final Pattern REF = Pattern.compile("(?m)(?:^|[\\s\\[,\"'-])([\\w.-]+)@\\d+");
    private static final int MAX_FILE = 2 * 1024 * 1024;

    /** One Sutra: its file, its text and its problems (empty when it compiled). */
    record Sutra(String name, Path file, String text, List<SutraProblem> problems) {}

    /** One library: its name, version, where it came from and its files. */
    record Library(String name, String version, String source, Map<String, byte[]> files) {
        String identity() {
            byte[] m = files.get("MANIFEST.sha384");
            if (m != null) {
                return new String(m, StandardCharsets.UTF_8);
            }
            StringBuilder b = new StringBuilder();
            files.forEach((k, v) -> b.append(k).append('\0').append(Arrays.hashCode(v)).append('\n'));
            return b.toString();
        }
    }

    /** What the project holds at one moment. */
    record State(Map<String, Sutra> sutras, Map<String, byte[]> libraryFiles, List<String> problems, String stamp) {}

    private final ServerConfig.Project config;
    private final List<Path> extra;
    private final Log log;
    private volatile State state;

    Project(ServerConfig.Project config, List<Path> extra, Log log) {
        this.config = config;
        this.extra = List.copyOf(extra);
        this.log = log;
        this.state = load();
    }

    State state() {
        return state;
    }

    Path dir() {
        return config.dir();
    }

    /** Reloads when a Sutra or a project library changed; true when it did. */
    boolean refresh() {
        String now = stamp();
        if (now.equals(state.stamp())) {
            return false;
        }
        state = load();
        log.info("project-reloaded", "sutras", state.sutras().size(), "problems", state.problems().size());
        return true;
    }

    /** The names of the library files a Sutra's text needs (every file of each library it names), as {@code rachana build} lists them. */
    static List<String> needed(String text, Map<String, byte[]> files) {
        Set<String> used = new LinkedHashSet<>();
        Matcher m = USE.matcher(text);
        while (m.find()) {
            used.add(m.group(1));
        }
        m = REF.matcher(text);
        while (m.find()) {
            used.add(m.group(1));
        }
        List<String> out = new ArrayList<>();
        for (String p : files.keySet()) {
            String[] parts = p.split("/");
            if (parts.length > 2 && used.contains(parts[1])) {
                out.add(p);
            }
        }
        out.sort(null);
        return out;
    }

    private Path sutraDir() {
        Path s = config.dir().resolve("sutras");
        return Files.isDirectory(s) ? s : config.dir();
    }

    /** Modification times of everything a reload reads, as one text. */
    private String stamp() {
        StringBuilder b = new StringBuilder();
        for (Path root : List.of(sutraDir(), config.dir().resolve("rachana_libs"))) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> s = Files.walk(root)) {
                for (Path f : s.filter(Files::isRegularFile).sorted().toList()) {
                    b.append(f).append(' ').append(Files.getLastModifiedTime(f).toMillis()).append(' ').append(Files.size(f)).append('\n');
                }
            } catch (IOException e) {
                b.append(root).append(" unreadable\n");
            }
        }
        return b.toString();
    }

    private State load() {
        String stamp = stamp();
        List<String> problems = new ArrayList<>();
        List<Library> libs = libraries(problems);
        Map<String, byte[]> files = new TreeMap<>();
        List<LibraryBundle> bundles = new ArrayList<>();
        for (Library l : libs) {
            l.files().forEach((n, b) -> files.put("rachana_libs/" + l.name() + "/" + n, b));
            bundles.add(LibraryBundle.ofBytes(l.name(), l.files(), l.source()));
        }
        Rachana rachana = Rachana.core().language(config.level()).libraries(LibrarySet.of(LibraryResolver.of(bundles)));
        Map<String, Sutra> sutras = new TreeMap<>();
        Path dir = sutraDir();
        List<Path> found = new ArrayList<>();
        try (Stream<Path> s = Files.walk(dir)) {
            s.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(SUFFIX)).sorted().forEach(found::add);
        } catch (IOException e) {
            problems.add(dir + " could not be read");
        }
        for (Path f : found) {
            String name = f.getFileName().toString();
            name = name.substring(0, name.length() - SUFFIX.length());
            if (!name.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,99}")) {
                problems.add(dir.relativize(f) + ": a Sutra file name is letters, digits, '_', '.' or '-'");
                continue;
            }
            if (sutras.containsKey(name)) {
                problems.add(dir.relativize(f) + ": a second Sutra named " + name + " (" + dir.relativize(sutras.get(name).file()) + " is served)");
                continue;
            }
            String text;
            try {
                text = Files.readString(f, StandardCharsets.UTF_8);
            } catch (IOException e) {
                problems.add(dir.relativize(f) + " could not be read");
                continue;
            }
            List<SutraProblem> ps = List.of();
            try {
                rachana.parser().parse(text, dir.relativize(f).toString().replace('\\', '/'), f.getParent().getFileName().toString());
            } catch (SutraException e) {
                ps = e.problems();
            } catch (RuntimeException e) {
                ps = List.of(new SutraProblem("RCH-0000", "the Sutra could not be checked: " + e.getClass().getSimpleName(), null));
            }
            sutras.put(name, new Sutra(name, f, text, ps));
        }
        return new State(Map.copyOf(sutras), java.util.Collections.unmodifiableMap(files), List.copyOf(problems), stamp);
    }

    /** The libraries, in order of sources; a name twice with different contents is refused (a problem) and offered from neither. */
    private List<Library> libraries(List<String> problems) {
        List<Library> found = new ArrayList<>();
        Path own = config.dir().resolve("rachana_libs");
        if (Files.isDirectory(own)) {
            found.addAll(find(own, problems));
        }
        for (Path p : extra) {
            found.addAll(find(p, problems));
        }
        found.addAll(bundled(problems));
        Map<String, Library> seen = new LinkedHashMap<>();
        Set<String> refused = new LinkedHashSet<>();
        for (Library l : found) {
            Library had = seen.get(l.name());
            if (refused.contains(l.name())) {
                continue;
            }
            if (had == null) {
                seen.put(l.name(), l);
            } else if (!had.version().equals(l.version()) || !had.identity().equals(l.identity())) {
                problems.add("library " + l.name() + " is in " + had.source() + " (" + had.version() + ") and in " + l.source() + " ("
                        + l.version() + ") with different contents: refused, rename or remove one");
                seen.remove(l.name());
                refused.add(l.name());
            }
        }
        return new ArrayList<>(seen.values());
    }

    /** Every library a path offers: itself when it is one, else each child folder or {@code .rlib} that is one. */
    private static List<Library> find(Path p, List<String> problems) {
        List<Library> out = new ArrayList<>();
        try {
            if (Files.isRegularFile(p)) {
                out.add(describe(unzip(Files.readAllBytes(p)), p.toString()));
                return out;
            }
            if (isLibrary(p)) {
                out.add(describe(folder(p), p.toString()));
                return out;
            }
            try (Stream<Path> s = Files.list(p)) {
                for (Path q : s.sorted().toList()) {
                    if (q.getFileName().toString().startsWith(".")) {
                        continue;
                    }
                    try {
                        if (Files.isDirectory(q) && isLibrary(q)) {
                            out.add(describe(folder(q), q.toString()));
                        } else if (q.getFileName().toString().endsWith(".rlib")) {
                            out.add(describe(unzip(Files.readAllBytes(q)), q.toString()));
                        }
                    } catch (IllegalArgumentException e) {
                        problems.add(e.getMessage());
                    }
                }
            }
        } catch (IOException | UncheckedIOException | IllegalArgumentException e) {
            problems.add(p + ": " + (e instanceof IllegalArgumentException ? e.getMessage() : "could not be read"));
        }
        return out;
    }

    private static boolean isLibrary(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.anyMatch(f -> MANIFEST.matcher(f.getFileName().toString()).matches());
        }
    }

    private static Map<String, byte[]> folder(Path dir) throws IOException {
        Map<String, byte[]> files = new TreeMap<>();
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path f : s.filter(Files::isRegularFile).toList()) {
                String rel = dir.relativize(f).toString().replace('\\', '/');
                if (Arrays.stream(rel.split("/")).anyMatch(x -> x.startsWith(".")) || Files.size(f) > MAX_FILE) {
                    continue;
                }
                files.put(rel, Files.readAllBytes(f));
            }
        }
        return files;
    }

    private static Map<String, byte[]> unzip(byte[] zip) throws IOException {
        Map<String, byte[]> files = new TreeMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
                String n = e.getName();
                if (e.isDirectory()) {
                    continue;
                }
                if (n.startsWith("/") || n.contains("\\") || Arrays.asList(n.split("/")).contains("..")) {
                    throw new IllegalArgumentException("unsafe entry name " + n);
                }
                byte[] b = in.readNBytes(MAX_FILE + 1);
                if (b.length <= MAX_FILE) {
                    files.put(n, b);
                }
            }
        }
        return files;
    }

    private static Library describe(Map<String, byte[]> files, String source) {
        String manifest = files.keySet().stream().filter(n -> MANIFEST.matcher(n).matches()).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(source + ": no *.rachana-lib.yaml at the top"));
        String text = new String(files.get(manifest), StandardCharsets.UTF_8);
        Matcher n = NAME.matcher(text);
        if (!n.find()) {
            throw new IllegalArgumentException(source + ": the manifest names no library");
        }
        Matcher v = VERSION.matcher(text);
        return new Library(n.group(1), v.find() ? v.group(1) : "", source, files);
    }

    /** The libraries bundled in the jar ({@code META-INF/rachana/libs/<name>/}, listed in {@code META-INF/rachana/libraries}). */
    private static List<Library> bundled(List<String> problems) {
        List<Library> out = new ArrayList<>();
        ClassLoader cl = Project.class.getClassLoader();
        try (InputStream idx = cl.getResourceAsStream("META-INF/rachana/libraries")) {
            if (idx == null) {
                return out;
            }
            for (String name : new String(idx.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (name.isBlank()) {
                    continue;
                }
                LibraryResolver.classpath(cl).resolve(name.trim(), "").ifPresentOrElse(
                        b -> out.add(describe(new TreeMap<>(b.files()), "bundled:" + name.trim())),
                        () -> problems.add("bundled library " + name.trim() + " is missing from the jar"));
            }
        } catch (IOException | IllegalStateException e) {
            problems.add("the bundled libraries could not be read: " + e.getMessage());
        }
        return out;
    }
}
