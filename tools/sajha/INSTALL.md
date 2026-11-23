<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary. All rights reserved. -->
# Rachana GenUI kit for SAJHA: install (1.1.0)

This folder is a SAJHA plugin. Put it where SAJHA reads plugins: `<sajha>/config/plugins/rachana_genui/` (the folder holding this file,
`plugin.json`, `tools/`, `schemas/` and `rachana_genui/`). Nothing in SAJHA changes. The plugin is **unsigned**: SAJHA loads it with a
notice while `plugins.allow_unsigned` is true (its default); sign it for production, or set that switch to false once it is signed.

1. Install the drishti-rachana wheel with the V8 extra into SAJHA's Python (3.10 or later; tested on 3.11 and 3.13; the wheel is not on a
   public index): `pip install 'drishti_rachana-1.1.0-py3-none-any.whl[v8]'`
2. Set three environment variables in SAJHA's process environment:

   | Variable | Value | Meaning |
   |---|---|---|
   | `RACHANA_LIBS` | `<sajha>/config/plugins/rachana_genui/libraries` | the fragment libraries (`core`, `finance`) shipped in this folder; path-separated list, `.rlib` files allowed |
   | `RACHANA_GENUI_DATA_TOOLS` | e.g. `risk_query,positions_query` | SAJHA tools `publish_ui` may call, as the user, to fetch a table's rows (comma-separated; none by default) |
   | `RACHANA_GENUI_DATA_ORIGINS` | e.g. `https://drishti.example:18480` | Data Protocol hosts a board may read directly (none by default) |

3. Restart SAJHA: `tools/list` shows `search_fragments`, `get_fragment`, `check_sutra`, `publish_ui`. Grant them in SAJHA's policy like any other tool.

Optional settings (board server address, limits, audit file, secret): README.md, "Deploy into SAJHA", step 3.

## Who can open a board

The board server is its own small HTTP server and has **no sign-in of its own**. A board's address (`/b/<id>`, 128 random bits) is the
access: whoever holds the link, or `/b/<id>/board.json`, reads the rows that were fetched with the publisher's rights. So:

- keep `RACHANA_GENUI_HOST` on a loopback or private interface (the default is `127.0.0.1`), or put the port behind a proxy that signs
  readers in (SAJHA's proxy and sign-in, or yours); share a link only with people who may see that data;
- the `view` audit event has no reader identity (the board's HTTP thread has no SAJHA caller): it records that a board was opened, not by whom;
- tokens and boards live in one process. With more than one SAJHA worker set the same `RACHANA_GENUI_SECRET` in all of them (check tokens
  then verify everywhere); a board is still held by the worker that published it, and the others do not share it.
