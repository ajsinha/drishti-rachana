<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary. All rights reserved. -->
# P2: determinism probes, results

**Status: run 2026-11-19 (i7b M0, lane Opus 1).** The input was [JS_PROOF](JS_PROOF.md) section 3; the probes are
[conformance/1/el/determinism.yaml](../../conformance/1/el/determinism.yaml) (255 cases, `expect` = the reference's actual
result, `verified: false`). Reproduce with `tools/proofs/proof.py` ([P1_RESULTS](P1_RESULTS.md) section 10). The JavaScript
build is P1's; the WebAssembly build is [WASM_RESULTS](WASM_RESULTS.md)'s.

## 1. The answer

Every byte difference found falls in **seven classes**, each with one Core rule and an owner step. No difference needs a
per-runtime code path. Nothing differed in date arithmetic, sorting, hash order, regular-expression semantics, Excel number codes,
named formats, booleans, limits or problem texts. **The list of what A5 must own is closed** (section 4), with one decision
for the owner (section 5: CoreMath moves 15 JVM results).

| | Runtimes compared with the reference (Drishti's jars, Java 21) | Cases | Differ |
|---|---|---|---|
| Conformance (all kinds a compiler runs: `expr`, `expr-error`, `check`, `grammar`, `format`, `problem`; 37 `sutra`/`patch`/`plan`/`edit`/`protocol` are out of P1's cut) | the cut copy on the JVM | 1,118 | **0** (faithful) |
| | JVM, Java 25 | 1,118 | 1 (`tanh`) |
| | JVM with `-Duser.language=de -Duser.timezone=Asia/Kolkata`; with `tr_TR` | 1,118 | 0; 0 |
| | Node 22.22.1 · Chromium 153 · Firefox 155 · WebKit 26.6 (TeaVM 0.16.0) | 1,118 | 45 · 32 · 43 · 37 |
| | WebAssembly GC under wasmtime 50.0.1 from Python 3.14 | 1,118 | 19 |
| | the JavaScript bundle in Python: V8 (mini-racer 0.14.1) · QuickJS 1.19.4 | 1,118 | 52 · 44 |
| Conformance again at `079d0c4` (1,590 cases: the suite grew while this ran; `proof.py all` from clean) | cut copy on the JVM · Java 25 · `de` · `tr` | 1,590 | 0 (faithful) · 1 · **1** · **1** (class D7) |
| | Node · Chromium · Firefox · WebKit | 1,590 | 64 · 50 · 62 · 55 |
| | WebAssembly · V8 in Python · QuickJS in Python | 1,590 | 35 · 76 · 67 |
| Probes (JS_PROOF section 3, 255 inputs) | Java 25 · `de` · `tr` | 255 | 0 · 0 · 0 |
| | Node · Chromium · Firefox · WebKit | 255 | 24 · 23 · 24 · 23 |
| | WebAssembly | 255 | 21 |

## 2. The difference table (by class)

The JVM column is the reference's text; "JS" is the TeaVM JavaScript build in every engine unless a cell names one.

| Class | Probe or case (examples) | JVM | Other runtime | Root cause | Core rule | Owner |
|---|---|---|---|---|---|---|
| **D1 transcendental maths** (J3) | 34 conformance cases in Node, 21 to 32 in browsers, 19 in WebAssembly: `norm_s_dist`, `f_dist`, `binom_dist`, `hypgeom_dist`, `npv`, `irr`, `mirr`, `pv`, `fv`, `pmt`, `nper`, `rate`, `cumprinc`, `effect`, `log`, `tanh`; probes D-M01 to D-M05 | `0.9087887256040952` | `…953` (Node, Firefox), `…952` (Chromium), and so on: **every engine differs from every other**, and Node 22's V8 differs from Chromium 153's V8 | TeaVM maps `Math.exp/log/pow/sin/atan2` to the engine's `Math`; the WebAssembly build imports them from its host (here CPython's libm); HotSpot uses its own intrinsics. **Java 21 and Java 25 differ too** (`tanh(0.5)` and `fisher_inv`: JDK 24 added a `tanh` intrinsic) | `CoreMath`: a port of C fdlibm for `exp`, `log`, `pow`, `log1p`, `expm1`, `log10`, `tanh`, `sin`, `atan2`, used on every runtime including the JVM | A5 |
| **D1b wrong maths in TeaVM** (new; J3) | D-M06: `pmt(1e-12, 360, 200000)`, `pv`, `fv`, `nper`, `effect(1e-10, 12)`, `nominal`; D-M01 `log10(1000)` | `-555.5555556558511`; `3` | `-555.5061709334833` (relative error 1e-4); `2.9999999999999996` | TeaVM's class library implements `log1p(x)` as `log(1 + x)`, `expm1(x)` as `exp(x) - 1`, `log10(x)` as `log(x) / 2.302585092994046` and `tanh` through `exp` (read from its bytecode): wrong results, not last-place noise | the same `CoreMath` | A5 |
| **D2 `Math.rint` and `(long)` of a non-finite double** (J4) | 11 conformance cases (`'CP-0042' * 1`, `'Infinity' * 1`, blank text times one …); D-R02c/d, D-R03a/b/d, D-R04d | `NaN`, `Infinity` | JS: the engine's `RangeError: The number NaN cannot be converted to a BigInt` escapes (wording differs per engine); WebAssembly: correct | TeaVM's `Math.rint(d)` is `(double) Math.round(d)` (half up, not half even) and its `double`→`long` is `BigInt(Math.floor(d))`, which throws on NaN and infinities and wraps instead of saturating. `Values.normalise` and `Values.text` call `Math.rint` on every number | `CoreMath.rint` (half even, NaN and infinities through) and `CoreMath.toLong` (Java's saturating cast) wherever Core rounds or casts; `Math.rint` and `(long) double` leave the JDK allow-list | A5 |
| **D3 number text** (J1) | D-N08a `5e-324`; D-M01b `exp(-745)` | `4.9E-324` | `4.940656458412465E-324` (JS and WebAssembly) | TeaVM's `Double.toString` is not the shortest round trip for subnormals (JDK 19+ is) | `NumberText.shortest` (Ryū class) on every runtime | A5 |
| **D4 number reading** (J2, Q2) | D-R04b `'2.2250738585072012e-308' * 1`; D-R02c `'0x1p3' * 1` | `2.2250738585072014E-308`; `8` | `2.225073858507201E-308` (not correctly rounded); NaN (JS crashes as D2) | TeaVM's `Double.parseDouble` misrounds near the smallest normal and does not read hexadecimal floats | `NumberText.parse` with the grammar of Q2 (decimal only; `0x1p3`, `1d`, `1f`, `Infinity`, `NaN` are not number-like text): the JVM changes for D-R02a to c and D-R03a, b (section 5) | A5, H6 |
| **D5 text by code point and case** (J15, J31, new) | D-U03d `reverse('a😀')`; D-U05a to e, D-U06c `upper('ß')`, `lower('İ')`, final sigma, `upper('𞤢')`; D-U08c `trim(' x ')` | `😀a`; `SS`, `i̇`, `aς`, Adlam upper; ` x` | JS and WebAssembly: `\ude00\ud83da` (pair broken); WebAssembly: `ß`, `i`, `aσ`, unchanged (no special casing, no astral case tables); JS: `x` (no-break space trimmed) | TeaVM's `StringBuilder.reverse` reverses UTF-16 units; its WebAssembly class library has simple BMP case tables only, its JavaScript one delegates to the engine (same as Java here); `String.strip` in the JavaScript library uses the engine's white-space set | `reverse` by code point; case mapping from Core tables generated from one pinned Unicode version (with Java's special casing); `trim` and `clean` by a written character set | A5, H6 |
| **D7 the JVM's default locale** (J10) | `format/formats#dmy-2026-10-02` (the `dmy` format, pattern `dd MMM yyyy`) | `02 Oct 2026` | the same JVM started with `de_DE`: `02 Okt. 2026`; with `tr_TR`: `02 Eki 2026` | `FormatSpec` calls `DateTimeFormatter.ofPattern` without a locale: **a JVM-against-JVM defect today** | Core's date-pattern formatter with its English table | A5 |
| **D6 the regular-expression deadline** (J20) | 12 conformance cases (`matches`, `regex_extract`, `regex_replace`) in V8 and QuickJS embedded in Python | results | `ReferenceError: performance is not defined` | `SafeRegex` reads `System.nanoTime` for its time limit; TeaVM maps it to `performance.now()`, which an embedded engine need not have | the step budget of J20 (count of NFA steps), no clock in Core | A3 |

**Not differing anywhere** (all 255 probes and 1,081 runnable cases): `0.1 + 0.2`, `1e21`, `-0` (prints `0`), the `E`
cut-overs at 10⁻³ and 10⁷, `round` half away from zero in decimal, `mod`, `%`, `BigDecimal` results and `toPlainString`,
`fmt` named formats (including U+2212), Excel number codes (`text(…, '#,##0.00')`), ISO weeks, `edate` month ends, Excel
date carry-over, `days360`, `datedif`, the Gregorian range ends, every booleans-and-operators probe, the order of
`sort`/`unique`/`group` (including 600 keys and the 500-group cut), the "known: …" list of an unknown function, depth and
length limits (nesting 201 is refused before any stack overflow on every runtime), regular-expression match choice,
refusals and line ends, UTF-16 code-unit ordering (D-U01, D-U02), `len`/`left`/`mid` in code units.

## 3. Probes the P1 cut could not run

| Probe | Why | Where it runs |
|---|---|---|
| D-T12 a JVM started with `de` | run: the probe's `date` format has no month name, so it agrees; the conformance case `format/formats#dmy-2026-10-02` (added by H2 while this ran) shows the defect: class D7 | A5 |
| D-T13 form-field zones | a `Form` model field, not an expression | A13 (binder) |
| D-O01 event cycles, D-O04 mixed problem order | need `SutraWiring` over a bound Sutra and A2's full problem list | A2, A14 |
| D-L1 YAML nested 1,000 deep | the YAML reader is A8's; the cut reads the reference's tree | A8 |

## 4. What A5 must own (closed list, sized)

| # | Core rule | Replaces | Weight in the runtime bundle (TeaVM, unminified share) | Size (bd) |
|---|---|---|---|---|
| 1 | `CoreMath`: fdlibm port of 9 functions, the JVM included | `java.lang.Math` transcendental, `StrictMath` | adds ~1% | 1.5 |
| 2 | `CoreMath.rint`, `CoreMath.toLong`; no `Math.rint`/`(long) double` in Core (8 + 21 sites) | TeaVM's `rint` and `d2l` | none | 0.5 |
| 3 | `NumberText.shortest` and `NumberText.parse` (grammar of Q2), no `long` on the way | `Double.toString`, `parseDouble`, `BigDecimal.valueOf(double)` | removes TeaVM's `DoubleAnalyzer` (7.5% of T4's profile) and most `Long_*` (28%) | 1.5 |
| 4 | text by code point (`reverse`), case tables from one Unicode version, white-space set | `StringBuilder.reverse`, `toUpperCase/LowerCase`, `strip` | case tables add ~20 to 40 KB raw | 1 |
| 5 | the regex step budget (with A3's move of `SafeRegex`) | `System.nanoTime` in `SafeRegex` | none | 0.25 |
| 5b | date patterns with Core's English month and day names | `DateTimeFormatter.ofPattern` without a locale (D7) | removes `java.time.format` (11% of the runtime bundle) | 0.5 |
| 6 | the four T1 gaps (`BigDecimal.sqrt`, `unmodifiableSortedMap`, `Matcher.append*`) as proven in the cut | JDK members TeaVM lacks | none | 0.25 |
| | **A5 total** | | | **5.5** (plan: 3) |

Done in A5 (2026-11-21), every row: `CoreMath`, `NumberText`, `CoreText`, `DateText`, `CoreDecimal`, the step budget, Core's
number-code printer, the T1 gaps; decisions in [DECISIONS](../design/DECISIONS.md) D5-1 to D5-7 (row 3: Java's grammar stays at level 1.0).

`java.time` can stay as far as determinism goes (every D-T probe agreed); T2's weight argues for replacing
`java.time.format` regardless ([P1_RESULTS](P1_RESULTS.md) T2).

## 5. For the owner

| # | Point | Measured | Recommendation |
|---|---|---|---|
| P2-1 | `CoreMath` makes every runtime agree, **including Java 21 with Java 25**, but it moves the reference's own results | the cut copy with fdlibm (`StrictMath`) on the JVM changes **13 of 1,118 conformance cases and 2 of 255 probes** in the last place (`norm_s_dist`, `f_dist`, `binom_dist`, `hypgeom_dist`, `gamma_dist`, `npv`, `irr`, `mirr`, `rate`, `log`, `fisher`); Java 21 and 25 then agree | accept: A5's gate "goldens unchanged" gets one written exception (these 15 last-place changes, listed); the alternative (tolerances in cases, Q1) leaves Java 21 and 25 disagreeing already |
| P2-2 | Q2 number grammar changes the JVM | `'0x1p3' * 1` is `8`, `'1d' * 1` is `1`, `'Infinity' * 1` is infinity on the JVM today | as recommended in JS_PROOF Q2: decimal only; five probes change, listed in `determinism.yaml` |
| P2-3 | D1b is a correctness defect of TeaVM, not noise | `pmt(1e-12, 360, 200000)` wrong in the fourth digit in every JavaScript engine | report upstream; Core does not depend on the fix (CoreMath) |
