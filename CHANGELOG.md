<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary: see LICENSE. -->
# Changelog

Entries are written per release; the regime's unreleased work is listed after the releases it follows.

## Unreleased (toward v0.10.0, the untangling)

- **A14, the binder's API, writer and cases**: `rachana.binder(hooks, pivots)` and `rachana.views(hooks, pivots, tables, dates,
  budget)`: `ViewBuilder`, Core's view pipeline (controls, the date, panel sources through the `TableProvider`, map lookups, `when`,
  wired panels, strip, title, keys, controls and selects, screen grid, phone; a host's reference, mnemonic, provenance and timings left
  unset). `ViewJson` is the canonical writer: the conformance `sutra` kind (`JvmRuntime`, and the reference bridge over the host's
  engine) writes whole ViewModels with it, and the 5 views of the new `sutra/binder/panels` suite (harvested from the host's panel
  tests: chart kinds, drill levels and a timeline, tabs with a tree table, metrics, strip/keys/controls/selects) are byte-identical to
  the reference's Jackson output (verified). The driver checks `at` (JSON Pointers into the ViewModel). `patch` cases wait for `data:`
  (B1). The level replay (CG2) compares the cases a level recorded: cases added later and `unsupported` ones now answered are reported,
  not differences (3 sutra examples are now answered). Conformance: 2,112 of 2,117 identical to the reference, 5 known (the 2 of A1;
  3 Core-only Sutra examples the reference cannot read); 0 verified failing (one unverified example, `metric-over-a-list`, fails: its
  `sum($.sales, 'revenue')` is not a Core aggregate; the example `control-value-reaches-a-template` named a control kind `select` that
  does not exist, now `dropdown`). API baseline: `Rachana.binder`, `Rachana.views`, `ViewBuilder` and its `Request`.
- **A13, the binder behind hooks**: the binder and its neighbours (Drishti's engine `bind`, `view`, `control`, `wire`, and the live
  `Frame` and `Patch` records) moved here with their history (84 commits, `tools/split/a13-paths.txt`) into
  `com.ash.rachana.{bind,view,control,wire}`, and every host type behind a hook: `LinkResolver` (which kind an id names, a kind's
  code), `TableProvider` (list, search, fetch, freshness; `computedId`), `Masker` (the viewer's masks and kinds), `PanelBinder` per
  profile kind (`links`, `provenance`), `PivotEngine` (a pivot panel's cells), `BusinessDays` (a request's business date and calendar),
  a label function; `BindHooks` holds them (`BindHooks.core()`: Core alone). Core's own `EntityRef`, `Entity` (with `Origin`),
  `EntityHit`, `AsOf`, `SourceFailures`, `Layout`, `PivotLimits`, `RelativeDates`. `BindContext` carries a `host` object Core never
  reads. The ViewModel records carry Core's `@Include` (Jackson's rules); `ViewJson` is Core's canonical writer (records in field order,
  the rules applied) and `MaskCount` and `ViewModelSchema` read it; the map cache is Core's `TtlCache` (no Caffeine). New codes
  `RCH-5001` (bad request) and `RCH-5002` (forbidden). Changed answers (for review): a panel too deep to evaluate names "the expression
  depth limit"; the ViewModel schema's `$id` is this specification's address. Host words in Core: still 2 files. Conformance
  byte-identical (2,110 of 2,112 identical to the reference, the 2 known; 0 verified failing); `BindHooksTest` and the 5 moved unit
  tests; the API baseline gains the four packages (104 types).
- **A7, the model and parser split along the Core/profile line**: each instance has a `Grammar` (`rachana.grammar()`, its parser
  `rachana.parser()`, its schema `rachana.schema(formats)`): Core's keys and kinds plus what its profiles add. A profile adds top-level
  keys (`SutraKey`: reader, the panels it brings, schema fragment), brings the profile panel kinds (`links`, `provenance`), the `kind`
  source form, `form.save` and a fragment resolver (`FragmentResolver`; Core's default is the folder library). What a profile key
  reads is kept in `Sutra.extensions()`; `match()` and `domain()` read it. `Entities.addTo(profile)` holds the entity additions
  (`match`, `domain`, `links`, `provenance`, kind sources, `form.save`) until the host's profile module takes them; Core alone reads a
  Sutra with none of them and refuses them (`RCH-2011`, `RCH-2021`, `RCH-2010`, `RCH-2058`); `setMatch` is refused without the `match`
  key. `new SutraParser()`, `new OpApplier()` and the editors' no-argument constructors are Core's grammar alone; the others take the
  instance's parser. Core `SutraStore` (`name@version`, newest wins, listeners). Removed (planned):
  `RachanaSchema.build(formats, kinds)`, now `build(formats, grammar)`; the schema's `$id` is `urn:rachana:schema:sutra:1`. Host words
  left in Core: 2 files (the limit messages' setting names, which change conformance words, and `signatures.yaml`). Conformance
  byte-identical to before (2,110 of 2,112 identical to the reference, the 2 known; 0 verified failing); `GrammarTest` new.
- **A6, registries per instance**: `Rachana.with(options, profiles…)` builds one instance (its own `FunctionTable`, compiler and
  cache, format table); `Profile.core()` and `Profile.named(name).functions(…).formats(…).calendars(…).build()`. Nothing is
  process-wide: `Functions.register` and `Calendars.use` are gone, providers are no longer found by `ServiceLoader` (a profile hands
  them over), and two instances in one JVM never see each other's functions, calendars or patterns. A profile cannot redefine a Core
  name (refused when the instance is built), nor can two providers give one name. `RachanaOptions.regexLimits` now reaches the text
  functions, per instance. A host whose functions come and go rebuilds and swaps its instance. The static `Functions` methods read
  the Core table (`FunctionTable.core()`). Conformance results byte-identical to before A6 (2,110 of 2,112 identical to the reference,
  the 2 known; 0 verified failing); 840 unit tests (`RachanaInstanceTest` new).
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
- **CG5 and CG2 skeletons**: `java/api-surface.txt` (1,661 lines: the public and protected API of `rachana-api` and `rachana-core`)
  with `ApiSurfaceTest`; `compat/levels/1.0/` (results of the JVM runtime at level 1.0 and the four generated definition files),
  `tools/regime/levels.py` (`write` once, `check` replays) and `LevelReplayTest`.
- `conformance/1/FROZEN.sha384` (pre-release), `compat/reserved-names.yaml`, `compat/promotions.yaml`, `compat/level-switches.yaml`.
- `tools/gate/drishti_gate.py` (CG8, dry run), `compat/forward-vectors/FILES.md` (CG7).
