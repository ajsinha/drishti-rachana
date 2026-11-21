#!/usr/bin/env python3
# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""The conformance runtime program of the Python SDK's V8 engine (RUNNER 5): case lines on standard input, results lines on standard output,
every answer from the tools or runtime bundle run in V8 through drishti_rachana.engine (the same glue as runners/conformance.mjs).
Usage: python3 pyv8_runtime.py [--bundle tools|runtime] [--runtime python-v8] [--profiles core,drishti] <formats file>...
Run by tools/conformance/verify_class_a.py; needs mini-racer (uv run --with mini-racer --with pyyaml ...)."""
import hashlib, json, sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "python" / "src"))
from drishti_rachana.engine import Rachana  # noqa: E402

BY_KIND = {"expr": "evaluate", "expr-error": "evaluateError", "check": "checked", "grammar": "grammar", "format": "format", "problem": "check",
           "sutra": "view", "patch": "patch", "fire": "fire", "plan": "plan", "lib": "library"}


def main(argv):
    bundle, runtime, profiles, files = "tools", "python-v8", "core", []
    i = 0
    while i < len(argv):
        if argv[i] == "--bundle": bundle = argv[i + 1]; i += 2
        elif argv[i] == "--runtime": runtime = argv[i + 1]; i += 2
        elif argv[i] == "--profiles": profiles = argv[i + 1]; i += 2
        else: files.append(argv[i]); i += 1
    bdir = Path(__file__).resolve().parents[2] / "js" / "packages" / "rachana" / "dist"
    r = Rachana(profiles=profiles, formats=[Path(f).read_text(encoding="utf-8") for f in files], bundle=bundle, bundle_dir=bdir,
                max_memory=1 << 30, timeout=60,
                 expected_sha256=hashlib.sha256((bdir / f"rachana.{bundle}.js").read_bytes()).hexdigest())
    ctx = r._ctx
    has_hello = ctx.eval("typeof rachana.hello") == "function"
    has_answer = ctx.eval("typeof rachana.answer") == "function"
    greeted = False
    for line in sys.stdin:
        if not greeted:
            greeted = True
            out = ctx.call("rachana.hello", runtime) if has_hello else json.dumps(
                {"runtime": runtime, "runtimeVersion": "rachana-" + ctx.call("rachana.version"), "language": "1.0",
                 "profiles": profiles.split(","), "kinds": list(BY_KIND)})
            sys.stdout.write(out + "\n"); sys.stdout.flush(); continue
        if not line.strip(): continue
        try:
            if has_answer:
                out = ctx.call("rachana.answer", line.rstrip("\n"), timeout_sec=60)
            else:
                c = json.loads(line); fn = BY_KIND.get(c["kind"]); cid = c.pop("case")
                if not fn:
                    out = json.dumps({"case": cid, "error": "kind not implemented: " + c["kind"]})
                else:
                    a = ctx.call("rachana." + fn, json.dumps(c, ensure_ascii=False), timeout_sec=60)
                    out = '{"case":%s,%s' % (json.dumps(cid), a[1:]) if a.startswith('{"error":"') else '{"case":%s,"actual":%s}' % (json.dumps(cid), a)
        except Exception as x:
            out = json.dumps({"case": json.loads(line)["case"], "error": "V8 exception: " + str(x)[:300]})
        sys.stdout.write(out + "\n")
    sys.stdout.flush()


if __name__ == "__main__":
    main(sys.argv[1:])
