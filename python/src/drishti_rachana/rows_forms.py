# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""A page of rows as an Arrow IPC stream (PROTOCOL 5.2) or NDJSON (5.3): the Python twin of java/rachana-data's ArrowRows and
NdjsonRows. The host answers the JSON page; these write it in the other forms. Arrow needs the official pyarrow
(`pip install 'drishti-rachana[arrow]'`); without it :func:`arrow_available` is false and the host answers 406, as for a table that
does not declare `arrow`. Types (PROTOCOL 3.1): string Utf8, integer Int64 (Decimal(38, 0) when a value is beyond a long), decimal
Decimal128 (the column's `scale`, else the largest scale in the page), number Float64, boolean Bool, date Date32, timestamp
Timestamp(ms, UTC), time Time32(ms), list and object Utf8 holding JSON; every field carries `rachana.type`. A masked cell is a null slot
plus true in a `<name>$masked` Bool column (3.2)."""
from __future__ import annotations

import datetime
import json
import re
from decimal import Decimal
from typing import Any, Optional

BATCH_ROWS = 65_536
ARROW_MEDIA = "application/vnd.apache.arrow.stream"
NDJSON_MEDIA = "application/x-ndjson"
_MASK = "$masked"
_DECIMAL = re.compile(r"-?[0-9]+(\.[0-9]+)?\Z")


def arrow_available() -> bool:
    try:
        import pyarrow  # noqa: F401
    except ImportError:
        return False
    return True


def _compact(x: Any) -> str:
    return json.dumps(x, ensure_ascii=False, separators=(",", ":"))


def _columns(page: dict) -> list[str]:
    if isinstance(page.get("columns"), list):
        return [str(c) for c in page["columns"]]
    rows = page.get("rows")
    return list(rows[0]) if rows else []


def _values(page: dict, cols: list[str]) -> list[list]:
    if isinstance(page.get("values"), list):
        return [list(v) for v in page["values"]]
    return [[r.get(c) for c in cols] for r in page.get("rows") or []]


def _masked_cell(v: Any) -> bool:
    return isinstance(v, dict) and v.get("$masked") is True


def _fits_long(v: Any) -> bool:
    if isinstance(v, bool):
        return False
    try:
        d = Decimal(str(v))
    except Exception:
        return False
    return d == d.to_integral_value() and -(2 ** 63) <= d <= 2 ** 63 - 1


def _infer(values: list[list], c: int) -> str:
    t: Optional[str] = None
    for row in values:
        v = row[c]
        if v is None or _masked_cell(v):
            continue
        vt = ("boolean" if isinstance(v, bool) else "integer" if isinstance(v, int) else "number" if isinstance(v, float)
              else "decimal" if isinstance(v, str) and _DECIMAL.match(v) else "string" if isinstance(v, str) else "object")
        if t is None or t == vt:
            t = vt
        else:
            t = "number" if {t, vt} == {"integer", "number"} else "string"
    return t or "string"


def ndjson_write(page: dict) -> bytes:
    """The page as NDJSON: one header object, then one array of cells per line in `columns` order."""
    cols = _columns(page)
    head: dict = {"table": page.get("table")}
    if page.get("version") is not None:
        head["version"] = page["version"]
    head["columns"] = cols
    for k in ("total", "next", "totals", "schema"):
        if page.get(k) is not None:
            head[k] = page[k]
    lines = [_compact(head)] + [_compact(r) for r in _values(page, cols)]
    return ("\n".join(lines) + "\n").encode("utf-8")


def arrow_write(page: dict, schema: dict, batch_rows: int = BATCH_ROWS) -> bytes:
    """The page as an Arrow IPC stream; `schema` is the table's schema document (its columns' types, scale, masked, fmt, label, unit)."""
    import pyarrow as pa

    cols = _columns(page)
    values = _values(page, cols)
    docs = {c["name"]: c for c in schema.get("columns", []) if isinstance(c, dict)}
    fields, arrays, masks = [], [], []
    for i, name in enumerate(cols):
        doc = docs.get(name)
        typ = doc["type"] if doc else _infer(values, i)
        col_masked = bool(doc and doc.get("masked"))
        cells = [None if (v is None or _masked_cell(v)) else v for v in (row[i] for row in values)]
        any_mask = col_masked or any(_masked_cell(row[i]) for row in values)
        meta = {"rachana.type": typ}
        for k in ("fmt", "label", "unit"):
            if doc and isinstance(doc.get(k), str):
                meta["rachana." + k] = doc[k]
        if typ == "integer":
            big = any(v is not None and not _fits_long(v) for v in cells)
            at = pa.decimal128(38, 0) if big else pa.int64()
            arr = pa.array([None if v is None else (Decimal(str(v)) if big else int(Decimal(str(v)))) for v in cells], type=at)
        elif typ == "decimal":
            scale = max((max(0, -Decimal(str(v)).as_tuple().exponent) for v in cells if v is not None), default=0)
            if doc and isinstance(doc.get("scale"), int):
                scale = doc["scale"]
            q = Decimal(1).scaleb(-scale)
            out = []
            for v in cells:
                if v is None:
                    out.append(None)
                    continue
                d = Decimal(repr(v)) if isinstance(v, float) else Decimal(str(v))
                r = d.quantize(q)
                if r != d:
                    raise ValueError(f"{name}: {v} does not fit scale {scale}")
                out.append(r)
            arr = pa.array(out, type=pa.decimal128(38, scale))
        elif typ == "number":
            arr = pa.array([None if v is None or v != v or v in (float("inf"), float("-inf")) else float(v) for v in cells], type=pa.float64())
        elif typ == "boolean":
            arr = pa.array([None if v is None else v is True for v in cells], type=pa.bool_())
        elif typ == "date":
            arr = pa.array([None if v is None else datetime.date.fromisoformat(v) for v in cells], type=pa.date32())
        elif typ == "timestamp":
            arr = pa.array([None if v is None else datetime.datetime.fromisoformat(v.replace("Z", "+00:00")) for v in cells],
                           type=pa.timestamp("ms", tz="UTC"))
        elif typ == "time":
            arr = pa.array([None if v is None else datetime.time.fromisoformat(v) for v in cells], type=pa.time32("ms"))
        elif typ in ("list", "object"):
            arr = pa.array([None if v is None else _compact(v) for v in cells], type=pa.string())
        else:
            arr = pa.array([None if v is None else v if isinstance(v, str) else _compact(v) if isinstance(v, (dict, list)) else str(v)
                            for v in cells], type=pa.string())
        fields.append(pa.field(name, arr.type, True, meta))
        arrays.append(arr)
        if any_mask:
            masks.append((name, [col_masked or _masked_cell(row[i]) for row in values]))
    for name, flags in masks:
        fields.append(pa.field(name + _MASK, pa.bool_(), True, {"rachana.masked": name}))
        arrays.append(pa.array(flags, type=pa.bool_()))
    md = {"rachana.table": str(page.get("table", schema.get("name", "")))}
    if schema.get("key"):
        md["rachana.key"] = _compact(schema["key"])
    if isinstance(page.get("version"), dict):
        md["rachana.epoch"] = str(page["version"]["epoch"])
        md["rachana.seq"] = str(page["version"]["seq"])
    for k in ("total", "next"):
        if page.get(k) is not None:
            md["rachana." + k] = str(page[k])
    for k in ("totals", "schema"):
        if page.get(k) is not None:
            md["rachana." + k] = _compact(page[k])
    table = pa.Table.from_arrays(arrays, schema=pa.schema(fields, metadata=md))
    sink = pa.BufferOutputStream()
    with pa.ipc.new_stream(sink, table.schema) as w:
        for b in table.to_batches(max_chunksize=max(1, batch_rows)):
            w.write_batch(b)
    return sink.getvalue().to_pybytes()
