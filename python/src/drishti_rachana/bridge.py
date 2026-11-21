# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""The in-process bridge of the Data Protocol (PROTOCOL 12) over a :class:`DataHost`: the Python twin of java/rachana-data's
``MemoryHost.Bridge``. One view to host message in, exactly one answer out; ``sub`` and ``unsub`` as well, with the ``msg`` frames of
the subscription pushed to the ``push`` callback (host to view). ``rows`` and ``query`` with ``accept: arrow`` answer ``res.arrow``
(the Arrow IPC bytes, base64 in JSON) for a table that declares ``arrow`` and pyarrow is installed, else an error ``res``."""
from __future__ import annotations

import base64
import json
from typing import Any, Callable

from .protocol import ProtocolError
from .rows_forms import arrow_available
from .server import DataHost
from .subscription import Subscription


class Bridge:
    def __init__(self, host: DataHost, push: Callable[[dict], None]) -> None:
        self.host, self.push = host, push
        self.subs: dict[str, Subscription] = {}

    def close(self) -> None:
        for s in list(self.subs.values()):
            s.close()
        self.subs.clear()

    def handle(self, m: dict) -> dict:
        t, out = m.get("t"), {"rdp": 1}
        if t == "hello":
            return {**out, "t": "welcome", "v": 1, "limits": {"pageRows": 10000}}
        if t == "ping":
            return {**out, "t": "pong", "ts": m.get("ts")}
        out.update(t="res", re=m.get("id"))
        try:
            if t == "sub":
                return self._sub(m, out)
            if t == "unsub":
                s = self.subs.pop(str(m.get("id")), None)
                if s:
                    s.close()
                out["body"] = {}
                return out
            if t in ("tables", "schema", "rows", "query"):
                return self._read(t, m, out)
            raise ProtocolError("rdp.query.unsupported", f"no bridge message '{t}' here", operation=t)
        except ProtocolError as e:
            out["error"] = e.to_json()["error"]
            return out

    def _sub(self, m: dict, out: dict) -> dict:
        sid, name = str(m.get("id")), str(m.get("table"))
        t = self.host._table(name)
        a = m.get("after")
        after = f"{a.get('epoch')}:{a.get('seq')}" if isinstance(a, dict) else f"{t.epoch}:{t.seq}"
        every = m["conflate"].get("everyMs") if isinstance(m.get("conflate"), dict) else 0
        old = self.subs.pop(sid, None)
        if old:
            old.close()

        def sink(f: dict) -> None:
            msg = {"rdp": 1, "t": "msg", "sub": sid, "table": name, "epoch": f["epoch"], "seq": f["seq"]}
            if f.get("first") is not None:
                msg["first"] = f["first"]
            msg["data"] = f["data"]
            self.push(msg)

        sub = self.host.subscribe(name, after, m.get("filter"), int(every or 0), sink)
        if sub.recovered:
            self.subs[sid] = sub
        with t.lock:
            out["body"] = {"recovered": sub.recovered, "version": {"epoch": t.epoch, "seq": t.seq}}
        return out

    def _read(self, t: str, m: dict, out: dict) -> dict:
        name, body = m.get("table"), m.get("body") or {}
        if t == "tables":
            r = self.host.handle("GET", "/tables")
        elif t == "schema":
            r = self.host.handle("GET", f"/tables/{name}")
        elif t == "rows":
            r = self.host.handle("GET", f"/tables/{name}/rows", {k: ",".join(map(str, v)) if isinstance(v, list) else str(v) for k, v in body.items()})
        else:
            r = self.host.handle("POST", f"/tables/{name}/query", None, json.dumps(body).encode())
        doc: Any = json.loads(r.body)
        if r.status != 200:
            out["error"] = doc.get("error")
        elif "arrow" in str(m.get("accept")) and t in ("rows", "query"):
            tab = self.host._table(str(name))
            if arrow_available() and "arrow" in tab.schema().get("can", []):
                out["t"] = "res.arrow"
                out["buffer"] = base64.b64encode(self.host.encode_page(tab, doc, "arrow")[0]).decode()
            else:
                out["error"] = ProtocolError("rdp.query.unsupported", f"{name} is not offered as Arrow", table=name, operation="arrow").to_json()["error"]
        else:
            out["body"] = doc
        return out
