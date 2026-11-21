# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""Flask adapter (docs/guides/CLI_AND_SDKS.md, Python): a blueprint that serves Sutras and the Data Protocol v1 resources.

    from drishti_rachana.adapters.flask import RachanaBlueprint
    rachana = RachanaBlueprint(sutras="sutras/")

    @rachana.table("sales", key=["region", "month"])
    def sales(): ...                       # list of dicts, or an Arrow / Polars / pandas table

    @rachana.live("orders")                # a generator of (key, row) changes; row None removes
    def orders(): ...

    app.register_blueprint(rachana, url_prefix="/dash")

Routes under the prefix: ``/`` (list of Sutras), ``/page/<name>`` (the page hosting ``<rachana-view>``), ``/sutras/<name>.sutra.yaml``,
``/static/...`` (the assets of ``<rachana-view>``: drishti_rachana.assets), ``/data/...`` (the Data Protocol: JSON, Arrow IPC for a table
that declares ``arrow`` when pyarrow is installed, NDJSON; ``GET`` or ``POST /data/changes`` for the change stream).
"""
from __future__ import annotations

import json
from pathlib import Path
from typing import Callable, Optional, Union

from flask import Blueprint, Response, abort, request

from ..protocol import ProtocolError
from ..server import DataHost, _STATUS
from ..assets import find as find_asset
from ._common import SUFFIX, SutraFolder, index_page, rows_of, start_async_live, view_page


class RachanaBlueprint(Blueprint):
    def __init__(self, sutras: Union[str, Path, None] = None, *, name: str = "rachana") -> None:
        super().__init__(name, __name__)
        self.host = DataHost()
        self.folder = SutraFolder(sutras)
        self.add_url_rule("/", "index", self._index)
        self.add_url_rule("/page/<name>", "page", self._page)
        self.add_url_rule(f"/sutras/<name>{SUFFIX}", "sutra", self._sutra)
        self.add_url_rule("/static/<path:rel>", "static", self._static)
        self.add_url_rule("/data", "data_root", self._data, defaults={"sub": ""}, methods=["GET"])
        self.add_url_rule("/data/<path:sub>", "data", self._data, methods=["GET", "POST"])

    def table(self, name: str, *, key: Optional[list[str]] = None, title: Optional[str] = None, schema: Optional[dict] = None) -> Callable:
        """Expose a function's rows as a table; ``schema`` is the table's declared schema document (``can: [..., arrow]`` offers Arrow)."""
        def deco(fn: Callable) -> Callable:
            self.host.add_table(name, lambda: rows_of(fn()), key, title, schema)
            return fn
        return deco

    def live(self, name: str) -> Callable:
        def deco(fn: Callable) -> Callable:
            self.host.set_live(name, start_async_live(fn))
            return fn
        return deco

    def mount(self, path: Union[str, Path]) -> None:
        self.folder.mount(path)

    def _index(self):
        return Response(index_page(sorted(self.folder.files()), request.script_root + request.path.rstrip("/")), mimetype="text/html")

    def _page(self, name: str):
        if name not in self.folder.files():
            abort(404)
        base = request.script_root + request.path[: -len("/page/" + name)]
        return Response(view_page(name, base), mimetype="text/html")

    def _static(self, rel: str):
        found = find_asset(rel)
        if found is None:
            abort(404)
        return Response(found[0], mimetype=found[1].split(";")[0], headers={"Content-Type": found[1], "Cache-Control": "max-age=300"})

    def _sutra(self, name: str):
        t = self.folder.text(name)
        if t is None:
            abort(404)
        return Response(t, mimetype="application/yaml")

    def _data(self, sub: str):
        sub = "/" + sub
        if sub == "/changes":
            try:
                stream = self.host.stream(request.args.to_dict(), request.get_json(silent=True), request.headers.get("Last-Event-ID"))
            except ProtocolError as e:
                return Response(json.dumps(e.to_json()), status=_STATUS.get(e.code, 500), mimetype="application/json")
            return Response(stream, mimetype="text/event-stream", headers={"Cache-Control": "no-store", "X-Accel-Buffering": "no"})
        r = self.host.handle(request.method, sub, request.args.to_dict(), request.get_data(), request.headers.get("Accept", "application/json"))
        return Response(r.body, status=r.status, mimetype=r.content_type)
