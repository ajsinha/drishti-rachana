<!-- Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved. Drishti Rachana. Proprietary: see LICENSE. -->
---
product: Drishti Rachana
summary: A declarative UI platform - describe a screen in a short YAML Sutra, supply data over the Rachana Data Protocol, get a fast, dense, professional screen painted by Drishti Elements.
language: "rachana: 1 (levels 1.0 to 1.2)"
parts: [specification, conformance suite, Java compiler, JavaScript build, Python package, Data Protocol, live tables, server, CLI, Studio, libraries, upgrade tool]
runtimes: [JVM (Java 21), Node 20+, browsers, Python 3.10+ on V8]
install-order: [Rachana, tools, Lake, Drishti]
licence: proprietary (LICENSE); specification and conformance suite under LICENSE-SPEC.md (draft)
author: Ashutosh Sinha
---

<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="brand/drishti_rachana-logo.svg">
    <img src="brand/drishti_rachana-logo-light.svg" alt="Drishti Rachana" width="300">
  </picture>
</p>

# Drishti Rachana

**Describe a screen. Supply the data. Get a fast, professional UI.** Screens become assembly, not writing.

You write a **Sutra**, a short YAML file in the **Rachana** language that says what a screen shows and where each value comes from.
Rachana compiles it with your data into a **ViewModel** (plain JSON: every title, figure, column and row worked out) and paints it with
**Drishti Elements**: grids, charts, maps, pivots, forms and live updates. A Sutra holds no code and no pixels, so it is safe to review,
to keep in version control and to have an AI assistant write.

```yaml
rachana: 1.2
sutra: sales-overview
version: 1
title: { id: "'Sales overview'" }
data:
  sales: { table: sales, key: [region, product, month] }
panels:
  - { id: revenue, kind: metric, label: Revenue, value: "sum($.sales, 'revenue')", fmt: amount0, span: 3 }
  - { id: by-region, kind: hbar, title: Revenue by region, rows: "group($.sales, 'region', 'revenue')", label: label, value: value, span: 9 }
```

## What it is made of

| Part | What it does | Where |
|---|---|---|
| **Specification** | the language (`rachana: 1`, levels 1.0 to 1.2), the expression language Rachana-EL, the function catalogue, formats, the ViewModel schema and the Data Protocol | [spec/1](spec/1/README.md) |
| **Conformance suite** | 2,685 cases (a Sutra plus data in, the exact expected result out); any runtime that passes them is Rachana | [conformance](conformance/README.md) |
| **Compiler** | one reference implementation in Java; built for JavaScript (TeaVM); Python runs that bundle on V8; byte-identical answers everywhere | [java](java/README.md), [js](js/README.md), [python](python/README.md) |
| **`<rachana-view>`** | compiles and binds a Sutra in the page and paints it with Drishti Elements | `js/packages/rachana` |
| **Data Protocol** | tables as JSON, Arrow or NDJSON rows; paging, filters, grouping; live keyed patches over SSE or WebSocket; an in-process bridge | [guide](docs/guides/DATA_PROTOCOL.md) |
| **Live tables** | tables kept current by key and version, per-reader views, guarded edits, four-eyes approvals, exactly-once write-back | [guide](docs/guides/LIVE_TABLES.md) |
| **Server** | a project of Sutras over files, JDBC and REST, sign-in, access rules, edits | [guide](docs/guides/SERVER.md) |
| **CLI** | `rachana new`, `lint`, `preview`, `build`, `studio`, `lib`, `upgrade`, `serve` | [guide](docs/guides/CLI_AND_SDKS.md) |
| **Studio** | a visual editor: fragment palette, live preview, checked source, parameter forms, diff and save | [guide](docs/guides/STUDIO.md) |
| **Libraries** | typed fragments to assemble screens from: `core` (33 fragments) and `finance` (50); your own with `rachana lib` | [guide](docs/guides/LIBRARIES.md), [libraries](libraries/README.md) |
| **Python SDK** | lint on V8, a Data Protocol host, Flask and FastAPI adapters | [guide](docs/guides/CLI_AND_SDKS.md#python) |
| **Upgrade tool** | rewrites level-1.0 Sutras and fragments for a later level with the same meaning | [guide](docs/guides/UPGRADE.md) |

## Ways to use it

| Mode | For | How |
|---|---|---|
| Static | analysts | Sutras plus CSV, JSON or Arrow files; `rachana build` writes a site that needs no backend |
| Embedded | app developers | `<rachana-view>` plus a Sutra inside any web app, on your own Data Protocol endpoint |
| Python app | Flask and FastAPI teams | `drishti-rachana`: mount Sutras, expose tables, stream changes |
| Server | teams | the Rachana server: Sutras over files, databases and REST, live updates, sign-in, edits with approvals |
| Drishti | enterprises | the Drishti product, built on Rachana |

## Start here

| Tutorial | You will |
|---|---|
| [Your first dashboard](docs/tutorials/FIRST_DASHBOARD.md) | write, lint, preview and build a dashboard from a CSV file; add a library fragment in Studio |
| [Build and use a library](docs/tutorials/LIBRARY.md) | write a typed fragment, sign and pack a library, map your columns onto it |
| [Edit a live table with approvals](docs/tutorials/LIVE_EDITS.md) | run the server, edit a cell as one user, approve it as another, see it written back |
| [A live board in Flask](docs/tutorials/PYTHON_APP.md) | serve a Sutra and a live table from a Python app |

| Guide | Contents |
|---|---|
| [The Rachana language](docs/guides/LANGUAGE.md) | Sutras, panels, data, expressions, `let` and functions, state and effects, fragments as functions, levels, problems |
| [Libraries and `rachana lib`](docs/guides/LIBRARIES.md) | using, finding, building, signing, packing and locking libraries |
| [Data Protocol](docs/guides/DATA_PROTOCOL.md) | resources, rows, queries, live changes, the bridge, checking a host |
| [Live tables, editing and approvals](docs/guides/LIVE_TABLES.md) | declarations, the live grid and tray, the edit path, four eyes |
| [Server](docs/guides/SERVER.md) | running it, the configuration file, security, write-back |
| [Studio](docs/guides/STUDIO.md) | the visual editor |
| [Command line and SDKs](docs/guides/CLI_AND_SDKS.md) | the CLI, the Java launcher, JavaScript, Python, Java |
| [Upgrading Sutras](docs/guides/UPGRADE.md) | `rachana upgrade` and its rules |

The specification ([spec/1/SPEC.md](spec/1/SPEC.md)) is the reference for every key and rule. The designs behind the product, and their
decisions, are in [docs/design](docs/design/DECISIONS.md). What changed when: [CHANGELOG.md](CHANGELOG.md).

## Build

```bash
cd java && ./mvnw clean verify          # every module, the unit tests, the guards and the compatibility regime (JDK 21); Windows: mvnw.cmd
cd .. && python3 tools/js/build_js.py   # the JavaScript bundles (TeaVM), checked against the conformance suite
python3 tools/conformance/rachana_conformance.py --jvm    # the conformance suite on the JVM
```

## Install for Drishti

The Drishti family builds in this order: **Drishti Tools → Rachana → Lake → Drishti** (`rachana-server` reads and writes CSV
files with Tools' `tools-csv`; Core and the other modules use nothing of Tools). Each one is installed into the local Maven repository
(`~/.m2/repository`, Windows `C:\Users\<user>\.m2\repository`) **from its release tag**, never from a branch, before the next one
builds:

```bash
git checkout <release tag>
cd java && ./mvnw clean install -DskipTests       # Windows: mvnw.cmd clean install -DskipTests
```

In IntelliJ IDEA the same step is the launcher **Rachana install release**, on the tag's checkout. Details:
[java/README.md, Installing a release for Drishti](java/README.md#installing-a-release-for-drishti).

## IDE support

Every part builds and runs from the IDE with checked-in launchers: **IntelliJ IDEA** for Java (open `java/pom.xml`; the launchers of
`java/run-configs/`, JavaScript included, with no bash), **PyCharm** for Python (open `python/`; `python/run-configs/`) and **VS Code**
(open `drishti-rachana.code-workspace`: tasks and launch configurations for all three). The one guide, with prerequisites, Windows
notes and company-network certificates: [java/README.md, Build and run in the IDE](java/README.md#build-and-run-in-the-ide).

## Licence

Author: Ashutosh Sinha. Proprietary, all rights reserved ([LICENSE](LICENSE)). The specification and the conformance suite (`spec/`,
`conformance/`) are intended to be under the permissive [LICENSE-SPEC.md](LICENSE-SPEC.md), still a draft and not yet in force.
