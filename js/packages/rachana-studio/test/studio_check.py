# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""Chromium check of Rachana Studio: open a copy of examples/static-sales, insert a core fragment (ranked-bars, with its columns
mapped), see the preview paint the new panel, see the Sutra grow, save it to disk, and finish with no console error or failed request.
Libraries: RACHANA_LIBS (path), else ../drishti-rachana-libraries/libraries beside this repository; the check is skipped (exit 0, said
so) when neither exists.
Run: uv run --with playwright==1.64.0 python js/packages/rachana-studio/test/studio_check.py   (once: playwright install chromium)"""
import os, re, shutil, subprocess, sys, tempfile, time
from pathlib import Path
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[4]
libs = os.environ.get("RACHANA_LIBS") or str(ROOT.parent / "drishti-rachana-libraries" / "libraries")
if not Path(libs).exists():
    print("studio_check: SKIPPED, no library folder (set RACHANA_LIBS)")
    sys.exit(0)
work = Path(tempfile.mkdtemp(prefix="rachana-studio-"))
proj = work / "sales"
shutil.copytree(ROOT / "examples" / "static-sales", proj)
srv = subprocess.Popen(["node", str(ROOT / "js/packages/rachana-cli/bin/rachana.js"), "studio", str(proj), "--libs", libs],
                       stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
fails = []
try:
    url = re.search(r"(http://127\.0\.0\.1:\d+/)", srv.stdout.readline()).group(1)
    with sync_playwright() as p:
        b = p.chromium.launch()
        pg = b.new_page(viewport={"width": 1500, "height": 950})
        errs = []
        pg.on("console", lambda m: errs.append(m.text) if m.type == "error" else None)
        pg.on("pageerror", lambda e: errs.append(str(e)))
        pg.on("requestfailed", lambda r: errs.append("request failed: " + r.url))
        pg.on("response", lambda r: errs.append(f"HTTP {r.status}: {r.url}") if r.status >= 400 else None)
        pg.goto(url)
        pg.wait_for_selector("#studio[data-ready=yes]", timeout=15000)
        ppainted = lambda: int(pg.evaluate("Number(document.getElementById('studio').dataset.painted || 0)"))
        painted_text = lambda: pg.evaluate("(document.querySelector('rachana-view elements-view')?.shadowRoot?.textContent || '').trim()")
        pg.wait_for_function("(document.querySelector('rachana-view elements-view')?.shadowRoot?.textContent || '').trim().length > 100", timeout=15000)
        pg.wait_for_selector("[data-rs=source] .CodeMirror", timeout=15000)

        def step(name, ok, detail=""):
            print(f"{name}: {'ok' if ok else 'FAIL'} {detail}")
            if not ok: fails.append(name)

        step("palette lists core fragments", pg.locator("[data-rs=palette] .di-option").count() >= 20, f"({pg.locator('[data-rs=palette] .di-option').count()})")
        step("assistant bar is disabled", pg.locator("[data-rs=ai]").is_disabled())
        before = ppainted()
        # the palette search narrows, then the fragment is chosen
        pg.fill("[data-rs=search]", "ranked")
        pg.locator("[data-rs=palette] .di-option", has_text="ranked-bars").first.click()
        pg.wait_for_selector("[data-rs=form] .di-form-field", timeout=5000)
        # choose the table and map the columns: label <- region, value <- revenue
        form_text = pg.inner_text("[data-rs=form]")
        step("form shows the typed parameters", all(w in form_text for w in ("Table", "Column mapping", "Title", "Tone")))
        path_sel = lambda path: f"[data-rs=form] [data-path='{path}']"
        def pick(path, value):
            pg.locator(f"{path_sel(path)} .di-select-control").click()
            pg.locator(".di-select-pop .di-option", has_text=re.compile(rf"^{re.escape(value)}$")).first.click()
        pick("rows.from", "sales")
        pick("rows.columns.label", "region")
        pick("rows.columns.value", "revenue")
        pg.fill(f"{path_sel('title')} input", "Revenue by region")
        pg.locator("[data-rs=form] button[data-act=submit]").click()
        pg.wait_for_function("window.__studio.state.text.includes('core/ranked-bars@1')", timeout=5000)
        text = pg.evaluate("window.__studio.state.text")
        step("the use block is in the source", "columns: { label: region, value: revenue }" in text and "title: Revenue by region" in text, repr(text[-200:]))
        pg.wait_for_function("window.__studio.editor.problems.length === 0 && window.__studio.state.checks > 1", timeout=10000)
        pg.wait_for_timeout(800)   # the check of the new text has answered
        step("no problems after the insert", pg.evaluate("window.__studio.editor.problems.length") == 0, str(pg.evaluate("window.__studio.editor.problems")))
        pg.wait_for_function(f"Number(document.getElementById('studio').dataset.painted) > {before}", timeout=10000)
        pg.wait_for_function("(document.querySelector('rachana-view elements-view')?.shadowRoot?.textContent || '').includes('Revenue by region')", timeout=10000)
        step("the preview paints the new panel", True, f"({len(painted_text())} chars)")
        step("the changes pane shows the insert", "+" in pg.inner_text("[data-rs=diff]") and "ranked-bars" in pg.inner_text("[data-rs=diff]"))
        # a broken edit shows a problem live
        pg.evaluate("window.__studio.editor.setValue(window.__studio.state.text + '\\n  - { id: broken, kind: metric, label: X, value: \"1 +\" }\\n')")
        pg.wait_for_function("Number(document.getElementById('studio').dataset.problems) > 0", timeout=10000)
        step("a broken edit is reported", True)
        pg.evaluate("window.__studio.editor.setValue(window.__studio.state.text.replace(/\\n  - \\{ id: broken[^\\n]*\\n/, '\\n'))")
        pg.wait_for_function("document.getElementById('studio').dataset.problems === '0'", timeout=10000)
        # save writes into the project folder
        pg.locator("[data-rs=save]").click()
        pg.wait_for_function("document.querySelector('[data-rs=status]').dataset.state === 'saved'", timeout=5000)
        saved = (proj / "overview.sutra.yaml").read_text()
        step("save wrote the file", "core/ranked-bars@1" in saved and "Revenue by region" in saved)
        step("no console errors", not errs, "; ".join(errs[:4]))
        # the server refuses a path outside the folder (its 400 is logged by the browser, so this comes after the console check)
        bad = pg.evaluate("""fetch('/api/save', {method:'POST', headers:{'content-type':'application/json','x-rachana-studio':'1'}, body: JSON.stringify({path:'../escape.sutra.yaml', text:'x'})}).then(r => r.status)""")
        step("a path outside the project is refused", bad in (400, 403) and not (work / "escape.sutra.yaml").exists(), str(bad))
        if os.environ.get("STUDIO_SHOT"): pg.screenshot(path=os.environ["STUDIO_SHOT"])
        b.close()
finally:
    srv.terminate()
    srv.wait(timeout=10)
    shutil.rmtree(work, ignore_errors=True)
sys.exit(1 if fails else 0)
