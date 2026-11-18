<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary. All rights reserved. -->
# Build plan, sequence and impact analysis: Rachana R0 and R1 with Drishti i8

**Status: plan and analysis, 2026-11-17. Nothing built, nothing run.** Written before any code, for the builders that follow.
Read from git only: Drishti `wip/i7a` at `42047f059` ("the base"; i7a is in its release drill), this repository at `fc9812c`,
Drishti Elements `design/views` at `c3cce3e`, `design/eventing` at `a974666`, `feature/events` at `8bbd95f`. No build, test or
server was run, so every statement about behaviour is from reading the code; a point that is an inference and not a line that
was read says **(inferred)**. Drishti paths are relative to its repository root. Sizes are **builder-days** in the sense of
[SPLIT_PLAN](../design/SPLIT_PLAN.md) (S = 1, M = 3, L = 5): relative sizes, not calendar promises.

This plan refines [SPLIT_PLAN](../design/SPLIT_PLAN.md) (its step numbers 0a to 16 are quoted as "SP n") and adds what it does
not cover: Views, the Data Protocol, the CLI, the Python SDK, the proofs, and what the i7a tree says differently.
Deleted when i8 is released; what stays true moves into the design documents.

## Contents

1. [The answer in one page](#1-the-answer-in-one-page)
2. [Dependency analysis](#2-dependency-analysis)
3. [What the existing plans get wrong against the i7a tree](#3-what-the-existing-plans-get-wrong-against-the-i7a-tree)
4. [Impact by area](#4-impact-by-area)
5. [How Drishti consumes pinned Rachana](#5-how-drishti-consumes-pinned-rachana)
6. [Guards, both sides](#6-guards-both-sides)
7. [Risks that would fail a drill](#7-risks-that-would-fail-a-drill)
8. [The sequence](#8-the-sequence)
9. [Lanes, grades and what starts today](#9-lanes-grades-and-what-starts-today)
10. [Decisions needed from the owner](#10-decisions-needed-from-the-owner)

## 1. The answer in one page

**i8 is almost pure engineering, so the plan is built around three rules.**

1. **The risky proofs come first**, before any split work is committed to: the JavaScript build proof (T1 to T10 of
   [COMPILER_AND_RUNTIMES](../design/COMPILER_AND_RUNTIMES.md) 4.2) and the Java ↔ JavaScript byte-for-byte check on the
   places two runtimes drift (number printing, text ordering, the boolean quirks of SPEC section 9, dates, regular
   expressions). Both run in the i7b window (increments P1, P2), beside the conformance harvest (H1 to H6). Their written
   result exists before i8 is cut. If P1 fails, i8a is unchanged for Drishti (JVM only) and the fallbacks of 4.3 apply.
2. **i8 is split in two releases** (recommended, decision N1):

   | | i8a "the engine" | i8b "the product" |
   |---|---|---|
   | Holds | the split (SP 1 to 12), Sutra Core, the binder in Core (SP 13, 14), conformance in every runtime, the JavaScript build | Views (the painter in Drishti Elements and its adoption), `data:` and the Data Protocol v1, `<rachana-view>`, the CLI and static mode, the Python SDK (Flask, FastAPI), in-browser compile in Drishti |
   | Drishti proves it by | the same screens (ViewModel goldens byte-identical), VaR goldens exact, Core conformance with the Drishti profile loaded, about 25,000 lines fewer | measured faster screens against the speed budgets; the parity suite of the renderer; the embed tests unchanged |
   | Size | about 55 builder-days; critical chain A2 → A6 → A7 → A8b → A10 → A11 → A13 → A14 (29) | about 88 builder-days, of which 35.5 are the painter in Drishti Elements |
   | Rachana releases | `v0.9.0` (compiler), `v0.10.0` (binder), `v0.11.0` (JavaScript build) | `v0.12` to `v0.14`, then **`v1.0.0`** |
   | Drills | one (a second only if the first needed fixes) | one (same rule) |

   **Why yes.** One i8 would be about 143 builder-days behind a single drill, with a wide mechanical change (184 files of
   imports) and a wholesale client change (the renderer) in the same drill: a failure could not be attributed. The two halves
   touch different files (i8a: `compute/`, `kernel/drishti-engine`, server wiring; i8b: the console, new endpoints, two other
   repositories), so i8b's lanes in Rachana and Elements run **during** i8a without touching Drishti's tree. i8a ends with a
   result a customer never sees but Drishti can prove; i8b ends with things a person can look at.
3. **i8b ends with three visible deliverables**, each an increment with a done-means (section 8.5):
   - **D1** a static dashboard built from CSV files with `rachana build` (no backend);
   - **D2** a small live dashboard served by Flask and the same by FastAPI;
   - **D3** named Drishti screens measurably faster through in-browser compile, the before and after in Drishti's
     `docs/admin/BENCHMARK.md`, held by the budgets P4, P6, P8.

**The sequence, one line each** (details in section 8):

| Window | Increments |
|---|---|
| now (i7a drilling; this repository only, text and code, no machine) | F1 case format · F2 expression cases drafted by reading · F3 ViewModel schema transcribed · F4 `functions.yaml` drafted · F5 proof plan and JDK-use inventory · F6 Java reactor skeleton and guards · F7 Python package skeleton · F8 Data Protocol normative text and schemas · F9 demo data and Sutras |
| i7b (R0) | **P1 JavaScript proof** · **P2 determinism probes** · H1 JVM runner and the pinned cases jar · H2 harvest · H3 guards in report mode · H4 language definition and schema · H5 `functions.yaml` complete · H6 grammar proof and the known differences settled · H7 baselines · H8 goldens frozen · G1 review |
| i8a | A1 errors · A2 `Node` · A3 small dependencies · A4 Spring out · A5 deterministic text · A6 registries per instance · A7 model and parser split · A8 own YAML and JSON · A9 aggregate kernel · **A10 move, `v0.9.0`** · A11 Drishti on the pin · A12 guards fail · A13 binder behind hooks · A14 binder moves, `v0.10.0` · A15 JavaScript build, `v0.11.0` · A16 docs · G2 review · drill |
| i8b | V1 to V5 painter (Elements 2.6.0) · B1 `data:` and the plan · B2 Data Protocol client and codecs · B3 `<rachana-view>` · B4 CLI and static (**D1**) · B5 Python SDK (**D2**) · B6 Drishti adopts the painter · B7 Drishti serves the Data Protocol · B8 in-browser compile in Drishti (**D3**) · B9 docs, gallery, **`v1.0.0`** · G3 review · drill |
| after i8b (R2, i8.x) | first item: **Rachana Studio** (the generic Sutra designer, rebuilt here and embedded by Drishti; section 4.8, decision N14), one build with the playground |

## 2. Dependency analysis

### 2.1 What depends on i7a (the released base)

| i7a delivered | What in R0/i8 rests on it | Consequence |
|---|---|---|
| Modules as `.dmod` beside the jar; `ModuleHomes`; source mode `ConfigResolution.repository(root)` | "every shipped Sutra" (503 `*.sutra.yaml` files: 225 in module packs, 18 in `config/packs/core`, 43 in `examples/packs`, 193 server test resources, the rest docs and tools) is read through source mode: `PackSutrasTest:40` already does. The goldens (H8), the YAML differential (A8) and the grammar proof (H6) use the same one call | no folder walk of `modules/` is added (i7a guard `NoModuleFolderReaderTest`) |
| Kernel-first loader; `ModuleClassLoader.KERNEL_NAMESPACE = "com.ash.drishti."` | after the rename to `com.ash.rachana` the loader **gives every Rachana class to every module** (`kernelMayGive`, line 84: anything outside the namespace is the kernel's to give) | section 3, C4; decision N8 |
| Child Spring contexts as the design; modules started after the kernel | modules register expression functions late, through `kernel.api.function.FunctionProvider` → `server/modules/host/ModuleBridge` → `RachanaFunctions` → the static `rachana.el.Functions.register`. A per-instance registry (A6) must take a module that starts or is switched off in Admin → Modules | section 3, C7 |
| Typed kernel API and its baseline `kernel-api-surface.txt` | the only Rachana trace in the surface is `Explanation.rachana(String)`. No Rachana type is in a module-facing signature; no file under `modules/` or `drishti-sdk/` imports `com.ash.drishti.rachana` (one banking **test**, `DomainPacksTest`, imports `engine.view`) | the split does not change the kernel API or the SDK; A11 asserts the baseline file is byte-equal before and after |
| `KernelLibraries` and the generated `META-INF/drishti/kernel-libraries.txt` | Rachana's jars become kernel libraries; the generated list changes | regenerated in A11, never merged by hand |
| One exec jar, no module inside it | Rachana's four jars ride inside `drishti-server-<v>-exec.jar` as ordinary nested jars | the release tree, containers and the SDK need nothing new at run time |

Everything that edits Drishti's tree therefore waits for the **released** i7a (the R0 work on a branch from the i7a tag) and
is merged forward with `git merge`, never rebased.

### 2.2 What depends on i7b

| i7b item | Needed by | Not needed by |
|---|---|---|
| **Eventing** (drishti-events, wire protocol v1; Elements 2.5.0 vendored in Drishti; the server side of `engine/live`) | the live half of the Data Protocol (D23: topics `rdp.<table>`), painter step V5 (live), `<rachana-view>` live (B3), the client side of D2, `patch` conformance over a transport, B7 | all of i8a. Patches as **values** (`Frame`, `Patch`, `PatchDiffer`) are conformance `patch` cases with no transport |
| Eventing **rewrites `kernel/drishti-engine/.../engine/live`** (590 lines; `ViewModelSchema` imports `live.Frame` and `live.Patch`) | A13/A14 move the patch model into Core: they must start from the i7b shape. H8 freezes frame goldens **after** i7b's eventing has merged | A1 to A12 (they do not touch `engine/live`) |
| **Lenses retired into role Homes** | nothing in the compiler: no file of `compute/drishti-rachana` or of the binder packages names a lens. Order only: B6 replaces the renderer wholesale and re-approves visual snapshots (`test_snapshots_*_browser.py`); doing it after role Homes avoids approving the Home pages twice **(inferred: role Homes are console pages, not Sutras)** | i8a |
| **K5** (banking's kernel-internals 43 → 39) | nothing: `modules/banking/java/kernel-internals.txt` names no Rachana, `engine.bind` or `engine.view` class | – |
| A released i7b as the base | A1 onwards merge onto it; A2 to A14 are not front-loaded before it (they collide with i7b in `drishti-server`) | H1 to H8 (test-side, their own files) run inside i7b as its third lane |

### 2.3 What depends on R0

| R0 output | Gate for |
|---|---|
| conformance cases passing on the code as it is in Drishti (H1, H2) | every i8a step ("conformance green" is the check of A1 to A14) |
| ViewModel goldens (H8) | A2, A6, A7, A8, A13, A14 (byte-identical) |
| P1 and P2 results | A5 (what "deterministic text" must replace), A8 (what the YAML reader may use), A15 (go, or which fallback) |
| the known differences settled (H6, decision N5) | the cases must not pin a defect: after H6 behaviour is frozen until `v0.9.0` |
| baselines (H7) | A2's 3% gate; D3's "faster" |

### 2.4 What can be front-loaded now, and what must wait

| Can start today (this repository only; text and code, nothing compiled or run while the drill holds the machine) | Why it is safe |
|---|---|
| F1 to F9 (section 8.1) | new files under `conformance/`, `spec/1/`, `java/`, `python/`, `gallery/`, `docs/plans/`; reads Drishti's tree, writes nothing there; every file is verified later by an increment that names it |

| Must wait | For |
|---|---|
| P1, P2, and the first compile and test of F6, F7 | the machine (the drill's end); they still touch only this repository |
| H1 to H8 | the released i7a base (they add tests and resources in Drishti's tree) |
| A1 to A16 | the released i7b base, and G1 |
| V5, B3's live part, B5's live demo, B7 | drishti-events' first usable release (i7b) |
| B6 to B8 | the released i8a (Drishti on a pin with the binder in Core) |
| The painter V1 to V4 | nothing in Drishti: it can start in `drishti-elements` when a builder slot is free during i8a |

## 3. What the existing plans get wrong against the i7a tree

**Sev** = what happens if a builder follows the plan as written: **stop** (cannot be done as written), **silent** (builds and
passes, wrong), **cost** (works, more work than estimated).

| # | The plan says | The base says | Sev | Where fixed |
|---|---|---|---|---|
| C1 | binder and neighbours ≈ 4,700 lines; "about 170 files" import the package | `engine/bind` 2,068 + `view` 1,364 + `control` 820 + `wire` 1,045 = **5,297** (plus `ViewPipeline` 748, which stays). **184** files outside the module import `com.ash.drishti.rachana`: `drishti-server` 80, `drishti-engine` 47, `drishti-bicompute` 19, `drishti-tables` 12, `drishti-modules-it` 12, `drishti-inference` 11, `drishti-benchmarks` 2, `drishti-it` 1. Up to 73 more files import `engine.{view,bind,control,wire}` (server 41, engine 25 with the packages' own files, modules-it 6, banking test 1) and are rewritten at A14 | cost | A11, A14 |
| C2 | SP 9: the aggregate kernel is "about 760 lines" in `bicompute/rachana` | those 763 lines (`ListAggregates` 217, `ListAnalytics` 475, `ListGrouping` 71) are **callers**. `ListAggregates.ENGINE_NAME` maps 47 Rachana names to the engine's built-ins; the maths is in `bicompute/functions` (`Builtins` 810, `MoreAggregates` 556, `Moments` 483, `Holistics` 474, `Comoments` 217, `OncePerParent` 189) written as `kernel.api.pivot.AggFunction<S>` over `Batch`, with `encode`/`decode` and `sqlState`/`fromSql`. `AggFunction` is **published kernel API** (modules implement it): it cannot move or change | stop | A9 as re-scoped; decision N7 |
| C3 | SP section 4: jars "held in Drishti as its other pinned jars are" | Drishti holds **no** pinned jar: `pom.xml` has no `<repositories>`; `lib/ext` is a run-time drop folder; `external_lib` is Python only ("Jars do not go here"). The only pin precedent is the vendored Elements tree and `drishti.elements.version` (`pom.xml:94`) | stop | section 5; decision N2 |
| C4 | "`.internal` is hidden with the Java module system … the Java module boundary at run time" | Drishti runs a Spring Boot exec jar on the **class path**: `module-info` is not enforced there. And after the rename `ModuleClassLoader.kernelMayGive` hands **all** of `com.ash.rachana.**` to every module (today `com.ash.drishti.rachana` is a hidden kernel internal) | silent | A11, A12; decision N8 |
| C5 | SP 11 rewrites imports | `ArchitectureRulesTest:39-95` states the layers by the package pattern `com.ash.drishti.rachana..` (five rules, among them "tables depend on Rachana, never the reverse"). After the rename each rule matches nothing and **passes vacuously** | silent | A11 |
| C6 | no step | [COMPILER_AND_RUNTIMES](../design/COMPILER_AND_RUNTIMES.md) 3.2 requires Core's own number-to-text and date-to-text, but no SP step does it. In the packages that move: `String.format`/`formatted` 10 uses in 4 files; `java.text.DecimalFormat` in `el/TextParts`; JDK number-to-text in `el/Printer`, `el/TextParts`, `format/FormatSpec`; `java.util.regex` in 18 files; `Locale`, `ZoneId` or `DateTimeFormatter` in 28. Without the step the JVM and JavaScript builds cannot be byte-identical | stop | P2, **A5 (new)** |
| C7 | SP 6: "`Rachana.with(options, profiles…)`", a profile is "a value built in code", nothing global | modules add functions **after** the kernel started and can be switched off at run time (section 2.1); `genomics` (`GenomicsFunctions`) and the SDK archetype (`GreetingFunctions`) do it today; `ModuleHostServerTest` and `OneGrammarRulesTest` hold it | stop | A6: the server holds one `Rachana` in a holder and **rebuilds and swaps it** when a module starts or stops (expression cache dropped with it); no late mutation of an instance |
| C8 | SP 5: settings renamed `rachana.*`, the old `drishti.rachana.*` "still read" | two names for one setting is dual machinery, and Drishti's rule is one generated `CONFIGURATION.md`. `application.yaml:1046` (`drishti.rachana`), nine files outside the module name `RachanaConfiguration` or `RachanaProperties` | cost | A4: Drishti's names **stay** `drishti.rachana.*` (they are the host's settings); the wiring maps them to Core's `RachanaOptions`. No rename, no second read; decision N6 |
| C9 | `format/Formats`, `WorldRegions` "read the shared tables of Drishti Elements" | they are packed into the module's jar by `compute/drishti-rachana/pom.xml` from `drishti-console/web/static/vendor/drishti-elements/${drishti.elements.version}/data/{formats,world-regions}.yaml`. After the move Core's jar carries **Rachana's** copy (its Elements pin) while the console formats with **Drishti's** pin: a server-formatted cell and a browser-formatted one can differ | silent | section 5 (the pin guard compares the two files' hashes) |
| C10 | goldens "byte-identical" through SP 13, 14; Core has its own JSON writer | `view/ViewModel`, `PanelData` and `ViewModelSchema` use Jackson's `@JsonInclude`; the API's bytes are written by Spring's `ObjectMapper`. Records in `com.ash.rachana.view` have no Jackson annotation: null-dropping and field order change unless one writer is the truth | silent | A14: the API answers with **Core's canonical JSON writer** (raw JSON body); a test compares it with Jackson's bytes on every golden before the switch |
| C11 | SP 0b "freeze goldens" (S) | no ViewModel golden exists today (`ViewPipelineTest`, `ViewModelSchemaTest` check shape, not bytes). Recording through the flat test class path proves nothing about a packaged server (I7A_IMPACT S6) | cost | H8 (M): recorded in source mode over every pack Sutra, plus one packaged check through `drishti-modules-it/PackagedServer` |
| C12 | D6: the ViewModel specification lives here, imported from the reference (`contract: "1.0"`) | VIEWS_DESIGN 1.1 puts the schema in `elements/drishti-views/spec/` as "the source of truth", with `spec: "1.0"` and renamed fields (`fmt` → `format`, `ev` → `key`, `emptyLabel` → `emptyText`, `title.pill` → `tag`, `ref`/`mnemonic`/`provenance` → `meta`/`status`); SUTRA_FOR_EVERYONE still names `<elements-sutra>` (superseded by D7). Two ViewModel shapes and two homes | stop | decision N3 |
| C13 | SP 0a: "a test harness in `drishti-rachana` that reads case files" | the Drishti module and this repository have the same name; the plan does not say where the cases live before SP 10 or how Drishti's build reads them | stop | H1; decision N4 |
| C14 | – | `tools/drillkit/affected-map.txt:21`: `compute/drishti-rachana/* -> test_rachana*, test_sutra*, test_pivot*`. After the split a language change arrives as a **pin move** (the POM property and the pinned folders), which no line names for those tests | silent | A11: a line for the pinned folders and the property that selects **every** lane |
| C15 | SP 10 tags `v1.0.0` | the Java public API is then frozen by semantic versioning before its three consumers exist (SP risk "cut too narrow or too wide"); SPEC is "draft 1" with 14 open differences; VIEWS_DESIGN Q3 recommends 0.9 first | cost | decision N9: `v0.9.0` at the move, `v1.0.0` at the end of i8b |
| C16 | SP 9 at a glance: "steps 4, 5 and 8 can run in parallel after step 3" and "`6 7` → `8 9`" | 7 and 8 both rewrite `parse/SutraParser` and `parse/SutraBlocks` (two of the eight Jackson users) | cost | A8 split: A8a (the new reader and the differential test, new files, parallel) and A8b (the switch, after A7) |
| C17 | CORE_AND_PROFILES 3.3 rule 5: profile facts go under `meta.<profile>` | the same goldens must stay byte-identical while `ref`, `mnemonic`, `provenance` are top-level fields of contract 1.x | stop | A13: Core emits `meta.drishti`; the profile's **contract writer** (about 150 lines, in Drishti) writes contract 1.x from it, so the API's bytes do not change in i8; the writer retires with contract 2 (parked, decision N3) |

## 4. Impact by area

### 4.1 `compute/drishti-rachana` (22,273 lines main in 130 files; 6,952 lines of tests in 50 files, 264 test methods)

| Package (lines, files) | After i8a | Product dependencies to cut first |
|---|---|---|
| `el` (7,634, 37), `el/check` (1,386, 7) | Core | `api.DataNode` (14 files), `api.NodeType` (5), `api.error.{ErrorCode,DrishtiException}` (6 each), `common.SafeRegex` (2), `common.BusinessCalendar` (2), Caffeine (2 files), Jackson in `el/check/Signatures` |
| `format` (446, 4) | Core | Jackson and Spring in `Formats`; Jackson in `WorldRegions`; resources from the Elements tree (C9) |
| `model` (3,285, 20), `parse` (3,087, 11) | Core, 7 files split (CORE_AND_PROFILES 2.2) | Jackson in `PositionalYamlReader`, `SutraParser`, `SutraBlocks`; `java.nio.file` in `SutraBlocks` |
| `design/ops` (1,894, 22) | Core but `SetMatch` | Jackson in `Ops` |
| `about` (1,398, 12) | **stays** | – |
| root (3,143, 17) | mixed: `RachanaConfiguration`, `RachanaProperties`, `SutraMatcher`, `MatchTrace`, `UnderConstruction`, `UiKitDir` stay; `SutraRegistry` splits; the rest moves | `common.SharedWatchService` (1), SLF4J (3 files), Spring (5 files in all) |
| `resources/rachana/el/signatures.yaml` | splits into `spec/1/functions.yaml` and the profile's catalogue | `FunctionCataloguePage` (`drishti-bicompute`) and `docs/guides/FUNCTIONS.md` are generated from it: the generator reads Core's file from the jar **and** the profile's |

The module stays in Drishti's reactor under the same artifactId as the **profile module** (about 4,500 lines kept, about
1,200 new: hooks, the `RCH-` → `DRS-` mapping, the profile declaration, the contract writer). Its package stays
`com.ash.drishti.rachana`. POM: `drishti-common`, Spring Boot and Caffeine stay only for what remains; the Elements resource
block leaves. `ErrorCode.java` (`kernel/drishti-api`) keeps its ten Sutra and expression codes: they are what the mapping targets.

### 4.2 `kernel/drishti-engine`: the binder

| Files | After i8a | Entanglement read (all of it) |
|---|---|---|
| `bind/*` (8 files, 2,068), `view/*` (7, 1,364), `control/*` (5, 820), `wire/*` (3, 1,045) | Core `bind`, `view` but for the hooks; about 1,700 lines stay as profile binders and the pipeline | `api.DataNode` 9, `api.EntityRef` 5, `api.EntityDocument` 4, `api.AsOf` 3, `graph.ReferenceCatalog` 3, `engine.source.SourceRouter` 3, `graph.LinkRef` 2, `source.SourceFailures` 2, `common.BusinessCalendar` 2, `api.EntityHit` 2, and one each of `graph.BadgeRenderer`, `command.Mnemonics`, `inference.{Semantics,EffectiveLayout}`, `time.BusinessDates`, `search.form.RelativeDates`, `pivot.PivotProperties`, `live.{Frame,Patch}`, `ViewerContext`, `common.Fingerprint`; Caffeine in one file; Jackson `ObjectMapper`/`JsonNode` in one |
| `live/{Frame,Patch,PatchDiffer}` | Core (the patch model); `TopicHub`, `ViewStream`, `LiveMetrics`, `LiveProperties` stay (transport) | rewritten by i7b first (section 2.2) |
| `ViewPipeline` (748), `ViewerContext`, `source/*`, `command/*` | stay | call `rachana.open(...)` |
| `view/ViewModelSchema` (283) and `server/api/ViewModelSchemaController` | the generator moves; `GET /api/v1/viewmodel/schema` answers Core's schema plus the profile's fragment | `ViewModelSchemaTest` stays in Drishti as the regression |

The entanglement is about 45 import lines over 23 files: smaller than SPLIT_PLAN feared, which is why A13 keeps its L but
its one-day spike (DECISIONS open point 4) is answered here from reading.

### 4.3 `compute/drishti-bicompute`: the aggregate kernel (re-scoped, C2)

- **Moves into Core's `agg`**: the accumulator **state and maths** of the names Core specifies (SPEC 5.7 and 5.8: `sum`,
  `count`, `avg`, `min`, `max`, `median`, `percentile`, `distinct`, `wavg`, the variance and deviation family, and the 32
  statistics), with their one treatment of empty and masked values: `Builtins.KSum` (Neumaier), `Count`, `MinMax`, `Avg`,
  `WeightedAvg`, `Welford`, and the parts of `Moments`, `Holistics`, `Comoments` those names use. Plus `ListAggregates`,
  `ListAnalytics`, `ListGrouping` (763) as Core's list functions over `Node`.
- **Stays**: every `AggFunction<S>` (the engine's contract: `Batch` columns, `encode`/`decode`, `sqlState`/`fromSql`,
  `merge`), now a thin shell over Core's state for the moved names; the sketches (`Kll`, `Hll`, `SpaceSaving`, `Sketches`),
  `OncePerParent` (`sum_once`, `count_once`, `avg_once`: SPEC 5.11 "not in Core"), `Final`, `Functions`, `custom/*`,
  `EngineFunctions` (249), `FunctionCataloguePage`.
- **Readers of the built-ins** (they must see no change): `functions/Functions` 19 uses, `custom/{CustomFunctions,JavaPlugins}`,
  `harness/*`, `explain/Explain`, `formula/FormulaFunction`, `lang/{ScanEvaluator,RowsEvaluator}`, `server/tables/MeasureFacts`.
- **The proof**: `RachanaAggregatesTest`, `RachanaStatisticsTest`, `RachanaListAnalyticsTest`, `SutraAggregateParityTest`,
  `DifferentialTest`, the BI recorder (`BiFoundationRecorder`), and the VaR goldens (`VarGoldenMatrixTest`,
  `PackagedVarGoldenTest`) exact. State bytes (`encode`) and `sqlState` text must be **unchanged**: a cluster mid-upgrade merges
  partials from old and new nodes **(inferred)**; A9 adds a test that encodes with the old class and decodes with the new.

### 4.4 `kernel/drishti-server`, `drishti-tables`, `drishti-inference`, tests and benchmarks

- Import rewrite only (C1), except: `EngineConfiguration` and the other eight users of `RachanaConfiguration`/`RachanaProperties`
  (`StudioController`, `design/{DesignBinding,DesignShip}`, `layout/LayoutController`, `pivot/PivotPromoteController`,
  `ErrorShapeTest`, `ImperfectDataTest`, `InferenceEngineTest`); `ApiExceptionHandler` (the one mapper `RCH-` → `DRS-`);
  `modules/host/{ModuleBridge,RachanaFunctions}` (C7).
- `testing/drishti-it/ArchitectureRulesTest` (C5), `LicenseHeaderTest` and `SourceFileSizeTest` (the moved files leave their
  scan; this repository gets the same two guards, the 2,500-line cap included), `testing/drishti-benchmarks/HotPathBenchmark`
  (A2's gate; a benchmark is a heavy job: run alone on a quiet machine, never beside a drill).
- Tests that move or stay: SPLIT_PLAN section 6 holds. Counted: of the 50 test files, 12 stay (`about/*` 4, `MatchTraceTest`,
  `SutraRegistryTest`, `PackSutrasTest`, `ReferenceSutrasTest`, `DocumentedSutrasTest`, `UnderConstructionTest`, `UiKitDirTest`,
  `parse/UiKitBlocksTest`); 38 become cases plus a thin runner.

### 4.5 Launchers, build, drill lanes

| Thing | Impact |
|---|---|
| `pom.xml` | one property `drishti.rachana.version`; one file repository (section 5); `dependencyManagement` for four artifacts; the reactor keeps `compute/drishti-rachana` |
| `tools/drillkit/affected-map.txt` | C14 |
| lanes `java21`, `java25` | the drill's Java copies build from the copied tree: the pinned repository is inside the tree, so a copy resolves offline. Rachana's jars are compiled at release 21 and run on both |
| lane `anywhere` (`test_release_from_other_folder.py`: `git archive HEAD` + the gate's jar) | the pinned jars are **committed**, so they are in the archive (a git-ignored artefact would be missing, as built `.dmod`s are: I7A_IMPACT S23) |
| lane `containers` | `.dockerignore` must not exclude the pinned folder (it excludes `**/target` and module sources today) |
| lanes `browser`, `other`, `tools-a/b` | i8a: unchanged files, so only the affected-map rule matters. i8b: the renderer, the vendored Elements 2.6.0 and the Rachana bundle reach every browser test |
| `bin/`, `tools/drishti.py`, run configurations | none in i8a. i8b adds no launcher: the Rachana CLI is this repository's, not an operator command of Drishti |
| `packaging/drishti-app` | the exec jar gains four nested jars; `kernel-libraries.txt` regenerated; `THIRD-PARTY-NOTICES.md` and `test_vendor_licences.py` gain the first-party entry |

### 4.6 The console (i8b)

| Area | Today | After i8b |
|---|---|---|
| `drishti-console/web/static/elements/drishti-renderer/src` (17 modules, 2,782 lines) | paints contract 1.x | VIEWS_DESIGN A.2: about 1,700 lines leave (the painter is Elements' `drishti-views`), about 1,000 stay (`drishti_net`, `_embed`, `_signin`, `_console`, `_about`, thinner `_elements`, `_index`), `drishti_adapt.js` (about 250) is new. Deleted in the same change: never a flag or a second painter |
| `web/static/render/{1.2.0,2.4.0}` (the public entry) | pinned by version and integrity | a new major with the same exported names (VIEWS_DESIGN M3) |
| `web/static/vendor/drishti-elements/2.4.0` | one pinned folder | 2.5.0 arrives with i7b (events), 2.6.0 with B6 (views); `test_elements_vendored.py` holds "one folder, its manifest and the Maven property say the same version" |
| `web/static/vendor/drishti-rachana/<version>/` | – | new in B8: the runtime bundle, `<rachana-view>`, the manifest of SHA-384 hashes; `VENDOR.md` row; nothing from a CDN |
| Python that reads Sutra text (`core/sutra_summary.py`, `tools/packcatalogue.py`: `yaml.safe_load`; `core/sutra_diff.py`, `designer_outline.py`: text) | display and diff helpers | unchanged in i8; listed by the "no second copy" guard in report mode (H3), judged there, never widened. The Sutra designer's Python (2,751 lines) is the large entry of that list: section 4.8. No console JavaScript parses a Sutra or an expression (none found) |
| Tests | `test_render_parity_browser.py`, `test_elements_renderer_browser.py`, `test_embed_*`, `test_render_*`, `test_sutra_wire_browser.py`, `test_block_sutras_browser.py`, `test_page_walk_browser.py`, `test_snapshots_*_browser.py`, `test_elements_{vendored,version,guards,budget}.py` | the parity suite and the embed tests are B6's gate **unchanged**; snapshots re-approved once, for intended differences only |

### 4.7 Documents (same push as the change, one home per fact)

| Document | Change | With |
|---|---|---|
| `docs/guides/RACHANA_REFERENCE.md` | the language text leaves for this repository's `spec/1/`; Drishti keeps the profile's additions and the designer chapters and ships the specification inside the conformance jar so Help can show it offline | A11 |
| `docs/guides/FUNCTIONS.md`, `docs/architecture/FUNCTION_CATALOGUE.md` | generated from Core's `functions.yaml` and the profile's catalogue | A11 |
| `docs/architecture/VIEWMODEL.md` | links the specification; keeps the profile's fields and the code-to-fix table | A14, B6 |
| `docs/architecture/ARCHITECTURE.md`, `ELEMENTS_IN_DRISHTI.md`, new `RACHANA_IN_DRISHTI.md` (the pin, the checklist of SPLIT_PLAN section 4), ADR 008, `TEST_HARNESS.md`, `BUILD_GUIDE.md`, `DEVELOPER_GUIDE.md`, `OPERATIONS_GUIDE.md` (names the old package), `CONFIGURATION.md` (generated), `API_REFERENCE.md`, `EMBED_GUIDE.md`, root `README.md` (counts from the file system), `CHANGELOG.md`, `docs/admin/BENCHMARK.md` | as each step touches them | A4, A11, A14, A16, B6, B8 |
| 112 documents under `docs/` name Rachana, Sutra or the ViewModel | only those above change; the rest are names-only | – |
| This repository | `README`, `ROADMAP`, the design documents lose "planned" as things are built; `spec/1/*` draft → 1.0; `CHANGELOG.md` starts at `v0.9.0`; this file deleted at i8b's release | every increment |

### 4.8 The Sutra designer: does it still belong in Drishti?

**Answer: the generic Sutra designer belongs here, as "Rachana Studio"; Drishti embeds it and keeps only designers of Drishti
things. Confirmed, with two corrections from the code: it is a rebuild, not a move, and it lands with R2, not i8b** (decision N14).

What the base holds today:

| Part | Where (lines) | What it is |
|---|---|---|
| Editing operations, comment-preserving | `compute/drishti-rachana`: `design/ops/*` (1,894, 22 files), `SutraLayoutEditor`, `SutraPivotEditor`, `SutraWriter`, `SutraText`; used by `server/api/{DesignController,BuilderController}`, `design/DesignRebase` | **Core already**: moves in i8a (A10) into `rachana-tools`, reaches the browser in `rachana.tools.js` (A15) |
| The console's designer modes (Customize, Outline, Grid, settings, forms) | Python: `core/designer.py` 370, `designer_grid.py` 980, `designer_outline.py` 232, `designer_settings.py` 213, `designer_forms.py` 167, `routes/designer_routes.py` 649, `designer_grid_routes.py` 140 (2,751). JavaScript: `drishti_designer{,-grid,-outline,-settings,-forms,-describe,-modes}.js` (1,292), CSS 233, on Elements' drishti-designer | **a second editing implementation**: `designer.py:44-62` loads the Sutra with PyYAML and writes it back with `yaml.safe_dump` (comments and layout are lost; the Java operations keep them); `designer_grid.py` has its own tokenizer for panel formulas. It does not call `design/ops`. This is what the "no second copy" and "SDKs hold no semantics" guards exist to prevent |
| Product-bound helpers of those modes | `core/designer_links.py` 190 (linked records from the packs' `graph.fields`), `core/describe.py` 329 (Describe mode: a deterministic parser led by command mnemonics), `core/designs.py`, `layouts.py` | Drishti's knowledge: stays, offered to the Studio through the profile |
| Server side of designs | `server/design/*` (1,905: `DesignShip`, `DesignBinding`, `PackFragment`, `DesignRebase`, forms, About), `StudioController` (preview, inferred, save), `layout/*`, `engine/design/*` (2,839: `AutoDesigner`, `PanelChooser`, samples) | Drishti's: storage per user, propose and approve, write-back into packs, catalogue-driven drafting (D5). Stays |
| Other designers | Pack Designer, connector designer and data bindings, BI/board and theme designers | designers of Drishti things. Stay |

| | Moves to Rachana Studio (rebuilt on `rachana.tools.js` operations) | Stays in Drishti |
|---|---|---|
| Function | layout and outline editing, the grid mode, panel settings from a fragment's `doc:`, form entry settings, the YAML editor with problems and fixes, live preview through `<rachana-view>`, undo as an operation log | where a Sutra is stored and who may change it (`SutraStorage`: load, save draft, propose), samples (`SampleProvider`), linked-record suggestions, the fragment catalogue of the UI kit and packs, Describe mode's mnemonic clauses, `form.save`, "my version of this page" |
| Profile-aware | kinds, options and keys come from the loaded profile declarations (`drishti.profile.json`), so `links`, `provenance`, `match` and `source: { kind }` appear when Drishti hosts it | the profile and its providers |
| Size | about 5,000 lines of new JavaScript in `js/packages/rachana-studio`, no Python | about 4,000 lines leave (Python 2,751 less about 500 of thin routes; JavaScript 1,292; CSS 233), about 600 arrive (the embed and the providers) |

- **Release: R2, first item ("R2s"), with i8.x and before i9.** It needs A15 (the tools bundle), B3 (`<rachana-view>` for the
  preview), the profile declaration (A6, A11) and Elements' drishti-designer. The playground of R2 **is** the Studio opened on
  sample data, so they are one build, and i9 refines AI-written Sutras in it. Not in i8b: i8b is already about 88 builder-days and
  the Studio is a new user-facing screen (mock-ups first: two or three, the owner picks). Estimated 15 to 18 builder-days plus
  the Drishti embed (about 5).
- **Until then (i8a, i8b):** the Python designer is frozen. H3 lists it in the "no second copy" report; no feature is added to
  it; it keeps working because it only needs Sutra text in and out.
- **Risks.** (1) *No bespoke UI*: the Studio is built from Elements libraries only and arrives in Drishti as vendored files;
  Drishti's embed glue stays under `test_designer_kit_guard.py`. (2) *Write-back*: the Studio never writes a pack; it hands text
  to the host's `SutraStorage`, and Drishti's `DesignShip` path (propose, approve, rebase) is unchanged. (3) *Existing Designs*:
  hidden and added panels are kept today in a Design's notes (`keep`); the embed reads them once into the operation log or the
  Studio must show the same Hide and Show, or people lose their hidden panels **(inferred from `designer.py`'s header)**.
  (4) *Formatting changes*: the first Studio save of a Design written by PyYAML re-lays it out once (the operations keep
  comments from then on); reviewers see one large diff. (5) *Tests*: 23 console test files name the designer or Studio; the
  Sutra-designer ones (`test_designer*.py`, `test_studio_*`) are re-cut against the embed, the pack, connector, BI and board ones
  are untouched. (6) *Describe mode* is product grammar: it stays a Drishti provider, or retires in i9 in favour of the AI path.

### 4.9 The line count

| | Before | After i8a |
|---|---|---|
| `compute/drishti-rachana` main | 22,273 | ≈ 4,500 kept + ≈ 1,200 new |
| `compute/drishti-rachana` tests | 6,952 | ≈ 2,000 |
| engine `bind`, `view`, `control`, `wire` | 5,297 | ≈ 1,700 |
| `bicompute/rachana` callers and the moved accumulator maths | 763 + ≈ 2,000 of 2,729 | ≈ 300 + ≈ 600 of shells |
| **Drishti, Java** | | **≈ 26,000 lines fewer** (A16 counts it from the file system, per README rule) |
| console renderer (i8b) | 2,782 | ≈ 1,250 |

## 5. How Drishti consumes pinned Rachana

Recommended (decision N2). It mirrors the Elements pin and keeps the demo machine and the Windows QA machine offline-safe.

| Artefact | Held in Drishti as | Pin |
|---|---|---|
| `rachana-api`, `rachana-core`, `rachana-tools`, `rachana-data` jars (and their POMs, `.sha1` files) | a **committed Maven file repository**: `packaging/pinned/maven/com/ash/rachana/<artifact>/<version>/`, declared once in the parent POM (`file://${maven.multiModuleProjectDirectory}/packaging/pinned/maven`, `checksumPolicy` fail, releases only) | `drishti.rachana.version` |
| the cases and the specification | `rachana-conformance-cases-<version>.jar` in the same repository (YAML and Markdown, no code). **First used in R0** (H1), so the pin path is proven in i7b's drill by an artefact that cannot break a screen | same property |
| JavaScript bundles, `<rachana-view>` (from B8) | `drishti-console/web/static/vendor/drishti-rachana/<version>/` with `drishti-rachana.json` (SHA-384 per file) | the folder name |
| the manifest | `packaging/pinned/drishti-rachana.json`: version, SHA-384 of each jar, **the Elements version Rachana was built with and the hashes of `formats.yaml` and `world-regions.yaml`** (C9) | – |

Rules: a version is immutable (a re-published version with other bytes would be served stale from `~/.m2`: the guard hashes the
files in the tree, and the release script refuses an existing tag); a pin moves only between tags; the old version's folder is
deleted in the same commit; moving the pin is SPLIT_PLAN's checklist plus "regenerate `kernel-libraries.txt`, FUNCTIONS, the
schema". A release is produced by this repository's `tools/release` (tag, build, write the repository layout and manifest into a
zip attached to the GitHub release); Drishti's `tools/drishti.py` gains nothing: the zip is unpacked into the two folders by hand
of the builder and checked by the guard.

## 6. Guards, both sides

Written **first, in report mode** (H3, with the list as the work queue that may only shrink), switched to fail at A12.

| Side | Guard | Fails when | Increment |
|---|---|---|---|
| Drishti | `RachanaCoreReachTest` (report → fail) | a Core-to-be package uses a product class; or product code uses a Rachana internal | H3, A12 |
| Drishti | `RachanaPinGuardTest` + `test_rachana_pinned.py` | the property, the folders and the manifest disagree; a file's hash differs; a second version is present; Rachana's Elements tables differ from the console's vendored ones (C9) | H1 (jar part), A11, B8 (JS part) |
| Drishti | `ArchitectureRulesTest` (rewritten rules, C5) + "public API only" | a Drishti class imports `com.ash.rachana.internal..`; a layer rule matches no class (each rule asserts it matched at least one) | A11 |
| Drishti | `ModuleClassLoader` hidden prefix + `ModuleVerifier` rule (C4, N8) | a module reaches `com.ash.rachana.**` other than through the kernel API | A11 |
| Drishti | "no second copy" | a Drishti source defines an expression parser, a Sutra YAML reader, or a class named like a moved one | H3 report, A12 fail |
| Drishti | "profile only adds" + `drishti.profile.json` fresh | Core conformance fails with the Drishti profile loaded; the profile registers a Core name; the generated declaration is stale | A6, A11 |
| Drishti | `ApiSurfaceTest` baseline byte-equal across the split | a Rachana type leaks into the kernel API | A11 |
| Rachana | names no host; dependency allow-list; JDK allow-list (from P1); public surface; licence header; 2,500-line cap | SPLIT_PLAN section 5 | F6 (written), A10 (on) |
| Rachana | spec not stale; coverage; additive only | as SPLIT_PLAN | H4, H5, A10 |
| Rachana | **runtimes agree** | `results/<runtime>.jsonl` of Node or a browser differs from the JVM's by one byte | A15 |
| Rachana | SDKs hold no semantics | the JavaScript facade or the Python package has a parser, operator table or formatter | B3, B5 |
| Rachana | the painter's schema is the specification's | the schema copy vendored with Elements differs from `spec/1/viewmodel.schema.json` | B3 |
| Rachana | budgets P1 to P11 | a regression above 10% | A15 (P1 to P3, P10), B3 (P4 to P8, P11), B4 (P9) |

## 7. Risks that would fail a drill

| # | Risk | Seen as | Limit |
|---|---|---|---|
| R1 | A golden changes by bytes that are not behaviour: JSON writer (C10), number text (C6), map order | red goldens late in a drill | H8 records before any change; A5 and A14 each carry a "same bytes as before" test; the canonical writer is switched alone |
| R2 | The import rewrite collides with parallel work or leaves a vacuous rule (C5) | merge pain; a broken layer nobody sees | A11 in one sitting with other Drishti builders paused; scripted and re-runnable; rules assert a match |
| R3 | Aggregates: a pivot or VaR figure moves in the last digits, or old partials cannot be merged | `VarGoldenMatrixTest`, BI recordings, cluster tests red | A9 alone, Opus; accumulator maths moved **without edits** (file moves, then the shell); the encode/decode cross test |
| R4 | The per-instance registry misses a module's functions after a restart or a switch (C7) | `ModuleHostServerTest`, `OneGrammarRulesTest`, the genomics walk browser test | the holder swap with a test that starts, stops and restarts a module and evaluates its function each time |
| R5 | The pinned repository is missing in a lane (archive, container, a drill copy) or `~/.m2` serves stale bytes | the build fails in one lane only, or passes on the builder's machine alone | committed files; `.dockerignore` checked; the pin guard in the gate; a pre-drill check that resolves with an empty local repository |
| R6 | Own YAML reader differs from Jackson on a shipped Sutra | a pack screen fails to load in the browser lane | A8a's differential over all 503 files, node for node with positions, before A8b; the reader refuses what it does not support, loudly |
| R7 | Formats skew between Rachana's and Drishti's Elements pins (C9) | a snapshot or parity test differs by one formatted cell | the hash comparison in the pin guard; a Rachana release states its Elements version |
| R8 | `HotPathBenchmark` gate run on a loaded machine | a false 3% failure or a false pass | baselines (H7) and gates run alone; never during a drill |
| R9 | Generated or append-only files merged by hand | `CONFIGURATION.md`, `API_REFERENCE.md`, `kernel-libraries.txt`, `kernel-api-surface.txt`, `FUNCTIONS.md`, console `answers/*.json`, snapshots, `CHANGELOG.md` | regenerate, never merge (I7A_IMPACT section 2) |
| R10 | i8b: the new painter is slower or differs | the parity suite, snapshots, page-load budgets | VIEWS_DESIGN M0 baseline first (H7); B6 does not merge unless every number is at or better than the baseline |
| R11 | Windows: CRLF in cases or the specification changes "bytes" | conformance red on the Windows QA machine only | `.gitattributes` (`conformance/** spec/** text eol=lf`) in F1; TeaVM is pure Java (no `.exe`) |
| R12 | The binder slips (A13, A14) | i8a late | the cut line of section 8.4: the drill needs A1 to A12; A13 and A14 ride only if green at G2, else they open i8b |
| R13 | The JavaScript proof fails (P1) | no browser compile | known **before i8 starts**; Drishti's i8a is unaffected; fallbacks 4.3; D3 then compiles on the server and i8b's D1, D2 wait or use the fallback the owner accepts (D16) |

## 8. The sequence

Every increment is green alone with its tests and its documents, on its own branch, merged by `git merge`. **Grade**: Opus where
semantics could drift or the design is open in code; Sonnet for the well-specified and mechanical. **Lane**: R = this
repository, D = Drishti's tree (`Dc` compiler and engine, `Ds` server wiring and tests, `Dw` console), E = Drishti Elements.
Two increments in the same lane letter never run at once unless their file sets below are disjoint.

### 8.1 Front-load: now, this repository only (no build, no test run, no Drishti file touched)

| # | Goal | Files (new) | Done means | Verified later by | Grade | Size |
|---|---|---|---|---|---|---|
| F1 | The conformance case format, final: kinds, canonical JSON, `since`, `profile`, `verified`, the results line, the runner contract (cases in, `results/<runtime>.jsonl` out, cases on stdin for third parties) | `conformance/README.md`, `FORMAT.md`, `RUNNER.md`, `conformance/schema/case.schema.json`, `conformance/1/<area>/examples/<kind>.yaml` (one file per kind; `el/examples/arithmetic.yaml` holds the hand-checked arithmetic cases), `.gitattributes` | the schema validates the sample; every kind of COMPILER_AND_RUNTIMES 6.1 has one example; the format is reviewed once and then frozen | H1 | Opus | 2 |
| F2 | Expression cases drafted **by reading** Drishti's tests (`el/ElTest`, `MoreScalarsTest`, `MathFunctionsTest`, `ListFunctionsTest`, `ListMoreTest`, `ListFiltersTest`, `DateFunctionsTest`, `TextFunctionsTest`, `LogicFunctionsTest`, `FinanceFunctionsTest`, `DistributionsTest`, `FoldedSyntaxTest`, `MaskTest`, `ElLimitsTest`, `PrinterTest`, `ValuesNumberTest`, `CheckedModeTest`: about 2,300 lines) | `conformance/1/el/*.yaml`, each case `verified: false` and naming its source test method | every assertion of those tests is a case or is listed in `conformance/1/el/NOT_HARVESTED.md` with the reason | H1 (record mode fills and checks `expect`) | Sonnet | 4 |
| F3 | The ViewModel schema transcribed from `engine/view/{ViewModel,PanelData,ViewModelSchema}.java`, `live/{Frame,Patch}.java` and `VIEWMODEL.md` | `spec/1/viewmodel.schema.json` (marked draft), `spec/1/README.md` row | 55 definitions, every record closed, each naming its Java record | H4 compares it with `ViewModelSchema.build()` after canonical ordering: equal, or the difference is fixed here | Sonnet | 2 |
| F4 | `functions.yaml` drafted from `signatures.yaml`, split Core / profile by SPEC 5.11 | `spec/1/functions.yaml` (draft), a list of names whose null and error behaviour is not stated in SPEC | every Core name of SPEC 5.2 to 5.9 present with signature and one example | H5 | Sonnet | 2 |
| F5 | The proof plan: thresholds agreed first, the twenty expressions of T4, **the JDK-use inventory** of the packages that move (class by class), the determinism stress list (number text: non-whole numbers, NaN, infinities, `1e21`, `-0`; ordering by UTF-16 unit and astral characters; `true + 1`, `true * 1`, `true == 1`; rounding half; dates across zones and leap days; regular-expression acceptance) | `docs/plans/JS_PROOF.md`, `conformance/1/el/determinism.yaml` (inputs only) | a builder can run P1 and P2 from it without a design question | P1, P2 | Opus | 2 |
| F6 | The Java reactor skeleton of COMPILER_AND_RUNTIMES 2 (`rachana-api`, `-core`, `-tools`, `-data`, `-conformance`), empty packages, `module-info`, the guards as tests (names no host, dependency and JDK allow-lists, licence header, line cap), `tools/release` draft, the CI workflow | `java/**`, `tools/release/**`, `.github/workflows/ci.yml` | reads complete against the design; compiles at the first machine slot | first compile after the drill; A10 | Sonnet | 3 |
| F7 | The Python package skeleton: the case reader and results writer, the runtime adapter interface (JVM bridge now, WASM in R2), Data Protocol server-side types (schema, rows page, patch frame), Flask and FastAPI adapter outlines | `python/pyproject.toml`, `python/src/drishti_rachana/**`, `python/tests/**` | no language semantics in it (the guard's pattern list is written with it) | B5 | Sonnet | 3 |
| F8 | The Data Protocol v1 as normative text with JSON Schemas (table schema, rows, query with the filter syntax tree, patch frames, errors) and `protocol` case skeletons | `spec/1/DATA_PROTOCOL.md`, `spec/1/protocol/*.schema.json`, `conformance/1/protocol/*.yaml` | every section of the design has its normative form; section 6.2 marked "follows the events wire protocol v1; re-checked when it ships" | B2, B7 | Sonnet, Opus reviews the filter tree | 3 |
| F9 | Demo content for D1 and D2: synthetic CSV files, the Sutras (sales overview, a live orders board), `rachana.yaml` | `gallery/static-sales/**`, `gallery/live-orders/**` | each Sutra uses only keys of SPEC as settled (D10); synthetic data only | B4, B5 | Sonnet | 2 |

Waves of at most three builders while the drill runs: **{F1, F2, F3}**, then **{F5, F4, F6}**, then **{F8, F7, F9}**. File sets
are disjoint within and across waves, except `spec/1/README.md` (F3, F4, F8 each add one row: merged in wave order).

### 8.2 R0, inside i7b (base: the released i7a; Drishti lane `Dc`/`Ds` is i7b's third lane; P1, P2 are this repository's)

| # | Goal | Files | Done means | Targeted tests | Grade | After | Lane | Size |
|---|---|---|---|---|---|---|---|---|
| **P1** | **The JavaScript proof, T1 to T10**, on a throwaway branch `proof/js` here: a copy of `el`, `el/check`, `format`, `model`, `parse`, dependencies cut by hand, cross-compiled | `proof/js` only (never merged); `docs/plans/JS_PROOF_RESULT.md` on `main` | each of T1 to T10 has a measured answer; T1's gap list becomes A5's and A8's input and the JDK allow-list; go, or the named fallback | the proof's own runs (Node, three browsers) | Opus | F5; the machine | R | 5 |
| **P2** | **Determinism probes**: the stress list of F5 run on the reference (JVM, a test in Drishti that only prints) and on P1's build; every difference written down as "Core rule" + case | `conformance/1/el/determinism.yaml` (expected filled from the JVM), the difference table in `JS_PROOF_RESULT.md` | the list of what A5 must own is closed and sized; no "to be seen" left | the probe test (`ElDeterminismProbeTest`, deleted after) | Opus | P1, H1 | R + Dc (one test file) | 2 |
| H1 | The JVM runner in Drishti reading cases from the **pinned cases jar** (section 5), with a record mode; F2's drafts verified and corrected; the pin mechanism (file repository, manifest, `RachanaPinGuardTest`) | Drishti: `pom.xml`, `packaging/pinned/**`, `compute/drishti-rachana/src/test/.../conformance/*`; here: `conformance/1/el/*` | every `el` case `verified: true` and green; a wrong pin fails the guard | the runner; `RachanaPinGuardTest` | Sonnet | F1, F2 | R + Dc | 3 |
| H2 | Harvest the rest (SP 0a): `problem`, `sutra` (parse level), `check`, `format`, `edit` cases from `StrictSutraTest`, `SutraParserTest`, `ControlsTest`, `FormTest`, `ScreenLayoutTest`, `NestedTabsTest`, `PivotOptionTest`, `DrillLevelsTest`, `PanelSourceTest`, `SutraWiringTest`, `parse/*`, `design/ops/*`, the two editor tests, `FormatsTest`, `WorldRegionsTest`; each Java test then **runs the cases** and its assertions are deleted (moved, never duplicated) | here: `conformance/1/{sutra,problem,check,format,edit}/*`; Drishti: the 38 test files | the module's test count is the cases' count; no assertion kept twice | the module's tests | Sonnet ×2 (H2a `el` side and formats; H2b parse, model, ops), Opus spot review | H1 | R + Dc | 7 |
| H3 | Guards in report mode (SP 1): `RachanaCoreReachTest`, "no second copy" | Drishti: `testing/drishti-it/...`, the two report files | the lists exist, are committed, and a new entry fails | the guards | Sonnet | i7a | Ds | 1 |
| H4 | `rachana.lang.json` and `sutra.schema.json` generated from the tables with a stale check (SP 0c); F3's ViewModel schema compared with the reference's | Drishti: a generator test in `compute/drishti-rachana`; here: `spec/1/*.json` | stale check green; F3's file equal to the generated one or corrected | the stale test; `ViewModelSchemaTest` | Sonnet | H1 | R + Dc | 2 |
| H5 | `functions.yaml` complete (SP 0d): nulls, errors, examples as cases; the coverage gate over the definition | here: `spec/1/functions.yaml`, `conformance/1/el/functions-*.yaml`; Drishti: the coverage test | every key, option, kind, function, format, tone and code of `rachana.lang.json` has a case | coverage test | Sonnet; Opus for null and error semantics | H2a, H4 | R + Dc | 3 |
| H6 | The grammar proof (SP 0e, test part): a recogniser driven by `rachana-el.ebnf` reads every expression of the 503 Sutras; the 14 known differences (SPEC 9, TRANSLATION 11) each settled as decided in N5 and written into SPEC and a case | Drishti: one test; here: `spec/1/SPEC.md`, `TRANSLATION.md` (draft 2) | zero expressions refused by one and accepted by the other; "known differences" sections empty | the recogniser test | Opus | H2 | R + Dc | 3 |
| H7 | Baselines on a quiet machine: `HotPathBenchmark`; VIEWS_DESIGN M0 (today's renderer); page load and control-change times of the D3 candidate screens (`tools/drillkit/pageloads.py`) | Drishti: `docs/admin/BENCHMARK.md` (a baseline section) | numbers recorded with machine and commit | – (measurement) | Sonnet | i7b's eventing merged | Ds | 2 |
| H8 | Goldens frozen (SP 0b, C11): the ViewModel JSON of every reference and pack Sutra on the demo data, the frames of the live cases, the problem list of every documented bad Sutra; source mode plus one packaged check | Drishti: `testing/drishti-modules-it/.../ViewModelGoldenTest`, `src/test/resources/goldens/**` | byte comparison after dropping `timings` and the two provenance times; a re-record needs a flag and shows in review | the golden test | Sonnet | i7b's eventing merged (C15) | Ds | 3 |
| **G1** | Fable pre-drill review of R0 within i7b's review: cases verified, goldens deterministic over three runs, P1/P2 results read, the i8 cut decided | – | written go for A1 | – | Fable | all above | – | – |

Parallel in i7b: P1 (Opus) beside H1 → {H2a, H2b, H3} → {H4, H5, H6} → {H7, H8}. The R0 lane never edits a file i7b's own
lanes edit, but for the append-only ones (R9).

### 8.3 i8a: untangle, move, consume (base: the released i7b)

| # | Goal (SP step) | Files | Done means | Targeted tests | Grade | After | Size |
|---|---|---|---|---|---|---|---|
| A1 | Errors (SP 2): `RachanaException`, `RCH-` codes with today's numbers; one mapper to `DRS-` | `el/ElException`, `SutraException`, `SutraProblem`; `server/.../ApiExceptionHandler` | no moved class extends `DrishtiException`; API error shapes unchanged | conformance; `ErrorShapeTest` | Sonnet | G1 | 1 |
| A2 | The value interface (SP 3): `Node` in a zero-dependency Maven module `rachana-api` (inside Drishti for now); `DataNode implements Node`; evaluator, formats, checker read `Node` | new `compute/rachana-api`; `kernel/drishti-api/.../DataNode`; `el/*`, `el/check/*`, `format/*` | no wrapping at the boundary; masked and missing carried by `Node` | conformance; goldens; `MaskTest` cases; `HotPathBenchmark` within 3% of H7 (quiet machine) | **Opus** | A1 | 3 |
| A3 | Small dependencies out (SP 4): `SafeRegex` as Core's own with the product's delegating to it; `Calendars` hook (default calendar per N5); Core LRU for Caffeine; `Diagnostics` for SLF4J | `el/{DateFunctions,TextFunctions,Functions}`, `common/SafeRegex`, `SutraRegistry` (cache only) | the reach report loses those rows | conformance (dates, text) | Sonnet | A2 | 2 |
| A4 | Spring out (SP 5, as corrected by C8): `RachanaOptions` record in Core; `RachanaConfiguration`/`Properties` stay in the profile part and map `drishti.rachana.*` | `RachanaConfiguration`, `RachanaProperties`, `format/Formats`, the eight readers | no setting renamed; `CONFIGURATION.md` unchanged | context-load tests; `test_docs_settings.py` | Sonnet | A2 | 1 |
| A5 | **Deterministic text (new, C6)**: Core's own number-to-text (shortest round trip), date-to-text, fixed rounding, UTF-16 ordering stated; no `String.format`, `java.text`, `Locale` or zone default left in the packages that move; what P2 listed | `el/{Printer,TextParts,DateParts}`, `format/FormatSpec`, the 4 `String.format` files | every determinism case green; the bytes of every golden unchanged | conformance `el/determinism`, `format`; goldens | **Opus** | A2, P2 | 3 |
| A6 | Registries per instance (SP 6, C7): `Rachana.with(options, profiles…)`; `Profile.core()`; the `drishti` profile object; the server's holder that rebuilds and swaps on module start and stop | `el/{Functions,FunctionProvider,Aggregates,MeasureFunctions}`, `bicompute/rachana/EngineFunctions`, `server/modules/host/{ModuleBridge,RachanaFunctions}`, `EngineConfiguration` | no static registry; two instances in one JVM do not see each other's functions; a profile cannot register a Core name | conformance with the profile loaded; `RachanaAggregatesTest`, `SutraAggregateParityTest`, `OneGrammarRulesTest`, `ModuleHostServerTest`, the new start-stop-restart test | **Opus** | A3 | 5 |
| A7 | Model and parser split along the line (SP 7) | `model/{Sutra,PanelKind,Wiring,Form}`, `parse/{SutraParser,SutraBuilder,WiringReader,SutraBlocks,BlockUses}`, `RachanaSchema`, `SutraRegistry` → `SutraStore` + the directory registry, `design/ops/SetMatch` | goldens byte-identical; the schema snapshot identical with both profiles loaded | `PackSutrasTest`, `SutraRegistryTest`, `SutraBlocksTest`, `UiKitBlocksTest`, goldens | **Opus** | A6 | 5 |
| A8a | Own YAML and JSON (SP 8), the new code only: Core's positional YAML reader and JSON reader/writer as new files; the differential test over all 503 Sutras, node for node with positions, against Jackson | new files in `parse/`; one test | zero differences, or each is a refused construct with a loud message that no shipped file uses | the differential test | **Opus** | A2 (parallel with A6, A7: new files) | 3 |
| A8b | The switch: the eight Jackson users on Core's reader and writer; Jackson leaves the module's POM for the Core packages | `parse/{PositionalYamlReader,SutraParser,SutraBlocks}`, `design/ops/Ops`, `el/check/Signatures`, `format/{Formats,WorldRegions}` | no Jackson import in a Core package | conformance `problem`, `edit`; goldens | Opus | A7, A8a | 2 |
| A9 | The aggregate kernel (SP 9, as re-scoped by C2 and section 4.3) | `bicompute/functions/{Builtins,Moments,MoreAggregates,Holistics,Comoments}`, `bicompute/rachana/{ListAggregates,ListAnalytics,ListGrouping}`, new `agg` package | maths moved by file moves without edits, then the shells; state bytes and `sqlState` text unchanged | the four `bicompute/rachana` tests, `DifferentialTest`, BI recordings, `VarGoldenMatrixTest`, `PackagedVarGoldenTest`, the encode/decode cross test | **Opus** | A6 (parallel with A7: other files) | 5 |
| **A10** | **Move with history (SP 10); `v0.9.0`**: `git filter-repo` on a fresh clone with the reviewed path allow-list; the package rename in one commit; headers; merged into this repository's `main` (`--allow-unrelated-histories`, no force-push) onto F6's skeleton; guards on; the release zip | here: `java/**`; Drishti: untouched | this repository builds alone: unit tests, conformance, names no host; size and product-word scan before the first push; the public API reviewed against Drishti, the CLI outline and the server outline | Rachana's build | Sonnet (mechanics), **Opus** (API surface) | A1 to A9; reach report at zero | 3 |
| A11 | Drishti consumes the pin (SP 11): moved sources deleted; the four jars pinned (section 5); the scripted import rewrite (184 files); `ArchitectureRulesTest` rewritten (C5); loader and verifier rule (C4, N8); affected-map (C14); generated documents; `RACHANA_IN_DRISHTI.md` | wide; one sitting, other Drishti builders paused | goldens byte-identical; Core conformance inside Drishti with the profile loaded; `kernel-api-surface.txt` byte-equal | the full targeted set of A1 to A9, then the affected lanes | Sonnet | A10 | 3 |
| A12 | Guards to fail mode, both sides (SP 12, section 6) | the guard tests | every guard red on a planted violation, green without | the guards | Sonnet | A11 | 1 |
| A13 | Binder behind hooks (SP 13): `LinkResolver`, `Masker`, `TableProvider`, profile panel binders for `links` and `provenance`; Core emits `meta.drishti`, the profile's contract writer writes contract 1.x (C17) | `engine/{bind,view,control,wire}/*`, `ViewPipeline`, new profile binders | the four packages compile against Rachana's public API only; the API's bytes unchanged | goldens; `ChartPanelsTest`, `DeepTradeViewTest`, `DrillPanelTest`, `MetricPanelTest`, `MapPanelTest`, `SutraNextEquivalenceTest`, `ViewPipelineTest`, `LiveTest` | **Opus** | A12 | 5 |
| A14 | The binder moves (SP 14); `v0.10.0`; the pin moves; the API answers with Core's canonical writer (C10); `sutra` and `patch` cases harvested from the goldens with product-only fields removed | here: `rachana-core` `bind`, `view`; Drishti: 73 files of imports, `ViewModelSchemaController` | the Jackson-versus-Core byte test green on every golden before the switch | goldens; `ViewModelSchemaTest`; conformance `sutra`, `patch` | Opus (API), Sonnet (move) | A13 | 3 |
| A15 | **The JavaScript build** (SP 16 less `<rachana-view>`); `v0.11.0`: `rachana-js` (the tool P1 chose), the two bundles, the minimal facade, runners in Node and under Playwright in three browsers, the "runtimes agree" guard, budgets P1 to P3 and P10 | here only: `java/rachana-js`, `js/packages/rachana/**` | **every case byte-identical in JVM, Node, Chromium, Firefox, WebKit**; budgets met or the stated fallback taken | the runners | **Opus** (hand-over, intrinsics), Sonnet (runners, packaging) | A10 for `expr`, `problem`, `check`, `format`, `edit`; A14 for `sutra`, `patch` | 8 |
| A16 | Documents, the line count from the file system, `BENCHMARK.md`, README of both repositories, CHANGELOGs | section 4.7 | the docs checks | `test_docs_*`, `test_tools_guide.py` | Sonnet | A12 or A14 | 2 |
| **G2** | Fable pre-drill review, then **one drill** | – | – | – | Fable | – | – |

**Parallel plan (at most three Opus builders, a fourth only if Sonnet):** A1 → A2 → {A3, A4 (Sonnet), A8a (Opus)} →
{A5, A6 (Opus)} → {A7, A9 (Opus), A8a's tail} → A8b → A10 → A11 → A12 → A13 → A14, with A15 (this repository only) from A10
onwards beside A11 to A14, and the painter V1 to V4 (Elements) whenever a slot is free.

**The cut line (R12).** The i8a drill needs A1 to A12 and A16. A13 and A14 ride if green at G2; A15 never touches Drishti's tree
and is an exit criterion of i8a by its own gate, not by the drill. If A13/A14 slip they are the first increments of i8b and the
line count of i8a is about 22,000, with the rest in i8b.

### 8.4 i8b: the product (base: the released i8a; lanes R and E start earlier)

| # | Goal | Files | Done means | Targeted tests | Grade | After | Lane | Size |
|---|---|---|---|---|---|---|---|---|
| V1 | Painter: the schema taken from `spec/1/viewmodel.schema.json` (N3), the vectors, the generator, the validator (VIEWS steps 1, 2) | Elements `elements/drishti-views/{spec,src}` | Node tests over the vectors | Elements' Node tests | Opus | H4 | E | 6 |
| V2 | Painter core: `paint`, the handle, panel frame and states, layout, strip, title (VIEWS 3) with the P1 to P3 speed tests | `drishti_views-{paint,screen,layout,panels}.js` | both themes; budgets from the first day | Elements browser tests | Opus | V1 | E | 5 |
| V3 | The kinds: words and tables, then data and charts (VIEWS 4, 6) | `drishti_views-kinds.js` | every kind of the schema drawn | gallery tests | Sonnet | V2 | E | 9 |
| V4 | Datasets (VIEWS 5) | `drishti_views-data.js` | inline, Arrow, pages, server model | contract tests | Opus | V2 | E | 4 |
| V5 | Patches, live over drishti-events, `<elements-view>`, the fluent builder (VIEWS 7, 8, 12, 13); **Elements 2.6.0** | `drishti_views-{patch,element,build}.js` | first usable release; frame budgets | patch vectors; live tests | Opus | V3, V4; drishti-events (i7b) | E | 11.5 |
| B1 | `data:` and tables (SP 15, first half): the `data:` block, `TableProvider`, schema-checked expressions, the plan, `rachana explain`; `v0.12.0` | here: `rachana-core`, `rachana-tools` | cases `data/`, `plan/` green in JVM and JavaScript | conformance | **Opus** | A14 | R | 5 |
| B2 | `rachana-data`: the Data Protocol client, JSON and Arrow codecs, the in-memory provider, CSV and JSON readers; the `protocol` cases runnable against a host | here: `rachana-data`, `conformance/1/protocol` | F8's text and the code agree; a reference host in tests | conformance `protocol` | Opus (push-down), Sonnet (codecs) | B1, F8 | R | 5 |
| B3 | `<rachana-view>` and the facade: worker, `Node` over JavaScript objects and Arrow columns, hand-over without copies, live over drishti-events and the minimal SSE form; Elements 2.6.0 vendored here; `v0.13.0` | here: `js/packages/rachana/src`, `js/vendor/drishti-elements/2.6.0` | budgets P4 to P8, P11; strict CSP and Trusted Types pages | browser tests; the two guards of section 6 | **Opus** | A15, V5, B2 | R | 5 |
| B4 | The CLI on Node: `new`, `dev`, `lint`, `fix`, `fmt`, `build`, `preview`, `explain`, `schema`, `functions`, `conformance`; static mode. **Deliverable D1** | here: `js/packages/rachana-cli`, `gallery/static-sales` | **`rachana build` turns `gallery/static-sales` (CSV files + one Sutra) into a site that opens from `file://` with no server**; `lint --format json` is a conformance case; P9 | CLI tests; a browser test of the built site | Sonnet | B3, F9 | R | 6 |
| B5 | The Python SDK: serve Sutras and tables, stream changes, Flask and FastAPI adapters; lint through the Node CLI or the JVM bridge until R2's WASM (no compiler in Python in R1); `v0.14.0`. **Deliverable D2** | here: `python/**`, `gallery/live-orders` | **the same live orders board runs under Flask and under FastAPI in about thirty lines each; rows change on screen without a reload**; the `protocol` cases pass against both | `python/tests`; one browser test per adapter | Sonnet, Opus reviews the streaming path | B3, F7 | R | 6 |
| B6 | Drishti adopts the painter (VIEWS A.5: A1 to A4): Elements 2.6.0 vendored, `drishti_adapt.js`, `render`/tags/sign-in/About rebased on `paint`, the neutral modules deleted in the same change, the public entry's next major | `drishti-console/web/static/{elements/drishti-renderer,render,vendor/drishti-elements}` | **every number at or better than H7's baseline**; nothing a host embedding Drishti sees changes | `test_render_parity_browser.py`, `test_embed_*`, `test_page_walk_browser.py`, snapshots re-approved once | **Opus** (adapter, rebase), Sonnet (parity fixes, samples) | V5, i8a | Dw | 14 |
| B7 | Drishti serves the Data Protocol: a `TableProvider` over virtual tables and datasets **as the viewer**, masks applied before rows leave, the endpoints, topics `rdp.<table>` on its events connection | `kernel/drishti-server/.../rdp/*`, `application.yaml` (token scope rows), `API_REFERENCE.md` | the `protocol` cases pass against Drishti; a masked column never leaves unmasked; `TokenScopeGuardTest` | `protocol` cases; entitlement tests | **Opus** | B2, i8a | Ds | 4 |
| B8 | In-browser compile in Drishti for the screens chosen at H7 (N10): the page gets the Sutra and table addresses, `<rachana-view>` compiles and binds, a control change re-binds locally; the Rachana bundle vendored with its guard. **Deliverable D3** | `drishti-console/web/static/vendor/drishti-rachana/<v>`, the pages' templates and routes, `docs/admin/BENCHMARK.md` | **the before and after table for each named screen (first paint, control change, one live patch), every one faster, P4, P6, P8 held in the gate**; entity screens with masks stay server-bound | page-load tests; `test_rachana_pinned.py`; the screens' browser tests | **Opus** | B3, B6, B7 | Dw | 5 |
| B9 | Documents, the gallery, the tutorial path; the specification declared 1.0; **`v1.0.0`**; Drishti's pin moved to it | both repositories | D1, D2, D3 shown in the gallery and in Drishti's release notes | docs checks | Sonnet | all | R, D | 3 |
| **G3** | Fable pre-drill review, then **one drill** | – | – | – | Fable | – | – | – |

### 8.5 The three deliverables, restated as acceptance

| | What a person does | What they see | Increment |
|---|---|---|---|
| D1 | `rachana build` in `gallery/static-sales` | a folder that opens in a browser from the file system: metrics, a bar chart by region, a sortable table, from three CSV files and a Sutra of about twenty lines | B4 |
| D2 | `python app.py` (Flask) or `uvicorn app:app` (FastAPI) in `gallery/live-orders` | the same board; orders arrive and totals move live | B5 |
| D3 | opens the named Drishti screens | the same screens as before; `BENCHMARK.md` shows first paint, control change and live patch before (H7) and after, with the budget beside each | B8 |

## 9. Lanes, grades and what starts today

| Lane | Owns (nobody else writes here) | Increments |
|---|---|---|
| R: this repository | `conformance/`, `spec/`, `java/`, `js/`, `python/`, `gallery/`, `tools/`, `docs/` | F1 to F9, P1, P2 (result), the case side of H1 to H6, A10, A15, B1 to B5, B9 |
| Dc: Drishti compiler and engine | `compute/drishti-rachana`, `compute/rachana-api`, `compute/drishti-bicompute/{functions,rachana}`, `kernel/drishti-engine/{bind,view,control,wire}` | H1, H2, H4 to H6 (tests), A1 to A9, A13, A14 |
| Ds: Drishti server and tests | `kernel/drishti-server`, `testing/*`, `pom.xml`, `packaging/pinned`, `tools/drillkit` | H3, H7, H8, A11, A12, B7 |
| Dw: Drishti console | `drishti-console/**` | B6, B8 |
| E: Drishti Elements | `elements/drishti-views/**` | V1 to V5 |

Lane Dc is a single-writer chain except where section 8.3 names disjoint files (A3/A4/A8a; A7/A9). A11 takes Dc, Ds and the
import lines everywhere at once: every other Drishti builder is paused for it.

**Grades.** Opus: F1, F5, P1, P2, H6, A2, A5, A6, A7, A8, A9, A13, the API review of A10 and A14, the hand-over of A15, V1, V2,
V4, V5, B1, B3, B6's adapter, B7, B8. Sonnet: everything else. Fable: G1, G2, G3 and this plan. Never more than three Opus
builders at once; a fourth builder only if Sonnet.

**Starts today** (the i7a drill is running: code and text only, three builders, nothing compiled or run): wave 1 **F1 (Opus),
F2 (Sonnet), F3 (Sonnet)**; then F5, F4, F6; then F8, F7, F9. **Starts when the machine is free, still without Drishti's tree:**
the first compile of F6 and F7, then **P1**. **Starts on the released i7a:** H1, H3. Everything else follows section 8.

## 10. Decisions needed from the owner

Beyond D1 to D32. Each has a recommendation; the plan above assumes the recommendation.

| # | Question | Recommendation | Why | If not |
|---|---|---|---|---|
| **N1** | Split i8 into **i8a "engine"** and **i8b "product"**, each with its own review and drill? | **Yes.** | Section 1: about 55 and 88 builder-days; different files; a failure can be attributed; i8b's Rachana and Elements lanes run during i8a | one i8 of about 143 builder-days behind one drill, with the import rewrite and the renderer replacement in the same drill |
| **N2** | How does Drishti hold the pinned jars (C3)? | **A committed Maven file repository inside Drishti** (`packaging/pinned/maven`), with a manifest and a guard; the release zip from this repository's GitHub release is unpacked into it | offline (demo machine, Windows QA), in every drill copy, in `git archive` and in containers; mirrors the Elements pin; about 3 MB per version | GitHub Packages (a token and network in every lane), or `install-file` into `~/.m2` (invisible state, stale bytes) |
| **N3** | One ViewModel contract, and where (C12, C17)? | **(a)** The specification is this repository's `viewmodel.schema.json` (D6); Elements' painter ships a hash-checked copy. **(b)** Version 1 keeps the reference's field names as they are (no renames `fmt`/`ev`/`emptyLabel`/`pill`): the painter draws them. **(c)** Core emits host facts under `meta.<profile>`; in i8 Drishti's contract writer keeps its API bytes at contract 1.x. **(d)** Contract 2 (the API itself writes `meta.drishti`, the writer and the browser mapping retire; VIEWS M6) is **parked for i8.x** | one shape, one home; goldens byte-identical through i8; `drishti_adapt.js` shrinks to the host fields | two schemas and a permanent mapping in the browser (dual machinery), or a contract break inside the split |
| **N4** | Where do conformance cases live during R0 (C13)? | **Here from the first day**; Drishti reads them from a pinned, code-free cases jar (H1). A builder iterating locally points the runner at a checkout with a test-only property | one home; the pin mechanism is proven in i7b's drill by something that cannot break a screen | cases in Drishti's test resources until A10, then moved: simpler for H1, but F2's work has no home now and the pin is first exercised by the riskiest step |
| **N5** | The 14 known differences (SPEC 9: eight; TRANSLATION 11: six): fix the reference, or pin it? | **Pin the reference's behaviour as `rachana: 1`** for the language quirks (SPEC 9 items 2 to 7; TRANSLATION 11 items 1, 2, 4), each stated in SPEC with a lint warning where it surprises (`true + 1`). **Move the host conventions to the profile** with no change a Drishti user sees: the default calendar (Core: weekends only; Drishti's `Calendars` hook gives `USNY` as today), id-like column linking, `graph.focus`, `line.source`, `ref`/`mnemonic`/`provenance`. SPEC 9 item 8 is a documentation fix | "behaviour frozen during the split" holds; no Sutra a customer has changes meaning | fixing quirks in R0 changes goldens before they are frozen and needs a pack-by-pack review |
| **N6** | Drishti's setting names after the split (C8)? | **Keep `drishti.rachana.*`**; no rename and no second name | one way per job; `CONFIGURATION.md` unchanged | a rename with an old-name read, against the standing rule |
| **N7** | Scope of the aggregate move (C2): which code goes into Core? | **The accumulator state and maths of the Core-listed names only** (SPEC 5.7, 5.8), by file moves; every `AggFunction` shell, the sketches and the `_once` family stay in the engine | D3 is kept (one implementation, lower layer) without touching the published `AggFunction` contract, the state bytes or SQL push-down | moving `AggFunction` itself breaks the kernel API for modules; moving only the 763 lines leaves Core without `sum` |
| **N8** | May a Drishti module use Rachana's API directly (C4)? | **No for now**: `ModuleClassLoader` hides `com.ash.rachana.**` from modules as it hides kernel internals; modules keep the kernel API (`FunctionProvider`, `Explanation`). Inside the kernel, `com.ash.rachana.internal..` is closed by an architecture rule, since `module-info` is not enforced in the exec jar | the kernel API stays the one module contract; a Rachana minor can never break a `.dmod` | modules bind to Rachana's Java API and every pin move becomes a module compatibility question |
| **N9** | The first tag: `v1.0.0` at the move (SP 10) or later (C15)? | **`v0.9.0` at the move; `v1.0.0` at the end of i8b**, when Drishti, the CLI and the Python SDK have run on the public API and the specification is no longer a draft | the API and the specification are frozen after three consumers proved them, not before | `v1.0.0` in i8a and a likely `v2` of the Java API within weeks |
| **N10** | Which Drishti screens compile in the browser for D3? | **Only screens whose data is already a governed table the viewer may read whole and that is under the client threshold** (list, board and dashboard screens: five named at H7 with their baseline). Entity screens with masked fields stay server-bound | masks are applied where the data lives; "faster" is claimed only where it is measured | a wider claim that cannot be honoured for masked data |
| **N11** | The painter in Drishti Elements for i8b: its first usable release only (VIEWS steps 1 to 8, 12, 13; 35.5 days) as **Elements 2.6.0**, with the ViewModel helpers for Python and Java (steps 15, 16) dropped and the ViewModel playground folded into Rachana's later playground? | **Yes** (as SUTRA_FOR_EVERYONE 7.1 already proposes) | a Sutra is the way to author from any language; the helpers would be a second way | 68.5 days in Elements and two authoring paths |
| **N12** | `LICENSE-SPEC.md` is still a draft: approve it before the specification is published? | **Approve before B9** (the specification is declared 1.0 and published there). Nothing in R0 or i8a publishes | "open specification" must be true in the licence on the day the page goes up | publication waits for R3 |
| **N13** | P1 needs the machine for about a week of light builds during i7b, on a throwaway copy of the compiler sources in this repository's `proof/js` branch (pushed, never merged). Agreed? | **Yes**, starting when the i7a drill ends; never beside a drill or a benchmark | its answer must exist before i8 is cut | the JavaScript risk is discovered inside i8a |
| **N14** | With Rachana leaving, where does the Sutra designer live (section 4.8)? | **Here, as Rachana Studio, rebuilt on Core's editing operations and embedded by Drishti; first item of R2 (i8.x), one build with the playground; mock-ups first.** Drishti keeps storage, governance, write-back, samples, link suggestions and its own designers (pack, connector, BI/board). The Python designer is frozen from now and deleted when the embed lands | today's console designer is a second editing implementation in Python (PyYAML round trip); the comment-preserving operations already move to Core in i8a; i9 needs one place to refine generated Sutras | keeping it in Drishti keeps two editing implementations and gives Rachana no editor; putting it in i8b adds about 20 builder-days to the larger half |
