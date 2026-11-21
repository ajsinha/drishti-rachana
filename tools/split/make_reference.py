#!/usr/bin/env python3
# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""Rebuild the reference results (ref.jsonl) of the split proof from a given Drishti commit, so that compare_results.py can be
repeated by anyone. Drishti is read, never changed: the commit is checked out into a scratch folder (git worktree of a clone),
built there with Maven and the bridge is run over its jars. Nothing from Drishti is committed here.

    python3 tools/split/make_reference.py --drishti /path/to/drishti-clone --commit <sha-or-tag> --out ref.jsonl [--java-home JDK21]
    python3 tools/split/compare_results.py ref.jsonl results/jvm.jsonl

Steps: (1) `git worktree add` of the commit into a scratch folder; (2) `mvn -o -q package -DskipTests` for compute/drishti-rachana and
kernel/drishti-engine with `dependency:build-classpath` (compiler-only class path and engine class path); (3) the compiler-only
bridge for every kind (every kind: the bridge reports the ones it does not answer as unsupported); (4) RefBridgeSutra over the engine class path for `--kinds sutra`; (5) the two outputs merged
into one file in case order. The scratch folder is removed at the end. See tools/reference-bridge/README.md."""
import argparse, json, os, shutil, subprocess, sys, tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RUN = ROOT / "tools" / "conformance" / "rachana_conformance.py"


def sh(cmd, **kw):
    print("+", " ".join(map(str, cmd)), flush=True)
    return subprocess.run(cmd, check=True, **kw)


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--drishti", required=True, help="a Drishti clone (read only: only `git worktree add` is run in it)")
    ap.add_argument("--commit", required=True, help="the commit or tag to take the reference from (i7a: its release tag)")
    ap.add_argument("--out", required=True, help="the reference results file to write")
    ap.add_argument("--java-home", default=os.environ.get("RACHANA_JAVA_HOME", "/usr/lib/jvm/java-21-openjdk-amd64"))
    a = ap.parse_args(argv)
    env = dict(os.environ, JAVA_HOME=a.java_home)
    scratch = Path(tempfile.mkdtemp(prefix="rachana-ref-"))
    wt = scratch / "drishti"
    try:
        sh(["git", "-C", a.drishti, "worktree", "add", "--detach", str(wt), a.commit])
        cp1, cp2 = scratch / "cp.txt", scratch / "cp-engine.txt"
        for mod, cp in (("compute/drishti-rachana", cp1), ("kernel/drishti-engine", cp2)):
            sh(["mvn", "-o", "-q", "package", "dependency:build-classpath", "-pl", mod, "-am", "-DskipTests",
                f"-Dmdep.outputFile={cp}", "-Dmdep.includeScope=runtime"], cwd=wt, env=env)
        jar = lambda mod: next(j for j in sorted((wt / mod / "target").glob("*.jar")) if not j.name.endswith(("-sources.jar", "-tests.jar")))
        for cp, mod in ((cp1, "compute/drishti-rachana"), (cp2, "kernel/drishti-engine")):
            cp.write_text(str(jar(mod)) + os.pathsep + cp.read_text().strip())
        formats = os.environ.get("RACHANA_DRISHTI_FORMATS", str(ROOT / "tools" / "conformance" / "profile" / "drishti-formats.yaml"))
        base = ["python3", str(RUN)]
        env2 = dict(env, RACHANA_DRISHTI_FORMATS=formats)
        main_out, sutra_out = scratch / "main.jsonl", scratch / "sutra.jsonl"
        sh(base + ["--classpath-file", str(cp1), "--out", str(main_out)], cwd=ROOT, env=env2)
        cmd = (f"java -cp {cp2.read_text().strip()} {ROOT / 'tools' / 'reference-bridge' / 'RefBridgeSutra.java'} "
               f"{ROOT / 'spec' / '1' / 'formats.yaml'} {formats}")
        sh(base + ["--cmd", cmd, "--kinds", "sutra", "--out", str(sutra_out)], cwd=ROOT, env=env2)
        merged = {}
        for f in (main_out, sutra_out):
            for l in f.read_text(encoding="utf-8").splitlines():
                if l.strip():
                    merged[json.loads(l)["case"]] = l
        Path(a.out).write_text("".join(merged[k] + "\n" for k in sorted(merged)), encoding="utf-8")
        print(f"{len(merged)} reference lines -> {a.out}")
        return 0
    finally:
        subprocess.run(["git", "-C", a.drishti, "worktree", "remove", "--force", str(wt)], check=False)
        shutil.rmtree(scratch, ignore_errors=True)


if __name__ == "__main__":
    sys.exit(main())
