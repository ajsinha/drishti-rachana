# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""Fragment libraries for ``rachana lint``: the bundled ``libraries/`` of a Rachana checkout is one source among others. Sources, in
order: ``<project>/rachana_libs``, each ``--libs`` path (an unpacked library, an ``.rlib``, or a folder of them), ``RACHANA_LIBS``
(path-delimited), then the bundled ones. A name found twice with different contents is refused with a problem, never shadowed."""
from __future__ import annotations

import os
import re
import zipfile
from pathlib import Path
from typing import Iterable, Optional

MANIFEST = re.compile(r"\.rachana-lib\.yaml$")
MAX_FILE = 2 * 1024 * 1024
BUILT_IN = Path(__file__).resolve().parents[3] / "libraries"      # a source checkout; absent in an installed wheel


def _top(text: str, key: str) -> Optional[str]:
    m = re.search(rf'^{key}:\s*"?([^"\s#]+)"?', text, re.M)
    return m.group(1) if m else None


def _describe(files: dict[str, str], source: str) -> dict:
    mf = next((n for n in files if "/" not in n and MANIFEST.search(n)), None)
    if not mf:
        raise ValueError(f"{source}: no *.rachana-lib.yaml at the top")
    name = _top(files[mf], "library")
    if not name:
        raise ValueError(f"{source}: the manifest names no library")
    return {"name": name, "version": _top(files[mf], "version") or "", "source": source, "files": files}


def read_library(path: Path) -> dict:
    files: dict[str, str] = {}
    if path.is_dir():
        for f in sorted(path.rglob("*")):
            rel = f.relative_to(path).parts
            if f.is_file() and not any(x.startswith(".") for x in rel) and f.stat().st_size <= MAX_FILE:
                files["/".join(rel)] = f.read_text(encoding="utf-8")
    else:
        with zipfile.ZipFile(path) as z:
            for i in z.infolist():
                n = i.filename
                if i.is_dir() or n.startswith("/") or ".." in n.split("/"):
                    continue
                if i.file_size <= MAX_FILE:
                    files[n] = z.read(i).decode("utf-8")
    return _describe(files, str(path))


def find_libraries(path: Path, problems: list[str]) -> list[dict]:
    if not path.exists():
        problems.append(f"no such library path: {path}")
        return []
    found: list[dict] = []

    def one(q: Path) -> None:
        try:
            found.append(read_library(q))
        except Exception as e:  # noqa: BLE001 - reported as a problem
            problems.append(str(e))

    if path.is_file():
        one(path)
    elif any(MANIFEST.search(n.name) for n in path.iterdir()):
        one(path)
    else:
        for q in sorted(path.iterdir()):
            if q.name.startswith("."):
                continue
            if (q.is_dir() and any(MANIFEST.search(x.name) for x in q.iterdir())) or (q.is_file() and q.name.endswith(".rlib")):
                one(q)
    return found


def _identity(lib: dict):
    return lib["files"].get("MANIFEST.sha384") or sorted(lib["files"].items())


def project_libraries(root: Path, extra: Iterable[str] = (), env: Optional[dict] = None) -> tuple[list[dict], list[str]]:
    env = os.environ if env is None else env
    problems: list[str] = []
    seen: dict[str, dict] = {}
    refused: set[str] = set()
    paths = [root / "rachana_libs", *map(Path, extra), *(Path(p) for p in (env.get("RACHANA_LIBS") or "").split(os.pathsep) if p), BUILT_IN]
    for p in paths:
        if p in (paths[0], BUILT_IN) and not p.exists():
            continue
        for lib in find_libraries(p, problems):
            had = seen.get(lib["name"])
            if had is None:
                if lib["name"] not in refused:
                    seen[lib["name"]] = lib
            elif had["version"] != lib["version"] or _identity(had) != _identity(lib):
                problems.append(f"library {lib['name']} is in {had['source']} ({had['version'] or 'no version'}) and in {lib['source']} "
                                f"({lib['version'] or 'no version'}) with different contents: refused, rename or remove one")
                del seen[lib["name"]]
                refused.add(lib["name"])
    return list(seen.values()), problems


def bundle_files(libraries: Iterable[dict]) -> dict[str, str]:
    return {f"rachana_libs/{l['name']}/{n}": t for l in libraries for n, t in l["files"].items()}
