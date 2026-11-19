# Copyright (c) 2026 Ashutosh Sinha. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""Byte comparison of results files: diff.py <reference.jsonl> <other.jsonl>...; prints differing cases per runtime."""
import json, sys
ref = open(sys.argv[1], encoding="utf-8").read().split("\n")
for other in sys.argv[2:]:
    o = open(other, encoding="utf-8").read().split("\n")
    diffs = [(a, b) for a, b in zip(ref, o) if a != b]
    print(f"{other}: {len(diffs)} lines differ of {len(ref) - 1}" + ("" if len(ref) == len(o) else f" (line counts {len(ref)} vs {len(o)})"))
    for a, b in diffs:
        x, y = json.loads(a), json.loads(b)
        print("  ", x["case"], "|", json.dumps(x["actual"], ensure_ascii=False)[:90], "|", json.dumps(y["actual"], ensure_ascii=False)[:90])
