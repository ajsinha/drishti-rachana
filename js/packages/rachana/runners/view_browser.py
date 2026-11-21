# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""<rachana-view> in real browsers (B3): the reference server (java/rachana-data) serves the repository and a live table on one origin;
the page (test/view.html) compiles and binds a Sutra with the runtime bundle and paints it with Drishti Elements' <elements-view>; a
patch published to the table must reach the painted view through the change stream.

Usage: uv run --with playwright==1.64.0 python js/packages/rachana/runners/view_browser.py [--browsers chromium,firefox,webkit]
Needs: cd java && ./mvnw clean package -DskipTests; python3 tools/js/build_js.py."""
import json
import os
import pathlib
import subprocess
import sys
import urllib.request

from playwright.sync_api import sync_playwright

ROOT = pathlib.Path(__file__).resolve().parents[4]
TABLE = {"schema": {"name": "sales", "key": ["region", "desk"], "columns": [{"name": "region", "type": "string"}, {"name": "desk", "type": "string"},
         {"name": "revenue", "type": "decimal"}], "can": ["page", "filter", "changes", "replay"]},
         "rows": [{"region": "North", "desk": "A", "revenue": "100"}, {"region": "South", "desk": "B", "revenue": "50"}]}


def post(base, path, body):
    rq = urllib.request.Request(base + path, data=json.dumps(body).encode(), headers={"Content-Type": "application/json"}, method="POST")
    urllib.request.urlopen(rq, timeout=10).read()


def main(argv):
    browsers = argv[argv.index("--browsers") + 1].split(",") if "--browsers" in argv else ["chromium", "firefox", "webkit"]
    target = ROOT / "java" / "rachana-data" / "target"
    jar = next(j for j in target.glob("rachana-data-*.jar") if not j.name.endswith(("-sources.jar", "-tests.jar")))
    mp = os.pathsep.join([str(jar), (target / "runtime-classpath.txt").read_text().strip()])
    server = subprocess.Popen(["java", "--add-opens=java.base/java.nio=org.apache.arrow.memory.core", "-p", mp, "-m", "com.ash.rachana.data/com.ash.rachana.data.ReferenceServer", "--port", "0",
                               "--static", str(ROOT)], stdout=subprocess.PIPE, text=True)
    base = server.stdout.readline().split("listening on", 1)[1].strip()
    origin = base[: base.index("/rdp/v1")]
    bad = 0
    try:
        with sync_playwright() as p:
            for name in browsers:
                post(base, "/_test/fixture", TABLE)
                browser = getattr(p, name).launch()
                page = browser.new_page()
                errors = []
                page.on("pageerror", lambda e: errors.append(str(e)))
                page.goto(origin + "/js/packages/rachana/test/view.html")
                try:
                    page.wait_for_function("window.__events.some(e => e.name === 'ready' || e.name === 'error')", timeout=30000)
                    page.get_by_text("150", exact=True).first.wait_for(timeout=10000)
                    post(base, "/_test/patch", {"table": "sales", "update": [{"region": "North", "desk": "A", "revenue": "120"}]})
                    page.get_by_text("170", exact=True).first.wait_for(timeout=10000)
                    post(base, "/_test/patch", {"table": "sales", "add": [{"region": "East", "desk": "Z", "revenue": "5"}]})
                    page.get_by_text("Z", exact=True).first.wait_for(timeout=10000)
                    ev = page.evaluate("window.__events")
                    errs = [e for e in ev if e["name"] == "error"] + errors
                    print(f"{name}: painted, live total 150 -> 170, row added; events {[e['name'] for e in ev]}{'; errors ' + str(errs) if errs else ''}")
                    bad += 1 if errs else 0
                except Exception as e:  # noqa: BLE001 - one browser's failure is reported, the others still run
                    print(f"{name}: FAILED: {e}; events {page.evaluate('window.__events')}; page errors {errors}", file=sys.stderr)
                    bad += 1
                browser.close()
    finally:
        server.kill()
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
