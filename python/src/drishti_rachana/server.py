# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""A minimal Data Protocol v1 host in plain Python (docs/design/DATA_PROTOCOL.md 3 to 7), framework neutral.

The Flask and FastAPI adapters are thin: they hand ``(method, path, query, body)`` to :meth:`DataHost.handle` and stream
:meth:`DataHost.changes`. Implemented: the discovery document, table list, schema, rows (JSON; columnar or objects shape;
``limit``, ``offset``/``cursor``, ``sort``, ``columns``), ``POST query`` (filter tree, sort, columns, limit, cursor), keyed
patches with a numbered log and the minimal SSE change stream. Not implemented (answered 406 or ``rdp.query.unsupported``):
Arrow IPC, NDJSON, grouping push-down, filtered subscriptions. The JVM reference host (java/rachana-data) is the full one."""
from __future__ import annotations

import json
import threading
import uuid
import zlib
from dataclasses import dataclass
from typing import Any, Callable, Iterable, Iterator, Optional

from .protocol import Column, ProtocolError, TableSchema, Version

PAGE_ROWS = 10000
_STATUS = {"rdp.auth.required": 401, "rdp.forbidden": 403, "rdp.table.unknown": 404, "rdp.column.unknown": 422,
           "rdp.query.invalid": 422, "rdp.query.unsupported": 422, "rdp.cursor.invalid": 410, "rdp.table.no-key": 422,
           "rdp.internal": 500}


@dataclass
class Response:
    status: int
    body: bytes
    content_type: str = "application/json"


def _key_text(row: dict, key: list[str]) -> str:
    return json.dumps([str(row.get(k)) for k in key])


def _type_of(values: Iterable[Any]) -> str:
    t = "string"
    for v in values:
        if v is None:
            continue
        if isinstance(v, bool):
            return "boolean"
        if isinstance(v, int):
            t = "integer"
        elif isinstance(v, float):
            return "number"
        elif isinstance(v, (list, tuple)):
            return "list"
        elif isinstance(v, dict):
            return "object"
        else:
            return "string"
    return t


class _Table:
    def __init__(self, name: str, source: Callable[[], list[dict]] | list[dict], key: Optional[list[str]], title: Optional[str]):
        self.name, self.source, self.key, self.title = name, source, key, title
        self.epoch = uuid.uuid4().hex[:4]
        self.seq = 0
        self.log: list[tuple[int, dict]] = []
        self.live_source: Optional[Callable[[], Iterator]] = None
        self.live_started = False
        self.cond = threading.Condition()
        self.overlay: Optional[dict[str, dict]] = None      # rows after patches, by key text

    def rows(self) -> list[dict]:
        if self.overlay is not None:
            return list(self.overlay.values())
        return list(self.source() if callable(self.source) else self.source)

    def snapshot(self) -> list[dict]:
        rows = self.rows()
        if self.key and self.overlay is None:
            self.overlay = {_key_text(r, self.key): dict(r) for r in rows}
            rows = list(self.overlay.values())
        return rows

    def apply(self, patch: dict) -> None:
        """Apply a keyed patch (DATA_PROTOCOL 6.1) and append it to the log."""
        if not self.key:
            raise ProtocolError("rdp.table.no-key", f"{self.name} has no key and cannot change", table=self.name)
        self.snapshot()
        ov = self.overlay
        assert ov is not None
        if patch.get("reset") is not None:
            ov.clear()
            for r in patch["reset"]:
                ov[_key_text(r, self.key)] = dict(r)
        for k in patch.get("remove", []):
            ov.pop(json.dumps([str(x) for x in (k if isinstance(k, (list, tuple)) else [k])]), None)
        for r in patch.get("update", []) + patch.get("add", []):
            kt = _key_text(r, self.key)
            ov[kt] = {**ov.get(kt, {}), **r}
        with self.cond:
            self.seq += 1
            self.log.append((self.seq, {"table": self.name, **{k: v for k, v in patch.items() if v is not None}}))
            del self.log[:-1000]
            self.cond.notify_all()

    def schema(self) -> dict:
        rows = self.rows()
        names: list[str] = []
        for r in rows[:200]:
            for k in r:
                if k not in names:
                    names.append(k)
        cols = [Column(name=n, type=_type_of(r.get(n) for r in rows[:200])).to_json() for n in names]
        can = ["page", "sort", "filter", "offset", "count"] + (["changes"] if self.key else [])
        d: dict = {"name": self.name}
        if self.title:
            d["title"] = self.title
        if self.key:
            d["key"] = self.key
        d.update(columns=cols, rows=len(rows), can=can, version={"epoch": self.epoch, "seq": self.seq},
                 limits={"pageRows": PAGE_ROWS})
        return d


def _eval(node: dict, row: dict) -> Any:
    if "col" in node:
        v: Any = row
        for part in node["col"].split("."):
            v = v.get(part) if isinstance(v, dict) else None
        return v
    if "lit" in node:
        return node["lit"]
    if "list" in node:
        return [_eval(n, row) for n in node["list"]]
    op, a = node.get("op"), [_eval(n, row) for n in node.get("args", [])]

    def num(x: Any) -> Any:
        try:
            return float(x)
        except (TypeError, ValueError):
            return x

    def cmp(f: Callable[[Any, Any], bool]) -> Any:
        if a[0] is None or a[1] is None:
            return False
        x, y = (num(a[0]), num(a[1])) if not (isinstance(a[0], str) and isinstance(a[1], str)) else (a[0], a[1])
        try:
            return f(x, y)
        except TypeError:
            return False

    table: dict[str, Callable[[], Any]] = {
        "==": lambda: cmp(lambda x, y: x == y), "!=": lambda: not cmp(lambda x, y: x == y),
        "<": lambda: cmp(lambda x, y: x < y), "<=": lambda: cmp(lambda x, y: x <= y),
        ">": lambda: cmp(lambda x, y: x > y), ">=": lambda: cmp(lambda x, y: x >= y),
        "&&": lambda: all(bool(x) for x in a), "||": lambda: any(bool(x) for x in a), "!": lambda: not a[0],
        "in": lambda: a[0] in a[1], "not-in": lambda: a[0] not in a[1], "is-null": lambda: a[0] is None,
        "contains": lambda: a[0] is not None and str(a[1]).lower() in str(a[0]).lower(),
        "starts_with": lambda: str(a[0]).startswith(str(a[1])), "ends_with": lambda: str(a[0]).endswith(str(a[1])),
        "lower": lambda: None if a[0] is None else str(a[0]).lower(), "upper": lambda: None if a[0] is None else str(a[0]).upper(),
    }
    if op not in table:
        raise ProtocolError("rdp.query.unsupported", f"the operation '{op}' is not evaluated by this host")
    return table[op]()


def _sort_key(v: Any) -> tuple:
    return (v is None, 0, "") if v is None else ((False, 0, v) if isinstance(v, (int, float)) else (False, 1, str(v)))


class DataHost:
    """Tables by name; ``handle`` answers the Data Protocol's HTTP resources; ``publish`` feeds the change stream."""

    def __init__(self, base: str = "") -> None:
        self.base = base.rstrip("/")
        self.tables: dict[str, _Table] = {}

    def add_table(self, name: str, source: Callable[[], list[dict]] | list[dict], key: Optional[list[str]] = None,
                  title: Optional[str] = None) -> _Table:
        t = self.tables[name] = _Table(name, source, key, title)
        return t

    def publish(self, name: str, patch: dict) -> None:
        self._table(name).apply(patch)

    def set_live(self, name: str, source: Callable[[], Iterator]) -> None:
        """``source()`` returns an iterator of ``(key, row)`` changes (``row`` None removes); started on first subscriber."""
        self._table(name).live_source = source

    def _table(self, name: str) -> _Table:
        t = self.tables.get(name)
        if t is None:
            raise ProtocolError("rdp.table.unknown", f"no table '{name}'", table=name)
        return t

    # -- HTTP -------------------------------------------------------------------------------------------------------
    def handle(self, method: str, path: str, query: Optional[dict[str, str]] = None, body: Optional[bytes] = None,
               accept: str = "application/json") -> Response:
        """``path`` is relative to the protocol base (``/``, ``/tables``, ``/tables/x``, ``/tables/x/rows``, ``/tables/x/query``)."""
        query = query or {}
        try:
            if "arrow" in accept or "ndjson" in accept:
                return Response(406, json.dumps(ProtocolError("rdp.query.unsupported", "this host answers JSON only").to_json()).encode())
            parts = [p for p in path.strip("/").split("/") if p]
            if not parts and method == "GET":
                return self._json({"protocol": "rdp", "v": 1, "tables": f"{self.base}/tables", "changes": f"{self.base}/changes",
                                   "limits": {"pageRows": PAGE_ROWS}})
            if parts == ["tables"] and method == "GET":
                return self._json({"tables": [{"name": t.name, **({"title": t.title} if t.title else {}),
                                               **({"key": t.key} if t.key else {}), "can": t.schema()["can"]} for t in self.tables.values()]})
            if len(parts) >= 2 and parts[0] == "tables":
                t = self._table(parts[1])
                if len(parts) == 2 and method == "GET":
                    return self._json(t.schema())
                if parts[2:] == ["rows"] and method == "GET":
                    return self._json(self._rows(t, self._query_of(query)))
                if parts[2:] == ["query"] and method == "POST":
                    try:
                        q = json.loads(body or b"{}")
                    except ValueError as e:
                        raise ProtocolError("rdp.query.invalid", "the query is not JSON") from e
                    if q.get("group"):
                        raise ProtocolError("rdp.query.unsupported", "grouping is not evaluated by this host", table=t.name)
                    return self._json(self._rows(t, q))
            return Response(404, json.dumps(ProtocolError("rdp.table.unknown", "no such resource").to_json()).encode())
        except ProtocolError as e:
            return Response(_STATUS.get(e.code, 500), json.dumps(e.to_json()).encode())

    @staticmethod
    def _json(d: dict) -> Response:
        return Response(200, json.dumps(d, ensure_ascii=False).encode())

    @staticmethod
    def _query_of(q: dict[str, str]) -> dict:
        out: dict = {}
        if q.get("columns"):
            out["columns"] = q["columns"].split(",")
        if q.get("sort"):
            out["sort"] = [{"col": s.lstrip("-"), "dir": "desc" if s.startswith("-") else "asc"} for s in q["sort"].split(",")]
        for k in ("limit", "offset"):
            if q.get(k):
                out[k] = int(q[k])
        if q.get("cursor"):
            out["cursor"] = q["cursor"]
        if q.get("shape"):
            out["shape"] = q["shape"]
        if q.get("schema") == "true":
            out["schema"] = True
        return out

    def _rows(self, t: _Table, q: dict) -> dict:
        rows = t.snapshot()
        known = [c["name"] for c in t.schema()["columns"]]
        for c in q.get("columns") or []:
            if c not in known:
                raise ProtocolError("rdp.column.unknown", f"{t.name} has no column '{c}'", table=t.name, column=c, expected=known)
        if q.get("filter"):
            rows = [r for r in rows if _eval(q["filter"], r)]
        for s in reversed(q.get("sort") or []):
            if s["col"] not in known:
                raise ProtocolError("rdp.column.unknown", f"{t.name} has no column '{s['col']}'", table=t.name, column=s["col"], expected=known)
            rows = sorted(rows, key=lambda r, c=s["col"]: _sort_key(r.get(c)), reverse=s.get("dir") == "desc")
        sig = json.dumps([q.get("filter"), q.get("sort"), q.get("columns")], sort_keys=True)
        start = int(q.get("offset") or 0)
        if q.get("cursor"):
            tag, _, off = str(q["cursor"]).partition(":")
            if tag != str(zlib.crc32(sig.encode())) or not off.isdigit():
                raise ProtocolError("rdp.cursor.invalid", "the cursor is not from this query", table=t.name)
            start = int(off)
        limit = min(int(q.get("limit") or PAGE_ROWS), PAGE_ROWS)
        page = rows[start:start + limit]
        cols = q.get("columns") or known
        out: dict = {"table": t.name, "version": {"epoch": t.epoch, "seq": t.seq}}
        if q.get("shape") == "objects":
            out["rows"] = [{c: r.get(c) for c in cols} for r in page]
        else:
            out.update(columns=cols, values=[[r.get(c) for c in cols] for r in page])
        out["total"] = len(rows)
        if start + limit < len(rows):
            out["next"] = f"{zlib.crc32(sig.encode())}:{start + limit}"
        if q.get("schema"):
            out["schema"] = t.schema()
        return out

    # -- changes (minimal SSE form, DATA_PROTOCOL 6.2) --------------------------------------------------------------
    def _start_live(self, t: _Table) -> None:
        if t.live_source is None or t.live_started:
            return
        t.live_started = True

        def pump() -> None:
            for key, row in t.live_source():          # type: ignore[misc]
                if row is None:
                    t.apply({"remove": [list(key) if isinstance(key, (list, tuple)) else [key]]})
                else:
                    t.apply({"update": [row]})
        threading.Thread(target=pump, daemon=True, name=f"rachana-live-{t.name}").start()

    def changes(self, table: str, after: Optional[str] = None, heartbeat: float = 15.0, stop: Optional[threading.Event] = None) -> Iterator[str]:
        """SSE text frames (``event: msg``) for ``table`` after ``epoch:seq``; a ``: heartbeat`` comment while idle. A reader
        whose position is not in the log (other epoch, or too old) gets ``event: gap`` and must re-read the snapshot."""
        t = self._table(table)
        if not t.key:
            raise ProtocolError("rdp.table.no-key", f"{table} has no key and cannot change", table=table)
        seq0 = t.seq                      # the position at subscribe time, before a live source can move it
        self._start_live(t)
        return self._frames(t, after, heartbeat, stop, seq0)

    def _frames(self, t: _Table, after: Optional[str], heartbeat: float, stop: Optional[threading.Event], seq0: int) -> Iterator[str]:
        table = t.name
        seq = seq0
        if after:
            epoch, _, n = after.partition(":")
            if epoch == t.epoch and n.isdigit() and int(n) >= (t.log[0][0] - 1 if t.log else t.seq):
                seq = int(n)
            else:
                yield "event: gap\ndata: " + json.dumps({"table": table, "version": {"epoch": t.epoch, "seq": t.seq}}) + "\n\n"
        while not (stop and stop.is_set()):
            with t.cond:
                pending = [(s, p) for s, p in t.log if s > seq]
                if not pending:
                    t.cond.wait(timeout=min(heartbeat, 1.0) if stop else heartbeat)
                    pending = [(s, p) for s, p in t.log if s > seq]
            if not pending:
                yield ": heartbeat\n\n"
                if stop is None and heartbeat <= 0:
                    return
                continue
            for s, p in pending:
                seq = s
                yield f"id: {t.epoch}:{s}\nevent: msg\ndata: " + json.dumps({"topic": f"rdp.{table}", "kind": "rows", "seq": s, "data": p}) + "\n\n"
