<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary. All rights reserved. -->
# The GenUI kit's tools

The authority is each tool's definition in [`../schemas/`](../schemas) (SAJHA's tool format: `name`, `description`, `annotations`,
`inputSchema`, `outputSchema`, `metadata`). Every output schema that carries a fragment's doc refers to **one** definition,
`{"$ref": "#/$defs/doc"}`, the `doc` of [spec/1/sutra.schema.json](../../../spec/1/sutra.schema.json) (copied to
`schemas/doc.schema.json` and inlined when the tool loads, so a client receives a schema that stands alone). An agent therefore gets
typed, documented fields in every result. This page says what each tool is for and shows one call.

Tables are described everywhere as `{name: {columns: [{name, type}], as?: "rows" | "record"}}`: names and types, **never rows**.

## search_fragments (read-only)

Finds fragments by text (the request's words against name, keywords, summary, use-when, tags, category, description; rarer words
weigh more) **and** by data shape (each table parameter of a fragment against your tables: compatible types, name affinity, coverage
of its required columns; a record table's fields against its value parameters). Deterministic: no embeddings, no model.

```json
{"query": "limit utilisation", "tables": {"limit_use": {"columns": [{"name": "LIMIT_NAME", "type": "text"},
  {"name": "LIMIT_USD", "type": "number"}, {"name": "USED_USD", "type": "number"}, {"name": "OWNER", "type": "text"}]}}}
```
```json
{"results": [{"id": "finance/limit-table@1", "library": "finance", "name": "limit-table", "major": 1,
  "doc": {"summary": "Many limits at once - name, limit, used, headroom and the share used, ...", "use-when": ["..."],
          "avoid-when": ["One limit deserves a panel with history: use finance/limit-gauge."],
          "data": {"table": "rows", "in-words": "One row per limit: name, limit, used and optionally owner.",
                   "sample-row": {"name": "Rates Desk", "limit": 2500000, "used": 1850000, "owner": "Market Risk"}},
          "example": {"use": "finance/limit-table@1", "with": {"rows": {"from": "$.limits", "columns": {"...": "..."}}}},
          "see-also": ["finance/limit-gauge", "finance/risk-hierarchy-tree"], "keywords": ["limits", "utilisation", "..."]},
  "shape": {"tables": {"rows": {"required": true, "columns": {"name": {"type": "text"}, "...": {}}}}, "values": {}},
  "fit": {"coverage": 1.0, "tables": {"rows": {"table": "limit_use", "coverage": 1.0,
          "mapping": {"name": "LIMIT_NAME", "limit": "LIMIT_USD", "used": "USED_USD", "owner": "OWNER"}, "missing": []}}},
  "use": {"use": "finance/limit-table@1", "with": {"rows": {"from": "$.limit_use", "columns": {"name": "LIMIT_NAME",
          "limit": "LIMIT_USD", "used": "USED_USD", "owner": "OWNER"}}}},
  "score": 21.9, "matched": ["keywords:limit", "keywords:utilisation"]}], "count": 1, "categories": ["charts", "..."]}
```

`caution` appears when the request uses words of a fragment's `avoid-when`.

## get_fragment (read-only)

`{"id": "finance/limit-table@1"}` (or `library/name`, or a bare `name` one library has) answers the whole interface: `doc`,
`params` (each with `type`, `required`/`optional`/`default`, `doc`, and a table's `columns`), `slots`, `emits`, `shape`, `use`
(mapped to `tables` when given), `libraries_line` (`finance@1`), `source` (the fragment file) and `example_sutra` (the library's
example on synthetic data). Not found: `{"found": false, "nearest": [...]}`.

## check_sutra (read-only; counts attempts)

`{"sutra": "<YAML>", "tables": {...}}` answers `{ok, problems, summary, plan, attempt, stop?, engine, sutra_hash, check_token?}`.

* `problems`: SPEC 6.1's shape: `code`, `severity`, `message`, `range.start` (line, column), `path` (JSON Pointer), `expected`
  (valid names, nearest first). At most 25, sorted by position, `more` beyond.
* `plan`: the tables, fragments and libraries the Sutra reads.
* `attempt`: per user and Sutra name; beyond `max` (3) the answer has `stop: true` and `RGK-140` first.
* `engine`: `v8` (the compiler ran, RCH codes), `unavailable` (the V8 extra is missing: `RGK-150`), `not-run` (the kit's own errors
  came first).
* `check_token`: present when `ok`; binds this exact text.

Kit codes (a host's, SPEC 6.2): `RGK-101` too large · `RGK-102` not a Sutra · `RGK-103` too many panels · `RGK-110` a source an agent
may not write (`url:`, a long `rows:`) · `RGK-111` table not offered · `RGK-112` column not in the table · `RGK-120` library not
listed, allowed or installed · `RGK-121` fragment not in the catalogue · `RGK-130` markup or script text · `RGK-140` too many attempts
· `RGK-150` compiler unavailable. Compiler codes: SPEC 6.2 (`RCH-2071` unknown column, `RCH-2114`..`2118` fragment uses, `RCH-2101`
expressions, ...). The board's own doc block is linted as a warning (`RCH-2093`).

## publish_ui (writes a board)

```json
{"sutra": "<the text that passed>", "check_token": "<from check_sutra>", "title": "Risk mash-up",
 "tables": {"var_by_desk": {"tool": "risk_query", "arguments": {"view": "var_by_desk", "as_of": "2026-11-20"}, "path": "rows"},
            "firm_risk":   {"tool": "risk_query", "arguments": {"view": "firm_risk"}, "path": "rows"}}}
```
```json
{"published": true, "board": "Xz...Ag", "version": 1, "url": "http://risk-genui.example:8765/b/Xz...Ag", "expires_minutes": 1440,
 "tables": {"var_by_desk": {"rows": 6}, "firm_risk": {"rows": 1}}, "fragments": ["finance/var-tiles@1", "core/ranked-bars@1"],
 "summary": "Board Xz...Ag v1: 2 fragments over var_by_desk (6 rows), firm_risk (1 rows)"}
```

A table source is exactly one of `{tool, arguments?, path?}` (a data tool on the allow-list, called as the user), `{protocol,
table?}` (a Data Protocol host on the allow-list; the page reads it) or `{rows}` (at most 200 rows the agent wrote). Refusals come as
`{"published": false, "error": {code, message, table?}}`: `RGK-170` no passing check for this text · `RGK-171` the Sutra does not
fit the tables as fetched (with `problems`) · `RGK-172` board unknown, expired or someone else's · `RGK-160`/`161` data tool missing
or failed · `RGK-162` data tool not allowed · `RGK-163` no list of rows at `path` · `RGK-164` too many rows · `RGK-165` origin not
allowed · `RGK-166` too many tables · `RGK-167` mixed hand-overs. `board: <id>` publishes a new version of an existing board: its
open pages repaint.

## The board server

`GET /b/<id>` (the page, strict CSP) · `/b/<id>/events` (SSE: `event: board`, data `{type, id, version, title, sutra, protocol,
libraries, tables: {name: {rows} | {table}}, updated}`) · `/b/<id>/board.json` · `/libraries/<name>.json` (the library's files as
`<rachana-view>`'s `files` property takes them) · `/static/...` (the wheel's assets) · `/spa/app.js`, `/spa/app.css` · `/healthz`.
