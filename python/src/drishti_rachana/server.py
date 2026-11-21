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
from decimal import Decimal, InvalidOperation
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
    def __init__(self, name: str, source: Callable[[], list[dict]] | list[dict], key: Optional[list[str]], title: Optional[str],
                 declared: Optional[dict] = None, epoch: Optional[str] = None):
        self.name, self.source, self.key, self.title = name, source, key, title
        self.declared = declared          # the schema document a host declares (columns, can, ...); inferred from the rows when None
        self.epoch = epoch or uuid.uuid4().hex[:4]
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
        if self.declared is not None:
            d = {**self.declared, "name": self.name, "rows": len(rows), "version": {"epoch": self.epoch, "seq": self.seq}}
            d.setdefault("limits", {"pageRows": PAGE_ROWS})
            d.setdefault("can", ["page", "sort", "filter", "offset", "count", "group"])
            d["columns"] = [c if isinstance(c, dict) else {"name": c, "type": "string"} for c in d.get("columns", [])]
            return d
        names: list[str] = []
        for r in rows[:200]:
            for k in r:
                if k not in names:
                    names.append(k)
        cols = [Column(name=n, type=_type_of(r.get(n) for r in rows[:200])).to_json() for n in names]
        can = ["page", "sort", "filter", "offset", "count", "group"] + (["changes", "replay"] if self.key else [])
        d: dict = {"name": self.name}
        if self.title:
            d["title"] = self.title
        if self.key:
            d["key"] = self.key
        d.update(columns=cols, rows=len(rows), can=can, version={"epoch": self.epoch, "seq": self.seq},
                 limits={"pageRows": PAGE_ROWS})
        return d


_NUMERIC = ("integer", "number", "decimal")
_OPS = {"==", "!=", "<", "<=", ">", ">=", "&&", "||", "!", "in", "not-in", "is-null", "contains", "starts_with", "ends_with", "lower", "upper"}


def _refs(node: dict) -> Iterator[str]:
    """The column names a filter tree reads; an unknown operation is ``rdp.query.invalid`` (DATA_PROTOCOL 7)."""
    if "col" in node:
        yield node["col"].split(".")[0]
    for n in node.get("args", []) + node.get("list", []):
        yield from _refs(n)
    if "op" in node and node["op"] not in _OPS:
        raise ProtocolError("rdp.query.invalid", f"the operation '{node['op']}' is not in the filter language")


def _number(x: Any) -> Any:
    try:
        return Decimal(str(x)) if not isinstance(x, bool) else x
    except (InvalidOperation, ValueError):
        return x


def _eval(node: dict, row: dict, types: Optional[dict] = None) -> Any:
    types = types or {}
    if "col" in node:
        v: Any = row
        for part in node["col"].split("."):
            v = v.get(part) if isinstance(v, dict) else None
        return v
    if "lit" in node:
        return node["lit"]
    if "list" in node:
        return [_eval(n, row, types) for n in node["list"]]
    op, args = node.get("op"), node.get("args", [])
    a = [_eval(n, row, types) for n in args]
    numeric = any(("col" in n and types.get(n["col"]) in _NUMERIC) or n.get("type") in _NUMERIC for n in args)

    def cmp(f: Callable[[Any, Any], bool]) -> Any:
        if a[0] is None or a[1] is None:
            return False
        x, y = (_number(a[0]), _number(a[1])) if numeric or not (isinstance(a[0], str) and isinstance(a[1], str)) else (a[0], a[1])
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
        raise ProtocolError("rdp.query.invalid", f"the operation '{op}' is not in the filter language")
    return table[op]()


def _ordered(rows: list[dict], col: str, desc: bool, nulls_first: bool, ctype: str) -> list[dict]:
    """Sort by one column, stable; empty values last unless ``nulls_first`` whatever the direction (DATA_PROTOCOL 6.1)."""
    def key(r: dict) -> Any:
        v = r.get(col)
        if ctype in _NUMERIC:
            n = _number(v)
            return (0, n) if isinstance(n, Decimal) else (1, str(n))
        return (0, v if isinstance(v, (int, float)) and not isinstance(v, bool) else str(v))
    full = [r for r in rows if r.get(col) is not None]
    empty = [r for r in rows if r.get(col) is None]
    try:
        full = sorted(full, key=key, reverse=desc)
    except TypeError:
        full = sorted(full, key=lambda r: str(r.get(col)), reverse=desc)
    return empty + full if nulls_first else full + empty


def _decimal_text(d: Decimal, scale: int) -> str:
    return f"{d:.{scale}f}"


def _aggregate(rows: list[dict], v: dict, ctype: str, scale: int) -> Any:
    agg, col = v["agg"], v.get("col")
    vals = [r.get(col) for r in rows if r.get(col) is not None] if col else []
    if agg == "count":
        return len(rows) if not col else len(vals)
    if agg == "distinct":
        return len({json.dumps(x, sort_keys=True) for x in vals})
    if not vals:
        return None
    if ctype in _NUMERIC:
        nums = [_number(x) for x in vals]
        r = {"sum": lambda: sum(nums), "min": lambda: min(nums), "max": lambda: max(nums), "avg": lambda: sum(nums) / len(nums)}[agg]()
        if ctype == "decimal":
            return _decimal_text(Decimal(r), scale)
        return int(r) if ctype == "integer" and agg != "avg" else float(r)
    if agg in ("min", "max"):
        return (min if agg == "min" else max)(vals, key=str)
    raise ProtocolError("rdp.query.invalid", f"{agg} needs a numeric column", column=col)


class DataHost:
    """Tables by name; ``handle`` answers the Data Protocol's HTTP resources; ``publish`` feeds the change stream."""

    def __init__(self, base: str = "") -> None:
        self.base = base.rstrip("/")
        self.tables: dict[str, _Table] = {}

    def add_table(self, name: str, source: Callable[[], list[dict]] | list[dict], key: Optional[list[str]] = None,
                  title: Optional[str] = None, schema: Optional[dict] = None, epoch: Optional[str] = None) -> _Table:
        t = self.tables[name] = _Table(name, source, key, title, schema, epoch)
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
        if str(q.get("schema")).lower() == "true":
            out["schema"] = True
        return out

    def _rows(self, t: _Table, q: dict) -> dict:
        rows = t.snapshot()
        sch = t.schema()
        cols_meta = {c["name"]: c for c in sch["columns"]}
        known = list(cols_meta)
        can = set(sch.get("can", []))
        types = {n: c.get("type") for n, c in cols_meta.items()}

        def column(name: str) -> dict:
            if name not in cols_meta:
                raise ProtocolError("rdp.column.unknown", f"{t.name} has no column '{name}'", table=t.name, column=name, expected=known)
            return cols_meta[name]

        def need(op: str) -> None:
            if op not in can:
                raise ProtocolError("rdp.query.unsupported", f"{t.name} does not declare '{op}'", table=t.name, operation=op)

        for c in q.get("columns") or []:
            column(c)
        if q.get("filter"):
            for c in _refs(q["filter"]):
                if column(c).get("masked"):
                    raise ProtocolError("rdp.forbidden", f"{c} is masked and cannot be filtered", table=t.name, column=c)
            need("filter")
            rows = [r for r in rows if _eval(q["filter"], r, types)]
        if q.get("sort"):
            need("sort")
        if q.get("offset"):
            need("offset")
        grouped = q.get("group")
        if grouped:
            need("group")
            for c in grouped.get("by", []) + [v["col"] for v in grouped.get("values", []) if v.get("col")]:
                if column(c).get("masked"):
                    raise ProtocolError("rdp.forbidden", f"{c} is masked and cannot be grouped", table=t.name, column=c)
            return self._grouped(t, rows, q, grouped, column)
        for s in q.get("sort") or []:
            if column(s["col"]).get("masked"):
                raise ProtocolError("rdp.forbidden", f"{s['col']} is masked and cannot be sorted", table=t.name, column=s["col"])
        order = list(q.get("sort") or [])
        for k in reversed([{"col": k} for k in (t.key or []) if k not in [s["col"] for s in order]]):     # the key last: stable pages
            rows = _ordered(rows, k["col"], False, False, types.get(k["col"], "string"))
        for s in reversed(order):
            rows = _ordered(rows, s["col"], s.get("dir") == "desc", s.get("nulls") == "first", types.get(s["col"], "string"))
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
            out["schema"] = sch
        return out

    def _grouped(self, t: _Table, rows: list[dict], q: dict, g: dict, column: Callable[[str], dict]) -> dict:
        """``group`` (DATA_PROTOCOL 6.3): one row per distinct ``by`` tuple, the aggregates of ``values``, optional ``totals``."""
        by, values = g.get("by", []), g.get("values", [])
        groups: dict[str, list[dict]] = {}
        for r in rows:
            groups.setdefault(json.dumps([r.get(c) for c in by], default=str), []).append(r)

        def agg(rs: list[dict], v: dict) -> Any:
            c = column(v["col"]) if v.get("col") else {}
            scale = c.get("scale")
            if scale is None:
                scale = max((len(str(x).partition(".")[2]) for x in (r.get(v.get("col")) for r in rows) if x is not None), default=0)
            return _aggregate(rs, v, c.get("type", "string"), int(scale))

        out_rows = [[rs[0].get(c) for c in by] + [agg(rs, v) for v in values] for rs in groups.values()]
        names = by + [v["name"] for v in values]
        for s in reversed(q.get("sort") or []):
            if s["col"] not in names:
                raise ProtocolError("rdp.column.unknown", f"{s['col']} is not in the grouped result", table=t.name, column=s["col"], expected=names)
            i = names.index(s["col"])
            out_rows.sort(key=lambda r, i=i: (r[i] is None, _number(r[i]) if isinstance(_number(r[i]), Decimal) else str(r[i])), reverse=s.get("dir") == "desc")
        out: dict = {"table": t.name, "version": {"epoch": t.epoch, "seq": t.seq}, "columns": names, "values": out_rows, "total": len(out_rows)}
        if g.get("totals"):
            out["totals"] = {v["name"]: agg(rows, v) for v in values}
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
            replay = "replay" in t.schema().get("can", [])
            if epoch == t.epoch and n.isdigit() and int(n) == t.seq:
                seq = int(n)
            elif replay and epoch == t.epoch and n.isdigit() and int(n) >= (t.log[0][0] - 1 if t.log else t.seq):
                seq = int(n)
            else:      # another epoch, a position the log no longer holds, or a host without replay: read the snapshot again
                yield "event: gap\ndata: " + json.dumps({"t": "gap", "table": table, "recoverable": False,
                                                          "version": {"epoch": t.epoch, "seq": t.seq}}) + "\n\n"
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
                yield f"id: {t.epoch}:{s}\nevent: msg\ndata: " + json.dumps({"t": "msg", "topic": f"rdp.{table}", "stream": f"rdp.{table}", "epoch": t.epoch, "kind": "rows", "seq": s, "data": p}) + "\n\n"
