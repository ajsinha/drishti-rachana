# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""An editable live table in <rachana-view> in a real browser: the live reference host (java/rachana-live) serves the repository and a live
table on one origin; the page (test/live_edit.html) paints a Sutra whose live panel says `edit: cells`; the grid of the vendored Drishti
Elements pack reads the table's `write` for the reader, an edit is checked in the page and sent as a write (a ULID, the version the row
shows), the cell is marked queued while the row stays as it was, and the new value shows when the host's change arrives on the stream. A
reader without `write` gets no editable cell.

Usage: uv run --with playwright==1.64.0 python js/packages/rachana/runners/live_edit_browser.py [--browsers chromium,firefox,webkit]
Needs: cd java && ./mvnw clean package -DskipTests; python3 tools/js/build_js.py; the browsers (see view_browser.py)."""
import json
import os
import pathlib
import subprocess
import sys
import urllib.request

from playwright.sync_api import sync_playwright

ROOT = pathlib.Path(__file__).resolve().parents[4]
DECL = """rachana: 1
live-table: trades
snapshot: trades
stream: trades.live
key: id
version: v
changed-by: by
changed-at: at
columns: [ { name: id }, { name: v, type: integer }, { name: book }, { name: qty, type: integer }, { name: status }, { name: by }, { name: at, type: timestamp } ]
edit:
  fields:
    - { id: qty, bind: qty, type: number, min: 0, required: true }
    - { id: status, bind: status, type: enum, options: [NEW, DONE] }
  reason: optional
"""
ROWS = [{"id": "a", "v": 1, "book": "A", "qty": 1, "status": "NEW", "by": "system", "at": "2026-11-21T09:00:00Z"},
        {"id": "b", "v": 1, "book": "B", "qty": 2, "status": "NEW", "by": "system", "at": "2026-11-21T08:00:00Z"}]
READERS = {"bob": {"editable": ["qty", "status"]}, "viv": {}}
CELL = "[data-panel=trades] .dg-row:nth-child(1) .dg-cell:nth-child(3)"


def call(base, path, body):
    rq = urllib.request.Request(base + path, data=json.dumps(body).encode(), headers={"Content-Type": "application/json"}, method="POST")
    return urllib.request.urlopen(rq, timeout=10).read()


def load(base, reader):
    call(base, "/_test/live", {"name": "trades", "edit": {"sink": "outbox"}, "declaration": DECL, "rows": ROWS, "readers": READERS})
    call(base, "/_test/as", {"reader": reader})


def painted(page):
    page.wait_for_function("window.__events.some(e => e.name === 'ready' || e.name === 'error')", timeout=30000)
    page.wait_for_selector("[data-panel=trades] .dg-row", timeout=30000)


def main(argv):
    browsers = argv[argv.index("--browsers") + 1].split(",") if "--browsers" in argv else ["chromium"]
    target = ROOT / "java" / "rachana-live" / "target"
    jar = next(j for j in target.glob("rachana-live-*.jar") if not j.name.endswith(("-sources.jar", "-tests.jar")))
    mp = os.pathsep.join([str(jar), (target / "runtime-classpath.txt").read_text().strip()])
    server = subprocess.Popen(["java", "--add-opens=java.base/java.nio=org.apache.arrow.memory.core", "-p", mp, "-m", "com.ash.rachana.live/com.ash.rachana.live.LiveReferenceServer",
                               "--port", "0", "--static", str(ROOT)], stdout=subprocess.PIPE, text=True)
    base = server.stdout.readline().split("listening on", 1)[1].strip()
    origin = base[: base.index("/rdp/v1")]
    bad = 0
    try:
        with sync_playwright() as p:
            for name in browsers:
                browser = getattr(p, name).launch()
                try:
                    load(base, "viv")                                    # a reader the host gives no `write`: nothing is editable
                    page = browser.new_page()
                    errors, failed = [], []
                    page.on("pageerror", lambda e: errors.append(str(e)))
                    page.goto(origin + "/js/packages/rachana/test/live_edit.html")
                    painted(page)
                    assert page.locator("[data-panel=trades] .dg-editable").count() == 0, "a reader without write sees an editable cell"
                    page.close()
                    load(base, "bob")
                    page = browser.new_page()
                    page.on("pageerror", lambda e: errors.append(str(e)))
                    page.on("response", lambda r: failed.append((r.status, r.url)) if r.status >= 400 else None)
                    page.goto(origin + "/js/packages/rachana/test/live_edit.html")
                    painted(page)
                    page.wait_for_selector(CELL + ".dg-editable", timeout=10000)
                    if os.environ.get("LIVE_EDIT_SHOT"):
                        page.screenshot(path=os.environ["LIVE_EDIT_SHOT"])
                    page.locator(CELL).click()
                    page.keyboard.press("Enter")
                    page.locator("[data-panel=trades] .dg-editor-input").fill("-5")
                    page.keyboard.press("Enter")
                    assert "at least 0" in page.locator("[data-panel=trades] .dg-editor-msg").inner_text(), "the rule is checked in the page first"
                    page.keyboard.press("Escape")
                    page.locator(CELL).click()
                    page.keyboard.press("Enter")
                    page.locator("[data-panel=trades] .dg-editor-input").fill("9")
                    page.keyboard.press("Enter")
                    page.wait_for_selector(CELL + ".dg-queued", timeout=10000)
                    assert page.locator(CELL).inner_text().strip() == "1", "the row stays as it was until the table's patch"
                    writes = [e["detail"] for e in page.evaluate("window.__events") if e["name"] == "write"]
                    assert writes and writes[0]["change"]["key"] == ["a"] and writes[0]["change"]["basedOn"] == 1 and writes[0]["change"]["set"] == {"qty": 9}, writes
                    assert len(writes[0]["change"]["id"]) == 26 and writes[0]["answer"]["state"] == "queued", writes
                    call(base, "/_test/outbox", {"flush": True})        # the source applies it: its change arrives on the stream
                    page.get_by_text("9", exact=True).first.wait_for(timeout=10000)
                    assert page.locator(CELL + ".dg-queued").count() == 0
                    assert not failed, f"requests refused: {failed}"
                    errs = errors + [e for e in page.evaluate("window.__events") if e["name"] == "error"]
                    print(f"{name} {browser.version}: an editable live table: write columns offered, rule checked, one write sent, queued mark, new value from the stream{'; errors ' + str(errs) if errs else ''}")
                    bad += 1 if errs else 0
                except Exception as e:  # noqa: BLE001 - one browser's failure is reported, the others still run
                    print(f"{name}: FAILED: {e}", file=sys.stderr)
                    bad += 1
                browser.close()
    finally:
        server.kill()
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
