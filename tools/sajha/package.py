#!/usr/bin/env python3
# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""Builds the SAJHA plugin zip, reproducibly: the same tree gives the same bytes.

    python3 tools/sajha/package.py [out_dir]      default out_dir: dist/release-<version>; writes rachana-genui-sajha-plugin-<version>.zip

The zip holds one folder, rachana_genui/: INSTALL.md, README.md, plugin.json, requirements.txt, tools/, schemas/, rachana_genui/, spa/,
catalogue/, docs/, example/ (all of tools/sajha, without tests) and libraries/ (the repository's core and finance libraries). A relative
link that leaves the folder (into the checkout's docs/design or spec/1) is reduced to its text, so no link in the zip is broken."""
import re
import sys
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
TOP = "rachana_genui"
STAMP = (2020, 2, 2, 0, 0, 0)
PARTS = ("INSTALL.md", "README.md", "plugin.json", "requirements.txt", "tools", "schemas", "rachana_genui", "spa", "catalogue", "docs", "example")
OUTSIDE = re.compile(r"\[([^\]]+)\]\((?:\.\./){2,}[^)]*\)")     # a link that climbs out of the kit's folder


def files():
    for part in PARTS:
        p = HERE / part
        yield from ([p] if p.is_file() else sorted(q for q in p.rglob("*") if q.is_file()))


def add(z, name, data):
    zi = zipfile.ZipInfo(f"{TOP}/{name}", STAMP)
    zi.compress_type = zipfile.ZIP_DEFLATED
    zi.external_attr = 0o644 << 16
    z.writestr(zi, data)


def main(argv):
    version = re.search(r'^version = "([^"]+)"', (ROOT / "python" / "pyproject.toml").read_text(encoding="utf-8"), re.M).group(1)
    out = Path(argv[1]) if len(argv) > 1 else ROOT / "dist" / f"release-{version}"
    out.mkdir(parents=True, exist_ok=True)
    target = out / f"rachana-genui-sajha-plugin-{version}.zip"
    with zipfile.ZipFile(target, "w") as z:
        for f in files():
            if "__pycache__" in f.parts or f.suffix == ".pyc":
                continue
            data = f.read_bytes()
            if f.suffix == ".md":
                data = OUTSIDE.sub(r"\1", data.decode("utf-8")).encode("utf-8")
            add(z, f.relative_to(HERE).as_posix(), data)
        libs = ROOT / "libraries"
        for f in sorted(q for q in libs.rglob("*") if q.is_file() and "__pycache__" not in q.parts):
            add(z, "libraries/" + f.relative_to(libs).as_posix(), f.read_bytes())
    print(target)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
