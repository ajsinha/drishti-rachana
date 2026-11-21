<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary. All rights reserved. -->
# The Rachana Data Protocol

The Data Protocol is how a Rachana view gets **tables** from a **host** and hears about **changes** to them. Any server that speaks
it can feed any Sutra; any client that speaks it can paint one. The normative text and its JSON Schemas are
[spec/1/data-protocol/PROTOCOL.md](../../spec/1/data-protocol/PROTOCOL.md) (`schema.json`, `page.json`, `filter.json`,
`patch.json`, `error.json`, `bridge.json`). This guide shows how the parts fit and which implementation to use.

## 1. The parts

| Part | Section of PROTOCOL.md | In one line |
|---|---|---|
| Tables and schema | 2, 3 | a name, typed columns, an optional key and the capabilities the table declares in `can` |
| HTTP resources | 4 | `GET {base}`, `GET {base}/tables`, `GET {base}/tables/{name}`, `GET .../rows`, `POST .../query`, `GET` or `POST {base}/changes` |
| Rows | 5 | JSON (columns or objects), Apache Arrow IPC, NDJSON |
| Query | 6, 7 | sort, cursor or offset paging, grouping with totals, and a filter syntax tree (never a query string) |
| Live changes | 8 | keyed patches (`reset`, `remove`, `update`, `add`) numbered by `{epoch, seq}`, over the events wire protocol, SSE or WebSocket |
| Security | 9, 11 | the host authenticates and authorises; masked values never leave it |
| Errors | 10 | one shape: `{error: {code, message, ...}}` with `rdp.*` codes and HTTP statuses |
| In-process bridge | 12 | the same messages over a desktop web view's message channel, no HTTP |
| Limits, versions | 13, 14 | sizes per page, patch and message; both sides ignore what they do not know |
| Live tables | 16 | windowed subscriptions, writes, pending changes, narrowed lists (see [Live tables](LIVE_TABLES.md)) |

A host must answer only `GET {base}/tables/{name}` and `GET {base}/tables/{name}/rows`; everything else is declared in `can` and a
reader never uses a capability the table does not declare. A request a host only half understood is refused
(`rdp.query.unsupported`), never answered with other rows.

## 2. A conversation

From the [server tutorial](../tutorials/LIVE_EDITS.md) (base `http://127.0.0.1:8095/data/v1`, signed in):

```text
GET  /data/v1/tables/orders
{"name":"orders","title":"Orders","key":["ID"],"columns":[{"name":"ID","type":"string","label":"Order"}, ...],
 "can":["page","sort","filter","count","group","offset","changes","changes.filter","changes.window","replay","arrow","write"],
 "rowVersion":"V","changedBy":"BY_USER","write":{"columns":{"QTY":{"id":"qty","type":"number","min":0}},"reason":"none"},
 "live":{"state":"up"},"version":{"epoch":"e1","seq":0}}

GET  /data/v1/tables/orders/rows?shape=objects
{"table":"orders","version":{"epoch":"e1","seq":0},
 "rows":[{"ID":"A-1","V":1,"QTY":10,"BY_USER":"system"},{"ID":"A-2","V":1,"QTY":25,"BY_USER":"system"}],"total":2}
```

The reader then follows `GET /data/v1/changes?table=orders&after=e1:0` (Server-Sent Events): each frame is one patch, and a resume
after a lost connection sends `Last-Event-ID`. A host that cannot replay answers a `gap` and the reader reads the rows again.

## 3. Values

Types are `string`, `integer`, `decimal`, `number`, `boolean`, `date`, `timestamp`, `list` and the rest of section 3.1. A `decimal`
travels as text (`"140.00"`) so no digit is lost; aggregates over decimals are exact (34 digits, half to even) on every host. A
masked value travels as a marker, never as the value (3.2).

## 4. Implementations

| Use | Implementation |
|---|---|
| a standalone server over files, JDBC and REST | [the Rachana server](SERVER.md) (`java/rachana-server`) |
| a Flask or FastAPI app | `drishti_rachana.adapters.flask` / `.fastapi` over `drishti_rachana.server.DataHost` ([CLI and SDKs](CLI_AND_SDKS.md#python)) |
| a static site with no server | `static_files_host.js`, written by `rachana build`: answers schema, filter, sort, columns and cursor paging from the site's files (no change stream) |
| a JVM host of your own | `java/rachana-data` (`TableProvider`, `ReferenceServer`, `TableClient`) and `java/rachana-live` for live tables |
| a desktop web view | the bridge messages of section 12, answered by the host application |
| the reader in a page | `DataClient` in `js/packages/rachana` (used by `<rachana-view>`) |

## 5. Checking a host

The `protocol` cases of the conformance suite run against any host that offers the test adapter of section 15:

```bash
python3 tools/conformance/protocol_check.py --reference                  # the JVM reference host (build java/ first)
uv run --with pyarrow python3 tools/conformance/protocol_check.py --python   # the Python DataHost
python3 tools/conformance/protocol_check.py --live                       # the live-table reference host, live cases too
python3 tools/conformance/protocol_check.py --base http://127.0.0.1:8080/rdp/v1   # your own host
```

Each case loads a fixture, sends its requests, reads the change stream and compares the answers; a results line per case and a
summary are written, as for the language cases ([conformance/RUNNER.md](../../conformance/RUNNER.md)).
