# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""Flask adapter: STUB of the planned API (docs/MODES.md 3, "subject to change"). Real work is plan B5.

    from rachana.adapters.flask import RachanaBlueprint
    rachana = RachanaBlueprint(sutras="sutras/")

    @rachana.table("sales")
    def sales(): ...                       # list of dicts, or an Arrow table

    @rachana.live("orders")                # keyed patches over SSE (DATA_PROTOCOL 6)
    def orders(): ...

    app.register_blueprint(rachana, url_prefix="/dash")

TODO(B5): serve the Data Protocol v1 resources and the change stream; the page that hosts ``<rachana-view>``.
The import name will move to ``rachana.flask`` only if it can be done without shadowing the ``flask`` package.
"""
from __future__ import annotations

from pathlib import Path
from typing import Callable, Optional, Union


class RachanaBlueprint:
    def __init__(self, sutras: Union[str, Path, None] = None, *, name: str = "rachana") -> None:
        raise NotImplementedError("rachana.adapters.flask is a stub of the planned API (plan B5)")

    def table(self, name: str, *, key: Optional[list[str]] = None) -> Callable:
        """Decorator: expose ``name`` as a Data Protocol table."""
        raise NotImplementedError

    def live(self, name: str) -> Callable:
        """Decorator: the function yields ``(key, row)`` changes for table ``name``."""
        raise NotImplementedError

    def mount(self, path: Union[str, Path]) -> None:
        """Mount a Sutra file or folder."""
        raise NotImplementedError
