# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""Rachana in Python without a JVM (decision W-1, the seed of plan B8): the JavaScript bundle of the A15 build run on V8 through
mini-racer. The answers are the bundle's, which agree byte for byte with the JVM on every conformance case; this module only
carries JSON text across.

    from drishti_rachana.engine import Rachana
    r = Rachana()                                  # Core alone; Rachana(profiles="core,drishti", formats=[core_yaml, names_yaml])
    r.evaluate("$.a * 2", record={"a": 21})        # {'value': 42, 'type': 'number', 'text': '42', 'reads': ['$.a']}
    r.check(open("trade.sutra.yaml").read())       # {'ok': True, 'problems': []}  (compile and lint: parse, then check)

The bundles are built by tools/js/build_js.py into drishti_rachana/bundle/ (git-ignored; a wheel carries them). Needs the
``v8`` extra (``mini-racer``)."""
from __future__ import annotations

import hashlib
import json
import re
from pathlib import Path
from typing import Any, Iterable

BUNDLE_DIR = Path(__file__).resolve().parent / "bundle"
# Limits of the V8 isolate (defence in depth: Core's own budgets already bound an expression). A host may pass its own.
DEFAULT_MAX_MEMORY = 1024 * 1024 * 1024   # bytes of V8 heap; the isolate stops (MemoryError) beyond it
DEFAULT_TIMEOUT = 30.0                    # seconds per call into the bundle; a longer call is cancelled (JSTimeoutException)


class BundleHashError(RuntimeError):
    """The bundle's SHA-256 is not the recorded one: it is not run."""


def bundle_sha256(path: Path, expected: str | None = None) -> str:
    """Check ``path`` against ``expected`` (a hex SHA-256) or, when none is given, against the ``SHA256SUMS`` file that
    tools/js/build_js.py writes beside the bundles (the hashes the release notes record, decision P1-3). A bundle with no recorded
    hash is refused. Returns the hash."""
    actual = hashlib.sha256(path.read_bytes()).hexdigest()
    if expected is None:
        sums = path.parent / "SHA256SUMS"
        if not sums.is_file():
            raise BundleHashError(f"{sums} is missing: no recorded SHA-256 for {path.name} (build with tools/js/build_js.py, or pass expected_sha256)")
        recorded = {n.strip(): h for h, _, n in (ln.partition("  ") for ln in sums.read_text(encoding="utf-8").splitlines() if ln.strip())}
        expected = recorded.get(path.name)
        if expected is None:
            raise BundleHashError(f"{sums} has no line for {path.name}")
    if actual != expected.lower():
        raise BundleHashError(f"{path.name}: SHA-256 {actual} is not the recorded {expected}")
    return actual


_EXPORT = re.compile(r"export\s*\{([^}]*)\}\s*;?\s*$")


def script_of(module_text: str, name: str = "rachana") -> str:
    """The ES module bundle as a classic script: its closing ``export{a as b, ...}`` becomes ``globalThis.<name> = {b: a, ...}``
    (V8 embedded through mini-racer evaluates scripts, not modules)."""
    m = _EXPORT.search(module_text)
    if not m:
        raise ValueError("not a Rachana bundle: no export list at its end")
    pairs = []
    for part in m.group(1).split(","):
        local, _, exported = part.strip().partition(" as ")
        pairs.append(f"{json.dumps(exported or local)}: {local}")
    return module_text[: m.start()] + f"globalThis.{name} = {{{', '.join(pairs)}}};\n"


class Rachana:
    """One Rachana instance inside one V8 isolate: ``bundle`` is ``"tools"`` (default: everything) or ``"runtime"``."""

    def __init__(self, profiles: str = "core", formats: Iterable[str] = (), bundle: str = "tools", bundle_dir: Path | None = None,
                 max_memory: int = DEFAULT_MAX_MEMORY, timeout: float = DEFAULT_TIMEOUT, expected_sha256: str | None = None):
        try:
            from py_mini_racer import MiniRacer
        except ImportError as e:  # pragma: no cover - depends on the extra
            raise ImportError("drishti_rachana.engine needs mini-racer: pip install 'drishti-rachana[v8]'") from e
        path = (bundle_dir or BUNDLE_DIR) / f"rachana.{bundle}.js"
        if not path.is_file():
            raise FileNotFoundError(f"{path} is missing: build it with python3 tools/js/build_js.py")
        bundle_sha256(path, expected_sha256)           # before a byte of it runs
        self._timeout = timeout
        self._ctx = MiniRacer()
        self._ctx.set_hard_memory_limit(max_memory)
        self._ctx.eval(script_of(path.read_text(encoding="utf-8")), timeout_sec=timeout)
        self._ctx.eval(f"rachana.init({json.dumps(profiles)}, {json.dumps(json.dumps(list(formats)))})", timeout_sec=timeout)

    def _call(self, fn: str, request: dict[str, Any]) -> dict[str, Any]:
        return json.loads(self._ctx.call(f"rachana.{fn}", json.dumps(request, ensure_ascii=False), timeout_sec=self._timeout))

    def version(self) -> str:
        return self._ctx.call("rachana.version", timeout_sec=self._timeout)

    def evaluate(self, expr: str, record: Any = None, **given: Any) -> dict[str, Any]:
        """An expression over a record (``given`` may add ``row``, ``ctl``, ``today``, ``parents``, ``index``)."""
        return self._call("evaluate", {"expr": expr, "given": {"record": record or {}, **given}})

    def template(self, template: str, record: Any = None, **given: Any) -> dict[str, Any]:
        return self._call("evaluate", {"template": template, "given": {"record": record or {}, **given}})

    def check(self, sutra: str, file: str = "case.sutra.yaml") -> dict[str, Any]:
        """Compile and lint a Sutra: parsed, then its expressions checked; ``{"ok": bool, "problems": [...]}``."""
        return self._call("check", {"sutra": sutra, "file": file})

    def format(self, value: Any, format: str | None = None) -> dict[str, Any]:
        request: dict[str, Any] = {"value": value}
        if format:
            request["format"] = format
        return self._call("format", request)

    def view(self, sutra: str, record: Any = None, **given: Any) -> dict[str, Any]:
        """The ViewModel of a Sutra over a record, built with Core's hooks."""
        return self._call("view", {"sutra": sutra, "given": {"record": record or {}, **given}})

    def edit(self, sutra: str, operation: dict[str, Any]) -> dict[str, Any]:
        """One editing operation (FORMAT 5.11) on a Sutra's text; tools bundle only."""
        return self._call("edit", {"sutra": sutra, "operation": operation})


def main(argv: list[str] | None = None) -> int:
    """The conformance runtime program on V8 (RUNNER 5): ``python -m drishti_rachana.engine [--profiles core,drishti] <formats
    file>...``; case lines on standard input, results lines out, answered by the tools bundle's own harness."""
    import sys

    args = list(sys.argv[1:] if argv is None else argv)
    profiles = "core"
    if len(args) >= 2 and args[0] == "--profiles":
        profiles, args = args[1], args[2:]
    r = Rachana(profiles=profiles, formats=[Path(f).read_text(encoding="utf-8") for f in args])
    out = sys.stdout
    greeted = False
    for line in sys.stdin:
        if not greeted:
            greeted = True
            out.write(r._ctx.call("rachana.hello", "python-v8", timeout_sec=r._timeout) + "\n")
            out.flush()
            continue
        if line.strip():
            out.write(r._ctx.call("rachana.answer", line.rstrip("\n"), timeout_sec=r._timeout) + "\n")
    out.flush()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
