# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""Checks the examples against spec/1 (needs PyYAML and jsonschema). Run: python3 -I examples/check_examples.py

 1. every CSV parses and has equal-width rows;
 2. every Sutra is valid YAML (no duplicate keys), uses only Sutra Core top-level keys, panel kinds, panel options,
    data-entry keys, function names, format names and tones (spec/1/SPEC.md 2.2 to 2.6, spec/1/functions.yaml,
    spec/1/formats.yaml);
 3. gen_orders.py runs for 2 seconds and every line of its output validates against spec/1/data-protocol/patch.json.
"""
import csv, json, re, subprocess, sys
from pathlib import Path
import yaml, jsonschema

ROOT = Path(__file__).resolve().parent
SPEC = ROOT.parent / "spec" / "1"

TOP = {"rachana", "sutra", "version", "profiles", "description", "notes", "doc", "title", "strip", "controls", "data",
       "panels", "layout", "phone", "keys", "refresh"}
COMMON = {"id", "kind", "title", "description", "key", "code", "area", "order", "section", "span", "height", "collapsible",
          "phone", "refresh", "when", "where", "on-select", "source", "emits", "waits", "children", "columns", "form",
          "infer"}
TABLE = {"rows", "totalLabel", "limit", "moreLabel", "link", "highlight", "search", "pivot", "children", "expand", "levels",
         "crumb", "root", "empty"}
KINDS = {  # SPEC 2.3: kind -> (required, accepted)
    "kv": (set(), {"rows", "columns", "fields"}), "table": ({"rows"}, TABLE),
    "line": (set(), {"rows", "x", "y", "mark", "footer", "unit", "fmt", "step"}),
    "hbar": ({"rows"}, {"label", "value", "fmt", "tone", "shape"}),
    "metric": ({"value"}, {"label", "fmt", "tone", "delta", "deltaFmt", "deltaTone", "unit", "caption"}),
    "markdown": ({"text"}, set()),
}
CONTROL = {"id", "kind", "label", "options", "from", "default", "remember", "required", "placement", "panel", "parent",
           "min", "max", "step", "range"}
DATA = {"table", "url", "rows", "record", "format", "path", "key", "fields", "as", "where", "limit", "refresh", "live", "mode"}
SOURCES = {"table", "url", "rows", "record"}
COLUMN = {"label", "bind", "fmt", "tone", "link", "total", "emphasis"}

def functions():
    doc = yaml.safe_load((SPEC / "functions.yaml").read_text())
    return {f["name"] for f in doc["core"]}          # deprecated spellings are not for new Sutras

class Strict(yaml.SafeLoader):
    pass
def _map(loader, node):
    seen = set()
    for k, _ in node.value:
        key = loader.construct_object(k)
        if key in seen:
            raise yaml.YAMLError(f"duplicate key {key!r} line {k.start_mark.line + 1}")
        seen.add(key)
    return loader.construct_mapping(node)
Strict.add_constructor(yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, _map)

def expressions(value):
    """Strings of an expression/template position: yield text; templates give their ${...} parts."""
    if isinstance(value, str):
        yield value

def calls(text):
    text = re.sub(r"'[^']*'|\"[^\"]*\"", "''", text)           # drop literals
    return set(re.findall(r"\b([A-Za-z_][A-Za-z0-9_]*)\s*\(", text))

TONES = {"sign", "status", "pos", "neg", "link", "accent", "ok", "warn", "bad"}   # SPEC 2.6

def formats():
    doc = yaml.safe_load((SPEC / "formats.yaml").read_text())
    return set(doc["formats"]) | {"list"}                 # list is built in (formats.yaml, last comment)

def check_looks(where, entry, fmts, bad):
    for k in ("fmt", "deltaFmt"):
        if k in entry and entry[k] not in fmts: bad(f"{where}: format {entry[k]} is not in formats.yaml")
    for k in ("tone", "deltaTone"):
        if k in entry and entry[k] not in TONES: bad(f"{where}: tone {entry[k]} is not a Core tone")

def check_sutra(path, fns, errs, fmts=frozenset()):
    def bad(msg): errs.append(f"{path.name}: {msg}")
    try:
        doc = yaml.load(path.read_text(), Strict)
    except yaml.YAMLError as e:
        return bad(f"YAML: {e}")
    for k in set(doc) - TOP: bad(f"unknown top-level key {k}")
    if str(doc.get("rachana")) not in ("1", "1.1", "1.2"): bad("rachana must be a declared level: 1, 1.1 or 1.2")
    if not re.fullmatch(r"[a-z][a-z0-9-]{1,63}", str(doc.get("sutra", ""))): bad("bad sutra name")
    texts = [doc.get("title", {}).get("id", "")]
    for name, src in (doc.get("data") or {}).items():
        if isinstance(src, str): continue
        for k in set(src) - DATA: bad(f"data.{name}: unknown key {k}")
        if len(set(src) & SOURCES) != 1: bad(f"data.{name}: needs exactly one of {sorted(SOURCES)}")
    for c in doc.get("controls") or []:
        for k in set(c) - CONTROL: bad(f"control {c.get('id')}: unknown key {k}")
        texts.append(str(c.get("from", "")))
    ids = set()
    for p in doc.get("panels") or []:
        pid, kind = p.get("id"), p.get("kind")
        if pid in ids: bad(f"duplicate panel id {pid}")
        ids.add(pid)
        if kind not in KINDS: bad(f"panel {pid}: kind {kind} not used in these examples' checker"); continue
        req, acc = KINDS[kind]
        for k in req - set(p): bad(f"panel {pid}: missing {k}")
        for k in set(p) - COMMON - req - acc: bad(f"panel {pid}: option {k} not accepted by {kind}")
        check_looks(f"panel {pid}", p, fmts, bad)
        for col in p.get("columns") or []:
            for k in set(col) - COLUMN: bad(f"panel {pid}: column key {k}")
            check_looks(f"panel {pid} column {col.get('label')}", col, fmts, bad)
            texts.append(str(col.get("bind", "")))
        for k in ("rows", "value", "delta", "caption", "title", "text", "limit"):
            if k in p: texts.append(str(p[k]))
    for t in texts:
        parts = re.findall(r"\$\{(.*?)\}", t) or [t]
        for part in parts:
            for fn in calls(part):
                if fn not in fns: bad(f"function {fn} is not in functions.yaml (in: {part})")

def main():
    errs = []
    for f in sorted(ROOT.rglob("*.csv")):
        rows = list(csv.reader(f.read_text().splitlines()))
        if len({len(r) for r in rows}) != 1: errs.append(f"{f.name}: ragged rows")
        print(f"csv   {f.relative_to(ROOT)}: {len(rows) - 1} rows")
    fns = functions()
    for f in sorted(ROOT.rglob("*.sutra.yaml")):
        check_sutra(f, fns, errs, formats())
        print(f"sutra {f.relative_to(ROOT)}")
    schema = json.loads((SPEC / "data-protocol" / "patch.json").read_text())
    out = subprocess.run([sys.executable, "-I", str(ROOT / "live-orders" / "gen_orders.py"), "--duration", "2", "--rate", "10",
                          "--seed", "3"], capture_output=True, text=True, timeout=20, check=True).stdout.splitlines()
    kinds = set()
    for line in out:
        patch = json.loads(line)
        jsonschema.validate(patch, schema)
        kinds |= {k for k in ("add", "update", "remove") if k in patch}
    print(f"patch {len(out)} lines valid against patch.json; operations seen: {sorted(kinds)}")
    for e in errs: print("ERROR", e)
    return 1 if errs else 0

if __name__ == "__main__":
    sys.exit(main())
