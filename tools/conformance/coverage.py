#!/usr/bin/env python3
# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see LICENSE.
"""Plan R0 H5, the coverage gate (RUNNER 7 rule 3, COMPILER_AND_RUNTIMES 6.2): every key, panel option, panel kind, control kind,
function, format, tone and problem code of spec/1/rachana.lang.json appears in at least one case of conformance/1, and every Core
function of spec/1/functions.yaml in an expression.

    python3 tools/conformance/coverage.py               exit 0 when every Core entry is covered or listed in conformance/coverage-gaps.txt
    python3 tools/conformance/coverage.py --list        also print the uncovered entries, the profile's included
    python3 tools/conformance/coverage.py --write-gaps  rewrite conformance/coverage-gaps.txt from the run (review the diff: it may only shrink)

The gate is over Core entries (profile "core"); the Drishti profile's entries are reported, its cases live in the profile's suite.
conformance/coverage-gaps.txt is the work queue: an uncovered Core entry not in it fails (a new definition entry needs a case), and an
entry in it that is now covered fails too (the queue only shrinks, and the file says so exactly).

How a case covers an entry (read from the parsed case files, not from comments or notes):
  function  `name(` in an expression, template or Sutra text of a case
  key       a mapping key at its place (a form field's lookup is the mapping under its options) in a case's Sutra (sutra: top level; panel: an item of panels/body; column: an item of columns;
            control: an item of controls; source: the mapping under source; ...: the place names of rachana.lang.json `keys`)
  panel kind / control kind / panel option   `kind:` of a panel or a control; a key of a panel mapping
  format    `format` of a format case, or fmt/deltaFmt/xFmt in a Sutra
  tone      `tone` of a format case, or tone/deltaTone in a Sutra
  code      a `code` in an `expect` (a problem, an error)"""
import json
import re
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
GAPS = ROOT / "conformance" / "coverage-gaps.txt"
# the key a mapping sits under in a Sutra -> its place in rachana.lang.json `keys`
PLACE_OF = {"panels": "panel", "body": "panel", "strip": "strip", "columns": "column", "controls": "control", "layout": "layout",
            "source": "source", "emits": "emit", "children": "children", "form": "form", "save": "form.save", "fields": "form.field",
            "lookup": "form.lookup", "rules": "form.rule", "hints": "form.hint", "levels": "level", "markers": "marker"}


class Seen:
    def __init__(self):
        self.keys, self.panel_kinds, self.control_kinds, self.options = set(), set(), set(), set()
        self.formats, self.tones, self.codes, self.texts = set(), set(), set(), []


def walk(node, place, seen):
    """Records (place, key) for every mapping key of a Sutra; a panel's `phone` mapping is panel.phone, the top level's is phone."""
    if isinstance(node, list):
        for x in node:
            walk(x, place, seen)
        return
    if not isinstance(node, dict):
        return
    for k, v in node.items():
        k = str(k)
        seen.keys.add((place, k))
        if place == "panel":
            seen.options.add(k)
            if k == "kind" and isinstance(v, str):
                seen.panel_kinds.add(v)
        if place == "control" and k == "kind" and isinstance(v, str):
            seen.control_kinds.add(v)
        if k in ("fmt", "deltaFmt", "xFmt") and isinstance(v, str):
            seen.formats.add(v)
        if k in ("tone", "deltaTone") and isinstance(v, str):
            seen.tones.add(v)
        if k == "phone" and isinstance(v, dict):
            walk(v, "panel.phone" if place == "panel" else "phone", seen)
        elif k == "options" and place == "form.field" and isinstance(v, dict):    # a field's lookup is written under options
            walk(v, "form.lookup", seen)
        elif k == "source" and place == "form.lookup":
            walk(v, "form.lookup.source", seen)
        elif k in PLACE_OF and isinstance(v, (dict, list)):
            walk(v, PLACE_OF[k], seen)


def codes_in(v, seen):
    if isinstance(v, dict):
        for k, x in v.items():
            if k == "code" and isinstance(x, str):
                seen.codes.add(x)
            codes_in(x, seen)
    elif isinstance(v, list):
        for x in v:
            codes_in(x, seen)


def read_cases():
    seen = Seen()
    for p in sorted((ROOT / "conformance" / "1").rglob("*.yaml")):
        doc = yaml.safe_load(p.read_text(encoding="utf-8")) or {}
        for c in doc.get("cases") or []:
            for f in ("expr", "template"):
                if isinstance(c.get(f), str):
                    seen.texts.append(c[f])
            sutras = [c["sutra"]] if isinstance(c.get("sutra"), str) else []
            sutras += [t for t in (c.get("files") or {}).values() if isinstance(t, str)]
            for s in sutras:
                seen.texts.append(s)
                try:
                    walk(yaml.safe_load(s), "sutra", seen)
                except (yaml.YAMLError, ValueError):     # a Sutra a case refuses may not even be YAML
                    pass
            if c.get("kind") == "format":
                if isinstance(c.get("format"), str):
                    seen.formats.add(c["format"])
                if isinstance(c.get("tone"), str):
                    seen.tones.add(c["tone"])
                if isinstance((c.get("expect") or {}).get("tone"), str):      # the tone a tone word resolves to (pos, neg, ok ...)
                    seen.tones.add(c["expect"]["tone"])
            codes_in(c.get("expect"), seen)
            for step in c.get("steps") or []:
                codes_in(step, seen)
    return seen


def uncovered(lang, seen):
    """(gap line, profile) for every entry no case covers."""
    called = set(re.findall(r"(?<![A-Za-z0-9_.$@])([A-Za-z_][A-Za-z0-9_]*)\s*\(", "\n".join(seen.texts)))
    out = []
    for place, entries in lang["keys"].items():
        out += [(f"key {place}.{e['name']}", e["profile"]) for e in entries if (place, e["name"]) not in seen.keys]
    out += [(f"panel-kind {e['name']}", e["profile"]) for e in lang["panelKinds"] if e["name"] not in seen.panel_kinds]
    out += [(f"panel-option {e['name']}", e["profile"]) for e in lang["panelOptions"] if e["name"] not in seen.options]
    out += [(f"control-kind {e['name']}", e["profile"]) for e in lang["controlKinds"] if e["name"] not in seen.control_kinds]
    out += [(f"function {e['name']}", e["profile"]) for e in lang["functions"] if e["name"] not in called]
    out += [(f"format {e['name']}", e["profile"]) for e in lang["formats"] if e["name"] not in seen.formats]
    out += [(f"tone {e['name']}", e["profile"]) for e in lang["tones"] if e["name"] not in seen.tones]
    out += [(f"code {e['code']}", e["profile"]) for e in lang["codes"] if e["code"] not in seen.codes]
    return out


def main() -> int:
    lang = json.loads((ROOT / "spec" / "1" / "rachana.lang.json").read_text(encoding="utf-8"))
    seen = read_cases()
    gaps = uncovered(lang, seen)
    core = sorted(g for g, p in gaps if p == "core")
    profile = sorted(g for g, p in gaps if p != "core")
    cat = yaml.safe_load((ROOT / "spec" / "1" / "functions.yaml").read_text(encoding="utf-8"))
    text = "\n".join(seen.texts)
    fn_missing = [f["name"] for f in cat["core"] if not re.search(r"(?<![A-Za-z0-9_])" + re.escape(f["name"]) + r"\s*\(", text)]
    total = sum(len(v) for v in lang["keys"].values()) + sum(len(lang[k]) for k in
                                                              ("panelKinds", "panelOptions", "controlKinds", "functions", "formats", "tones", "codes"))
    print(f"rachana.lang.json: {total} entries; uncovered: {len(core)} Core, {len(profile)} Drishti profile")
    print(f"functions.yaml: {len(cat['core'])} Core functions, {len(cat['core']) - len(fn_missing)} in an expression of a case")
    if "--write-gaps" in sys.argv:
        GAPS.write_text("# Copyright (c) 2026 Ashutosh Sinha. Specification licence: ../LICENSE-SPEC.md (draft, not yet in force).\n"
                        "# Core entries of spec/1/rachana.lang.json no case covers yet (plan R0 H5): the work queue; it may only shrink.\n"
                        "# Written by tools/conformance/coverage.py --write-gaps.\n" + "".join(g + "\n" for g in core), encoding="utf-8")
        print(f"wrote {GAPS.relative_to(ROOT)} ({len(core)} entries)")
    listed = {l.strip() for l in GAPS.read_text(encoding="utf-8").splitlines() if l.strip() and not l.startswith("#")} if GAPS.exists() else set()
    new = [g for g in core if g not in listed]
    closed = sorted(listed - set(core))
    if "--list" in sys.argv:
        print("\n".join(["uncovered Core:"] + core + ["uncovered profile:"] + profile + ["functions.yaml Core not in an expression:"] + fn_missing))
    if new:
        print("not covered and not in coverage-gaps.txt (write a case):\n  " + "\n  ".join(new), file=sys.stderr)
    if closed:
        print("covered now, still in coverage-gaps.txt (run --write-gaps):\n  " + "\n  ".join(closed), file=sys.stderr)
    if fn_missing:
        print("Core functions of functions.yaml with no case: " + ", ".join(fn_missing), file=sys.stderr)
    return 1 if new or closed or fn_missing else 0


if __name__ == "__main__":
    sys.exit(main())
