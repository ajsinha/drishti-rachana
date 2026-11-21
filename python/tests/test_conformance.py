# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
import json
from pathlib import Path

import pytest

from drishti_rachana.cli import main
from drishti_rachana.conformance import Adapter, NoneAdapter, Options, run
from drishti_rachana.conformance.canonical import dumps, number_text
from drishti_rachana.conformance.compare import diff

CASES = Path(__file__).resolve().parents[2] / "conformance" / "1"


def test_number_text_follows_format_9():
    assert [number_text(x) for x in (2.0, -7.0, 0.5, 0.1 + 0.2, 100.25, 0.001, 1e20, 1e15, 12345678.5, 1e-4, 4.9e-324)] \
        == ["2", "-7", "0.5", "0.30000000000000004", "100.25", "0.001", "1.0E20", "1.0E15", "1.23456785E7", "1.0E-4",
            "4.9E-324"]
    assert number_text(-0.0) == "0"


def test_canonical_json():
    assert dumps({"b": 1, "a": [True, None, "x\u007f/\n "]}) == '{"b":1,"a":[true,null,"x\\u007f/\\n "]}'
    assert dumps(float("nan")) == '{"$num":"NaN"}'


def test_compare_rules():
    assert diff({"value": 3, "type": "number"}, {"value": 3, "type": "number", "text": "3"}) is None
    assert diff({"at": {"/a/0": 1}}, {"a": [1]}) is None
    assert diff({"at": {"/a/1": 1}}, {"a": [1]}) is not None
    assert diff({"problems": [{"code": "RCH-1"}]}, {"problems": [{"code": "RCH-1", "message": "m"}]}) is None
    assert diff({"value": 1.0}, {"value": 1.0000000001}, tolerance={"abs": 1e-6}) is None
    assert diff({"steps": [{"status": 200, "body": {"a": 1}}]},
                {"steps": [{"status": 200, "body": {"a": 1, "b": 2}}]}) is None


def test_none_adapter_marks_everything_unsupported(tmp_path):
    pytest.importorskip("yaml")
    out, summ = tmp_path / "r" / "none.jsonl", tmp_path / "r" / "none.summary.json"
    code = run(NoneAdapter(), Options(cases=CASES, out=out, summary=summ))
    assert code == 0
    lines = [json.loads(l) for l in out.read_text(encoding="utf-8").splitlines()]
    assert lines and all(l["status"] == "unsupported" and l["actual"] is None for l in lines)
    assert [l["case"] for l in lines][0].startswith("edit/")
    s = json.loads(summ.read_text(encoding="utf-8"))
    assert s["unsupported"] == s["cases"] == len(lines) and s["pass"] == 0 and s["runtime"] == "none"


def test_adapter_runs_and_exit_codes(tmp_path):
    pytest.importorskip("yaml")

    class Wrong(Adapter):
        name = "wrong"
        kinds = frozenset({"grammar"})

        def run_case(self, case):
            return {"accepted": True}

    out, summ = tmp_path / "w.jsonl", tmp_path / "w.summary.json"
    code = run(Wrong(), Options(cases=CASES, out=out, summary=summ, only=("grammar/**",)), stderr=open(tmp_path / "e", "w"))
    s = json.loads(summ.read_text(encoding="utf-8"))
    assert code in (0, 1, 3) and s["cases"] > 0 and s["pass"] + s["fail"] + s["unsupported"] == s["cases"]
    assert (code == 0) == (s["fail"] == 0)


def test_cli_none_and_usage_error(tmp_path):
    pytest.importorskip("yaml")
    assert main(["conformance", "run", "--cases", str(CASES), "--out", str(tmp_path / "o.jsonl"),
                 "--summary", str(tmp_path / "s.json")]) == 0
    assert main(["conformance", "run", "--cases", str(tmp_path / "nope"), "--out", str(tmp_path / "x.jsonl")]) == 2
    assert not (tmp_path / "x.jsonl").exists()
