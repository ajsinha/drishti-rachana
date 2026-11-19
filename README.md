<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="brand/drishti_rachana-logo.svg">
    <img src="brand/drishti_rachana-logo-light.svg" alt="Drishti Rachana" width="300">
  </picture>
</p>

# Drishti Rachana

**Describe a screen. Supply the data. Get a fast, professional UI.**

Drishti Rachana is a declarative UI platform. You write a short **Sutra** (a screen or dashboard described in the
**Rachana** grammar), point it at your data in the agreed **Rachana Data Protocol**, and get a fast, dense,
professional interface drawn with **Drishti Elements**: grids, charts, maps, pivots, forms and live updates.

Status: **planning.** The language and compiler exist today inside the Drishti product and are being separated into
this repository. Drishti is the first customer: it will consume pinned releases of Rachana, exactly as it consumes
Drishti Elements today.

Author: Ashutosh Sinha. Proprietary. All rights reserved (see [LICENSE](LICENSE)). The specification and conformance suite (`spec/`, `conformance/`) are intended to be under the permissive [LICENSE-SPEC.md](LICENSE-SPEC.md) (draft).

## What it is made of

| Part | What it does |
|---|---|
| **Sutra spec** | The language: a screen as a short YAML document (`rachana: 1`). Structure defined by a published JSON Schema; the expression language **Rachana-EL** defined by an EBNF grammar. |
| **Conformance suite** | Shared test cases (a Sutra plus data, and the exact expected result). Any implementation, in any language, is Rachana if it passes them. |
| **Compiler** | Turns a Sutra into a ViewModel. One reference implementation (Java), built for the JVM, for JavaScript (browsers, Node) and for WebAssembly (Python and others). |
| **Views** | `<rachana-view>`: compiles and binds a Sutra, then paints the ViewModel with Drishti Elements' painter (`<elements-view>`). |
| **Rachana Data Protocol** | Tables as JSON rows or Apache Arrow; live changes as keyed patches over SSE or WebSocket. |
| **SDKs** | Java, JavaScript and **Python** (Flask and FastAPI adapters: serve Sutras and data endpoints, stream live changes, lint Sutras in CI). |
| **Sutra libraries** (planning) | Prebuilt fragments to reference, parametrise and use in a Sutra: `core`, `finance`, `genomics`, `economy`, `geo`, `ops`, `ai`. Typed parameters, table contracts, slots, events; versioned, pinned, signed. |
| **Tooling** | `rachana new / dev / build / lint / preview`, editor support from the compiler, a playground and gallery built with Rachana itself. |

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
| [Compiler and runtimes](docs/design/COMPILER_AND_RUNTIMES.md) | Repository layout, the Java reference, the JavaScript and WebAssembly builds, the conformance suite, performance budgets, the CLI, desktop hosts, the standalone server |
| [Split plan](docs/design/SPLIT_PLAN.md) | The step-by-step separation from Drishti, guards, tests that move, risks |
| [Sutra libraries](docs/design/LIBRARIES.md) | **Planning.** The fragment-library framework: one mechanism (fragments, typed), `use: library/fragment@major`, table contracts, events, versions and the lock file, resolution, the `.rlib` archive, signing, licensing, generated documents and gallery, tests and gates, the AI kit, the Studio palette, a bank's private library, Drishti's migration with no screen change |
| [Library catalogue](docs/design/LIBRARY_CATALOGUE.md) | **Planning.** Seven libraries and 209 fragments, each with its purpose, parameters, data, slots, events, what it needs and an example |
| [Generative UI](docs/design/AI_GENUI.md) | Prompt kit, validate-and-fix loop, MCP tool schemas, safety, evaluation |
| [Decisions](docs/design/DECISIONS.md) | Numbered questions for the owner, each with a recommendation |

Everything described in these documents is planned unless stated otherwise.
