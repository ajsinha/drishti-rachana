#!/usr/bin/env python3
# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""The fragment catalogue as one JSON document, generated from the libraries' doc blocks (and their declared interfaces), with the
doc lint's findings (RCH-2093). The tools build the same catalogue in memory; this writes it for review, for another host, or for a
search index.

    python3 tools/sajha/catalogue/generate.py [--libs PATH ...] [--out catalogue.json] [--search "limit use" --columns NAME:text,USED:number]

Exit 1 when any fragment's doc is thin (the guard)."""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from rachana_genui import catalogue  # noqa: E402
from rachana_genui.settings import settings  # noqa: E402


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--libs", nargs="*", help="library folders or .rlib files (default: RACHANA_LIBS or the checkout's libraries/)")
    ap.add_argument("--out", help="write the catalogue here (default: standard output)")
    ap.add_argument("--search", help="instead, print the results of a search")
    ap.add_argument("--columns", help="with --search: one table's columns, NAME:type,...")
    a = ap.parse_args(argv)
    cat = catalogue.load(a.libs or settings().libraries)
    if a.search is not None:
        tables = None
        if a.columns:
            tables = {"t": {"columns": [{"name": c.split(":")[0], "type": (c.split(":") + [""])[1]} for c in a.columns.split(",")]}}
        for r in cat.search(a.search, tables=tables):
            print(f"{r['score']:7.2f}  {r['id']:40} {r['doc'].get('summary', '')}")
            print(f"         use: {json.dumps(r['use'])}")
        return 0
    doc = json.dumps(cat.to_json(), indent=1, ensure_ascii=False, default=str)
    if a.out:
        Path(a.out).write_text(doc + "\n", encoding="utf-8")
        print(f"{len(cat.entries)} fragments from {len(cat.libraries)} libraries into {a.out}; {len(cat.problems)} doc problems")
    else:
        print(doc)
    for p in cat.problems:
        print(f"{p['file']}:{p['range']['start']['line']}: {p['code']} {p['message']}", file=sys.stderr)
    return 1 if cat.problems else 0


if __name__ == "__main__":
    raise SystemExit(main())
