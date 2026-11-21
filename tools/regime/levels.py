#!/usr/bin/env python3
# Copyright (c) 2026 Ashutosh Sinha. Proprietary: see LICENSE.
"""Language levels (CG2, SUTRA_EXPANSION 6.5): compat/levels/<L>/{results.jsonl, rachana.lang.json, sutra.schema.json, functions.yaml}.
`write L` is run by the release that creates level L: it writes the folder once and refuses an existing one (no re-record flag).
`check [L]` replays the conformance suite of Rachana's JVM runtime at --language L and compares each case recorded in results.jsonl
byte for byte; it exits 1 on a difference. Cases added after the level was written are not part of it, and a case recorded as
`unsupported` (a kind the runtime did not run yet) may be answered now, and a case recorded `fail` (it never held at the level) may have
moved to a later level or been withdrawn: all are reported, none is a difference. Build first: cd java && ./mvnw clean package -DskipTests."""
import argparse, shutil, subprocess, sys, tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LEVELS = ROOT / "compat" / "levels"
DEFS = ["rachana.lang.json", "sutra.schema.json", "functions.yaml"]


def replay(level, out):
    r = subprocess.run([sys.executable, str(ROOT / "tools/conformance/rachana_conformance.py"), "--jvm", "--language", level, "--out", str(out)],
                       capture_output=True, text=True)
    if not out.is_file():
        sys.exit(f"the JVM runtime did not write results for level {level}:\n{r.stdout[-2000:]}{r.stderr[-2000:]}")


ap = argparse.ArgumentParser(description=__doc__)
ap.add_argument("cmd", choices=["write", "check"])
ap.add_argument("level", nargs="?")
a = ap.parse_args()
with tempfile.TemporaryDirectory(prefix="rachana-levels-") as tmp:
    if a.cmd == "write":
        if not a.level:
            ap.error("write needs a level, e.g. 1.0")
        d = LEVELS / a.level
        if d.exists():
            sys.exit(f"{d} exists: a level's files are written once (SUTRA_EXPANSION 6.5)")
        replay(a.level, Path(tmp) / "results.jsonl")
        d.mkdir(parents=True)
        shutil.copy(Path(tmp) / "results.jsonl", d / "results.jsonl")
        for f in DEFS:
            shutil.copy(ROOT / "spec/1" / f, d / f)
        print(f"wrote {d}")
    else:
        levels = [a.level] if a.level else sorted(p.name for p in LEVELS.iterdir() if p.is_dir())
        bad = 0
        for lv in levels:
            replay(lv, Path(tmp) / f"{lv}.jsonl")
            import json
            now = {json.loads(x)["case"]: x for x in (Path(tmp) / f"{lv}.jsonl").read_text(encoding="utf-8").splitlines() if x}
            then = {json.loads(x)["case"]: x for x in (LEVELS / lv / "results.jsonl").read_text(encoding="utf-8").splitlines() if x}
            # a case recorded failing (it never held at this level: an unverified case pinning a later behaviour) that the suite has
            # since moved to a later level, or withdrawn, is no longer part of this level: reported, not a difference
            moved = [c for c, x in then.items() if json.loads(x)["status"] == "fail"
                     and (c not in now or json.loads(now[c])["status"] == "unsupported")]
            changed = [c for c, x in then.items() if now.get(c) != x and json.loads(x)["status"] != "unsupported" and c not in moved]
            answered = [c for c, x in then.items() if now.get(c) != x and json.loads(x)["status"] == "unsupported"]
            added = len(set(now) - set(then))
            for c in changed:
                print(f"  changed: {c}")
            print(f"level {lv}: {'DIFFERENT' if changed else 'identical'} ({len(then) - len(changed) - len(answered) - len(moved)} identical, "
                  f"{len(answered)} newly answered, {len(moved)} failing cases moved to a later level or withdrawn, {added} cases added since)")
            bad += bool(changed)
        sys.exit(1 if bad else 0)
