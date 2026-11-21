<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary: see LICENSE. -->
Pinned third-party releases, copied unchanged, never loaded from a CDN.

| Folder | What | How it was made |
|---|---|---|
| `drishti-elements/3.1.0/` | Drishti Elements 3.1.0: `drishti-core`, `drishti-views` (the painter, `<elements-view>`), `drishti-events`, `drishti-inputs` (controls), `drishti-grid` (tables; pulls `drishti-chart` without its engine), one script and one stylesheet, and the files they read at run time under `vendor/`, `drishti-elements-pack.json` with their SHA-384 | `python3 tools/elements/cli.py pack --libs core,views,events,inputs,grid --no-engines` on the `v3.1.0` tag of drishti-elements |
| `drishti-elements/2.4.0/` | data files of an earlier pin (formats, world regions) | copied |

Chart panels need the engine (ECharts), which `--no-engines` leaves out: a page that draws charts packs it as well.
