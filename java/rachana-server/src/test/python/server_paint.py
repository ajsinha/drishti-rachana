# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""The standalone server in Chromium (ServerEndToEndTest drives it): open a Sutra's page, sign in, the view paints from the Data
Protocol; then wait for a live change. Prints PAINTED when the first paint is there (the test then changes the table's file) and LIVE
when the change has painted; any console error, failed request or HTTP error fails it.
Run: uv run --with playwright==1.64.0 python server_paint.py <base url> <user> <password> <text to wait for after the change>"""
import sys
from playwright.sync_api import sync_playwright

base, user, password, changed = sys.argv[1:5]
errs = []
TEXT = "() => (document.querySelector('rachana-view elements-view')?.shadowRoot?.textContent || '')"


def wait_for(pg, text, ms):
    """Polls the painted text from Python: the page's CSP has no 'unsafe-eval', which Playwright's wait_for_function would need."""
    for _ in range(ms // 100):
        if text in pg.evaluate(TEXT):
            return True
        pg.wait_for_timeout(100)
    return False

with sync_playwright() as p:
    b = p.chromium.launch()
    pg = b.new_page()
    pg.on("console", lambda m: errs.append("console: " + m.text) if m.type == "error" else None)
    pg.on("pageerror", lambda e: errs.append("page error: " + str(e)))
    pg.on("requestfailed", lambda r: errs.append("request failed: " + r.url) if "/data/v1/changes" not in r.url else None)
    pg.on("response", lambda r: errs.append(f"HTTP {r.status}: {r.url}") if r.status >= 400 and not r.url.endswith("/signin") else None)
    pg.goto(base + "/v/sales")
    pg.fill("#user", user)
    pg.fill("#password", password)
    pg.click("button[type=submit]")
    pg.wait_for_url(base + "/v/sales")
    if not wait_for(pg, "South", 20000):
        print("NOT PAINTED: " + pg.evaluate(TEXT)[:300] + " | " + " | ".join(errs), flush=True)
        sys.exit(1)
    print("PAINTED " + pg.evaluate(TEXT)[:200].replace("\n", " "), flush=True)
    if not wait_for(pg, changed, 20000):
        print("NO LIVE UPDATE: " + pg.evaluate(TEXT)[:300] + " | " + " | ".join(errs), flush=True)
        sys.exit(1)
    print("LIVE " + pg.evaluate(TEXT)[:200].replace("\n", " "), flush=True)
    b.close()
if errs:
    print("ERRORS " + " | ".join(errs), flush=True)
    sys.exit(1)
print("OK", flush=True)
