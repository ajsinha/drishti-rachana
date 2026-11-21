# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""One reader's subscription to a table's changes: the Python twin of java/rachana-data's Subscription (PROTOCOL 8.2, 8.4).

Its `msg` frames go to a sink, in `seq` order. With a **filter** (8.2 `changes.filter`) each patch is restated for the rows the reader
may see: a row that stops matching is a `remove`, a row that starts matching an `update` carrying every field, a matching row an
`update` of the whole row; a patch that touches none of them is still sent, empty, so the numbering has no holes. With **conflation**
(`conflate: {everyMs}`, 8.4) the frames arriving within `everyMs` are merged by key into one message whose `seq` is the last merged
and `first` the first (a reader takes `first` as the message's start: it is no gap). Thread-safe; the sink is called one frame at a time."""
from __future__ import annotations

import json
import threading
from typing import Any, Callable, Optional


def key_text(row: dict, key: list[str]) -> str:
    return json.dumps([str(row.get(k)) for k in key])


def removed_key_text(k: Any) -> str:
    return json.dumps([str(x) for x in (k if isinstance(k, (list, tuple)) else [k])])


def merge(table: str, key: list[str], patches: list[dict]) -> dict:
    """Patches of one table merged by key in order (8.4): a later `remove` cancels an earlier `add` or `update` of that key, a later
    `add` after a `remove` stands, later fields win; a `reset` starts the merged state again from its rows. The result has the same
    effect as the patches applied one by one. A table without a key concatenates its adds."""
    out: dict = {"table": table}
    reset: Optional[dict[str, dict]] = None
    ops: dict[str, list] = {}               # key text -> [removed key values or None, fields or None]
    adds: list = []

    def apply(rows: dict[str, dict], p: dict) -> None:
        for k in p.get("remove") or []:
            rows.pop(removed_key_text(k), None)
        for r in (p.get("update") or []) + (p.get("add") or []):
            kt = key_text(r, key)
            rows[kt] = {**rows.get(kt, {}), **r}

    for p in patches:
        if isinstance(p.get("reset"), list):
            reset = {key_text(r, key): dict(r) for r in p["reset"]}
            ops.clear()
            adds.clear()
            apply(reset, {k: v for k, v in p.items() if k != "reset"})
            continue
        if reset is not None:
            apply(reset, p)
            continue
        if not key:
            adds.extend(p.get("add") or [])
            continue
        for k in p.get("remove") or []:
            ops[removed_key_text(k)] = [list(k) if isinstance(k, (list, tuple)) else [k], None]
        for r in (p.get("update") or []) + (p.get("add") or []):
            e = ops.setdefault(key_text(r, key), [None, None])
            e[1] = {**(e[1] or {}), **r}
    if reset is not None:
        out["reset"] = list(reset.values())
        return out
    if not key:
        out["add"] = adds
        return out
    remove = [e[0] for e in ops.values() if e[0] is not None]
    update = [e[1] for e in ops.values() if e[1] is not None]
    if remove:
        out["remove"] = remove
    if update:
        out["update"] = update
    return out


class Subscription:
    def __init__(self, table: str, key: list[str], match: Optional[Callable[[dict], bool]], every_ms: int, recovered: bool,
                 sink: Callable[[dict], None]) -> None:
        self.table, self.key, self.match = table, key, match
        self.every_ms = max(0, int(every_ms or 0))
        self.recovered = recovered
        self.sink = sink
        self.visible: set[str] = set()
        self.pending: list[dict] = []
        self.scheduled = False
        self.closed = False
        self.on_close: Callable[[], None] = lambda: None
        self.lock = threading.RLock()

    def start(self, rows: list[dict]) -> None:
        """The rows the reader sees at the start (a filtered subscription), by key text. Called under the table's lock."""
        if self.match is not None:
            self.visible.update(key_text(r, self.key) for r in rows if self.match(r))

    def offer(self, frame: dict, rows_after: list[dict]) -> None:
        """A published frame, the table's rows as they are after it. Called under the table's lock, in seq order."""
        if self.closed:
            return
        f = frame
        if self.match is not None:
            f = {**frame, "data": self._restate(frame["data"], rows_after)}
        with self.lock:
            self.pending.append(f)

    def drain(self) -> None:
        """Delivers what is pending: now, or (conflated) once the window that started with the first pending frame ends."""
        with self.lock:
            if self.closed or not self.pending:
                return
            if self.every_ms > 0:
                if not self.scheduled:
                    self.scheduled = True
                    t = threading.Timer(self.every_ms / 1000.0, self.flush)
                    t.daemon = True
                    t.start()
                return
            out, self.pending = self.pending, []
            for f in out:
                self.sink(f)        # under this subscription's lock: two publishers never interleave one reader's frames

    def flush(self) -> None:
        """The conflation window ended: the pending frames merged into one message."""
        with self.lock:
            self.scheduled = False
            if self.closed or not self.pending:
                return
            merged = dict(self.pending[-1])
            if len(self.pending) > 1:
                merged["first"] = self.pending[0]["seq"]
                merged["data"] = merge(self.table, self.key, [p["data"] for p in self.pending])
            self.pending = []
            self.sink(merged)

    def close(self) -> None:
        self.closed = True
        self.on_close()

    def _restate(self, patch: dict, rows_after: list[dict]) -> dict:
        now = {key_text(r, self.key): r for r in rows_after}
        out: dict = {"table": patch.get("table")}
        if isinstance(patch.get("reset"), list):
            self.visible.clear()
            rows = []
            for kt, r in now.items():
                if self.match(r):          # type: ignore[misc]
                    self.visible.add(kt)
                    rows.append(r)
            out["reset"] = rows
            return out
        touched: dict[str, list] = {}
        for k in patch.get("remove") or []:
            touched.setdefault(removed_key_text(k), list(k) if isinstance(k, (list, tuple)) else [k])
        for op in ("update", "add"):
            for r in patch.get(op) or []:
                touched.setdefault(key_text(r, self.key), [r.get(c) for c in self.key])
        remove, update = [], []
        for kt, kv in touched.items():
            row = now.get(kt)
            hit = row is not None and bool(self.match(row))      # type: ignore[misc]
            was = kt in self.visible
            if was and not hit:
                remove.append(kv)
                self.visible.discard(kt)
            elif hit:
                update.append(row)
                self.visible.add(kt)
        if remove:
            out["remove"] = remove
        if update:
            out["update"] = update
        return out
