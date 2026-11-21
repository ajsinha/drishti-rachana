/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.parse;

import com.ash.rachana.SutraException;
import com.ash.rachana.SutraProblem;
import com.ash.rachana.model.SourceLocation;
import com.ash.rachana.parse.SutraBlocks.Block;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * One library of shared blocks and fragments (split step A6, registries per instance): the host's UI kit, the block files of its
 * Sutra folders and the sections its modules contribute. A host makes one per {@link com.ash.rachana.Rachana} instance and hands it to
 * its profile ({@code Profile.Builder.fragments}, or {@link com.ash.rachana.profile.Entities#addTo(com.ash.rachana.profile.Profile.Builder,
 * java.util.function.Supplier, BlockLibrary)}); two libraries never see each other's blocks. Nothing of it is static. The library is a
 * snapshot replaced whole by {@link #load}; a parse reads the snapshot of that moment. Thread-safe.
 */
public final class BlockLibrary implements FragmentResolver {

    private final AtomicReference<Map<String, TreeMap<Integer, Block>>> libraryRef = new AtomicReference<>(Map.of());
    private final AtomicReference<List<SutraProblem>> problemsRef = new AtomicReference<>(List.of());
    private final AtomicReference<List<SutraProblem>> warningsRef = new AtomicReference<>(List.of());
    private final AtomicReference<List<SutraProblem>> clashesRef = new AtomicReference<>(List.of());
    private final AtomicReference<Path> kitRef = new AtomicReference<>();

    /** An empty library: no UI kit, no blocks, no contributions. */
    public BlockLibrary() {
    }

    /** A module's section of a kind's page (module.yaml {@code sections:}): the fragment it brings and the kind whose Sutras show it. */
    public record Contribution(String module, String kind, Block block) {}

    private final java.util.Map<String, List<Contribution>> contributions = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * A module's section: its fragment joins the library (named {@code <module>/<fragment>}) and is appended to every Sutra of the kind
     * when the Sutras are read again (the fragment's panels carry their own {@code when:}). One implementation for every module.
     */
    public void contribute(String module, String kind, String fragmentYaml, String file) {
        Block b = SutraBlocks.read(fragmentYaml, file, file);
        Block named = new Block(module + "/" + b.name(), b.version(), b.text(), b.params(), b.file(), b.path());
        contributions.compute(module, (k, old) -> {
            List<Contribution> l = new ArrayList<>(old == null ? List.of() : old);
            l.add(new Contribution(module, kind, named));
            return l;
        });
        libraryRef.updateAndGet(m -> {
            Map<String, TreeMap<Integer, Block>> n = new TreeMap<>(m);
            n.computeIfAbsent(named.name(), x -> new TreeMap<>()).put(named.version(), named);
            return Map.copyOf(n);
        });
    }

    /** Takes a module's sections away (the module stopped); the Sutras are read again by the caller. */
    public void withdraw(String module) {
        List<Contribution> gone = contributions.remove(module);
        if (gone != null) {
            libraryRef.updateAndGet(m -> {
                Map<String, TreeMap<Integer, Block>> n = new TreeMap<>(m);
                gone.forEach(c -> n.remove(c.block().name()));
                return Map.copyOf(n);
            });
        }
    }

    /** The {@code use:} nodes of the sections modules contribute to a kind's Sutras, in module order. */
    public List<PNode> contributedUses(String kind) {
        List<PNode> out = new ArrayList<>();
        contributions.values().stream().flatMap(List::stream).filter(c -> c.kind().equals(kind))
                .sorted(java.util.Comparator.comparing(c -> c.block().name()))
                .forEach(c -> out.add(new PNode(Map.of("use", new PNode(c.block().ref(), 1, 1)), 1, 1)));
        return out;
    }

    /**
     * Where the host's UI kit is (its {@code fragments/} folder read by every {@link #load}), or null for
     * none (a unit test of blocks alone). The server and the CLI set it before the first load.
     */
    public void kit(Path dir) {
        kitRef.set(dir == null ? null : dir.toAbsolutePath().normalize());
    }

    /** The host's UI kit folder set by {@link #kit(Path)}, if any. */
    public java.util.Optional<Path> kit() {
        return java.util.Optional.ofNullable(kitRef.get());
    }

    /** Whether a block was read from the host's UI kit (an unqualified platform fragment) rather than a pack or a site folder. */
    public boolean fromKit(Block b) {
        Path k = kitRef.get();
        if (k == null || b.path() == null) {
            return false;
        }
        try {
            return Path.of(b.path()).toAbsolutePath().normalize().startsWith(k);
        } catch (java.nio.file.InvalidPathException e) {
            return false;
        }
    }

    /**
     * Reads the host's UI kit fragments (when a kit is set) and every block file under the folders: the library becomes exactly
     * these. Kit fragments are unqualified ({@code series-line@1}); a pack's blocks are namespaced by the pack ({@code trading/legs@1})
     * and also answer to their plain name unless the kit has that name: on a clash the kit wins and the pack's stays reachable only
     * qualified, with a warning ({@link #clashes()}). Among folders, a later one (a more specific pack) wins a plain name. Returns the
     * problems of the files that are not blocks.
     */
    public List<SutraProblem> load(Collection<Path> dirs) {
        Map<String, TreeMap<Integer, Block>> next = new TreeMap<>();
        List<SutraProblem> problems = new ArrayList<>();
        List<SutraProblem> warnings = new ArrayList<>();
        List<SutraProblem> clashes = new ArrayList<>();
        java.util.Set<String> kitNames = new java.util.HashSet<>();
        Path kit = kitRef.get() == null ? null : kitRef.get().resolve("fragments");
        List<Path> all = new ArrayList<>();
        if (kit != null) {
            all.add(kit);
        }
        for (Path d : dirs) {
            Path n = d.toAbsolutePath().normalize();
            if (kit == null || !(n.startsWith(kit) || kit.startsWith(n))) {   // the kit is read once, first (a caller may still name it)
                all.add(d);
            }
        }
        for (Path dir : all) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            boolean isKit = dir == kit;
            String pack = isKit ? null : SutraBlocks.packOf(dir);
            try (Stream<Path> s = Files.walk(dir)) {
                for (Path f : s.filter(SutraBlocks::isBlockFile).sorted().toList()) {
                    try {
                        Block b = SutraBlocks.read(Files.readString(f, StandardCharsets.UTF_8), f.getFileName().toString(), f.toString());
                        String plain = b.name();
                        if (pack != null) {                            // namespaced by its pack: trading/deal-ticket
                            b = new Block(pack + "/" + plain, b.version(), b.text(), b.params(), b.file(), b.path());
                            next.computeIfAbsent(b.name(), k -> new TreeMap<>()).put(b.version(), b);
                        }
                        if (isKit) {
                            kitNames.add(plain);
                            next.computeIfAbsent(plain, k -> new TreeMap<>()).put(b.version(), b);
                        } else if (kitNames.contains(plain)) {
                            clashes.add(new SutraProblem(SutraBlocks.BLOCK, (pack == null ? "the site block " : "pack " + pack + "'s ") + plain
                                    + " has the name of a UI kit fragment: the kit's wins" + (pack == null ? " (rename it)"
                                    : "; use " + pack + "/" + plain + "@" + b.version() + " for the pack's"), new SourceLocation(f.toString(), 1, 1)));
                        } else {
                            next.computeIfAbsent(plain, k -> new TreeMap<>()).put(b.version(), b);   // a later folder (a more specific pack) wins
                        }
                        if (!b.fragment()) {                           // an older block: its doc is a warning today
                            warnings.addAll(SutraDocstring.checkSutra(SutraBlocks.parse(b.text().replace("%{", "pXq{"), b.file()), "block " + plain, f.toString()));
                        }
                    } catch (SutraException e) {
                        problems.addAll(e.problems());
                    } catch (IOException e) {
                        problems.add(SutraBlocks.problem("cannot read " + f + ": " + e.getMessage(), f.getFileName().toString(), 1, 1));
                    }
                }
            } catch (IOException e) {
                problems.add(SutraBlocks.problem("cannot scan " + dir + ": " + e.getMessage(), dir.toString(), 1, 1));
            }
        }
        contributions.values().forEach(l -> l.forEach(c -> next.computeIfAbsent(c.block().name(), k -> new TreeMap<>()).put(c.block().version(), c.block())));
        libraryRef.set(Map.copyOf(next));
        java.util.Set<Block> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (TreeMap<Integer, Block> versions : next.values()) {
            for (Block b : versions.values()) {
                if (seen.add(b) && b.fragment() && b.text().contains("use:")) {
                    List<SutraProblem> inner = new ArrayList<>();
                    SutraBlocks.expand(List.of(new PNode(Map.of("use", new PNode(b.ref(), 1, 1)), 1, 1)), b.path(), inner, this);
                    problems.addAll(inner);
                }
            }
        }
        problemsRef.set(List.copyOf(problems));
        warningsRef.set(List.copyOf(warnings));
        clashesRef.set(List.copyOf(clashes));
        return problems;
    }

    /** Each block once (a pack's block is listed under its qualified and its plain name), in name order. */
    public List<Block> blocks() {
        java.util.Set<Block> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        List<Block> out = new ArrayList<>();
        libraryRef.get().values().forEach(vs -> vs.values().stream().filter(seen::add).forEach(out::add));
        out.sort(java.util.Comparator.comparing(Block::name).thenComparing(Block::version));
        return out;
    }

    /** The names a pack or site block shares with a UI kit fragment, read last: the kit's won ({@code RCH-2048}, a warning). */
    public List<SutraProblem> clashes() {
        return clashesRef.get();
    }

    /** The blocks known now: name to versions. */
    public Map<String, TreeMap<Integer, Block>> library() {
        return libraryRef.get();
    }

    /** The problems of the block files read last. */
    public List<SutraProblem> problems() {
        return problemsRef.get();
    }

    /** The warnings of the block files read last: an older block without a complete {@code doc:} ({@code RCH-2055}). */
    public List<SutraProblem> warnings() {
        return warningsRef.get();
    }


    /** The block a use names ({@code name@N}, or {@code name} for the newest version), if it is loaded. */
    @Override
    public java.util.Optional<Block> find(String ref) {
        return SutraBlocks.find(libraryRef.get(), ref);
    }

    @Override
    public java.util.Collection<String> names() {
        return libraryRef.get().keySet();
    }
}
