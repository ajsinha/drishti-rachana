# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""The browser assets of ``<rachana-view>``, as the Flask and FastAPI adapters serve them under their mount (``<mount>/static/...``):

=========================================  ==========================================================================================
URL under ``static/``                      source
=========================================  ==========================================================================================
``rachana/src/*.js``                       the element, the data client, the screen (js/packages/rachana/src)
``rachana/rachana.runtime.js``             the compiler/runtime bundle (built by tools/js/build_js.py into ``bundle/``)
``vendor/drishti-elements/3.3.0/**``       the vendored Drishti Elements pack (painter, charts, stylesheet)
``vendor/apache-arrow/21.1.0/*.js``        Apache Arrow JS (a page that asks for ``form="arrow"``)
=========================================  ==========================================================================================

In a wheel the files are package data (``drishti_rachana/web`` and ``bundle``); in a checkout they are read from ``js/``.
Nothing else is served: a path is looked up in this table, never joined to a folder the caller names."""
from __future__ import annotations

from pathlib import Path
from typing import Optional

ELEMENTS_VERSION = "3.3.0"
ARROW_VERSION = "21.1.0"
PACKAGE = Path(__file__).resolve().parent
CHECKOUT = PACKAGE.parents[2]                # python/src/drishti_rachana -> the repository root (a source checkout only)

TYPES = {".js": "text/javascript; charset=utf-8", ".mjs": "text/javascript; charset=utf-8", ".css": "text/css; charset=utf-8",
         ".json": "application/json", ".svg": "image/svg+xml", ".woff2": "font/woff2", ".woff": "font/woff", ".ttf": "font/ttf",
         ".png": "image/png", ".map": "application/json", ".txt": "text/plain; charset=utf-8", ".html": "text/html; charset=utf-8"}
STUB_CSS = "/* the rules of this sheet are in the pack's drishti-elements.css */\n"


def _roots() -> dict[str, list[Path]]:
    js = CHECKOUT / "js"
    return {
        "rachana/src": [PACKAGE / "web" / "rachana" / "src", js / "packages" / "rachana" / "src"],
        f"vendor/drishti-elements/{ELEMENTS_VERSION}": [PACKAGE / "web" / "vendor" / "drishti-elements" / ELEMENTS_VERSION,
                                                         js / "vendor" / "drishti-elements" / ELEMENTS_VERSION],
        f"vendor/apache-arrow/{ARROW_VERSION}": [PACKAGE / "web" / "vendor" / "apache-arrow" / ARROW_VERSION,
                                                  js / "vendor" / "apache-arrow" / ARROW_VERSION],
    }


def _runtime() -> list[Path]:
    return [PACKAGE / "bundle" / "rachana.runtime.js", CHECKOUT / "js" / "packages" / "rachana" / "dist" / "rachana.runtime.js"]


def find(rel: str) -> Optional[tuple[bytes, str]]:
    """The bytes and media type of the asset at ``rel`` (below ``static/``), or None. A stylesheet of the Elements pack that the
    pack carries inside ``drishti-elements.css`` (its loader asks for each by address) is an empty sheet, so no request fails."""
    parts = [p for p in rel.split("/") if p]
    if not parts or any(p in (".", "..") or "\\" in p or "\0" in p for p in parts):
        return None
    rel = "/".join(parts)
    if rel == "rachana/rachana.runtime.js":
        for f in _runtime():
            if f.is_file():
                return f.read_bytes(), TYPES[".js"]
        return None
    for prefix, dirs in _roots().items():
        if rel.startswith(prefix + "/"):
            tail = rel[len(prefix) + 1:]
            for d in dirs:
                f = (d / tail).resolve()
                if f.is_file() and d.resolve() in f.parents:
                    return f.read_bytes(), TYPES.get(f.suffix.lower(), "application/octet-stream")
            if prefix.startswith("vendor/drishti-elements") and tail.endswith(".css"):
                return STUB_CSS.encode(), TYPES[".css"]
    return None
