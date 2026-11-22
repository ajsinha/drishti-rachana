#!/usr/bin/env python3
# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see LICENSE.
"""The "schema never drifts" guard: every Sutra of the conformance cases and of the examples that the compiler accepts must validate
against the published spec/1/sutra.schema.json.

A schema generated from the grammar can still miss a construct (1.0.1: top-level state, data, libraries, let and functions, a panel's
on and actions were absent). The compiler is the truth, so a Sutra that compiles and does not validate is a schema bug.

Checked: the `sutra` of each conformance case that is not expected to fail (expect.ok is not false, and no problem is expected), and
every examples/**/*.sutra.yaml and libraries/**/examples/*.sutra.yaml. The Drishti profile's top-level keys (match, domain) are
dropped first, and a Sutra that uses a profile construct (its panel kinds, source forms, form.save) is left out: the published schema is
Core's. LENIENT names the few places where the compiler ignores a value.

    python3 tools/conformance/schema_cases.py          exit 1 on the first drift, naming each file and case
Needs PyYAML and jsonschema (requirements.txt)."""
import json
import sys
from pathlib import Path

import jsonschema
import jsonschema.exceptions
import yaml

sys.path.insert(0, str(Path(__file__).resolve().parent))
from rachana_conformance import CoreLoader  # noqa: E402  (YAML 1.2 core: `on` is text, not true)

ROOT = Path(__file__).resolve().parents[2]
PROFILE_KEYS = {"match", "domain"}                     # Drishti profile: a Sutra's top-level keys
PROFILE_KINDS = {"links", "provenance"}                # Drishti profile: its panel kinds
PROFILE_SOURCE = {"kind", "id", "search", "pack", "params", "as-of", "model"}   # Drishti profile: the entity form of a panel's source

# Where the compiler is more lenient than the grammar it documents (a value it ignores or does not check): not schema gaps.
# This list only shrinks; each entry is "<file>#<case id>" with the reason.
LENIENT = {
    "conformance/1/problem/coverage-valid.yaml#column-link-and-tone": "a column's link: an object is read as not-true",
    "conformance/1/problem/coverage-valid.yaml#table-more-total-labels-highlight-link": "a column's total: 'sum' is read as not-true",
    "conformance/1/sutra/binder/live.yaml#a-live-blotter": "fmt: number is not a format name; a live panel's column does not check it",
}


def uses_profile(node):
    """True when the Sutra holds a construct of the Drishti profile (the published schema is Core's)."""
    if isinstance(node, dict):
        if node.get("kind") in PROFILE_KINDS and "id" in node:
            return True
        if isinstance(node.get("source"), dict) and PROFILE_SOURCE & set(node["source"]):
            return True
        if isinstance(node.get("form"), dict) and "save" in node["form"]:
            return True
        return any(uses_profile(v) for v in node.values())
    if isinstance(node, list):
        return any(uses_profile(v) for v in node)
    return False


def load(text):
    return yaml.load(text, Loader=CoreLoader)


def expected_to_fail(case):
    e = case.get("expect")
    return isinstance(e, dict) and (e.get("ok") is False or bool(e.get("problems")))


def sutras():
    for f in sorted((ROOT / "conformance" / "1").rglob("*.yaml")):
        try:
            d = load(f.read_text(encoding="utf-8"))
        except yaml.YAMLError:
            continue
        for c in (d.get("cases") or []) if isinstance(d, dict) else []:
            if isinstance(c.get("sutra"), str) and not expected_to_fail(c):
                yield f"{f.relative_to(ROOT)}#{c['id']}", c["sutra"]
    for pattern in ("examples/**/*.sutra.yaml", "libraries/**/*.sutra.yaml"):
        for f in sorted(ROOT.glob(pattern)):
            yield str(f.relative_to(ROOT)), f.read_text(encoding="utf-8")


def main() -> int:
    schema = json.loads((ROOT / "spec" / "1" / "sutra.schema.json").read_text(encoding="utf-8"))
    v = jsonschema.Draft202012Validator(schema)
    n = bad = 0
    for name, text in sutras():
        try:
            doc = load(text)
        except yaml.YAMLError:
            continue
        if not isinstance(doc, dict):
            continue
        if name in LENIENT:
            continue
        for k in PROFILE_KEYS:
            doc.pop(k, None)
        if uses_profile(doc):
            continue
        n += 1
        errs = sorted(v.iter_errors(doc), key=lambda e: list(map(str, e.absolute_path)))
        if errs:
            bad += 1
            e = jsonschema.exceptions.best_match(errs)
            print(f"{name}: /{'/'.join(map(str, e.absolute_path))}: {e.message[:200]}")
    print(f"{n} Sutras checked against spec/1/sutra.schema.json, {bad} do not validate")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
