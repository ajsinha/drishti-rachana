# Copyright (c) 2026 Ashutosh Sinha. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""The cuts of the M0 proof copy (P1 step 2 of docs/plans/JS_PROOF.md, and the four T1 gaps), applied to a fresh copy of the
compiler sources. Each cut is one exact text replacement or one replacement file; nothing else in the copy changes. The list is
the table "Cuts" of docs/plans/P1_RESULTS.md.

Usage (by proof.py): cuts.py <copy root: the folder holding com/ash/drishti/...> <tools/proofs/java/cut>
"""
import pathlib
import shutil
import sys

R = "com/ash/drishti/rachana/"
JACKSON_TREE = [("import com.fasterxml.jackson.databind.JsonNode;", "import com.ash.proof.JsonNode;"),
                ("import com.fasterxml.jackson.databind.ObjectMapper;", "import com.ash.proof.ObjectMapper;"),
                ("import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;", "import com.ash.proof.YAMLFactory;")]

# (id, file, [(old, new)...]) -- every old text must occur exactly once
EDITS = [
    ("C2 Caffeine -> LRU", R + "el/ElCompiler.java", [
        ("import com.github.benmanes.caffeine.cache.Cache;", "import com.ash.proof.Cache;"),
        ("import com.github.benmanes.caffeine.cache.Caffeine;", "import com.ash.proof.Caffeine;")]),
    ("C3 resources and Jackson tree", R + "format/Formats.java", JACKSON_TREE + [
        ('Formats.class.getClassLoader().getResourceAsStream("formats.yaml")', 'com.ash.proof.Resources.open("formats.yaml")'),
        ("public final class Formats implements com.ash.drishti.common.LiveSettings {", "public final class Formats {"),
        ("""    /** Packs changed: the bundled formats, each pack's formats now, then the site file. A file that is not valid YAML refuses the reload. */
    @Override
    public Runnable prepare(org.springframework.core.env.Environment candidate) {
        java.util.List<String> files = org.springframework.boot.context.properties.bind.Binder.get(candidate)
                .bind("drishti.rachana.pack-formats-files", org.springframework.boot.context.properties.bind.Bindable.listOf(String.class)).orElse(java.util.List.of());
        Map<String, FormatSpec> next = load(overrideFile, files).specs;
        return () -> specs = next;
    }
""", "")]),
    ("C3 resources and Jackson tree", R + "format/WorldRegions.java", JACKSON_TREE + [
        ('WorldRegions.class.getClassLoader().getResourceAsStream("world-regions.yaml")', 'com.ash.proof.Resources.open("world-regions.yaml")')]),
    ("C3 resources and Jackson tree", R + "el/check/Signatures.java", JACKSON_TREE + [
        ("Signatures.class.getClassLoader().getResourceAsStream(resource)", "com.ash.proof.Resources.open(resource)")]),
    ("C5 Jackson exception type", R + "parse/SutraParser.java", [
        ("import com.fasterxml.jackson.core.JsonProcessingException;", "import com.ash.proof.JsonProcessingException;")]),
    ("C5 Jackson exception type and string quoting", R + "parse/SutraBlocks.java", [
        ("import com.fasterxml.jackson.core.JsonProcessingException;", "import com.ash.proof.JsonProcessingException;"),
        ("com.fasterxml.jackson.core.io.JsonStringEncoder.getInstance()", "com.ash.proof.JsonStringEncoder.getInstance()"),
        # T1 gaps G3, G4: TeaVM has no Matcher.appendReplacement/appendTail(StringBuilder)
        ("""            StringBuilder filled = new StringBuilder();
            while (pm.find()) {""", """            StringBuilder filled = new StringBuilder();
            int tail = 0;                                   // PROOF GAP G3/G4: TeaVM lacks Matcher.appendReplacement/appendTail(StringBuilder)
            while (pm.find()) {"""),
        ("""                pm.appendReplacement(filled, Matcher.quoteReplacement(v));
            }
            pm.appendTail(filled);""", """                filled.append(b.text(), tail, pm.start()).append(v);
                tail = pm.end();
            }
            filled.append(b.text(), tail, b.text().length());""")]),
    ("G1 BigDecimal.sqrt(MathContext)", R + "el/MathFunctions.java", [
        ("return Args.out(x.sqrt(MathContext.DECIMAL64));", "return Args.out(CoreDecimal.sqrt(x, MathContext.DECIMAL64));")]),
    ("G2 Collections.unmodifiableSortedMap", "com/ash/drishti/common/BusinessCalendar.java", [
        ("return java.util.Collections.unmodifiableSortedMap(out);",
         "return out;  // PROOF GAP G2: TeaVM lacks Collections.unmodifiableSortedMap; read-only by convention here")]),
]
# replacement files: C4 (the YAML reader reads the reference's own tree) and G1's Core square root
FILES = [R + "parse/PositionalYamlReader.java", R + "el/CoreDecimal.java"]


def main():
    root, cut = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
    for cid, f, pairs in EDITS:
        p = root / f
        s = p.read_text(encoding="utf-8")
        for old, new in pairs:
            n = s.count(old)
            if n != 1:
                raise SystemExit(f"{cid}: {f}: expected the text once, found {n}: {old[:60]!r}")
            s = s.replace(old, new)
        p.write_text(s, encoding="utf-8")
    for f in FILES:
        shutil.copyfile(cut / f, root / f)
    print(f"cuts: {sum(len(e[2]) for e in EDITS)} replacements in {len(EDITS)} files, {len(FILES)} files replaced")


main()
