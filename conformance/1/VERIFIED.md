<!-- Copyright (c) 2026 Ashutosh Sinha. Specification licence: ../../LICENSE-SPEC.md (draft, not yet in force). -->
# Verification log of the conformance cases

Gate 2 review 9.6 procedure. Verifier: **independent review lane** (did not build the cases), 2026-11-21, on `g2/integration` at `40fa759`. A flip changes the `verified` flag and nothing else (`tools/conformance/verify_class_a.py` proves it by parsing the file before and after). FORMAT 3 still says a person flips a case: the owner reads this log and spot-checks (one case in ten of Class B) before the tag.

**Class A** (reference-recorded): the Drishti reference at `v2.9.11.i_7a` (RefBridge, and RefBridgeSutra for `sutra`) gives the case's expected answer, the case is not in `tools/split/known-differences.txt`, and the JVM runtime, the Node tools bundle and the Python V8 engine (tools bundle) all pass with the same results line byte for byte at level 1.0 and at the newest level for a case without `until`; the Node and Python V8 runtime bundles agree wherever they implement the kind. The script writes the list and the evidence; re-run: `verify_class_a.py run`, then `list`.

**Class B** (written by reading, or not recorded by the reference): the expected answer read against the section its `source` names (and the case's own arithmetic recomputed); all runtimes pass (the build proves it). The evidence column names the sections read. Where the spec is silent or ambiguous, or the case contradicts it, the case is not flipped and is listed at the end with the reason.

Totals: **246 Class A and 237 Class B flipped; 38 not flipped.**

## Flipped

### `conformance/1/el/determinism.yaml`

| Case id | Class | Evidence |
|---|---|---|
| `el/determinism#d-b01-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-b01-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-b01-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-b01-d` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-b02-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-b02-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-b02-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-b02-d` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-b03-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-b03-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-b03-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-b03-d` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-b04-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-b04-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-b04-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-b05-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-b05-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-b06-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-b06-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-b06-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-b06-d` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-b06-e` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-e1-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-e1-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-e1-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-l1-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-l1-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-l2-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-l2-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-l2-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-m01-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-m01-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-m01-d` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-m01-e` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-m01-f` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-m01-g` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-m01-h` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-m02-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-m02-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-m02-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-m02-d` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-m03-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-m03-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-m03-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-m03-d` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-m03-e` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-m03-f` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-m03-g` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-m03-h` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-m03-i` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-m04-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-m04-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-m04-d` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-m04-e` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-m05-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-m05-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-m05-d` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-m05-e` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-m06-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-m06-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-m06-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-m06-d` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-m06-e` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-m06-f` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-m07-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-m07-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-m07-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-m07-d` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-m07-g` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n01-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n02-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n03-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n03-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n03-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n04-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n04-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n04-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n05-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n05-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n05-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n05-d` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n06-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n06-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n06-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n07-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n07-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n07-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n07-d` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n07-e` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n08-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n08-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n08-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n09-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n09-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n09-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n10-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n10-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n10-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n10-d` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n10-e` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n12-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n12-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n12-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n12-d` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n13-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n13-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n14-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n14-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n14-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n20-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n21-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n21-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n22-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n22-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n22-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n22-d` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n23-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n23-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n23-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n23-d` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n23-e` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n24-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n24-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-o05-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-o05-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-r01-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-r01-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-r01-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-r02-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-r02-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-r02-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-r02-d` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-r03-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-r03-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-r03-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-r03-d` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-r04-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-r04-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-r04-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-r04-d` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-r05-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-r05-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-r05-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-r05-d` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-r05-e` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-r06-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-r06-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-t01-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-t01-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-t01-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-t02-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-t02-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-t02-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-t02-d` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-t03-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-t03-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-t03-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-t04-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-t04-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-t04-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-t05-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-t05-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-t05-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-t05-d` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-t05-e` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-t05-f` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-t06-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-t06-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-t06-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-t07-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-t07-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-t07-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-t07-d` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-t08-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-t08-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-t08-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-t08-d` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-t09-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-t09-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-t10-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-t10-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-t11-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-t11-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-t12-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-t14-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-t14-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-u01-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-u01-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-u01-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-u02-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-u02-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-u03-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-u03-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-u03-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-u03-d` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-u04-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-u04-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-u05-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-u05-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-u05-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-u05-d` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-u05-e` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-u05-f` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-u06-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-u06-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-u06-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-u07-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-u07-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-u07-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-u08-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-u08-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-u08-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-u08-d` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-u09-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-u09-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-u09-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-u10-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-u10-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-u10-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-x01-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-x01-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-x01-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-x02-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-x02-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-x02-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-x02-d` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-x02-e` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-x03-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-x03-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-x03-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-x04-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-x04-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-x04-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-x05-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-x05-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-x05-c` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-x06-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-x07-a` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-x07-b` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `el/determinism#d-n11-a` | B | spec read: SPEC 4.10 rule 1 (unknown function is RCH-2101; trunc, int, sumx are not Core names); reference gives the code too |
| `el/determinism#d-n11-b` | B | spec read: SPEC 4.10 rule 1 (unknown function is RCH-2101; trunc, int, sumx are not Core names); reference gives the code too |
| `el/determinism#d-n11-c` | B | spec read: SPEC 4.10 rule 1 (unknown function is RCH-2101; trunc, int, sumx are not Core names); reference gives the code too |
| `el/determinism#d-o03-a` | B | spec read: SPEC 4.10 rule 1 (unknown function is RCH-2101; trunc, int, sumx are not Core names); reference gives the code too |
| `el/determinism#d-m07-e` | B | spec read: SPEC 4.10 rule 1 (unknown function is RCH-2101; trunc, int, sumx are not Core names); reference gives the code too |
| `el/determinism#d-m07-f` | B | spec read: SPEC 4.10 rule 1 (unknown function is RCH-2101; trunc, int, sumx are not Core names); reference gives the code too |
| `el/determinism#d-n20-b` | B | spec read: SPEC 4.16 rule 7, 5.7 (compensated sum; doubles at 1.0); 5.7 PERCENTILE.INC, median, sample stddev; 5.6 group (key order by code point, 500 groups); values recomputed |
| `el/determinism#d-n20-c` | B | spec read: SPEC 4.16 rule 7, 5.7 (compensated sum; doubles at 1.0); 5.7 PERCENTILE.INC, median, sample stddev; 5.6 group (key order by code point, 500 groups); values recomputed |
| `el/determinism#d-n25-a` | B | spec read: SPEC 4.16 rule 7, 5.7 (compensated sum; doubles at 1.0); 5.7 PERCENTILE.INC, median, sample stddev; 5.6 group (key order by code point, 500 groups); values recomputed |
| `el/determinism#d-n25-b` | B | spec read: SPEC 4.16 rule 7, 5.7 (compensated sum; doubles at 1.0); 5.7 PERCENTILE.INC, median, sample stddev; 5.6 group (key order by code point, 500 groups); values recomputed |
| `el/determinism#d-n25-c` | B | spec read: SPEC 4.16 rule 7, 5.7 (compensated sum; doubles at 1.0); 5.7 PERCENTILE.INC, median, sample stddev; 5.6 group (key order by code point, 500 groups); values recomputed |
| `el/determinism#d-o02-a` | B | spec read: SPEC 4.16 rule 7, 5.7 (compensated sum; doubles at 1.0); 5.7 PERCENTILE.INC, median, sample stddev; 5.6 group (key order by code point, 500 groups); values recomputed |
| `el/determinism#d-o02-b` | B | spec read: SPEC 4.16 rule 7, 5.7 (compensated sum; doubles at 1.0); 5.7 PERCENTILE.INC, median, sample stddev; 5.6 group (key order by code point, 500 groups); values recomputed |

### `conformance/1/format/examples/format.yaml`

| Case id | Class | Evidence |
|---|---|---|
| `format/examples/format#empty-formats-as-empty-text` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `format/examples/format#masked-prints-dots-and-no-sign-tone` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `format/examples/format#percent-two-places` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `format/examples/format#signed-whole-amount-positive-tone` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `format/examples/format#status-tone-pending-is-warn` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `format/examples/format#unknown-format-is-the-text-form` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |
| `format/examples/format#unknown-tone-word-gives-no-tone` | A | reference i7a pass; jvm, noder, nodet, pyv8r, pyv8t same bytes at 1.0; at 1.2 jvm, noder, nodet, pyv8r, pyv8t |

### `conformance/1/el/functions-aggregates.yaml`

| Case id | Class | Evidence |
|---|---|---|
| `el/functions-aggregates#sum-1` | B | spec read: SPEC 5.7 and functions.yaml (the entry's example and description; arithmetic rechecked by hand) |
| `el/functions-aggregates#count-1` | B | spec read: SPEC 5.7 and functions.yaml (the entry's example and description; arithmetic rechecked by hand) |
| `el/functions-aggregates#avg-1` | B | spec read: SPEC 5.7 and functions.yaml (the entry's example and description; arithmetic rechecked by hand) |
| `el/functions-aggregates#median-1` | B | spec read: SPEC 5.7 and functions.yaml (the entry's example and description; arithmetic rechecked by hand) |
| `el/functions-aggregates#median-2` | B | spec read: SPEC 5.7 and functions.yaml (the entry's example and description; arithmetic rechecked by hand) |
| `el/functions-aggregates#variance-1` | B | spec read: SPEC 5.7 and functions.yaml (the entry's example and description; arithmetic rechecked by hand) |
| `el/functions-aggregates#stddev-1` | B | spec read: SPEC 5.7 and functions.yaml (the entry's example and description; arithmetic rechecked by hand) |
| `el/functions-aggregates#variance-pop-1` | B | spec read: SPEC 5.7 and functions.yaml (the entry's example and description; arithmetic rechecked by hand) |
| `el/functions-aggregates#stddev-pop-1` | B | spec read: SPEC 5.7 and functions.yaml (the entry's example and description; arithmetic rechecked by hand) |
| `el/functions-aggregates#distinct-1` | B | spec read: SPEC 5.7 and functions.yaml (the entry's example and description; arithmetic rechecked by hand) |
| `el/functions-aggregates#percentile-1` | B | spec read: SPEC 5.7 and functions.yaml (the entry's example and description; arithmetic rechecked by hand) |
| `el/functions-aggregates#percentile-2` | B | spec read: SPEC 5.7 and functions.yaml (the entry's example and description; arithmetic rechecked by hand) |
| `el/functions-aggregates#wavg-1` | B | spec read: SPEC 5.7 and functions.yaml (the entry's example and description; arithmetic rechecked by hand) |

### `conformance/1/el/functions-math.yaml`

| Case id | Class | Evidence |
|---|---|---|
| `el/functions-math#min-1` | B | spec read: SPEC 5.3 (min, max over one list), 5.7 |
| `el/functions-math#max-1` | B | spec read: SPEC 5.3 (min, max over one list), 5.7 |

### `conformance/1/el/python-words.yaml`

| Case id | Class | Evidence |
|---|---|---|
| `el/python-words#and-is-and` | B | spec read: SPEC 4.8 (and, or, not as Python reads them; not below the comparisons, and over or); outcomes recomputed by hand |
| `el/python-words#or-is-or` | B | spec read: SPEC 4.8 (and, or, not as Python reads them; not below the comparisons, and over or); outcomes recomputed by hand |
| `el/python-words#not-reads-below-the-comparison` | B | spec read: SPEC 4.8 (and, or, not as Python reads them; not below the comparisons, and over or); outcomes recomputed by hand |
| `el/python-words#not-binds-tighter-than-and` | B | spec read: SPEC 4.8 (and, or, not as Python reads them; not below the comparisons, and over or); outcomes recomputed by hand |
| `el/python-words#and-binds-tighter-than-or` | B | spec read: SPEC 4.8 (and, or, not as Python reads them; not below the comparisons, and over or); outcomes recomputed by hand |
| `el/python-words#not-in-is-still-membership` | B | spec read: SPEC 4.8 (and, or, not as Python reads them; not below the comparisons, and over or); outcomes recomputed by hand |

### `conformance/1/el/check.yaml`

| Case id | Class | Evidence |
|---|---|---|
| `el/check#check-notional-1000000` | B | spec read: SPEC 4.14 and 6.2 (9127 type or scope; 9152 unknown name; decimal for /; functions.yaml power returns decimal; date - date integer; old spelling) |
| `el/check#check-tradedate-tradedate` | B | spec read: SPEC 4.14 and 6.2 (9127 type or scope; 9152 unknown name; decimal for /; functions.yaml power returns decimal; date - date integer; old spelling) |
| `el/check#check-power-n-2` | B | spec read: SPEC 4.14 and 6.2 (9127 type or scope; 9152 unknown name; decimal for /; functions.yaml power returns decimal; date - date integer; old spelling) |
| `el/check#check-status-null-n-0` | B | spec read: SPEC 4.14 and 6.2 (9127 type or scope; 9152 unknown name; decimal for /; functions.yaml power returns decimal; date - date integer; old spelling) |
| `el/check#check-mtm-n` | B | spec read: SPEC 4.14 and 6.2 (9127 type or scope; 9152 unknown name; decimal for /; functions.yaml power returns decimal; date - date integer; old spelling) |
| `el/check#check-status-1` | B | spec read: SPEC 4.14 and 6.2 (9127 type or scope; 9152 unknown name; decimal for /; functions.yaml power returns decimal; date - date integer; old spelling) |
| `el/check#check-tradedate-9-oct` | B | spec read: SPEC 4.14 and 6.2 (9127 type or scope; 9152 unknown name; decimal for /; functions.yaml power returns decimal; date - date integer; old spelling) |
| `el/check#check-mtx` | B | spec read: SPEC 4.14 and 6.2 (9127 type or scope; 9152 unknown name; decimal for /; functions.yaml power returns decimal; date - date integer; old spelling) |
| `el/check#check-startswith-status-l` | B | spec read: SPEC 4.14 and 6.2 (9127 type or scope; 9152 unknown name; decimal for /; functions.yaml power returns decimal; date - date integer; old spelling) |
| `el/check#check-0-n-10` | B | spec read: SPEC 4.14 and 6.2 (9127 type or scope; 9152 unknown name; decimal for /; functions.yaml power returns decimal; date - date integer; old spelling) |
| `el/check#check-ctl-desk-status` | B | spec read: SPEC 4.14 and 6.2 (9127 type or scope; 9152 unknown name; decimal for /; functions.yaml power returns decimal; date - date integer; old spelling) |
| `el/check#check-n-1-2` | B | spec read: SPEC 4.14 and 6.2 (9127 type or scope; 9152 unknown name; decimal for /; functions.yaml power returns decimal; date - date integer; old spelling) |

### `conformance/1/el/examples/check.yaml`

| Case id | Class | Evidence |
|---|---|---|
| `el/examples/check#integer-times-integer-is-integer` | B | spec read: SPEC 4.14 (the sentence each case is named after) and 6.2 |
| `el/examples/check#divide-is-decimal` | B | spec read: SPEC 4.14 (the sentence each case is named after) and 6.2 |
| `el/examples/check#date-minus-date-is-integer-days` | B | spec read: SPEC 4.14 (the sentence each case is named after) and 6.2 |
| `el/examples/check#text-plus-number-is-an-implicit-cast` | B | spec read: SPEC 4.14 (the sentence each case is named after) and 6.2 |
| `el/examples/check#control-in-data-scope-refused` | B | spec read: SPEC 4.14 (the sentence each case is named after) and 6.2 |
| `el/examples/check#unknown-name-did-you-mean` | B | spec read: SPEC 4.14 (the sentence each case is named after) and 6.2 |

### `conformance/1/grammar/examples/grammar.yaml`

| Case id | Class | Evidence |
|---|---|---|
| `grammar/examples/grammar#call-with-path-and-text` | B | spec read: SPEC 2.1, 4.4, 4.10 rule 3, 4.11, 6.3 (the sentence each case is named after) |
| `grammar/examples/grammar#filter-then-projection` | B | spec read: SPEC 2.1, 4.4, 4.10 rule 3, 4.11, 6.3 (the sentence each case is named after) |
| `grammar/examples/grammar#named-argument-after-positional` | B | spec read: SPEC 2.1, 4.4, 4.10 rule 3, 4.11, 6.3 (the sentence each case is named after) |
| `grammar/examples/grammar#single-ampersand-refused` | B | spec read: SPEC 2.1, 4.4, 4.10 rule 3, 4.11, 6.3 (the sentence each case is named after) |
| `grammar/examples/grammar#assignment-refused` | B | spec read: SPEC 2.1, 4.4, 4.10 rule 3, 4.11, 6.3 (the sentence each case is named after) |
| `grammar/examples/grammar#template-with-parts` | B | spec read: SPEC 2.1, 4.4, 4.10 rule 3, 4.11, 6.3 (the sentence each case is named after) |
| `grammar/examples/grammar#template-with-unclosed-part` | B | spec read: SPEC 2.1, 4.4, 4.10 rule 3, 4.11, 6.3 (the sentence each case is named after) |

### `conformance/1/grammar/reserved.yaml`

| Case id | Class | Evidence |
|---|---|---|
| `grammar/reserved#pipe-refused` | B | spec read: SPEC 4.15 rule 1 (reserved tokens refused at 1.0, characters inside a text); 4.17 and 4.11 (accepted from 1.1); EBNF identifier, number |
| `grammar/reserved#empty-default-refused` | B | spec read: SPEC 4.15 rule 1 (reserved tokens refused at 1.0, characters inside a text); 4.17 and 4.11 (accepted from 1.1); EBNF identifier, number |
| `grammar/reserved#arrow-refused` | B | spec read: SPEC 4.15 rule 1 (reserved tokens refused at 1.0, characters inside a text); 4.17 and 4.11 (accepted from 1.1); EBNF identifier, number |
| `grammar/reserved#spread-refused` | B | spec read: SPEC 4.15 rule 1 (reserved tokens refused at 1.0, characters inside a text); 4.17 and 4.11 (accepted from 1.1); EBNF identifier, number |
| `grammar/reserved#range-refused` | B | spec read: SPEC 4.15 rule 1 (reserved tokens refused at 1.0, characters inside a text); 4.17 and 4.11 (accepted from 1.1); EBNF identifier, number |
| `grammar/reserved#empty-record-refused` | B | spec read: SPEC 4.15 rule 1 (reserved tokens refused at 1.0, characters inside a text); 4.17 and 4.11 (accepted from 1.1); EBNF identifier, number |
| `grammar/reserved#comment-refused` | B | spec read: SPEC 4.15 rule 1 (reserved tokens refused at 1.0, characters inside a text); 4.17 and 4.11 (accepted from 1.1); EBNF identifier, number |
| `grammar/reserved#digit-separator-refused` | B | spec read: SPEC 4.15 rule 1 (reserved tokens refused at 1.0, characters inside a text); 4.17 and 4.11 (accepted from 1.1); EBNF identifier, number |
| `grammar/reserved#reserved-tokens-in-a-string-accepted` | B | spec read: SPEC 4.15 rule 1 (reserved tokens refused at 1.0, characters inside a text); 4.17 and 4.11 (accepted from 1.1); EBNF identifier, number |
| `grammar/reserved#or-accepted` | B | spec read: SPEC 4.15 rule 1 (reserved tokens refused at 1.0, characters inside a text); 4.17 and 4.11 (accepted from 1.1); EBNF identifier, number |
| `grammar/reserved#conditional-accepted` | B | spec read: SPEC 4.15 rule 1 (reserved tokens refused at 1.0, characters inside a text); 4.17 and 4.11 (accepted from 1.1); EBNF identifier, number |
| `grammar/reserved#filter-accepted` | B | spec read: SPEC 4.15 rule 1 (reserved tokens refused at 1.0, characters inside a text); 4.17 and 4.11 (accepted from 1.1); EBNF identifier, number |
| `grammar/reserved#numbers-with-points-accepted` | B | spec read: SPEC 4.15 rule 1 (reserved tokens refused at 1.0, characters inside a text); 4.17 and 4.11 (accepted from 1.1); EBNF identifier, number |
| `grammar/reserved#underscore-in-a-name-accepted` | B | spec read: SPEC 4.15 rule 1 (reserved tokens refused at 1.0, characters inside a text); 4.17 and 4.11 (accepted from 1.1); EBNF identifier, number |
| `grammar/reserved#braces-in-template-text-accepted` | B | spec read: SPEC 4.15 rule 1 (reserved tokens refused at 1.0, characters inside a text); 4.17 and 4.11 (accepted from 1.1); EBNF identifier, number |
| `grammar/reserved#reserved-token-in-a-template-part-refused` | B | spec read: SPEC 4.15 rule 1 (reserved tokens refused at 1.0, characters inside a text); 4.17 and 4.11 (accepted from 1.1); EBNF identifier, number |
| `grammar/reserved#pipe-accepted-l11` | B | spec read: SPEC 4.15 rule 1 (reserved tokens refused at 1.0, characters inside a text); 4.17 and 4.11 (accepted from 1.1); EBNF identifier, number |
| `grammar/reserved#empty-default-accepted-l11` | B | spec read: SPEC 4.15 rule 1 (reserved tokens refused at 1.0, characters inside a text); 4.17 and 4.11 (accepted from 1.1); EBNF identifier, number |
| `grammar/reserved#spread-accepted-l11` | B | spec read: SPEC 4.15 rule 1 (reserved tokens refused at 1.0, characters inside a text); 4.17 and 4.11 (accepted from 1.1); EBNF identifier, number |
| `grammar/reserved#empty-record-accepted-l11` | B | spec read: SPEC 4.15 rule 1 (reserved tokens refused at 1.0, characters inside a text); 4.17 and 4.11 (accepted from 1.1); EBNF identifier, number |
| `grammar/reserved#comment-accepted-l11` | B | spec read: SPEC 4.15 rule 1 (reserved tokens refused at 1.0, characters inside a text); 4.17 and 4.11 (accepted from 1.1); EBNF identifier, number |
| `grammar/reserved#digit-separator-accepted-l11` | B | spec read: SPEC 4.15 rule 1 (reserved tokens refused at 1.0, characters inside a text); 4.17 and 4.11 (accepted from 1.1); EBNF identifier, number |
| `grammar/reserved#reserved-token-in-a-template-part-accepted-l11` | B | spec read: SPEC 4.15 rule 1 (reserved tokens refused at 1.0, characters inside a text); 4.17 and 4.11 (accepted from 1.1); EBNF identifier, number |

### `conformance/1/problem/data.yaml`

| Case id | Class | Evidence |
|---|---|---|
| `problem/data#two-sources-in-one-entry` | B | spec read: SPEC 2.4 (exactly one source key, RCH-2073; key required for live, RCH-2072; each entry a mapping/text/url shorthand; rows a list) and 6.2 (2011, 2012) |
| `problem/data#no-source-in-an-entry` | B | spec read: SPEC 2.4 (exactly one source key, RCH-2073; key required for live, RCH-2072; each entry a mapping/text/url shorthand; rows a list) and 6.2 (2011, 2012) |
| `problem/data#live-needs-a-key` | B | spec read: SPEC 2.4 (exactly one source key, RCH-2073; key required for live, RCH-2072; each entry a mapping/text/url shorthand; rows a list) and 6.2 (2011, 2012) |
| `problem/data#unknown-key-in-an-entry` | B | spec read: SPEC 2.4 (exactly one source key, RCH-2073; key required for live, RCH-2072; each entry a mapping/text/url shorthand; rows a list) and 6.2 (2011, 2012) |
| `problem/data#rows-must-be-a-list-of-mappings` | B | spec read: SPEC 2.4 (exactly one source key, RCH-2073; key required for live, RCH-2072; each entry a mapping/text/url shorthand; rows a list) and 6.2 (2011, 2012) |
| `problem/data#an-address-is-a-url-entry` | B | spec read: SPEC 2.4 (exactly one source key, RCH-2073; key required for live, RCH-2072; each entry a mapping/text/url shorthand; rows a list) and 6.2 (2011, 2012) |

### `conformance/1/sutra/data.yaml`

| Case id | Class | Evidence |
|---|---|---|
| `sutra/data#rows-entry-is-a-field-of-the-record` | B | spec read: SPEC 2.4 (each entry a field of $; where reads controls; limit; host record beside data), 5.7 sums recomputed, FORMAT 4 tables, 5.4 amount0 |
| `sutra/data#record-entry-is-an-object` | B | spec read: SPEC 2.4 (each entry a field of $; where reads controls; limit; host record beside data), 5.7 sums recomputed, FORMAT 4 tables, 5.4 amount0 |
| `sutra/data#table-entry-from-the-host` | B | spec read: SPEC 2.4 (each entry a field of $; where reads controls; limit; host record beside data), 5.7 sums recomputed, FORMAT 4 tables, 5.4 amount0 |
| `sutra/data#entry-where-reads-a-control` | B | spec read: SPEC 2.4 (each entry a field of $; where reads controls; limit; host record beside data), 5.7 sums recomputed, FORMAT 4 tables, 5.4 amount0 |
| `sutra/data#entry-limit-keeps-the-first-rows` | B | spec read: SPEC 2.4 (each entry a field of $; where reads controls; limit; host record beside data), 5.7 sums recomputed, FORMAT 4 tables, 5.4 amount0 |
| `sutra/data#host-record-and-data-together` | B | spec read: SPEC 2.4 (each entry a field of $; where reads controls; limit; host record beside data), 5.7 sums recomputed, FORMAT 4 tables, 5.4 amount0 |

### `conformance/1/live/declaration.yaml`

| Case id | Class | Evidence |
|---|---|---|
| `live/declaration#every-problem-at-once-in-position-order` | B | spec read: LIVE_TABLES 2, 2.1, 1.6 and SPEC 6.2 (codes 2020, 2023, 2024, 2060, 2071, 2072, 2080-2083, 2011, 2101; problems in position order, 6.1) |
| `live/declaration#edit-needs-a-key-and-a-version` | B | spec read: LIVE_TABLES 2, 2.1, 1.6 and SPEC 6.2 (codes 2020, 2023, 2024, 2060, 2071, 2072, 2080-2083, 2011, 2101; problems in position order, 6.1) |
| `live/declaration#key-version-and-change-columns-are-not-editable` | B | spec read: LIVE_TABLES 2, 2.1, 1.6 and SPEC 6.2 (codes 2020, 2023, 2024, 2060, 2071, 2072, 2080-2083, 2011, 2101; problems in position order, 6.1) |
| `live/declaration#heavy-column-is-never-in-a-list` | B | spec read: LIVE_TABLES 2, 2.1, 1.6 and SPEC 6.2 (codes 2020, 2023, 2024, 2060, 2071, 2072, 2080-2083, 2011, 2101; problems in position order, 6.1) |
| `live/declaration#profile-keys-without-their-profile` | B | spec read: LIVE_TABLES 2, 2.1, 1.6 and SPEC 6.2 (codes 2020, 2023, 2024, 2060, 2071, 2072, 2080-2083, 2011, 2101; problems in position order, 6.1) |
| `live/declaration#profile-keys-with-their-profile` | B | spec read: LIVE_TABLES 2, 2.1, 1.6 and SPEC 6.2 (codes 2020, 2023, 2024, 2060, 2071, 2072, 2080-2083, 2011, 2101; problems in position order, 6.1) |
| `live/declaration#unknown-key-and-condition-that-does-not-compile` | B | spec read: LIVE_TABLES 2, 2.1, 1.6 and SPEC 6.2 (codes 2020, 2023, 2024, 2060, 2071, 2072, 2080-2083, 2011, 2101; problems in position order, 6.1) |

### `conformance/1/live/merge.yaml`

| Case id | Class | Evidence |
|---|---|---|
| `live/merge#older-and-equal-versions-are-dropped` | B | spec read: LIVE_TABLES 1.3 (version rule, no version column, deletion, where condition, key text) and PROTOCOL 8.1 (remove is a list of key values; each applied change takes the next seq) |
| `live/merge#a-newer-version-replaces-the-row` | B | spec read: LIVE_TABLES 1.3 (version rule, no version column, deletion, where condition, key text) and PROTOCOL 8.1 (remove is a list of key values; each applied change takes the next seq) |
| `live/merge#a-skipped-version-is-applied` | B | spec read: LIVE_TABLES 1.3 (version rule, no version column, deletion, where condition, key text) and PROTOCOL 8.1 (remove is a list of key values; each applied change takes the next seq) |
| `live/merge#versions-compare-as-numbers` | B | spec read: LIVE_TABLES 1.3 (version rule, no version column, deletion, where condition, key text) and PROTOCOL 8.1 (remove is a list of key values; each applied change takes the next seq) |
| `live/merge#without-a-version-column-the-latest-arrival-wins` | B | spec read: LIVE_TABLES 1.3 (version rule, no version column, deletion, where condition, key text) and PROTOCOL 8.1 (remove is a list of key values; each applied change takes the next seq) |
| `live/merge#a-deletion-removes-the-row-and-is-sent` | B | spec read: LIVE_TABLES 1.3 (version rule, no version column, deletion, where condition, key text) and PROTOCOL 8.1 (remove is a list of key values; each applied change takes the next seq) |
| `live/merge#a-row-that-stops-holding-the-condition-leaves` | B | spec read: LIVE_TABLES 1.3 (version rule, no version column, deletion, where condition, key text) and PROTOCOL 8.1 (remove is a list of key values; each applied change takes the next seq) |
| `live/merge#a-snapshot-row-outside-the-condition-is-not-held` | B | spec read: LIVE_TABLES 1.3 (version rule, no version column, deletion, where condition, key text) and PROTOCOL 8.1 (remove is a list of key values; each applied change takes the next seq) |
| `live/merge#compound-and-numeric-keys` | B | spec read: LIVE_TABLES 1.3 (version rule, no version column, deletion, where condition, key text) and PROTOCOL 8.1 (remove is a list of key values; each applied change takes the next seq) |

### `conformance/1/live/overlay.yaml`

| Case id | Class | Evidence |
|---|---|---|
| `live/overlay#a-changed-row-shadows-the-sources-and-moves-in-the-order` | B | spec read: LIVE_TABLES 1.1, 1.3, 1.5, 7 and PROTOCOL 6.1, 6.2 (merged order, key tie-break, nulls last by default, keyset cursor, offset over the merged order; totals recomputed by hand). Two cases (a-new-source-version-reads-the-index-again, an-order-by-a-column-the-source-does-not-keep) rest on design-doc statements about index keeping and absent sort values, not on SPEC or PROTOCOL text |
| `live/overlay#a-removed-key-hides-the-sources-row` | B | spec read: LIVE_TABLES 1.1, 1.3, 1.5, 7 and PROTOCOL 6.1, 6.2 (merged order, key tie-break, nulls last by default, keyset cursor, offset over the merged order; totals recomputed by hand). Two cases (a-new-source-version-reads-the-index-again, an-order-by-a-column-the-source-does-not-keep) rest on design-doc statements about index keeping and absent sort values, not on SPEC or PROTOCOL text |
| `live/overlay#a-new-row-takes-its-place-in-the-order` | B | spec read: LIVE_TABLES 1.1, 1.3, 1.5, 7 and PROTOCOL 6.1, 6.2 (merged order, key tie-break, nulls last by default, keyset cursor, offset over the merged order; totals recomputed by hand). Two cases (a-new-source-version-reads-the-index-again, an-order-by-a-column-the-source-does-not-keep) rest on design-doc statements about index keeping and absent sort values, not on SPEC or PROTOCOL text |
| `live/overlay#a-new-source-version-reads-the-index-again` | B | spec read: LIVE_TABLES 1.1, 1.3, 1.5, 7 and PROTOCOL 6.1, 6.2 (merged order, key tie-break, nulls last by default, keyset cursor, offset over the merged order; totals recomputed by hand). Two cases (a-new-source-version-reads-the-index-again, an-order-by-a-column-the-source-does-not-keep) rest on design-doc statements about index keeping and absent sort values, not on SPEC or PROTOCOL text |
| `live/overlay#the-whole-sort-list-orders-a-page` | B | spec read: LIVE_TABLES 1.1, 1.3, 1.5, 7 and PROTOCOL 6.1, 6.2 (merged order, key tie-break, nulls last by default, keyset cursor, offset over the merged order; totals recomputed by hand). Two cases (a-new-source-version-reads-the-index-again, an-order-by-a-column-the-source-does-not-keep) rest on design-doc statements about index keeping and absent sort values, not on SPEC or PROTOCOL text |
| `live/overlay#absent-values-last-unless-nulls-first` | B | spec read: LIVE_TABLES 1.1, 1.3, 1.5, 7 and PROTOCOL 6.1, 6.2 (merged order, key tie-break, nulls last by default, keyset cursor, offset over the merged order; totals recomputed by hand). Two cases (a-new-source-version-reads-the-index-again, an-order-by-a-column-the-source-does-not-keep) rest on design-doc statements about index keeping and absent sort values, not on SPEC or PROTOCOL text |
| `live/overlay#a-cursor-continues-after-its-last-row` | B | spec read: LIVE_TABLES 1.1, 1.3, 1.5, 7 and PROTOCOL 6.1, 6.2 (merged order, key tie-break, nulls last by default, keyset cursor, offset over the merged order; totals recomputed by hand). Two cases (a-new-source-version-reads-the-index-again, an-order-by-a-column-the-source-does-not-keep) rest on design-doc statements about index keeping and absent sort values, not on SPEC or PROTOCOL text |
| `live/overlay#an-offset-page-of-the-merged-order` | B | spec read: LIVE_TABLES 1.1, 1.3, 1.5, 7 and PROTOCOL 6.1, 6.2 (merged order, key tie-break, nulls last by default, keyset cursor, offset over the merged order; totals recomputed by hand). Two cases (a-new-source-version-reads-the-index-again, an-order-by-a-column-the-source-does-not-keep) rest on design-doc statements about index keeping and absent sort values, not on SPEC or PROTOCOL text |
| `live/overlay#an-order-by-a-column-the-source-does-not-keep` | B | spec read: LIVE_TABLES 1.1, 1.3, 1.5, 7 and PROTOCOL 6.1, 6.2 (merged order, key tie-break, nulls last by default, keyset cursor, offset over the merged order; totals recomputed by hand). Two cases (a-new-source-version-reads-the-index-again, an-order-by-a-column-the-source-does-not-keep) rest on design-doc statements about index keeping and absent sort values, not on SPEC or PROTOCOL text |
| `live/overlay#the-query-tier-is-shadowed-in-place` | B | spec read: LIVE_TABLES 1.1, 1.3, 1.5, 7 and PROTOCOL 6.1, 6.2 (merged order, key tie-break, nulls last by default, keyset cursor, offset over the merged order; totals recomputed by hand). Two cases (a-new-source-version-reads-the-index-again, an-order-by-a-column-the-source-does-not-keep) rest on design-doc statements about index keeping and absent sort values, not on SPEC or PROTOCOL text |
| `live/overlay#a-filtered-page-and-its-total` | B | spec read: LIVE_TABLES 1.1, 1.3, 1.5, 7 and PROTOCOL 6.1, 6.2 (merged order, key tie-break, nulls last by default, keyset cursor, offset over the merged order; totals recomputed by hand). Two cases (a-new-source-version-reads-the-index-again, an-order-by-a-column-the-source-does-not-keep) rest on design-doc statements about index keeping and absent sort values, not on SPEC or PROTOCOL text |

### `conformance/1/live/readers.yaml`

| Case id | Class | Evidence |
|---|---|---|
| `live/readers#two-readers-see-their-own-rows-and-totals` | B | spec read: LIVE_TABLES 3, 5 and PROTOCOL 3.2, 11, 16 Readers (own rows, totals, patches; masks; refusal semantics; audit read-refused; fail closed) |
| `live/readers#the-same-paged-table-for-two-readers` | B | spec read: LIVE_TABLES 3, 5 and PROTOCOL 3.2, 11, 16 Readers (own rows, totals, patches; masks; refusal semantics; audit read-refused; fail closed) |
| `live/readers#patches-reach-only-the-readers-whose-rows-they-are` | B | spec read: LIVE_TABLES 3, 5 and PROTOCOL 3.2, 11, 16 Readers (own rows, totals, patches; masks; refusal semantics; audit read-refused; fail closed) |
| `live/readers#a-row-that-leaves-a-readers-scope-is-removed-for-them` | B | spec read: LIVE_TABLES 3, 5 and PROTOCOL 3.2, 11, 16 Readers (own rows, totals, patches; masks; refusal semantics; audit read-refused; fail closed) |
| `live/readers#a-windowed-readers-total-counts-their-rows-only` | B | spec read: LIVE_TABLES 3, 5 and PROTOCOL 3.2, 11, 16 Readers (own rows, totals, patches; masks; refusal semantics; audit read-refused; fail closed) |
| `live/readers#masked-values-are-sent-masked` | B | spec read: LIVE_TABLES 3, 5 and PROTOCOL 3.2, 11, 16 Readers (own rows, totals, patches; masks; refusal semantics; audit read-refused; fail closed) |
| `live/readers#a-masked-value-never-orders-or-filters` | B | spec read: LIVE_TABLES 3, 5 and PROTOCOL 3.2, 11, 16 Readers (own rows, totals, patches; masks; refusal semantics; audit read-refused; fail closed) |
| `live/readers#a-refused-reader-is-forbidden-and-audited` | B | spec read: LIVE_TABLES 3, 5 and PROTOCOL 3.2, 11, 16 Readers (own rows, totals, patches; masks; refusal semantics; audit read-refused; fail closed) |
| `live/readers#a-reader-told-nothing-of-the-table-sees-it-unknown` | B | spec read: LIVE_TABLES 3, 5 and PROTOCOL 3.2, 11, 16 Readers (own rows, totals, patches; masks; refusal semantics; audit read-refused; fail closed) |
| `live/readers#without-an-authoriser-reads-are-refused` | B | spec read: LIVE_TABLES 3, 5 and PROTOCOL 3.2, 11, 16 Readers (own rows, totals, patches; masks; refusal semantics; audit read-refused; fail closed) |
| `live/readers#a-subscription-of-a-refused-reader-is-refused` | B | spec read: LIVE_TABLES 3, 5 and PROTOCOL 3.2, 11, 16 Readers (own rows, totals, patches; masks; refusal semantics; audit read-refused; fail closed) |

### `conformance/1/live/resync.yaml`

| Case id | Class | Evidence |
|---|---|---|
| `live/resync#a-stream-gap-starts-a-new-epoch` | B | spec read: LIVE_TABLES 1.2, 1.3, 1.4, LT-15 and PROTOCOL 8.3, 16 (gap and new epoch, resync, paused state with reason, trimmed paged table resyncs) |
| `live/resync#after-a-gap-the-snapshot-is-read-again` | B | spec read: LIVE_TABLES 1.2, 1.3, 1.4, LT-15 and PROTOCOL 8.3, 16 (gap and new epoch, resync, paused state with reason, trimmed paged table resyncs) |
| `live/resync#a-paused-stream-serves-the-snapshot` | B | spec read: LIVE_TABLES 1.2, 1.3, 1.4, LT-15 and PROTOCOL 8.3, 16 (gap and new epoch, resync, paused state with reason, trimmed paged table resyncs) |
| `live/resync#a-reconnected-stream-is-up-again` | B | spec read: LIVE_TABLES 1.2, 1.3, 1.4, LT-15 and PROTOCOL 8.3, 16 (gap and new epoch, resync, paused state with reason, trimmed paged table resyncs) |
| `live/resync#a-trimmed-paged-table-resyncs` | B | spec read: LIVE_TABLES 1.2, 1.3, 1.4, LT-15 and PROTOCOL 8.3, 16 (gap and new epoch, resync, paused state with reason, trimmed paged table resyncs) |

### `conformance/1/live/retention.yaml`

| Case id | Class | Evidence |
|---|---|---|
| `live/retention#ttl-by-a-column` | B | spec read: LIVE_TABLES 1.4 (ttl by column or arrival, max-rows oldest first, idle close and reopen; the adapter clock start is stated in the file's description) |
| `live/retention#ttl-by-arrival` | B | spec read: LIVE_TABLES 1.4 (ttl by column or arrival, max-rows oldest first, idle close and reopen; the adapter clock start is stated in the file's description) |
| `live/retention#max-rows-drops-the-oldest` | B | spec read: LIVE_TABLES 1.4 (ttl by column or arrival, max-rows oldest first, idle close and reopen; the adapter clock start is stated in the file's description) |
| `live/retention#idle-close-and-reopen` | B | spec read: LIVE_TABLES 1.4 (ttl by column or arrival, max-rows oldest first, idle close and reopen; the adapter clock start is stated in the file's description) |
| `live/retention#a-table-with-a-reader-stays-open` | B | spec read: LIVE_TABLES 1.4 (ttl by column or arrival, max-rows oldest first, idle close and reopen; the adapter clock start is stated in the file's description) |

### `conformance/1/protocol/examples/bridge.yaml`

| Case id | Class | Evidence |
|---|---|---|
| `protocol/examples/bridge#bridge-hello-welcome` | B | spec read: PROTOCOL 12, 10.1 (hello/welcome, unknown table as an error res, ping/pong) |
| `protocol/examples/bridge#bridge-unknown-table-is-an-error-res` | B | spec read: PROTOCOL 12, 10.1 (hello/welcome, unknown table as an error res, ping/pong) |
| `protocol/examples/bridge#bridge-ping-pong` | B | spec read: PROTOCOL 12, 10.1 (hello/welcome, unknown table as an error res, ping/pong) |

### `conformance/1/protocol/examples/error.yaml`

| Case id | Class | Evidence |
|---|---|---|
| `protocol/examples/error#error-unknown-table` | B | spec read: PROTOCOL 4, 6.2, 8.1, 10.1 and error.json (table, column, expected, operation fields) |
| `protocol/examples/error#error-unknown-column-expected-names` | B | spec read: PROTOCOL 4, 6.2, 8.1, 10.1 and error.json (table, column, expected, operation fields) |
| `protocol/examples/error#error-cursor-invalid` | B | spec read: PROTOCOL 4, 6.2, 8.1, 10.1 and error.json (table, column, expected, operation fields) |
| `protocol/examples/error#error-sort-not-declared-is-unsupported` | B | spec read: PROTOCOL 4, 6.2, 8.1, 10.1 and error.json (table, column, expected, operation fields) |
| `protocol/examples/error#error-changes-need-a-key` | B | spec read: PROTOCOL 4, 6.2, 8.1, 10.1 and error.json (table, column, expected, operation fields) |

### `conformance/1/protocol/examples/filter.yaml`

| Case id | Class | Evidence |
|---|---|---|
| `protocol/examples/filter#filter-and-sort` | B | spec read: PROTOCOL 6.1, 6.3, 7, 11 and SPEC 4.5-4.8 (results recomputed by hand over the fixture rows) |
| `protocol/examples/filter#filter-in-list-and-null` | B | spec read: PROTOCOL 6.1, 6.3, 7, 11 and SPEC 4.5-4.8 (results recomputed by hand over the fixture rows) |
| `protocol/examples/filter#filter-case-insensitive-contains` | B | spec read: PROTOCOL 6.1, 6.3, 7, 11 and SPEC 4.5-4.8 (results recomputed by hand over the fixture rows) |
| `protocol/examples/filter#filter-unknown-operation-is-invalid` | B | spec read: PROTOCOL 6.1, 6.3, 7, 11 and SPEC 4.5-4.8 (results recomputed by hand over the fixture rows) |
| `protocol/examples/filter#group-with-totals` | B | spec read: PROTOCOL 6.1, 6.3, 7, 11 and SPEC 4.5-4.8 (results recomputed by hand over the fixture rows) |
| `protocol/examples/filter#filter-not-equal-keeps-empty` | B | spec read: PROTOCOL 6.1, 6.3, 7, 11 and SPEC 4.5-4.8 (results recomputed by hand over the fixture rows) |
| `protocol/examples/filter#filter-not-of-ordering-against-empty-is-true` | B | spec read: PROTOCOL 6.1, 6.3, 7, 11 and SPEC 4.5-4.8 (results recomputed by hand over the fixture rows) |
| `protocol/examples/filter#filter-on-masked-column-is-forbidden` | B | spec read: PROTOCOL 6.1, 6.3, 7, 11 and SPEC 4.5-4.8 (results recomputed by hand over the fixture rows) |

### `conformance/1/protocol/examples/page.yaml`

| Case id | Class | Evidence |
|---|---|---|
| `protocol/examples/page#page-columnar-default` | B | spec read: PROTOCOL 3.2, 5.1 |
| `protocol/examples/page#page-objects-shape` | B | spec read: PROTOCOL 3.2, 5.1 |
| `protocol/examples/page#page-masked-cell-and-schema` | B | spec read: PROTOCOL 3.2, 5.1 |

### `conformance/1/protocol/examples/patch.yaml`

| Case id | Class | Evidence |
|---|---|---|
| `protocol/examples/patch#patch-order-remove-update-add` | B | spec read: PROTOCOL 8.1, 8.2, 8.3 (order reset, remove, update, add; unknown key update adds; resume after a position) |
| `protocol/examples/patch#patch-resume-after-position` | B | spec read: PROTOCOL 8.1, 8.2, 8.3 (order reset, remove, update, add; unknown key update adds; resume after a position) |
| `protocol/examples/patch#patch-add-existing-key-merges` | B | spec read: PROTOCOL 8.1, 8.2, 8.3 (order reset, remove, update, add; unknown key update adds; resume after a position) |
| `protocol/examples/patch#patch-reset-replaces-all` | B | spec read: PROTOCOL 8.1, 8.2, 8.3 (order reset, remove, update, add; unknown key update adds; resume after a position) |

### `conformance/1/protocol/examples/protocol.yaml`

| Case id | Class | Evidence |
|---|---|---|
| `protocol/examples/protocol#schema-document` | B | spec read: PROTOCOL 2, 3, 5.1, 6.2, 10.1 |
| `protocol/examples/protocol#cursor-paging-is-stable` | B | spec read: PROTOCOL 2, 3, 5.1, 6.2, 10.1 |
| `protocol/examples/protocol#unknown-column-is-422` | B | spec read: PROTOCOL 2, 3, 5.1, 6.2, 10.1 |

### `conformance/1/protocol/examples/schema.yaml`

| Case id | Class | Evidence |
|---|---|---|
| `protocol/examples/schema#schema-all-types` | B | spec read: PROTOCOL 2, 3.1, 4 |
| `protocol/examples/schema#schema-base-document` | B | spec read: PROTOCOL 2, 3.1, 4 |
| `protocol/examples/schema#schema-catalogue` | B | spec read: PROTOCOL 2, 3.1, 4 |

### `conformance/1/protocol/forms.yaml`

| Case id | Class | Evidence |
|---|---|---|
| `protocol/forms#ndjson-is-a-header-then-one-line-per-row` | B | spec read: PROTOCOL 3.1, 3.2, 5.2, 5.3, 6.2, 12 (the Arrow and NDJSON steps are compared as the JSON page they stand for, as the file's description says) |
| `protocol/forms#ndjson-carries-the-next-cursor-in-its-header` | B | spec read: PROTOCOL 3.1, 3.2, 5.2, 5.3, 6.2, 12 (the Arrow and NDJSON steps are compared as the JSON page they stand for, as the file's description says) |
| `protocol/forms#arrow-rows-with-the-protocol-metadata` | B | spec read: PROTOCOL 3.1, 3.2, 5.2, 5.3, 6.2, 12 (the Arrow and NDJSON steps are compared as the JSON page they stand for, as the file's description says) |
| `protocol/forms#arrow-every-type-and-a-masked-cell` | B | spec read: PROTOCOL 3.1, 3.2, 5.2, 5.3, 6.2, 12 (the Arrow and NDJSON steps are compared as the JSON page they stand for, as the file's description says) |
| `protocol/forms#bridge-rows-as-arrow` | B | spec read: PROTOCOL 3.1, 3.2, 5.2, 5.3, 6.2, 12 (the Arrow and NDJSON steps are compared as the JSON page they stand for, as the file's description says) |
| `protocol/forms#bridge-arrow-not-offered-is-an-error-res` | B | spec read: PROTOCOL 3.1, 3.2, 5.2, 5.3, 6.2, 12 (the Arrow and NDJSON steps are compared as the JSON page they stand for, as the file's description says) |

### `conformance/1/protocol/host.yaml`

| Case id | Class | Evidence |
|---|---|---|
| `protocol/host#bridge-rows-of-a-sortable-table` | B | spec read: PROTOCOL 4, 5.2, 6.1, 6.2, 7, 8.2, 8.3, 11, 12 |
| `protocol/host#key-is-the-last-tie-breaker` | B | spec read: PROTOCOL 4, 5.2, 6.1, 6.2, 7, 8.2, 8.3, 11, 12 |
| `protocol/host#descending-with-nulls-last` | B | spec read: PROTOCOL 4, 5.2, 6.1, 6.2, 7, 8.2, 8.3, 11, 12 |
| `protocol/host#ordering-against-empty-is-false` | B | spec read: PROTOCOL 4, 5.2, 6.1, 6.2, 7, 8.2, 8.3, 11, 12 |
| `protocol/host#a-cursor-of-another-query-is-410` | B | spec read: PROTOCOL 4, 5.2, 6.1, 6.2, 7, 8.2, 8.3, 11, 12 |
| `protocol/host#filtering-a-masked-column-is-forbidden` | B | spec read: PROTOCOL 4, 5.2, 6.1, 6.2, 7, 8.2, 8.3, 11, 12 |
| `protocol/host#no-replay-is-a-gap` | B | spec read: PROTOCOL 4, 5.2, 6.1, 6.2, 7, 8.2, 8.3, 11, 12 |
| `protocol/host#arrow-not-offered-is-406` | B | spec read: PROTOCOL 4, 5.2, 6.1, 6.2, 7, 8.2, 8.3, 11, 12 |

### `conformance/1/protocol/narrow.yaml`

| Case id | Class | Evidence |
|---|---|---|
| `protocol/narrow#a-bare-list-is-refused-naming-the-columns` | B | spec read: PROTOCOL 16 Narrowing and Schema fields (and LIVE_TABLES 2.1) |
| `protocol/narrow#a-filter-that-does-not-fix-a-narrow-column-is-refused` | B | spec read: PROTOCOL 16 Narrowing and Schema fields (and LIVE_TABLES 2.1) |
| `protocol/narrow#a-narrowed-list-pages-without-the-heavy-columns` | B | spec read: PROTOCOL 16 Narrowing and Schema fields (and LIVE_TABLES 2.1) |
| `protocol/narrow#in-on-a-narrow-column-narrows` | B | spec read: PROTOCOL 16 Narrowing and Schema fields (and LIVE_TABLES 2.1) |
| `protocol/narrow#one-row-by-its-key-has-the-heavy-columns` | B | spec read: PROTOCOL 16 Narrowing and Schema fields (and LIVE_TABLES 2.1) |
| `protocol/narrow#the-schema-says-narrow-and-heavy` | B | spec read: PROTOCOL 16 Narrowing and Schema fields (and LIVE_TABLES 2.1) |

### `conformance/1/protocol/window.yaml`

| Case id | Class | Evidence |
|---|---|---|
| `protocol/window#window-by-keys` | B | spec read: PROTOCOL 16 Windowed subscription and Patch fields, 8.3, 8.2, 10.1; LIVE_TABLES 1.5 |
| `protocol/window#a-change-outside-the-window-sends-the-total-only` | B | spec read: PROTOCOL 16 Windowed subscription and Patch fields, 8.3, 8.2, 10.1; LIVE_TABLES 1.5 |
| `protocol/window#top-insert-in-a-newest-first-order` | B | spec read: PROTOCOL 16 Windowed subscription and Patch fields, 8.3, 8.2, 10.1; LIVE_TABLES 1.5 |
| `protocol/window#no-top-insert-for-a-page-below-the-top` | B | spec read: PROTOCOL 16 Windowed subscription and Patch fields, 8.3, 8.2, 10.1; LIVE_TABLES 1.5 |
| `protocol/window#no-top-insert-when-the-order-is-not-newest-first` | B | spec read: PROTOCOL 16 Windowed subscription and Patch fields, 8.3, 8.2, 10.1; LIVE_TABLES 1.5 |
| `protocol/window#win-changes-the-window` | B | spec read: PROTOCOL 16 Windowed subscription and Patch fields, 8.3, 8.2, 10.1; LIVE_TABLES 1.5 |
| `protocol/window#another-callers-window-is-refused` | B | spec read: PROTOCOL 16 Windowed subscription and Patch fields, 8.3, 8.2, 10.1; LIVE_TABLES 1.5 |
| `protocol/window#overflow-gives-an-unrecoverable-gap` | B | spec read: PROTOCOL 16 Windowed subscription and Patch fields, 8.3, 8.2, 10.1; LIVE_TABLES 1.5 |
| `protocol/window#a-windowed-resume-with-changes-missed-reads-again` | B | spec read: PROTOCOL 16 Windowed subscription and Patch fields, 8.3, 8.2, 10.1; LIVE_TABLES 1.5 |
| `protocol/window#sse-windowed-stream-starts-with-subd` | B | spec read: PROTOCOL 16 Windowed subscription and Patch fields, 8.3, 8.2, 10.1; LIVE_TABLES 1.5 |
| `protocol/window#sse-window-route-of-an-unknown-stream` | B | spec read: PROTOCOL 16 Windowed subscription and Patch fields, 8.3, 8.2, 10.1; LIVE_TABLES 1.5 |
| `protocol/window#the-schema-offers-windows` | B | spec read: PROTOCOL 16 Windowed subscription and Patch fields, 8.3, 8.2, 10.1; LIVE_TABLES 1.5 |

### `conformance/1/protocol/write.yaml`

| Case id | Class | Evidence |
|---|---|---|
| `protocol/write#an-applied-change-shows-when-the-source-sends-it` | B | spec read: PROTOCOL 16 Writes, 10.1, 12 and LIVE_TABLES 1.6, 5 (guard order, states applied/queued/held/refused, 403/404/405/409/422, audit events, tray, retry in order) |
| `protocol/write#a-queued-change-is-sent-by-the-outbox` | B | spec read: PROTOCOL 16 Writes, 10.1, 12 and LIVE_TABLES 1.6, 5 (guard order, states applied/queued/held/refused, 403/404/405/409/422, audit events, tray, retry in order) |
| `protocol/write#a-change-id-is-its-makers-only` | B | spec read: PROTOCOL 16 Writes, 10.1, 12 and LIVE_TABLES 1.6, 5 (guard order, states applied/queued/held/refused, 403/404/405/409/422, audit events, tray, retry in order) |
| `protocol/write#a-rejected-change-is-never-sent` | B | spec read: PROTOCOL 16 Writes, 10.1, 12 and LIVE_TABLES 1.6, 5 (guard order, states applied/queued/held/refused, 403/404/405/409/422, audit events, tray, retry in order) |
| `protocol/write#a-conflict-names-the-version-who-and-when` | B | spec read: PROTOCOL 16 Writes, 10.1, 12 and LIVE_TABLES 1.6, 5 (guard order, states applied/queued/held/refused, 403/404/405/409/422, audit events, tray, retry in order) |
| `protocol/write#a-table-without-edits-takes-no-writes` | B | spec read: PROTOCOL 16 Writes, 10.1, 12 and LIVE_TABLES 1.6, 5 (guard order, states applied/queued/held/refused, 403/404/405/409/422, audit events, tray, retry in order) |
| `protocol/write#the-tray-takes-no-writes` | B | spec read: PROTOCOL 16 Writes, 10.1, 12 and LIVE_TABLES 1.6, 5 (guard order, states applied/queued/held/refused, 403/404/405/409/422, audit events, tray, retry in order) |
| `protocol/write#forbidden-when-the-switch-is-off` | B | spec read: PROTOCOL 16 Writes, 10.1, 12 and LIVE_TABLES 1.6, 5 (guard order, states applied/queued/held/refused, 403/404/405/409/422, audit events, tray, retry in order) |
| `protocol/write#forbidden-without-the-right` | B | spec read: PROTOCOL 16 Writes, 10.1, 12 and LIVE_TABLES 1.6, 5 (guard order, states applied/queued/held/refused, 403/404/405/409/422, audit events, tray, retry in order) |
| `protocol/write#forbidden-for-a-column-the-declaration-does-not-edit` | B | spec read: PROTOCOL 16 Writes, 10.1, 12 and LIVE_TABLES 1.6, 5 (guard order, states applied/queued/held/refused, 403/404/405/409/422, audit events, tray, retry in order) |
| `protocol/write#a-row-outside-the-readers-scope-is-absent` | B | spec read: PROTOCOL 16 Writes, 10.1, 12 and LIVE_TABLES 1.6, 5 (guard order, states applied/queued/held/refused, 403/404/405/409/422, audit events, tray, retry in order) |
| `protocol/write#the-tray-lists-only-the-callers-changes` | B | spec read: PROTOCOL 16 Writes, 10.1, 12 and LIVE_TABLES 1.6, 5 (guard order, states applied/queued/held/refused, 403/404/405/409/422, audit events, tray, retry in order) |
| `protocol/write#a-write-over-the-bridge` | B | spec read: PROTOCOL 16 Writes, 10.1, 12 and LIVE_TABLES 1.6, 5 (guard order, states applied/queued/held/refused, 403/404/405/409/422, audit events, tray, retry in order) |
| `protocol/write#a-sink-that-refuses-answers-refused` | B | spec read: PROTOCOL 16 Writes, 10.1, 12 and LIVE_TABLES 1.6, 5 (guard order, states applied/queued/held/refused, 403/404/405/409/422, audit events, tray, retry in order) |
| `protocol/write#a-failed-send-stays-queued-and-is-tried-again` | B | spec read: PROTOCOL 16 Writes, 10.1, 12 and LIVE_TABLES 1.6, 5 (guard order, states applied/queued/held/refused, 403/404/405/409/422, audit events, tray, retry in order) |
| `protocol/write#a-paged-table-guards-the-sources-own-row` | B | spec read: PROTOCOL 16 Writes, 10.1, 12 and LIVE_TABLES 1.6, 5 (guard order, states applied/queued/held/refused, 403/404/405/409/422, audit events, tray, retry in order) |

### `conformance/1/protocol/live.yaml`

| Case id | Class | Evidence |
|---|---|---|
| `protocol/live#bridge-sub-then-msg` | B | spec read: PROTOCOL 8.1-8.4, 12, 16 (sub/msg, resume after a position, unsub, no-key at subscribe, filtered subscription restated, changes.filter, replay-less gap; the empty patch keeps the numbering, PROTOCOL 16) |
| `protocol/live#bridge-sub-resumes-after-a-position` | B | spec read: PROTOCOL 8.1-8.4, 12, 16 (sub/msg, resume after a position, unsub, no-key at subscribe, filtered subscription restated, changes.filter, replay-less gap; the empty patch keeps the numbering, PROTOCOL 16) |
| `protocol/live#bridge-unsub-stops-the-messages` | B | spec read: PROTOCOL 8.1-8.4, 12, 16 (sub/msg, resume after a position, unsub, no-key at subscribe, filtered subscription restated, changes.filter, replay-less gap; the empty patch keeps the numbering, PROTOCOL 16) |
| `protocol/live#bridge-sub-of-a-table-without-a-key` | B | spec read: PROTOCOL 8.1-8.4, 12, 16 (sub/msg, resume after a position, unsub, no-key at subscribe, filtered subscription restated, changes.filter, replay-less gap; the empty patch keeps the numbering, PROTOCOL 16) |
| `protocol/live#a-filtered-subscription-restates-each-patch` | B | spec read: PROTOCOL 8.1-8.4, 12, 16 (sub/msg, resume after a position, unsub, no-key at subscribe, filtered subscription restated, changes.filter, replay-less gap; the empty patch keeps the numbering, PROTOCOL 16) |
| `protocol/live#a-filter-needs-changes-filter` | B | spec read: PROTOCOL 8.1-8.4, 12, 16 (sub/msg, resume after a position, unsub, no-key at subscribe, filtered subscription restated, changes.filter, replay-less gap; the empty patch keeps the numbering, PROTOCOL 16) |
| `protocol/live#a-filtered-stream-resumes-only-from-now` | B | spec read: PROTOCOL 8.1-8.4, 12, 16 (sub/msg, resume after a position, unsub, no-key at subscribe, filtered subscription restated, changes.filter, replay-less gap; the empty patch keeps the numbering, PROTOCOL 16) |

### `conformance/1/patch/patch.yaml`

| Case id | Class | Evidence |
|---|---|---|
| `patch/patch#update-merges-fields-add-of-an-existing-key-updates` | B | spec read: PROTOCOL 8.1 (update merges, add of an existing key updates, unknown key added, reset first), TRANSLATION 10 and FORMAT 5.9 (a change no panel reads: frames []) |
| `patch/patch#reset-replaces-every-row` | B | spec read: PROTOCOL 8.1 (update merges, add of an existing key updates, unknown key added, reset first), TRANSLATION 10 and FORMAT 5.9 (a change no panel reads: frames []) |
| `patch/patch#a-change-no-panel-reads-sends-nothing` | B | spec read: PROTOCOL 8.1 (update merges, add of an existing key updates, unknown key added, reset first), TRANSLATION 10 and FORMAT 5.9 (a change no panel reads: frames []) |

### `conformance/1/plan/checked.yaml`

| Case id | Class | Evidence |
|---|---|---|
| `plan/checked#an-unknown-column-stays-rch-2071` | B | spec read: SPEC 6.2 (RCH-2071 with expected, nearest first) and 4.14 (an unknown column stays RCH-2071) |

### `conformance/1/plan/examples/plan.yaml`

| Case id | Class | Evidence |
|---|---|---|
| `plan/examples/plan#control-filter-pushed-down-as-a-tree` | B | spec read: PROTOCOL 7 (control replaced by its value, the filter tree) |

### `conformance/1/plan/plan.yaml`

| Case id | Class | Evidence |
|---|---|---|
| `plan/plan#multi-select-control-becomes-an-in-list` | B | spec read: PROTOCOL 7 (in over a list, filter tree), SPEC 6.2 and PROTOCOL 10.2 (RCH-2070, RCH-2071) |
| `plan/plan#unknown-table-is-rch-2070` | B | spec read: PROTOCOL 7 (in over a list, filter tree), SPEC 6.2 and PROTOCOL 10.2 (RCH-2070, RCH-2071) |
| `plan/plan#unknown-column-is-rch-2071` | B | spec read: PROTOCOL 7 (in over a list, filter tree), SPEC 6.2 and PROTOCOL 10.2 (RCH-2070, RCH-2071) |

### `conformance/1/sutra/examples/sutra.yaml`

| Case id | Class | Evidence |
|---|---|---|
| `sutra/examples/sutra#metric-over-a-list` | B | spec read: TRANSLATION 1 (T4), 2.1, 3.0, 3.8, 3.11, 5 and SPEC 5.4 (amount0); sums and counts recomputed |
| `sutra/examples/sutra#falsy-when-leaves-the-panel-out` | B | spec read: TRANSLATION 1 (T4), 2.1, 3.0, 3.8, 3.11, 5 and SPEC 5.4 (amount0); sums and counts recomputed |
| `sutra/examples/sutra#control-value-reaches-a-template` | B | spec read: TRANSLATION 1 (T4), 2.1, 3.0, 3.8, 3.11, 5 and SPEC 5.4 (amount0); sums and counts recomputed |

## Not flipped (for the lead)

| Case id | Reason | Detail |
|---|---|---|
| `el/determinism#d-m01-a` | spec-gap | exact last digit of a real-number function is the fdlibm port's, SPEC 4.16/5.8 give only a tolerance; case has none (exp(1) expects ...0455, the correctly rounded double is ...045) |
| `el/determinism#d-m04-c` | spec-gap | exact last digit of a real-number function is the fdlibm port's, SPEC 4.16/5.8 give only a tolerance; case has none (exp(1) expects ...0455, the correctly rounded double is ...045) |
| `el/determinism#d-m05-a` | spec-gap | exact last digit of a real-number function is the fdlibm port's, SPEC 4.16/5.8 give only a tolerance; case has none (exp(1) expects ...0455, the correctly rounded double is ...045) |
| `el/check#check-n-0-mtm-0` | spec-gap | SPEC 4.14 does not say how a conditional's two branches join (decimal and integer) |
| `el/check#check-switch-status-a-1-b-2-0` | spec-gap | functions.yaml gives switch the result type any; the case expects integer (more precise inference than the spec states) |
| `el/check#check-concat-status-x` | spec-ambiguous | SPEC 4.14 says a function without a signature is RCH-9129, but functions.yaml gives concat/let a signature; which functions lack a checked-mode signature is not stated |
| `el/check#check-let-x-n-x-2` | spec-ambiguous | SPEC 4.14 says a function without a signature is RCH-9129, but functions.yaml gives concat/let a signature; which functions lack a checked-mode signature is not stated |
| `el/check#check-status-in-n` | spec-ambiguous | SPEC 4.8 says a scalar on the right of in is a list of one; the case refuses it (and the list with mismatched items) in checked mode; SPEC 4.14 does not list in-type checks |
| `el/check#check-status-in-1-2` | spec-ambiguous | SPEC 4.8 says a scalar on the right of in is a list of one; the case refuses it (and the list with mismatched items) in checked mode; SPEC 4.14 does not list in-type checks |
| `el/check#check-round-mtm-n` | spec-gap | SPEC 4.14/4.10 do not say that round's n must be a literal in checked mode (functions.yaml: n is integer) |
| `el/check#check-date-09-10-2026-tradedate` | spec-gap | SPEC 4.14 does not say a date() literal is validated as ISO in checked mode (4.10 rule 2 lists other literal checks) |
| `problem/data#data-is-not-a-key-of-level-1-0` | spec-gap | SPEC 2.2 lists data as planned with no since 1.1; nothing says level 1.0 refuses it with RCH-2008 (7.2 rule 6 names the rule, not the key) |
| `problem/data#a-name-that-is-not-a-field-name` | spec-ambiguous | SPEC 2.4 does not say what makes a data name invalid or which code (6.2: 2073 name clash, 2020 bad name) |
| `sutra/data#table-as-record-is-its-first-row` | spec-gap | SPEC 2.4 gives as: rows\|record but not which row a table gives as a record (the case takes the first) |
| `sutra/data#missing-table-reads-as-no-rows` | spec-ambiguous | SPEC 2.4 says an unknown table is RCH-2070 and 6.4 makes absent data a panel state; the case expects count 0 with a value |
| `sutra/data#decimal-cells-are-exact` | spec-conflict | SPEC 4.2 text form of a decimal has no trailing zeros ('0.1'); the case expects '0.10' (PROTOCOL 3.1 says scale gives the digits after the point but no section gives the cell text of a decimal column) |
| `live/declaration#blotter-reads-and-gives-its-schema` | spec-gap | the exact can set a declaration implies (arrow, replay, group, count, offset...) is not specified; the case lists it |
| `live/declaration#read-only-table-with-a-compound-key` | spec-gap | the exact can set a declaration implies (arrow, replay, group, count, offset...) is not specified; the case lists it |
| `protocol/forms#arrow-of-a-grouped-query-with-totals` | spec-ambiguous | PROTOCOL 3.1/6.3 do not say the text scale of a decimal sum over a column that declares no scale ('3.30' vs '3.3') |
| `protocol/host#offset-needs-can-offset` | spec-gap | PROTOCOL 6.2 says a host with offset in can accepts offset; it does not say that a host without it refuses with rdp.query.unsupported (only sort is stated, section 4) |
| `protocol/host#group-count-distinct-min-max` | spec-ambiguous | PROTOCOL 3.1/6.3 do not say the text scale of a decimal sum over a column that declares no scale ('3.30' vs '3.3') |
| `protocol/write#a-repeated-change-id-answers-the-first-answer` | spec-conflict | PROTOCOL 16 and LIVE_TABLES 1.6/L7 say a repeated change id answers its FIRST answer (queued); the case expects state 'sent' with a version on the third call, and 'sent' is not one of the four states (applied, queued, held, refused) |
| `protocol/write#a-held-change-waits-for-the-hosts-decision` | spec-gap | LIVE_TABLES 5 lists the audit record's fields (no 'decidedBy'); the deciding person's field name is not specified |
| `protocol/write#an-invalid-change-names-the-column-and-rule` | spec-gap | error.json leaves the rule word of a shape failure free; the case pins rule 'basedOn' for a missing version |
| `protocol/write#the-schema-offers-write-to-an-editor-only` | spec-gap | the exact can set of a table (arrow, replay, count, ...) is not specified; the case lists it |
| `protocol/live#a-conflated-stream-merges-by-key` | spec-ambiguous | PROTOCOL 8.4 says a later remove cancels an earlier add of that key; it does not say whether the remove is kept when the add was in the same merged window (the case keeps it) |
| `patch/patch#compound-key-remove-and-a-panel-frame` | spec-gap | frame header values (seq 1, generation 0, latencyMs 0, p99Ms 0) are not specified; FORMAT 5.9 says no timing is involved |
| `patch/patch#a-strip-figure-is-a-strip-patch` | spec-gap | frame header values (seq 1, generation 0, latencyMs 0, p99Ms 0) are not specified; FORMAT 5.9 says no timing is involved |
| `plan/checked#a-date-against-text-is-rch-9127` | spec-gap | SPEC 4.14 and FORMAT 5.10 do not define the plan document's problem fields (entry, panel, where) or its local list; the checked-mode codes are specified |
| `plan/checked#a-number-against-text-is-rch-9127` | spec-gap | SPEC 4.14 and FORMAT 5.10 do not define the plan document's problem fields (entry, panel, where) or its local list; the checked-mode codes are specified |
| `plan/checked#a-filter-on-a-masked-column-is-rch-9161` | spec-gap | SPEC 4.14 and FORMAT 5.10 do not define the plan document's problem fields (entry, panel, where) or its local list; the checked-mode codes are specified |
| `plan/checked#a-well-typed-condition-has-no-problem` | spec-gap | the plan document's local list and the folding of date('2026-01-01') into a literal are not specified (PROTOCOL 7 does not list date; FORMAT 5.10 defers the document) |
| `plan/plan#entry-where-is-pushed-down` | spec-gap | the plan document's readers and local lists (and the reason words) are not specified; FORMAT 5.10 defers the document |
| `plan/plan#a-panel-where-stays-local-with-two-readers` | spec-gap | the plan document's readers and local lists (and the reason words) are not specified; FORMAT 5.10 defers the document |
| `plan/plan#outside-the-subset-stays-local` | spec-gap | the plan document's readers and local lists (and the reason words) are not specified; FORMAT 5.10 defers the document |
| `plan/plan#a-table-that-cannot-filter-filters-locally` | spec-gap | the plan document's readers and local lists (and the reason words) are not specified; FORMAT 5.10 defers the document |
| `plan/plan#functions-of-the-subset-and-folded-constants` | spec-gap | PROTOCOL 7 does not say that a function of a control's value (lower(ctl.q)) is folded to a literal, nor that '== null' becomes is-null |
| `patch/examples/patch#update-remove-and-a-no-op` | spec-ambiguous | step 3 adds a row the panel reads (TRANSLATION 10: patches for panels whose paths read the changed columns) but expects frames [] because the figure is unchanged; FORMAT 5.9 says frames [] only when nothing a panel reads changes |
