#!/usr/bin/env python3
# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana Libraries. Proprietary: see the LICENSE file at the root of this repository.
"""Chromium paint test of the library gallery (tools/libraries/gallery.py): builds the gallery of each library, serves it, opens the
index and a handful of fragment pages (every page with --all) and requires no console error, a painted page, the page's own parts
(parameters, example source) and the fragment's live example text.
Run: uv run --with playwright==1.64.0 --with pyyaml python tools/libraries/gallery_check.py [--all] [--out DIR]   (once: playwright install chromium)"""
import argparse, functools, http.server, shutil, sys, tempfile, threading
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import gallery
from playwright.sync_api import sync_playwright

# fragment -> a word its live example paints (from the example's own data)
PROBES = {"key-figure": "Total revenue", "ranked-bars": "Ranking", "data-table": "Order", "network-graph": "Network",
          "steps-waterfall": "Budget to actual", "record-card": "FOUNDED", "scatter-plot": "Scatter"}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--all", action="store_true")
    ap.add_argument("--out")
    a = ap.parse_args()
    out = Path(a.out or tempfile.mkdtemp(prefix="rachana-gallery-"))
    bad = 0
    root = gallery.ROOT / "libraries"
    sites = {lib.name: gallery.build(lib, out) for lib in sorted(p for p in root.iterdir() if p.is_dir())}
    handlers = {}
    with sync_playwright() as p:
        b = p.chromium.launch()
        for lib, site in sites.items():
            class Quiet(http.server.SimpleHTTPRequestHandler):
                def log_message(self, *x): pass
            srv = http.server.ThreadingHTTPServer(("127.0.0.1", 0), functools.partial(Quiet, directory=str(site)))
            threading.Thread(target=srv.serve_forever, daemon=True).start()
            pages = sorted(x.stem for x in site.glob("*.html") if x.stem != "index")
            chosen = pages if a.all else ["library"] + [n for n in PROBES if n in pages]
            for name in chosen:
                pg = b.new_page()
                errs = []
                pg.on("console", lambda m: errs.append(m.text) if m.type == "error" and "404" not in m.text else None)
                pg.on("pageerror", lambda e: errs.append(str(e)))
                # the vendored Elements pack carries no grid bundle (a pack limit, not a gallery one): the request for it is the one tolerated 404
                pg.on("response", lambda r: errs.append(f"HTTP {r.status}: {r.url}") if r.status >= 400 and "drishti_grid.js" not in r.url else None)
                pg.goto(f"http://127.0.0.1:{srv.server_address[1]}/{name}.html")
                text = ""
                try:
                    pg.wait_for_function("(document.querySelector('rachana-view elements-view')?.shadowRoot?.textContent || '').trim().length > 100", timeout=10000)
                    pg.wait_for_timeout(400)
                    text = pg.evaluate("document.querySelector('rachana-view elements-view').shadowRoot.textContent")
                except Exception as e:
                    errs.append("nothing painted: " + str(e)[:80])
                want = ["Parameters", "Example source"] if name != "library" else ["fragments, version"]
                if name in PROBES:
                    want.append(PROBES[name])
                errs += [f"missing '{w}'" for w in want if w not in text]
                if name == "library":
                    manifest, frags = gallery.exports(root / lib)
                    used = {f.get("category") for f in frags}
                    for c in manifest.get("categories", []):
                        if c["id"] in used and c["title"] not in text:
                            errs.append(f"category '{c['title']}' missing")
                    hrefs = pg.evaluate("[...document.querySelectorAll('nav.gallery-nav a')].map(a => a.getAttribute('href'))")
                    errs += [f"no link to {f['name']}.html" for f in frags if f"{f['name']}.html" not in hrefs]
                    errs += [f"fragment '{f['name']}' not listed" for f in frags if f["name"] not in text]
                print(f"{lib}/{name}: {'ok' if not errs else 'FAIL'} ({len(text)} chars){''.join(chr(10) + '  ' + e for e in errs)}")
                bad += bool(errs)
                pg.close()
            srv.shutdown()
        b.close()
    if not a.out:
        shutil.rmtree(out, ignore_errors=True)
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
