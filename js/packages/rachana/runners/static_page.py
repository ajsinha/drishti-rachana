# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""The shared loop of the static-page browser runners (form_browser.py, state_browser.py): the repository served on a free local port,
one test page opened per browser, the runner's checks run once the page reports ready (or an error), and every browser's result reported.

run(argv, page, check): `page` is the path under the repository root; `check(page)` asserts and returns the line printed after
"<browser> <version>: ". A failed check or an error event fails that browser; the others still run. Returns the process exit code."""
import functools
import http.server
import pathlib
import sys
import threading

from playwright.sync_api import sync_playwright

ROOT = pathlib.Path(__file__).resolve().parents[4]


class Quiet(http.server.SimpleHTTPRequestHandler):
    def log_message(self, *args):
        pass


def run(argv, page_path, check):
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
                page.goto(origin + page_path)
                try:
                    page.wait_for_function("window.__events.some(e => e.name === 'ready' || e.name === 'error')", timeout=30000)
                    said = check(page)
                    errs = [e for e in page.evaluate("window.__events") if e["name"] == "error"] + errors
                    print(f"{name} {browser.version}: {said}{'; errors ' + str(errs) if errs else ''}")
                    bad += 1 if errs else 0
                except Exception as e:  # noqa: BLE001 - one browser's failure is reported, the others still run
                    print(f"{name}: FAILED: {e}; events {page.evaluate('window.__events')}; page errors {errors}", file=sys.stderr)
                    bad += 1
                browser.close()
    finally:
        server.shutdown()
    return 1 if bad else 0
