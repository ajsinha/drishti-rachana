<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary. All rights reserved. -->
# static-sales: CSV files and Sutras in, a dashboard site out

A three-page sales dashboard built from three CSV files and three Sutras, with no server of its own: `overview` (four tiles, bars by
region, a monthly trend, a top-8 table), `region` (a region dropdown, an office card, bars by product) and `product` (a product
dropdown, bars by region). It uses every `data:` entry form (`table`, `url`, `rows`, `record`). All data is synthetic (fictional
regions, products and figures). Walk-through: [Tutorial, part 1](../../docs/TUTORIAL.md#part-1-your-first-dashboard).

| File | What |
|---|---|
| `data/sales.csv` | 120 rows: `region, product, month, units, revenue`; key `region, product, month` |
| `data/targets.csv` | 30 rows: `region, month, target`; key `region, month` |
| `data/regions.csv` | 5 rows: `code, name, office, hub, staff` (a lookup) |
| `overview.sutra.yaml`, `region.sutra.yaml`, `product.sutra.yaml` | the three pages |

## Prerequisites

- Node.js 20 or later (checked with 22). That is all: no Python, no Java, no `npm install` (the command line has no dependencies).
- The compiler bundle is committed (`python/src/drishti_rachana/bundle/rachana.tools.js`); nothing to build.
- A modern browser.

## Run

In the commands below `rachana` is `node ..\..\js\packages\rachana-cli\bin\rachana.js` (Windows) or
`node ../../js/packages/rachana-cli/bin/rachana.js` (Linux, macOS).

Windows:

```
cd drishti-platform\drishti-rachana\examples\static-sales
node ..\..\js\packages\rachana-cli\bin\rachana.js lint
node ..\..\js\packages\rachana-cli\bin\rachana.js dev . --port 4711
```

Linux and macOS:

```
cd drishti-platform/drishti-rachana/examples/static-sales
node ../../js/packages/rachana-cli/bin/rachana.js lint
node ../../js/packages/rachana-cli/bin/rachana.js dev . --port 4711
```

`lint` prints `3 file(s), 0 failing problem(s)`. `dev` serves the folder and prints `serving http://127.0.0.1:4711/`. Open
<http://127.0.0.1:4711/> and choose **overview**, **region** or **product**.

To write the finished static site instead (a folder `site/` for any web server; a browser does not load it from `file://`):

```
node ../../js/packages/rachana-cli/bin/rachana.js build          # built site: 3 Sutra(s), 3 table file(s)
py -3.13 -m http.server 4712 --directory site                    # Windows;  python3.13 -m http.server 4712 --directory site on Linux
```

then open <http://127.0.0.1:4712/overview.html>. Delete `site/` when done (it is generated).

## What you should see

`overview`: tiles Revenue, Units sold, Target attainment and June revenue; five bars (one per region); a line of six months; a table
of the eight best region and product pairs. `region` and `product`: choose a name in the dropdown and the tiles, bars, card and table
recompute at once, with no reload and no server round trip.

## Stop

Press Ctrl+C in the window that runs `dev` (or end its process by id). `build` ends by itself.

## Troubleshooting

| You see | Cause and fix |
|---|---|
| `node` is not recognised | Node.js 20 or later is not installed or not on the PATH: install it, open a new window |
| `rachana.tools.js is missing: build it with ...` | `python\src\drishti_rachana\bundle\rachana.tools.js` is missing from your copy: unzip the platform again (or `git checkout` it); to rebuild: `python3 tools/js/build_js.py` (JDK 21) |
| `EADDRINUSE` / port in use | choose another: `--port 4720` |
| the built site shows a blank page | it was opened from `file://`; serve the `site` folder as shown above |
| `lint` reports a problem | you edited a Sutra or CSV: the message names the file, line and rule ([Language](../../docs/WRITING_SUTRAS.md#the-rachana-language-level-12)) |

More about the files and the Sutra keys: [examples index](../README.md).
