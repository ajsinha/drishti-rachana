# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""A runner implementing conformance/CONFORMANCE.md: load, run through an adapter, compare, write, exit code."""
from __future__ import annotations

import fnmatch
import json
import os
import sys
import time
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Optional

from .canonical import dumps
from .cases import Case, CaseFileError, load_suite
from .compare import diff

EXIT_OK, EXIT_FAIL, EXIT_USAGE, EXIT_UNVERIFIED = 0, 1, 2, 3   # RUNNER 4.4


class UsageError(Exception):
    """Bad option, case file that does not validate, results file not writable (exit 2)."""


class Adapter(ABC):
    """A runtime under test. It implements kinds; the runner does loading, comparing and writing."""

    name: str = "unnamed"             # runtime name: [a-z][a-z0-9-]{0,31}
    version: str = "0"
    language: str = "1.0"             # highest language minor implemented
    profiles: tuple[str, ...] = ("core",)
    kinds: frozenset[str] = frozenset()

    @abstractmethod
    def run_case(self, case: dict[str, Any]) -> dict[str, Any]:
        """Run one merged case (no ``expect``) and return the *actual* document of its kind, every field the kind
        defines (FORMAT 5). Raise any exception for ``error`` (the runtime threw)."""


class NoneAdapter(Adapter):
    """Implements nothing: every case is ``unsupported``. Proves the runner and the gate's plumbing."""

    name = "none"
    kinds = frozenset()

    def run_case(self, case: dict[str, Any]) -> dict[str, Any]:  # never called: no kind is implemented
        raise NotImplementedError


@dataclass
class Options:
    cases: Path = Path("conformance/1")
    runtime: Optional[str] = None
    out: Optional[Path] = None
    summary: Optional[Path] = None
    profiles: tuple[str, ...] = ("core",)
    language: Optional[str] = None
    only: tuple[str, ...] = ()
    kinds: Optional[tuple[str, ...]] = None
    timeout_ms: int = 10000       # TODO: enforced only by the --cmd driver; in-process runs cannot interrupt a case
    record: bool = False


@dataclass
class Result:
    case: Case
    status: str
    actual: Optional[dict] = None
    detail: str = ""


def _minor(s: str) -> tuple[int, int]:
    a, b = s.split(".")
    return int(a), int(b)


def _status(case: Case, adapter: Adapter, o: Options, language: str, profiles: set[str]) -> Optional[str]:
    kinds = adapter.kinds if o.kinds is None else adapter.kinds & set(o.kinds)
    if case.kind not in kinds or not set(case.profiles) <= profiles or _minor(case.since) > _minor(language):
        return "unsupported"
    return None


def run(adapter: Adapter, o: Options, *, stderr=sys.stderr) -> int:
    """Run the suite; write the results and summary; return the exit code. Nothing is written on exit 2."""
    if o.record:
        raise UsageError("--record is the JVM reference's (RUNNER 6)")
    runtime = o.runtime or adapter.name
    out = o.out or Path("results") / f"{runtime}.jsonl"
    summary_path = o.summary or Path("results") / f"{runtime}.summary.json"
    language = o.language or adapter.language
    profiles = {"core", *o.profiles}
    loaded = profiles & ({"core", *adapter.profiles})
    try:
        cases = load_suite(o.cases)
    except CaseFileError as e:
        raise UsageError(str(e)) from e
    seen: set[str] = set()
    for c in cases:
        if c.id in seen:
            raise UsageError(f"duplicate case id {c.id}")
        seen.add(c.id)
    if o.only:
        cases = [c for c in cases if any(fnmatch.fnmatchcase(c.id, g) for g in o.only)]

    t0 = time.monotonic()
    results: list[Result] = []
    for c in cases:
        st = _status(c, adapter, o, language, loaded)
        if st:
            results.append(Result(c, st))
            continue
        try:
            actual = adapter.run_case(c.for_program())
        except Exception as e:  # the runtime threw: never a language problem (RUNNER 3)
            results.append(Result(c, "error", None, f"{type(e).__name__}: {e}"))
            continue
        d = _compare(c, actual)
        results.append(Result(c, "pass" if d is None else "fail", actual, d or ""))

    lines = "".join(dumps({"case": r.case.id, "kind": r.case.kind, "status": r.status,
                           "actual": r.actual if r.status in ("pass", "fail") else None}) + "\n" for r in results)
    counts = {s: sum(r.status == s for r in results) for s in ("pass", "fail", "error", "unsupported")}
    bad = [r for r in results if r.status in ("fail", "error")]
    unverified = sum(not r.case.verified for r in bad)
    summ = {"runtime": runtime, "runtimeVersion": adapter.version, "language": language,
            "profiles": sorted(loaded, key=lambda p: (p != "core", p)), "cases": len(results), **counts,
            "unverifiedFailing": unverified,
            "failures": [{"case": r.case.id, "status": r.status, "verified": r.case.verified, "detail": r.detail}
                         for r in bad],
            "millis": int((time.monotonic() - t0) * 1000)}
    try:
        out.parent.mkdir(parents=True, exist_ok=True)
        summary_path.parent.mkdir(parents=True, exist_ok=True)
        out.write_bytes(lines.encode("utf-8"))
        summary_path.write_text(json.dumps(summ, ensure_ascii=False) + "\n", encoding="utf-8")
    except OSError as e:
        raise UsageError(f"cannot write results: {e}") from e
    for r in bad:
        print(f"{r.status.upper()} {r.case.id}: {r.detail}", file=stderr)
    if any(r.case.verified for r in bad):
        return EXIT_FAIL
    return EXIT_UNVERIFIED if bad else EXIT_OK


def _compare(c: Case, actual: dict) -> Optional[str]:
    """`expect` of the case against the actual document. A protocol case has per-step `expect` (FORMAT 5.12)."""
    if c.kind == "protocol":
        exp = [s.get("expect", {}) for s in c.body.get("steps", [])]
        return diff({"steps": exp}, actual)
    expect = c.body.get("expect") or {}
    return diff(expect, actual, tolerance=expect.get("tolerance"))
