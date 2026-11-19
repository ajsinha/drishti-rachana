# Copyright (c) 2026 Ashutosh Sinha. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""T3, T4 and T5 in the browsers: run_browser_bench.py <bundle.js> <bench dir> <bench50k dir> <out.json> [browser ...]

The same bench.mjs as Node, served by Playwright's request routing (no port), one browser at a time.
"""
import json
import pathlib
import sys

from playwright.sync_api import sync_playwright

ORIGIN = "https://proof.rachana.test"
RUN = """
import { t3, t4, t5 } from '/bench.mjs';
const a = performance.now();
const m = await import('/bundle.js');
const moduleMs = performance.now() - a;
const lines = (await (await fetch('/cases.jsonl')).text()).trim().split('\\n').map(JSON.parse);
const typical = lines.find(l => l.case.endsWith('#typical')).pnode, large = lines.find(l => l.case.endsWith('#large')).pnode;
const r = { runtime: navigator.userAgent, moduleMs };
r.t3 = t3(m, typical, large);
r.t4 = t4(m, await (await fetch('/rows.json')).text(), await (await fetch('/rows.plain.json')).json());
r.t5 = t5(m, await (await fetch('/rows50.json')).text(), '@.price * @.qty - @.amount');
window.__result = r;
"""


def main():
    bundle, d, d50, out = map(pathlib.Path, sys.argv[1:5])
    here = pathlib.Path(__file__).parent
    files = {"/": (b'<!doctype html><meta charset="utf-8"><script type="module" src="/run.mjs"></script>', "text/html"),
             "/run.mjs": (RUN.encode(), "text/javascript"), "/bench.mjs": ((here / "bench.mjs").read_bytes(), "text/javascript"),
             "/mini-el.mjs": ((here / "mini-el.mjs").read_bytes(), "text/javascript"), "/bundle.js": (bundle.read_bytes(), "text/javascript"),
             "/cases.jsonl": ((d / "prep" / "cases.jsonl").read_bytes(), "text/plain"), "/rows.json": ((d / "rows.json").read_bytes(), "application/json"),
             "/rows.plain.json": ((d / "rows.plain.json").read_bytes(), "application/json"), "/rows50.json": ((d50 / "rows.json").read_bytes(), "application/json")}
    results = {}
    with sync_playwright() as p:
        for name in sys.argv[5:] or ["chromium", "firefox", "webkit"]:
            b = getattr(p, name).launch()
            page = b.new_page()
            page.route(ORIGIN + "/**", lambda r: r.fulfill(status=200, body=files["/" + r.request.url[len(ORIGIN):].lstrip("/")][0],
                                                           headers={"content-type": files["/" + r.request.url[len(ORIGIN):].lstrip("/")][1]}))
            page.goto(ORIGIN + "/")
            page.wait_for_function("() => window.__result !== undefined", timeout=600000)
            results[name] = page.evaluate("() => window.__result") | {"version": b.version}
            print(name, b.version, "module", round(results[name]["moduleMs"], 1), "t3", results[name]["t3"])
            b.close()
    out.write_text(json.dumps(results, indent=1), encoding="utf-8")


main()
