#!/usr/bin/env python3
# Copyright (c) 2026 Ashutosh Sinha. Proprietary: see LICENSE.
"""CG8, the release candidate in Drishti (SUTRA_EXPANSION 6.5): drives a Drishti checkout at a ref with a Rachana candidate.

What it does, in order (the plan the tool prints and the report records):
  1. resolve the candidate (a tools/release/cases_jar.py or release.py layout: maven/ + drishti-rachana.json) and its commit;
  2. resolve the Drishti ref ("latest" = the newest v* tag, or "develop", or any ref) to a commit;
  3. find, at that commit, the tests CG8 names: ViewModelGoldenTest, PackSutrasTest, RachanaPinGuardTest, the Core conformance runner
     with the Drishti profile, the VaR goldens (exact), HotPathBenchmark (within 3%);
  4. [run mode only] copy that commit to a scratch folder (git archive: Drishti is never checked out, written or built in place),
     put the candidate into the scratch copy's packaging/pinned, run Maven per module for those tests, the benchmark twice;
  5. write dist/gate/<candidate>-<ref>.json: candidate commit, Drishti commits, each step's result. tools/release/release.py refuses
     to tag without a report for the candidate commit that says passed.

Default mode is DRY-RUN and READ-ONLY on Drishti (git ls-tree/rev-parse only): it prints the plan, reports which named tests exist at
the ref and which do not yet (they arrive with the migration, docs/DEVELOPING.md), and writes no report that could
satisfy the release tool (a dry run's report says "dry-run" and is refused by release.py). `--run` executes steps 4 and 5; it is for
the builder cutting a release, on the owner's machine, never beside a drill. Nothing here ever writes to Drishti's checkout.

    python3 tools/gate/drishti_gate.py --drishti ~/IdeaProjects/drishti --at latest [--candidate DIR] [--run] [--allow-absent]
"""
import argparse, json, os, re, shutil, subprocess, sys, tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
# the tests CG8 names: (step, file name pattern, tolerance)
STEPS = [
    ("view-model-goldens", r"ViewModelGoldenTest\.java$", "byte-identical (source mode and packaged)"),
    ("pack-sutras", r"PackSutrasTest\.java$", "no problems"),
    ("pin-guard", r"RachanaPinGuardTest\.java$", "pin matches the manifest"),
    ("core-conformance-drishti-profile", r"(CoreConformance|RachanaConformance)[A-Za-z]*Test\.java$", "every case passes"),
    ("var-goldens", r"(VarGoldenMatrixTest|PackagedVarGoldenTest)\.java$", "exact"),
    ("hot-path-benchmark", r"HotPathBenchmark\.java$", "within 3% of the baseline"),
]


def git(drishti, *args):
    return subprocess.run(["git", "-C", str(drishti), *args], check=True, capture_output=True, text=True).stdout.strip()


def resolve_ref(drishti, at):
    if at == "latest":
        tags = [t for t in git(drishti, "tag", "--list", "v*", "--sort=-creatordate").splitlines() if t]
        if not tags:
            sys.exit("no v* tag in the Drishti checkout")
        at = tags[0]
    return at, git(drishti, "rev-parse", at + "^{commit}")


def find_tests(drishti, commit):
    names = git(drishti, "ls-tree", "-r", "--name-only", commit).splitlines()
    found = {}
    for step, pat, _ in STEPS:
        found[step] = [n for n in names if re.search(pat, n)]
    return found


def module_of(path):
    return path.split("/src/")[0] if "/src/" in path else None


def candidate_info(cdir):
    if not cdir:
        return {"dir": None, "version": None, "commit": None}
    m = Path(cdir) / "drishti-rachana.json"
    info = json.loads(m.read_text()) if m.exists() else {}
    return {"dir": str(cdir), "version": info.get("version"), "commit": info.get("commit")}


def run_step(scratch, step, tests, tolerance):
    mods = {}
    for t in tests:
        m = module_of(t)
        if m:
            mods.setdefault(m, []).append(Path(t).stem)
    results = []
    for m, classes in sorted(mods.items()):
        cmd = ["mvn", "-B", "-q", "clean", "test", "-pl", m, "-Dtest=" + ",".join(classes), "-Dsurefire.failIfNoSpecifiedTests=false"]
        r = subprocess.run(cmd, cwd=scratch, capture_output=True, text=True)
        results.append({"module": m, "tests": classes, "ok": r.returncode == 0, "tail": r.stdout[-400:] + r.stderr[-400:]})
    return results


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--drishti", required=True, help="a Drishti checkout (read only; a scratch copy is built in --run)")
    ap.add_argument("--at", default="latest", help="latest | develop | any ref")
    ap.add_argument("--candidate", help="the release candidate's layout folder (maven/ and drishti-rachana.json)")
    ap.add_argument("--run", action="store_true", help="execute the steps (owner's machine, not beside a drill); default is the read-only dry run")
    ap.add_argument("--allow-absent", action="store_true", help="a named test absent at the ref is a skip, not a failure (the migration has not landed)")
    a = ap.parse_args()
    drishti = Path(a.drishti).expanduser().resolve()
    ref, commit = resolve_ref(drishti, a.at)
    cand = candidate_info(a.candidate)
    rachana_commit = subprocess.run(["git", "-C", str(ROOT), "rev-parse", "HEAD"], capture_output=True, text=True).stdout.strip()
    found = find_tests(drishti, commit)
    report = {"mode": "run" if a.run else "dry-run", "rachanaCommit": cand["commit"] or rachana_commit, "candidate": cand, "drishtiRef": ref,
              "drishtiCommit": commit, "steps": {}}
    print(f"gate: Rachana {report['rachanaCommit'][:12]} candidate {cand['version'] or '(none given)'} against Drishti {ref} {commit[:12]}")
    absent = 0
    for step, _, tol in STEPS:
        tests = found[step]
        print(f"  {step:36s} {tol:44s} " + (", ".join(Path(t).name for t in tests) if tests else "ABSENT at this commit"))
        report["steps"][step] = {"tolerance": tol, "tests": tests, "result": "planned" if tests else "absent"}
        absent += 0 if tests else 1
    if not a.run:
        print(f"dry run: read-only on Drishti, nothing executed, no report written ({absent} named test(s) absent at this commit).")
        return
    if not cand["dir"]:
        sys.exit("--run needs --candidate")
    if absent and not a.allow_absent:
        sys.exit(f"{absent} named test(s) absent at {ref}: the migration has not landed; use --allow-absent only for a rehearsal")
    scratch = Path(tempfile.mkdtemp(prefix="rachana-gate-"))
    try:
        tar = subprocess.run(["git", "-C", str(drishti), "archive", commit], check=True, capture_output=True).stdout
        subprocess.run(["tar", "-x", "-C", str(scratch)], input=tar, check=True)
        pinned = scratch / "packaging" / "pinned"
        if pinned.exists():
            shutil.rmtree(pinned)
        shutil.copytree(cand["dir"], pinned)
        ok = True
        for step, _, tol in STEPS:
            if not found[step]:
                report["steps"][step]["result"] = "skipped-absent"
                continue
            if step == "hot-path-benchmark":   # a JMH main class, not a test: its runner (twice, 3%) is written with the first real run
                report["steps"][step]["result"] = "not-automated"
                ok = False
                continue
            res = run_step(scratch, step, found[step], tol)
            report["steps"][step]["result"] = "passed" if all(r["ok"] for r in res) else "failed"
            report["steps"][step]["detail"] = res
            ok &= report["steps"][step]["result"] == "passed"
        report["passed"] = ok
    finally:
        shutil.rmtree(scratch, ignore_errors=True)
    out = ROOT / "dist" / "gate"
    out.mkdir(parents=True, exist_ok=True)
    f = out / f"{report['rachanaCommit'][:12]}-{ref}.json"
    f.write_text(json.dumps(report, indent=1) + "\n")
    print(("PASSED" if report["passed"] else "FAILED") + f": {f}")
    sys.exit(0 if report["passed"] else 1)


if __name__ == "__main__":
    main()
