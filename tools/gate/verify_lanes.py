#!/usr/bin/env python3
# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""The lanes of `./mvnw verify` that are not Java tests (G2 gate F3, owner decision O3): everything G2 built runs in the build, nothing is
skipped. rachana-guards runs one lane per Maven execution (phase verify, after every module is packaged):

    conformance  the suite at EVERY language level on the JVM runtime and on the Node bundle (tools), byte for byte the same results; the
                 runtime bundle against the tools bundle (tools/js/build_js.py --check-only); no failing case, verified or not
    protocol     the Data Protocol cases against the JVM reference, the Python host and the JVM live server (every case, live tables too)
    pytest       the Python SDK's tests with the bundles in place, the V8 engine and both web adapters: any skipped test fails the lane
    browser      Chromium under Playwright: <rachana-view> live, the form and state runners, a built site (paint_check), the Studio

Run by Maven as: uv run --with pyyaml --with jsonschema --with pyarrow python tools/gate/verify_lanes.py <lane> --java-home <home>
(uv, Node 20+ and a JDK are prerequisites of the build; the first browser lane downloads Chromium once)."""
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PLAYWRIGHT = "playwright==1.64.0"
sys.path.insert(0, str(ROOT / "tools" / "js"))
from build_js import bundle_levels  # noqa: E402  (the language levels come from the bundle: Core writes them once)


def run(cmd: list[str], env: dict[str, str], cwd: Path = ROOT, quiet: bool = False) -> subprocess.CompletedProcess:
    print("+ " + " ".join(str(c) for c in cmd), flush=True)
    r = subprocess.run([str(c) for c in cmd], cwd=cwd, env=env, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if not quiet or r.returncode:
        print(r.stdout[-6000:] if r.returncode else r.stdout[-1500:], flush=True)
    return r


def need(cond: bool, message: str, failures: list[str]) -> None:
    if not cond:
        failures.append(message)
        print("FAIL: " + message, file=sys.stderr, flush=True)


def conformance(env: dict[str, str]) -> list[str]:
    failures: list[str] = []
    dist = ROOT / "js" / "packages" / "rachana" / "dist"
    levels = bundle_levels(dist)
    need(len(levels) >= 3, f"the bundle names {levels} as its language levels", failures)
    driver = ROOT / "tools" / "conformance" / "rachana_conformance.py"
    with tempfile.TemporaryDirectory(prefix="rachana-lanes-") as tmp:
        for level in levels:
            made = {}
            for rt, flag in (("jvm", "--jvm"), ("node", "--node")):
                out, summ = Path(tmp) / f"{rt}-{level}.jsonl", Path(tmp) / f"{rt}-{level}.summary.json"
                r = run([sys.executable, driver, flag, "--profiles", "core,drishti", "--language", level, "--out", out, "--summary", summ], env, quiet=True)
                need(r.returncode == 0 and out.is_file() and summ.is_file(), f"{rt} at level {level}: the run failed (exit {r.returncode})", failures)
                if summ.is_file():
                    sm = json.loads(summ.read_text(encoding="utf-8"))
                    need(not sm["failures"] and not sm["error"], f"{rt} at level {level}: {len(sm['failures'])} failing case(s), {sm['error']} error(s)", failures)
                    need(sm["pass"] > 1000, f"{rt} at level {level}: only {sm['pass']} case(s) passed", failures)
                    print(f"conformance {rt} {level}: {sm['cases']} cases, pass {sm['pass']}, unsupported {sm['unsupported']}", flush=True)
                made[rt] = out
            if all(p.is_file() for p in made.values()):
                same = made["jvm"].read_bytes() == made["node"].read_bytes()
                need(same, f"level {level}: the JVM and the Node bundle answered different bytes", failures)
    r = run([sys.executable, ROOT / "tools" / "js" / "build_js.py", "--check-only"], env, quiet=True)
    need(r.returncode == 0, "the runtime bundle differs from the tools bundle (build_js.py --check-only)", failures)
    from build_js import brotli_size, size_guard
    dist = ROOT / "js" / "packages" / "rachana" / "dist"
    for m in size_guard({b: brotli_size(dist / b) for b in ("rachana.runtime.js", "rachana.tools.js")}):
        failures.append("size guard: " + m)
    return failures


def protocol(env: dict[str, str]) -> list[str]:
    failures: list[str] = []
    check = ROOT / "tools" / "conformance" / "protocol_check.py"
    for flag, unsupported_ok in (("--reference", True), ("--python", True), ("--live", False)):
        with tempfile.TemporaryDirectory(prefix="rachana-lanes-") as tmp:
            out = Path(tmp) / "protocol.jsonl"
            r = run([sys.executable, check, flag, "--out", out], env, quiet=True)
            line = next((l for l in r.stdout.splitlines() if l.startswith("protocol:")), "")
            need(r.returncode == 0 and "fail 0" in line and "error 0" in line, f"protocol {flag}: {line or 'no summary'} (exit {r.returncode})", failures)
            # the reference and the Python host serve no live tables (the --live server runs those cases): their unsupported are exactly those
            need(unsupported_ok or "unsupported 0" in line, f"protocol {flag}: a case was left unsupported: {line}", failures)
            print(line, flush=True)
    return failures


def pytest_lane(env: dict[str, str]) -> list[str]:
    failures: list[str] = []
    dist = ROOT / "js" / "packages" / "rachana" / "dist"
    bundle = ROOT / "python" / "src" / "drishti_rachana" / "bundle"
    bundle.mkdir(parents=True, exist_ok=True)
    sums = ""
    import hashlib
    for b in ("rachana.runtime.js", "rachana.tools.js"):                    # what tools/js/build_js.py puts there (git-ignored)
        shutil.copyfile(dist / b, bundle / b)
        sums += f"{hashlib.sha256((dist / b).read_bytes()).hexdigest()}  {b}\n"
    (bundle / "SHA256SUMS").write_text(sums, encoding="utf-8")
    base = Path(tempfile.mkdtemp(prefix="rachana-pytest-"))              # pytest's own base folder (it keeps the last three runs otherwise): removed with the lane
    try:
        r = run(["uv", "run", "--project", ROOT / "python", "--extra", "test", "--extra", "v8", "--extra", "flask", "--extra", "fastapi",
                 "pytest", "tests", "-q", "-rs", "-p", "no:cacheprovider", "--basetemp", base / "t"], env, cwd=ROOT / "python", quiet=True)
    finally:
        shutil.rmtree(base, ignore_errors=True)
    tail = r.stdout.strip().splitlines()[-1] if r.stdout.strip() else ""
    need(r.returncode == 0, f"the Python SDK tests failed (exit {r.returncode})", failures)
    need(not re.search(r"\b\d+ skipped\b", r.stdout), f"the Python SDK tests skipped some ({tail}): a missing prerequisite, never a pass", failures)
    print("pytest: " + tail, flush=True)
    return failures


def browser(env: dict[str, str]) -> list[str]:
    failures: list[str] = []
    base = ["uv", "run", "-q", "--with", PLAYWRIGHT, "python"]
    r = run(base + ["-m", "playwright", "install", "chromium"], env, quiet=True)
    need(r.returncode == 0, f"the pinned Chromium is missing and could not be installed (a browser lane never skips): uv run --with {PLAYWRIGHT} python -m playwright install chromium", failures)
    scripts = [(["js/packages/rachana/runners/view_browser.py", "--browsers", "chromium"], "painted"),
               (["js/packages/rachana/runners/form_browser.py"], "submitted"),
               (["js/packages/rachana/runners/state_browser.py"], "state"),
               (["js/packages/rachana/runners/live_edit_browser.py"], "editable live table"),
               (["js/packages/rachana-cli/test/paint_check.py"], "ok"),
               (["js/packages/rachana-studio/test/studio_check.py"], "ok")]
    for args, marker in scripts:
        r = run(base + args, env, quiet=True)
        need(r.returncode == 0 and "SKIPPED" not in r.stdout and "FAIL" not in r.stdout and marker in r.stdout, f"browser check {args[0]} failed (exit {r.returncode})", failures)
    return failures


LANES = {"conformance": conformance, "protocol": protocol, "pytest": pytest_lane, "browser": browser}


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("lane", choices=sorted(LANES))
    ap.add_argument("--java-home", default=os.environ.get("JAVA_HOME", ""), help="the JDK the build runs on: its bin/ comes first on the PATH of every program the lane starts (the Java 21 results)")
    a = ap.parse_args()
    env = dict(os.environ, PLAYWRIGHT_SKIP_BROWSER_GC="1")      # an install must not remove the browsers other checkouts pinned
    if a.java_home:
        env["JAVA_HOME"] = a.java_home
        env["PATH"] = str(Path(a.java_home) / "bin") + os.pathsep + env.get("PATH", "")
    failures = LANES[a.lane](env)
    if failures:
        print(f"\nverify lane {a.lane}: {len(failures)} problem(s)\n  " + "\n  ".join(failures), file=sys.stderr)
        return 1
    print(f"verify lane {a.lane}: green", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
