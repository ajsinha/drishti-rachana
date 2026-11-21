# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""<rachana-view> and a Sutra's state (level 1.2) in Chromium: a click on a bar of an hbar writes the state `desk` (the bar's own set, from the
ViewModel); the painter sends it back to the runtime bundle, which builds the view again; only the panels that read the state are drawn again
(the bar panel stays the same element) and the dependent panels show the new desk.

Usage: uv run --with playwright==1.64.0 python js/packages/rachana/runners/state_browser.py [--browsers chromium,firefox,webkit]
Needs: python3 tools/js/build_js.py (the runtime bundle in js/packages/rachana/dist); the browsers as for view_browser.py."""
import functools
import http.server
import pathlib
import sys
import threading

from playwright.sync_api import sync_playwright

ROOT = pathlib.Path(__file__).resolve().parents[4]
METRIC = "[data-panel=%s] .dv-metric-value"


class Quiet(http.server.SimpleHTTPRequestHandler):
    def log_message(self, *args):
        pass


def main(argv):
    browsers = argv[argv.index("--browsers") + 1].split(",") if "--browsers" in argv else ["chromium"]
    server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), functools.partial(Quiet, directory=str(ROOT)))
    threading.Thread(target=server.serve_forever, daemon=True).start()
    origin = "http://127.0.0.1:%d" % server.server_address[1]
    bad = 0
    try:
        with sync_playwright() as p:
            for name in browsers:
                browser = getattr(p, name).launch()
                page = browser.new_page()
                errors = []
                page.on("pageerror", lambda e: errors.append(str(e)))
                page.goto(origin + "/js/packages/rachana/test/state.html")
                try:
                    page.wait_for_function("window.__events.some(e => e.name === 'ready' || e.name === 'error')", timeout=30000)
                    page.wait_for_selector(METRIC % "chosen", timeout=10000)
                    assert page.inner_text(METRIC % "chosen") == "FX" and page.inner_text(METRIC % "pnl") == "3", "the default state"
                    page.evaluate("() => { const R = document.querySelector('elements-view').shadowRoot; window.__bars = R.querySelector('[data-panel=by-desk] .dv-bars'); }")
                    page.locator("[data-panel=by-desk] li", has_text="Rates").click()
                    page.wait_for_function("window.__bound.length > 0", timeout=10000)
                    assert page.inner_text(METRIC % "chosen") == "Rates", "the dependent panel follows the click"
                    assert page.inner_text(METRIC % "pnl") == "7", "and the panel that reads the state through a table"
                    assert page.evaluate("() => window.__bound[0].full === false && window.__bound[0].panels.indexOf('by-desk') < 0"), "only the panels that read the state were drawn again"
                    assert page.evaluate("() => document.querySelector('elements-view').shadowRoot.querySelector('[data-panel=by-desk] .dv-bars') === window.__bars"), "the bars were not drawn again"
                    page.locator("[data-panel=by-desk] li", has_text="Credit").click()
                    page.wait_for_function("window.__bound.length > 1", timeout=10000)
                    assert page.inner_text(METRIC % "pnl") == "5", "a second click"
                    errs = [e for e in page.evaluate("window.__events") if e["name"] == "error"] + errors
                    print(f"{name} {browser.version}: a click on a bar wrote state, the dependent panels followed (bound {page.evaluate('window.__bound')}){'; errors ' + str(errs) if errs else ''}")
                    bad += 1 if errs else 0
                except Exception as e:  # noqa: BLE001 - one browser's failure is reported, the others still run
                    print(f"{name}: FAILED: {e}; events {page.evaluate('window.__events')}; page errors {errors}", file=sys.stderr)
                    bad += 1
                browser.close()
    finally:
        server.shutdown()
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
