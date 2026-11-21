<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="brand/drishti_rachana-logo.svg">
    <img src="brand/drishti_rachana-logo-light.svg" alt="Drishti Rachana" width="300">
  </picture>
</p>

# Drishti Rachana

**Describe a screen. Supply the data. Get a fast, professional UI.**

**Screens become assembly, not writing.**

Drishti Rachana is a declarative UI platform. You write a short **Sutra** (a screen or dashboard described in the
**Rachana** grammar), point it at your data in the agreed **Rachana Data Protocol**, and get a fast, dense,
professional interface drawn with **Drishti Elements**: grids, charts, maps, pivots, forms and live updates.

Status: **the compiler is here** (`v0.9.0`). The Java compiler (Sutra parser, Rachana-EL, formats, checker, editing
operations) moved from the Drishti product into [`java/rachana-core`](java/README.md) with its git history, and is being
untangled from its first host step by step ([SPLIT_PLAN](docs/design/SPLIT_PLAN.md) section 0). The binder, Views, the Data
Protocol, the SDKs and the tooling are still planned. Drishti is the first customer: it will consume pinned releases of Rachana,
exactly as it consumes Drishti Elements today.

Author: Ashutosh Sinha. Proprietary. All rights reserved (see [LICENSE](LICENSE)). The specification and conformance suite (`spec/`, `conformance/`) are intended to be under the permissive [LICENSE-SPEC.md](LICENSE-SPEC.md) (draft).

## What it is made of

| Part | What it does |
|---|---|
| **Sutra spec** | The language: a screen as a short YAML document (`rachana: 1`). Structure defined by a published JSON Schema; the expression language **Rachana-EL** defined by an EBNF grammar. |
| **Conformance suite** | Shared test cases (a Sutra plus data, and the exact expected result). Any implementation, in any language, is Rachana if it passes them. |
| **Compiler** | Turns a Sutra into a ViewModel. One reference implementation (Java), built for the JVM, for JavaScript (browsers, Node); Python runs the JavaScript bundle on V8 (decision W-1). |
| **Views** | `<rachana-view>`: compiles and binds a Sutra, then paints the ViewModel with Drishti Elements' painter (`<elements-view>`). |
| **Rachana Data Protocol** | Tables as JSON rows or Apache Arrow; live changes as keyed patches over SSE or WebSocket. |
| **SDKs** | Java, JavaScript and **Python** (Flask and FastAPI adapters: serve Sutras and data endpoints, stream live changes, lint Sutras in CI). |
| **Sutra libraries** (planning) | Prebuilt fragments to reference, parametrise and use in a Sutra: `core`, `finance`, `genomics`, `economy`, `geo`, `ops`, `ai`. Typed parameters, table contracts, slots, events; versioned, pinned, signed. |
| **Tooling** | `rachana new / dev / build / lint / preview`, editor support from the compiler, a playground and gallery built with Rachana itself. |

## What this repository holds today

| Folder | What | State |
|---|---|---|
| `spec/1/` | the specification: SPEC, EBNF, functions, formats, the generated language definition and schemas | written (R0) |
| `conformance/1/` | 2,112 cases; [driver](tools/conformance/README.md) in Python | harvested (R0) |
| `java/` | the Maven reactor ([java/README](java/README.md)): `rachana-core` is the compiler, moved with history; the JVM conformance runtime; the guards; the compatibility regime | **built**: `./mvnw clean verify` |
| `java/rachana-js`, `js/packages/rachana` | the JavaScript build (TeaVM): `rachana.runtime.js` and `rachana.tools.js`, the Node and browser runners | **built** (A15): every conformance case byte-identical to the JVM in Node, Chromium, Firefox, WebKit |
| `python/` | `drishti_rachana.engine` (the JavaScript bundle on V8), the protocol types, the conformance runner; the web adapters are stubs | engine **built** (A15); the rest skeleton |
| `js/vendor/` | the two Drishti Elements data files Core bundles | vendored |
| `tools/` | conformance driver, reference bridge (until the migration), split tools, regime and release tools | built |

Build and prove: `cd java && ./mvnw clean verify`, then `python3 tools/conformance/rachana_conformance.py --jvm` (every
verified case passes; see [java/README](java/README.md)).

## Use from JavaScript or Python

```bash
python3 tools/js/build_js.py            # TeaVM build (JDK 21): js/packages/rachana/dist/ and the Python package's bundle folder
```

```js
import * as rachana from "./js/packages/rachana/dist/rachana.runtime.js";   // an ES module; works under a strict CSP
rachana.init("core", "[]");
JSON.parse(rachana.evaluate(JSON.stringify({ expr: "$.a * 2", given: { record: { a: 21 } } })));   // {value: 42, text: "42", ...}
JSON.parse(rachana.check(JSON.stringify({ sutra: yamlText })));                                     // {ok, problems}: compile and lint
```

```python
from drishti_rachana.engine import Rachana    # pip install -e 'python[v8]': the same bundle on V8, no JVM
r = Rachana()
r.check(open("trade.sutra.yaml").read())       # {'ok': True, 'problems': []}
r.evaluate("$.a * 2", record={"a": 21})        # {'value': 42, 'type': 'number', 'text': '42', 'reads': ['$.a']}
```

Every answer is the document the conformance suite records (FORMAT section 5), byte-identical to the JVM's
([COMPILER_AND_RUNTIMES](docs/design/COMPILER_AND_RUNTIMES.md) sections 4.1a and 5.3a). The facade is minimal; `<rachana-view>`
and the data hand-over come with B3.

## Ways to use it

| Mode | For | How |
|---|---|---|
| Static | analysts | Sutras plus CSV, JSON or Arrow files, `rachana build`: a dashboard site with no backend |
| Embedded | app developers | `<rachana-view>` plus a Sutra inside any web app, on your own data API |
| Python app | Flask / FastAPI teams | `pip install drishti-rachana`; mount Sutras, expose tables, stream changes |
| Small server | teams | a standalone server: Sutras plus REST or database sources, live updates, sign-in |
| Drishti | enterprises | the full Drishti product, built on Rachana |
| Desktop | desktop application teams | the same screens in an embedded web view (Electron, Java Swing through JCEF, .NET through WebView2), fed through an in-process bridge: no HTTP server |

## Documentation

| Document | Contents |
|---|---|
| [Overview](docs/OVERVIEW.md) | What it is, who it is for, the parts, a worked example |
| [Architecture](docs/ARCHITECTURE.md) | Components, compile-to-paint pipeline, data protocol, languages, repository boundaries |
| [Modes](docs/MODES.md) | The six ways to use it, with planned-API snippets |
| [AI](docs/AI.md) | Safe generative UI: chatbot flow and the Rachana MCP server |
| [Roadmap](docs/ROADMAP.md) | Phases R0 to R3 and the rules |

### Design

| Document | Contents |
|---|---|
| [Specification](docs/design/SPEC.md) | The normative language: document structure and JSON Schema, Rachana-EL grammar (EBNF), types, functions, versioning, the error model |
| [Core and profiles](docs/design/CORE_AND_PROFILES.md) | What is Sutra Core and what is a host's profile, file by file over the existing code; the profile mechanism; tables as data sources |
| [Data Protocol](docs/design/DATA_PROTOCOL.md) | Rachana Data Protocol v1: schema and types, JSON and Arrow, paging and push-down, live keyed patches, errors, limits, the in-process bridge |
| [Compiler and runtimes](docs/design/COMPILER_AND_RUNTIMES.md) | Repository layout, the Java reference, the JavaScript build and the Python route on V8, the conformance suite, performance budgets, the CLI, desktop hosts, the standalone server |
| [Split plan](docs/design/SPLIT_PLAN.md) | The step-by-step separation from Drishti, guards, tests that move, risks |
| [Sutra libraries](docs/design/LIBRARIES.md) | **Planning.** The fragment-library framework: one mechanism (fragments, typed), `use: library/fragment@major`, table contracts, events, versions and the lock file, resolution, the `.rlib` archive, signing, licensing, generated documents and gallery, tests and gates, the AI kit, the Studio palette, a bank's private library, Drishti's migration with no screen change |
| [Library catalogue](docs/design/LIBRARY_CATALOGUE.md) | **Planning.** Seven libraries and 209 fragments, each with its purpose, parameters, data, slots, events, what it needs and an example |
| [Generative UI](docs/design/AI_GENUI.md) | Prompt kit, validate-and-fix loop, MCP tool schemas, safety, evaluation |
| [Decisions](docs/design/DECISIONS.md) | Numbered questions for the owner, each with a recommendation |

Everything described in these documents is planned unless stated otherwise. What is built is listed above and in the
[changelog](CHANGELOG.md).

## Compatibility regime (M1 progress)

The promise that a host changes no byte by moving from one Rachana release to a later one of the same major
([SUTRA_EXPANSION 6](docs/design/SUTRA_EXPANSION.md)) is checked by gates in this repository. Built so far (counts from the file system):

| Gate | What exists |
|---|---|
| CG3 customer corpus | `compat/customers/drishti/2.9.11.i7a/`: 478 shipped Sutras and their blocks, the compiled documents (problems, canonical model, plan) and the Drishti-profile case files, 1,043 files with `MANIFEST.sha384`, synthetic demo data only; made by `tools/regime/corpus_export.py` from a read-only Drishti at its tag. `CustomerCorpusTest` replays it (through the reference bridge until the compiler moves here); `RecordedHost` answers host hooks from a recording (binds arrive with the binder, `v0.10.0`) |
| CG1 frozen cases | `conformance/1/FROZEN.sha384` (63 case files, state pre-release) and `FrozenCasesTest`; `tools/regime/freeze.py` freezes at the release |
| CG4 contract append-only | `ContractAppendOnlyTest`; `spec/1/released/` is written at the first release |
| CG6 reserved names | `compat/reserved-names.yaml` (generated from `spec/1/rachana.lang.json`), `compat/promotions.yaml`, `compat/level-switches.yaml`, `ReservedNamesTest` |
| CG7 painter forward vectors | the file list for the Elements lane: [compat/forward-vectors/FILES.md](compat/forward-vectors/FILES.md) |
| CG8 release candidate in Drishti | `tools/gate/drishti_gate.py`: read-only dry run today; `--run` drives a scratch copy at release time |

The tests are in `java/rachana-compat` (tests only, no product code). CG2 (level replay: `compat/levels/1.0/`, `tools/regime/levels.py`, `LevelReplayTest`) and CG5 (API surface: `java/api-surface.txt`, `ApiSurfaceTest`; update with `-Drachana.api.surface.write=true`) are built and run in the gate; the packages `view`, `agg`, `bind`, `wire`, `profile`, `control` and the `parse` additions are **EXPERIMENTAL until v1.0.0** (the baseline says so); the JavaScript facade's surface and CG10 (speed) are not started.
