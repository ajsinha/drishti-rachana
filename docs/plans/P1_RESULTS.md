<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary. All rights reserved. -->
# P1: the JavaScript proof, results

**Status: run 2026-11-19 (i7b M0, lane Opus 1), thresholds as agreed in [JS_PROOF](JS_PROOF.md) section 4.2; no threshold
was moved.** Determinism details are [P2_RESULTS](P2_RESULTS.md); the WebAssembly proof is [WASM_RESULTS](WASM_RESULTS.md).
Everything here is reproduced by `tools/proofs/proof.py` (section 10).

## 1. The answer

| Step | Threshold | Measured | Verdict |
|---|---|---|---|
| **T1** compiles | each gap has one replacement inside Core | **4 gaps** (TeaVM's class library lacks `BigDecimal.sqrt(MathContext)`, `Collections.unmodifiableSortedMap`, `Matcher.appendReplacement/appendTail(StringBuilder)`), each closed by one change in the copy; records, pattern `switch` with `when`, sealed types, lambdas, `java.time`, `java.util.regex`, `BigDecimal`, streams all compile | **pass** |
| **T2** size, Brotli 11 | runtime ≤ 150 KB, tools ≤ 350 KB | runtime-shaped entry **247 KB** (TeaVM `ADVANCED`) / 328 KB (`FULL`); the whole P1 cut (parse, check, evaluate: tools less `design/ops`) **300 KB** / 411 KB | runtime **miss** (1.6×); tools **pass on ADVANCED** before `design/ops` (estimate 330 to 345 KB with it) |
| **T3** start | module evaluation to first compiled Sutra ≤ 30 ms (Chromium) | Chromium: first compile of the typical Sutra **56 ms** after a 179 ms module evaluation (2.4 MB raw); warm parse + check typical p95 **3.8 ms** (≤ 8 ✓), large p95 **13.3 ms** (≤ 30 ✓) | **miss** (cold), warm budgets met |
| **T4** evaluation speed | each of the twenty within 3× a hand-written JavaScript evaluator | against a hand-written tree-walking evaluator (fallback 3's shape): **5× to 83×, median 11×** in Chromium, 7× to 138× in Node; against hand-written closures 9× to 5,000× | **miss** (all twenty) |
| **T5** data hand-over | no allocation proportional to table size per change | a 100-row change on 50,000 rows touching only the changed rows: **0.2 ms** (Chromium), heap flat across 10,000 and 50,000 rows; today's `DataNode.Arr` (a `List.copyOf`) forces an O(table) copy per change: 0.5 to 0.75 ms and 3.5× the garbage | **pass with an A1 requirement** (a host-backed list, no copy) |
| **T6** same output | byte-identical, or every difference owned by a planned replacement | 1,081 runnable cases + 255 probes; differences only in seven classes, each owned (P2 section 4) | **pass** (conditional on A5) |
| **T7** strict pages | no violation in three browsers; no dynamic code | strict CSP with Trusted Types: module page, classic script from `file://`, module Web Worker: results identical, **no violation** in Chromium, Firefox, WebKit; bundle scan: no `eval`, no `Function(`, one `setTimeout` with a function | **pass** |
| **T8** errors | always the language's problem, never an engine error | 11 cases leak the engine's `RangeError` (TeaVM's `double`→`long`, P2 class D2); in embedded engines 12 more leak `ReferenceError: performance` (D6); depth limits refuse before any stack overflow | **fail as built**; both causes are A5/A3 rules |
| **T9** reproducible build | same bytes from the same source | the class files are identical, the **bundle is not**: three clean builds, three SHA-256s (and two more in the clean `proof.py all` run: `a180d736…`, `81f66990…`) (TeaVM 0.16.0 names and orders some classes differently run to run); the results of the three bundles are byte-identical; Windows not run (no access to the QA machine from this lane) | **fail** (bytes); results reproducible |
| **T10** the tool | compatible licence; maintained; a second tool | TeaVM: Apache-2.0 (notice drafted, section 8), 12 releases in 24 months (0.11.0 2024-12-14 … 0.16.0 2026-10-02). J2CL: Apache-2.0, but its Maven integration (`com.vertispan.j2cl`) last published 2023-11 and its JRE emulation has no `java.time` (unverified beyond its documentation) | **pass** |

**Go or fallback.** Semantics: **go** (T1, T6, T7 pass; T8 is fixed by rules A5 already owns). Speed and size: **the named
fallbacks are required** (section 9): T4 misses by an order of magnitude, T2 and T3 by less. T9 triggers fallback 1
(J2CL) by the letter of JS_PROOF section 6; the recommendation is not to switch compilers for it (section 9, decision P1-3).

## 2. Versions and machine

TeaVM 0.16.0 (Maven Central, 2026-10-02), JDK 21.0.13 (builds and reference; Java 25.0.5 only to show P2's `tanh`),
Maven 3.9.12, Node 22.22.1, Playwright 1.63.0 with Chromium 153.0.8010.12, Firefox 155.0, WebKit 26.6; AMD Ryzen AI 9 HX
370 (24 threads), Linux, shared with three other builders (load 2 to 11 during the runs: timings are indicative, ratios
are the finding). Drishti source: `c6bc1d1bc` (read with `git archive`, never checked out); reference jars: Drishti's
`target/*.jar` built from that commit.

## 3. What was built

The cut of JS_PROOF section 2.1: `el`, `el/check`, `format`, `model`, `parse`, `SutraText`, `SutraWiring`,
`SutraExpressions`, `SutraProblem`, `SutraException`, and the product types they need copied as they are (`DataNode`,
`NodeType`, `PathWalker`, `ListValues`, `IdText`, `ErrorCode`, `DrishtiException`, `SafeRegex`, `BusinessCalendar`):
**93 files, about 18,900 lines**. Cuts (`tools/proofs/cuts.py`), each one text replacement or file:

| Cut | Was | Now |
|---|---|---|
| C2 | Caffeine caches in `ElCompiler` | a bounded LRU on `LinkedHashMap` (same API, `com.ash.proof.Cache`) |
| C3 | Jackson `JsonNode` reading `formats.yaml`, `world-regions.yaml`, `signatures.yaml` from the class path; Spring `LiveSettings` on `Formats` | the same JSON tree the reference's Jackson read, generated into a Java constant; the Spring reload method removed |
| C4 | `PositionalYamlReader` on Jackson YAML | reads the tree (values, lines, columns, issues) Drishti's own reader produced on the JVM (the prep step) |
| C5 | Jackson exception and string-quoting types in `SutraParser`, `SutraBlocks` | look-alikes with Jackson's documented behaviour |
| G1 to G4 | the four T1 gaps | `CoreDecimal.sqrt` (correctly rounded half even; equal to the JDK on 300,000 random inputs), a plain `TreeMap`, a hand loop for `appendReplacement/appendTail` |

**Faithfulness:** the cut copy on the JVM is **byte-identical** to Drishti's jars on all 1,118 conformance cases and 255
probes (the gate of JS_PROOF 4.1 step 2, `proof.py jvm`). The harness (`tools/proofs/java/harness`) turns a case into the
results line of RUNNER.md section 4 (canonical JSON of FORMAT section 8); it is one Java source on every runtime.

## 4. T1: the gap list

| Gap | Where | Replacement (one, in Core) | Feeds |
|---|---|---|---|
| `BigDecimal.sqrt(MathContext)` | `el/MathFunctions.sqrt` | Core's correctly rounded square root (`CoreDecimal`) | A5 |
| `Collections.unmodifiableSortedMap` | `common/BusinessCalendar` | an unmodifiable view Core owns (or the calendar becomes a hook, A3) | A3 |
| `Matcher.appendReplacement/appendTail(StringBuilder)` | `parse/SutraBlocks.expand` | a scanner (J12: Core has no `java.util.regex`) | A5 |

Rows of JS_PROOF 2.2 confirmed by the build: L1 (records' `ObjectMethods` bootstrap) and L2 (`SwitchBootstraps.typeSwitch`)
**compile** under TeaVM 0.16.0; J5 TeaVM emulates `long` with JavaScript `BigInt` (decisive for T4); J9 `java.time`
compiles and every date probe agrees (P2). The allow-list of JS_PROOF 2.5 stands, plus: no `Math.rint`, no `(long)` cast of
a double, no `StringBuilder.reverse`, no `String.strip`/`toUpperCase`/`toLowerCase` in Core (P2 classes D2, D5).

## 5. T2: size

| Bundle (TeaVM 0.16.0) | Raw | gzip 9 | **Brotli 11** |
|---|---|---|---|
| runtime-shaped entry (compile and evaluate expressions only), `FULL` | 1,906,045 | 456,027 | **327,691** |
| same, `SIMPLE` | 1,104,333 | 346,765 | 270,687 |
| same, `ADVANCED` | 1,001,173 | 306,993 | **246,775** |
| the whole P1 cut, `FULL` | 2,400,530 | 590,056 | 411,448 |
| the whole P1 cut, `ADVANCED` | 1,205,790 | 380,489 | **300,132** |

Weight of the runtime entry by package (unminified share): `rachana.el` 38.5% · `java.time.format` 11.3% ·
`java.util` 9.0% · SafeRegex + BusinessCalendar 6.2% · `java.util.regex` 6.2% · `java.time` 6.0% · TeaVM locale text
3.1% · `java.math` 2.9% · `java.time.temporal` 2.5% · `java.lang` 2.4% · TeaVM Unicode tables 1.7% · the rest under 1.6%
each. **Sized proposal:** A5's planned removals (`java.time.format`, `java.util.regex`, `java.text`, locale data, zone data:
about 22%) and the `ADVANCED` level bring the runtime to **about 190 KB**; replacing all of `java.time` by Core's civil-date
library (another 10%) to about 170 KB. Reaching 150 KB needs the function catalogue (`rachana.el` is 126 KB of it on its
own) to load in parts, which TeaVM cannot split: **decision P1-1**.

## 6. T3, T4, T5: speed

**T3** (ms; "first" = the first compile of the typical Sutra after module evaluation, then warm p50/p95 of 30 runs):

| Runtime | Module evaluation | First compile | Typical p50 / p95 | Large p50 / p95 |
|---|---|---|---|---|
| JVM (reference figures) | – | 75 to 120 (class loading) | 0.11 / 0.13 | 0.57 / 0.62 |
| Node 22 (`FULL`) | 108 to 204 | 54 to 77 | 2.6 to 3.3 / 4.0 | 11 to 16 / 17.7 to 18.5 |
| Node 22 (`ADVANCED`) | 89 | 81 | 3.1 / 4.1 | 15.4 / 19.5 |
| Chromium 153 | 179 | **56** | 2.3 / **3.8** | 11 / **13.3** |
| Firefox 155 | 357 | 58 | 4 / 8 | 26 / 37 |
| WebKit 26.6 | 208 | 75 | 5 / 7 | 23 / 30 |

Synthetic Sutras from `tools/proofs/runners/gen_bench.py`: typical 12 panels and 60 expressions, large 50 panels and 400.

**T4** (ns per evaluation over 10,000 rows, median of 10; Chromium and Node `FULL`; "interp." = the hand-written
tree-walking evaluator `runners/mini-el.mjs`, written for the proof only):

| # | Expression | JVM | Chromium | interp. | × | Node | interp. | × |
|---|---|---|---|---|---|---|---|---|
| 1 | `@.amount` | 25 | 160 | 30 | 5 | 208 | 32 | 7 |
| 2 | `@.amount * 2` | 42 | 250 | 30 | 8 | 644 | 50 | 13 |
| 3 | `@.price * @.qty - @.amount` | 317 | 1,060 | 90 | 12 | 1,663 | 104 | 16 |
| 4 | `@.amount > 1000000` | 24 | 160 | 30 | 5 | 624 | 48 | 13 |
| 5 | `@.ccy == 'USD'` | 78 | 250 | 30 | 8 | 836 | 52 | 16 |
| 6 | `@.ccy in [...]` | 76 | 1,390 | 120 | 12 | 2,528 | 133 | 19 |
| 7 | `&&` with `!=` | 53 | 730 | 90 | 8 | 1,153 | 128 | 9 |
| 8 | `?:` | 54 | 940 | 80 | 12 | 1,544 | 97 | 16 |
| 9 | `'Leg ' + #index` | 29 | 540 | 30 | 18 | 1,099 | 46 | 24 |
| 10 | template | 81 | 1,090 | 110 | 10 | 1,952 | 89 | 22 |
| 11 | `round(@.price, 2)` | 102 | 4,990 | 60 | 83 | 9,092 | 66 | 138 |
| 12 | `abs(@.amount) / 1e6` | 78 | 670 | 80 | 8 | 1,284 | 64 | 20 |
| 13 | `nonblank(...)` | 55 | 530 | 50 | 11 | 1,265 | 39 | 33 |
| 14 | `upper(left(...))` | 49 | 2,550 | 90 | 28 | 4,500 | 108 | 42 |
| 15 | date text `>=` | 95 | 870 | 80 | 11 | 1,225 | 60 | 20 |
| 16 | `days_between` | 181 | 3,620 | 170 | 21 | 5,786 | 171 | 34 |
| 17 | `reduce($.legs, 0, greatest(...))` ¹ | 854 | 29,430 | 1,360 | 22 | 49,850 | 648 | 77 |
| 18 | `size($.legs[?...])` | 306 | 7,840 | 1,430 | 5 | 3,879 | 550 | 7 |
| 19 | `map($.legs, ...)` | 338 | 4,170 | 680 | 6 | 4,640 | 646 | 7 |
| 20 | `matches(...)` | 142 | 2,600 | 120 | 22 | 2,541 | 108 | 23 |

¹ JS_PROOF's `sum($.legs, 'notional')` has no provider in the cut (aggregates are Drishti's pivot engine's until A6), and
`reduce` refuses addition by design; the stand-in walks the same ten legs. Firefox and WebKit (coarse timers) show the
same picture: median 22× (`bench-browsers.json`).

**Why.** A CPU profile of the Node run: TeaVM's `long` emulation (`Long_shru`, `Long_udiv`, `Long_create`, `Long_shl`,
`Long_shr`: **28%**), its `Double.toString` (`DoubleAnalyzer`: **7.5%**) reached through `Args.decimal` →
`BigDecimal.valueOf(double)`, path reads (`Expr$Field.eval` 10.6%), garbage collection 4.4%. `Values.normalise` turns every
whole result into a `Long` through `Math.rint` and a `long` cast, so arithmetic pays `BigInt` on every evaluation. The
JVM/JavaScript ratio is 6× to 50× (median about 15×; JS_PROOF B9 expected 2× to 5×). **P4 projection:** a typical Sutra
over 1,000 rows is about 60,000 evaluations: about 60 ms in Chromium against the 10 ms budget.

**T5** (100 changed rows on a 50,000-row table, the expression `@.price * @.qty - @.amount`; p50 ms):

| Runtime | Changed rows only | With today's `DataNode.Arr` copy | Heap over 100 changes (Node): rows only / with copy |
|---|---|---|---|
| JVM | 0.034 | 0.096 | – |
| Node | 0.26 | 0.75 | 5.2 MB / 18.0 MB (5.2 / 16.1 at 10,000 rows) |
| Chromium | 0.2 | 0.5 | – |

## 7. T6 to T9: same output, strict pages, errors, reproducibility

**T6** (P2 for the table): conformance cases differing from the reference (1,118-case suite at `ae27fc5`): Node 45,
Chromium 32, Firefox 43, WebKit 37, all P2 classes D1 (maths, 21 to 34) and D2 (11 engine `RangeError`s); on the 1,590-case
suite at `079d0c4` (the clean `proof.py all` run): Node 64, Chromium 50, Firefox 62, WebKit 55, the same classes plus one
D5 case (`reverse`). The four browsers also differ from each other
(engines' `Math`).

**T7** (`runners/run_browsers.py`, `runners/t7_pages.py`): `Content-Security-Policy: default-src 'self'; script-src
'self'; require-trusted-types-for 'script'` on every page. Module page, classic-script bundle (TeaVM `UMD`) from `file://`
with the policy in a `meta` tag, and the module bundle in a module Web Worker (created through a Trusted Types policy, as
any strict page must): **identical results, zero violations** in all three browsers.

**T8:** what escapes today is the engine's `RangeError` (D2) and, in embedded engines, `ReferenceError` (D6); no TeaVM
stack trace reached a results line (the harness reports a crash's message only). The depth (201) and length limits of
`el/limits` refuse with `RCH-2101` on every runtime before any engine stack limit.

**T9:** `mvn clean` builds from the same, byte-identical class files gave bundles `206ffc89…`, `0673b48c…`,
`cc85f9f3…` (same length ± 1 byte; class and member names assigned in a different order); all three produce the same
results file. Identity hash codes are the likely cause (unverified; `-XX:hashCode=2` breaks javac in the same JVM). Build
time: 15 s per bundle on this machine.

## 8. T10: the tool

TeaVM: Apache License 2.0 (`teavm-core` POM); the emitted class-library code is TeaVM's (parts derived from Apache Harmony,
Apache-2.0). Draft notice for `rachana.runtime.js` and `rachana.tools.js`: *"This bundle contains code generated by TeaVM
and parts of the TeaVM class library, Copyright Alexey Andreev and contributors, licensed under the Apache License, Version
2.0."* Cadence: 0.11.0 (2024-12-14), 0.12.0 to 0.12.3 (from 2025-05-04), 0.13.0, 0.13.1 (from 2025-11-03), 0.14.0,
0.14.1 (from 2026-05-02), 0.15.0 (2026-06-14), 0.16.0 (2026-10-02). Second tool: J2CL (Google, Apache-2.0); its JRE
emulation uses JavaScript `RegExp` and lacks `java.time` (documentation, unverified here), which section 2.5's allow-list
already avoids; it has no maintained Maven plugin (the Vertispan plugin's last release is 2023-11), so a J2CL build means a
Bazel or hand-written toolchain.

## 9. Fallbacks and decisions for the owner

| # | Trigger | Fallback (JS_PROOF 6) | Cost | Recommendation |
|---|---|---|---|---|
| P1-1 | T2 runtime 247 KB > 150 KB | none of the four fits: 2a is for tools only; the remainder after A5's removals is the function catalogue | A5 removals + `ADVANCED`: about 190 KB (no extra days: already in A5) | **owner: accept about 200 KB Brotli for the runtime** (records the measured figure in DECISIONS), or ask for a catalogue split design (est. 4 bd, two TeaVM builds sharing a hand-written seam: a second code path, not recommended) |
| P1-2 | T4 (11× to 22× median), T3 cold first compile | **2b intrinsics** for path read, comparison, arithmetic, the aggregate loop, `round`, text functions; plus A5's no-`long` number path | A5's number path removes about a third of the time (the profile); intrinsics for the hot nodes another 2× to 4× (estimate); **5 bd** beyond A15's 8 | take 2b in A15 and **re-measure T4 there**; if the median stays above 3× the interpreter, the owner chooses between **fallback 3** (a hand-written JavaScript evaluator for Core, D3: two evaluators, about 15 bd plus the differential gate) and **moving T4's threshold to the P4 budget** (10 ms for 60,000 evaluations: about 170 ns each, a 6× gain from today) |
| P1-3 | T9 bundle bytes differ | fallback 1 (J2CL) by the letter of the table | about 3 d to re-run T1, T2, T6 on J2CL, plus a non-Maven toolchain and a civil-date library | **do not switch**: the results are reproducible and checked by conformance on every build; record the bundle hash per release; spend 1 bd on TeaVM's ordering (report upstream with the reproduction) |
| P1-4 | T8 | none needed | inside A5 and A3 | – |

**Effect on the plan:** A5 grows from 3 to 5.5 bd (P2 section 4); A15 from 8 to 13 bd with 2b; one owner decision each for
P1-1 and (after A15's re-measure) P1-2. Nothing changes for Drishti's JVM path except P2-1's 15 last-place results.

## 10. Reproduce

```text
uv venv <venv> && VIRTUAL_ENV=<venv> uv pip install -r tools/proofs/requirements.txt
JAVA_HOME=<JDK 21> <venv>/bin/python tools/proofs/proof.py all --drishti <Drishti checkout> --commit c6bc1d1bc --python <venv>/bin/python
```

Steps and outputs are listed in `proof.py`'s help; everything is written under `tools/proofs/work/` and `tools/proofs/out/`
(git-ignored). The heavy steps run one at a time; the WebAssembly step needs about 11 GB of memory for one minute (see
WASM_RESULTS W3). Nothing is installed (`mvn install` is never run) and Drishti is only read (`git archive`, its jars).

## 11. Re-measured in A15 (2026-11-21, the real build)

The real build of Core (`java/rachana-js`, TeaVM 0.16.0, `ADVANCED`, minified) after A5, A9, A13 and A14; Node 22.22.1 on the
same machine (load 2 to 3, shared); Playwright 1.63.0 browsers. Reproduce: `python3 tools/js/build_js.py --bench`, then
`node js/packages/rachana/bench/bench-node.mjs java/rachana-js/target/js/rachana.bench.js <bench> <bench50k>` with
`tools/proofs/runners/gen_bench.py <dir> 10000` and `50000`.

| Step | P1 | A15 | Verdict |
|---|---|---|---|
| **T1** | 4 gaps | none left: the gaps were closed in Core (A3, A5); the build needed only the resource list (TeaVM's resource SPI) | **pass** |
| **T2** size, Brotli 11 | runtime-shaped entry 247 KB (expressions only), the P1 cut 300 KB | **runtime 361 KB** (with the Sutra parser and checker and the view builder), **tools 381 KB** (raw 1.39 MB and 1.48 MB) | runtime **miss** against 150 KB and against P1-1's accepted 200 KB; tools **miss** against 350 KB by 9%: DECISIONS D15-5 |
| **T3** start (Node) | module 108 to 204 ms; first compile 54 to 77 ms; typical p95 4.0 ms, large 18.5 ms | module 75 to 102 ms; first compile 50 to 69 ms; **typical p95 1.8 to 3.5 ms, large 8.8 to 15.6 ms** (two runs) | warm budgets **pass**; cold first compile still above 30 ms |
| **T4** (Node, median of the twenty) | about 19x the hand-written interpreter (7x to 138x) | **14x** (8x to 100x) after fallback 2b | **miss**: DECISIONS D15-6, the owner's choice by P1-2 |
| **T5** 100 changed rows of 50,000 | 0.26 ms (rows only) | **0.2 ms** (Core's lists are the host's own since A1: no copy exists) | **pass** |
| **T6** same output | 45 to 64 differences per engine | **0** on 2,123 cases in Node, Python on V8, Chromium, Firefox, WebKit; 0 on 5,600 random maths probes | **pass** |
| **T7** strict pages | pass | module page under `default-src 'self'` with Trusted Types: no violation in three browsers; no `eval`, no `Function(` in either bundle | **pass** |
| **T8** errors | 11 engine `RangeError`s, 12 `ReferenceError`s | **none** in any runtime's results | **pass** |
| **T9** reproducible | three builds, three SHA-256s | **two clean builds, the same SHA-256** (TeaVM run through its tool API in a fresh JVM); runtime `d0ced04f…`, tools `b012b411…` at `28d01bf` | **pass** (Windows not run) |
| **T10** the tool | pass | unchanged; one TeaVM file patched (D15-2) | **pass** |

**What the build fixed on the way** (each held by conformance and a unit test): TeaVM printed `a * (b / c)` as `a * b / c`
(one patched file, D15-2); TeaVM's `BigDecimal.doubleValue` and `BigDecimal.round` (Core's doors, D15-3); `exp`, `ln`,
`log10` and the trigonometric functions were still `java.lang.Math` method references (now `CoreMath`, six more fdlibm
functions ported); an edit refused by the check kept its edited text in JavaScript (`OpApplier`); a pattern error had no
words in TeaVM's library (`RegexText`).

**T4 per expression** (ns per evaluation over 10,000 rows, median of 10, Node; "interp." is P1's hand-written tree-walking
evaluator `tools/proofs/runners/mini-el.mjs`):

| # | Expression | A15 before 2b | A15 with 2b | interp. | x |
|---|---|---|---|---|---|
| 1 | `@.amount` | 94 | 200 | 21 | 9.7 |
| 2 | `@.amount * 2` | 394 | 404 | 33 | 12.3 |
| 3 | `@.price * @.qty - @.amount` | 1,254 | 1,112 | 126 | 8.8 |
| 4 | `@.amount > 1000000` | 282 | 296 | 32 | 9.4 |
| 5 | `@.ccy == 'USD'` | 335 | 315 | 34 | 9.4 |
| 6 | `@.ccy in ['USD', 'EUR', 'GBP']` | 1,935 | 1,879 | 89 | 21.2 |
| 7 | `@.amount > 0 && @.status != 'CANCELLED'` | 760 | 592 | 65 | 9.1 |
| 8 | `@.type == 'FX' ? @.amount : -@.amount` | 819 | 791 | 64 | 12.5 |
| 9 | `'Leg ' + #index` | 796 | 794 | 31 | 25.5 |
| 10 | `T:${@.book} / ${@.ccy}` | 1,123 | 1,184 | 54 | 22.1 |
| 11 | `round(@.price, 2)` | 4,505 | 4,281 | 43 | 100 |
| 12 | `abs(@.amount) / 1e6` | 648 | 733 | 51 | 14.4 |
| 13 | `nonblank(@.desk, 'n/a')` | 822 | 693 | 45 | 15.4 |
| 14 | `upper(left(@.book, 3))` | 1,873 | 1,843 | 74 | 24.9 |
| 15 | `@.trade_date >= '2026-01-01'` | 538 | 587 | 42 | 13.8 |
| 16 | `days_between(@.trade_date, '2026-11-17')` | 4,586 | 4,581 | 133 | 34.5 |
| 17 | `reduce($.legs, 0, greatest(@acc, @.notion...` | 27,986 | 26,813 | 580 | 46.2 |
| 18 | `size($.legs[?@.type == 'FLOAT'])` | 3,729 | 3,892 | 477 | 8.2 |
| 19 | `map($.legs, @.notional / 1e6)` | 4,378 | 4,264 | 517 | 8.3 |
| 20 | `matches(@.book, '^BOOK-[0-9]+$')` | 2,092 | 2,057 | 66 | 31.1 |

**Fallback 2b as taken:** the words of a double through a `DataView` instead of a `long` (`internal.Bits`), so Core's maths and
`rint` run without `BigInt`; the engine's own number text and number reading (`internal.NumberText`). The profile that remains
(Node, `--cpu-prof`): the expression tree's field reads and the map lookups under them (about 19%), boxing and type checks
(about 10%), `BigDecimal` in `round` (`round(@.price, 2)` is the 100x outlier), `java.time.format` in `days_between`'s date
reading. None is a hot spot an intrinsic behind a Java interface removes without moving the data model to JavaScript objects (B3's
`Node` over JavaScript objects) or a second evaluator (fallback 3).
