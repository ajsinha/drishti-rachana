/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.lib;

import com.ash.rachana.SutraException;
import com.ash.rachana.SutraProblem;
import com.ash.rachana.model.SourceLocation;
import com.ash.rachana.parse.FragmentResolver;
import com.ash.rachana.parse.PNode;
import com.ash.rachana.parse.SutraBlocks;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The libraries a host offers its Sutras (DESIGN.md (libraries) 9, 10, 12): a {@link LibraryResolver}, the project's lock (optional: a host
 * without one, such as the playground, takes what its resolver has) and the host's trust. A parsed library is cached by its content
 * hash for the life of the set, so expanding a use never re-reads a file. {@link #fragments} is the adapter that serves a Sutra's
 * library fragments through the 1.0 {@link FragmentResolver} hook, which keeps its signature: a host that implements only
 * {@code FragmentResolver} is asked for nothing new. Immutable apart from its cache; thread-safe.
 */
public final class LibrarySet {

    private static final LibrarySet NONE = new LibrarySet(LibraryResolver.none(), null, Library.Trust.NONE);
    private static final Pattern DECLARED = Pattern.compile("([a-z][a-z0-9-]{0,31})@([0-9]+)");
    private static final Pattern LONG = Pattern.compile("([a-z][a-z0-9-]{0,31})@([0-9]+)/([a-z][a-z0-9-]{1,63})@([0-9]+)");
    private static final Pattern SHORT = Pattern.compile("([a-z][a-z0-9-]{0,31})/([a-z][a-z0-9-]{1,63})(@([0-9]+))?");
    /** How deep {@code depends} may chain (DESIGN.md (libraries) 3.3). */
    public static final int MAX_DEPTH = 4;

    private final LibraryResolver resolver;
    private final LibraryLock lock;
    private final Library.Trust trust;
    private final Map<String, Library> cache = new ConcurrentHashMap<>();

    private LibrarySet(LibraryResolver resolver, LibraryLock lock, Library.Trust trust) {
        this.resolver = resolver;
        this.lock = lock;
        this.trust = trust;
    }

    /** No libraries: every library use is {@code RCH-2110}. */
    public static LibrarySet none() {
        return NONE;
    }

    /** The libraries a resolver finds, with no lock and no signature requirement. */
    public static LibrarySet of(LibraryResolver resolver) {
        return new LibrarySet(resolver, null, Library.Trust.NONE);
    }

    /** The same set pinned by a lock: a library not in it, or not matching its version and hash, is refused. */
    public LibrarySet locked(LibraryLock l) {
        return new LibrarySet(resolver, l, trust);
    }

    /** The same set with the host's trust (verifier, trusted publisher keys, whether a signature is required). */
    public LibrarySet trusting(Library.Trust t) {
        return new LibrarySet(resolver, lock, t == null ? Library.Trust.NONE : t);
    }

    /** Its resolver. */
    public LibraryResolver resolver() {
        return resolver;
    }

    /** Its lock, or null. */
    public LibraryLock lock() {
        return lock;
    }

    /**
     * The library of this name and major, found, checked against the lock and loaded (cached by hash).
     *
     * @throws SutraException {@code RCH-2110} not found, {@code 2111} not in the lock or another major, {@code 2113} not the pinned bytes,
     *     and the problems of {@link Library#load}
     */
    public Library load(String name, int major) {
        LibraryLock.Entry pinned = lock == null ? null : lock.get(name);
        if (lock != null && pinned == null) {
            throw fail(Library.NOT_LOCKED, "library " + name + " is not in " + LibraryLock.FILE + " (rachana lib add " + name + "@" + major + ")");
        }
        if (pinned != null && !pinned.version().startsWith(major + ".")) {
            throw fail(Library.NOT_LOCKED, "library " + name + "@" + major + " is wanted, and " + LibraryLock.FILE + " pins " + pinned.version()
                    + " (one version of a library per project)");
        }
        Optional<LibraryBundle> found;
        try {
            found = resolver.resolve(name, pinned == null ? Integer.toString(major) : pinned.version());
        } catch (IllegalArgumentException | IllegalStateException | java.io.UncheckedIOException e) {
            throw fail(Library.INTEGRITY, e.getMessage());
        }
        if (found.isEmpty()) {
            List<String> have = new ArrayList<>();
            resolver.list().forEach(r -> have.add(r.toString()));
            throw fail(Library.NOT_FOUND, "no library " + name + (pinned == null ? "@" + major : " " + pinned.version()) + " is found"
                    + (have.isEmpty() ? " (no libraries are installed)" : " (installed: " + String.join(", ", have) + ")"));
        }
        LibraryBundle b = found.get();
        String sha = b.sha384();
        if (pinned != null && (!pinned.version().equals(b.version()) || !pinned.sha384().equals(sha))) {
            throw fail(Library.INTEGRITY, "library " + name + " " + b.version() + " in " + b.source() + " is not the one " + LibraryLock.FILE
                    + " pins (" + pinned.version() + ", sha384 " + pinned.sha384().substring(0, 12) + "…; found " + sha.substring(0, 12) + "…)");
        }
        Library cached = cache.get(sha);
        if (cached != null) {
            return cached;
        }
        Library lib = Library.load(b, trust);
        if (lib.major() != major) {
            throw fail(Library.NOT_LOCKED, "library " + name + " " + lib.version() + " is not major " + major);
        }
        cache.put(sha, lib);
        return lib;
    }

    /**
     * The fragment resolver a Sutra reads its uses through: its {@code libraries:} entries (each {@code name@major}) and any long-form use
     * ({@code use: finance@1/var-tiles@1}) are loaded, with their dependencies; then {@code lib/fragment@N} of a loaded library is served
     * from it and every other reference goes to the host's resolver, as at level 1.0. Problems (a library not found, not locked, invalid)
     * are reported at the entry or the use that asked. With nothing to load the host's resolver itself is returned.
     */
    public FragmentResolver fragments(FragmentResolver host, PNode root, String file, List<SutraProblem> problems) {
        return fragments(host, root, file, problems, l -> { });
    }

    /** As {@link #fragments(FragmentResolver, PNode, String, List)}, telling {@code loaded} each library loaded (dependencies too), once. */
    public FragmentResolver fragments(FragmentResolver host, PNode root, String file, List<SutraProblem> problems,
            java.util.function.Consumer<Library> loaded) {
        Map<String, Library> byName = new LinkedHashMap<>();
        Map<String, Library> byLong = new LinkedHashMap<>();
        PNode declared = root == null ? null : root.map().get("libraries");
        if (declared != null) {
            if (!declared.isList()) {
                problems.add(at("RCH-2012", "'libraries' is a list of name@major (libraries: [core@1, finance@1])", file, declared));
            }
            for (PNode e : declared.list()) {
                Matcher m = DECLARED.matcher(e.text() == null ? "" : e.text().trim());
                if (!m.matches()) {
                    problems.add(at("RCH-2012", "a libraries entry is name@major, not '" + e.text() + "'", file, e));
                    continue;
                }
                Library had = byName.get(m.group(1));
                if (had != null) {
                    problems.add(at(Library.NOT_LOCKED, "library " + m.group(1) + " is listed twice: one version of a library per project", file, e));
                    continue;
                }
                take(m.group(1), Integer.parseInt(m.group(2)), file, e, byName, problems, new ArrayList<>());
            }
        }
        for (PNode u : longUses(root)) {
            Matcher m = LONG.matcher(u.text().trim());
            m.matches();
            String key = m.group(1) + "@" + m.group(2);
            if (!byLong.containsKey(key)) {
                Library l = take(m.group(1), Integer.parseInt(m.group(2)), file, u, byName, problems, new ArrayList<>());
                if (l != null) {
                    byLong.put(key, l);
                }
            }
        }
        Map<String, Library> all = new java.util.TreeMap<>(byName);
        byLong.values().forEach(l -> all.putIfAbsent(l.name(), l));
        all.values().forEach(loaded);
        if (byName.isEmpty() && byLong.isEmpty()) {
            return host;
        }
        return new FragmentResolver() {
            @Override
            public Optional<SutraBlocks.Block> find(String ref) {
                String r = ref == null ? "" : ref.trim();
                Matcher lm = LONG.matcher(r);
                if (lm.matches()) {
                    Library l = byLong.get(lm.group(1) + "@" + lm.group(2));
                    return l == null ? Optional.empty() : l.fragment(lm.group(3), Integer.parseInt(lm.group(4)));
                }
                Matcher sm = SHORT.matcher(r);
                if (sm.matches() && byName.containsKey(sm.group(1))) {
                    Library l = byName.get(sm.group(1));
                    if (sm.group(4) == null) {
                        return Optional.empty();                     // a library fragment is always used with its major (RCH-2048)
                    }
                    return l.fragment(sm.group(2), Integer.parseInt(sm.group(4)));
                }
                return host.find(ref);
            }

            @Override
            public Optional<String> contract(SutraBlocks.Block fragment, String name) {
                String path = fragment == null || fragment.path() == null ? "" : fragment.path();
                int slash = path.indexOf('/');
                Library l = slash < 0 ? null : byName.get(path.substring(0, slash));
                if (l == null) {
                    l = byLong.values().stream().filter(x -> path.startsWith(x.name() + "/")).findFirst().orElse(null);
                }
                return l == null ? host.contract(fragment, name) : l.contract(name);
            }

            @Override
            public Collection<String> names() {
                Set<String> out = new LinkedHashSet<>(host.names());
                byName.values().forEach(l -> l.fragmentNames().forEach(f -> out.add(l.name() + "/" + f)));
                return out;
            }
        };
    }

    /** Loads a library and, depth first, its dependencies into {@code byName}; problems at {@code at}. */
    private Library take(String name, int major, String file, PNode at, Map<String, Library> byName, List<SutraProblem> problems, List<String> chain) {
        Library had = byName.get(name);
        if (had != null) {
            if (had.major() != major) {
                problems.add(at(Library.NOT_LOCKED, "library " + name + " is wanted as @" + major + " and as @" + had.major()
                        + " (" + String.join(" > ", chain) + "): one version of a library per project", file, at));
            }
            return had;
        }
        if (chain.contains(name)) {
            problems.add(at(Library.INVALID, "libraries depend on each other in a cycle: " + String.join(" > ", chain) + " > " + name, file, at));
            return null;
        }
        if (chain.size() >= MAX_DEPTH) {
            problems.add(at(Library.INVALID, "library dependencies chain at most " + MAX_DEPTH + " deep: " + String.join(" > ", chain) + " > " + name, file, at));
            return null;
        }
        Library lib;
        try {
            lib = load(name, major);
        } catch (SutraException e) {
            for (SutraProblem p : e.problems()) {
                String f = p.location() == null ? null : p.location().file();
                String where = f == null || !f.contains("/") ? "" : " (" + f + ":" + p.location().line() + ")";
                problems.add(at(p.code(), "library " + name + "@" + major + ": " + p.message() + where, file, at));
            }
            return null;
        }
        byName.put(name, lib);
        List<String> deeper = new ArrayList<>(chain);
        deeper.add(name);
        lib.depends().forEach((d, m) -> take(d, m, file, at, byName, problems, deeper));
        return lib;
    }

    /** Every {@code use:} of the long form anywhere in a Sutra's text. */
    private static List<PNode> longUses(PNode n) {
        List<PNode> out = new ArrayList<>();
        walk(n, out);
        return out;
    }

    private static void walk(PNode n, List<PNode> out) {
        if (n == null) {
            return;
        }
        if (n.isMap()) {
            PNode u = n.map().get("use");
            if (u != null && u.text() != null && LONG.matcher(u.text().trim()).matches()) {
                out.add(u);
            }
            n.map().values().forEach(v -> walk(v, out));
        } else if (n.isList()) {
            n.list().forEach(v -> walk(v, out));
        }
    }

    private static SutraException fail(String code, String message) {
        return new SutraException(List.of(new SutraProblem(code, message, new SourceLocation(LibraryLock.FILE, 1, 1))));
    }

    private static SutraProblem at(String code, String message, String file, PNode n) {
        return new SutraProblem(code, message, new SourceLocation(file, n == null ? 1 : n.line(), n == null ? 1 : n.column()));
    }
}
