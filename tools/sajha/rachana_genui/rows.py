# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""Where a board's rows come from: the data path, which never passes through the model (GD4). A table source is one of

* ``{"tool": "<SAJHA tool>", "arguments": {...}, "path": "rows"}``: the kit calls that tool itself, **as the caller** (SAJHA's inner
  calls: the caller's rights, policy and audit apply; cycles and depth are refused), and takes the rows at ``path``. The tool must be
  on the kit's allow-list (``RACHANA_GENUI_DATA_TOOLS``);
* ``{"protocol": "<base address>", "table": "<name>"}``: the board's page reads the table itself from a Data Protocol host (Drishti),
  whose origin must be on ``RACHANA_GENUI_DATA_ORIGINS``; the rows reach the browser only;
* ``{"rows": [...]}``: rows the agent wrote itself, at most ``max_inline_rows`` (constants: targets, labels). Never a tool's data.

Outside SAJHA (tests, the example, a plain application) a tool is a Python function registered with :func:`register_tool`.
"""
from __future__ import annotations

from typing import Any, Callable, Optional
from urllib.parse import urlparse

from .settings import settings

_local_tools: dict[str, Callable[[dict], Any]] = {}


class SourceError(ValueError):
    def __init__(self, code: str, message: str, table: str = ""):
        super().__init__(message)
        self.code, self.table = code, table


def register_tool(name: str, fn: Callable[[dict], Any]) -> None:
    """A data tool for the kit outside SAJHA: ``fn(arguments) -> result`` (a dict or a list of rows)."""
    _local_tools[name] = fn


def unregister_tool(name: str) -> None:
    _local_tools.pop(name, None)


def _at(value: Any, path: str) -> Any:
    for key in [k for k in (path or "").replace("$.", "").split(".") if k and k != "$"]:
        if isinstance(value, dict):
            value = value.get(key)
        elif isinstance(value, list) and key.isdigit():
            value = value[int(key)]
        else:
            return None
    return value


def _call_sajha(name: str, arguments: dict) -> Any:
    """One inner call as the caller, through SAJHA's own machinery (registry, inner_calls, composition.execute_step)."""
    from sajha.core import inner_calls
    from sajha.core.composition import execute_step
    from sajha.tools.tools_registry import get_tools_registry

    tool = get_tools_registry().get_tool(name)
    if tool is None:
        raise SourceError("RGK-160", f"no tool '{name}' in SAJHA")
    with inner_calls.entered(name):
        inner_calls.check(name)
        step = execute_step(tool, arguments, name)
    if step.error:
        raise SourceError("RGK-161", f"tool '{name}' failed: {step.error}")
    return step.value


def call_tool(name: str, arguments: dict) -> Any:
    if name in _local_tools:
        return _local_tools[name](arguments)
    try:
        import sajha  # noqa: F401
    except ImportError:
        raise SourceError("RGK-160", f"no data tool '{name}' (outside SAJHA, register one with rows.register_tool)")
    return _call_sajha(name, arguments)


def resolve(name: str, src: dict) -> dict:
    """One table of a board: ``{"rows": [...], "source": {...}}`` or ``{"protocol": base, "table": t, "source": {...}}``."""
    s = settings()
    if not isinstance(src, dict):
        raise SourceError("RGK-110", f"table '{name}': a source is a mapping (tool, protocol or rows)", name)
    kinds = [k for k in ("tool", "protocol", "rows") if k in src]
    if len(kinds) != 1:
        raise SourceError("RGK-110", f"table '{name}': give exactly one of tool, protocol or rows", name)
    if "tool" in src:
        tool = str(src["tool"])
        if tool not in s.data_tools:
            raise SourceError("RGK-162", f"table '{name}': tool '{tool}' is not on the kit's data-tool allow-list", name)
        value = call_tool(tool, dict(src.get("arguments") or {}))
        rows = _at(value, src.get("path", "")) if src.get("path") else value
        if isinstance(rows, dict) and isinstance(rows.get("rows"), list):
            rows = rows["rows"]
        if not isinstance(rows, list) or not all(isinstance(r, dict) for r in rows):
            raise SourceError("RGK-163", f"table '{name}': tool '{tool}' gave no list of rows at '{src.get('path', '')}'", name)
        if len(rows) > s.max_rows:
            raise SourceError("RGK-164", f"table '{name}': {len(rows)} rows; at most {s.max_rows} on a board (filter or "
                              "aggregate in the tool, or use the Data Protocol)", name)
        return {"rows": rows, "source": {"tool": tool, "arguments": src.get("arguments") or {}}}
    if "protocol" in src:
        base = str(src["protocol"])
        origin = "{0.scheme}://{0.netloc}".format(urlparse(base))
        if origin not in s.data_origins:
            raise SourceError("RGK-165", f"table '{name}': {origin} is not on the kit's data-origin allow-list", name)
        return {"protocol": base, "table": str(src.get("table") or name), "source": {"protocol": base}}
    rows = src["rows"]
    if not isinstance(rows, list) or len(rows) > s.max_inline_rows:
        raise SourceError("RGK-110", f"table '{name}': at most {s.max_inline_rows} rows written by the agent; data from a tool "
                          "is published with {tool: ...}", name)
    return {"rows": rows, "source": {"inline": len(rows)}}


def resolve_all(tables: dict[str, dict]) -> tuple[dict[str, dict], Optional[str]]:
    """Every table; at most one Data Protocol base per board (the page's <rachana-view> reads one)."""
    s = settings()
    if len(tables) > s.max_tables:
        raise SourceError("RGK-166", f"{len(tables)} tables; at most {s.max_tables} on a board")
    out = {n: resolve(n, src) for n, src in tables.items()}
    bases = {t["protocol"] for t in out.values() if "protocol" in t}
    if len(bases) > 1 or (bases and any("rows" in t for t in out.values())):
        raise SourceError("RGK-167", "a board reads its tables from one place: all pushed by the kit, or all from one Data "
                          "Protocol host")
    return out, (bases.pop() if bases else None)
