<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary. All rights reserved. -->
# Live tables, editing and approvals

A **live table** is a table that stays current while people look at it: rows arrive and change by key, each reader sees the rows
their access allows, and, when the table is declared editable, readers with the right may change cells. Changes can be held for a
second person to approve (four eyes). The engine is `java/rachana-live`; the [Rachana server](SERVER.md) runs it over files, JDBC and
REST sources. The protocol additions are [PROTOCOL.md section 16](../../spec/1/data-protocol/PROTOCOL.md); the design and its
reasoning are [docs/design/LIVE_TABLES.md](../design/LIVE_TABLES.md).

## 1. How it works

- **Snapshot and stream.** A table reads its rows from a snapshot source (a file, a database query, a REST answer) and applies
  changes from a stream. The server's stream reads the source again every `refresh` and sends what changed by key.
- **Merge by key and version.** Rows are matched by the declared key. With a `version:` column an arriving row whose version is not
  above the held one is dropped, so changes never go back or repeat.
- **One shared table, per-reader views.** The rows are held once; each reader gets a view made by the host's access rules (row
  filter, masked columns, editable columns), never stored beyond the request and that reader's subscription.
- **Windows.** A reader showing a page of a large table follows only the keys it shows, plus the new total; bursts are coalesced per
  key and bounded, so a busy table never queues without limit.
- **Gaps.** When a stream loses its place the table starts a new epoch and every reader reads again; a stream whose source is down
  is `paused` and the table keeps serving what it has.

## 2. The declaration

One file per table, `<name>.live.yaml`, in the project folder or its `live/` folder. It names a configured table as its `snapshot`
(and `stream`) and says how it is shown and edited:

```yaml
rachana: 1
live-table: orders           # the table's name in the Data Protocol
title: Orders
snapshot: orders             # a table of rachana-server.yaml
stream: orders               # follow its changes
key: ID
version: V                   # needed for edits: the version a change is based on
changed-by: BY_USER          # optional: who made the row's last change
tray: true                   # serve the pending changes as orders.changes
columns:
  - { name: ID, label: Order }
  - { name: V, type: integer }
  - { name: QTY, type: integer, label: Quantity }
  - { name: BY_USER, label: Changed by }
edit:                        # absent: read-only
  fields:
    - { id: qty, bind: QTY, type: number, min: 0 }
```

Other keys: `where` (an expression every row must hold), `sort`, `group-by`, `subtotals`, `changed-at`, `retention` (`ttl`, `on`,
`max-rows`), `narrow` (a list must filter on one of the named columns first) and `heavy: true` on a column (left out of list pages,
read one row at a time). A declaration is checked whole when it is loaded; one with problems is set aside and listed, never
half-served. `rachana lint` checks `*.live.yaml` files with the Sutras.

## 3. Showing a live table

A Sutra needs no special syntax: a `data:` entry names the table, and two fragments of the core library draw it.

```yaml
libraries: [core@1]
data:
  orders:  { table: orders, key: ID, live: true }
  pending: { table: orders.changes, key: change, live: true }
panels:
  - use: core/live-table@1
    with: { title: Orders, rows: $.orders, order: V, edit: cells,
            columns: [ { label: Order, bind: "@.ID" }, { label: Quantity, bind: "@.QTY", fmt: amount0 } ] }
  - use: core/held-changes@1
    with: { changes: $.pending }
```

`core/live-table@1` is the grid: a window of rows kept current, "N new" for rows outside the page, the stale and paused banners, and
editing in the cells (`edit: cells`) or in a record panel beside the grid (`edit: beside`). Only the columns the schema offers this
reader under `write` are editable. `core/held-changes@1` lists the reader's own pending changes and, for an approver, the held changes
of others to release or reject. Their parameters are on the [core library page](../../libraries/core/README.md).

## 4. Editing

A write is accepted only when all four hold, checked on the server for every request:

1. the declaration has `edit:` naming the column;
2. the table's edits are switched on (`edits.off` in the server configuration lists tables switched off);
3. a write binding exists (the server: `write:` on the configured table and an `edits.journal`);
4. the access rules let this reader edit this row and column (`access.tables.<name>.edit`).

A change names the row's key, the version it was made on (`basedOn`), the columns it sets and a change id chosen by the client:

```json
{ "id": "c1", "key": ["A-1"], "basedOn": 1, "set": { "QTY": 12 } }
```

It is checked by the same form rules the page ran (`type`, `required`, `pattern`, `min`, `max`, `options`), then by the version
guard. The answers:

| Answer | When |
|---|---|
| `200 {change, state: applied \| queued \| held \| sent, message}` | accepted |
| `409 rdp.write.conflict` with `current: {version, by}` | the row changed since `basedOn` |
| `422 rdp.write.invalid` with `fields: [{column, rule, message}]` | a form rule failed |
| `403 rdp.write.forbidden` | edits off, no right, a column not editable, or no CSRF token (audited) |
| `404` | the row is outside the reader's scope, as if absent |

**Exactly once.** The change is written to the outbox journal before it is acknowledged, sent to the source once, and sent again with
the same change id after a crash; the source records each applied id, so a change never applies twice. The same change id sent again
answers the first answer. The edit shows when the source's own change arrives on the stream, never before.

## 5. Approvals (four eyes)

A table listed in the server's `edits.hold` keeps every change `held` until an approver decides it. Approvers are the roles in
`access.tables.<name>.approve`. A change is never decided by its maker.

- The pending changes are the table `<name>.changes` (columns `change`, `state`, `summary`, `by`, `at`, `message`): each reader sees
  their own; an approver also sees the held changes of others whose rows they may read.
- An approver decides with a write to that table: `{"id": "d2", "key": ["c1"], "basedOn": "held", "set": {"state": "released"}}`, or
  `"rejected"` with a `reason`.
- A released change is applied as any other; every step (`write-submitted`, `write-held`, `write-released`, `write-sent`,
  `write-refused`) is in the server's audit log.

The tutorial [Edit a live table with approvals](../tutorials/LIVE_EDITS.md) runs the whole path.

## 6. In another host

`rachana-live` is a library: a JVM host installs `LiveTables` with its own snapshot sources and streams (`SnapshotSource`,
`LiveStream`), an `Authoriser` (who sees and edits what), an `AuditSink`, and either Rachana's `Outbox` with a `ChangeWriter` for its
source, or its own `ChangeSink`. `LiveTables.screen(name)` answers the Sutra that shows a declared table with the two fragments above,
so a declaration alone gives a screen. The live-table cases of the conformance suite run against such a host with
`tools/conformance/protocol_check.py --live` or `--base ... --with-live`.
