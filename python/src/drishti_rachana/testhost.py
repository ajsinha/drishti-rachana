# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""The Python host under test: a :class:`DataHost` over the standard library's HTTP server with the test adapter of PROTOCOL 15
(``POST /_test/fixture``, ``/_test/patch``, ``/_test/bridge``), the one set of ``protocol`` cases that also checks the JVM reference
server runs against it:

    python tools/conformance/protocol_check.py --python
    python -m drishti_rachana.testhost [--port 0] [--base /rdp/v1]     # prints ``rdp-python-server listening on <url>``

Loopback only; not a production server (use the Flask or FastAPI adapter for that)."""
from __future__ import annotations

import argparse
import json
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qsl, urlsplit

from .protocol import ProtocolError
from .server import DataHost, _STATUS


class TestHost:
    __test__ = False   # not a pytest class

    def __init__(self, port: int = 0, base: str = "/rdp/v1") -> None:
        self.base = "" if base in ("", "/") else base.rstrip("/")
        self.host = DataHost(self.base)
        outer = self

        class Handler(BaseHTTPRequestHandler):
            protocol_version = "HTTP/1.1"

            def log_message(self, *a):  # noqa: D401
                pass

            def _send(self, status: int, body: bytes = b"", ctype: str = "application/json") -> None:
                self.send_response(status)
                self.send_header("Content-Type", ctype)
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def _serve(self) -> None:
                u = urlsplit(self.path)
                n = int(self.headers.get("Content-Length") or 0)
                body = self.rfile.read(n) if n else b""
                if not u.path.startswith(outer.base):
                    return self._send(404)
                rel = u.path[len(outer.base):] or "/"
                q = dict(parse_qsl(u.query))
                try:
                    if rel.startswith("/_test/"):
                        return self._test(rel, body)
                    if rel == "/changes":
                        return self._stream(q)
                    r = outer.host.handle(self.command, rel, q, body, self.headers.get("Accept") or "application/json")
                    self._send(r.status, r.body, r.content_type)
                except ProtocolError as e:
                    self._send(_STATUS.get(e.code, 500), json.dumps(e.to_json()).encode())

            def _test(self, rel: str, body: bytes) -> None:
                doc = json.loads(body or b"{}")
                if rel == "/_test/fixture":
                    outer.host.tables.clear()
                    s = doc.get("schema") or {}
                    outer.host.add_table(s.get("name") or doc.get("name"), list(doc.get("rows") or []), s.get("key"), s.get("title"), schema=s, epoch="t1")
                    return self._send(204)
                if rel == "/_test/patch":
                    outer.host.publish(doc["table"], doc)
                    return self._send(204)
                if rel == "/_test/bridge":
                    return self._send(200, json.dumps(outer.bridge(doc)).encode())
                self._send(404, json.dumps(ProtocolError("rdp.table.unknown", "no test path " + rel).to_json()).encode())

            def _stream(self, q: dict) -> None:
                try:
                    frames = outer.host.changes(q.get("table", ""), q.get("after") or self.headers.get("Last-Event-ID"), heartbeat=15.0,
                                                stop=outer.stop)
                except ProtocolError as e:
                    return self._send(_STATUS.get(e.code, 500), json.dumps(e.to_json()).encode())
                self.send_response(200)
                self.send_header("Content-Type", "text/event-stream")
                self.send_header("Cache-Control", "no-store")
                self.send_header("Transfer-Encoding", "chunked")
                self.end_headers()
                try:
                    for f in frames:
                        b = f.encode()
                        self.wfile.write(b"%x\r\n" % len(b) + b + b"\r\n")
                        self.wfile.flush()
                except (BrokenPipeError, ConnectionResetError):
                    pass
                self.close_connection = True

            do_GET = do_POST = _serve

        self.stop = threading.Event()
        self.server = ThreadingHTTPServer(("127.0.0.1", port), Handler)
        self.server.daemon_threads = True

    def bridge(self, m: dict) -> dict:
        """One view to host bridge message (PROTOCOL 12) answered with the host to view message it produces."""
        t, out = m.get("t"), {"rdp": 1}
        if t == "hello":
            return {**out, "t": "welcome", "v": 1, "limits": {"pageRows": 10000}}
        if t == "ping":
            return {**out, "t": "pong", "ts": m.get("ts")}
        out.update(t="res", re=m.get("id"))
        name = m.get("table")
        body = m.get("body") or {}
        if t == "tables":
            r = self.host.handle("GET", "/tables")
        elif t == "schema":
            r = self.host.handle("GET", f"/tables/{name}")
        elif t == "rows":
            r = self.host.handle("GET", f"/tables/{name}/rows", {k: ",".join(map(str, v)) if isinstance(v, list) else str(v) for k, v in body.items()})
        elif t == "query":
            r = self.host.handle("POST", f"/tables/{name}/query", None, json.dumps(body).encode())
        else:
            out["error"] = ProtocolError("rdp.query.unsupported", f"no bridge message '{t}' here", operation=t).to_json()["error"]
            return out
        doc = json.loads(r.body)
        out.update(body=doc) if r.status == 200 else out.update(error=doc.get("error"))
        return out

    @property
    def url(self) -> str:
        return f"http://127.0.0.1:{self.server.server_address[1]}{self.base}"

    def serve_forever(self) -> None:
        self.server.serve_forever()

    def close(self) -> None:
        self.stop.set()
        self.server.shutdown()
        self.server.server_close()


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(prog="python -m drishti_rachana.testhost")
    ap.add_argument("--port", type=int, default=0)
    ap.add_argument("--base", default="/rdp/v1")
    a = ap.parse_args(argv)
    h = TestHost(a.port, a.base)
    print(f"rdp-python-server listening on {h.url}", flush=True)
    try:
        h.serve_forever()
    except KeyboardInterrupt:
        h.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
