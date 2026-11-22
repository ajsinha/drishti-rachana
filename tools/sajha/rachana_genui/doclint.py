# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""The doc block lint, RCH-2093 (SPEC 2.9): a ``doc:`` block that is missing or too thin for a catalogue, a component browser or an AI
agent to choose and use the Sutra or fragment from it alone.

This module mirrors the compiler's rule (java/rachana-core ... parse/DocBlockLint.java) for the GenUI kit, which runs without a JVM:
the same checks, the same messages, the same JSON Pointer paths. The guard tests run both over the shipped libraries. When the V8
bundle exports the rule (after the next build) the kit asks the bundle instead and this file goes.

Kinds: ``fragment`` (a typed fragment or a text-form block: every field), ``sutra`` (a screen: summary, description, category,
use-when, avoid-when and keywords; data, example and see-also checked only when written).
"""
from __future__ import annotations

import re
from dataclasses import dataclass, field
from typing import Any, Optional

import yaml

CODE = "RCH-2093"
SUMMARY_MAX = 120
#: The keys a doc block takes: the GenUI fields, then the docstring keys that came before them (RCH-2055).
KEYS = ("summary", "description", "category", "tags", "use-when", "avoid-when", "data", "example", "see-also", "keywords",
        "intents", "params", "emits", "expects", "examples", "since", "uses")
DATA_KEYS = ("table", "in-words", "sample-row")
REF = re.compile(r"^([a-z][a-z0-9-]{0,63}/)?[a-z][a-z0-9-]{1,63}(@[0-9]+)?$")
MIN_WORDS = 4
MIN_DESCRIPTION = 40
MIN_KEYWORDS = 3


@dataclass
class Problem:
    code: str
    severity: str
    message: str
    file: str
    line: int
    column: int
    path: str

    def to_json(self) -> dict:
        return {"code": self.code, "severity": self.severity, "message": self.message, "file": self.file,
                "range": {"start": {"line": self.line, "column": self.column}}, "path": self.path}


@dataclass
class _Ctx:
    what: str
    file: str
    severity: str
    out: list = field(default_factory=list)

    def add(self, node: Optional[yaml.Node], path: str, message: str, at: Optional[yaml.Node] = None) -> None:
        n = node if node is not None else at
        line, col = (n.start_mark.line + 1, n.start_mark.column + 1) if n is not None else (1, 1)
        self.out.append(Problem(CODE, self.severity, f"{self.what}: {message}", self.file, line, col, path))


def _get(node: Optional[yaml.Node], key: str) -> Optional[yaml.Node]:
    if not isinstance(node, yaml.MappingNode):
        return None
    for k, v in node.value:
        if isinstance(k, yaml.ScalarNode) and k.value == key:
            return v
    return None


def _text(node: Optional[yaml.Node]) -> Optional[str]:
    return node.value if isinstance(node, yaml.ScalarNode) and node.tag != "tag:yaml.org,2002:null" else None


def _words(s: Optional[str]) -> int:
    return len((s or "").split())


def _texts(node: Optional[yaml.Node]) -> Optional[list[tuple[str, yaml.Node]]]:
    if not isinstance(node, yaml.SequenceNode):
        return None
    return [(_text(n) or "", n) for n in node.value]


def _plain(node: Optional[yaml.Node]) -> Any:
    return None if node is None else yaml.safe_load(yaml.serialize(node))


def _declared(root: yaml.Node) -> tuple[dict[str, dict], dict[str, dict]]:
    """The fragment's parameters as plain maps, and its table or record parameters with their required columns."""
    params = _plain(_get(root, "params")) or {}
    params = params if isinstance(params, dict) else {}
    shaped: dict[str, dict] = {}
    for n, p in params.items():
        if not isinstance(p, dict):
            continue
        t = str(p.get("type", ""))
        if t == "table":
            cols = p.get("columns") or {}
            shaped[n] = {"required": [c for c, d in cols.items() if not (d or {}).get("optional")], "open": not cols or all(
                (d or {}).get("optional") for d in cols.values())}
        elif t.startswith("record"):
            shaped[n] = {"required": [], "open": True}
    return params, shaped


def check(text: str, file: str = "file.yaml", kind: Optional[str] = None, severity: str = "warning") -> list[Problem]:
    """The RCH-2093 problems of one file's text (a Sutra, a typed fragment or a block). ``kind`` is found from the text when not
    given: a ``fragment:`` key makes it a fragment, else a Sutra. A text that does not read as YAML gives no problem here: the
    compiler reports it (RCH-2001)."""
    try:
        root = yaml.compose(text.replace("%{", "_{"))      # a name substitution reads as text, at the same columns
    except yaml.YAMLError:
        return []
    if not isinstance(root, yaml.MappingNode):
        return []
    name = _text(_get(root, "fragment")) or _text(_get(root, "sutra")) or _text(_get(root, "block")) or file
    if kind is None:
        kind = "fragment" if _get(root, "fragment") is not None or _get(root, "block") is not None else "sutra"
    ctx = _Ctx(f"{kind} {name}", file, severity)
    doc = _get(root, "doc")
    if not isinstance(doc, yaml.MappingNode):
        ctx.add(doc, "/doc", "has no doc: block (summary, description, category, use-when, avoid-when, data, example, see-also, "
                "keywords)", at=root)
        return ctx.out
    for k, _v in doc.value:
        if isinstance(k, yaml.ScalarNode) and k.value not in KEYS:
            ctx.add(k, f"/doc/{k.value}", f"doc.{k.value} is not a doc key (one of {', '.join(KEYS)})")
    summary = _text(_get(doc, "summary"))
    if not summary or "\n" in summary.strip() or len(summary) > SUMMARY_MAX or _words(summary) < MIN_WORDS:
        ctx.add(_get(doc, "summary"), "/doc/summary", f"doc.summary is one line of {MIN_WORDS} words or more and at most "
                f"{SUMMARY_MAX} characters, saying what it shows", at=doc)
    description = _text(_get(doc, "description"))
    if not description or len(description.strip()) < MIN_DESCRIPTION:
        ctx.add(_get(doc, "description"), "/doc/description", f"doc.description says what it shows and how, in "
                f"{MIN_DESCRIPTION} characters or more", at=doc)
    if not _text(_get(doc, "category")):
        ctx.add(_get(doc, "category"), "/doc/category", "doc.category names the group it is listed under", at=doc)
    for key in ("use-when", "avoid-when"):
        items = _texts(_get(doc, key))
        if not items:
            ctx.add(_get(doc, key), f"/doc/{key}", f"doc.{key} is a list of one sentence or more"
                    + (" (each naming what to use instead)" if key == "avoid-when" else ""), at=doc)
            continue
        for i, (s, n) in enumerate(items):
            if _words(s) < MIN_WORDS:
                ctx.add(n, f"/doc/{key}/{i}", f"doc.{key}[{i}] is a sentence of {MIN_WORDS} words or more")
    keywords = _texts(_get(doc, "keywords"))
    if not keywords or len([s for s, _ in keywords if s.strip()]) < MIN_KEYWORDS:
        ctx.add(_get(doc, "keywords"), "/doc/keywords", f"doc.keywords lists {MIN_KEYWORDS} search words or more", at=doc)
    tags = _get(doc, "tags")
    if tags is not None and _texts(tags) is None:
        ctx.add(tags, "/doc/tags", "doc.tags is a list of words")
    strict = kind == "fragment"
    params, shaped = _declared(root) if strict else ({}, {})
    _data(ctx, doc, strict, shaped)
    _example(ctx, doc, strict, name, params)
    see = _get(doc, "see-also")
    items = _texts(see)
    if see is None and strict:
        ctx.add(None, "/doc/see-also", "doc.see-also lists the related fragments (library/name), one or more", at=doc)
    elif see is not None and (items is None or (strict and not items)):
        ctx.add(see, "/doc/see-also", "doc.see-also is a list of fragment names (library/name)")
    else:
        for i, (s, n) in enumerate(items or []):
            if not REF.match(s):
                ctx.add(n, f"/doc/see-also/{i}", f"doc.see-also[{i}] '{s}' is a fragment name (library/name or library/name@major)")
    return ctx.out


def _data(ctx: _Ctx, doc: yaml.Node, strict: bool, shaped: dict[str, dict]) -> None:
    data = _get(doc, "data")
    if data is None:
        if strict:
            ctx.add(None, "/doc/data", "doc.data says in words what data it takes (in-words) and gives one sample-row", at=doc)
        return
    if not isinstance(data, yaml.MappingNode):
        ctx.add(data, "/doc/data", "doc.data is a mapping: table, in-words, sample-row")
        return
    for k, _v in data.value:
        if isinstance(k, yaml.ScalarNode) and k.value not in DATA_KEYS:
            ctx.add(k, f"/doc/data/{k.value}", f"doc.data.{k.value} is not a data key (one of {', '.join(DATA_KEYS)})")
    if _words(_text(_get(data, "in-words"))) < MIN_WORDS:
        ctx.add(_get(data, "in-words"), "/doc/data/in-words", f"doc.data.in-words says in {MIN_WORDS} words or more what the "
                "rows are (one row per what) and which columns they carry", at=data)
    sample = _get(data, "sample-row")
    if not isinstance(sample, yaml.MappingNode) or not sample.value:
        ctx.add(sample, "/doc/data/sample-row", "doc.data.sample-row is one row: a mapping of column to a synthetic value", at=data)
        return
    table = _text(_get(data, "table"))
    if table is None:
        if strict and shaped:
            ctx.add(None, "/doc/data/table", f"doc.data.table names the parameter the sample row is of (one of {', '.join(shaped)})",
                    at=data)
        return
    if strict and table not in shaped:
        ctx.add(_get(data, "table"), "/doc/data/table", f"doc.data.table '{table}' is not a table or record parameter"
                + (f" (one of {', '.join(shaped)})" if shaped else ""))
        return
    have = {k.value for k, _v in sample.value if isinstance(k, yaml.ScalarNode)}
    missing = [c for c in shaped.get(table, {}).get("required", []) if c not in have]
    if missing:
        ctx.add(sample, "/doc/data/sample-row", f"doc.data.sample-row has every required column of {table}: missing "
                + ", ".join(missing))


def _example(ctx: _Ctx, doc: yaml.Node, strict: bool, name: str, params: dict[str, dict]) -> None:
    ex = _get(doc, "example")
    if ex is None:
        if strict:
            ctx.add(None, "/doc/example", "doc.example is a minimal use of it: { use: library/name@major, with: {...} }", at=doc)
        return
    if not isinstance(ex, yaml.MappingNode):
        ctx.add(ex, "/doc/example", "doc.example is a mapping: { use: library/name@major, with: {...} }")
        return
    use = _text(_get(ex, "use")) or ""
    used = use.split("/")[-1].split("@")[0]
    if strict and used != name:
        ctx.add(_get(ex, "use") or ex, "/doc/example/use", f"doc.example.use is a use of {name} (library/{name}@major)")
    with_ = _get(ex, "with")
    given = {k.value for k, _v in with_.value} if isinstance(with_, yaml.MappingNode) else set()
    if with_ is not None and not isinstance(with_, yaml.MappingNode):
        ctx.add(with_, "/doc/example/with", "doc.example.with is a mapping of parameter to value")
    if strict:
        for k in sorted(given - set(params)):
            ctx.add(with_, f"/doc/example/with/{k}", f"doc.example sets '{k}', which is not a parameter")
        for k in sorted(n for n, p in params.items() if isinstance(p, dict) and p.get("required") and n not in given):
            ctx.add(with_ if with_ is not None else ex, "/doc/example/with", f"doc.example gives the required parameter '{k}'")
