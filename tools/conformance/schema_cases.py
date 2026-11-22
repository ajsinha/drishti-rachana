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
The other direction (1.0.1 patch gate): a schema must not become looser than the compiler. MUST_REFUSE names the conformance cases the
compiler refuses for the type or shape of a value; each must FAIL validation. Of all refused cases (the Drishti profile's left out),
most are semantic (an unknown field, a bad reference: no schema can see them) and validate by design; their number may not grow past
REFUSED_BUT_VALID_MAX, so any loosening is seen. LENIENT may not grow past LENIENT_MAX.

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

# Refused by the compiler (RCH-2029, a value of the wrong type or range) and so also by the schema: "<file>#<case id>".
MUST_REFUSE = (
    "conformance/1/problem/strict-sutra.yaml#table-rows-5",
    "conformance/1/problem/strict-sutra.yaml#table-limit-many",
    "conformance/1/problem/strict-sutra.yaml#table-limit-negative",
)
REFUSED_BUT_VALID_MAX = 194   # compiler-refused cases that validate (semantic problems); only shrinks: lower it when it does
LENIENT_MAX = 3               # only shrinks

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


def refused(validator):
    """Yield (name, validates) for every conformance case the compiler refuses (the Drishti profile's left out)."""
    for f in sorted((ROOT / "conformance" / "1").rglob("*.yaml")):
        try:
            d = load(f.read_text(encoding="utf-8"))
        except yaml.YAMLError:
            continue
        for c in (d.get("cases") or []) if isinstance(d, dict) else []:
            if not (isinstance(c.get("sutra"), str) and expected_to_fail(c)):
                continue
            try:
                doc = load(c["sutra"])
            except yaml.YAMLError:
                continue
            if not isinstance(doc, dict):
                continue
            for k in PROFILE_KEYS:
                doc.pop(k, None)
            if uses_profile(doc):
                continue
            yield f"{f.relative_to(ROOT)}#{c['id']}", not any(True for _ in validator.iter_errors(doc))


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
    valid = {name for name, ok in refused(v) if ok}
    names = {name for name, _ in refused(v)}
    for name in MUST_REFUSE:
        if name not in names:
            bad += 1
            print(f"MUST_REFUSE names no refused case: {name}")
        elif name in valid:
            bad += 1
            print(f"{name}: the compiler refuses it and the schema accepts it (the schema became looser than the compiler)")
    if len(valid) > REFUSED_BUT_VALID_MAX:
        bad += 1
        print(f"{len(valid)} refused cases validate, more than the {REFUSED_BUT_VALID_MAX} committed: the schema got looser")
    if len(LENIENT) > LENIENT_MAX:
        bad += 1
        print(f"LENIENT has {len(LENIENT)} entries, more than {LENIENT_MAX}: the list only shrinks")
    print(f"{len(names)} refused cases: {len(names) - len(valid)} fail validation, {len(valid)} validate (semantic; at most {REFUSED_BUT_VALID_MAX})")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
