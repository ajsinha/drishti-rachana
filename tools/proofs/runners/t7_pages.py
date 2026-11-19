# Copyright (c) 2026 Ashutosh Sinha. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""T7, the two page shapes not covered by run_browsers.py: the classic-script bundle from file:// (strict CSP in a meta tag) and
the module bundle inside a module Web Worker (strict CSP header). Prints, per browser, whether the results equal the reference.

Usage: t7_pages.py <umd bundle> <es bundle> <cases.jsonl> <dir of <browser>.jsonl from run_browsers.py> <scratch dir>
"""
import hashlib
import json
import pathlib
import sys

from playwright.sync_api import sync_playwright

CSP = "default-src 'self'; script-src 'self'; require-trusted-types-for 'script'"
ORIGIN = "https://proof.rachana.test"


def main():
    umd, es, cases, ref, scratch = (pathlib.Path(a).resolve() for a in sys.argv[1:6])
    scratch.mkdir(parents=True, exist_ok=True)
    (scratch / "rachana-proof.js").write_bytes(umd.read_bytes())
    (scratch / "cases.js").write_text("self.CASES = " + json.dumps(cases.read_text(encoding="utf-8")) + ";\n", encoding="utf-8")
    (scratch / "run.js").write_text("self.RESULT = self.runAll(self.CASES);\n", encoding="utf-8")
    (scratch / "index.html").write_text(
        f'<!doctype html><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="{CSP}">'
        '<script src="rachana-proof.js"></script><script src="cases.js"></script><script src="run.js"></script>', encoding="utf-8")
    worker = "import { runAll } from '/bundle.js'; onmessage = e => postMessage(runAll(e.data));"
    page_js = ("const tt = self.trustedTypes ? trustedTypes.createPolicy('proof', { createScriptURL: u => u }) : { createScriptURL: u => u };"
               "const w = new Worker(tt.createScriptURL('/worker.mjs'), { type: 'module' });"
               "w.onmessage = e => { window.__result = e.data; }; w.onerror = e => { window.__result = 'ERROR ' + e.message; };"
               "fetch('/cases.jsonl').then(r => r.text()).then(t => w.postMessage(t));")
    files = {"/": (b'<!doctype html><meta charset="utf-8"><script type="module" src="/page.mjs"></script>', "text/html"),
             "/page.mjs": (page_js.encode(), "text/javascript"), "/worker.mjs": (worker.encode(), "text/javascript"),
             "/bundle.js": (es.read_bytes(), "text/javascript"), "/cases.jsonl": (cases.read_bytes(), "text/plain")}
    with sync_playwright() as p:
        for name in ["chromium", "firefox", "webkit"]:
            want = hashlib.sha256((ref / f"{name}.jsonl").read_bytes()).hexdigest()
            b = getattr(p, name).launch()
            pg = b.new_page()
            errs = []
            pg.on("console", lambda m: errs.append(m.text) if m.type == "error" else None)
            pg.goto((scratch / "index.html").as_uri())
            pg.wait_for_function("() => self.RESULT !== undefined", timeout=60000)
            got = hashlib.sha256(pg.evaluate("() => self.RESULT").encode()).hexdigest()
            print(json.dumps({"browser": name, "page": "file:// classic script, CSP meta", "same": got == want, "errors": errs[:3]}))
            pg2 = b.new_page()
            pg2.route(ORIGIN + "/**", lambda r: r.fulfill(status=200, body=files["/" + r.request.url[len(ORIGIN):].lstrip("/")][0],
                                                          headers={"content-type": files["/" + r.request.url[len(ORIGIN):].lstrip("/")][1],
                                                                   "content-security-policy": CSP}))
            errs2 = []
            pg2.on("console", lambda m: errs2.append(m.text[:200]))
            pg2.on("pageerror", lambda e: errs2.append(str(e)[:200]))
            pg2.on("worker", lambda w: errs2.append("worker started " + w.url))
            pg2.goto(ORIGIN + "/")
            try:
                pg2.wait_for_function("() => window.__result !== undefined", timeout=60000)
                res = pg2.evaluate("() => window.__result")
            except Exception:
                res = "TIMEOUT " + " | ".join(errs2)
            got2 = hashlib.sha256(res.encode()).hexdigest()
            print(json.dumps({"browser": name, "page": "module worker, CSP header", "same": got2 == want, "errors": errs2[:3],
                              "head": res[:80] if got2 != want else ""}))
            b.close()


main()
