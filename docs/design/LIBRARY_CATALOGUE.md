<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary. All rights reserved. -->
# Sutra libraries: the catalogue

**Screens become assembly, not writing.**

Status: **planning (design proposal for the owner, 2026-11-19).** No library and no fragment below is built in this
repository. Names, parameters and defaults are proposals; each is settled when its fragment is written with its
document, examples and tests ([LIBRARIES](LIBRARIES.md) 15.1). The framework (what a fragment is, typed parameters,
table contracts, slots, events, versions, packaging) is [LIBRARIES](LIBRARIES.md); the decisions are L-1 to L-20 in
[DECISIONS](DECISIONS.md).

## Contents

1. [How to read an entry](#1-how-to-read-an-entry)
2. [The libraries](#2-the-libraries)
3. [`core`: generic screens](#3-core-generic-screens)
4. [`finance`: trading, risk, market data](#4-finance-trading-risk-market-data)
5. [`genomics`: variants, cohorts, proteins](#5-genomics-variants-cohorts-proteins)
6. [`economy`: countries, indicators, the public sector](#6-economy-countries-indicators-the-public-sector)
7. [`geo`: maps](#7-geo-maps)
8. [`ops`: operations, data quality, administration](#8-ops-operations-data-quality-administration)
9. [`ai`: assistant and generated screens](#9-ai-assistant-and-generated-screens)
10. [What the catalogue asks of the expansion](#10-what-the-catalogue-asks-of-the-expansion)
11. [Counts, waves and sizes](#11-counts-waves-and-sizes)
12. [Shared table contracts](#12-shared-table-contracts)
13. [Libraries considered and not proposed](#13-libraries-considered-and-not-proposed)

## 1. How to read an entry

```text
#### `library/name@1`
Origin · Needs · Wave · Size
What it shows and when to use it.
- Parameters: name: type = default (a required parameter has no default and says so)
- Data, Slots, Events
- Use: a working reference
```

| Field | Values |
|---|---|
| **Origin** | **kit**: exists today as a fragment of Drishti's UI kit (the name in brackets), to be re-published here in the typed form. **pack**: exists today as a fragment owned by a Drishti pack; its generic shape is extracted, with no Drishti field name. **pattern**: written in place, repeatedly, in Drishti's Sutras; to be extracted. **new**: not in Drishti |
| **Needs** | What the fragment requires beyond the **baseline**. The baseline is the language as it is today (`rachana: 1`, the twenty Core panel kinds, controls, events, forms) plus the typed fragment (C1), libraries (X42), the type language (X8) and lambdas (X13). Item numbers are those of [SUTRA_EXPANSION](SUTRA_EXPANSION.md) (L, C, S, I, D, F, V, A, G) and [EL_EXPANSION](EL_EXPANSION.md) (X); K1 to K5 are section 10 of this document |
| **Wave** | LB1 (`core`, baseline), LB2 (the other libraries, baseline), LB3 (needs R2's structure: containers, overlays, state, effects, selections, grid depth), LB4 (needs the chart grammar, windowed tables, the later kinds or the later function libraries). [LIBRARIES](LIBRARIES.md) section 23 |
| **Size** | builder-days for the fragment, its document, examples, synthetic samples, tests and goldens |
| **Parameters** | `name: type = default`. Value types are the EL type language; `table{a: date, b: number, c?: text}` is a table parameter with its contract inline (`?` marks an optional column, mapped at the use: LIBRARIES 5.1); name types (`format`, `tone`, `enum[...]`, `columns`, `state<T>`, `selection`, `message`) are fixed at compile time |

Not repeated in the entries: every fragment takes the placement words on its use (`as`, `title`, `area`, `span`,
`when`, `key`, `requires`, `variant`, `load`, `a11y`), has an `empty` text where it draws rows, prints its own
texts from the library's message table, and names formats and tones, never colours. Column names in contracts are
lower-case words of the domain; a host maps its own names once (`columns: { pnl: PNL_USD }`) or in a private
variant fragment.

## 2. The libraries

| Library | For | Licence (proposed, L-11) | Depends on | Categories |
|---|---|---|---|---|
| **`core`** | any screen: figures, records, tables, charts, layout, filters, forms, states, history | free library licence | none | figures · records · tables · charts · layout and navigation · filters · forms · states and feedback · history and comparison |
| **`finance`** | trading, P&L, market risk, counterparty risk, collateral, market data, funds; generic terms only | proprietary domain library | `core`; later the function libraries `statistics`, `risk`, `timeseries`, `finance` (X23 to X28) | market data · trading · P&L · market risk · limits · counterparty and collateral · funds and equities |
| **`genomics`** | variants, association studies, cohorts, expression, proteins and protein design | proprietary domain library | `core`, `geo` | cards · variants · association · cohorts and expression · genome tracks · proteins and design |
| **`economy`** | countries, macro indicators, forecasts, public finances, elections, official news | proprietary domain library | `core`, `geo` | countries · indicators · forecasts · public finances · politics and news |
| **`geo`** | maps as fragments: shaded regions, points, bubbles, flows, map with table | free library licence | `core`; later the function library `geo` (X34) | regions · points and flows · linked |
| **`ops`** | operations and administration: jobs, health, data freshness and quality, schema, lineage, approvals | free library licence | `core` | runs and health · data quality · schema and lineage · work queues |
| **`ai`** | screens around a model: answers with sources, prompts, transcripts, generated views, explanation, evaluation | free library licence | `core` | answers · conversation · generated views · evaluation |

Seven first-party libraries (decision L-14). The function libraries EL_EXPANSION already names (`timeseries`,
`statistics`, `risk`, `quant`, `units`, `linalg`, `sketch`) are libraries of functions only and are not listed
here; `finance` and `geo` each hold EL_EXPANSION's functions of that name **and** the fragments below.

## 3. `core`: generic screens

Maps are in [`geo`](#7-geo-maps), which carries the same free licence and ships beside `core`.

### 3.1 Figures

#### `core/key-figure@1`
Origin: kit (`key-figure`) · Needs: baseline · Wave: LB1 · Size: 0.5

One headline number, large, with its label and a caption: the figure a reader looks for first.
- **Parameters:** `value: number` (required) · `label: text = "Value"` · `fmt: format = amount2` · `caption: text? = none` · `delta: number? = none` · `delta-fmt: format = signed2` · `tone: tone = none`
- **Data:** none · **Slots:** none · **Events:** none
- **Use:** `{ use: core/key-figure@1, title: Revenue, with: { value: "sum($.sales, 'revenue')", label: Total, fmt: amount0 } }`

#### `core/kpi-strip@1`
Origin: pattern (rows of metric panels at the head of dashboards) · Needs: L1 (`group` with `each`) · Wave: LB3 · Size: 1

A row of up to eight headline figures from a list: label, value, optional change against a reference.
- **Parameters:** `items: list<record{label: text, value: number, delta: number?, caption: text?}>` (required) · `fmt: format = amount0` · `delta-fmt: format = signed1` · `per-row: integer = 4` · `tone: tone = sign`
- **Data:** none (the list is a value; build it with `let:`) · **Slots:** none · **Events:** `pick {label: text}`
- **Use:** `{ use: core/kpi-strip@1, with: { items: "[{label: 'Orders', value: size($.orders)}, {label: 'Revenue', value: sum($.orders, 'amount')}]" } }`

#### `core/figure-trend@1`
Origin: new · Needs: L1 · Wave: LB3 · Size: 1

A figure with the small line of its recent history beside it: the level and the direction in one card.
- **Parameters:** `rows: table{date: date, value: number}` (required) · `label: text = "Value"` · `fmt: format = amount0` · `compare: enum[first, previous, none] = previous` · `periods: integer = 30`
- **Data:** `rows` ordered by `date`; `live: ok` · **Slots:** none · **Events:** none
- **Use:** `{ use: core/figure-trend@1, title: Active users, with: { rows: $.daily_users } }`

#### `core/gauge-figure@1`
Origin: new · Needs: baseline · Wave: LB1 · Size: 0.5

A value against a maximum as a gauge with warning and breach bands.
- **Parameters:** `value: number` (required) · `max: number` (required) · `warn-at: number = 0.8` · `label: text = "Used"` · `fmt: format = pct1`
- **Data:** none · **Slots:** none · **Events:** none
- **Use:** `{ use: core/gauge-figure@1, title: Disk, with: { value: "$.disk.used", max: "$.disk.size" } }`

#### `core/status-badges@1`
Origin: pattern (status panels on record screens) · Needs: baseline · Wave: LB1 · Size: 0.5

A short row of named states (Active, Confirmed, Late), each toned by its value.
- **Parameters:** `items: list<record{label: text, value: text}>` (required) · `tone: tone = status`
- **Data:** none · **Slots:** none · **Events:** none
- **Use:** `{ use: core/status-badges@1, with: { items: "[{label: 'Status', value: $.status}, {label: 'Approval', value: $.approval}]" } }`

#### `core/period-compare@1`
Origin: new · Needs: baseline · Wave: LB1 · Size: 0.5

One measure now against the same measure in a reference period, with the change in value and per cent.
- **Parameters:** `now: number` (required) · `then: number` (required) · `label: text = "Now"` · `then-label: text = "Before"` · `fmt: format = amount0` · `good: enum[up, down] = up`
- **Data:** none · **Slots:** none · **Events:** none
- **Use:** `{ use: core/period-compare@1, title: Sales, with: { now: "$.mtd", then: "$.mtd_last_year", then-label: Last year } }`

### 3.2 Records

#### `core/field-list@1`
Origin: kit (`field-list`) · Needs: baseline · Wave: LB1 · Size: 0.5

Every field of one record as label and value; the plain facts panel.
- **Parameters:** `record: record` (required) · `only: list<text>? = none` · `labels: record? = none` · `formats: record? = none` · `columns: integer = 1`
- **Data:** none · **Slots:** none · **Events:** none
- **Use:** `{ use: core/field-list@1, title: Details, with: { record: "$.customer", only: [name, segment, since] } }`

#### `core/record-card@1`
Origin: pack (company and filing cards) · Needs: baseline · Wave: LB1 · Size: 1

A record as a card: a title, a line under it, up to eight chosen facts, and a status.
- **Parameters:** `name: text` (required) · `subtitle: text? = none` · `facts: list<record{label: text, value: any, fmt: text?}>` (required) · `status: text? = none` · `status-tone: tone = status`
- **Data:** none · **Slots:** `footer` (panels, max 2) · **Events:** none
- **Use:** `{ use: core/record-card@1, with: { name: "$.name", subtitle: "$.sector", facts: "[{label: 'Founded', value: $.founded}, {label: 'Employees', value: $.staff, fmt: 'amount0'}]" } }`

#### `core/record-header@1`
Origin: pattern (the head of every entity screen) · Needs: L1 · Wave: LB3 · Size: 1

The head of a record screen: identity on the left, status badges, up to four figures on the right, actions.
- **Parameters:** `name: text` (required) · `id: text? = none` · `badges: list<record{label: text, value: text}>? = none` · `figures: list<record{label: text, value: number, fmt: text?}>? = none`
- **Data:** none · **Slots:** `actions` (actions, max 5) · **Events:** none
- **Use:** `{ use: core/record-header@1, with: { name: "$.name", id: "$.id", figures: "[{label: 'Balance', value: $.balance}]" } }`

#### `core/note@1`
Origin: kit (`explainer-note`) · Needs: baseline · Wave: LB1 · Size: 0.5

A short note telling the reader how to read the figures beside it. Text only, never markup.
- **Parameters:** `text: text` (required) · `tone: tone = none`
- **Data:** none · **Slots:** none · **Events:** none
- **Use:** `{ use: core/note@1, title: How to read this, with: { text: "Gains are positive. Figures are in thousands." } }`

#### `core/source-note@1`
Origin: kit (the Core-pure part of `source-footer`) · Needs: baseline · Wave: LB1 · Size: 0.5

The foot of a screen over published data: publisher, dataset, retrieval date, licence.
- **Parameters:** `publisher: text` (required) · `dataset: text? = none` · `retrieved: date? = none` · `licence: text? = none` · `link: text? = none`
- **Data:** none · **Slots:** none · **Events:** none
- **Use:** `{ use: core/source-note@1, area: right, with: { publisher: "$.source.publisher", retrieved: "$.source.retrieved" } }`

### 3.3 Tables

#### `core/data-table@1`
Origin: pack (a table whose columns are a parameter) · Needs: baseline · Wave: LB1 · Size: 1

A sortable, searchable table of rows with the columns the user gives, totals, and a drill on a row click.
- **Parameters:** `rows: table{}` (required; any columns) · `columns: columns` (required) · `key: text? = none` · `search: boolean = true` · `limit: integer = 200` · `total-label: text = "Total"`
- **Data:** `rows`; `key` required for drill and live; `live: ok`; `window: no` (use `core/big-table`) · **Slots:** none · **Events:** `open {key: text, row: record}`
- **Use:** `{ use: core/data-table@1, title: Orders, with: { rows: $.orders, key: id, columns: [ { label: Order, bind: "@.id" }, { label: Amount, bind: "@.amount", fmt: amount0, total: true } ] }, on: { open: [ { go: { sutra: order, args: { id: "event.key" } } } ] } }`

#### `core/measure-table@1`
Origin: kit (`measure-table`) · Needs: baseline · Wave: LB1 · Size: 0.5

A list as a table: a name column and two measures, the first totalled, the second toned by sign when asked.
- **Parameters:** `rows: table{name: text, first: number, second?: number}` (required) · `name-label: text = "Name"` · `first-label: text = "Value"` · `first-fmt: format = amount0` · `first-total: boolean = true` · `second-label: text = "Change"` · `second-fmt: format = pc2` · `second-tone: tone = none`
- **Data:** `rows` in the order given · **Slots:** none · **Events:** none
- **Use:** `{ use: core/measure-table@1, title: By region, with: { rows: { from: $.by_region, columns: { name: region, first: revenue, second: growth } }, second-tone: sign } }`

#### `core/row-picker@1`
Origin: kit (`row-picker`) · Needs: baseline · Wave: LB1 · Size: 0.5

A compact table whose rows, clicked, fire an event with the row's key: the master of a master-detail screen.
- **Parameters:** `rows: table{id: text, name: text, type?: text, amount?: number, date?: date}` (required) · `amount-fmt: format = amount0` · heading texts for each column
- **Data:** `rows`; key `id` · **Slots:** none · **Events:** `select {id: text, row: record}`
- **Use:** `{ use: core/row-picker@1, as: pick, title: Customers, with: { rows: $.customers } }`

#### `core/master-detail@1`
Origin: pattern (a picker with child panels, on several screens) · Needs: L1 (`split`), S1 · Wave: LB3 · Size: 2

A list on the left and, beside it, whatever the user puts in the detail slot for the chosen row.
- **Parameters:** `rows: table{}` (required) · `key: text` (required) · `columns: columns` (required) · `sizes: list<integer> = [5, 7]` · `first-chosen: boolean = true`
- **Data:** `rows`; key required; `live: ok` · **Slots:** `detail` (panels, max 6; exposes `row: record`), `empty-detail` (panels, max 1) · **Events:** `select {key: text, row: record}` · **Outputs:** `chosen: text`
- **Use:** `{ use: core/master-detail@1, as: md, with: { rows: $.customers, key: id, columns: [ { label: Name, bind: "@.name" } ] }, slots: { detail: [ { use: core/field-list@1, with: { record: "slot.row" } } ] } }`

#### `core/drill-table@1`
Origin: pattern (tables with `levels` and a crumb) · Needs: baseline · Wave: LB1 · Size: 1

A table that regroups in place as the reader clicks down a list of levels, with a crumb to climb back.
- **Parameters:** `rows: table{}` (required) · `levels: list<text>` (required; column names, outermost first) · `measures: columns` (required) · `fmt: format = amount0`
- **Data:** `rows` holding every level column and measure; `window: ok` · **Slots:** none · **Events:** `leaf {path: list<text>, row: record}`
- **Use:** `{ use: core/drill-table@1, title: Sales, with: { rows: $.sales, levels: [region, country, city], measures: [ { label: Revenue, bind: "@.revenue", total: true } ] } }`

#### `core/tree-table@1`
Origin: new · Needs: baseline · Wave: LB1 · Size: 1

A hierarchy given as rows with a parent column, shown as an expanding table with measures per node.
- **Parameters:** `rows: table{id: text, parent: text, name: text}` (required) · `measures: columns` (required) · `open-levels: integer = 1`
- **Data:** `rows`: one row a node, `parent` empty at the root; measures already rolled up by the host · **Slots:** none · **Events:** `select {id: text, row: record}`
- **Use:** `{ use: core/tree-table@1, title: Organisation, with: { rows: $.units, measures: [ { label: Headcount, bind: "@.headcount" } ] } }`

#### `core/pivot-table@1`
Origin: pattern (pivot panels) · Needs: baseline · Wave: LB1 · Size: 0.5

A cross-tabulation of one measure by a row field and a column field, with totals and an optional heat scale.
- **Parameters:** `rows: table{}` (required) · `by: text` (required) · `across: text` (required) · `value: text` (required) · `agg: enum[sum, avg, min, max, count] = sum` · `fmt: format = amount0` · `heat: boolean = false` · `totals: boolean = true`
- **Data:** `rows` at any grain; `window: ok` · **Slots:** none · **Events:** `cell {by: text, across: text, value: number}`
- **Use:** `{ use: core/pivot-table@1, title: Revenue, with: { rows: $.sales, by: region, across: quarter, value: revenue, heat: true } }`

#### `core/pivot-board@1`
Origin: new · Needs: S1, I1, L1 · Wave: LB3 · Size: 2

A pivot the reader re-arranges: pickers for the row field, the column field, the measure and the aggregate.
- **Parameters:** `rows: table{}` (required) · `dimensions: list<text>` (required) · `measures: list<text>` (required) · `by: text` (required; the first arrangement) · `across: text` (required) · `fmt: format = amount0`
- **Data:** `rows`; `window: ok` · **Slots:** none · **Events:** `cell {by: text, across: text, value: number}` · **Outputs:** `by: text`, `across: text`, `measure: text`
- **Use:** `{ use: core/pivot-board@1, with: { rows: $.sales, dimensions: [region, product, channel, quarter], measures: [revenue, units], by: region, across: quarter } }`

#### `core/grouped-table@1`
Origin: new · Needs: V3 (`group-by`, pinning, column groups) · Wave: LB3 · Size: 1

A table grouped by one or two columns with subtotals, a pinned first column and optional column groups.
- **Parameters:** `rows: table{}` (required) · `group-by: list<text>` (required) · `columns: columns` (required) · `pin: text? = none` · `open: boolean = true`
- **Data:** `rows`; `live: ok` · **Slots:** none · **Events:** `open {key: text, row: record}`
- **Use:** `{ use: core/grouped-table@1, with: { rows: $.orders, group-by: [status], columns: [ { label: Order, bind: "@.id" }, { label: Qty, bind: "@.qty", total: true } ] } }`

#### `core/big-table@1`
Origin: new · Needs: D2 (windowed tables), V3 · Wave: LB4 · Size: 2

A table over a table too large to send: sort, filter and scroll are answered by the provider a window at a time.
- **Parameters:** `rows: table{}` (required; windowed) · `columns: columns` (required) · `key: text` (required) · `window: integer = 200`
- **Data:** `rows` with `window: ok`, key required, provider capabilities `page`, `sort`, `filter` · **Slots:** `menu` (menu, max 8) · **Events:** `open {key: text, row: record}`
- **Use:** `{ use: core/big-table@1, with: { rows: $.events, key: id, columns: [ { label: Time, bind: "@.at", fmt: datetime }, { label: Event, bind: "@.name" } ] } }`

#### `core/leaderboard@1`
Origin: new · Needs: V2 (bars in cells) · Wave: LB3 · Size: 0.5

Rows ranked by a score, with the rank, the name, the score as a bar in the cell, and the change in rank.
- **Parameters:** `rows: table{name: text, score: number, previous-rank?: integer}` (required) · `top: integer = 10` · `fmt: format = amount0` · `score-label: text = "Score"`
- **Data:** `rows`, any order (ranked by `score`) · **Slots:** none · **Events:** `select {name: text, row: record}`
- **Use:** `{ use: core/leaderboard@1, title: Top sellers, with: { rows: { from: $.sellers, columns: { score: revenue } } } }`

#### `core/headline-list@1`
Origin: pack (headline and filing lists) · Needs: baseline · Wave: LB1 · Size: 0.5

Dated items with a headline, a source and a link: news, announcements, filings.
- **Parameters:** `rows: table{date: datetime, headline: text, source?: text, link?: text}` (required) · `limit: integer = 20` · `date-fmt: format = date`
- **Data:** `rows`, newest first; `live: ok` · **Slots:** none · **Events:** `open {link: text, row: record}`
- **Use:** `{ use: core/headline-list@1, title: Announcements, with: { rows: $.news } }`

### 3.4 Charts

#### `core/series-line@1`
Origin: kit (`series-line`) · Needs: baseline · Wave: LB1 · Size: 0.5

A line of one series: x along the bottom, y up the side.
- **Parameters:** `rows: table{x: any, y: number}` (required) · `fmt: format = amount2` · `unit: text? = none` · `step: boolean = false` · `mark: any? = none` (an x value to mark)
- **Data:** `rows` in x order; `live: ok` · **Slots:** none · **Events:** `point {x: any, y: number}`
- **Use:** `{ use: core/series-line@1, title: Price, with: { rows: { from: $.history, columns: { x: date, y: close } }, fmt: price2 } }`

#### `core/multi-line@1`
Origin: new · Needs: V1 (chart grammar) · Wave: LB4 · Size: 1

Several series on one time axis, coloured by a field, with a legend the reader can switch series off in.
- **Parameters:** `rows: table{x: any, y: number, series: text}` (required) · `fmt: format = amount2` · `palette: palette = categorical-1` · `rebase: boolean = false` (each series as 100 at its first point) · `max-series: integer = 12`
- **Data:** `rows` in long form (one row per series and x) · **Slots:** `layers` (layers, max 4) · **Events:** `point {series: text, x: any, y: number}`
- **Use:** `{ use: core/multi-line@1, title: Revenue by region, with: { rows: { from: $.monthly, columns: { x: month, y: revenue, series: region } } } }`

#### `core/area-stack@1`
Origin: pattern (area panels with a limit line) · Needs: baseline · Wave: LB1 · Size: 0.5

Series stacked as areas over time, with an optional horizontal limit.
- **Parameters:** `rows: table{x: any}` (required) · `series: list<text>` (required; the columns to stack) · `limit: number? = none` · `limit-label: text = "Limit"` · `unit: text? = none`
- **Data:** `rows` in wide form (one column per series) · **Slots:** none · **Events:** none
- **Use:** `{ use: core/area-stack@1, title: Usage, with: { rows: { from: $.usage, columns: { x: day } }, series: [compute, storage, network] } }`

#### `core/ranked-bars@1`
Origin: kit (`ranked-bars`) · Needs: baseline · Wave: LB1 · Size: 0.5

One horizontal bar a row, labelled and sized by a value, largest first.
- **Parameters:** `rows: table{label: text, value: number}` (required) · `fmt: format = amount0` · `tone: tone = none` · `top: integer? = none` · `feeds: selection? = none`
- **Data:** `rows` · **Slots:** none · **Events:** `bar {label: text, value: number}`
- **Use:** `{ use: core/ranked-bars@1, title: Revenue by region, with: { rows: "group($.sales, 'region', 'revenue')" } }`

#### `core/donut-breakdown@1`
Origin: pattern (grouped bars drawn as a donut) · Needs: baseline · Wave: LB1 · Size: 0.5

The shares of a whole as a donut, at most eight slices, the rest as "Other".
- **Parameters:** `rows: table{label: text, value: number}` (required) · `fmt: format = amount0` · `max-slices: integer = 8` · `other-label: text = "Other"`
- **Data:** `rows` · **Slots:** none · **Events:** `slice {label: text, value: number}`
- **Use:** `{ use: core/donut-breakdown@1, title: Mix, with: { rows: "group($.sales, 'channel', 'revenue')" } }`

#### `core/stacked-bars@1`
Origin: new · Needs: V1 · Wave: LB4 · Size: 1

Bars by category split by a second field: stacked, side by side, or as shares of 100%.
- **Parameters:** `rows: table{category: text, series: text, value: number}` (required) · `stack: enum[sum, percent, none] = sum` · `orient: enum[vertical, horizontal] = vertical` · `fmt: format = amount0` · `palette: palette = categorical-1`
- **Data:** `rows` in long form · **Slots:** none · **Events:** `bar {category: text, series: text, value: number}`
- **Use:** `{ use: core/stacked-bars@1, with: { rows: { from: $.sales, columns: { category: quarter, series: region, value: revenue } }, stack: percent } }`

#### `core/bar-line@1`
Origin: new · Needs: V1 (two axes) · Wave: LB4 · Size: 1

Bars for one measure and a line for another on a second axis, over the same x.
- **Parameters:** `rows: table{x: any, bar: number, line: number}` (required) · `bar-label: text` (required) · `line-label: text` (required) · `bar-fmt: format = amount0` · `line-fmt: format = pct1`
- **Data:** `rows` in x order · **Slots:** none · **Events:** `point {x: any}`
- **Use:** `{ use: core/bar-line@1, with: { rows: { from: $.monthly, columns: { x: month, bar: revenue, line: margin } }, bar-label: Revenue, line-label: Margin } }`

#### `core/distribution@1`
Origin: pattern (histograms with markers) · Needs: baseline · Wave: LB1 · Size: 0.5

A histogram of one numeric column, with up to four marked values (a mean, a threshold).
- **Parameters:** `values: list<number>` (required) · `bins: integer = 40` · `markers: list<record{label: text, value: number}>? = none` · `fmt: format = compact` · `unit: text? = none`
- **Data:** none (a list; `pluck` it from a table) · **Slots:** none · **Events:** none
- **Use:** `{ use: core/distribution@1, title: Order size, with: { values: "$.orders[*].amount", markers: "[{label: 'Mean', value: avg($.orders, 'amount')}]" } }`

#### `core/scatter-plot@1`
Origin: pattern (scatter panels) · Needs: baseline · Wave: LB1 · Size: 0.5

Points by two measures, optionally sized, grouped by colour and labelled.
- **Parameters:** `rows: table{x: number, y: number, label?: text, group?: text, size?: number}` (required) · `x-label: text` (required) · `y-label: text` (required) · `x-fmt: format = amount2` · `fmt: format = amount2`
- **Data:** `rows`; `max-rows: 20000` · **Slots:** none · **Events:** `point {label: text, row: record}`
- **Use:** `{ use: core/scatter-plot@1, with: { rows: { from: $.products, columns: { x: price, y: units, label: name } }, x-label: Price, y-label: Units } }`

#### `core/steps-waterfall@1`
Origin: pattern (waterfall panels) · Needs: baseline · Wave: LB1 · Size: 0.5

From a starting total to an ending total through signed steps.
- **Parameters:** `rows: table{label: text, value: number, total?: boolean}` (required) · `fmt: format = amount0` · `unit: text? = none` · `sum-label: text = "Total"`
- **Data:** `rows` in step order · **Slots:** none · **Events:** `step {label: text, value: number}`
- **Use:** `{ use: core/steps-waterfall@1, title: Budget to actual, with: { rows: $.bridge } }`

#### `core/small-multiples@1`
Origin: new · Needs: L1 (`each`) · Wave: LB3 · Size: 1

The same small line chart once per value of a field, on a shared scale, at most 50.
- **Parameters:** `rows: table{x: any, y: number, facet: text}` (required) · `per-row: integer = 4` · `shared-scale: boolean = true` · `fmt: format = amount0`
- **Data:** `rows` in long form · **Slots:** none · **Events:** `facet {facet: text}`
- **Use:** `{ use: core/small-multiples@1, with: { rows: { from: $.monthly, columns: { x: month, y: revenue, facet: region } } } }`

#### `core/spark-table@1`
Origin: new · Needs: V2 (sparklines in cells) · Wave: LB3 · Size: 0.5

A table with a name, a latest value, a change and a sparkline of the history in each row.
- **Parameters:** `rows: table{name: text, latest: number, change?: number, history: list<number>}` (required) · `fmt: format = amount2` · `change-fmt: format = signed2`
- **Data:** `rows`, one per series, the history as a list in the row · **Slots:** none · **Events:** `select {name: text}`
- **Use:** `{ use: core/spark-table@1, title: Indicators, with: { rows: $.indicators } }`

#### `core/network-graph@1`
Origin: new · Needs: baseline (kind `graph`) · Wave: LB1 · Size: 0.5

Nodes and edges: who is connected to whom, grouped by colour.
- **Parameters:** `nodes: table{id: text, label: text, group?: text}` (required) · `edges: table{from: text, to: text, label?: text}` (required) · `layout: enum[force, layered, radial] = force`
- **Data:** `nodes`, `edges`; `max-rows: 2000` each · **Slots:** none · **Events:** `node {id: text}`
- **Use:** `{ use: core/network-graph@1, title: Ownership, with: { nodes: $.entities, edges: $.holdings } }`

#### `core/treemap-breakdown@1`
Origin: new · Needs: V4 (kind `treemap`) · Wave: LB4 · Size: 0.5

A whole split into nested rectangles by one or two levels, sized by a measure and toned by another.
- **Parameters:** `rows: table{group: text, item: text, size: number, shade?: number}` (required) · `fmt: format = amount0` · `shade-tone: tone = sign`
- **Data:** `rows` · **Slots:** none · **Events:** `cell {group: text, item: text}`
- **Use:** `{ use: core/treemap-breakdown@1, with: { rows: { from: $.products, columns: { group: category, item: name, size: revenue, shade: growth } } } }`

#### `core/calendar-heat@1`
Origin: new · Needs: V4 (kind `calendar`) · Wave: LB4 · Size: 0.5

A year of days as a grid, each day shaded by a value: activity, volume, incidents.
- **Parameters:** `rows: table{date: date, value: number}` (required) · `fmt: format = amount0` · `year: integer? = none`
- **Data:** `rows`, one per day · **Slots:** none · **Events:** `day {date: date, value: number}`
- **Use:** `{ use: core/calendar-heat@1, title: Orders per day, with: { rows: $.daily_orders } }`

#### `core/flow-sankey@1`
Origin: new · Needs: V4 (kind `sankey`) · Wave: LB4 · Size: 0.5

Quantities flowing from sources to targets through up to four stages.
- **Parameters:** `rows: table{from: text, to: text, value: number}` (required) · `fmt: format = amount0`
- **Data:** `rows`; no cycles · **Slots:** none · **Events:** `link {from: text, to: text}`
- **Use:** `{ use: core/flow-sankey@1, title: Funnel, with: { rows: $.transitions } }`

#### `core/box-compare@1`
Origin: new · Needs: V4 (kind `box`), X26 (quartiles) · Wave: LB4 · Size: 0.5

The spread of a measure per group as box plots: median, quartiles, whiskers, outliers.
- **Parameters:** `rows: table{group: text, value: number}` (required) · `fmt: format = amount2`
- **Data:** `rows` at observation grain · **Slots:** none · **Events:** none
- **Use:** `{ use: core/box-compare@1, title: Delivery days by carrier, with: { rows: { from: $.shipments, columns: { group: carrier, value: days } } } }`

### 3.5 Layout and navigation

#### `core/page-header@1`
Origin: pattern (the title row of dashboards) · Needs: L1, I1 · Wave: LB3 · Size: 1

A screen's head: title, a line under it, the as-of time with a freshness tone, and action buttons.
- **Parameters:** `heading: text` (required) · `subtitle: text? = none` · `as-of: datetime? = none` · `stale-after: duration? = none`
- **Data:** none · **Slots:** `actions` (actions, max 5), `filters` (controls, max 6) · **Events:** none
- **Use:** `{ use: core/page-header@1, with: { heading: "Sales overview", as-of: "$.loaded_at" }, slots: { actions: [ { label: Export, do: [ { export: detail } ] } ] } }`

#### `core/tab-set@1`
Origin: pattern (tabbed areas) · Needs: L1 (`arrange: tabs`), S1 · Wave: LB3 · Size: 1

Named tabs, each holding the user's panels; the open tab can be kept in the address.
- **Parameters:** `tabs: list<record{id: text, title: text}>` (required; at most 8) · `into: state<text>? = none` · `lazy: boolean = true`
- **Data:** none · **Slots:** one per tab, named by its id (panels, max 12) · **Events:** `change {tab: text}`
- **Use:** `{ use: core/tab-set@1, with: { tabs: [ { id: summary, title: Summary }, { id: detail, title: Detail } ] }, slots: { summary: [ ... ], detail: [ ... ] } }`

#### `core/split-view@1`
Origin: new · Needs: L1 (`arrange: split`) · Wave: LB3 · Size: 0.5

Two sides with a divider the reader can move.
- **Parameters:** `sizes: list<integer> = [6, 6]` · `orient: enum[row, column] = row`
- **Data:** none · **Slots:** `first` (panels, max 8), `second` (panels, max 8) · **Events:** none
- **Use:** `{ use: core/split-view@1, with: { sizes: [8, 4] }, slots: { first: [ ... ], second: [ ... ] } }`

#### `core/fold-section@1`
Origin: pattern (folding sections) · Needs: L1 (`arrange: accordion`) · Wave: LB3 · Size: 0.5

A titled section the reader can fold away, with a one-line summary shown while folded.
- **Parameters:** `heading: text` (required) · `summary: text? = none` · `open: boolean = true`
- **Data:** none · **Slots:** `body` (panels, max 12) · **Events:** none
- **Use:** `{ use: core/fold-section@1, with: { heading: Advanced, open: false }, slots: { body: [ ... ] } }`

#### `core/detail-drawer@1`
Origin: new · Needs: L2 (overlays), I1 · Wave: LB3 · Size: 1

A drawer from the side that shows the user's panels for one chosen thing, with a close action and a title.
- **Parameters:** `heading: text` (required) · `open-when: boolean` (required) · `size: integer = 5` · `side: enum[right, left] = right`
- **Data:** none · **Slots:** `body` (panels, max 8), `actions` (actions, max 3) · **Events:** `closed {}`
- **Use:** `{ use: core/detail-drawer@1, with: { heading: "Order ${state.order}", open-when: "state.order != null" }, slots: { body: [ ... ] }, on: { closed: [ { set: { order: null } } ] } }`

#### `core/confirm-dialog@1`
Origin: new · Needs: L2, I1 · Wave: LB3 · Size: 0.5

A modal question with a consequence line and two answers.
- **Parameters:** `question: text` (required) · `consequence: text? = none` · `open-when: boolean` (required) · `yes-label: text = "Confirm"` · `no-label: text = "Cancel"` · `tone: tone = warn`
- **Data:** none · **Slots:** none · **Events:** `yes {}`, `no {}`
- **Use:** `{ use: core/confirm-dialog@1, with: { question: "Delete ${size(sel.rows)} rows?", open-when: "state.confirming" }, on: { yes: [ { host: rows.delete, with: { ids: sel.rows } } ] } }`

#### `core/wizard-shell@1`
Origin: new · Needs: S3 (`flow`), L1 (`arrange: steps`) · Wave: LB4 · Size: 2

A multi-step shell: a step list, one step's panels at a time, Back and Next that obey each step's condition, a review step.
- **Parameters:** `steps: list<record{id: text, title: text}>` (required; at most 10) · `can-advance: fn(step: text) -> boolean` (required) · `finish-label: text = "Finish"`
- **Data:** none · **Slots:** one per step, named by its id (panels, max 8); `review` (panels, max 6) · **Events:** `step {from: text, to: text}`, `finish {}` · **Outputs:** `step: text`
- **Use:** `{ use: core/wizard-shell@1, as: wz, with: { steps: [ { id: who, title: Who }, { id: what, title: What } ], can-advance: "s => s == 'who' ? state.name != '' : true" }, slots: { who: [ ... ], what: [ ... ] } }`

#### `core/launch-tiles@1`
Origin: new · Needs: L1 (`each`), I1 · Wave: LB3 · Size: 0.5

A grid of tiles, each a name, a line of words and a figure, that fire an event when chosen: a home page.
- **Parameters:** `items: list<record{id: text, title: text, summary: text?, figure: number?}>` (required) · `per-row: integer = 4` · `fmt: format = amount0`
- **Data:** none · **Slots:** none · **Events:** `open {id: text}`
- **Use:** `{ use: core/launch-tiles@1, with: { items: "$.screens" }, on: { open: [ { host: nav.open, with: { id: "event.id" } } ] } }`

### 3.6 Filters

#### `core/filter-bar@1`
Origin: pattern (a row of controls over a dashboard) · Needs: S1, I1 (controls inside a fragment are C1) · Wave: LB3 · Size: 2

A row of filters over given columns of a table (a choice list per text column, a range per date or number), with Clear, and the combined condition as an output.
- **Parameters:** `rows: table{}` (required) · `fields: list<record{column: text, label: text, kind: text?}>` (required; at most 8) · `into: state<record>? = none`
- **Data:** `rows` (the options are its distinct values; `window: ok` with provider `distinct`) · **Slots:** `extra` (controls, max 4) · **Events:** `change {values: record}` · **Outputs:** `values: record`, `active: integer`
- **Use:** `{ use: core/filter-bar@1, as: f, with: { rows: $.orders, fields: [ { column: status, label: Status }, { column: placed, label: Placed, kind: date-range } ] } }`

#### `core/search-box@1`
Origin: pattern (search controls) · Needs: S1 · Wave: LB3 · Size: 0.5

A search field that writes what was typed into a state the user names, with a result count.
- **Parameters:** `into: state<text>` (required) · `placeholder: text = "Search"` · `count: integer? = none` · `min-chars: integer = 2`
- **Data:** none · **Slots:** none · **Events:** `search {text: text}`
- **Use:** `{ use: core/search-box@1, with: { into: q, count: "size($.customers[?contains(@.name, state.q)])" } }`

#### `core/segmented-filter@1`
Origin: pattern (segmented controls) · Needs: S1 · Wave: LB3 · Size: 0.5

A few choices as a segmented control, written into a state the user names.
- **Parameters:** `options: list<text>` (required; at most 7) · `into: state<text>` (required) · `all-label: text? = "All"`
- **Data:** none · **Slots:** none · **Events:** `change {value: text}`
- **Use:** `{ use: core/segmented-filter@1, with: { options: "distinct($.orders, 'status')", into: status } }`

#### `core/date-range-filter@1`
Origin: pattern (date-range controls on most list screens) · Needs: S1, X7 (ranges) · Wave: LB3 · Size: 0.5

A from..to date choice with quick ranges (today, week, month to date, year to date).
- **Parameters:** `into: state<text>` (required; a `from..to` range) · `quick: list<text> = [today, wtd, mtd, ytd]` · `min: date? = none` · `max: date? = none`
- **Data:** none · **Slots:** none · **Events:** `change {from: date, to: date}`
- **Use:** `{ use: core/date-range-filter@1, with: { into: window } }`

#### `core/cross-filter-bars@1`
Origin: new · Needs: I2 (selections) · Wave: LB3 · Size: 0.5

Ranked bars whose clicks feed a selection the user names, so every panel that reads it follows.
- **Parameters:** `rows: table{label: text, value: number}` (required) · `feeds: selection` (required) · `multi: boolean = true` · `fmt: format = amount0`
- **Data:** `rows` · **Slots:** none · **Events:** `change {values: list<text>}`
- **Use:** `{ use: core/cross-filter-bars@1, title: By desk, with: { rows: "group($.positions, 'desk', 'pnl')", feeds: desk } }`

#### `core/saved-views@1`
Origin: new · Needs: S4 (view-state document), I1 · Wave: LB4 · Size: 1

The reader's saved arrangements of this screen (filters, state, selections): pick one, save the current one.
- **Parameters:** `views: table{id: text, name: text, owner?: text}` (required) · `current: text? = none`
- **Data:** `views` (the host stores view-state documents) · **Slots:** none · **Events:** `apply {id: text}`, `save {name: text}`, `remove {id: text}`
- **Use:** `{ use: core/saved-views@1, with: { views: $.my_views }, on: { apply: [ { host: views.apply, with: { id: "event.id" } } ] } }`

### 3.7 Forms

#### `core/entry-form@1`
Origin: pattern (forms on record screens) · Needs: baseline (`form:` without save) · Wave: LB1 · Size: 1

A form of the fields the user lists, with their rules and hints, that raises one event when sent.
- **Parameters:** `fields: list<record{id: text, label: text, type: text, required: boolean?, options: list<text>?}>` (required; at most 30) · `initial: record? = none` · `submit-label: text = "Save"`
- **Data:** none · **Slots:** `fields` (fields, max 10) · **Events:** `submit {values: record}`, `cancel {}`
- **Use:** `{ use: core/entry-form@1, with: { fields: [ { id: name, label: Name, type: text, required: true }, { id: tier, label: Tier, type: enum, options: [A, B, C] } ] }, on: { submit: [ { host: customer.save, with: { values: "event.values" } } ] } }`

#### `core/stepped-form@1`
Origin: pattern (forms with `steps`) · Needs: baseline · Wave: LB1 · Size: 1

A longer form split into named steps, each checked before the next, with a draft kept between visits.
- **Parameters:** `steps: list<record{title: text, fields: list<record>}>` (required; at most 8) · `initial: record? = none` · `draft: boolean = true`
- **Data:** none · **Slots:** none · **Events:** `submit {values: record}`
- **Use:** `{ use: core/stepped-form@1, with: { steps: [ { title: Party, fields: [ ... ] }, { title: Terms, fields: [ ... ] } ] } }`

#### `core/form-sections@1`
Origin: new · Needs: F2, F4 (dependent fields, conditional sections), F1 (host validation) · Wave: LB4 · Size: 2

A form in titled sections that appear by condition, with fields that depend on each other and the host's field errors shown in place.
- **Parameters:** `sections: list<record{id: text, title: text, when: fn(form: record) -> boolean?, fields: list<record>}>` (required) · `initial: record? = none` · `validate: enum[submit, change] = submit`
- **Data:** none · **Slots:** `actions` (actions, max 3) · **Events:** `submit {values: record}`, `change {field: text, value: any}`
- **Use:** `{ use: core/form-sections@1, with: { sections: [ { id: terms, title: Terms, fields: [ ... ] }, { id: fees, title: Fees, when: "f => f.product != 'FRA'", fields: [ ... ] } ] } }`

#### `core/line-items-form@1`
Origin: new · Needs: F3 (repeating sections) · Wave: LB4 · Size: 1

A header and a list of repeating lines (an invoice, the legs of a deal) with add, remove and a running total.
- **Parameters:** `header: list<record>` (required; fields) · `line: list<record>` (required; fields of one line) · `min: integer = 1` · `max: integer = 20` · `total-of: text? = none`
- **Data:** none · **Slots:** none · **Events:** `submit {header: record, lines: list<record>}`
- **Use:** `{ use: core/line-items-form@1, with: { header: [ ... ], line: [ { id: item, label: Item, type: text }, { id: amount, label: Amount, type: number } ], total-of: amount } }`

#### `core/edit-grid@1`
Origin: pattern (tables with cell editing) · Needs: I1, I7 (pending cells) · Wave: LB3 · Size: 1

A table whose marked columns can be edited in place; each change is an event the host accepts or rejects.
- **Parameters:** `rows: table{}` (required) · `key: text` (required) · `columns: columns` (required; `edit: true` marks an editable one)
- **Data:** `rows`; key required; `live: ok` · **Slots:** none · **Events:** `change {key: text, column: text, value: any, row: record}`
- **Use:** `{ use: core/edit-grid@1, with: { rows: $.targets, key: id, columns: [ { label: Region, bind: "@.region" }, { label: Target, bind: "@.target", fmt: amount0, edit: true } ] } }`

### 3.8 States and feedback

#### `core/alert-banner@1`
Origin: new · Needs: baseline · Wave: LB1 · Size: 0.5

A toned line across the screen, shown only while its condition holds.
- **Parameters:** `show: boolean` (required) · `text: text` (required) · `tone: tone = warn` · `detail: text? = none`
- **Data:** none · **Slots:** none · **Events:** none
- **Use:** `{ use: core/alert-banner@1, with: { show: "$.used > $.limit", text: "Over the limit by ${fmt($.used - $.limit, 'amount0')}", tone: bad } }`

#### `core/empty-state@1`
Origin: new · Needs: I1 · Wave: LB3 · Size: 0.5

What a screen says when there is nothing to show yet: a line, a reason, and one thing the reader can do.
- **Parameters:** `show: boolean` (required) · `heading: text` (required) · `reason: text? = none` · `action-label: text? = none`
- **Data:** none · **Slots:** none · **Events:** `action {}`
- **Use:** `{ use: core/empty-state@1, with: { show: "size($.orders) == 0", heading: "No orders yet", action-label: "Create one" } }`

#### `core/load-frame@1`
Origin: new · Needs: D4 (lazy panels) · Wave: LB3 · Size: 0.5

A frame that draws its title at once and loads the user's panels when they come into view.
- **Parameters:** `heading: text` (required) · `load: enum[visible, idle] = visible` · `min-height: integer = 4`
- **Data:** none · **Slots:** `body` (panels, max 6) · **Events:** none
- **Use:** `{ use: core/load-frame@1, with: { heading: History }, slots: { body: [ ... ] } }`

#### `core/freshness-badge@1`
Origin: pattern (as-of lines) · Needs: baseline · Wave: LB1 · Size: 0.5

How old the data is, toned when it is older than it should be.
- **Parameters:** `as-of: datetime` (required) · `stale-after: integer = 3600` (seconds) · `label: text = "As of"`
- **Data:** none · **Slots:** none · **Events:** none
- **Use:** `{ use: core/freshness-badge@1, with: { as-of: "$.loaded_at", stale-after: 900 } }`

### 3.9 History and comparison

#### `core/event-timeline@1`
Origin: kit (`event-timeline`) · Needs: baseline · Wave: LB1 · Size: 0.5

Dated events with a label, a detail line and a status.
- **Parameters:** `rows: table{date: date, label: text, detail?: text, status?: text}` (required) · `tone: tone = status` · `newest-first: boolean = true`
- **Data:** `rows` · **Slots:** none · **Events:** `select {row: record}`
- **Use:** `{ use: core/event-timeline@1, title: History, with: { rows: $.events } }`

#### `core/audit-trail@1`
Origin: new · Needs: baseline · Wave: LB1 · Size: 1

Who changed what and when: actor, time, field, the value before and after.
- **Parameters:** `rows: table{at: datetime, actor: text, action: text, field?: text, before?: any, after?: any}` (required) · `limit: integer = 100`
- **Data:** `rows`, newest first; `window: ok` · **Slots:** none · **Events:** `select {row: record}`
- **Use:** `{ use: core/audit-trail@1, title: Changes, with: { rows: $.audit } }`

#### `core/compare-ab@1`
Origin: new · Needs: baseline · Wave: LB1 · Size: 1

Two records side by side, field by field, with the differences toned and the numeric differences computed.
- **Parameters:** `a: record` (required) · `b: record` (required) · `a-label: text = "A"` · `b-label: text = "B"` · `fields: list<record{field: text, label: text, fmt: text?}>` (required) · `only-different: boolean = false`
- **Data:** none · **Slots:** none · **Events:** none
- **Use:** `{ use: core/compare-ab@1, with: { a: "$.today", b: "$.yesterday", a-label: Today, b-label: Yesterday, fields: [ { field: total, label: Total, fmt: amount0 } ] } }`

#### `core/version-diff@1`
Origin: new · Needs: baseline · Wave: LB1 · Size: 0.5

Rows added, removed and changed between two versions of a keyed table.
- **Parameters:** `before: table{}` (required) · `after: table{}` (required) · `key: text` (required) · `columns: columns` (required)
- **Data:** two tables of the same shape; `max-rows: 20000` · **Slots:** none · **Events:** `select {key: text}`
- **Use:** `{ use: core/version-diff@1, with: { before: $.limits_yesterday, after: $.limits_today, key: id, columns: [ { label: Limit, bind: "@.limit", fmt: amount0 } ] } }`

## 3A. Home fragments (role Homes)

Written for the role Homes of Drishti (hybrid: exceptions first and what changed above a KPI strip), generic in every word. Built at language level 1.2 from the panel kinds that exist (`metric`, `line`, `table`, `grid` with `bars`); nothing here needs a new kind. These entries replace the planned `core/kpi-strip` (needed `group` with `each`) and `core/spark-table` for the Home's needs: a strip is eight fixed figure slots shown by `when`.

| Fragment | Parameters (main) | Events | Notes |
|---|---|---|---|
| `core/kpi-strip@1` | `items: list<record{label, value, delta?, caption?}>` (1 to 8), `history?: table{label, date, value}`, `fmt`, `delta-fmt`, `tone` | none | a `line` panel per figure when `history` is given; a figure with no rows gets an empty line |
| `core/tile-grid@1` | `tiles: list<record{label, value, delta?, caption?}>` (1 to 9), `title`, `fmt`, `delta-fmt`, `tone` | none | metric tiles; no history |
| `core/exceptions-list@1` | `rows: table{severity, message, key?, open?}`, `title`, `empty` | `open {command}` | high, medium, low; the title carries the count |
| `core/change-feed@1` | `rows: table{key, change, measure?, before?, after?, why?, kind?, open?}`, `since?`, `title`, `fmt`, `change-fmt`, `limit`, `empty` | `open {command}` | largest absolute move first; `why` is data from the server's explain, never computed here |
| `core/use-bar-table@1` | `rows: table{name, value, limit?, change?}`, `warn-at`, `fmt`, `change-fmt`, `name-label`, `title` | `open {name}` | `grid` with `bars: [share]`; state Breach, Warning, Within |
| `core/calendar-list@1` | `rows: table{date, title, time?, kind?, open?}`, `limit`, `date-fmt` | `open {title, command}` | an agenda list, not a month grid (that is `core/calendar-heat`, V4) |
| `core/headline-rail@1` | `rows: table{date, headline, source?, link?}`, `limit`, `date-fmt` | `open {link}` | the compact `core/headline-list` |
| `finance/stress-tiles@1` | `rows: table{scenario, pnl, limit?}`, `fmt` | none | up to six, worst first, caption the share of limit |

Known limits: a `metric` panel raises no click event at 1.2, so the figure tiles are not clickable; a Home pairs them with a table or the quick-command list for the next step (the language addition is a `click` on `metric`, listed for the owner).

## 4. `finance`: trading, risk, market data

Generic terms only: a trade, a leg, a book, a desk, a counterparty, a netting set, a curve, a scenario. No vendor
system's field names and no product's. `finance` computes nothing a host must own: a VaR, an exposure profile or a
valuation arrives as data; a fragment shows it. Where a fragment derives a display figure (a share of a limit, a
count of exceptions) it says so. Later function libraries (`risk`, `statistics`, `timeseries`, X23 to X28) add
optional helpers; no baseline fragment needs them.

### 4.1 Market data

#### `finance/instrument-header@1`
Origin: pattern (the head of instrument and deal screens) · Needs: L1 · Wave: LB3 · Size: 1

The head of an instrument screen: name, identifiers, currency, last price with change, status.
- **Parameters:** `name: text` (required) · `identifiers: list<record{label: text, value: text}>? = none` · `currency: text? = none` · `last: number? = none` · `change: number? = none` · `price-fmt: format = price2` · `status: text? = none`
- **Data:** none · **Slots:** `actions` (actions, max 5) · **Events:** none
- **Use:** `{ use: finance/instrument-header@1, with: { name: "$.name", identifiers: "[{label: 'ISIN', value: $.isin}]", last: "$.last", change: "$.change" } }`

#### `finance/quote-card@1`
Origin: pack (quote ranges on market screens) · Needs: baseline · Wave: LB2 · Size: 1

A quote at a glance: last, change, the day's range and the 52-week range with the last price placed in each.
- **Parameters:** `last: number` (required) · `previous: number? = none` · `day-low: number? = none` · `day-high: number? = none` · `year-low: number? = none` · `year-high: number? = none` · `fmt: format = price2` · `as-of: datetime? = none`
- **Data:** none · **Slots:** none · **Events:** none
- **Use:** `{ use: finance/quote-card@1, title: "${$.symbol}", with: { last: "$.last", previous: "$.prev_close", day-low: "$.low", day-high: "$.high" } }`

#### `finance/price-bars@1`
Origin: pack (price bars) · Needs: baseline (kind `candlestick`) · Wave: LB2 · Size: 0.5

Open, high, low and close per period as candles, with volume under them.
- **Parameters:** `rows: table{date: date, open: number, high: number, low: number, close: number, volume?: number}` (required) · `fmt: format = price2`
- **Data:** contract `price-bars`; `live: ok` · **Slots:** none · **Events:** `bar {date: date}`
- **Use:** `{ use: finance/price-bars@1, title: Daily prices, with: { rows: $.bars } }`

#### `finance/price-history@1`
Origin: pattern (a closing-price line with facts beside it) · Needs: baseline · Wave: LB2 · Size: 1

A closing-price line with the period's high, low, return and volatility as figures beside it.
- **Parameters:** `rows: table{date: date, close: number}` (required) · `fmt: format = price2` · `periods-per-year: integer = 252`
- **Data:** contract `price-bars` (uses `date`, `close`) · **Slots:** none · **Events:** `point {date: date}`
- **Use:** `{ use: finance/price-history@1, with: { rows: $.bars } }`

#### `finance/curve-table@1`
Origin: kit (`curve-table`) · Needs: baseline · Wave: LB2 · Size: 0.5

A curve's pillars: tenor, instrument, quote, zero rate, discount factor.
- **Parameters:** `rows: table{tenor: text, zero: number, instrument?: text, quote?: number, df?: number}` (required) · `zero-fmt: format = pct4` · `df-fmt: format = df6`
- **Data:** contract `curve-points` · **Slots:** none · **Events:** `select {tenor: text}`
- **Use:** `{ use: finance/curve-table@1, title: Pillars, with: { rows: $.points } }`

#### `finance/curve-overview@1`
Origin: kit (`curve-overview`, a composite) · Needs: baseline · Wave: LB2 · Size: 0.5

A curve as a line and, under it, its pillars: `finance/term-structure@1` then `finance/curve-table@1` over the same rows.
- **Parameters:** `rows: table{tenor: text, zero: number, instrument?: text, quote?: number, df?: number}` (required) · `zero-fmt: format = pct4`
- **Data:** contract `curve-points` · **Slots:** none · **Events:** `select {tenor: text}`
- **Use:** `{ use: finance/curve-overview@1, with: { rows: $.points } }`

#### `finance/term-structure@1`
Origin: kit (`series-line` as used for curves) · Needs: baseline · Wave: LB2 · Size: 0.5

One term structure (zero rates, forwards, volatilities by expiry) drawn against tenor in tenor order.
- **Parameters:** `rows: table{tenor: text, value: number, years?: number}` (required) · `fmt: format = pct4` · `step: boolean = false` · `mark: text? = none` (a tenor to mark)
- **Data:** `rows` ordered by `years` when given, else as supplied · **Slots:** none · **Events:** `point {tenor: text, value: number}`
- **Use:** `{ use: finance/term-structure@1, title: 3M forwards, with: { rows: { from: $.points, columns: { value: forward } }, step: true } }`

#### `finance/curve-compare@1`
Origin: new · Needs: V1 · Wave: LB4 · Size: 1

Several curves on one tenor axis (today against yesterday, one currency against another) with the spread underneath.
- **Parameters:** `rows: table{curve: text, tenor: text, years: number, value: number}` (required) · `base: text? = none` (the curve spreads are taken against) · `fmt: format = pct4` · `spread-fmt: format = bp1`
- **Data:** `rows` in long form; at most 8 curves · **Slots:** none · **Events:** `point {curve: text, tenor: text}`
- **Use:** `{ use: finance/curve-compare@1, with: { rows: $.curves, base: "'Yesterday'" } }`

#### `finance/vol-surface@1`
Origin: pattern (a surface with its grid, on several market-data screens) · Needs: baseline (kind `surface`) · Wave: LB2 · Size: 1

A volatility surface by expiry and strike (or tenor), with the grid of numbers under it.
- **Parameters:** `rows: table{expiry: text, strike: any, vol: number}` (required) · `fmt: format = pct2` · `view: enum[heat, 3d] = heat` · `show-grid: boolean = true`
- **Data:** contract `vol-points` · **Slots:** none · **Events:** `cell {expiry: text, strike: any}`
- **Use:** `{ use: finance/vol-surface@1, title: Implied volatility, with: { rows: $.vols } }`

#### `finance/correlation-matrix@1`
Origin: pattern (a correlation screen) · Needs: baseline (kind `pivot` with heat) · Wave: LB2 · Size: 0.5

Correlations between named factors as a shaded square, with the strongest and weakest pairs called out.
- **Parameters:** `rows: table{a: text, b: text, value: number}` (required) · `fmt: format = signed2`
- **Data:** `rows`, one per ordered pair · **Slots:** none · **Events:** `cell {a: text, b: text}`
- **Use:** `{ use: finance/correlation-matrix@1, with: { rows: $.correlations } }`

#### `finance/fixing-ladder@1`
Origin: pattern (rate fixings as a ladder) · Needs: baseline (kind `ladder`) · Wave: LB2 · Size: 0.5

An index's fixings by date, newest first, with the day's change.
- **Parameters:** `rows: table{date: date, value: number}` (required) · `fmt: format = pct4` · `change-fmt: format = bp1`
- **Data:** `rows` · **Slots:** none · **Events:** none
- **Use:** `{ use: finance/fixing-ladder@1, title: Fixings, with: { rows: $.fixings } }`

#### `finance/fx-scenario-chart@1`
Origin: pattern (an FX scenario screen: a line and its table) · Needs: baseline · Wave: LB2 · Size: 1

The path of an exchange rate over scenario dates with the shock per scenario, as a line and a table.
- **Parameters:** `rows: table{date: date, rate: number, shock?: number}` (required) · `pair: text` (required) · `fmt: format = price4` · `shock-fmt: format = pc2`
- **Data:** contract `factor-scenarios` · **Slots:** none · **Events:** `point {date: date}`
- **Use:** `{ use: finance/fx-scenario-chart@1, with: { rows: $.scenarios, pair: "$.pair" } }`

### 4.2 Trading

#### `finance/trade-ticket@1`
Origin: pack (the deal ticket on every trade screen) · Needs: baseline · Wave: LB2 · Size: 1

The economic summary of one trade, read-only: product, direction, parties, dates, notional, price, status.
- **Parameters:** `trade: record{id: text, product: text, direction: text?, counterparty: text?, book: text?, trade-date: date?, maturity: date?, notional: number?, currency: text?, price: number?, status: text?}` (required) · `notional-fmt: format = amount0` · `price-fmt: format = price4`
- **Data:** none (a record) · **Slots:** `extra` (panels, max 2) · **Events:** none
- **Use:** `{ use: finance/trade-ticket@1, with: { trade: "{id: $.id, product: $.product, notional: $.notional, currency: $.ccy, status: $.status}" } }`

#### `finance/leg-table@1`
Origin: pack (the legs of a trade) · Needs: baseline · Wave: LB2 · Size: 0.5

A trade's legs side by side: pay or receive, notional, currency, rate or index, spread, day count, frequency.
- **Parameters:** `rows: table{leg: text, direction: text, notional: number, currency: text, rate?: number, index?: text, spread?: number, day-count?: text, frequency?: text}` (required) · `rate-fmt: format = pct4`
- **Data:** contract `trade-legs` · **Slots:** none · **Events:** none
- **Use:** `{ use: finance/leg-table@1, title: Legs, with: { rows: $.legs } }`

#### `finance/payment-schedule@1`
Origin: pack (payments and flow schedules of a trade) · Needs: baseline · Wave: LB2 · Size: 0.5

A trade's scheduled payments: period, payment date, rate, amount, currency, paid or due.
- **Parameters:** `rows: table{pay-date: date, amount: number, currency: text, start?: date, end?: date, rate?: number, status?: text}` (required) · `fmt: format = amount2`
- **Data:** contract `cashflows` · **Slots:** none · **Events:** none
- **Use:** `{ use: finance/payment-schedule@1, title: Payments, with: { rows: $.payments } }`

#### `finance/cashflow-ladder@1`
Origin: kit (`cashflow-ladder`) · Needs: baseline (kind `ladder`) · Wave: LB2 · Size: 0.5

Cash flows by date as a ladder: date, type, signed amount, currency, with the running net.
- **Parameters:** `rows: table{date: date, amount: number, currency: text, type?: text}` (required) · `fmt: format = signed0`
- **Data:** contract `cashflows` · **Slots:** none · **Events:** none
- **Use:** `{ use: finance/cashflow-ladder@1, with: { rows: $.flows } }`

#### `finance/settlement-figures@1`
Origin: pack (payment metrics on trade lists) · Needs: baseline · Wave: LB2 · Size: 0.5

Three figures over a set of payments: due today, due this week, overdue.
- **Parameters:** `rows: table{pay-date: date, amount: number, status?: text}` (required) · `today: date` (required) · `fmt: format = amount0`
- **Data:** contract `cashflows` · **Slots:** none · **Events:** `pick {bucket: text}`
- **Use:** `{ use: finance/settlement-figures@1, with: { rows: $.payments, today: "asof()" } }`

#### `finance/lifecycle-events@1`
Origin: pack (the events of a trade) · Needs: baseline · Wave: LB2 · Size: 0.5

A trade's life as a timeline: booked, amended, fixed, paid, matured; a variant of `core/event-timeline@1`.
- **Parameters:** `rows: table{date: date, event: text, detail?: text, status?: text}` (required)
- **Data:** `rows` · **Slots:** none · **Events:** `select {row: record}`
- **Use:** `{ use: finance/lifecycle-events@1, with: { rows: $.events } }`

#### `finance/trade-blotter@1`
Origin: pack (a trade table with its columns as a parameter) · Needs: baseline · Wave: LB2 · Size: 1

A list of trades with standard leading columns and the user's own after them; a click opens the trade.
- **Parameters:** `rows: table{id: text, product: text, counterparty?: text, book?: text, notional?: number, currency?: text, maturity?: date, status?: text}` (required) · `columns: columns = []` (extra columns) · `limit: integer = 200`
- **Data:** contract `trades`; key `id`; `live: ok` · **Slots:** none · **Events:** `open {id: text, row: record}`
- **Use:** `{ use: finance/trade-blotter@1, title: Trades, with: { rows: $.trades }, on: { open: [ { go: { sutra: trade, args: { id: "event.id" } } } ] } }`

#### `finance/live-blotter@1`
Origin: new · Needs: D2, V3, V2, I3 (multi-select and bulk), I5 (row menu), D6 (flash) · Wave: LB4 · Size: 2

A trading blotter over a large live table: grouped, pinned key column, changes flash, rows are ticked for bulk actions.
- **Parameters:** `rows: table{id: text, status: text}` (required; windowed) · `columns: columns` (required) · `group-by: list<text> = []` · `window: integer = 200` · `feeds: selection? = none`
- **Data:** `rows` with key `id`, `live: ok`, `window: ok` · **Slots:** `menu` (menu, max 8), `bulk` (actions, max 4) · **Events:** `open {id: text, row: record}` · **Outputs:** `chosen: list<text>`
- **Use:** `{ use: finance/live-blotter@1, as: bl, with: { rows: $.orders, group-by: [status], columns: [ { label: Qty, bind: "@.qty", fmt: amount0 } ] }, slots: { bulk: [ { label: "Cancel ${size(state.bl.chosen)}", do: [ { host: order.cancel-many, with: { ids: "state.bl.chosen" } } ] } ] } }`

#### `finance/order-ticket@1`
Origin: new · Needs: S1, I1, F1, F2 · Wave: LB3 · Size: 2

An order entry ticket: side, quantity, order type, limit price, time in force; checks before send; one event when sent. It sends nothing itself.
- **Parameters:** `instrument: text` (required) · `sides: list<text> = [BUY, SELL]` · `types: list<text> = [MARKET, LIMIT]` · `side: text? = none` · `last: number? = none` · `max-qty: number? = none` · `price-fmt: format = price4`
- **Data:** none · **Slots:** `footer` (panels, max 2), `fields` (fields, max 6) · **Events:** `submit {order: record{instrument: text, side: text, qty: number, type: text, limit: number?, tif: text}}`, `cancel {}`
- **Use:** `{ use: finance/order-ticket@1, as: tk, with: { instrument: "state.instrument", last: "$.quote.last" }, on: { submit: [ { host: order.place, with: { order: "event.order" } } ] } }`

#### `finance/depth-ladder@1`
Origin: new · Needs: V2 (bars in cells) · Wave: LB3 · Size: 1

An order book as a price ladder: bid sizes on one side, ask sizes on the other, bars for depth, the spread in the middle.
- **Parameters:** `rows: table{price: number, bid-size?: number, ask-size?: number}` (required) · `levels: integer = 10` · `price-fmt: format = price4` · `size-fmt: format = amount0`
- **Data:** `rows`; key `price`; `live: ok` · **Slots:** none · **Events:** `price {price: number, side: text}`
- **Use:** `{ use: finance/depth-ladder@1, title: Depth, with: { rows: $.book } }`

#### `finance/fills-summary@1`
Origin: new · Needs: baseline · Wave: LB2 · Size: 1

The executions of an order: filled against ordered, average price, and the fills as a table.
- **Parameters:** `rows: table{time: datetime, qty: number, price: number, venue?: text}` (required) · `ordered: number` (required) · `reference: number? = none` (a price to measure slippage against) · `price-fmt: format = price4`
- **Data:** `rows`; `live: ok` · **Slots:** none · **Events:** none
- **Use:** `{ use: finance/fills-summary@1, with: { rows: $.fills, ordered: "$.order.qty", reference: "$.order.arrival_price" } }`

#### `finance/position-table@1`
Origin: pattern (position screens: a table with facts beside it) · Needs: baseline · Wave: LB2 · Size: 0.5

Positions by instrument: quantity, price, market value, day P&L, share of the total.
- **Parameters:** `rows: table{instrument: text, quantity: number, price?: number, value: number, pnl?: number}` (required) · `value-fmt: format = amount0` · `pnl-fmt: format = signed0`
- **Data:** contract `positions`; key `instrument`; `live: ok` · **Slots:** none · **Events:** `open {instrument: text, row: record}`
- **Use:** `{ use: finance/position-table@1, title: Positions, with: { rows: $.positions } }`

### 4.3 P&L

#### `finance/pnl-tiles@1`
Origin: pattern (P&L figures at the head of desk screens) · Needs: baseline · Wave: LB2 · Size: 0.5

Day, month-to-date and year-to-date P&L as three figures toned by sign.
- **Parameters:** `day: number` (required) · `mtd: number? = none` · `ytd: number? = none` · `fmt: format = signed0` · `currency: text? = none`
- **Data:** none · **Slots:** none · **Events:** none
- **Use:** `{ use: finance/pnl-tiles@1, with: { day: "$.pnl.day", mtd: "$.pnl.mtd", ytd: "$.pnl.ytd" } }`

#### `finance/pnl-waterfall@1`
Origin: pattern (a P&L explain screen) · Needs: baseline (kind `waterfall`) · Wave: LB2 · Size: 0.5

P&L explained: from the opening value through each effect (rates, spreads, FX, carry, new trades, unexplained) to the close.
- **Parameters:** `rows: table{effect: text, amount: number, total?: boolean}` (required) · `fmt: format = signed0` · `unexplained-warn: number? = none` (tones the unexplained step above this share)
- **Data:** contract `pnl-explain` · **Slots:** none · **Events:** `step {effect: text}`
- **Use:** `{ use: finance/pnl-waterfall@1, title: P&L explain, with: { rows: $.explain } }`

#### `finance/pnl-attribution-bars@1`
Origin: pattern (signed bars by book or desk) · Needs: baseline · Wave: LB2 · Size: 0.5

P&L by a grouping (book, desk, strategy) as bars toned by sign, losses and gains on either side of zero.
- **Parameters:** `rows: table{label: text, pnl: number}` (required) · `fmt: format = signed0` · `top: integer? = none` · `feeds: selection? = none`
- **Data:** `rows` · **Slots:** none · **Events:** `bar {label: text}`
- **Use:** `{ use: finance/pnl-attribution-bars@1, title: P&L by desk, with: { rows: "group($.positions, 'desk', 'pnl')" } }`

#### `finance/pnl-history@1`
Origin: new · Needs: baseline · Wave: LB2 · Size: 0.5

Daily P&L as a line of the cumulative total, with the best and worst day as figures.
- **Parameters:** `rows: table{date: date, pnl: number}` (required) · `fmt: format = signed0` · `cumulative: boolean = true`
- **Data:** contract `daily-pnl` · **Slots:** none · **Events:** `point {date: date}`
- **Use:** `{ use: finance/pnl-history@1, with: { rows: $.daily } }`

### 4.4 Market risk

#### `finance/var-tiles@1`
Origin: pattern (VaR figures on risk screens) · Needs: baseline · Wave: LB2 · Size: 0.5

Value-at-risk and expected shortfall as tiles, each with its confidence and horizon, the change on the day and, when a limit is given, the share used.
- **Parameters:** `var: number` (required) · `es: number? = none` · `var-label: text = "VaR 99%"` · `es-label: text = "ES 97.5%"` · `previous-var: number? = none` · `limit: number? = none` · `horizon: text = "1d"` · `fmt: format = amount0`
- **Data:** none · **Slots:** none · **Events:** none
- **Use:** `{ use: finance/var-tiles@1, with: { var: "$.risk.var", es: "$.risk.es", limit: "$.limit.var" } }`

#### `finance/var-histogram@1`
Origin: kit (`pnl-distribution`) · Needs: baseline (kind `histogram`) · Wave: LB2 · Size: 0.5

The distribution of scenario P&L with the VaR and the expected shortfall marked on the loss side.
- **Parameters:** `rows: table{pnl: number}` (required) · `var: number` (required) · `es: number? = none` · `bins: integer = 40` · `fmt: format = compact` · `unit: text? = none`
- **Data:** contract `scenario-pnl` (uses `pnl`); `max-rows: 100000` · **Slots:** none · **Events:** none
- **Use:** `{ use: finance/var-histogram@1, with: { rows: $.scenario_pnl, var: "$.risk.var", es: "$.risk.es" } }`

#### `finance/var-path@1`
Origin: pattern (scenario P&L drawn against scenario date) · Needs: baseline · Wave: LB2 · Size: 1

Scenario P&L against the scenario's date, with the worst days marked and listed: when in history the losses come from.
- **Parameters:** `rows: table{date: date, pnl: number}` (required) · `var: number? = none` · `worst: integer = 5` · `fmt: format = signed0`
- **Data:** contract `scenario-pnl` · **Slots:** none · **Events:** `scenario {date: date, pnl: number}`
- **Use:** `{ use: finance/var-path@1, with: { rows: $.scenario_pnl, var: "$.risk.var" } }`

#### `finance/var-contributors@1`
Origin: pattern (contributor tables on risk screens) · Needs: baseline · Wave: LB2 · Size: 0.5

What makes up a VaR: each position or book with its stand-alone, component and incremental figure, largest first.
- **Parameters:** `rows: table{name: text, component: number, standalone?: number, incremental?: number}` (required) · `total: number? = none` · `top: integer = 20` · `fmt: format = amount0`
- **Data:** contract `risk-contributions` · **Slots:** none · **Events:** `open {name: text, row: record}`
- **Use:** `{ use: finance/var-contributors@1, with: { rows: $.contributions, total: "$.risk.var" } }`

#### `finance/backtest-traffic-light@1`
Origin: new · Needs: baseline · Wave: LB2 · Size: 1

A backtest's verdict: the count of exceptions over the window and the zone it falls in (green, amber, red), with the thresholds shown.
- **Parameters:** `rows: table{date: date, pnl: number, var: number}` (required) · `amber-from: integer = 5` · `red-from: integer = 10` · `window: integer = 250`
- **Data:** contract `backtest` (an exception is a day whose loss exceeds its VaR; counted here, stated in the note) · **Slots:** none · **Events:** `exception {date: date}`
- **Use:** `{ use: finance/backtest-traffic-light@1, with: { rows: $.backtest } }`

#### `finance/backtest-chart@1`
Origin: new · Needs: V1 (bars, a line, marked points) · Wave: LB4 · Size: 1

Daily P&L as bars against the VaR as a line on the loss side, exceptions marked.
- **Parameters:** `rows: table{date: date, pnl: number, var: number}` (required) · `fmt: format = signed0`
- **Data:** contract `backtest` · **Slots:** `layers` (layers, max 2) · **Events:** `point {date: date}`
- **Use:** `{ use: finance/backtest-chart@1, title: P&L against VaR, with: { rows: $.backtest } }`

#### `finance/risk-hierarchy-tree@1`
Origin: pattern (risk rolled up a hierarchy of books and desks) · Needs: baseline (table with `root` and `expand`) · Wave: LB2 · Size: 1

A risk measure down a hierarchy (firm, division, desk, book): each node with its figure, its limit and the share used.
- **Parameters:** `rows: table{id: text, parent: text, name: text, value: number, limit?: number}` (required) · `value-label: text = "VaR"` · `fmt: format = amount0` · `open-levels: integer = 2`
- **Data:** contract `risk-hierarchy` (figures per node supplied; a risk measure is not summed here) · **Slots:** none · **Events:** `select {id: text, row: record}`
- **Use:** `{ use: finance/risk-hierarchy-tree@1, as: tree, with: { rows: $.hierarchy } }`

#### `finance/scenario-matrix@1`
Origin: pattern (scenario sets shown as tables) · Needs: baseline (kind `pivot`) · Wave: LB2 · Size: 0.5

Scenarios down the side, books or factors across, P&L in the cells, shaded by size, the worst row first.
- **Parameters:** `rows: table{scenario: text, group: text, pnl: number}` (required) · `fmt: format = signed0` · `heat: boolean = true`
- **Data:** contract `stress-results` · **Slots:** none · **Events:** `cell {scenario: text, group: text}`
- **Use:** `{ use: finance/scenario-matrix@1, title: Stress by desk, with: { rows: { from: $.stress, columns: { group: desk } } } }`

#### `finance/stress-table@1`
Origin: pattern (stress result screens) · Needs: baseline · Wave: LB2 · Size: 0.5

Stress scenarios with their P&L, toned by sign, the worst highlighted, each with its description.
- **Parameters:** `rows: table{scenario: text, pnl: number, description?: text}` (required) · `fmt: format = signed0`
- **Data:** contract `stress-results` · **Slots:** none · **Events:** `open {scenario: text}`
- **Use:** `{ use: finance/stress-table@1, with: { rows: $.stress_totals } }`

#### `finance/greeks-ladder@1`
Origin: pattern (sensitivity tables by tenor) · Needs: baseline · Wave: LB2 · Size: 0.5

Sensitivities by bucket (tenor or strike): one row a bucket, a column per greek, totals at the foot.
- **Parameters:** `rows: table{bucket: text, delta?: number, gamma?: number, vega?: number, theta?: number, rho?: number}` (required) · `fmt: format = amount0`
- **Data:** contract `sensitivities` · **Slots:** none · **Events:** `select {bucket: text}`
- **Use:** `{ use: finance/greeks-ladder@1, title: Greeks by tenor, with: { rows: $.greeks } }`

#### `finance/sensitivity-heatmap@1`
Origin: pattern (sensitivities by factor and tenor) · Needs: baseline (kind `pivot` with heat) · Wave: LB2 · Size: 0.5

One sensitivity across two axes (risk factor by tenor, currency by tenor) as a shaded grid with totals.
- **Parameters:** `rows: table{factor: text, bucket: text, value: number}` (required) · `fmt: format = signed0`
- **Data:** contract `sensitivities` in long form · **Slots:** none · **Events:** `cell {factor: text, bucket: text}`
- **Use:** `{ use: finance/sensitivity-heatmap@1, title: DV01, with: { rows: $.dv01 } }`

#### `finance/concentration-treemap@1`
Origin: new · Needs: V4 (kind `treemap`) · Wave: LB4 · Size: 0.5

Where the risk or the exposure sits: nested by two levels, sized by exposure, toned by P&L.
- **Parameters:** `rows: table{group: text, name: text, exposure: number, pnl?: number}` (required) · `fmt: format = amount0`
- **Data:** `rows` · **Slots:** none · **Events:** `cell {group: text, name: text}`
- **Use:** `{ use: finance/concentration-treemap@1, with: { rows: { from: $.positions, columns: { group: sector, name: issuer, exposure: value } } } }`

#### `finance/what-if-shocks@1`
Origin: new · Needs: X37 (what-if), S1 · Wave: LB4 · Size: 2

Sliders for a few market shocks and the linear P&L estimate they give from supplied sensitivities; an estimate, labelled as one.
- **Parameters:** `sensitivities: table{factor: text, value: number}` (required) · `factors: list<record{factor: text, label: text, min: number, max: number, step: number}>` (required; at most 6) · `fmt: format = signed0`
- **Data:** contract `sensitivities` · **Slots:** none · **Events:** `change {shocks: record}` · **Outputs:** `estimate: number`
- **Use:** `{ use: finance/what-if-shocks@1, with: { sensitivities: $.delta_by_factor, factors: [ { factor: rates, label: "Rates, bp", min: -100, max: 100, step: 5 } ] } }`

### 4.5 Limits

#### `finance/limit-gauge@1`
Origin: new · Needs: baseline (kind `gauge`) · Wave: LB2 · Size: 0.5

One limit: the amount used against it, with warning and breach bands and, when given, the recent history.
- **Parameters:** `used: number` (required) · `limit: number` (required) · `warn-at: number = 0.8` · `label: text = "Limit used"` · `fmt: format = amount0` · `history: table{date: date, used: number}? = none`
- **Data:** optional `history` · **Slots:** `footer` (panels, max 2) · **Events:** `breach-click {used: number, limit: number}`
- **Use:** `{ use: finance/limit-gauge@1, title: VaR limit, with: { used: "$.risk.var", limit: "$.limit.var" } }`

#### `finance/limit-table@1`
Origin: pattern (limit screens: a table with utilisation) · Needs: baseline (V2 adds the bar in the cell) · Wave: LB2 · Size: 0.5

Many limits at once: name, limit, used, headroom and the share used, toned at the warning and breach levels, breaches first.
- **Parameters:** `rows: table{name: text, limit: number, used: number, owner?: text}` (required) · `warn-at: number = 0.8` · `fmt: format = amount0`
- **Data:** contract `limit-utilisation`; `live: ok` · **Slots:** none · **Events:** `open {name: text, row: record}`
- **Use:** `{ use: finance/limit-table@1, title: Limits, with: { rows: $.limits } }`

#### `finance/limit-band@1`
Origin: new · Needs: V4 (kind `bullet`) · Wave: LB4 · Size: 0.5

A two-sided limit (a position that may be long or short): the value on a band with both bounds and the warning zones.
- **Parameters:** `value: number` (required) · `lower: number` (required) · `upper: number` (required) · `warn-at: number = 0.8` · `fmt: format = signed0`
- **Data:** none · **Slots:** none · **Events:** none
- **Use:** `{ use: finance/limit-band@1, title: Net delta, with: { value: "$.delta", lower: "-$.limit", upper: "$.limit" } }`

### 4.6 Counterparty and collateral

#### `finance/counterparty-card@1`
Origin: pattern (counterparty facts on credit screens) · Needs: baseline · Wave: LB2 · Size: 0.5

A counterparty at a glance: name, rating, country, sector, current exposure, limit and the share used.
- **Parameters:** `counterparty: record{name: text, rating: text?, country: text?, sector: text?, exposure: number?, limit: number?}` (required) · `fmt: format = amount0`
- **Data:** none (a record) · **Slots:** `footer` (panels, max 2) · **Events:** none
- **Use:** `{ use: finance/counterparty-card@1, with: { counterparty: "slot.row" } }`

#### `finance/exposure-profile@1`
Origin: pattern (exposure over time as areas with a limit) · Needs: baseline (kind `area`) · Wave: LB2 · Size: 0.5

Exposure through time: expected and potential future exposure by date, against the limit, with the peak called out.
- **Parameters:** `rows: table{date: date, expected: number, potential?: number}` (required) · `limit: number? = none` · `fmt: format = amount0`
- **Data:** contract `exposure-profile` · **Slots:** none · **Events:** `point {date: date}`
- **Use:** `{ use: finance/exposure-profile@1, with: { rows: $.profile, limit: "$.limit.pfe" } }`

#### `finance/netting-set-tree@1`
Origin: pattern (netting-set screens) · Needs: baseline · Wave: LB2 · Size: 1

Group, counterparty, netting set and trade as a tree, with gross value, net value and collateral at each node.
- **Parameters:** `rows: table{id: text, parent: text, name: text, level: text, gross?: number, net?: number, collateral?: number}` (required) · `fmt: format = amount0` · `open-levels: integer = 2`
- **Data:** contract `netting-hierarchy` (figures per node supplied) · **Slots:** none · **Events:** `select {id: text, level: text, row: record}`
- **Use:** `{ use: finance/netting-set-tree@1, with: { rows: $.netting } }`

#### `finance/netting-summary@1`
Origin: pattern (the figures and bars at the head of a netting-set screen) · Needs: baseline · Wave: LB2 · Size: 1

A netting set in four figures (gross positive, gross negative, net, after collateral) with the largest trades as bars.
- **Parameters:** `gross-positive: number` (required) · `gross-negative: number` (required) · `net: number` (required) · `collateral: number? = none` · `trades: table{id: text, value: number}? = none` · `fmt: format = amount0`
- **Data:** optional `trades` · **Slots:** none · **Events:** `bar {id: text}`
- **Use:** `{ use: finance/netting-summary@1, with: { gross-positive: "$.gross_pos", gross-negative: "$.gross_neg", net: "$.net", trades: $.trade_values } }`

#### `finance/collateral-ledger@1`
Origin: pattern (collateral balance screens) · Needs: baseline · Wave: LB2 · Size: 0.5

Collateral held and posted: asset, direction, nominal, haircut, value after haircut, totals by direction.
- **Parameters:** `rows: table{asset: text, direction: text, nominal: number, currency: text, haircut?: number, value: number}` (required) · `fmt: format = amount0`
- **Data:** contract `collateral-holdings` · **Slots:** none · **Events:** `open {asset: text}`
- **Use:** `{ use: finance/collateral-ledger@1, title: Collateral, with: { rows: $.collateral } }`

#### `finance/margin-call-ladder@1`
Origin: pattern (margin calls as a ladder) · Needs: baseline (kind `ladder`) · Wave: LB2 · Size: 0.5

Margin calls by date: direction, amount, status (issued, agreed, disputed, settled), with the open total.
- **Parameters:** `rows: table{date: date, direction: text, amount: number, currency: text, status: text}` (required) · `fmt: format = amount0`
- **Data:** contract `margin-calls`; `live: ok` · **Slots:** none · **Events:** `open {row: record}`
- **Use:** `{ use: finance/margin-call-ladder@1, with: { rows: $.calls } }`

#### `finance/agreement-terms@1`
Origin: pattern (agreement and collateral-terms screens) · Needs: baseline · Wave: LB2 · Size: 0.5

The terms of a collateral agreement: thresholds, minimum transfer amounts, independent amounts, eligible currencies, frequency.
- **Parameters:** `terms: record{type: text?, threshold-us: number?, threshold-them: number?, mta-us: number?, mta-them: number?, independent-amount: number?, currency: text?, frequency: text?}` (required) · `fmt: format = amount0`
- **Data:** none (a record) · **Slots:** none · **Events:** none
- **Use:** `{ use: finance/agreement-terms@1, with: { terms: "$.agreement" } }`

#### `finance/addon-breakdown@1`
Origin: pattern (capital exposure screens: a table and its totals) · Needs: baseline · Wave: LB2 · Size: 0.5

A regulatory exposure built up: replacement cost, add-on by asset class, the multiplier, the exposure at default.
- **Parameters:** `rows: table{asset-class: text, addon: number}` (required) · `replacement-cost: number` (required) · `multiplier: number? = none` · `exposure: number` (required) · `fmt: format = amount0`
- **Data:** `rows` (every figure supplied; nothing is computed) · **Slots:** none · **Events:** none
- **Use:** `{ use: finance/addon-breakdown@1, with: { rows: $.addons, replacement-cost: "$.rc", exposure: "$.ead" } }`

#### `finance/valuation-adjustments@1`
Origin: pattern (valuation adjustment tables) · Needs: baseline · Wave: LB2 · Size: 0.5

Valuation adjustments per counterparty or netting set (credit, debit, funding), with totals and the change on the day.
- **Parameters:** `rows: table{name: text, credit?: number, debit?: number, funding?: number, change?: number}` (required) · `fmt: format = signed0`
- **Data:** `rows` · **Slots:** none · **Events:** `open {name: text}`
- **Use:** `{ use: finance/valuation-adjustments@1, with: { rows: $.adjustments } }`

### 4.7 Funds and equities

#### `finance/holdings-table@1`
Origin: pack (holder and holdings lists) · Needs: baseline · Wave: LB2 · Size: 0.5

Holdings of a portfolio, or holders of a security: name, quantity, value, weight, change since the last report.
- **Parameters:** `rows: table{name: text, value: number, quantity?: number, weight?: number, change?: number}` (required) · `fmt: format = amount0` · `top: integer = 50`
- **Data:** contract `holdings` · **Slots:** none · **Events:** `open {name: text}`
- **Use:** `{ use: finance/holdings-table@1, title: Top holdings, with: { rows: $.holdings } }`

#### `finance/financials-table@1`
Origin: pack (annual financials of a company) · Needs: baseline (kind `pivot`) · Wave: LB2 · Size: 0.5

Reported figures with periods across and line items down, growth toned by sign.
- **Parameters:** `rows: table{item: text, period: text, value: number}` (required) · `fmt: format = compact` · `periods: integer = 5`
- **Data:** `rows` in long form · **Slots:** none · **Events:** `cell {item: text, period: text}`
- **Use:** `{ use: finance/financials-table@1, title: Income statement, with: { rows: $.income } }`

#### `finance/allocation-donut@1`
Origin: pattern (allocation donuts) · Needs: baseline · Wave: LB2 · Size: 0.5

A portfolio's split by asset class, sector or currency, with the weights; a variant of `core/donut-breakdown@1`.
- **Parameters:** `rows: table{label: text, value: number}` (required) · `fmt: format = pct1` · `as-weights: boolean = true`
- **Data:** `rows` · **Slots:** none · **Events:** `slice {label: text}`
- **Use:** `{ use: finance/allocation-donut@1, with: { rows: "group($.positions, 'asset_class', 'value')" } }`

#### `finance/performance-chart@1`
Origin: new · Needs: V1, X26 (returns and drawdown) · Wave: LB4 · Size: 1

A portfolio against its benchmark, rebased to 100, with the drawdown underneath and the period's return, volatility and maximum drawdown as figures.
- **Parameters:** `rows: table{date: date, value: number, benchmark?: number}` (required) · `fmt: format = amount2` · `periods-per-year: integer = 252`
- **Data:** `rows` in date order · **Slots:** none · **Events:** `point {date: date}`
- **Use:** `{ use: finance/performance-chart@1, with: { rows: $.nav } }`

## 5. `genomics`: variants, cohorts, proteins

Generic terms of the field (gene, variant, sample, cohort, residue, design round). Coordinates are a chromosome
name and a 1-based position; the reference build is the host's and is shown, never assumed. Four fragments need
panel kinds that neither the language nor the expansion has yet (section 10, K2 to K5); they are listed so that
the kinds are designed for them.

### 5.1 Cards

#### `genomics/gene-card@1`
Origin: pattern (gene screens: facts as a list) · Needs: baseline · Wave: LB2 · Size: 0.5

A gene at a glance: symbol, name, location, strand, biotype, the number of known variants.
- **Parameters:** `gene: record{symbol: text, name: text?, chromosome: text?, start: integer?, end: integer?, strand: text?, biotype: text?, variants: integer?}` (required) · `build: text? = none`
- **Data:** none (a record) · **Slots:** `footer` (panels, max 2) · **Events:** none
- **Use:** `{ use: genomics/gene-card@1, with: { gene: "$.gene", build: "$.study.build" } }`

#### `genomics/variant-card@1`
Origin: pattern (variant screens) · Needs: baseline · Wave: LB2 · Size: 0.5

One variant: position, reference and alternate alleles, gene, consequence, impact, frequency, clinical significance.
- **Parameters:** `variant: record{id: text, chromosome: text, position: integer, ref: text, alt: text, gene: text?, consequence: text?, impact: text?, frequency: number?, significance: text?}` (required) · `frequency-fmt: format = pct4`
- **Data:** none (a record) · **Slots:** none · **Events:** none
- **Use:** `{ use: genomics/variant-card@1, with: { variant: "slot.row" } }`

#### `genomics/sample-card@1`
Origin: pattern (sample screens) · Needs: baseline · Wave: LB2 · Size: 0.5

One sample: identifier, cohort, tissue, the sequencing method and its quality figures.
- **Parameters:** `sample: record{id: text, cohort: text?, tissue: text?, method: text?, coverage: number?, call-rate: number?}` (required)
- **Data:** none (a record) · **Slots:** none · **Events:** none
- **Use:** `{ use: genomics/sample-card@1, with: { sample: "$.sample" } }`

#### `genomics/qc-strip@1`
Origin: new · Needs: baseline · Wave: LB2 · Size: 0.5

A run's quality figures as tiles, each toned against its threshold: mean coverage, call rate, duplication, contamination.
- **Parameters:** `metrics: list<record{label: text, value: number, min: number?, max: number?, fmt: text?}>` (required; at most 8)
- **Data:** none · **Slots:** none · **Events:** none
- **Use:** `{ use: genomics/qc-strip@1, with: { metrics: "[{label: 'Coverage', value: $.qc.coverage, min: 30}, {label: 'Call rate', value: $.qc.call_rate, min: 0.98, fmt: 'pct2'}]" } }`

### 5.2 Variants

#### `genomics/variant-table@1`
Origin: pattern (variant lists) · Needs: baseline · Wave: LB2 · Size: 1

Variants as a table: position, change, gene, consequence, impact (toned), frequency, genotype; a click opens one.
- **Parameters:** `rows: table{id: text, chromosome: text, position: integer, ref: text, alt: text, gene?: text, consequence?: text, impact?: text, frequency?: number, genotype?: text}` (required) · `limit: integer = 200`
- **Data:** contract `variants`; key `id`; `window: ok` · **Slots:** none · **Events:** `open {id: text, row: record}`
- **Use:** `{ use: genomics/variant-table@1, title: Variants, with: { rows: $.variants } }`

#### `genomics/variant-lollipop@1`
Origin: new · Needs: V1 (rules, points and bands on one axis) · Wave: LB4 · Size: 1

Variants along a protein or gene: a stem at each position, its height the count, its colour the consequence, the domains as bands beneath.
- **Parameters:** `rows: table{position: integer, count: number, consequence?: text, label?: text}` (required) · `length: integer` (required) · `domains: table{name: text, start: integer, end: integer}? = none` · `palette: palette = categorical-1`
- **Data:** contract `variant-positions`; optional `domains` · **Slots:** none · **Events:** `variant {position: integer, row: record}`
- **Use:** `{ use: genomics/variant-lollipop@1, with: { rows: $.by_position, length: "$.protein.length", domains: $.domains } }`

#### `genomics/consequence-bars@1`
Origin: new · Needs: baseline · Wave: LB2 · Size: 0.5

Counts of variants by consequence or impact, as ranked bars that can feed a selection.
- **Parameters:** `rows: table{label: text, value: number}` (required) · `feeds: selection? = none`
- **Data:** `rows` · **Slots:** none · **Events:** `bar {label: text}`
- **Use:** `{ use: genomics/consequence-bars@1, with: { rows: "group($.variants, 'consequence')" } }`

#### `genomics/frequency-bars@1`
Origin: new · Needs: baseline · Wave: LB2 · Size: 0.5

An allele's frequency by population, with the overall frequency marked.
- **Parameters:** `rows: table{population: text, frequency: number, count?: integer}` (required) · `overall: number? = none` · `fmt: format = pct4`
- **Data:** `rows` · **Slots:** none · **Events:** none
- **Use:** `{ use: genomics/frequency-bars@1, title: Frequency by population, with: { rows: $.frequencies } }`

### 5.3 Association

#### `genomics/manhattan-plot@1`
Origin: new · Needs: V1 (points with rule lines), K1 (a point mark budget of 200,000) · Wave: LB4 · Size: 2

Association results across the genome: position along the bottom by chromosome, minus log ten of the p-value up the side, the significance lines drawn, the top hits labelled.
- **Parameters:** `rows: table{chromosome: text, position: integer, p: number, label?: text}` (required) · `significant: number = 5e-8` · `suggestive: number? = 1e-5` · `label-top: integer = 10`
- **Data:** contract `association-results`; `max-rows: 200000` (a host thins the non-significant points above that) · **Slots:** none · **Events:** `hit {chromosome: text, position: integer, row: record}`
- **Use:** `{ use: genomics/manhattan-plot@1, title: Association, with: { rows: $.results } }`

#### `genomics/qq-plot@1`
Origin: new · Needs: V1 (points and a reference line), X26 · Wave: LB4 · Size: 1

Observed against expected p-values, with the diagonal and the inflation factor as a figure.
- **Parameters:** `rows: table{p: number}` (required) · `max-points: integer = 5000`
- **Data:** contract `association-results` (uses `p`) · **Slots:** none · **Events:** none
- **Use:** `{ use: genomics/qq-plot@1, with: { rows: $.results } }`

#### `genomics/volcano-plot@1`
Origin: new · Needs: baseline (kind `scatter`) · Wave: LB2 · Size: 0.5

Effect size against significance for each gene, the up and down groups toned, the strongest labelled.
- **Parameters:** `rows: table{name: text, effect: number, p: number}` (required) · `effect-cut: number = 1` · `p-cut: number = 0.05`
- **Data:** `rows`; `max-rows: 20000` · **Slots:** none · **Events:** `point {name: text, row: record}`
- **Use:** `{ use: genomics/volcano-plot@1, with: { rows: { from: $.differential, columns: { name: gene, effect: log2fc, p: padj } } } }`

#### `genomics/hit-table@1`
Origin: pattern (association lists) · Needs: baseline · Wave: LB2 · Size: 0.5

The leading association hits: variant, nearest gene, effect, standard error, p-value, sorted by significance.
- **Parameters:** `rows: table{variant: text, gene?: text, effect: number, se?: number, p: number}` (required) · `top: integer = 50`
- **Data:** contract `association-results` · **Slots:** none · **Events:** `open {variant: text}`
- **Use:** `{ use: genomics/hit-table@1, with: { rows: $.results } }`

### 5.4 Cohorts and expression

#### `genomics/cohort-matrix@1`
Origin: new · Needs: baseline (kind `pivot` with heat); K2 adds the alteration glyphs · Wave: LB2 · Size: 1

Genes down, samples across, each cell saying whether and how the gene is altered in that sample; rows and columns ordered by frequency.
- **Parameters:** `rows: table{gene: text, sample: text, alteration: text}` (required) · `max-genes: integer = 30` · `max-samples: integer = 200`
- **Data:** contract `alterations` · **Slots:** none · **Events:** `cell {gene: text, sample: text}`
- **Use:** `{ use: genomics/cohort-matrix@1, title: Alterations, with: { rows: $.alterations } }`

#### `genomics/expression-heatmap@1`
Origin: new · Needs: baseline (kind `pivot` with heat) · Wave: LB2 · Size: 0.5

Expression of chosen genes across samples or groups as a shaded grid, on a z-score or raw scale.
- **Parameters:** `rows: table{gene: text, sample: text, value: number}` (required) · `scale: enum[raw, row-z] = row-z` · `fmt: format = signed2`
- **Data:** `rows` in long form; `max-rows: 50000` · **Slots:** none · **Events:** `cell {gene: text, sample: text}`
- **Use:** `{ use: genomics/expression-heatmap@1, with: { rows: $.expression } }`

#### `genomics/survival-curve@1`
Origin: new · Needs: baseline (kind `line` with `step`); V1 for several groups on one chart · Wave: LB2 · Size: 0.5

A survival curve as steps: the share surviving against time, with the number at risk as a table; the estimate is supplied.
- **Parameters:** `rows: table{time: number, survival: number, at-risk?: integer}` (required) · `time-label: text = "Months"`
- **Data:** `rows` in time order · **Slots:** none · **Events:** none
- **Use:** `{ use: genomics/survival-curve@1, with: { rows: $.survival } }`

#### `genomics/sample-map@1`
Origin: new · Needs: V5 (points on a map) · Wave: LB4 · Size: 0.5

Where samples come from: points or counts by place on a map; a variant of `geo/point-map@1`.
- **Parameters:** `rows: table{id: text, latitude: number, longitude: number, group?: text}` (required) · `cluster: boolean = true`
- **Data:** `rows` · **Slots:** none · **Events:** `place {ids: list<text>}`
- **Use:** `{ use: genomics/sample-map@1, with: { rows: $.samples } }`

#### `genomics/pathway-graph@1`
Origin: pattern (pathway membership) · Needs: baseline (kind `graph`) · Wave: LB2 · Size: 0.5

The genes of a pathway and their interactions, the altered or significant ones toned.
- **Parameters:** `nodes: table{id: text, label: text, group?: text}` (required) · `edges: table{from: text, to: text, kind?: text}` (required)
- **Data:** `nodes`, `edges`; `max-rows: 500` each · **Slots:** none · **Events:** `node {id: text}`
- **Use:** `{ use: genomics/pathway-graph@1, with: { nodes: $.pathway_genes, edges: $.interactions } }`

### 5.5 Genome tracks

#### `genomics/coverage-track@1`
Origin: new · Needs: baseline (kind `area`) · Wave: LB2 · Size: 0.5

Read depth along a region as an area, with the target depth as a line.
- **Parameters:** `rows: table{position: integer, depth: number}` (required) · `target: number? = none`
- **Data:** `rows` in position order (the host bins a long region) · **Slots:** none · **Events:** none
- **Use:** `{ use: genomics/coverage-track@1, with: { rows: $.depth, target: 30 } }`

#### `genomics/copy-number-track@1`
Origin: pattern (copy-number segments) · Needs: V1 (rules and bands) · Wave: LB4 · Size: 1

Copy-number segments along the chromosomes: each segment at its level, gains and losses toned, chromosome boundaries marked.
- **Parameters:** `rows: table{chromosome: text, start: integer, end: integer, value: number}` (required) · `gain-above: number = 0.3` · `loss-below: number = -0.3`
- **Data:** contract `segments` · **Slots:** none · **Events:** `segment {row: record}`
- **Use:** `{ use: genomics/copy-number-track@1, with: { rows: $.segments } }`

#### `genomics/region-browser@1`
Origin: new · Needs: K3 (kind `tracks`), D2 · Wave: LB4 · Size: 2

A region of the genome as stacked tracks on one coordinate axis (genes, variants, coverage, the user's own), with pan and zoom.
- **Parameters:** `chromosome: text` (required) · `start: integer` (required) · `end: integer` (required) · `genes: table{name: text, start: integer, end: integer, strand?: text}? = none` · `variants: table{position: integer, label?: text}? = none` · `into: state<text>? = none` (the region shown)
- **Data:** each track's table, read for the region shown (windowed by position) · **Slots:** `tracks` (layers, max 6) · **Events:** `region {chromosome: text, start: integer, end: integer}`, `feature {track: text, row: record}`
- **Use:** `{ use: genomics/region-browser@1, with: { chromosome: "$.gene.chromosome", start: "$.gene.start", end: "$.gene.end", genes: $.genes, variants: $.variants } }`

### 5.6 Proteins and design

#### `genomics/protein-card@1`
Origin: pattern (protein screens) · Needs: baseline · Wave: LB2 · Size: 0.5

A protein: name, organism, length, mass, function in a line, its domains as a list.
- **Parameters:** `protein: record{id: text, name: text, organism: text?, length: integer?, mass: number?, function: text?}` (required) · `domains: table{name: text, start: integer, end: integer}? = none`
- **Data:** optional `domains` · **Slots:** none · **Events:** none
- **Use:** `{ use: genomics/protein-card@1, with: { protein: "$.protein", domains: $.domains } }`

#### `genomics/structure-panel@1`
Origin: new · Needs: K4 (kind `structure`, a molecular 3D view in Drishti Elements) · Wave: LB4 · Size: 2

A protein's 3D structure, coloured by chain, by a per-residue value or by confidence, with chosen residues highlighted.
- **Parameters:** `structure: text` (required; a handle the host resolves to coordinates, never an address) · `colour-by: enum[chain, value, confidence] = chain` · `values: table{residue: integer, value: number}? = none` · `highlight: list<integer>? = none` · `feeds: selection? = none`
- **Data:** optional per-residue `values` · **Slots:** none · **Events:** `residue {residue: integer, chain: text}`
- **Use:** `{ use: genomics/structure-panel@1, with: { structure: "$.protein.structure", colour-by: value, values: $.scores, highlight: "sel.residues" } }`

#### `genomics/sequence-view@1`
Origin: new · Needs: K5 (kind `sequence`) · Wave: LB4 · Size: 1

A sequence in rows of fixed width with position rulers, features as bands, and differences from a reference toned.
- **Parameters:** `sequence: text` (required) · `reference: text? = none` · `features: table{name: text, start: integer, end: integer}? = none` · `width: integer = 60` · `feeds: selection? = none`
- **Data:** optional `features` · **Slots:** none · **Events:** `residue {position: integer}`
- **Use:** `{ use: genomics/sequence-view@1, with: { sequence: "$.design.sequence", reference: "$.protein.sequence" } }`

#### `genomics/mutation-scan-heatmap@1`
Origin: pattern (mutation-scan scores per position and substitution) · Needs: baseline (kind `pivot` with heat) · Wave: LB2 · Size: 0.5

Every substitution at every position as a shaded grid: position across, amino acid down, the score in the cell.
- **Parameters:** `rows: table{position: integer, substitution: text, score: number}` (required) · `fmt: format = signed2` · `from: integer? = none` · `to: integer? = none`
- **Data:** contract `mutation-scores` · **Slots:** none · **Events:** `cell {position: integer, substitution: text}`
- **Use:** `{ use: genomics/mutation-scan-heatmap@1, with: { rows: $.scores } }`

#### `genomics/contact-map@1`
Origin: pattern (residue contacts) · Needs: baseline (kind `pivot` with heat) · Wave: LB2 · Size: 0.5

Which residues are close to which, as a shaded square by residue number.
- **Parameters:** `rows: table{a: integer, b: integer, distance: number}` (required) · `within: number = 8`
- **Data:** `rows`; `max-rows: 50000` · **Slots:** none · **Events:** `cell {a: integer, b: integer}`
- **Use:** `{ use: genomics/contact-map@1, with: { rows: $.contacts } }`

#### `genomics/design-leaderboard@1`
Origin: pattern (designs ranked within a design round) · Needs: baseline (V2 adds the bars in cells) · Wave: LB2 · Size: 1

The designs of a round ranked by a score, with the other measures beside it, the round's best marked and the change from the previous round.
- **Parameters:** `rows: table{design: text, round: text, score: number, mutations?: integer, stability?: number, activity?: number}` (required) · `round: text? = none` · `top: integer = 20` · `fmt: format = signed3`
- **Data:** contract `designs` · **Slots:** `columns` (columns, max 6) · **Events:** `open {design: text, row: record}`
- **Use:** `{ use: genomics/design-leaderboard@1, title: "Round ${state.round}", with: { rows: $.designs, round: "state.round" } }`

#### `genomics/round-progress@1`
Origin: new · Needs: baseline · Wave: LB2 · Size: 0.5

How the best and the median score moved from round to round.
- **Parameters:** `rows: table{round: text, best: number, median?: number, designs?: integer}` (required) · `fmt: format = signed3`
- **Data:** `rows` in round order · **Slots:** none · **Events:** `round {round: text}`
- **Use:** `{ use: genomics/round-progress@1, with: { rows: $.rounds }, on: { round: [ { set: { round: "event.round" } } ] } }`

#### `genomics/design-card@1`
Origin: pattern (design screens) · Needs: baseline · Wave: LB2 · Size: 0.5

One design: its round, its parent, the mutations against the reference as a list, and its scores.
- **Parameters:** `design: record{id: text, round: text?, parent: text?, score: number?}` (required) · `mutations: table{position: integer, from: text, to: text, effect?: number}? = none`
- **Data:** optional `mutations` · **Slots:** none · **Events:** `mutation {position: integer}`
- **Use:** `{ use: genomics/design-card@1, with: { design: "$.design", mutations: $.mutations } }`

## 6. `economy`: countries, indicators, the public sector

Generic terms: a country, a region, an indicator, an observation, a forecast vintage. A fragment shows published
figures with their source; it names no publisher. Pair every screen with `core/source-note@1`.

### 6.1 Countries

#### `economy/country-card@1`
Origin: pattern (country profile screens) · Needs: baseline · Wave: LB2 · Size: 0.5

A country at a glance: name, region, income group, population, output, output per head, currency.
- **Parameters:** `country: record{code: text, name: text, region: text?, income-group: text?, population: number?, gdp: number?, gdp-per-head: number?, currency: text?}` (required) · `year: integer? = none`
- **Data:** none (a record) · **Slots:** `footer` (panels, max 2) · **Events:** none
- **Use:** `{ use: economy/country-card@1, with: { country: "$.country", year: "$.year" } }`

#### `economy/country-compare@1`
Origin: new · Needs: baseline · Wave: LB2 · Size: 1

Two to five countries side by side over a chosen list of indicators, the best and worst in each row toned.
- **Parameters:** `rows: table{country: text, indicator: text, value: number}` (required) · `countries: list<text>` (required; at most 5) · `fmt: format = amount2`
- **Data:** contract `observations` (the latest per country and indicator) · **Slots:** none · **Events:** `cell {country: text, indicator: text}`
- **Use:** `{ use: economy/country-compare@1, with: { rows: $.latest, countries: "sel.countries" } }`

#### `economy/group-members@1`
Origin: kit (`measure-table` as used for country groups) · Needs: baseline · Wave: LB2 · Size: 0.5

The members of a group of countries with a level and a growth figure each, the level totalled.
- **Parameters:** `rows: table{country: text, level: number, growth?: number}` (required) · `level-label: text` (required) · `level-fmt: format = amount0` · `growth-fmt: format = pc2`
- **Data:** `rows` · **Slots:** none · **Events:** `open {country: text}`
- **Use:** `{ use: economy/group-members@1, with: { rows: $.members, level-label: "Output, bn" } }`

### 6.2 Indicators

#### `economy/indicator-figure@1`
Origin: pattern (the headline figure of indicator screens) · Needs: baseline · Wave: LB2 · Size: 0.5

An indicator's latest value with its period, its unit, and the change from the period before and from a year before.
- **Parameters:** `latest: number` (required) · `period: text` (required) · `previous: number? = none` · `year-ago: number? = none` · `unit: text? = none` · `fmt: format = amount2` · `good: enum[up, down, none] = none`
- **Data:** none · **Slots:** none · **Events:** none
- **Use:** `{ use: economy/indicator-figure@1, title: Inflation, with: { latest: "$.cpi.latest", period: "$.cpi.period", year-ago: "$.cpi.year_ago", unit: "%", good: down } }`

#### `economy/indicator-series@1`
Origin: kit (`series-line` as used on indicator screens) · Needs: baseline · Wave: LB2 · Size: 0.5

An indicator through time with its unit and frequency, the latest point marked.
- **Parameters:** `rows: table{period: date, value: number}` (required) · `unit: text? = none` · `fmt: format = amount2` · `step: boolean = false`
- **Data:** contract `observations` (one country, one indicator) · **Slots:** none · **Events:** `point {period: date}`
- **Use:** `{ use: economy/indicator-series@1, title: Unemployment rate, with: { rows: $.series, unit: "%" } }`

#### `economy/indicator-compare@1`
Origin: new · Needs: V1 · Wave: LB4 · Size: 1

One indicator for several countries on one chart, optionally rebased, with shaded periods (recessions, a policy regime).
- **Parameters:** `rows: table{period: date, country: text, value: number}` (required) · `bands: table{from: date, to: date, label?: text}? = none` · `rebase: boolean = false` · `fmt: format = amount2`
- **Data:** contract `observations`; at most 12 countries · **Slots:** none · **Events:** `point {country: text, period: date}`
- **Use:** `{ use: economy/indicator-compare@1, with: { rows: $.growth, bands: $.recessions } }`

#### `economy/indicator-table@1`
Origin: pattern (tables of latest indicators) · Needs: baseline (V2 adds a sparkline per row) · Wave: LB2 · Size: 0.5

A country's indicators as a table: name, latest value, period, change, unit.
- **Parameters:** `rows: table{indicator: text, value: number, period: text, change?: number, unit?: text}` (required) · `fmt: format = amount2`
- **Data:** `rows` · **Slots:** none · **Events:** `open {indicator: text}`
- **Use:** `{ use: economy/indicator-table@1, title: Key indicators, with: { rows: $.indicators } }`

#### `economy/world-indicator-map@1`
Origin: kit (`region-map`) · Needs: baseline (kind `map`) · Wave: LB2 · Size: 0.5

The world shaded by one indicator, one country highlighted, each country a click away; a variant of `geo/region-map@1`.
- **Parameters:** `rows: table{code: text, value: number}` (required) · `highlight: text? = none` · `fmt: format = amount2` · `unit: text? = none`
- **Data:** `rows` keyed by ISO 3166 alpha-3 code · **Slots:** none · **Events:** `region {code: text}`
- **Use:** `{ use: economy/world-indicator-map@1, title: Growth, with: { rows: $.growth_by_country, highlight: "$.country.code" } }`

#### `economy/contribution-bars@1`
Origin: new · Needs: V1 (stacked bars with a line) · Wave: LB4 · Size: 1

What a headline rate is made of: stacked contributions per period (components of inflation, of growth) with the total as a line.
- **Parameters:** `rows: table{period: date, component: text, contribution: number}` (required) · `fmt: format = signed2` · `palette: palette = categorical-1`
- **Data:** `rows` in long form · **Slots:** none · **Events:** `bar {period: date, component: text}`
- **Use:** `{ use: economy/contribution-bars@1, title: Contributions to inflation, with: { rows: $.contributions } }`

### 6.3 Forecasts

#### `economy/forecast-table@1`
Origin: pattern (projection tables: years across) · Needs: baseline (kind `pivot`) · Wave: LB2 · Size: 0.5

Projections with years across and indicators down, the projected years marked apart from the reported ones.
- **Parameters:** `rows: table{indicator: text, year: integer, value: number, projected?: boolean}` (required) · `fmt: format = amount1`
- **Data:** contract `forecasts` · **Slots:** none · **Events:** `cell {indicator: text, year: integer}`
- **Use:** `{ use: economy/forecast-table@1, title: Projections, with: { rows: $.projections } }`

#### `economy/forecast-fan@1`
Origin: new · Needs: V1 (bands) · Wave: LB4 · Size: 1

History as a line and the forecast as a fan of ranges widening into the future.
- **Parameters:** `rows: table{period: date, value: number, low?: number, high?: number, projected?: boolean}` (required) · `fmt: format = amount1`
- **Data:** contract `forecasts` (one indicator) · **Slots:** none · **Events:** none
- **Use:** `{ use: economy/forecast-fan@1, with: { rows: $.gdp_path } }`

#### `economy/vintage-compare@1`
Origin: new · Needs: baseline · Wave: LB2 · Size: 0.5

How a forecast for the same years changed between two publications: this vintage, the last, the revision.
- **Parameters:** `rows: table{year: integer, current: number, previous: number}` (required) · `current-label: text = "Latest"` · `previous-label: text = "Previous"` · `fmt: format = amount1`
- **Data:** `rows` · **Slots:** none · **Events:** none
- **Use:** `{ use: economy/vintage-compare@1, with: { rows: $.revisions } }`

### 6.4 Public finances and policy

#### `economy/fiscal-position@1`
Origin: pattern (fiscal position screens) · Needs: baseline · Wave: LB2 · Size: 1

A government's finances: balance, debt, revenue and spending as shares of output, as figures and a line each.
- **Parameters:** `rows: table{year: integer, balance: number, debt: number, revenue?: number, spending?: number}` (required) · `fmt: format = pc1`
- **Data:** `rows` in year order · **Slots:** none · **Events:** none
- **Use:** `{ use: economy/fiscal-position@1, with: { rows: $.fiscal } }`

#### `economy/labour-panel@1`
Origin: pattern (labour market screens) · Needs: baseline · Wave: LB2 · Size: 1

A labour market: unemployment and participation as figures and lines, employment by sector as a table.
- **Parameters:** `rates: table{period: date, unemployment: number, participation?: number}` (required) · `sectors: table{name: text, first: number, second?: number}? = none` · `fmt: format = pc1`
- **Data:** `rates`; optional `sectors` · **Slots:** none · **Events:** none
- **Use:** `{ use: economy/labour-panel@1, with: { rates: $.labour, sectors: $.jobs_by_sector } }`

#### `economy/policy-rate-path@1`
Origin: new · Needs: baseline (kind `line` with `step`) · Wave: LB2 · Size: 0.5

A central bank's policy rate as steps through time, with the decisions as a list beside it.
- **Parameters:** `rows: table{date: date, rate: number, decision?: text}` (required) · `fmt: format = pct2`
- **Data:** `rows` in date order · **Slots:** none · **Events:** `decision {date: date}`
- **Use:** `{ use: economy/policy-rate-path@1, title: Policy rate, with: { rows: $.decisions } }`

#### `economy/release-calendar@1`
Origin: new · Needs: baseline · Wave: LB2 · Size: 0.5

Upcoming and recent data releases: date, indicator, the period it covers, the previous value, the value when out.
- **Parameters:** `rows: table{date: datetime, indicator: text, period?: text, previous?: number, actual?: number}` (required) · `fmt: format = amount2`
- **Data:** `rows`; `live: ok` · **Slots:** none · **Events:** `open {indicator: text}`
- **Use:** `{ use: economy/release-calendar@1, with: { rows: $.releases } }`

#### `economy/trade-flows@1`
Origin: new · Needs: V4 (kind `sankey`) · Wave: LB4 · Size: 0.5

Who trades what with whom: exporters to products to importers, sized by value.
- **Parameters:** `rows: table{from: text, to: text, value: number}` (required) · `fmt: format = compact`
- **Data:** `rows` · **Slots:** none · **Events:** `link {from: text, to: text}`
- **Use:** `{ use: economy/trade-flows@1, with: { rows: $.flows } }`

### 6.5 Politics and news

#### `economy/election-results@1`
Origin: pattern (election screens: a table and bars by party) · Needs: baseline · Wave: LB2 · Size: 1

An election's result: votes and seats by party as bars and a table, with turnout and the change from the last one.
- **Parameters:** `rows: table{party: text, votes?: number, share: number, seats?: integer, change?: number}` (required) · `turnout: number? = none` · `majority: integer? = none`
- **Data:** `rows` · **Slots:** none · **Events:** `party {party: text}`
- **Use:** `{ use: economy/election-results@1, with: { rows: $.results, turnout: "$.election.turnout" } }`

#### `economy/tenure-timeline@1`
Origin: pattern (office holders through time) · Needs: baseline (kind `timeline`) · Wave: LB2 · Size: 0.5

Who held an office and when: holder, party, from and to.
- **Parameters:** `rows: table{from: date, to?: date, holder: text, party?: text}` (required)
- **Data:** `rows` · **Slots:** none · **Events:** `select {holder: text}`
- **Use:** `{ use: economy/tenure-timeline@1, title: Heads of government, with: { rows: $.holders } }`

#### `economy/news-pulse@1`
Origin: pack (headlines with their sources as bars) · Needs: baseline · Wave: LB2 · Size: 1

The news about a topic or a place: the count of items by source or theme as bars, and the items under them.
- **Parameters:** `rows: table{date: datetime, headline: text, source: text, theme?: text, link?: text}` (required) · `by: enum[source, theme] = source` · `limit: integer = 20`
- **Data:** `rows`; `live: ok` · **Slots:** none · **Events:** `open {link: text, row: record}`
- **Use:** `{ use: economy/news-pulse@1, with: { rows: $.news } }`

## 7. `geo`: maps

Shapes are the painter's vendored sets (the world, admin-1 regions of the countries Drishti Elements ships); no tile
server is ever contacted. Places are named by ISO 3166 codes or by latitude and longitude.

#### `geo/region-map@1`
Origin: kit (`region-map`) · Needs: baseline (kind `map`) · Wave: LB2 · Size: 0.5

The world, or one country's regions, shaded by one figure, with one region highlighted and each a click away.
- **Parameters:** `rows: table{code: text, value: number}` (required) · `scope: text = "world"` (a country code for its regions) · `highlight: text? = none` · `fmt: format = amount0` · `unit: text? = none` · `feeds: selection? = none`
- **Data:** `rows` keyed by region code · **Slots:** none · **Events:** `region {code: text, value: number}`
- **Use:** `{ use: geo/region-map@1, title: Sales by country, with: { rows: { from: $.by_country, columns: { code: iso3, value: revenue } } } }`

#### `geo/region-ranking@1`
Origin: new · Needs: I2 (a selection shared by the map and the table), L1 · Wave: LB3 · Size: 1

A shaded map beside a ranked table of the same regions; choosing in either highlights the other.
- **Parameters:** `rows: table{code: text, name: text, value: number}` (required) · `scope: text = "world"` · `fmt: format = amount0` · `top: integer = 15`
- **Data:** `rows` · **Slots:** none · **Events:** `region {code: text}` · **Outputs:** `chosen: list<text>`
- **Use:** `{ use: geo/region-ranking@1, with: { rows: $.by_country } }`

#### `geo/region-picker@1`
Origin: new · Needs: S1 · Wave: LB3 · Size: 0.5

A small map used as a filter: a click writes the region's code into a state the user names.
- **Parameters:** `into: state<text>` (required) · `scope: text = "world"` · `available: list<text>? = none`
- **Data:** none · **Slots:** none · **Events:** `change {code: text}`
- **Use:** `{ use: geo/region-picker@1, with: { into: country } }`

#### `geo/location-card@1`
Origin: new · Needs: baseline · Wave: LB2 · Size: 0.5

One place: its name, address lines, coordinates and the region it is in, with that region highlighted on a small map.
- **Parameters:** `place: record{name: text, address: text?, latitude: number?, longitude: number?, country: text?, region: text?}` (required)
- **Data:** none (a record) · **Slots:** none · **Events:** none
- **Use:** `{ use: geo/location-card@1, with: { place: "$.site" } }`

#### `geo/point-map@1`
Origin: new · Needs: V5 (map layers: points) · Wave: LB4 · Size: 1

Places as points on a map, coloured by a group, clustered when dense.
- **Parameters:** `rows: table{id: text, latitude: number, longitude: number, label?: text, group?: text}` (required) · `scope: text = "world"` · `cluster: boolean = true` · `palette: palette = categorical-1`
- **Data:** `rows`; `max-rows: 50000` · **Slots:** none · **Events:** `place {id: text, row: record}`
- **Use:** `{ use: geo/point-map@1, title: Sites, with: { rows: $.sites } }`

#### `geo/bubble-map@1`
Origin: new · Needs: V5 (bubbles) · Wave: LB4 · Size: 0.5

A measure per place as bubbles sized by value, toned by a second measure.
- **Parameters:** `rows: table{id: text, latitude: number, longitude: number, value: number, shade?: number, label?: text}` (required) · `fmt: format = amount0`
- **Data:** `rows`; `max-rows: 5000` · **Slots:** none · **Events:** `place {id: text}`
- **Use:** `{ use: geo/bubble-map@1, with: { rows: { from: $.branches, columns: { value: deposits } } } }`

#### `geo/flow-map@1`
Origin: new · Needs: V5 (lines) · Wave: LB4 · Size: 1

Movements between places as lines whose width is the quantity.
- **Parameters:** `rows: table{from-latitude: number, from-longitude: number, to-latitude: number, to-longitude: number, value: number, label?: text}` (required) · `fmt: format = amount0` · `top: integer = 200`
- **Data:** `rows` · **Slots:** none · **Events:** `flow {row: record}`
- **Use:** `{ use: geo/flow-map@1, title: Shipments, with: { rows: $.routes } }`

#### `geo/layered-map@1`
Origin: new · Needs: V5 (shaded regions with points over them) · Wave: LB4 · Size: 1

A shaded region map with a layer of points on top: a rate by region and the sites inside it.
- **Parameters:** `regions: table{code: text, value: number}` (required) · `points: table{id: text, latitude: number, longitude: number, label?: text}` (required) · `scope: text = "world"` · `fmt: format = amount0`
- **Data:** `regions`, `points` · **Slots:** `layers` (layers, max 3) · **Events:** `region {code: text}`, `place {id: text}`
- **Use:** `{ use: geo/layered-map@1, with: { regions: $.rate_by_state, points: $.clinics, scope: US } }`

## 8. `ops`: operations, data quality, administration

For the screens every data product needs about itself. These fragments are also what Rachana's own tools are drawn
with (the conformance report, the library gate's report, the standalone server's status page).

### 8.1 Runs and health

#### `ops/job-runs@1`
Origin: new · Needs: baseline · Wave: LB2 · Size: 0.5

Runs of jobs as a table: job, start, duration, rows, status (toned), with the failed ones first.
- **Parameters:** `rows: table{job: text, started: datetime, status: text, duration?: number, rows?: integer, message?: text}` (required) · `limit: integer = 100`
- **Data:** contract `job-runs`; `live: ok` · **Slots:** none · **Events:** `open {row: record}`
- **Use:** `{ use: ops/job-runs@1, title: Loads, with: { rows: $.runs } }`

#### `ops/run-timeline@1`
Origin: new · Needs: baseline (kind `timeline`) · Wave: LB2 · Size: 0.5

One run's steps in order with their status and how long each took.
- **Parameters:** `rows: table{date: datetime, label: text, status?: text, detail?: text}` (required)
- **Data:** `rows` · **Slots:** none · **Events:** `select {row: record}`
- **Use:** `{ use: ops/run-timeline@1, with: { rows: $.steps } }`

#### `ops/health-board@1`
Origin: new · Needs: L1 (`each`) · Wave: LB3 · Size: 1

Services or nodes as a grid of tiles, each with its state, a figure and how long it has been in that state; the unhealthy first.
- **Parameters:** `rows: table{name: text, state: text, since?: datetime, figure?: number, group?: text}` (required) · `per-row: integer = 6` · `fmt: format = amount0`
- **Data:** `rows`; key `name`; `live: ok` · **Slots:** none · **Events:** `open {name: text}`
- **Use:** `{ use: ops/health-board@1, title: Cluster, with: { rows: $.nodes } }`

#### `ops/sla-figure@1`
Origin: new · Needs: baseline · Wave: LB2 · Size: 0.5

A service level: the share met over a period against its target, as a gauge with the misses counted.
- **Parameters:** `met: number` (required) · `total: number` (required) · `target: number = 0.99` · `label: text = "Within target"`
- **Data:** none · **Slots:** none · **Events:** none
- **Use:** `{ use: ops/sla-figure@1, with: { met: "$.sla.met", total: "$.sla.total", target: 0.995 } }`

#### `ops/throughput-chart@1`
Origin: new · Needs: baseline (kind `area`) · Wave: LB2 · Size: 0.5

A rate through time (requests, rows, messages) with a capacity line and the peak as a figure.
- **Parameters:** `rows: table{time: datetime, value: number}` (required) · `capacity: number? = none` · `unit: text? = none` · `fmt: format = compact`
- **Data:** `rows`; `live: ok` · **Slots:** none · **Events:** none
- **Use:** `{ use: ops/throughput-chart@1, title: Rows per second, with: { rows: $.throughput } }`

#### `ops/log-tail@1`
Origin: new · Needs: D6 (`tail`, `flash`), D2 · Wave: LB4 · Size: 1

The last lines of a log as they arrive, with a level filter and a text search; older lines fall off.
- **Parameters:** `rows: table{time: datetime, level: text, message: text, source?: text}` (required) · `keep: integer = 500` · `levels: list<text> = [ERROR, WARN, INFO]`
- **Data:** `rows`; `live: ok`, appending · **Slots:** none · **Events:** `open {row: record}`
- **Use:** `{ use: ops/log-tail@1, with: { rows: $.log } }`

#### `ops/capacity-bars@1`
Origin: new · Needs: V4 (kind `bullet`) · Wave: LB4 · Size: 0.5

Used against capacity for several resources at once, each with its warning mark.
- **Parameters:** `rows: table{name: text, used: number, capacity: number}` (required) · `warn-at: number = 0.8` · `fmt: format = compact`
- **Data:** `rows` · **Slots:** none · **Events:** `open {name: text}`
- **Use:** `{ use: ops/capacity-bars@1, with: { rows: $.volumes } }`

### 8.2 Data quality

#### `ops/freshness-table@1`
Origin: new · Needs: baseline · Wave: LB2 · Size: 0.5

Tables with the time of their last load, their age against what is expected, and their row counts.
- **Parameters:** `rows: table{name: text, loaded: datetime, expected-every?: number, rows?: integer}` (required) · `now: datetime` (required)
- **Data:** contract `table-status` · **Slots:** none · **Events:** `open {name: text}`
- **Use:** `{ use: ops/freshness-table@1, title: Data freshness, with: { rows: $.tables, now: "now()" } }`

#### `ops/quality-scorecard@1`
Origin: new · Needs: baseline · Wave: LB2 · Size: 1

Data checks: the share passed as a figure, failures by rule as bars, the failing checks as a table.
- **Parameters:** `rows: table{check: text, table: text, status: text, failed-rows?: integer, message?: text}` (required)
- **Data:** contract `check-results` · **Slots:** none · **Events:** `open {check: text, row: record}`
- **Use:** `{ use: ops/quality-scorecard@1, with: { rows: $.checks } }`

#### `ops/column-profile@1`
Origin: new · Needs: baseline · Wave: LB2 · Size: 1

One column of a table profiled: type, share empty, distinct values, minimum and maximum, the commonest values and the distribution.
- **Parameters:** `profile: record{name: text, type: text, rows: integer, empty: integer, distinct: integer?, min: any?, max: any?}` (required) · `top: table{value: text, count: integer}? = none` · `values: list<number>? = none`
- **Data:** optional `top` · **Slots:** none · **Events:** none
- **Use:** `{ use: ops/column-profile@1, with: { profile: "slot.row", top: $.top_values } }`

#### `ops/reject-report@1`
Origin: new · Needs: baseline · Wave: LB2 · Size: 0.5

Rows a load refused: the count by reason as bars and the rejected rows with their reason.
- **Parameters:** `rows: table{row: integer, reason: text, column?: text, value?: text}` (required) · `limit: integer = 200`
- **Data:** `rows` · **Slots:** none · **Events:** `reason {reason: text}`
- **Use:** `{ use: ops/reject-report@1, with: { rows: $.rejects } }`

### 8.3 Schema and lineage

#### `ops/schema-view@1`
Origin: new · Needs: baseline · Wave: LB2 · Size: 0.5

A table's columns as the Data Protocol describes them: name, type, key, unit, format, meaning.
- **Parameters:** `columns: table{name: text, type: text, key?: boolean, unit?: text, fmt?: text, doc?: text}` (required) · `name: text? = none`
- **Data:** `columns` (a table's schema as rows) · **Slots:** none · **Events:** `select {name: text}`
- **Use:** `{ use: ops/schema-view@1, with: { columns: $.schema, name: "'sales'" } }`

#### `ops/contract-fit@1`
Origin: new · Needs: baseline · Wave: LB2 · Size: 0.5

A table against a contract: each required column found, missing or of the wrong type (the report of `rachana lib check-host`).
- **Parameters:** `rows: table{column: text, needed: text, found?: text, status: text}` (required) · `contract: text` (required)
- **Data:** `rows` · **Slots:** none · **Events:** none
- **Use:** `{ use: ops/contract-fit@1, with: { rows: $.fit, contract: "'scenario-pnl'" } }`

#### `ops/lineage-graph@1`
Origin: new · Needs: baseline (kind `graph`) · Wave: LB2 · Size: 0.5

Where a table comes from and what reads it: sources, steps and consumers as a layered graph.
- **Parameters:** `nodes: table{id: text, label: text, group?: text}` (required) · `edges: table{from: text, to: text}` (required) · `focus: text? = none`
- **Data:** `nodes`, `edges` · **Slots:** none · **Events:** `node {id: text}`
- **Use:** `{ use: ops/lineage-graph@1, with: { nodes: $.objects, edges: $.reads, focus: "state.table" } }`

#### `ops/problem-list@1`
Origin: new · Needs: baseline · Wave: LB2 · Size: 0.5

Problems in the compiler's shape (code, severity, message, file, line), grouped by file, errors first.
- **Parameters:** `rows: table{code: text, severity: text, message: text, file?: text, line?: integer}` (required)
- **Data:** `rows` · **Slots:** none · **Events:** `open {file: text, line: integer}`
- **Use:** `{ use: ops/problem-list@1, title: Lint, with: { rows: $.problems } }`

### 8.4 Work queues

#### `ops/approval-queue@1`
Origin: new · Needs: I3 (multi-select and bulk), I1 · Wave: LB3 · Size: 1

Items waiting for a decision: tick several, approve or reject with a reason; the decision is an event.
- **Parameters:** `rows: table{id: text, subject: text, requested-by: text, requested: datetime, kind?: text}` (required) · `need-reason: enum[never, reject, always] = reject`
- **Data:** `rows`; key `id`; `live: ok` · **Slots:** `detail` (panels, max 4; exposes `row: record`) · **Events:** `decide {ids: list<text>, decision: text, reason: text?}`
- **Use:** `{ use: ops/approval-queue@1, with: { rows: $.pending }, on: { decide: [ { host: approvals.decide, with: { ids: "event.ids", decision: "event.decision" } } ] } }`

#### `ops/checklist@1`
Origin: new · Needs: baseline · Wave: LB2 · Size: 0.5

Steps of a procedure with their state, who did each and when, and the share done.
- **Parameters:** `rows: table{step: text, status: text, by?: text, at?: datetime}` (required)
- **Data:** `rows` in step order · **Slots:** none · **Events:** `step {step: text}`
- **Use:** `{ use: ops/checklist@1, title: Release checklist, with: { rows: $.steps } }`

#### `ops/plan-gantt@1`
Origin: new · Needs: V4 (kind `gantt`) · Wave: LB4 · Size: 0.5

Tasks on a time axis with their dependencies and progress.
- **Parameters:** `rows: table{id: text, name: text, start: date, end: date, progress?: number, after?: text, group?: text}` (required) · `today: date? = none`
- **Data:** `rows` · **Slots:** none · **Events:** `task {id: text}`
- **Use:** `{ use: ops/plan-gantt@1, with: { rows: $.tasks, today: "asof()" } }`

## 9. `ai`: assistant and generated screens

For hosts that put a model beside their screens. Nothing here calls a model: a fragment shows what the host's
assistant produced and reports what the reader did. `ai` names no provider.

#### `ai/answer-card@1`
Origin: new · Needs: baseline · Wave: LB2 · Size: 0.5

An answer in words with its sources listed under it and the time it was produced. Text only.
- **Parameters:** `text: text` (required) · `sources: table{title: text, link?: text, detail?: text}? = none` · `produced: datetime? = none` · `generated-label: message = generated`
- **Data:** optional `sources` · **Slots:** `under` (panels, max 3) · **Events:** `source {link: text}`
- **Use:** `{ use: ai/answer-card@1, with: { text: "$.answer.text", sources: $.answer_sources } }`

#### `ai/citation-list@1`
Origin: new · Needs: baseline · Wave: LB2 · Size: 0.5

The sources behind an answer: title, where from, the passage used.
- **Parameters:** `rows: table{title: text, origin?: text, passage?: text, link?: text}` (required)
- **Data:** `rows` · **Slots:** none · **Events:** `open {link: text}`
- **Use:** `{ use: ai/citation-list@1, with: { rows: $.citations } }`

#### `ai/prompt-box@1`
Origin: new · Needs: baseline (`form:` without save) · Wave: LB2 · Size: 0.5

A box to ask a question, with example questions; sending it is an event. It calls nothing.
- **Parameters:** `placeholder: text = "Ask about this screen"` · `examples: list<text>? = none` · `busy: boolean = false` · `max-chars: integer = 2000`
- **Data:** none · **Slots:** none · **Events:** `ask {text: text}`
- **Use:** `{ use: ai/prompt-box@1, with: { examples: [ "Which desk moved most today?" ], busy: "$.assistant.busy" }, on: { ask: [ { host: assistant.ask, with: { text: "event.text" } } ] } }`

#### `ai/transcript@1`
Origin: new · Needs: L1 (`each`) · Wave: LB3 · Size: 1

A conversation as turns: who spoke, when, what was said; the latest at the foot.
- **Parameters:** `rows: table{at: datetime, role: text, text: text}` (required) · `limit: integer = 50`
- **Data:** `rows`; `live: ok`, appending · **Slots:** `under-turn` (panels, max 2; exposes `turn: record`) · **Events:** none
- **Use:** `{ use: ai/transcript@1, with: { rows: $.turns } }`

#### `ai/suggestion-chips@1`
Origin: new · Needs: I1 · Wave: LB3 · Size: 0.5

Follow-up questions or next steps as a row of buttons; choosing one is an event.
- **Parameters:** `items: list<text>` (required; at most 6)
- **Data:** none · **Slots:** none · **Events:** `choose {text: text}`
- **Use:** `{ use: ai/suggestion-chips@1, with: { items: "$.assistant.suggestions" }, on: { choose: [ { host: assistant.ask, with: { text: "event.text" } } ] } }`

#### `ai/generated-frame@1`
Origin: new · Needs: baseline · Wave: LB2 · Size: 0.5

A frame around panels a model wrote: marked as generated, with the question, the time, and the tables it read.
- **Parameters:** `question: text` (required) · `produced: datetime? = none` · `tables: list<text>? = none` · `generated-label: message = generated`
- **Data:** none · **Slots:** `body` (panels, max 20) · **Events:** `keep {}`, `discard {}`
- **Use:** `{ use: ai/generated-frame@1, with: { question: "$.request.text", tables: [sales, targets] }, slots: { body: [ ... ] } }`

#### `ai/data-answer@1`
Origin: new · Needs: baseline · Wave: LB2 · Size: 1

The answer to a question about a table, in three parts chosen by its shape: a headline figure, a chart (a line when there is a date, bars otherwise), and the rows.
- **Parameters:** `rows: table{label: any, value: number}` (required) · `question: text? = none` · `shape: enum[auto, trend, ranking, breakdown] = auto` · `fmt: format = amount0` · `columns: columns = []`
- **Data:** `rows` (the result the host's query tool returned) · **Slots:** none · **Events:** `pick {label: any}`
- **Use:** `{ use: ai/data-answer@1, with: { rows: { from: $.result, columns: { label: month, value: revenue } }, question: "Revenue by month" } }`

#### `ai/feedback-bar@1`
Origin: new · Needs: I1 · Wave: LB3 · Size: 0.5

Was this right: two buttons and an optional line of words; the verdict is an event.
- **Parameters:** `subject: text` (required; an id of what is being rated) · `ask-why: enum[never, down, always] = down`
- **Data:** none · **Slots:** none · **Events:** `rate {subject: text, verdict: text, why: text?}`
- **Use:** `{ use: ai/feedback-bar@1, with: { subject: "$.answer.id" }, on: { rate: [ { host: assistant.rate, with: { id: "event.subject", verdict: "event.verdict" } } ] } }`

#### `ai/explanation-tree@1`
Origin: new · Needs: X44 (explanation) · Wave: LB3 · Size: 1

How a figure was computed, as a tree: each step with its expression, its inputs and its value.
- **Parameters:** `rows: table{id: text, parent: text, label: text, value?: any, source?: text}` (required) · `open-levels: integer = 2`
- **Data:** `rows` (the explanation tree of X44, as rows) · **Slots:** none · **Events:** `select {id: text}`
- **Use:** `{ use: ai/explanation-tree@1, title: How this was computed, with: { rows: $.explanation } }`

#### `ai/tool-log@1`
Origin: new · Needs: baseline · Wave: LB2 · Size: 0.5

The tools an assistant called for an answer: tool, arguments in brief, status, duration.
- **Parameters:** `rows: table{at: datetime, tool: text, status: text, duration?: number, summary?: text}` (required)
- **Data:** `rows`; `live: ok` · **Slots:** none · **Events:** `open {row: record}`
- **Use:** `{ use: ai/tool-log@1, with: { rows: $.calls } }`

#### `ai/eval-scorecard@1`
Origin: new · Needs: baseline · Wave: LB2 · Size: 1

An evaluation run: first-try validity, validity within three attempts, tokens, by task family, against the targets.
- **Parameters:** `rows: table{family: text, tasks: integer, first-try: number, within-three: number, tokens?: number}` (required) · `first-try-target: number = 0.85` · `within-three-target: number = 0.99`
- **Data:** `rows` · **Slots:** none · **Events:** `family {family: text}`
- **Use:** `{ use: ai/eval-scorecard@1, with: { rows: $.eval } }`

## 10. What the catalogue asks of the expansion

Writing the fragments down shows what they need that is not yet in [SUTRA_EXPANSION](SUTRA_EXPANSION.md) or
[EL_EXPANSION](EL_EXPANSION.md). Each is a proposal for those documents, not a decision of this one (L-15).

| # | Ask | Needed by | Proposal |
|---|---|---|---|
| **K1** | A stated budget for the `point` mark of the chart grammar (V1): 200,000 points drawn on canvas, with a defined thinning rule above it | `genomics/manhattan-plot`, `core/scatter-plot` at scale | part of V1's performance budget; the thinning rule is in the specification so every painter drops the same points |
| **K2** | A `rect` (cell) mark with an optional glyph in V1 | `genomics/cohort-matrix` (alteration glyphs), dense heat grids that today go through `pivot` | one more mark in V1's closed list; `pivot` with `heat` stays as it is |
| **K3** | A panel kind `tracks`: stacked tracks on one coordinate axis, pan and zoom, each track read for the region shown | `genomics/region-browser` | a Core kind in V4's third batch, drawn by Drishti Elements' chart library; the region is state, the reads are windowed (D2) |
| **K4** | A panel kind `structure`: a molecular 3D view (chains, residues, colour by value, highlight) | `genomics/structure-panel` | a kind registered by a **`life-science` painter module** of Drishti Elements with a vendored viewer, exposed through a Core kind that an older painter refuses (SUTRA_EXPANSION 6.3); the structure is a host handle, never an address |
| **K5** | A panel kind `sequence`: a residue or base sequence in fixed-width rows with rulers and bands | `genomics/sequence-view` | a small Core kind (text grid); ignore-safe fallback is the plain sequence |
| **K6** | Slots named by a parameter (one slot per tab id, per wizard step) | `core/tab-set`, `core/wizard-shell` | in C1: `slots: { each: tabs, by: id, accepts: panels }`, checked at the use against the list given; the list must be a literal |
| **K7** | Distinct values of a column as a provider capability, so a filter over a windowed table can list its options | `core/filter-bar` over `window: ok` tables | a Data Protocol query form (`distinct`, with a limit) if section 5 of the protocol does not already give it; otherwise a note there |
| **K8** | A `message` parameter type and a library message table before A2 (languages) is complete | every fragment that prints a text of its own | the table and `%{message}` lookup come with the framework (LB0); plural rules and locales arrive with A2 and X32 |

Everything else the catalogue needs is already an item of the two expansion documents. The items needed most often,
counted over the 209 fragments: L1 containers (16), I1 actions (13), V1 the chart grammar (13), S1 state (10), V4
further kinds (9), V5 map layers (5), D2 windowed tables (4), V2 cell visuals (4, and 3 baseline fragments gain
from it), V3 grid depth (3), X26 statistics (3); L2 overlays, I2 selections, I3 bulk actions, F1 and F2 forms and
D6 streaming detail (2 each). 136 fragments need only the baseline.

## 11. Counts, waves and sizes

Computed from the entries above (a script over the `Origin`, `Wave` and `Size` lines; the generated library index
replaces it once libraries exist).

### 11.1 Fragments per library and wave

| Library | LB1 baseline | LB2 baseline | LB3 with R2's structure | LB4 chart grammar, scale, later kinds | Fragments | Fragment days |
|---|---|---|---|---|---|---|
| `core` | 31 | | 24 | 12 | **67** | 53.5 |
| `finance` | | 47 | 3 | 7 | **57** | 41.5 |
| `genomics` | | 20 | | 8 | **28** | 22 |
| `economy` | | 16 | | 4 | **20** | 14 |
| `geo` | | 2 | 2 | 4 | **8** | 6 |
| `ops` | | 13 | 2 | 3 | **18** | 11.5 |
| `ai` | | 7 | 4 | | **11** | 7.5 |
| **Total** | **31** (19.5 days) | **105** (64.5 days) | **35** (31.5 days) | **38** (40.5 days) | **209** | **156** |

### 11.2 Where they come from

| Origin | `core` | `finance` | `genomics` | `economy` | `geo` | `ops` | `ai` | Total |
|---|---|---|---|---|---|---|---|---|
| kit (a Drishti kit fragment re-published) | 9 | 5 | | 3 | 1 | | | **18** |
| pack (a Drishti pack fragment, generic shape extracted) | 3 | 10 | | 1 | | | | **14** |
| pattern (repeated in Drishti's Sutras, to be extracted) | 22 | 29 | 12 | 8 | | | | **71** |
| new | 33 | 13 | 16 | 8 | 7 | 18 | 11 | **106** |

So 103 of the 209 already exist in Drishti in some form: the first customer's screens are the evidence that these
are the shapes people need, and its goldens are the proof data for the extraction (LIBRARIES 21).

### 11.3 Days

| Item | Days |
|---|---|
| the 209 fragments, each with its document, examples, tests and goldens | 156 |
| per library: the contract files, seeded generators for the synthetic samples, the message table, the library page and a review of the gallery (`core` 3, `finance` 6, `genomics` 4, `economy` 3, `geo` 1, `ops` 2, `ai` 1) | 20 |
| **The libraries** | **176** |
| the framework LB0 (LIBRARIES 23), beside C1, X42 and T1 which are already planned | 54 |
| the AI kit for libraries and Studio's palette (LB2) | 18 |
| distribution (LB5) | 6 |
| **Everything in these two documents** | **254** |

By wave, libraries only: LB1 22.5 days (with `core`'s overhead), LB2 81.5, LB3 31.5, LB4 40.5. With three builders
LB1 is about two weeks after the framework; LB2 about six weeks; LB3 and LB4 follow the expansion items they need.
The sizes assume the fragment is small because the panel kinds do the work; a fragment that turns out to need a
painter change is a finding against the expansion, recorded in section 10, not a bigger fragment.

### 11.4 Order inside a wave

1. A library's **contracts and samples** first: every fragment's examples and tests read them.
2. Then the fragments other fragments are built from (`core/key-figure`, `series-line`, `ranked-bars`,
   `data-table`, `field-list`, `event-timeline`, `donut-breakdown`): `finance`, `genomics` and `economy` use them
   as composites and variants rather than repeating panels.
3. Then by evidence: `kit` and `pack` origins before `pattern` before `new`, because the first two come with a
   screen that proves them.
4. A fragment marked LB3 or LB4 whose baseline form is useful ships that form first as `@1` only when the later
   form can be reached by **adding** optional parameters; otherwise it waits (a frozen major must not be spent on
   a stopgap).

## 12. Shared table contracts

The contracts named in the entries, one file each under the library's `contracts/` (LIBRARIES 5.2). A host that
serves these tables, under any names, lights up the fragments listed.

| Library | Contract | Grain | Read by |
|---|---|---|---|
| `finance` | `price-bars` | one row per instrument and period | `price-bars`, `price-history` |
| | `curve-points` | one row per curve and pillar | `curve-table`, `curve-overview` |
| | `vol-points` | one row per surface, expiry and strike | `vol-surface` |
| | `factor-scenarios` | one row per risk factor and scenario date | `fx-scenario-chart` |
| | `trades` | one row per trade | `trade-blotter` |
| | `trade-legs` | one row per trade and leg | `leg-table` |
| | `cashflows` | one row per payment | `payment-schedule`, `cashflow-ladder`, `settlement-figures` |
| | `positions` | one row per book and instrument | `position-table` |
| | `daily-pnl` | one row per scope and day | `pnl-history` |
| | `pnl-explain` | one row per scope, day and effect | `pnl-waterfall` |
| | `scenario-pnl` | one row per scope and scenario day | `var-histogram`, `var-path` |
| | `risk-contributions` | one row per scope and contributor | `var-contributors` |
| | `backtest` | one row per scope and day: P&L and the VaR for that day | `backtest-traffic-light`, `backtest-chart` |
| | `risk-hierarchy` | one row per node, with its parent and its figures | `risk-hierarchy-tree` |
| | `stress-results` | one row per scenario and group | `scenario-matrix`, `stress-table` |
| | `sensitivities` | one row per scope, factor and bucket | `greeks-ladder`, `sensitivity-heatmap`, `what-if-shocks` |
| | `limit-utilisation` | one row per limit | `limit-table` |
| | `exposure-profile` | one row per netting set or counterparty and future date | `exposure-profile` |
| | `netting-hierarchy` | one row per node (group, counterparty, netting set, trade) | `netting-set-tree` |
| | `collateral-holdings` | one row per agreement and asset | `collateral-ledger` |
| | `margin-calls` | one row per call | `margin-call-ladder` |
| | `holdings` | one row per portfolio (or security) and holding (or holder) | `holdings-table` |
| `genomics` | `variants` | one row per variant (and sample, when genotypes are given) | `variant-table` |
| | `variant-positions` | one row per position along a gene or protein | `variant-lollipop` |
| | `association-results` | one row per variant tested | `manhattan-plot`, `qq-plot`, `hit-table` |
| | `alterations` | one row per gene and sample altered | `cohort-matrix` |
| | `segments` | one row per sample and segment | `copy-number-track` |
| | `mutation-scores` | one row per position and substitution | `mutation-scan-heatmap` |
| | `designs` | one row per design | `design-leaderboard` |
| `economy` | `observations` | one row per country, indicator and period | `country-compare`, `indicator-series`, `indicator-compare` |
| | `forecasts` | one row per country, indicator, year and vintage | `forecast-table`, `forecast-fan` |
| `ops` | `job-runs` | one row per run | `job-runs` |
| | `table-status` | one row per table | `freshness-table` |
| | `check-results` | one row per check and run | `quality-scorecard` |

34 contracts. Fragments with an inline table shape (most of `core`, by design: generic fragments take any table)
have no shared contract. Every sample for these contracts is synthetic, generated from a seed, and carries no real
instrument, counterparty, person, patient or publisher.

## 13. Libraries considered and not proposed

| Library | Why not now | What would change that |
|---|---|---|
| `energy`, `commodities` | Needs units of measure (X9, later) to be right; the generic shapes are already in `core` and `finance` | a customer in that field, and X9 |
| `healthcare` (patients, encounters) | Personal data rules differ by jurisdiction and belong in a host's profile; the fragments would be `core` with other words | a customer with a governed host |
| `retail`, `sales`, `crm` | The largest general market and the best demonstrations, but every fragment would be a `core` fragment with fixed labels | once `core` is out: a small `business` library of **variants** (sales funnel, cohort retention, customer card) is cheap and makes the gallery speak to analysts; proposed as the eighth library after LB2 if the owner wants it (L-14) |
| `quant` (pricing tools) | Rachana shows supplied numbers; a pricing library invites the belief that it values things. EL_EXPANSION keeps option formulas off by default | never as fragments; functions only, as X29 says |
| `report` (printed pages) | Needs L6 canvas and L7 print rules (P3) | those items |
| a community library index | Signing, review and naming for third parties is a product of its own | after R3, with the public registries |
