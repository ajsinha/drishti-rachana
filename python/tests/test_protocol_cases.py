# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""The `protocol` conformance cases (conformance/1/protocol) against the Python DataHost: the same cases tools/conformance/protocol_check.py
runs against the JVM reference server (`--reference`) and against this host (`--python`)."""
import importlib.util, json, sys, threading
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[2]
pytest.importorskip("yaml")
sys.path.insert(0, str(ROOT / "tools" / "conformance"))
import protocol_check as pc  # noqa: E402
from rachana_conformance import load_files, merged  # noqa: E402
from drishti_rachana.testhost import TestHost  # noqa: E402


# The frozen example that sorts a table without `can: sort` (PROTOCOL 4 refuses it): it fails on the JVM reference too;
# protocol/host#bridge-rows-of-a-sortable-table is the corrected case.
HAS_ARROW = importlib.util.find_spec("pyarrow") is not None
KNOWN = {"protocol/examples/bridge#bridge-rows-request-answered-once"}


def _cases():
    out = []
    for _p, suite, doc in load_files(ROOT / "conformance" / "1"):
        for c in doc["cases"]:
            c = merged(doc, c)
            cid = f"{suite}#{c['id']}"
            if c["kind"] == "protocol":
                marks = [pytest.mark.xfail(strict=True, reason="PROTOCOL 4")] if cid in KNOWN else []
                if pc.is_live(c):
                    marks.append(pytest.mark.skip(reason="a live table's case: the Python host serves no live tables (LIVE_TABLES LT-14)"))
                if not HAS_ARROW and "arrow" in json.dumps(c, default=str).lower():
                    marks.append(pytest.mark.skip(reason="the Arrow cases need pyarrow (pip install 'drishti-rachana[arrow]')"))
                out.append(pytest.param(c, id=cid, marks=marks))
    return out


@pytest.fixture(scope="module")
def host():
    h = TestHost(0)
    threading.Thread(target=h.serve_forever, daemon=True).start()
    yield pc.Host(h.url)
    h.close()


@pytest.mark.parametrize("case", _cases())
def test_protocol_case(host, case):
    status, why, _actual = pc.run_case(host, case)
    assert status == "pass", "; ".join(why)
