# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""<rachana-view> with a form panel and an action button (level 1.2) in Chromium: a submit that breaks a rule shows the runtime's message and sets
no state; a submit that passes sets the state (the dependent panel follows) and hands the order to the host; an action button fires its effects.

Usage: python3 js/packages/rachana/runners/form_browser.py [--browsers chromium,firefox,webkit]
Needs: python3 tools/js/build_js.py (the runtime bundle in js/packages/rachana/dist); playwright and its browsers as for state_browser.py."""
import functools
import http.server
import pathlib
import sys
import threading

from playwright.sync_api import sync_playwright

ROOT = pathlib.Path(__file__).resolve().parents[4]
METRIC = "[data-panel=shown] .dv-metric-value"
FORM = "[data-panel=ticket] "


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
                page.goto(origin + "/js/packages/rachana/test/form.html")
                try:
                    page.wait_for_function("window.__events.some(e => e.name === 'ready' || e.name === 'error')", timeout=30000)
                    page.wait_for_selector(FORM + "[data-act=submit]", timeout=15000)
                    assert page.inner_text(FORM + "[data-act=submit]") == "Send", "the form's own words"
                    assert page.inner_text(METRIC) == "0", "the default state"
                    page.fill(FORM + "[data-path=qty] input", "2000")                    # breaks the rule qty <= 1000
                    page.click(FORM + "[data-act=submit]")
                    page.wait_for_selector(FORM + ".di-form-summary:not([hidden])", timeout=10000)
                    assert "At most 1,000" in page.inner_text(FORM + ".di-form-summary"), "the rule's message is shown"
                    assert page.inner_text(METRIC) == "0" and page.evaluate("window.__calls.length") == 0, "a refused form sets no state and sends nothing"
                    assert page.evaluate("window.__refused[0].problems[0].code") == "RCH-5003"
                    page.fill(FORM + "[data-path=qty] input", "25")                      # passes
                    page.click(FORM + "[data-act=submit]")
                    page.wait_for_function("document.querySelector('elements-view').shadowRoot.querySelector('[data-panel=shown] .dv-metric-value').textContent === '25'", timeout=10000)
                    host = page.evaluate("window.__calls[0]")
                    assert host[0] == "host" and host[1]["action"] == "order.place" and host[1]["with"] == {"qty": 25}, host
                    page.click("[data-panel=shown] [data-action=notify]")                 # an action button: notify goes to the host
                    page.wait_for_function("window.__calls.length === 2", timeout=10000)
                    assert page.evaluate("window.__calls[1][1].text") == "Told"
                    page.click("[data-panel=shown] [data-action=reset]")                  # an action button: state effects run
                    page.wait_for_function("document.querySelector('elements-view').shadowRoot.querySelector('[data-panel=shown] .dv-metric-value').textContent === '0'", timeout=10000)
                    errs = [e for e in page.evaluate("window.__events") if e["name"] == "error"] + errors
                    print(f"{name} {browser.version}: a form refused on a rule, then submitted; an action button fired{'; errors ' + str(errs) if errs else ''}")
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
