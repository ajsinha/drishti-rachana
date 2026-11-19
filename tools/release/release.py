#!/usr/bin/env python3
# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see LICENSE.
"""Release: tag, build, and write the pinned-repository layout Drishti copies.

    python3 tools/release/release.py 0.9.0 [--dry-run]

Steps:
 1. refuse a dirty tree, a non-main branch, or an existing tag v<version> (a version is immutable);
 2. set the reactor version to <version> (versions-maven-plugin), run `java/mvnw -B clean verify`;
 3. write dist/rachana-<version>-pinned.zip with
      maven/com/ash/rachana/<artifact>/<version>/<artifact>-<version>.jar, .pom, and a .sha1 for each
      (artifacts: rachana-api, rachana-core, rachana-tools, rachana-data, rachana-conformance-cases)
      drishti-rachana.json: version, SHA-384 of each jar, the Elements version and the hashes of formats.yaml and
      world-regions.yaml Rachana was built with (read from the pinned tree js/vendor/drishti-elements/<version>/data)
    Drishti unpacks maven/ into packaging/pinned/maven and the manifest into packaging/pinned/ (decision N2);
 4. create the tag v<version> locally (pushing and the GitHub release stay a manual step).
"""
import hashlib, json, re, subprocess, sys, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "java"
ARTIFACTS = ["rachana-api", "rachana-core", "rachana-tools", "rachana-data", "rachana-conformance-cases"]
GROUP_PATH = "com/ash/rachana"


def run(*cmd, cwd=ROOT):
    print("+", " ".join(cmd))
    subprocess.run(cmd, cwd=cwd, check=True)


def out(*cmd):
    return subprocess.run(cmd, cwd=ROOT, check=True, capture_output=True, text=True).stdout.strip()


def digest(path, algo):
    h = hashlib.new(algo)
    h.update(path.read_bytes())
    return h.hexdigest()


def main(argv):
    if len(argv) < 2:
        sys.exit(__doc__)
    version, dry = argv[1], "--dry-run" in argv
    if out("git", "status", "--porcelain"):
        sys.exit("refusing: the working tree is not clean")
    if out("git", "rev-parse", "--abbrev-ref", "HEAD") != "main" and not dry:
        sys.exit("refusing: not on main")
    if out("git", "tag", "-l", f"v{version}"):
        sys.exit(f"refusing: tag v{version} exists; a released version is never rebuilt")
    mvnw = str(JAVA / "mvnw")
    previous = re.search(r"<artifactId>rachana-parent</artifactId>\s*<version>([^<]+)</version>", (JAVA / "pom.xml").read_text()).group(1)
    elements = re.search(r"<drishti.elements.version>([^<]+)</drishti.elements.version>", (JAVA / "pom.xml").read_text()).group(1)
    data = ROOT / "js" / "vendor" / "drishti-elements" / elements / "data"
    run(mvnw, "-B", "versions:set", f"-DnewVersion={version}", "-DgenerateBackupPoms=false", cwd=JAVA)
    try:
        run(mvnw, "-B", "clean", "verify", cwd=JAVA)
        dist = ROOT / "dist"
        dist.mkdir(exist_ok=True)
        manifest = {"version": version, "jars": {}, "elements": {"version": elements, "formats.yaml": digest(data / "formats.yaml", "sha384"),
                                                          "world-regions.yaml": digest(data / "world-regions.yaml", "sha384")}}
        with zipfile.ZipFile(dist / f"rachana-{version}-pinned.zip", "w", zipfile.ZIP_DEFLATED) as z:
            for a in ARTIFACTS:
                base = f"{a}-{version}"
                for ext in ("jar", "pom"):
                    src = JAVA / a / "target" / f"{base}.jar" if ext == "jar" else JAVA / a / "pom.xml"
                    arc = f"maven/{GROUP_PATH}/{a}/{version}/{base}.{ext}"
                    z.write(src, arc)
                    z.writestr(arc + ".sha1", digest(src, "sha1") + "\n")
                    if ext == "jar":
                        manifest["jars"][f"{base}.jar"] = digest(src, "sha384")
            parent = JAVA / "pom.xml"  # the module poms name it as their parent, so it travels too
            arc = f"maven/{GROUP_PATH}/rachana-parent/{version}/rachana-parent-{version}.pom"
            z.write(parent, arc)
            z.writestr(arc + ".sha1", digest(parent, "sha1") + "\n")
            z.writestr("drishti-rachana.json", json.dumps(manifest, indent=2, sort_keys=True) + "\n")
        if not dry:
            run("git", "tag", f"v{version}")
    finally:
        run(mvnw, "-B", "versions:set", f"-DnewVersion={previous}", "-DgenerateBackupPoms=false", cwd=JAVA)


if __name__ == "__main__":
    main(sys.argv)
