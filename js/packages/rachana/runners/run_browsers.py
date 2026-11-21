# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""The JavaScript build in Chromium, Firefox and WebKit under Playwright (split step A15, "runtimes agree"): the bundle answers
the conformance case lines in a module page with the strict Content-Security-Policy of P1's T7; the answers are written one per
line, to be compared byte for byte with the JVM's (JvmRuntime over the same lines).

Usage: run_browsers.py <bundle.js> <case lines.jsonl (RUNNER 5, greeting first)> <out dir> <formats file>... [--browsers a,b]
The case lines are what the driver sends; capture them with --cmd "sh -c 'tee <file> | node .../conformance.mjs ...'".
Pages are served by Playwright's request routing from an invented origin (no port is opened)."""
import json
import pathlib
import sys

from playwright.sync_api import sync_playwright

ORIGIN = "https://rachana.test"
CSP = "default-src 'self'; script-src 'self'; connect-src 'self'; require-trusted-types-for 'script'"
PAGE = """<!doctype html><meta charset="utf-8"><title>rachana conformance</title><script type="module" src="run.js"></script>"""
RUN = """import * as m from './rachana.js';
const [cases, formats] = await Promise.all([fetch('cases.jsonl').then(r => r.text()), fetch('formats.json').then(r => r.text())]);
m.init('core,drishti', formats);
const t0 = performance.now(), out = [];
for (const line of cases.split('\\n').slice(1)) if (line.trim()) out.push(m.answer(line));
window.__result = { text: out.join('\\n') + '\\n', ms: performance.now() - t0 };
"""


def main(argv):
    browsers = ["chromium", "firefox", "webkit"]
    if "--browsers" in argv:
        i = argv.index("--browsers")
        browsers = argv[i + 1].split(",")
        argv = argv[:i] + argv[i + 2:]
    bundle, cases, out = (pathlib.Path(a) for a in argv[:3])
    formats = json.dumps([pathlib.Path(f).read_text(encoding="utf-8") for f in argv[3:]])
    files = {"/": ("text/html", PAGE.encode()), "/run.js": ("text/javascript", RUN.encode()),
             "/rachana.js": ("text/javascript", bundle.read_bytes()), "/cases.jsonl": ("text/plain", cases.read_bytes()),
             "/formats.json": ("application/json", formats.encode())}
    out.mkdir(parents=True, exist_ok=True)
    with sync_playwright() as p:
        for name in browsers:
            browser = getattr(p, name).launch()
            page = browser.new_page()
            violations = []
            page.on("console", lambda m: violations.append(m.text) if "Content Security Policy" in m.text or "CSP" in m.text else None)

            def route(r):
                path = r.request.url[len(ORIGIN):] or "/"
                kind, body = files.get(path, ("text/plain", b""))
                r.fulfill(status=200 if path in files else 404, body=body,
                          headers={"Content-Type": kind + "; charset=utf-8", "Content-Security-Policy": CSP})

            page.route(ORIGIN + "/**", route)
            page.goto(ORIGIN + "/")
            page.wait_for_function("() => window.__result", timeout=600_000)
            r = page.evaluate("() => window.__result")
            (out / f"{name}.jsonl").write_text(r["text"], encoding="utf-8")
            print(json.dumps({"browser": name, "version": browser.version, "ms": round(r["ms"]), "lines": r["text"].count("\n"),
                              "cspViolations": violations}))
            browser.close()
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
