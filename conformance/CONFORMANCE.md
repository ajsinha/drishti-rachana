<!-- Copyright (c) 2026 Ashutosh Sinha. Specification licence: ../LICENSE-SPEC.md (draft, not yet in force). -->
# Rachana conformance suite

The conformance suite is the **executable form of the specification** ([../spec/1/SPEC.md](../spec/1/SPEC.md)): a set of cases, each
an input and the exact output a conforming runtime gives. Where the specification's prose and a case disagree, that is
a defect in one of them and a release blocker; until it is fixed, the case wins (SPEC section 0).

"Is Rachana" means: passes `conformance/<major>/` for the profile and minor claimed ([RUNNER.md](CONFORMANCE.md#the-runner-contract) section 7).

## Contents

| File | What it is |
|---|---|
| [FORMAT.md](CONFORMANCE.md#conformance-case-format-rachana-1) | the case format: files, the eleven kinds and their fields, values, how expected problems are matched (code, path, range), comparison, canonical JSON, number text pinned to the Java reference |
| [schema/case.schema.json](schema/case.schema.json) | the JSON Schema (draft 2020-12) every case file validates against |
| [RUNNER.md](CONFORMANCE.md#the-runner-contract) | the contract every runtime's runner implements: options, the results file, statuses, exit codes, cases on stdin for third-party runtimes, record mode, the gate |
| `1/` | the cases of language major 1, one folder per area |
| `1/<area>/examples/` | hand-written examples of the format, one file per kind; ordinary cases |

## The kinds

| Kind | Pins | Area |
|---|---|---|
| `expr` | an expression's value, type, text form and the paths it read | `el/` |
| `expr-error` | an expression that does not compile: code, message, offsets | `el/` |
| `check` | checked mode: the type, or the refusal | `el/` |
| `grammar` | the grammar accepts or refuses a text | `grammar/` |
| `format` | a format and a tone applied to a value | `format/` |
| `sutra` | Sutra + data → the ViewModel | `sutra/` |
| `problem` | a Sutra's complete, ordered problem list | `problem/` |
| `patch` | table patches → ViewModel patch frames | `patch/` |
| `plan` | what is asked of each table: columns, filter tree, push-down | `plan/` |
| `edit` | an editing operation → the new text, comments kept | `edit/` |
| `protocol` | a Data Protocol host's answers | `protocol/` |

## Running

```text
rachana conformance run                              # this runtime, conformance/1, results/<runtime>.jsonl
rachana conformance run --cmd "./my-port --stdio"    # a third-party runtime, cases on stdin
rachana conformance protocol --base http://host/data/v1
```

The JVM runner runs on every build of this repository (and in a host from a pinned cases jar); the
other runtimes run the same cases. The gate: the reference passes and every runtime's results file is
byte-identical to the reference's (RUNNER section 7).

## Writing a case

Read [FORMAT.md](CONFORMANCE.md#conformance-case-format-rachana-1) section 10. In short: self-contained, one point per case, expect only what is certain,
`verified: false` with a `source` until the reference has confirmed it, validate against the schema before committing.

## Licence

Everything in `conformance/` is covered by [LICENSE-SPEC.md](../LICENSE-SPEC.md) (draft, not yet in force): free to
read, implement and test against.

## Conformance case format, `rachana: 1`

After the freeze a change to this
format is an addition only (a new kind, a new optional field), never a change of meaning. The machine-readable form is
[schema/case.schema.json](schema/case.schema.json) (JSON Schema draft 2020-12); where this text and the schema
disagree, that is a defect in one of them. How a runtime runs the cases is [RUNNER.md](CONFORMANCE.md#the-runner-contract).
`PROTOCOL n` below is a section of the Data Protocol, [../spec/1/data-protocol/PROTOCOL.md](../spec/1/PROTOCOL.md).

### Contents

1. [Files and folders](#1-files-and-folders)
2. [The case file](#2-the-case-file)
3. [Fields every case has](#3-fields-every-case-has)
4. [Values in cases](#4-values-in-cases)
5. [The kinds](#5-the-kinds)
6. [Matching expected problems](#6-matching-expected-problems)
7. [Comparing expected and actual](#7-comparing-expected-and-actual)
8. [Canonical JSON](#8-canonical-json)
9. [Number text, pinned to the Java reference](#9-number-text-pinned-to-the-java-reference)
10. [Rules for case authors](#10-rules-for-case-authors)

### 1. Files and folders

```text
conformance/
  README.md  FORMAT.md  RUNNER.md
  schema/case.schema.json        the schema every case file validates against
  1/                             the suite of language major 1 (SPEC section 8)
    el/          expr, expr-error and check cases
    grammar/     grammar cases
    format/      format cases
    sutra/       sutra cases
    problem/     problem cases
    patch/       patch cases
    fire/        fire cases (a trigger fired on a mark: state, effects, refusals)
    plan/        plan cases
    edit/        edit cases
    protocol/    protocol cases (run against a Data Protocol host, not a compiler)
    lib/         lib cases (one library folder read and checked)
    <area>/examples/   hand-written examples of this format, one file per kind; ordinary cases, run like any other
```

- One YAML file per **group** of related cases: `conformance/1/<area>/<group>.yaml`, at most one folder level below
  the area (`examples/` is the only one so far). File names are lower case, `[a-z0-9-]`, ending `.yaml`.
- A file's **suite name** is its path below `conformance/1/` without `.yaml`: `el/arithmetic`,
  `el/examples/arithmetic`. A case's **case id** is `<suite>#<id>`: `el/examples/arithmetic#add-two-numbers`. Case
  ids are stable for ever; a case is never renumbered or renamed, only added (or removed with the defect it pinned).
- Files are UTF-8 without a byte-order mark, LF line ends (`.gitattributes` enforces `eol=lf` for `conformance/**`),
  and start with the specification-licence comment.
- A future major gets `conformance/2/`; `conformance/1/` stays and keeps passing on every runtime that reads major 1.

### 2. The case file

A case file is **one YAML 1.2 document**, read with the YAML 1.2 *core* schema and the strict rules of SPEC 2.1:
no repeated key, no second document, no tags. Anchors and aliases are allowed (to share a table between cases).
`2026-01-15` unquoted is a string, `yes`/`no`/`on`/`off` are strings, only `true`/`false` are booleans and only
`null` or `~` is null. Mapping order is kept.

```yaml
# Copyright (c) 2026 Ashutosh Sinha. Specification licence: ../../../LICENSE-SPEC.md (draft, not yet in force).
suite: el/arithmetic
since: "1.0"
profile: core
verified: false
given: { today: "2026-01-15" }          # defaults merged into every case of the file (section 3)
cases:
  - id: null-in-null-out
    kind: expr
    given: { record: { a: 2 } }
    expr: "$.missing * 2"
    expect: { value: null, type: empty, text: "" }
```

| Field | Required | Meaning |
|---|---|---|
| `suite` | yes | MUST equal the file's suite name (section 1); a runner refuses a file where it does not |
| `since` | yes | the language minor (`"1.0"`, quoted) every case of the file needs, unless a case says a later one |
| `profile` | yes | the profiles the cases need: `core`, or a list (`[core, drishti]`); section 3 |
| `verified` | no, default `false` | the default for the cases' `verified` |
| `description` | no | what the group covers, for people |
| `given` | no | inputs shared by every case of the file; a case's own `given` overrides it key by key (one level deep: a case that gives `record` replaces the file's `record` whole) |
| `cases` | yes | the cases, in a fixed order; at least one |

### 3. Fields every case has

| Field | Required | Meaning |
|---|---|---|
| `id` | yes | `[a-z0-9][a-z0-9-]{0,79}`, unique in the file |
| `kind` | yes | one of the kinds of section 5 |
| `since` | no | a later minor than the file's, when this case pins something added in it |
| `until` | no | the last minor the case applies to, when a later minor answers it differently (the later answer is its own case with that `since`); a runtime of a later minor does not run it |
| `profile` | no | overrides the file's `profile` |
| `verified` | no | `true` once the reference runner (JVM) has produced exactly `expect` and a person has reviewed it; `false` for a case written by hand or by reading, not yet confirmed. Only the reference's record mode (RUNNER section 6) helps turn `false` into `true`; a person flips it |
| `source` | no | where the case came from: a test method of the reference (`ElTest#nullArithmetic`), a SPEC section (`SPEC 4.5`), a function example (`functions.yaml#round`), a defect number |
| `note` | no | one or two sentences for people; never read by a runner |
| `tags` | no | free words for filtering (`determinism`, `mask`, `bmp`, `slow`) |

**Profiles.** `core` cases pin Sutra Core and MUST pass with every profile loaded (SPEC 7.4); a runner always runs
them. A case whose `profile` names another profile (`[core, drishti]`) runs only on a runtime that has that profile
and is reported `unsupported` elsewhere. A profile's cases live in that profile's own suite, not here, except where a
Core rule is about profiles (`RCH-2060`: a profile key in a runtime without the profile is a `core` case).

**Versions.** An implementation of `rachana: 1.x` runs every case with `since` ≤ `x` and reports the others
`unsupported`. Cases never change when a minor ships (SPEC 7.2): a later minor adds cases.

### 4. Values in cases

Cases hold values in three places: inputs (`given.record`, `given.tables`, `value` of a `format` case), expected
values (`expect.value`) and documents (`expect.viewModel`). All three are the JSON data model: null, boolean, number,
string, array, object (keys in order). Rachana values that JSON cannot hold are written as **tagged objects**: an
object of **exactly one** key from this table.

| Rachana value | Written | Notes |
|---|---|---|
| empty | `null` | missing and `null` are the same value (SPEC 4.2) |
| NaN, +∞, −∞ | `{ $num: NaN }`, `{ $num: Infinity }`, `{ $num: -Infinity }` | the strings exactly; in a ViewModel these are `null` (TRANSLATION 8) and never tagged |
| an exact decimal a double cannot hold | `{ $dec: "0.1000000000000000000001" }` | plain digits, optional `-`, no exponent; any decimal a double holds is written as a number |
| a masked value | `{ $masked: true }` | the same tag as the Data Protocol's (PROTOCOL 3.2); in inputs it stands for the host's `Masker` having masked the value |
| a link | `{ $link: { id: CP-1, kind: counterparty, label: Acme } }` | `kind` and `label` optional; the result of `link(id, kind?, label?)` |

Every other object is a Rachana object. A record field whose name starts with `$` cannot be written in a case in
`rachana: 1` (none exists in the reference's tests); if one is ever needed, a new tag is added for it.

**Numbers in case files** are read as IEEE 754 doubles and compared by their canonical text (section 9), never as
parsed doubles. Write the shortest decimal that reads back to the double you mean (`0.30000000000000004`, `1e20`,
`0.1`); `1e20` and `1.0E20` in a case file are the same number. A whole number above 2⁵³ that a double cannot hold
exactly is refused by the runner (write it as a string or `$dec`).

**Dates** are ISO text, as in Rachana (SPEC 4.2): quote them in YAML out of habit, though the core schema keeps them
text anyway.

### 5. The kinds

Fourteen kinds. Every kind is a pure function from the case's inputs to one **actual** document; a runner writes that
document in full on the case's results line (RUNNER section 4) and compares the parts `expect` names (section 7).

| Kind | Area | Input fields | Actual document (all fields always written) | Typical `expect` |
|---|---|---|---|---|
| `expr` | `el/` | `expr` or `template`, `functions`, `given` | `{value, type, text, reads}`, or `{error: {code, message}}` when evaluation raised | `value`, `type`, `text`, `reads`, or `error` |
| `expr-error` | `el/` | `expr` or `template`, `functions`, `given.limits` | `{problems: [problem…]}` from compiling the expression alone | `problems` |
| `check` | `el/` | `expr`, `scope`, `types`, `given.limits` | `{type, problems}` from checked mode (SPEC 4.14) | `type`, or `problems` |
| `grammar` | `grammar/` | `text`, `rule` | `{accepted}` | `accepted` |
| `format` | `format/` | `value`, `format`, `tone` | `{text, tone}` (`tone` null when none) | `text`, `tone` |
| `sutra` | `sutra/` | `sutra`, `file`, `files`, `given` | `{viewModel}` | `viewModel`, or `at` |
| `problem` | `problem/` | `sutra`, `file`, `files`, `given.profiles`, `given.limits` | `{ok, problems}` (SPEC 6.1, all problems in order) | `ok`, `problems` |
| `patch` | `patch/` | `sutra`, `file`, `files`, `given`, `steps` | `{viewModel, steps: [{frames, viewModel}…]}` | `steps` with `frames` or `at` |
| `fire` | `fire/` | `sutra`, `file`, `files`, `given`, `steps` | `{viewModel, steps: [{state, effects, problems, viewModel}…]}` | `steps` with `state`, `effects`, `problems` or `at` |
| `plan` | `plan/` | `sutra`, `file`, `files`, `given` (with `schemas`) | `{plan}` | `plan`, or `at` |
| `edit` | `edit/` | `sutra`, `file`, `operation` | `{text, problems}` | `text`, or `problems` |
| `protocol` | `protocol/` | `fixture`, `steps` (requests) | `{steps: [{status, body}…]}` | `steps` with `status`, `body` |
| `lib` | `lib/` | `library`, `files` | `{ok, problems, version, sha384, fragments}` | `ok`, `problems`, `sha384` |
| `live` | `live/` | `declaration`, `file`, `profileKeys` | `{ok, problems, schema}` | `ok`, `problems`, `schema` |

#### 5.1 `given`: the context

| Field | Read as | Used by |
|---|---|---|
| `record` | `$`, the screen's record | `expr`, `sutra`, `patch`, `plan` |
| `row` | `@`, the current row or element | `expr` |
| `parents` | `^`, `^^`, …: nearest first | `expr` |
| `index` | `#index` (default `-1`) | `expr` |
| `ctl` | control values: `{ region: North }` → `ctl.region`; for a `sutra` case these are the request's control values (TRANSLATION 5) | `expr`, `sutra`, `patch`, `plan` |
| `state` | state values (level 1.2, SUTRA_EXPANSION S1): `{ order: O-7 }` → `state.order`; for a `sutra` case the request's `state.<id>` values (a list as its items) | `sutra`, `patch`, `plan` |
| `events` | what panels emitted: `{ trades: { select: { id: 7 } } }` → `@trades.select.id` | `expr`, `sutra` |
| `src` | `$src` | `expr` |
| `today` | the as-of date the host sets (`asof()`), ISO text; **required whenever the result could depend on it** (a case never reads a clock) | all but `grammar`, `edit`, `protocol` |
| `tables` | `data:` tables by name: a list of row objects, or `{ schema, rows }` with a Data Protocol schema (PROTOCOL 2) | `sutra`, `patch`, `plan` |
| `schemas` | Data Protocol schemas by table name, for planning without rows | `plan` |
| `profiles` | profiles loaded, beyond `core` (the runner loads at least what `profile` names) | all |
| `limits` | host-lowered bounds of SPEC 4.12: `{ depth, length }` | `expr`, `expr-error`, `check`, `problem` |

A masked input is a `{ $masked: true }` in `record`, `row`, `tables` or `src`, standing for the host's `Masker`.

#### 5.2 `expr`

Exactly one of `expr` (an expression) or `template` (a template, SPEC 4.11). The expression is compiled, then
evaluated once with `given`.

`functions` (optional, with `expr`): a host's measure functions, `{ net: { params: [x], expr: "x - 1" } }` (SPEC 4.10 rule 9).
Each call of one is expanded in the expression's text first; the expanded text is what is compiled. A problem is `RCH-2101` at its
place in the expression's text: the offending token, or, for a problem in a function (its expression, a count of arguments, a call by
name, a cycle), the call written in the expression.

- `value`: the result as a value (section 4).
- `type`: `empty`, `boolean`, `number`, `decimal`, `text`, `list`, `object`, `link` or `masked`. `number` is a
  double; `decimal` an exact decimal (SPEC 4.10 rule 7).
- `text`: the value's text form (SPEC 4.2): empty `""`, masked `•••`.
- `reads`: the document paths the expression read, rooted at `$`, each written as in R-PATH (`$.legs[0].rate`), in
  first-read order without repeats. Paths below `@`, `ctl` and `@panel` are not listed.
- `error`: when evaluation raised (`RCH-2102`): `{code, message}`; matched as in section 6. A case that expects
  `error` expects no `value`.
- `tolerance`: only for a `number` result of a real-number function (SPEC 4.10 rule 7, 5.8, 5.9; SPEC 8 item 5):
  `{ abs: 1e-12 }` or `{ rel: 1e-9 }`. The case passes when `|actual − expected|` is within it; the results line still
  carries the actual text, so the byte gate across this project's runtimes is unaffected (RUNNER 5). A case with a `tolerance`
  names no `text` (the digits are what the tolerance loosens); a case whose expected digits are not the correctly rounded result
  of the function MUST carry one (SPEC 4.10 rule 7).

#### 5.3 `expr-error`

The expression (or template) alone does not compile (with `functions`, as 5.2: expanded first). `expect.problems` lists the problems in order (section 6). For an
expression compiled alone there is no file: `path` and `range` are absent, and the position is `expr.start` /
`expr.end`, 0-based offsets into the expression text (SPEC 6.1).

#### 5.4 `check`

Checked mode (SPEC 4.14). `scope` is `data` (a data scope: `ctl`, `@panel`, `$src`, `#index` refused) or `screen`.
`types` gives the known types: `{ record: { qty: integer, price: decimal }, row: { … } }`, each a map from field name
to one of SPEC 4.14's types, or a nested map for an object. `expect.type` is the checked type; `expect.problems` the
refusals (`RCH-9127`, `RCH-9129`, `RCH-9152`, `RCH-9161`). A case expects one or the other: a refused expression has
`type: null`. The actual is `{type, problems}`: every problem the checker finds, in order (both sides of `&&` that are not
conditions are two problems), each `{code, severity, message, fix?, expr: {source, start, end}}`; a warning (`RCH-2091`)
keeps the type.

#### 5.5 `grammar`

`text` is read by the grammar rule `rule` of [rachana-el.ebnf](../spec/1/rachana-el.ebnf): `expression` (default) or
`template`. `expect.accepted` is `true` or `false`. These cases hold the recogniser of the grammar proof and
the parser to the same answer: a text accepted by one and refused by the other is a defect. A runtime answers with its own
compiler at the case's level, so a case's text calls only functions the level has; a text the next level reads differently
is two cases, the earlier with `until`.

#### 5.6 `format`

`value` (section 4) formatted by the format `format` (a name of the shared format table, SPEC 2.6; optional: without
one, the value's text form) and given the tone word `tone` (optional). `expect.text` and `expect.tone` (`pos`, `neg`, `ok`, `warn`, `bad`, `link`, `accent`, or null).
These are R-FMT and R-TONE of TRANSLATION 1.1 on their own.

#### 5.7 `sutra`

`sutra` is the Sutra text (a YAML block scalar), `file` its file name (default `case.sutra.yaml`), `files` other
files by name for `use:` fragments: a `<name>.v<N>.fragment.yaml` (or `.block.yaml`) file is a fragment the host's
resolver serves in place of the profile's; `rachana_libs/<library>/<path>` files are the libraries a `LibraryResolver`
finds (one folder per library); `rachana.lock` is their lock; `contracts/<name>.json` is a published table contract the
host serves for its fragments (LIBRARIES 5.2) (`problem` and `patch` cases read `files` the same way). With `given`, the ViewModel is built (TRANSLATION). `expect.viewModel` is the
whole ViewModel; `expect.at` checks parts of it by JSON Pointer (section 7). Always dropped from the actual before
writing or comparing: `timings`, `provenance.fetchedAt`, `provenance.updatedAt` (TRANSLATION 0). The ViewModel's
schema is `spec/1/viewmodel.schema.json`; its key order is the order of output (section 8). A `pivot` panel's cells come from the
runner's own small pivot engine (a host hook in Core): `sum`, `count`, `avg`, `min` or `max` of each cell, subtotal and total
over its own rows; another aggregate is the host's and not run by a case.

#### 5.8 `problem`

The Sutra is compiled. `expect.ok` is `true` exactly when no problem has severity `error`. `expect.problems` is the
**complete, ordered** list (section 6); `[]` when there are none: the errors, then the advisories (severity `warning`,
such as `RCH-2120`). From level 1.1 each actual problem of the file compiled carries `path` and the full `range` (start
and end, each with `line`, `column`, `offset`); at level 1.0 a runtime writes `range.start` with `line` and `column` only, the
shape level 1.0 was recorded in (SPEC 6.1). When the Sutra uses libraries (level 1.2), the actual also has `libraries`, each
`{name, version, sha384}` sorted by name: the compile result's record of the library bytes it stands on; `expect.libraries`
compares it whole. For every `problem` case whose actual problems carry
a `fix`, the runner also applies every fix, compiles again and fails the case if the number of problems grew
(SPEC 6.1); a case need not ask for this.

#### 5.9 `patch`

The view is built as for `sutra`; then each entry of `steps` is applied in order. A step is `{ table, patch }` where
`patch` is a Data Protocol patch (`{ remove, update, add, reset }`, PROTOCOL 8.1) to the named table of
`given.tables`, which MUST have a `key`. The actual is the first ViewModel and, per step, the ViewModel patch frames
that step produced and the ViewModel after applying them. `expect.steps[i]` names `frames` (the exact frames, shape of
`spec/1/viewmodel.schema.json`) and/or `at` (parts of the ViewModel after that step). A step that changes nothing a
panel reads produces `frames: []`. No transport, timing or conflation is involved (those are `protocol` cases).

**The frames of a step.** After a step the runtime builds the view again and compares it with the view before the step. A patch
is made only for what **differs**: a strip figure, a panel, a dataset whose ViewModel is not the same as before. A panel that
reads the changed table but shows the same as before (a row added with a `0` under a sum) makes no patch. A step with at least
one patch produces **exactly one frame** holding all of them, in the order strip (by index), panels (in the ViewModel's order),
datasets; a step with none produces `frames: []` and uses no `seq`. The frame's header is fixed in this kind: `seq` counts the
frames this view has produced, from `1`, rising by one per frame; `generation` is `0` (the source generation is a host's fact, and
there is no host); `latencyMs` and `p99Ms` are `0` (no time is measured). A step that changes the screen's shape (the number of
strip figures or panels, a panel's id, the title, the controls, the state, the datasets' names) has no frames to give: the whole
ViewModel is sent again, and a case checks it with `at`.

#### 5.10 `plan`

What a runtime would ask of each table (SPEC 2.4, PROTOCOL 6 and 7): the tables read, the columns, the filter as a
syntax tree with controls replaced by their values, sorting, grouping, and what stays local. `given.schemas` replaces
rows. Cases check the plan document with `at`.

**The plan document** is defined here: `{ tables, problems }`. A host that prints a plan prints this document.

`tables` has one entry per `table` entry of `data:`, in the order written (`url`, `rows` and `record` entries are not planned):

| Field | Meaning |
|---|---|
| `entry` | the `data:` name |
| `name` | the host table it reads |
| `readers` | the ids of the panels that read `$.<entry>` anywhere (an option, a column, the title), in the Sutra's order, panels inside tabs and bodies included; a read by the strip or the screen's title adds no id |
| `live` | the entry's `live` (`false` when not written) |
| `query` | what is asked of the table, a query document of PROTOCOL 6 with only these fields, each left out when it does not apply: `columns`, `filter`, `limit`. `{}` reads the table whole |
| `local` | the row conditions the runtime applies itself, in the order met (the entry's `where`, then each reader's): `{ panel?, where, reason }`, `panel` only for a panel's condition, `where` the text as written |
| `window` | only when every reader is a `live` panel (SPEC 2.3): the largest `window` among them; `query.limit` is then that window, or the entry's `limit` when smaller |

Rules. (1) **The entry's `where`** goes into `query.filter` when the whole of it is in the push-down subset (PROTOCOL 7, written
as that section's "What the runtime writes" says: controls and every part that reads no column folded to literals, `== null` as
`is-null`) and the table can `filter` (a table whose schema is not known is taken to); otherwise it is `local`. (2) **A panel's
`where`** is pushed down by the same test only when that panel is the entry's **sole reader**: the only panel that reads it, reading
it as its `rows` (`rows: "$.<entry>"`) and nowhere else, with no strip or title reading it; otherwise it is `local`. (3) Several
conditions pushed down are one `&&` of them in the order met; one alone is itself. (4) `columns` is named only when the table's schema is known and the sole
reader's columns are all plain fields: those fields and the columns its conditions and the entry's read (pushed down or local), in
the schema's order; otherwise it is absent and every column is read. (5) `limit` is the entry's.

`reason` is one of these texts, exactly: `the table has other readers` (a panel's condition, rule 2); `outside the push-down subset
of the Data Protocol v1`; `the table cannot filter`; or `the condition does not compile: ` followed by the compiler's message.

`problems` lists what the plan found against the host's schemas (`given.schemas`), tables in order: `{ code, message, … }` with
`RCH-2070` for a table the host does not have (the entry is still listed), `RCH-2071` for a column the table
lacks, with `expected` (the table's columns, nearest first), and, from level 1.2, the checked-mode refusals of each row condition
read against the table's columns (SPEC 4.14): `RCH-9127`, `RCH-9129`, `RCH-9161`, each with `entry`, `panel` (for a panel's
condition), `where` (the condition as written) and `fix` when there is one. A condition with a problem is planned as rule 1 or 2
says all the same: the problems, not the plan, say that the screen is not to be shown. Without known schemas there are no problems.

#### 5.11 `edit`

`operation` is `{ op, … }`, an editing operation of the tools layer applied to `sutra`. `expect.text` is the whole new
text: comments, blank lines and layout outside the edit kept byte for byte. `expect.problems` is for an operation
refused (e.g. it would make the Sutra invalid). The operation names and arguments are those of the reference's
editing operations, the name in kebab case: `add-panel`, `move`, `set-option`, `bind`, `remove`, `set-title`, `set-strip`,
`set-controls`, `set-keys`, `set-block`, `text`, and a host profile's `set-match` (`edit/ops.yaml`).
One case applies one operation.

#### 5.12 `protocol`

Run against a Data Protocol host, not a compiler. `fixture` is the reference table the host must serve for the case
(`{ name, schema, rows }`, PROTOCOL 2 and 5); a host under test loads it through its test adapter. `steps` is a
list of `{ request, expect, capture }`:

- `request`: `{ method, path, query, body, headers }`; `path` is relative to the host's base; `${name}` in a string is
  replaced by a value captured earlier.
- `expect`: `{ status, body }`; `body` is compared as a **subset** (section 7): a host may add fields (PROTOCOL 14).
- `capture`: `{ name: "/json/pointer" }` into the step's response body (a cursor for the next page).

A `protocol` case has no top-level `expect`: each step carries its own.

A fixture may instead be a **live table** ([LIVE_TABLES](../docs/DESIGN.md#live-tables-design) 9): `{ name, declaration, rows, tier?,
sourceColumns?, options?, readers?, authoriser?, stream?, edit? }`, the declaration's text served from a source holding `rows`, loaded
through `POST /_test/live` (PROTOCOL 15). `edit: { sink?: outbox | applied | refuse | none, hold?, switch?: on | off, writerFails? }`
sets up the edit path (PROTOCOL 16 Writes); a reader's view may add `editable: [columns]`, `mayEdit` (the host's refusal), `approve: true` (the approve right over the tray) and `mayDecide` (the host's refusal of a decision; the adapter always refuses one on the caller's own change). Such a case runs only against a host with live tables; another host reports it
`unsupported`.

#### 5.13 `lib`

`files` is one library folder (paths inside it, `/` separated, text content), `library` its name. The library is read
and checked as a host loads it with no signature requirement ([LIBRARIES](../docs/DESIGN.md#sutra-libraries-the-fragment-library-framework) 3, 11, 12): the
file-type rule, the manifest, `MANIFEST.sha384` against every file, `exports` against the fragment files, the uses
inside it and the scope of its typed fragments. `ok` is `true` when it loads; `problems` as in `problem` cases;
`version` (the manifest's), `sha384` (SHA-384 of the canonical `.rlib` archive of the files, 96 hex digits: the
library's identity in a lock) and `fragments` (its fragment names, sorted) are `null` when it is refused. Signatures are
not part of this kind (a runtime need not carry Ed25519); they are tested by the JVM tools.

#### 5.14 `live`

`declaration` is a live table's declaration (`<name>.live.yaml`, [LIVE_TABLES](../docs/DESIGN.md#live-tables-design) section 2), read
and checked as a host loads it, every problem reported at once, in the order of their positions. `profileKeys` lists the
profile keys the host reads (`kind`, `query`, `links`, `edit.roles`, `edit.save`, `edit.fields.editable-by`; absent: none, so
each is a problem). Formats are checked against the runtime's format names. `ok` is `true` when it loads; `problems` as in
`problem` cases; `schema` is the table's Data Protocol schema document (section 4 of LIVE_TABLES: `rowVersion`, `changedBy`,
`changedAt`, `narrow`, `view`, column `heavy`), `null` when it is refused. What the engine adds at run time (`live`, `capped`,
a reader's masks and `write`) is not part of this kind; the `protocol/` suites test it.

#### 5.15 `fire`

Level 1.2 (SPEC 2.8). The view is built as for `sutra`; then each entry of `steps` fires a trigger as a painter fires it:
`{ panel, trigger, mark?, values? }`. `mark` (default 0) counts the marks the trigger lands on in the order the
ViewModel lists them: a table's rows, depth first through a tree's or a drill table's children (`row-click`); an
`hbar`'s bars (`bar-click`); a chart's points, steps, events or candles, a line or area chart's x positions, the one
mark of a metric, gauge or record, a pivot's cells and a surface's points row by row, an empty one keeping its place (`point-click`); a panel's buttons in order (`action`); the one form of a `form` panel
(`submit`, `cancel`, with `values` its field values: a `submit` is checked first against the form's fields and rules, each
failing check a problem `RCH-5003` and nothing runs, a value for no field `RCH-5001`; the effects read `values` where they
say `event`); or a control's change, `{ control, trigger: change, value }`: the control takes `value` (a text, a list for a
multi-select) and its `on.change` effects run with `event` the value (a control the screen lacks is `RCH-5001`). The effects the mark
carries run in order; the state they write goes into the next bind request and the view is built again. The actual,
per step: `state` (every state value of the screen after the step, id to value), `effects` (the effects run, in order:
`{do: set, state, value}`, and the ones handed to the host), `problems` (the refusals: a panel, trigger or mark the
screen does not have is `RCH-5001`, and nothing runs) and the `viewModel` after the step. `expect.steps[i]` names any
of `state`, `effects` (compared exactly), `problems` (section 6) and `at` (parts of that step's ViewModel).

### 6. Matching expected problems

A problem is SPEC 6.1's shape. An expected problem names some of its fields; **only the fields it names are compared**,
except that `code` is always named. Fields are matched as follows.

| Field | Matched | Notes |
|---|---|---|
| `code` | exactly, always | `RCH-` and four digits for Core; codes never change meaning (SPEC 6.2) |
| `severity` | exactly | when absent: `error` is expected |
| `path` | exactly | an RFC 6901 JSON Pointer to the YAML node (`/panels/2/limit`); `""` is the document |
| `range` | field by field: each of `start.line`, `start.column`, `start.offset`, `end.line`, `end.column`, `end.offset` that is given | 1-based line and column, 0-based offset, `end` exclusive, positions in UTF-16 code units (SPEC 4.13) |
| `expr` | each of `source`, `start`, `end` that is given | 0-based offsets inside the expression or template text |
| `message` | exactly, byte for byte | for words SPEC 6.3 pins |
| `messageStart` | the actual message starts with it | for messages with a list or an offset not pinned yet; never both `message` and `messageStart` |
| `expected` | the whole list exactly, in order; `more` exactly when given | nearest first (SPEC 6.1) |
| `fix` | the whole list of edits exactly | each edit `{ range, text }` |
| `file` | exactly | |
| `doc` | not compared | it is derived from `code` |

**Lists of problems** (`problems` of `expr-error`, `check`, `problem`, `edit`) are matched **in order and in full**: the
actual list has exactly as many problems as the expected one, and the n-th actual problem matches the n-th expected.
Order is SPEC 6.1's (by position; the suite pins ties). An evaluation error of an `expr` case (`expect.error`) is
matched by the same rules with `code` and `message` / `messageStart` only.

### 7. Comparing expected and actual

A case **passes** when every field `expect` names matches the actual document; fields `expect` does not name are not
compared (they still appear on the results line). `expect` MUST name at least one field (a `protocol` case's
steps each carry theirs).

| Comparison | Used for | Rule |
|---|---|---|
| exact | every field not listed below | the canonical JSON (section 8) of the expected and of the actual part are equal, byte for byte |
| problems | `problems`, `error` | section 6 |
| tolerance | `value` of an `expr` case with `tolerance` | numeric, as in 5.2; both sides finite numbers |
| at | `at` (`sutra`, `patch` steps, `plan`) | a map from JSON Pointer into the actual document (`/panels/0/data/value/text`) to the expected value there, each compared exactly; a pointer that resolves to nothing fails |
| subset | `body` of `protocol` steps | expected object: each of its keys present in the actual and matching by subset; expected array: same length, elements matched in order by subset; scalar: exact |

There is no "ignore order", "any value" or regular-expression matching: the result of a conforming runtime is
determined, so the case says what it is or leaves the field out.

### 8. Canonical JSON

The results line and every exact comparison use one serialisation, so that "the same" means "the same bytes".

1. UTF-8, no byte-order mark. No white space outside strings. A results line ends with one LF.
2. **Objects keep their order**; keys are never sorted. The order is the order the specification defines: for the
   ViewModel and the Data Protocol, the order of their schemas' `properties`; for the actual documents of section 5,
   the order written in the table of section 5 (`value, type, text, reads`); for a Rachana object, its fields in
   written order (SPEC 4.2); for expected values read from a case file, the order written in the file. A key whose
   value is absent is left out, never written as `null`, unless the specification says the field is always sent.
3. **Strings**: `"` and `\` are escaped as `\"` and `\\`; U+0008, U+0009, U+000A, U+000C, U+000D as `\b`, `\t`, `\n`,
   `\f`, `\r`; every other code point below U+0020, and U+007F, as `\u00XX` with lower-case hex; a lone surrogate as
   `\uXXXX` lower-case; everything else, including `/`, U+2028, U+2029 and all non-ASCII, as itself in UTF-8.
4. **Numbers**: the number text of section 9, which is always a valid JSON number token. NaN and the infinities never
   appear as numbers: they are the tags of section 4 in values and `null` in documents.
5. **Literals**: `true`, `false`, `null`.
6. **Tags** (section 4) serialise as the one-key objects they are: `{"$num":"NaN"}`, `{"$masked":true}`,
   `{"$link":{"id":"CP-1","kind":"counterparty","label":"Acme"}}`, `{"$dec":"0.1000000000000000000001"}`.

Comparison of two numbers is comparison of their text. A runner that parses JSON or YAML MUST keep enough to reprint a
number by section 9 (the double it denotes, or the decimal digits of a `$dec`).

### 9. Number text, pinned to the Java reference

Every runtime prints a number exactly as the Java reference prints it. Stated without Java so a port can implement it:

| # | Number | Text | Examples |
|---|---|---|---|
| N1 | a double `x` that is whole and `|x| < 10¹⁵` | its integer digits, `-` if negative | `2`, `-7`, `1209000`, `999999999999999` |
| N2 | another finite double with `10⁻³ ≤ |x| < 10⁷` | plain decimal of the **shortest** digit string that reads back to `x`, at least one digit after the point | `0.5`, `0.30000000000000004`, `100.25`, `0.001` |
| N3 | another finite double | `d.ddd…E±n` of the same shortest digits: one digit before the point, at least one after, `E`, the exponent with `-` only when negative, no `+`, no leading zeros | `1.0E20`, `1.0E15`, `1.2345678E7`, `1.0E-4`, `4.9E-324` |
| N4 | an exact decimal (SPEC 4.10 rule 7) | plain digits, no exponent, trailing fraction zeros removed, a whole value without a point | `0.3`, `1.21`, `2` |
| N5 | NaN, +∞, −∞ | text form `NaN`, `Infinity`, `-Infinity`; in canonical JSON the tags of section 4 | |
| N6 | negative zero | `0` | |

N2 and N3 are Java's `Double.toString` as specified from JDK 19 on (the shortest decimal that rounds to the double;
the reference runs on Java 21). "Shortest" means fewest significant digits; among equally short candidates, the one
closest to the exact value of `x`; if two are equally close, the one whose last digit is even. N1 applies first: a whole
double such as `1.5e7 = 15000000` prints `15000000`, not `1.5E7`.

N4's removal of trailing zeros, N6 and N1's bound of exactly `10¹⁵` (SPEC 4.2 "below 10¹⁵") are pinned by cases that carry `tags: [determinism]`.

These rules give the text form of SPEC 4.2 and the numbers of canonical JSON; a format (`amount0`, `pct2`, …, [formats.yaml](../spec/1/formats.yaml)) has
its own rules and is not this.

### 10. Rules for case authors

1. **Self-contained.** A case holds everything it reads; file-level `given` and YAML anchors in the same file are the
   only sharing. No case reads the clock, the locale, the time zone, the network or a file outside the case.
2. **One point per case.** Name the case after the point (`text-join-with-number`, not `test-7`).
3. **Expect only what is certain.** Name the fields the case is about; the results line keeps the rest.
4. **Written by reading → `verified: false`**, with `source` naming the test method or the SPEC section. Only the
   reference runner's record mode plus a person's review makes it `true` (RUNNER 6).
5. **Never pin a defect silently.** Where the reference differs from SPEC, the case pins the decision taken and says so in `note`.
6. **Quote expressions in YAML double quotes** with Rachana's single quotes inside: `expr: "sum($.sales, 'revenue')"`;
   write a Sutra as a `|` block scalar. Use `\u` escapes inside double quotes for characters a reviewer could not see
   (U+00A0, U+200B, astral characters).
7. **Validate** every file against [schema/case.schema.json](schema/case.schema.json) before committing; the runners
   refuse a file that does not validate.

## The runner contract

Frozen with [FORMAT](#conformance-case-format-rachana-1). Every runtime's runner
implements this contract: the JVM reference, Node, the browsers, Python over WebAssembly, and any third-party port.
What a case *is* and how it is compared is FORMAT.md; this is how a runner is called and what it writes.

### 1. Runners

| Runtime name | Runner | Reads cases | Notes |
|---|---|---|---|
| `jvm` | `rachana-conformance` (JUnit dynamic tests; one test per case) | from the folder, or the pinned cases jar | **the reference**; the only one with record mode (section 6) |
| `node` | `js/packages/rachana/test/conformance.mjs` | from the folder | |
| `chromium`, `firefox`, `webkit` | the same module under Playwright | handed in by the Node side | one runtime name per browser |
| `python-wasm` | `python/tests/test_conformance.py` under `wasmtime` | from the folder | from R2 |
| any other name | a third party's program, driven by `rachana conformance run --cmd` (section 5) | on stdin | the driver writes the results file |

A runtime name is `[a-z][a-z0-9-]{0,31}`. A runner that runs inside a test framework still writes the results file of
section 4; the framework's own report is extra.

### 2. Inputs

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

### 3. Statuses

| Status | When |
|---|---|
| `pass` | every field `expect` names matches (FORMAT 7) |
| `fail` | a field does not match |
| `error` | the runtime threw, crashed or timed out on the case (never a language problem: those are actual results) |
| `unsupported` | the kind is not implemented by this runtime, a profile the case needs is not loaded, or `since` is later than `--language` |

A runner never skips a case silently: a case it does not run is `unsupported` and still has its line. There is no
per-runtime list of known failures; a case that fails on one runtime is a defect to fix.

### 4. Outputs

#### 4.1 The results file: `results/<runtime>.jsonl`

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

#### 4.2 Diagnostics

Failures are explained on standard error and in the summary, never in the results file: for `fail`, the case id, the
first field that differs, a short expected/actual excerpt; for `error`, the exception's message (the runtime's own
words; not compared).

#### 4.3 The summary: `results/<runtime>.summary.json`

```json
{"runtime":"node","runtimeVersion":"0.9.0","language":"1.0","profiles":["core"],
 "cases":1834,"pass":1830,"fail":1,"error":0,"unsupported":3,"unverifiedFailing":1,
 "failures":[{"case":"el/dates#eomonth-leap","status":"fail","verified":false,"detail":"value: expected \"2028-02-29\", actual \"2028-02-28\""}],
 "millis":4210}
```

`unverifiedFailing` counts `fail` and `error` cases with `verified: false`. The summary is for people and CI logs; it
is never compared between runtimes.

#### 4.4 Exit codes

| Code | Meaning |
|---|---|
| `0` | every case run is `pass` or `unsupported` |
| `1` | at least one case with `verified: true` is `fail` or `error` |
| `2` | usage failure: bad option, a case file that does not parse or validate, a duplicate case id, the results file cannot be written. No results file is left behind |
| `3` | only cases with `verified: false` are `fail` or `error` |

CI treats `3` as red: every case of the frozen suite is `verified: true`, so code `3` cannot occur.

### 5. Third-party runtimes: cases on stdin

`rachana conformance run --cmd "<program and arguments>"` (any launcher) runs a program that is not
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

### 6. Record mode (the JVM reference only)

`--record` makes the reference runner help turn hand-written cases into verified ones:

1. Cases with `verified: true` are run and compared as always; record mode never changes them. A failing verified
   case is a failure (exit 1), not something to record.
2. For each case with `verified: false`, the runner writes the actual result into the case's `expect`, **keeping the
   fields the case named and only those**, except that a case whose `expect` is empty gets every field of its kind. Each
   case file is rewritten with the reference's comment-keeping YAML writer: comments, order and layout kept; only
   `expect` values change.
3. `verified` stays `false`. A person reviews the diff (`git diff conformance/`) against SPEC and the source test, and
   flips `verified: true` in the same commit. A change of meaning found on review is a defect in the case or in the
   reference, settled by the level rule (known differences, below), never by recording over it.

### 7. The gate

A change to this repository, or to a runtime, is green only when:

1. the `jvm` runner exits `0`;
2. every other runtime's results file is **byte-identical** to `jvm`'s for the cases both ran, after removing lines
   with status `unsupported` from either side (a runtime may lack a kind; it may not differ on one);
3. the coverage rule of COMPILER_AND_RUNTIMES 6.2 holds: every key, option, panel kind, function, format, tone and
   problem code of `spec/1/rachana.lang.json` appears in at least one case.

`tolerance` (FORMAT 5.2) loosens only the comparison with `expect`, for ports; this project's runtimes are built from one
source and still pass rule 2 byte for byte. "Is Rachana" (SPEC 8) means: exit `0` on `conformance/<major>/` for the
profile and minor claimed, with no case `unsupported` other than `protocol` (a compiler is not a host) and kinds the
claimed scope excludes (a linter need not bind: it claims `expr-error`, `check`, `grammar`, `problem`).

### 8. Protocol runs

`rachana conformance protocol --base <url>` runs the `protocol` cases against a Data Protocol host instead of a
compiler. Before each case the host loads the case's `fixture` through its test adapter (a host-specific hook; the
standalone server, the Python and Java SDK adapters each have one). Results go to `results/protocol-<host>.jsonl` with
the same line shape; `actual` is `{"steps":[{"status":…,"body":…}…]}` with bodies as received, canonicalised.

## Known differences between the reference and the specification

At language level 1.0 the reference wins byte for byte and the case is verified; the specified behaviour is the 1.1 variant of the case (`since: "1.1"`, id suffix `-l11`).

| Cases | SPEC | Reference (level 1.0) | Settled |
|---|---|---|---|
| 43 `bad-*`, `expr-error` and limit cases | 6.3: `expression '<source>': <reason>` | the words alone, with ` at <offset>` | the cases pin the reference and are verified; the prefix is level 1.1 (`el/messages-level-1-1`, 43 cases) |
| `el/text#regex-extract-ab-trade-id` | `RCH-2102` | `RCH-4010` | `RCH-4010` verified; `-l11` expects `RCH-2102` |
| `el/distributions#standardize-42-40-1-5` | a number | a decimal | decimal verified; `-l11` expects a number |

Three families are defined at level 1.1 (SPEC 4.15, 4.16, 4.17, 4.13): `el/reserved` and `grammar/reserved` (the reserved tokens and names refused with their messages), `el/numbers-exact` (exact integer and decimal literals, decimal128, `/` half-even to 34 digits) and `el/text-code-points` (lengths, positions and order by code point). Their cases carry `since: "1.1"`; a case runs at its own level, so the 22 cases tagged `changes-with-xd3` in `el/examples/arithmetic` and `el/paths-operators` keep the level-1.0 doubles (`0.1 + 0.2` is `0.30000000000000004`, `type: number`). The `problem` cases at level 1.0 follow the reference: no `path` or `expr` offsets inside a Sutra, and one problem where a later one depends on the first; the complete list is level 1.1. `check` cases run on Core's checker over record types. Function examples are generated from `spec/1/functions.yaml` (one case per example; examples that need a fixture are described in words) and every Core function has at least one case (`tools/conformance/coverage.py`); `xnpv`, `cumprinc` and `ln` carry a tolerance of 1e-9 (SPEC 4.10 rule 7).
