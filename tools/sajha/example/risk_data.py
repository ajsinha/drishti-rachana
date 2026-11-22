# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""Synthetic risk data for the example: what a SAJHA data tool (a DuckDB query, a Drishti Data Protocol read, a REST tool) would
return. Every name and figure is invented. ``SCHEMAS`` is what the tool's outputSchema tells an agent: column names and types,
never rows."""
from __future__ import annotations

import random

DESKS = ["Rates", "Credit", "FX", "Equities", "Commodities", "Structured"]
SECTORS = {"Alpha Bank": "Banks", "Beta Capital": "Banks", "Gamma Energy": "Energy", "Delta Shipping": "Industrials",
           "Epsilon Telecom": "Telecoms", "Zeta Retail": "Consumer", "Eta Utilities": "Utilities", "Theta Mining": "Materials",
           "Iota Insurance": "Insurance", "Kappa Motors": "Industrials", "Lambda Pharma": "Healthcare", "Mu Sovereign Fund": "Sovereign"}

SCHEMAS = {
    "firm_risk": {"as": "record", "columns": [{"name": "AS_OF", "type": "date"}, {"name": "VAR_99", "type": "number"},
                  {"name": "VAR_99_PREV", "type": "number"}, {"name": "ES_975", "type": "number"}, {"name": "VAR_LIMIT", "type": "number"}]},
    "var_by_desk": {"columns": [{"name": "DESK", "type": "text"}, {"name": "VAR_99", "type": "number"},
                    {"name": "VAR_99_PREV", "type": "number"}, {"name": "VAR_CHANGE", "type": "number"}]},
    "exposure_by_cpty": {"columns": [{"name": "COUNTERPARTY", "type": "text"}, {"name": "SECTOR", "type": "text"},
                         {"name": "EXPOSURE_USD", "type": "number"}, {"name": "PNL_USD", "type": "number"}]},
    "limit_use": {"columns": [{"name": "LIMIT_NAME", "type": "text"}, {"name": "LIMIT_USD", "type": "number"},
                  {"name": "USED_USD", "type": "number"}, {"name": "OWNER", "type": "text"}]},
}


def rows(view: str, as_of: str = "2026-11-20", seed: int = 7) -> list[dict]:
    """The rows of one view for one day; a later ``seed`` is the next intraday run (the figures move)."""
    r = random.Random(f"{view}-{as_of}-{seed}")
    desk = {d: round(r.uniform(0.6, 4.2) * 1e6, -3) for d in DESKS}
    prev = {d: round(v * r.uniform(0.85, 1.12), -3) for d, v in desk.items()}
    if view == "var_by_desk":
        return [{"DESK": d, "VAR_99": desk[d], "VAR_99_PREV": prev[d], "VAR_CHANGE": desk[d] - prev[d]} for d in DESKS]
    if view == "firm_risk":
        firm = round(sum(desk.values()) * 0.62, -3)          # diversified: a VaR never adds up across desks
        return [{"AS_OF": as_of, "VAR_99": firm, "VAR_99_PREV": round(firm * r.uniform(0.9, 1.08), -3),
                 "ES_975": round(firm * 1.27, -3), "VAR_LIMIT": 12_000_000}]
    if view == "exposure_by_cpty":
        return [{"COUNTERPARTY": c, "SECTOR": s, "EXPOSURE_USD": round(r.uniform(2, 60) * 1e6, -4),
                 "PNL_USD": round(r.uniform(-1.5, 1.5) * 1e5, -2)} for c, s in SECTORS.items()]
    if view == "limit_use":
        out = []
        for name, lim, owner in [("VaR firm", 12e6, "Market Risk"), ("VaR Rates", 4.5e6, "Rates"), ("VaR Credit", 3.5e6, "Credit"),
                                 ("Stress firm", 60e6, "Market Risk"), ("Alpha Bank exposure", 55e6, "Credit Risk"),
                                 ("Banks sector", 120e6, "Credit Risk"), ("FX net open", 25e6, "FX"), ("Issuer Theta Mining", 30e6, "Credit Risk")]:
            out.append({"LIMIT_NAME": name, "LIMIT_USD": lim, "USED_USD": round(lim * r.uniform(0.35, 1.08), -3), "OWNER": owner})
        return out
    raise ValueError(f"no view '{view}' (one of {', '.join(SCHEMAS)})")


def tool(arguments: dict) -> dict:
    """The data tool's call: ``{view, as_of?, run?}`` -> ``{rows: [...]}`` (registered as 'risk_data' outside SAJHA)."""
    return {"rows": rows(str(arguments["view"]), str(arguments.get("as_of", "2026-11-20")), int(arguments.get("run", 7)))}
