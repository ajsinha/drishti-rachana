<!-- Copyright (c) 2026 Ashutosh Sinha. Specification licence: ../../LICENSE-SPEC.md (draft, not yet in force). -->
# The Rachana specification, `rachana: 1`

**Status: draft 1 (2026-11-17), with the decisions for 1.0 taken on 2026-11-19 (EL_EXPANSION XD2 to XD4: the reservations
of 4.15, the numbers of 4.16, text by code point in 4.13).** Normative once the owner approves it and the first conformance
suite is cut. Until then it describes the language as the reference implementation (Drishti's `compute/drishti-rachana`)
behaves today, plus the settled additions of the design ([../../docs/design/SPEC.md](../../docs/design/SPEC.md),
[CORE_AND_PROFILES](../../docs/design/CORE_AND_PROFILES.md), [DECISIONS](../../docs/design/DECISIONS.md)).

Companion files in this folder:

| File | Normative for | State |
|---|---|---|
| this text | meaning: document model, evaluation, functions, errors, versions | draft 1 |
| [rachana-el.ebnf](rachana-el.ebnf) | expression syntax (60 rules, ISO 14977) | draft 1 |
| [TRANSLATION.md](TRANSLATION.md) | Sutra + data → ViewModel | draft 1 |
| [functions.yaml](functions.yaml) | every Core function: signature, types, nulls, errors, examples | draft, completed in R0 step H5 |
| [formats.yaml](formats.yaml) | the Core format names and how each prints (2.6) | draft |
| [reserved.yaml](reserved.yaml) | the tokens and names held back for later minors, and the profile names to be promoted (4.15) | draft |
| `rachana.lang.json`, `sutra.schema.json` | every key, kind, option; document structure | generated, R0 step 0c |
| [viewmodel.schema.json](viewmodel.schema.json) | the ViewModel | draft by reading; generated in R0 step H4 (decision D6) |
| [data-protocol/PROTOCOL.md](data-protocol/PROTOCOL.md) and its JSON Schemas | the Data Protocol v1: tables, rows, query and filter tree, live patches, errors, bridge | draft 1 |

## 0. Conventions

- **MUST**, **MUST NOT**, **SHOULD**, **MAY** are used as in RFC 2119.
- An **implementation** is a compiler, binder or linter that claims to implement `rachana: 1`.
- **[today]** marks behaviour of the reference implementation now; **[planned]** marks a settled addition not yet
  built; it gets its `since` minor version when it ships.
- **[CS]** marks a rule whose exact output the conformance suite (`conformance/1/`) will pin. Where this text and the
  suite disagree, that is a defect in one of them and a release blocker; until it is fixed, **the suite wins**.
- Examples use `→` for "evaluates to". `empty` is the empty value (section 4.2).

## 1. Scope

`rachana: 1` defines **Sutra Core**: a YAML document that describes one screen (title, headline strip, controls,
panels, layout, keys, forms) over named data, and **Rachana-EL**, the expression language its values are written in.

A **profile** is a named set of additions a host registers (extra keys, panel kinds, source forms, functions, formats,
problem codes). A profile MUST NOT change the meaning of anything in Core and MUST NOT make a valid Core Sutra invalid.
`drishti` is the first profile; what belongs to it is listed where it matters (sections 2.6 and 5.11).

## 2. Document model

### 2.1 The file

1. A Sutra is **one YAML 1.2 document** in one UTF-8 file named `<name>.sutra.yaml`. The older form
   `<name>.v<N>.sutra.yaml` MUST still be read.
2. YAML is read **strictly**: a key repeated in one mapping, a second document after `---`, and any tag (`!x`) are
   errors (`RCH-2033`). Anchors and aliases are allowed. Every node keeps its line and column.
3. Every string value that holds an expression or template is a YAML string. Single quotes inside a double-quoted YAML
   string are the usual way to write Rachana text: `value: "sum($.sales, 'revenue')"`.
4. An unknown key is an error with its position (`RCH-2011`; `RCH-2023` for a panel option), never ignored. A key that
   belongs to a profile the implementation does not have is `RCH-2060` with words that name the profile, not "unknown".

```yaml
rachana: 1
sutra: regional-sales
version: 1
title: { id: "'Regional sales'" }
data:
  sales: { table: sales, key: [region, month] }
panels:
  - { id: by-region, kind: hbar, rows: "group($.sales, 'region', 'revenue')", label: label, value: value, fmt: amount0 }
```

### 2.2 Top-level keys of Sutra Core

| Key | Req. | Type | Default | Meaning | State |
|---|---|---|---|---|---|
| `rachana` | yes | integer | | language major; MUST be `1` (`RCH-2009` otherwise) | today |
| `sutra` | yes | text matching `[a-z][a-z0-9-]{1,63}` | | the screen's name (`RCH-2020`) | today |
| `version` | today yes | integer 1..2³¹−1 | `1` (1.1) | `name@version` identifies a Sutra | optional: planned (D11) |
| `profiles` | | list of text | `[core]` | profiles the file needs; the checker verifies the claim (`RCH-2061`, `RCH-2062`) | planned |
| `description`, `notes` | | text | | prose for people; never used to build the view (`RCH-2012` if not text) | today |
| `doc` | | mapping | | docstring: `summary`, `params`, `emits`, `examples`, `since` (`RCH-2055`) | today |
| `title` | | mapping `{pill?, id, with?}`; text (1.1) | the name | heading (TRANSLATION 2.2) | text form planned (D11) |
| `strip` | | list (≤ 8) of `{label?, bind, fmt?, tone?, emphasis?}` | `[]` | headline figures | today |
| `controls` | | list (≤ 12) | `[]` | what the reader sets; read as `ctl.<id>` | today |
| `data` | | mapping name → source | `{}` | the screen's tables and records, each a field of `$` (2.4) | planned (D10) |
| `panels` | | list of panels and `{use: …}` entries | `[]` | the body | today |
| `layout` | | mapping | panels flow | 12-column grid: `areas`, `columns`, `split`, `tabs`, `sections`, `sticky`, `arrange`, `phone`, `tablet` | today |
| `phone` | | mapping | | phone order, default tier, summary | today |
| `keys` | | mapping `F1`..`F12` → action | `{}` | function keys (TRANSLATION 2.5) | today |
| `refresh` | | seconds, or `{every, user-can-pause}` | none | re-read interval for top-level panels without their own | today |

`match` and `domain` exist today but belong to the `drishti` profile (decision D12). A Core implementation answers
them with `RCH-2060` ("'match' belongs to the profile of a data platform; a Core screen reads its data from 'data:'").

### 2.3 Panels

A panel is a mapping `{id, kind, …}`. `id` is unique in the Sutra (`RCH-2024`). Keys every panel accepts [today]:
`title` (template), `description`, `key` (`F1`..`F12`), `code`, `area` (`main` | `right`), `order`, `section`,
`span` (1..12), `height` (1..24) (`RCH-2030`), `collapsible`, `phone`, `refresh`, `when`, `where`, `on-select`,
`source`, `emits`, `waits`, `children`, `columns`, `form`, `infer`.

Core has **twenty panel kinds**. Each has required and accepted options; any other option is `RCH-2023` with the
accepted ones in `expected`; a missing required option is `RCH-2022`.

| Kind | Requires | Accepts |
|---|---|---|
| `kv` | | `rows`, `columns`, `fields` |
| `table` | `rows` | `totalLabel`, `limit`, `moreLabel`, `link`, `highlight`, `search`, `pivot`, `children`, `expand`, `levels`, `crumb`, `root`, `empty` |
| `ladder` | `rows` | as `table` without `limit`, `moreLabel`, `link` |
| `tabs` | `each` | `tabTitle`, `layout`, `body` or `panels` |
| `line` | | `rows`, `x`, `y`, `mark`, `footer`, `unit`, `fmt`, `step` |
| `area` | `rows` | `x`, `series`, `limit`, `limitLabel`, `unit` |
| `hbar` | `rows` | `label`, `value`, `fmt`, `tone`, `shape` (`bars`, `donut`) |
| `metric` | `value` | `label`, `fmt`, `tone`, `delta`, `deltaFmt`, `deltaTone`, `unit`, `caption` |
| `gauge` | `value` | `max`, `label`, `fmt` |
| `status` | | `fields` |
| `markdown` | `text` | |
| `surface` | `rows`, `y` | `fmt`, `unit`, `view` |
| `waterfall` | `rows` | `label`, `value`, `total`, `sum`, `fmt`, `unit`, `colors` |
| `histogram` | `rows` | `value`, `bins`, `markers`, `fmt`, `unit` |
| `scatter` | `rows`, `x`, `y` | `size`, `label`, `group`, `fmt`, `xFmt`, `xLabel`, `yLabel` |
| `candlestick` | `rows` | `x`, `open`, `high`, `low`, `close`, `volume`, `fmt`, `unit` |
| `graph` | `nodes` | `edges`, `label`, `group`, `layout` |
| `timeline` | `rows` | `date`, `label`, `detail`, `status`, `tone` |
| `pivot` | `rows`, `by`, `across` | `value`, `agg`, `fmt`, `tone`, `heat`, `totals`, `expand` |
| `map` | | `highlight`, `region`, `link`, `shade`, `fmt`, `unit` |

`links` and `provenance` are `drishti` profile kinds. Which options hold an expression, a template, a field name or a
plain word is fixed per kind in `rachana.lang.json` [CS]; TRANSLATION.md section 3 states each one's effect.

### 2.4 Data: the `data:` entries [planned, D10]

`data:` names the screen's inputs. **Each entry becomes a field of `$`** (a table is a list of rows, a record an
object), so every expression form works unchanged:

```yaml
data:
  sales:   { table: sales, key: [region, month] }   # a name the host resolves (Data Protocol)
  targets: /data/targets.csv                         # text: shorthand for { url: /data/targets.csv }
  regions: { rows: [ { code: N, name: North }, { code: S, name: South } ] }
  summary: { url: /api/summary.json, as: record }
panels:
  - { id: north, kind: metric, value: "sum($.sales[?@.region == 'N'], 'revenue')", fmt: amount0 }
```

A source written as a mapping has **exactly one** of these (`RCH-2073` otherwise):

| Key | Meaning |
|---|---|
| `table: <name>` | a table the host resolves by name (Data Protocol); unknown name: `RCH-2070` |
| `url: <address>` | a file or endpoint read whole; `format: json \| csv \| ndjson \| arrow` (from the extension when absent); `path` picks rows out of a wrapped JSON answer. The host's allow-list decides addresses; an address is never built by an expression |
| `rows: [ … ]` | rows written in the file |
| `record: { … }` | one record written in the file |

Common keys: `key` (field or list of fields identifying a row; required for live changes, selection and drills,
`RCH-2072`), `fields` (`{name: {type, label?, fmt?}}`), `as: rows | record`, `where` (expression over each row `@`;
may read controls), `limit`, `refresh` (seconds), `live: true | false`, `mode: auto | client | server`.

A data name that equals a field of a host's own record is `RCH-2073` in a checked host and a warning otherwise.
A panel's own `source: { table: <name>, where?, limit? }` [today] gives that panel its rows as `$src`; `<name>` is a
`data:` entry or a host table. `source: { kind: … }` is a profile form. There is deliberately no source type for a
function, REST paging or a topic: a host exposes those as named tables.

### 2.5 Controls, events, forms, fragments (summary)

- **Controls** [today]: `{id, kind, label?, options?, from?, default?, remember?, required?, placement?, panel?,
  parent?, min?, max?, step?, range?}`; kinds `dropdown`, `multi-select`, `cascading`, `toggle`, `segmented`, `date`,
  `date-range`, `number-range`, `top-n`, `search`; codes `RCH-2034`..`2039`. `default` is a value of the control or
  the word `first` (the first option; `multi-select` also `all`); a `top-n` default is a whole number above 0.
  A control's value is read as `ctl.<id>`
  (text; a list of text for `multi-select`; a whole number for `top-n`; `from..to` text for a range). A control
  option form that names an entity kind (`options: { kind: … }`) is a profile form.
- **Events** [today]: a panel `emits: [{name, on}]` (`row-click`, `bar-click`, …); another reads them as
  `@<panel>.<event>.<field>`; `waits` says what a panel shows until an event fires; `children` nests panels shown
  `always` or on selection (depth limit `RCH-2056`). `on-select: {by, field?, filter?}` sets a control from a click.
- **Forms** [today, D2]: `form:` with fields, rules, hints, steps and drafts is Core; it raises a `submit` action.
  `form.save` (targets, connectors, approvals) and `editable-by` are the `drishti` profile's (`RCH-2058` for Core shape).
- **Fragments** [today]: a `panels` entry `{use: name@version, with?}` is replaced by the fragment's panels before
  checking; where fragments come from is the host's `FragmentResolver` (`RCH-2048` when not found).

### 2.6 Formats and tones

`fmt:` and `fmt(value, 'name')` name a **format**; `tone:` names a **tone**. Both are names, never styles. Core's
format names are those of [formats.yaml](formats.yaml) (the table shared with Drishti Elements); a profile MAY add
names, never redefine one.
An unknown format name formats as plain text (`Values.text`) [today, CS]. Core tones: `sign` (→ `pos`/`neg` by the
number's sign, none for 0, empty or non-numbers), `status` (→ `bad` for text containing *fail*, *reject*, *dispute*,
*breach*; `warn` for *pending*, *unmatched*, *warn*; else `ok`; none for empty text), and the fixed tones `pos`, `neg`,
`link`, `accent`, `ok`, `warn`, `bad`. Any other tone word gives no tone [today, CS].

## 3. Where expressions appear

A YAML string in a Sutra is one of four things, fixed per key [CS]:

| Form | Examples of keys | Read as |
|---|---|---|
| expression | strip `bind`, column `bind`, `rows`, `each`, `when`, `where`, `highlight`, `value`, `delta`, `max`, `limit` (when text), `title.id`, `title.with`, `children`, `tabTitle`, `moreLabel`, `mark`, `source` (text form), area `limit`, a control's `from`, `min`, `max`, `step`, a `keys` entry beginning `link(` | Rachana-EL (section 4) |
| template | a panel's `title`, `title.pill`, `markdown.text`, metric `caption` | text with `${…}` parts (4.11) |
| field name | `label`, `value`, `x`, `y`, `date` … of chart kinds | a field of each row; see TRANSLATION 3.0 for the field-or-expression rule |
| word | `kind`, `fmt`, `tone`, `area`, `layout`, `shape`, `colors`, `view` | a closed set or a name |

Every expression and template of a Sutra MUST be compiled when the Sutra loads; a compile error is `RCH-2101` with the
Sutra position and the offset inside the expression (section 7). Field names are not checked unless types are known
(checked mode, 4.14).

## 4. Rachana-EL

The syntax is [rachana-el.ebnf](rachana-el.ebnf). This section gives the meaning.

### 4.1 Principles

1. **Closed and pure.** No loops, no assignment outside `let`, no I/O. An expression reads only its context (4.3).
2. **Total paths.** A missing field, an index out of range, a path through a non-container: `empty`. Never an error.
3. **Null in, null out.** Operators and scalar functions on `empty` give `empty`, with the exceptions stated.
4. **Wrong types in functions are errors** (`RCH-2102`), which `iferror` catches; operators never raise one, except
   exact arithmetic that would lose a whole unit (4.16).
5. **Deterministic.** The same expression over the same context gives the same value in every implementation: no clock,
   locale or random source except through the context (`asof()`), which the host sets [CS].

### 4.2 Values

| Value | Written | Notes |
|---|---|---|
| empty | `null`, a missing field | missing and `null` are the same value |
| boolean | `true`, `false` | |
| number | `42`, `0.5`, `.5`, `1e6` | three kinds (4.16): an **integer** (`42`) and a **decimal** (`0.5`, `.5`) are exact, 34 significant digits; a literal with an exponent (`1e6`) and every real-number result is a **number**, an IEEE 754 double. A whole double below 10¹⁵ in magnitude prints without decimals (`1e0 + 1` → `2`) |
| text | `'a'`, `"a"` | Unicode; `''` is a text, not empty |
| date, timestamp | `'2026-11-17'`, `'2026-11-17T09:30:00Z'` | carried as ISO-8601 text; typed only in checked mode |
| list | `[1, 2]`, `$.legs` | ordered |
| object | `$`, `$.legs[0]` | fields in written order; no object literal |
| link | `link('CP-1', 'counterparty')` | prints its label or id; what it opens is the host's |
| masked | (from the host) | a value the reader may not see; prints `•••` (4.9) |

**Truthiness**: `empty` false; boolean as is; number true unless 0 or NaN; text true unless `''` (`'false'` and `'0'`
are true); list and object true unless they have no elements; link true; masked never true.

**Text form** of a value (used by `+` joining, templates, comparisons as text) [CS]: empty → `''`; a whole number below
10¹⁵ → digits (`2`, `-7`); an integer or a decimal → plain digits with no exponent and no trailing zeros after the point
(`0.1`, never `1E-1`; `1.10 * 3` → `3.3`; `0.0` → `0`); another number → the shortest round-trip decimal of the double as
the reference prints it (`0.30000000000000004`, `1.0E20`, `NaN`, `Infinity`);
boolean → `true`/`false`; link → its label, else its id; list → `[n]`; object → `{n}` (n = size).

### 4.3 The context: names an expression reads

| Written | Is | Outside its place |
|---|---|---|
| `$` | the screen's record; each `data:` entry is a field | always present (may be empty) |
| `@` | the current row or element (`rows`, `each`, a filter, `map`, `sort`, `reduce`, a row condition) | empty |
| `^`, `^^`, … | the element the current one is in; for a table row, the record | empty |
| `#index` | 0-based position of the current row in its list | `-1` |
| bare `name` | a `let` name in scope; else a field of `@` inside a row; else of `$` | |
| `ctl.<id>` | the current value of control `id` | empty when not set |
| `@<panel>` | what panel `<panel>` emitted: `{event: payload}`; `@parent` the parent panel's | empty until it fires |
| `$src` | what the panel's own `source:` names | empty |
| `@acc` | inside `reduce`'s third argument only: the value so far | elsewhere: the events of a panel `acc` |

```text
$.legs[0].notional          a path from the record
@.amount * 2                the row's field
amount * 2                  the same inside a row (a bare name)
$.legs[?^.desk == 'FX']     inside the filter, ^ is the record the list is in
ctl.region                  'North' when the control region is set to North
@trades.select.id           the id the trades panel's select event carried
```

### 4.4 Paths, filters, projection

| Selector | Rule | Example → result |
|---|---|---|
| `.name` | field of an object; empty for anything else; a masked target gives the mask | `$.ccy` → `'USD'`; `'abc'.x` → empty |
| `[n]` | element `trunc(n)` of a list; negative counts from the end; out of range → empty | `$.legs[-1]` → the last leg |
| `['name']` | a field by text (any characters) | `$['day-count']` |
| `[?p]` | the elements for which `p`, with `@` each element and `^` the container's holder, is truthy; a non-list → empty | `$.legs[?@.type == 'FLOAT']` → a list |
| `[*]` | the list itself (a non-list → empty); a following `.name` takes that field of **each** element | `$.legs[*].id` → `[1, 2]` |

A filter result is a list, so `.name` after it is empty: write `first($.legs[?…]).name`. A `.name` after `[*]` keeps
projecting: `$.a[*].b.c` is the `c` of each `b`. An index of a masked list is masked.

### 4.5 Arithmetic and text joining

| Expression | Rule | Examples |
|---|---|---|
| `a + b` | empty if one side is empty and the other is empty or a number; number + number adds; otherwise both sides' text forms are joined | `1 + 2` → `3`; `'Leg ' + 1` → `'Leg 1'`; `'1' + 1` → `'11'`; `empty + 1` → empty; `'x' + empty` → `'x'`; `true + 1` → `'true1'` |
| `a - b`, `a * b`, `a / b`, `a % b` | empty if either side is empty; otherwise both sides as numbers (number-like text counts, read as a literal is (4.16); `true` is 1, `false` 0, other text is NaN) | `'5' * 2` → `10`; `'0.1' * 3` → `0.3`; `'x' * 2` → NaN |
| `a / 0`, `a % 0` | empty | `1 / 0` → empty |
| `a % b` | remainder with the sign of `a` (truncated division) | `-3 % 2` → `-1` (`mod(-3, 2)` → `1`) |
| date − date | two texts that both read as dates (ISO, or a timestamp's first 10 characters followed by `T` or a space) subtract to whole days | `'2026-11-17' - '2026-11-01'` → `16` |
| `-a` | empty stays empty; otherwise the negated number | `-'3'` → `-3` |
| `!a` | the negation of truthiness; masked stays masked | `!empty` → `true`; `!'0'` → `false` |

Which kind of number a result is, and how exact, is 4.16: integers and decimals are exact (`0.1 + 0.2` → `0.3`), `/`
rounds half-even to 34 significant digits, and a double on either side makes the result a double. [CS] pins the text of
every result.

### 4.6 Equality

`a == b`:

1. If either side is empty: true only when both are (`empty == null` → `true`; `empty == 'X'` → `false`;
   `empty != 'X'` → **`true`**).
2. Else if either side is a number: both as numbers (`1 == '1.0'` → `true`; `1 == true` → `true`; `1 == 'a'` → `false`;
   NaN equals nothing). Two exact numbers compare exactly (`0.1 + 0.2 == 0.3` → `true`); an exact number against a double
   compares as two doubles (4.16).
3. Else both text forms compared exactly, case-sensitive (`'USD' == 'usd'` → `false`; `true == 'true'` → `true`).

`!=` is the negation of `==`. A masked side gives the mask.

### 4.7 Ordering

`<`, `<=`, `>`, `>=`:

1. A masked side gives the mask.
2. Both sides as numbers; if both are numbers the comparison is numeric (`'10' > '9'` → `true`), exact between exact
   numbers and as doubles when either is a double (4.16).
3. If either is not a number **and both are texts**, compared as text, character by character by Unicode **code point**
   (ISO dates order correctly: `'2026-01-31' < '2026-02-01'` → `true`; `'｡' < '😀'` → `true`) (4.13) [CS].
4. Otherwise `false`: anything against empty, a number against a non-numeric text, a list or object.

A chain `0 < x < 10` parses as `(0 < x) < 10` (a boolean against 10, numerically); write `0 < x && x < 10`.

### 4.8 Logic, conditional, membership

- `a && b`: masked `a` → mask; falsy `a` → `false` without evaluating `b`; else masked `b` → mask, else `truthy(b)`.
- `a || b`: truthy `a` → `true` without evaluating `b`; truthy `b` → `true`; else mask if either is masked, else `false`.
  Results are booleans, never operands: `$.strategy || 'n/a'` → `true`; use `nonblank($.strategy, 'n/a')`.
- `c ? a : b`: masked `c` → mask (either branch would reveal it); else only the chosen branch is evaluated.
  `if(c, a, b?)` and `ifs(c1, a1, …, else?)` are the same conditional in call form; `if` without `b` gives empty.
- `x in list`: true when an element (not masked) equals `x` by 4.6. **Strict**: an empty list, empty, `''` or an
  object holds nothing; a scalar is a list of one. `x not in list` is its negation. Masked `x` or list → mask.

```text
'FX' in ['FX', 'RATES']        → true        empty in [null, 'A']   → true
'FX' in []                     → false       'FX' not in []         → true
'FX' in 'FX'                   → true        one_of('FX', [])       → true   (controls: nothing chosen holds all)
```

### 4.9 Masked values

A host's `Masker` marks values the reader may not see. A masked value prints `•••` in every format and:

1. propagates through every path, operator and function: a result computed from it is masked;
2. is never truthy, so a condition on it is never true (`!` included);
3. in lists: functions that read elements return the mask if any element they read is masked;
4. exceptions: `coalesce` and `nonblank` pass it on only if it is the first value they would return; `a || b` is
   `true` when the other side is truthy; `iferror` returns a masked first argument as it is; `map` masks only the
   element concerned; `if`, `ifs`, `switch`, `choose` are masked only when the masked value decides;
5. a list literal keeps a masked item masked in its place.

### 4.10 Functions: calls, arguments, forms

1. **Closed set.** A name not in the function table of the loaded profiles is a compile error listing near matches
   (`RCH-2101`; with types known, `RCH-9152`). An argument count out of range is a compile error
   (`size takes 1 argument(s), got 2`).
2. **Literal checks.** A wrong literal argument is found at compile time: an unknown day-count basis, interval,
   calendar, unit, fill method, a refused regular expression, a `pad` fill of two characters, a `sequence` length
   outside 0..10,000 [today, CS].
3. **Named arguments.** `f(x, name = v)`: names come after positional arguments; the name MUST be a parameter of the
   signature, given once and not also by place. A Core function binds every named argument to its position (a skipped
   optional parameter before it is empty, i.e. its default). A keyword-only parameter (`where`, `by`, `ignore`, …)
   exists only for profile functions; over a list it is refused at evaluation (`RCH-2102`).
4. **Eager and lazy.** Most functions receive evaluated arguments and return a masked result if any argument is
   masked (4.9). **Forms** receive their arguments unevaluated: `switch`, `iferror`, `is_error`, `choose` evaluate
   lazily; `map`, `sort`, `reduce`, `group` evaluate an argument once per element with `@` the element, `#index` its
   position and `^` the previous `@` (or `$`); `$` is still the record. `if`, `ifs`, `let` are built by the parser.
5. **`reduce` refuses a hand-written sum**: a third argument that adds or subtracts `@acc` with `+`/`-` (also inside
   a conditional's branches) does not compile; use `sum(…)` or `concat(…)`.
6. **Errors.** A function given a value of the wrong type or outside its domain raises `RCH-2102`
   (`round_up: 'abc' is not a number`, `sqrt: -4 is negative`, `eomonth: 'soon' is not a date (yyyy-mm-dd)`).
   Number-like text counts as a number; a number counts as the text it prints (`left(20261016, 4)` → `'2026'`).
7. **Decimals stay decimal.** Rounding, multiples, remainders, whole powers up to ±64, `sqrt`, `clamp`,
   `least`/`greatest`, `sequence`, `yearfrac` compute in decimal arithmetic; an integer or decimal argument is taken as it
   is, a double argument is first read to 15 significant digits (`round_up(1e-1 + 2e-1, 1)` → `0.3`; `power(1.1, 2)` →
   `1.21`). Real-number functions (`exp`, `ln`, trigonometry, distributions, discounting) compute in binary floating point
   and return a number (4.16) [CS].
8. **Dates** are ISO text in and out; a timestamp counts by its date. `asof()` is the as-of date the host sets.

### 4.11 Templates

A template is text with `${expression}` parts (EBNF part C). Each part is evaluated and inserted as its text form
(4.2): a link as its label, a list as `[n]`. Text without `${` is used as written. Braces inside a part are matched
(`"${size($.legs)} legs"`); brace matching ignores quotes. An unclosed `${` is `RCH-2101` at the template offset of
`${`; an error inside a part is reported at the offset inside that part [today; CS pins positions].

A host MAY render a template **leniently**, writing a placeholder for a part that fails at evaluation and counting it;
TRANSLATION.md says where.

```text
"Leg ${#index + 1} · ${@.type}"        → "Leg 2 · FLOAT"
"${size($.sales)} rows"                → "12 rows"
"Cost: $5"                             → "Cost: $5"   (no ${: literal)
```

### 4.12 Limits

| Bound | Default | Beyond it |
|---|---|---|
| nesting depth (parentheses, brackets, calls, unary operators, `?:` branches, each operand of a binary chain: `a + b + c` is 3) | 200 | `RCH-2101` "expression nested deeper than 200 levels" at the position reached |
| expression length | 10,000 characters | `RCH-2101` at offset 10,000 |
| items in a list literal | 1,000 | `RCH-2101` at the `[` |
| names in one `let` | 63 | `RCH-2101` |
| `sequence` length | 10,000 | compile error for a literal, `RCH-2102` otherwise |
| `group` result | 500 groups | further groups left out [CS] |
| significant digits of a number literal without an exponent | 34 | `RCH-2101` "number '…' has more than 34 significant digits" at the literal (4.16) |

A host MAY lower the depth and length bounds (settings `rachana.max-expression-depth`, `rachana.max-expression-length`)
and MUST report the bound in the message.

### 4.13 Characters

Expressions and texts are sequences of Unicode characters, and a character is a **code point** (decision XD4, EL_EXPANSION
X31): every length, position and order of a text value is counted in code points, in every runtime.

1. **Lengths and positions.** `len`, `left`, `right`, `mid`, `find`, `search`, `pad`, `reverse`, `rept`'s limit, `code`
   (the first character), every position a text function takes or gives, and the 10,000-character bound of an expression
   (4.12) count code points: `len('😀')` → `1`; `mid('a😀b', 2, 1)` → `'😀'`; `code('😀')` → `128512`; `reverse('a😀')` →
   `'😀a'`. A combining mark is a code point of its own (`len('é')` written as `e` and U+0301 → `2`): graphemes are a
   later minor's functions (X31), not the meaning of a length.
2. **Order.** Texts are ordered code point by code point, then the shorter first (4.7, `sort`, and `least` and
   `greatest` over texts): U+FF61 `'｡'` sorts before U+1F600 `'😀'`, although its UTF-16 code unit is larger. No
   locale collation and no case folding: `'B' < 'a'` → `true`.
3. **Lone surrogates.** A text from the host may hold an unpaired surrogate (U+D800 to U+DFFF); it counts as one code
   point with that value. An expression's own text cannot hold one (the file is UTF-8).
4. **Source positions are not text values.** The offsets of a problem (6.1, `expr.start`, `range`) and the `at <offset>`
   of a message stay in **UTF-16 code units**, the unit editors and the Language Server Protocol use, so a tool can place
   them without converting (FORMAT 6).

Only characters outside the Basic Multilingual Plane are counted differently from the reference's UTF-16 code units; the
reference changes to this rule (KNOWN_DIFFERENCES, `el/text-code-points`).

### 4.14 Checked mode

Where types are known (a `data:` table with a schema, a profile's models), every expression is also type-checked
before it runs. Types: `integer`, `decimal`, `number`, `string`, `boolean`, `date`, `timestamp`, `none`, `list`,
`table`, `any`. Numeric types fit each other; `date` and `timestamp` fit each other; `any` and `none` fit anywhere.
`integer op integer` is `integer` for `+ - * %`, else `decimal` if either side is, else `number`; `/` is `decimal`;
`date - date` is `integer` days. Checked mode refuses: non-boolean conditions, implicit casts (a text with `+`, a date
against `'9 Oct'`), chained comparisons, screen-only roots (`ctl`, `@panel`, `$src`, `#index`) in data scopes, old
spellings (`RCH-9127`); functions without a signature yet (`RCH-9129`); unknown names with "did you mean"
(`RCH-9152`); a filter, `in`, sort or grouping on a field masked for the reader (`RCH-9161`). **Checked mode only
refuses more; it never changes what a text means.** A literal's type is `integer` (whole, no exponent), `decimal` (a
fraction, no exponent) or `number` (with an exponent), as 4.16 says.

### 4.15 Reservations

`rachana: 1.0` holds back what its later minors need, so that each of them stays an addition (7.2; decision XD2,
EL_EXPANSION X1). The list is [reserved.yaml](reserved.yaml); everything in it is refused in 1.0 with a message that names it
as reserved, and leaves the file only when a minor defines it.

1. **Tokens.** `|>`, `??`, `=>`, `...`, `..`, `{`, `}`, `/*`, `*/` are tokens of the grammar (EBNF `reserved-token`) and
   are always `RCH-2101`, with the reason reserved.yaml gives (6.3), at the token's offset: `$.a ?? 0` →
   `expression '$.a ?? 0': '??' is reserved for a later version of Rachana: write coalesce(a, b) at 4`. The lexer reads
   them greedily, before the operators: `...` before `..`, and a number stops before `..` (`1..5` is `1`, `..`, `5`).
   An underscore directly inside a number (`1_000`) is refused the same way (`'_' in a number is reserved …`), so digit
   separators can come later. Inside a quoted text none of these is anything but characters (`'{'` is a text).
2. **Core names.** reserved.yaml's `names` (278 function names, by the addition each is held for) are not Core
   functions in 1.0 and are never offered (not in `expected`, near-match lists, completion or the prompt kit). A call of
   one is `RCH-2101` "'<name>' is reserved for a later version of Rachana", in place of "unknown function"; a profile
   that registers one is refused at registration, **unless** reserved.yaml lists it under `promoted` for that profile:
   the profile keeps it, with its meaning, until Core defines the name with the same meaning proven by the profile's own
   cases (EL_EXPANSION rule 9). A call of a name a known profile owns keeps that profile's problem when the profile is not
   loaded (`RCH-2060`, 5.11). A field of that name (`$.filter`, `@.rank`) is a field as before: only calls are held.
3. **Answers are open sets.** Words a function answers from a closed list today may gain members in a later minor:
   `type_of` (`'decimal'`, `'date'`, `'money'`, …), the kinds of `sort` keys, the tones. A host and a caller MUST accept a
   word they do not know, as a painter accepts an unknown tone (it draws nothing for it); a runtime of 1.0 itself answers
   only the 1.0 words (`type_of` of an integer or a decimal is `'number'`).
4. **Names an author defines win.** A name an author defines (a `let` name today; a function or a library alias in a later
   minor, X41) is never taken away by a Core name a later minor adds: where it is in scope it keeps its meaning, and the
   compiler warns that it hides a Core name (the warning's code is assigned with X41). Core's new names therefore never
   change what an existing Sutra means.

### 4.16 Numbers: integer, decimal, number

Three kinds of number, as checked mode names them (decision XD3, EL_EXPANSION X2). The arithmetic is Core's own code
(`CoreDecimal`, `CoreMath`) in every runtime, so the same expression gives the same digits everywhere.

| Kind | What it is | Written as |
|---|---|---|
| **integer** | an exact whole number of at most 34 digits; an integer is a decimal with no fraction, so 64-bit identifiers survive (`9007199254740993` stays itself) | `42`, `0`, `1234567890123456789` |
| **decimal** | an exact decimal of at most 34 significant digits: IEEE 754 **decimal128** values, exponent range included | `0.1`, `.5`, `1.10`, `1.` |
| **number** | an IEEE 754 binary64 double | a literal with an exponent (`1e-3`, `2.5E3`), a real-number function's result, a double from the host |

1. **Literals.** A number literal without an exponent is an integer or a decimal, exactly as written; one with more than
   34 significant digits does not compile (`RCH-2101` "number '…' has more than 34 significant digits"). A literal with
   an exponent is a double: write `1e-3` for a double, `0.001` for an exact thousandth. Number-like text that an operator
   or a function reads as a number follows the same rule (`'0.1' * 3` → `0.3`).
2. **`+`, `-`, `*`, `%` and unary `-` on integers and decimals are exact**: `0.1 + 0.2` → `0.3`; `1.10 * 3` → `3.3`;
   `5.5 % 2` → `1.5`. A result that needs more than 34 significant digits is rounded **half to even** to 34 digits
   (decimal128 rounding), and only digits after the point can be lost that way: a result whose integer part has more
   than 34 digits (its magnitude is 10³⁴ or more) is `RCH-2102` "the result needs more than 34 digits before the point",
   never a silent loss of a unit. `date − date` (4.5) is an integer of days.
3. **`/` on integers and decimals** gives the quotient rounded half to even to 34 significant digits: `1 / 3` →
   `0.3333333333333333333333333333333333`; `2 / 3` → `0.6666666666666666666666666666666667`; `10 / 4` → `2.5`. `/ 0`
   and `% 0` are empty, as for every kind (4.5).
4. **A double on either side makes a double.** An integer or decimal meeting a number is converted to the nearest
   double and the operation is binary floating point: `1e-3 * 2` → `0.002` (a number); `0.1 + 1e-1` →
   `0.2` (a number). Equality and ordering follow the same rule: two exact numbers compare exactly, anything with a
   double compares as doubles (`0.1 == 1e-1` → `true`).
5. **Real-number functions** (`exp`, `ln`, `log`, the trigonometry, `power` beyond whole exponents, distributions,
   discounting) take doubles and return a number; the decimal functions of 4.10 rule 7 keep their exact results.
6. **Values from the host** keep the kind the host gives: a JSON number in a record is a double; a column the Data
   Protocol types `decimal` or `integer` arrives exact. `$.qty * 2` with a JSON `qty` of `3` is the double `6`, which
   prints `6`.
7. **List aggregates** add as `+` does: exact over integers and decimals (`sum([0.1, 0.2])` → `0.3`); once a double is
   among the elements, the whole sum is the compensated double sum of 5.7.
8. **Text form**: 4.2. A decimal never prints an exponent, trailing zeros after the point, or a negative zero.

`type_of` answers `'number'` for all three kinds in 1.0 (4.15 rule 3); `expr` cases name the kind in their `type`
(`number` for a double, `decimal` for an exact result: FORMAT 5.2).

## 5. Function catalogue (Core)

Core has **207 functions** (196 scalar, list and form functions plus the 11 list aggregates whose kernel moves into
Core, decision D3) and 6 deprecated spellings. Each entry's full signature, parameter types, defaults, null and error
behaviour and examples go into `functions.yaml` (R0 step 0d); each example there is a conformance case. Arity is
written `min–max`; `?` marks an optional argument; `…` a repeatable one.

### 5.1 Rules every function keeps

- Empty in, empty out, unless stated. Exceptions: the text tests (`contains`, `starts_with`, `ends_with`, `matches`,
  `matches_case`, `has`, `any_of`, `all_of`) answer `false`; `is_*` and `type_of` answer for empty; `concat`,
  `coalesce`, `nonblank`, `least`, `greatest` skip empty arguments; `size` gives `0`; `sum` and `count` of nothing give
  `0`, other aggregates empty.
- Tests answer `true` or `false`, never empty.
- Masked in, masked out (4.9).
- Wrong type → `RCH-2102` naming the function and the value.

### 5.2 Logic and choice (21)

| Function | Arity | Result | Example |
|---|---|---|---|
| `if(c, a, b?)` | 2–3 | `a` if `c` is truthy else `b` (empty); lazy | `if($.qty > 0, 'long', 'short')` |
| `ifs(c1, a1, …, else?)` | 2–64 | the answer of the first truthy condition; odd count: last is else | `ifs($.n > 1e9, 'L', $.n > 1e8, 'M', 'S')` |
| `switch(x, v1, a1, …, else?)` | 3–64 | the answer of the first `vi == x` (4.6); even count: last is else; lazy | `switch($.type, 'FIXED', 'F', 'FLOAT', 'V', '?')` |
| `iferror(x, y)` | 2 | `x`, or `y` when `x` raises `RCH-2102`, is empty or NaN; `y` lazy | `iferror($.a / $.b, 0)` |
| `is_error(x)` | 1 | whether `x` raises an error | `is_error(date('soon'))` → `true` |
| `let(n = v, …, r)` | 2–64 | `r` with the names bound in turn | `let(n = $.notional, n / 1e6)` |
| `choose(i, a1, a2, …)` | 2–64 | the `i`-th value (1-based); lazy | `choose(2, 'a', 'b')` → `'b'` |
| `coalesce(a, …)` | 1–8 | first non-empty (`''` counts as a value) | `coalesce($.book, 'UNBOOKED')` |
| `nonblank(a, …)` | 1–8 | first that is neither empty nor blank text | `nonblank($.pkg, 'n/a')` |
| `within(v, 'from..to')` | 2 | `v` in the range; either end may be empty; an empty range holds all; numbers as numbers, else text | `within($.date, ctl.when)` |
| `one_of(v, list)` | 2 | `v` equals an element; **empty choice holds everything** | `one_of($.desk, ctl.desks)` |
| `has(list, v)`, `any_of(list, vs)`, `all_of(list, vs)` | 2 | list attribute tests | `has($.tags, 'fx')` |
| `link(id, kind?, label?)` | 1–3 | a link value; empty when `id` is empty | `link($.cpty, 'counterparty')` |
| `is_number`, `is_text`, `is_date`, `is_boolean`, `is_blank` | 1 | type tests (`is_blank`: empty, NaN or blank text) | `is_blank('  ')` → `true` |
| `type_of(x)` | 1 | `'empty'`, `'number'`, `'text'`, `'boolean'`, `'link'`, `'list'`, `'object'` | `type_of([1])` → `'list'` |

### 5.3 Math (43)

`abs`, `sign`, `sqrt`, `exp`, `ln`, `log10` (1); `log(x, base?)` (1–2); `round(x, n?)`, `round_up`, `round_down`,
`ceiling(x, m?)`, `floor(x, m?)` (1–2); `round_sig(x, digits)`, `mround(x, m)`, `mod(x, y)`, `power(x, y)`,
`combin`, `permut`, `atan2(x, y)`, `decimal(text, radix)` (2); `base(n, radix, min_len?)` (2–3); `clamp(x, lo, hi)`
(3); `fact`, `even`, `odd` (1); `gcd(…)`, `lcm(…)`, `least(…)`, `greatest(…)` (1–64); `pi()` (0); `sin`, `cos`,
`tan`, `asin`, `acos`, `atan`, `sinh`, `cosh`, `tanh`, `radians`, `degrees` (1); `min`, `max` (1–8: over one list,
the list aggregate; with several arguments, the deprecated spelling of `least`/`greatest`).

| Example | Result |
|---|---|
| `round(2.675, 2)` | `2.68` (half away from zero, in decimal) |
| `round_up(2.671, 2)`, `round_down(-2.679, 2)` | `2.68`, `-2.67` |
| `mod(-3, 2)` | `1` (sign of the divisor; empty when `y` is 0) |
| `ceiling(4.2, 0.5)` | `4.5` |
| `power(2, -1)`, `power(0, -1)` | `0.5`, empty |
| `least($.maturity, '2030-12-31')` | the earlier ISO date (no numbers: compared as text) |
| `max($.legs[*].notional)` | the largest number in the list |

### 5.4 Text (32)

`upper`, `lower`, `proper`, `trim`, `clean`, `len`, `char`, `code`, `reverse` (1); `left(t, n)`, `right(t, n)`,
`rept(t, n)`, `exact(a, b)`, `split(t, sep)`, `contains(t, part)`, `starts_with`, `ends_with`, `matches(t, re)`,
`matches_case(t, re)`, `fmt(v, 'format')`, `text(v, 'excel-format')` (2); `mid(t, start, n)`, `replace(t, old,
new)`, `regex_replace(t, re, by)` (3); `find(part, t, start?)`, `search(part, t, start?)`, `text_before(t, sep,
n?)`, `text_after(t, sep, n?)`, `regex_extract(t, re, group?)` (2–3); `value(t, decimal_sep?, group_sep?)` (1–3); `pad(t, n, char?, side?)`
(2–4); `concat(…)` (1–64).

Rules: `contains`, `starts_with`, `ends_with`, `matches` ignore case; `matches_case`, `regex_*`, `replace`, `exact`
do not. Regular expressions run on a linear-time engine: classes, groups `( )` and `(?: )`, alternation, `* + ? {n,m}`
and lazy forms, `^`, `$`, a leading `(?i)`; back-references, look-around, atomic groups, possessive quantifiers and
`\b` are refused at compile time for a literal pattern; patterns ≤ 200 characters; input cut at 8,192 characters
[CS]. Positions are 1-based (`mid`, `find`); `find`/`search` give 0 when absent.

| Example | Result |
|---|---|
| `left('MX-2000', 3)`, `mid('MX-2000', 4, 2)` | `'MX-'`, `'20'` |
| `split('2032-06-25', '-')` | `['2032', '06', '25']` |
| `pad(7, 3, '0')` | `'007'` |
| `concat($.book, ' / ', $.ccy)` | `'BOOK-3 / AUD'` (empty parts add nothing; a list is an error) |
| `regex_extract('MX-1', '^([A-Z]+)-', 1)` | `'MX'` |
| `fmt(1234.5, 'amount0')` | `'1,235'` (the named format) |

### 5.5 Dates (22)

`asof()` (0); `date(text)` or `date(y, m, d)` (1–3); `timestamp(text)`, `year`, `quarter`, `month`, `week` (ISO),
`day`, `hour`, `minute`, `second` (1); `weekday(d, type?)`, `eomonth(d, n?)`, `is_business_day(d, calendar?)` (1–2);
`days_between(a, b)`, `edate(d, n)` (2); `days360(a, b, european?)`, `business_days_between(a, b, calendar?)` (2–3);
`datedif(a, b, unit)`, `yearfrac(a, b, basis)` (3); `intck(interval, a, b, calendar?)` (3–4);
`shift_date(d, n, unit?, calendar?)` (2–4).

Fixed vocabularies (compile-time checked when literal): `yearfrac` bases `ACT/360`, `ACT/365F`, `30/360`, `30E/360`,
`ACT/ACT`; `intck` intervals `day`, `week`, `month`, `quarter`, `year`, `business_day`; `datedif` units `Y`, `M`,
`D`, `MD`, `YM`, `YD`; `shift_date` units `business-day` (default; `business_day` accepted), `day`, `week`, `month`,
`quarter`, `year`. Calendars come from the host's `Calendars` hook; **without one, weekends are the only non-business
days** [planned; today the reference defaults to its `USNY` calendar].

| Example | Result |
|---|---|
| `days_between('2026-11-01', '2026-11-17')` | `16` |
| `edate('2024-01-31', 1)` | `'2024-02-29'` |
| `date(2026, 14, 1)` | `'2027-02-01'` (Excel carry-over) |
| `week('2027-01-01')` | `53` |
| `shift_date(asof(), -1)` | the business day before the as-of date |

### 5.6 Lists (21)

`size`, `first`, `last`, `flatten`, `unique` (1); `take(l, n)`, `drop(l, n)` (2); `sequence(n, start?, step?)` (1–3);
`map(l, expr)` (2); `reduce(l, init, expr)` (3); `sort(l, key?, desc?, key2?, desc2?, …)` (1–17);
`group(l, key, aggregate?, by?)` (2–4); `fillmissing(l, method, value?)` (2–3); `interp1(xs, ys, x, method?)` (3–4);
`polyval(coeffs, x)` (2); `trapz(ys)` or `trapz(xs, ys)`, `cumtrapz` likewise (1–2); `union`, `intersect`, `difference`,
`is_member` (2).

| Example | Result |
|---|---|
| `size($.legs)`, `size('abc')`, `size(5)` | `2`, `3`, `0` |
| `take(sort($.events, @.date, true), 3)` | the three latest events |
| `map($.legs, @.notional / 1e6)` | `[242, 242]` |
| `reduce($.legs, '', concat(@acc, left(@.type, 2)))` | `'FIFL'` |
| `group($.sales, 'region', 'revenue')` | `[{label, value, count}, …]` in key order; ≤ 500 groups |
| `group($.sales, @.region, avg(@.price))` | the average price per region |
| `sequence(3, 0.1, 0.1)` | `[0.1, 0.2, 0.3]` (decimal) |

`sort` orders numbers (and number-like text) as numbers and before text; text by character; empty keys last either
way; stable. `unique` keeps the first of each (`1` and `1.0` are one; `'1'` differs). `group`'s key is a quoted field
(dotted path) or an expression over `@`; its aggregate a quoted field (summed) or an aggregate call over `@`; `by`
`'month'`/`'year'` cuts an ISO date key.

### 5.7 List aggregates (11, plus `min` and `max`; the kernel moving into Core)

`sum(l, 'field'?)`, `avg`, `median`, `variance`, `stddev`, `variance_pop`, `stddev_pop`, `distinct` (1–2);
`count(l?, 'field'?)` (0–2); `percentile(l, 'field'?, p)` (2–3); `wavg(l, 'x', 'w')` or `wavg(xs, ws)` (2–3);
`min(l)`, `max(l)` (above). Number-like text counts; other non-numbers are skipped; a masked element masks the result;
`sum` uses compensated summation [CS]. `percentile` is Excel's `PERCENTILE.INC`.

`sum($.legs, 'notional')` → `484000000`; `percentile($.pnl[*].v, 0.99)`; `wavg($.legs, 'price', 'qty')`.

### 5.8 Statistics (32)

`norm_dist(x, mean, sd, cumulative?)`, `norm_inv(p, mean, sd)`, `norm_s_dist(z, cumulative?)`, `norm_s_inv(p)`,
`lognorm_dist`, `lognorm_inv`, `t_dist`, `t_inv`, `chisq_dist`, `chisq_inv`, `f_dist`, `f_inv`, `binom_dist`,
`binom_inv`, `poisson_dist`, `expon_dist`, `gamma_dist`, `gamma_inv`, `gamma`, `gamma_ln`, `beta_dist` (3–6),
`beta_inv` (3–5), `weibull_dist`, `hypgeom_dist` (4–5), `negbinom_dist`, `erf`, `erfc`, `standardize`,
`confidence_norm`, `confidence_t`, `fisher`, `fisher_inv`: Excel's functions of the same name with `.` written `_`.
`norm_s_inv(0.99)` → `2.3263478740408408` [CS: real-number results are pinned to a tolerance stated per case].

### 5.9 Finance on given flows (25)

`npv(rate, flows)`, `xnpv(rate, flows, dates)`, `irr(flows, guess?)`, `xirr(flows, dates, guess?)`, `mirr`, `pv`,
`fv`, `pmt`, `nper` (3–5), `ipmt`, `ppmt` (4–6), `rate` (3–6), `cumipmt`, `cumprinc` (6), `effect`, `nominal`,
`fvschedule` (2), `rri`, `pduration` (3), `accrued(rate, start, end, …)` (3–5), `dollarde`, `dollarfr` (2),
`macaulay_duration`, `modified_duration`, `convexity` (3–5). Root finders (`irr`, `xirr`, `rate`) use Newton's method
from the guess, then a sign-change scan, to 1e-12; no root is `RCH-2102`, never a non-root figure. A flow list with an
empty element gives empty.

### 5.10 Deprecated spellings (read, never offered; warning `RCH-2090` with a fix)

| Old | Write | Note |
|---|---|---|
| `startsWith`, `matchesCase`, `oneOf` | `starts_with`, `matches_case`, `one_of` | same results |
| `today()` | `asof()` | same result |
| `days(a, b)` | `days_between(a, b)` | `days` gives empty, not an error, for a non-date |
| `min(a, b, …)`, `max(a, b, …)` | `least`, `greatest` | the old form skips non-numbers |

### 5.11 Not in Core

**Drishti profile functions** (they need a model or a platform): `measure`, `cell`, `ratio`, `convert`, `fx`,
`related`, `related_rows`, `calc`, `share_of`, `parent_of`, `as_of`, `avg_by`, `sum_by`, `min_by`, `max_by`,
`avg_over`, `latest`, `earliest`, `opening`, `closing`, the time functions `ytd`, `mtd`, `qtd`, `fiscal_ytd`,
`prior_period`, `same_period_last_year`, `running`, and the windows `lag`, `lead`, `rank`, `dense_rank`,
`percent_rank`, `moving_avg`, `moving_sum`, `moving_min`, `moving_max`. A Core implementation that meets one answers
`RCH-2060` naming the profile.

**To be classified in R0 step 0d** (today supplied by Drishti's pivot engine as list functions; Core or profile is
not yet settled, so a Core implementation MAY lack them): `sum_once`, `count_once`, `avg_once`, `sumproduct`, `sumsq`,
`product`, `devsq`, `avedev`, `geomean`, `harmean`, `mode`, `mode_multi`, `range`, `iqr`, `cv`, `stderr`, `nmiss`,
`kurtosis`, `skewness`, `skewness_pop`, `covar`, `covar_pop`, `corr`, `rsq`, `slope`, `intercept`, `steyx`,
`trimmean`, `percentile_exc`, `rank_avg`, `percent_rank_of`, `kth_largest`, `kth_smallest`, `string_agg`, `cumsum`,
`cumprod`, `cummax`, `cummin`, `movsum`, `movmean`, `movstd`, `movmedian`, `maxk`, `mink`, `bounds`, `zscore`,
`rescale`, `isoutlier`, `polyfit`.

## 6. Problems (the error model)

### 6.1 Shape

A compile returns **all** problems of a file, sorted by position (deterministic, [CS]):

```json
{ "ok": false, "language": "1.0",
  "problems": [ {
    "code": "RCH-2101", "severity": "error",
    "message": "expression 'sum($.sales 'revenue')': expected ')' but found 'revenue' at 12",
    "file": "sales.sutra.yaml",
    "range": { "start": { "line": 9, "column": 14, "offset": 201 }, "end": { "line": 9, "column": 37, "offset": 224 } },
    "path": "/panels/0/value",
    "expr": { "source": "sum($.sales 'revenue')", "start": 12, "end": 21 },
    "doc": "https://ajsinha.github.io/drishti-rachana/problems/RCH-2101" } ] }
```

| Field | Always | Meaning |
|---|---|---|
| `code` | yes | `RCH-` and four digits; stable for ever |
| `severity` | yes | `error` (the Sutra does not load), `warning`, `info` |
| `message` | yes | one plain sentence naming the thing and what is allowed; names only, never data values |
| `file` | when known | as given |
| `range` | yes | 1-based line and column, 0-based offset; `end` exclusive |
| `path` | yes | RFC 6901 JSON Pointer to the YAML node (`/panels/2/limit`), so a tool can find it without positions |
| `expr` | expression problems | the source and 0-based `start`/`end` inside it |
| `expected` | closed sets | valid names, nearest first, at most 40 (then `"more": n`) |
| `fix` | when certain | text edits; applying every fix of a result MUST NOT increase the number of problems [CS] |
| `doc` | yes | a stable page per code |

An API that serves compile results wraps the same list (HTTP 422).

### 6.2 Code ranges

Core keeps the reference's numbers with the prefix `RCH-` (decision D8; Drishti maps them to `DRS-` one to one).

| Range | Family | Codes |
|---|---|---|
| `RCH-2001`..`2009` | file and YAML | `2001` YAML syntax, `2004` file name, `2008` newer than this runtime, `2009` language version |
| `RCH-2010`..`2039` | structure | `2010` missing key, `2011` unknown key, `2012` wrong shape, `2020` bad name or value, `2021` unknown kind, `2022` missing option, `2023` invalid option, `2024` duplicate id, `2029` bad option value, `2030` span/height, `2031` pivot, `2033` strict YAML, `2034`..`2039` controls |
| `RCH-2048`..`2059` | reuse, docstring, wiring, forms, layout | `2048` fragment not found, `2055` docstring, `2056` children depth, `2057` event or source read, `2058` form, `2059` layout |
| `RCH-2060`..`2069` | profiles | `2060` key, kind or function of a profile not loaded, `2061` profile claimed but unused, `2062` unknown profile |
| `RCH-2070`..`2079` | data | `2070` unknown table, `2071` unknown column (`expected`), `2072` key missing for a live table, `2073` source shape or name clash, `2074` not push-down-able over a server table |
| `RCH-2090`..`2099` | deprecation, style (warnings) | `2090` deprecated spelling |
| `RCH-2101`, `2102` | expressions | `2101` does not compile (syntax, unknown function, arity, literal check, limits); `2102` evaluation error |
| `RCH-9127`, `9129`, `9152`, `9161` | checked mode | type or scope; not yet available; unknown name; masked field filtered |

Codes outside these ranges belong to a host. A code never changes meaning; wording may improve; tools match on codes.

### 6.3 Expression messages

An expression problem's message is `expression '<source>': <reason> at <offset>` (or `template '<source>': …`). The
reasons the parser gives (exact words pinned by [CS]):

| Reason | Example input |
|---|---|
| `unexpected character '&'` | `$.a & $.b` |
| `'**' is not Rachana: write power(x, y)` | `2 ** 3` |
| `unterminated string` | `'abc` |
| `bad number '1.2.3'` | `1.2.3` |
| `unexpected '='` | `$.ccy = 'AUD'` |
| `expected ')' but the expression ends` | `round($.a, 2` |
| `expected a field name but found '.5'` | `$.items.5` |
| `the expression ends early: a value is missing` | `$.qty >` |
| `unknown function 'roundd'; known: …` | `roundd(1)` |
| `size takes 1 argument(s), got 2` | `size($.a, 'b')` |
| `round has no argument named digits; its arguments: [x, n]` | `round(1, digits = 2)` |
| `round: n is given twice (by place and by name)` | `round(1, 2, n = 3)` |
| `round: a value without a name follows a named one; …` | `round(n = 2, 1)` |
| `let: 'in' cannot be a name` | `let(in = 1, in)` |
| `reduce does not add up a list: use sum(...) …` | `reduce($.l, 0, @acc + @.v)` |
| `a list written in an expression holds at most 1000 values` | a list of 1,001 items |
| `'??' is reserved for a later version of Rachana: write coalesce(a, b)` (each token of reserved.yaml with its reason) | `$.a ?? 0` |
| `'_' in a number is reserved for a later version of Rachana: write the digits without it` | `1_000` |
| `'group_by' is reserved for a later version of Rachana` | `group_by($.t, 'desk')` |
| `number '12345678901234567890123456789012345' has more than 34 significant digits` | a 35-digit literal |

### 6.4 Run-time states are not problems

A panel whose data is absent, denied, pending or failed is a **state** in the ViewModel (TRANSLATION.md 4), with
words; the rest of the screen is built. An `RCH-2102` during binding is that panel's `error`. Only a Sutra that does
not compile produces problems.

## 7. Versions and compatibility

### 7.1 What is versioned

| Thing | Version | Where |
|---|---|---|
| Language | `major.minor` | major in the file (`rachana: 1`); minor in each definition entry's `since` |
| Grammar, schema, definition, catalogue | the language's | `spec/1/…`, `x-rachana-language` inside |
| ViewModel | its own `major.minor` (`1.0` today) | the ViewModel's `contract` field |
| Data Protocol | its own major | `v` in the base document and the bridge `hello`; `/v1/` in addresses by convention |
| Conformance suite | the language's | `conformance/1/`, each case has `since` |

The first published minor is **1.0**: the language as the reference implements it when the conformance suite is cut,
less the profile keys. Settled additions ([planned] above: optional `version`, text `title`, `data:`, `profiles`,
`RCH-2060`..`2079`) take the minor in which they ship.

### 7.2 Within `rachana: 1`

1. Minors only **add**: keys, options, kinds, functions, formats, problem codes, each with `since`.
2. Nothing is removed and nothing changes meaning: a Sutra valid in 1.x is valid, with the same ViewModel, in every
   later 1.y; the 1.x conformance suite passes unchanged on 1.y (a release gate).
3. Deprecation is a warning (`RCH-2090`) with a machine-applicable fix, never an error.
4. A check that would refuse existing files ships as a warning for at least two minors and becomes an error only in the
   next major.
5. A runtime given something newer than itself says so (`RCH-2008`: "`flash` needs Rachana 1.4; this runtime is 1.2").

### 7.3 Majors

A major is cut only for a change that cannot be an addition. Every runtime of major N reads N and N−1, the latter for
at least 24 months after N ships; every breaking change ships with a `rachana migrate` rule that rewrites files
mechanically, keeping comments and layout (decision D13). `spec/1/` stays published for ever.

### 7.4 Profiles

A profile versions with its host. It may add, never change Core's meaning, never make a valid Core Sutra invalid.
Core's conformance suite MUST pass with every profile loaded.

## 8. Conformance

An implementation conforms to `rachana: 1.x` when it passes every case of `conformance/1/` with `since` ≤ x, as
[conformance/RUNNER.md](../../conformance/RUNNER.md) section 7 states. The case kinds are those of
[conformance/FORMAT.md](../../conformance/FORMAT.md) section 5: `expr`, `expr-error`, `check`, `grammar`, `format`,
`sutra` (Sutra + data → ViewModel, see TRANSLATION.md), `problem`, `patch`, `plan`, `edit` and, for Data Protocol
hosts, `protocol`. The suite will pin, in particular:

1. the text form of non-whole numbers, NaN and the infinities (4.2), and every result of 4.16;
2. text ordering and lengths by code point, outside the Basic Multilingual Plane too (4.13);
3. the exact words of problem messages and their offsets, including inside templates (6.3, 4.11);
4. the order of problems and the effect of fixes (6.1);
5. real-number function results, each with its tolerance (5.8, 5.9);
6. regular-expression acceptance and match choice (5.4);
7. aggregate summation (compensated) and `percentile` (5.7);
8. formats and tones over every value kind (2.6);
9. every example in `functions.yaml` and in this text.

The grammar is proven against the parser by a grammar-driven recogniser that reads every expression of every shipped
Sutra (R0 step 0e, the test part).

## 9. Known differences to resolve before 1.0

Found while deriving this text from the reference; each is either a defect in the reference or a rule to change here,
to be settled with the conformance suite.

1. **Default calendar.** The reference defaults date functions to `USNY`; Core says weekends only without a host
   calendar (5.5).
2. **Checked-mode syntax errors** are reported as `RCH-9127` by the reference, not `RCH-2101`.
3. **Template error offsets** are relative to the part, except an unclosed `${` (relative to the template) (4.11).
4. **Ordering by UTF-16 code unit**, where the design said code point (4.7, 4.13). **Settled** (XD4, 2026-11-19): code
   point; the reference changes (cases `el/text-code-points`).
5. **`+` with booleans** joins text (`true + 1` → `'true1'`) while `*` and `==` treat `true` as 1 (4.5, 4.6).
6. **Links in a list literal** become their display text (the link is lost) (4.2).
7. **Named keyword-only arguments** of profile functions compile in Core and fail only at evaluation (4.10).
8. The Drishti reference guide's grammar and error table predate list literals, `in`/`not in`, `^`, `@panel`, `$src`
   and named arguments, and still say "there are no list literals"; this specification supersedes them.
