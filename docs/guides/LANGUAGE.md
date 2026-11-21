<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary. All rights reserved. -->
# The Rachana language (level 1.2)

A **Sutra** is one screen written as a short YAML file (`*.sutra.yaml`). It says what the screen shows and where each value comes
from; it holds no code and no pixels. This guide walks through the language at level 1.2. The normative text is the
specification, [spec/1/SPEC.md](../../spec/1/SPEC.md); each section below names the part of it that is the one home of the details.

| One home | For |
|---|---|
| [SPEC.md](../../spec/1/SPEC.md) | every key, rule and problem code |
| [functions.yaml](../../spec/1/functions.yaml) | every Core function: signature, types, null and error behaviour, examples |
| [formats.yaml](../../spec/1/formats.yaml) | the format names (`amount0`, `pct2`, `date`, ...) |
| [rachana.lang.json](../../spec/1/rachana.lang.json), [sutra.schema.json](../../spec/1/sutra.schema.json) | the generated language definition and JSON Schema (editors and AI tools read these) |
| [rachana-el.ebnf](../../spec/1/rachana-el.ebnf) | the expression grammar |
| [TRANSLATION.md](../../spec/1/TRANSLATION.md), [VIEWMODEL.md](../../spec/1/VIEWMODEL.md) | what a Sutra compiles to |

## 1. A Sutra

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

The file is from [examples/static-sales](../../examples/static-sales/overview.sutra.yaml), trimmed. Every Sutra has the same parts:
the `rachana:` level, an id, a `title`, optional `data:` entries, optional `controls`, and `panels` laid out on a 12-column grid
(`span`). SPEC 2.1 and 2.2 list every top-level key.

### The language level

The `rachana:` key is the one place a file's level is written: `rachana: 1` (or `1.0`) is level 1.0, `rachana: 1.2` is 1.2. Every
tool (lint, build, preview, Studio, `<rachana-view>`, the server) reads each file at the level it declares, never above the tool's own:
a level above the tool's is `RCH-2008`, an unknown minor `RCH-2009`. No command takes a level flag. `rachana new` writes the newest
level; [rachana upgrade](UPGRADE.md) moves a 1.0 file to 1.2 without changing its meaning. Within major 1 a later level only adds
(SPEC 7.2), so a 1.0 file reads the same on a 1.2 host.

## 2. Panels

A panel is one tile of the screen: `{id, kind, ...options, span}`. The kinds are `metric`, `gauge`, `kv`, `status`, `markdown`, `table`,
`pivot`, `ladder`, `hbar`, `line`, `area`, `scatter`, `histogram`, `waterfall`, `candlestick`, `timeline`, `surface`, `graph`, `map`
and `tabs` (the generated list with the options each takes: `panelKinds` and `panelOptions` in
[rachana.lang.json](../../spec/1/rachana.lang.json)). A `table` takes `rows` and `columns` (`{label, bind, fmt, total}`); a chart takes
`rows` and the fields it plots. SPEC 2.3 has the rules; TRANSLATION.md shows what each kind becomes in the ViewModel.

Values are written in four ways, fixed per key (SPEC 3): an **expression** (`value: "sum($.sales, 'revenue')"`), a **template**
(`caption: "${size($.sales)} sales rows"`), a **name** (`fmt: amount0`, `tone: sign`) or plain **text** (`label: Revenue`).

## 3. Data

`data:` names the inputs a screen reads, each read in expressions as `$.<name>` (SPEC 2.4):

| Entry | Meaning |
|---|---|
| `{table: sales, key: [...]}` | a table the host serves over the [Data Protocol](DATA_PROTOCOL.md) (in static mode a file under `data/`) |
| `{url: ...}` | a file or endpoint read whole (JSON, CSV, NDJSON, Arrow), at an address the host allows |
| `{rows: [...]}` | rows written in the file |
| `{record: {...}}` | one record written in the file |

Common keys: `key`, `fields`, `where` (pushed to the host as a filter tree), `limit`, `refresh`, `live: true` (follow the table's
changes) and `mode: server` (paged from the host, for large and [live](LIVE_TABLES.md) tables). A table the host does not have fails the panels that read it with `RCH-2070`; it is never read as
no rows.

## 4. Expressions (Rachana-EL)

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

## 5. Declared names: `let` and `functions`

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

## 6. State, events and effects

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

## 7. Fragments, and fragments as functions

A **fragment** is a reusable group of panels, used in a Sutra's `panels` as `{use: <library>/<name>@<major>, with: {...}}`. A
**typed fragment** (level 1.2) declares each parameter with a type, and behaves like a function: it reads only its arguments, never
the caller's screen (`RCH-2117`), and returns events and outputs. The fragment is written once with its own local names; each use
binds the caller's data and column names to them at compile time.

The fragment `top-rows` of a small library `shop` (`rachana_libs/shop/fragments/top-rows.v1.fragment.yaml`; the whole library is
built in the tutorial [Build and use a library](../tutorials/LIBRARY.md)):

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

Fragments come from [libraries](LIBRARIES.md); a host may also resolve unqualified `name@N` fragments of its own. A text-form fragment (untyped, `%{param}` as
text) is still read; a file is wholly one form or the other.

## 8. Problems

Every mistake is a **problem** with a code (`RCH-xxxx`), a message, a severity and a source location (SPEC 6), all reported at once.
`rachana lint` prints them as text, JSON or SARIF; Studio shows them as you type. Run-time states (no rows, a failed table) are shown
on the panel and are not problems (SPEC 6.4).
