# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""FastAPI adapter (docs/MODES.md 3): a router that serves Sutras and the Data Protocol v1 resources.

    from drishti_rachana.adapters.fastapi import RachanaRouter
    rachana = RachanaRouter(sutras="sutras/")

    @rachana.table("sales", key=["region", "month"])
    async def sales(): ...                 # list of dicts, or an Arrow / Polars / pandas table (sync or async function)

    @rachana.live("orders")                # a sync or async generator of (key, row) changes; row None removes
    async def orders(): ...

    app.include_router(rachana, prefix="/dash")

Same routes as the Flask adapter: ``/``, ``/page/{name}``, ``/sutras/{name}.sutra.yaml``, ``/data/...``.
"""
from __future__ import annotations

import asyncio
import inspect
from pathlib import Path
from typing import Callable, Optional, Union

from fastapi import APIRouter, HTTPException, Request
from fastapi.responses import HTMLResponse, Response, StreamingResponse

from ..protocol import ProtocolError
from ..server import DataHost
from ._common import DEFAULT_VIEW_SCRIPT, SUFFIX, SutraFolder, index_page, rows_of, start_async_live, view_page


class RachanaRouter(APIRouter):
    def __init__(self, sutras: Union[str, Path, None] = None, *, view_script: str = DEFAULT_VIEW_SCRIPT) -> None:
        super().__init__()
        self.host = DataHost()
        self.folder = SutraFolder(sutras)
        self._script = view_script
        self.add_api_route("/", self._index, methods=["GET"], response_class=HTMLResponse)
        self.add_api_route("/page/{name}", self._page, methods=["GET"], response_class=HTMLResponse)
        self.add_api_route("/sutras/{name}" + SUFFIX, self._sutra, methods=["GET"])
        self.add_api_route("/data", self._data, methods=["GET"])
        self.add_api_route("/data/{sub:path}", self._data, methods=["GET", "POST"])

    def table(self, name: str, *, key: Optional[list[str]] = None, title: Optional[str] = None) -> Callable:
        def deco(fn: Callable) -> Callable:
            if inspect.iscoroutinefunction(fn):
                self.host.add_table(name, lambda: rows_of(asyncio.run(fn())), key, title)
            else:
                self.host.add_table(name, lambda: rows_of(fn()), key, title)
            return fn
        return deco

    def live(self, name: str) -> Callable:
        def deco(fn: Callable) -> Callable:
            self.host.set_live(name, start_async_live(fn))
            return fn
        return deco

    def mount(self, path: Union[str, Path]) -> None:
        self.folder.mount(path)

    def _index(self, request: Request):
        return index_page(sorted(self.folder.files()), request.scope.get("root_path", "") + request.url.path.rstrip("/"))

    def _page(self, name: str, request: Request):
        if name not in self.folder.files():
            raise HTTPException(404)
        base = request.scope.get("root_path", "") + request.url.path[: -len("/page/" + name)]
        return view_page(name, base, self._script)

    def _sutra(self, name: str):
        t = self.folder.text(name)
        if t is None:
            raise HTTPException(404)
        return Response(t, media_type="application/yaml")

    async def _data(self, request: Request, sub: str = ""):
        sub = "/" + sub
        q = dict(request.query_params)
        if sub == "/changes":
            try:
                stream = self.host.changes(q.get("table", ""), q.get("after"))
            except ProtocolError as e:
                return Response(__import__("json").dumps(e.to_json()), status_code=404 if e.code == "rdp.table.unknown" else 422,
                                media_type="application/json")

            async def frames():
                loop = asyncio.get_running_loop()
                it = iter(stream)
                while True:
                    frame = await loop.run_in_executor(None, next, it, None)
                    if frame is None:
                        return
                    yield frame
            return StreamingResponse(frames(), media_type="text/event-stream", headers={"Cache-Control": "no-store"})
        body = await request.body()
        loop = asyncio.get_running_loop()
        r = await loop.run_in_executor(None, lambda: self.host.handle(request.method, sub, q, body, request.headers.get("accept", "application/json")))
        return Response(r.body, status_code=r.status, media_type=r.content_type)
