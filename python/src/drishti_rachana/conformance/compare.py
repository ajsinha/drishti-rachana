# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""Comparing expected and actual (FORMAT 6, 7). Returns None on a match, else the first difference as text.

This compares documents a runtime produced; it contains no language semantics.
"""
from __future__ import annotations

import math
from typing import Any

from .canonical import dumps

_PROBLEM_EXACT = ("severity", "path", "file")


def _num(v: Any) -> bool:
    return isinstance(v, (int, float)) and not isinstance(v, bool)


def _pointer(doc: Any, ptr: str) -> tuple[bool, Any]:
    cur = doc
    if ptr == "":
        return True, cur
    for tok in ptr.split("/")[1:]:
        tok = tok.replace("~1", "/").replace("~0", "~")
        if isinstance(cur, list) and tok.isdigit() and int(tok) < len(cur):
            cur = cur[int(tok)]
        elif isinstance(cur, dict) and tok in cur:
            cur = cur[tok]
        else:
            return False, None
    return True, cur


def subset(exp: Any, act: Any) -> bool:
    if isinstance(exp, dict):
        return isinstance(act, dict) and all(k in act and subset(v, act[k]) for k, v in exp.items())
    if isinstance(exp, list):
        return isinstance(act, list) and len(exp) == len(act) and all(subset(e, a) for e, a in zip(exp, act))
    return dumps(exp) == dumps(act)


def _problem_diff(exp: dict, act: dict) -> str | None:
    if exp.get("code") != act.get("code"):
        return f"code: expected {exp.get('code')!r}, actual {act.get('code')!r}"
    if "severity" not in exp and act.get("severity", "error") != "error":
        return f"severity: expected 'error', actual {act.get('severity')!r}"
    for k in _PROBLEM_EXACT:
        if k in exp and dumps(exp[k]) != dumps(act.get(k)):
            return f"{k}: expected {dumps(exp[k])}, actual {dumps(act.get(k))}"
    if "message" in exp and exp["message"] != act.get("message"):
        return f"message: expected {exp['message']!r}, actual {act.get('message')!r}"
    if "messageStart" in exp and not str(act.get("message", "")).startswith(exp["messageStart"]):
        return f"message: expected to start with {exp['messageStart']!r}"
    for k in ("range", "expr"):
        if k in exp and not subset(exp[k], act.get(k)):
            return f"{k}: expected {dumps(exp[k])}, actual {dumps(act.get(k))}"
    for k in ("expected", "more", "fix"):
        if k in exp and dumps(exp[k]) != dumps(act.get(k)):
            return f"{k}: expected {dumps(exp[k])}, actual {dumps(act.get(k))}"
    return None


def problems_diff(exp: list, act: Any) -> str | None:
    if not isinstance(act, list) or len(exp) != len(act):
        return f"problems: expected {len(exp)}, actual {len(act) if isinstance(act, list) else 'none'}"
    for i, (e, a) in enumerate(zip(exp, act)):
        d = _problem_diff(e, a if isinstance(a, dict) else {})
        if d:
            return f"problems[{i}].{d}"
    return None


def _tolerant(exp: Any, act: Any, tol: dict) -> bool:
    if not (_num(exp) and _num(act)) or any(map(lambda v: isinstance(v, float) and not math.isfinite(v), (exp, act))):
        return False
    delta = abs(act - exp)
    return ("abs" in tol and delta <= tol["abs"]) or ("rel" in tol and delta <= tol["rel"] * abs(exp))


def diff(expect: dict, actual: dict | None, *, tolerance: dict | None = None) -> str | None:
    """First difference between ``expect`` and the actual document, or None when the case passes."""
    if actual is None:
        return "no actual document"
    for field, exp in expect.items():
        if field == "tolerance":
            continue
        if field in ("problems",) and isinstance(exp, list):
            d = problems_diff(exp, actual.get("problems"))
        elif field == "error" and isinstance(exp, dict):
            d = problems_diff([exp], [actual["error"]]) if isinstance(actual.get("error"), dict) else "error: none"
        elif field == "at":
            d = None
            for ptr, want in exp.items():
                ok, got = _pointer(actual, ptr)
                if not ok or dumps(want) != dumps(got):
                    d = f"at {ptr}: expected {dumps(want)}, actual {dumps(got) if ok else 'nothing'}"
                    break
        elif field == "steps":
            d = _steps_diff(exp, actual.get("steps"))
        elif field == "value" and tolerance and _tolerant(exp, actual.get("value"), tolerance):
            d = None
        else:
            d = None if field in actual and dumps(exp) == dumps(actual[field]) else (
                f"{field}: expected {dumps(exp)}, actual {dumps(actual.get(field))}")
        if d:
            return d
    return None


def _steps_diff(exp: list, act: Any) -> str | None:
    if not isinstance(act, list) or len(exp) != len(act):
        return "steps: length differs"
    for i, (e, a) in enumerate(zip(exp, act)):
        if "body" in e or "status" in e:                       # protocol step
            if "status" in e and e["status"] != a.get("status"):
                return f"steps[{i}].status: expected {e['status']}, actual {a.get('status')}"
            if "body" in e and not subset(e["body"], a.get("body")):
                return f"steps[{i}].body: expected {dumps(e['body'])}"
        else:                                                  # patch step: frames and/or at
            d = diff({k: v for k, v in e.items() if k in ("frames", "at")}, a)
            if d:
                return f"steps[{i}].{d}"
    return None
