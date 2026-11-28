# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""The live-orders example under Flask and FastAPI, in process: the page, the runtime bundle, the element source, the Sutra and the data
answer 200 (so a missing bundle or a broken adapter never goes unnoticed again)."""
import json
import sys
from pathlib import Path

import pytest

from drishti_rachana.engine import bundle_sha256

ROOT = Path(__file__).resolve().parents[2]
LIVE = ROOT / "examples" / "live-orders"
PATHS = ["/dash/", "/dash/page/orders", "/dash/static/rachana/rachana.runtime.js", "/dash/static/rachana/src/rachana_view.js",
         "/dash/sutras/orders.sutra.yaml"]


@pytest.fixture(scope="module", autouse=True)
def live_on_path():
    sys.path.insert(0, str(LIVE))
    yield
    sys.path.remove(str(LIVE))
    for m in ("flask_app", "fastapi_app", "gen_orders"):
        sys.modules.pop(m, None)


def test_runtime_bundle_is_committed_and_matches_its_hash():
    f = ROOT / "python" / "src" / "drishti_rachana" / "bundle" / "rachana.runtime.js"
    assert f.is_file(), "rachana.runtime.js must be committed (python3 tools/js/build_js.py rebuilds it)"
    bundle_sha256(f)
    bundle_sha256(f.with_name("rachana.tools.js"))


def _check(get):
    for p in PATHS:
        r = get(p)
        assert r.status_code == 200, p
    page = get("/dash/page/orders").text
    assert "rachana-view" in page and "rachana.runtime.js" in page
    assert len(get("/dash/static/rachana/rachana.runtime.js").content) > 1_000_000
    rows = get("/dash/data/tables/orders/rows?shape=objects&limit=5")
    assert rows.status_code == 200, rows.text[:200]
    assert len(json.loads(rows.text)["rows"]) == 5


def test_flask_live_orders():
    import flask_app
    c = flask_app.app.test_client()
    _check(lambda p: type("R", (), {"status_code": (r := c.get(p)).status_code, "text": r.get_data(as_text=True), "content": r.get_data()})())


def test_fastapi_live_orders():
    from fastapi.testclient import TestClient
    import fastapi_app
    _check(TestClient(fastapi_app.app).get)
