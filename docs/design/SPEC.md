<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary. All rights reserved. -->
# The Rachana specification: design

Status: **design, phase R0.** This document designs the normative specification of the language. The language itself
exists today (inside the Drishti product); the published specification, the `RCH-` codes, the `data:` block and the
profile mechanism are **planned**. Where this document describes behaviour that exists today it says "today".

The specification is open: anyone may read it and write Sutras. The runtime that implements it is proprietary.

Related: [CORE_AND_PROFILES](CORE_AND_PROFILES.md) (what is Core), [DATA_PROTOCOL](DATA_PROTOCOL.md),
[COMPILER_AND_RUNTIMES](COMPILER_AND_RUNTIMES.md) (conformance suite), [DECISIONS](DECISIONS.md).

## 1. What the specification consists of

| Artefact | File in this repository (planned) | Normative for |
|---|---|---|
| This text | `spec/1/SPEC.md` (this design becomes it) | meaning: evaluation rules, compatibility, errors |
| Sutra JSON Schema, per profile | `spec/1/sutra.schema.json` (Core) | document structure |
| Language definition | `spec/1/rachana.lang.json` | every key, panel kind, option, function, format, tone, problem code, limit, each with `profile` and `since` |
| Rachana-EL grammar | `spec/1/rachana-el.ebnf` (section 4 below) | expression syntax |
| Function catalogue | `spec/1/functions.yaml` | every function: signature, types, null and error behaviour, examples |
| ViewModel schema | `spec/1/viewmodel.schema.json` | the compiler's output (see [DECISIONS](DECISIONS.md) D6) |
| Data Protocol | `spec/1/DATA_PROTOCOL.md` + schemas | tables and live changes |
| Conformance suite | `conformance/1/` | the executable meaning; where text and suite disagree, it is a defect in one of them and a release blocker |

**One source of truth.** Today the parser's own tables (`PanelKind`, `ControlKind`, `Functions`, `Wiring`, `Form`,
`PivotSpec`) are the truth and `RachanaSchema` generates the JSON Schema from them, so an editor never offers what the
parser refuses. That stays. `rachana.lang.json` and `sutra.schema.json` are **generated from the reference compiler's
tables at build time** and committed; a gate test fails when a committed file is stale. Nothing is written by hand
twice.

**Publication.** Each release of this repository publishes `spec/<major>/` unchanged at a stable address
(`https://ajsinha.github.io/drishti-rachana/spec/1/...`, the pattern Drishti Elements uses for its docs site). The
schema's `$id` is that address. Tagged releases are immutable; `spec/1/` on `main` is always the newest minor of
major 1.

## 2. Document structure

### 2.1 The file

- A Sutra is **one YAML document** in one file named `<name>.sutra.yaml` (a version may be part of the name:
  `<name>.v<N>.sutra.yaml`, today's form, still read).
- YAML is read strictly, as today: a key written twice in one mapping, a second document after `---`, and any tag
  (`!x`) are errors (`RCH-2033`). Anchors and aliases are allowed. Every node keeps its line and column.
- The first key is `rachana: 1`: the language's **major** version. A missing or unknown value is `RCH-2009`.
- An unknown key is an error with its position, never ignored (`RCH-2011`, `RCH-2023` for a panel option).

### 2.2 Top level (Sutra Core)

| Key | Required | Type | Default | Meaning | Status |
|---|---|---|---|---|---|
| `rachana` | yes | integer | | language major version | today |
| `sutra` | yes | text `[a-z][a-z0-9-]{1,63}` | | the screen's name | today |
| `version` | | integer ≥ 1 | `1` | `name@version` identifies a Sutra | today required; optional is planned |
| `profiles` | | list of text | `[core]` | profiles the file needs; the checker verifies the claim | planned |
| `description`, `notes` | | text | | prose for people; not used to build the view | today |
| `doc` | | mapping | | docstring for component browsers and AI: `summary`, `params`, `emits`, `examples`, `since` | today |
| `title` | | text template **or** `{pill?, id, with?}` | the name | heading; the text form is planned shorthand | mapping today |
| `strip` | | list (≤ 8) of `{label?, bind, fmt?, tone?, emphasis?}` | empty | headline figures | today |
| `controls` | | list (≤ 12) | empty | what the reader sets; read as `ctl.<id>` | today |
| `data` | | mapping name → source | empty | the screen's tables and records; each is a field of `$` (section 2.4) | **planned** |
| `panels` | | list of panels and `{use: …}` entries | empty | the body | today |
| `layout` | | mapping | panels flow | areas on a 12-column grid, split, tabs, sections, sticky, phone and tablet pictures | today |
| `phone` | | mapping | | phone order, default tier, summary | today |
| `keys` | | mapping `F1`..`F12` → action | empty | function keys | today |
| `refresh` | | seconds or `{every, user-can-pause}` | none | re-read interval for top-level panels | today |

Keys that exist today but belong to a host profile, not to Core: `match`, `domain` (see
[CORE_AND_PROFILES](CORE_AND_PROFILES.md)). A Core runtime knows them and answers with a teaching message
(`RCH-2060`: "'match' belongs to the profile of a data platform; a Core screen reads its data from 'data:'"), never
"unknown key".

### 2.3 Panels

A panel is `{id, kind, …}`. Common keys (today): `title` (template), `description`, `key`, `code`, `area`, `order`,
`section`, `span` (1..12), `height` (1..24), `collapsible`, `phone`, `refresh`, `when`, `where`, `on-select`, `source`,
`emits`, `waits`, `children`, `columns`, `form`, `infer`.

Core panel kinds and their options are the reference compiler's `PanelKind` table. Each kind has required options and
accepted options; anything else is `RCH-2023`.

| Kind | Requires | Accepts |
|---|---|---|
| `kv` | | `rows`, `columns`, `fields` |
| `table` | `rows` | `totalLabel`, `limit`, `moreLabel`, `link`, `highlight`, `search`, `pivot`, `children`, `expand`, `levels`, `crumb`, `root`, `empty` |
| `ladder` | `rows` | as `table` without `limit`, `moreLabel`, `link` |
| `tabs` | `each` | `tabTitle`, `layout`, plus `body` or `panels` |
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

`links` and `provenance` (today's 21st and 22nd kinds) describe a data platform's entity graph and are profile kinds.
A profile registers further kinds (CORE_AND_PROFILES section 3).

### 2.4 Data in a Sutra (planned; this settles the keys)

`data:` names the screen's inputs. **Each entry becomes a field of `$`**: a table is a list of rows, a record is an
object. Every existing expression then works unchanged (`rows: $.sales`, `sum($.sales, 'revenue')`,
`$.sales[?@.region == ctl.region]`).

```yaml
data:
  sales:   { table: sales, key: [region, month] }         # a name the host resolves (Data Protocol)
  targets: /data/targets.csv                               # shorthand for { url: /data/targets.csv }
  regions: { rows: [ { code: N, name: North }, { code: S, name: South } ] }
  summary: { url: /api/summary.json, as: record }
```

A source written as text is `{ url: <text> }`. Written as a mapping it has **exactly one** of:

| Key | Meaning |
|---|---|
| `table: <name>` | A table **the host resolves by name** through the Rachana Data Protocol: a file in static mode, an endpoint in an app, a governed table in a platform. The same Sutra runs in all of them. |
| `url: <address>` | A file or endpoint read as a whole: `format: json \| csv \| ndjson \| arrow` (from the extension when absent), `path` picks the rows out of a wrapped JSON answer. Address rules are the host's allow-list; an address is never built by an expression. |
| `rows: [...]` / `record: {...}` | Data written in the file (templates, tutorials, tests). |

Common keys on any source: `key` (field or list of fields that identify a row; required for live changes, selection
and drills), `fields` (`{name: {type, label?, fmt?}}` when inference is not enough), `as: rows | record`, `where`
(an expression over each row `@`, which may read controls), `limit`, `refresh` (seconds), `live: true | false`
(subscribe to the table's changes; default true when the host offers them), `mode: auto | client | server`.

The panel-level `source:` (today) keeps its meaning: `source: { table: <name>, where?, limit? }` gives that one panel
its rows as `$src`. In Core, `<name>` is a `data:` entry or a host table. `source: { kind: … }` is a profile form.

There is deliberately no source type for "a function in the page", "REST pages", "a topic": a host exposes any of
these as a named table through the Data Protocol's provider interface, so the language has one way to name data.

## 3. Rachana-EL: values and types

### 3.1 Run-time values

| Value | Notes |
|---|---|
| empty | missing and `null` behave the same |
| boolean | `true`, `false` |
| number | IEEE 754 double; a whole number prints without decimals; decimal functions (`round`, `power` with a whole exponent, `decimal`) work in exact decimal arithmetic and return a number |
| text | Unicode string; `""` is a text, not empty |
| date, timestamp | carried as ISO-8601 text (`2026-11-17`, `2026-11-17T09:30:00Z`); typed in checked mode |
| list | ordered values |
| object | name → value, in written order |
| link | made by `link(...)`; how it opens is the host's |
| masked | a value the reader may not see; prints `•••`; propagates through every operator and function; never truthy |

Truthiness: empty false; boolean as is; number true unless 0 or NaN; text true unless `""`; list and object true unless
empty; link true; masked never true.

### 3.2 Static types (checked mode)

Where column types are known (a `data:` table with a schema, a profile's models), expressions are type-checked before
they run. Types: `integer`, `decimal`, `number`, `string`, `boolean`, `date`, `timestamp`, `none`, `list`, `table`,
`any`. Rules (today, `ElType`): numeric types fit each other; `date` and `timestamp` fit each other; `any` and `none`
fit anywhere; `integer op integer` is `integer` for `+ - * %`, otherwise `decimal` if either side is, else `number`;
`/` is `decimal`; `date - date` is `integer` days. Checked mode only refuses more; it never changes what a text means.

### 3.3 Evaluation rules (normative; all exist today)

1. **Total paths.** A missing field, an index out of range, a path through a non-object: empty. Never an error.
2. **Null in, null out.** Arithmetic and scalar functions on empty give empty. `+` joins text when either side is not
   a number (`'x' + empty` is `x`).
3. **Division and remainder by zero** give empty.
4. **Equality.** Numeric when either side is a number (`1 == '1.0'`), otherwise text, case-sensitive. Empty equals
   only empty; `empty != 'X'` is true.
5. **Ordering.** Numeric when both read as numbers, else text by code point (ISO dates order correctly). Anything
   against empty is false.
6. **`&&`, `||`** short-circuit and return a boolean, not an operand.
7. **Membership** `x in list` is strict: an empty list holds nothing; a non-list is a list of one.
8. **Masked** carries through everything; a condition on a masked value is never true.
9. **Determinism.** No function reads a clock, a locale or a random source except through the evaluation context
   (`today()`, `asof()`), which the host sets. The same inputs give byte-identical output in every runtime.
10. **Bounded.** Nesting ≤ 200, length ≤ 10,000 characters, a list literal ≤ 1,000 items (settings
    `rachana.max-expression-depth`, `rachana.max-expression-length`). Beyond a bound is a compile error with a position.

## 4. Rachana-EL: the grammar

Derived from the reference lexer and parser (`el/Lexer`, `el/Parser`). ISO 14977 EBNF; terminals in quotes.

```ebnf
(* ---- lexical ---- *)
letter      = ? any Unicode letter ? ;
digit       = "0" | "1" | "2" | "3" | "4" | "5" | "6" | "7" | "8" | "9" ;
ws          = ? Unicode white space ? ;                       (* allowed between any two tokens *)
ident       = ( letter | "_" ) { letter | digit | "_" } ;
number      = ( digit { digit } [ "." { digit } ] | "." digit { digit } ) [ ( "e" | "E" ) [ "+" | "-" ] digit { digit } ] ;
string      = "'" { schar - "'" } "'" | '"' { schar - '"' } '"' ;
schar       = "\" ? any character ?                           (* the next character, literally; no \n escapes *)
            | ? any character except the closing quote and "\" ? ;
panel-id    = letter { letter | digit | "_" | "-" ( letter | digit ) } ;

(* ---- expressions, lowest binding first ---- *)
expr        = or [ "?" expr ":" expr ] ;                      (* right associative *)
or          = and { "||" and } ;
and         = equality { "&&" equality } ;
equality    = compare { ( "==" | "!=" ) compare } ;
compare     = sum { ( "<" | "<=" | ">" | ">=" | "in" | "not" "in" ) sum } ;
sum         = product { ( "+" | "-" ) product } ;
product     = unary { ( "*" | "/" | "%" ) unary } ;
unary       = ( "!" | "-" ) unary | postfix ;
postfix     = primary { "." ident                             (* field; after [*], that field of each element *)
                      | "[" "*" "]"                           (* projection: every element *)
                      | "[?" expr "]"                         (* filter: elements for which expr, with @ bound, is truthy *)
                      | "[" expr "]" } ;                      (* index (number, negative from the end) or key (text) *)
primary     = number | string | "true" | "false" | "null"
            | "$"                                             (* the screen's record *)
            | "@"                                             (* the current row or element *)
            | "^" { "^" }                                     (* parent of the current element; ^^ its parent; no space between *)
            | "#index"                                        (* 0-based position of the current row; -1 outside a row *)
            | "$src"                                          (* what the panel's source: names *)
            | "@" panel-id                                    (* a panel's events: @trades.select.id; @parent in a child *)
            | "@acc"                                          (* only inside reduce's third argument: the value so far *)
            | "ctl" "." ident                                 (* the value of a control *)
            | "[" [ expr { "," expr } [ "," ] ] "]"           (* list literal *)
            | "(" expr ")"
            | let | call
            | ident ;                                         (* a let name, else a field of @ in a row, else of $ *)
let         = "let" "(" ident "=" expr "," { ident "=" expr "," } expr ")" ;
call        = ident "(" [ args ] ")" ;
args        = arg { "," arg } [ "," ] ;
arg         = expr | ident "=" expr ;                         (* named arguments come after positional ones *)

(* ---- templates: text with embedded expressions ---- *)
template    = { text-part | "${" expr "}" } ;                 (* braces inside expr are matched *)
```

Rules the grammar cannot show:

- Reserved words: `true`, `false`, `null`, `ctl`, `in`, `not`. They cannot be `let` names.
- `**` and `//` are refused with their Rachana form (`power(x, y)`, `floor(x / y)`); `&`, `|`, `;`, a lone `=` outside
  `let` and named arguments, and a lone `#` are errors.
- `$.day-count` is `$.day` minus `count`; a name with other characters is written `$['day-count']`.
- `if(c, a, b)` and `ifs(c1, a1, …, else)` are the conditional in function form; `reduce` that adds `@acc` is refused
  in favour of `sum`.
- A function name must be known, with an argument count in range, at compile time.
- A named argument must be one of the function's parameters, given once.

Precedence (lowest to highest): `?:` · `||` · `&&` · `== !=` · `< <= > >= in not-in` · `+ -` · `* / %` · unary `! -` ·
postfix. All binary operators group left; `?:` groups right.

## 5. Functions

The set is **closed per profile**: a name outside it is a compile error that lists near matches. Core's catalogue
(`spec/1/functions.yaml`, generated from the reference's `Functions` table and `signatures.yaml`):

| Family | Functions (Core) |
|---|---|
| logic | `if ifs switch coalesce nonblank iferror one_of within let is_blank is_boolean is_date is_error is_number is_text type_of` |
| math | `abs ceiling floor round round_up round_down round_sig mround mod power sqrt exp ln log log10 sign clamp even odd fact gcd lcm combin permut choose pi atan2 base decimal least greatest` |
| text | `upper lower proper trim clean len left right mid find search contains starts_with ends_with exact replace rept pad concat split text text_before text_after value char code matches matches_case regex_extract regex_replace fmt` |
| dates | `date timestamp today asof year quarter month week day weekday days days_between days360 datedif edate eomonth yearfrac intck shift_date is_business_day business_days_between` |
| lists | `size first last take drop reverse sort unique flatten map reduce sequence has any_of all_of group` |
| aggregates over a list | `sum count avg min max median percentile distinct wavg variance stddev variance_pop stddev_pop` |
| statistics | `norm_dist norm_inv norm_s_dist norm_s_inv t_dist t_inv chisq_dist chisq_inv f_dist f_inv beta_dist beta_inv gamma gamma_ln gamma_dist gamma_inv binom_dist binom_inv negbinom_dist poisson_dist expon_dist weibull_dist lognorm_dist lognorm_inv hypgeom_dist confidence_norm confidence_t standardize fisher fisher_inv erf erfc` |
| links | `link` (the value is Core; what a target means is the host's) |

Each catalogue entry has: `name`, `family`, `signature`, `params` (`name`, `type`, `default`, `variadic`,
`keyword-only`), `returns`, `scopes` (`row`, `rule`, `aggregate`, `context`), `pushdown` (may a server evaluate it),
`incremental` (can a live change update it without a rescan), `nulls`, `errors`, `examples` (each example is also a
conformance case), `since`, `profile`.

**R0 work item.** Today `signatures.yaml` carries full signatures for 71 functions; the rest have a name and an
argument range only. R0 completes the catalogue for every Core function.

Functions that need a model or a platform are profile functions and are not in Core: `measure`, `cell`, `calc`,
`share_of`, `parent_of`, `as_of`, `convert`, `fx`, `related`, `related_rows`, `label`, the time-intelligence and window
families over a model (`ytd`, `rank`, `moving_avg`, …). See CORE_AND_PROFILES section 3.3. Dates on business
calendars (`shift_date`, `business_days_between`, `is_business_day`) are Core and read the calendar the host supplies;
without one, weekends are the only non-business days.

Formats (`fmt: amount0`) and tones (`tone: sign`) are named, never styled. Core's names are the tables shared with
Drishti Elements (`formats.yaml`); a profile adds names.

## 6. Versions and compatibility

### 6.1 What is versioned

| Thing | Version | Where it appears |
|---|---|---|
| Language | `major.minor` (`1.4`) | major in the file (`rachana: 1`); minor in each definition entry's `since` |
| Schema, definition, grammar, catalogue | the language's | `spec/1/…`, `x-rachana-language: "1.4"` inside |
| ViewModel | own `major.minor` | `spec` field of a ViewModel |
| Data Protocol | own major | `v` in the handshake, `/v1/` in addresses |
| Conformance suite | the language's | `conformance/1/`, each case has `since` |
| Runtime packages | semantic version of this repository | reports the language minor it implements |

### 6.2 Within `rachana: 1` (minor versions)

1. **Additions only**: new keys, options, kinds, functions, formats, problem codes. Each carries `since`.
2. **Nothing is removed and nothing changes meaning.** A Sutra valid in 1.x is valid, with the same ViewModel, in every
   later 1.y. The conformance suite of 1.x passes unchanged on 1.y; this is a release gate.
3. **Deprecation** is a warning (`RCH-2090`, with the replacement as a machine-applicable fix), never an error.
   Old spellings (`startsWith` for `starts_with`) keep compiling and are never offered by editors.
4. **Stricter checks arrive as warnings first.** A check that would refuse existing files ships as a warning for at
   least two minors, and becomes an error only in the next major.
5. A runtime given a key newer than itself says so: "`flash` needs Rachana 1.4; this runtime is 1.2" (`RCH-2008`).
6. Problem **codes are stable**: a code never changes meaning. Message wording may improve; tools match on codes.

### 6.3 From `rachana: 1` to `rachana: 2`

1. A major is cut only for a change that cannot be an addition (removing deprecated keys, changing a default or a rule
   of evaluation). Expected rarely; none is planned.
2. Every runtime of major N **reads N and N−1**. Support for N−1 ends no sooner than 24 months after N ships.
3. `rachana migrate` rewrites a file from N−1 to N mechanically, keeping comments and layout (it uses the positional
   editing operations of the tools module). Every breaking change must come with its migration rule; a breaking
   change without one is not accepted into the language.
4. A registry may hold files of both majors at once; the major is read per file.
5. `spec/2/` and `conformance/2/` are new folders; `spec/1/` stays published for ever.
6. The ViewModel and the Data Protocol version independently, so a language major does not force a painter or a
   server upgrade.

### 6.4 Profiles and versions

A profile versions with the host that owns it. A profile may add; it may not change the meaning of anything in Core,
and it may not make a valid Core Sutra invalid. The conformance suite for Core runs with every profile loaded
(a gate in the host, see [SPLIT_PLAN](SPLIT_PLAN.md)).

## 7. The error model

Errors exist for people and for machines (editors, CI, AI fix loops). One shape everywhere.

### 7.1 A problem

```json
{
  "code": "RCH-2023",
  "severity": "error",
  "message": "option 'limit' is not valid for 'ladder' panels",
  "file": "sales.sutra.yaml",
  "range": { "start": { "line": 31, "column": 5, "offset": 812 }, "end": { "line": 31, "column": 10, "offset": 817 } },
  "path": "/panels/2/limit",
  "expr": { "source": "sum($.sales 'revenue')", "start": 12, "end": 21 },
  "expected": ["totalLabel", "highlight", "search", "pivot", "children", "expand", "levels", "crumb", "root", "empty"],
  "fix": { "title": "Remove 'limit'", "edits": [ { "range": { "start": {"line": 31, "column": 5}, "end": {"line": 32, "column": 1} }, "text": "" } ] },
  "doc": "https://ajsinha.github.io/drishti-rachana/problems/RCH-2023"
}
```

| Field | Always | Meaning |
|---|---|---|
| `code` | yes | stable identifier, `RCH-` and four digits |
| `severity` | yes | `error` (the Sutra does not load), `warning`, `info` |
| `message` | yes | one sentence in plain words that names the thing and says what is allowed |
| `file` | when known | as given to the compiler |
| `range` | yes | 1-based line and column, 0-based character offset; `end` is exclusive |
| `path` | yes | JSON Pointer to the YAML node, so a tool can find it without positions |
| `expr` | for expression problems | the expression's source and the 0-based span inside it |
| `expected` | when a closed set applies | the valid names (options, kinds, functions, fields), nearest first |
| `fix` | when one is certain | text edits that resolve the problem; applying it must not create a new problem of the same code |
| `doc` | yes | a stable page for the code |

A compile returns **all** problems of a file, not the first (today's behaviour). The result is
`{ "ok": false, "language": "1.4", "problems": [ … ] }`; an API wraps the same list (HTTP 422 for a host that serves it).

### 7.2 Code ranges

Core keeps today's numbers so existing knowledge and tests carry over; only the prefix changes from the product's
`DRS-` to `RCH-`. The product maps one to the other in its profile adapter (see [DECISIONS](DECISIONS.md) D8).

| Range | Family | Examples (existing) |
|---|---|---|
| `RCH-2001`..`2009` | file and YAML | `2001` YAML syntax, `2004` file name, `2008` newer than this runtime (new), `2009` language version |
| `RCH-2010`..`2039` | structure | `2010` missing key, `2011` unknown key, `2012` wrong shape, `2020` bad name, `2021` unknown kind, `2022` missing option, `2023` invalid option, `2024` duplicate id, `2029` bad option value, `2030` span/height, `2031` pivot, `2033` strict YAML, `2034`..`2039` controls |
| `RCH-2048`..`2059` | reuse, docstring, wiring, forms, layout | `2048` block not found, `2055` docstring, `2056` children depth, `2057` event or source read, `2058` form, `2059` layout |
| `RCH-2060`..`2069` | profiles (new) | `2060` key of a profile this runtime does not have, `2061` profile claimed but not needed, `2062` unknown profile |
| `RCH-2070`..`2079` | data (new) | `2070` unknown table, `2071` unknown column (with `expected`), `2072` key missing for a live table, `2073` source shape, `2074` not push-down-able over a server table |
| `RCH-2090`..`2099` | deprecation and style (warnings) | `2090` deprecated spelling |
| `RCH-2101`, `2102` | expressions | `2101` does not compile, `2102` evaluation error (a function given a wrong value; `iferror` catches it) |
| `RCH-9127`, `9129`, `9152`, `9161` | checked mode | type or scope, not yet available, unknown name with "did you mean", masked field in a filter |

Codes outside these ranges belong to a host (governance, packs, API envelopes) and are not part of the specification.

### 7.3 Run-time states are not errors

A panel whose data is absent, denied, pending or failed is a **state** of that panel in the ViewModel
(`empty`, `denied`, `pending`, `error`, `stale`), with words; the rest of the screen is drawn. Only a Sutra that does
not compile produces problems.

### 7.4 Contract for fix loops

- Messages never contain data values from the user's tables, only names; a problem is safe to send to a model.
- `expected` is capped at 40 names; the rest are summarised (`"more": 112`).
- Problems are sorted by position; the order is deterministic and part of conformance.
- Applying every `fix` of a result and compiling again must not increase the number of problems (a conformance rule).
