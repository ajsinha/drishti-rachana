#!/usr/bin/env python3
# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""Verification of conformance cases (G2 gate review 9.6), mechanical part.

CLASS A: a case that was recorded from the i7a reference (`verified: false`, `since` 1.0) is flipped to `verified: true` only when
  - the reference (RefBridge over Drishti at v2.9.11.i_7a; RefBridgeSutra for `sutra`) gives the case's expected answer (FORMAT 7),
  - the case is not in tools/split/known-differences.txt (those are read by a person: Class B),
  - the JVM runtime, the Node tools bundle and the Python V8 engine (tools bundle) all answer it and pass, and every runtime that
    answers it (also the Node and Python V8 runtime bundle, which implement fewer kinds) writes the same canonical results line,
    byte for byte, at language level 1.0 and, for a case without `until`, at the newest level too.

    python3 tools/conformance/verify_class_a.py run  --out DIR --ref-cp1 CP1 --ref-cp2 CP2     # writes DIR/*.jsonl (needs the builds)
    python3 tools/conformance/verify_class_a.py list --out DIR [--evidence FILE]                # the flip list and its evidence
    python3 tools/conformance/verify_class_a.py flip --out DIR                                  # flips exactly that list in the case files
    python3 tools/conformance/verify_class_a.py flip --ids FILE                                 # flips the case ids of FILE (Class B, after reading)

A flip changes `verified` and nothing else (checked: the parsed files are equal but for `verified`)."""
from __future__ import annotations

import argparse, json, os, re, shlex, subprocess, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CASES = ROOT / "conformance" / "1"
sys.path.insert(0, str(ROOT / "tools" / "conformance"))
import rachana_conformance as rc  # noqa: E402

DRIVER = ROOT / "tools" / "conformance" / "rachana_conformance.py"
FORMATS = [ROOT / "spec" / "1" / "formats.yaml", ROOT / "tools" / "conformance" / "profile" / "drishti-formats.yaml"]
LEVELS = ["1.0", "1.1", "1.2"]
RUNTIMES = ("jvm", "nodet", "noder", "pyv8t", "pyv8r")          # the JVM, Node (tools, runtime bundle), Python V8 (tools, runtime bundle)
REQUIRED = ("jvm", "nodet", "pyv8t")
KNOWN = ROOT / "tools" / "split" / "known-differences.txt"


def run_all(out: Path, cp1: str, cp2: str) -> None:
    out.mkdir(parents=True, exist_ok=True)
    fm = " ".join(shlex.quote(str(f)) for f in FORMATS)
    env = dict(os.environ, RACHANA_DRISHTI_FORMATS=str(FORMATS[1]))
    py = lambda *a: subprocess.run([sys.executable, str(DRIVER), *a], env=env, check=False)
    py("--classpath-file", cp1, "--out", str(out / "ref-main.jsonl"), "--summary", str(out / "ref-main.sum"))
    py("--cmd", f"java -cp {Path(cp2).read_text().strip()} {ROOT / 'tools/reference-bridge/RefBridgeSutra.java'} {fm}", "--kinds", "sutra",
       "--out", str(out / "ref-sutra.jsonl"), "--summary", str(out / "ref-sutra.sum"))
    node = ROOT / "js" / "packages" / "rachana" / "dist"
    v8 = lambda b, rt: f"uv run -q --with mini-racer python3 {ROOT / 'tools/conformance/pyv8_runtime.py'} --bundle {b} --runtime {rt} --profiles core,drishti {fm}"
    for lv in LEVELS:
        common = ["--profiles", "core,drishti", "--language", lv]
        for rt, extra in (("jvm", ["--jvm"]), ("nodet", ["--node"]), ("noder", ["--node", "--bundle", str(node / "rachana.runtime.js")]),
                          ("pyv8t", ["--cmd", v8("tools", "pyv8t")]), ("pyv8r", ["--cmd", v8("runtime", "pyv8r")])):
            py(*extra, *common, "--runtime", rt, "--out", str(out / f"{rt}-{lv}.jsonl"), "--summary", str(out / f"{rt}-{lv}.sum"))


def read(p: Path) -> dict[str, str]:
    return {json.loads(l)["case"]: l for l in p.read_text(encoding="utf-8").splitlines() if l.strip()}


def known_differences() -> set[str]:
    return {l.split()[0] for l in KNOWN.read_text(encoding="utf-8").splitlines() if l.strip() and not l.startswith("#")}


def unverified():
    """[(path, suite, doc, case, case id, effective since, until)] of every case that is not verified."""
    out = []
    for p, suite, doc in rc.load_files(CASES):
        for c in doc["cases"]:
            m = rc.merged(doc, c)
            if not m["verified"]:
                out.append((p, suite, doc, c, f"{suite}#{c['id']}", str(m.get("since") or "1.0"), c.get("until")))
    return out


def decide(out: Path):
    """(flips: {case id: evidence}, not flipped: {case id: reason})"""
    ref = {**read(out / "ref-main.jsonl"), **{k: v for k, v in read(out / "ref-sutra.jsonl").items() if json.loads(v)["status"] != "unsupported"}}
    res = {(rt, lv): read(out / f"{rt}-{lv}.jsonl") for rt in RUNTIMES for lv in LEVELS}
    known = known_differences()
    flips, no = {}, {}
    for _p, _s, _d, c, cid, since, until in unverified():
        r = json.loads(ref[cid])
        if since != "1.0":
            no[cid] = f"not recorded from the reference: since {since}"
        elif r["status"] != "pass":
            no[cid] = f"the reference does not give the expected answer ({r['status']})"
        elif cid in known:
            no[cid] = "listed in known-differences.txt (read by a person)"
        else:
            levels = [lv for lv in LEVELS if lv == "1.0" or (until is None and lv == LEVELS[-1])]
            why, lines = None, {}
            for lv in levels:
                got = {rt: json.loads(res[(rt, lv)][cid]) for rt in RUNTIMES}
                for rt in REQUIRED:
                    if got[rt]["status"] != "pass":
                        why = f"{rt} at {lv}: {got[rt]['status']}"
                for rt in RUNTIMES:
                    if got[rt]["status"] not in ("pass", "unsupported"):
                        why = why or f"{rt} at {lv}: {got[rt]['status']}"
                answered = {rt: res[(rt, lv)][cid] for rt in RUNTIMES if got[rt]["status"] == "pass"}
                if len(set(answered.values())) > 1:
                    why = why or f"the runtimes wrote different bytes at {lv}"
                lines[lv] = sorted(answered)
            if why:
                no[cid] = why
            else:
                flips[cid] = (f"reference i7a pass; {', '.join(lines['1.0'])} same bytes at 1.0"
                              + (f"; at {LEVELS[-1]} {', '.join(lines[LEVELS[-1]])}" if until is None else "; until-bounded"))
    return flips, no


def flip(ids: set[str]) -> int:
    """Set `verified: true` for the case ids (text edit; nothing else changes), then prove it by parsing."""
    by_file: dict[Path, set[str]] = {}
    for p, suite, doc, c, cid, *_ in unverified():
        if cid in ids:
            by_file.setdefault(p, set()).add(c["id"])
    n = 0
    for p, want in by_file.items():
        suite = p.relative_to(CASES).with_suffix("").as_posix()
        before = rc.load_files_one(p) if hasattr(rc, "load_files_one") else None
        text = p.read_text(encoding="utf-8")
        old_doc = rc.yaml.load(text, Loader=rc.CoreLoader)
        fv = bool(old_doc.get("verified", False))
        unv = [c["id"] for c in old_doc["cases"] if not c.get("verified", fv)]
        lines = text.split("\n")
        starts = [i for i, l in enumerate(lines) if re.match(r'^  - id: "?[a-z0-9][a-z0-9-]*"?\s*$', l)]
        if len(starts) != len(old_doc["cases"]):
            sys.exit(f"{p}: {len(old_doc['cases'])} cases but {len(starts)} block starts (a flow-style case?): flip by hand")
        idx = {old_doc["cases"][k]["id"]: starts[k] for k in range(len(starts))}
        end = {old_doc["cases"][k]["id"]: (starts[k + 1] if k + 1 < len(starts) else len(lines)) for k in range(len(starts))}
        edits = []                                           # (line index, "set"|"insert", text)
        use_file_level = set(unv) == want or len(want) * 2 >= len(unv)
        if use_file_level:
            fl = [i for i, l in enumerate(lines[:starts[0]]) if re.match(r"^verified: (true|false)\s*$", l)]
            if not fl:
                sys.exit(f"{p}: no file-level verified line")
            edits.append((fl[0], "set", "verified: true"))
            targets = {cid: "false" for cid in unv if cid not in want}      # the rest are marked false explicitly
            flipped_explicit = {cid: "true" for cid in want}
        else:
            targets = {}
            flipped_explicit = {cid: "true" for cid in want}
        for cid in sorted(set(targets) | set(flipped_explicit)):
            val = targets.get(cid) or flipped_explicit[cid]
            body = range(idx[cid] + 1, end[cid])
            ex = [i for i in body if re.match(r"^    verified: (true|false)\s*$", lines[i])]
            if use_file_level and cid in want:
                if ex:
                    edits.append((ex[0], "delete" if False else "set", "    verified: true"))   # an explicit false of a flipped case
                continue
            if ex:
                edits.append((ex[0], "set", f"    verified: {val}"))
            else:
                edits.append((idx[cid], "insert", f"    verified: {val}"))
        for i, kind, t in sorted(edits, key=lambda e: -e[0]):
            if kind == "set":
                lines[i] = t
            else:
                lines.insert(i + 1, t)
        new = "\n".join(lines)
        new_doc = rc.yaml.load(new, Loader=rc.CoreLoader)
        strip = lambda d: {**{k: v for k, v in d.items() if k not in ("verified", "cases")}, "cases": [{k: v for k, v in c.items() if k != "verified"} for c in d["cases"]]}
        if strip(old_doc) != strip(new_doc):
            sys.exit(f"{p}: the edit changed something other than `verified`")
        nfv = bool(new_doc.get("verified", False))
        for c in new_doc["cases"]:
            was = bool(next(o for o in old_doc["cases"] if o["id"] == c["id"]).get("verified", fv))
            now = bool(c.get("verified", nfv))
            if now != (was or c["id"] in want):
                sys.exit(f"{p}: {c['id']} is {now} after the edit, expected {was or c['id'] in want}")
        p.write_text(new, encoding="utf-8", newline="\n")
        n += len(want)
    return n


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("action", choices=["run", "list", "flip"])
    ap.add_argument("--out", type=Path)
    ap.add_argument("--ref-cp1"); ap.add_argument("--ref-cp2")
    ap.add_argument("--ids", type=Path, help="flip: the case ids to flip, one per line (Class B)")
    ap.add_argument("--evidence", type=Path, help="list: write `case id<TAB>evidence` lines here")
    a = ap.parse_args(argv)
    if a.action == "run":
        run_all(a.out, a.ref_cp1, a.ref_cp2); return 0
    if a.ids:
        ids = {l.split("\t")[0].strip() for l in a.ids.read_text(encoding="utf-8").splitlines() if l.strip() and not l.startswith("#")}
        print(f"flipped {flip(ids)} case(s)"); return 0
    flips, no = decide(a.out)
    print(f"class A: {len(flips)} case(s) to flip; {len(no)} not recorded or not agreed")
    reasons = {}
    for r in no.values():
        reasons[re.sub(r"since .*", "since later", r)] = reasons.get(re.sub(r"since .*", "since later", r), 0) + 1
    for r, k in sorted(reasons.items(), key=lambda x: -x[1]):
        print(f"  {k:4d}  {r}")
    if a.evidence:
        a.evidence.write_text("".join(f"{k}\t{v}\n" for k, v in sorted(flips.items())), encoding="utf-8")
    if a.action == "flip":
        print(f"flipped {flip(set(flips))} case(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
