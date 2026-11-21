<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary: see LICENSE. -->
Pinned third-party releases, copied unchanged, never loaded from a CDN.

| Folder | What | How it was made |
|---|---|---|
| `drishti-elements/3.1.0/` | Drishti Elements 3.1.0: `drishti-core`, `drishti-views` (the painter, `<elements-view>`), `drishti-events`, `drishti-chart` (chart panels: line, area, waterfall, histogram, scatter, candlestick, surface, map, the hbar donut) with `drishti-inputs` (which it needs) and its engine (Apache ECharts 6.1.0, bundled into the script); one script (2,226,134 bytes) and one stylesheet; `vendor/` holds the files the libraries read at run time (CodeMirror for inputs' code field, ECharts GL for 3D traces, loaded only when drawn), each with its licence; `drishti-elements-pack.json` lists every file with its SHA-384 | `python3 tools/elements/cli.py pack --libs core,views,events,chart` on the `v3.1.0` tag of drishti-elements (engines included, no `--maps`: a map panel's shapes are the host's to serve) |
| `apache-arrow/21.1.0/` | **Apache Arrow JS 21.1.0** (Apache-2.0; `LICENSE`, `NOTICE` beside it): `Arrow.es2015.min.js`, the npm package's ES2015 UMD build (`window.Arrow` in a page; `require` in Node), 178,919 bytes, sha384 `fec07eec1aa5968e355e9ae768e5146afdc3d12d272293650c764fce1ae0c864183b3ecb7dba9d320a91b202f048fadb`. The data client reads Arrow IPC answers with it (`arrowPage`); loaded only by a page that asks for `form="arrow"` | the same file Drishti Elements vendors (`vendor/apache-arrow/21.1.0`, from `npm i --ignore-scripts apache-arrow@21.1.0`, its last line `//# sourceMappingURL=...` removed); `grep -E 'https?://'` finds only the licence URL in its header |
| `drishti-elements/2.4.0/` | data files of an earlier pin (formats, world regions) | copied |

