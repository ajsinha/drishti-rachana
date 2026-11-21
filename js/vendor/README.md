<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary: see LICENSE. -->
Pinned third-party releases, copied unchanged, never loaded from a CDN.

| Folder | What | How it was made |
|---|---|---|
| `drishti-elements/3.1.0/` | Drishti Elements 3.1.0: `drishti-core`, `drishti-views` (the painter, `<elements-view>`), `drishti-events`, one script and one stylesheet, `drishti-elements-pack.json` with their SHA-384 | `python3 tools/elements/cli.py pack --libs core,views,events --no-engines` on the `v3.1.0` tag of drishti-elements |
| `drishti-elements/2.4.0/` | data files of an earlier pin (formats, world regions) | copied |

Chart panels need `drishti-chart` and its engine; they are not in this pack yet (a page that draws charts packs `chart` as well).
