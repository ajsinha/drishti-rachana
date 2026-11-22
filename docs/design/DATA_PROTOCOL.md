<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary. All rights reserved. -->
# Rachana Data Protocol v1: decisions

The normative text is [spec/1/data-protocol/PROTOCOL.md](../../spec/1/data-protocol/PROTOCOL.md) (tables and schema, types, the HTTP
resources, rows in JSON, Arrow and NDJSON, query, the filter tree, live changes, errors, masking, the bridge, limits, versioning,
conformance). This note keeps only the decisions behind it and repeats none of the rules. Related: [LIVE_TABLES](LIVE_TABLES.md),
[DECISIONS](DECISIONS.md).

## Small on purpose

A host with a list of dictionaries and one HTTP route can implement the minimum in an afternoon; a host with a hundred million rows
implements push-down and never ships them. The protocol is not a query language, not an authentication scheme and not a transport for
ViewModels. Three transports carry the same messages: HTTP, a stream for changes, and an in-process bridge for desktop hosts.

## The filter is a syntax tree, never text

A Sutra's row condition is compiled by the runtime; controls are replaced by their values; the result travels as the expression's
syntax tree in JSON. No server receives expression text to interpret, so there is nothing to inject into and a host reads the tree
with its own tools (a SQL layer, an index).

## Which form carries rows

Whole-table JSON for small tables, Arrow for large ones, `query` with push-down beyond that, so the runtime never holds a table it
cannot. The thresholds are defaults of the client (`rachana.data.inline-rows`, `rachana.data.client-rows`), not of the protocol. A
panel that would need more rows locally than the threshold says "this needs a server table: n rows"; it is never silently truncated.

## Authentication and authorisation are the host's job

The protocol defines no sign-in, token format or role model. The table client uses the host page's own `fetch`, so cookies and
`Authorization` headers are the host's; a stream's token comes from a host callback inside the `hello` / `auth` frames, never in an
address. Authorisation is per call: lists, rows and masks are decided for that caller, a filtered subscription is evaluated for that
caller. Masking is expressed in the data, so a runtime can show it and refuse to filter or group on it, but can never reveal it. A
Sutra cannot introduce an origin: `url:` sources are checked against the host's allow-list (default: same origin). Knowing a table's
name grants nothing.

## The live part follows the events wire protocol

Live changes are keyed patches over the events wire protocol designed for Drishti Elements, so one client serves tables and events.

## A change id names one content

A write's `id` is the client's and makes a retry safe: the same `id` is never taken twice. It is answered from the first answer only
when the content is the same: the host's `ChangeSink` keeps a digest of the content (`Change.digest()`: SHA-256 of the canonical
`table`, `op`, `key`, `basedOn`, `set`, `from`, `reason`; not the `id`, maker or time) with its first answer and gives it back through
`ChangeSink.priorDigest(id)`; the engine compares it with the repeat's. Other content under a used `id` is a client fault, never a
retry: `409 rdp.write.conflict` with `reused: true`, audited `write-reused` so the host can look into it, and the sink never sees it.
A sink that keeps no digest (the default `null`) leaves the engine only its own memory of the ids it answered since it started. A
digest, not the change, keeps the SPI small and lets a host keep one column next to its answers.

## A table taken away answers expired

A host that makes tables of its own (a search's result) takes them out with `LiveTables.undeclare(name)`, so nothing of them stays.
A reader learns it the protocol's way: its stream ends with a `gap` that cannot be recovered (`expired: true`), and reading again is
`404 rdp.table.unknown` with `expired: true`, never a row held before. The engine remembers the last ten thousand names taken away to
say "expired" rather than "unknown"; beyond that a name is simply unknown, which is still never a stale result.

## Deliberately not in v1

Writes (a form submits through the host's own endpoints and signals `submit`); joins across tables (a host exposes a joined table
under a name); server-side expression text; binary WebSocket frames for Arrow patches (JSON patches only; reserved); schema
evolution during a stream (a schema change is a new `epoch`).
