<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary. All rights reserved. -->
# Tutorial: your first dashboard

You will make a sales dashboard from a CSV file: write a Sutra, check it, preview it, add a library fragment in Studio, and build a
static site that any web server can serve. About 20 minutes. Guides behind each step: [the language](../guides/LANGUAGE.md),
[CLI and SDKs](../guides/CLI_AND_SDKS.md), [Studio](../guides/STUDIO.md).

## Prepare your environment

| You need | Check |
|---|---|
| JDK 21 (to build the compiler bundles) | `java -version` |
| Node 20 or later | `node --version` |
| Python 3 | `python3 --version` |

From the repository root, build the bundles once (a few minutes; they land in `js/packages/rachana/dist/`):

```bash
python3 tools/js/build_js.py
```

In IntelliJ IDEA, PyCharm or VS Code the same build is the launcher **Rachana JS runtime bundle** ([java/README.md](../../java/README.md#build-and-run-in-the-ide)).
Below, `rachana` stands for `node <repository>/js/packages/rachana-cli/bin/rachana.js`; on Linux or macOS:

```bash
alias rachana="node $PWD/js/packages/rachana-cli/bin/rachana.js"
```

On Windows (PowerShell): `function rachana { node C:\path\to\drishti-rachana\js\packages\rachana-cli\bin\rachana.js @args }`.

## Step 1: a starter project

From the command line, in a folder of your choice:

```text
$ rachana new my-dash
created my-dash (4 files). Next: cd my-dash && rachana lint
$ cd my-dash
$ rachana lint
1 file(s), 0 failing problem(s)
```

The folder holds `sutras/overview.sutra.yaml` (two figures and a note, declaring the newest level, `rachana: 1.2`), `data/sales.csv`,
`rachana.yaml` and a `README.md`.

## Step 2: your data

Copy the example's sales table over the starter's (120 rows: `region, product, month, units, revenue`; synthetic figures):

```bash
cp <repository>/examples/static-sales/data/sales.csv data/sales.csv
```

A `table: sales` entry in a Sutra reads `data/sales.csv`; the first line names the columns.

## Step 3: write the Sutra

Replace `sutras/overview.sutra.yaml` with:

```yaml
rachana: 1.2
sutra: overview
version: 1
title: { id: "'Sales overview'" }
data:
  sales: { table: sales, key: [region, product, month] }
controls:
  - { id: region, kind: dropdown, label: Region, options: ["North", "South", "East", "West", "Central"], default: first }
panels:
  - { id: revenue, kind: metric, label: Revenue, value: "sum($.sales, 'revenue')", fmt: amount0, span: 4 }
  - { id: units, kind: metric, label: Units, value: "sum($.sales, 'units')", fmt: amount0, span: 4 }
  - { id: region-rev, kind: metric, label: "Revenue, selected region", value: "sum($.sales[?@.region == ctl.region], 'revenue')", fmt: amount0, span: 4 }
  - { id: by-region, kind: hbar, title: Revenue by region, rows: "group($.sales, 'region', 'revenue')", label: label, value: value, fmt: amount0, span: 6 }
  - { id: trend, kind: line, title: Revenue by month, rows: "group($.sales, 'month', 'revenue')", x: label, y: value, fmt: amount0, span: 6 }
```

Three figures, one of them following the **Region** dropdown (`ctl.region`), a bar per region and a line over the months. `group`
returns rows of `label`, `value` and `count`.

## Step 4: check it

```text
$ rachana lint
1 file(s), 0 failing problem(s)
```

Make a mistake on purpose: change `sum($.sales, 'units')` to `summ($.sales, 'units')` and lint again:

```text
$ rachana lint
sutras/overview.sutra.yaml:11:5: error RCH-2101: expression 'summ($.sales, 'units')': unknown function 'summ'; known: abs, accrued, ...
1 file(s), 1 failing problem(s)
```

The exit status is 1, so the same command stops a CI job (`--format sarif` for code-scanning tools). Put `sum` back.

**In the UI:** start the preview and open the address it prints. Choose another region in the dropdown: the third figure changes.

```text
$ rachana preview sutras/overview.sutra.yaml --data data --port 4400
serving http://127.0.0.1:4400/page/overview  (Ctrl+C to stop)
```

## Step 5: add a library fragment

The `core` library holds ready-made screens ([its page](../../libraries/core/README.md)). Add a ranking of products.

**In the UI (Studio):**

```text
$ rachana studio sutras --data data --port 4500
studio http://127.0.0.1:4500/  (core 1.0.0, finance 1.0.0; Ctrl+C to stop)
```

1. Open `http://127.0.0.1:4500/`, choose `overview.sutra.yaml` in **Sutra**.
2. In **Fragments**, search `ranked` and choose **ranked-bars** (core).
3. In **Fragment properties**, set the title to `Revenue by product`, choose the table `sales` and map `label` to `product` and
   `value` to `revenue`; press **Insert into Sutra**. The `use:` block appears in the source, `libraries: [core@1]` is added, and
   the preview shows the new panel.
4. A ranking should have one bar per product, so in the source change the `rows:` line to the grouped rows (below). **Changes** shows
   the diff; press **Save** (Ctrl+S).

**From the command line (or any editor):** add `libraries: [core@1]` after `version: 1`, and at the end of `panels`:

```yaml
  - use: core/ranked-bars@1
    as: products
    with:
      title: Revenue by product
      rows: "group($.sales, 'product', 'revenue')"
```

`rows` is the fragment's table parameter: the grouped rows already have the columns the fragment names (`label`, `value`), so no
mapping is needed. To bind a table whose columns have other names, map them: `rows: { from: $.sales, columns: { label: product,
value: revenue } }` ([fragments as functions](../guides/LANGUAGE.md#7-fragments-and-fragments-as-functions)). Lint again: `1 file(s), 0
failing problem(s)`.

## Step 6: build the site

```text
$ rachana build --out site
built site: 1 Sutra(s), 1 table file(s)
$ cd site && python3 -m http.server 8000
```

Open `http://127.0.0.1:8000/overview.html`. The `site/` folder holds `overview.html`, `index.html`, the Sutra, `data/sales.csv` with
`data/index.json`, the runtime and the Drishti Elements painter: no backend, any static web server (a browser does not fetch from
`file://`). The page filters, sorts and pages the table itself.

## Next

- Serve the same screens with sign-in, databases and live updates: [Edit a live table with approvals](LIVE_EDITS.md).
- Package your own fragments: [Build and use a library](LIBRARY.md).
- The finished three-page example with every kind of `data:` entry: [examples/static-sales](../../examples/static-sales/README.md).
