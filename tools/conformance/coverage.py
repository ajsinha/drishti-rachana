#!/usr/bin/env python3
# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see LICENSE.
"""Plan H5, the coverage gate over the definition (the part that exists before rachana.lang.json): every function of
spec/1/functions.yaml `core` appears in at least one `expr` case of conformance/1 (as `name(`, or named as a syntax form).
    python3 tools/conformance/coverage.py [--list]      exit 0 when every Core function is covered, 1 otherwise"""
import re
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]


def main() -> int:
    cat = yaml.safe_load((ROOT / "spec" / "1" / "functions.yaml").read_text(encoding="utf-8"))
    names = [f["name"] for f in cat["core"]]
    text = "\n".join(p.read_text(encoding="utf-8") for p in (ROOT / "conformance" / "1").rglob("*.yaml"))
    missing = [n for n in names if not re.search(r"(?<![A-Za-z0-9_])" + re.escape(n) + r"\s*\(", text)]
    print(f"{len(names)} Core functions, {len(names) - len(missing)} covered by a case, {len(missing)} not")
    if "--list" in sys.argv:
        print("\n".join(missing))
    return 1 if missing else 0


if __name__ == "__main__":
    sys.exit(main())
