<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary: see LICENSE. -->
# Changelog

Entries are written per release; the regime's unreleased work is listed after the releases it follows.

## Unreleased (toward v0.10.0, the untangling)

- **A1, errors**: `RachanaException` (code, words; message is the code and the words) and `RachanaCode` replace the host's exception
  and error codes; every code is `RCH-` with the number it always had (problems, editing refusals, evaluation and syntax errors,
  checked-mode refusals). A host maps them to its own by the number at its edge. The accepted options in an editing refusal are
  named sorted (they were in an unordered set's order, which changed between runs).
- **A8a, A8b, Core's own YAML and JSON**: `PositionalYamlReader` is now Core's own reader (no library, no regular expressions):
  the same tree, types, positions, issues and error texts as before (`tools/yaml-diff`: 168,923 inputs, every Drishti and
  Rachana YAML file, case texts, edge inputs and mutations; `PositionalYamlReaderTest` on 1,113 recorded vectors). New: an
  anchor or alias is an issue (`RCH-2033` in a Sutra). `Json` reads and writes JSON. Jackson and SnakeYAML leave the build.
  API: `PositionalYamlReader.read` throws the unchecked `YamlSyntaxException` (message, line, column) instead of an
  `IOException`; `Ops.parse` takes a plain JSON value and `Ops.toJson` returns maps and lists; `Signatures.parse` takes a
  plain value; `PNode.plain()` added. Conformance results byte-identical before and after.
- `tools/split/compare_results.py` and `known-differences.txt`: the proof of each split step against the reference bridge.

## v0.9.0 (2026-11-19): the compiler moves here

- **The compiler, with its history** (split step A10): Drishti's `compute/drishti-rachana` (and its earlier paths) at Drishti
  `main` `c6bc1d1bc`, less the profile part, extracted with `git filter-repo` from a fresh clone under a reviewed path allow-list
  (`tools/split/a10-paths.txt`), 138 commits kept; packages renamed `com.ash.drishti.rachana` → `com.ash.rachana`; into
  `java/rachana-core`. Behaviour is unchanged.
- `rachana-core` builds and tests alone (JDK 21, a named module): 783 unit tests. The host types it still names are held in
  `com.ash.rachana.hostcompat`, and Jackson and Caffeine are allowed, each until a named step ([java/SPLIT_EXEMPTIONS.md](java/SPLIT_EXEMPTIONS.md)).
- `rachana-conformance`: `JvmRuntime`, the conformance runtime over this code; `rachana_conformance.py --jvm` runs it. Over all
  2,112 cases its results are **byte-identical** to the reference bridge over Drishti's jars of the same commit: 1,908 pass,
  74 unverified failing (recorded), 130 unsupported, **0 verified failing**.
- The format table and world regions come from the pinned Drishti Elements `2.4.0` data (`js/vendor/drishti-elements/2.4.0/data`).
- Guards: split exemptions and the host types read from `SPLIT_EXEMPTIONS.md` and fail when stale; host words in `rachana-core`
  counted and may only go down.
- Changes to the moved code, all without a change of behaviour: `Formats` no longer implements the host's Spring reload hook;
  `Signatures` reads its own module's resource first.

## Unreleased (M1, the compatibility regime)

- The customer-corpus export (`tools/regime/corpus_export.py`, `CorpusCompile.java`) and `compat/customers/drishti/2.9.11.i7a/`.
- `java/rachana-compat`: `CustomerCorpusTest` (CG3), `RecordedHost`, `FrozenCasesTest` (CG1), `ContractAppendOnlyTest` (CG4),
  `ReservedNamesTest` (CG6).
- `conformance/1/FROZEN.sha384` (pre-release), `compat/reserved-names.yaml`, `compat/promotions.yaml`, `compat/level-switches.yaml`.
- `tools/gate/drishti_gate.py` (CG8, dry run), `compat/forward-vectors/FILES.md` (CG7).
