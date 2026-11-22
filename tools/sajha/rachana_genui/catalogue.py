# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""The fragment catalogue, generated from the libraries: one entry per fragment and major, built from its ``doc:`` block (the only
prose the catalogue reads: no comments, no READMEs) and its declared interface (params, slots, events). Searched two ways:

* by **text**: the words of a request against each entry's name, keywords, summary, use-when, tags, category and description
  (weighted, rarer words count more); a word of an entry's avoid-when is reported, never scored;
* by **data shape**: the caller's tables (column names and types, never rows) against each fragment's table and value parameters:
  which parameter each column can fill, how much of the fragment is covered, and the ready ``use:`` with its column mapping.

Deterministic and testable: no embeddings, no model. Libraries are read through drishti_rachana.libraries (the wheel).
"""
from __future__ import annotations

import hashlib
import math
import re
import threading
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable, Optional

import yaml

from . import doclint

FRAGMENT_FILE = re.compile(r"^fragments/([a-z][a-z0-9-]{1,63})\.v([0-9]+)\.fragment\.yaml$")
WORD = re.compile(r"[a-z0-9&]+")
STOP = {"a", "an", "the", "by", "of", "for", "and", "or", "in", "on", "to", "with", "per", "show", "me", "what", "is", "it", "its",
        "as", "at", "from", "each", "one", "all", "my", "our", "screen", "panel", "board", "view"}
WEIGHTS = {"name": 3.0, "keywords": 3.0, "summary": 2.0, "use-when": 2.0, "tags": 2.0, "category": 1.5, "description": 1.0}
NUMBER = {"number", "integer", "decimal", "money", "float", "double", "int", "long", "bigint"}
TEXT = {"text", "string", "str", "varchar"}
DATE = {"date"}
DATETIME = {"datetime", "timestamp", "date-time"}
#: Words that name the same thing in a fragment's column and a caller's column (both lower-cased, without separators)
SYNONYMS = {
    "label": {"name", "desk", "book", "region", "counterparty", "cpty", "sector", "group", "category", "scenario", "limitname"},
    "name": {"label", "desk", "book", "counterparty", "cpty", "limitname", "instrument", "issuer", "entity"},
    "value": {"amount", "amt", "pnl", "exposure", "var", "mtm", "notional", "usd", "total"},
    "used": {"usage", "utilisation", "utilization", "usedamt", "current", "exposure"},
    "limit": {"lim", "limitamt", "cap", "max"},
    "date": {"day", "asof", "businessdate", "cob", "valuedate", "dt"},
    "pnl": {"profit", "pl", "profitloss", "dailypnl"},
    "exposure": {"ead", "mtm", "exp", "exposureusd"},
    "group": {"sector", "industry", "country", "region", "desk"},
    "var": {"valueatrisk", "var99", "var95"},
    "es": {"expectedshortfall", "cvar", "es975"},
    "headline": {"title", "subject", "heading", "summary", "topic"},
    "source": {"publisher", "provider", "origin", "outlet", "agency", "author"},
    "link": {"url", "href", "uri", "permalink"},
    "previousvar": {"varprev", "prevvar", "varyesterday", "varprior"},
}


def _norm(s: str) -> str:
    return re.sub(r"[^a-z0-9]", "", s.lower())


def _tokens(text: Any) -> list[str]:
    if isinstance(text, (list, tuple)):
        text = " ".join(map(str, text))
    out = []
    for w in WORD.findall(str(text or "").lower().replace("p&l", "pnl")):
        if w in STOP:
            continue
        out.append(w[:-1] if len(w) > 3 and w.endswith("s") and not w.endswith("ss") else w)
    return out


def _kind(t: Optional[str]) -> str:
    t = (t or "").lower().rstrip("?")
    if t in NUMBER:
        return "number"
    if t in TEXT:
        return "text"
    if t in DATE:
        return "date"
    if t in DATETIME:
        return "datetime"
    if t in ("boolean", "bool"):
        return "boolean"
    return "any" if not t else t


def _fits(local: str, caller: str) -> bool:
    if local == "any":
        return True
    if local == caller:
        return True
    if local == "number" and caller == "number":
        return True
    if local == "date" and caller in ("date", "datetime"):
        return True
    if local == "datetime" and caller == "datetime":
        return True
    if local == "text" and caller in ("text", "date", "datetime"):
        return True
    return False


def _affinity(local: str, caller: str) -> float:
    a, b = _norm(local), _norm(caller)
    if a == b:
        return 1.0
    if b in SYNONYMS.get(a, set()) or a in SYNONYMS.get(b, set()):
        return 0.8
    if a and b and (a in b or b in a):
        return 0.6
    for syn in SYNONYMS.get(a, set()):
        if syn in b:
            return 0.5
    lt, ct = _parts(local), _parts(caller)
    if lt and ct:
        share = sum(1 for t in lt if t in ct) / len(lt)
        return 0.5 if share == 1 else round(0.3 * share, 3)
    return 0.0


ABBREVIATIONS = {"previous": "prev", "prior": "prev", "yesterday": "prev", "utilisation": "util", "utilization": "util",
                 "amount": "amt", "number": "num", "quantity": "qty", "counterparty": "cpty", "exposure": "exp"}


def _parts(name: str) -> set:
    """The words of a column name (VAR_99_PREV, previous-var, varPrev), shortened to one spelling."""
    words = re.findall(r"[a-z]+|[0-9]+", re.sub(r"([a-z])([A-Z])", r"\1 \2", name).lower())
    return {ABBREVIATIONS.get(w, w) for w in words if not w.isdigit()}


@dataclass
class Entry:
    id: str
    library: str
    library_version: str
    name: str
    major: int
    file: str
    doc: dict
    params: dict
    slots: dict
    emits: list
    text: dict  # field -> tokens

    def shape(self) -> dict:
        tables, values = {}, {}
        for n, p in self.params.items():
            if not isinstance(p, dict):
                continue
            t = str(p.get("type", ""))
            if t == "table":
                cols = p.get("columns") or {}
                tables[n] = {"required": bool(p.get("required")), "columns": {c: {"type": (d or {}).get("type"),
                             "optional": bool((d or {}).get("optional")), "doc": (d or {}).get("doc")} for c, d in cols.items()},
                             "open": not cols or all((d or {}).get("optional") for d in cols.values())}
            elif t in ("number", "integer", "decimal", "money", "text", "date", "datetime", "boolean"):
                values[n] = {"type": t, "required": bool(p.get("required")), "doc": p.get("doc")}
        return {"tables": tables, "values": values}

    def summary_json(self) -> dict:
        return {"id": self.id, "library": self.library, "name": self.name, "major": self.major, "doc": self.doc,
                "shape": self.shape()}

    def full_json(self) -> dict:
        return {**self.summary_json(), "library_version": self.library_version, "file": self.file, "params": self.params,
                "slots": self.slots, "emits": self.emits}


class Catalogue:
    """The entries of a set of libraries. Built once per set of library hashes; thread-safe to read."""

    def __init__(self, libraries: list[dict]):
        self.libraries = {l["name"]: l for l in libraries}
        self.entries: dict[str, Entry] = {}
        self.problems: list[dict] = []
        for lib in libraries:
            for path, text in sorted(lib["files"].items()):
                m = FRAGMENT_FILE.match(path)
                if not m:
                    continue
                for p in doclint.check(text, f"{lib['name']}/{path}", "fragment"):
                    self.problems.append(p.to_json())
                d = yaml.safe_load(text.replace("%{", "_{")) or {}
                doc = d.get("doc") or {}
                e = Entry(id=f"{lib['name']}/{m.group(1)}@{m.group(2)}", library=lib["name"], library_version=lib.get("version", ""),
                          name=m.group(1), major=int(m.group(2)), file=path, doc=doc, params=d.get("params") or {},
                          slots=d.get("slots") or {}, emits=d.get("emits") or [],
                          text={"name": _tokens(m.group(1).replace("-", " ")), "keywords": _tokens(doc.get("keywords")),
                                "summary": _tokens(doc.get("summary")), "use-when": _tokens(doc.get("use-when")),
                                "tags": _tokens(doc.get("tags")), "category": _tokens(doc.get("category")),
                                "description": _tokens(doc.get("description")), "avoid-when": _tokens(doc.get("avoid-when"))})
                self.entries[e.id] = e
        n = max(len(self.entries), 1)
        df: dict[str, int] = {}
        for e in self.entries.values():
            for w in set(w for f in WEIGHTS for w in e.text[f]):
                df[w] = df.get(w, 0) + 1
        self.idf = {w: math.log(1 + n / c) for w, c in df.items()}

    # ---- lookup -------------------------------------------------------------------------------------------------------------
    def find(self, ref: str) -> Optional[Entry]:
        """An entry by ``library/name@major``, ``library/name`` (the newest major) or ``name`` when only one library has it."""
        ref = ref.strip()
        if ref in self.entries:
            return self.entries[ref]
        base = ref.split("@")[0]
        hits = [e for e in self.entries.values() if f"{e.library}/{e.name}" == base or ("/" not in base and e.name == base)]
        return max(hits, key=lambda e: e.major) if len({(e.library, e.name) for e in hits}) == 1 else None

    def nearest(self, ref: str, n: int = 5) -> list[str]:
        import difflib
        names = sorted({f"{e.library}/{e.name}" for e in self.entries.values()})
        return difflib.get_close_matches(ref.split("@")[0], names, n=n, cutoff=0.4)

    def library_files(self, names: Iterable[str]) -> dict[str, str]:
        return {f"rachana_libs/{n}/{p}": t for n in names if n in self.libraries for p, t in self.libraries[n]["files"].items()}

    # ---- search -------------------------------------------------------------------------------------------------------------
    def _text_score(self, e: Entry, words: list[str]) -> tuple[float, list[str]]:
        score, why = 0.0, []
        for w in set(words):
            for f, weight in WEIGHTS.items():
                if w in e.text[f]:
                    score += weight * self.idf.get(w, 1.0)
                    why.append(f"{f}:{w}")
                    break
        return score, why

    def fit(self, e: Entry, tables: dict[str, dict]) -> dict:
        """How the caller's tables fill this fragment: per table parameter the best caller table, its coverage and mapping; the
        value parameters a record table can fill. ``tables`` is ``{name: {columns: [{name, type}], as?: rows|record}}``."""
        shape = e.shape()
        out: dict[str, Any] = {"tables": {}, "values": {}, "coverage": 0.0}
        parts, covered = 0, 0.0
        for pname, p in shape["tables"].items():
            best = None
            for tname, t in tables.items():
                if t.get("as") == "record":
                    continue
                cols = [(c["name"], _kind(c.get("type"))) for c in t.get("columns", [])]
                req = [(c, _kind(d.get("type"))) for c, d in p["columns"].items() if not d["optional"]]
                opt = [(c, _kind(d.get("type"))) for c, d in p["columns"].items() if d["optional"]]
                used, mapping, missing, aff = set(), {}, [], 0.0
                locals_ = req + opt
                # names first: every (fragment column, caller column) pair that shares name semantics, best affinity first (a required
                # column wins a tie), so 'headline' takes TITLE before it could take PUBLISHER merely for being text
                pairs = sorted(((_affinity(local, cn), (local, lk) not in req, local, cn) for local, lk in locals_
                                for cn, ck in cols if _fits(lk, ck)), key=lambda x: (-x[0], x[1], x[2], x[3]))
                for a, _opt, local, cn in pairs:
                    if a > 0 and local not in mapping and cn not in used:
                        used.add(cn)
                        mapping[local] = cn
                        aff += a
                for local, lk in req:               # then the required ones left: any column of a fitting type
                    if local in mapping:
                        continue
                    rest = sorted((cn for cn, ck in cols if cn not in used and _fits(lk, ck)))
                    if rest:
                        used.add(rest[0])
                        mapping[local] = rest[0]
                    else:
                        missing.append(local)
                cov = 1.0 if not req else (len(req) - len(missing)) / len(req)
                rank = (cov, aff)
                if best is None or rank > best[0]:
                    best = (rank, {"table": tname, "coverage": round(cov, 3), "mapping": mapping, "missing": missing})
            if best:
                out["tables"][pname] = best[1]
            if p["required"]:
                parts += 1
                covered += best[1]["coverage"] if best else 0.0
        records = {n: t for n, t in tables.items() if t.get("as") == "record"}
        taken: set = set()
        # the best pairs first, so 'var' takes VAR before 'previous-var' could
        pairs = sorted(((_affinity(vname, c["name"]), vname, tname, c["name"]) for vname, v in shape["values"].items()
                        for tname, t in records.items() for c in t.get("columns", [])
                        if _fits(_kind(v["type"]), _kind(c.get("type")))), key=lambda x: (-x[0], x[1], x[2], x[3]))
        for a, vname, tname, cname in pairs:
            if a > 0 and vname not in out["values"] and (tname, cname) not in taken:
                out["values"][vname] = {"table": tname, "field": cname}
                taken.add((tname, cname))
        for vname, v in shape["values"].items():
            if v["required"]:
                parts += 1
                covered += 1.0 if vname in out["values"] else 0.0
        out["coverage"] = round(covered / parts, 3) if parts else 0.0
        return out

    def use_of(self, e: Entry, fit: Optional[dict] = None) -> dict:
        """A ready ``use:`` of the fragment: the doc's example, or the mapping a fit found (``$.<table>`` with its columns)."""
        if not fit or not (fit["tables"] or fit["values"]):
            return dict(e.doc.get("example") or {"use": e.id, "with": {}})
        with_: dict[str, Any] = {}
        for pname, t in fit["tables"].items():
            with_[pname] = {"from": f"$.{t['table']}", "columns": t["mapping"]} if t["mapping"] else f"$.{t['table']}"
        for vname, v in fit["values"].items():
            with_[vname] = f"$.{v['table']}.{v['field']}"
        return {"use": e.id, "with": with_}

    def search(self, query: str = "", tables: Optional[dict] = None, category: str = "", library: str = "",
               limit: int = 10, allowed: Optional[Iterable[str]] = None) -> list[dict]:
        words = _tokens(query)
        allowed = set(allowed or [])
        scored = []
        for e in self.entries.values():
            if allowed and e.library not in allowed:
                continue
            if library and e.library != library:
                continue
            if category and e.doc.get("category") != category:
                continue
            ts, why = self._text_score(e, words) if words else (0.0, [])
            caution = sorted({w for w in words if w in e.text["avoid-when"]})
            fit = self.fit(e, tables) if tables else None
            if words and ts == 0 and not (fit and fit["coverage"] >= 1.0):
                continue
            if not words and fit is not None and fit["coverage"] == 0:
                continue
            score = ts + (4.0 * fit["coverage"] if fit else 0.0)
            item = {**e.summary_json(), "score": round(score, 3), "matched": why}
            if caution:
                item["caution"] = f"the request names words of its avoid-when ({', '.join(caution)}): read doc.avoid-when"
            if fit is not None:
                item["fit"] = fit
            item["use"] = self.use_of(e, fit)
            scored.append(item)
        scored.sort(key=lambda x: (-x["score"], x["id"]))
        return scored[:limit]

    def to_json(self) -> dict:
        return {"libraries": [{"name": n, "version": l.get("version", "")} for n, l in sorted(self.libraries.items())],
                "fragments": [self.entries[k].full_json() for k in sorted(self.entries)], "problems": self.problems}


_lock = threading.Lock()
_cache: dict[str, Catalogue] = {}


def load(paths: list[str]) -> Catalogue:
    """The catalogue of the libraries under ``paths`` (folders of libraries, unpacked libraries or .rlib files), cached by their
    contents' hash."""
    from drishti_rachana.libraries import find_libraries     # the wheel: one reader of libraries

    problems: list[str] = []
    libs: list[dict] = []
    seen: set[str] = set()
    for p in paths:
        for lib in find_libraries(Path(p), problems):
            if lib["name"] not in seen:
                seen.add(lib["name"])
                libs.append(lib)
    h = hashlib.sha256()
    for lib in sorted(libs, key=lambda l: l["name"]):
        h.update(lib["name"].encode())
        h.update((lib["files"].get("MANIFEST.sha384") or repr(sorted(lib["files"].items()))).encode())
    key = h.hexdigest()
    with _lock:
        if key not in _cache:
            _cache.clear()
            _cache[key] = Catalogue(libs)
            _cache[key].load_problems = problems
        return _cache[key]
