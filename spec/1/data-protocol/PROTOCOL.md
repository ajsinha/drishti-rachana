<!-- Copyright (c) 2026 Ashutosh Sinha. Specification licence: ../../../LICENSE-SPEC.md (draft, not yet in force). -->
# The Rachana Data Protocol, version 1

**Status: draft 1 (2026-11-17), front-load item F8.** Normative once the owner approves it. The design it is made from is
[../../../docs/design/DATA_PROTOCOL.md](../../../docs/design/DATA_PROTOCOL.md); where this text and the design differ, this text
wins. The words MUST, MUST NOT, SHOULD and MAY are used as in RFC 2119. **Section 8.2 follows the events wire protocol v1
(`EVENTING_DESIGN.md` section 8 of Drishti Elements, branch `design/eventing`); it is re-checked when that protocol ships.**

The machine-readable forms are JSON Schemas (draft 2020-12) in this folder: [schema.json](schema.json) (table schema, base
document, catalogue), [page.json](page.json) (a page of rows), [filter.json](filter.json) (filter tree and query document),
[patch.json](patch.json) (keyed patch and stream frames), [error.json](error.json), [bridge.json](bridge.json). Where text and
schema disagree, that is a defect in one of them. Executable examples: `conformance/1/protocol/` (section 15).

## 1. Scope

The protocol is how a Rachana runtime gets **tables** from a **host** and hears about **changes** to them. It is not a query
language, not an authentication scheme and not a transport for ViewModels. Three transports carry the same messages: HTTP
(sections 4 to 7), a stream for changes (section 8) and an in-process bridge (section 12). A host MUST offer at least
`GET {base}/tables/{name}` and `GET {base}/tables/{name}/rows` (section 4); everything else is declared in `can` and optional.

## 2. Tables and schema

A **table** has a name, a schema, an optional key and capabilities. The **name** is letters, digits, `_` and `-`, with `.` as a
separator, at most 128 characters. The schema document ([schema.json](schema.json)):

| Field | Required | Meaning |
|---|---|---|
| `name` | yes | the table name |
| `columns` | yes | list of columns, at most 2,000 |
| `title` | | words for people |
| `key` | | column names, in key order; unique per row; required for live tables (section 8) |
| `rows` | | the row count, when cheap |
| `can` | | capabilities (below) |
| `version` | | `{ epoch, seq }`: the position in the change stream the schema was read at |
| `limits` | | this table's limits (section 13) |
| `append` | | `{ window }`: an append-only table (section 8.6) |

A **column**: `name` and `type` (required); `nullable` (default true), `label`, `fmt` (a Rachana format name), `unit`, `scale`
(decimal), `grain` (date: `day`, `month`, `quarter`, `year`), `items` (list: the element type), `hint`, `masked` (the whole
column is masked for this reader). A `list` column MUST give `items`.

`can` values: `page` (cursor paging), `offset`, `sort`, `filter`, `group` (aggregation push-down), `count` (an exact `total` is
cheap), `snapshot` (consistent paging and `asOf`), `arrow`, `arrow.lz4`, `changes`, `changes.filter`, `replay`, and for a live table
(section 16) `changes.window`, `write` and `narrow`. A reader MUST NOT
use a capability the table does not declare; a table with none is read whole.

## 3. Types and the JSON encoding of values

### 3.1 Types

| Type | JSON form | Arrow type | Rule |
|---|---|---|---|
| `string` | string | Utf8, LargeUtf8 or Dictionary(Utf8) | |
| `integer` | number within ±2^53, otherwise a string of digits | Int8..Int64, UInt8..UInt64 | a reader MUST accept both forms; a writer MUST use a string outside ±2^53 |
| `decimal` | string (`"1250.50"`) or number | Decimal128/256 | exact; `scale` says the digits after the point; a writer SHOULD send a string; a reader of a number accepts double precision |
| `number` | number | Float32/64 | `NaN` and infinities MUST be sent as `null` |
| `boolean` | `true`, `false` | Bool | |
| `date` | `"2026-11-17"` | Date32 | ISO 8601 calendar date |
| `timestamp` | `"2026-11-17T09:30:00.000Z"` | Timestamp(ms or µs, zone) | MUST carry `Z` or an offset |
| `time` | `"09:30:00"` | Time32/64 | |
| `list` | array of the `items` type | List | |
| `object` | object | Struct, or Utf8 holding JSON with field metadata `rachana.type=object` | read with dotted paths |

A null is `null` in JSON and an invalid slot in Arrow, for every type. A cell whose column is not nullable MUST NOT be null.

### 3.2 Masked values

A value the reader may not see is, in JSON, `{ "$masked": true }` (in any column of any type), and in Arrow a null slot plus a
`<name>$masked` boolean column (true where masked). Masked is distinct from null. A column with `masked: true` in the schema is
masked in every row. A host MUST apply masks before values leave it (section 11).

## 4. HTTP resources

All under a base address `{base}` the host chooses. A host SHOULD answer with `Cache-Control: no-store` unless it says otherwise.

| Method and path | Returns |
|---|---|
| `GET {base}` | the base document: `{ "protocol": "rdp", "v": 1, "tables": "{base}/tables", "changes": "{base}/changes", "limits": {…} }` (`schema.json#/$defs/base`) |
| `GET {base}/tables` | `{ "tables": [ { name, title?, key?, rows?, can? } … ] }`, only the tables **this caller** may read (`schema.json#/$defs/catalogue`) |
| `GET {base}/tables/{name}` | the schema document (section 2) |
| `GET {base}/tables/{name}/rows` | rows (section 5); query parameters below |
| `POST {base}/tables/{name}/query` | rows for a query document (section 6) |
| `GET` or `POST {base}/changes` | the minimal change stream (section 8.2) |

Query parameters of `rows`: `limit`, `cursor`, `offset`, `sort` (`-revenue,region`: a `-` prefix is descending), `columns`
(comma separated), `shape` (`columns`, the default, or `objects`), `schema` (`true`: include the schema document). A host that
does not declare `sort` and receives `sort` MUST answer `rdp.query.unsupported` (naming `sort`); it MUST NOT ignore it.

## 5. Rows

### 5.1 JSON

The answer ([page.json](page.json)) has `table` and exactly one row form:

- **columnar-by-row** (the default): `columns` (names, in answer order) and `values` (one array per row, in `columns` order);
- **objects**: `rows`, an array of objects (for hand-written hosts), selected with `shape=objects`.

A reader MUST accept both. Other fields: `version` (the change-stream position this answer is a snapshot of, section 8.3),
`total` (when `count` is cheap, otherwise absent), `next` (an opaque cursor; absent on the last page), `totals` (section 6.3),
`schema` (when asked). With no `columns` parameter the answer carries every column of the schema, in schema order.

### 5.2 Arrow IPC

`Accept: application/vnd.apache.arrow.stream` returns an Arrow IPC **stream**: the schema message, then record batches of at most
65,536 rows. Protocol facts travel as schema metadata ([page.json](page.json) `arrowSchemaMetadata`): `rachana.table`,
`rachana.key` (JSON text of the key list), `rachana.epoch`, `rachana.seq`, `rachana.total`, `rachana.next`. Per-field metadata:
`rachana.type` (when the Arrow type is not enough), `rachana.fmt`, `rachana.label`, `rachana.unit`. Compression is none or
LZ4 frame (`can` has `arrow.lz4`); a reader MUST NOT require zstd. Paging uses the same `cursor`; `rachana.next` carries the next
one. Decimals and 64-bit integers MUST be read without loss. A host that does not declare `arrow` and is asked for it answers `406`
with `rdp.query.unsupported`.

### 5.3 NDJSON

`Accept: application/x-ndjson` streams one header object (`page.json#/$defs/ndjsonHeader`), then one array of cells per line, in
`columns` order, so the first rows can paint before the last arrive. A host MAY offer it; there is no `can` value for it: a host
that does not offer it answers `406`.

### 5.4 Choosing a form (guidance, not protocol)

Up to about 5,000 rows: JSON, whole table. Up to about 2,000,000: Arrow, in the runtime's worker. More: `query` with push-down; the
runtime never holds the table. A runtime MUST say so ("this needs a server table: 3,200,000 rows") rather than truncate silently.

## 6. Query

`POST {base}/tables/{name}/query` takes a **query document** ([filter.json](filter.json) `$defs/query`), all fields optional:
`columns`, `filter`, `sort`, `limit`, `cursor`, `offset`, `group`, `asOf`, `params`. The answer has the form of section 5 (by
`Accept`). A host MUST answer `rdp.query.invalid` for a document that does not validate and `rdp.column.unknown` for a column the
table lacks (also inside `filter`, `sort` and `group`).

### 6.1 Sorting

`sort` is a list of `{ col, dir, nulls }`: `dir` is `asc` (default) or `desc`; `nulls` is `first` or `last` (default `last`,
independent of `dir`). The host MUST append the table's key (or, with no key, its own stable row identity) as the final
tie-breaker so that pages are stable. Text sorts by Unicode code point order of the UTF-8 form unless the host declares
otherwise in its documentation; numbers and decimals by value; dates and timestamps chronologically.

### 6.2 Paging

By **cursor**: `next` of one page is `cursor` of the next. A cursor is opaque, bound to the query that produced it and MAY expire; a
cursor of another query or an expired one is `rdp.cursor.invalid` (410), and the reader starts again from the first page. A host
with `offset` in `can` also accepts `offset` (not together with `cursor`). A page carries the `version` it was read at. A host with
`snapshot` pages one consistent snapshot; without it rows may move between pages and a live table is reconciled by the stream.
`limit` above the host's `pageRows` is lowered, not refused.

### 6.3 Grouping

`group` (only when `can` has `group`): `by` (columns), `values` (`{ name, agg, col?, p? }`), `totals`. `agg`: `sum`, `count`,
`min`, `max`, `avg`, `distinct`, `median`, `percentile` (needs `p`, 0 to 1 exclusive). `count` without `col` counts rows; every
other `agg` needs `col`. The answer's columns are the `by` columns then each value's `name`. With `totals: true` the answer has
`totals`: `{ name: value }` over all rows after `filter`. A runtime never sums what it was not sent in full.

## 7. The filter syntax tree

A row condition is compiled by the runtime; controls are replaced by their values; the result travels as a **syntax tree in JSON**
([filter.json](filter.json)). A host MUST NOT accept expression text and MUST NOT interpret any string as an expression.

| Node | Form |
|---|---|
| column | `{ "col": "region" }`; a dotted path reaches into an `object` column (`"legs.rate"`) |
| literal | `{ "lit": value, "type"?: "date" \| "timestamp" \| "decimal" }`; `type` says how a string literal is read and is allowed only on a string: `date` is `yyyy-mm-dd`, `timestamp` carries `Z` or an offset (3.1), `decimal` is digits with an optional `-` and fraction, no exponent |
| list | `{ "list": [ node, … ] }` |
| operation | `{ "op": name, "args": [ node, … ] }` |

Operations and arity (the schema enforces it; `in` and `not-in` take a column or expression then a `list`):

| Arity | Operations |
|---|---|
| 2 exactly | `==` `!=` `<` `<=` `>` `>=` `-` `/` `%` `contains` `starts_with` `ends_with` `has` `any_of` `all_of` `in` `not-in` |
| 2 or more | `&&` `\|\|` `+` `*` |
| 1 exactly | `!` `neg` `is-null` `lower` `upper` `len` `abs` `floor` `ceiling` `year` `quarter` `month` `day` |
| 1 or 2 | `round` (value, digits) |
| 1 or more | `coalesce` |

This is the **fixed push-down subset of v1**; a new operation is an addition of a minor of the protocol (section 14), never text.
Each operation is the Rachana-EL operator or Core function of the same name, at the arity above; three names differ from
EL's spelling: `neg` is unary `-`, `not-in` is `not in`, and `is-null(x)` is `x == null`. An operation with more than two
arguments is the left fold of its binary form, as Rachana-EL reads a chain: `{ "op": "+", "args": [a, b, c] }` is
`(a + b) + c` (this matters for `+`, which joins text when a side is not a number).
Semantics are those of Rachana-EL ([SPEC](../SPEC.md) section 4 and the entries of `functions.yaml`), with a missing or null
value as `empty`: null in, null out for operators and scalar functions, with SPEC's exceptions (`'x' + empty` → `'x'`
(4.5); `contains`, `starts_with`, `ends_with`, `has`, `any_of`, `all_of` answer `false` or `true`, never empty, and
`coalesce` skips empty (5.1)); `empty == empty` is true and `empty != 'X'` is true
(4.6); an ordering comparison against `empty` is false (4.7); `&&`, `||` and `!` follow SPEC 4.8 over truthy values; a row
whose condition is not truthy is excluded; equality is numeric when either side is a number, else exact text; `contains`,
`starts_with` and `ends_with` are as `functions.yaml` defines them (case rules included); `has(list, x)`, `any_of` and
`all_of` work over list columns. A literal of `type: date` against a `timestamp` column compares the timestamp's date in UTC.
A host that cannot honour these semantics for an operation MUST answer `rdp.query.unsupported` with
`operation` naming it; it MUST NOT approximate. The runtime then reports it at the panel, or, when the table is small enough, reads
rows and filters locally. An operation outside the subset over a table that is server-only is `RCH-2074` at author time.

Filtering, sorting or grouping on a masked column is `rdp.forbidden` (section 11).

## 8. Live changes

### 8.1 Patches

A live table is a **stream of keyed row patches**. The stream has an `epoch` (a new one whenever numbering starts again) and a
`seq` that rises by exactly one per message within an epoch. A patch ([patch.json](patch.json)):

```json
{ "table": "sales",
  "remove": [ ["West", "2026-01-01"] ],
  "update": [ { "region": "North", "month": "2026-02-01", "revenue": "140.00" } ],
  "add":    [ { "region": "East",  "month": "2026-02-01", "revenue": "75.00" } ] }
```

A key in `remove` is **always a list of the key values in key order**, even for a one-column key. Keys compare as text after the
value's JSON form (a decimal `"1.50"` is a different key from `"1.5"`; hosts SHOULD send one canonical text). Within one patch the
reader applies, in order: `reset` (an array replaces every row), `remove`, `update` (the fields given are merged into the row with
that key; an unknown key is added with those fields), `add` (an existing key is updated: its given fields are merged). A row
removed and added in one patch is present afterwards. A table without a key cannot be live: `rdp.table.no-key` at subscribe,
`RCH-2072` at compile time. A patch is at most 10,000 rows and 1 MB (section 13).

### 8.2 Transport: the events wire protocol

Changes travel as messages of the events wire protocol v1: `hello`/`welcome`, `sub`/`subd`, `msg`, `gap`, `err`, heartbeats, resume
with `after`, over a WebSocket (sub-protocol `events.v1`) or Server-Sent Events, **exactly as that protocol specifies**. This
protocol fixes only these conventions ([patch.json](patch.json) `$defs`):

| Convention | Value |
|---|---|
| topic of a table | `rdp.<table>`; each dot-separated part of the table name is one topic segment (a part with characters outside the topic grammar is encoded as one segment by the events protocol's `segment` rule) |
| stream | the topic (one numbering per table) |
| message `kind` | `rows` |
| message `key` | absent (a patch is not conflated by the transport; section 8.4) |
| message `data` | the patch of section 8.1 |
| filtered subscription | `sub` carries `meta: { "filter": <filter tree> }`; the host sends only matching rows and sends `remove` for a row that stops matching, and `update` carrying every field of a row that starts matching (`can` has `changes.filter`; without it `sub` with a filter is refused with `rdp.query.unsupported`) |
| token | the host callback's token goes in `hello` or `auth`, never in an address |

**Minimal SSE form** for a host with nothing else: `GET {base}/changes?table=sales&after=a1:4112` (epoch, colon, seq) answers
`text/event-stream`: one `event: msg` frame per patch whose `data` is the `msg` frame of `patch.json#/$defs/msgFrame` **with
`epoch` present**, a `: hb` comment heartbeat, read-only; `id:` is `<epoch>:<seq>`. Resume is `Last-Event-ID`. A host that cannot
replay answers a `gap` frame with `recoverable: false` and the reader resyncs. A filtered or conflated stream in the minimal form is
`POST {base}/changes` with the body `{ "table", "after": "<epoch>:<seq>", "filter"?: <filter tree>, "conflate"?: { "everyMs": n } }`
(the same stream answered; `GET` MAY also take `everyMs`). A host that cannot replay a filtered stream answers a position other than
the table's current one with the `gap` frame.

### 8.3 Snapshot, resume and gaps

1. The reader reads the rows; the answer's `version` is `{epoch, seq}`.
2. It subscribes to `rdp.<table>` with `after: { "<stream>": { epoch, seq } }`.
3. `subd.recovered: true`: every message after `after` follows; the reader applies each in order.
4. A message whose `seq` is not above the last applied is dropped. A message whose `seq` is more than one above is a gap.
5. **Resync** (read the rows again, resume from the new `version`) is required on: `recovered: false`; a `gap` with
   `recoverable: false`; a different `epoch`; a `seq` gap not repaired. Until then the reader keeps the old rows, marked stale.
6. A host without `replay` in `can` MUST say `recovered: false` on every re-subscribe.

### 8.4 Conflation and rate

A runtime applies patches at most once per animation frame (or its platform's equivalent); patches arriving faster are merged by key
in order: a later `remove` cancels an earlier `add` or `update` of that key; a later `add` after a `remove` stands; later fields
win. A host MAY do the same for a slow reader and MUST send `gap` if it drops instead. A reader MAY ask in `sub` for
`conflate: { "everyMs": n }`. A message that merges the messages `first` to `seq` carries both (`first` absent: one message); the
reader takes `first` as its start for the rules of 8.3, so a merged message is no gap.

### 8.5 Order and consistency

Messages of one table are applied in `seq` order and never out of order across epochs. A snapshot and a patch stream are
consistent when the host's snapshot `version` is exact: a host MUST NOT report a `version` newer than the rows it sends.

### 8.6 Appending tables

A table with `append: { window }` carries only `add` patches, needs no key, and readers keep the newest `window` rows.

## 9. Authentication and authorisation

The protocol defines **no** sign-in, token format or role model. **Auth is the host's.** HTTP calls use the host page's own `fetch`
(or the HTTP client the host hands the runtime): cookies and `Authorization` headers are the host's; the runtime adds none and
stores none. A stream's token comes from a host callback (section 8.2). Authorisation is per call: `GET {base}/tables` lists only
what the caller may read; rows are filtered and fields masked **for that caller**, also in subscriptions. A runtime calls only the
configured `{base}` and addresses on the host's allow-list (default: the same origin); a Sutra cannot introduce an origin. Knowing a
table's name grants nothing. A host that must not leak a table's existence answers `rdp.table.unknown` instead of `rdp.forbidden`.

## 10. Errors

### 10.1 Shape and codes

An HTTP error has the status below and the body `{ "error": { code, message, … } }` ([error.json](error.json)); a stream error is
the events protocol's `err` frame with the same `code` and the words in `detail`. `retry` says whether trying again can help;
`retryAfterMs` when to. Messages MUST NOT contain row values.

| Code | HTTP | Meaning | Panel state |
|---|---|---|---|
| `rdp.auth.required` | 401 | no or expired credentials | `denied` |
| `rdp.forbidden` | 403 | not permitted (the table, or a masked column used in `filter`, `sort` or `group`) | `denied` |
| `rdp.table.unknown` | 404 | no such table | `error` |
| `rdp.column.unknown` | 422 | a column the table lacks; `column`, `expected` (nearest first) | `error` |
| `rdp.query.invalid` | 422 | the query document or a parameter is malformed | `error` |
| `rdp.query.unsupported` | 422 (`406` for a format) | an operation, parameter or format the table cannot do; `operation` names it | local fallback or `error` |
| `rdp.cursor.invalid` | 410 | cursor expired or of another query | re-read from the start |
| `rdp.limit.rows` | 413 | a row or size limit | `error` |
| `rdp.limit.time` | 504 | the time limit | `error`, retry |
| `rdp.limit.rate` | 429 | too many requests | retry after `retryAfterMs` |
| `rdp.table.no-key` | 422 | changes asked of a table without a key | not live |
| `rdp.unavailable` | 503 | the source is down; `retry: true` | `stale` if rows are held, else `error` |
| `rdp.internal` | 500 | the host's fault | `error` |
| `rdp.query.narrow` | 422 | a list of a table with `narrow` that fixes none of its columns; `anyOf` names them (section 16) | the search form |
| `rdp.write.unsupported` | 405 | a write to a table without `can: write` (section 16) | read-only |
| `rdp.write.forbidden` | 403 | a write refused: the site switch is off, no right, a column not editable; the reason in words | read-only |
| `rdp.write.conflict` | 409 | a write on an old version; `current: { version, by, at }` | re-read the row |
| `rdp.write.invalid` | 422 | a write breaking a field rule; `fields: [{ column, rule, message }]` | show the rules |

A failed table fails only the panels that read it. A host's own codes in the `rdp.` family MUST be treated by a reader as
`rdp.internal` unless it knows them.

### 10.2 RCH codes

Wire codes are the `rdp.` codes. The runtime reports a failure as a Sutra problem with an `RCH-` code only where SPEC 6.2 has one
for the same fact, and may put it in the error's `rch` field: `rdp.table.unknown` is `RCH-2070`, `rdp.column.unknown`
`RCH-2071`, `rdp.table.no-key` `RCH-2072`, `rdp.query.unsupported` over a server-only table `RCH-2074`; the masked-column
refusal is `RCH-9161`. The other codes are panel states, not problems of the Sutra, and have no `RCH-` code.

## 11. Masking

A host MUST remove or tag a value the caller may not see **before it leaves the host**: in rows, `totals`, patches, Arrow and the
bridge alike. It MUST NOT compute `filter`, `sort`, `group` or `totals` over a masked column (`rdp.forbidden`), since that would
reveal order or content. A reader shows a masked value as a mask and never infers it. A masked cell in a patch has the same tag.

## 12. The in-process bridge

For a view in a desktop host (Electron, JCEF, JavaFX WebView, WebView2, CefSharp) the same messages travel over the web view's
message channel. Every message is a JSON object `{ "rdp": 1, "t": …}` ([bridge.json](bridge.json)):

| Direction | `t` | Fields | Meaning |
|---|---|---|---|
| view → host | `hello` | `client`, `v` | first message; answered by `welcome` |
| host → view | `welcome` | `v`, `can?`, `limits?` | |
| view → host | `tables`, `schema`, `rows`, `query` | `id`, `table` (not for `tables`), `body?`, `accept?` | a request. `body` is the query-parameter object for `rows` and the query document for `query` |
| host → view | `res` | `re`, `body` or `error` | exactly one answer per request, `body` as the HTTP answer of sections 4 to 6, `error` the error object of section 10 |
| host → view | `res.arrow` | `re`, `buffer` | Arrow IPC bytes (base64 in JSON; a platform with transferable buffers uses them in the same field) |
| view → host | `sub`, `unsub` | `id`, `table`, `after?`, `filter?`, `conflate?` | subscribe to changes, with the position held (absent: from now), an optional filter tree and conflation (8.4); answered by `res` whose `body` is `{ recovered, version }` (`recovered: false`: read the rows again and subscribe from their version); `unsub` (the `id` of the `sub`) by `res` with an empty `body` |
| host → view | `msg` | `sub`, `table`, `epoch`, `seq`, `first?`, `data` | a patch (section 8.1) of the subscription `sub`; the rules of 8.3 apply |
| view → host | `win` | `id`, `sub`, `keys`, `top?` | changes the window of the subscription `sub` (section 16); answered by `res` with an empty `body` |
| view → host | `write` | `id`, `table`, `body` (a change, section 16) | a write (`can: write`); answered by `res` whose `body` is `{ change, state, message, version? }` |
| host → view | `table` | `name`, `schema`, rows (either form), `version?` | **push**: a whole table without being asked |
| view → host | `action` | `view`, `panel?`, `name`, `payload?` | what the reader did; `name` is `select`, `open`, `control`, `key`, `submit`, `ready` or `error` |
| host → view | `control` | `view`, `set: [{ id, value }]` | the host sets control values |
| either | `ping`, `pong` | `ts` | liveness; `pong` returns the `ts` |

Request ids are strings unique per view; the host answers every request exactly once. A message larger than `limits.messageBytes`
(default 8 MB as JSON) MUST use Arrow buffers or paging. The bridge has **no authentication**: both ends are one application; the
view MUST accept bridge messages only from its host channel, never from page content or another frame. A runtime embedded
without a web view uses a plain `TableProvider` interface whose methods have the names of the requests above.

## 13. Limits

Defaults; a host MAY lower or raise them and reports them in `limits` of the base document, the schema or `welcome`.

| Limit (`limits` field) | Default |
|---|---|
| rows per JSON page (`pageRows`) | 10,000 |
| rows per Arrow batch (`arrowBatchRows`) | 65,536 |
| whole-table read without paging (`wholeTableRows`, `wholeTableBytes`) | 2,000,000 rows or 256 MB, whichever first |
| query document (`queryBytes`) · filter depth (`filterDepth`) · `in` list (`inList`) | 64 KB · 32 · 10,000 values |
| columns per table (`columns`) | 2,000 |
| text cell (`cellBytes`) | 1 MB |
| query time (`queryMs`) | 10,000 ms |
| patch (`patchRows`, `patchBytes`) | 10,000 rows, 1 MB |
| subscriptions per connection (`subscriptions`) | 200 |
| bridge message (`messageBytes`) | 8 MB |
| table name | 128 characters |

A limit hit is `rdp.limit.rows` (size) or `rdp.limit.time`; a query document beyond depth or size is `rdp.query.invalid`.

## 14. Versioning

The major is in the base document (`"v": 1`), the bridge `hello` and, by convention, the path. Within v1 a field, a `can` value, an
operation of section 7 or a message type MAY be added; **both sides MUST ignore fields, `can` values and message types they do not
know**. A change that breaks is v2. A schema change during a stream is a new `epoch`. Writes only under `can: write` (section 16;
other forms submit through the host's own endpoints and signal `submit`). Not in v1: joins (a host exposes a joined table under a name), expression text, binary WebSocket
frames for patches.

## 15. Conformance

`conformance/1/protocol/` holds `protocol` cases (FORMAT 5.12): a fixture table, requests, expected status and body (subset
comparison). The folder `examples/` has two or three cases per message of this protocol. A host under test serves each case's
`fixture` through a **test adapter**; its table has the version `{ epoch: "t1", seq: 0 }` at the start of each case, and cases
that need the live parts use three conventions of the adapter (the cases' paths, relative to `{base}`):

| Path | Meaning in a case |
|---|---|
| `POST /_test/patch` with a patch (section 8.1) | the adapter applies it to the fixture table and publishes it; answers `204` |
| `GET /changes?table=…&after=…` | the minimal SSE form; the **body of the step is the JSON array of the `msg` frames** (the parsed `data:` of each SSE event, in order), read until the stream is idle |
| `POST /_test/bridge` with a view → host bridge message | the body of the answer is the host → view message it produces (one channel per fixture: its subscriptions last until the next fixture) |
| `GET /_test/bridge/out` | the host → view messages the channel pushed since the last read (`msg` of its subscriptions), as a JSON array |

A host with live tables (section 16) adds: `POST /_test/live` (a live-table fixture, FORMAT 5.12: a declaration and its source's
rows; the table's epoch is the host's own), `POST /_test/as` `{ reader, view? }` (later requests are made as that reader; one bridge
channel per reader), `POST /_test/stream` (change the source, push the stream's upserts, deletes, gap or state), `POST /_test/upkeep`
`{ waitMs?, advanceMs? }` (move the host's clock, run retention and idle close) and `POST /_test/audit` (the audit events since the
last read).

The adapter's paths exist only under test and are not part of the protocol.

## 16. Live tables (additions under `can`)

A **live table** ([LIVE_TABLES](../../../docs/design/LIVE_TABLES.md)) is a table the host keeps current from a durable snapshot and
a live stream, merged by key and record version. Everything here is an addition to v1 under new `can` values (section 14);
a reader that does not know them reads the table as any other.

**Schema fields** (section 2; [schema.json](schema.json)): `rowVersion`, `changedBy`, `changedAt` (column names); `live: { state:
up | paused, reason? }` (a paused stream serves its snapshot and is never an error); `capped` (a table read whole stopped at its
row cap); `narrow: { anyOf }`; a column's `heavy` (never in a list page or a patch: read by fetching one row by key); `view: {
sort, groupBy, subtotals, widths }` (the table's own presentation); and, for a reader who may edit, `write: { columns: { <name>:
<field rules> }, reason }`.

**Windowed subscription** (`can: changes.window`; [patch.json](patch.json) `$defs/window`): a reader that shows a page of the table
subscribes with `meta.window: { keys: [[…]], top?: bool }` (the bridge `sub` carries `window`), the keys its page shows. It is sent
patches for those keys only, plus its new `total` when rows outside its page changed (a patch with no rows and a `total`). With
`top: true` (its page starts at the first row of the table's own order) and an order that is newest first (descending by
`changedAt` or `rowVersion`), a new or changed row is sent to insert on top and listed in the patch's `top`; any other reader gets
the total only. The window is changed by a `win` message (bridge, events) `{ sub, keys, top }`, or `POST {base}/changes/{sub}/window`
(body `{ keys, top? }`, answered `204`) in the minimal SSE form, whose `POST {base}/changes` body carries `window: { keys, top? }` and
whose first frame is `subd` with the `sub` id. Only the stream's own caller may change it; anyone else gets
`rdp.table.unknown`. A reader whose window falls more than the host's capacity behind gets a `gap` (`recoverable: false`) for itself
only and reads its page again. A subscription without a window gets every patch (section 8).

**Patch fields** (8.1): `total` (the reader's count after the patch) and `top` (keys to insert first). A patch with neither rows
nor these is still sent when the numbering needs it.

**Narrowing** (`can: narrow`): `rows` or `query` without a filter that fixes one of `narrow.anyOf` (an `==` or `in` on the column,
and-ed at the top of the filter) answers `422 rdp.query.narrow` naming them and reads nothing. Heavy columns are left out of list
pages and patches; `query` with a filter fixing the whole key returns them.

**Writes** (`can: write`): `POST {base}/tables/{name}/write`, bridge `t: write`, with `{ id, op: update | add | remove, key, basedOn,
set: { col: value }, from?: { col: value }, reason? }`; answered `{ change, state: applied | queued | held | refused, message,
version? }`. The same `id` again answers the first answer again. A write is accepted only when the table's declaration edits the
column, the host's switch is on, the host has a write path and the reader may edit that row and column; a row outside the
reader's scope is `404` as if absent. The reader's own pending changes are the table `<name>.changes` (key `change`; columns
`state`, `key`, `set`, `by`, `at`, `message`), live like any table. Errors: `rdp.write.unsupported`, `rdp.write.forbidden`,
`rdp.write.conflict`, `rdp.write.invalid` (section 10.1).

**Readers.** Every page, total, patch and change is the reader's: rows outside their scope are never counted, shown or sent;
masked values are `{ "$masked": true }` and never pick, order or filter a row. The rights are the host's alone.
