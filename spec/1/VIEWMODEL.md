<!-- Copyright (c) 2026 Ashutosh Sinha. Specification licence: ../../LICENSE-SPEC.md (draft, not yet in force). -->
# The ViewModel, contract 1.x

**Status: draft 1 (2026-11-17).** The machine form is [viewmodel.schema.json](viewmodel.schema.json) (JSON Schema draft 2020-12,
68 definitions; contract 1.1). **It was transcribed by reading the reference implementation's Java records and is replaced by the generated
schema in R0 step H4**, where it is compared with the reference's own `ViewModelSchema.build()`: equal, or the difference is
fixed. Until then, where this text and the schema disagree the schema is the one to correct against the records.

## What the contract is

The ViewModel is the JSON a runtime produces from a Sutra and its data (see [TRANSLATION.md](TRANSLATION.md)): everything a
client draws, nothing it must compute. Every value is already formatted text plus a tone, so all clients show numbers
identically. It is one document per screen, and a stream of `Frame`s of patches that keep it current.

Decision N3: there is **one** ViewModel contract, and its home is this folder. Version 1 keeps the reference
implementation's field names exactly (including `fmt`, `ev`, `emptyLabel`, `title.pill`, `ref`, `mnemonic`, `provenance`);
a painter draws them as they are. The contract version is carried in the document: `"contract": "1.0"` (major.minor). The
minor moves for additions (a field, a kind, a tone); the major for a break. A client refuses a major it does not know and
ignores what a newer minor adds.

### Shape

- Root: `ViewModel`. Required: `ref`, `title`, `strip`, `panels`, `keys`, `provenance`, `timings`, `contract`. Optional:
  `mnemonic`, `controls`, `selects`, `layout`, `phone`.
- `panels[]` are `PanelView`s (`id`, `kind` required). `kind` is one of the 22 kinds; the kind decides which record `data`
  carries (`allOf` of `if/then` pairs): `kv`, `status`, `provenance` → `Fields`; `table`, `ladder` → `Table`; `line`, `area` →
  `Chart`; `hbar` → `Bars`; and one record each for `tabs`, `links`, `markdown`, `gauge`, `surface`, `waterfall`,
  `histogram`, `scatter`, `candlestick`, `graph`, `timeline`, `pivot`, `metric`, `map`. `data` is null when the panel failed
  (`error`) or is denied (`denied`).
- Live: `Frame` (`seq`, `generation`, `patches`, `latencyMs`, `p99Ms`); a `Patch` has `op` (`strip`, `panel`, `provenance`,
  `deleted`, `restored`) and the field that op names (`index` + `cell`, `panel`, `provenance`, `at`).
  A frame carries a patch only for what differs from the view before it; when nothing differs there is no frame. `seq` counts a
  view's frames from `1`, by one per frame. `generation` (the source's generation), `latencyMs` and `p99Ms` (the host's measured
  delay from the change to the frame, the last one and the 99th percentile) are **the host's facts**: a runtime with no host
  writes `0` for all three (FORMAT 5.9), and a painter never reads them to decide what to draw.
- Tones: `pos`, `neg`, `link`, `accent`, `ok`, `warn`, `bad`, `muted`, `inactive` (`Tone`). `Series.tone` is the author's
  own word and stays free text.

### Optional, null and closed

Each definition names its Java record (`x-java`) and its Jackson inclusion (`x-include`):

- `ALWAYS` records write every component: all are required, and a reference-typed one may be `null`.
- `NON_NULL`, `NON_DEFAULT` and `NON_EMPTY` records (and components annotated so) leave a component out when it is null,
  default (a `false` flag, a `0`) or empty; such a component is optional and **never null**.
- Numbers may be `null`: the server writes NaN and the infinities as null.
- Definitions are closed (`additionalProperties: false`), as the reference's schema is. This is a statement about what a
  producer may write. **A consumer must ignore unknown fields**: a validator for a live runtime of a newer minor should use
  that runtime's own schema, not an older copy.

## Contract 1.1

Appended to 1.0 (SUTRA_EXPANSION 6.3): every addition is optional, comes after the fields of its record, and is **left out
when unused**, so a document that uses none of it is byte for byte a 1.0 document. `contract` is the **smallest minor that
describes the document**: `"1.1"` only when it has `datasets`, a panel of kind `grid`, `tree` or `chart` (nested in tabs
too) or a map with `points`; otherwise `"1.0"`. The released 1.0 schema is kept in
[released/viewmodel-1.0.schema.json](released/viewmodel-1.0.schema.json) and `ContractAppendOnlyTest` (CG4) proves this
schema contains it. New definitions carry `x-since: "1.1"`.

| Addition | Where | What |
|---|---|---|
| `datasets` | ViewModel, after `phone` | `{name: Dataset}`: rows that `grid`, `tree` and `chart` panels read; two panels may read one dataset and a `rows` patch reaches both |
| `Dataset` | new | `key` (fields), `fields` (`DataField`: `name`, `type` `string`/`number`/`boolean`/`date`/`datetime`, `label`, `fmt`), and exactly one source: `values` (rows as lists in the order of `fields`, what Rachana writes), `rows` (objects), `name` (rows the page hands the painter), `arrow` (`{url}` or `{base64}`), `pages` (`{url, size, mode offset/cursor, total, sort, filter}`, `sort`/`filter` `server` to push them to the server), `server` (`{url}`, a server-side row model); `more`: rows the producer left out |
| raw values | Dataset only | Unlike every other part of the document a dataset's values are **raw** (numbers, text, booleans, null) and formatted by the painter with the field's `fmt`; a value the reader may not see is `{"$masked": true}` (Data Protocol 3.2) |
| kind `grid` | PanelView | data `Grid`: `dataset`, `columns` (`GridColumn`: `field`, `label`, `fmt`, `tone` (`sign` or a tone word), `width`, `pinned` left/right, `agg`, `hidden`), `group`, `sort` (`SortKey`: `field`, `desc`), `pivot` (`GridPivot`: `rows`, `columns`, `values` of `Measure`), `search`, `emptyLabel` |
| kind `tree` | PanelView | data `Tree`: `dataset`, `node`, `parent` or `path`, `label`, `measures` (`Measure`), `mode` grid/org/sunburst/icicle, `open` |
| kind `chart` | PanelView | data `DataChart`: `dataset`, `type` bar/line/area/scatter/pie/donut/heatmap/histogram, `x`, `y` (`Measure`: `field`, `agg`, `label`, `fmt`), `by`, `stack` sum/percent, `horizontal`, `xLabel`, `yLabel`, `bins`, `limit` |
| `Measure.agg`, `GridColumn.agg` | | `sum`, `count`, `min`, `max`, `avg`, `first`, `last`, `distinct`: the painter combines the rows (per `x` and `by` for a chart) |
| `GeoMap.points` | map data, after `unit` | `GeoPoint`: `label`, `lat`, `lon` (always written), `value` (sizes the bubble), `text`, `tone`, `link`: places drawn as bubbles over the map |
| op `rows` | Patch | `dataset`, then `reset` (every row), `remove` (keys; a compound key joined by U+001F), `update` (merged by key; an unknown key added), `add` (appended; an existing key updated), applied in that order |

Rachana writes `datasets` only when a Sutra of language level 1.1 has a `grid`, `tree` or `chart` panel (SPEC 2.3); it
never writes `rows`, `name`, `arrow`, `pages`, `server` or map `points` itself: those are for hosts and other producers.
Drishti's screens, at level 1.0, stay byte-identical.

## Contract 1.2

Appended to 1.1 by the same rules (optional, after the fields of its record, left out when unused; new definitions carry
`x-since: "1.2"`). `contract` is `"1.2"` only when the document has `state`, a mark's `ev` (a pivot cell's and a surface point's too), a `live`, `treemap` or `bullet` panel, a column `bar` or a series' `mark`, `axis` or `ev` of the table below; otherwise as for 1.1. A painter that knows only
1.0 or 1.1 ignores what 1.2 adds (decision S1-OPEN b): the screen still draws, and a click simply writes no state.

| Addition | Where | What |
|---|---|---|
| `state` | ViewModel, after `datasets` | the screen's state values (SUTRA_EXPANSION S1), in declared order, a fragment use's private state as `<as>.<name>` |
| `StateView` | new | `id`; `value` (text, a number, `true`/`false`, an ISO date text, a list of text, or `null`: always written); `url` (the host keeps it in the address; `false` until the view-state document, S4); `reads` (the panels, `strip` or `title` whose expressions read it, directly or through a `let`: only those are rebuilt when it changes) |
| `ev` | `Point`, `Step`, `Event`, `Candle` (after their fields); `Chart.ev` (a list beside `x`); `Metric`, `Gauge`, `Fields` | on a panel that emits on `point-click` (I1): a mark's row in the panel's data (before `where`), sent back by a click as a row's `ev` is; `"0"` on a figure or a record, the panel one mark (its payload reads the panel's context, no row); absent otherwise |
| `Series.mark`, `axis`, `ev` | line and area series, after `tone` | a line panel's `layers` or `by` (level 1.2): `mark` `bar`, `area` or `point` (absent: a line), `axis` `right` (absent: the left), `ev` beside `x` each point's row in the data (null where the series has no point) on a split line that emits on point-click |
| `Cell.ev`, `set`, `on` | a pivot's cells, after `path` | on a pivot that emits on `point-click`: a cell with a figure is a mark, `ev` its place among the cells row by row (`row * columns + column`; an empty cell keeps its place and carries nothing), `set` and `on` as a point's, `@` the cell `{row, path?, column, value}` |
| `Surface.ev`, `set`, `on` | after `view`, lists of lists beside `z` | on a surface that emits on `point-click`: each point's place row by row (`row * x + column`, `null` where there is no number), what a click writes, its whole effect list; `@` the point `{x, y, z}` |
| kinds `treemap`, `bullet` | PanelView | data `Treemap` (`cells`: `TreemapCell` `label`, `group`, `value`, `text`, `tone`, `ev`, `set`; `total`) and `Bullet` (`value`, `text`, `label`, `min`, `max`, `bands`: `Band` `from`, `to`, `tone`; `lower`, `lowerText`, `upper`, `upperText`, `tone`, `caption`) |
| kind `live` | PanelView | data `Grid` (as `grid`) over a dataset whose source is `live`; drawn by reading and following the table, never from `values` |
| `Dataset.live` | Dataset, after `server` | `LiveRef`: `table`, `window` (rows read and followed at a time), `top` (new rows of a newest-first order inserted on top), `filter` (a filter tree, PROTOCOL 7): the painter asks the page's data client for the first window in the panel's `sort`, subscribes with its keys and applies the patches; a gap reads the page again; `edit` (after `filter`): `cells` or `beside` when the panel offers the table's guarded edit path (PROTOCOL 16 Writes): the painter offers, for the reader, the columns the table's schema lists under `write` (with their rules) and sends each change as a protocol `write` with a client-made change id; absent: read only |
| `GridColumn.bar` | grid and live columns, after `hidden` | `true`: each number drawn with a bar in its cell against the column's largest; the text still says the value |
| `Row.set`, `Bar.set` | table and ladder rows (a tree's and a drill table's children too, each evaluated for its own node), hbar bars, after `ev` | what a click writes into the state (I1 `set: { state, to }`): state id to the value, already evaluated for that row or bar and in the state's type; absent when the panel's `on:` writes no state |
| `set` (point-click) | `Point`, `Step`, `Event`, `Candle`, `Metric`, `Gauge`, `Fields` after `ev`; `Chart.set` (a list beside `x`, `null` for a point whose effects failed) | what a `point-click` on the mark writes into the state (I1 `on: { point-click: [ { set: { state, to } } ] }`), evaluated as `Row.set` is: `@` the mark's row, `#index` its place before `where`; a figure or a record in the panel's context; absent when the panel's `on: point-click` writes no state |
| `on`, `EffectView` | after `set` on every mark that has one; `Chart.on` beside `x` | a mark's whole effect list when it holds more than plain state writes (I1: a `when`, `toggle`, `clear`, `notify`, `host`): trigger to `[EffectView]`, each evaluated for the mark (`{do: set, state, value}`, `{do: toggle, state}`, `{do: clear, state}`, `{do: notify, text, tone?}`, `{do: host, action, with?}`), an effect whose `when` is false left out; the mark then has no `set`. The painter runs `set`, `toggle` and `clear` on the state (a cleared state is no longer sent back: its default stands) and hands `notify` and `host` to the host, which decides; Rachana never runs a host action |
| `form` panels, `FormPanel` | `PanelView.kind` gains `form`; its `data` | a standalone form (SPEC 2.8 item 5): `values` (field id to starting value, `null` when none: always written), `submitLabel?`, `cancelLabel?`, `on` (`submit`, `cancel` to `[EffectView]`). The fields, rules and hints are the panel's `form`, as for a table's form. The painter checks the values as it does a table's form, hands them to the host (`ScreenWire.submit` checks them again, then runs the effects) and applies the state writes that come back |
| `EffectView.event`, `EffectView.withEvent` | after `with` | an effect of `submit` or `cancel` that reads the sent values: `event` the field (dotted inside a value; empty for every value) whose value a `set` writes, `withEvent` a `host`'s arguments filled from fields; both filled in when the form is sent |
| `PanelView.actions`, `ActionView` | after `compute` | the buttons of the panel's header (SPEC 2.8 item 6): `{id, label, tone?, confirm?, requires?, effects: [EffectView]}`, the label and question rendered, the effects evaluated over the panel's context. The painter asks first when `confirm` is set, asks the host about `requires`, then runs the effects as a mark's |
| `ControlView.on` | after `truncated` | a control's effect lists (SPEC 2.8 item 7): `change` to `[EffectView]`, evaluated over the screen; an effect that reads the event (`event: ""`, `withEvent`) takes the new value. The painter sends the new value as `ctl.<id>` as always, runs the effects as a mark's and sends the state they write with the same bind (`ScreenWire.change` runs them for a host) |

The painter keeps the values, applies a clicked mark's `set` (a row, a bar, a point, a step, an event, a candle, a figure or a record) (then rebuilds the parts its states' `reads` name), and
sends every value back with the next bind as a request parameter `state.<id>=<text>` (a list's items joined by commas;
`StateValues`), exactly as it sends `ctl.<id>`. A value that does not fit its state's type is ignored there (the default
stands). `set: <control>` in `on:` is written as today's `selects` entry and row `pick` (one mechanism).

## Differences found (for N3)

Between the Java records (the contract-1.0 truth, now this schema) and the Views design (VIEWS_DESIGN, the `design/views`
branch of Elements) field names and shapes:

| Java record / schema (contract 1.x) | Views design | Resolution under N3 |
|---|---|---|
| `contract: "1.0"` | `spec: "1.0"` | contract 1.x keeps `contract`; the painter reads it |
| `fmt` (Surface, Histogram, Candles) | `format` | stays `fmt` in 1; rename is contract 2 |
| `ev` (Row, Bar) | `key` | stays `ev` in 1 |
| `Table.emptyLabel` | `emptyText` | stays `emptyLabel` in 1 |
| `title {pill, id, with}` | `title {tag, text, subtitle, link}` | stays; the painter draws `pill`, `id`, `with` |
| `ref {kind, id}`, `mnemonic`, `provenance.sutra`, `fingerprint`, `generation`, `timings` (top level) | all under `meta` | Core emits host facts under `meta.<profile>`; Drishti's contract writer maps them to these top-level fields (C17); contract 2 (parked) retires the writer |
| `provenance` (`fetchedAt`, `updatedAt`, `staleAfter`, `stale`, `partial`, `missing`, `masked`, `maskedPanels`) | `status {updatedAt, source, stale, partial, notes}` | stays `provenance` in 1 |
| panel `empty`, `error`, `denied`, `wire.waits`, `compute.state` | one `state.is` | stays separate fields in 1 |
| `Link {kind, id, mnemonic}` | `{ref: {kind, id, mnemonic}}` | stays |
| `keys[].action` words `panel`, `link`, `raw`, `impact`, `denied` | actions the adapter handles | the record's own javadoc lists four; `denied` is in the generator's enum and is kept |
| frames: ops `strip`, `panel`, `provenance`, `deleted`, `restored` | `cell` by index, `panel`, `status`, `frozen`, `thawed` | stays; the stream's vocabulary is separate from the document's |
| streamed `skeleton`, `panel-add`, `panel-replace`, `patch`, `done`, `error` | `view` with pending panels, `panel-add`, `panel`, `done`, `state` | not part of this schema (transport, not document) |
| Cell `emphasis` written only when `true` | Cell, unchanged | same |
| `Frame` has no `contract` stamp | `spec` on frames | not stamped in 1 |
| no `a11y`, `formats`, `live`, `layout.narrow` | present in the design | not in contract 1; additions would come as 1.x minors or contract 2. `datasets` and the kinds `grid`, `tree`, `chart` came in 1.1 (above), with fields `values` (columnar), `fmt` and `key` as lists, and the design's derived reads (`where`, `groupBy`) left to the producer |

Other things the transcription found:

- `ControlView.kind` and `.placement` are free strings in the schema (the reference does not enumerate them); the
  documented words are in each field's description.
- `PanelView.kind` lists 22 kinds, the same set the reference's `DATA_BY_KIND` maps, so the schema and the grammar agree.
- `Chart.step` and `PivotRow.group` are boxed booleans in Java (optional, never null); `ViewModel.layout`, `phone`,
  `PanelView.form`, `PanelCompute.progress`/`stamp` and `Table.pivot` are open objects whose inner shape is described in prose
  in the reference's VIEWMODEL.md, not as records.
- The schema's `$id` is this specification's address, `https://ajsinha.github.io/drishti-rachana/spec/1/viewmodel.schema.json`
  (the reference's was a host address; it changed at split step A13, when the generator moved here).
