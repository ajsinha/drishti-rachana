<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary: see LICENSE. -->
# Changelog

Entries are written per release; the regime's unreleased work is listed after the releases it follows.

## Unreleased (toward v0.10.0, the untangling)

- **A1, errors**: `RachanaException` (code, words; message is the code and the words) and `RachanaCode` replace the host's exception
  and error codes; every code is `RCH-` with the number it always had (problems, editing refusals, evaluation and syntax errors,
  checked-mode refusals). A host maps them to its own by the number at its edge. The accepted options in an editing refusal are
  named sorted (they were in an unordered set's order, which changed between runs).
- **A2, the value interface**: `com.ash.rachana.api.Node` (zero dependencies): kind, fields, elements, scalar value, masked and
  missing, path navigation, and Rachana's own nodes behind `Node.of`; the evaluator, the formats and the checker read only `Node`, so a
  host passes its own document type by implementing it (`ForeignNodeTest`), never by copying. The host's `DataNode`, `NodeType`,
  `PathWalker` left `hostcompat`; `ListValues` and `IdText` are now `com.ash.rachana.format`'s own.
- **A3, small dependencies out**: `SafeRegex` (the linear-time regular expressions) and `BusinessCalendar` are Core's own
  (`com.ash.rachana.el`); the date functions read business days through the `Calendars` hook (Core's calendars unless a host
  installs its resolver); the compiler's cache is Core's own LRU, so **Caffeine is gone**; the regular-expression caps are read
  from `rachana.regex.*` (they were the host's setting names). The temporary `hostcompat` package is gone.
- **A4, settings**: `RachanaOptions`, every setting of Core in one value (expression cache size and limits, the site and profile
  format files, the regular-expression caps), with `compiler()` and `formats()`; Core reads no system property any more (the
  regular-expression caps are plain defaults until A6 hands the options to each instance). Spring never moved.
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
