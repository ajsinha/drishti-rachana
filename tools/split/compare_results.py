#!/usr/bin/env python3
# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""The proof of each split step: the conformance results over this repository's code against the reference bridge's.

    python3 tools/split/compare_results.py REFERENCE.jsonl RACHANA.jsonl

Both files are rachana_conformance.py results (one line per case, canonical JSON). Every case must give the same line, byte for
byte, except the cases listed in known-differences.txt beside this script (each with the step that made it and why). Exit 0 when
that holds and no verified case fails in RACHANA; 1 otherwise. A listed case that no longer differs is reported, so the list
stays true.
"""
import json, sys
from pathlib import Path


def lines(p):
    return {json.loads(l)["case"]: l for l in Path(p).read_text(encoding="utf-8").splitlines() if l.strip()}


def main(argv):
    if len(argv) != 3:
        sys.exit(__doc__)
    ref, new = lines(argv[1]), lines(argv[2])
    known = {}
    for l in (Path(__file__).parent / "known-differences.txt").read_text(encoding="utf-8").splitlines():
        if l.strip() and not l.startswith("#"):
            case, _, why = l.partition(" ")
            known[case] = why.strip()
    bad = 0
    if set(ref) != set(new):
        print(f"case sets differ: only in reference {sorted(set(ref) - set(new))[:5]}, only here {sorted(set(new) - set(ref))[:5]}")
        bad += 1
    same = differ = 0
    for c in sorted(set(ref) & set(new)):
        if ref[c] == new[c]:
            same += 1
            if c in known:
                print(f"listed but now identical (remove it from known-differences.txt): {c}")
            continue
        differ += 1
        if c in known:
            print(f"known difference: {c}: {known[c]}")
        else:
            print(f"DIFFERS: {c}\n  reference: {ref[c][:300]}\n  rachana:   {new[c][:300]}")
            bad += 1
    status = {json.loads(new[c])["status"] for c in new}
    print(f"{same} identical, {differ} different ({differ - bad if bad <= differ else 0} known), statuses here: {sorted(status)}")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
