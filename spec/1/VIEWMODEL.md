<!-- Copyright (c) 2026 Ashutosh Sinha. Specification licence: ../../LICENSE-SPEC.md (draft, not yet in force). -->
# The ViewModel, contract 1.x

**Status: draft 1 (2026-11-17).** The machine form is [viewmodel.schema.json](viewmodel.schema.json) (JSON Schema draft 2020-12,
55 definitions). **It was transcribed by reading the reference implementation's Java records and is replaced by the generated
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
| no `a11y`, `datasets`, `formats`, `live`, `layout.narrow` | present in the design | not in contract 1; additions would come as 1.x minors or contract 2 |

Other things the transcription found:

- `ControlView.kind` and `.placement` are free strings in the schema (the reference does not enumerate them); the
  documented words are in each field's description.
- `PanelView.kind` lists 22 kinds, the same set the reference's `DATA_BY_KIND` maps, so the schema and the grammar agree.
- `Chart.step` and `PivotRow.group` are boxed booleans in Java (optional, never null); `ViewModel.layout`, `phone`,
  `PanelView.form`, `PanelCompute.progress`/`stamp` and `Table.pivot` are open objects whose inner shape is described in prose
  in the reference's VIEWMODEL.md, not as records.
- The schema's `$id` is this specification's address, `https://ajsinha.github.io/drishti-rachana/spec/1/viewmodel.schema.json`
  (the reference's was a host address; it changed at split step A13, when the generator moved here).
