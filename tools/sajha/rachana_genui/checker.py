# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""The check of an agent's Sutra: the kit's safety rules (RGK codes, host codes in the sense of SPEC 6.2), then the compiler itself
(RCH codes) on V8, with the libraries the Sutra names. One result shape (AI_GENUI 3.1): ok, problems (sorted, at most 25), a summary
in words, the plan (tables and fragments read), the attempt count and, when ok, a check token that publish_ui requires.

Kit codes (a host's, SPEC 6.2):

    RGK-101  the Sutra is larger than the limit              RGK-111  a table that was not offered
    RGK-102  not a YAML mapping with a sutra: name           RGK-112  a column the offered table does not have
    RGK-103  more panels than the limit                      RGK-120  a library that is not allowed or not found
    RGK-110  a data source an agent may not write            RGK-121  a fragment the catalogue does not have
             (url:, a long rows: list)                       RGK-130  text that looks like markup or script
    RGK-140  too many attempts: stop and simplify            RGK-150  the compiler is not available here
    RGK-160..167  publish_ui: a table source that cannot be read (rachana_genui.rows)

Rows are never an input: tables are described by name and columns only (GD4).
"""
from __future__ import annotations

import hashlib
import hmac
import json
import re
import threading
from typing import Any, Optional

import yaml

from . import audit, doclint
from .catalogue import Catalogue
from .settings import settings

MAX_PROBLEMS = 25
MARKUP = re.compile(r"(?i)<\s*(script|iframe|object|embed|style|link|meta|svg)\b|javascript:|\bon[a-z]+\s*=|data:text/html")
FIELD_REF = re.compile(r"\$\.([A-Za-z_][A-Za-z0-9_]*)\.([A-Za-z_][A-Za-z0-9_]*)")

_attempts: dict[tuple[str, str], int] = {}
_alock = threading.Lock()
_engine = None
_elock = threading.Lock()


def sutra_hash(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def token_for(text: str) -> str:
    return hmac.new(settings().secret.encode(), sutra_hash(text).encode(), hashlib.sha256).hexdigest()


def token_ok(text: str, token: str) -> bool:
    return bool(token) and hmac.compare_digest(token_for(text), token)


def engine():
    """The compiler on V8 (drishti_rachana.engine), one isolate per process, used under a lock; None when not installed or built."""
    global _engine
    with _elock:
        if _engine is None:
            try:
                from drishti_rachana.engine import Rachana
                _engine = Rachana()
            except Exception as e:  # noqa: BLE001 - reported as RGK-150
                _engine = e
        return None if isinstance(_engine, Exception) else _engine


def _index(text: str) -> dict[str, yaml.Node]:
    """JSON Pointer -> YAML node, for placing a problem."""
    out: dict[str, yaml.Node] = {}

    def walk(n: yaml.Node, p: str) -> None:
        out[p] = n
        if isinstance(n, yaml.MappingNode):
            for k, v in n.value:
                walk(v, f"{p}/{str(k.value).replace('~', '~0').replace('/', '~1')}")
        elif isinstance(n, yaml.SequenceNode):
            for i, v in enumerate(n.value):
                walk(v, f"{p}/{i}")
    try:
        root = yaml.compose(text)
        if root is not None:
            walk(root, "")
    except yaml.YAMLError:
        pass
    return out


class _Problems:
    def __init__(self, text: str):
        self.items: list[dict] = []
        self.where = _index(text)

    def add(self, code: str, message: str, path: str = "", severity: str = "error", **extra: Any) -> None:
        n = self.where.get(path)
        while n is None and path:
            path = path.rsplit("/", 1)[0]
            n = self.where.get(path)
        line, col = (n.start_mark.line + 1, n.start_mark.column + 1) if n is not None else (1, 1)
        p = {"code": code, "severity": severity, "message": message, "range": {"start": {"line": line, "column": col}}, "path": path}
        p.update({k: v for k, v in extra.items() if v})
        self.items.append(p)


def _walk_panels(panels: Any, path: str):
    """Every panel and use, with its pointer, including slot content and children."""
    if not isinstance(panels, list):
        return
    for i, p in enumerate(panels):
        if not isinstance(p, dict):
            continue
        here = f"{path}/{i}"
        yield p, here
        for key in ("panels", "children"):
            yield from _walk_panels(p.get(key), f"{here}/{key}")
        for sname, content in (p.get("slots") or {}).items() if isinstance(p.get("slots"), dict) else []:
            yield from _walk_panels(content, f"{here}/slots/{sname}")


def _strings(v: Any, path: str):
    if isinstance(v, str):
        yield v, path
    elif isinstance(v, dict):
        for k, x in v.items():
            yield from _strings(x, f"{path}/{k}")
    elif isinstance(v, list):
        for i, x in enumerate(v):
            yield from _strings(x, f"{path}/{i}")


def kit_check(text: str, tables: Optional[dict], cat: Catalogue, probs: _Problems) -> dict:
    """The kit's own rules; returns the plan (tables, fragments, libraries)."""
    s = settings()
    plan: dict[str, Any] = {"tables": [], "fragments": [], "libraries": []}
    if len(text.encode("utf-8")) > s.max_sutra_bytes:
        probs.add("RGK-101", f"the Sutra is {len(text.encode('utf-8'))} bytes; at most {s.max_sutra_bytes}: make it smaller")
        return plan
    try:
        doc = yaml.safe_load(text)
    except yaml.YAMLError as e:
        mark = getattr(e, "problem_mark", None)
        probs.items.append({"code": "RCH-2001", "severity": "error", "message": f"the text is not YAML: {getattr(e, 'problem', e)}",
                            "range": {"start": {"line": (mark.line + 1) if mark else 1, "column": (mark.column + 1) if mark else 1}},
                            "path": ""})
        return plan
    if not isinstance(doc, dict) or not isinstance(doc.get("sutra"), str):
        probs.add("RGK-102", "a Sutra is a YAML mapping with rachana:, sutra: <name>, version: and panels:")
        return plan
    for v, p in _strings(doc, ""):
        if MARKUP.search(v):
            probs.add("RGK-130", "text that looks like markup or script; a Sutra holds words and expressions only", p)
    libs = doc.get("libraries") or []
    for i, ref in enumerate(libs if isinstance(libs, list) else []):
        name = str(ref).split("@")[0]
        if name not in cat.libraries:
            probs.add("RGK-120", f"library '{name}' is not installed here", f"/libraries/{i}", expected=sorted(cat.libraries))
        elif s.allowed_libraries and name not in s.allowed_libraries:
            probs.add("RGK-120", f"library '{name}' is not allowed for agents here", f"/libraries/{i}", expected=s.allowed_libraries)
        else:
            plan["libraries"].append(name)
    offered = tables or {}
    data = doc.get("data") or {}
    for dname, src in (data.items() if isinstance(data, dict) else []):
        here = f"/data/{dname}"
        if isinstance(src, str) or (isinstance(src, dict) and "url" in src):
            probs.add("RGK-110", f"data entry '{dname}' reads an address; an agent's Sutra reads offered tables only "
                      f"({{ table: <name> }})", here)
        elif isinstance(src, dict) and "rows" in src:
            n = len(src["rows"]) if isinstance(src["rows"], list) else 0
            if n > s.max_inline_rows:
                probs.add("RGK-110", f"data entry '{dname}' writes {n} rows; at most {s.max_inline_rows} written in the Sutra "
                          "(constants such as targets or labels): data from a tool is published as a table", here)
        elif isinstance(src, dict) and "table" in src:
            t = str(src["table"])
            plan["tables"].append(t)
            if offered and t not in offered:
                probs.add("RGK-111", f"table '{t}' was not offered", f"{here}/table", expected=sorted(offered))
    count = 0
    cols_of = {n: {c["name"] for c in (t.get("columns") or [])} for n, t in offered.items()}
    alias = {d: str(v.get("table")) for d, v in data.items() if isinstance(v, dict) and "table" in v} if isinstance(data, dict) else {}
    for p, here in _walk_panels(doc.get("panels"), "/panels"):
        count += 1
        if "use" not in p:
            continue
        ref = str(p["use"])
        e = cat.find(ref)
        if e is None:
            probs.add("RGK-121", f"fragment '{ref}' is not in the catalogue (search_fragments finds one)", f"{here}/use",
                      expected=cat.nearest(ref))
            continue
        plan["fragments"].append(e.id)
        if e.library not in plan["libraries"]:
            probs.add("RGK-120", f"fragment '{ref}' is in library '{e.library}', which libraries: does not list "
                      f"(add {e.library}@{e.major})", f"{here}/use")
        for pname, val in (p.get("with") or {}).items() if isinstance(p.get("with"), dict) else []:
            if isinstance(val, dict) and isinstance(val.get("columns"), dict):
                m = re.match(r"^\$\.([A-Za-z_][A-Za-z0-9_]*)$", str(val.get("from", "")))
                t = alias.get(m.group(1), m.group(1)) if m else None
                if t in cols_of and cols_of[t]:
                    for local, caller in val["columns"].items():
                        if caller not in cols_of[t]:
                            probs.add("RGK-112", f"table '{t}' has no column '{caller}' (for {local})",
                                      f"{here}/with/{pname}/columns/{local}", expected=sorted(cols_of[t]))
    for v, p in _strings(doc.get("panels"), "/panels"):
        for m in FIELD_REF.finditer(v):
            t = alias.get(m.group(1), m.group(1))
            if t in cols_of and cols_of[t] and m.group(2) not in cols_of[t]:
                probs.add("RGK-112", f"table '{t}' has no column '{m.group(2)}'", p, expected=sorted(cols_of[t]))
    if count > s.max_panels:
        probs.add("RGK-103", f"{count} panels; at most {s.max_panels}: split the board or use fewer fragments", "/panels")
    for d in doclint.check(text, "sutra.yaml", "sutra"):
        if d.path in ("/doc", "/doc/summary", "/doc/description"):
            probs.items.append({**d.to_json(), "severity": "warning"})
    return plan


def check(text: str, tables: Optional[dict] = None, cat: Optional[Catalogue] = None, count: bool = True) -> dict:
    from . import catalogue as catalogue_mod
    s = settings()
    cat = cat or catalogue_mod.load(s.libraries)
    probs = _Problems(text)
    plan = kit_check(text, tables, cat, probs)
    name = ""
    try:
        d = yaml.safe_load(text)
        name = str(d.get("sutra", "")) if isinstance(d, dict) else ""
    except yaml.YAMLError:
        pass
    key = (audit.caller(), name)
    with _alock:
        n = _attempts.get(key, 0) + (1 if count else 0)
        _attempts[key] = n
    engine_state = "not-run"
    if not any(p["severity"] == "error" for p in probs.items):
        r = engine()
        if r is None:
            engine_state = "unavailable"
            probs.add("RGK-150", "the compiler is not available here (pip install 'drishti-rachana[v8]' and its bundle)",
                      severity="error" if s.require_engine else "warning")
        else:
            engine_state = "v8"
            res = r.check(text, file=f"{name or 'board'}.sutra.yaml", files=cat.library_files(plan["libraries"]))
            for p in res.get("problems", []):
                probs.items.append(p)
    errors = [p for p in probs.items if p.get("severity", "error") == "error"]
    items = sorted(probs.items, key=lambda p: (p["range"]["start"]["line"], p["range"]["start"]["column"], p["code"]))
    ok = not errors
    stop = not ok and n > s.max_attempts
    result: dict[str, Any] = {"ok": ok, "problems": items[:MAX_PROBLEMS], "engine": engine_state,
                              "attempt": {"n": n, "max": s.max_attempts}, "sutra_hash": sutra_hash(text), "plan": plan}
    if len(items) > MAX_PROBLEMS:
        result["more"] = len(items) - MAX_PROBLEMS
    if stop:
        result["stop"] = True
        result["problems"].insert(0, {"code": "RGK-140", "severity": "error", "message": f"{n} attempts for '{name}'; stop: "
                                      "simplify the board (fewer fragments) or ask the user", "range": {"start": {"line": 1,
                                      "column": 1}}, "path": ""})
    if ok:
        result["check_token"] = token_for(text)
        with _alock:
            _attempts.pop(key, None)
    result["summary"] = summary(result)
    audit.event("check", sutra=name, sutra_hash=result["sutra_hash"], ok=ok, engine=engine_state,
                codes=sorted({p["code"] for p in items}), attempt=n)
    return result


def summary(r: dict) -> str:
    if r["ok"]:
        warn = [p for p in r["problems"] if p.get("severity") == "warning"]
        return (f"OK: {len(r['plan']['fragments'])} fragments over tables {', '.join(sorted(set(r['plan']['tables']))) or 'none'}"
                + (f"; {len(warn)} warnings" if warn else "") + ". Publish it with publish_ui and this check_token.")
    errs = [p for p in r["problems"] if p.get("severity", "error") == "error"]
    first = "; ".join(f"line {p['range']['start']['line']}: {p['message']}"
                      + (f" (expected one of {', '.join(map(str, p['expected'][:6]))})" if p.get("expected") else "")
                      for p in errs[:3])
    tail = " Stop and simplify." if r.get("stop") else " Change only the lines named and check again."
    return f"{len(errs)} error{'s' if len(errs) != 1 else ''}. {first}.{tail}"


def reset_attempts() -> None:
    with _alock:
        _attempts.clear()


def describe_tables(tables: dict[str, Any]) -> dict:
    """Column schemas of fetched tables ({name: {rows}}), as check takes them: names and types only."""
    out = {}
    for n, t in tables.items():
        rows = t.get("rows") or []
        names: list[str] = []
        for r in rows[:200]:
            for k in r:
                if k not in names:
                    names.append(k)
        out[n] = {"columns": [{"name": c, "type": _type_of([r.get(c) for r in rows[:200]])} for c in names]}
    return out


def _type_of(vals: list) -> str:
    t = "text"
    for v in vals:
        if v is None:
            continue
        if isinstance(v, bool):
            return "boolean"
        if isinstance(v, (int, float)):
            t = "number"
            continue
        if isinstance(v, str) and re.match(r"^\d{4}-\d{2}-\d{2}$", v):
            t = "date"
            continue
        return "text"
    return t

