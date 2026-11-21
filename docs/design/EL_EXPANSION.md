<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary. All rights reserved. -->
# Rachana-EL: the expansion proposal

Status: **proposal for the owner, 2026-11-18. Nothing here is built.** It says what Rachana-EL lacks against the best
expression languages, what to add, what to refuse, in which order, at what size, and which points need a decision.
It builds on [spec/1/SPEC.md](../../spec/1/SPEC.md) section 4 and 5, [rachana-el.ebnf](../../spec/1/rachana-el.ebnf),
[functions.yaml](../../spec/1/functions.yaml) (207 Core functions, 36 Drishti-profile, 49 engine list functions
awaiting classification), and the accepted items of [FRONTLOAD_REVIEW](../plans/FRONTLOAD_REVIEW.md) (A1 to A3),
[JS_PROOF](../plans/JS_PROOF.md) (Q1 to Q4) and [R0_R1_I8_PLAN](../plans/R0_R1_I8_PLAN.md) (A7b).

Words used: **bd** = builder-days (specification text, Java reference, conformance cases; the JavaScript and Python
runtimes come from the same source, so they are not counted twice). **Core** = always present. **Core library** =
part of Core's conformance, loaded only when a Sutra uses it (section 1, rule 10). **Profile** = a host's.
Releases: **R0** (specification only, language 1.0, inside i7b), **R1** (language 1.1, with i8), **R2** (language 1.2,
with i9), **later** (1.3 and 1.4). Additions are numbered X1 to X50; decisions XD1 to XD22.

## Contents

1. Vision and principles
2. Gap analysis against the benchmark languages
3. The proposed additions (X1 to X50)
4. What not to add, and why
5. Roadmap and sizes
6. Decisions for the owner

## 1. Vision and principles

**The aim.** Rachana-EL becomes the one expression language a person or a model needs for a data screen: the
function names of Excel, the table work of SQL and dataframes, the safety of CEL and Starlark, and three things none
of them has together: the same bytes in Java, JavaScript and Python; a value that can explain how it was computed; and
a catalogue from which the prompt kit, the editor support and the conformance suite are all generated.

**"Best" is measured, not claimed.** Six tests, each a gate:

| # | Test | Gate |
|---|---|---|
| 1 | A person who knows Excel reads any expression without a manual | names and argument order follow Excel where Excel has the function |
| 2 | A model writes it correctly the first time | ≥ 95% first-try valid expressions on the held-out task set; 100% after applying the machine fixes |
| 3 | Same result everywhere | every case byte-equal in the three runtimes; no tolerance inside Rachana's own builds (Q1) |
| 4 | Nothing hangs, nothing leaks | every evaluation ends inside a stated budget; a masked value never reaches an output |
| 5 | Every number can say why | an explanation tree for any cell, itself a conformance case |
| 6 | Table work at dataframe speed | the budgets P4 to P7 of COMPILER_AND_RUNTIMES hold with the new table functions |

**Ten rules the expansion keeps.** They are the fixed principles, made specific.

1. **Additions only.** Everything here is new syntax that is an error today, or a new function name. No existing
   expression changes meaning inside `rachana: 1`. Three things must be settled **before 1.0 is cut**, because after
   that they would be breaking (X1, X2, X31a).
2. **One way per job.** New syntax is added only where it removes a class of mistakes or makes something possible that
   functions cannot. Each construct has one canonical spelling, which the formatter prints. A spelling from another
   language is **refused with a fix**, never accepted as a synonym (as `**` and `//` are today).
3. **Total.** Iteration is only over finite values. There is no recursion and no loop statement. Every evaluation
   also runs inside counted budgets (X45), and the counting is part of the specification, so every runtime stops at the
   same place.
4. **Deterministic.** All arithmetic goes through Core's own `CoreMath` and `CoreDecimal`. Every data table a function
   needs (currencies, time zones, Unicode, locale data) is a **pinned pack** whose version appears in the ViewModel.
   Clock, locale, zone and random seeds come only from the context.
5. **Hooks, not I/O.** A function reaches the outside only through a host hook (today: `Calendars`, `Clock`,
   `Masker`, `TableProvider`). New hooks: `Rates`, `Zones`, `Geo`, `Messages`, `LibraryResolver`. A missing hook turns
   that feature off with a named problem; it never guesses.
6. **Names, never styles.** A value can ask for a tone, an icon or a microchart by name; colours, pixels and fonts stay
   the painter's.
7. **Row-wise meaning, vectorised execution.** An expression over a table means "for each row". The runtime may run
   it over columns. There is no implicit broadcasting and no implicit filter context.
8. **The catalogue is the truth.** Each function entry carries types, null and mask behaviour, cost class,
   explanation template and examples. The prompt kit, the language server, the documents and the cases are generated.
9. **Promotion, not duplication.** When Core gains something a profile already has (`lag`, `rank`, `ytd`, `convert`),
   the profile's function **becomes** the Core function with the same meaning; the host's engine is then one more
   executor of it, proven by parity cases.
10. **Size discipline.** The JavaScript runtime budget is 385 KB (restated at the G1 close as measured + ~5%; bigger bundles acceptable (owner); the 150 KB first aimed at was not reachable). Core keeps a base (operators, logic, text, dates,
    lists, aggregates); the larger families (`statistics`, `finance`, `timeseries`, `geo`, `quant`, `units`) are Core
    libraries, linked only when a Sutra uses a function of theirs. The compiler knows this at compile time.

## 2. Gap analysis against the benchmark languages

What Rachana-EL already has that most lack: total paths, one empty value, masked values that propagate, exact decimal
rounding, a linear-time regular-expression engine, compile-time checks of literal arguments, machine-fixable
problems, and a closed function set.

| Language | What it does best | What Rachana-EL lacks against it | Take (addition) | Leave, and why |
|---|---|---|---|---|
| Excel (LET, LAMBDA, dynamic arrays) | names everyone knows; `XLOOKUP`; `FILTER`, `SORTBY`, `SCAN`, `BYROW`; named LAMBDA libraries | named lambdas; lookup; scan; user functions; bond functions | X13, X17, X20, X28, X41, X42, X50 | recursion in LAMBDA; cell addresses; volatile `NOW`, `RAND`; locale-dependent separators |
| Power BI DAX | named measures; time intelligence; iterators (`SUMX`) | Sutra-level names; period-to-date in Core; windows | X41, X23, X24 | `CALCULATE` and implicit filter context: the largest source of wrong numbers and of model mistakes |
| Tableau calculations | level-of-detail expressions; table calculations; bins; parameters | windows; bins; what-if parameters | X15, X19, X23, X37 | "compute using" (meaning that depends on how the chart is laid out) |
| SQL | joins; window functions; `FILTER`; `GROUPING SETS`; `CASE` | joins; windows; subtotals | X20 to X23 | three-valued NULL logic; SQL text in expressions; recursive queries |
| jq, JSONata, JMESPath | building objects; pipes; reshaping nested data | record literals; pipe; `flat_map`, `entries` | X6, X11, X17 | recursive descent; generators and backtracking; JSONata's implicit flattening |
| CEL | proven safety: not Turing-complete, cost estimation, gradual types, macros | counted budgets; richer checked types | X8, X45 | protobuf-centred types; its commutative error-absorbing logic |
| Kusto KQL | pipe-first table verbs (`where`, `extend`, `summarize`, `top`, `join`); time bins; `dcount`, `percentile` sketches | pipe; verbs; time buckets; approximate aggregates | X11, X20, X24, X39 | statements; cross-cluster calls; plug-ins |
| Polars, pandas | vectorised expressions; a lazy plan that is optimised; `over`; as-of joins; `resample` | plan and push-down; vectorised execution; as-of | X20, X23, X24, X46 | index alignment; mutation in place; `apply` of arbitrary code |
| Vega-Lite expressions | made for UI: parameters, `datum`, formats, scales | display values; screen state; richer formats | X33, X35, X36 | raw colours and pixels; event-stream syntax; JavaScript semantics |
| Observable, D3 | `bin`, `rollup`, `extent`, `ticks`, `nice`; number and time formats | histogram bins; extent; nice ticks | X17, X26, X33 | imperative JavaScript |
| Bloomberg BQL | every item takes dates, period, currency, fill; conversion is built in | money and conversion; fills; periods; as-of | X5, X24, X25 | vendor data fields (the host's data, not the language) |
| kdb+/q | as-of join, window join, time bars, each-prior | as-of join; time buckets; differences | X23, X24 | terseness; right-to-left reading; adverbs as symbols |
| R tidyverse | verb pipelines that read as sentences; `pivot_longer`, `pivot_wider`; `case_when`; lubridate | verbs; pivot and unpivot; date helpers | X3, X20, X22 | non-standard evaluation (code that captures code) |
| Lua, Starlark | Starlark's rules: deterministic, hermetic, no recursion, frozen values, step limits | they confirm Rachana's rules; the counted step limit is missing | X13 (rules), X45 | statements, mutation, loops |
| Arrow compute | typed kernels; decimal128; temporal kernels with zones; stated null handling | pinned numeric tower; typed temporal values | X2, X3, X4, X46 | an options object per call; the unsigned-integer family |

**Summary of the gap.** Rachana-EL today is a strong scalar and list language (about the level of Excel without
LAMBDA, plus JMESPath paths). It lacks: (a) a second parameter name, so nested `map` and `reduce` are awkward;
(b) a way to build a record; (c) table operations beyond `group` and `sort`; (d) windows and time series outside the
Drishti profile; (e) typed time, money and units; (f) reuse (named functions, libraries); (g) explanation; (h) stated
evaluation budgets; (i) the tooling and AI artefacts that make a language easy to write.

## 3. The proposed additions

Each group has one table (priority P1 must, P2 should, P3 could; cost; release; Core, Core library or profile;
conformance impact) and then one entry per addition with a syntax sketch, the semantics with determinism and safety
notes, and an example.

### 3.1 Foundations and types

| # | Addition | Pri | Cost | Release | Where | Conformance |
|---|---|---|---|---|---|---|
| X1 | Reservations before 1.0 | P1 | 2 bd | R0 | Core | ≈ 30 `grammar` and `expr-error` cases: each reserved token and name refused with its message |
| X2 | The numeric tower, pinned | P1 | 2 bd R0 + 8 bd R1 | R0, R1 | Core | ≈ 150 `expr` cases; replaces tolerance for decimal results |
| X3 | Typed date, time, timestamp, duration | P1 | 10 bd | R2 | Core | ≈ 180 cases; DST and leap cases from JS_PROOF 3.4 |
| X4 | Time zones | P2 | 8 bd | later (1.3) | Core + `Zones` hook, pinned zone pack | ≈ 80 cases per pinned pack version |
| X5 | Money | P1 | 7 bd | R2 | Core + `Rates` hook | ≈ 90 cases; a fixed test rate table |
| X6 | Record literals and spread | P1 | 4 bd | R1 | Core | ≈ 50 cases; EBNF +4 rules |
| X7 | Range values and slicing | P2 | 4 bd | R1 | Core | ≈ 50 cases; EBNF +2 rules |
| X8 | The checked-mode type language | P1 | 6 bd R1 + 6 bd R2 | R1, R2 | Core | ≈ 200 `check` cases; `functions.yaml` gains typed signatures |
| X9 | Units of measure | P3 | 10 bd | later (1.4) | Core library `units` | ≈ 100 cases; pinned unit table |
| X10 | Vectors and matrices | P3 | 12 bd | later (1.4) | Core library `linalg` | ≈ 80 cases; bytes pinned by `CoreMath` |

**X1. Reservations before 1.0.** Fix, in the 1.0 text, everything later minors need so that they stay additions.
(a) Tokens refused today stay refused with a message that names them as reserved: `|>`, `??`, `=>`, `..`, `...`,
`{`, `}`, `/* */`, `_` inside a number. (b) A list of Core function names held for the families below (about 300
names), so no profile takes one; a profile that already has one (36 Drishti names) is recorded as "to be promoted"
(rule 9). (c) `type_of` answers are an open set: a host MUST accept words it does not know. (d) The rule for user
names (X41): a locally defined name wins over a Core function added later, with a warning. No runtime change.

**X2. The numeric tower, pinned.** Three kinds, as checked mode already names them: `integer` (exact), `decimal`
(exact, 34 significant digits, the decimal128 format) and `number` (IEEE double). `integer` is a decimal with no
fraction, so 64-bit identifiers survive (A1, A11).

```text
0.1 + 0.2 == 0.3            → true      (both literals are decimals; + is exact: A3)
1.10 * 3                    → 3.3
1 / 3                       → 0.3333333333333333333333333333333333   (34 digits, half-even)
1e-3 * 2                    → 0.002     (an exponent literal is a number; the result is a number)
sqrt(2) * 1.5               → a number  (decimal with number gives number)
```

Rules: a literal without an exponent is a decimal; `+ - *` on decimals are exact, `/` rounds half-even to 34 digits,
`/ 0` is empty; a decimal mixed with a number gives a number; real-number functions return numbers; overflow of 34
digits is `RCH-2102`, never a silent loss. Deterministic because `CoreDecimal` is Core's own code in every runtime.
A lint rule warns about an unformatted division in a template. This is the completion of A3 and needs XD3.

**X3. Typed date, time, timestamp, duration.** Values stay ISO 8601 text on the wire and in text form (nothing a
Sutra prints changes), but functions and checked mode treat four kinds: `date`, `time` (`'09:30:00'`), `timestamp`
(UTC with `Z`, milliseconds: A2), `duration` (`'PT1H30M'`, exact seconds) and the calendar `period` (`'P1M'`).

```text
duration('PT90M')                                → 'PT1H30M'
timestamp_add($.sent, duration(minutes = 15))    → '2026-11-17T09:45:00.000Z'
between_time($.sent, $.acked)                    → 'PT4M12S'        seconds(…) → 252
date_trunc($.trade_date, 'quarter')              → '2026-10-01'
date_bucket($.ts, 'PT5M')                        → the start of the 5-minute bar
overlap_days($.a_from, $.a_to, $.b_from, $.b_to) → 12
age($.opened, asof(), 'Y')                       → 3
```

A duration is exact seconds and never depends on a zone; a period is added by calendar rules with the stated month-end
rule (`edate`'s). About 25 functions. `date - date` keeps giving whole days.

**X4. Time zones.** `at_zone(ts, 'America/Toronto')` gives the local date-time with its offset; `zone_offset`,
`local_date`, `from_local(text, zone)`. Zone rules come from Core's own pinned pack (about 30 KB, rules from 1970),
not from the platform, because the JDK, the browser and Python each carry a different zone database: with the
platform's, the three runtimes would disagree. The pack version is written in the ViewModel. A local time that does
not exist moves forward by the gap; one that occurs twice takes the earlier offset; both are cases. Without the pack
only `UTC` and fixed offsets (`+05:30`) are known and a named zone is a compile error naming the pack. `zone()`
reads the viewer's zone from the context.

**X5. Money.** A value of an exact amount and an ISO 4217 code (pinned table: code, minor units).

```text
money(1250.5, 'USD') + money(10, 'USD')      → USD 1260.50
money(1, 'USD') + money(1, 'EUR')            → RCH-2102 "add: USD and EUR; convert one of them"
convert($.mtm, 'CAD')                        → through the host's Rates hook, at asof()
convert($.mtm, 'CAD', rate = 1.3712)         → explicit rate; no hook needed
amount(m), currency(m), round_money(m)       → 1260.5, 'USD', to the currency's minor units
sum(map($.legs, l => convert(l.mtm, 'USD'))) → one currency before adding
```

Money never converts by itself and never mixes; `*` and `/` take a plain number on one side; money over money of one
currency is a plain ratio. `convert` is the Drishti profile's function promoted (rule 9); the `Rates` hook answers
(from, to, date) with an exact rate or "none" (then the result is empty and the panel says which rate is missing).

**X6. Record literals and spread.** `{` and `}` are errors today, so this is an addition.

```text
{ label: @.desk, value: sum(@.rows, 'pnl') }
{ 'day-count': 'ACT/360', ...@ }                   a copy of the row with one more field
[ ...$.fixed_legs, ...$.float_legs ]               spread in a list literal
map($.legs, l => { id: l.id, mm: l.notional / 1e6 })
```

Keys are identifiers or quoted text, kept in written order; a key written twice is a compile error; a later spread or
key replaces an earlier one; at most 1,000 fields. A record is an ordinary object. Inside YAML the expression is
quoted, as today's rule says; a lint rule catches an unquoted one.

**X7. Range values and slicing.** `a..b`, both ends included, as `within` and the range controls already write it.

```text
within($.date, '2026-01-01'..asof())        the text form of a range is 'from..to': the control's own form
$.legs[1..3]                                 elements 1, 2, 3; negative ends count from the end; clamped
x in 1..10                                   membership by ordering, no list is built
map(1..12, m => eomonth(date(2026, m, 1)))   a whole-number or date range is a list of at most 10,000 elements
```

Precedence: below `+ -`, above the comparisons. The lexer ends a number before `..` (`1..5` is `bad number` today).

**X8. The checked-mode type language.** Checked mode gains the types the additions need: `list<T>`, `record{…}`,
`table{…}`, `fn(T…) -> U`, `T?` (may be empty), `money`, `duration`, `range<T>`, and unions (`number | text`) in
catalogue signatures only. Types are **inferred**; nobody writes a type inside an expression. They are written in
`functions.yaml`, in a table's schema and in a user function's `params` (X41). Inference flows through `let`, lambdas,
pipes and record literals, so `map($.sales, r => r.revnue)` is `RCH-9152` with "did you mean `revenue`". Checked mode
still only refuses more and never changes a meaning. Function types are checked in every mode (X13).

**X9. Units of measure.** `qty(5, 'MWh')`, `to_unit(q, 'GJ')`, `qty(100, 'bbl') / qty(1, 'day')`; adding kilograms
to metres is `RCH-2102`. A pinned table (SI, energy, volume, mass, time, data sizes), exact decimal factors. A library,
because few screens need it, and those that do (commodities) need it badly.

**X10. Vectors and matrices.** `vec(list)` and `matrix(rows)`: on these two kinds only, `+ - * /` work element by
element; `dot`, `mmult`, `transpose`, `cholesky`, `solve`, `covariance_matrix`, `quad_form(w, m)`. A new kind, so the
meaning of `+` on plain lists does not change. Size limit by the cell budget (X45).

### 3.2 Operators and syntax

| # | Addition | Pri | Cost | Release | Where | Conformance |
|---|---|---|---|---|---|---|
| X11 | The pipe `\|>` | P1 | 3 bd | R1 | Core | ≈ 40 cases; EBNF +1 rule; printer round-trip cases |
| X12 | `??`, first non-empty | P1 | 1 bd | R1 | Core | ≈ 20 cases |
| X13 | Named-parameter lambdas | P1 | 8 bd | R1 | Core | ≈ 120 cases, including every refusal |
| X14 | Comments and digit separators | P2 | 2 bd | R1 | Core | ≈ 20 cases |
| X15 | Small choice functions | P1 | 2 bd | R1 | Core | ≈ 40 cases (from the examples) |
| X16 | The foreign-habit table | P1 | 2 bd R0 + 4 bd R1 | R0, R1 | Core | one `problem` case with a `fix` per row (≈ 80) |

**X11. The pipe.** `x |> f(a)` means `f(x, a)`: the left side becomes the first argument. Lowest precedence.

```text
$.trades
  |> filter(t => t.desk == ctl.desk)
  |> group_by('ccy', { notional: sum(@.rows, 'notional'), n: size(@.rows) })
  |> sort(@.notional, true)
  |> take(10)
```

Pure sugar: the compiler rewrites it before anything else, so evaluation, cost and explanation are those of the nested
calls. The right side must be a call. The catalogue marks the few functions whose first argument is not the subject
(`find(part, text)`, `npv(rate, flows)`); piping into one is a compile error with the rewritten call as the fix.

**X12. `??`.** `a ?? b` is `coalesce(a, b)`: `a` unless it is empty, else `b`, and `b` is evaluated only then.
`''` and `0` are values. It exists because `a || b` gives a boolean (SPEC 4.8), the most frequent slip of people and
models coming from JavaScript. Precedence between `?:` and `||`. `coalesce` stays for three or more.

**X13. Named-parameter lambdas.** `x => body` and `(a, b) => body`, wherever a form takes an expression today, and
as a `let` value.

```text
map($.books, b => sum(filter($.trades, t => t.book == b.id), 'notional'))     both rows have a name
reduce($.legs, '', (acc, leg) => concat(acc, left(leg.type, 2)))               no @acc
let(bp = x => x * 10000, bp($.spread))                                         a local function
sort($.rows, r => r.maturity)        same as        sort($.rows, @.maturity)   the short form stays
```

Safety: a function value is **second class**. It can be bound by `let`, passed to a parameter typed `fn`, and
called. It cannot be stored in a list or record, compared, printed or returned by a data source. A `let` name is not in
scope in its own value, so there is no recursion. Function types are checked in every mode and there are no recursive
types, so a function cannot be applied to itself; the step budget (X45) is the second guard. Inside a named lambda
`@` keeps its outer meaning, which is what makes nesting clear. Style rule (formatter and linter): `@` form when the
body reads one element and is not nested; named form otherwise.

**X14. Comments and digit separators.** `/* why this threshold */` anywhere white space may stand; the printer keeps
it. `1_000_000` is the number 1000000 (an underscore only between digits). Both make long expressions reviewable; the
separator removes the miscounted-zeros mistake.

**X15. Small choice functions.** `between(x, lo, hi)` (the fix offered for `lo < x < hi`); `bucket(x, edges,
labels?)` → the band of `x` (`bucket(@.util, [0.8, 0.95], ['ok', 'warn', 'bad'])`); `nullif(a, b)`;
`safe_div(a, b, otherwise?)`; `clamp` exists. `bucket` is what Tableau bins and DAX `SWITCH(TRUE())` are used for.

**X16. The foreign-habit table.** A generated table of about 80 spellings from other languages, each refused with
the Rachana spelling and a machine fix: `and`/`or`, `<>`, `=`, `===`, `IS NULL`, `&` for joining, `x.length`,
`list.map(…)`, `SUM(…)` in capitals, `substr`, `LIKE 'A%'`, `IIF`, `CASE WHEN`, `nvl`, `isnull`, `DATEADD`,
`TODAY()`. The rows come from the mistakes the AI evaluation records (AI_GENUI 6), so the table grows with evidence.
It is also the migration of form rules (A7b): `FormExpr`'s `and`, `or`, `not`, `empty(x)` become rows with fixes.

### 3.3 Lists and higher-order functions

| # | Addition | Pri | Cost | Release | Where | Conformance |
|---|---|---|---|---|---|---|
| X17 | The list and record toolkit | P1 | 8 bd | R1 | Core | ≈ 150 cases (every example) |
| X18 | The 49 engine list functions classified into Core | P1 | 2 bd R0 + 5 bd R1 | R0, R1 | Core base and library `statistics` | ≈ 150 cases; parity with Drishti's engine |
| X19 | `group_by` with records | P1 | 4 bd | R1 | Core | ≈ 40 cases |

**X17. The list and record toolkit.** About 30 functions, each bounded by its input: `filter(l, p)` (the call form of
`[?p]`, needed for pipes), `any(l, p)`, `all(l, p)`, `first(l, p?)`, `last(l, p?)`, `index_of(l, v)`,
`flat_map`, `zip(a, b, …)`, `zip_with(a, b, fn)`, `scan(l, init, fn)` (running reduce), `chunk(l, n)`,
`sliding(l, n)`, `pairwise(l)`, `take_while`, `drop_while`, `top(l, n, key?)`, `bottom`, `arg_max(l, key)`,
`arg_min`, `reverse` (now also a list), `count_by(l, key)`, `index_by(l, key)`, `text_join(l, sep, last_sep?)`,
`extent(l)`, `bin(l, edges or n)`, `nice_ticks(lo, hi, n)`; for records `keys`, `values`, `entries`,
`from_entries`, `pick(r, names)`, `omit`, `merge(a, b)`, `get(r, 'a.b', otherwise?)`.

```text
any($.legs, l => l.ccy != $.ccy)                    → true when a leg is in another currency
scan($.pnl[*].v, 0, (acc, v) => acc + v)            → the running total
top($.trades, 5, @.notional)                        → the five largest
text_join(unique($.legs[*].ccy), ', ', ' and ')     → 'AUD, CAD and USD'
```

**X18. The engine list functions.** Classify the 49 functions of SPEC 5.11 as planned in H5: all become Core.
Base: `product`, `sumproduct`, `sumsq`, `cumsum`, `cumprod`, `cummax`, `cummin`, `string_agg`, `kth_largest`,
`kth_smallest`, `nmiss`. Library `statistics`: the rest (`corr`, `slope`, `skewness`, `movmean`, `zscore`,
`polyfit`, …). The pivot engine then calls Core's kernel (decision D3), one implementation.

**X19. `group_by` with records.** `group` keeps its fixed `{label, value, count}` shape and its 500-group cut.
`group_by(l, key or [keys], aggregates?)` returns one record per group with the key fields, `rows`, and one field per
named aggregate; the limit is the cell budget, and exceeding it is an error, never a silent cut.

```text
group_by($.trades, ['desk', 'ccy'], { notional: sum(@.rows, 'notional'), worst: min(@.rows[*].pnl) })
→ [ { desk: 'FX', ccy: 'USD', rows: […], notional: 4.2e8, worst: -12000 }, … ]     in first-seen key order
```

### 3.4 Tables

A table is a list of records with a known schema. It is not a new kind of value: every list function works on it,
`type_of` says `list`, and a table function given a plain list of records works the same. What is new is the set of
verbs, the logical plan behind them, and push-down.

| # | Addition | Pri | Cost | Release | Where | Conformance |
|---|---|---|---|---|---|---|
| X20 | Table verbs, the plan, push-down | P1 | 18 bd | R2 | Core | ≈ 200 `expr` cases + ≈ 60 `plan` cases; local and pushed results byte-equal |
| X21 | Joins | P1 | 8 bd | R2 | Core | ≈ 80 cases, every `how`, empty and masked keys |
| X22 | Pivot, unpivot, subtotals | P2 | 8 bd | later (1.3) | Core | ≈ 70 cases |
| X23 | Windows | P1 | 12 bd | R2 | Core library `timeseries` (ranks in base) | ≈ 150 cases; parity with the Drishti engine's 10 window functions |
| X24 | Time series | P2 | 12 bd | later (1.3) | Core library `timeseries` | ≈ 140 cases |
| X25 | Calendars, fiscal periods, schedules | P2 | 6 bd | later (1.3) | Core + `Calendars` hook (extended) | ≈ 90 cases with a fixed test calendar |

**X20. Table verbs.** `filter`, `extend(t, {name: expr, …})` (add or replace columns), `select(t, names or record)`,
`sort` (existing), `group_by` (X19), `top`, `append(a, b)`, `distinct_rows(t, keys?)`, `lookup(t, key, value,
result, otherwise?)` (Excel's `XLOOKUP`; an index is built once per evaluation), `row_count`.

```text
$.trades
  |> extend({ usd: convert(@.mtm, 'USD'), limit: lookup($.limits, 'cpty', @.cpty, 'limit') })
  |> filter(r => amount(r.usd) > r.limit)
  |> select(['trade_id', 'cpty', 'usd', 'limit'])
```

A pipeline over a `data:` table compiles to a **plan**. The planner pushes the leading verbs that fit the Data
Protocol's fixed subset (filter, sort, group, page: decision D24) to the host and runs the rest locally. The result is
the same either way, and that is a conformance kind (`plan`) that exists already. A verb that cannot be pushed over a
table above the client threshold is `RCH-2074`, as today.

**X21. Joins.** `join(a, b, on, how?)`: `on` is a column name, a list of names, or a record `{left: right}`
(equality only); `how` is `inner` (default), `left`, `full`, `semi`, `anti`. Column clashes get the suffix `_right`.
Empty keys never match. Row order: left order, then right order. No cross join. The output is bounded by the cell
budget; a join that would exceed it stops with `RCH-2103` naming both sizes. A masked key column is refused
(`RCH-9161`), as filters are.

**X22. Pivot, unpivot, subtotals.** `pivot(t, rows, columns, value, aggregate?)` (at most 200 new columns, in sorted
order), `unpivot(t, keep, names_to, values_to)`, `rollup(t, keys, aggregates)` (subtotals per key prefix and a grand
total, with `level` and `is_total` fields: SQL's `GROUPING SETS` for the usual case).

**X23. Windows.** One verb, `window(t, by, order, columns)`; the functions inside are the promoted profile names with their meanings unchanged.

```text
$.pnl |> window('desk', 'date', {
           prev:   lag(@.pnl),               chg:  @.pnl - lag(@.pnl),
           cum:    running(sum(@.pnl)),      ma20: moving_avg(@.pnl, 20),
           rk:     rank(@.pnl, desc = true), pct:  percent_rank(@.pnl) })
```

Inside the record: `lag`, `lead`, `first_value`, `last_value`, `nth_value`, `row_number`, `rank`, `dense_rank`,
`percent_rank`, `ntile`, `running(aggregate)`, `moving_avg/sum/min/max/median/stddev(x, n)`, and a general
`over(aggregate, before, after)`. Frames are counted in rows; ties in `order` keep the input order (stable), which is
what makes the result deterministic. Outside `window` these names are a compile error that shows the verb.

**X24. Time series.** `resample(t, 'date', 'month', aggregates, fill?)`, `date_spine(from, to, step, calendar?)`
(at most 10,000), `fill_gaps(t, 'date', step, method)`, `asof_join(a, b, on, by?, tolerance?)` (the last row of `b`
at or before each row of `a`; kdb's `aj`), `diff`, `pct_change`, `returns(xs, 'simple' or 'log')`, `ewma(xs,
lambda)`, `ytd`, `mtd`, `qtd`, `fiscal_ytd`, `prior_period`, `same_period_last_year` (promoted), `rebase(xs, 100)`.

**X25. Calendars, fiscal periods, schedules.** `is_holiday`, `holidays_between`, `next_business_day`,
`adjust(d, 'MF', calendar)` (following, modified following, preceding), `add_tenor(d, '3M', calendar?,
convention?)`, `schedule(start, end, '6M', calendar?, convention?, stub?)` (at most 1,200 dates), `imm_date`,
`fiscal_year(d)`, `fiscal_quarter`, `fiscal_period`; joint calendars `'USNY+GBLO'`. The fiscal year start and the
calendars are the host's; without them: January and weekends only, as SPEC 5.5 says.

### 3.5 Statistics, risk and finance

| # | Addition | Pri | Cost | Release | Where | Conformance |
|---|---|---|---|---|---|---|
| X26 | Performance and descriptive statistics | P2 | 8 bd | later (1.3) | Core library `statistics` | ≈ 120 cases, bytes pinned by `CoreMath` |
| X27 | Risk helpers | P2 | 8 bd | later (1.3) | Core library `risk`; under Drishti marked host-only | ≈ 80 cases checked against the VaR goldens |
| X28 | Fixed income | P2 | 10 bd | later (1.4) | Core library `finance` | ≈ 150 cases against Excel's published results |
| X29 | Option formulas | P3 | 6 bd | later (1.4) | Core library `quant`, off by default | ≈ 80 cases |

**X26.** `volatility(returns, periods_per_year?)`, `annualise`, `drawdown(xs)` (a list), `max_drawdown`, `sharpe`,
`sortino`, `beta(a, b)`, `tracking_error`, `information_ratio`, `hit_rate`, `histogram(l, bins)`, `quantiles(l,
[ps])`, `ecdf`, `winsorize`, `covariance_matrix(columns)`, `correlation_matrix`, `linear_fit(xs, ys)` → `{slope,
intercept, r2}`, and seeded sampling: `sample(l, n, seed)`, `random_normal(seed, n)` on a specified generator, so the
same seed gives the same numbers in every runtime. No unseeded random source exists.

**X27.** On a vector of scenario P&L: `var_hist(pnl, 0.99)`, `es_hist`, `var_parametric(sd, 0.99, horizon?)`,
`es_parametric`, `scale_horizon(v, from, to)`, `component_var(positions, total)`, `incremental_var`,
`backtest_exceptions(pnl, var)`, `kupiec(exceptions, n, level)`, `traffic_light(exceptions)`. Each entry in the
catalogue carries `runs: host` in the Drishti profile, so Drishti's rule holds (every risk number is computed on the
server); a standalone host may run them anywhere. They are the same functions as the engine's, by rule 9.

**X28.** Excel's bond family under Rachana names (`bond_price`, `bond_yield`, `bond_duration`, `coupon_dates`,
`coupon_days`, `accrued` extended), day counts `ACT/365L`, `ACT/ACT ISDA`, `ACT/ACT ICMA`, `BUS/252`, `30/360 US`,
and curves **given as data**: `discount_factor(curve, date)`, `zero_rate`, `forward_rate`, `interp1` with
`log-linear` and `monotone-cubic`. Arithmetic on supplied figures; no market data, no calibration.

**X29.** `bs_price`, `bs_delta`, `bs_gamma`, `bs_vega`, `bs_theta`, `bs_rho`, `black76_price`, `bachelier_price`,
`implied_vol` (bounded root finder, 100 steps). Textbook closed forms, pure. A separate library, **not loaded by the
Drishti profile**, because Drishti does not price; another host may want them (XD14).

### 3.6 Text, formats, languages, geography

| # | Addition | Pri | Cost | Release | Where | Conformance |
|---|---|---|---|---|---|---|
| X30 | Text II | P2 | 7 bd | later (1.3) | Core | ≈ 120 cases; regex acceptance cases |
| X31 | Code points (decision, R0) and Unicode functions | P1 decision, P3 functions | 1 bd R0 + 8 bd | R0, later (1.4) | Core; pinned Unicode pack | non-BMP cases of JS_PROOF 3.3; ≈ 60 cases |
| X32 | Languages and plurals | P2 | 10 bd | later (1.4) | Core + pinned locale packs + `Messages` hook | ≈ 40 cases per pinned locale |
| X33 | Formats II | P1 | 5 bd | R2 | Core | ≈ 120 `format` cases |
| X34 | Geography | P3 | 6 bd | later (1.4) | Core library `geo` + `Geo` hook | ≈ 60 cases |

**X30.** `regex_extract_all`, `regex_split`, `regex_count`, named groups (`(?<ccy>[A-Z]{3})` → a record with
`regex_match`), `\b` (still linear time); `truncate(t, n, '…')`, `initials`, `slug`, `squeeze`, `starts_with_any`,
`edit_distance(a, b)` and `similarity` (inputs cut at 256 characters), `parse_json(t)` (cut at 64 KB, depth 32; the
result is plain data), `to_json(v)` (canonical form), `hash(t, 'sha256')`, `stable_index(t, n)` (a stable number
0..n-1 for a key, for consistent series tones), `base64`, `url_encode`.

**X31.** Decide before 1.0 that lengths, positions and ordering are by **code point** (SPEC 9.4, A5); the reference
changes only for characters outside the Basic Multilingual Plane. Later: `normalize(t, 'NFC')`, `casefold`,
`graphemes`, `strip_accents`, from a pinned Unicode pack, because the platforms carry different Unicode versions.

**X32.** `locale()` from the context; locale-aware formats (`fmt(v, 'amount2', locale = 'fr-CA')`) from pinned
packs; `plural(n, {one: '1 trade', other: '${n} trades'})` by the pinned plural rules; `ordinal(n)`;
`list_and(l)`; `ago(ts)` relative to `asof()`; `msg('key', {…})` through the host's `Messages` hook for translated
screens. Without a pack: English, as today.

**X33.** Formats with arguments (`fmt(v, 'amount', decimals = 3)`); new names `bp1`, `multiple1` (`3.2x`), `bytes`,
`si`, `duration` (`2h 05m`), `money` (uses the value's currency and minor units), `ordinal`, `signed_pct1`,
`accounting2` (brackets for negatives), full date and time patterns, and the pinned output for the infinities and NaN.

**X34.** `geo_distance(lat1, lon1, lat2, lon2)` (haversine, `CoreMath`), `bearing`, `bbox(points)`, `in_bbox`,
`in_polygon` (at most 10,000 vertices), `geohash`, `centroid`; `region_of(lat, lon, level)` through the host's
`Geo` hook (the map packs know the regions; the language does not).

### 3.7 Values for the screen

| # | Addition | Pri | Cost | Release | Where | Conformance |
|---|---|---|---|---|---|---|
| X35 | Display values and tone rules | P1 | 8 bd (+ 6 bd painter in Drishti Elements) | R2 | Core; ViewModel minor | ≈ 80 `sutra` cases; ViewModel schema additions |
| X36 | Screen-state functions | P1 | 7 bd | R2 | Core | ≈ 70 `sutra` cases |
| X37 | What-if | P2 | 7 bd | later (1.3) | Core | ≈ 60 cases |
| X38 | Live-row state | P2 | 4 bd | later (1.3) | Core | ≈ 30 `patch` cases |

**X35. Display values.** Like `link` today: a typed value the painter draws, with a plain text form for exports.

```text
spark(@.history[*].px, 'line')                    a microchart in a cell
bar(@.util, 1, tone = bucket(@.util, [0.8, 0.95], ['ok', 'warn', 'bad']))
bullet(@.pnl, @.budget, [0, 0.5, 1])              badge(@.status, 'warn')
icon('arrow-up', 'pos')                           delta(@.px, previous(@.px), 'bp1')
heat(@.corr, -1, 1, 'diverging')                  a step of a named scale, never a colour
```

`tone:` and `highlight:` may be an expression that returns a tone **name**; an unknown name gives no tone, as today.
Icons and scales are closed sets of names, checked at compile time when literal. A masked input gives the mask.

**X36. Screen-state functions.** Functions, not new roots: a new root word such as `sel.` would change the meaning of
a data field of that name, and a function name cannot. `selection('trades')` (the selected rows of a panel),
`controls()` (a record of every control value, for captions), `control_label('desk')`, `is_set(ctl.desk)`,
`drill_path()` and `drill_level()` (inside a drillable panel), `viewer('name' or 'roles')` (what the host states),
`width_class()` (`narrow`, `medium`, `wide`), `locale()`, `zone()`, `panel_rows('trades')` (the rows a panel shows
after its own filters). Each is context the host sets, so the result is a function of data and context only. Rachana
has no implicit filter context: a panel filters with its own `where`, and a share is written as
`sum(filtered) / sum($.sales)`. That is kept on purpose (section 4).

**X37. What-if.** A parameter is a control (`kind: number`, `slider`, `text`), read as `ctl.rate`: no new concept.
`what_if(expr, {rate: ctl.rate + 0.01})` evaluates `expr` with control values replaced, purely;
`sensitivity(expr, 'rate', [0.01, 0.02, 0.03])` gives a table (Excel's data table); `grid(expr, 'rate', […],
'vol', […])` at most 2,500 cells; `goal_seek(expr, 'rate', target, lo, hi)` by bisection, at most 100 steps, and
"no root" is an error, never a wrong figure.

**X38. Live-row state.** In a live table, `previous(expr)` evaluates `expr` on the row as it was before the last
change (empty at first sight), `changed(expr)`, `direction(expr)` (`up`, `down`, none), `row_age()` (a duration
since the row last changed, against the context's clock). The result depends only on the current and the previous
row, both of which the runtime holds, so replaying the same patches gives the same ViewModel. One mechanism, shared
with the live result-set engine.

### 3.8 Approximation and security

| # | Addition | Pri | Cost | Release | Where | Conformance |
|---|---|---|---|---|---|---|
| X39 | Approximate aggregates | P3 | 9 bd | later (1.4) | Core library `sketch` | ≈ 50 cases; any input order gives the same bytes |
| X40 | Masking II, labels, the `policy` profile | P2 | 9 bd | later (1.3) | Core + profile `policy` | ≈ 100 cases; a leak test per function family |

**X39.** `approx_distinct(l)` (HyperLogLog, pinned hash and precision), `approx_percentile(l, p)` (DDSketch: its
buckets do not depend on input order, unlike t-digest, so shards merge to the same bytes), `approx_top(l, k)`. The
result carries an `approximate` mark with its error bound, which the ViewModel passes on, so a screen can show "≈".
A sketch is a value a host may return as a partial aggregate and Core merges.

**X40.** `is_masked(x)`, `mask(x, 'last4' or 'initial' or 'all')` (an author may hide more, never less),
`suppress_small(count, k)`. **Labels**: a host may attach classification labels to values (`internal`, `client`);
they propagate like the mask does (a result carries the union), and the host decides what a label forbids (export,
external sharing). **Profile `policy`**: the host writes row rules in Rachana-EL under a restricted profile (row
columns, `viewer(…)`, the push-down subset only), as form rules use the `form` profile. The Sutra can never widen
access (CORE_AND_PROFILES 3.5); the rule language is simply the same one.

### 3.9 Reuse: names, functions, libraries, tests

| # | Addition | Pri | Cost | Release | Where | Conformance |
|---|---|---|---|---|---|---|
| X41 | Sutra-level `let:` and `functions:` | P1 | 8 bd | R1 | Core | ≈ 80 `sutra` and `problem` cases |
| X42 | Libraries | P1 | 12 bd | R2 | Core + `LibraryResolver` hook | new case kind `lib`; ≈ 80 cases |
| X43 | Tests in Sutras and libraries | P2 | 4 bd | R2 | Core tools | the runner reuses the `expr` case format |

**X41.** Two new top-level keys.

```yaml
let:                                   # named values; each is evaluated at most once per scope
  fx:    "$.trades |> filter(t => t.desk == 'FX')"
  total: "sum(fx, 'notional')"
functions:                             # pure, typed, documented, with examples that are run as tests
  share:
    doc: The part as a share of the whole; empty when the whole is zero.
    params: { part: decimal, whole: decimal }
    returns: decimal
    is: "safe_div(part, whole)"
    examples: [ { expr: "share(1, 4)", result: 0.25 } ]
```

A function body reads only its parameters and other functions. The call graph must have no cycle (checked at load),
so there is no recursion. A local name wins over a Core function added in a later minor, with a warning, so an old
file never changes meaning (X1).

**X42.** A library is a file (`treasury.rachana-lib.yaml`: name, version, `functions`, optional `formats`), shipped
in a pack, a profile or a folder. A Sutra states `libraries: [treasury@2]` and calls `treasury.carry(…)`; a field
followed by `(` is an error today, so the namespaced call is an addition and can never collide with Core. The host's
`LibraryResolver` finds the file (as `FragmentResolver` does); the compile result records the content hash. A library
may use other libraries, without cycles. `rachana doc` writes its reference page from the `doc` texts.

**X43.** `tests:` in a Sutra or library: `{given: {…}, expr, expect}`; every `examples` entry is one too. `rachana
test` runs them in any runtime; a failing example fails the load in strict mode. The format is the conformance `expr`
case, so a library's tests can be donated to the suite unchanged.

### 3.10 Explanation, budgets, speed

| # | Addition | Pri | Cost | Release | Where | Conformance |
|---|---|---|---|---|---|---|
| X44 | Explanation | P1 | 12 bd | R2 | Core | new case kind `explain`; ≈ 100 cases |
| X45 | Counted budgets and the cost model | P1 | 1 bd R0 + 6 bd R1 | R0, R1 | Core | ≈ 60 cases: each budget, exactly at and one past the limit |
| X46 | The optimiser and vectorised execution | P1 | 15 bd | R2 | Core (no language change) | no new cases: the whole suite must pass with it on and off |

**X44. Explanation.** `rachana.explain(sutra, data, target)` returns a tree for any cell: each node has the source
span, the operation, the value in text form (a masked value stays `•••`), the paths and row counts it read, and a
sentence built from the function's `explain` template in the catalogue.

```text
percentile($.pnl[*].v, 0.99)  →  -1,204,300
  "the 0.99 quantile of 500 numbers (Excel PERCENTILE.INC): between the 495th and 496th smallest"
  reads: $.pnl[*].v (500 rows of table pnl, as of 2026-11-17)
```

Bounded (200 nodes, lists shown by their first 5 elements and a count) and deterministic, so it is a conformance
kind. It costs nothing unless asked for. The painter offers it as "how was this computed"; a model receives it as
the ground truth for a narrative, which stops invented explanations.

**X45. Counted budgets.** Added to the limits of SPEC 4.12:

| Budget | Default | Counted as |
|---|---|---|
| steps | 1,000,000 per evaluation | one per node evaluated, plus what each function's cost class charges (`1`, `n`, `n·log n`, `n·m`) |
| cells | 1,000,000 | elements of every list and fields of every record created |
| text | 1,000,000 characters | the length of every text created (`rept`, `concat`, `text_join`) |
| call depth | 64 | nested user-function and lambda calls |

Charges are computed from the expression **as written**, not as optimised, so every runtime and every optimiser stops
at the same point with `RCH-2103` (a new code: "the expression needs more than 1,000,000 steps: …"). A host may
lower a budget, never remove it. The compiler also estimates the cost from the table sizes it knows and warns before
running. Each catalogue entry gains `cost`.

**X46. The optimiser.** Constant folding; common sub-expressions; one evaluation per `let:` name per scope; the
dependency paths that already drive incremental updates extended through lambdas and verbs; row-wise lambdas over a
table run column by column on Arrow data without boxing; typed fast paths when checked mode knows the types;
incremental forms of `group_by` and `window` for live tables. None of it is visible in a result or in a budget.

### 3.11 Tooling and AI

| # | Addition | Pri | Cost | Release | Where | Conformance |
|---|---|---|---|---|---|---|
| X47 | Formatter, linter, style guide | P1 | 6 bd | R1 | Core tools | `edit` cases: format is stable and keeps meaning; one `problem` case per lint rule |
| X48 | The language server | P1 | 12 bd | R2 | Core tools (JavaScript build) | protocol tests in the tools build |
| X49 | The AI kit for expressions | P1 | 6 bd R1 + 4 bd R2 | R1, R2 | Core tools, `spec/1/ai/` | every generated example is a case; evaluation set of ≈ 300 tasks |
| X50 | Translators from Excel formulas | P2 | 8 bd | later (1.3) | Core tools | ≈ 200 pairs (formula, expression, same result) |

**X47.** `rachana fmt` prints the one canonical form (spacing, `@` or named lambda by the style rule, pipe layout
beyond 100 characters, one spelling per function). About 30 lint rules as warnings with fixes: a nested `@`, an
unformatted number in a template, `reduce` where an aggregate exists, a condition that is always true, an unused
`let`, a deprecated spelling, an expression above a cost threshold.

**X48.** `rachana-ls`: diagnostics with the same problems and fixes as the compiler; completion of functions, columns
(from the table schema), control ids, panel events and library names; hover with the catalogue entry and the inferred
type; signature help; go to definition and rename for `let:` names and functions; the explanation tree in the editor.
Rachana Studio uses the same server.

**X49.** (a) A **style guide** of one page, generated, that the formatter enforces. (b) A **few-shot catalogue**: for
every function, 2 to 3 verified examples from `functions.yaml`, and for every intent of AI_GENUI 2.1 the expressions
it needs. (c) **Grammar-constrained decoding**: the EBNF exported as GBNF and Lark with the closed function names in
it, and a **schema-specialised grammar** (`rachana grammar --table sales`) in which a field name can only be a column
of the caller's table, so a model cannot write an unknown name at all. (d) A **JSON tree form** of an expression (the
filter tree of the Data Protocol, extended) for providers with structured output; `rachana print` turns it into text.
(e) The evaluation set of section 1, test 2, run on every release.

**X50.** `rachana from-excel "=IFERROR(VLOOKUP(A2, Limits!A:B, 2, FALSE), 0)"` gives the Rachana expression with the
ranges mapped to named columns, or a list of what it could not map. Most of the 207 Core functions are Excel's, so
the mapping is mostly a table. DAX and SQL expressions later, if asked for.

## 4. What not to add, and why

| Not added | Why |
|---|---|
| General recursion, `while`, loops with a condition | termination cannot be shown; every use in a data screen is covered by `scan`, `reduce`, `sequence`, `schedule` and the bounded root finders |
| Assignment, mutation, statements | an expression is a value; `let` names are enough and keep every sub-expression explainable |
| I/O functions (`http`, files, `now()`, `env`, unseeded `random`) | not deterministic and not safe; data and clock come through hooks and the context |
| `eval(text)`, a function chosen by a text name | the closed set, the cost estimate and the type check would all be lost; an injection door for model-written Sutras |
| Implicit filter context (`CALCULATE`, context transition) | the same expression giving different numbers by position is DAX's main source of wrong figures; explicit rows are clearer to people and models |
| Implicit broadcasting on plain lists (`[1,2] + [3,4]`, scalar functions lifting over lists) | it would change what existing expressions mean; `map`, `zip_with` and the `vec` kind (X10) cover it |
| Safe navigation `?.` | paths are already total: a missing field is empty, never an error |
| Synonyms (`and`/`or`, `<>`, `length`, capitals) | two spellings double what a model must learn and what a formatter must undo; refused with a fix instead (X16) |
| Method-call syntax (`list.map(…)`) | a second way to call; the pipe gives the left-to-right reading |
| List comprehensions (`[x * 2 for x in l]`) | a second way to write `map` and `filter` |
| Destructuring pattern `match` | `ifs`, `switch`, `bucket` and `within` cover the cases a screen has; patterns add much grammar and many ways to be wrong |
| A percent type or `5%` literal | `%` is the remainder operator; percent is a format (`pct2`), the value is `0.05` |
| Text templates inside expressions (back-ticks) | templates exist at the key level; inside an expression `concat` and `text_join` are enough |
| Three-valued logic (SQL NULL), option types | one empty value with stated rules is simpler; `??`, `coalesce`, `nonblank` handle it |
| `try`/`catch`, user-defined error classes | `iferror` and `is_error` are enough for a language without side effects |
| Regular-expression back-references and look-around | they need a backtracking engine, which has no time bound |
| Raw colours, pixel sizes, CSS, HTML in values | names, never styles; also closes a script-injection path |
| Operators or keywords defined by users or profiles, macros | the grammar must stay one grammar for models, tools and constrained decoding |
| Automatic currency conversion, floating-point money | silent conversion is a wrong number that looks right |
| Platform time zones, platform locale, platform Unicode | each differs between the JDK, browsers and Python; pinned packs only |
| Solvers, optimisers, automatic differentiation, symbolic algebra | unbounded work; belongs in a host's engine, shown through a table |
| Pricing models and market data in Core | Core computes on supplied figures; models are a host's (X29 is an optional library) |
| t-digest, KLL and other order-dependent or randomised sketches | results would differ by input order or seed; DDSketch and HyperLogLog do not |
| Type annotations inside expressions | types are inferred or stated in YAML; annotations would be one more thing for a model to get wrong |

## 5. Roadmap and sizes

Everything fits `rachana: 1` as additive minors. **No `rachana: 2` is needed for this proposal.**

| Phase | Language | With | Contents | Size |
|---|---|---|---|---|
| 0 | 1.0 (R0) | i7b, specification only | X1 reservations (2) · X2 numeric rules (2) · X16 first table (2) · X18 classification (2) · X31 code points (1) · X45 catalogue `cost` field (1) | **10 bd** |

**Phase 0 progress (2026-11-19).** X1, X2 (its rules) and X31 (its decision) are in the specification: SPEC 4.13, 4.15 and
4.16, the grammar's `reserved-token`, `spec/1/reserved.yaml` (10 tokens, 278 names, 66 of them to be promoted from Drishti), and 92 conformance
cases (`el/reserved`, `grammar/reserved`, `el/numbers-exact`, `el/text-code-points`), unverified until the compiler changes
after `v0.10.0` is pinned (`conformance/1/el/KNOWN_DIFFERENCES.md`). X16, X18 and X45 are other lanes'.
| 1 | 1.1 (R1) | i8 | **the syntax batch**, in one grammar revision: X6 records (4) · X7 ranges (4) · X11 pipe (3) · X12 `??` (1) · X13 lambdas (8) · X14 comments (2); and X2 decimal build (8) · X8 types I (6) · X15 choice (2) · X16 habits (4) · X17 toolkit (8) · X18 kernel (5) · X19 `group_by` (4) · X41 names and functions (8) · X45 budgets (6) · X47 formatter and linter (6) · X49 AI kit I (6) | **85 bd** |
| 2 | 1.2 (R2) | i9 | X3 temporal (10) · X5 money (7) · X8 types II (6) · X20 verbs and plan (18) · X21 joins (8) · X23 windows (12) · X33 formats (5) · X35 display values (8) · X36 screen state (7) · X42 libraries (12) · X43 tests (4) · X44 explanation (12) · X46 optimiser (15) · X48 language server (12) · X49 AI kit II (4) | **140 bd** |
| 3 | 1.3 | later | X4 zones (8) · X22 pivot (8) · X24 time series (12) · X25 calendars (6) · X26 statistics (8) · X27 risk (8) · X30 text (7) · X37 what-if (7) · X38 live state (4) · X40 masking and policy (9) · X50 Excel translator (8) | **85 bd** |
| 4 | 1.4 | later | X9 units (10) · X10 linear algebra (12) · X28 fixed income (10) · X29 options (6) · X31 Unicode (8) · X32 languages (10) · X34 geography (6) · X39 sketches (9) | **71 bd** |
| | | | **Total** | **391 bd** |

Outside this repository and not in the totals: the painter for display values in Drishti Elements (6 bd), Drishti
adopting the promoted functions and the `Rates` and extended `Calendars` hooks (about 10 bd), Studio using the
language server (in Studio's own plan).

**Order inside a phase.** Phase 1: the syntax batch first (22 bd, one builder, deep-coding grade), because the
prompt kit, the formatter and every example depend on the final grammar; X45 beside it; then X17, X19, X41 in
parallel. Phase 2: X20 and X46 are one lane (the plan and the vectorised runner are the same code); X3 and X5 a
second; X44 and X48 a third.

**Function count.** Core today 207. After phase 1 about 290; after phase 2 about 380; after phase 4 about 540, of
which about 330 are in the base and the rest in Core libraries. For comparison: Excel has about 510 functions and
DAX about 250.

**If a `rachana: 2` is ever cut**, these are the candidates, none needed now and each with a mechanical `rachana
migrate` rule: `a || b` returning the operand; `+` no longer joining text (a separate joining operator); `empty !=
'X'` being empty-aware in one consistent way; lists indexed from 0 but text positions from 1; `one_of` with an empty
choice holding everything; numbers and number-like text being equal (`1 == '1.0'`). Recommendation: keep the list,
decide after 1.3 with real usage figures from the linter.

## 6. Decisions for the owner

| # | Question | Recommendation | If not |
|---|---|---|---|
| **XD1** | Accept the programme as a whole: 50 additions, about 391 bd, five additive minors (1.0 to 1.4)? | **Yes**, with phases 3 and 4 re-confirmed when phase 2 ships. | Phases 0 to 2 only (235 bd) already close gaps (a) to (i) of section 2. |
| **XD2** | Use the window before 1.0 is cut for the reservations (X1)? | **Yes.** It costs 2 bd now; after the cut, each reserved token or name would need a warning period or a major. | Later minors risk colliding with profile names and user names. |
| **XD3** | Are number literals without an exponent exact decimals, with decimal128 arithmetic and `/` rounded half-even to 34 digits (X2)? | **Yes** (it completes A3). `0.1 + 0.2 == 0.3` is then true, as in a SQL host. | Literals stay doubles and only decimal columns are exact; a filter then differs between a literal and a column of the same value. |
| **XD4** | Text measured and ordered by code point (SPEC 9.4, X31), decided before 1.0? | **Yes.** Only characters outside the Basic Multilingual Plane change. | UTF-16 units stay for ever in `rachana: 1`; Python and SQL hosts must imitate them. |
| **XD5** | All new syntax in one minor (1.1): pipe, `??`, lambdas, records, spread, ranges, comments, digit separators? | **Yes.** One grammar revision, one prompt-kit revision, one round of model evaluation. | Syntax arrives over three minors; the grammar card and every model prompt change three times. |
| **XD6** | Add `??`, or refuse it with a fix to `coalesce`? | **Add.** `a \|\| b` returning a boolean is the commonest slip; `??` is what people and models write. | Refuse with a fix: stricter "one way", one more round trip for a model. |
| **XD7** | Ranges include both ends (`1..3` is 1, 2, 3)? | **Yes**: the same as `within` and the range controls, and natural for dates. | Half-open like Python: then `within` and slicing read differently. |
| **XD8** | Lambdas second class, no recursion, function types always checked, step budget as the second guard (X13, X45)? | **Yes.** Evaluation always ends, and that can be shown. | First-class functions: more expressive, but termination then rests on the budget alone. |
| **XD9** | A table is a list of records with a schema; meaning is row-wise; execution is vectorised; no broadcasting on plain lists (X20, X46)? | **Yes.** Nothing existing changes meaning, and the speed comes from the runner. | A separate table kind and column arithmetic: a second data model to learn. |
| **XD10** | Promote the Drishti profile's general functions to Core (`lag`, `lead`, `rank`, `dense_rank`, `percent_rank`, `moving_*`, `running`, `ytd`, `mtd`, `qtd`, `fiscal_ytd`, `prior_period`, `same_period_last_year`, `convert`, `fx`), with the engine as a push-down executor? | **Yes** (rule 9: one function, parity cases). Model functions (`measure`, `cell`, `related`, …) stay in the profile. | Core needs other names for the same things: two ways for ever. |
| **XD11** | No implicit filter context; totals and shares are written explicitly (X36)? | **Yes.** | A `CALCULATE`-like form: familiar to DAX authors, and the hardest thing in DAX to get right. |
| **XD12** | Money in Core with the `Rates` hook; no automatic conversion; mixing currencies is an error (X5)? | **Yes.** | Money stays a number and a text column: sums across currencies remain possible by mistake. |
| **XD13** | Time zones from Core's own pinned pack, default UTC only (X4)? | **Yes.** The platforms' zone data differ. | Platform zones: smaller, but the three runtimes can differ on the same day. |
| **XD14** | Risk helpers carry `runs: host` under Drishti (X27); the option formulas are a library Drishti does not load (X29)? | **Yes**: both of Drishti's rules hold (the server computes risk; Drishti does not price) while standalone hosts get the functions. | Leave X27 and X29 out of Rachana altogether. |
| **XD15** | Libraries as files with versions, called as `name.function(…)`, found by a host hook; local `functions:` first, in 1.1 (X41, X42)? | **Yes.** | Only local functions: no sharing between Sutras except by copying. |
| **XD16** | Explanation as a P1 with its own conformance kind (X44)? | **Yes.** It is the feature no benchmark language has, and it serves both the "how was this computed" tooltip and model narratives. | A debug trace outside the specification: not portable, not testable. |
| **XD17** | Budget defaults: 1,000,000 steps, 1,000,000 cells, 1,000,000 characters, depth 64; a host may lower, never remove; new code `RCH-2103` (X45)? | **Yes**; figures confirmed by measurement against the speed budgets before 1.1. | Limits stay per function, as today: no bound on a composed expression. |
| **XD18** | Approximate aggregates use HyperLogLog and DDSketch, not t-digest (X39)? | **Yes**: the result does not depend on input order. | t-digest: better known, results differ between runs and shards. |
| **XD19** | Display values and tone expressions, names only, with a ViewModel minor and the painter work in Drishti Elements (X35)? | **Yes.** | Microcharts stay panel kinds only; no chart inside a table cell. |
| **XD20** | Phase 1 at 85 bd beside i8, in this repository's own lane? | **Yes, as one lane of two builders**; it touches no Drishti file until the pin moves. If i8 is short of builders: the syntax batch, X45 and X47 only (34 bd), the rest moves to 1.2. | 1.1 ships without new syntax; the model evaluation is done twice. |
| **XD21** | Foreign spellings are always refused with a fix, including `and`/`or` from form rules, migrated by `rachana migrate` (X16, A7b)? | **Yes**; for `and`/`or`/`not` **reversed** 2026-11-21 (DECISIONS PY1: Rachana-EL's own operators). | Accept them as synonyms: easier first contact, two spellings in every file and prompt. |
| **XD22** | `rachana: 2`: not now; keep the candidate list of section 5 and decide after 1.3? | **Yes.** | Cut 2 early: a migration for every existing Sutra with little gain. |
