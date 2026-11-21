# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""Chromium check of the live-orders board under the Flask adapter: the page must paint, orders must keep arriving over SSE (the
open-orders tile changes without a reload), and there must be no console error, failed request or HTTP error. Needs the runtime bundle
(python3 tools/js/build_js.py).

    uv run --with flask --with playwright==1.64.0 python examples/live-orders/paint_check.py       (once: playwright install chromium)"""
import sys
import threading
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path[:0] = [str(HERE), str(HERE.parents[1] / "python" / "src")]
from playwright.sync_api import sync_playwright  # noqa: E402
from werkzeug.serving import make_server  # noqa: E402

import flask_app  # noqa: E402

srv = make_server("127.0.0.1", 0, flask_app.app, threaded=True)
threading.Thread(target=srv.serve_forever, daemon=True).start()
errs, tiles = [], []
SHADOW = "document.querySelector('rachana-view elements-view')?.shadowRoot"
with sync_playwright() as p:
    b = p.chromium.launch()
    pg = b.new_page()
    pg.on("console", lambda m: errs.append(m.text) if m.type == "error" else None)
    pg.on("pageerror", lambda e: errs.append(str(e)))
    pg.on("requestfailed", lambda r: errs.append("request failed: " + r.url))
    pg.on("response", lambda r: errs.append(f"HTTP {r.status}: {r.url}") if r.status >= 400 else None)
    pg.goto(f"http://127.0.0.1:{srv.server_address[1]}/dash/page/orders")
    try:
        pg.wait_for_function(f"(({SHADOW})?.textContent || '').trim().length > 100", timeout=15000)
        for _ in range(8):                                        # the board changes while we look: the text differs between samples
            tiles.append(pg.evaluate(f"{SHADOW}.textContent.replace(/\\s+/g, ' ')"))
            pg.wait_for_timeout(500)
    except Exception as e:                                        # noqa: BLE001
        errs.append("nothing painted: " + str(e)[:120])
    b.close()
srv.shutdown()
changed = len(set(tiles)) > 1
print(f"painted: {bool(tiles)}; text changed while open (live): {changed}; console/request problems: {len(errs)}")
for e in errs:
    print("  " + e)
sys.exit(0 if tiles and changed and not errs else 1)
