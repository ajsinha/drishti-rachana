#!/usr/bin/env python3
# Copyright (c) 2026 Ashutosh Sinha. Proprietary: see LICENSE.
"""The customer-corpus export (compatibility regime CG3, SUTRA_EXPANSION 6.6): drishti-compat-corpus-<drishti version>.

Reads a Drishti checkout READ-ONLY (git plumbing at a tag: ls-tree and cat-file; nothing is checked out, written or run inside it)
and Drishti's reference jars, and writes compat/customers/drishti/<version>/:

  sutras/ blocks/        every shipped Sutra and block, byte for byte, at its path in Drishti (synthetic demo data only)
  groups.json            which files form one pack for the block library (a Sutra sees the blocks of its own group and the UI kit)
  compiled/<file>.json   per Sutra: problem list, canonical model text, plan (null in i7a: the reference has no plan)
  problems/ cases/       the Drishti-profile conformance cases of this repository (problem cases; every other kind)
  profile/               the declarative half of the profile (spec/1/rachana.lang.json entries owned by the profile), level.txt
  binds/                 written by corpus_binds.py (the reference binder over synthetic records, gate finding F13)
  MANIFEST.sha384        a hash per file

    python3 tools/regime/corpus_export.py --drishti ~/IdeaProjects/drishti --classpath-file CP.txt [--tag v2.9.11.i_7a]
"""
import argparse, hashlib, json, os, re, shutil, subprocess, sys, tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
INCLUDE = re.compile(r"^(modules/[^/]+/packs/|config/packs/|examples/packs/|kernel/drishti-server/src/test/resources/sutra-next/)")
KIT = "config/ui-kit/fragments/"


def git(drishti, *args, binary=False):
    r = subprocess.run(["git", "-C", str(drishti), *args], check=True, capture_output=True)
    return r.stdout if binary else r.stdout.decode()


def read_blobs(drishti, tag, paths):
    """One cat-file --batch process: the blobs at the tag, never a checkout."""
    p = subprocess.run(["git", "-C", str(drishti), "cat-file", "--batch"], input="".join(f"{tag}:{x}\n" for x in paths).encode(),
                       capture_output=True, check=True)
    out, i = {}, 0
    buf = p.stdout
    for x in paths:
        nl = buf.index(b"\n", i)
        head = buf[i:nl].split()
        if len(head) != 3:
            sys.exit(f"cannot read {x} at {tag}")
        size = int(head[2])
        out[x] = buf[nl + 1:nl + 1 + size]
        i = nl + 1 + size + 1
    return out


def group_of(path):
    if "/sutras/" in path:
        return path[:path.index("/sutras/")]
    m = re.match(r"(.*/sutra-next)(/[^/]+)?/", path)
    if m:
        return m.group(1) + (m.group(2) or "")
    return str(Path(path).parent)


def sha384(b):
    return hashlib.sha384(b).hexdigest()


def drishti_cases():
    """The Drishti-profile conformance cases of this repository, by folder: problem cases and the others."""
    probs, others = [], []
    for f in sorted((ROOT / "conformance" / "1").rglob("*.yaml")):
        head = "\n".join(f.read_text(encoding="utf-8").splitlines()[:12])
        m = re.search(r"^profile:\s*(.*)$", head, re.M)
        if m and "drishti" in m.group(1):
            (probs if f.parent.name == "problem" else others).append(f)
    return probs, others


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--drishti", required=True, help="a Drishti checkout, read only")
    ap.add_argument("--tag", default="v2.9.11.i_7a")
    ap.add_argument("--version", help="corpus folder name; default: the tag as the jars name it (v2.9.11.i_7a -> 2.9.11.i7a)")
    ap.add_argument("--classpath-file", required=True, help="Drishti's reference jars and libraries, one class path (tools/reference-bridge/README.md)")
    ap.add_argument("--java", default=os.environ.get("RACHANA_JAVA", "java"), help="a Java 21 launcher")
    ap.add_argument("--out", help="default compat/customers/drishti/<version>")
    a = ap.parse_args()
    drishti = Path(a.drishti).expanduser().resolve()
    version = a.version or re.sub(r"^v", "", a.tag).replace("_", "")
    out = Path(a.out) if a.out else ROOT / "compat" / "customers" / "drishti" / version
    names = git(drishti, "ls-tree", "-r", "--name-only", a.tag).splitlines()
    shipped = [n for n in names if INCLUDE.match(n) and re.search(r"\.(sutra|block|fragment)\.yaml$", n)]
    nameset = set(names)
    shipped += sorted(x for x in {group_of(n) + "/pack.yaml" for n in shipped} if x in nameset)   # the pack's name: namespaces its blocks
    kit = [n for n in names if n.startswith(KIT)]
    if not shipped or not kit:
        sys.exit(f"nothing to export at {a.tag}")
    blobs = read_blobs(drishti, a.tag, shipped + kit)
    if out.exists():
        shutil.rmtree(out)
    scratch = Path(tempfile.mkdtemp(prefix="rachana-corpus-"))
    try:
        groups = {}
        for n in shipped:
            kind = "sutras" if n.endswith(".sutra.yaml") else "blocks"
            if n.endswith("/pack.yaml"):
                for base in (out / kind, scratch / "tree"):
                    (base / n).parent.mkdir(parents=True, exist_ok=True)
                    (base / n).write_bytes(blobs[n])
                continue
            for base in (out / kind, scratch / "tree"):
                (base / n).parent.mkdir(parents=True, exist_ok=True)
                (base / n).write_bytes(blobs[n])
            g = groups.setdefault(group_of(n), {"dir": group_of(n), "pack": Path(group_of(n)).name, "sutras": [], "blocks": []})
            g[kind].append(n)
        for n in kit:
            for base in (out / "blocks" / "ui-kit", scratch / "kit" / "fragments"):
                (base / n[len(KIT):]).parent.mkdir(parents=True, exist_ok=True)
                (base / n[len(KIT):]).write_bytes(blobs[n])
        gl = [dict(g, sutras=sorted(g["sutras"]), blocks=sorted(g["blocks"])) for _, g in sorted(groups.items()) if g["sutras"]]
        (scratch / "groups.json").write_text(json.dumps({"groups": gl}, indent=1))
        (out).mkdir(parents=True, exist_ok=True)
        shutil.copy(scratch / "groups.json", out / "groups.json")
        cp = Path(a.classpath_file).read_text().strip()
        r = subprocess.run([a.java, "-cp", cp, str(ROOT / "tools/regime/CorpusCompile.java"), str(scratch), str(out)], capture_output=True, text=True)
        sys.stdout.write(r.stdout)
        if r.returncode:
            sys.exit(r.stderr)
        probs, others = drishti_cases()
        for kind, files in (("problems", probs), ("cases", others)):
            for f in files:
                d = out / kind / f.relative_to(ROOT / "conformance" / "1")
                d.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy(f, d)
        lang = json.loads((ROOT / "spec/1/rachana.lang.json").read_text(encoding="utf-8"))
        (out / "profile").mkdir()
        (out / "level.txt").write_text(lang["language"] + "\n")
        (out / "profile" / "README.txt").write_text(
            "The declarative half of Drishti's profile is the set of entries of spec/1/rachana.lang.json whose profile is not 'core'\n"
            "(compat/reserved-names.yaml lists them; tools/regime/reserved_names.py regenerates it). capabilities.json arrives with the painter.\n")
        (out / "VERSION.json").write_text(json.dumps({"corpus": f"drishti-compat-corpus-{version}", "drishtiTag": a.tag,
            "drishtiCommit": git(drishti, "rev-parse", a.tag + "^{commit}").strip(), "language": lang["language"],
            "sutras": sum(len(g["sutras"]) for g in gl), "blocks": sum(len(g["blocks"]) for g in gl) + len(kit),
            "plan": "none in the i7a reference (plan cases arrive with Core)", "binds": "later (v0.10.0)"}, indent=1) + "\n")
        lines = [f"{sha384(p.read_bytes())}  {p.relative_to(out).as_posix()}" for p in sorted(out.rglob("*")) if p.is_file() and p.name != "MANIFEST.sha384"]
        (out / "MANIFEST.sha384").write_text("\n".join(lines) + "\n")
        print(f"wrote {out} ({len(lines)} files)")
    finally:
        shutil.rmtree(scratch, ignore_errors=True)


if __name__ == "__main__":
    main()
