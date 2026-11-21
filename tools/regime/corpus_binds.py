#!/usr/bin/env python3
# Copyright (c) 2026 Ashutosh Sinha. Proprietary: see LICENSE.
"""The bind half of the customer-corpus export (CG3, SUTRA_EXPANSION 6.6, gate findings F2, F13): compat/customers/drishti/<version>/binds/.

Per shipped Sutra of the corpus, synthetic data is made and the corpus release's reference binder binds the Sutra over it
(tools/regime/CorpusBind.java over Drishti's engine jars at the corpus's tag: a scratch clone, read only, removed at the end). Two passes:

  expand  the reference parses every Sutra (fragments and blocks expanded) and writes it as JSON; the synthetic data is made from those
          panels (and the Sutra's own text): the record (every `$.path` read; lists where rows are read, each row with the `@.FIELD`s of
          its panel and the fields a chart's options name, or their defaults: x, y, date, label, ...; a field-list's object; a
          histogram's numbers; a graph's nodes and edges joined), the entities of each kind a panel's `source:`, a map, a control or a
          child panel reads (three per kind), and the record's fields that name entities (`link($.FIELD, 'kind')`). Values are made up
          from the field's name (dates around the business date, texts, numbers, country codes); no data of any customer or source.
  bind    the reference binds each Sutra as its view pipeline does, over a RECORDING source router (one source plugin serving
          every kind from the synthetic entities: linked documents, wired sources, map lookups, control lookups) and the host's pivot
          engine (bicompute's PivotCore, as the reference's binder asks it); every answer is written down as a host call.

Written per Sutra file:

  binds/<file>.json        the recording, RecordedHost's format: {file, group, hostCalls: [record, calendar.today, source.fetch
                           [kind, id], source.search [kind, text], source.freshness [kind], pivot.cells [view, fields, rows],
                           pivot.key [coordinates], panel [provenance|links, id]]}
  binds/<file>.view.json   the ViewModel exactly as the reference wrote it, or
  binds/<file>.error.txt   why the reference could not bind it

and the corpus's MANIFEST.sha384 and VERSION.json ("binds": the count) are rewritten. CustomerCorpusTest replays every recording
through Core's binder (CoreBinds: the recording answers Core's TableProvider, PivotEngine and host panels) and compares byte for byte;
tools/split/known-differences.txt lists the accepted differences, each with a reason. --report prints the filled and empty panels by
kind of the recorded views (the F2 bar: every kind filled somewhere, under 10% of data panels empty).

    python3 tools/regime/corpus_binds.py --drishti ~/IdeaProjects/drishti [--tag v2.9.11.i_7a] [--classpath-file CP-ENGINE.txt]
    python3 tools/regime/corpus_binds.py --report
"""
import argparse, datetime, hashlib, json, os, re, shutil, subprocess, sys, tempfile
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
TODAY = "2026-01-15"
ROWS = 3
NUMBERS = [[("#numbers", None)]]                                        # put(): the rows are plain numbers, not objects

PATH = re.compile(r"(?<![A-Za-z0-9_@])\$((?:\.[A-Za-z_][A-Za-z0-9_-]*(?:\[(?:\*|\d+)\])?)+)")
FIELD = re.compile(r"(?<![A-Za-z0-9_$])@((?:\.[A-Za-z_][A-Za-z0-9_-]*(?:\[(?:\*|\d+)\])?)+)")
STEP = re.compile(r"\.([A-Za-z_][A-Za-z0-9_-]*)(\[(?:\*|\d+)\])?")
DATE = re.compile(r"(DATE|_DT$|^DT_|TIME|MATURITY|EXPIRY|AS_OF|ASOF|SETTLE|START|END|FROM|UNTIL|day$|Date$|date$|At$)", re.I)
ISO3 = re.compile(r"(^|_)ISO3?$|^iso3?$|^iso_?3$", re.I)
COUNTRIES = ("USA", "GBR", "DEU", "FRA", "JPN", "CAN", "IND", "BRA", "AUS")
TEXT = re.compile(r"(ID$|_ID|Id$|NAME|Name|CODE|Code|TYPE|Type|STATUS|Status|CCY|CURRENCY|Currency|SIDE|Side|DESK|Desk|BOOK|Book|DESC|LABEL|"
                  r"Label|KIND|Kind|COUNTRY|Country|REGION|Region|SECTOR|Sector|ISIN|CUSIP|TICKER|SYMBOL|TEXT|Text|TITLE|Title|URL|EMAIL|"
                  r"NOTE|Note|COMMENT|REASON|Reason|SOURCE|Source|SYSTEM|TRADER|Trader|COUNTERPARTY|Counterparty|ENTITY|Entity|LEI|"
                  r"RATING|Rating|TENOR|Tenor|INDEX|Index|PRODUCT|Product|METHOD|Method|ROLE|Role|USER|User|GROUP|Group|CATEGORY|LEVEL|"
                  r"FLAG|Flag|MODE|Mode|STATE|State|CLASS|Class|^id$|^name$|^code$|^type$|^status$|^kind$|^key$|^label$)")


def value(name, i):
    """A made-up value for field `name` of row i: a date, a text or a number, from the name alone (deterministic)."""
    h = int(hashlib.sha256(name.encode()).hexdigest()[:6], 16)
    if ISO3.search(name):                                               # a country code a map can place
        return COUNTRIES[(h + i) % len(COUNTRIES)]
    if DATE.search(name):                                               # from a month before the business date to four after it
        return (datetime.date.fromisoformat(TODAY) + datetime.timedelta(days=(h + 37 * i) % 150 - 30)).isoformat()
    if TEXT.search(name):
        return f"{name.upper()[:12]}-{chr(65 + (h + i) % 26)}{i + 1}"
    n = (h % 90000) / 100 + 100 * i - (250 if (h + i) % 3 == 0 else 0)
    return int(n) if h % 2 == 0 else round(n, 2)


def put(doc, steps, leaf_fields, i=0):
    """Sets the path `steps` ([(name, '[*]' or None)]) in doc, lists of ROWS where a step reads rows; the leaf a made-up value, or rows of
    `leaf_fields` when the path is a rows path."""
    name, idx = steps[0]
    last = len(steps) == 1
    if last and leaf_fields == NUMBERS:                                 # a list of plain numbers (a histogram's rows)
        if not isinstance(doc.get(name), list):
            h = int(hashlib.sha256(name.encode()).hexdigest()[:6], 16)
            doc[name] = [round(((h >> (j % 16)) % 2000) / 10 - 100 + 7 * j, 2) for j in range(4 * ROWS)]
        return
    if idx:
        lst = doc.get(name)
        if not isinstance(lst, list):
            lst = doc[name] = [{} for _ in range(ROWS)]
        for j, item in enumerate(lst):
            if not isinstance(item, dict):
                continue
            if last:
                for f in leaf_fields:
                    put(item, f, [], j)
            else:
                put(item, steps[1:], leaf_fields, j)
        return
    if last:
        if leaf_fields:
            lst = doc.get(name)
            if not isinstance(lst, list):
                lst = doc[name] = [{} for _ in range(ROWS)]
            for j, item in enumerate(lst):
                if isinstance(item, dict):
                    for f in leaf_fields:
                        put(item, f, [], j)
        elif name not in doc:
            doc[name] = value(name, i)
        return
    nxt = doc.get(name)
    if not isinstance(nxt, dict):
        if isinstance(nxt, list):
            return
        nxt = doc[name] = {}
    put(nxt, steps[1:], leaf_fields, i)


def steps(path):
    return [(m.group(1), m.group(2)) for m in STEP.finditer(path)]


def texts(node, out):
    if isinstance(node, str):
        out.append(node)
    elif isinstance(node, dict):
        for v in node.values():
            texts(v, out)
    elif isinstance(node, list):
        for v in node:
            texts(v, out)


def panels(node, out):
    """Every mapping that reads rows (rows:, or items under columns), outermost first."""
    if isinstance(node, dict):
        if isinstance(node.get("rows"), str):
            out.append(node)
        for v in node.values():
            panels(v, out)
    elif isinstance(node, list):
        for v in node:
            panels(v, out)


def record(text):
    """The synthetic record of one Sutra file."""
    try:
        doc = yaml.safe_load(text)
    except yaml.YAMLError:
        doc = None
    all_text = []
    texts(doc, all_text)
    rec = {}
    row_paths = {}
    for p in ([] if doc is None else _panels(doc)):
        m = PATH.search(p["rows"])
        if not m:
            continue
        inner = []
        texts({k: v for k, v in p.items() if k != "rows"}, inner)
        fields = []
        for t in inner:
            for f in FIELD.finditer(t):
                st = steps(f.group(1))
                if st and st not in fields:
                    fields.append(st)
        row_paths.setdefault(m.group(1), [])
        for f in fields:
            if f not in row_paths[m.group(1)]:
                row_paths[m.group(1)].append(f)
    for path, fields in row_paths.items():
        st = steps(path)
        if st:
            put(rec, st, fields or [[("VALUE", None)]])
    for t in all_text:
        for m in PATH.finditer(t):
            st = steps(m.group(1))
            if st:
                put(rec, st, [])
    return rec


def _panels(doc):
    out = []
    panels(doc, out)
    return out


# ---- the synthetic data from the Sutra as the reference parsed it (fragments and blocks expanded: CorpusBind expand) ---------------

SRC = re.compile(r"\$src((?:\.[A-Za-z_][A-Za-z0-9_-]*(?:\[(?:\*|\d+)\])?)+)")
LINK = re.compile(r"link\(\s*\$\.([A-Za-z_][A-Za-z0-9_]*)\s*,\s*'([a-z0-9][a-z0-9-]*)'")
SOURCE_LINK = re.compile(r"^\s*link\(.*,\s*'([a-z0-9][a-z0-9-]*)'\s*(?:,[^)]*)?\)\s*$")
BARE = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*$")
ENTITIES = 3
# the options that name a field of the rows (not an expression), and the field a kind reads when the option is not written
# (the reference's ChartBinder and Binder defaults)
FIELD_OPTIONS = ("x", "y", "date", "label", "detail", "status", "value", "across", "by", "size", "group", "open", "high", "low",
                 "close", "volume", "total", "sum")
FIELD_DEFAULTS = {"LINE": {"x": "tenor", "y": "value"}, "AREA": {"x": "tenor"}, "HBAR": {"label": "label", "value": "value"},
                  "TIMELINE": {"date": "date", "label": "event", "detail": "description", "status": "status"},
                  "CANDLESTICK": {"x": "date", "open": "open", "high": "high", "low": "low", "close": "close"},
                  "WATERFALL": {"label": "label", "value": "value"}, "SCATTER": {"label": "label"}, "SURFACE": {"x": "tenor", "y": "value"}}
NOT_FIELDS = {"KV", "METRIC", "MARKDOWN", "STATUS", "GAUGE", "MAP", "PROVENANCE", "LINKS", "GRAPH", "TABS"}


class Needs:
    """What one document must hold: [(steps, row fields)], made into documents by put()."""

    def __init__(self):
        self.paths = []

    def need(self, st, fields=()):
        for i, (p, f) in enumerate(self.paths):
            if p == st:
                for x in fields:
                    if x not in f:
                        f.append(x)
                return
        self.paths.append((st, list(fields)))

    def make(self, i=0):
        doc = {}
        for st, fields in sorted(self.paths, key=lambda t: len(t[1]) == 0):   # rows first: a plain leaf never stands where rows go
            put(doc, st, fields, i)
        return doc


def strings(node, out, skip=("parts", "children", "location", "body")):
    """The texts of a parsed Sutra's node, mappings in key order (the reference parses some from Map.of: their order is a draw)."""
    if isinstance(node, str):
        out.append(node)
    elif isinstance(node, dict):
        for k, v in sorted(node.items()):
            if k not in skip:
                strings(v, out, skip)
    elif isinstance(node, list):
        for v in node:
            strings(v, out, skip)


def row_fields(panel, texts):
    """The fields a panel reads in each of its rows: every @.FIELD, and the options that name a field (or their defaults)."""
    fields = []
    for t in texts:
        for f in FIELD.finditer(t):
            st = steps(f.group(1))
            if st and st not in fields:
                fields.append(st)
    opts = panel.get("options") or {}
    kind = panel.get("kind")
    if kind not in NOT_FIELDS:
        named = dict(FIELD_DEFAULTS.get(kind, {}))
        for k in FIELD_OPTIONS:
            if k in opts:
                named[k] = opts[k]
        for v in named.values():
            for n in (v if isinstance(v, list) else [v]):
                if isinstance(n, str) and BARE.match(n) and [(n, None)] not in fields:
                    fields.append([(n, None)])
        for ser in opts.get("series") or [] if isinstance(opts.get("series"), list) else []:
            if isinstance(ser, dict) and isinstance(ser.get("value"), str) and BARE.match(ser["value"]):
                fields.append([(ser["value"], None)])
    return fields


class Synth:
    """The synthetic data of one Sutra: the record (root), the entities of each kind its panels, maps and controls read, and the
    record's fields that name entities (link($.FIELD, 'kind'))."""

    def __init__(self):
        self.root = Needs()
        self.kinds = {}
        self.links = {}
        self.graphs = []                                                # (Needs, nodes path, edges path): edges joined to the nodes

    def kind(self, k):
        return self.kinds.setdefault(k, Needs())

    def panel(self, p, target, at, src_kind):
        """One panel: `target` the Needs its $. paths read, `at` (Needs, steps of the rows) what @ is, `src_kind` the kind $src is."""
        wiring = p.get("wiring") or {}
        opts = p.get("options") or {}
        source = wiring.get("source")
        if isinstance(source, dict) and isinstance(source.get("kind"), str) and BARE.match(source["kind"].replace("-", "_")):
            src_kind = source["kind"]
            self.kind(src_kind)
        if isinstance(opts.get("source"), str):
            m = SOURCE_LINK.match(opts["source"])
            if m:
                target = self.kind(m.group(1))
        texts = []
        strings(p, texts)
        body = p.get("body")
        if isinstance(body, dict):
            strings(body, texts)
        fields = row_fields(p, texts)
        rows = opts.get("rows") or opts.get("each")
        inner = None                                                    # the rows of this panel, @ of its parts
        kind = p.get("kind")
        if kind == "HISTOGRAM" and not opts.get("value"):
            fields = NUMBERS
        elif kind == "KV" and not p.get("columns"):                    # a record's plain fields, listed (field-list)
            fields = []
        if isinstance(rows, str):
            m = SRC.search(rows)
            if rows.strip().startswith("$src") and src_kind and not m:
                for f in fields:
                    self.kind(src_kind).need(f)
                inner = (self.kind(src_kind), [])
            elif m and src_kind:
                st = steps(m.group(1))
                self.kind(src_kind).need(st, fields)
                inner = (self.kind(src_kind), st)
            elif BARE.match(rows.strip()):                              # a field of the document read by name (rows: points)
                st = [(rows.strip(), None)]
                target.need(st, fields)
                inner = (target, st)
            else:
                m = PATH.search(rows)
                if m:
                    st = steps(m.group(1))
                    target.need(st, fields)
                    inner = (target, st)
                else:
                    m = FIELD.search(rows)
                    if m and at is not None:
                        st = at[1][:-1] + [(at[1][-1][0], "[*]")] + steps(m.group(1)) if at[1] else steps(m.group(1))
                        at[0].need(st, fields)
                        inner = (at[0], st)
        if kind == "KV" and not p.get("columns") and inner is not None:
            for f in ("NAME", "CODE", "AMOUNT"):
                inner[0].need(inner[1] + [(f, None)] if inner[1] else [(f, None)])
        if kind == "GRAPH":
            gl = {}
            for key, fs in (("nodes", ["id", "kind", opts.get("label") or "label", opts.get("group") or "type"]), ("edges", ["from", "to"])):
                m = PATH.search(str(opts.get(key) or ""))
                if m:
                    target.need(steps(m.group(1)), [[(f, None)] for f in fs if isinstance(f, str) and BARE.match(f)])
                    gl[key] = steps(m.group(1))
            if len(gl) == 2:
                self.graphs.append((target, gl["nodes"], gl["edges"]))
        if inner is None and at is not None and p.get("kind") not in ("TABS",):
            for f in fields:                                            # a kv or metric of a tab: its @.fields are the element's
                st = at[1][:-1] + [(at[1][-1][0], "[*]")] + f if at[1] else f
                at[0].need(st)
        for t in texts:
            for m in PATH.finditer(t):
                st = steps(m.group(1))
                if st and not (inner and st == inner[1]):
                    target.need(st)
            if src_kind:
                for m in SRC.finditer(t):
                    st = steps(m.group(1))
                    if st:
                        self.kind(src_kind).need(st)
            for m in LINK.finditer(t):
                self.links.setdefault(m.group(1), m.group(2))
        if p.get("kind") == "MAP":
            for key in ("link", "shade"):
                o = opts.get(key)
                if isinstance(o, dict) and isinstance(o.get("kind"), str):
                    n = self.kind(o["kind"])
                    for v in o.values():
                        for m in PATH.finditer(str(v)):
                            n.need(steps(m.group(1)))
        for part in p.get("parts") or []:
            self.panel(part, target, inner or at, src_kind)
        for ch in wiring.get("children") or []:
            csrc = ch.get("source")
            ck = csrc.get("kind") if isinstance(csrc, dict) and isinstance(csrc.get("kind"), str) else src_kind
            if ck:
                self.kind(ck)
            for part in ch.get("panels") or []:
                self.panel(part, target, inner or at, ck)

    def sutra(self, s):
        texts = []
        strings({k: v for k, v in s.items() if k != "panels"}, texts)
        for t in texts:
            for m in PATH.finditer(t):
                st = steps(m.group(1))
                if st:
                    self.root.need(st)
            for m in LINK.finditer(t):
                self.links.setdefault(m.group(1), m.group(2))
        for c in s.get("controls") or []:
            if isinstance(c.get("lookup"), str):
                self.kind(c["lookup"])
        for p in s.get("panels") or []:
            self.panel(p, self.root, None, None)
        for f, k in self.links.items():
            self.root.need([(f, None)])
            self.kind(k)

    @staticmethod
    def at(doc, st):
        for name, _ in st:
            doc = doc.get(name) if isinstance(doc, dict) else None
        return doc

    def join_graphs(self, target, doc):
        """A graph's edges between its nodes: each edge from the first node to the next ones (made-up ids would join nothing)."""
        for t, nodes_at, edges_at in self.graphs:
            if t is target:
                nodes, edges = self.at(doc, nodes_at), self.at(doc, edges_at)
                if isinstance(nodes, list) and isinstance(edges, list) and len(nodes) > 1:
                    for j, e in enumerate(edges):
                        if isinstance(e, dict):
                            e["from"] = nodes[0].get("id")
                            e["to"] = nodes[1 + j % (len(nodes) - 1)].get("id")

    def data(self, text):
        rec = self.root.make()
        self.join_graphs(self.root, rec)
        for t in [text]:                                                # the Sutra's own text too: what the model does not hold
            try:
                doc = yaml.safe_load(t)
            except yaml.YAMLError:
                doc = None
            all_text = []
            texts(doc, all_text)
            for x in all_text:
                for m in PATH.finditer(x):
                    st = steps(m.group(1))
                    if st:
                        put(rec, st, [])
        ents = {}
        for k in sorted(self.kinds):
            ents[k] = [{"id": f"{k.upper()}-{i + 1}", "title": f"{k.replace('-', ' ').title()} {i + 1}", "data": self.kinds[k].make(i)}
                       for i in range(ENTITIES)]
        return {"record": rec, "entities": ents, "links": dict(sorted(self.links.items()))}


def synthetic(expanded, text):
    """The synthetic data of one Sutra file: from the reference's parse when it has one, else from the text alone (record())."""
    if expanded is None:
        return {"record": record(text), "entities": {}, "links": {}}
    sy = Synth()
    sy.sutra(expanded)
    return sy.data(text)


HOST_KINDS = ("provenance", "links")


def host_panels(view_text):
    """The answers of the host's own panel binders in a reference view (the profile's kinds: provenance, links), as recorded calls
    {hook: panel, args: [kind, id], answer: the panel's data}, panels of tabs included."""
    calls = []

    def walk(panels):
        for p in panels or []:
            if p.get("kind") in HOST_KINDS and "data" in p:
                calls.append({"hook": "panel", "args": [p["kind"], p["id"]], "answer": p["data"]})
            for t in ((p.get("data") or {}).get("tabs") or []) if isinstance(p.get("data"), dict) else []:
                walk(t.get("panels"))
    walk(json.loads(view_text).get("panels"))
    return calls


# ---- what the recorded views hold: filled and empty panels by kind (gate finding F2's bar) -----------------------------------------

# the data a panel of each kind shows; a panel whose data is missing (an error) or whose list is empty is empty
FILLED = {"table": "rows", "ladder": "rows", "hbar": "bars", "timeline": "events", "links": "links", "pivot": "rows", "kv": "fields",
          "status": "fields", "provenance": "fields", "tabs": "tabs", "candlestick": "candles", "histogram": "bins", "scatter": "points",
          "waterfall": "steps", "graph": "nodes", "markdown": "text"}
NOT_DATA = ("markdown", "provenance", "links", "tabs")                 # the host's and the text panels: not data panels


def filled(p):
    d = p.get("data")
    if not isinstance(d, dict):
        return False
    k = p["kind"]
    if k in ("line", "area"):
        return bool(d.get("x")) and any(s.get("values") for s in d.get("series") or [])
    if k == "surface":
        return bool(d.get("z"))
    if k == "metric":
        return d.get("value") not in (None, "", "—")
    if k == "map":
        return bool(d.get("highlight") or d.get("shade"))
    return bool(d.get(FILLED.get(k, "")))


def panel_report(binds):
    """Per panel kind, panels filled and empty in the recorded views (panels of tabs included), and the share of data panels empty."""
    counts = {}
    views = no_view = all_empty = 0
    for v in sorted(Path(binds).rglob("*.view.json")):
        views += 1
        data = []

        def walk(panels):
            for p in panels or []:
                c = counts.setdefault(p["kind"], [0, 0])
                c[0 if filled(p) else 1] += 1
                if p["kind"] not in NOT_DATA:
                    data.append(filled(p))
                d = p.get("data")
                for t in (d.get("tabs") or []) if isinstance(d, dict) else []:
                    walk(t.get("panels"))
        walk(json.loads(v.read_text(encoding="utf-8")).get("panels"))
        if data and not any(data):
            all_empty += 1
    no_view = len(list(Path(binds).rglob("*.error.txt")))
    lines = [f"{'kind':<12} {'filled':>7} {'empty':>7}"]
    df = de = 0
    for k in sorted(counts):
        f, e = counts[k]
        lines.append(f"{k:<12} {f:>7} {e:>7}")
        if k not in NOT_DATA:
            df, de = df + f, de + e
    lines.append(f"data panels: {df + de}, empty {de} ({100.0 * de / max(1, df + de):.1f}%); views {views}, every data panel empty "
                 f"in {all_empty}; no reference view {no_view}")
    return "\n".join(lines)


def freshness(view_text):
    """The router's freshness of each kind a wired panel read (its panel provenance: the source, last update, threshold, stale), as
    recorded calls {hook: source.freshness, args: [kind], answer}: the host's settings, one answer per kind."""
    out = {}

    def walk(panels):
        for p in panels or []:
            pv = p.get("provenance")
            if isinstance(pv, dict) and pv.get("kind"):
                out[pv["kind"]] = {"source": pv.get("source"), "lastUpdate": pv.get("updatedAt"), "staleAfter": pv.get("staleAfter"),
                                   "stale": bool(pv.get("stale"))}
            d = p.get("data")
            for t in (d.get("tabs") or []) if isinstance(d, dict) else []:
                walk(t.get("panels"))
    walk(json.loads(view_text).get("panels"))
    return [{"hook": "source.freshness", "args": [k], "answer": out[k]} for k in sorted(out)]


def sha384(b):
    return hashlib.sha384(b).hexdigest()


def sh(cmd, **kw):
    print("+", " ".join(str(c) if len(str(c)) < 200 else "<class path>" for c in cmd), flush=True)
    return subprocess.run(cmd, check=True, **kw)


def engine_classpath(drishti, tag, scratch, java_home):
    """Drishti's engine class path at `tag`: a scratch clone (the user's checkout is only read), built offline, never installed."""
    wt = scratch / "drishti"
    sh(["git", "clone", "--quiet", "--no-local", "--no-checkout", str(drishti), str(wt)])
    sh(["git", "-C", str(wt), "-c", "advice.detachedHead=false", "checkout", "--quiet", tag], env=dict(os.environ, GIT_LFS_SKIP_SMUDGE="1"))
    cp = scratch / "cp-engine.txt"
    sh(["mvn", "-o", "-q", "package", "dependency:build-classpath", "-pl", "kernel/drishti-engine", "-am", "-DskipTests",
        f"-Dmdep.outputFile={cp}", "-Dmdep.includeScope=runtime"], cwd=wt, env=dict(os.environ, JAVA_HOME=java_home))
    jar = next(j for j in sorted((wt / "kernel/drishti-engine/target").glob("*.jar")) if not j.name.endswith(("-sources.jar", "-tests.jar")))
    return str(jar) + os.pathsep + cp.read_text().strip()


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--drishti", help="a Drishti clone (read only: cloned from, never written)")
    ap.add_argument("--tag", default="v2.9.11.i_7a")
    ap.add_argument("--corpus", default=str(ROOT / "compat/customers/drishti/2.9.11.i7a"))
    ap.add_argument("--classpath-file", help="the engine class path at the tag, built already (skips the scratch build)")
    ap.add_argument("--java-home", default=os.environ.get("RACHANA_JAVA_HOME", "/usr/lib/jvm/java-21-openjdk-amd64"))
    ap.add_argument("--from-binds", action="store_true", help="reuse the reference's views already in binds/ (rewrite the recordings and the manifest only)")
    ap.add_argument("--scratch", help="where the scratch folder is made (default: the system's temporary folder); removed at the end")
    ap.add_argument("--report", action="store_true", help="only print the filled and empty panels by kind of the recorded views")
    ap.add_argument("--keep", action="store_true", help="keep the scratch folder (to look at the expanded Sutras and the records)")
    a = ap.parse_args(argv)
    corpus = Path(a.corpus)
    if a.report:
        print(panel_report(corpus / "binds"))
        return
    scratch = Path(tempfile.mkdtemp(prefix="rachana-binds-", dir=a.scratch))
    try:
        if a.from_binds:
            cp = None
        elif a.classpath_file:
            cp = Path(a.classpath_file).read_text().strip()
        elif a.drishti:
            cp = engine_classpath(Path(a.drishti).expanduser(), a.tag, scratch, a.java_home)
        else:
            ap.error("give --drishti or --classpath-file")
        tree = scratch / "tree"
        shutil.copytree(corpus / "sutras", tree, dirs_exist_ok=True)
        shutil.copytree(corpus / "blocks", tree, dirs_exist_ok=True)
        shutil.copytree(corpus / "blocks/ui-kit", scratch / "kit/fragments", dirs_exist_ok=True)
        shutil.rmtree(tree / "ui-kit", ignore_errors=True)
        shutil.copy(corpus / "groups.json", scratch / "groups.json")
        groups = json.loads((corpus / "groups.json").read_text())["groups"]
        binds = corpus / "binds"
        if a.from_binds:                                                # the recordings as they are: the manifest only
            n = sum(len(g["sutras"]) for g in groups)
        else:
            java = str(Path(a.java_home) / "bin" / "java")
            formats = [str(ROOT / "spec/1/formats.yaml"),
                       os.environ.get("RACHANA_DRISHTI_FORMATS", str(ROOT / "tools/conformance/profile/drishti-formats.yaml"))]
            exp = scratch / "expanded"
            sh([java, "-cp", cp, str(ROOT / "tools/regime/CorpusBind.java"), "expand", str(scratch), str(exp)] + formats)
            records = {}
            for g in groups:
                for f in g["sutras"]:
                    e = Path(str(exp / f) + ".sutra.json")
                    records[f] = synthetic(json.loads(e.read_text(encoding="utf-8")) if e.is_file() else None,
                                           (corpus / "sutras" / f).read_text(encoding="utf-8"))
            (scratch / "records.json").write_text(json.dumps(records), encoding="utf-8")
            out = scratch / "out"
            sh([java, "-cp", cp, str(ROOT / "tools/regime/CorpusBind.java"), "bind", str(scratch), str(out)] + formats)
            shutil.rmtree(binds, ignore_errors=True)
            n = 0
            for g in groups:
                for f in g["sutras"]:
                    view = Path(str(out / f) + ".view.json")
                    calls = Path(str(out / f) + ".calls.json")
                    rec = {"file": f, "group": g["dir"], "hostCalls": [
                        {"hook": "record", "args": [], "answer": records[f]["record"]},
                        {"hook": "calendar.today", "args": [], "answer": TODAY}]
                        + (json.loads(calls.read_text(encoding="utf-8")) if calls.is_file() else [])
                        + (host_panels(view.read_text(encoding="utf-8")) + freshness(view.read_text(encoding="utf-8")) if view.is_file() else [])}
                    dst = binds / f
                    dst.parent.mkdir(parents=True, exist_ok=True)
                    Path(str(dst) + ".json").write_text(json.dumps(rec, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
                    for ext in (".view.json", ".error.txt"):
                        src = Path(str(out / f) + ext)
                        if src.is_file():
                            shutil.copyfile(src, str(dst) + ext)
                    n += 1
        v = json.loads((corpus / "VERSION.json").read_text())
        v["binds"] = n
        (corpus / "VERSION.json").write_text(json.dumps(v, indent=1) + "\n")
        lines = [f"{sha384(p.read_bytes())}  {p.relative_to(corpus).as_posix()}" for p in sorted(corpus.rglob("*"))
                 if p.is_file() and p.name != "MANIFEST.sha384"]
        (corpus / "MANIFEST.sha384").write_text("\n".join(lines) + "\n")
        print(f"recorded {n} binds into {binds}")
        print(panel_report(binds))
    finally:
        if a.keep:
            print(f"kept {scratch}")
        else:
            shutil.rmtree(scratch, ignore_errors=True)


if __name__ == "__main__":
    main()
