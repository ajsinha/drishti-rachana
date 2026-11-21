<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary. All rights reserved. -->
# static-sales (deliverable D1)

Demo inputs for static mode (docs/MODES.md 1): CSV files and Sutras in, a site out. **Synthetic data**: fictional
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
(`table`, `url`, `rows`, `record`; SPEC 2.4). `data:` is a planned key (decision D10): these files are written for it.

## What `rachana build` will produce (planned; B4)

```bash
cd examples/static-sales
rachana lint           # checks the three Sutras against spec/1 and the CSV columns
rachana build          # writes ./site
```

A folder `site/` that any static web server serves (a browser does not fetch files from `file://`; `rachana preview` serves it too): one page per Sutra (`overview.html`, `region.html`,
`product.html`), `<rachana-view>` with the Drishti Elements pack and the runtime bundle, and the three tables as static files, which the
page answers to the Data Protocol itself (`static_files_host.js`). The `table:` names `sales`, `targets` and `regions` resolve
to `data/sales.csv`, `data/targets.csv` and `data/regions.csv`. Expected on `overview.html`: Revenue, Units sold, Target attainment and June revenue tiles; a bar per
region (five bars, in key order: group returns its rows by key); a line of six months; a table
of the eight best region and product pairs. Choosing a region or product on the other two pages recomputes locally.

Check the inputs today (no compiler needed): `python3 -I examples/check_examples.py` from the repository root.
