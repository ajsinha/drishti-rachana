<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary. All rights reserved. -->
# Tutorial: build and use a library

You will write a typed fragment, package it as a signed library, pin it in a lock file and use it from a Sutra, with your own column
names mapped onto the fragment's. Guides: [Libraries and `rachana lib`](../guides/LIBRARIES.md) and
[fragments as functions](../guides/LANGUAGE.md#7-fragments-and-fragments-as-functions).

## Prepare your environment

Do the preparation of [Your first dashboard](FIRST_DASHBOARD.md#prepare-your-environment) (bundles built, `rachana` alias). The library
commands also need the Java modules, built once from the repository root, and the launcher variable:

```bash
cd java && ./mvnw clean package -DskipTests && cd ..
export RACHANA_CLASSPATH="$(ls $PWD/java/rachana-{cli,tools,core,api}/target/rachana-*.jar | grep -v -e sources -e tests | tr '\n' ':')"
```

Windows: [the Java launcher](../guides/CLI_AND_SDKS.md#the-java-launcher). Work in a new folder `shop-project` with
`data/sales.csv` copied from `examples/static-sales/data/`.

## Step 1: the library's manifest

`rachana_libs/shop/shop.rachana-lib.yaml`:

```yaml
rachana: 1
library: shop
version: 1.0.0
title: Shop
summary: Screens for a small shop.
publisher: { name: "Example Shop" }
licence: LicenseRef-Example
scope: public
profiles: []
depends: []
needs: { language: "1.2", contract: "1.0" }
categories:
  - { id: tables, title: Tables }
```

## Step 2: a typed fragment

`rachana_libs/shop/fragments/top-rows.v1.fragment.yaml` (`v1` is the major):

```yaml
rachana: 1.2
fragment: top-rows
version: 1
doc:
  summary: The largest rows of a table by one column.
  description: >-
    A short ranked list. Use it for a top-N beside a chart; for every row with search and totals use core/data-table.
  category: tables
params:
  rows:  { type: table, required: true, doc: The rows, columns: { name: { type: text, doc: "The row's name" }, value: { type: number, doc: The figure } } }
  limit: { type: integer, default: 5, min: 1, max: 50, doc: How many rows }
  fmt:   { type: format, default: amount0, doc: How the figure is written }
emits:
  - { name: pick, payload: { name: text }, doc: A row was chosen }
panels:
  - id: list
    kind: table
    rows: "take(sort(arg.rows, @.value, true), arg.limit)"
    columns: [ { label: Name, bind: "@.name" }, { label: Value, bind: "@.value", fmt: "%{fmt}" } ]
    emits: [ { name: pick, on: row-click, payload: { name: "@.name" } } ]
```

The fragment knows only its own names: a table with columns `name` and `value`, read as `arg.rows`; `fmt` is a name parameter placed
as `%{fmt}`. It returns one event, `pick`.

## Step 3: an example and its golden

Every fragment ships an example Sutra. `rachana_libs/shop/examples/top-rows.sutra.yaml`:

```yaml
rachana: 1.2
sutra: best-products
version: 1
libraries: [shop@1]
title: { id: "'Best products'" }
data:
  sales: { rows: [ { product: Kettle, revenue: 120 }, { product: Toaster, revenue: 80 } ] }
panels:
  - use: shop/top-rows@1
    as: best
    with:
      rows: { from: $.sales, columns: { name: product, value: revenue } }
      limit: 8
  - { id: picked, kind: markdown, text: "Picked: ${@best.pick.name}" }
```

Build the library (writes `exports` into the manifest and `MANIFEST.sha384`), record the example's ViewModel as its golden, and build
again so the golden is in the manifest:

```text
$ rachana lib build rachana_libs/shop
built rachana_libs/shop
$ java -cp "$RACHANA_CLASSPATH" <repository>/tools/libraries/LibCheck.java rachana_libs --update
ok   shop/top-rows
1 passed, 0 failed
$ rachana lib build rachana_libs/shop
built rachana_libs/shop
```

## Step 4: the quality gates

```text
$ rachana lib gate rachana_libs/shop
pass LG1 Library valid
pass LG2 Frozen fragments
     . nothing released yet: no FROZEN.sha384
pass LG3 Definition of done
     . top-rows: 1 use in its example; two are required, a minimal and a full one
...
pass LG8 Budgets
```

A failing gate names what is missing (a `doc.description`, a golden); the lines marked `.` are notes, failures only with `--strict`.

## Step 5: sign, pack, verify, lock

```text
$ rachana lib keygen
public  ed25519:10c4...0db
private 2593...cb8   (keep it secret)
$ rachana lib sign rachana_libs/shop ed25519:10c4...0db 2593...cb8
signed rachana_libs/shop
$ rachana lib pack rachana_libs/shop --out dist --require-signed --publisher ed25519:10c4...0db
dist/shop-1.0.0.rlib
$ rachana lib verify dist/shop-1.0.0.rlib --require-signed --publisher ed25519:10c4...0db
ok dist/shop-1.0.0.rlib
$ rachana lib lock dist/shop-1.0.0.rlib --lock rachana.lock
wrote rachana.lock (1 added)
```

Your keys differ; keep the private seed out of the repository. `rachana.lock` pins the exact version and the SHA-384 of the archive:

```yaml
lock: 1
libraries:
  shop: { version: 1.0.0, sha384: "77f9...dd74", source: "release:shop-1.0.0.rlib", licence: LicenseRef-Example }
```

## Step 6: use it from a Sutra

`sutras/best.sutra.yaml`, over the real sales table whose columns are `product` and `revenue`:

```yaml
rachana: 1.2
sutra: best-products
version: 1
libraries: [shop@1]
title: { id: "'Best products'" }
data:
  sales: { table: sales, key: [region, product, month] }
panels:
  - use: shop/top-rows@1
    as: best
    with:
      rows: { from: $.sales, columns: { name: product, value: revenue } }   # the fragment's column: your column
      limit: 8
  - { id: picked, kind: markdown, text: "Picked: ${@best.pick.name}" }
```

```text
$ rachana lint
2 file(s), 0 failing problem(s)
$ rachana build --out site
```

`rachana lint` and `build` run from the project folder read `rachana_libs/` there; another folder is `--libs <path>` or
`RACHANA_LIBS`. Map a column the fragment does not have, or forget a required one, and lint names it (`RCH-2114`, `RCH-2115`).

**In the UI:** `rachana studio sutras --data data` lists **top-rows** (shop) in **Fragments** beside the core and finance fragments;
its form offers your `sales` table and maps `name` and `value` onto its columns.

## Next

- The libraries shipped with Rachana: [core](../../libraries/core/README.md), [finance](../../libraries/finance/README.md).
- Move older Sutras onto level 1.2 to use typed fragments: [rachana upgrade](../guides/UPGRADE.md).
