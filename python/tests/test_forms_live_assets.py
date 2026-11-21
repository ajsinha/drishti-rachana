# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""Arrow IPC and NDJSON answers, filtered and conflated subscriptions, the bridge, and the assets of <rachana-view> (the adapters'
/static); the `protocol` cases of conformance/1 cover the same on the wire (test_protocol_cases.py)."""
import json
import queue
import threading
import time

import pytest

from drishti_rachana import rows_forms
from drishti_rachana.bridge import Bridge
from drishti_rachana.server import DataHost
from drishti_rachana.subscription import merge

SCHEMA = {"name": "kv", "key": ["k"], "columns": [{"name": "k", "type": "string"}, {"name": "v", "type": "decimal"}],
          "can": ["page", "sort", "count", "arrow"]}
LIVE = {"name": "lv", "key": ["id"], "columns": [{"name": "id", "type": "string"}, {"name": "n", "type": "integer"}],
        "can": ["page", "changes", "changes.filter", "replay"]}


def host():
    h = DataHost()
    h.add_table("kv", [{"k": "b", "v": "2.50"}, {"k": "a", "v": "1.25"}, {"k": "c", "v": None}], ["k"], schema=SCHEMA, epoch="t1")
    h.add_table("lv", [{"id": "a", "n": 1}, {"id": "b", "n": 5}], ["id"], schema=LIVE, epoch="t1")
    return h


def test_ndjson_is_a_header_then_a_line_per_row():
    r = host().handle("GET", "/tables/kv/rows", {"sort": "k", "limit": "2"}, None, "application/x-ndjson")
    assert r.status == 200 and r.content_type == "application/x-ndjson"
    lines = [json.loads(x) for x in r.body.decode().splitlines()]
    assert lines[0]["columns"] == ["k", "v"] and lines[0]["total"] == 3 and "next" in lines[0]
    assert lines[1:] == [["a", "1.25"], ["b", "2.50"]]


def test_arrow_round_trips_with_pyarrow_and_is_406_when_not_offered(monkeypatch):
    pa = pytest.importorskip("pyarrow")
    h = host()
    r = h.handle("GET", "/tables/kv/rows", {"sort": "-k"}, None, "application/vnd.apache.arrow.stream")
    assert r.status == 200 and r.content_type == "application/vnd.apache.arrow.stream"
    t = pa.ipc.open_stream(r.body).read_all()
    assert t.column("k").to_pylist() == ["c", "b", "a"] and str(t.schema.field("v").type) == "decimal128(38, 2)"
    meta = {k.decode(): v.decode() for k, v in t.schema.metadata.items()}
    assert meta["rachana.table"] == "kv" and meta["rachana.epoch"] == "t1" and meta["rachana.total"] == "3"
    # a reader that also takes JSON gets JSON
    assert h.handle("GET", "/tables/kv/rows", {}, None, "application/vnd.apache.arrow.stream, application/json").content_type == "application/json"
    # a table that does not declare arrow, and a host without pyarrow, both answer 406 with rdp.query.unsupported
    h.add_table("plain", [{"k": "a"}], ["k"])
    for host_, table in ((h, "plain"),):
        r = host_.handle("GET", f"/tables/{table}/rows", {}, None, "application/vnd.apache.arrow.stream")
        assert r.status == 406 and json.loads(r.body)["error"]["operation"] == "arrow"
    monkeypatch.setattr("drishti_rachana.server.arrow_available", lambda: False)
    assert h.handle("GET", "/tables/kv/rows", {}, None, "application/vnd.apache.arrow.stream").status == 406


def test_arrow_masked_cells_and_big_integers():
    pa = pytest.importorskip("pyarrow")
    page = {"table": "m", "columns": ["n", "s"], "values": [["9223372036854775808", {"$masked": True}], [1, "x"]]}
    t = pa.ipc.open_stream(rows_forms.arrow_write(page, {"name": "m", "columns": [{"name": "n", "type": "integer"}, {"name": "s", "type": "string"}]})).read_all()
    assert str(t.schema.field("n").type) == "decimal128(38, 0)" and t.column("s$masked").to_pylist() == [True, False]
    assert t.column("s").to_pylist() == [None, "x"]


def test_filtered_subscription_restates_each_patch():
    h, out = host(), []
    sub = h.subscribe("lv", None, {"op": ">", "args": [{"col": "n"}, {"lit": 2}]}, 0, out.append)
    assert sub.recovered
    h.publish("lv", {"update": [{"id": "a", "n": 3}, {"id": "b", "n": 1}]})
    h.publish("lv", {"add": [{"id": "c", "n": 0}]})
    assert [f["data"] for f in out] == [{"table": "lv", "remove": [["b"]], "update": [{"id": "a", "n": 3}]}, {"table": "lv"}]
    assert [f["seq"] for f in out] == [1, 2]


def test_filter_needs_changes_filter_and_a_known_column():
    h = host()
    h.add_table("nf", [{"id": "a"}], ["id"], schema={"name": "nf", "key": ["id"], "columns": [{"name": "id", "type": "string"}], "can": ["page", "changes"]})
    flt = {"op": "==", "args": [{"col": "id"}, {"lit": "a"}]}
    with pytest.raises(Exception) as e:
        h.subscribe("nf", None, flt, 0, lambda f: None)
    assert e.value.code == "rdp.query.unsupported"
    with pytest.raises(Exception) as e:
        h.subscribe("lv", None, {"op": "==", "args": [{"col": "zzz"}, {"lit": 1}]}, 0, lambda f: None)
    assert e.value.code == "rdp.column.unknown"


def test_a_filtered_stream_resumes_only_from_now_and_a_conflated_one_merges():
    h = host()
    h.publish("lv", {"update": [{"id": "a", "n": 3}]})
    gap = list(h.stream({}, {"table": "lv", "after": "t1:0", "filter": {"op": ">", "args": [{"col": "n"}, {"lit": 2}]}}, heartbeat=0))
    assert len(gap) == 1 and '"recoverable": false' in gap[0]
    h2 = host()
    for p in ({"update": [{"id": "a", "n": 2}]}, {"add": [{"id": "c", "n": 9}], "update": [{"id": "a", "n": 4}]}, {"remove": [["c"]]}):
        h2.publish("lv", p)
    stop = threading.Event()
    frames = h2.stream({}, {"table": "lv", "after": "t1:0", "conflate": {"everyMs": 50}}, stop=stop, heartbeat=0.05)
    f = next(x for x in frames if x.startswith("id:"))
    stop.set()
    msg = json.loads(f.split("data: ", 1)[1])
    assert (msg["first"], msg["seq"]) == (1, 3) and msg["data"] == {"table": "lv", "remove": [["c"]], "update": [{"id": "a", "n": 4}]}


def test_merge_reset_and_keyless():
    assert merge("t", ["id"], [{"update": [{"id": "a", "n": 1}]}, {"reset": [{"id": "z", "n": 0}]}, {"update": [{"id": "z", "n": 7}]}]) == \
        {"table": "t", "reset": [{"id": "z", "n": 7}]}
    assert merge("t", [], [{"add": [{"x": 1}]}, {"add": [{"x": 2}]}]) == {"table": "t", "add": [{"x": 1}, {"x": 2}]}


def test_bridge_sub_unsub_and_arrow():
    pytest.importorskip("pyarrow")
    h, out = host(), []
    b = Bridge(h, out.append)
    r = b.handle({"rdp": 1, "t": "sub", "id": "s1", "table": "lv"})
    assert r["body"] == {"recovered": True, "version": {"epoch": "t1", "seq": 0}}
    h.publish("lv", {"update": [{"id": "a", "n": 2}]})
    assert out == [{"rdp": 1, "t": "msg", "sub": "s1", "table": "lv", "epoch": "t1", "seq": 1, "data": {"table": "lv", "update": [{"id": "a", "n": 2}]}}]
    assert b.handle({"rdp": 1, "t": "unsub", "id": "s1"})["body"] == {}
    h.publish("lv", {"update": [{"id": "a", "n": 3}]})
    assert len(out) == 1
    assert b.handle({"rdp": 1, "t": "sub", "id": "s2", "table": "lv", "after": {"epoch": "x", "seq": 1}})["body"]["recovered"] is False
    a = b.handle({"rdp": 1, "t": "query", "id": "q", "table": "kv", "accept": "arrow", "body": {}})
    assert a["t"] == "res.arrow" and isinstance(a["buffer"], str)
    assert b.handle({"rdp": 1, "t": "rows", "id": "r", "table": "lv", "accept": "arrow"})["error"]["operation"] == "arrow"
    b.close()


def test_conflation_timer_delivers_without_a_further_publish():
    h, q = host(), queue.Queue()
    sub = h.subscribe("lv", None, None, 40, q.put)
    h.publish("lv", {"update": [{"id": "a", "n": 2}]})
    h.publish("lv", {"update": [{"id": "a", "n": 3}]})
    f = q.get(timeout=2)
    assert f["seq"] == 2 and f["first"] == 1
    sub.close()
    time.sleep(0.1)


# ---- the assets -----------------------------------------------------------------------------------------------------

def test_assets_resolve_and_refuse_traversal():
    from drishti_rachana import assets
    js = assets.find("rachana/src/rachana_view.js")
    assert js and js[1].startswith("text/javascript") and b"defineRachanaView" in js[0]
    css = assets.find("vendor/drishti-elements/3.2.0-dev+282ee8a/drishti-elements.css")
    assert css and css[1].startswith("text/css")
    assert assets.find("vendor/drishti-elements/3.2.0-dev+282ee8a/css/drishti_views.css")[0].startswith(b"/*")      # a stub: the pack carries it inside
    for bad in ("../../README.md", "rachana/src/../../../README.md", "rachana/src/nope.js", "vendor/other/x.js", "", "rachana/src"):
        assert assets.find(bad) is None
    rt = assets.find("rachana/rachana.runtime.js")
    if rt:
        assert rt[1].startswith("text/javascript")


def test_the_adapters_serve_the_assets_and_the_page_uses_them(tmp_path):
    flask = pytest.importorskip("flask")
    from drishti_rachana.adapters.flask import RachanaBlueprint
    (tmp_path / "hello.sutra.yaml").write_text("rachana: 1\nsutra: hello\nversion: 1\n")
    bp = RachanaBlueprint(sutras=tmp_path)
    app = flask.Flask(__name__)
    app.register_blueprint(bp, url_prefix="/dash")
    c = app.test_client()
    page = c.get("/dash/page/hello").data.decode()
    assert "/dash/static/vendor/drishti-elements/3.2.0-dev+282ee8a/drishti-elements.js" in page and "/dash/static/rachana/src/rachana_view.js" in page
    assert "localhost/rachana-view" not in page
    r = c.get("/dash/static/rachana/src/rachana_view.js")
    assert r.status_code == 200 and r.headers["Content-Type"].startswith("text/javascript")
    assert c.get("/dash/static/vendor/drishti-elements/3.2.0-dev+282ee8a/drishti-elements.css").headers["Content-Type"].startswith("text/css")
    assert c.get("/dash/static/rachana/src/missing.js").status_code == 404
    assert c.get("/dash/static/..%2f..%2fREADME.md").status_code == 404


def test_fastapi_serves_the_assets(tmp_path):
    pytest.importorskip("fastapi")
    pytest.importorskip("httpx")
    from fastapi import FastAPI
    from fastapi.testclient import TestClient
    from drishti_rachana.adapters.fastapi import RachanaRouter
    app = FastAPI()
    app.include_router(RachanaRouter(sutras=tmp_path), prefix="/dash")
    c = TestClient(app)
    r = c.get("/dash/static/rachana/src/data_client.js")
    assert r.status_code == 200 and r.headers["content-type"].startswith("text/javascript")
    assert c.get("/dash/static/rachana/src/missing.js").status_code == 404
