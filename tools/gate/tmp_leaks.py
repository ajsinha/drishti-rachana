#!/usr/bin/env python3
# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""A test run leaves no temporary folder behind (G2 gate F16). The reactor points every test's temporary folder (Java's java.io.tmpdir, TMPDIR/TEMP/TMP
of the Node and Python programs it starts) at one folder of its own, target/tmp; `create` makes it empty when the build starts and `check` fails the
build, naming what is left, when it ends. Every test cleans its own temporary folders (a JUnit @TempDir, a try/finally, node:test `after`).

    python3 tools/gate/tmp_leaks.py create <folder>
    python3 tools/gate/tmp_leaks.py check <folder>"""
import shutil
import sys
from pathlib import Path


def main(argv: list[str]) -> int:
    if len(argv) != 2 or argv[0] not in ("create", "check"):
        print(__doc__, file=sys.stderr)
        return 2
    d = Path(argv[1])
    if argv[0] == "create":
        shutil.rmtree(d, ignore_errors=True)
        d.mkdir(parents=True)
        return 0
    # uv's own empty lock files (uv-<hash>.lock) are the tool's, not a test's
    left = sorted(p.name for p in d.iterdir() if not (p.name.startswith("uv-") and p.name.endswith(".lock"))) if d.is_dir() else None
    if left is None:
        print(f"tmp_leaks: {d} does not exist: the build did not create it, so nothing was checked", file=sys.stderr)
        return 1
    if left:
        print(f"tmp_leaks: the tests left {len(left)} temporary item(s) in {d}:\n  " + "\n  ".join(left[:40]) + "\nEvery test must remove what it creates.", file=sys.stderr)
        return 1
    print("tmp_leaks: no temporary folder left behind")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
