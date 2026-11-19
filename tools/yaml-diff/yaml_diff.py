#!/usr/bin/env python3
# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see LICENSE.
"""The YAML differential of plan A8a: Core's reader against the reference's, over every YAML file of a Drishti checkout
(read with `git ls-files`, never changed) and of this repository, the texts inside the conformance case files, the edge
inputs and their mutations. Exit 0 when the only differences are the refused constructs (anchors, aliases).

    python3 tools/yaml-diff/yaml_diff.py --drishti <checkout> [--mutations 20] [--record] [--work <dir>]

Needs rachana-core compiled (`mvn -o -q -pl rachana-core -am clean compile` in java/), Drishti's built
compute/drishti-rachana jar (i7a), and the reference's libraries in the local Maven repository: Jackson 2.21.4 and
SnakeYAML 2.4. `--record` rewrites java/rachana-core/src/test/resources/yaml/reference-vectors.jsonl (this repository's
inputs only) from the reference.
"""
import argparse, glob, os, subprocess, sys, tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
M2 = Path(os.environ.get("M2_REPO", Path.home() / ".m2" / "repository"))
JARS = ["com/fasterxml/jackson/core/jackson-core/2.21.4/jackson-core-2.21.4.jar",
        "com/fasterxml/jackson/core/jackson-databind/2.21.4/jackson-databind-2.21.4.jar",
        "com/fasterxml/jackson/core/jackson-annotations/2.21/jackson-annotations-2.21.jar",
        "com/fasterxml/jackson/dataformat/jackson-dataformat-yaml/2.21.4/jackson-dataformat-yaml-2.21.4.jar",
        "org/yaml/snakeyaml/2.4/snakeyaml-2.4.jar"]


def yaml_files(repo: Path):
    out = subprocess.run(["git", "-C", str(repo), "ls-files", "-z"], check=True, capture_output=True).stdout.decode().split("\0")
    return [str(repo / f) for f in out if f.endswith((".yaml", ".yml"))]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--drishti", required=True, help="a Drishti checkout with compute/drishti-rachana/target/*.jar built")
    ap.add_argument("--mutations", default="20")
    ap.add_argument("--record", action="store_true")
    ap.add_argument("--work", default=None, help="folder for the file list and the report (default: a temporary folder)")
    ap.add_argument("--java", default=os.environ.get("JAVA", "java"))
    a = ap.parse_args()
    drishti = Path(a.drishti).resolve()
    ref = sorted(glob.glob(str(drishti / "compute/drishti-rachana/target/drishti-rachana-*.jar")))
    ref = [j for j in ref if not j.endswith(("-sources.jar", "-tests.jar"))]
    if not ref:
        sys.exit("no compute/drishti-rachana jar in " + str(drishti))
    cp = [str(ROOT / "java/rachana-core/target/classes"), ref[0]] + [str(M2 / j) for j in JARS]
    for p in cp:
        if not Path(p).exists():
            sys.exit("missing " + p)
    work = Path(a.work or tempfile.mkdtemp(prefix="yaml-diff-"))
    work.mkdir(parents=True, exist_ok=True)
    lst = work / "files.txt"
    lst.write_text("\n".join(yaml_files(drishti) + yaml_files(ROOT)) + "\n")
    cmd = [a.java, "-Xmx3g", "-cp", os.pathsep.join(cp), str(ROOT / "tools/yaml-diff/YamlDiff.java"), "--files", str(lst),
           "--cases", str(ROOT / "conformance"), "--mutations", a.mutations, "--report", str(work / "report.txt")]
    if a.record:
        cmd += ["--record", str(ROOT / "java/rachana-core/src/test/resources/yaml/reference-vectors.jsonl")]
    r = subprocess.run(cmd)
    print("report:", work / "report.txt")
    sys.exit(r.returncode)


if __name__ == "__main__":
    main()
