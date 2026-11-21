<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary: see LICENSE. -->
# Changelog

Entries are written per release; the regime's unreleased work is listed after the releases it follows.

## Unreleased (G2)

### Fragments are functions (decision L-21, owner 2026-11-21), level 1.2 (G2 lane A2)

- **Binding like a call** (SPEC 2.5, LIBRARIES 5 to 7, DECISIONS L-21): a table parameter names its local columns or a
  published contract (`contract: <name>`; `contracts/<name>.json` in a library, `Library.contract`, or served by the host,
  `FragmentResolver.contract`); the use binds it as `rows: $.t` (identity) or `{ from, columns: { <local>: <caller> } }`,
  renamed at compile time in the panels that read it (field options, `bind`, `@.<column>`). With the caller's columns known
  (`data:` `fields` or written rows) problems name both sides: `RCH-2114` unknown local column, `RCH-2115` required column
  missing, `RCH-2116` unknown caller column or type misfit; `RCH-2120` advisory for a mapping never read (severity warning,
  `Sutra.advisories()`). Every typed fragment is hermetic (`RCH-2117`).
- **Instances and return values**: `as:` scopes ids (`<as>-<id>`); `emits: [{name, payload, doc}]` declared and checked
  against the inner panel that raises it; the screen reads `@<as>.<event>.<field>`; `on: {<event>: {set, field}}` sets a
  control from the event's row (`on-select`). `TypedFragment.bind`, `TypedFragment.Caller`, `SutraBlocks.Calls`.
- **Compile result records library hashes**: `Sutra.libraries()` (`UsedLibrary`: name, exact version, SHA-384), from
  `LibrarySet.fragments(..., Consumer<Library>)`; the `problem` case output carries `libraries` (FORMAT 5.8, schema).
- **Conformance**: `sutra/fragments-calls` (6), `problem/fragments-calls` (11), verified; JVM and both JavaScript bundles
  agree at 1.0, 1.1, 1.2; level-1.0 replay and the customer corpus unchanged. API additions only (`api-surface.txt`).

### IntelliJ launchers and installing a release

- **Build launchers**: `java/run-configs/` holds four IntelliJ Maven configurations in the folder **Rachana** (build all, test, install release for a release tag only, JS bundles), the build guide is [java/README](java/README.md#build-in-intellij-idea), and a release is installed into `~/.m2` for Drishti from its tag only (decision R-1).

### Rachana-EL level 1.1: the syntax batch and exact numbers (lane B: EL phase 1)

- **Syntax at language level 1.1** (SPEC 4.17, EBNF part D; EL_EXPANSION X6, X7, X11 to X14; DECISIONS PY1 to PY8): record literals
  `{a: 1, 'k-2': x, ...r}` and list spread `[...a, b]`; Python slices `t[a:b:c]` and `range(start, stop, step)` (end-exclusive; `..` stays
  reserved; `range` is only the sequence at 1.1, the aggregate max − min is `span`, EL1-1); the pipe `x |> f(a)`; `a ?? b`; `a if c else b`; lambdas `lambda x: …`,
  second class (map, sort, reduce, group, `let`; no recursion; `=>` stays reserved); `**` and infix `^` (right-associative, `-2 ** 2` is
  `-4`); comments `/* … */`; digit separators `1_000_000`. Reserved names are refused by name at 1.1.
- **Numbers built at 1.1** (SPEC 4.16, X2, XD3): literals without an exponent are exact integers and decimals (34 digits), `+ - * %`
  exact, `/` half-even to 34 digits, a double on either side a double, overflow of the integer part `RCH-2102`; `sum` exact over
  integers and decimals. **Texts by code point** at 1.1 (SPEC 4.13, XD4): `left`, `right`, `mid`, `find`, `search`, `pad`, ordering,
  `sort`, `least`, `greatest`. The N5 level-1.1 variants: problem messages `expression '<source>': …` with the token's end,
  `standardize` a number, a refused pattern at evaluation `RCH-2102`.
- **Level 1.0 unchanged**: the replay of `compat/levels/1.0` is identical; the level is the compiler's (`ElCompiler(…, level)`,
  `Rachana.language`). API additions only (`Expr.Op`, `Minus`, `Power`, `Coalesce`, `RecordLit`, `Pair`, `Spread`, `Slice`,
  `Lambda`, `FnRef`, `LocalCall`; `Printer.print(e, v11)`; level-aware `CheckedMode.check`/`parse` and `Checker.check`;
  `ElException.end()`). The checker types `??`, `**` and the exact operators (X8, first part). `FilterTree` reads level 1.1 trees.
- **Conformance**: `el/syntax-1-1` (114 cases) and `el/syntax-at-1-0` (13), verified; `el/numbers-exact`, `el/text-code-points`,
  `el/reserved` and `el/messages-level-1-1` verified, their cases that failed at 1.0 moved to `since: "1.1"` (9 reserved-token
  cases withdrawn: 1.1 defines the tokens; two `**` message cases withdrawn: PY6). `tools/regime/levels.py check` skips a recorded
  case that moved to a later level or was withdrawn only when `compat/levels/moved-cases.yaml` lists it (EL1-4, below). JVM, both JavaScript bundles
  and the Python V8 engine agree.

### The EL1 decisions built (DECISIONS "EL1 decisions taken")

- **EL1-1**: at level 1.1 `range(...)` is only Python's sequence (the dispatch on the first argument is gone); the aggregate max − min is
  `span(list, field?)` (functions.yaml, `since: "1.1"`; Core's `range` kernel); a list or a text written as `range`'s argument does not
  compile (`RCH-2101`), one met at evaluation is `RCH-2102`, both naming `span`; a masked argument masks. Level 1.0 keeps the `range`
  aggregate and has no `span`. Cases in `el/syntax-1-1` and `el/syntax-at-1-0`.
- **EL1-4**: `compat/levels/moved-cases.yaml` lists every recorded 1.0 case that moved to a later level (46, with `since`) or was withdrawn
  (9, with the reason); `tools/regime/levels.py check` skips only those and fails on any other difference and on a wrong entry
  (`levels.py compare`, tested by `LevelReplayTest`). A level's `functions.yaml` is spec/1's less the entries of a later `since`.
- **EL1-2**: checked mode at 1.1 warns `RCH-2091` on `-x ** n` or `-x ^ n` without parentheses (`-(x ** n)` by Python's rule, `(-x) ^ n`
  in Excel), with both spellings as the fix (SPEC 4.17, 6.2).

### `data:` and the Data Protocol; `<rachana-view>` (lane C: SPLIT_PLAN steps 15 and 16, B1 to B3)

- **`data:`** at language level 1.1 (SPEC 2.4, D10): entries `{ table }`, `{ url }` (or an address), `{ rows }`, `{ record }`, each a
  field of `$`; `key`, `fields`, `as`, `where` (reads controls), `limit`, `refresh`, `live`, `mode`, `format`, `path`. Problems
  `RCH-2073` (not exactly one source, a bad name), `RCH-2072` (live without a key), `RCH-2011`, `RCH-2012`. At level 1.0 `data` stays an
  unknown key (`RCH-2011`): the level-1.0 replay is identical.
- **Core, `com.ash.rachana.table`** (one implementation; the JavaScript bundles are the same code): `TableSchema` (cells decoded by type:
  decimals exact, big integers, masked), `KeyedRows` (patches in the order of PROTOCOL 8.1; keys compared as JSON text; append windows),
  `FilterTree` (a row condition as the protocol's filter tree with controls and constants folded; a received tree checked and spelt as
  the Rachana-EL condition of the same meaning), `DataPlan` (the plan document: per table the query, the readers and what stays local;
  a panel's `where` is pushed down only for the table's sole reader; `RCH-2070`, `RCH-2071`), `DataRecord`, `LiveScreen`; `ViewDiff`
  (ViewModel frames: `strip`, `panel`, `rows` patches).
- **`java/rachana-data`**: `MemoryHost` (the protocol transport-free: schema, rows, query with filter, sort with the key as tie-breaker,
  cursor paging, grouping with exact decimal totals, the change log, gaps, the bridge), `ReferenceServer` (JDK HTTP server, the minimal
  SSE stream, the test adapter of PROTOCOL 15, static files for a page), `TableClient` (the JVM client: the plan's queries, paging, the
  stream into a `LiveScreen`).
- **JavaScript** (`js/packages/rachana/src`): the bundles export `patch`, `plan` and `screen(op, request)`; `DataClient` (transport only:
  fetch, cursor paging, the SSE stream or a host's `subscribe` hook, the origin allow-list), `Screen`, and `<rachana-view>` painting with
  `<elements-view>` from Drishti Elements 3.1.0, vendored (`js/vendor/drishti-elements/3.1.0`, the release's `pack` of core, views and
  events, with its SHA-384 manifest).
- **Conformance**: the runner answers the kinds `patch` and `plan` (`steps` compared per step); new cases `sutra/data` (9),
  `problem/data` (8), `plan/plan` (8), `patch/patch` (5), `protocol/host` (10); the frozen example `plan/examples/plan` corrected as its
  note said (`select` is not a control kind). `tools/conformance/protocol_check.py --reference` runs the 40 protocol cases on the
  reference server: 39 pass; `protocol/examples/bridge#bridge-rows-request-answered-once` sorts a table that does not declare `sort`,
  which PROTOCOL 4 refuses (unverified; `protocol/host#bridge-rows-of-a-sortable-table` is the corrected case). JVM and JavaScript agree
  on all 2,160 cases at levels 1.0 and 1.1; budgets: runtime 379 KB, tools 399 KB Brotli. Tests: Core `TablesTest`, `MemoryHostTest`,
  `TableClientTest` (JVM end to end), `js/packages/rachana/test/data_client.test.mjs` (Node end to end),
  `runners/view_browser.py` (`<rachana-view>` live in Chromium).
- Not yet (lane C2 below builds the protocol's rest): checked mode over table schemas, exact decimal arithmetic in aggregates (the
  decimals item, X2).

### The Data Protocol completed: row forms, bridge subscriptions, conflation, filters (lane C2)

- **Arrow IPC and NDJSON** (PROTOCOL 5.2, 5.3, 12). `java/rachana-data`: `ArrowRows` writes a page as an Arrow IPC stream and reads one
  back with the official Apache Arrow Java library (19.0.0, `arrow-vector` with the `arrow-memory-unsafe` allocator; allowed in this
  module only, Core stays free of it; a JVM that runs it opens `java.nio` to `org.apache.arrow.memory.core`), types by PROTOCOL 3.1,
  masked cells as `<name>$masked`, the protocol facts as schema metadata (`rachana.totals` and `rachana.schema` added); `NdjsonRows`
  (header, then one line per row). The reference server answers both (Arrow only for tables declaring `arrow`, else `406`); the bridge
  answers `accept: arrow` with `res.arrow` (base64). `TableClient.read(table, query, Form)`. JavaScript: `arrowPage` (the vendored
  Apache Arrow JS 21.1.0, `js/vendor/apache-arrow`, the same file Drishti Elements vendors), `ndjsonPage`, `DataClient({form, arrow})`,
  `<rachana-view form="arrow|ndjson">`. Every form gives the same rows (`RowFormsTest`, `data_client.test.mjs`). Cases
  `protocol/forms` (7); `protocol_check.py` compares Arrow and NDJSON answers as the JSON page they stand for (Arrow with pyarrow).
- **Subscriptions** (PROTOCOL 8.2 to 8.4, 12). `MemoryHost.subscribe(table, after, filter, everyMs, sink)` gives a `Subscription`:
  the backlog then live frames in seq order; **filtered** (`changes.filter`): each patch restated for the rows the reader sees (a row
  that stops matching is a `remove`, one that starts an `update` with every field, an untouched patch an empty one so the numbering has
  no holes; resumable only from the current position, else `recovered: false`); **conflated** (`everyMs`): one message per window,
  merged by key (`Subscription.merge`: later remove cancels, later fields win, `reset` restarts), carrying `first` and `seq`. The
  bridge: `MemoryHost.bridge(push)` opens a channel (`sub` answered by `res {recovered, version}`, `msg` with the `sub` id, `unsub`);
  the reference server's SSE stream runs on a subscription and takes `POST /changes {table, after, filter?, conflate?}`; the test
  adapter gains `GET /_test/bridge/out` (PROTOCOL 15). `TableClient.follow(..., filter, everyMs, ...)`, and a gap now stops the follow.
  JavaScript: `BridgeChannel` (requests, `res.arrow`, `sub`/`msg`) behind `DataClient({bridge})`, `follow(..., {filter, everyMs})`;
  `Screen` follows a table read through a pushed-down filter with a filtered subscription where the table offers one, and takes a
  conflated message's `first`. Specification additions: PROTOCOL 8.2 (the POST form), 8.4 (`first`), 12 (`sub.conflate`, `msg.sub`,
  `msg.first`, the `sub` answer), 15; `bridge.json`, `patch.json`. Cases `protocol/live` (8); tests `SubscriptionTest`, the Node bridge
  test.
- **Chart panels in `<rachana-view>`**: the vendored Elements 3.1.0 pack now has `drishti-chart` (with `drishti-inputs`, Apache
  ECharts in the script, and its run-time files under `vendor/`), packed from the `v3.1.0` tag with its SHA-384 manifest. The browser
  test's Sutra has a line panel; `runners/view_browser.py` passes in Chromium 156, Firefox 157 and WebKit 27.2 (Playwright 1.64.0).

### The `rachana` command and the Python SDK basics (G2, CLI and SDK lane)

- **CLI (Node, `js/packages/rachana-cli`)**: `rachana new` (starter project), `lint` (compile and problems; `--format text|json|sarif`,
  `--strict`; exit 0 / 1 errors / 2 usage), `build` (lints, then a static site: pages, Sutras, data files, protocol index,
  runtime bundle), `preview` and `dev` (local server). `lib build|sign|keygen|pack|verify|lock` is the Java `LibraryTool` behind
  `com.ash.rachana.cli.Main` (the jar launcher; the Node launcher spawns it: one implementation). Not yet: the painter step of
  `build` and `preview` (`<rachana-view>` is on g2/data), live reload, `fix`, `fmt`, `explain`.
- **Python SDK**: `python -m drishti_rachana lint` / `rachana lint` on V8 (`drishti-rachana[v8]`), same output formats and exit
  codes; `drishti_rachana.server.DataHost`, a minimal Data Protocol v1 host (JSON; schema, rows, query filter tree, cursor paging,
  keyed patches, SSE changes); `RachanaBlueprint` (Flask) and `RachanaRouter` (FastAPI) replace the stubs; the live-orders example
  uses them. Reuse of lane C's server shapes is pending the g2/data merge; the host follows DATA_PROTOCOL.md directly.

### Language level 1.2: typed fragments (C1) and libraries (X42), pulled forward by decision L-19

- **Typed fragments** (SPEC 2.5, LIBRARIES 4): `TypedFragment`; a fragment whose `params:` are declarations with a `type` is
  checked at each use: value parameters (an expression in the user's context, read as `arg.<name>`; literals checked against
  the type and `min`/`max`; `text` taken as written), table parameters (`columns`, `optional`, `when-bound`), name parameters
  as checked tokens (`%{name}` only for those; `id`, `format`, `tone`, `icon`, `variant`, `area`, `palette`, `key`, `enum`,
  `columns`), `slots` of panels. Problems `RCH-2114`..`2118`; a typed use below level 1.2 is `RCH-2008`. The text form is
  untouched: every Drishti fragment expands to the same bytes (CG3, 478 Sutras, byte-identical; level-1.0 replay identical).
- **Libraries** (SPEC 2.5, LIBRARIES 3, 7, 9 to 12; package `com.ash.rachana.lib`): the top-level key `libraries:` (level 1.2;
  `RCH-2008` below), `use: <library>/<fragment>@N` and the long form `<library>@M/<fragment>@N`; `LibraryResolver` (memory,
  folder `rachana_libs/<name>/`, a folder of `.rlib` files, `first` of several, a conflict refused) and `LibrarySet` (resolver +
  `rachana.lock` + trust; libraries cached by content hash; dependencies loaded, cycles and depth 4 checked); the
  `FragmentResolver` hook keeps its signature, served through an adapter (`LibrarySet.fragments`). `Library.load` checks the
  file-type rule, the manifest, `MANIFEST.sha384`, `exports` staleness, the uses inside a library and the scope of a public
  library's typed fragments (`RCH-2117`). `LibraryLock` reads and writes the lock. `Rlib`: the deterministic `.rlib`
  (sorted, stored, fixed time) and `Sha384` in plain Java, so the JVM and the JavaScript build compute one identity.
  `Grammar.withFragments`, `Grammar.withLibraries`, `Rachana.libraries`. Codes `RCH-2110`..`2113`.
- **Tools** (`rachana-tools`): `LibraryTool` (`build`: exports and hashes; `sign`; `pack`; `verify`; `lockEntry`) and `Ed25519`
  (the JDK's Ed25519; a `SignatureVerifier` for JVM hosts; keys are plain `ed25519:<hex>`). `LibraryToolTest`.
- **Conformance**: the new kind `lib` (FORMAT 5.13, schema); `files` now read by the runtimes for `sutra` and `problem` cases
  (fragment files, `rachana_libs/`, `rachana.lock`); `sutra/fragments-typed` (5), `problem/fragments-typed` (18),
  `lib/libraries` (7), all verified on the JVM; the JVM and both JavaScript bundles answer every case byte-identically at
  1.0, 1.1 and 1.2 (`build_js.py` now checks level 1.2; runtime 383,760 B, tools 404,588 B Brotli, within the budgets).
- API baseline: additions only. Not yet: `as:`, `emits`/`on:`, private state, `let`, outputs, `fn` parameters, column mapping
  `{from, columns}`, library functions (X41), the classpath and package resolvers, the `rachana lib` commands of the CLI, the
  generated `rachana.lang.json`/`sutra.schema.json` at level 1.2.

## v0.11.0 (2026-11-21): the JavaScript build, ViewModel contract 1.1 and the G1 gate

### ViewModel contract 1.1 and language level 1.1 (datasets; grid, tree, chart)

- **Contract 1.1**, appended to 1.0 (SUTRA_EXPANSION 6.3; VIEWMODEL "Contract 1.1"): `datasets` (`Dataset`, `DataField`,
  `ArrowRef`, `PagesRef`, `ServerRef`: inline `values` or `rows`, `name`, Arrow, REST pages, a server-side row model; raw values
  formatted by the painter, masked as `{"$masked": true}`), the panel kinds `grid` (`Grid`, `GridColumn`, `SortKey`, `GridPivot`),
  `tree` (`Tree`) and `chart` (`DataChart`, `Measure`), map `points` (`GeoPoint`, bubbles), and the patch op `rows` (reset, remove,
  update, add by key). Every addition is left out when unused; the **stamp is the smallest minor that describes the document**
  (`1.1` only with datasets, a dataset kind or map points), so every existing ViewModel keeps its bytes (the level-1.0 replay is
  identical; Drishti stays at level 1.0). `spec/1/released/viewmodel-1.0.schema.json` written (`freeze.py --contract 1.0`, the contract v0.9.0
  released): CG4 now checks the current schema contains it. `spec/1/viewmodel.schema.json` gains 13 definitions (`x-since: "1.1"`),
  structurally equal to the records' generated schema (`compare_viewmodel_schema.py`, which now ignores `x-since`).
- **Language level 1.1** (SPEC 2.3, 7.2 rule 6): the kinds `grid`, `tree` and `chart` put their rows into the view's datasets
  (`DatasetBinder`; options `dataset`, `rowKey`, `group`, `sort`, `limit`, `search`, `empty`; `node`, `parent`, `path`, `label`,
  `mode`, `open`; `x`, `y`, `by`, `agg`, `stack`, `type`, `horizontal`, `xLabel`, `yLabel`, `bins`, `top`, `fmt`). Levels are real
  in the grammar: `Grammar.atLevel`, `Rachana.language(level)`; every instance starts at 1.0, where the new kinds are not offered
  (`RCH-2021`'s list unchanged) and using one is `RCH-2008`. A conformance case runs at the level of its `since`.
- Conformance: `sutra/binder/datasets` (6 cases, since 1.1, verified on the JVM), `problem/levels` (1 case, since 1.0; a known
  difference from the reference, which has no level 1.1). the customer corpus (478 Sutras) byte-identical on Core; JVM and the JavaScript build agree on all 2,130 cases at levels 1.0 and
  1.1; 0 verified failing; `levels.py check`: level 1.0 identical. API baseline: additions only (view records, `Grammar` levels,
  `PanelKind.since/at`, `BindContext.datasets`, `Rachana.language`). `DatasetPanelsTest`. The generated `rachana.lang.json` and
  `sutra.schema.json` still describe level 1.0 (generated from the reference's tables); SPEC lists the 1.1 names.
### G1 re-check, R1: the runtime bundle

- **R1, the runtime bundle built at `52e112f` was broken** (named formats not applied, `round`, `shift_date`, `b.ni is not a
  function`; Node stopped on a null read). Root cause in TeaVM 0.16.0, not in the merge: the precise dependency analysis merges
  type-set domains (`Transition.mergeDomains`) in an order that varies between runs, and some orders lose types: no `Boolean`
  reached `Plain.asBoolean`, so `booleanValue()` stayed a virtual call with no implementation emitted. The merge only changed the
  order. Fix: a second patched TeaVM file, `PreciseDependencyAnalyzer` (`java/rachana-js/src/build/java`, Apache-2.0, marked
  "Drishti Rachana patch"): no domain merging. The bundles are now the same on every build (three builds, two heap sizes).
- **The build checks both bundles**: `tools/js/build_js.py` runs the conformance suite in Node on the runtime and tools bundles
  at levels 1.0 and 1.1 after every build (`--check-only` runs the check alone) and fails on any error or verified failing case,
  or when the runtime answers a case differently from the tools bundle (the runtime leaves out only the 43 `edit` cases). The Node
  runner counts a case that throws as that case's error instead of stopping.
- Results: both bundles 0 errors, 0 verified failing at 1.0 and 1.1; the runtime's 1,954 (1.0) and 2,006 (1.1) answers and the
  tools bundle's 1,997 and 2,049 are byte-identical to the JVM's. `./mvnw clean verify` (JDK 21): 912 tests, 0 failures, 0 skipped.
- Bundle SHA-256 (P1-3): `rachana.runtime.js` `45bbfa4a6e99b34627bd86f75d1bcaaf744e4f583630e6de6ddd2fb47388269e` (1,412,427
  bytes; Brotli 366,858), `rachana.tools.js` `e25dfc97e2b1dbbcc8d756097691fb25ad40b402420078fed0fc33f55c4cfb9e` (1,511,248
  bytes; Brotli 387,940).

### G1 gate fixes

Findings of the i7b gate 1 review (Drishti `docs/plans/I7B_GATE_1_REVIEW.md`).

- **F4, the block library per instance (A6)**: `parse.BlockLibrary` holds what `SutraBlocks` kept in static fields (the library,
  its problems, warnings and clashes, the UI kit folder, the modules' contributed sections). A host makes one per instance and hands
  it to its profile (`Entities.addTo(profile, kinds, blocks)`, or `Profile.Builder.fragments`); two instances never share blocks.
  `SutraBlocks` keeps the stateless half (`read`, `expand(..., resolver)`, `sizes(..., resolver)`, `packOf`, `flow`, `isUse`).
  Core alone resolves no block (`FragmentResolver.none()`, was the static folder library). API: removed the static `SutraBlocks.load`,
  `kit`, `fromKit`, `library`, `blocks`, `find`, `problems`, `warnings`, `clashes`, `contribute`, `withdraw`, `contributedUses`,
  `expand(3 args)`, `sizes(1 arg)`, `SutraBlocks.Contribution` (now `BlockLibrary.Contribution`), `FragmentResolver.folders()`;
  changed `BlockUses.of(text)` to `of(text, resolver)`; added `BlockLibrary`, `FragmentResolver.none()`, `Entities.addTo(…, blocks)`,
  `Entities.match(kinds, sections)` (DRISHTI_MIGRATION_TODO 8.3i).
- **F1, the customer corpus on Core (CG3)**: `CustomerCorpusTest` compiles the 478 Sutras of `compat/customers/drishti/2.9.11.i7a`
  on Core (`CoreRuntime`: one instance with the conformance suite's `drishti` profile, `conformance.Cases.profile`, and its own
  `BlockLibrary`), by default; every document is byte-identical to the recorded `compiled/`, and the test checks that all 478 were
  compared. A runtime that cannot run fails the test (it was skipped). The reference bridge runs only with
  `-Drachana.compat.runtime=bridge`. The Drishti profile's formats are not loaded: no format enters these documents. Not yet: the
  corpus's recorded ViewModels (`binds/`, F13), before G4.
- **F2, one regex budget per call**: `SafeRegex.replaceAll` draws every match on one `maxSteps` budget (it was one budget per match,
  so a pattern that rescans the value for each match cost the value's length times the budget: 0.6 to 1.7 s for one call on 8 KB).
  The review's three 8 KB cases now stop at `RCH-4011` within one budget (`SafeRegexBudgetTest`). `find` and `extract` were already
  one scan. A `regex_replace` over many matches can now reach the budget where it did not.
- **F3, a form field's `pattern` on `SafeRegex`** (one regex engine, DECISIONS G1-1): matched against the whole value by the new
  `SafeRegex.matches` (linear time; a value over the input cap is refused, never cut); look-around, back-references and the rest
  of what `SafeRegex` does not offer are refused at lint (`RCH-2058`, SafeRegex's words in brackets). No shipped Drishti Sutra
  (repository and the i7a corpus) has a form pattern. `internal.RegexText` deleted (Core and the JavaScript build).
- **F9**: `el/determinism#d-m01-a` expects Core's `exp(1)` (`2.7182818284590455`); its known-differences reason corrected.
  Level 1.0 re-recorded for this case and `problem/forms#pattern-not-a-regex` only (DECISIONS G1-2, for the owner); conformance
  `--jvm`: 2,123 cases, pass 1,938, fail 58 (all unverified, F8), 0 verified failing.

### The JavaScript build

Step A15. Not tagged: the tag waits for the owner (G1 gate).

- `java/rachana-js` (Maven profile `js`) builds `rachana.runtime.js` and `rachana.tools.js` with TeaVM 0.16.0 through its tool
  API; `tools/js/build_js.py` copies them into `js/packages/rachana/dist/` and the Python package and prints their sizes and
  SHA-256. **Rule (P1-3): the release notes of every release record both bundles' SHA-256.** At `28d01bf`: runtime
  `d0ced04fc29dd911c30aa11c8b8fd75b9952d8f4081e3e28cd01a52c5139d1bb` (361 KB Brotli), tools
  `b012b4111a5efb11a6c9a14eb6b6cd965a347e1bd1382da27524532d4a23c0cd` (381 KB Brotli); two clean builds give the same bytes.
- The view package reads no record by reflection: generated writers (`ViewRecords`) and the schema text generated at build time
  (`ViewModelSchemaText`), checked fresh by a test; ViewModel bytes unchanged (conformance, and 331 corpus Sutras built both ways).
- Runtimes agree: every conformance case byte-identical to the JVM in Node, Chromium, Firefox, WebKit and Python on V8
  (`--node`, `runners/run_browsers.py`, `python -m drishti_rachana.engine`); `conformance.Cases` is the one harness, no I/O.
- Python on V8 (W-1): `drishti_rachana.engine.Rachana` compiles and lints, evaluates, formats, builds views and edits (extra `v8`).
- Core: `exp`, `ln`, `log10` and the trigonometric functions through `CoreMath` (six fdlibm functions added); `NumberText.toDouble`,
  `CoreDecimal.round`, `internal.Bits`, `internal.RegexText`; the guard refuses `Math::`, `doubleValue()` and `BigDecimal.round`
  elsewhere. Three JVM results move by one unit in the last place (level 1.0 re-recorded, D15-4).
- One TeaVM file patched (`StatementRenderer`, Apache-2.0): its printer dropped the parentheses of `a * (b / c)` (D15-2).
- Re-measure (P1_RESULTS section 11): T1, T5 to T10 pass; T2 and T4 miss, for the owner (D15-5, D15-6).

## v0.10.0 (2026-11-21): the untangling

Steps A1 to A8b, A13, A14 and A7b are in (the binder moved and behind hooks, its API, Core's canonical writer, form expressions in
Rachana-EL). Not tagged: the tag waits for the owner (G1 gate). Proof: `./mvnw clean verify` green on JDK 21; conformance
`--jvm` 2,095 of 2,123 identical to the reference, the 28 differences each listed in `tools/split/known-differences.txt`, 0 verified
failing. API baseline changes since v0.9.0 to note: the binder packages (`bind`, `view`, `control`, `wire`), `Rachana.binder`/`views`,
`ViewBuilder`, `el.FormRules`, `RCH-5001`/`RCH-5002`, and the planned removals listed in the entries below.

- **A7b, form expressions in Rachana-EL**: `and`, `or` and `not` are Rachana-EL operators (DECISIONS PY1; `not` reads below the
  comparisons, as in Python); a form's rules, hints and `=` defaults are read by Rachana-EL's parser and evaluated by the `form`
  profile (`el/FormRules`): exact decimals with their written scale, `/` to 16 digits, `+` joining a text, empty values, three-valued
  `and`/`or`, a rule that cannot be told holds. `FormExpr` keeps its API; its own lexer and parser are gone. Every answer equals the old
  evaluator's (`FormRulesTest`, the old one kept in the tests); changed: the words of a syntax error, Rachana-EL's spellings read too,
  `_` separators taken out by the profile (DECISIONS A7b-1 to A7b-4, for review). Conformance: `el/python-words` (6 cases, unverified,
  known: the reference refuses the words); 2,095 of 2,123 identical to the reference, 28 known, 0 verified failing. The reference's
  `sutra` program is now `RefBridgeSutra.java` (the engine class path changes other kinds' answers). API: `el.FormRules`.
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
- **A9, the aggregate kernel in Core**: the state and maths of the aggregates SPEC 5.7 and 5.8 list, and the list functions over
  them, moved here with their history from Drishti `c6bc1d1bc` (git filter-repo, `tools/split/a9-paths.txt`): `Builtins`, `Moments`,
  `MoreAggregates`, `Holistics`, `Comoments`, `Final`, `ListAggregates`, `ListAnalytics`, `ListGrouping`, now
  `com.ash.rachana.agg` (exported). Core's own contract `Aggregate` (start, add a columnar `Batch`, merge, finish, encode and decode
  with the engine's bytes, SQL state) and `Kind`; `Catalogue.of(name, …)` gives an aggregate by name; `CoreAggregates` is the first
  provider of every function table, so `sum`, `count`, `avg`, `min`, `max`, `median`, `percentile`, `distinct`, `wavg`, the variance
  family, the 32 statistics, the list analytics and `group(...)`'s grouping are computed by Core (the catalogue says `R`, source
  `Rachana Core`). What only a host computes stays with it and is refused in Core alone with the reason: the `_once` family,
  `measure`, `cell`, a model's measure-level functions (decision N7); the sketches and the `AggFunction` shells stay in the engine.
  The moved maths uses Core's own classes (A5's guard covers `agg/`; `CoreMath.hypot` added, equal to `StrictMath.hypot`). Results:
  16 aggregate cases that the reference refused now pass, seven determinism probes expect Core's values, and refusals that list
  the known functions now name the aggregates (56 known differences in all, 0 verified failing; 1,924 cases pass); level 1.0 written
  again (37 results). 855 unit tests (the moved `RachanaAggregatesTest`, `RachanaStatisticsTest`, `RachanaListAnalyticsTest`).
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
- **A5, the same bytes on every runtime**: Core no longer asks the JDK for anything whose answer differs between runtimes. Its
  own classes do it: `CoreMath` (an fdlibm port of `exp`, `log`, `log10`, `log1p`, `expm1`, `pow`, `tanh`, `atan2`, `sin`, `rint`
  and the saturating casts; equal to `StrictMath` on Java 21 bit for bit), `NumberText` (Core's one door between doubles and text;
  on the JVM Java's shortest round-trip printing and correctly rounded reading; the JavaScript build swaps in a same-named class),
  `CoreText` (upper and lower case, with the expansions and the final sigma, white space, strip and reversal by code point, from
  Java 21's Unicode 15.0 tables carried in Core), `CoreDecimal.sqrt` (whole-number arithmetic; equal to `BigDecimal.sqrt`), and
  `DateText` (English month and day names, and the `date` format's pattern printed by Core). `text(v, code)` prints Excel number
  codes with Core's own printer (`java.text` left Core; equal to the `DecimalFormat` it replaces for every code in its grammar,
  and two quoted texts side by side no longer print a stray quote). `String.format` is gone. The regular-expression budget is a
  count of steps, not a clock: `SafeRegex.Limits(maxPattern, maxProgram, maxInput, maxSteps)` (default 20 million steps) replaces
  `timeoutNanos` (planned; API baseline rewritten). The `date` format's `MMM` no longer follows the JVM's default locale, and
  `WorldRegions` no longer upper-cases with it. A guard (`CoreDeterminismGuardTest`) refuses these JDK calls anywhere else in Core,
  the binder moved by A13 included (its case mapping, strip, `rint` and number reading now go through Core's classes).
  Results: 17 conformance results move in the last place, all in the P2-1 list (`norm_s_dist`, `f_dist`, `binom_dist`,
  `hypgeom_dist`, `gamma_dist`, `npv`, `irr`, `mirr`, `rate`, `log`, `fisher`), listed in `tools/split/known-differences.txt`;
  the two determinism probes among them now expect Core's value; every other result is byte-identical (2,093 of 2,112 identical to
  the reference, the 19 known; 0 verified failing). Language level 1.0 (`compat/levels/1.0`, not yet released) was written again
  by `levels.py write 1.0`: 18 results (the 17, and `fisher_inv`, which had been recorded on Java 25) and the `fisher(0.5)` example.
  New tests: `CoreTextTest`, `CoreDecimalTest`, `DateTextTest`, `NumberCodeTest`, `SafeRegexBudgetTest`, the guard; 822 unit tests.
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
