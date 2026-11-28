# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""What the Flask and FastAPI adapters share: the Sutra folder, the page that hosts ``<rachana-view>``, table registration."""
from __future__ import annotations

import html
import inspect
import json
import threading
from pathlib import Path
from typing import Any, Callable, Iterator, Optional, Union

from ..assets import ELEMENTS_VERSION, RUNTIME_MISSING, runtime_present
from ..server import DataHost

SUFFIX = ".sutra.yaml"
STATIC = "/static"       # under the adapter's mount: the assets of <rachana-view> (drishti_rachana.assets)


def rows_of(value: Any) -> list[dict]:
    """A table function's result as a list of dicts: a list of dicts, an Arrow table, a Polars or pandas frame."""
    if hasattr(value, "to_pylist"):
        return value.to_pylist()
    if hasattr(value, "to_dicts"):
        return value.to_dicts()
    if hasattr(value, "to_dict") and hasattr(value, "columns"):
        return value.to_dict("records")
    return list(value)


class SutraFolder:
    def __init__(self, root: Union[str, Path, None]) -> None:
        self.roots: list[Path] = [Path(root)] if root else []

    def mount(self, path: Union[str, Path]) -> None:
        self.roots.append(Path(path))

    def files(self) -> dict[str, Path]:
        out: dict[str, Path] = {}
        for r in self.roots:
            for f in ([r] if r.is_file() else sorted(r.rglob("*" + SUFFIX))):
                out.setdefault(f.name[: -len(SUFFIX)], f)
        return out

    def text(self, name: str) -> Optional[str]:
        f = self.files().get(name)
        return f.read_text(encoding="utf-8") if f else None


def index_page(names: list[str], base: str) -> str:
    items = "".join(f'<li><a href="{base}/page/{html.escape(n)}">{html.escape(n)}</a></li>' for n in names)
    return f"<!doctype html><meta charset=utf-8><title>Sutras</title><h1>Sutras</h1><ul>{items}</ul>"


def view_page(name: str, base: str) -> str:
    """The page of one Sutra: Drishti Elements, then ``<rachana-view>`` over the Sutra file and the Data Protocol under ``base``, with
    its assets served by the adapter at ``base + STATIC`` (drishti_rachana.assets)."""
    n, b = html.escape(name), html.escape(base)
    if not runtime_present():
        return f"<!doctype html><meta charset=utf-8><title>{n}</title><h1>Rachana runtime bundle missing</h1><p>{html.escape(RUNTIME_MISSING)}</p>"
    el = f"{b}{STATIC}/vendor/drishti-elements/{ELEMENTS_VERSION}"
    return ("<!doctype html><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'>"
            f"<title>{n}</title><link rel=stylesheet href='{el}/drishti-elements.css'><script src='{el}/drishti-elements.js'></script>"
            f"<script type=module>import '{b}{STATIC}/rachana/src/rachana_view.js';</script>"
            f"<rachana-view src='{b}/sutras/{n}{SUFFIX}' data='{b}/data' runtime='{b}{STATIC}/rachana/rachana.runtime.js' "
            "live></rachana-view>")


def start_async_live(fn: Callable[[], Any]) -> Callable[[], Iterator]:
    """Wrap a (sync or async) generator function as the sync iterator factory the host wants."""
    if not inspect.isasyncgenfunction(fn):
        return fn

    def run() -> Iterator:
        import asyncio
        import queue
        q: queue.Queue = queue.Queue()
        done = object()

        def pump() -> None:
            async def go() -> None:
                async for item in fn():
                    q.put(item)
            asyncio.run(go())
            q.put(done)
        threading.Thread(target=pump, daemon=True).start()
        while True:
            item = q.get()
            if item is done:
                return
            yield item
    return run


def stop_message(e: Exception) -> str:
    return json.dumps({"error": str(e)})
