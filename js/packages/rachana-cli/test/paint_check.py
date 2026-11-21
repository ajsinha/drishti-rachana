# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""Chromium check: `rachana build` static-sales, serve the site, open every page; it must paint with no console errors.
Run: uv run --with playwright==1.64.0 python js/packages/rachana-cli/test/paint_check.py   (once: playwright install chromium)"""
import http.server, subprocess, sys, tempfile, threading, functools
from pathlib import Path
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[4]
site = Path(tempfile.mkdtemp(prefix="rachana-site-"))
subprocess.run(["node", str(ROOT / "js/packages/rachana-cli/bin/rachana.js"), "build", "--src", str(ROOT / "examples/static-sales"), "--out", str(site)], check=True)
class Quiet(http.server.SimpleHTTPRequestHandler):
    def log_message(self, *a): pass
H = functools.partial(Quiet, directory=str(site))
srv = http.server.ThreadingHTTPServer(("127.0.0.1", 0), H)
threading.Thread(target=srv.serve_forever, daemon=True).start()
bad = 0
with sync_playwright() as p:
    b = p.chromium.launch()
    for page_name in sorted(x.stem for x in site.glob("*.html") if x.stem != "index"):
        pg = b.new_page()
        errs = []
        pg.on("console", lambda m: errs.append(m.text) if m.type == "error" else None)
        pg.on("pageerror", lambda e: errs.append(str(e)))
        pg.on("requestfailed", lambda r: errs.append("request failed: " + r.url))
        pg.on("response", lambda r: errs.append(f"HTTP {r.status}: {r.url}") if r.status >= 400 else None)
        pg.goto(f"http://127.0.0.1:{srv.server_address[1]}/{page_name}.html")
        try:
            pg.wait_for_function("(document.querySelector('rachana-view elements-view')?.shadowRoot?.textContent || '').trim().length > 100", timeout=8000)
            pg.wait_for_timeout(500)
            n = pg.evaluate("document.querySelector('rachana-view elements-view').shadowRoot.textContent.trim().length")
        except Exception as e:
            errs.append("nothing painted: " + str(e)[:80] + " len=" + str(pg.evaluate("(document.querySelector('rachana-view elements-view')?.shadowRoot?.textContent||'').length")))
            n = 0
        ok = not errs and n > 20
        print(f"{page_name}: {'ok' if ok else 'FAIL'} ({n} chars){''.join(chr(10) + '  ' + e for e in errs)}")
        bad += 0 if ok else 1
    b.close()
subprocess.run(["rm", "-rf", str(site)])
sys.exit(1 if bad else 0)
