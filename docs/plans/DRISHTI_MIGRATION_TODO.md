<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary: see LICENSE. -->
# Drishti-side work of R0, held for the migration step

Status: **checklist, not started in Drishti.** Owner rule of 2026-11-19: "No change needs to be made in Drishti right now; only
Elements and Rachana change." Everything R0 (plan [R0_R1_I8_PLAN](R0_R1_I8_PLAN.md) H1 to H5) needs **inside Drishti** is written
down here, precisely enough to do in one sitting when the migration starts. The Rachana-side halves are done in this repository:
the cases (H2), the reference bridge (`tools/reference-bridge/RefBridge.java`: `expr`, `expr-error`, `format`, `problem`, `edit`),
the definition generator (`LangDump.java`, `tools/conformance/spec_files.py`, H4), the coverage gate (`tools/conformance/coverage.py`,
H5) and the cases jar (`tools/release/cases_jar.py`).

A first version of items 1 to 3 was written and run in a Drishti worktree before the rule (never committed, never pushed); it is kept
as [drishti-migration/R0_H1_RUNNER.patch](drishti-migration/R0_H1_RUNNER.patch) (applies cleanly to Drishti `main` c6bc1d1bc; the
pinned jar itself is not in the patch: it is rebuilt by step 1.3).

## 1. The pin (H1, Drishti part; decision N2)

- [ ] 1.1 Parent `pom.xml`: property `drishti.rachana.version` beside `drishti.elements.version`, with a comment naming the folder, the
      manifest and the guard.
- [ ] 1.2 Parent `pom.xml`: one `<repositories>` entry `rachana-pinned`, `file://${maven.multiModuleProjectDirectory}/packaging/pinned/maven`,
      releases only, `checksumPolicy` `fail`, `updatePolicy` `never`, snapshots off. `.mvn/maven.config` gains
      `-Daether.offline.protocols=file`: without it every `-o` build (the drill's) refuses the file repository on a machine whose
      `~/.m2` has not seen the jar (proven: with it, `mvn -o` resolves the jar from the tree).
- [ ] 1.3 The jar: in this repository, at the commit to pin, `python3 tools/release/cases_jar.py <version> --into <drishti>/packaging/pinned`.
      It writes `maven/com/ash/rachana/rachana-conformance-cases/<version>/` (jar, pom, `.sha1` of each; any other version's folder
      deleted) and `packaging/pinned/drishti-rachana.json` (version, commit, SHA-384 of the jar, Elements fields null). The jar is built
      from `git archive` of the commit, entries sorted, fixed times: the same commit and version give the same bytes. Proposed first
      version `0.8.0-r0.1` (below `v0.9.0`, the move with history); a version is immutable, the next pin is `0.8.0-r0.2`.
- [ ] 1.4 `compute/drishti-rachana/pom.xml`: test dependency `com.ash.rachana:rachana-conformance-cases:${drishti.rachana.version}`.
- [ ] 1.5 `testing/drishti-it/.../RachanaPinGuardTest` (in the patch): fails when the property, the one version folder per artefact
      and the manifest disagree, a jar's SHA-384 or a `.sha1` is wrong, a second version folder is present, the jar's
      `META-INF/rachana-cases.json` names another version or commit, the repository is not declared once with `checksumPolicy` fail, or
      (once the manifest names one) Rachana's Elements version and table hashes differ from the console's vendored ones (C9). Its second
      test proves a doctored copy (jar bytes, property, second folder, commit) fails. Runs in the gate (every drishti-it test does).
- [ ] 1.6 Docs: `docs/architecture/ELEMENTS_IN_DRISHTI.md`-style paragraph for Rachana (where the pin lives, how it moves:
      SPLIT_PLAN 4's checklist), CHANGELOG, CONFIGURATION if the property is listed there.

## 2. The JVM runner (H1)

- [ ] 2.1 `compute/drishti-rachana/src/test/java/com/ash/drishti/rachana/conformance/` (in the patch): `Canonical` (FORMAT 8 and 9),
      `CaseYaml` (YAML 1.2 core schema over SnakeYAML: only `true`/`false` booleans, dates stay text, `1e20` a number, no repeated
      key), `CaseSource` (the pinned jar on the test class path, or a checkout named by the test-only property
      `rachana.conformance.cases`, decision N4), `ConformanceRun` (validate, merge file fields, run, compare, `target/conformance/jvm.jsonl`
      and `jvm.summary.json`, record mode), `Expect` (FORMAT 6 and 7, `at` pointers included), `KindRunner`, `CoreKinds` (`expr`,
      `expr-error`, `format`, `problem`, `edit`: the bridge's logic in-process), `RachanaConformanceTest` (one dynamic test per case
      grouped by suite; verified fail = red, unverified fail and unsupported = skipped with the reason).
- [ ] 2.2 Bring `CoreKinds` level with the bridge as it is at the pin: a list-valued `ctl` entry is handed over as `DataNode.of(list)`
      (added to the bridge in H2; the patch predates it).
- [ ] 2.3 Proof to repeat after applying: with the pinned jar and `mvn -o`, the test's `jvm.jsonl` equals the Python driver's results
      over the bridge byte for byte for every case both run (on 2026-11-19: 1,457 of 1,458 identical, 0 verified failing; the one
      difference is item 7.1).
- [ ] 2.4 The `sutra` and `patch` kinds need the engine's binder: a second runner `kernel/drishti-engine/src/test/.../EngineConformanceTest`
      using `drishti-rachana`'s test-jar (`maven-jar-plugin` `test-jar`, as `drishti-server` does) with a `sutra` kind over
      `ViewPipeline.builtPreview(...)`, dropping `timings` and the provenance times (FORMAT 5.7). Until then those kinds are unsupported.
- [ ] 2.5 Retire `tools/reference-bridge` here once 2.1 runs in Drishti's build (RUNNER 1: the JVM runner is the reference).

## 3. Guards in report mode (H3)

- [ ] 3.1 `testing/drishti-it/.../RachanaCoreReachTest` (ArchUnit, `@AnalyzeClasses(packages = "com.ash.drishti", DoNotIncludeTests)`,
      sharing ArchitectureRulesTest's import). Core-to-be = every class of `com.ash.drishti.rachana..` except the profile's
      (CORE_AND_PROFILES 2: `about..`, `SutraMatcher`, `MatchTrace`, `model.Match`, `UnderConstruction`, `UiKitDir`, `SutraRegistry`,
      `RachanaConfiguration`, `RachanaProperties`). Report A: each Core-to-be top-level class → each class it depends on outside Core-to-be
      and outside `java..` (product classes, the profile's classes, Spring, SLF4J, Caffeine, Jackson). Report B: each product top-level
      class → the Core-to-be packages it uses (A11's import-rewrite queue; "internal" has no meaning before `module-info` at A10, so all
      uses are listed). Both written sorted, one line each, to `testing/drishti-it/src/test/resources/rachana/core-reach.txt`. A line
      not in the committed file fails ("new reach: remove it, or it is new work"); a committed line no longer found fails too, with
      "regenerate with `-Drachana.reach.write=true`" (the queue only shrinks and the file stays exact). Switches to fail-on-any-line at A12.
      Cross-check: report A's product rows equal `docs/reports/R0_REACH_REPORT.md` of this repository (7 product classes, 6 framework
      packages at i7a).
- [ ] 3.2 `testing/drishti-it/.../RachanaNoSecondCopyTest` (source scan with `RepositoryFiles`), same report-file discipline
      (`no-second-copy.txt`): (a) a Java main class outside `compute/drishti-rachana` whose simple name equals a Core-to-be class's;
      (b) an expression parser outside the module (reuse OneGrammarRulesTest's detection and its two listed exceptions, `DelParser`,
      `ScopeExpr`, rather than a second heuristic); (c) a Sutra YAML reader: Java (YAMLFactory/YAMLMapper/SnakeYAML on `*.sutra.yaml`
      text), Python (`yaml.safe_load`/`yaml.load` in a file that reads Sutras: `core/sutra_summary.py`, `tools/packcatalogue.py`; text
      readers `core/sutra_diff.py`, `designer_outline.py`, plan section 3.3 and 4.8), console JavaScript (none at i7a; the scan proves it).
      Fail mode at A12.

## 4. Delete the Java assertions that are now cases (H2; SPLIT_PLAN 6: moved, never duplicated)

Rule: an assertion is deleted only when a **verified** case with `source: <Test>#<method>` pins the same fact at least as strictly
(the cases pin the complete, ordered problem list with exact messages, where the Java tests use `anyMatch(startsWith(code) && contains(words))`).
Assertions on Java API or model objects stay (they are unit tests of the profile or of internals). After deleting, the module's
`RachanaConformanceTest` must run the cases from the pin (item 2) in the same commit, so coverage stays equal; the proof is this
table: every "delete" row has its cases, verified, in the pinned jar.

| Test | Methods whose assertions move (delete) | Stays in Java (model / API; reason) |
|---|---|---|
| `ControlsTest` | `malformedControlsAreDrs2034`, `duplicateIdsAreDrs2035`, `aCtlReferenceToAnUnknownControlIsDrs2036`, `aCascadingControlNeedsAnEarlierParentDrs2037`, `onSelectMustNameAControlAndPanelsDrs2038`, `defaultsMustFitTheKindDrs2039`, `wherePivotFreeOptionsAreChecked`, `ctlReadsTheControlValuesInExpressions`, `projectionListsAFieldOfEveryElement`, `withinAndOneOfFilterRows`, `aMaskedValueIsNeverWithinOrOneOf` (`problem/controls`, `el/controls-ctl`); the problem/validity assertions of `everyKindReadsWithItsFields`, `aSliderReads...Drs2034And2039`, `thePanelOptionsAreKeptOnThePanel` | the `Control` accessors of those three methods; `aSutraWithoutControlsHasNone`'s `controls()`; `theWorkbenchSetControlsOpWritesTheSection` (an `edit` case `set-controls` still to write) |
| `FormTest` | the lint words of `lintSaysWhatIsWrongInWords` (as Sutra-level cases), the problem assertions of `moneyDatetimeEntityAndDefaults` (`problem/forms`) | `aFormReads` accessors, `expressions` (`FormExpr` API), `theServerChecksValuesAndRules`, `rolesAndReadOnly`, `Form.amount`/`check`/`withDefaults`, `theJsonSchemaDescribesForms` |
| `PanelSourceTest` | validity of a `source:` on data-reading kinds (`problem/panel-source`) | `PanelKind.accepts` / `expressionOptions` loop: the compiler does not refuse `source:` on `links`/`provenance`, so the refusal half is not observable |
| `DrillLevelsTest` | `aBadLevelIsRefusedWithTheReason`, `aBrokenExpressionInALevelIsReportedAtLoad`, validity of `aTableDrillsIntoLevelsAtAnyDepth` (`problem/drill-levels`) | the `levels` option accessors |
| `ScreenLayoutTest` | `lintFindsUnknownAreasOverlapsAndBadSplitsOrTabs`, `aNamedAreaNeedsTheLayoutToNameIt`, validity of the other three (`problem/screen-layout`) | layout model accessors |
| `NestedTabsTest` | `anExpressionInsideATabIsCheckedLikeAnyOther`, `aPanelInATabNeedsAnIdAndAKindATabCanHold`, `idsAreUniqueWithinATab...`, `onlyTabsTakePanels`, validity (`problem/nested-tabs`) | `parts()`, the `SutraWriter` round trip, the two schema assertions |
| `PivotOptionTest` | `mistakesAreDrs2031WithTheirReasons`, `onlyTablesAndLaddersTakeAPivot`, `aBrokenFieldExpressionIsCaughtAtLoad`, the `mode` refusal, validity (`problem/pivot-options`) | `PivotSpec` accessors, `anArrangementIsReadAgainstTheFieldsOnOffer`, `aKindsSpecReadsDocumentPaths` |
| `SutraWiringTest` | `lintFindsUnknownPanelsEventsAndFields`, `lintFindsACycleAndADepthOverThree`, `theParserRefusesWhatIsNotBuilt`, the refusals of `aComputedSourceTakesTypedParametersAsExpressions`, validity (`problem/wiring`) | `Wiring` accessors; `eventsAndSourceAreReadInExpressions` (could become `expr` cases with `given.events`/`src`: a follow-up); `aUseCarriesItsPanelsWiring` (fragments) |
| `parse/PhoneReaderTest` | `aWordThatIsNotATierOrAnOptionThatDoesNotFitTheKindIsDrs2059`, validity of the first method (`problem/phone`) | phone accessors; the fragment method |
| `SutraLayoutEditorTest` | `theGrammarChecksSpanAndHeight` (`problem/phone#span-*`, `height-*`) | the editor itself (`SutraLayoutEditor` is not an operation of `design/ops`) |
| `parse/SutraBlocksTest` | the first assertion of `anUnknownBlockOrParameterIsDrs2048...` (`problem/blocks`) | everything that needs block or fragment files: `SutraBlocksTest`, `SutraFragmentsTest`, `UiKitBlocksTest` stay until the runner supports `files` (block tests keep a unit form with an in-memory resolver, SPLIT_PLAN 6) |
| `design/ops/OpApplierTest` | the text results and refusals of every method (`edit/ops`, 41 cases; multi-op batches split into one case per op) | `applied()` counts, the problem's `op` index and `name()`, `Bind.roles`, `Ops.toJson` round trip, the format errors the bridge maps to `RCH-5020` |
| `design/ops/BlockOpsTest`, `SutraPivotEditorTest`, `format/WorldRegionsTest` | none | block library needed; a separate editor class; the `WorldRegions` API (no format name reaches it) |
| `StrictSutraTest`, `SutraParserTest`, `FormatsTest` (harvested before H2 as `problem/strict-sutra`, `format/formats`) | the asserted codes and lines, for every case `verified: true` | as listed in those files' notes |
| `el/*Test` (F2, `conformance/1/el/*`) | every assertion whose case is `verified: true` (SPLIT_PLAN 6: "a thin JUnit runner; a few unit tests for internals") | the rows of `conformance/1/el/NOT_HARVESTED.md` |

The per-assertion ledger (method, number of assertions, case ids, what is not harvested and why) is in the H2 harvest notes of
[R0 report section](../reports/R0_H2_HARVEST.md).

## 5. The definition and the gates (H4, H5)

- [ ] 5.1 Move the generator into Drishti's compiler module (until A10) or `rachana-tools` (after): the tables it reads by reflection
      today are private (`SutraBuilder.TOP`, `PANEL`, `STRIP`, `COLUMN`; `ControlsReader.KEYS`; `LayoutReader.KEYS`; `PhoneReader.*`;
      `WiringReader.*`; `Form.*`; `PanelOptions.MARKER_KEYS`). Give them one package-level accessor (`parse.SutraTables`), no behaviour
      change. The problem codes have no table: they are literals where raised; `LangDump` reads them from the class files' constant
      pools. A code table (`RchCodes`) is the clean fix, at A1 (the error mapper).
- [ ] 5.2 A stale test in Drishti comparing the generated files with the pinned jar's `spec/1/rachana.lang.json` and `sutra.schema.json`
      (a table change in Drishti fails until the spec here is regenerated and the pin moved).
- [ ] 5.3 The coverage gate stays here (`tools/conformance/coverage.py`); Drishti needs nothing for it.

## 6. Order when the migration starts

1 (pin) → 2 (runner, proof 2.3) → 4 (deletions, one test class per commit, module tests green each time) → 3 (guards) → 5.
VaR goldens and every test of `compute/drishti-rachana`, `kernel/drishti-engine`, `testing/drishti-it` green at each step; one gate
(`tools/drill.sh --gate --no-push`) at the end, the drill with the release that carries it.

## 7. Findings for the plan (not Drishti changes)

1. **`fisher_inv` differs between JDK 21 and JDK 25 in the last digit** (`0.7499999673941484` on 21, `...483` on 25): the reference
   uses `Math.tanh`, which JDK 24+ computes with an intrinsic. Determinism probe P2 and A5 own it (Core should use `StrictMath` for
   the real-number functions). The case carries a tolerance, so it passes on both; the byte gate between runtimes would not.
2. **An editing refusal's message lists choices in `Set.of` order** ("expected one of ..."), which changes between JVM runs: two
   `edit` cases match with `messageStart`. A1/A-steps: sort the list.
3. **`ctl` lists**: the host hands a multi-valued control over as a document list; a runner must too (now in the bridge).

## 8. The split's Drishti half (M1, held: the compiler moved first, owner rule 2026-11-19)

The compiler moved to this repository **before** Drishti was untangled ([SPLIT_PLAN](../design/SPLIT_PLAN.md) section 0). Rachana
`v0.9.0` holds the code of Drishti `main` `c6bc1d1bc` (`compute/drishti-rachana`, less the profile part); the untangling steps run
here. What Drishti must do when the migration starts, in this order:

- [ ] 8.1 **Consume the pin (A11).** Section 1's pin mechanism, with `drishti.rachana.version` at the Rachana release the migration
      takes (`v0.10.0` once the binder has moved, else `v0.9.x`). The four jars (`rachana-api`, `rachana-core`, `rachana-tools`,
      `rachana-data`) held in `packaging/pinned/maven` as section 1 says.
- [ ] 8.2 **Delete Drishti's copy** of every moved file: the paths of `tools/split/a10-paths.txt` here (main code, the signatures
      catalogue, the moved unit tests), each test class deleted only where its cases or its Rachana unit test cover it (section 4).
      Drishti keeps: `about/**`, `SutraMatcher`, `MatchTrace`, `UnderConstruction`, `UiKitDir`, `RachanaConfiguration`,
      `RachanaProperties`, `SutraRegistry` (the directory registry, on Core's `SutraStore` after A7) and their tests.
- [ ] 8.3 **The import rewrite**: `com.ash.drishti.rachana` → `com.ash.rachana` across the product (about 184 files), scripted and
      re-runnable; the other Drishti builders paused on those modules.
- [ ] 8.3b **Settings (A4)**: `RachanaConfiguration` builds one `RachanaOptions` from `RachanaProperties` (`expression-cache-size`,
      `max-expression-depth`, `max-expression-length`, `formats-file`, `pack-formats-files`, the regex caps) and makes the compiler and
      the format table from it (`options.compiler()`, `options.formats()`); no setting is renamed, `CONFIGURATION.md` unchanged.
- [ ] 8.4 **The formats reload hook**: `Formats` no longer implements `LiveSettings` (Spring cannot be a Rachana dependency). Drishti
      adds a small `LiveSettings` adapter in its profile part that reads `drishti.rachana.pack-formats-files`, builds the table with
      `Formats.load(override, files)` and swaps it into the holder the readers use (the behaviour of the deleted `Formats.prepare`).
- [ ] 8.5 **The host types**: Rachana's `hostcompat` copies (`DataNode`, `NodeType`, `ListValues`, `PathWalker`, `IdText`,
      `SafeRegex`, `BusinessCalendar`, `HostException`, `ErrorCode`) are never used by Drishti; Drishti keeps its own and, from A2,
      makes `DataNode` **implement** `com.ash.rachana.api.Node` (no wrapping): `kind()` from its `type()`, and `value()`, `elements()`,
      `fields()` which its records already have (covariant returns), `isArray()`; then `HotPathBenchmark` within 3% of the H7 baseline; from A1 maps `RCH-` codes to `DRS-` at its API edge (`ApiExceptionHandler`);
      from A3 its `SafeRegex` delegates to Core's `com.ash.rachana.el.SafeRegex` (Core reads the caps from `rachana.regex.*`
      system properties until A4; since A4 Core reads none: Drishti passes its `drishti.search.regex.*` settings through
      `RachanaOptions.regexLimits`, which reaches the text functions with A6), and its `BusinessCalendar` is replaced by Core's (the same code) or installed with `Calendars.use(...)`.
- [ ] 8.6 **The tests that stayed** (they read Drishti's files, so they did not move): `SutraParserTest.referenceSutrasParse`,
      `SutraWiringTest.aUseCarriesItsPanelsWiring`, `SutraLayoutEditorTest` (three methods over the IRS example), `OpApplierTest`
      (six methods over `docs/guides/examples`), `PackSutrasTest`, `ReferenceSutrasTest`, `DocumentedSutrasTest`,
      `parse/SutraFragmentsTest`, `parse/UiKitBlocksTest`: keep them in Drishti, running against the pinned jars.
- [ ] 8.7 **Guards (A12, steps 1 and 12)**: section 3's report-mode guards, then fail mode on both sides (SPLIT_PLAN section 5).
- [ ] 8.8 **Proof**: ViewModel goldens byte-identical, Core conformance inside Drishti with the Drishti profile loaded, the full
      targeted set of A1 to A9, then one gate and the release's single drill (PD1); Drishti's tag.
- [ ] 8.9 **Retire the reference bridge** here (`tools/reference-bridge`) once 8.8 is green: until then it is the reference every
      Rachana step is compared with.

- [ ] 8.3c **Registries per instance (A6)**: Drishti builds its `drishti` profile object in its profile part:
      `Profile.named("drishti").functions(new EngineFunctions(…)).formats(<pack formats files>).calendars(<its resolver, if any>)`,
      and one `Rachana.with(options, Profile.core(), drishti, <each started module's profile>)` in a holder that **rebuilds and swaps**
      the instance when a module starts or stops (`ModuleBridge`/`RachanaFunctions` stop calling the removed `Functions.register`;
      `META-INF/services/...FunctionProvider` in bicompute is no longer read: the profile hands the provider over). Every
      `new ElCompiler()` that must see engine functions takes the holder's `rachana.compiler()` (or `rachana.functions()`);
      `Calendars.use(...)` becomes the profile's `calendars(...)`; `drishti.search.regex.*` reach Core through
      `RachanaOptions.regexLimits`. Tests: `RachanaAggregatesTest`, `SutraAggregateParityTest`, `OneGrammarRulesTest`,
      `ModuleHostServerTest`, plus a start-stop-restart test of the holder.

- [ ] 8.3d **The model split (A7)**: the `drishti` profile takes the entity additions — `Entities.addTo(Profile.named("drishti"), () -> servedKinds)`
      — and later owns them (move `Entities`, `Match` and the contributions/UI-kit half of `SutraBlocks` into the profile module).
      Every `new SutraParser()` that reads Drishti Sutras takes `rachana.parser()` (the no-argument one is Core alone and refuses
      `match`); likewise `new OpApplier(rachana.parser())`, `new SutraLayoutEditor(rachana.parser())`, `new SutraPivotEditor(...)`.
      `RachanaSchema.build(formats, kinds)` becomes `rachana.schema(formats)` (the kinds through the supplier); the schema's `$id` is
      now `urn:rachana:schema:sutra:1`. `SutraRegistry` keeps directory scanning, pack precedence, the watch service and the last good
      version, and holds what it reads in Core's `SutraStore`. Tests: `PackSutrasTest`, `SutraRegistryTest`, `SutraBlocksTest`,
      `UiKitBlocksTest`, the schema snapshot with the `drishti` profile loaded, the goldens.

- [ ] 8.3e **The binder behind hooks (A13)**: `kernel/drishti-engine/.../engine/{bind,view,control,wire}` and `engine/live/{Frame,Patch}`
      moved here with history (allow-list `tools/split/a13-paths.txt`; packages `com.ash.rachana.{bind,view,control,wire}`, `Frame` and
      `Patch` in `view`). Drishti deletes its copy and builds the binder with its hooks, in its profile module:
      `new Binder(el, formats, hooks, pivotEngine)` with `BindHooks.core().withLinks(...)` — a `LinkResolver` over `ReferenceCatalog.kindOf`
      and `Mnemonics.codeFor`; `.withPanel(LINKS, ...)` (the old `links(c)`: `LinkRef`s, `BadgeRenderer`, resolved/pending/missing) and
      `.withPanel(PROVENANCE, ...)` (layout label, `Fingerprint.shortForm()`, source and generation), both reading what Drishti puts in
      `BindContext.host()` (its `LinkRef` list, fingerprint, `EntityDocument`); `.withLabels(Semantics::humanize)`; `.withLimits(...)`
      and `.withPivot(new PivotLimits(enabled, maxRecords, "drishti.pivot.enabled"))` from its own `drishti.panels` / `drishti.pivot`
      settings (Core's `PanelLimits` lost its Spring annotations); a `PivotEngine` over `PivotCore` (the old `engineCells`: `RowsProvider`,
      `StateCache.none()`, the same `Settings`, `PivotView.parse`) whose `key` is `CellKey.of(m).canonical()`. A `TableProvider` over
      `SourceRouter` (`search`, `list(...).hits()`, `listAcross(...).hits()`, `fetchAll` with failures, `freshness` from
      `SourceRegistry.Freshness`, `partition()`; `computedId` is `ComputeRef.format`'s algorithm, keep them equal), handing
      `Entity(ref, data, deleted, Origin(source, fetchedAt), the EntityDocument)`; a `Masker` over `ViewerContext` (`mask` = `redact`,
      `mayOpen`), which `TableRows.rows` hands back (cast it to read the viewer); `BusinessDays` over `BusinessDates`
      (`today(asOf)` = `resolve(asOf).businessDate()`); `RelativeDates` is now Core's copy in `control` (delete Drishti's or keep them
      equal). `EffectiveLayout` becomes `Layout(label, inferred, explanations)`; `BindContext` is `(doc, layout, eval, linked, pending,
      denied, maps, host)`; `MapSupport.resolve` takes the `Masker`; `ScreenWire.Screen` takes a `Masker`, `Pair.from` is an
      `Entity.Origin`. Error codes at the edge: `RCH-5001`/`RCH-5002` (bad request, forbidden) and the panel's `RCH-2102` map to
      `DRS-` by the number. Changed answers to review: the too-deep panel message names "the expression depth limit" (was the setting
      name; reached only when the depth limit is lifted); the ViewModel schema's `$id` is
      `https://ajsinha.github.io/drishti-rachana/spec/1/viewmodel.schema.json`; Core alone binds `links`/`provenance` to an error and a
      pivot panel without a `PivotEngine` to an error. The ViewModel records carry Core's `@Include` instead of Jackson's `@JsonInclude`:
      until A14's writer answers the API, Drishti's `ObjectMapper` needs an annotation introspector that reads `@Include` as
      `@JsonInclude` (same rules), or the bytes change (nulls written). The ViewModel keeps contract 1.x's shape (`ref`, `mnemonic`,
      `provenance` top level); C17's `meta.<profile>` is not done (A14 or contract 2). About 73 files' imports of
      `engine.{bind,view,control,wire,live.Frame,live.Patch}` are rewritten (scripted with 8.3). Tests: the goldens byte-identical,
      `ChartPanelsTest`, `DeepTradeViewTest`, `DrillPanelTest`, `MetricPanelTest`, `MapPanelTest`, `SutraNextEquivalenceTest`,
      `ViewPipelineTest`, `LiveTest`, `ViewModelSchemaTest`.

### 8.x A8b API changes the host adapts to at A11 (no Drishti change now)

| Rachana API | Was | Is | Drishti callers |
|---|---|---|---|
| `parse.PositionalYamlReader.read` | throws `IOException` (Jackson's `JsonProcessingException`) | throws unchecked `YamlSyntaxException` (`getMessage()` is the old `getOriginalMessage()`; `line()`, `column()`) | `server/cli/SutraCli` (catch), `SutraFragmentsTest` |
| `design.ops.Ops.parse(JsonNode)` | Jackson tree | `Ops.parse(Object)`: plain JSON values (`Json.read`, or the host's tree converted to maps and lists) | `BuilderController`, `DesignRebase` |
| `design.ops.Ops.toJson` | `ObjectNode` / `ArrayNode` | `Map<String,Object>` / `List<Map<String,Object>>` (same bytes when written) | `DesignRebase` |
| `el.check.Signatures.parse(JsonNode, String)` | Jackson tree | `parse(Object, String)` | none found |

