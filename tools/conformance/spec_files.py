#!/usr/bin/env python3
# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see LICENSE.
"""Plan R0 H4, the "spec not stale" guard (SPLIT_PLAN 0c, section 5): spec/1/rachana.lang.json and spec/1/sutra.schema.json are generated
from the reference compiler's tables by tools/reference-bridge/LangDump.java (read-only over Drishti's jars) and committed here.

    python3 tools/conformance/spec_files.py --classpath-file CP.txt --formats <drishti core pack formats.yaml>          check: exit 1 when stale
    python3 tools/conformance/spec_files.py --classpath-file CP.txt --formats ... --write                              regenerate both files

CP.txt holds the class path of the reference's jars (the RefBridge one: drishti-rachana, -api, -common and their libraries). Run it on
Java 21 (the reference's production runtime). The generation is deterministic (keys sorted, set-valued lists sorted)."""
import argparse
import difflib
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FILES = ["rachana.lang.json", "sutra.schema.json"]


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--classpath-file", required=True)
    ap.add_argument("--formats", required=True, help="Drishti's core pack formats.yaml (the profile's format names)")
    ap.add_argument("--write", action="store_true")
    ap.add_argument("--java", default=os.environ.get("RACHANA_JAVA", "java"))
    a = ap.parse_args(argv)
    cp = Path(a.classpath_file).read_text().strip()
    with tempfile.TemporaryDirectory(prefix="rachana-spec-") as tmp:
        subprocess.run([a.java, "-cp", cp, str(ROOT / "tools" / "reference-bridge" / "LangDump.java"), tmp, a.formats], check=True,
                       stderr=subprocess.DEVNULL if not os.environ.get("RACHANA_VERBOSE") else None)
        stale = []
        for name in FILES:
            new, old = Path(tmp) / name, ROOT / "spec" / "1" / name
            if a.write:
                shutil.copyfile(new, old)
                print(f"wrote spec/1/{name}")
            elif not old.exists() or old.read_bytes() != new.read_bytes():
                stale.append(name)
                if old.exists():
                    diff = difflib.unified_diff(old.read_text().splitlines(), new.read_text().splitlines(), f"spec/1/{name}", "generated", lineterm="", n=1)
                    print("\n".join(list(diff)[:40]))
        if stale:
            print(f"stale: {', '.join(stale)} (regenerate with --write and review the diff)", file=sys.stderr)
            return 1
    if not a.write:
        print("spec/1: " + ", ".join(FILES) + " are current")
    return 0


if __name__ == "__main__":
    sys.exit(main())
