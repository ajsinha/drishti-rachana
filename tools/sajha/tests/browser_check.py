#!/usr/bin/env python3
# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""Chromium check of the board page (not collected by pytest): serves the risk mash-up with two runs, opens the board and requires that
it paints its panels, updates in place from version 1 to 2 over Server-Sent Events, raises no Content-Security-Policy violation and
no console error, and requests nothing from another origin.   uv run --with playwright==1.64.0 python tools/sajha/tests/browser_check.py"""
import re
import subprocess
import sys
import time
from pathlib import Path

from playwright.sync_api import sync_playwright


def until(pg, expr, seconds):
    """Polls (the page's strict policy refuses the string evaluation of wait_for_function)."""
    end = time.time() + seconds
    while time.time() < end:
        if pg.evaluate(expr):
            return
        time.sleep(0.25)
    raise AssertionError(f"timed out waiting for {expr}")


KIT = Path(__file__).resolve().parents[1]
proc = subprocess.Popen([sys.executable, "-u", str(KIT / "example" / "risk_mashup.py"), "--serve", "--runs", "2", "--every", "12", "--port", "0"],
                        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
try:
    url = None
    for line in proc.stdout:
        m = re.search(r"open (http\S+)", line)
        if m:
            url = m.group(1)
            break
    assert url, "the example printed no board URL"
    errors, requests = [], []
    with sync_playwright() as p:
        b = p.chromium.launch()
        pg = b.new_page()
        pg.on("console", lambda m: errors.append(m.text) if m.type == "error" else None)
        pg.on("pageerror", lambda e: errors.append(str(e)))
        pg.on("request", lambda r: requests.append(r.url))
        pg.add_init_script("document.addEventListener('securitypolicyviolation', e => (window.__v = window.__v || []).push("
                           "e.violatedDirective + ' ' + e.blockedURI))")
        pg.goto(url)
        until(pg, "()=>document.getElementById('meta').textContent.startsWith('version 1')", 20)
        painted = "()=>document.querySelector('elements-view')?.shadowRoot?.textContent || ''"      # the painter draws into its shadow tree
        until(pg, "()=>(" + painted[4:] + ").includes('What changed since yesterday')", 20)
        text = pg.evaluate(painted)
        for needle in ("Limit use", "VaR by desk", "Exposure by counterparty", "What changed since yesterday"):
            assert needle in text, f"panel missing: {needle}"
        until(pg, "()=>document.getElementById('meta').textContent.startsWith('version 2')", 40)   # live update, no reload
        assert pg.inner_text("#state") == "live"
        v = pg.evaluate("window.__v || []")
        assert not v, f"Content-Security-Policy violations: {v}"
        assert not errors, f"console errors: {errors}"
        origin = url.split("/b/")[0]
        assert all(r.startswith(origin) or r.startswith(("data:", "blob:")) for r in requests), "a request left the board's origin"
        b.close()
    print("board check: ok (painted, live update 1 -> 2, no CSP violation, no console error, same origin only)")
finally:
    proc.terminate()
