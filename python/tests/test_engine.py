# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""Smoke test of the V8 path (W-1): the JavaScript bundle in Python compiles, lints and evaluates. Skipped without mini-racer
or a built bundle (python3 tools/js/build_js.py)."""
import pytest

pytest.importorskip("py_mini_racer")
from drishti_rachana.engine import BUNDLE_DIR, Rachana  # noqa: E402

pytestmark = pytest.mark.skipif(not (BUNDLE_DIR / "rachana.tools.js").is_file(), reason="bundle not built (tools/js/build_js.py)")

GOOD = """rachana: 1
sutra: trade
version: 1
title: { id: $.id }
panels:
  - { id: terms, kind: kv, columns: [ { label: Notional, bind: $.notional, fmt: amount0 } ] }
"""


@pytest.fixture(scope="module")
def rachana():
    return Rachana()


def test_evaluates_like_the_jvm(rachana):
    assert rachana.evaluate("$.a * 2", record={"a": 21})["text"] == "42"
    assert rachana.evaluate("round(2.675, 2)")["text"] == "2.68"
    assert rachana.evaluate("5e-324 * 1")["text"] == "4.9E-324"     # Java's shortest text, not the engine's


def test_compiles_and_lints(rachana):
    assert rachana.check(GOOD) == {"ok": True, "problems": []}
    bad = rachana.check(GOOD.replace("$.notional", "nosuch($.notional)"))
    assert bad["ok"] is False and bad["problems"][0]["code"].startswith("RCH-")


def test_runtime_bundle_and_version():
    r = Rachana(bundle="runtime")
    assert r.version() == "0.12.0"
    assert r.check(GOOD)["ok"] is True


def test_a_bundle_that_is_not_the_recorded_one_is_refused(tmp_path):
    import shutil

    from drishti_rachana.engine import BundleHashError
    shutil.copy(BUNDLE_DIR / "rachana.tools.js", tmp_path / "rachana.tools.js")
    shutil.copy(BUNDLE_DIR / "SHA256SUMS", tmp_path / "SHA256SUMS")
    Rachana(bundle_dir=tmp_path)                       # untouched: runs
    with (tmp_path / "rachana.tools.js").open("a", encoding="utf-8") as f:
        f.write("\n// altered\n")
    with pytest.raises(BundleHashError):
        Rachana(bundle_dir=tmp_path)


def test_a_runaway_call_is_cancelled():
    r = Rachana(timeout=1.0)
    with pytest.raises(Exception, match="(?i)timeout|timed out"):
        r._ctx.eval("while (true) {}", timeout_sec=1.0)
