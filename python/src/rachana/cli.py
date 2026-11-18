# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""``rachana`` command line. Today: ``rachana conformance run`` (conformance/RUNNER.md 2)."""
from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Optional, Sequence

from .conformance.runner import EXIT_USAGE, NoneAdapter, Options, UsageError, run


def _csv(s: Optional[str]) -> tuple[str, ...]:
    return tuple(x for x in (s or "").split(",") if x)


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(prog="rachana")
    top = p.add_subparsers(dest="group", required=True)
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


def main(argv: Optional[Sequence[str]] = None) -> int:
    try:
        a = build_parser().parse_args(argv)
    except SystemExit as e:
        return EXIT_USAGE if e.code else 0
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
