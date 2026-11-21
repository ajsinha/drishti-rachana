#!/usr/bin/env python3
# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""Builds the JavaScript bundles (split step A15): java/rachana-js with TeaVM (./mvnw -Pjs), copies rachana.runtime.js and
rachana.tools.js into js/packages/rachana/dist/ (git-ignored) and the Python package's bundle folder, and prints each bundle's
size (raw, gzip 9, Brotli 11) and SHA-256 (decision P1-3: recorded in the release notes of every release). Then it runs the
conformance suite in Node on BOTH bundles, at every language level (check_bundles below), and fails on any error or verified failing
case, or when the runtime bundle answers a case differently from the tools bundle (G1 R1: a runtime bundle broken by TeaVM was
shipped once because nothing ran it).

Usage: python3 tools/js/build_js.py [--java-home <JDK 21>] [--opt ADVANCED|SIMPLE|FULL] [--no-minify] [--bench] [--json <file>]
       python3 tools/js/build_js.py --check-only     (the check alone, on the bundles in js/packages/rachana/dist)"""
import argparse, gzip, hashlib, json, os, shutil, subprocess, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BUNDLES = ("rachana.runtime.js", "rachana.tools.js")

def bundle_levels(dist: Path) -> list[str]:
    """The language levels the tools bundle reads (Core's Grammar.DEFAULT_LEVEL and LEVELS: the one place), oldest first."""
    js = ("import(process.argv[1]).then((m) => { m.init('core', '[]'); process.stdout.write(m.levels()); });")
    out = subprocess.run(["node", "-e", js, (dist / "rachana.tools.js").resolve().as_uri()], check=True, capture_output=True, text=True).stdout
    return json.loads(out)["levels"]
CONFORMANCE = ROOT / "tools" / "conformance" / "rachana_conformance.py"


def brotli_size(path: Path) -> int:
    """Brotli quality 11 through Node's zlib (no Python wheel needed)."""
    js = ("const z=require('zlib'),f=require('fs');const b=f.readFileSync(process.argv[1]);"
          "process.stdout.write(String(z.brotliCompressSync(b,{params:{[z.constants.BROTLI_PARAM_QUALITY]:11,"
          "[z.constants.BROTLI_PARAM_SIZE_HINT]:b.length}}).length))")
    return int(subprocess.run(["node", "-e", js, str(path)], check=True, capture_output=True, text=True).stdout)


SIZE_GUARD = ROOT / "tools" / "js" / "bundle-size-guard.json"


def size_guard(sizes: dict[str, int]) -> list[str]:
    """The size guard (tools/js/bundle-size-guard.json): the problems found; a bundle over its 'max' (Brotli 11) is one. Bigger is allowed
    when deliberate: the guard file is changed in the same commit, with its reason."""
    guard = json.loads(SIZE_GUARD.read_text(encoding="utf-8"))["bundles"]
    out = []
    for b, n in sizes.items():
        g = guard.get(b)
        if g is None:
            out.append(f"{b} has no entry in {SIZE_GUARD.name}")
        elif n > g["max"]:
            out.append(f"{b} is {n:,} bytes (Brotli 11), over its guard of {g['max']:,} (measured {g['measured']:,} + 5%): if the growth is deliberate, "
                       f"update {SIZE_GUARD.relative_to(ROOT)} in the same commit with the reason")
    return out


def check_bundles(dist: Path, work: Path) -> int:
    """Runs the conformance suite on both bundles at every level. Fails (1) on any error or verified failing case in either bundle,
    on a case the runtime bundle answers differently from the tools bundle, or on a case only the tools bundle answers whose kind
    the runtime serves (only edit and live, the declaration checker of Studio and lint, are the tools bundle's alone, by design)."""
    work.mkdir(parents=True, exist_ok=True)
    bad = 0
    for level in bundle_levels(dist):
        res = {}
        for b in BUNDLES:
            out = work / f"{b}-{level}.jsonl"
            summ = work / f"{b}-{level}.summary.json"
            subprocess.run([sys.executable, str(CONFORMANCE), "--node", "--bundle", str(dist / b), "--profiles", "core,drishti",
                            "--language", level, "--out", str(out), "--summary", str(summ)], cwd=ROOT, check=False,
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            if not summ.is_file() or not out.is_file():
                print(f"conformance {b} {level}: no result (the runner stopped)", file=sys.stderr)
                bad += 1
                continue
            sm = json.loads(summ.read_text(encoding="utf-8"))
            verified = [f for f in sm["failures"] if f["verified"]]
            errors = [f for f in sm["failures"] if f["status"] == "error"]
            print(f"conformance {b} {level}: {sm['cases']} cases, pass {sm['pass']}, unsupported {sm['unsupported']}, "
                  f"error {sm['error']}, verified failing {len(verified)}")
            for f in (errors + [v for v in verified if v not in errors])[:20]:
                print(f"  {f['status']}: {f['case']}: {f['detail'][:200]}", file=sys.stderr)
            bad += 1 if errors or verified else 0
            res[b] = {json.loads(ln)["case"]: (json.loads(ln), ln) for ln in out.read_text(encoding="utf-8").splitlines() if ln}
        if len(res) < 2:
            continue
        rt, tools = res[BUNDLES[0]], res[BUNDLES[1]]
        differ = [c for c, (r, ln) in rt.items() if r["status"] != "unsupported" and (c not in tools or tools[c][1] != ln)]
        missing = [c for c, (r, _) in rt.items() if r["status"] == "unsupported" and tools.get(c, ({},))[0].get("status") != "unsupported"
                   and r["kind"] not in ("edit", "live")]
        for c in differ[:20]:
            print(f"  runtime differs from tools: {c}", file=sys.stderr)
        for c in missing[:20]:
            print(f"  runtime does not answer: {c}", file=sys.stderr)
        print(f"conformance {level}: runtime = tools on every case the runtime answers: {'yes' if not differ and not missing else 'NO'} "
              f"({len(differ)} differ, {len(missing)} unanswered)")
        bad += 1 if differ or missing else 0
    return 1 if bad else 0


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--java-home", default=os.environ.get("RACHANA_JAVA_HOME", "/usr/lib/jvm/java-21-openjdk-amd64"))
    ap.add_argument("--opt", default="ADVANCED")
    ap.add_argument("--no-minify", action="store_true")
    ap.add_argument("--json", help="also write the sizes and hashes here")
    ap.add_argument("--bench", action="store_true", help="also build rachana.bench.js (the measuring entry) into java/rachana-js/target/js")
    ap.add_argument("--check-only", action="store_true", help="no build: run the conformance check on the bundles in js/packages/rachana/dist")
    a = ap.parse_args(argv)
    dist = ROOT / "js" / "packages" / "rachana" / "dist"
    work = ROOT / "java" / "rachana-js" / "target" / "js-conformance"
    if a.check_only:
        return check_bundles(dist, work)
    env = dict(os.environ, JAVA_HOME=a.java_home)
    out = ROOT / "java" / "rachana-js" / "target" / "js"
    cmd = ["./mvnw", "-q", "-o", "-Pjs", "clean", "package", "-DskipTests", "-pl", "rachana-js", "-am",
           f"-Drachana.js.opt={a.opt}", f"-Drachana.js.minify={'false' if a.no_minify else 'true'}", f"-Drachana.js.bench={'bench' if a.bench else 'none'}"]
    subprocess.run(cmd, cwd=ROOT / "java", env=env, check=True)
    report = {}
    for dest in (ROOT / "js" / "packages" / "rachana" / "dist", ROOT / "python" / "src" / "drishti_rachana" / "bundle"):
        dest.mkdir(parents=True, exist_ok=True)
        for b in BUNDLES:
            shutil.copyfile(out / b, dest / b)
    for b in BUNDLES:
        data = (out / b).read_bytes()
        report[b] = {"raw": len(data), "gzip9": len(gzip.compress(data, 9, mtime=0)), "brotli11": brotli_size(out / b),
                     "sha256": hashlib.sha256(data).hexdigest()}
        r = report[b]
        print(f"{b}: raw {r['raw']:,} · gzip {r['gzip9']:,} · brotli {r['brotli11']:,} · sha256 {r['sha256']}")
    # the Python engine checks every bundle against this file before it runs it (python/src/drishti_rachana/engine.py)
    sums = "".join(f"{report[b]['sha256']}  {b}\n" for b in BUNDLES)
    (ROOT / "python" / "src" / "drishti_rachana" / "bundle" / "SHA256SUMS").write_text(sums, encoding="utf-8")
    over = size_guard({b: report[b]["brotli11"] for b in BUNDLES})
    for m in over:
        print("build_js: size guard: " + m, file=sys.stderr)
    if a.json:
        Path(a.json).write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    if check_bundles(dist, work):
        print("build_js: the conformance check FAILED (above); do not ship these bundles", file=sys.stderr)
        return 1
    return 1 if over else 0


if __name__ == "__main__":
    sys.exit(main())
