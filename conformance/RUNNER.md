<!-- Copyright (c) 2026 Ashutosh Sinha. Specification licence: ../LICENSE-SPEC.md (draft, not yet in force). -->
# The runner contract

**Status: draft 1 (2026-11-17), front-load item F1.** Frozen with [FORMAT.md](FORMAT.md). Every runtime's runner
implements this contract: the JVM reference, Node, the browsers, Python over WebAssembly, and any third-party port.
What a case *is* and how it is compared is FORMAT.md; this is how a runner is called and what it writes.

## 1. Runners

| Runtime name | Runner | Reads cases | Notes |
|---|---|---|---|
| `jvm` | `rachana-conformance` (JUnit dynamic tests; one test per case) | from the folder, or the pinned cases jar inside Drishti (plan H1, N4) | **the reference**; the only one with record mode (section 6) |
| `node` | `js/packages/rachana/test/conformance.mjs` | from the folder | |
| `chromium`, `firefox`, `webkit` | the same module under Playwright | handed in by the Node side | one runtime name per browser |
| `python-wasm` | `python/tests/test_conformance.py` under `wasmtime` | from the folder | from R2 |
| any other name | a third party's program, driven by `rachana conformance run --cmd` (section 5) | on stdin | the driver writes the results file |

A runtime name is `[a-z][a-z0-9-]{0,31}`. A runner that runs inside a test framework still writes the results file of
section 4; the framework's own report is extra.

## 2. Inputs

Every runner accepts these options (the names are those of `rachana conformance run`; a test-framework runner takes
the same values as system properties or environment variables `RACHANA_CONFORMANCE_<NAME>`, upper case, `-` as `_`).

| Option | Default | Meaning |
|---|---|---|
| `--cases <dir>` | `conformance/1` | the suite folder; every `*.yaml` below it is a case file |
| `--runtime <name>` | the runner's own | written into the summary |
| `--out <file>` | `results/<runtime>.jsonl` | the results file (section 4); its folder is created |
| `--summary <file>` | `results/<runtime>.summary.json` | the summary (section 4.3) |
| `--profiles <a,b>` | `core` | the profiles loaded; `core` is always loaded. Core cases run in every configuration |
| `--language <major.minor>` | the runtime's own | cases with a later `since` are `unsupported` |
| `--only <glob,…>` | all | case ids to run (`el/**`, `el/examples/arithmetic#*`); the others are not written |
| `--kinds <kind,…>` | all the runtime implements | kinds to run |
| `--timeout <ms>` | `10000` | per case; a case that takes longer is `error` |
| `--record` | off | JVM reference only (section 6) |

Before the first case a runner **validates every case file** against
[schema/case.schema.json](schema/case.schema.json), checks that `suite` equals the file's suite name and that case
ids are unique in the file. A file that fails is a usage failure (exit 2): nothing is run. Then it merges file-level
`since`, `profile`, `verified` and `given` into each case (FORMAT 2, 3).

**Order.** Case files are taken in the byte order of their UTF-8 suite names; cases in file order. A runner may run
cases concurrently but writes them in this order.

**Isolation.** Each case gets a fresh compile and a fresh view; nothing one case does is visible to another. A runner
never reads the clock, locale, time zone or environment on a case's behalf: everything comes from `given`
(`today` is required wherever it matters). The process's own locale and zone are irrelevant to results; a runner's CI
runs it once under a non-English locale and a non-UTC zone to prove that.

## 3. Statuses

| Status | When |
|---|---|
| `pass` | every field `expect` names matches (FORMAT 7) |
| `fail` | a field does not match |
| `error` | the runtime threw, crashed or timed out on the case (never a language problem: those are actual results) |
| `unsupported` | the kind is not implemented by this runtime, a profile the case needs is not loaded, or `since` is later than `--language` |

A runner never skips a case silently: a case it does not run is `unsupported` and still has its line. There is no
per-runtime list of known failures; a case that fails on one runtime is a defect to fix.

## 4. Outputs

### 4.1 The results file: `results/<runtime>.jsonl`

One line per case, in the order of section 2, each the canonical JSON (FORMAT 8) of:

```json
{"case":"el/examples/arithmetic#add-two-numbers","kind":"expr","status":"pass","actual":{"value":3,"type":"number","text":"3","reads":[]}}
```

| Field | Value |
|---|---|
| `case` | the case id, `<suite>#<id>` |
| `kind` | the case's kind |
| `status` | section 3 |
| `actual` | the actual document of the kind, **every field the kind defines** (FORMAT 5), whatever `expect` names; `null` for `error` and `unsupported` |

Nothing else: no times, no runtime name, no message, no path on disk. That is what makes two runtimes' files
comparable byte for byte. The file ends with an LF after the last line; an empty selection writes an empty file.

### 4.2 Diagnostics

Failures are explained on standard error and in the summary, never in the results file: for `fail`, the case id, the
first field that differs, a short expected/actual excerpt; for `error`, the exception's message (the runtime's own
words; not compared).

### 4.3 The summary: `results/<runtime>.summary.json`

```json
{"runtime":"node","runtimeVersion":"0.9.0","language":"1.0","profiles":["core"],
 "cases":1834,"pass":1830,"fail":1,"error":0,"unsupported":3,"unverifiedFailing":1,
 "failures":[{"case":"el/dates#eomonth-leap","status":"fail","verified":false,"detail":"value: expected \"2028-02-29\", actual \"2028-02-28\""}],
 "millis":4210}
```

`unverifiedFailing` counts `fail` and `error` cases with `verified: false`. The summary is for people and CI logs; it
is never compared between runtimes.

### 4.4 Exit codes

| Code | Meaning |
|---|---|
| `0` | every case run is `pass` or `unsupported` |
| `1` | at least one case with `verified: true` is `fail` or `error` |
| `2` | usage failure: bad option, a case file that does not parse or validate, a duplicate case id, the results file cannot be written. No results file is left behind |
| `3` | only cases with `verified: false` are `fail` or `error` |

CI treats `3` as green while cases are being harvested (R0) and as red once the suite is frozen (the `v0.9.0` tag, plan
N9); from then on every case is `verified: true` and code `3` cannot occur.

## 5. Third-party runtimes: cases on stdin

`rachana conformance run --cmd "<program and arguments>"` (any launcher: npm, PyPI, jar) runs a program that is not
written against this repository's code. The **driver** reads and validates the case files, starts the program once,
and talks JSON lines over its standard input and output; the program never reads case files and never compares.

1. Driver → program, first line: `{"rachana-conformance":1,"language":"1.0","profiles":["core"]}`.
2. Program → driver, first line: `{"runtime":"<name>","runtimeVersion":"<text>","language":"1.x","profiles":[…],"kinds":[…]}`:
   what it implements. Kinds and profiles it does not list are `unsupported` without being sent.
3. Driver → program, one line per case: the case with file-level fields merged in and **without `expect`**, plus
   `"case":"<suite>#<id>"`: `{"case":"el/examples/arithmetic#add-two-numbers","kind":"expr","since":"1.0","profile":"core","expr":"1 + 2","given":{"today":"2026-01-15"}}`.
4. Program → driver, one line per case, in the same order: `{"case":"…","actual":{…}}`, or
   `{"case":"…","error":"<words>"}` when it failed on the case. Numbers in `actual` are written by FORMAT 9; the
   driver keeps their text and does not reparse them.
5. The driver closes the program's standard input after the last case; the program exits `0`.

The driver compares, writes `results/<runtime>.jsonl` and the summary exactly as section 4, and exits by 4.4. A line
that is not JSON, a case id out of order, a missing answer within `--timeout`, or the program exiting early makes
that case (and every unanswered case) `error`. The program's standard error is copied to the driver's.

## 6. Record mode (the JVM reference only)

`--record` makes the reference runner help turn hand-written cases into verified ones (plan H1):

1. Cases with `verified: true` are run and compared as always; record mode never changes them. A failing verified
   case is a failure (exit 1), not something to record.
2. For each case with `verified: false`, the runner writes the actual result into the case's `expect`, **keeping the
   fields the case named and only those**, except that a case whose `expect` is empty gets every field of its kind. Each
   case file is rewritten with the reference's comment-keeping YAML writer: comments, order and layout kept; only
   `expect` values change.
3. `verified` stays `false`. A person reviews the diff (`git diff conformance/`) against SPEC and the source test, and
   flips `verified: true` in the same commit. A change of meaning found on review is a defect in the case or in the
   reference, settled as plan N5 says, never by recording over it.

## 7. The gate

A change to this repository, or to a runtime, is green only when:

1. the `jvm` runner exits `0` (or `3` before the freeze);
2. every other runtime's results file is **byte-identical** to `jvm`'s for the cases both ran, after removing lines
   with status `unsupported` from either side (a runtime may lack a kind; it may not differ on one);
3. the coverage rule of COMPILER_AND_RUNTIMES 6.2 holds: every key, option, panel kind, function, format, tone and
   problem code of `spec/1/rachana.lang.json` appears in at least one case (once that file exists, plan H5).

`tolerance` (FORMAT 5.2) loosens only the comparison with `expect`, for ports; this project's runtimes are built from one
source and still pass rule 2 byte for byte. "Is Rachana" (SPEC 8) means: exit `0` on `conformance/<major>/` for the
profile and minor claimed, with no case `unsupported` other than `protocol` (a compiler is not a host) and kinds the
claimed scope excludes (a linter need not bind: it claims `expr-error`, `check`, `grammar`, `problem`).

## 8. Protocol runs

`rachana conformance protocol --base <url>` runs the `protocol` cases against a Data Protocol host instead of a
compiler. Before each case the host loads the case's `fixture` through its test adapter (a host-specific hook; the
standalone server, the Python and Java SDK adapters each have one). Results go to `results/protocol-<host>.jsonl` with
the same line shape; `actual` is `{"steps":[{"status":…,"body":…}…]}` with bodies as received, canonicalised.
