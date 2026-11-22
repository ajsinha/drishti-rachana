# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""The four tools end to end, on their own: search by text and by shape, get, the check-fix loop and its safety rules, publish
(rows fetched by the kit, never given by the agent), the board page and its live stream."""
import json
import urllib.request

import risk_data
from rachana_genui import checker

LIMITS = {"limit_use": risk_data.SCHEMAS["limit_use"]}
SUTRA = """rachana: 1.2
sutra: limits-board
version: 1
doc:
  summary: Limit use across the firm, breaches first.
  description: The limits of the day with what is used, the headroom and the share used.
libraries: [finance@1]
title: { id: "'Limits'" }
data:
  limit_use: { table: limit_use }
panels:
  - use: finance/limit-table@1
    with: { rows: { from: $.limit_use, columns: { name: LIMIT_NAME, limit: LIMIT_USD, used: USED_USD } } }
"""


def test_search_by_text_and_by_shape(tools):
    r = tools["search_fragments"].execute({"query": "var es tiles"})["results"]
    assert r[0]["id"] == "finance/var-tiles@1" and r[0]["doc"]["use-when"]
    r = tools["search_fragments"].execute({"tables": LIMITS, "query": "limits"})["results"][0]
    assert r["id"] == "finance/limit-table@1" and r["fit"]["coverage"] == 1.0
    assert r["use"]["with"]["rows"] == {"from": "$.limit_use", "columns": {"name": "LIMIT_NAME", "limit": "LIMIT_USD",
                                        "used": "USED_USD", "owner": "OWNER"}}
    rec = {"firm": {"as": "record", "columns": [{"name": "VAR_99", "type": "number"}, {"name": "VAR_99_PREV", "type": "number"}]}}
    r = tools["search_fragments"].execute({"tables": rec, "query": "var tiles"})["results"][0]
    assert r["use"]["with"] == {"var": "$.firm.VAR_99", "previous-var": "$.firm.VAR_99_PREV"}


def test_get_fragment_and_nearest(tools):
    g = tools["get_fragment"].execute({"id": "limit-table"})
    assert g["found"] and g["id"] == "finance/limit-table@1" and "fragment: limit-table" in g["source"] and g["example_sutra"]
    assert g["params"]["rows"]["columns"]["used"]["type"] == "number" and g["libraries_line"] == "finance@1"
    n = tools["get_fragment"].execute({"id": "finance/limit-tabel"})
    assert not n["found"] and "finance/limit-table" in n["nearest"]


def test_check_finds_the_wrong_column_then_passes(tools):
    bad = tools["check_sutra"].execute({"sutra": SUTRA.replace("used: USED_USD", "used: USED"), "tables": LIMITS})
    assert not bad["ok"] and "check_token" not in bad
    p = next(p for p in bad["problems"] if p["code"] == "RGK-112")
    assert p["path"] == "/panels/0/with/rows/columns/used" and p["range"]["start"]["line"] == 13 and "USED_USD" in p["expected"]
    good = tools["check_sutra"].execute({"sutra": SUTRA, "tables": LIMITS})
    assert good["ok"] and good["check_token"] and good["plan"]["fragments"] == ["finance/limit-table@1"]
    assert good["engine"] == "unavailable" and any(p["code"] == "RGK-150" for p in good["problems"])


def test_check_refuses_what_an_agent_may_not_write(tools, kit):
    cases = {
        "RGK-110": SUTRA.replace("{ table: limit_use }", "{ url: https://elsewhere.example/x.json }"),
        "RGK-111": SUTRA.replace("{ table: limit_use }", "{ table: secret_table }"),
        "RGK-121": SUTRA.replace("finance/limit-table@1", "finance/limit-tabel@1"),
        "RGK-120": SUTRA.replace("libraries: [finance@1]", "libraries: [core@1]"),
        "RGK-130": SUTRA.replace("Limit use across", "<script>alert(1)</script> Limit use across"),
        "RGK-101": SUTRA + "#" * kit.max_sutra_bytes,
        "RCH-2001": "rachana: 1.2\nsutra: [unclosed\n",
    }
    for code, text in cases.items():
        r = tools["check_sutra"].execute({"sutra": text, "tables": LIMITS})
        assert not r["ok"] and code in {p["code"] for p in r["problems"]}, code


def test_attempts_are_bounded(tools, kit):
    bad = SUTRA.replace("used: USED_USD", "used: USED")
    for _ in range(kit.max_attempts):
        assert not tools["check_sutra"].execute({"sutra": bad, "tables": LIMITS}).get("stop")
    r = tools["check_sutra"].execute({"sutra": bad, "tables": LIMITS})
    assert r["stop"] and r["problems"][0]["code"] == "RGK-140"


def _get(url, timeout=5):
    with urllib.request.urlopen(url, timeout=timeout) as r:
        return r.status, dict(r.headers), r.read()


def test_publish_needs_the_check_and_fetches_the_rows_itself(tools, kit):
    pub = tools["publish_ui"]
    src = {"limit_use": {"tool": "risk_data", "arguments": {"view": "limit_use"}, "path": "rows"}}
    assert pub.execute({"sutra": SUTRA, "check_token": "x", "tables": src})["error"]["code"] == "RGK-170"
    token = tools["check_sutra"].execute({"sutra": SUTRA, "tables": LIMITS})["check_token"]
    assert pub.execute({"sutra": SUTRA + "\n", "check_token": token, "tables": src})["error"]["code"] == "RGK-170"
    assert pub.execute({"sutra": SUTRA, "check_token": token, "tables": {"limit_use": {"tool": "other_tool"}}})["error"]["code"] == "RGK-162"
    many = {"limit_use": {"rows": [{"LIMIT_NAME": "x", "LIMIT_USD": 1, "USED_USD": 1}] * (kit.max_inline_rows + 1)}}
    assert pub.execute({"sutra": SUTRA, "check_token": token, "tables": many})["error"]["code"] == "RGK-110"
    wrong = {"limit_use": {"tool": "risk_data", "arguments": {"view": "var_by_desk"}, "path": "rows"}}
    assert pub.execute({"sutra": SUTRA, "check_token": token, "tables": wrong})["error"]["code"] == "RGK-171"
    out = pub.execute({"sutra": SUTRA, "check_token": token, "tables": src, "title": "Limits"})
    assert out["published"] and out["tables"] == {"limit_use": {"rows": 8}} and out["version"] == 1
    events = [json.loads(l) for l in open(kit.audit_file)]
    assert [e["event"] for e in events][-1] == "rachana.genui.publish" and "rows" not in json.dumps(events[-1]["tables"]["limit_use"]["source"])

    status, headers, body = _get(out["url"])
    assert status == 200 and b"<rachana-view" in body and b"<script>" not in body
    assert "script-src 'self'" in headers["Content-Security-Policy"] and "unsafe-eval" not in headers["Content-Security-Policy"]
    status, _, body = _get(out["url"] + "/board.json")
    board = json.loads(body)
    assert board["sutra"] == SUTRA and len(board["tables"]["limit_use"]["rows"]) == 8 and board["libraries"] == ["finance"]
    assert _get(kit.public_url + "/static/rachana/src/rachana_view.js")[0] == 200
    assert _get(kit.public_url + "/spa/app.js")[0] == 200
    lib = json.loads(_get(kit.public_url + "/libraries/finance.json")[2])
    assert "rachana_libs/finance/fragments/limit-table.v1.fragment.yaml" in lib

    # a second publish to the same board reaches an open stream
    r = urllib.request.urlopen(out["url"] + "/events", timeout=5)
    first = _event(r)
    assert first["version"] == 1
    again = pub.execute({"sutra": SUTRA, "check_token": token, "tables": {"limit_use": {"tool": "risk_data", "arguments":
                         {"view": "limit_use", "run": 9}, "path": "rows"}}, "board": out["board"]})
    assert again["version"] == 2 and _event(r)["version"] == 2
    r.close()


def _event(resp):
    data = None
    while True:
        line = resp.readline().decode().rstrip("\n")
        if line.startswith("data: "):
            data = json.loads(line[6:])
        elif line == "" and data is not None:
            return data


def test_tables_described_from_rows_carry_names_and_types_only():
    d = checker.describe_tables({"t": {"rows": [{"A": 1, "B": "x", "C": "2026-11-20"}]}})
    assert d == {"t": {"columns": [{"name": "A", "type": "number"}, {"name": "B", "type": "text"}, {"name": "C", "type": "date"}]}}
