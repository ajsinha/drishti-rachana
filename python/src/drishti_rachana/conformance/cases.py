# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""Loading, validating and merging case files (FORMAT 2, 3; RUNNER 2)."""
from __future__ import annotations

import json
import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

KINDS = ("expr", "expr-error", "check", "grammar", "format", "sutra", "problem", "patch", "plan", "edit", "protocol")
_ID = re.compile(r"[a-z0-9][a-z0-9-]{0,79}\Z")
_SINCE = re.compile(r"[0-9]+\.[0-9]+\Z")


class CaseFileError(Exception):
    """A case file that does not parse or validate (a usage failure, exit 2)."""


@dataclass
class Case:
    id: str                      # "<suite>#<id>"
    suite: str
    kind: str
    since: str
    profiles: list[str]
    verified: bool
    body: dict[str, Any]         # the merged case, `given` merged, including `expect`

    def for_program(self) -> dict[str, Any]:
        """The case without `expect`, plus its id (RUNNER 5 step 3)."""
        d = {k: v for k, v in self.body.items() if k not in ("expect", "id")}
        return {"case": self.id, **d}


def _strict_loader():
    try:
        import yaml
    except ImportError as e:  # pragma: no cover
        raise CaseFileError("reading case files needs PyYAML: pip install 'drishti-rachana[conformance]'") from e

    class Loader(yaml.SafeLoader):
        pass

    # YAML 1.2 core schema: only true/false are booleans, only null/~ are null, no timestamps, no sexagesimal.
    Loader.yaml_implicit_resolvers = {
        k: [(t, r) for t, r in v if t not in ("tag:yaml.org,2002:bool", "tag:yaml.org,2002:null",
                                              "tag:yaml.org,2002:timestamp", "tag:yaml.org,2002:int",
                                              "tag:yaml.org,2002:float")]
        for k, v in yaml.SafeLoader.yaml_implicit_resolvers.items()
    }
    Loader.add_implicit_resolver("tag:yaml.org,2002:bool", re.compile(r"^(?:true|false)$"), list("tf"))
    Loader.add_implicit_resolver("tag:yaml.org,2002:null", re.compile(r"^(?:null|~|)$"), ["n", "~", ""])
    Loader.add_implicit_resolver("tag:yaml.org,2002:int", re.compile(r"^[-+]?[0-9]+$"), list("-+0123456789"))
    Loader.add_implicit_resolver(
        "tag:yaml.org,2002:float",
        re.compile(r"^[-+]?(?:\.[0-9]+|[0-9]+(?:\.[0-9]*)?)(?:[eE][-+]?[0-9]+)?$"), list("-+0123456789."))

    def construct_mapping(loader, node, deep=False):
        seen = set()
        for k, _ in node.value:
            key = loader.construct_object(k, deep=True)
            if key in seen:
                raise CaseFileError(f"repeated key {key!r} (line {k.start_mark.line + 1})")
            seen.add(key)
        return yaml.SafeLoader.construct_mapping(loader, node, deep)

    Loader.construct_mapping = construct_mapping
    Loader.add_constructor("tag:yaml.org,2002:map", lambda l, n: l.construct_mapping(n, True))
    return Loader, yaml


def suite_name(root: Path, file: Path) -> str:
    return file.relative_to(root).with_suffix("").as_posix()


def _merge_given(file_given: dict | None, case_given: dict | None) -> dict | None:
    if file_given is None and case_given is None:
        return None
    return {**(file_given or {}), **(case_given or {})}   # one level deep (FORMAT 2)


def _validate_schema(doc: Any, schema_path: Path, where: str) -> None:
    try:
        import jsonschema
    except ImportError:
        return                                            # the structural checks below still run
    schema = json.loads(schema_path.read_text(encoding="utf-8"))
    errors = sorted(jsonschema.Draft202012Validator(schema).iter_errors(doc), key=lambda e: list(e.path))
    if errors:
        e = errors[0]
        raise CaseFileError(f"{where}: {'/'.join(map(str, e.path))}: {e.message}")


def load_file(root: Path, file: Path, schema_path: Path | None = None) -> list[Case]:
    Loader, yaml = _strict_loader()
    where = str(file)
    try:
        doc = yaml.load(file.read_text(encoding="utf-8"), Loader=Loader)
    except CaseFileError as e:
        raise CaseFileError(f"{where}: {e}") from e
    except yaml.YAMLError as e:
        raise CaseFileError(f"{where}: {e}") from e
    if not isinstance(doc, dict):
        raise CaseFileError(f"{where}: not a mapping")
    if schema_path is not None and schema_path.exists():
        _validate_schema(doc, schema_path, where)
    suite = doc.get("suite")
    if suite != suite_name(root, file):
        raise CaseFileError(f"{where}: suite {suite!r} must equal {suite_name(root, file)!r}")
    since = doc.get("since")
    if not isinstance(since, str) or not _SINCE.match(since):
        raise CaseFileError(f"{where}: since must be a quoted 'major.minor'")
    cases = doc.get("cases")
    if not isinstance(cases, list) or not cases:
        raise CaseFileError(f"{where}: cases must be a non-empty list")
    fprofile = doc.get("profile", "core")
    out: list[Case] = []
    seen: set[str] = set()
    for c in cases:
        cid = c.get("id") if isinstance(c, dict) else None
        if not isinstance(cid, str) or not _ID.match(cid):
            raise CaseFileError(f"{where}: bad case id {cid!r}")
        if cid in seen:
            raise CaseFileError(f"{where}: duplicate case id {cid!r}")
        seen.add(cid)
        if c.get("kind") not in KINDS:
            raise CaseFileError(f"{where}: {cid}: unknown kind {c.get('kind')!r}")
        profile = c.get("profile", fprofile)
        profiles = [profile] if isinstance(profile, str) else list(profile)
        body = dict(c)
        given = _merge_given(doc.get("given"), c.get("given"))
        if given is not None:
            body["given"] = given
        body["since"] = c.get("since", since)
        body["profile"] = profile
        out.append(Case(id=f"{suite}#{cid}", suite=suite, kind=c["kind"], since=body["since"], profiles=profiles,
                        verified=bool(c.get("verified", doc.get("verified", False))), body=body))
    return out


def load_suite(cases_dir: Path, schema_path: Path | None = None) -> list[Case]:
    """Every ``*.yaml`` below ``cases_dir``: files in byte order of their suite names, cases in file order."""
    if not cases_dir.is_dir():
        raise CaseFileError(f"{cases_dir}: not a folder")
    files = sorted(cases_dir.rglob("*.yaml"), key=lambda f: suite_name(cases_dir, f).encode("utf-8"))
    if schema_path is None:
        guess = cases_dir.parent / "schema" / "case.schema.json"
        schema_path = guess if guess.exists() else None
    all_cases: list[Case] = []
    for f in files:
        all_cases.extend(load_file(cases_dir, f, schema_path))
    return all_cases
