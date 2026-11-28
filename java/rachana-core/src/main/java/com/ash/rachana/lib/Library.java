/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.lib;

import com.ash.rachana.SutraException;
import com.ash.rachana.SutraProblem;
import com.ash.rachana.model.SourceLocation;
import com.ash.rachana.parse.PNode;
import com.ash.rachana.parse.PositionalYamlReader;
import com.ash.rachana.parse.SutraBlocks;
import com.ash.rachana.parse.TypedFragment;
import com.ash.rachana.parse.YamlSyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.ash.rachana.model.Ordered;
import java.util.LinkedHashSet;

/**
 * One library version, read and checked (DESIGN.md (libraries) 3, 12): its manifest, its fragments and its identity. {@link #load} refuses a
 * library that breaks a rule: only data files ({@code .yaml}, {@code .json}, {@code .jsonl}, {@code .md}, {@code LICENSE},
 * {@code MANIFEST.sha384}, {@code SIGNATURE}); a complete manifest; a {@code MANIFEST.sha384} that names every file with its hash; an
 * {@code exports} list that matches the fragments; uses inside the library pinned to its own fragments or a declared dependency; no
 * fragment of a public library reading the screen around it; a valid signature where the host asks for one. Immutable; thread-safe.
 */
public final class Library {

    /** Library not found by any resolver. */
    public static final String NOT_FOUND = "RCH-2110";
    /** Library not in the lock file, or the lock's constraint not met. */
    public static final String NOT_LOCKED = "RCH-2111";
    /** Invalid library: manifest, file type, stale exports, dependency cycle, a limit. */
    public static final String INVALID = "RCH-2112";
    /** Integrity: hash mismatch, bad or missing signature, publisher not trusted. */
    public static final String INTEGRITY = "RCH-2113";
    /** The most fragments a library holds (DESIGN.md (libraries) 18). */
    public static final int MAX_FRAGMENTS = 500;
    /** The file holding a SHA-384 per file. */
    public static final String HASHES = "MANIFEST.sha384";
    /** The file holding the detached signatures of {@link #HASHES}. */
    public static final String SIGNATURE = "SIGNATURE";

    static final Pattern NAME = Pattern.compile("[a-z][a-z0-9-]{0,31}");
    private static final Pattern PATH = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9._-]*(/[A-Za-z0-9_][A-Za-z0-9._-]*)*");
    private static final Pattern FRAGMENT = Pattern.compile("fragments/([a-z][a-z0-9-]{1,63})\\.v([0-9]+)\\.fragment\\.yaml");
    private static final Pattern VERSION = Pattern.compile("([0-9]+)\\.([0-9]+)\\.([0-9]+)");
    private static final Pattern DEPEND = Pattern.compile("([a-z][a-z0-9-]{0,31})@([0-9]+)");
    private static final Pattern USE = Pattern.compile("(?m)^\\s*-?\\s*\\{?\\s*use:\\s*[\"']?([^\\s,}\"']+)");
    private static final Set<String> KEYS = Ordered.set("rachana", "library", "version", "title", "summary", "publisher", "licence", "scope", "profiles",
            "depends", "needs", "categories", "exports", "functions", "formats");

    private final String name;
    private final String version;
    private final String scope;
    private final List<String> profiles;
    private final Map<String, Integer> depends;
    private final String publisherKey;
    private final String licence;
    private final String sha384;
    private final String source;
    private final String signedBy;
    private final Map<String, TreeMap<Integer, SutraBlocks.Block>> fragments;
    private final Map<String, String> contracts;
    private final Map<String, com.ash.rachana.el.Declarations.Function> functions;

    private Library(String name, String version, String scope, List<String> profiles, Map<String, Integer> depends, String publisherKey,
            String licence, String sha384, String source, String signedBy, Map<String, TreeMap<Integer, SutraBlocks.Block>> fragments,
            Map<String, String> contracts, Map<String, com.ash.rachana.el.Declarations.Function> functions) {
        this.functions = functions;
        this.name = name;
        this.version = version;
        this.scope = scope;
        this.profiles = profiles;
        this.depends = depends;
        this.publisherKey = publisherKey;
        this.licence = licence;
        this.sha384 = sha384;
        this.source = source;
        this.signedBy = signedBy;
        this.fragments = fragments;
        this.contracts = contracts;
    }

    /** The trust a host places in libraries (DESIGN.md (libraries) 12): its verifier, the publishers it trusts, whether a signature is required. */
    public record Trust(SignatureVerifier verifier, Set<String> publishers, boolean requireSigned) {

        /** No verifier, no requirement: a developer's setting. */
        public static final Trust NONE = new Trust(null, Set.of(), false);
    }

    /**
     * A bundle read and checked.
     *
     * @throws SutraException with every problem found ({@code RCH-2112}, {@code 2113}, {@code 2117}, and fragment problems), each located
     *     in the library's file ({@code finance/fragments/limit-gauge.v1.fragment.yaml})
     */
    public static Library load(LibraryBundle b, Trust trust) {
        List<SutraProblem> out = new ArrayList<>();
        String name = b.name();
        if (!NAME.matcher(name).matches()) {
            throw new SutraException(List.of(problem(INVALID, "a library name is lower-case letters, digits and '-', at most 32 characters: '" + name + "'", name, 1)));
        }
        long total = 0;
        for (Map.Entry<String, byte[]> f : b.files().entrySet()) {
            String p = f.getKey();
            total += f.getValue().length;
            boolean typeOk = p.endsWith(".yaml") || p.endsWith(".json") || p.endsWith(".jsonl") || p.endsWith(".md") || p.equals("LICENSE")
                    || p.equals(HASHES) || p.equals(SIGNATURE) || p.equals("FROZEN.sha384");
            if (!PATH.matcher(p).matches() || p.contains("..") || !typeOk) {
                out.add(problem(INVALID, "the library holds " + p + ": only .yaml, .json, .jsonl, .md, LICENSE, " + HASHES + " and " + SIGNATURE
                        + " files (a library carries no code, markup, fonts or images)", name + "/" + p, 1));
            }
        }
        if (total > Rlib.MAX_BYTES) {
            out.add(problem(INVALID, "the library is " + total + " bytes; at most " + Rlib.MAX_BYTES, name, 1));
        }
        String manifestText = b.manifest();
        if (manifestText == null) {
            out.add(problem(INVALID, "the library has no manifest " + b.manifestPath(), name, 1));
            throw new SutraException(out);
        }
        String mfile = name + "/" + b.manifestPath();
        PNode root;
        try {
            root = new PositionalYamlReader().read(manifestText);
        } catch (YamlSyntaxException e) {
            out.add(problem(INVALID, "YAML syntax: " + e.getMessage(), mfile, e.line()));
            throw new SutraException(out);
        }
        Map<String, PNode> m = root.map();
        m.forEach((k, v) -> {
            if (!KEYS.contains(k)) {
                out.add(problem(INVALID, "unknown key '" + k + "' in the manifest", mfile, v.line()));
            }
        });
        if (m.get("rachana") == null || !Long.valueOf(1).equals(m.get("rachana").value())) {
            out.add(problem(INVALID, "the manifest starts with rachana: 1", mfile, 1));
        }
        if (!name.equals(text(m.get("library")))) {
            out.add(problem(INVALID, "the manifest names its library: library: " + name, mfile, line(m.get("library"))));
        }
        String version = text(m.get("version"));
        if (version == null || !VERSION.matcher(version).matches()) {
            out.add(problem(INVALID, "version is a semantic version x.y.z, quoted or not: '" + version + "'", mfile, line(m.get("version"))));
            version = "0.0.0";
        }
        for (String k : List.of("summary", "licence")) {
            if (text(m.get(k)) == null || text(m.get(k)).isBlank()) {
                out.add(problem(INVALID, "the manifest states its " + k, mfile, 1));
            }
        }
        String scope = text(m.get("scope"));
        if (!"public".equals(scope) && !"private".equals(scope)) {
            out.add(problem(INVALID, "scope is public or private, not '" + scope + "'", mfile, line(m.get("scope"))));
        }
        List<String> profiles = new ArrayList<>();
        if (m.get("profiles") != null) {
            m.get("profiles").list().forEach(p -> profiles.add(p.text()));
        }
        if (!profiles.isEmpty() && !"private".equals(scope)) {
            out.add(problem(INVALID, "a library that declares profiles " + profiles + " is scope: private (a public library never names a host)",
                    mfile, line(m.get("profiles"))));
        }
        Map<String, Integer> depends = new LinkedHashMap<>();
        for (PNode d : m.get("depends") == null ? List.<PNode>of() : m.get("depends").list()) {
            Matcher dm = DEPEND.matcher(d.text() == null ? "" : d.text());
            if (!dm.matches() || dm.group(1).equals(name)) {
                out.add(problem(INVALID, "depends lists other libraries as name@major, not '" + d.text() + "'", mfile, d.line()));
            } else {
                depends.put(dm.group(1), Integer.parseInt(dm.group(2)));
            }
        }
        // X42: the library's functions, in the manifest and in functions/<file>.yaml (each a mapping of functions), called <library>.<f>(...)
        List<SutraProblem> fnAdvice = new ArrayList<>();
        Map<String, com.ash.rachana.el.Declarations.Function> functions = new TreeMap<>(
                new com.ash.rachana.parse.DeclarationsReader(mfile, out, fnAdvice).functions(m.get("functions"), name + "."));
        for (Map.Entry<String, byte[]> f : b.files().entrySet()) {
            if (f.getKey().startsWith("functions/")) {
                String path = name + "/" + f.getKey();
                if (!f.getKey().endsWith(".yaml")) {
                    out.add(problem(INVALID, "a file under functions/ is <name>.yaml (a mapping of functions): " + f.getKey(), path, 1));
                    continue;
                }
                try {
                    PNode fr = new PositionalYamlReader().read(new String(f.getValue(), StandardCharsets.UTF_8));
                    new com.ash.rachana.parse.DeclarationsReader(path, out, fnAdvice).functions(fr, name + ".").forEach((k, v) -> {
                        if (functions.put(k, v) != null) {
                            out.add(problem(INVALID, "the function " + k + " is declared twice", path, 1));
                        }
                    });
                } catch (YamlSyntaxException e) {
                    out.add(problem(INVALID, "YAML syntax: " + e.getMessage(), path, e.line()));
                }
            }
        }
        PNode publisher = m.get("publisher");
        String key = publisher == null ? null : text(publisher.map().get("key"));
        if (key != null && !key.matches("ed25519:[0-9a-f]{64}")) {
            out.add(problem(INVALID, "publisher.key is ed25519: and 64 hex digits", mfile, line(publisher)));
        }
        String signedBy = hashes(b, key, trust, out);
        Map<String, TreeMap<Integer, SutraBlocks.Block>> frags = new TreeMap<>();
        for (Map.Entry<String, byte[]> f : b.files().entrySet()) {
            if (!f.getKey().startsWith("fragments/")) {
                continue;
            }
            Matcher fm = FRAGMENT.matcher(f.getKey());
            String path = name + "/" + f.getKey();
            if (!fm.matches()) {
                out.add(problem(INVALID, "a file under fragments/ is <name>.v<N>.fragment.yaml: " + f.getKey(), path, 1));
                continue;
            }
            String text = new String(f.getValue(), StandardCharsets.UTF_8);
            try {
                SutraBlocks.Block read = SutraBlocks.read(text, fm.group(1) + ".v" + fm.group(2) + ".fragment.yaml", path);
                SutraBlocks.Block q = new SutraBlocks.Block(name + "/" + read.name(), read.version(), read.text(), read.params(), path, path);
                uses(text, name, depends, path, out);
                if ("public".equals(scope) && TypedFragment.isTyped(SutraBlocksAccess.masked(q))) {
                    out.addAll(TypedFragment.of(q).outsideScope());
                }
                frags.computeIfAbsent(read.name(), n -> new TreeMap<>()).put(read.version(), q);
            } catch (SutraException e) {
                out.addAll(e.problems());
            }
        }
        Map<String, String> contracts = new TreeMap<>();
        for (Map.Entry<String, byte[]> f : b.files().entrySet()) {          // published table shapes (DESIGN.md (libraries) 5.2)
            if (f.getKey().startsWith("contracts/")) {
                contract(name, f.getKey(), new String(f.getValue(), StandardCharsets.UTF_8), contracts, out);
            }
        }
        if (frags.values().stream().mapToInt(Map::size).sum() > MAX_FRAGMENTS) {
            out.add(problem(INVALID, "a library holds at most " + MAX_FRAGMENTS + " fragments", mfile, 1));
        }
        String stale = staleExports(m.get("exports"), frags);
        if (stale == null) {
            stale = LibraryExports.stale(m.get("exports"), b.files());
        }
        if (stale != null) {
            out.add(problem(INVALID, "exports " + stale + " (rachana lib build writes them)", mfile, line(m.get("exports"))));
        }
        if (!out.isEmpty()) {
            throw new SutraException(out);
        }
        Map<String, TreeMap<Integer, SutraBlocks.Block>> fixed = new TreeMap<>();
        frags.forEach((k, v) -> fixed.put(k, new TreeMap<>(v)));
        return new Library(name, version, scope, List.copyOf(profiles), Collections.unmodifiableMap(depends), key, text(m.get("licence")), b.sha384(),
                b.source(), signedBy, Collections.unmodifiableMap(fixed), Collections.unmodifiableMap(contracts), Collections.unmodifiableMap(functions));
    }

    /** Checks {@code MANIFEST.sha384} against every file and the signature against the trust; the key that signed, or null. */
    private static String hashes(LibraryBundle b, String publisherKey, Trust trust, List<SutraProblem> out) {
        String name = b.name();
        byte[] listing = b.files().get(HASHES);
        if (listing == null) {
            out.add(problem(INVALID, "the library has no " + HASHES + " (rachana lib build writes it)", name, 1));
            return null;
        }
        String expected = hashList(b.files());
        if (!expected.equals(new String(listing, StandardCharsets.UTF_8))) {
            List<String> differ = new ArrayList<>();
            Map<String, String> listed = new TreeMap<>();
            for (String ln : new String(listing, StandardCharsets.UTF_8).split("\n")) {
                int sp = ln.indexOf("  ");
                if (sp > 0) {
                    listed.put(ln.substring(sp + 2), ln.substring(0, sp));
                }
            }
            for (Map.Entry<String, byte[]> f : b.files().entrySet()) {
                if (!f.getKey().equals(HASHES) && !f.getKey().equals(SIGNATURE) && !Sha384.hex(f.getValue()).equals(listed.remove(f.getKey()))) {
                    differ.add(f.getKey());
                }
            }
            differ.addAll(listed.keySet());
            out.add(problem(INTEGRITY, "the files do not match " + HASHES + (differ.isEmpty() ? " (its layout)" : ": " + String.join(", ", differ)),
                    name + "/" + HASHES, 1));
            return null;
        }
        byte[] sig = b.files().get(SIGNATURE);
        if (sig == null) {
            if (trust.requireSigned()) {
                out.add(problem(INTEGRITY, "library " + name + " is not signed, and this host requires signed libraries", name, 1));
            }
            return null;
        }
        if (trust.verifier() == null) {
            if (trust.requireSigned()) {
                out.add(problem(INTEGRITY, "this host requires signed libraries but has no signature verifier", name, 1));
            }
            return null;
        }
        String good = null;
        for (String ln : new String(sig, StandardCharsets.UTF_8).split("\n")) {
            String[] parts = ln.trim().split("\\s+");
            if (parts.length != 2 || !parts[0].matches("ed25519:[0-9a-f]{64}") || !parts[1].matches("[0-9a-f]{128}")) {
                continue;
            }
            boolean known = parts[0].equals(publisherKey) || trust.publishers().contains(parts[0]);
            if (known && trust.verifier().verify(parts[0], listing, unhex(parts[1]))) {
                if (trust.publishers().isEmpty() || trust.publishers().contains(parts[0])) {
                    good = parts[0];
                    break;
                }
            } else if (known) {
                out.add(problem(INTEGRITY, "the signature of library " + name + " by " + parts[0] + " does not verify", name + "/" + SIGNATURE, 1));
                return null;
            }
        }
        if (good == null && (trust.requireSigned() || !trust.publishers().isEmpty())) {
            out.add(problem(INTEGRITY, "library " + name + " is not signed by a publisher this host trusts", name + "/" + SIGNATURE, 1));
        }
        return good;
    }

    /** The text of {@code MANIFEST.sha384} for these files: one {@code <hex>  <path>} line per file but itself and the signature, by path. */
    public static String hashList(Map<String, byte[]> files) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, byte[]> f : new TreeMap<>(files).entrySet()) {
            if (!f.getKey().equals(HASHES) && !f.getKey().equals(SIGNATURE)) {
                sb.append(Sha384.hex(f.getValue())).append("  ").append(f.getKey()).append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * The {@code exports.fragments} entries the fragment files of a bundle give, as manifest YAML lines (each
     * {@code - { name: x, majors: [1, 2] }}); what {@code rachana lib build} writes.
     */
    public static List<String> exportLines(Map<String, byte[]> files) {
        return LibraryExports.lines(files);
    }

    private static String staleExports(PNode exports, Map<String, TreeMap<Integer, SutraBlocks.Block>> frags) {
        Map<String, List<Integer>> listed = new TreeMap<>();
        PNode fs = exports == null ? null : exports.map().get("fragments");
        for (PNode e : fs == null ? List.<PNode>of() : fs.list()) {
            String n = text(e.map().get("name"));
            List<Integer> majors = new ArrayList<>();
            PNode ms = e.map().get("majors");
            for (PNode x : ms == null ? List.<PNode>of() : ms.list()) {
                majors.add(x.value() instanceof Long l ? l.intValue() : -1);
            }
            listed.put(n, majors);
        }
        Map<String, List<Integer>> actual = new TreeMap<>();
        frags.forEach((k, v) -> actual.put(k, new ArrayList<>(v.keySet())));
        for (String n : listed.keySet()) {
            if (!actual.containsKey(n)) {
                return "list " + n + ", which has no fragment file";
            }
            if (!actual.get(n).containsAll(listed.get(n))) {
                return "list majors " + listed.get(n) + " of " + n + ", which has " + actual.get(n);
            }
        }
        return null;
    }

    private static void uses(String text, String own, Map<String, Integer> depends, String path, List<SutraProblem> out) {
        Matcher u = USE.matcher(text);
        while (u.find()) {
            String ref = u.group(1);
            Matcher q = Pattern.compile("([a-z][a-z0-9-]{0,31})/([a-z][a-z0-9-]{1,63})@([0-9]+)").matcher(ref);
            if (!q.matches() || !q.group(1).equals(own) && !depends.containsKey(q.group(1))) {
                out.add(problem(SutraBlocks.BLOCK, "inside a library a use names its library and pins the major (use: " + own + "/<fragment>@N, or a "
                        + "library it depends on): '" + ref + "'", path, 1));
            }
        }
    }

    /** Its name. */
    public String name() {
        return name;
    }

    /** Its version ({@code 1.4.2}). */
    public String version() {
        return version;
    }

    /** Its major. */
    public int major() {
        return Integer.parseInt(version.substring(0, version.indexOf('.')));
    }

    /** {@code public} or {@code private}. */
    public String scope() {
        return scope;
    }

    /** The profiles it needs (empty: Core-pure). */
    public List<String> profiles() {
        return profiles;
    }

    /** The libraries it uses, with their majors. */
    public Map<String, Integer> depends() {
        return depends;
    }

    /** Its licence (an SPDX id or a LicenseRef). */
    public String licence() {
        return licence;
    }

    /** Its identity: SHA-384 of its canonical archive. */
    public String sha384() {
        return sha384;
    }

    /** Where its resolver found it. */
    public String source() {
        return source;
    }

    /** The key whose signature was verified, or null. */
    public String signedBy() {
        return signedBy;
    }

    /** The publisher's key the manifest names, or null. */
    public String publisherKey() {
        return publisherKey;
    }

    /** Its fragment names. */
    /** The library's functions (X42) by their called name, {@code <library>.<function>}. */
    public Map<String, com.ash.rachana.el.Declarations.Function> functions() {
        return functions;
    }

    public Set<String> fragmentNames() {
        return fragments.keySet();
    }

    /** A fragment by name and major ({@code limit-gauge}, 1); the block's name is qualified ({@code finance/limit-gauge}). */
    /** The text of its published contract {@code name} ({@code contracts/<name>.json}), if it holds one. */
    public Optional<String> contract(String name) {
        return Optional.ofNullable(contracts.get(name));
    }

    /** The names of its published contracts, sorted. */
    public Set<String> contractNames() {
        return contracts.keySet();
    }

    /**
     * A contract file read and checked: {@code contracts/<name>.json}, a JSON object with {@code contract} equal to its file name and
     * {@code columns}, a list of {@code { name, type, doc, optional? }} with distinct field names ({@code RCH-2112} otherwise).
     */
    private static void contract(String library, String path, String text, Map<String, String> into, List<SutraProblem> out) {
        String where = library + "/" + path;
        Matcher fm = java.util.regex.Pattern.compile("contracts/([a-z][a-z0-9-]{0,63})\\.json").matcher(path);
        if (!fm.matches()) {
            out.add(problem(INVALID, "a file under contracts/ is <name>.json: " + path, where, 1));
            return;
        }
        PNode c;
        try {
            c = SutraBlocksAccess.parse(text, where);
        } catch (SutraException e) {
            out.addAll(e.problems());
            return;
        }
        PNode cols = c.isMap() ? c.map().get("columns") : null;
        if (!c.isMap() || !fm.group(1).equals(text(c.map().get("contract"))) || cols == null || !cols.isList() || cols.list().isEmpty()) {
            out.add(problem(INVALID, "contract " + path + " is { contract: " + fm.group(1) + ", columns: [ { name, type, doc } ] }", where, 1));
            return;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (PNode col : cols.list()) {
            String n = col.isMap() ? text(col.map().get("name")) : null;
            if (n == null || !n.matches("[A-Za-z_][A-Za-z0-9_]*") || !seen.add(n) || text(col.map().get("type")) == null) {
                out.add(problem(INVALID, "contract " + fm.group(1) + ": each column is { name, type, doc } with a distinct field name", where, line(col)));
                return;
            }
        }
        into.put(fm.group(1), text);
    }

    /** The majors of fragment {@code fragment} it holds, ascending (empty when it holds none). */
    public java.util.SortedSet<Integer> majors(String fragment) {
        TreeMap<Integer, SutraBlocks.Block> v = fragments.get(fragment);
        return v == null ? new java.util.TreeSet<>() : new java.util.TreeSet<>(v.keySet());
    }

    public Optional<SutraBlocks.Block> fragment(String fragment, int major) {
        TreeMap<Integer, SutraBlocks.Block> v = fragments.get(fragment);
        return Optional.ofNullable(v == null ? null : v.get(major));
    }

    private static String text(PNode n) {
        return n == null ? null : n.text();
    }

    private static int line(PNode n) {
        return n == null ? 1 : n.line();
    }

    static byte[] unhex(String h) {
        byte[] out = new byte[h.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(h.substring(2 * i, 2 * i + 2), 16);
        }
        return out;
    }

    static SutraProblem problem(String code, String message, String file, int line) {
        return new SutraProblem(code, message, new SourceLocation(file, Math.max(1, line), 1));
    }

    /** Reads a block's root with its parameters masked (the parse package's own reading). */
    private static final class SutraBlocksAccess {
        static PNode masked(SutraBlocks.Block b) {
            return new PositionalYamlReader().read(b.text().replace("%{", "pXq{"));
        }

        static PNode parse(String text, String file) {
            try {
                return new PositionalYamlReader().read(text);
            } catch (RuntimeException e) {
                throw new SutraException(List.of(problem(INVALID, "contract " + file + " does not read as JSON: " + e.getMessage(), file, 1)));
            }
        }
    }
}
