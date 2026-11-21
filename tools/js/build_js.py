#!/usr/bin/env python3
# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""Builds the JavaScript bundles (split step A15): java/rachana-js with TeaVM (./mvnw -Pjs), copies rachana.runtime.js and
rachana.tools.js into js/packages/rachana/dist/ (git-ignored) and the Python package's bundle folder, and prints each bundle's
size (raw, gzip 9, Brotli 11) and SHA-256 (decision P1-3: recorded in the release notes of every release).

Usage: python3 tools/js/build_js.py [--java-home <JDK 21>] [--opt ADVANCED|SIMPLE|FULL] [--no-minify] [--bench] [--json <file>]"""
import argparse, gzip, hashlib, json, os, shutil, subprocess, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BUNDLES = ("rachana.runtime.js", "rachana.tools.js")


def brotli_size(path: Path) -> int:
    """Brotli quality 11 through Node's zlib (no Python wheel needed)."""
    js = ("const z=require('zlib'),f=require('fs');const b=f.readFileSync(process.argv[1]);"
          "process.stdout.write(String(z.brotliCompressSync(b,{params:{[z.constants.BROTLI_PARAM_QUALITY]:11,"
          "[z.constants.BROTLI_PARAM_SIZE_HINT]:b.length}}).length))")
    return int(subprocess.run(["node", "-e", js, str(path)], check=True, capture_output=True, text=True).stdout)


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--java-home", default=os.environ.get("RACHANA_JAVA_HOME", "/usr/lib/jvm/java-21-openjdk-amd64"))
    ap.add_argument("--opt", default="ADVANCED")
    ap.add_argument("--no-minify", action="store_true")
    ap.add_argument("--json", help="also write the sizes and hashes here")
    ap.add_argument("--bench", action="store_true", help="also build rachana.bench.js (the measuring entry) into java/rachana-js/target/js")
    a = ap.parse_args(argv)
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
    if a.json:
        Path(a.json).write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())
