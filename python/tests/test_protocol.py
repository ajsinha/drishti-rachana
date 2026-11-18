# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
import json

from rachana.protocol import Column, Patch, ProtocolError, Query, RowsPage, TableSchema, Version, col, lit, op


def test_schema_roundtrip_keeps_order_and_drops_absent():
    d = {"name": "sales", "title": "Sales", "key": ["region", "month"],
         "columns": [{"name": "region", "type": "string"},
                     {"name": "revenue", "type": "decimal", "scale": 2, "unit": "USD", "fmt": "amount0"}],
         "rows": 6, "can": ["page", "sort"], "version": {"epoch": "a1", "seq": 4112}}
    s = TableSchema.from_json(d)
    assert s.columns[1].scale == 2
    assert json.dumps(s.to_json()) == json.dumps(d)


def test_page_forms():
    p = RowsPage.from_json({"table": "t", "columns": ["a", "b"], "values": [[1, 2], [3, 4]], "next": "c-1"})
    assert p.as_objects() == [{"a": 1, "b": 2}, {"a": 3, "b": 4}]
    assert RowsPage.from_json({"table": "t", "rows": [{"a": 1}]}).as_objects() == [{"a": 1}]
    assert "total" not in p.to_json()


def test_filter_tree_and_query():
    f = op("&&", op("==", col("region"), lit("North")), op(">=", col("month"), lit("2026-01-01", "date")))
    q = Query(filter=f, limit=500)
    assert Query.from_json(q.to_json()) == q
    assert q.to_json()["filter"]["args"][1]["args"][1] == {"lit": "2026-01-01", "type": "date"}


def test_patch_always_sends_reset():
    p = Patch(table="sales", remove=[["West", "2026-01-01"]], add=[{"region": "East"}])
    assert list(p.to_json()) == ["table", "remove", "add", "reset"]
    assert Patch.from_json(p.to_json()) == p


def test_error_body():
    e = ProtocolError("rdp.column.unknown", "sales has no column 'x'", table="sales", retry=False)
    assert e.to_json() == {"error": {"code": "rdp.column.unknown", "message": "sales has no column 'x'",
                                     "table": "sales", "retry": False}}
    assert ProtocolError.from_json(e.to_json()).code == "rdp.column.unknown"
    assert Column("a", "string").to_json() == {"name": "a", "type": "string"}
    assert Version("a1", 1).to_json() == {"epoch": "a1", "seq": 1}
