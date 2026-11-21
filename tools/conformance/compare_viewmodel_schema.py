#!/usr/bin/env python3
# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see LICENSE.
"""Plan H4: spec/1/viewmodel.schema.json against the reference's ViewModelSchema.build() (dumped by tools/reference-bridge/SchemaDump.java).
Descriptions and x-* annotations (x-java, x-include, x-since) are ignored, and {allOf: [x]} (a $ref that can carry a description) is read as x.
    python3 tools/conformance/compare_viewmodel_schema.py reference.json     # exit 0: structurally equal, 1: differences listed"""
import json
import sys
from pathlib import Path

SPEC = Path(__file__).resolve().parents[2] / "spec" / "1" / "viewmodel.schema.json"
NOISE = ("description", "x-include", "x-java", "x-since", "$comment")


def norm(x):
    if isinstance(x, dict):
        rest = {k: v for k, v in x.items() if k not in NOISE}
        if set(rest) == {"allOf"} and len(rest["allOf"]) == 1:
            return norm(rest["allOf"][0])
        return {k: norm(v) for k, v in rest.items()}
    return [norm(v) for v in x] if isinstance(x, list) else x


def main() -> int:
    ref = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))["$defs"]
    mine = json.loads(SPEC.read_text(encoding="utf-8"))["$defs"]
    bad = sorted(set(ref) ^ set(mine)) + [k for k in ref if k in mine and norm(ref[k]) != norm(mine[k])]
    for k in bad:
        print("differs:", k)
    print(f"{len(ref)} reference definitions, {len(mine)} in the specification, {len(bad)} differ")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
