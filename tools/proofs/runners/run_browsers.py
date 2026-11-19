# Copyright (c) 2026 Ashutosh Sinha. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""P1/P2 browser runner: the proof bundle under Playwright in Chromium, Firefox and WebKit, one browser at a time.

Usage: run_browsers.py <bundle.js> <cases.jsonl> <out dir> [browser ...]
Pages are served by Playwright's request routing from an invented origin (no port is opened), with the strict
Content-Security-Policy of T7. Writes <out>/<browser>.jsonl and prints timings and CSP violations.
"""
import json
import pathlib
import sys

from playwright.sync_api import sync_playwright

ORIGIN = "https://proof.rachana.test"
CSP = "default-src 'self'; script-src 'self'; require-trusted-types-for 'script'"
PAGE = """<!doctype html><meta charset="utf-8"><title>proof</title>
<script type="module" src="/run.mjs"></script>"""
RUN = """
const v = [];
document.addEventListener('securitypolicyviolation', e => v.push(e.violatedDirective + ' ' + e.blockedURI));
const t0 = performance.now();
const m = await import('/bundle.js');
const t1 = performance.now();
const cases = await (await fetch('/cases.jsonl')).text();
const t2 = performance.now();
const out = m.runAll(cases);
const t3 = performance.now();
window.__result = { out, module: t1 - t0, run: t3 - t2, violations: v, ua: navigator.userAgent };
"""


def main():
    bundle, cases, out = map(pathlib.Path, sys.argv[1:4])
    names = sys.argv[4:] or ["chromium", "firefox", "webkit"]
    files = {"/": (PAGE.encode(), "text/html"), "/run.mjs": (RUN.encode(), "text/javascript"),
             "/bundle.js": (bundle.read_bytes(), "text/javascript"), "/cases.jsonl": (cases.read_bytes(), "text/plain")}
    out.mkdir(parents=True, exist_ok=True)
    with sync_playwright() as p:
        for name in names:
            b = getattr(p, name).launch()
            page = b.new_page()

            def route(r):
                path = "/" + r.request.url[len(ORIGIN):].lstrip("/")
                body, ct = files.get(path, (b"", "text/plain"))
                r.fulfill(status=200 if path in files else 404, body=body,
                          headers={"content-type": ct + "; charset=utf-8", "content-security-policy": CSP})

            page.route(ORIGIN + "/**", route)
            errors = []
            page.on("pageerror", lambda e: errors.append(str(e)))
            page.on("console", lambda m: errors.append("console " + m.type + ": " + m.text) if m.type in ("error", "warning") else None)
            page.goto(ORIGIN + "/")
            page.wait_for_function("() => window.__result !== undefined", timeout=300000)
            r = page.evaluate("() => window.__result")
            (out / f"{name}.jsonl").write_text(r["out"], encoding="utf-8")
            print(json.dumps({"browser": name, "version": b.version, "module_ms": round(r["module"], 1), "run_ms": round(r["run"]),
                              "violations": r["violations"], "errors": errors[:5]}))
            b.close()


main()
