<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary. All rights reserved. -->
# Rachana Studio

Studio is the visual editor of a folder of Sutras: a palette of library fragments, the live preview, the Sutra source checked as you
type, a parameter form that inserts a fragment, and the unsaved changes as a diff. It edits the same YAML files every other tool
reads; it adds nothing of its own to them.

## 1. Open it

```bash
node js/packages/rachana-cli/bin/rachana.js studio examples/static-sales --port 4500
node js/packages/rachana-cli/bin/rachana.js studio examples/server-demo/project/sutras --data examples/server-demo/project/data --port 4501
```

`rachana studio [folder] [--libs <path>,...] [--data <dir>] [--port <n>]`. It listens on `127.0.0.1` only and prints its address and
the libraries it found (`studio http://127.0.0.1:4500/  (core 1.0.0, finance 1.0.0; Ctrl+C to stop)`). It needs the runtime bundles
(`python3 tools/js/build_js.py`). In the IDE: the launchers **Rachana Studio** and **Rachana Studio (server-demo)**.

| Option | Meaning |
|---|---|
| `folder` | the folder of `*.sutra.yaml` files (default: the current folder) |
| `--data <dir>` | the tables the preview reads (`<name>.csv`, `.json`, ...); default: the folder's `data/` |
| `--libs <path>,...` | more libraries besides the bundled ones, `<folder>/rachana_libs` and `RACHANA_LIBS` ([Libraries](LIBRARIES.md#2-where-libraries-are-found)) |
| `--port <n>` | the port (default: any free port) |

## 2. The screen

| Area | What it does |
|---|---|
| **Sutra** (top) | opens one of the folder's Sutras |
| **Fragments** (left) | every fragment of every library found, by category, with a search box |
| **Live preview** (middle) | the real `<rachana-view>` painting the Sutra over the folder's data, redrawn as you edit |
| **Sutra source** (right) | the YAML in a code editor; the compiler checks it at the level its `rachana:` key declares, problems marked on their lines |
| **Fragment properties** (right) | the chosen fragment's parameters as a form: typed fields, the tables of the folder's data files, and a column mapping that proposes your columns for the fragment's; **Insert into Sutra** adds the `use:` block |
| **Changes** (right) | the unsaved changes as a diff |
| **Save** | writes the Sutra back into the folder (Ctrl+S); nothing else is ever written |

Save writes only a `*.sutra.yaml` path inside the opened folder. The assistant bar ("Describe a screen...") is reserved for AI
authoring and does not generate yet.

## 3. A first edit

The tutorial [Your first dashboard](../tutorials/FIRST_DASHBOARD.md) (step 5) inserts a library fragment with Studio and shows the YAML
it writes; [Build and use a library](../tutorials/LIBRARY.md) shows your own library in the palette.
