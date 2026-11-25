<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary. All rights reserved. -->
# Examples

Three runnable examples, all with synthetic data (fictional regions, products, customers and figures). Detail on running them: [IN_YOUR_APP.md](../docs/IN_YOUR_APP.md) and [TUTORIAL.md](../docs/TUTORIAL.md). `python3 -I examples/check_examples.py` checks the inputs of all three against the specification (needs PyYAML and jsonschema).

## static-sales

The static-mode example: CSV files and Sutras in, a site out (tutorial: [Your first dashboard](../docs/TUTORIAL.md#part-1-your-first-dashboard)). **Synthetic data**: fictional
regions, products and figures; no real company or bank. The CSV files carry no header comment (it would break the
format); they are covered by the repository licence (`LICENSE`).

| File | What |
|---|---|
| `data/sales.csv` | 120 rows: `region, product, month, units, revenue`; 5 regions x 4 products x 6 months (2026-01 to 2026-06). Key: `region, product, month` |
| `data/targets.csv` | 30 rows: `region, month, target`. Key: `region, month` |
| `data/regions.csv` | 5 rows: `code, name, office, hub, staff`: a lookup |
| `overview.sutra.yaml` | four metric tiles (revenue, units, attainment against target, June against May), bar by region, trend line, top-8 table of region and product pairs |
| `region.sutra.yaml` | a region dropdown (read as `ctl.region`); tiles, an office card from the lookup, bars by product, monthly line, a detail table with totals |
| `product.sutra.yaml` | a product dropdown; tiles, a card from rows written in the file, bars by region, units by month, a note from a written record |

The Sutras use only Sutra Core keys, panel kinds and functions of `spec/1`, and all four `data:` entry forms
(`table`, `url`, `rows`, `record`; SPEC 2.4).

### Build it

```bash
cd examples/static-sales
rachana lint           # 3 file(s), 0 failing problem(s)
rachana build          # built site: 3 Sutra(s), 3 table file(s)
```

`rachana` is `node js/packages/rachana-cli/bin/rachana.js` ([CLI guide](../docs/IN_YOUR_APP.md#command-line-and-sdks)). It writes a folder `site/` that any static web server serves (a browser does not fetch files from `file://`; `rachana preview` serves it too): one page per Sutra (`overview.html`, `region.html`,
`product.html`), `<rachana-view>` with the Drishti Elements pack and the runtime bundle, and the three tables as static files, which the
page answers to the Data Protocol itself (`static_files_host.js`). The `table:` names `sales`, `targets` and `regions` resolve
to `data/sales.csv`, `data/targets.csv` and `data/regions.csv`. On `overview.html`: Revenue, Units sold, Target attainment and June revenue tiles; a bar per
region (five bars, in key order: group returns its rows by key); a line of six months; a table
of the eight best region and product pairs. Choosing a region or product on the other two pages recomputes locally.

Check the inputs alone (no compiler needed): `python3 -I examples/check_examples.py` from the repository root.

## server-demo

The standalone server's example (`java/rachana-server`): one Sutra, two tables from files (`data/sales.csv`, `data/regions.json`), sign-in
with a users file. **Synthetic data**: fictional regions, products and figures.

| File | What |
|---|---|
| `rachana-server.yaml` | the one configuration file: port 8090, loopback, tables, `auth.mode: users` |
| `users.yaml` | one user, `dev`, password `dev-only-pass`. **Development only**: written here so the example runs; it protects nothing; never reuse it |
| `project/sutras/overview.sutra.yaml` | the page |
| `project/data/` | the two tables |

```bash
java -jar java/rachana-server/target/rachana-server-<version>-all.jar check --config examples/server-demo/rachana-server.yaml
java -jar java/rachana-server/target/rachana-server-<version>-all.jar serve --config examples/server-demo/rachana-server.yaml   # http://127.0.0.1:8090/ , user dev
node js/packages/rachana-cli/bin/rachana.js studio examples/server-demo/project/sutras --data examples/server-demo/project/data --port 4500
```

In the IDE: the launchers `Rachana server serve (server-demo)`, `check`, `hash-password` (type a password in the Run window, press Enter) and
`Rachana Studio (server-demo)`; see docs/DEVELOPING.md (Build and run in the IDE).

## live-orders

The Python app example (tutorial: [A live board in Flask](../docs/TUTORIAL.md#part-4-a-live-board-in-flask)): the same live board under Flask and under FastAPI. **Synthetic
data**: fictional customers and items. The CSV has no header comment; it is covered by the repository licence.

| File | What |
|---|---|
| `orders_seed.csv` | 40 starting orders: `order_id, ts, customer, region, item, qty, price, amount, status`; key `order_id` |
| `gen_orders.py` | standard library only; reads the seed, then writes one keyed patch per line to stdout in Data Protocol v1 patch format (`spec/1/data-protocol/patch.json`): `add` new orders, `update` a status, `remove` a finished order (a key is a list) |
| `orders.sutra.yaml` | the board: four KPI tiles, a bar chart of value by status, a grid of the whole book (`data:` entry `orders`, `key: [order_id]`, `live: true`) |
| `flask_app.py`, `fastapi_app.py` | the adapters in use (`drishti_rachana.adapters`), about thirty lines each; the adapter serves the data, the page and the assets of `<rachana-view>` (`/dash/static/...`) |
| `paint_check.py` | Chromium check: the board paints, changes while open (SSE), no console error (`python3 paint_check.py` (flask and playwright installed in your Python)) |

```bash
python3 gen_orders.py --duration 5 --rate 4 --seed 7     # patches on stdout, no app needed
```

`python flask_app.py` or `uvicorn fastapi_app:app`, open `/dash/`; orders arrive, statuses move and the
tiles and grid change on screen without a reload. `gen_orders.OrderBook` is shared by both apps as the data source: the
adapters turn each patch into `(key, row)` changes (a `None` row removes) and publish them as `rdp.orders` messages.
