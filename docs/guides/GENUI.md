<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary. All rights reserved. -->
# GenUI with Rachana, Elements and MCP

An AI agent can answer a question with a working screen instead of a paragraph. Here that means: the model writes a **Sutra** (a small
YAML document), Rachana **validates and compiles** it, and Drishti Elements **paints** it in place through `<rachana-view>`. The model
writes no code, and the rows never pass through the model. This guide says what is built, what the agent's tools are, how to connect an
MCP client, one worked example from question to screen, the guardrails, and how to use it inside your own Flask or FastAPI app
([In your own application](IN_YOUR_APP.md)).

The design and its reasoning are [docs/design/AI_GENUI.md](../design/AI_GENUI.md). This guide is the practical side, and links to it
instead of repeating it.

## 1. What is built and what is planned

Read this first; the names differ between the design and the product.

| Item | State | Where |
|---|---|---|
| The language that makes it safe: Sutras are data, no script, HTML or CSS | built | [Language](LANGUAGE.md), `spec/1/SPEC.md` |
| The check: the compiler's problems with codes, positions and the expected names | built | SPEC section 6; the compiler (JVM, JavaScript bundle, Python on V8) |
| The doc block of fragments and Sutras (what an agent searches) | built in **1.1.0** | SPEC 2.9; problem `RCH-2093` |
| **The GenUI kit lite for SAJHA**: four MCP tools `search_fragments`, `get_fragment`, `check_sutra`, `publish_ui`, the board server and the board page | built in **1.1.0** (`tools/sajha/` in the 1.1.0 tree) | section 3 |
| The plugin zip of the kit | built by `python3 tools/sajha/package.py` (1.1.0 tree) | |
| The generic Rachana MCP server for any MCP client (`drishti-rachana-mcp`: `validate_sutra`, `render_dashboard`, `sutra_examples`, `data_to_table`) | **planned**: `js/packages/rachana-mcp/` is a placeholder (`STATUS = "planned"`), nothing is implemented | design section 4 |
| Auto-applied "certain fixes" (`fixed` in a check result), a prompt kit of cards, `describe_table`, `update_dashboard` | **planned** (the kit lite returns problems with the expected names; the agent edits) | design sections 2 and 3 |
| The evaluation harness (`rachana eval`) and its task set | **planned** | design section 6 |
| Streaming, ephemeral Sutras inside Drishti (in-process GenUI) | **planned** (Drishti i9). The kit lite repaints an open board when you publish a new version; it does not stream a Sutra token by token | design sections 7 and 8 |

This documentation branch is based on 1.0.1, so the kit's files (`tools/sajha/...`) are not in this tree; they are in the 1.1.0 tree
and later. File paths below that start with `tools/sajha/` refer to it. What was run for this guide: the kit's worked example
(section 5) from a clean extract of the `v1.1.0` tag, without the V8 extra (so the check ran the kit's own rules and said
`engine: unavailable`); everything about MCP clients and the SAJHA deployment is from the kit's documents and is marked where
illustrative.

## 2. The idea in one picture

```
CONTROL PATH (small: names and text)                         DATA PATH (the rows)
user ─► agent ─► search_fragments / get_fragment             data source ─► data tool (SAJHA, as the user)
          │      check_sutra  ◄── problems ── fix            or a Data Protocol host (Drishti)
          │      publish_ui(sutra, check_token, SOURCES) ─────────────► the page: <rachana-view> + Elements
          └─ gives the user the board's address                          rows go to the page, never through the model
```

Why it is safe, in four sentences: a Sutra has no place for code; the compiler checks it completely and says what is wrong in terms a
model can act on; the painter draws only what the compiled ViewModel says; and the model sees column names and types, not rows.

## 3. The tools an agent gets

The kit is a SAJHA plugin (sajhamcpserver's plugin format: `plugin.json` plus `tools/*.py`); SAJHA publishes the tools over its MCP
endpoint, with its sign-in, roles, policy and audit applied to them like any other tool. Each tool's authoritative definition is its
JSON file in `tools/sajha/schemas/` (name, description, annotations, `inputSchema`, `outputSchema`), and `tools/sajha/docs/TOOLS.md`
has one call of each. In short:

Tables are always described as `{name: {columns: [{name, type}], as?: "rows" | "record"}}`: **names and types, never rows**.

| Tool | Writes? | Input | Output |
|---|---|---|---|
| `search_fragments` | read-only | `query` (the user's words), `tables` (the shapes in hand) | `results[]`: `id` (`finance/limit-table@1`), `doc` (summary, use-when, avoid-when, data in words with a sample row, example, see-also, keywords), `fit` (coverage 0 to 1, the column mapping, missing columns), `use` (a ready `use:` stanza mapped to your columns), `score`; `count`, `categories`; `caution` when the request uses words of an `avoid-when` |
| `get_fragment` | read-only | `id` (`library/name@major`, `library/name`, or a bare name one library has), optionally `tables` | the whole interface: `doc`, `params` (type, required or default, columns of table parameters), `slots`, `emits`, `shape`, `use`, `libraries_line`, `source`, `example_sutra` on synthetic data; or `{found: false, nearest: [...]}` |
| `check_sutra` | read-only; counts attempts per user and Sutra name | `sutra` (YAML text), `tables` (shapes) | `ok`, `problems[]` (`code`, `severity`, `message`, `range.start`, `path`, `expected`; at most 25, sorted by position), `summary`, `plan` (tables, fragments, libraries read), `attempt`, `stop`, `engine` (`v8`, `unavailable`, `not-run`), `sutra_hash`, and `check_token` when clean |
| `publish_ui` | writes a board | `sutra`, `check_token`, `tables` (each table's **source**), `title`, optionally `board` (update an open board) | `{published, board, version, url, expires_minutes, tables{rows}, fragments, summary}`, or `{published: false, error: {code, message}}` |

A table **source** is exactly one of `{tool, arguments, path}` (a data tool on the allow-list, called **as the user** by the kit),
`{protocol, table}` (a Data Protocol host on the allow-list, read by the page), or `{rows}` (constants the agent wrote itself, at most
200).

Search is deterministic (text words against the doc block, plus data shape: compatible types, name affinity, coverage of required
columns); there are no embeddings and no model in the kit. Every fragment is documented by its `doc:` block, so a fragment or a library of
your own joins the catalogue by being on `RACHANA_LIBS` and having a complete doc block.

### Install and configure (the kit's own steps)

The exact steps are `tools/sajha/INSTALL.md` and `tools/sajha/README.md` (1.1.0 tree). Summary:

1. Install the drishti-rachana wheel with the V8 extra into SAJHA's Python (not on a public index; build it from a checkout as in
   [In your own application](IN_YOUR_APP.md), section 3): `pip install 'drishti_rachana-1.1.0-py3-none-any.whl[v8]'`.
2. Put the plugin folder (or the zip's `rachana_genui/` folder) at `<sajha>/config/plugins/rachana_genui/`. The plugin is unsigned; SAJHA
   loads it with a notice while `plugins.allow_unsigned` is true.
3. Set `RACHANA_LIBS` (the fragment libraries), `RACHANA_GENUI_DATA_TOOLS` (SAJHA data tools `publish_ui` may call, none by default) and
   `RACHANA_GENUI_DATA_ORIGINS` (Data Protocol hosts a board may read, none by default).
4. Restart SAJHA; `tools/list` shows the four tools. Grant them in SAJHA's policy.

Run without SAJHA: `python3 tools/sajha/example/risk_mashup.py` (section 5).

## 4. Connect an MCP client

The kit does not run an MCP server of its own: **SAJHA is the MCP server** and the kit's tools are part of its catalogue. So an MCP
client connects to SAJHA's `/mcp` endpoint, and sees the four tools next to SAJHA's other tools. The two snippets below are
**illustrative**: the address, any token and the client's exact configuration keys are yours to fill in and check against the
client's own documentation; they were not run for this guide.

Claude Code (illustrative):

```bash
claude mcp add --transport http sajha https://<your-sajha-host>/mcp       # add a bearer header if your SAJHA needs one
```

Claude Desktop, through a bridge for a remote server (illustrative; `mcp-remote` is a third-party package):

```json
{ "mcpServers": { "sajha": { "command": "npx", "args": ["-y", "mcp-remote", "https://<your-sajha-host>/mcp"] } } }
```

Give the model the rules of the kit in its system prompt. A block that works (from the kit's guide):

> You build dashboards with the Rachana tools. Never put data rows in a Sutra or a tool argument: describe tables by column names and
> types, and give publish_ui each table's source. Search fragments with the user's words and the tables' shapes; use the `use` of the
> best fit; check with check_sutra; fix only the lines a problem names; publish only what passed; after three failed checks, simplify
> or ask. Give the user the board's URL.

The **generic** server for clients with no SAJHA (`npx drishti-rachana-mcp`, stdio and streamable HTTP) is planned; there is nothing
to configure for it yet.

## 5. A worked example, end to end

The kit ships this example as `tools/sajha/example/risk_mashup.py`: a script plays the agent's part with no model, calling the four tools in
the order an agent does. The data is synthetic. I ran it from a clean extract of the `v1.1.0` tag in a throwaway environment (needs
`pyyaml` and `jsonschema`; no V8):

```bash
RACHANA_GENUI_REQUIRE_ENGINE=0 PYTHONPATH=python/src python3 tools/sajha/example/risk_mashup.py      # add --serve to keep the board up
```

The conversation, with the real output lines of that run (the user's sentence is illustrative):

1. **User asks** (illustrative): "Show me firm VaR and ES, VaR by desk, exposure by counterparty, limit use and what changed since
   yesterday."
2. **Shape, not rows.** The agent reads the data tool's `outputSchema`: `firm_risk` (5 columns), `var_by_desk` (4), `exposure_by_cpty` (4),
   `limit_use` (4). No rows are read.
3. **Search**, once per need, with the shapes. Real results:
   `search_fragments('var es tiles at the head of a risk screen')` gave `finance/var-tiles@1` (coverage 1.0);
   `'rank desks by value at risk'` gave `core/ranked-bars@1`; `'many limits utilisation breaches first'` gave `finance/limit-table@1`;
   `'where exposure concentrates by counterparty and sector'` gave `finance/concentration-treemap@1`;
   `'measure with signed change by name'` gave `core/measure-table@1`. Each result carries a ready `use:` with the columns mapped.
4. **Get** the one the agent is unsure of: `get_fragment('core/measure-table@1')` lists its parameters (`rows*`, `title`, `name-label`,
   `first-label`, `first-fmt`, `second-label`, `second-fmt`); the optional column `second` is mapped to `VAR_CHANGE`.
5. **Compose** a Sutra from the `use` blocks (the first draft contains one wrong column, as a model might write it). The finished one is
   `tools/sajha/example/risk-mashup.sutra.yaml`; its shape, trimmed (**illustrative trim**; the file is the real thing):

   ```yaml
   rachana: 1.2
   sutra: risk-mashup
   doc: { summary: "Firm VaR and ES, VaR by desk, exposure by counterparty, limit use and what changed since yesterday." }
   libraries: [core@1, finance@1]
   data:
     firm_risk:   { table: firm_risk, as: record }       # tables are named; rows are never written out
     var_by_desk: { table: var_by_desk }
   panels:
     - use: finance/var-tiles@1
       with: { var: "$.firm_risk.VAR_99", es: "$.firm_risk.ES_975", previous-var: "$.firm_risk.VAR_99_PREV" }
     - use: core/ranked-bars@1
       with: { rows: { from: $.var_by_desk, columns: { label: DESK, value: VAR_99 } }, title: VaR by desk }
   ```

   `rows: { from: $.t, columns: {<fragment column>: <your column>} }` maps your table onto the fragment's own column names.
6. **Validate.** `check_sutra` attempt 1 answered `ok=False`: "1 error. line 19: table 'firm_risk' has no column 'ES_99' (expected one
   of AS_OF, ES_975, VAR_99, VAR_99_PREV, VAR_LIMIT). Change only the lines named and check again." The agent changed that one name to
   `ES_975`, taken from the problem's `expected` list. Attempt 2: `ok=True` and a `check_token`.
7. **Publish.** `publish_ui` with the check token and each table's source (the data tool and its arguments). The kit called the data
   tool itself and pushed the rows to the page: "Board ... v1: 5 fragments over firm_risk (1 rows), var_by_desk (6 rows),
   exposure_by_cpty (12 rows), limit_use (8 rows)", with the address `http://127.0.0.1:8765/b/<board id>`.
8. **The screen.** The user opens the address. One `<rachana-view>` paints the Sutra with Elements: the tiles, the bars, the limit
   table, the treemap, the change table. To update it (a new run, a new filter) the agent publishes again with `board: <id>`; the open
   page repaints in place over SSE. (I did not open the page in a browser; the kit has its own Chromium check, `tools/sajha/tests/browser_check.py`.)

The shape of a `publish_ui` call (**illustrative** values; the schema is in `tools/sajha/schemas/publish_ui.json`):

```json
{"sutra": "<the exact text that passed>", "check_token": "<from check_sutra>", "title": "Risk mash-up",
 "tables": {"var_by_desk": {"tool": "risk_query", "arguments": {"view": "var_by_desk", "as_of": "2026-11-20"}, "path": "rows"}}}
```

## 6. Guardrails: what is refused, and why

| Guardrail | Enforced by | What the agent sees |
|---|---|---|
| **No code.** The language has no script, HTML, CSS or handler; markup-like text is refused as defence in depth | the grammar, then the kit | `RGK-130` |
| **The check is mandatory.** `publish_ui` takes only the exact text that passed, and checks it again against the columns the tables really have | `check_token`, `publish_ui` | `RGK-170` no passing check for this text; `RGK-171` does not fit the tables as fetched |
| **Rows never through the model.** The model gets shapes; rows travel data tool, kit, page (or data host, page). Only constants the agent wrote may be inline, at most 200 | tool design | `RGK-110` for `url:` sources or more than 200 inline rows |
| **No invented addresses.** A Sutra cannot name a URL; data hosts and tools are on allow-lists | kit | `RGK-110`, `RGK-162` data tool not allowed, `RGK-165` origin not allowed |
| **Allow-lists** for libraries, data tools and Data Protocol origins | `RACHANA_GENUI_ALLOWED_LIBRARIES`, `_DATA_TOOLS`, `_DATA_ORIGINS` | `RGK-120` library not listed or allowed |
| **Real columns, real tables, real fragments** | kit and compiler | `RGK-111` table not offered; `RGK-112` column not in the table; `RGK-121` fragment not in the catalogue; `RCH-2071` unknown column; `RCH-2114` to `RCH-2118` fragment use errors; `RCH-2101` an expression does not compile |
| **Limits.** 64 KB per Sutra, 60 panels, 12 tables, 50,000 rows per table, 3 check attempts, boards expire after a day (all settings) | kit | `RGK-101` too large; `RGK-103` too many panels; `RGK-140` too many attempts (`stop: true`: simplify or ask the user); `RGK-164` too many rows; `RGK-166` too many tables |
| **As the user.** Data tools run with the user's rights, policy and audit in SAJHA; a board belongs to its publisher | SAJHA, kit | `RGK-160`/`161` data tool missing or failed; `RGK-172` board unknown, expired or someone else's |
| **Read-only for the data.** The kit reads through data tools and publishes a view; it writes nothing back to a source. `search_fragments`, `get_fragment` and `check_sutra` are read-only | tool annotations | |
| A thin doc block on the board itself is a warning, not an error | compiler | `RCH-2093` |
| No compiler on the machine: the check says so and publishing is refused unless you switch the requirement off (development only) | kit | `RGK-150`, `engine: unavailable` |

The problems are the compiler's own shape (SPEC 6.1): `code`, `severity`, `message`, `range`, `path` and, where it helps, `expected` with
the valid names, nearest first. The agent's rule is simple: change only the lines a problem names, check again, stop after three.

**Who can open a board.** The board server has no sign-in of its own: the board's address (128 random bits) is the access, and whoever
holds it reads the rows fetched with the publisher's rights. Keep `RACHANA_GENUI_HOST` on a loopback or private interface, or put
the port behind SAJHA's proxy and sign-in, and share a link only with people who may see that data. (`tools/sajha/INSTALL.md`.)

## 7. Using it inside your own Flask or FastAPI app

Two ways, both from the kit's guide (`tools/sajha/docs/GENUI_GUIDE.md`, section 5):

* **Your app is the agent's host.** Call the kit's tool classes directly, register your data functions as the data tools (rows come from
  your code, not the model), and expose `search_fragments`, `check_sutra` and `publish_ui` to your model as tool definitions. The
  kit's guide has the short snippet to do it (`rows.register_tool(...)`, then the three tools by name).
* **Your app serves the screens.** Mount the adapter and a Sutra folder as in [In your own application](IN_YOUR_APP.md). A Sutra that the
  agent produced and that passed the check is just a `*.sutra.yaml` file: write it into the folder `RachanaRouter(sutras=...)` serves,
  register a `@rachana.table` function for each table name it uses, and the page `/dash/page/<name>` paints it. The kit's own board
  page is another option (`tools/sajha/spa/`, about 80 lines of JavaScript to copy from): it takes `{sutra, tables}` over SSE and
  repaints on each new version.

Check what a model produced before you serve it, as the kit does: `python -m drishti_rachana lint <folder>` runs the compiler on V8
(extra `v8`), the same one the kit uses, and `--format sarif` or `json` feeds a CI step. *Writing a model-made Sutra into a served
folder is a pattern from the two guides, not a feature of the adapters; there is no built-in hand-over, and it was not run for this guide.*

## 8. Your own fragments, so agents can find them

The catalogue reads only the `doc:` block (SPEC 2.9). A fragment an agent should find needs a summary that says what it shows, a
description, `use-when` and `avoid-when` (naming the better alternative), `data` (the rows in words and one synthetic sample row in the
fragment's own column names), `example` (a minimal use), `see-also` and `keywords`. `RCH-2093` reports what is missing, and the library
gate fails on it. A library on `RACHANA_LIBS` joins the catalogue. The library format is in [Libraries](LIBRARIES.md).

## 9. Troubleshooting

| You see | Cause and fix |
|---|---|
| `tools/list` does not show the four tools | the plugin folder is not where SAJHA reads plugins, or `plugin.json` is missing; check `config.plugins.dir` and SAJHA's log |
| `check_sutra` says `engine: unavailable` (`RGK-150`) | the V8 extra is not installed in SAJHA's Python: `pip install '...whl[v8]'`; only for development set `RACHANA_GENUI_REQUIRE_ENGINE=0` |
| `search_fragments` finds nothing | `RACHANA_LIBS` does not point at the libraries (a copied folder has no checkout), or the fragments have thin doc blocks |
| `publish_ui` answers `RGK-162` | the data tool is not in `RACHANA_GENUI_DATA_TOOLS` |
| `RGK-140` and `stop: true` | three failed checks: simplify (fewer fragments) or ask the user; do not loop |
| `RGK-170` | the text changed after the check: check again and publish exactly that text |
| the board page is empty or does not update | the page needs the runtime bundle of the wheel (`python3 tools/js/build_js.py` before building it); more than one SAJHA worker needs the same `RACHANA_GENUI_SECRET`, and a board lives in the worker that published it |
