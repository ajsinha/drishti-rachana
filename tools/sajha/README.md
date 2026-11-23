<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary. All rights reserved. -->
# Rachana GenUI kit for SAJHA

Four MCP tools that let an agent build a **real, checked, live UI** for its user at runtime, from the Rachana fragment libraries:

| Tool | What it does |
|---|---|
| `search_fragments` | finds fragments by what the user asked **and** by the shape of the data in hand (column names and types, never rows); answers each fragment's documented contract and a ready `use:` mapped to your columns |
| `get_fragment` | one fragment's whole interface: doc block, parameters with their columns, slots, events, source, an example Sutra on synthetic data |
| `check_sutra` | the mandatory check: the kit's safety rules and the Rachana compiler; problems at exact positions with the expected names; a `check_token` when clean |
| `publish_ui` | publishes the checked Sutra as a live **board**; the kit fetches the rows itself (as the user) from a data tool, or the page reads a Data Protocol host; rows never pass through the model |

The board page (`spa/`) is plain JavaScript: one `<rachana-view>` (Drishti Elements painter) that receives `{sutra, tables}` over
Server-Sent Events and repaints in place on every new version. Strict Content-Security-Policy, every asset from the drishti-rachana
wheel, no CDN.

How an agent uses them: [docs/GENUI_GUIDE.md](docs/GENUI_GUIDE.md). Each tool's contract: [docs/TOOLS.md](docs/TOOLS.md).
The design (control path and data path, safety, audit): [docs/design/GENUI_KIT.md](../../docs/design/GENUI_KIT.md).

## Folder

```
tools/sajha/
  INSTALL.md              the short install (the first file in the plugin zip)
  package.py              builds the plugin zip, reproducibly: python3 tools/sajha/package.py
  plugin.json             SAJHA plugin manifest (name rachana-genui, the four tools)
  requirements.txt        installed by SAJHA's plugin loader (pyyaml, jsonschema)
  tools/*.py              the four tools: each file defines one BaseMCPTool subclass, as SAJHA's plugin loader expects
  schemas/*.json          each tool's definition in SAJHA's tool format (name, description, annotations, inputSchema,
                          outputSchema, metadata); doc.schema.json: the doc definitions of spec/1/sutra.schema.json, by $ref
  rachana_genui/          the kit: catalogue, doc lint, check, row sources, boards and the board server, settings, audit
  catalogue/generate.py   the catalogue as JSON (from the libraries' doc blocks), and a search from the command line
  spa/                    the board page: index.html, app.js, app.css
  example/                the worked example: a risk mash-up composed, checked, fixed and published (synthetic data)
  docs/                   GENUI_GUIDE.md (for agent and app developers), TOOLS.md (the contracts)
  tests/                  pytest, light: python -m pytest -q tools/sajha/tests
```

## Deploy into SAJHA

The kit is a SAJHA **plugin** (sajhamcpserver's `config/plugins/<name>/plugin.json` + `tools/*.py` format; nothing in SAJHA changes).

1. Install the drishti-rachana wheel, with the V8 extra, into SAJHA's Python environment (it is not on a public index):

   ```bash
   pip install 'drishti_rachana-1.1.0-py3-none-any.whl[v8]'       # the compiler on V8, the libraries reader, the <rachana-view> assets
   ```

   The wheel must carry its bundles (`python3 tools/js/build_js.py` before `python -m build python/`): the check and the page need them.

2. Put the folder where SAJHA reads plugins (`config.plugins.dir`, default `config/plugins`), as a copy or a link:

   ```bash
   cp -r tools/sajha  <sajha>/config/plugins/rachana_genui        # or: ln -s "$PWD/tools/sajha" <sajha>/config/plugins/rachana_genui
   ```

   or point SAJHA at a folder of plugins that holds it (`config.plugins.dir` in `application.yml`). On start SAJHA loads
   `plugin.json`, runs `pip install -r requirements.txt`, imports each `tools/*.py` and registers the four tools. An unsigned plugin
   loads with a notice while `plugins.allow_unsigned` is true (SAJHA's default); sign it for production (SAJHA's plugin signing).

3. Set the kit's environment (SAJHA's own process environment):

   | Variable | Default | Meaning |
   |---|---|---|
   | `RACHANA_LIBS` | the checkout's `libraries/` | library folders or `.rlib` files, path-separated (a copied folder has no checkout: set it) |
   | `RACHANA_GENUI_DATA_TOOLS` | none | SAJHA tools `publish_ui` may call, as the user, for a table's rows (comma-separated) |
   | `RACHANA_GENUI_DATA_ORIGINS` | none | Data Protocol hosts (Drishti) a board may read directly (`https://drishti.example:18480`) |
   | `RACHANA_GENUI_HOST`, `_PORT`, `_PUBLIC_URL` | `127.0.0.1`, `8765`, `http://host:port` | the board server: where it binds, the address users open |
   | `RACHANA_GENUI_ALLOWED_LIBRARIES` | all found | libraries an agent may use |
   | `RACHANA_GENUI_MAX_ROWS`, `_MAX_TABLES`, `_MAX_PANELS`, `_MAX_SUTRA_BYTES`, `_MAX_INLINE_ROWS`, `_MAX_ATTEMPTS`, `_BOARD_TTL_MINUTES`, `_MAX_BOARDS` | 50000, 12, 60, 65536, 200, 3, 1440, 500 | limits |
   | `RACHANA_GENUI_REQUIRE_ENGINE` | `1` | publishing needs the compiler's check on V8; `0` only for development |
   | `RACHANA_GENUI_AUDIT_FILE` | none | audit events as JSON lines (besides the log and the hooks) |
   | `RACHANA_GENUI_SECRET` | per process | the key of check tokens; set it when SAJHA runs several workers |
   | `RACHANA_GENUI_CONFIG` | none | one JSON file with any of the above (lower-case names: `{"data_tools": ["risk_query"]}`) |

   **Who can open a board.** The board server has no sign-in of its own: a board's link is the access (see INSTALL.md, "Who can open a
   board"). Keep `RACHANA_GENUI_HOST` on a loopback or private interface or put the port behind SAJHA's proxy and sign-in; the `view`
   audit event has no reader identity; tokens and boards are per process.

4. Grant the tools in SAJHA's policy like any other (roles, rate limits); the data tools keep their own grants: `publish_ui` calls
   them **as the user** through SAJHA's inner-call path, so a user can never publish data they could not read.

5. Check: `tools/list` shows the four tools; run the example below against your SAJHA, or `python3 tools/sajha/example/risk_mashup.py`.

Alternative layout: copy `schemas/*.json` into `config/tools/rachana_genui/` (a SAJHA tool folder) and put `tools/sajha` **and**
`tools/sajha/tools` on SAJHA's `PYTHONPATH`; each definition's `implementation` (`search_fragments.SearchFragmentsTool`, ...) is
then imported by SAJHA's tool loader. The plugin is simpler; use one layout, not both (a tool name may exist once).

## Run on its own

```bash
pip install -e 'python[v8]' pyyaml jsonschema && python3 tools/js/build_js.py      # in a drishti-rachana checkout
python3 tools/sajha/example/risk_mashup.py --serve --runs 5 --every 20             # prints the board's address; it updates live
python -m pytest -q tools/sajha/tests
python3 tools/sajha/catalogue/generate.py --search "limit use" --columns NAME:text,LIMIT:number,USED:number
```

Without the V8 extra the check runs the kit's own rules only and says so (`engine: unavailable`, `RGK-150`); publishing then needs
`RACHANA_GENUI_REQUIRE_ENGINE=0`.

## Integration points with SAJHA

| SAJHA | Used for |
|---|---|
| `sajha.tools.base_mcp_tool.BaseMCPTool` | the tools' base class (a stand-in with the same surface when SAJHA is absent) |
| plugin loader (`sajha/core/plugins.py`) | discovery: `plugin.json`, `requirements.txt`, `tools/*.py` |
| `sajha.tools.tools_registry.get_tools_registry().get_tool`, `sajha.core.inner_calls` (`entered`, `check`), `sajha.core.composition.execute_step` | `publish_ui` fetching rows from a data tool as the caller: rights, policy, audit, depth and cycles as SAJHA applies them |
| `sajha.observability.caller.current()` | the user in the kit's audit events and the owner of a board |
| SAJHA's tool-call audit and policy | every call of the four tools, as for any tool |
