<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary: see LICENSE. -->
Pinned third-party releases, copied unchanged, never loaded from a CDN.

| Folder | What | How it was made |
|---|---|---|
| `drishti-elements/3.1.0/` | Drishti Elements 3.1.0: `drishti-core`, `drishti-views` (the painter, `<elements-view>`), `drishti-events`, one script and one stylesheet, `drishti-elements-pack.json` with their SHA-384 | `python3 tools/elements/cli.py pack --libs core,views,events --no-engines` on the `v3.1.0` tag of drishti-elements |
| `apache-arrow/21.1.0/` | **Apache Arrow JS 21.1.0** (Apache-2.0; `LICENSE`, `NOTICE` beside it): `Arrow.es2015.min.js`, the npm package's ES2015 UMD build (`window.Arrow` in a page; `require` in Node), 178,919 bytes, sha384 `fec07eec1aa5968e355e9ae768e5146afdc3d12d272293650c764fce1ae0c864183b3ecb7dba9d320a91b202f048fadb`. The data client reads Arrow IPC answers with it (`arrowPage`); loaded only by a page that asks for `form="arrow"` | the same file Drishti Elements vendors (`vendor/apache-arrow/21.1.0`, from `npm i --ignore-scripts apache-arrow@21.1.0`, its last line `//# sourceMappingURL=...` removed); `grep -E 'https?://'` finds only the licence URL in its header |
| `drishti-elements/2.4.0/` | data files of an earlier pin (formats, world regions) | copied |

Chart panels need `drishti-chart` and its engine; they are not in this pack yet (a page that draws charts packs `chart` as well).
