<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary. All rights reserved. -->
# GenUI with Rachana: the guide

For two readers: the **agent builder** (the system prompt and the tool loop of a GenAI app on SAJHA) and the **app developer** (the
Python app and the page the user opens). The contracts of the tools are in [TOOLS.md](TOOLS.md); the design is
[docs/design/GENUI_KIT.md](../../../docs/design/GENUI_KIT.md).
The architecture for architects and managers (why, the two paths, a worked example, what is built): [docs/design/GENUI_ARCHITECTURE.md](../../../docs/design/GENUI_ARCHITECTURE.md).

## 1. What the agent makes

A **Sutra**: a YAML document that names fragments from the libraries, binds them to tables by name, and lays them out. It holds no
code (no script, HTML, CSS or handlers: the grammar has no place for them) and no rows. The compiler checks it completely; the page
compiles it again and paints it with Drishti Elements. The agent composes, the kit checks, the page paints.

```yaml
rachana: 1.2
sutra: risk-mashup
version: 1
doc:
  summary: Firm VaR and ES, VaR by desk, exposure by counterparty, limit use and what changed since yesterday.
  description: A risk mash-up over four tables of the day.
libraries: [core@1, finance@1]
data:
  firm_risk: { table: firm_risk, as: record }       # tables are named, never written out
  var_by_desk: { table: var_by_desk }
panels:
  - use: finance/var-tiles@1
    with: { var: "$.firm_risk.VAR_99", es: "$.firm_risk.ES_975", previous-var: "$.firm_risk.VAR_99_PREV" }
  - use: core/ranked-bars@1
    with: { title: VaR by desk, rows: { from: $.var_by_desk, columns: { label: DESK, value: VAR_99 } } }
```

`rows: { from: $.t, columns: { <fragment column>: <your column> } }` binds your table to a fragment's table parameter: the fragment
is written in its own column names, you map yours onto them. The compiler renames at compile time; nothing is copied.

## 2. The loop: search, get, compose, check, fix, publish

```
 user asks ─► data tool's outputSchema (columns, types) ─► search_fragments(query, tables) ─► get_fragment (when unsure)
                                                                          │
 publish_ui(sutra, check_token, table SOURCES) ◄── ok ◄── check_sutra ◄── compose the Sutra from the results' `use`
          │                                              │ problems
          ▼                                              └─► fix only the lines named ─► check_sutra (at most 3 attempts)
 board URL ─► the user's browser: <rachana-view> paints; publish again with `board` to update it live
```

1. **Shape, not rows.** Learn each table's columns from the data tool's `outputSchema` (SAJHA's `tools/list` carries it). If a tool
   has none, call it once with the smallest selection it allows and read the column names only. Never paste rows into a Sutra or a
   tool argument.
2. **Search** with the user's words and the shapes: `search_fragments({query: "limit utilisation", tables: {limit_use: {columns:
   [...]}}})`. Each result has `doc` (summary, use-when, avoid-when, data in words with a sample row, example, see-also), `fit`
   (coverage 0..1, the column mapping, the missing columns) and `use`, a ready `use:` stanza. Prefer coverage 1.0. Read
   `avoid-when`: it names the better fragment. One search per need ("VaR by desk", "limit use", "what changed").
3. **Get** a fragment when a parameter is unclear: `get_fragment({id: "core/measure-table@1"})` gives every parameter with its
   columns (optional ones too: map `second` to your change column) and an example Sutra.
4. **Compose.** Start from the `use` blocks. Add `libraries: [core@1, finance@1]` for every library you use, one `data:` entry per
   table (`name: { table: name }`, `as: record` for a one-row table whose fields fill value parameters), a `doc:` with a summary and a
   description, and the panels. Titles and labels are yours to write.
5. **Check**: `check_sutra({sutra, tables})` with the same shapes. On problems, read `summary`, then each problem's `code`, `path`,
   `message` and `expected`, and change **only those lines**. Check again. After three failing attempts the tool answers `stop`:
   simplify (fewer fragments) or ask the user; never loop.
6. **Publish**: `publish_ui({sutra, check_token, tables: {limit_use: {tool: "risk_query", arguments: {...}, path: "rows"}}, title})`.
   Give each table's **source**: the kit calls the data tool itself, as the user, and pushes the rows to the page. The text must be
   exactly the one that passed (any change needs a new check). Give the user the `url`. To update the open page (new run, new
   filter), publish again with `board: <id>`.

### Problem codes the loop meets most

| Code | Meaning | The fix |
|---|---|---|
| `RGK-112` | a column the table does not have | use one of `expected` |
| `RGK-111` | a table that was not offered | use one of `expected`, or offer the table |
| `RGK-121` | a fragment the catalogue does not have | search again; `expected` lists near names |
| `RGK-120` | a library not in `libraries:` (or not allowed) | add `<library>@<major>` |
| `RGK-110` | a `url:` source, or more than 200 rows written in the Sutra | publish the data as a table source |
| `RCH-2114` / `RCH-2115` / `RCH-2116` | unknown parameter / required parameter missing / wrong type | read `get_fragment` |
| `RCH-2071` | unknown column (compiler) | use one of `expected` |
| `RCH-2101` | an expression does not compile | quote it, check `==`, `group()` and the function names |
| `RCH-2093` (warning) | the board's own doc is thin | write a one-line summary and a description |

## 3. Safety rules (what the kit enforces, what the prompt should say)

* **No code in Sutras.** There is nowhere to put it; text that looks like markup or script is refused (`RGK-130`) as defence in depth.
* **The check is mandatory.** `publish_ui` takes only a text with a valid `check_token`, and checks again against the columns the
  tables really have (`RGK-171`).
* **Rows never through the model (GD4).** Shapes go to the model; rows go from the data tool to the kit to the page. Only constants
  the agent writes itself (a list of targets, labels) may be inline, at most 200 rows.
* **Allow-lists.** Libraries (`RACHANA_GENUI_ALLOWED_LIBRARIES`), data tools (`RACHANA_GENUI_DATA_TOOLS`) and Data Protocol origins
  (`RACHANA_GENUI_DATA_ORIGINS`). A Sutra cannot name an address (`url:` is refused).
* **Size limits.** 64 KB per Sutra, 60 panels, 12 tables, 50,000 rows per table, 3 check attempts, boards expire after a day.
* **As the user.** Data tools run with the user's rights, policy and audit in SAJHA. A board belongs to its publisher; only they can
  update it. Its address is unguessable (128 bits); share it like any capability link.

A system-prompt block that works:

> You build dashboards with the Rachana tools. Never put data rows in a Sutra or a tool argument: describe tables by column names and
> types, and give publish_ui each table's source. Search fragments with the user's words and the tables' shapes; use the `use` of the
> best fit; check with check_sutra; fix only the lines a problem names; publish only what passed; after three failed checks, simplify
> or ask. Give the user the board's URL.

## 4. Data hand-over: inline or Data Protocol

| | Pushed by the kit (`{tool, arguments, path}`) | Read by the page (`{protocol, table}`) |
|---|---|---|
| Rows travel | data tool → kit (in SAJHA, as the user) → board server → page (SSE) | Data Protocol host (Drishti) → page |
| Good for | tool results up to tens of thousands of rows; any SAJHA tool | large or live tables; data that must not leave its host; row-level rules enforced by the host |
| The page | answers `<rachana-view>`'s Data Protocol requests in the page (the runtime's own query: filter, sort, page) | `<rachana-view data="<host>">`, live changes followed |
| Limits | `RACHANA_GENUI_MAX_ROWS` per table | the host's; its origin must be allowed (and is in the page's CSP) |

A board takes one hand-over for all its tables (one Data Protocol host, or all pushed). Constants the agent wrote (`{rows: [...]}`,
at most 200) count as pushed.

## 5. The app developer: Python app and page

**Inside SAJHA** nothing is to build: deploy the folder ([README](../README.md)), allow your data tools, and give the agent the four
tools. The board server runs inside SAJHA's process (one daemon thread) on `RACHANA_GENUI_HOST:PORT`; put it behind the same
reverse proxy and sign-in as SAJHA for users outside the machine.

**In your own Python app** (no SAJHA), call the tool classes directly and register your data functions:

```python
from rachana_genui import rows
from rachana_genui.settings import Settings, use
import importlib.util

s = Settings.load(); s.data_tools = ["orders"]; use(s)
rows.register_tool("orders", lambda args: {"rows": my_db.query(args["desk"])})        # your data, never through the model
def tool(name):
    spec = importlib.util.spec_from_file_location(name, f"tools/sajha/tools/{name}.py"); m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m); return next(v for v in vars(m).values() if getattr(v, "TOOL", "") == name)({})
search, check, publish = tool("search_fragments"), tool("check_sutra"), tool("publish_ui")
# expose them to your model as tools (their definitions: t.name, t.description, t.input_schema, t.output_schema)
```

**Your own page** instead of `spa/`: any page that loads Drishti Elements and `<rachana-view>` from the wheel's assets can follow a
board: open `/b/<id>/events` (SSE) and on each `board` message set `view.files` (from `/libraries/<name>.json`), the table hand-over
(`data` attribute, or a `fetch` that answers from the pushed rows as `spa/app.js` does) and `view.sutra`. `spa/app.js` is 80 lines
to copy from.

## 6. Writing fragments agents can use

The catalogue reads only the `doc:` block (SPEC 2.9). A fragment an agent should find needs: a summary that says what it shows, a
description, `use-when` and `avoid-when` (naming the alternative), `data` (the rows in words and a synthetic sample row in the
fragment's own column names), `example` (a minimal use), `see-also` and `keywords`. `RCH-2093` reports what is missing, and the
library gate fails on it. Your own libraries join the catalogue by being on `RACHANA_LIBS`.
