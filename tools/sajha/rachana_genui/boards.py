# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""Boards and the board server: publish_ui stores a checked Sutra with its tables as a **board** (an unguessable id, versions, an
expiry) and the board server pushes every version to the board's page over Server-Sent Events.

Routes (standard library HTTP server, one daemon thread, started on the first publish):

    GET /b/<id>                 the SPA (spa/index.html) with a strict Content-Security-Policy
    GET /b/<id>/events          SSE: the board now, then each new version ({type: board, version, sutra, tables, libraries, ...})
    GET /b/<id>/board.json      the board now (a page that cannot hold a stream)
    GET /spa/<file>             the SPA's own script and style
    GET /static/<path>          the assets of <rachana-view> from the drishti-rachana wheel (drishti_rachana.assets): no CDN
    GET /libraries/<name>.json  the files of one library, as <rachana-view>'s ``files`` property takes them
    GET /healthz

Nothing is served from a path the caller builds: every file is looked up in a fixed table or through the wheel's asset table.
"""
from __future__ import annotations

import json
import queue
import secrets
import threading
import time
from dataclasses import dataclass, field
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any, Optional
from urllib.parse import urlparse

from . import audit
from .settings import settings

SPA = Path(__file__).resolve().parents[1] / "spa"
SPA_FILES = {"app.js": "text/javascript; charset=utf-8", "app.css": "text/css; charset=utf-8"}


def csp(data_origins: list[str]) -> str:
    """The page's policy: scripts and styles only from this server; inline style attributes for the painter's charts (no inline
    script, no eval); data only from this server and the allowed Data Protocol hosts; no frames, forms, objects or base."""
    connect = " ".join(["'self'", *data_origins])
    return ("default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; "
            f"font-src 'self' data:; connect-src {connect}; worker-src 'self' blob:; base-uri 'none'; form-action 'none'; "
            "frame-ancestors 'self'; object-src 'none'")


@dataclass
class Board:
    id: str
    owner: str
    title: str
    sutra: str
    tables: dict
    protocol: Optional[str]
    libraries: list
    version: int = 1
    created: float = field(default_factory=time.time)
    updated: float = field(default_factory=time.time)
    subscribers: list = field(default_factory=list)

    def message(self) -> dict:
        return {"type": "board", "id": self.id, "version": self.version, "title": self.title, "sutra": self.sutra,
                "protocol": self.protocol, "libraries": self.libraries,
                "tables": {n: ({"rows": t["rows"]} if "rows" in t else {"table": t["table"]}) for n, t in self.tables.items()},
                "updated": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(self.updated))}


class BoardStore:
    def __init__(self):
        self.boards: dict[str, Board] = {}
        self.lock = threading.Lock()

    def _expire(self) -> None:
        ttl = settings().board_ttl_minutes * 60
        now = time.time()
        for k in [k for k, b in self.boards.items() if now - b.updated > ttl]:
            self.boards.pop(k, None)

    def publish(self, sutra: str, tables: dict, protocol: Optional[str], libraries: list, title: str,
                board: Optional[str] = None) -> Board:
        owner = audit.caller()
        with self.lock:
            self._expire()
            if board:
                b = self.boards.get(board)
                if b is None:
                    raise KeyError(f"no board '{board}' (it may have expired): publish without board to start a new one")
                if b.owner != owner:
                    raise PermissionError(f"board '{board}' belongs to another user")
                b.sutra, b.tables, b.protocol, b.libraries, b.title = sutra, tables, protocol, libraries, title or b.title
                b.version += 1
                b.updated = time.time()
            else:
                if len(self.boards) >= settings().max_boards:
                    oldest = min(self.boards.values(), key=lambda x: x.updated)
                    self.boards.pop(oldest.id, None)
                b = Board(secrets.token_urlsafe(16), owner, title, sutra, tables, protocol, libraries)
                self.boards[b.id] = b
            msg = b.message()
            for q in list(b.subscribers):
                q.put(msg)
            return b

    def get(self, board_id: str) -> Optional[Board]:
        with self.lock:
            self._expire()
            return self.boards.get(board_id)


STORE = BoardStore()


class _Handler(BaseHTTPRequestHandler):
    server_version = "rachana-genui"
    catalogue_loader = None

    def log_message(self, fmt: str, *args: Any) -> None:  # quiet: the audit records what matters
        pass

    def _send(self, status: int, body: bytes, ctype: str, extra: Optional[dict] = None) -> None:
        self.send_response(status)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Referrer-Policy", "no-referrer")
        self.send_header("Cache-Control", "no-store")
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(body)

    def _json(self, status: int, doc: Any) -> None:
        self._send(status, json.dumps(doc).encode(), "application/json")

    def do_GET(self) -> None:  # noqa: N802 - the server's name
        parts = [p for p in urlparse(self.path).path.split("/") if p]
        if parts == ["healthz"]:
            return self._json(200, {"ok": True})
        if len(parts) == 2 and parts[0] == "spa" and parts[1] in SPA_FILES:
            return self._send(200, (SPA / parts[1]).read_bytes(), SPA_FILES[parts[1]])
        if parts and parts[0] == "static":
            from drishti_rachana.assets import find
            got = find("/".join(parts[1:]))
            return self._send(200, got[0], got[1], {"Cache-Control": "max-age=3600"}) if got else self._json(404, {"error": "no such asset"})
        if len(parts) == 2 and parts[0] == "libraries" and parts[1].endswith(".json"):
            cat = type(self).catalogue_loader() if type(self).catalogue_loader else None
            name = parts[1][:-5]
            if cat is None or name not in cat.libraries:
                return self._json(404, {"error": "no such library"})
            return self._json(200, cat.library_files([name]))
        if len(parts) >= 2 and parts[0] == "b":
            b = STORE.get(parts[1])
            if b is None:
                return self._json(404, {"error": "no such board (it may have expired)"})
            if len(parts) == 2:
                body = (SPA / "index.html").read_bytes()
                return self._send(200, body, "text/html; charset=utf-8", {"Content-Security-Policy": csp(settings().data_origins)})
            if parts[2:] == ["board.json"]:
                return self._json(200, b.message())
            if parts[2:] == ["events"]:
                return self._events(b)
        return self._json(404, {"error": "not found"})

    def _events(self, b: Board) -> None:
        q: queue.Queue = queue.Queue()
        with STORE.lock:
            b.subscribers.append(q)
            q.put(b.message())
        audit.event("view", board=b.id, version=b.version)
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        try:
            while True:
                try:
                    msg = q.get(timeout=15)
                    self.wfile.write(f"event: board\nid: {msg['version']}\ndata: {json.dumps(msg)}\n\n".encode())
                except queue.Empty:
                    self.wfile.write(b": keep-alive\n\n")
                self.wfile.flush()
        except (BrokenPipeError, ConnectionResetError, OSError):
            pass
        finally:
            with STORE.lock:
                if q in b.subscribers:
                    b.subscribers.remove(q)


_server: Optional[ThreadingHTTPServer] = None
_slock = threading.Lock()


def ensure_server(catalogue_loader) -> str:
    """Start the board server once (a daemon thread); returns the address readers use."""
    global _server
    s = settings()
    with _slock:
        if _server is None:
            _Handler.catalogue_loader = staticmethod(catalogue_loader)
            _server = ThreadingHTTPServer((s.host, s.port), _Handler)
            _server.daemon_threads = True
            threading.Thread(target=_server.serve_forever, name="rachana-genui-boards", daemon=True).start()
            if s.port == 0:
                s.port = _server.server_address[1]
                s.public_url = f"http://{s.host}:{s.port}"
    return s.public_url


def stop_server() -> None:
    global _server
    with _slock:
        if _server is not None:
            _server.shutdown()
            _server.server_close()
            _server = None
