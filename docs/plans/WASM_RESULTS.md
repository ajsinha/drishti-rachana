<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary. All rights reserved. -->
# The WebAssembly proof (W1 to W6), results

**Status: run 2026-11-19 (i7b M0, lane Opus 1)** against [COMPILER_AND_RUNTIMES](../design/COMPILER_AND_RUNTIMES.md)
section 5.3 (option A: TeaVM's WebAssembly GC backend, run by `wasmtime` from Python, no JVM). Same copy of the compiler,
same harness and cases as [P1_RESULTS](P1_RESULTS.md); determinism classes are [P2_RESULTS](P2_RESULTS.md)'s.
Reproduce: `tools/proofs/proof.py wasm` (and `pyjs` for fallback C).

## 1. The answer

| # | Threshold | Measured | Verdict |
|---|---|---|---|
| **W1** compiles (and runs) | TeaVM builds it; `wasmtime` runs it from Python | TeaVM 0.16.0 builds the WebAssembly GC module without error and `wasmtime` 50.0.1 validates and runs it, **but only after two changes**: (a) TeaVM's `Throwable.fillInStackTrace` hands every new exception to a host import typed with a GC reference (`teavm.decorateException((ref null $Object))`), which `wasmtime-py` cannot declare: removed by a patched class-library class; (b) four string imports return `(ref extern)`, which `wasmtime-py` cannot declare: their types are rewritten to `(ref null extern)` in the binary. The module imports 41 functions of TeaVM's JavaScript runtime; the JavaScript-free path needs the maths, the string functions and inert stand-ins for TeaVM's start-up (it builds a JavaScript wrapper for the entry class even when nothing uses it), about 120 lines of Python; the rest are defined as traps and never called | **pass with two workarounds**; **fail as TeaVM ships it** |
| **W2** size | `.wasm` ≤ 3 MB | **2.66 MB** raw (Brotli 507 KB) | **pass** (but see W3: the compiled form is 106 MB) |
| **W3** start and lint | instantiate ≤ 200 ms; lint of a 50-panel Sutra ≤ 20 ms warm | first load compiles the module: **28 to 60 s and 7.5 to 11 GB of memory** (106 to 136 MB of machine code); from that compiled artifact (cached on disk) instantiate **45 to 81 ms** ✓, first lint 66 to 138 ms; warm lint of the large Sutra **p50 238 ms** (12×), typical 42 to 57 ms | **fail** |
| **W4** same output | conformance byte-identical under `wasmtime` on Linux, macOS, Windows | Linux: 1,118 cases, **0 traps**, 19 differ from the reference, all P2 class D1 (the maths imports are CPython's libm); probes: 21 differ (D1, D1b, D3, D4, D5). The NaN and infinity cases that crash JavaScript are **right** here (native conversions). macOS, Windows: **not answered** (no machine in this lane) | **conditional pass on Linux** (A5 closes every class); two platforms open |
| **W5** no native code beyond the wheel | only `wasmtime` | true: the host is Python; the cached artifact is code `wasmtime` generates locally | **pass** |
| **W6** memory ≤ 64 MB for the suite | | **136 MB** resident for the 1,118 cases (Python + `wasmtime` + the 106 MB mapped artifact); 541 MB with 10,000 bench rows loaded; 11 GB peak during the first compile | **fail** |

Evaluation speed, for scale (ns per evaluation, 10,000 rows): `@.amount` **11,400**, `@.price * @.qty - @.amount` 30,700,
`round(@.price, 2)` 53,700, `matches(...)` 74,700: **about 450× the JVM and 50× the JavaScript build**. The likely cause is
`wasmtime`'s GC barriers on TeaVM's object-heavy code (unverified; the 106 MB of machine code for 2.1 MB of WebAssembly
code points the same way).

**Option A is not viable today.** W3 and W6 miss by one to three orders of magnitude, and W1 needs a patched TeaVM class
library plus a binary rewrite to cross the boundary at all. The named fallbacks are measured in section 3; the owner
decides (section 4).

## 2. What was done

| Piece | File |
|---|---|
| The JavaScript-free entry: input as UTF-8 at an address from the module's `teavm.malloc`, one `call(op, ptr, len)` export, results copied back through `copyResult(ptr)`; no JavaScript interop | `tools/proofs/java/entry/wasm/com/ash/proof/WasmMain.java` |
| The class-library change: TeaVM's `TThrowable.fillInStackTrace` without its `decorateException(this)` call, compiled into a copy of `teavm-classlib-0.16.0.jar` (the original in the Maven repository is untouched) | `proof.py` step `wasm` |
| The boundary report (the module's imports and exports with types, read by hand because `wasmtime-py` aborts on GC types) | `runners/wasm_boundary.py`, output `out/wasm/boundary.txt` |
| The boundary patch: `(ref extern)` → `(ref null extern)` in the four imported functions' result types, two bytes each | `runners/wasm_patch.py` |
| The Python host: memory and globals the module expects, `teavmMath` from Python's `math` (JavaScript's `NaN`/`Infinity` rules restored), JavaScript strings as Python strings of UTF-16 units, inert stand-ins for TeaVM's start-up wrapper objects, unknown imports as traps, one call per case (a trap loses one case), the largest stack `wasmtime` allows (2,000 KiB), the compiled module cached with `Module.serialize` | `runners/run_wasm.py` |

The boundary as TeaVM emits it (from `boundary.txt`): 44 imports (`env.memory`, two globals, 41 functions: `teavmMath` 13,
`teavmJso` 17, `wasm:js-string` 7, `teavm` 2, `teavmDate` 1, `teavmMemory` 1), 9 of
them with GC reference types `wasmtime-py` cannot express; 17 exports, the entry points as JavaScript wrapper globals.
TeaVM 0.16.0 has no option for a host without JavaScript (its runtime is `wasm-gc-runtime.js`).

## 3. Fallbacks, measured

COMPILER_AND_RUNTIMES 5.2 names B (GraalVM native-image library per platform), C (the JavaScript bundle on an engine
embedded in Python) and D (a JVM side-car, excluded by the owner's rule that Python users need no JVM). C was run with the
same bundle as P1 (TeaVM classic-script build) and the same cases:

| Host for Python | Wheel | Load | 1,118 cases | Lint typical p50 / p95 | Lint large p50 / p95 | Resident | Differ from the reference |
|---|---|---|---|---|---|---|---|
| A: WebAssembly GC, `wasmtime` 50.0.1 | native (`wasmtime`) | 28 to 60 s first, then 0.1 s | 2.8 to 3.4 s | 42 to 57 / 50 to 74 ms | 238 / 261 to 303 ms | 136 MB | 19 (D1) |
| **C: V8, `mini-racer` 0.14.1** | native (V8) | **0.22 s** | **0.57 s** | **2.6 / 8.2 ms** | **13.7 / 22.2 ms** | 209 MB | 52 (D1, D2, and 12 D6: no `performance` object) |
| C: QuickJS 1.19.4 | native (QuickJS) | 1.2 s | 3.2 s | 41 / 50 ms | 251 / 272 ms | **59 MB** | 44 (D1, D2, D6) |
| B: GraalVM native-image | native, built per platform | not run (needs GraalVM on each build platform; JDK 21 only here) | | | | | |

V8 inside Python is within reach of W3's lint budget (large p95 22 ms against 20) and 100× faster than option A at
evaluation, with one bundle for browsers, Node and Python. Its differences are the classes A5 already closes (D1, D2) plus
D6, which A3 closes. Its cost: a native wheel (as `wasmtime` is), about 30 MB per platform, and V8's memory (209 MB
resident, W6 would need restating for it).

## 4. For the owner (the last fallback is his)

| # | Decision | Options and cost | Recommendation |
|---|---|---|---|
| W-1 | the Python path for compile, lint and bind (B8 in the i7b plan, 12 bd) | **C with V8** (`mini-racer`): no second build target, the JavaScript bundle and facade reused; about 6 bd for the package, the runner on three systems and a `performance` shim until A3 (below B8's 12); W6 restated to the measured order (about 200 MB) · **A** re-tried when `wasmtime-py` supports GC types and TeaVM a host without JavaScript (no date from either; the 50× evaluation gap would remain unexplained) · **B** native-image per platform (about 10 bd, a native library per platform to build and sign, reflection configuration) | **C with V8 now; re-run this proof on each TeaVM and `wasmtime` release** (the script is ready: about 10 minutes of machine time) |
| W-2 | macOS and Windows for W4 | needs the QA machines; the Windows machine forbids `.exe`, which a `wasmtime` or V8 wheel's native library is not, but its site policy should be asked | run `proof.py pyjs` there at M2 (G-C requires six runtimes) |

**Effect on the plan:** B8 (12 bd) becomes about 6 bd on fallback C, if the owner takes W-1; the WebAssembly build
(`java/rachana-wasm`, COMPILER_AND_RUNTIMES 2) is not created in M2. P5 and P9 of COMPILER_AND_RUNTIMES 7 for Python are
measured against V8, not WebAssembly.
