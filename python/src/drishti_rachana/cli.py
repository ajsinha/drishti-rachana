# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""``python -m drishti_rachana`` command line: ``lint`` (compile and lint Sutras on V8; needs the ``v8`` extra) and ``conformance run``
(conformance/RUNNER.md 2). The program named ``rachana`` is the Node CLI (``js/packages/rachana-cli``); Python installs no script of that name."""
from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Optional, Sequence

from .conformance.runner import EXIT_USAGE, NoneAdapter, Options, UsageError, run


def _csv(s: Optional[str]) -> tuple[str, ...]:
    return tuple(x for x in (s or "").split(",") if x)


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(prog="python -m drishti_rachana")
    top = p.add_subparsers(dest="group", required=True)
    lint = top.add_parser("lint", help="compile and lint Sutra files (exit 1 on errors; needs drishti-rachana[v8])")
    lint.add_argument("paths", nargs="*", default=["."], help="files or folders (*.sutra.yaml); default: the current folder")
    lint.add_argument("--format", choices=("text", "json", "sarif"), default="text")
    lint.add_argument("--strict", action="store_true", help="warnings fail too")
    lint.add_argument("--profiles", default="core")
    lint.add_argument("--libs", default="", help="more fragment libraries: paths (comma-separated) of an unpacked library, an .rlib or a folder of them; also RACHANA_LIBS; the bundled libraries/ are always offered")
    up = top.add_parser("upgrade", help="rewrite level-1.0 Sutras and fragments for a later level, keeping their meaning (needs a JVM: RACHANA_JAR or RACHANA_CLASSPATH)")
    up.add_argument("args", nargs=argparse.REMAINDER, help="paths, --to, --write, --diff, --report, --rules (rachana upgrade --rules)")
    conf = top.add_parser("conformance", help="conformance suite").add_subparsers(dest="action", required=True)
    r = conf.add_parser("run", help="run the suite through an adapter and write results/<runtime>.jsonl")
    r.add_argument("--cases", default="conformance/1")
    r.add_argument("--runtime")
    r.add_argument("--out")
    r.add_argument("--summary")
    r.add_argument("--profiles", default="core")
    r.add_argument("--language")
    r.add_argument("--only")
    r.add_argument("--kinds")
    r.add_argument("--timeout", type=int, default=10000)
    r.add_argument("--adapter", default="none", help="'none' (every case unsupported) or module:Class")
    r.add_argument("--cmd", help="third-party program speaking RUNNER 5 on stdin/stdout (not implemented yet)")
    r.add_argument("--record", action="store_true")
    return p


def _adapter(spec: str):
    if spec == "none":
        return NoneAdapter()
    import importlib
    mod, _, cls = spec.partition(":")
    if not cls:
        raise UsageError("--adapter is 'none' or module:Class")
    return getattr(importlib.import_module(mod), cls)()


SUFFIX = ".sutra.yaml"


def find_sutras(paths: Sequence[str]) -> list[Path]:
    out: list[Path] = []
    for p in map(Path, paths):
        if p.is_file():
            out.append(p)
        elif p.is_dir():
            out += sorted(f for f in p.rglob("*" + SUFFIX) if not any(x.startswith(".") or x == "node_modules" for x in f.relative_to(p).parts))
        else:
            raise FileNotFoundError(f"no such file or folder: {p}")
    return out


def lint_files(files: Sequence[Path], profiles: str = "core", level: Optional[str] = None, libs: Optional[dict] = None) -> list[dict]:
    """``[{"file", "ok", "problems"}]`` per file, from the bundle's ``check``: the same problem objects as the JVM and Node."""
    from .engine import Rachana
    r = Rachana(profiles=profiles)
    results = []
    for f in files:
        res = r.check(f.read_text(encoding="utf-8"), file=str(f), level=level, files=libs)
        results.append({"file": str(f), "ok": res["ok"], "problems": res["problems"]})
    return results


def sarif(results: list[dict]) -> dict:
    level = {"error": "error", "warning": "warning"}
    return {"version": "2.1.0", "$schema": "https://json.schemastore.org/sarif-2.1.0.json",
            "runs": [{"tool": {"driver": {"name": "rachana", "informationUri": "https://github.com/ajsinha/drishti-rachana"}},
                      "results": [{"ruleId": p["code"], "level": level.get(p["severity"], "note"), "message": {"text": p["message"]},
                                   "locations": [{"physicalLocation": {"artifactLocation": {"uri": r["file"]},
                                                  "region": {"startLine": p.get("range", {}).get("start", {}).get("line", 1),
                                                             "startColumn": p.get("range", {}).get("start", {}).get("column", 1)}}}]}
                                  for r in results for p in r["problems"]]}]}


def lint_main(paths: Sequence[str], fmt: str = "text", strict: bool = False, profiles: str = "core", libs: str = "") -> int:
    """Each Sutra is read at the level its ``rachana:`` key declares (SPEC 2.1: ``rachana: 1`` is 1.0, ``rachana: 1.2`` is 1.2)."""
    import json
    from .libraries import bundle_files, project_libraries
    files = find_sutras(paths)
    root = Path(paths[0]) if paths and Path(paths[0]).is_dir() else Path(".")
    libraries, lib_problems = project_libraries(root, [x for x in libs.split(",") if x])
    for m in lib_problems:
        print(f"rachana: {m}", file=sys.stderr)
    results = lint_files(files, profiles, None, bundle_files(libraries))
    bad = len(lib_problems) + sum(1 for r in results for p in r["problems"] if p["severity"] == "error" or (strict and p["severity"] == "warning"))
    if fmt == "json":
        print(json.dumps(results, indent=2, ensure_ascii=False))
    elif fmt == "sarif":
        print(json.dumps(sarif(results), indent=2, ensure_ascii=False))
    else:
        for r in results:
            for p in r["problems"]:
                st = p.get("range", {}).get("start", {})
                first = p["message"].splitlines()[0] if p["message"] else ""
                print(f"{r['file']}:{st.get('line', 1)}:{st.get('column', 1)}: {p['severity']} {p['code']}: {first}")
        print(f"{len(results)} file(s), {bad} failing problem(s)")
    return 1 if bad else 0


def java_command(command: str, args: Sequence[str], env: Optional[dict] = None) -> Optional[list[str]]:
    """The JVM command line of a command the Java rachana-cli runs (``upgrade``): RACHANA_JAR or RACHANA_CLASSPATH, RACHANA_JAVA."""
    import os
    env = os.environ if env is None else env
    java = env.get("RACHANA_JAVA", "java")
    if env.get("RACHANA_JAR"):
        return [java, "-jar", env["RACHANA_JAR"], command, *args]
    if env.get("RACHANA_CLASSPATH"):
        return [java, "-cp", env["RACHANA_CLASSPATH"], "com.ash.rachana.cli.Main", command, *args]
    return None


def upgrade_main(args: Sequence[str]) -> int:
    """``rachana upgrade``: the Java Upgrade of rachana-tools (one implementation), run as the Node command line runs it."""
    import subprocess
    cmd = java_command("upgrade", list(args))
    if cmd is None:
        print("rachana upgrade needs a JVM and the rachana-cli jar: set RACHANA_JAR (a runnable jar) or RACHANA_CLASSPATH (the module jars)",
              file=sys.stderr)
        return EXIT_USAGE
    try:
        return subprocess.run(cmd).returncode
    except OSError as e:
        print(f"rachana upgrade: {e}", file=sys.stderr)
        return EXIT_USAGE


def main(argv: Optional[Sequence[str]] = None) -> int:
    try:
        a = build_parser().parse_args(argv)
    except SystemExit as e:
        return EXIT_USAGE if e.code else 0
    if a.group == "upgrade":
        return upgrade_main(a.args)
    if a.group == "lint":
        try:
            return lint_main(a.paths, a.format, a.strict, a.profiles, a.libs)
        except (ImportError, FileNotFoundError) as e:
            print(f"rachana: {e}", file=sys.stderr)
            return EXIT_USAGE
    try:
        if a.cmd:
            raise UsageError("--cmd (RUNNER 5) is not implemented yet")  # TODO(B5/R2): the stdin/stdout driver
        o = Options(cases=Path(a.cases), runtime=a.runtime, out=Path(a.out) if a.out else None,
                    summary=Path(a.summary) if a.summary else None, profiles=_csv(a.profiles) or ("core",),
                    language=a.language, only=_csv(a.only), kinds=_csv(a.kinds) or None,
                    timeout_ms=a.timeout, record=a.record)
        return run(_adapter(a.adapter), o)
    except UsageError as e:
        print(f"rachana: {e}", file=sys.stderr)
        return EXIT_USAGE


if __name__ == "__main__":
    sys.exit(main())
