# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""The minimal Data Protocol host and the Flask and FastAPI adapters over it (spec/1/data-protocol/PROTOCOL.md)."""
import json
import threading

import pytest

from drishti_rachana.protocol import ProtocolError
from drishti_rachana.server import DataHost

SALES = [{"region": "North", "month": "2026-01", "revenue": 120}, {"region": "North", "month": "2026-02", "revenue": 135},
         {"region": "East", "month": "2026-01", "revenue": 75}]
SUTRA = "rachana: 1\nsutra: hello\nversion: 1\ntitle: { id: \"'Hello'\" }\npanels:\n  - { id: m, kind: metric, label: Revenue, value: 1 }\n"


def host():
    h = DataHost()
    h.add_table("sales", lambda: [dict(r) for r in SALES], key=["region", "month"], title="Sales")
    return h


def get(h, path, **q):
    r = h.handle("GET", path, {k: str(v) for k, v in q.items()})
    return r.status, json.loads(r.body)


def test_discovery_schema_and_rows():
    h = host()
    assert get(h, "/")[1]["protocol"] == "rdp"
    assert get(h, "/tables")[1]["tables"][0]["name"] == "sales"
    s = get(h, "/tables/sales")[1]
    assert [c["type"] for c in s["columns"]] == ["string", "string", "integer"] and s["key"] == ["region", "month"]
    st, body = get(h, "/tables/sales/rows", sort="-revenue", limit=2)
    assert st == 200 and body["columns"] == ["region", "month", "revenue"]
    assert [v[2] for v in body["values"]] == [135, 120] and body["total"] == 3 and "next" in body
    st, page2 = get(h, "/tables/sales/rows", sort="-revenue", limit=2, cursor=body["next"])
    assert [v[2] for v in page2["values"]] == [75] and "next" not in page2
    assert get(h, "/tables/sales/rows", shape="objects", columns="region")[1]["rows"][0] == {"region": "East"}   # unsorted pages come in key order (PROTOCOL 6.1)


def test_errors_use_the_protocol_codes():
    h = host()
    st, b = get(h, "/tables/nope")
    assert (st, b["error"]["code"]) == (404, "rdp.table.unknown")
    st, b = get(h, "/tables/sales/rows", columns="reveune")
    assert (st, b["error"]["code"]) == (422, "rdp.column.unknown") and "revenue" in b["error"]["expected"]
    st, b = get(h, "/tables/sales/rows", sort="-revenue", limit=1)
    st, b = get(h, "/tables/sales/rows", cursor=b["next"])
    assert (st, b["error"]["code"]) == (410, "rdp.cursor.invalid")
    assert h.handle("GET", "/tables/sales/rows", {}, None, "application/vnd.apache.arrow.stream").status == 406


def test_query_filter_tree():
    h = host()
    q = {"filter": {"op": "&&", "args": [{"op": "==", "args": [{"col": "region"}, {"lit": "North"}]},
                                         {"op": ">=", "args": [{"col": "revenue"}, {"lit": 130}]}]},
         "sort": [{"col": "revenue", "dir": "desc"}]}
    r = h.handle("POST", "/tables/sales/query", {}, json.dumps(q).encode())
    assert json.loads(r.body)["values"] == [["North", "2026-02", 135]]
    bad = h.handle("POST", "/tables/sales/query", {}, json.dumps({"filter": {"op": "matches", "args": []}}).encode())
    assert json.loads(bad.body)["error"]["code"] == "rdp.query.invalid"   # PROTOCOL 7, 10.1
    grp = h.handle("POST", "/tables/sales/query", {}, json.dumps({"group": {"by": ["region"], "values": [{"name": "n", "agg": "count"}]}}).encode())
    assert grp.status == 200 and json.loads(grp.body)["columns"] == ["region", "n"] and json.loads(grp.body)["total"] == 2


def test_keyed_patches_and_the_change_stream():
    h = host()
    stop = threading.Event()
    stream = h.changes("sales", stop=stop, heartbeat=0.05)   # the position is taken here
    h.publish("sales", {"update": [{"region": "North", "month": "2026-01", "revenue": 999}], "remove": [["East", "2026-01"]],
                        "add": [{"region": "West", "month": "2026-01", "revenue": 1}]})
    frame = next(stream)
    assert "event: msg" in frame and '"seq": 1' in frame and '"remove"' in frame
    h.publish("sales", {"update": [{"region": "North", "month": "2026-02", "revenue": 1}]})
    assert '"seq": 2' in next(stream)
    rows = get(h, "/tables/sales/rows", shape="objects")[1]["rows"]
    assert {(r["region"], r["month"], r["revenue"]) for r in rows} == {("North", "2026-01", 999), ("North", "2026-02", 1), ("West", "2026-01", 1)}
    stop.set()
    resumed = h.changes("sales", after="zzzz:1", stop=threading.Event(), heartbeat=0.05)
    assert "event: gap" in next(resumed)
    with pytest.raises(ProtocolError):
        h.add_table("nokey", [{"a": 1}])
        h.changes("nokey")


def test_live_source_feeds_the_log():
    h = host()
    h.set_live("sales", lambda: iter([(("East", "2026-01"), None), (("North", "2026-01"), {"region": "North", "month": "2026-01", "revenue": 5})]))
    stop = threading.Event()
    got = []
    for frame in h.changes("sales", stop=stop, heartbeat=0.05):
        if "event: msg" in frame:
            got.append(frame)
        if len(got) == 2:
            stop.set()
    assert '"remove"' in got[0] and '"update"' in got[1]


def test_flask_blueprint(tmp_path):
    flask = pytest.importorskip("flask")
    from drishti_rachana.adapters.flask import RachanaBlueprint
    (tmp_path / "hello.sutra.yaml").write_text(SUTRA)
    bp = RachanaBlueprint(sutras=tmp_path)

    @bp.table("sales", key=["region", "month"])
    def sales():
        return SALES

    app = flask.Flask(__name__)
    app.register_blueprint(bp, url_prefix="/dash")
    c = app.test_client()
    assert b"/dash/page/hello" in c.get("/dash/").data
    page = c.get("/dash/page/hello").data.decode()
    assert "<rachana-view" in page and "/dash/sutras/hello.sutra.yaml" in page and "data='/dash/data'" in page
    assert c.get("/dash/sutras/hello.sutra.yaml").data.decode() == SUTRA
    assert c.get("/dash/page/none").status_code == 404
    assert c.get("/dash/data").get_json()["protocol"] == "rdp"
    assert c.get("/dash/data/tables/sales/rows?limit=1").get_json()["total"] == 3
    r = c.post("/dash/data/tables/sales/query", json={"filter": {"op": "==", "args": [{"col": "region"}, {"lit": "East"}]}})
    assert r.get_json()["total"] == 1
    assert c.get("/dash/data/changes?table=nope").status_code == 404


def test_fastapi_router(tmp_path):
    pytest.importorskip("fastapi")
    pytest.importorskip("httpx")
    from fastapi import FastAPI
    from fastapi.testclient import TestClient
    from drishti_rachana.adapters.fastapi import RachanaRouter
    (tmp_path / "hello.sutra.yaml").write_text(SUTRA)
    router = RachanaRouter(sutras=tmp_path)

    @router.table("sales", key=["region", "month"])
    async def sales():
        return SALES

    app = FastAPI()
    app.include_router(router, prefix="/dash")
    c = TestClient(app)
    assert "/dash/page/hello" in c.get("/dash/").text
    page = c.get("/dash/page/hello").text
    assert "<rachana-view" in page and "/dash/sutras/hello.sutra.yaml" in page
    assert c.get("/dash/sutras/hello.sutra.yaml").text == SUTRA
    assert c.get("/dash/data").json()["protocol"] == "rdp"
    assert c.get("/dash/data/tables/sales/rows", params={"limit": 2}).json()["next"]
    assert c.post("/dash/data/tables/sales/query", json={"limit": 1}).json()["total"] == 3
    assert c.get("/dash/data/changes", params={"table": "nope"}).status_code == 404
