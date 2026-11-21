# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""Data Protocol v1 messages as dataclasses with ``to_json`` / ``from_json`` (docs/design/DATA_PROTOCOL.md).

TODO(F8): align names and field order with ``spec/1/data-protocol`` once the normative text and schemas land.
Key order of ``to_json`` follows the order the design writes the fields in; absent (None) fields are left out.
"""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Optional

Json = Any

COLUMN_TYPES = ("string", "integer", "decimal", "number", "boolean", "date", "timestamp", "time", "list", "object")
CAPABILITIES = ("page", "sort", "filter", "group", "arrow", "changes", "count", "offset", "snapshot")
ERROR_CODES = (
    "rdp.auth.required", "rdp.forbidden", "rdp.table.unknown", "rdp.column.unknown", "rdp.query.invalid",
    "rdp.query.unsupported", "rdp.cursor.invalid", "rdp.limit.rows", "rdp.limit.time", "rdp.limit.rate",
    "rdp.table.no-key", "rdp.unavailable", "rdp.internal",
)  # DATA_PROTOCOL 7


def _put(d: dict, **kw: Json) -> dict:
    for k, v in kw.items():
        if v is not None:
            d[k] = v
    return d


@dataclass
class Version:
    epoch: str
    seq: int

    def to_json(self) -> dict:
        return {"epoch": self.epoch, "seq": self.seq}

    @classmethod
    def from_json(cls, d: dict) -> "Version":
        return cls(epoch=d["epoch"], seq=d["seq"])


@dataclass
class Column:
    name: str
    type: str
    nullable: Optional[bool] = None
    label: Optional[str] = None
    fmt: Optional[str] = None
    unit: Optional[str] = None
    scale: Optional[int] = None
    grain: Optional[str] = None
    items: Optional[str] = None
    hint: Optional[str] = None
    masked: Optional[bool] = None

    def to_json(self) -> dict:
        d: dict = {"name": self.name, "type": self.type}
        return _put(d, nullable=self.nullable, label=self.label, fmt=self.fmt, unit=self.unit, scale=self.scale,
                    grain=self.grain, items=self.items, hint=self.hint, masked=self.masked)

    @classmethod
    def from_json(cls, d: dict) -> "Column":
        return cls(**{k: d[k] for k in ("name", "type", "nullable", "label", "fmt", "unit", "scale", "grain",
                                         "items", "hint", "masked") if k in d})


@dataclass
class TableSchema:
    """The schema document of a table (DATA_PROTOCOL 2)."""
    name: str
    columns: list[Column] = field(default_factory=list)
    title: Optional[str] = None
    key: Optional[list[str]] = None
    rows: Optional[int] = None
    can: Optional[list[str]] = None
    version: Optional[Version] = None
    limits: Optional[dict] = None

    def to_json(self) -> dict:
        d: dict = {"name": self.name}
        _put(d, title=self.title, key=self.key)
        d["columns"] = [c.to_json() for c in self.columns]
        _put(d, rows=self.rows, can=self.can, version=self.version.to_json() if self.version else None,
             limits=self.limits)
        return d

    @classmethod
    def from_json(cls, d: dict) -> "TableSchema":
        return cls(name=d["name"], columns=[Column.from_json(c) for c in d.get("columns", [])],
                   title=d.get("title"), key=d.get("key"), rows=d.get("rows"), can=d.get("can"),
                   version=Version.from_json(d["version"]) if "version" in d else None, limits=d.get("limits"))


@dataclass
class RowsPage:
    """One page of rows (DATA_PROTOCOL 4.1): columnar-by-row (``values``) or objects (``rows``)."""
    table: str
    columns: Optional[list[str]] = None
    values: Optional[list[list[Json]]] = None
    rows: Optional[list[dict]] = None
    version: Optional[Version] = None
    total: Optional[int] = None
    next: Optional[str] = None
    schema: Optional[TableSchema] = None
    totals: Optional[dict] = None

    def to_json(self) -> dict:
        d: dict = {"table": self.table}
        return _put(d, version=self.version.to_json() if self.version else None, columns=self.columns,
                    values=self.values, rows=self.rows, total=self.total, next=self.next,
                    schema=self.schema.to_json() if self.schema else None, totals=self.totals)

    @classmethod
    def from_json(cls, d: dict) -> "RowsPage":
        return cls(table=d["table"], columns=d.get("columns"), values=d.get("values"), rows=d.get("rows"),
                   version=Version.from_json(d["version"]) if "version" in d else None, total=d.get("total"),
                   next=d.get("next"), schema=TableSchema.from_json(d["schema"]) if "schema" in d else None,
                   totals=d.get("totals"))

    def as_objects(self) -> list[dict]:
        """The rows as objects, whichever form the page carries (readers accept both)."""
        if self.rows is not None:
            return list(self.rows)
        cols = self.columns or []
        return [dict(zip(cols, v)) for v in (self.values or [])]


# Filter tree (DATA_PROTOCOL 5.1): a syntax tree in JSON, never expression text.
FilterNode = dict


def col(name: str) -> FilterNode:
    return {"col": name}


def lit(value: Json, type: Optional[str] = None) -> FilterNode:
    return _put({"lit": value}, type=type)


def lst(*nodes: FilterNode) -> FilterNode:
    return {"list": list(nodes)}


def op(name: str, *args: FilterNode) -> FilterNode:
    return {"op": name, "args": list(args)}


@dataclass
class Query:
    """A query document (DATA_PROTOCOL 5)."""
    columns: Optional[list[str]] = None
    filter: Optional[FilterNode] = None
    sort: Optional[list[dict]] = None
    limit: Optional[int] = None
    cursor: Optional[str] = None
    group: Optional[dict] = None
    as_of: Optional[Json] = None
    params: Optional[dict] = None

    def to_json(self) -> dict:
        return _put({}, columns=self.columns, filter=self.filter, sort=self.sort, limit=self.limit,
                    cursor=self.cursor, group=self.group, asOf=self.as_of, params=self.params)

    @classmethod
    def from_json(cls, d: dict) -> "Query":
        return cls(columns=d.get("columns"), filter=d.get("filter"), sort=d.get("sort"), limit=d.get("limit"),
                   cursor=d.get("cursor"), group=d.get("group"), as_of=d.get("asOf"), params=d.get("params"))


@dataclass
class Patch:
    """A keyed row patch (DATA_PROTOCOL 6.1). Applied in order: reset, remove, update, add.

    ``remove`` holds keys (a compound key is a list in key order); ``update`` and ``add`` hold row objects.
    """
    table: str
    remove: Optional[list[Json]] = None
    update: Optional[list[dict]] = None
    add: Optional[list[dict]] = None
    reset: Optional[list[dict]] = None

    def to_json(self) -> dict:
        # "reset" is always sent in the design's example (null when not used); the other lists only when present.
        d = _put({"table": self.table}, remove=self.remove, update=self.update, add=self.add)
        d["reset"] = self.reset
        return d

    @classmethod
    def from_json(cls, d: dict) -> "Patch":
        return cls(table=d["table"], remove=d.get("remove"), update=d.get("update"), add=d.get("add"),
                   reset=d.get("reset"))


@dataclass
class ProtocolError(Exception):
    """The error body (DATA_PROTOCOL 7): ``{"error": {code, message, …}}``."""
    code: str
    message: str
    table: Optional[str] = None
    column: Optional[str] = None
    expected: Optional[list[str]] = None
    retry: Optional[bool] = None
    retry_after_ms: Optional[int] = None
    operation: Optional[str] = None

    def __post_init__(self) -> None:
        Exception.__init__(self, f"{self.code}: {self.message}")

    def to_json(self) -> dict:
        return {"error": _put({"code": self.code, "message": self.message}, table=self.table, column=self.column,
                              expected=self.expected, retry=self.retry, retryAfterMs=self.retry_after_ms,
                              operation=self.operation)}

    @classmethod
    def from_json(cls, d: dict) -> "ProtocolError":
        e = d["error"]
        return cls(code=e["code"], message=e["message"], table=e.get("table"), column=e.get("column"),
                   expected=e.get("expected"), retry=e.get("retry"), retry_after_ms=e.get("retryAfterMs"),
                   operation=e.get("operation"))
