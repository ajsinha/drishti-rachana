#!/usr/bin/env python3
# Copyright (c) 2026 Ashutosh Sinha. Proprietary: see LICENSE.
"""The bind half of the customer-corpus export (CG3, SUTRA_EXPANSION 6.6, gate finding F13): compat/customers/drishti/<version>/binds/.

Per shipped Sutra of the corpus, a synthetic record is made from the Sutra's own text (every `$.path` it reads, lists where it reads
rows or `[*]`, each row's fields from the `@.FIELD`s of the panel that reads it; values made up from the field's name: dates, texts,
numbers; no data of any customer or source), and the corpus release's reference binder binds the Sutra over it
(tools/regime/CorpusBind.java over Drishti's engine jars at the corpus's tag: a scratch clone, read only, removed at the end).
Written per Sutra file:

  binds/<file>.json        the recording: {file, group, hostCalls: [record, calendar.today]} (RecordedHost's format)
  binds/<file>.view.json   the ViewModel exactly as the reference wrote it, or
  binds/<file>.error.txt   why the reference could not bind it

and the corpus's MANIFEST.sha384 and VERSION.json ("binds": the count) are rewritten. CustomerCorpusTest replays every recording
through Core's binder and compares byte for byte; tools/split/known-differences.txt lists the accepted differences, each with a reason.

    python3 tools/regime/corpus_binds.py --drishti ~/IdeaProjects/drishti [--tag v2.9.11.i_7a] [--classpath-file CP-ENGINE.txt]
"""
import argparse, hashlib, json, os, re, shutil, subprocess, sys, tempfile
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
TODAY = "2026-01-15"
ROWS = 3

PATH = re.compile(r"(?<![A-Za-z0-9_@])\$((?:\.[A-Za-z_][A-Za-z0-9_-]*(?:\[(?:\*|\d+)\])?)+)")
FIELD = re.compile(r"(?<![A-Za-z0-9_$])@((?:\.[A-Za-z_][A-Za-z0-9_-]*(?:\[(?:\*|\d+)\])?)+)")
STEP = re.compile(r"\.([A-Za-z_][A-Za-z0-9_-]*)(\[(?:\*|\d+)\])?")
DATE = re.compile(r"(DATE|_DT$|^DT_|TIME|MATURITY|EXPIRY|AS_OF|ASOF|SETTLE|START|END|FROM|UNTIL|day$|Date$|date$|At$)", re.I)
TEXT = re.compile(r"(ID$|_ID|Id$|NAME|Name|CODE|Code|TYPE|Type|STATUS|Status|CCY|CURRENCY|Currency|SIDE|Side|DESK|Desk|BOOK|Book|DESC|LABEL|"
                  r"Label|KIND|Kind|COUNTRY|Country|REGION|Region|SECTOR|Sector|ISIN|CUSIP|TICKER|SYMBOL|TEXT|Text|TITLE|Title|URL|EMAIL|"
                  r"NOTE|Note|COMMENT|REASON|Reason|SOURCE|Source|SYSTEM|TRADER|Trader|COUNTERPARTY|Counterparty|ENTITY|Entity|LEI|"
                  r"RATING|Rating|TENOR|Tenor|INDEX|Index|PRODUCT|Product|METHOD|Method|ROLE|Role|USER|User|GROUP|Group|CATEGORY|LEVEL|"
                  r"FLAG|Flag|MODE|Mode|STATE|State|CLASS|Class|^id$|^name$|^code$|^type$|^status$|^kind$|^key$|^label$)")


def value(name, i):
    """A made-up value for field `name` of row i: a date, a text or a number, from the name alone (deterministic)."""
    h = int(hashlib.sha256(name.encode()).hexdigest()[:6], 16)
    if DATE.search(name):
        return f"2026-0{1 + (h + i) % 9}-{10 + (h + 3 * i) % 18:02d}"
    if TEXT.search(name):
        return f"{name.upper()[:12]}-{chr(65 + (h + i) % 26)}{i + 1}"
    n = (h % 90000) / 100 + 100 * i - (250 if (h + i) % 3 == 0 else 0)
    return int(n) if h % 2 == 0 else round(n, 2)


def put(doc, steps, leaf_fields, i=0):
    """Sets the path `steps` ([(name, '[*]' or None)]) in doc, lists of ROWS where a step reads rows; the leaf a made-up value, or rows of
    `leaf_fields` when the path is a rows path."""
    name, idx = steps[0]
    last = len(steps) == 1
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


def sha384(b):
    return hashlib.sha384(b).hexdigest()


def sh(cmd, **kw):
    print("+", " ".join(map(str, cmd)), flush=True)
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
    a = ap.parse_args(argv)
    corpus = Path(a.corpus)
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
        records = {}
        for g in groups:
            for f in g["sutras"]:
                records[f] = record((corpus / "sutras" / f).read_text(encoding="utf-8"))
        (scratch / "records.json").write_text(json.dumps(records), encoding="utf-8")
        out = scratch / "out"
        if a.from_binds:
            shutil.copytree(corpus / "binds", out)
        else:
            java = str(Path(a.java_home) / "bin" / "java")
            sh([java, "-cp", cp, str(ROOT / "tools/regime/CorpusBind.java"), str(scratch), str(out), str(ROOT / "spec/1/formats.yaml"),
                os.environ.get("RACHANA_DRISHTI_FORMATS", str(ROOT / "tools/conformance/profile/drishti-formats.yaml"))])
        binds = corpus / "binds"
        shutil.rmtree(binds, ignore_errors=True)
        n = 0
        for g in groups:
            for f in g["sutras"]:
                view = Path(str(out / f) + ".view.json")
                rec = {"file": f, "group": g["dir"], "hostCalls": [
                    {"hook": "record", "args": [], "answer": records[f]},
                    {"hook": "calendar.today", "args": [], "answer": TODAY}]
                    + (host_panels(view.read_text(encoding="utf-8")) if view.is_file() else [])}
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
    finally:
        shutil.rmtree(scratch, ignore_errors=True)


if __name__ == "__main__":
    main()
