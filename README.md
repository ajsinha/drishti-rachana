# Drishti Rachana

**Describe a screen. Supply the data. Get a fast, professional UI.**

Drishti Rachana is a declarative UI platform. You write a short **Sutra** (a screen or dashboard described in the
**Rachana** grammar), point it at your data in the agreed **Rachana Data Protocol**, and get a fast, dense,
professional interface drawn with **Drishti Elements**: grids, charts, maps, pivots, forms and live updates.

Status: **planning.** The language and compiler exist today inside the Drishti product and are being separated into
this repository. Drishti is the first customer: it will consume pinned releases of Rachana, exactly as it consumes
Drishti Elements today.

Author: Ashutosh Sinha. Proprietary. All rights reserved (see [LICENSE](LICENSE)).

## What it is made of

| Part | What it does |
|---|---|
| **Sutra spec** | The language: a screen as a short YAML document (`rachana: 1`). Structure defined by a published JSON Schema; the expression language **Rachana-EL** defined by an EBNF grammar. |
| **Conformance suite** | Shared test cases (a Sutra plus data, and the exact expected result). Any implementation, in any language, is Rachana if it passes them. |
| **Compiler** | Turns a Sutra into a ViewModel. One reference implementation (Java), built for the JVM, for JavaScript (browsers, Node) and for WebAssembly (Python and others). |
| **Views** | The painter: draws any ViewModel with Drishti Elements (`<elements-view>`). |
| **Rachana Data Protocol** | Tables as JSON rows or Apache Arrow; live changes as keyed patches over SSE or WebSocket. |
| **SDKs** | Java, JavaScript and **Python** (Flask and FastAPI adapters: serve Sutras and data endpoints, stream live changes, lint Sutras in CI). |
| **Tooling** | `rachana new / dev / build / lint / preview`, editor support from the compiler, a playground and gallery built with Rachana itself. |

## Ways to use it

| Mode | For | How |
|---|---|---|
| Static | analysts | Sutras plus CSV, JSON or Arrow files, `rachana build`: a dashboard site with no backend |
| Embedded | app developers | `<elements-view>` plus a compiled Sutra inside any web app, on your own data API |
| Python app | Flask / FastAPI teams | `pip install drishti-rachana`; mount Sutras, expose tables, stream changes |
| Small server | teams | a standalone server: Sutras plus REST or database sources, live updates, sign-in |
| Drishti | enterprises | the full Drishti product, built on Rachana |

## Documentation

| Document | Contents |
|---|---|
| [Overview](docs/OVERVIEW.md) | What it is, who it is for, the parts, a worked example |
| [Architecture](docs/ARCHITECTURE.md) | Components, compile-to-paint pipeline, data protocol, languages, repository boundaries |
| [Modes](docs/MODES.md) | The five ways to use it, with planned-API snippets |
| [AI](docs/AI.md) | Safe generative UI: chatbot flow and the Rachana MCP server |
| [Roadmap](docs/ROADMAP.md) | Phases R0 to R3 and the rules |

Everything described in these documents is planned unless stated otherwise.
