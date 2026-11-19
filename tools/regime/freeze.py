#!/usr/bin/env python3
# Copyright (c) 2026 Ashutosh Sinha. Proprietary: see LICENSE.
"""Written by the release that creates a level (CG1, CG4): conformance/1/FROZEN.sha384 (a hash per case file; `--released <tag>` sets
the state, otherwise pre-release) and, with --contract <minor>, spec/1/released/viewmodel-<minor>.schema.json from the current schema.
The release tool runs it; there is no other writer, and a released file is never rewritten (it refuses)."""
import argparse, hashlib, shutil, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

ap = argparse.ArgumentParser(description=__doc__)
ap.add_argument("--released", metavar="TAG", help="state the files as released at this tag (default: pre-release)")
ap.add_argument("--contract", metavar="MINOR", help="copy the current ViewModel schema to spec/1/released/ as this minor")
a = ap.parse_args()
cases = ROOT / "conformance" / "1"
target = cases / "FROZEN.sha384"
old = target.read_text(encoding="utf-8").splitlines() if target.exists() else []
if any(l.startswith("# state: released") for l in old):
    sys.exit("FROZEN.sha384 is released: a level's files are written once (SUTRA_EXPANSION 6.5)")
lines = [f"# state: {'released ' + a.released if a.released else 'pre-release'}",
         "# sha384 of every case file below conformance/1/ (tools/regime/freeze.py); a case is added, never edited (CG1)"]
for p in sorted(cases.rglob("*.yaml")):
    rel = p.relative_to(cases).as_posix()
    if rel.startswith("schema/"):
        continue
    lines.append(f"{hashlib.sha384(p.read_bytes()).hexdigest()}  {rel}")
target.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(f"wrote {target} ({len(lines) - 2} files)")
if a.contract:
    d = ROOT / "spec" / "1" / "released"
    d.mkdir(exist_ok=True)
    t = d / f"viewmodel-{a.contract}.schema.json"
    if t.exists():
        sys.exit(f"{t.name} exists: a released contract is never rewritten")
    shutil.copy(ROOT / "spec/1/viewmodel.schema.json", t)
    print(f"wrote {t}")
