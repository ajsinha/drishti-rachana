<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary. All rights reserved. -->
# Writing Sutras

Everything about writing the screen itself: the language, the libraries of fragments you assemble screens from, and Studio, the visual editor.
The normative text of every rule is the specification ([SPEC.md](../spec/1/SPEC.md)); how to run, serve and embed what you wrote is [IN_YOUR_APP.md](IN_YOUR_APP.md).

## The Rachana language (level 1.2)

A **Sutra** is one screen written as a short YAML file (`*.sutra.yaml`). It says what the screen shows and where each value comes
from; it holds no code and no pixels. This guide walks through the language at level 1.2. The normative text is the
specification, [SPEC.md](../spec/1/SPEC.md); each section below names the part of it that is the one home of the details.

| One home | For |
|---|---|
| [SPEC.md](../spec/1/SPEC.md) | every key, rule and problem code |
| [functions.yaml](../spec/1/functions.yaml) | every Core function: signature, types, null and error behaviour, examples |
| [formats.yaml](../spec/1/formats.yaml) | the format names (`amount0`, `pct2`, `date`, ...) |
| [rachana.lang.json](../spec/1/rachana.lang.json), [sutra.schema.json](../spec/1/sutra.schema.json) | the generated language definition and JSON Schema (editors and AI tools read these) |
| [rachana-el.ebnf](../spec/1/rachana-el.ebnf) | the expression grammar |
| [TRANSLATION.md](../spec/1/SPEC.md#translation-sutra--data--viewmodel-rachana-1), [VIEWMODEL.md](../spec/1/SPEC.md#the-viewmodel-contract-1x) | what a Sutra compiles to |

### 1. A Sutra

```yaml
rachana: 1.2                       # the language level this file is written at
sutra: sales-overview              # its id
version: 1
title: { id: "'Sales overview'" }
data:
  sales: { table: sales, key: [region, product, month] }
panels:
  - { id: revenue, kind: metric, label: Revenue, value: "sum($.sales, 'revenue')", fmt: amount0, span: 3 }
  - { id: by-region, kind: hbar, title: Revenue by region, rows: "group($.sales, 'region', 'revenue')",
      label: label, value: value, fmt: amount0, span: 9 }
```

The file is from [examples/static-sales](../examples/static-sales/overview.sutra.yaml), trimmed. Every Sutra has the same parts:
the `rachana:` level, an id, a `title`, optional `data:` entries, optional `controls`, and `panels` laid out on a 12-column grid
(`span`). SPEC 2.1 and 2.2 list every top-level key.

#### The language level

The `rachana:` key is the one place a file's level is written: `rachana: 1` (or `1.0`) is level 1.0, `rachana: 1.2` is 1.2. Every
tool (lint, build, preview, Studio, `<rachana-view>`, the server) reads each file at the level it declares, never above the tool's own:
a level above the tool's is `RCH-2008`, an unknown minor `RCH-2009`. No command takes a level flag. `rachana new` writes the newest
level; a Sutra is read at the level it declares and nothing rewrites it; Rachana has no migrator. Within major 1 a later level only adds
(SPEC 7.2), so a 1.0 file reads the same on a 1.2 host.

### 2. Panels

A panel is one tile of the screen: `{id, kind, ...options, span}`. The kinds are `metric`, `gauge`, `kv`, `status`, `markdown`, `table`,
`pivot`, `ladder`, `hbar`, `line`, `area`, `scatter`, `histogram`, `waterfall`, `candlestick`, `timeline`, `surface`, `graph`, `map`
and `tabs` (the generated list with the options each takes: `panelKinds` and `panelOptions` in
[rachana.lang.json](../spec/1/rachana.lang.json)). A `table` takes `rows` and `columns` (`{label, bind, fmt, total}`); a chart takes
`rows` and the fields it plots. SPEC 2.3 has the rules; TRANSLATION.md shows what each kind becomes in the ViewModel.

Values are written in four ways, fixed per key (SPEC 3): an **expression** (`value: "sum($.sales, 'revenue')"`), a **template**
(`caption: "${size($.sales)} sales rows"`), a **name** (`fmt: amount0`, `tone: sign`) or plain **text** (`label: Revenue`).

### 3. Data

`data:` names the inputs a screen reads, each read in expressions as `$.<name>` (SPEC 2.4):

| Entry | Meaning |
|---|---|
| `{table: sales, key: [...]}` | a table the host serves over the [Data Protocol](../spec/1/PROTOCOL.md) (in static mode a file under `data/`) |
| `{url: ...}` | a file or endpoint read whole (JSON, CSV, NDJSON, Arrow), at an address the host allows |
| `{rows: [...]}` | rows written in the file |
| `{record: {...}}` | one record written in the file |

Common keys: `key`, `fields`, `where` (pushed to the host as a filter tree), `limit`, `refresh`, `live: true` (follow the table's
changes) and `mode: server` (paged from the host, for large and [live](IN_YOUR_APP.md#live-tables-editing-and-approvals) tables). A table the host does not have fails the panels that read it with `RCH-2070`; it is never read as
no rows.

### 4. Expressions (Rachana-EL)

Rachana-EL is a small, side-effect-free expression language (SPEC 4; grammar in `rachana-el.ebnf`):

- **Paths and filters**: `$.sales`, `$.sales[?@.month == '2026-06-01']`, `@.revenue` (the current row), `^` (its parent),
  `#index` (its place).
- **Operators**: arithmetic, comparison, `and`/`or`/`not`, `?:`, `??`, `in`, the pipe `a |> f(b)`.
- **Numbers** (SPEC 4.16): integers and decimals are exact (decimal128, 34 digits, half to even); `avg` over decimals is the exact
  sum divided; a double among the operands gives a double.
- **Functions**: 200+ Core functions in logic, math, text, dates, lists, aggregates, statistics and finance on given flows
  (SPEC 5, the catalogue in `functions.yaml`). Lambdas: `map($.rows, lambda r: r.qty * r.price)`.
- **Controls** are read as `ctl.<id>`; another panel's event as `@<panel>.<event>.<field>`.
- **Checked mode** (SPEC 4.14) types every expression at compile time, so a misspelt field or a wrong argument is a problem in
  `rachana lint`, not a blank tile.

### 5. Declared names: `let` and `functions`

```yaml
let:
  total: "sum($.sales, 'revenue')"
functions:
  share: { params: { part: number, whole: number }, is: "whole == 0 ? 0 : part / whole", doc: Part of a whole }
panels:
  - { id: north, kind: metric, label: North share, value: "share(sum($.sales[?@.region == 'North'], 'revenue'), total)", fmt: pct0 }
```

A `let` is a value computed once per bind; a function body reads only its parameters and other functions. Both exist at compile
time only (SPEC 2.7). A library may publish functions, called as `<library>.<name>(...)`.

### 6. State, events and effects

A screen may hold **state** (`state: {desk: {type: text}}`, read as `state.desk`) and change it with **effects** (SPEC 2.8):

```yaml
state:
  region: { type: text }
panels:
  - id: by-region
    kind: hbar
    rows: "group($.sales, 'region', 'revenue')"
    label: label
    value: value
    on:
      bar-click: [ { set: { state: region, to: "@.label" } } ]
  - { id: detail, kind: table, rows: "$.sales[?@.region == state.region]", columns: [ { label: Product, bind: "@.product" } ] }
```

Triggers are `row-click`, `bar-click` and `point-click`; effects are `set`, `toggle`, `clear`, `notify` and `host` (a host action
handed to the page, never run by Rachana), each with an optional `when:`. Panels also take header `actions:` (buttons with
`confirm` and `requires`), controls take `on: {change: [...]}`, and a `kind: form` panel runs its `submit` effects only after its own
checks pass.

### 7. Fragments, and fragments as functions

A **fragment** is a reusable group of panels, used in a Sutra's `panels` as `{use: <library>/<name>@<major>, with: {...}}`. A
**typed fragment** (level 1.2) declares each parameter with a type, and behaves like a function: it reads only its arguments, never
the caller's screen (`RCH-2117`), and returns events and outputs. The fragment is written once with its own local names; each use
binds the caller's data and column names to them at compile time.

The fragment `top-rows` of a small library `shop` (`rachana_libs/shop/fragments/top-rows.v1.fragment.yaml`; the whole library is
built in the tutorial [Build and use a library](TUTORIAL.md#part-2-build-and-use-a-library)):

```yaml
rachana: 1.2
fragment: top-rows
version: 1
doc:
  summary: The largest rows of a table by one column.
  category: tables
params:
  rows:  { type: table, required: true, doc: The rows, columns: { name: { type: text, doc: "The row's name" }, value: { type: number, doc: The figure } } }
  limit: { type: integer, default: 5, min: 1, max: 50, doc: How many rows }
  fmt:   { type: format, default: amount0, doc: How the figure is written }
emits:
  - { name: pick, payload: { name: text }, doc: A row was chosen }
panels:
  - id: list
    kind: table
    rows: "take(sort(arg.rows, @.value, true), arg.limit)"
    columns: [ { label: Name, bind: "@.name" }, { label: Value, bind: "@.value", fmt: "%{fmt}" } ]
    emits: [ { name: pick, on: row-click, payload: { name: "@.name" } } ]
```

Used from a Sutra whose table names its columns differently:

```yaml
rachana: 1.2
sutra: best-products
version: 1
libraries: [shop@1]
title: { id: "'Best products'" }
data:
  sales: { table: sales, key: [region, product, month] }
panels:
  - use: shop/top-rows@1
    as: best
    with:
      rows: { from: $.sales, columns: { name: product, value: revenue } }   # local column: caller column
      limit: 8
  - { id: picked, kind: markdown, text: "Picked: ${@best.pick.name}" }
```

What the compiler does at the use (SPEC 2.5, "Typed fragments" and "Fragments are functions"):

- **Parameters** are values (`text`, `number`, `integer`, `decimal`, `boolean`, `date`, `datetime`, `money`, `list<T>`,
  `record{...}`, `fn(T) -> U` written as a lambda), **tables** (`type: table` with `columns:` or a published `contract:`) and
  **names** (`format`, `tone`, `id`, `enum`, ...) placed as `%{name}`. Each is `required`, has a `default`, or is `optional`.
- **Column mapping**: `rows: $.t` binds a table whose columns already have the local names; `rows: {from: $.t, columns: {...}}`
  renames local columns to the caller's in every `bind`, field-name option, `@.<column>` and column parameter that reads the table.
  A column the mapping cannot reach is `RCH-2121`, never a silent read; a missing required column is `RCH-2115`, a wrong type
  `RCH-2116`.
- **Scope**: `as: best` prefixes every id the fragment brings (`best-list`). Events come back as `@best.pick.name`; `on:` at the
  use turns them into state writes or control settings.
- **Private state and outputs**: a fragment's own `state:` is private to each use; `outputs:` names the parts the screen may read
  as `state.<as>.<name>`. Nested uses are scoped twice (`a-x-<id>`), so two uses never collide.
- **Slots**: `slots: {name: {accepts: panels, max}}` lets the caller place its own panels inside the fragment.

Fragments come from [libraries](WRITING_SUTRAS.md#libraries-and-rachana-lib); a host may also resolve unqualified `name@N` fragments of its own. A text-form fragment (untyped, `%{param}` as
text) is still read; a file is wholly one form or the other.

### 8. Problems

Every mistake is a **problem** with a code (`RCH-xxxx`), a message, a severity and a source location (SPEC 6), all reported at once.
`rachana lint` prints them as text, JSON or SARIF; Studio shows them as you type. Run-time states (no rows, a failed table) are shown
on the panel and are not problems (SPEC 6.4).

## Libraries and `rachana lib`

A **library** is a versioned, signed folder of [typed fragments](WRITING_SUTRAS.md#7-fragments-and-fragments-as-functions) (and optionally
functions) that any Sutra can use. Screens become assembly: a Sutra names the library, uses its fragments and maps its own data onto
them. The normative rules are SPEC 2.5 "Libraries" ([SPEC.md](../spec/1/SPEC.md)); the libraries shipped with Rachana and
every fragment's parameters are in their generated pages: [core](../libraries/core/README.md) and
[finance](../libraries/finance/README.md) (index: [README.md](../README.md#rachana-fragment-libraries)).

### 1. Using a library in a Sutra

```yaml
rachana: 1.2
sutra: orders
version: 1
libraries: [core@1]                 # each library and the major the Sutra is written against
title: { id: "'Orders'" }
data:
  orders: { rows: [ { ORDER_ID: A1, AMT_USD: 120 }, { ORDER_ID: A2, AMT_USD: 80 } ] }
panels:
  - use: core/data-table@1          # <library>/<fragment>@<major>; the major is required
    with:
      rows: $.orders
      columns: [ { label: Order, bind: "@.ORDER_ID" }, { label: Amount, bind: "@.AMT_USD", fmt: amount0, total: true } ]
```

`use: core@1/data-table@1` (the long form) works without a `libraries:` key. A library function is called as
`<library>.<name>(...)`. The compile result lists every library it was built from with its exact version and SHA-384.

### 2. Where libraries are found

A host finds libraries **on disk only, never on the network**, in this order of sources, all of which are read:

| Source | Used by |
|---|---|
| the libraries bundled with Rachana (`core`, `finance`) | every tool |
| `<project>/rachana_libs/` (library folders or `.rlib` files) | `lint`, `build`, `studio`, the server (`project.dir`) |
| `--libs <path>[,<path>]` | `rachana lint`, `build`, `studio`; `python -m drishti_rachana lint --libs` |
| `RACHANA_LIBS=<path>[:<path>]` | the same commands |
| `libraries:` in `rachana-server.yaml` | the [server](IN_YOUR_APP.md#the-rachana-server) |

Each path is a library folder, an `.rlib`, or a folder of them. A library name found twice with different contents is refused with
a problem (rename or remove one), never silently shadowed. A project's `rachana.lock` pins each library's exact version and the
SHA-384 of its canonical archive; a library not in the lock, or of another major, is `RCH-2111`; a hash or signature that does not
match is `RCH-2113`.

### 3. What a library is

```text
mylib/
  mylib.rachana-lib.yaml       the manifest: rachana, library, version (semver), title, summary, publisher, licence,
                               scope (public | private), profiles, depends, needs, categories; exports (generated)
  fragments/<name>.v<N>.fragment.yaml    one typed fragment per file and major
  functions/<file>.yaml        optional library functions
  contracts/<name>.json        optional published table contracts (Data Protocol schema form)
  examples/<name>.sutra.yaml   one example Sutra per fragment
  goldens/                     the ViewModel snapshot of each example
  MANIFEST.sha384              the SHA-384 of every file (generated)
  SIGNATURE                    optional Ed25519 signatures of MANIFEST.sha384
  README.md                    the library's page (generated)
```

Inside a library every use is `<library>/<fragment>@N` of itself or of a library it `depends` on. A typed fragment of a **public**
library reads only its parameters (`RCH-2117` for `$`, `ctl.`, `state.`, `source:`, `data:`, `url:`). Codes: `RCH-2110` not found,
`RCH-2112` invalid library.

The `.rlib` archive is a ZIP written deterministically (sorted entries, stored, a fixed time), so a folder and its archive have one
identity and one hash.

### 4. `rachana lib`

One implementation, the Java `LibraryTool`, reached from every launcher: `node js/packages/rachana-cli/bin/rachana.js lib ...` (with
`RACHANA_JAR` or `RACHANA_CLASSPATH` set; see [CLI and SDKs](IN_YOUR_APP.md#the-java-launcher)) or
`java -cp <jars> com.ash.rachana.cli.Main lib ...`.

| Command | What it does |
|---|---|
| `lib build DIR` | regenerates the manifest's `exports` and `MANIFEST.sha384` from the files |
| `lib keygen` | prints a new Ed25519 key pair (public key, private seed) |
| `lib sign DIR PUBLIC-KEY PRIVATE-SEED` | adds a signature line over `MANIFEST.sha384` to `SIGNATURE` |
| `lib pack DIR [--out FOLDER] [--require-signed] [--publisher KEY]...` | writes `<name>-<version>.rlib` |
| `lib verify DIR-OR-RLIB [--require-signed] [--publisher KEY]...` | checks every rule a host checks when it loads the library |
| `lib lock DIR-OR-RLIB... [--lock rachana.lock]` | adds or updates each library's entry in the lock file |
| `lib gate DIR [--strict]` | the quality gates: LG1 library valid, LG2 frozen fragments unchanged, LG3 definition of done (docs, examples), LG4 level replay, LG5 interface append-only, LG8 budgets; exit 1 when one fails (`--strict` also fails on the LG3 notes, such as a missing second example) |
| `lib freeze DIR` | records the released baseline (`FROZEN.sha384`, `released/`) that LG2 and LG5 check later versions against; run `lib build` after |
| `lib contracts DIR-OR-RLIB [--json]` | the tables a host must serve to use the library |
| `lib check-host DIR-OR-RLIB --base URL [--table CONTRACT=TABLE]... [--json]` | checks each contract against a running host's table schemas, column by column |

Exit status: 0 done, 1 the library does not verify (or a host column breaks a fragment), 2 usage.

### 5. Building the bundled libraries

```bash
cd java && ./mvnw clean package -DskipTests && cd ..
python3 tools/libraries/build.py              # docs, build, test against goldens, gates, sign (development key), pack, verify
python3 tools/libraries/build.py --update-goldens core   # after an intended change of a fragment's output
```

`build.py` writes each library's `README.md` from its fragment files, so the library pages are never edited by hand. `--gallery`
also builds a gallery site of every fragment with Rachana itself. The same check runs in the Maven build (`LibrariesCheckTest`).

### 6. Your own library

Write the fragments and examples, then `rachana lib build`, `rachana lib gate`, sign and pack. Put the folder or the `.rlib` where your
hosts read libraries (section 2); nothing in Rachana is modified or rebuilt. A step-by-step run is in the tutorial
[Build and use a library](TUTORIAL.md#part-2-build-and-use-a-library). In Studio, every fragment of every library found appears in the palette with a
parameter form ([Studio](WRITING_SUTRAS.md#rachana-studio)).

## Rachana Studio

Studio is the visual editor of a folder of Sutras: a palette of library fragments, the live preview, the Sutra source checked as you
type, a parameter form that inserts a fragment, and the unsaved changes as a diff. It edits the same YAML files every other tool
reads; it adds nothing of its own to them.

### 1. Open it

```bash
node js/packages/rachana-cli/bin/rachana.js studio examples/static-sales --port 4500
node js/packages/rachana-cli/bin/rachana.js studio examples/server-demo/project/sutras --data examples/server-demo/project/data --port 4501
```

`rachana studio [folder] [--libs <path>,...] [--data <dir>] [--port <n>]`. It listens on `127.0.0.1` only and prints its address and
the libraries it found (`studio http://127.0.0.1:4500/  (core 1.0.0, finance 1.0.0; Ctrl+C to stop)`). It needs the runtime bundles
(`python3 tools/js/build_js.py`). In the IDE: the launchers **Rachana Studio** and **Rachana Studio (server-demo)**.

| Option | Meaning |
|---|---|
| `folder` | the folder of `*.sutra.yaml` files (default: the current folder) |
| `--data <dir>` | the tables the preview reads (`<name>.csv`, `.json`, ...); default: the folder's `data/` |
| `--libs <path>,...` | more libraries besides the bundled ones, `<folder>/rachana_libs` and `RACHANA_LIBS` ([Libraries](WRITING_SUTRAS.md#2-where-libraries-are-found)) |
| `--port <n>` | the port (default: any free port) |

### 2. The screen

| Area | What it does |
|---|---|
| **Sutra** (top) | opens one of the folder's Sutras |
| **Fragments** (left) | every fragment of every library found, by category, with a search box |
| **Live preview** (middle) | the real `<rachana-view>` painting the Sutra over the folder's data, redrawn as you edit |
| **Sutra source** (right) | the YAML in a code editor; the compiler checks it at the level its `rachana:` key declares, problems marked on their lines |
| **Fragment properties** (right) | the chosen fragment's parameters as a form: typed fields, the tables of the folder's data files, and a column mapping that proposes your columns for the fragment's; **Insert into Sutra** adds the `use:` block |
| **Changes** (right) | the unsaved changes as a diff |
| **Save** | writes the Sutra back into the folder (Ctrl+S); nothing else is ever written |

Save writes only a `*.sutra.yaml` path inside the opened folder. The assistant bar ("Describe a screen...") is reserved for AI
authoring and does not generate yet.

### 3. A first edit

The tutorial [Your first dashboard](TUTORIAL.md#part-1-your-first-dashboard) (step 5) inserts a library fragment with Studio and shows the YAML
it writes; [Build and use a library](TUTORIAL.md#part-2-build-and-use-a-library) shows your own library in the palette.
