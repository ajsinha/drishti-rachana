#!/usr/bin/env python3
# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""The worked example: an agent builds a risk mash-up board - VaR and ES for the firm, VaR by desk, exposure by counterparty, limit
use and what changed since yesterday - from the fragment libraries, and publishes it live. All data is synthetic (risk_data.py).

The script plays the agent's part deterministically (no model), calling the four tools exactly as an agent does through SAJHA:

  1. learn the tables' SHAPE from the data tool's outputSchema (column names and types; no rows)
  2. search_fragments per need, with the shapes      -> fragments + ready uses mapped to the columns
  3. get_fragment for the one it is unsure of         -> the whole interface
  4. compose the Sutra (a first draft with one wrong column, as a model may write it)
  5. check_sutra -> RGK-112 with the expected names -> fix the one line -> check_sutra -> ok + check_token
  6. publish_ui with the tables' SOURCES (the data tool and its arguments): the kit fetches the rows, the page gets them
  7. (--runs N) publish again with the next intraday run: the open page updates in place

    python3 tools/sajha/example/risk_mashup.py              # prints the board URL; --serve keeps the board server running
    python3 tools/sajha/example/risk_mashup.py --serve --runs 5 --every 20

Inside SAJHA the same calls go through MCP and 'risk_data' is a real tool on the kit's allow-list (RACHANA_GENUI_DATA_TOOLS).
"""
from __future__ import annotations

import argparse
import importlib.util
import json
import os
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
KIT = HERE.parent
sys.path.insert(0, str(KIT))
sys.path.insert(0, str(HERE))
if (KIT.parents[1] / "python" / "src").is_dir():
    sys.path.insert(0, str(KIT.parents[1] / "python" / "src"))       # a checkout: the drishti_rachana package beside the kit

import risk_data  # noqa: E402
from rachana_genui import rows as kit_rows  # noqa: E402
from rachana_genui.settings import Settings, use  # noqa: E402

OUT = HERE / "risk-mashup.sutra.yaml"
DATA_TOOL = "risk_data"


def tool(name: str):
    spec = importlib.util.spec_from_file_location(name, KIT / "tools" / f"{name}.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    cls = next(v for v in vars(mod).values() if isinstance(v, type) and getattr(v, "TOOL", "") == name)
    return cls({})


def say(step: str, detail: str = "") -> None:
    print(f"[agent] {step}" + (f"\n        {detail}" if detail else ""))


def use_block(use: dict, extra: dict) -> str:
    """One panels: item as YAML text (flow mappings, as an agent writes them)."""
    w = {**(use.get("with") or {}), **extra}
    return f"  - use: {use['use']}\n    with: {json.dumps(w)}\n"


def compose(uses: dict[str, dict]) -> str:
    return (
        "rachana: 1.2\n"
        "sutra: risk-mashup\n"
        "version: 1\n"
        "doc:\n"
        "  summary: Firm VaR and ES, VaR by desk, exposure by counterparty, limit use and what changed since yesterday.\n"
        "  description: >-\n"
        "    A risk mash-up composed by an agent from the core and finance libraries over four tables of the day. Figures are\n"
        "    supplied by the risk systems; nothing is computed on the board but changes and shares.\n"
        "  category: market-risk\n"
        "libraries: [core@1, finance@1]\n"
        "title: { id: \"'Risk mash-up'\" }\n"
        "data:\n"
        "  firm_risk: { table: firm_risk, as: record }\n"
        "  var_by_desk: { table: var_by_desk }\n"
        "  exposure_by_cpty: { table: exposure_by_cpty }\n"
        "  limit_use: { table: limit_use }\n"
        "panels:\n"
        + use_block(uses["tiles"], {"var-label": "VaR 99% 1d", "es-label": "ES 97.5% 1d"})
        + use_block(uses["by_desk"], {"title": "VaR by desk", "fmt": "amount0"})
        + use_block(uses["limits"], {"title": "Limit use"})
        + use_block(uses["cpty"], {"title": "Exposure by counterparty"})
        + use_block(uses["changed"], {"title": "What changed since yesterday", "name-label": "Desk", "first-label": "VaR",
                                      "second-label": "Change", "first-fmt": "amount0", "second-fmt": "signed0"})
    )


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="the risk mash-up example")
    ap.add_argument("--serve", action="store_true", help="keep the board server running")
    ap.add_argument("--runs", type=int, default=1, help="intraday runs to publish (each updates the open page)")
    ap.add_argument("--every", type=float, default=15.0, help="seconds between runs")
    ap.add_argument("--port", type=int, default=int(os.environ.get("RACHANA_GENUI_PORT", "8765")))
    ap.add_argument("--host", default=os.environ.get("RACHANA_GENUI_HOST", "127.0.0.1"))
    a = ap.parse_args(argv)

    s = Settings.load()
    s.host, s.port, s.public_url = a.host, a.port, ("" if a.port == 0 else f"http://{a.host}:{a.port}")
    s.data_tools = [DATA_TOOL]
    if os.environ.get("RACHANA_GENUI_REQUIRE_ENGINE") is None:
        try:
            import py_mini_racer  # noqa: F401
        except ImportError:
            s.require_engine = False          # a checkout without the V8 extra: the kit's own checks only (said in the result)
    use(s)
    kit_rows.register_tool(DATA_TOOL, risk_data.tool)
    search, get, check, publish = (tool(n) for n in ("search_fragments", "get_fragment", "check_sutra", "publish_ui"))

    # 1. the shapes, from the data tool's outputSchema: names and types only
    shapes = {n: dict(sch) for n, sch in risk_data.SCHEMAS.items()}
    say("tables in hand (from the data tool's outputSchema)", ", ".join(f"{t}({len(s_['columns'])} columns)" for t, s_ in shapes.items()))
    as_data = shapes                                  # the Sutra reads each under its own name ($.firm_risk, $.var_by_desk, ...)

    # 2. search per need, with the shapes
    needs = {"tiles": ("var es tiles at the head of a risk screen", {"firm_risk": as_data["firm_risk"]}),
             "by_desk": ("rank desks by value at risk", {"var_by_desk": as_data["var_by_desk"]}),
             "limits": ("many limits utilisation breaches first", {"limit_use": as_data["limit_use"]}),
             "cpty": ("where exposure concentrates by counterparty and sector", {"exposure_by_cpty": as_data["exposure_by_cpty"]}),
             "changed": ("measure with signed change by name", {"var_by_desk": as_data["var_by_desk"]})}
    uses: dict[str, dict] = {}
    for need, (query, tables) in needs.items():
        found = search.execute({"query": query, "tables": tables, "limit": 3})["results"]
        top = found[0]
        say(f"search '{query}'", f"-> {top['id']} (coverage {top.get('fit', {}).get('coverage')}): {top['doc']['summary']}")
        uses[need] = top["use"]

    # 3. the interface of one, to set its labels right
    info = get.execute({"id": uses["changed"]["use"], "include_source": False, "include_example": False})
    say(f"get_fragment {info['id']}", "params: " + ", ".join(f"{p}{'*' if d.get('required') else ''}" for p, d in info["params"].items()))
    second = info["params"]["rows"]["columns"]["second"]
    say("its optional column 'second'", f"{second.get('doc')}: the agent maps it to VAR_CHANGE")
    uses["changed"]["with"]["rows"]["columns"]["second"] = "VAR_CHANGE"

    # 4. a first draft, with the slip a model makes: a column name from memory instead of the schema
    uses["tiles"]["with"]["es"] = "$.firm_risk.ES_99"
    draft = compose(uses)
    # 5. check, fix the line named, check again
    res = check.execute({"sutra": draft, "tables": as_data})
    say(f"check_sutra attempt {res['attempt']['n']}: ok={res['ok']}", res["summary"])
    text = draft
    for p in res["problems"]:
        if p["code"] == "RGK-112" and p.get("expected"):
            wrong = p["message"].split("'")[3]
            right = next((e for e in p["expected"] if e.startswith(wrong.split("_")[0])), p["expected"][0])
            text = text.replace(f"$.firm_risk.{wrong}", f"$.firm_risk.{right}")
            say("fix", f"{wrong} -> {right} (from the problem's expected names)")
    res = check.execute({"sutra": text, "tables": as_data})
    say(f"check_sutra attempt {res['attempt']['n']}: ok={res['ok']} (engine: {res['engine']})", res["summary"])
    if not res["ok"]:
        print(json.dumps(res["problems"], indent=1))
        return 1
    OUT.write_text("# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.\n"
                   "# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.\n"
                   "# Synthetic demo content: composed by example/risk_mashup.py (regenerate it, do not edit).\n" + text, encoding="utf-8")

    # 6. publish with SOURCES: the kit calls the data tool as the user; the rows never come back through the agent
    board = None
    for run in range(1, a.runs + 1):
        sources = {t: {"tool": DATA_TOOL, "arguments": {"view": t, "as_of": "2026-11-20", "run": 6 + run}, "path": "rows"}
                   for t in shapes}
        out = publish.execute({"sutra": text, "check_token": res["check_token"], "tables": sources, "title": "Risk mash-up",
                               **({"board": board} if board else {})})
        if not out["published"]:
            print(json.dumps(out, indent=1))
            return 1
        board = out["board"]
        say(f"publish_ui run {run}", f"{out['summary']}\n        open {out['url']}")
        if run < a.runs:
            time.sleep(a.every)
    if a.serve:
        say("serving the board; Ctrl-C to stop")
        try:
            while True:
                time.sleep(3600)
        except KeyboardInterrupt:
            pass
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
