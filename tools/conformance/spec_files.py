#!/usr/bin/env python3
# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see LICENSE.
"""The "spec not stale" guard (plan R0 H4): spec/1/rachana.lang.json and spec/1/sutra.schema.json are generated from the compiler's own
tables by java/rachana-tools LangFiles at the newest written language level (compat/levels/<L>) and committed.

    python3 tools/conformance/spec_files.py            check: exit 1 when stale
    python3 tools/conformance/spec_files.py --write    regenerate both files (a release writes a level with tools/regime/levels.py write)

Build first: cd java && ./mvnw clean package -DskipTests. Run on Java 21."""
import argparse
import difflib
import os
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FILES = ["rachana.lang.json", "sutra.schema.json"]


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--write", action="store_true")
    ap.add_argument("--java", default=os.environ.get("RACHANA_JAVA", "java"))
    a = ap.parse_args(argv)
    level = sorted(p.name for p in (ROOT / "compat" / "levels").iterdir() if p.is_dir())[-1]
    classes = [ROOT / "java" / m / "target" / "classes" for m in ("rachana-tools", "rachana-core", "rachana-api")]
    if not all(c.is_dir() for c in classes):
        sys.exit("build first: cd java && ./mvnw clean package -DskipTests")
    with tempfile.TemporaryDirectory(prefix="rachana-spec-") as tmp:
        subprocess.run([a.java, "-cp", os.pathsep.join(str(c) for c in classes), "com.ash.rachana.tools.LangFiles", level, tmp], check=True)
        stale = []
        for name in FILES:
            new, old = Path(tmp) / name, ROOT / "spec" / "1" / name
            if a.write:
                old.write_bytes(new.read_bytes())
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
        print(f"spec/1: {', '.join(FILES)} are current (level {level})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
