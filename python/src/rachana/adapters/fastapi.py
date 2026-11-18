# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""FastAPI adapter: STUB of the planned API (docs/MODES.md 3, "subject to change"). Real work is plan B5.

    from rachana.adapters.fastapi import RachanaRouter
    rachana = RachanaRouter(sutras="sutras/")

    @rachana.table("sales")
    async def sales(): ...

    @rachana.live("sales")                 # MODES.md spells this ``changes``: TODO(B5) settle one name
    async def sales_changes():
        async for key, row in watch_sales():
            yield key, row

    app.include_router(rachana, prefix="/dash")

TODO(B5): serve the Data Protocol v1 resources and the change stream (SSE and WebSocket).
"""
from __future__ import annotations

from pathlib import Path
from typing import Callable, Optional, Union


class RachanaRouter:
    def __init__(self, sutras: Union[str, Path, None] = None) -> None:
        raise NotImplementedError("rachana.adapters.fastapi is a stub of the planned API (plan B5)")

    def table(self, name: str, *, key: Optional[list[str]] = None) -> Callable:
        """Decorator: expose ``name`` as a Data Protocol table."""
        raise NotImplementedError

    def live(self, name: str) -> Callable:
        """Decorator: the async generator yields ``(key, row)`` changes for table ``name``."""
        raise NotImplementedError

    def mount(self, path: Union[str, Path]) -> None:
        """Mount a Sutra file or folder."""
        raise NotImplementedError
