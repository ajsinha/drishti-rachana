<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary. All rights reserved. -->
# Sutra libraries: the fragment-library framework

Status: **framework partly built (G2, 2026-11-21), the rest planning.** Built at language level 1.2 (SPEC 2.5, with
conformance cases; the CHANGELOG's Unreleased section lists the classes): the typed form (section 4.2's three parameter
classes, `required`/`default`/`optional`, `min`/`max`/`options`, `when-bound`, slots of `panels`; problems `RCH-2114` to
`2118`), `libraries:` and `use: <library>/<fragment>@N` and the long form (7.1), the manifest and its checks (3.2, 3.3:
file types, `exports` staleness, `depends` without cycles and at most 4 deep, `scope` with `profiles`), the resolvers
memory, folder, archive, classpath and package (10; the carriers' layouts of 11.2 are read, the wrappers themselves
are not generated yet), the lock (9.3), the deterministic
`.rlib` with SHA-384 per file and per archive, Ed25519 signatures with a trust list and `require-signed` (12; verified
on the JVM by `rachana-tools`), the hermetic check of a public library's typed fragments (4.4, `RCH-2117`) and the
`FragmentResolver` adapter (10). One choice made while building: a `text` parameter takes its text as written at the
use (a label, a title), not as an expression. Built next (G2 lane A2), under the owner's rule **fragments are
functions** (decision L-21): `as:` (instance-scoped ids `<as>-<id>`), declared `emits` with payloads read as
`@<as>.<event>.<field>`, `on:` setting a control (host actions wait for I1), the `{from, columns}` mapping renamed at
compile time with problems naming both sides (`RCH-2114`..`2116`, advisory `RCH-2120`), column texts of Core's column
parameters and lambda fields renamed too (lane A3; `RCH-2121` where a mapping cannot reach), contract files
(`contracts/<name>.json`, 5.2), the hermetic check for every typed fragment, and the library hashes in the compile
result (`Sutra.libraries()`). `fn` parameters (`fn(T, ...) -> U`, a lambda
in the user's context; lane A3), and `rachana lib contracts` / `rachana lib check-host` (22; in `java/rachana-cli` over
`LibraryTool`). Library functions (X42: manifest `functions:` and `functions/*.yaml`, called `<library>.<f>(...)`, lane A4).
Private state (`state:`, `<as>.<name>`, a stateful fragment needs `as:`), `outputs:` and a use's `on:` effect list writing
the screen's state (`event.<name>`) (lane A5, SPEC 2.5 and 2.8). Not built yet: the `state` and `selection` name parameters
(6.3), overlays, section 14 to
17 (documents, gates LG1 to LG9 beyond what `lib` cases check, the AI kit, Studio), the CLI commands of 22 (the work is in
`LibraryTool`). Per L-12 no library content lives here: the conformance suite carries a tiny synthetic `demo` library.
The rest of this document is the design; syntax not listed above is settled only when it enters `spec/1/SPEC.md` with a
`since` and conformance cases.
Sizes are builder-days for compiler, tools, cases and documents together.

Owner direction (2026-11-19): Rachana and Elements are to be complete products in themselves, and Rachana is to ship
**prebuilt Sutra libraries**: fragments that an author references, parametrises and uses in an executable Sutra,
arranged in distinct libraries (`core`, `finance`, `genomics`, and so on).

This document is the **framework**: what a library and a fragment are, how they are referenced, typed, versioned,
found, packaged, signed, licensed, documented, tested, offered to models and to Studio, and how a bank adds its own.
The companion [LIBRARY_CATALOGUE](LIBRARY_CATALOGUE.md) lists the libraries and every fragment proposed for them.
The questions for the owner are L-1 to L-20 in [DECISIONS](DECISIONS.md).

Read with: [SUTRA_EXPANSION](SUTRA_EXPANSION.md) (C1 components, C2 `extends`, C3 themes, section 6 the compatibility
regime, T1 document tests, M1 to M3), [EL_EXPANSION](EL_EXPANSION.md) (X8 the type language, X13 lambdas, X41 names
and functions, X42 libraries, X43 tests), [CORE_AND_PROFILES](CORE_AND_PROFILES.md) (hooks, profiles),
[DATA_PROTOCOL](DATA_PROTOCOL.md) (table schemas), [AI_GENUI](AI_GENUI.md) (the prompt kit),
[SPEC](../../spec/1/SPEC.md) 2.5 (fragments today).

## Contents

1. [Vision and principles](#1-vision-and-principles)
2. [One mechanism: the words](#2-one-mechanism-the-words)
3. [A library](#3-a-library)
4. [A fragment](#4-a-fragment)
5. [Data requirements](#5-data-requirements)
6. [Events: the only way out](#6-events-the-only-way-out)
7. [Using a fragment](#7-using-a-fragment)
8. [Theme, accessibility, languages](#8-theme-accessibility-languages)
9. [Versions, pins and the lockfile](#9-versions-pins-and-the-lockfile)
10. [Resolution](#10-resolution)
11. [Packaging and distribution](#11-packaging-and-distribution)
12. [Integrity and signing](#12-integrity-and-signing)
13. [Licensing](#13-licensing)
14. [Generated documents and the gallery](#14-generated-documents-and-the-gallery)
15. [Tests and gates](#15-tests-and-gates)
16. [The AI kit](#16-the-ai-kit)
17. [The Studio palette](#17-the-studio-palette)
18. [Performance budgets](#18-performance-budgets)
19. [Security](#19-security)
20. [A bank's private library](#20-a-banks-private-library)
21. [Drishti's existing fragments: one mechanism, zero screen change](#21-drishtis-existing-fragments-one-mechanism-zero-screen-change)
22. [Commands and problem codes](#22-commands-and-problem-codes)
23. [Roadmap and sizes](#23-roadmap-and-sizes)

## 1. Vision and principles

An author should build a professional screen mostly by **choosing and filling in**, not by writing panels: "a VaR
tile row, a P&L histogram with the VaR marked, a limit gauge, a blotter with drill" is four `use:` lines and the
names of four tables. A model should do the same, in fewer tokens and with fewer mistakes than writing panels. A
bank should be able to add its own vocabulary beside the shipped one and govern it as it governs code.

| # | Principle | What it rules out |
|---|---|---|
| 1 | **One mechanism.** A fragment is the only unit of reuse of screen structure. C1's "component" is what a fragment becomes when its parameters are typed; it is not a second thing. Functions (X42) and fragments ship in the **same** library, found by the same resolver, pinned by the same lockfile | a component model beside fragments; a function-library format beside a fragment-library format |
| 2 | **A library is data.** YAML, JSON and JSON Lines only. No script, no style sheet, no painter code, no binary | a plugin system; supply-chain risk of executable content |
| 3 | **Expansion is a compile-time step.** A use is replaced by the fragment's panels before binding (TRANSLATION T1). The ViewModel never shows that a panel came from a library; a painter needs nothing new | a runtime component tree; painter changes per library |
| 4 | **Hermetic.** A library fragment reads only what the use hands it: parameters, bound tables, slots. It cannot read the screen's record, controls, state or tables by name | hidden coupling to a host's field names; a fragment that reaches data its author was not given |
| 5 | **A fragment asks, the host decides.** A fragment declares the table shape it needs as a contract; it names no table, no address, no host action, no permission, no role | a library that only works on one host; a library that widens access |
| 6 | **Frozen once released.** A released fragment major produces the same ViewModel for the same inputs in every later release of its library. An improvement that would change a screen is a new fragment major beside the old one | "we upgraded the library and forty screens moved" |
| 7 | **Offline and exact.** Compilation never touches a network. A project pins exact library versions with content hashes; the compile result records them | a build that changes because a registry did |
| 8 | **Invisible unless asked for** (SUTRA_EXPANSION 6). Libraries, typed fragments and every fragment in them carry the language level and painter contract they need. A host below that line is not offered them and is not changed by them | any effect on Drishti from the existence of a library |
| 9 | **A fragment is a product.** It ships with its document, examples on synthetic data, tests, an accessibility summary, a budget and a place in the gallery, or it does not ship | a folder of snippets |
| 10 | **Generic.** First-party libraries use the words of a domain (trade, limit, variant), never the names of a product, a vendor system or a customer | Drishti's or any bank's field names in a public library |

## 2. One mechanism: the words

| Word | Meaning | Status |
|---|---|---|
| **fragment** | A named, versioned document whose `panels:` (and, typed, its overlays, state and events) replace a `use:` in a Sutra. File `<name>.v<N>.fragment.yaml`, header `fragment: <name>` | today (SPEC 2.5) |
| **text form** | A fragment whose `params:` are text defaults, filled in as `%{name}` before checking. This is every fragment that exists today | today; kept for all of major 1 with identical output |
| **typed form** | A fragment whose `params:` are declarations with a `type`. Adds checked parameters, table contracts, slots, declared events, private state, overlays, `as:` namespacing. This **is** C1 | planned (C1) |
| **component** | Prose only: a typed fragment. Not a key, not a file kind, not a second resolver. The header key stays `fragment:`; the sketch key `block:` in SUTRA_EXPANSION 3.2 is replaced by it | decision L-1 |
| **block** | The older shared-panel file of a host (Drishti has them). A host matter: its resolver may serve blocks as text-form fragments. Core has one word | today, profile |
| **library** | A named, versioned folder of fragments, functions (X42), formats, table contracts, samples, tests and messages, with one manifest | planned (X42, extended here) |
| **use** | One reference to a fragment inside a Sutra or another fragment: `{ use: finance/limit-gauge@1, with: {...} }` | today (`use:`, `with:`); `as:`, `slots:`, `on:` planned |

How a file is read: a fragment is in the **typed form** when every entry of its `params:` is a mapping with a `type`
key; otherwise it is in the text form. A file is entirely one or the other (a mixture is a problem). At a host's
language level below the one that introduces the typed form, a typed fragment is `RCH-2008` like any newer name, and
nothing about the text form changes: same expansion, same problems, same bytes.

X42's file (`treasury.rachana-lib.yaml`: name, version, `functions`, optional `formats`) is the library **manifest**
of this document with only functions in it. `libraries: [treasury@2]` and `treasury.carry(...)` keep their meaning.
The function libraries EL_EXPANSION names (`timeseries`, `statistics`, `risk`, `finance`, `quant`, `geo`, `units`,
`linalg`, `sketch`) are libraries in exactly this sense; `finance` and `geo` hold both functions and fragments.

## 3. A library

### 3.1 The folder

```text
finance/
  finance.rachana-lib.yaml          the manifest (3.2)
  fragments/
    limit-gauge.v1.fragment.yaml    one file per fragment major; majors live side by side
    limit-gauge.v2.fragment.yaml
  functions/                        X42 function files (may also be written in the manifest)
  formats.yaml                      named formats the library adds (never redefines one)
  contracts/
    scenario-pnl.table.json         a table contract in the Data Protocol's schema form (5.2)
  samples/
    scenario-pnl.small.jsonl        synthetic rows for each contract: gallery, tests, AI examples
  tests/
    limit-gauge.v1.test.yaml        T1 document tests (15)
  goldens/
    limit-gauge.v1/basic.viewmodel.json
  messages/
    en.yaml  fr.yaml                texts the fragments print, by key (A2)
  LICENSE                           the library's own licence (13)
  MANIFEST.sha384                   a hash per file (12)
  SIGNATURE                         optional detached signature over MANIFEST.sha384 (12)
```

Only these file types are accepted: `.yaml`, `.json`, `.jsonl`, `.md`, `LICENSE`, `MANIFEST.sha384`, `SIGNATURE`.
Anything else makes the library invalid (`RCH-2112`): a library cannot carry code, markup, fonts or images.

### 3.2 The manifest

```yaml
rachana: 1
library: finance
version: 1.4.2                       # semver (9.1)
title: Finance
summary: Trading, market risk, counterparty risk and market data screens in generic terms.
publisher: { name: "Ashutosh Sinha", key: ed25519:9f3c… }
licence: LicenseRef-Rachana-Domain-Library     # an SPDX id or a LicenseRef (13)
scope: public                        # public | private (20); private may declare profiles
profiles: []                         # empty: Core-pure, runs on any host
depends: [ core@1, statistics@1, risk@1 ]      # libraries this one uses; no cycles
needs: { language: "1.2", contract: "1.2" }    # the lowest level any fragment needs; per fragment in exports
categories:
  - { id: market-risk, title: Market risk }
  - { id: trading,     title: Trading }
exports:                             # generated by `rachana lib build`, checked for staleness, never typed by hand
  fragments:
    - { name: limit-gauge, majors: [1, 2], category: market-risk, status: active,
        needs: { language: "1.2", contract: "1.0", kinds: [gauge, metric], functions: [safe_div] } }
  functions: [ carry, roll_down ]
  formats:   [ bp1, dv01 ]
  contracts: [ scenario-pnl, limit-utilisation ]
```

`exports` is the library's public surface: what the palette, the AI kit, the gallery and the lockfile read without
opening a fragment. A fragment file not listed is private to the library (a building block of its composites).

### 3.3 Rules

1. A library name is lower-case letters, digits and `-`, at most 32 characters. First-party names are reserved in
   `compat/reserved-libraries.yaml`; a private library carries its owner's prefix (`acme-risk`).
2. `depends` forms no cycle and is at most 4 deep. A library uses only libraries it declares.
3. A library with `profiles: []` uses only Core names. A library that declares a profile (`profiles: [drishti]`)
   may use that profile's kinds, sources and action catalogue, runs only on a host with that profile, and must be
   `scope: private` (a first-party public library never names a host).
4. A library never redefines a Core function, format, tone or kind, nor a name of a library it depends on.
5. Everything in a library carries `since` in the library's own version, and each fragment records the language
   level and ViewModel contract it needs (computed by `rachana explain --needs`, never typed).

## 4. A fragment

### 4.1 The typed form, whole

```yaml
rachana: 1
fragment: limit-gauge
version: 1                                     # the fragment's own major: its interface and its output (9.2)
doc:
  summary: "Utilisation of one limit: used against the limit, with warning and breach bands."
  description: >-
    One limit as a gauge with the amounts beside it. Use one per limit that deserves a panel; for many limits use
    limit-table. Not for limits with two sides (use limit-band).
  category: market-risk
  tags: [limit, utilisation, gauge]
  intents: [status-board]                      # the AI kit's intents this fragment answers (16)
params:
  used:     { type: number, required: true, doc: "The amount used, in the limit's unit" }
  limit:    { type: number, required: true, doc: "The limit; an empty or zero limit shows 'no limit set'" }
  warn-at:  { type: number, default: 0.8, min: 0, max: 1, doc: "Share of the limit from which the tone is warn" }
  label:    { type: text, default: "Limit used" }
  fmt:      { type: format, default: amount0 }
  history:                                     # an optional table, with the shape it must have (section 5)
    type: table
    optional: true
    columns: { date: { type: date }, used: { type: number } }
slots:
  footer: { accepts: panels, max: 2, doc: "Panels under the gauge, e.g. a note on the limit's owner" }
emits:
  - { name: breach-click, payload: { used: number, limit: number }, doc: "The reader clicked the breach marker" }
let:
  share: "safe_div(arg.used, arg.limit)"
panels:
  - id: gauge
    kind: gauge
    title: "${arg.label}"
    value: share
    max: 1
    fmt: pct1
    tone: "share >= 1 ? 'bad' : share >= arg.warn-at ? 'warn' : 'good'"
    a11y: { summary: "${fmt(arg.used, '%{fmt}')} used of ${fmt(arg.limit, '%{fmt}')}" }
  - id: trend
    kind: line
    when-bound: history                        # left out when the optional table was not given
    rows: arg.history
    x: date
    y: used
    fmt: "%{fmt}"
  - { slot: footer }
budget: { panels: 4, bind-ms: 2 }
examples:
  - name: basic
    with: { used: 8200000, limit: 10000000 }
  - name: breached, with history
    with: { used: 11200000, limit: 10000000, history: { sample: limit-history.small } }
```

### 4.2 Parameters: two classes of type

A parameter is either a **value** (computed when the screen binds) or a **name** (fixed when the Sutra compiles).

| Class | Types | Written at the use as | Read inside the fragment as | Checked |
|---|---|---|---|---|
| **Value** | the EL type language (X8): `text`, `number`, `integer`, `decimal`, `boolean`, `date`, `datetime`, `duration`, `money`, `range<T>`, `list<T>`, `record{…}`, `T?`, and `fn(T…) -> U` (a named-parameter lambda, X13) | an expression, compiled **in the user's context** (it may read the user's `$`, `ctl.`, `state.`, `arg.`) | `arg.<name>` | type of the expression against the declared type, in every mode |
| **Table** | `table` with `columns:` or a named `contract:` (section 5) | an expression giving a table, or `{ from, columns }` to map column names | `arg.<name>`, a table whose columns have the fragment's names | each required column present, of the declared type; key, live and window rules |
| **Name** | `id`, `format`, `tone`, `icon`, `palette`, `variant`, `key` (a function key or chord), `area`, `enum` (with `options:`), `message` (a key of the library's message table), `columns` (a list of column definitions), `state` and `selection` (a name of the user's that the fragment may write, 6.3), `permission`, `fragment` (a use, for a parameterised inner fragment) | a plain word or list | `%{name}`, substituted as a **checked token** | against the closed list of that type (the formats, the tones, the icons, the user's declared state) |

Rules:

1. `arg.` is the same context root a screen's `args:` use (SUTRA_EXPANSION D1): a fragment's parameters are its
   arguments. Inside a fragment `arg.` means the fragment's own; the fragment cannot see the screen's.
2. `%{name}` in the typed form is allowed only for name-class parameters and only in positions that take a word of
   that type. It is a token, not text: a `format` parameter can only ever put a format name where a format name
   goes. The text form's free substitution is unchanged, for text-form files only.
3. `required: true` or a `default`. A default of a value parameter is a **literal** in a public library (it may not
   read the user's context: that would tie the library to a host's field names). A private library may write an
   expression default, compiled in the user's context (this is what Drishti's kit does today with `rows: $.POINTS`).
4. Constraints: `min`, `max`, `options`, `pattern` (a `like` pattern), `max-rows` on a table. A violated constraint
   on a literal is a compile problem at the use; on a computed value it is a state with words on the fragment's
   root panel, never a crash.
5. At most 40 parameters. A fragment that wants more is two fragments.
6. Every parameter has a `doc` of one line; the docstring lint (`RCH-2055`) requires it.
7. A declaration takes only its keys: a parameter `type`, `doc`, `default`, `required`, `optional`, `columns`, `contract`, `min`,
   `max`, `options`, `pattern`, `max-rows`, `live`, `key`, `window`, `dynamic`, `of`; a column `type`, `doc`, `optional`. Any other
   is `RCH-2048`. In a flow mapping (`{ type: number, doc: ... }`) a text with a comma is quoted: unquoted, `doc: The change,
   written beside it` is the doc "The change" and a key "written beside it", which the manifest would carry as such.

Every fragment also accepts the **placement words** on the use itself, with no declaration: `as` (7.2), `title`,
`area`, `span`, `when`, `key`, `requires`, `variant`, `load`, `a11y`. They apply to the fragment's root: its single
top panel or, when it has several, the `group` that holds them (L1). A text-form fragment keeps taking these as
parameters, as today.

### 4.3 Slots

A slot is a named hole the user may fill with their own content. The content is the **user's** code: it is compiled
in the user's context and may additionally read what the slot exposes.

```yaml
slots:
  detail:  { accepts: panels,  max: 6, exposes: { row: record }, doc: "Shown beside the chosen row" }
  columns: { accepts: columns, max: 20, doc: "Extra columns after the standard ones" }
  actions: { accepts: actions, max: 5,  doc: "Buttons in the header" }
```

`accepts` is one of `panels`, `columns`, `controls`, `fields` (form fields), `actions`, `menu` (context-menu
entries), `layers` (chart layers, V1). An empty slot draws nothing unless the fragment gives a `default:`. Content
reads an exposed value as `slot.<name>` (`slot.row.id`). At most 8 slots per fragment.

### 4.4 What a fragment may contain

| May contain | Bound |
|---|---|
| `panels:` (and `group` containers, L1), `overlays:` (L2), `controls:` | panels after expansion count toward the screen's 200 |
| `state:` (private to each use, S1), `let:` (S2), `selections:` (I2), one `flow:` (S3) | the screen's limits apply to the total |
| `functions:` (X41) and calls into the library's own and its dependencies' functions | no cycles |
| `emits:`, local effects (6.2), `form:` without save | |
| `use:` of fragments of its own library or of declared dependencies, always with a pinned major | nesting depth 4 (SUTRA_EXPANSION 5); a cycle is found when the library is built |

| May not contain | Why |
|---|---|
| `data:` entries, `source:` with a table name, any `url:` | principle 5: the user hands tables over |
| reads of `$`, `ctl.`, `state.`, `sel.`, `@<panel>` of the screen | principle 4; they are reached only through parameters and slots |
| `host:` effects, `go:` to a literal Sutra or link, a literal `requires:` | principle 5 (6.2); a profile-bound private library may |
| `extends:` of a Sutra, `match`, any profile key in a Core-pure library | a fragment is not a screen |
| colours, pixel sizes, HTML | never in any Sutra |

## 5. Data requirements

### 5.1 A table parameter is a contract

A fragment says what it needs from a table in the words of its own domain; the user says which of their tables it
is; the compiler checks the fit when the table's schema is known (Data Protocol section 2) and the binder checks it
again against the rows.

```yaml
params:
  rows:
    type: table
    doc: "One row per scenario day"
    key: optional                  # required | optional | none: required for live changes, selection and drill
    live: ok                       # ok | no: whether the fragment redraws correctly from keyed patches
    window: no                     # ok | no: whether a windowed (server) table may be passed (D2)
    max-rows: 100000               # above this the fragment refuses with words rather than draw slowly
    columns:
      date: { type: date,   doc: "The scenario's date" }
      pnl:  { type: number, doc: "P&L under that scenario; gains positive" }
      desk: { type: text,   optional: true, doc: "Shown as a split when given" }
```

At the use, three forms, all meaning the same thing:

```yaml
with: { rows: $.scenario_pnl }                                    # the table already has columns date, pnl
with: { rows: { from: $.vectors, columns: { date: SCEN_DATE, pnl: PNL_USD } } }   # map the user's names
with: { rows: "$.vectors |> select(date: r => r.SCEN_DATE, pnl: r => r.PNL_USD * $.fx)" }   # shape it first (X20)
```

- Inside the fragment the table has the fragment's column names (`@.pnl`). The mapping is a rename in the plan: no
  copy, and a push-down to the provider uses the user's real names.
- A column not in the contract is not visible to the fragment. A fragment cannot enumerate the user's columns.
  (A `columns`-class parameter is how a user passes extra columns to a table-drawing fragment.)
- An unmapped optional column is absent; `when-bound: <column>` on a panel, column or layer leaves that part out.
- A computed column is made by the user, upstream (`let:`, X20 verbs, a derived table D3). A mapping takes a
  column name only: one way to compute, and it stays in the user's file where a reviewer sees it.
- The rename covers the panels that read the table (`rows: arg.<table>`): their field-name options (`x`, `y`, `label`,
  `value`, `by`, ...) and column `bind`s; and in every expression `@.<column>` over a row of the table, a lambda element's
  field, the field of `first`/`last`/an index of its rows, and the text of a Core column parameter over the table
  (`sum(arg.pnl, 'pnl')`, `group(arg.t, 'desk', 'pnl')`; `role: column` in `functions.yaml`). A computed column text, a local
  column's text over another list, a column the table does not declare, or a bare name that is a local column is `RCH-2121`
  (L-21, rule 3): the fragment writes `@.<name>` or the column as text.
- A mapping the fragment never reads is an advisory (`RCH-2120`, severity warning), never a refusal.
- Misfit is a problem at the use: `RCH-2116` "`rows` of `finance/var-histogram@1` needs column `pnl` (number);
  `$.vectors` has `PNL_USD`, `SCEN_DATE`, `DESK`", with the fix "map it: `columns: { pnl: PNL_USD }`".

### 5.2 Published contracts

A shape used by several fragments is written once as a contract file, in the Data Protocol's schema form, and
referenced: `rows: { type: table, contract: scenario-pnl }`.

```json
{ "contract": "scenario-pnl", "library": "finance", "since": "1.0.0",
  "title": "Scenario P&L",
  "grain": "one row per scope and scenario day",
  "key": ["scope", "date"],
  "columns": [
    { "name": "scope", "type": "string",  "doc": "What the P&L is for: a book, a desk, a portfolio" },
    { "name": "date",  "type": "date",    "doc": "The scenario's date" },
    { "name": "pnl",   "type": "decimal", "unit": "currency", "doc": "P&L under that scenario; gains positive" } ] }
```

What a contract gives:

| Reader | Use |
|---|---|
| a host's data team | the list of tables to expose to light up a library: "serve these nine tables and the 41 fragments of `finance` work". `rachana lib contracts finance` prints it; `rachana lib check-host --base <url>` asks a Data Protocol host for each table's schema and reports the fit, column by column |
| the compiler | a table whose schema satisfies the contract needs no `columns:` mapping at the use |
| the gallery, tests and the AI kit | every contract has synthetic sample rows (`samples/<contract>.<size>.jsonl`) |
| Studio | "which of my tables fit this fragment" is a schema comparison, ranked |

A contract is versioned with its library and only grows (a new optional column); a changed meaning is a new
contract name. Contracts state shape and meaning, never where data comes from, and never a row rule: who may read
a row stays with the host's `TableProvider` (CORE_AND_PROFILES 3.5).

### 5.3 Live and large tables

A fragment that says `live: ok` has tests that replay keyed patches (conformance kind `patch`) over its sample. A
fragment that says `window: ok` reads its table only through operations the provider can answer (sort, filter,
group, a page), which the plan cases prove; passing a windowed table to a fragment that says `window: no` is a
compile problem at the use (`RCH-2074`, as for any panel), never a silent slow path.

## 6. Events: the only way out

### 6.1 Declared events

A fragment tells the screen that something happened by an event with a typed payload. That is the only thing a
Core-pure fragment can do outside itself.

```yaml
emits:
  - { name: select, payload: { key: text, row: record }, doc: "A row was chosen" }
  - { name: submit, payload: { order: "record{side: text, qty: number, limit: number?}" }, doc: "The ticket was sent" }
```

The user decides what an event means on their screen:

```yaml
- use: finance/order-ticket@1
  as: tk
  with: { instrument: "state.instrument", sides: [BUY, SELL] }
  on:
    submit: [ { host: order.place, with: { order: "event.order" }, optimistic: { status: "'SENDING'" } }, { close: ticket } ]
- { id: fills, kind: table, rows: "$.fills[?@.instrument == @tk.select.key]", columns: [ ... ] }   # read like any event
```

### 6.2 Effects inside a fragment

Of the closed effect list (SUTRA_EXPANSION 3.4) a fragment may use the **local** ones: `set`, `toggle` and `clear`
on its own state and selections; `open` and `close` on its own overlays; `fire` (its own events and flow);
`submit` (its own form, which raises an event); `refresh`; `copy`; `export` (the host's export still decides);
`notify`. It may not use `host` or `go` with a literal name. So a fragment can never send an order, open another
screen or call anything of the host's: it reports, and the screen's author wires the report to the host.

A private library that declares a profile may name that profile's actions (`host: order.cancel`); it then runs
only where that profile is loaded, and the host's `Permissions` hook still answers per reader.

### 6.3 Writing the user's state: by handing over a name

A filter bar must set something the rest of the screen reads. The user hands over **one name**, and the fragment
may write only that:

```yaml
params:
  into:  { type: state, of: text, doc: "The state the chosen value is written to" }
  feeds: { type: selection, optional: true, doc: "A selection a click on a bar feeds" }
---
- use: core/segmented-filter@1
  with: { options: "distinct($.trades, 'desk')", into: desk }       # state.desk is the user's, declared in state:
```

This is the existing rule ("only an action writes state") with the target named by the user at compile time. It is
not two-way binding: the fragment reads the current value as an ordinary value parameter if it needs it.

## 7. Using a fragment

### 7.1 Naming the libraries

```yaml
rachana: 1
sutra: desk-risk
libraries: [ core@1, finance@1 ]             # the one place a library's version line is chosen (X42's key)
panels:
  - use: finance/var-tiles@1                 # <library>/<fragment>@<fragment major>
    with: { var: "$.risk.var99", es: "$.risk.es975", limit: "$.limits.var" }
  - use: core/ranked-bars@1
    with: { rows: $.by_desk, label: desk, value: var }
```

- `libraries:` names each library and its **major** (`finance@1`); the exact version comes from the lockfile
  (9.3). The same key serves X42's functions: `finance.dv01(...)` needs `finance@1` in the list.
- `use: <library>/<fragment>@<major>`. The fragment major is required in a published Sutra and inside every
  library (`RCH-2048` otherwise, as today for composites); the CLI's `new` and Studio always write it.
- The long form `use: finance@1/var-tiles@1` is accepted in a Sutra that has no `libraries:` key (a single-file
  Sutra written by a model or in the playground). It means exactly the declaration plus the short form, and
  `rachana fmt` rewrites it so (decision L-3).
- An **unqualified** name (`use: series-line@1`) is resolved by the host's resolver as today: in Drishti its kit,
  in a project its own `fragments/` folder. Nothing about unqualified names changes.
- The existing qualified form (`use: trading/deal-ticket@1`, a pack's fragment in Drishti) already has this shape:
  a pack that ships fragments **is** a library named after the pack (section 21).

### 7.2 `as`, `with`, `slots`, `on`

```yaml
- use: core/master-detail@1
  as: md                                     # ids become md.list, md.detail; events @md.select; outputs state.md.*
  title: Counterparties
  span: 12
  with:
    rows: $.counterparties
    key: id
    columns: [ { label: Name, bind: "@.name" }, { label: Rating, bind: "@.rating" } ]
  slots:
    detail:
      - use: finance/counterparty-card@1
        with: { counterparty: "slot.row" }
  on:
    select: [ { set: { cpty: "event.key" } } ]
```

- `as:` names one use, like a variable holding a call's result (L-21): every id the fragment brings becomes
  `<as>-<id>` (a dot is not an id character), its events are read as `@<as>.<event>.<field>`. Two uses of one fragment
  are two instances only with `as:`; without it the second use's ids collide (`RCH-2024`).
- `with:` is checked against the parameter declarations: an unknown key is `RCH-2114` with the valid names; a
  missing required one `RCH-2115`; a wrong type `RCH-2116`.
- A fragment may publish **outputs**: `outputs: { query: text }` names private state the user may read (never
  write) as `state.<as>.<name>`. Everything else inside is invisible to the user.

### 7.3 Variants of a fragment, and leaving a library

- **A variant** of a first-party fragment is a small fragment in the user's own library that uses it with some
  parameters fixed and slots filled (a composite). That is the supported way to "customise".
- `extends`/`override` (C2) applies to Sutras. It is **not** offered on a library fragment from outside: overriding
  a panel inside someone else's fragment by id would make the fragment's internals part of its interface and break
  the frozen-output rule. A fragment exposes what may vary as parameters and slots.
- **Eject.** `rachana lib eject <sutra> --use <id>` replaces one use by the fragment's panels written in place
  (the result binds to the same ViewModel; a proof case per fragment). The escape hatch for the one screen that
  needs what no parameter gives; the library's licence says whether the ejected text may be kept (13).
- **Extract.** `rachana lib extract` (and Studio's "make a fragment") turns chosen panels of a Sutra into a
  fragment of a private library and replaces them by its use, with the same proof.

## 8. Theme, accessibility, languages

- **Theme.** A fragment names tones, variants, palettes and icons, never a colour or a size (SUTRA_EXPANSION C3).
  The screen's `theme:` and the painter's tokens decide the look; one fragment looks right in every host theme,
  light and dark, compact and comfortable. A fragment takes `variant` as a placement word and may declare
  `palette` and `tone` parameters where the choice is the user's (a categorical palette for series).
- **Accessibility.** Every panel that draws a chart carries an `a11y.summary` template built from its parameters;
  a fragment without one fails the library gate (15). Keyboard reachability of every action a fragment offers is a
  test: an action only in a context menu is refused.
- **Languages.** Every text a fragment prints by itself (a column heading it supplies, "no limit set") is a key of
  the library's message table (`messages/<lang>.yaml`, A2), with English as the fallback. Texts the user supplies
  are the user's. Until A2 exists, a library ships English and its texts are parameters with English defaults.
- **Phone and tiers.** A fragment declares its phone tier (`must`, `glance`, `hide`) as today, and from L3 may
  restate its inner arrangement per container-width tier. The gallery shows each example at three widths.

## 9. Versions, pins and the lockfile

### 9.1 Three numbers

| Thing | Form | Moves when | Promise |
|---|---|---|---|
| **Library version** | semver `X.Y.Z` | every release | `Z`: no golden of any released fragment major changes. `Y`: adds fragments, fragment majors, optional parameters, functions, contracts, messages. `X`: may remove a fragment major, a function or a contract (deprecated for at least one full major first) |
| **Fragment major** | `@N` | the fragment's interface or its output for existing inputs must change | majors live side by side in the library; a Sutra moves one use at a time |
| **Language level and ViewModel contract needed** | `1.minor` each, per fragment | computed | a host below it is not offered the fragment and gets `RCH-2008` naming it if a Sutra uses it |

### 9.2 Frozen fragment majors

Within a fragment major, across all later versions of its library's major:

1. Same `with`, same data, same context: **the same ViewModel bytes**, the same problem list.
2. A new parameter is optional and its default reproduces the earlier output. A new slot is empty by default. A new
   event is one nobody is required to handle. A new optional contract column changes nothing when unmapped.
3. A defect that a golden pinned is fixed in a **new major**, or, with the owner's approval and a line in the
   release notes, in place (the equivalent of a level switch, SUTRA_EXPANSION 6.2). Security fixes are applied in
   place with a notice.

This is what lets a host take a library's minor releases without re-testing every screen, and it is checked
mechanically (gate LG2, section 15).

### 9.3 Pins and the lockfile

A project (a folder of Sutras, a host's configuration, a pack) has one **`rachana.lock`**, written by the CLI,
committed, and read by every compile:

```yaml
# rachana.lock: written by `rachana lib add | update`; do not edit
lock: 1
libraries:
  core:    { version: 1.6.0, sha384: "b7e1…", source: "release:core-1.6.0.rlib", signed-by: "ed25519:9f3c…",
             licence: LicenseRef-Rachana-Library, needs: { language: "1.2", contract: "1.1" } }
  finance: { version: 1.4.2, sha384: "44a0…", source: "release:finance-1.4.2.rlib", signed-by: "ed25519:9f3c…",
             licence: LicenseRef-Rachana-Domain-Library, depends: [ core@1, statistics@1, risk@1 ] }
```

- `libraries: [finance@1]` in a Sutra is a **constraint** (major 1); the lock is the **choice** (1.4.2, this hash).
  A Sutra never carries an exact version: forty Sutras of one project always agree.
- A compile with a library that is not in the lock, or whose bytes do not match the hash, fails (`RCH-2111`,
  `RCH-2113`). There is no "latest".
- One version of a library per project. Two libraries that need different majors of a third is a problem at
  `rachana lib add`, not at run time.
- The compile result records each library's name, version and hash (X42), so a ViewModel can be traced to the exact
  fragment text that produced it.
- A host with no lock file (the playground, a model's single-file Sutra) gets the libraries the host's resolver was
  configured with; the versions and hashes are still recorded in the compile result.

## 10. Resolution

```java
// planned API, subject to change
public interface LibraryResolver {
  /** The library of this name satisfying the constraint, or empty. Never performs I/O beyond what the host configured. */
  Optional<LibraryBundle> resolve(String name, VersionConstraint constraint);
  /** What this resolver can offer: for the palette, the AI kit and `rachana lib list`. */
  List<LibraryRef> list();
}
```

| Resolver (shipped) | Finds libraries in | For |
|---|---|---|
| folder | `rachana_libs/<name>/` beside the lock file (unpacked, vendored, committed or restored by the CLI) | static sites, the CLI, Python and Node projects |
| archive | `.rlib` files in a configured folder | servers, desktop hosts |
| classpath | `META-INF/rachana/libs/<name>/` in jars | Java hosts |
| package | the installed npm packages and Python distributions that declare a Rachana library (11.2) | Node and Python hosts |
| memory | a map handed over by the host | tests, the playground, a host with its own store (Drishti's content store, a bank's registry client) |

- **Order.** Resolvers are consulted in the order the host lists them; the first that has the name answers. A name
  found in two places with different hashes is a problem, never a silent choice.
- **No network at compile time.** No shipped resolver opens a connection. Fetching is a separate, explicit step
  (`rachana lib add`, `rachana lib restore`), run by a person or a build, against the registries the project names.
  A host that wants libraries on demand writes its own resolver and owns that decision.
- **The 1.0 hook stays.** `FragmentResolver` (`use: name@version` → a block) keeps its signature and behaviour.
  Core supplies an adapter that answers it from a `LibraryResolver`; a host that implements only `FragmentResolver`
  (Drishti today) is not asked for anything new.
- **Cache.** A parsed library is cached by content hash for the life of the `Rachana` instance; expanding a use
  never re-reads a file.
- **In the browser.** `<rachana-view libraries="…">` takes the address of a prebuilt library index (one JSON per
  library, produced by `rachana lib build --bundle`) on the page's own origin; the static build inlines only the
  fragments a site's Sutras use.

## 11. Packaging and distribution

### 11.1 The `.rlib` archive

One artefact per library version: `finance-1.4.2.rlib`, a ZIP of the folder of section 3.1 built
**deterministically** (sorted entries, fixed timestamps, no extra fields), so the same source gives the same bytes
and the same hash on every machine. `rachana lib pack` writes it; `rachana lib verify` checks the manifest hashes,
the file-type rule, the signature and that `exports` is not stale.

### 11.2 Carriers

The `.rlib` is the canonical thing. Ecosystem packages are **wrappers generated from it** by the release tool, so
a team installs a library the way it installs anything else and its existing proxy, mirror and audit tools apply:

| Carrier | Name | Contents | Found by |
|---|---|---|---|
| a release file | `finance-1.4.2.rlib` on the release location (D22: GitHub Releases at first) | the archive | `rachana lib add finance@1` |
| npm | `@drishti-rachana/lib-finance` | the unpacked folder and the browser bundle | the package resolver; `<rachana-view>` |
| PyPI | `drishti-rachana-lib-finance` | the unpacked folder as package data, with an entry point `rachana.libraries` | the package resolver |
| Maven | `com.ash.rachana.lib:finance` | a jar with `META-INF/rachana/libs/finance/` | the classpath resolver |

Public registries wait for R3, as the runtime does (D22). Until then the release location and a customer's own
mirror carry the archives. A wrapper never contains code; the install tooling's scripts hooks are not used.

## 12. Integrity and signing

| Layer | What | Always? |
|---|---|---|
| `MANIFEST.sha384` | a SHA-384 per file of the library; the archive's own SHA-384 is the library's identity in the lock | always checked at `add`, `restore` and at load |
| lock hash | the compile fails when the bytes found are not the bytes pinned | always |
| `SIGNATURE` | an Ed25519 detached signature of `MANIFEST.sha384` by the publisher's key named in the manifest | first-party libraries are always signed |
| host trust list | `libraries.trusted-publishers: [ed25519:9f3c…]` and `libraries.require-signed: true` in the host's configuration: an unsigned or unknown-publisher library is refused (`RCH-2113`) | the host's choice; recommended on for production, off for a developer's private drafts |

Keys are plain Ed25519 public keys published with the release notes and in this repository; no certificate
authority is involved. Rotation: a library release may be signed by two keys during a changeover.

## 13. Licensing

Each library carries its own `LICENSE` and states it in the manifest; the lock file records it; `rachana lib
licences` prints the list for a project, so a legal review reads one table.

Recommendation (decision L-11):

| Libraries | Licence | Why |
|---|---|---|
| `core`, `geo`, `ops`, `ai` | a short permissive **library licence**: free to use, copy, modify and redistribute, with or without changes, for use with any implementation that passes the conformance suite; included with every Rachana distribution | They are the vocabulary of the product. Examples, tutorials, the AI kit and every customer's private library are built on them; a restriction here taxes adoption and buys nothing, since they are useless without a runtime |
| `finance`, `genomics`, `economy` and later domain libraries | **proprietary domain-library licence**, per library: use under a Rachana commercial licence that includes the library; ejected or modified text may be kept inside the licensee's organisation, not redistributed | The domain knowledge is the paid value above the runtime; per-library licensing lets a genomics customer not pay for finance |
| a customer's private library | the customer's | their content |

There is no technical lock: a library is readable YAML, and a licence, not obfuscation, protects it. The manifest's
`licence` and, for an enterprise delivery, a `licensee:` line in a signed manifest are the record.

Where the first-party libraries are kept is decision L-12: the freely licensed ones in this repository under
`libraries/`; the proprietary domain libraries in a private companion repository until the licensing tiers of R3
are decided, with their catalogue, contracts and gallery published here. Publishing is the irreversible direction,
so it is the one to delay.

## 14. Generated documents and the gallery

Nothing about a fragment is written twice. `rachana lib doc <library>` reads the manifest, the fragments, the
contracts, the messages and the goldens, and writes:

| Output | Contents | Built from |
|---|---|---|
| the library page | title, summary, licence, what it depends on, the contracts to expose, the categories | manifest, contracts |
| one page per fragment | summary and description; a **parameter table** (name, type, default, constraints, meaning); the **data** it needs (per table: columns, types, key, live, window, the contract's link); slots; events with payloads; outputs; what it needs (language level, kinds, functions); every example as the `use:` text **and** as a live render | `doc`, `params`, `slots`, `emits`, `examples`, computed needs |
| the **gallery** | every example of every fragment drawn on the library's synthetic samples, at three widths, light and dark, with "copy the use" and "open in the playground" | examples and samples; the gallery is itself a set of Sutras, built by `rachana build` |
| contract pages | each contract's columns and meaning, the fragments that read it, the sample files | `contracts/` |
| `library.index.json` | the machine form of all of the above: what Studio, the language server and the AI kit read | everything |

A staleness test fails the build when a generated page differs from what the source would produce. The catalogue
in [LIBRARY_CATALOGUE](LIBRARY_CATALOGUE.md) is the planning form of these pages; once a library is built its
pages replace its part of that document.

## 15. Tests and gates

### 15.1 What every fragment ships with (the definition of done)

| # | Required | How it is checked |
|---|---|---|
| 1 | a complete `doc` (summary ≤ 120 characters, description saying when to use it and when not, a `doc` per parameter, slot and event) | docstring lint (`RCH-2055`) |
| 2 | at least two `examples`, one minimal and one full | each example is a T1 document test and a gallery entry |
| 3 | a golden ViewModel per example | `rachana lib test`; frozen from release (LG2) |
| 4 | edge tests: an empty table, one row, a masked value in each shown column, a denied `requires`, the `max-rows` refusal, long and right-to-left text | T1 tests with partial expectations |
| 5 | for `live: ok`: a patch replay; for `window: ok`: plan cases showing every read is push-down-able | conformance kinds `patch`, `plan` |
| 6 | an accessibility summary on every chart panel; every action reachable by keyboard | lint |
| 7 | a `budget:` that its examples meet on the reference sizes (section 18) | measured in the library gate |
| 8 | each table parameter has a sample; each contract has small (≈ 20 rows), medium (≈ 2,000) and, where `window: ok`, large (≈ 200,000, generated from a seed, not committed) | `rachana lib verify` |
| 9 | it compiles at exactly the level and contract its `needs` states, and is refused below | level replay |
| 10 | no host name, product name or real-world data value anywhere in it | the "names no host" guard, extended to `libraries/` |

Tests use the T1 format (`<fragment>.v<N>.test.yaml`), so they run in every runtime with `rachana lib test` and can
be donated to the conformance suite unchanged. A function's `examples` are tests as X43 says.

```yaml
# tests/limit-gauge.v1.test.yaml
tests:
  - name: at 82% the tone is warn
    use: { use: finance/limit-gauge@1, with: { used: 8200000, limit: 10000000 } }
    expect:
      - { at: "panels[limit-gauge.gauge].tone", is: warn }
      - { problems: [] }
  - name: no limit set
    use: { use: finance/limit-gauge@1, with: { used: 5, limit: null } }
    expect: [ { at: "panels[limit-gauge.gauge].state", is: empty, words: "No limit set" } ]
```

### 15.2 Gates

| # | Gate | Checks | Blocks |
|---|---|---|---|
| LG1 | **Library valid** | `rachana lib verify` on every library: file types, manifest, `exports` not stale, dependencies acyclic, hashes | merge |
| LG2 | **Frozen fragments** | for every released fragment major: its released golden ViewModels are reproduced byte for byte by this commit (`libraries/<name>/FROZEN.sha384`); a golden is added, never edited | merge |
| LG3 | **Definition of done** | section 15.1 for every exported fragment | merge |
| LG4 | **Level replay** | each fragment compiles at its stated level and is `RCH-2008` one level below; the library index generated for a host at level L lists only what L can run | merge |
| LG5 | **Interface append-only** | within a fragment major: no parameter, slot, event, output or contract column removed, renamed, retyped or made required | merge |
| LG6 | **Three runtimes agree** | `rachana lib test` results byte-identical on the JVM, JavaScript and WebAssembly builds | release |
| LG7 | **Painted** | every gallery example paints without a console error in the painter's test run, at three widths and both themes; DOM snapshots for the `core` library | release |
| LG8 | **Budgets** | each fragment within its `budget:`; a regression above 10% fails (as CG10) | merge, release |
| LG9 | **AI composes** | the evaluation set of section 16 meets its targets with the library's cards in the kit | release of the kit |

The compatibility gates CG1 to CG10 are untouched. Libraries add no file to the Drishti corpus and change no Core
case: a host that uses no library is not affected by any of this (principle 8).

## 16. The AI kit

A model that composes from fragments writes less and is right more often: `{ use: finance/var-tiles@1, with: {…} }`
is one checked line where the panels it stands for are thirty.

| Part | Contents | Size target |
|---|---|---|
| `libraries.md` | one line per library available to this host: name, summary, the number of fragments by category | ≤ 200 tokens |
| `fragments/<library>.md` | the **fragment card** of each exported fragment: `name@major`, the summary, the parameters as `name: type = default`, the tables as `rows{date: date, pnl: number}`, slots, events, one minimal `use:` | ≤ 120 tokens per fragment |
| `fragments/<library>/<name>.md` | the full page of one fragment, on demand | ≤ 600 tokens |
| `with.schema.json` per fragment | the JSON Schema of its `with:`, `slots:` and `on:`, for structured output and grammar-constrained decoding: a model cannot write a parameter that does not exist | generated |
| intent map | each intent of AI_GENUI 2.1 (and each library's own intents) → the fragments that answer it, chosen by the shape of the caller's table (has a date column, has a key, row count) | generated from `doc.intents` and the contracts |
| examples | every fragment example, with its sample table's schema | the same files as the gallery |

Rules:

1. **Generated, for this host.** The cards come from `library.index.json` filtered to the host's language level,
   painter capabilities and installed libraries, so a model is never told about a fragment the host cannot run.
2. **Layering.** `libraries.md` is always in context; the model pulls a library's cards and a fragment's page
   through the tools. New MCP tools (AI_GENUI 4): `list_fragments(library?, intent?, table?)` returns cards ranked
   by contract fit against a table the caller holds; `get_fragment(name)` returns the page and the `with` schema.
3. **Fragments first.** The system text says: compose from fragments; write a panel in place only when no fragment
   fits. `rachana lint` gains an advisory rule for model-written Sutras: "these six panels are `core/kpi-strip@1`".
4. **Same safety.** A model-written Sutra may use any installed library; it still may not name a `url:` (D29) or a
   host action the host did not list. Fragments add no power (section 19), so they add no review burden.
5. **Streaming.** Under M3 each `use:` is one `panel-add` (or a few): a screen appears fragment by fragment.
6. **Measured.** The evaluation set gains ≈ 150 composition tasks ("a desk risk page from these four tables").
   Targets as D30 (first-try validity ≥ 85%, within three attempts ≥ 99%), plus: at least 70% of panels in the
   answers come from fragments, and median output tokens at most half of the panel-only baseline.

## 17. The Studio palette

Rachana Studio (decision N14) reads `library.index.json`; nothing below is hand-built per fragment.

| Feature | From |
|---|---|
| **Palette**: libraries, then categories, then fragments, with search over name, summary and tags; a thumbnail per fragment | manifest `categories`; the gallery's renders |
| **Fit**: with a table chosen, fragments ranked by how well the table's schema satisfies their contracts; unmet columns named | contracts against the Data Protocol schema |
| **Drop**: placing a fragment writes the `use:` with required parameters asked for at once and a proposed column mapping | `params`, the fit |
| **Property sheet**: one editor per parameter type: a column picker filtered by type for a table's columns; the format, tone, icon and palette pickers; a segmented choice for an `enum`; the expression editor (language server) for values; a state picker for `state` and `selection` | the parameter classes of 4.2 |
| **Slots** as drop zones, labelled with what they accept | `slots` |
| **Events** as a wiring panel: each event with its payload, and the effect list editor | `emits` |
| **Where used**, **upgrade** (`@1` → `@2` with the fragment's migrate rule and a before-and-after render), **eject**, **make a fragment** from chosen panels | the editing operations (M1), 7.3 |
| A private library appears in the same palette, marked with its publisher | the host's resolver |

Studio is built from Drishti Elements like every other screen (the designer, tree, inputs and grid libraries); the
palette is a tree and a grid, not bespoke UI.

## 18. Performance budgets

| Budget | Target | Where measured |
|---|---|---|
| load a library (parse, check, index) | ≤ 30 ms per 50 fragments, once per content hash | library gate, three runtimes |
| resolve and expand one use (cached library) | ≤ 0.2 ms | compile benchmark |
| compile overhead of fragments | a Sutra built from fragments compiles within 10% of the same panels written in place | per fragment (the eject proof gives the pair) |
| bind | **zero overhead**: a fragment's panels bind as in-place panels do; a column mapping is a rename in the plan | per fragment, on its sample at 2,000 and (where `window: ok`) 200,000 rows |
| a fragment's own `budget:` | declared: panels after expansion, `bind-ms` on the medium sample, payload KB | LG8 |
| the browser's library index | `core` ≤ 60 KB compressed; a static site ships only the fragments used | bundle check |
| limits | 40 parameters, 8 slots, 16 events per fragment; nesting 4; 500 fragments per library; a library ≤ 5 MB unpacked without large samples | `rachana lib verify` |

A fragment is never a reason for a screen to be slower than the same panels written by hand; the eject proof makes
that a test, not a hope.

## 19. Security

A fragment gets **no power a panel written in place does not have, and less**:

| Threat | Why it cannot happen |
|---|---|
| a library reads data the screen's author did not give it | hermetic scope (4.4): no `data:`, no table names, no `$`; a table arrives only as a parameter, and only its contract's columns are visible |
| a library reads data the **reader** may not see | the host's `TableProvider` and `Masker` answer as the reader before any fragment sees a row; a masked value is masked inside a fragment as anywhere (SPEC 4.9); `requires:` is answered by the host's `Permissions` |
| a library sends data out | no `url:`, no address built by an expression (D10), no `host` or `go` effect with a literal (6.2); `export` and `copy` are the host's to allow |
| a library acts for the reader (sends an order) | it can only emit an event; the screen's author wires it to a host action, and the host authorises the action per reader |
| a library injects markup or script | a library holds only data files (3.1); a Sutra has no HTML; name parameters are checked tokens, not text (4.2) |
| a library widens what a model-written screen may do | the model's limits (D29, the host's action list) apply to the expanded Sutra; expansion happens before checking |
| a tampered or substituted library | lock hash, manifest hashes, signature and the host's trust list (section 12) |
| a library that exhausts the machine | the screen's limits and the counted budgets (X45) apply after expansion; nesting and size limits (section 18) |
| a private fragment shadows a first-party one | names are qualified by library; first-party library names are reserved; two sources for one name with different hashes is an error (section 10) |
| a dependency changes under a library | a library's `depends` are resolved through the project's one lock file |

The checks run on the **expanded** Sutra with the host's own rules, so a host does not have to trust a library to
be safe, only to be useful. What a host must still review in a third-party library is what it reviews in any
Sutra: whether it shows the right thing.

## 20. A bank's private library

1. **Create.** `rachana lib new acme-risk --scope private` writes the folder, a manifest, a first fragment with its
   test, and the CI line (`rachana lib verify && rachana lib test`). It lives in the bank's own repository.
2. **Build on the shipped ones.** `depends: [core@1, finance@1]`. Most bank fragments are **variants**: a
   first-party fragment with the bank's formats, column mapping and slots fixed (`acme-risk/desk-var-tiles@1` uses
   `finance/var-tiles@1` and maps the bank's `scenario_pnl` table once, so no screen repeats the mapping).
3. **Use the bank's own platform words where needed.** A private library may declare `profiles: [<the host's>]`
   and then use that profile's kinds, sources and action names. It may write expression defaults that read the
   host's record. It is then bound to that host, which is the point.
4. **Contracts as the data agreement.** The bank writes its tables' contracts once (or accepts the first-party
   ones); `rachana lib check-host` in the data team's CI says when a table change would break a fragment.
5. **Publish inside.** `rachana lib pack` and the bank's existing artefact store (an npm, PyPI or Maven proxy, or
   a shared folder of `.rlib` files). Signed with the bank's key; the host's trust list names the bank's key and
   the first-party key and sets `require-signed: true`.
6. **Govern.** Review is a pull request on YAML. `status: draft | active | deprecated` and `replaces:` per
   fragment; where-used across the bank's Sutras (`rachana lib where-used`); the frozen-fragment gate (LG2) on the
   bank's own goldens means a library release cannot move a screen silently.
7. **Offer it.** The bank's library appears in Studio's palette and in the AI kit generated for that host, beside
   the first-party ones, under the bank's name.
8. **Never patched.** A bank does not edit a first-party library. It wraps (a variant), or ejects one use, or asks
   for a parameter.

## 21. Drishti's existing fragments: one mechanism, zero screen change

Drishti has the first generation of this design in production: a UI kit of 17 generic fragments
(`<name>.v<N>.fragment.yaml`, text parameters, a `doc:` block that already states a **type** for every parameter,
`emits`, `uses`, examples), pack-owned fragments addressed as `<pack>/<name>@<N>`, composites three deep, and an
equivalence test that proved each migrated screen byte-identical to its in-place layout. This design is that
mechanism, grown; it is not a replacement.

| Drishti today | In this design | Changes for Drishti |
|---|---|---|
| `fragment:` file, `version: N`, `%{param}` text | the **text form** (section 2), part of `rachana: 1` for the whole major | none |
| `doc.params` with `type`, `default`, `meaning` | the declarations the typed form promotes into `params:` and checks | none until Drishti chooses |
| `use: name@N` (the kit, unqualified) | an unqualified name, resolved by the host's `FragmentResolver` | none |
| `use: <pack>/<name>@N` | `use: <library>/<fragment>@<major>`: the pack is a library | none: the spelling is already this |
| `FragmentResolver` (the kit folder, pack folders, "the kit's name wins") | kept; Core's adapter can serve it from a `LibraryResolver` | none |
| `doc.uses`, pinned inner versions, depth 3 | `use:` inside a library, pinned, depth 4 | none (3 ≤ 4) |
| `emits` with a typed payload, `expects` | `emits` (6.1); `expects` becomes a value parameter | none until Drishti chooses |
| the equivalence test (old layout against fragments, full and masked reader) | the **eject proof** and the migrate proof, as product features | reused |

### 21.1 The steps, each optional and each provable

| Step | What happens | Why no screen changes |
|---|---|---|
| 0 | Nothing. Drishti stays at language level 1.0 with its `FragmentResolver` | The text form, unqualified names and `<pack>/<name>@N` are frozen for major 1; Drishti's shipped Sutras **and blocks** are in the customer corpus (CG3), so no Rachana merge can change what they expand to |
| 1 | **A manifest beside the files.** The kit folder gains `drishti-kit.rachana-lib.yaml` (`scope: private`, `profiles: [drishti]`); each pack that ships fragments gains one named after the pack. No fragment file and no Sutra is edited | A manifest is read only by the library tools (gallery, palette, AI cards, where-used). Expansion reads the same files through the same resolver. Proof: the corpus's compiled model texts and goldens are unchanged |
| 2 | **Typed in place.** `rachana migrate` rewrites a text-form fragment to the typed form at the **same major**: `params` take their types from `doc.params`; an expression default stays (allowed in a private library); `x: TENOR`-style field parameters stay parameters. Done in a Drishti release that raises its language level | The migrate rule ships with a proof case per fragment: every use in the corpus expands to the identical model before and after. Where a rule cannot prove identity for a file, it leaves that file in the text form, which remains valid for ever in major 1 |
| 3 | **Onto `core` and `finance`, screen by screen, only where identical.** For a kit fragment that has a first-party equivalent, `rachana migrate --to-library` offers the rewrite of each use and keeps it only if the ViewModel is byte-identical on the screen's goldens, for a full and a masked reader | A use whose output would differ in any byte (a panel option the first-party fragment does not write, a different default) simply stays on the kit fragment. There is no deadline and no flag day; the kit fragments are Drishti's for as long as one Sutra uses them |
| 4 | New Drishti screens are written against `core` and `finance` first, the kit second | New screens have no previous bytes to preserve |

### 21.2 Where each kit fragment goes

| Drishti kit fragment | Kind | First-party home ([catalogue](LIBRARY_CATALOGUE.md)) |
|---|---|---|
| `key-figure` | metric | `core/key-figure` |
| `field-list` | kv | `core/field-list` |
| `series-line` | line | `core/series-line` |
| `ranked-bars` | hbar | `core/ranked-bars` |
| `measure-table` | table | `core/measure-table` |
| `row-picker` | table with a click event | `core/row-picker`; the typed successor is `core/master-detail` |
| `event-timeline` | timeline | `core/event-timeline` |
| `explainer-note` | markdown | `core/note` |
| `region-map` | map | `geo/region-map` |
| `cashflow-ladder` | ladder | `finance/cashflow-ladder` |
| `curve-table`, `curve-overview` | table; composite | `finance/curve-table`, `finance/curve-overview` |
| `pnl-distribution` | histogram with markers | `finance/var-histogram` |
| `linked-entities`, `data-provenance`, `risk-links`, `source-footer` | profile kinds `links` and `provenance`, entity links | **stay in Drishti's private kit**: they draw platform concepts. `core/source-note` is the Core-pure part of the footer |

Drishti's pack-owned fragments (trade ticket, legs, payments, events, flow schedule, a trade table with columns as
a parameter; company and filing cards; headline lists; price bars) stay in their packs as private libraries. The
generic shape of each is in the catalogue (`finance/trade-ticket`, `finance/leg-table`, `finance/payment-schedule`,
`finance/price-bars`, `core/record-card`, `core/headline-list`), marked as extracted from a Drishti pattern, with
no Drishti field name in it.

## 22. Commands and problem codes

```text
rachana lib new <name> [--scope private]      a library skeleton with one fragment, its test and CI line
rachana lib add <name>@<major> [--from <registry>]   fetch, verify, unpack into rachana_libs/, write rachana.lock
rachana lib update [<name>]                   move within the major; prints the fragments and functions added
rachana lib restore                           fetch exactly what the lock names (the only other command that uses a network)
rachana lib list | info <name> | licences     what is installed; one library's surface; licences for a project
rachana lib verify <dir|.rlib>                file types, manifest, hashes, signature, exports not stale, limits
rachana lib build <dir> [--bundle]            regenerate exports, library.index.json, the browser bundle
rachana lib test <dir> [--paint]              T1 tests, goldens, budgets; in any runtime
rachana lib doc <dir>                         the pages and the gallery
rachana lib pack <dir> [--sign <key>]         the deterministic .rlib (and the npm, PyPI and Maven wrappers on release)
rachana lib contracts <name>                  the tables a host exposes to use the library
rachana lib check-host <name> --base <url>    each contract against a Data Protocol host's schemas
rachana lib where-used <fragment> <paths>     uses across Sutras and libraries, both levels
rachana lib eject | extract                   one use into panels; chosen panels into a fragment (7.3)
```

Problem codes (a new family, numbers provisional until they enter SPEC 6.2; `RCH-2048` keeps its meaning):

| Code | Meaning |
|---|---|
| `RCH-2048` | fragment not found; an unpinned use inside a library; a nesting cycle or depth (today's code) |
| `RCH-2110` | library not found by any resolver |
| `RCH-2111` | library not in the lock file, or the lock's constraint not met |
| `RCH-2112` | invalid library: manifest, file type, stale `exports`, dependency cycle, a limit |
| `RCH-2113` | integrity: hash mismatch, bad or missing signature, publisher not trusted |
| `RCH-2114` | unknown parameter, slot or event at a use (`expected` lists the valid names) |
| `RCH-2115` | required parameter or required table column missing |
| `RCH-2116` | parameter of the wrong type; a table that does not meet the contract (with the mapping fix) |
| `RCH-2117` | a fragment reaches outside its scope (reads `$`, names a table, a literal host action) |
| `RCH-2118` | slot content of a kind the slot does not accept, or over its `max` |
| `RCH-2119` | a library needs a profile, a level or a painter contract this host does not have (beside `RCH-2008`) |
| `RCH-2096` | advisory: these panels are a library fragment (model-written Sutras; a warning, with a fix) |

## 23. Roadmap and sizes

The owner's direction is that Rachana is complete before Drishti returns to the consumer side. Libraries need the
typed fragment (C1) and the library hook (X42), both planned for R2; this plan **pulls those two forward as the
first items after the split**, because every fragment depends on them and nothing in them depends on the rest of
R2. Fragments that need later additions (containers, the chart grammar, windowed tables) are built as those land:
the catalogue records what each one needs.

| Phase | Contents | Days |
|---|---|---|
| **LB0: the framework** | the manifest, `exports`, the library checker and limits (5); `LibraryResolver` with the five shipped resolvers and the `FragmentResolver` adapter (5, beside X42's 12); table parameters, contracts, column mapping and the fit check (7, beside C1's 8); name-class parameters as checked tokens, `state` and `selection` hand-over, outputs (4); the lock file, `add`, `update`, `restore`, `verify`, the deterministic `.rlib`, signing and the trust list (9); `lib test`, goldens, the gates LG1 to LG5 and LG8 (7); `lib doc`, `library.index.json` and the gallery generator (8); eject, extract, where-used and their proofs (5); the text-form to typed migrate rule and its proof harness (4) | **54** (+ C1 8, X42 12, T1 5 already planned) |
| **LB1: `core`, first wave** | the fragments of `core` that need only the baseline (today's panel kinds, C1, X42), with samples, tests, pages; this wave also proves the framework | catalogue section 11 |
| **LB2: `finance` and `economy`, first wave; the AI kit** | baseline fragments of both; fragment cards, `with` schemas, the two MCP tools, the composition evaluation set and gate LG9 (8); Studio's palette, fit and property sheet (10, in Studio's plan) | catalogue section 11 + **18** |
| **LB3: with R2's structure** | the fragments that need containers, overlays, state, effects, selections, grid depth (L1, L2, S1, I1 to I3, V2, V3): master-detail, wizard shell, the blotters, the order ticket, filter bars | catalogue section 11 |
| **LB4: with the chart grammar and scale** | the fragments that need V1, D2, V4, V5 and the later EL libraries (X23 to X28): layered charts, windowed blotters, treemaps, map layers, `genomics` plots | catalogue section 11 |
| **LB5: distribution** | the npm, PyPI and Maven wrappers and public registries (with R3); the library licences in force; LG6 and LG7 on every release | **6** |

The fragment counts and day totals per library and wave are in [LIBRARY_CATALOGUE](LIBRARY_CATALOGUE.md) section
11, computed from the catalogue itself. Per fragment the estimate covers the fragment, its document, two or more
examples, synthetic samples, tests and goldens: 0.5 day for a single-panel fragment, 1 day for a composite or one
with slots and events, 2 days for one with state, overlays or a flow.

What this needs from elsewhere: C1, X42, T1, X8 (the type language) and X13 (lambdas, for `fn` parameters) before
LB1; the Views painter of Drishti Elements for the gallery; the events design for `on:` at a use (the effect list,
I1); Studio for the palette. What it gives back: the gallery and the AI kit's examples stop being hand-written
Sutras and become uses of tested fragments.
