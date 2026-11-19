#!/usr/bin/env python3
# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see LICENSE.
"""The cases jar alone (plan R0 H1, decision N4): `rachana-conformance-cases-<version>.jar` built from one commit, written into a
host's pinned Maven file repository, with the manifest the host's pin guard reads.

    python3 tools/release/cases_jar.py <version> --into <drishti>/packaging/pinned [--commit REV]

Until the code moves here (A10) the cases and the specification are the only artefact Drishti pins, and they need no build of the
Java reactor: the jar is `conformance/`, `spec/` and `META-INF/LICENSE-SPEC.md` of the commit (read with `git archive`, so the
working tree's state does not matter), plus `META-INF/rachana-cases.json` (version, commit, case count). The bytes depend only on
the commit and the version (sorted entries, fixed times), so building twice gives the same SHA-384. `tools/release/release.py` stays
the release of everything once the reactor holds code; it writes the same layout.

Written into `--into`: `maven/com/ash/rachana/rachana-conformance-cases/<version>/` (jar, pom, a .sha1 of each) with every other
version's folder of that artefact deleted (a pin moves; one version is present), and `drishti-rachana.json` (version, commit, the
SHA-384 of each jar, the Elements fields, null until the jars carry tables of Elements). A version is immutable: an existing folder of
the same version with other bytes is refused.
"""
import argparse
import hashlib
import io
import json
import shutil
import subprocess
import sys
import tarfile
import zipfile
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
GROUP_PATH = "com/ash/rachana"
ARTIFACT = "rachana-conformance-cases"
WHEN = (2026, 1, 1, 0, 0, 0)


def git(*args) -> bytes:
    return subprocess.run(["git", *args], cwd=ROOT, check=True, capture_output=True).stdout


def files_of(commit: str) -> dict:
    """path -> bytes of conformance/, spec/ and LICENSE-SPEC.md at the commit."""
    data = git("archive", "--format=tar", commit, "conformance", "spec", "LICENSE-SPEC.md")
    out = {}
    with tarfile.open(fileobj=io.BytesIO(data)) as t:
        for m in t.getmembers():
            if m.isfile():
                out[m.name] = t.extractfile(m).read()
    return out


def jar_bytes(version: str, commit: str, files: dict) -> tuple:
    cases = 0
    for name, body in files.items():
        if name.startswith("conformance/") and name.endswith(".yaml"):
            cases += len((yaml.safe_load(body) or {}).get("cases") or [])
    entries = {}
    for name, body in files.items():
        entries["META-INF/LICENSE-SPEC.md" if name == "LICENSE-SPEC.md" else name] = body
    entries["META-INF/MANIFEST.MF"] = (f"Manifest-Version: 1.0\r\nImplementation-Title: {ARTIFACT}\r\nImplementation-Version: {version}\r\n"
                                       f"Rachana-Commit: {commit}\r\n\r\n").encode()
    entries["META-INF/rachana-cases.json"] = (json.dumps({"artifact": ARTIFACT, "version": version, "commit": commit, "cases": cases},
                                                         indent=2, sort_keys=True) + "\n").encode()
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as z:
        names = ["META-INF/MANIFEST.MF"] + sorted(n for n in entries if n != "META-INF/MANIFEST.MF")
        for n in names:
            info = zipfile.ZipInfo(n, WHEN)
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            z.writestr(info, entries[n])
    return buf.getvalue(), cases


def pom(version: str) -> bytes:
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<!-- Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved. Drishti Rachana: the conformance cases and the
     specification (YAML, JSON and Markdown, no code), licensed under LICENSE-SPEC.md. Written by tools/release/cases_jar.py. -->
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.ash.rachana</groupId>
  <artifactId>{ARTIFACT}</artifactId>
  <version>{version}</version>
  <packaging>jar</packaging>
  <name>{ARTIFACT}</name>
</project>
""".encode()


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("version")
    ap.add_argument("--into", required=True, help="the host's pinned folder (Drishti: packaging/pinned)")
    ap.add_argument("--commit", default="HEAD")
    a = ap.parse_args(argv)
    commit = git("rev-parse", a.commit + "^{commit}").decode().strip()
    jar, cases = jar_bytes(a.version, commit, files_of(commit))
    into = Path(a.into)
    base = into / "maven" / GROUP_PATH / ARTIFACT
    folder = base / a.version
    name = f"{ARTIFACT}-{a.version}"
    if (folder / f"{name}.jar").exists() and (folder / f"{name}.jar").read_bytes() != jar:
        sys.exit(f"refusing: {folder} holds version {a.version} with other bytes; a version is immutable")
    if base.exists():
        for old in base.iterdir():
            if old.is_dir() and old.name != a.version:
                shutil.rmtree(old)
    folder.mkdir(parents=True, exist_ok=True)
    for ext, body in (("jar", jar), ("pom", pom(a.version))):
        (folder / f"{name}.{ext}").write_bytes(body)
        (folder / f"{name}.{ext}.sha1").write_text(hashlib.sha1(body).hexdigest() + "\n", encoding="utf-8")
    manifest = {"version": a.version, "commit": commit, "jars": {f"{name}.jar": hashlib.sha384(jar).hexdigest()},
                "elements": {"version": None, "formats.yaml": None, "world-regions.yaml": None}}
    (into / "drishti-rachana.json").write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"{name}.jar: {cases} cases from {commit[:12]}, sha384 {manifest['jars'][name + '.jar'][:16]}... into {folder}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
