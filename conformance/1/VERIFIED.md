<!-- Copyright (c) 2026 Ashutosh Sinha. Specification licence: ../../LICENSE-SPEC.md (draft, not yet in force). -->
# Verification log of the conformance cases

Gate 2 review 9.6 procedure. Verifier: **independent review lane** (did not build the cases), 2026-11-21, on `g2/integration` at `40fa759`. A flip changes the `verified` flag and nothing else (`tools/conformance/verify_class_a.py` proves it by parsing the file before and after). FORMAT 3 still says a person flips a case: the owner reads this log and spot-checks (one case in ten of Class B) before the tag.

**Class A** (reference-recorded): the Drishti reference at `v2.9.11.i_7a` (RefBridge, and RefBridgeSutra for `sutra`) gives the case's expected answer, the case is not in `tools/split/known-differences.txt`, and the JVM runtime, the Node tools bundle and the Python V8 engine (tools bundle) all pass with the same results line byte for byte at level 1.0 and at the newest level for a case without `until`; the Node and Python V8 runtime bundles agree wherever they implement the kind. The script writes the list and the evidence; re-run: `verify_class_a.py run`, then `list`.

**Class B** (written by reading, or not recorded by the reference): the expected answer read against the section its `source` names (and the case's own arithmetic recomputed); all runtimes pass (the build proves it). The evidence column names the sections read. Where the spec is silent or ambiguous, or the case contradicts it, the case is not flipped and is listed at the end with the reason.

Totals of the first pass: **246 Class A and 237 Class B flipped; 38 not flipped.**

**The spec-gap pass** (2026-11-21, branch `g2/spec-gaps` off `g2/integration` at `05089da`; a second lane, which neither built the cases nor
made the first pass) took those 38 and the 14 cases added since (`protocol/approve` 10, `sutra/binder/live-edit` 4): for each it decided
the specified behaviour, wrote it into the specification, and then flipped the case, corrected its expectation, or listed a code fix.
Decisions `SG-1` to `SG-24` and code fixes `CF-1` to `CF-5` are in the section [Spec gaps closed](#spec-gaps-closed-the-second-pass).
**51 flipped (3 of them after their expectation was corrected); 1 not flipped (it waits for CF-1).** Of the cases this log covers,
that one alone is unverified. The 12 cases of `el/aggregates-host-values` arrived with the merge of `g2/integration` at `0bd6013`,
after this pass was made; their commit leaves them for the independent review lane, and this pass did not flip them.

**The code-fix pass** (2026-11-21, branch `g2/cf-fixes` off `g2/integration` at `6f621dc`) built `CF-1` to `CF-5` and the two lead
decisions on the host's aggregates (`L-1`, `L-2`), added 16 cases for them (verified against the decided text), replaced the case CF-1
waited for, and flipped the 12 cases of `el/aggregates-host-values` on an independent review. See [Code fixes built](#code-fixes-built-the-third-pass).
**No case below `conformance/1/` is unverified.**

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

## Spec gaps closed (the second pass)

How a case was closed: **flip** (the expectation matches the decided text; all runtimes pass), **fix** (the expectation was wrong
against the decided text and was corrected, then flipped), **code** (the implementation must change; the case is not flipped and
the fix is listed below). Runs behind every flip: the JVM runtime and the Node tools bundle at 1.0, 1.1 and 1.2 (the runtime bundle equal
to the tools bundle on every case it answers), `protocol_check.py --live` (143 of 143) and `--python` (the cases the Python host
implements) with pyarrow. Where the implementation was sound and the same on every runtime, the text says what it does.

### Decisions

| Id | Question | Decision (the normative text) | Where written | Cases | Closed by |
|---|---|---|---|---|---|
| SG-1 | Which `can` values does a live table list? | Every live table lists `page, sort, filter, count, group, offset, changes, changes.filter, changes.window, replay, arrow`, then `narrow` when declared, then `write` when the table takes writes (the tray: when the host holds changes). `can` is the table's, for every reader; the reader's own right is the schema's `write` object | PROTOCOL 16 "Capabilities" | `live/declaration#blotter-reads-and-gives-its-schema`, `live/declaration#read-only-table-with-a-compound-key`, `protocol/write#the-schema-offers-write-to-an-editor-only`, `protocol/approve#the-trays-schema-offers-state-to-an-approver` | flip; CF-5 for a paged table's `group` |
| SG-2 | What is the plan document? | `{ tables: [{ entry, name, readers, live, query { columns?, filter?, limit? }, local [{ panel?, where, reason }], window? }], problems }`; the push-down rules (entry's `where`; a panel's only for the sole reader), the four `reason` texts, the problems and their fields (`entry`, `panel`, `where`, `fix`, `expected`). In the filter every part that reads no column is folded to a literal (`lower(ctl.q)`, `date('…')`), `== null` is `is-null`, a chain is one operation | FORMAT 5.10 "The plan document"; PROTOCOL 7 "What the runtime writes" | `plan/plan#entry-where-is-pushed-down`, `#a-panel-where-stays-local-with-two-readers`, `#outside-the-subset-stays-local`, `#a-table-that-cannot-filter-filters-locally`, `#functions-of-the-subset-and-folded-constants`; `plan/checked#a-well-typed-condition-has-no-problem`, `#a-date-against-text-is-rch-9127`, `#a-number-against-text-is-rch-9127`, `#a-filter-on-a-masked-column-is-rch-9161` | flip |
| SG-3 | What are a frame's header values with no host? | One frame per step that changed something, holding every patch (strip, panels, datasets); `seq` from 1 by one per frame; `generation`, `latencyMs`, `p99Ms` are the host's facts and `0` without a host | FORMAT 5.9 "The frames of a step"; VIEWMODEL "Shape" | `patch/patch#compound-key-remove-and-a-panel-frame`, `patch/patch#a-strip-figure-is-a-strip-patch` | flip |
| SG-4 | Is the last digit of a real-number function pinned? | No. This project's runtimes agree in every bit (one set of algorithms, fdlibm; RUNNER 7 rule 2), but the specification holds an implementation to a tolerance. A case whose digits are not the correctly rounded result carries `tolerance` and no `text` | SPEC 4.10 rule 7; FORMAT 5.2 `tolerance` | `el/determinism#d-m01-a` (`exp(1)`), `#d-m04-c` (`rate`), `#d-m05-a` (`fisher`) | **fix**: `text` removed, `tolerance: { rel: 1e-15 }` added, the value kept (Core's); then flip |
| SG-5 | Is `data` a key of 1.0? | No: `since 1.1`; at 1.0 the key is `RCH-2008` | SPEC 2.2 (the row's state), 2.4 (heading and first paragraph) | `problem/data#data-is-not-a-key-of-level-1-0` | flip |
| SG-6 | What does `as: record` give over rows? | The first row, in the source's order, after the entry's `where`; no row gives a record with no fields; `as: rows` over a record is a list of one | SPEC 2.4 "`as`"; TRANSLATION 10 | `sutra/data#table-as-record-is-its-first-row` | flip |
| SG-7 | `offset` on a host without `can: offset`? | `422 rdp.query.unsupported`, `operation: offset`; the same for `filter` and `group` as for `sort`: refused, never ignored | PROTOCOL 4, 6.2 | `protocol/host#offset-needs-can-offset` | flip |
| SG-8 | The `rule` word of a write that is not well formed? | `column: null` and `rule` the part at fault: `shape`, `id`, `op`, `key`, `set`, `basedOn`, `from`, `reason`; for a value: `required`, `value`, `rule` | PROTOCOL 10.1 "The `fields` of `rdp.write.invalid`" | `protocol/write#an-invalid-change-names-the-column-and-rule` | flip |
| SG-9 | The audit record's fields, who decided? | The adapter's audit events: `{ at, caller, table, event, reason, change?, key?, columns?, from?, to?, current?, decidedBy? }` and the event names; `decidedBy` is the person who released or rejected | PROTOCOL 15 "The audit events" | `protocol/write#a-held-change-waits-for-the-hosts-decision` | flip |
| SG-10 | Literal parameters in checked mode? | `round`'s and `shift_date`'s `n` are whole number literals, `percentile`'s `p` a number literal 0 to 1, units and calendars text literals; `date('…')` and `timestamp('…')` take an ISO literal, checked when the expression is checked; `RCH-9127` otherwise | SPEC 4.14 "Checked mode in detail" rules 5, 6 | `el/check#check-round-mtm-n`, `el/check#check-date-09-10-2026-tradedate` | flip; CF-3 for a timestamp with a zone |
| SG-11 | The type of a conditional and of `switch`? | Types **join**, whatever the order written: equal types that type, `none` the other, numeric as `+` joins them, `date` with `timestamp` gives `timestamp`, `any` gives `any`; the types joined must fit each other (`RCH-9127`). `switch` is the join of its answers | SPEC 4.14 "Checked mode in detail" rules 1, 2 | `el/check#check-n-0-mtm-0`, `el/check#check-switch-status-a-1-b-2-0` | flip (both cases agree with the join); CF-2 for the orders the implementation gets wrong |
| SG-12 | The text of a decimal sum? | Exact, as text: `sum` has as many digits after the point as the longest value it adds (the column's `scale` when the cells carry it); `min` and `max` are the cell as held; an aggregate reads its own group's values, `totals` every row after `filter` | PROTOCOL 3.1 "The text of a decimal" | `protocol/forms#arrow-of-a-grouped-query-with-totals`, `protocol/host#group-count-distinct-min-max` | flip; CF-4 for the Python host outside these cases |
| SG-13 | `in` against a scalar in checked mode? | Refused: `in` and `not in` take a list written in the expression; each item must fit the value's type, each misfit its own `RCH-9127` at the item. The list of one of SPEC 4.8 is an implicit cast and stays the unchecked meaning | SPEC 4.14 "Checked mode in detail" rule 4 | `el/check#check-status-in-n`, `el/check#check-status-in-1-2` | flip |
| SG-14 | Which functions have no signature in checked mode? | Checked mode types 36 Core functions (listed by name); every other Core function, `let(...)`, a record literal, a slice and a lambda are `RCH-9129`. `functions.yaml` is the unchecked catalogue; its signatures do not make a function typed | SPEC 4.14 "Checked mode in detail" rule 3 | `el/check#check-concat-status-x`, `el/check#check-let-x-n-x-2` | flip |
| SG-15 | What is a valid data name? | A field name, `[A-Za-z_][A-Za-z0-9_]*`, because it is read as `$.<name>`; otherwise `RCH-2073` at the name | SPEC 2.4, 6.2 (`2073`) | `problem/data#a-name-that-is-not-a-field-name` | flip |
| SG-16 | A `table` entry whose table the host does not have? | A **failed entry**: `RCH-2070` wherever the host's tables are known, and in a view built all the same every panel that reads it is `error` with those words. Never a table with no rows. The existing text already said so (TRANSLATION 10 "a failed entry"; PROTOCOL 10.1 `rdp.table.unknown` → panel state `error`); the implementation and the case read it as no rows, which shows `0` for data that is not there | SPEC 2.4 "A table the host does not have"; TRANSLATION 10 | `sutra/data#missing-table-reads-as-no-rows` | **code**: CF-1, built in the third pass; the case replaced by `sutra/data#a-missing-table-fails-its-panels` |
| SG-17 | Does a conflated `remove` survive when it cancels an `add` of the same window? | Yes: the `add` or `update` is dropped, the `remove` stays (the sender cannot know the reader never held the key; a `remove` of an absent key changes nothing) | PROTOCOL 8.4 | `protocol/live#a-conflated-stream-merges-by-key` | flip |
| SG-18 | A step that changes a row a panel reads, with the same figure? | No frame: a patch is made only for what differs in the ViewModel | FORMAT 5.9 "The frames of a step"; TRANSLATION 10 | `patch/examples/patch#update-remove-and-a-no-op` | flip |
| SG-19 | A repeated change id after the change moved (the case contradicted PROTOCOL 16)? | **The text changed, not the case.** A repeated id is never taken twice and answers the change as it stands now: the first answer until it has moved, then `sent` (with `version`), `rejected` or `failed`; only to its maker, `403` to anyone else. "The first answer again" would leave a retry after a lost answer saying `queued` for ever. Writes are a `can` addition never released, so no released behaviour changes | PROTOCOL 16 "Writes" | `protocol/write#a-repeated-change-id-answers-the-first-answer` | flip |
| SG-20 | A decimal cell `"0.10"`: `0.10` or `0.1` (the case contradicted SPEC 4.2)? | **The text changed, not the case.** A `decimal` cell of a Data Protocol table prints the digits the host sent (`0.10`), by itself, joined and in a template; it equals `0.1`; any operator or function result prints by the general rule without trailing zeros; a literal `0.10` prints `0.1`. This is level 1.1 text (tables and decimals are 1.1), so no 1.0 behaviour changes. Same on the JVM and Node | SPEC 4.2 (text form), 4.16 rule 8 | `sutra/data#decimal-cells-are-exact` | flip |
| SG-21 | Where does the adapter's clock start? | `2026-11-21T12:00:00Z` with each fixture, moved only by `advanceMs`; with it the fixture's `tier` and `sourceColumns`, `/_test/stream`'s `source`, the case's writer and the adapter's refusal words | PROTOCOL 15 "What the live adapter fixes" | (the `live/retention` and write cases already verified rest on it) | text only |
| SG-22 | Four eyes: the 10 newer cases | Read against PROTOCOL 16 "Four eyes" and "Writes", completed where it was silent: the tray lists pending changes (`queued`, `held`, `failed`), a new one arrives as `update` and a decided one leaves as `remove`; `summary`'s form (`b: qty = 8, status = DONE`, `(masked)`); a rejection without a reason is `422` `{ column: null, rule: reason }`; a refusal is `403` with the host's reason, audited; an unlisted change is `404 rdp.table.unknown`; a change no longer held `409` with its state as `version` and its maker as `by`; a decision's id repeats as a change's; the tray's `write` for an approver | PROTOCOL 16 "Writes", "Four eyes"; 15 (the adapter's refusal words, audit) | `protocol/approve` (all 10) | flip |
| SG-23 | A live panel's `edit`: the 4 newer cases | Already specified: SPEC 2.3 (`edit`: `none`, `cells`, `beside`; `RCH-2029` for another word) and VIEWMODEL contract 1.2 (`LiveRef.edit`, absent when read only). Read and recomputed (the problem's place: line 8, column 48) | no new text | `sutra/binder/live-edit` (all 4) | flip |
| SG-24 | The two overlay cases that rested on the design document | Now protocol text: a table paged from its source is read at the source's data version (new rows from the next version on), and an order by a column the host's index cannot give treats it as null for the source's rows (after the live rows that carry it, then by key) | PROTOCOL 16 "A table paged from its source"; 15 (`tier`, `sourceColumns`, `source`) | `live/overlay#a-new-source-version-reads-the-index-again`, `live/overlay#an-order-by-a-column-the-source-does-not-keep` (flipped in the first pass) | text only |

### Code fixes for a builder lane

Each is behaviour the text now states and the implementation does not have. No flipped case depends on one; each names the case to add with it.

| Id | From | Where | Exact behaviour | Case |
|---|---|---|---|---|
| CF-1 | SG-16 | `rachana-core` `LiveScreen.record` and `DataRecord` (the JS bundles are the same source); `rachana-data` `TableClient.load`, which swallows `rdp.table.unknown` | A `table` entry whose table the host does not have (not handed, or `rdp.table.unknown`) is failed: every panel that reads `$.<entry>` has `error: "RCH-2070 the host has no table '<table>' (data '<entry>')"`, no `data`, `empty: true`; the other panels bind. Today it is read as no rows (`count($.t)` shows `0`) | replace `sutra/data#missing-table-reads-as-no-rows` by `sutra/data#a-missing-table-fails-its-panels`: the same Sutra, `expect.at` `/panels/0/error: "RCH-2070 the host has no table 'nothing-here' (data 't')"` and `/panels/0/empty: true` |
| CF-2 | SG-11 | `rachana-core` `el/check/Checker` (`Ternary`, `Coalesce`, `call` for `switch` and the `returns: same` functions over several arguments) and `ElType` | One join (SG-11) wherever a value is chosen among several. Today a conditional and `??` give the first side's type, `switch` its first answer's without checking that the answers fit, `coalesce`, `least` and `greatest` their last argument's | new `el/check` cases over the suite's record types: `$.n > 0 ? 0 : $.mtm` → `decimal` (today `integer`); `$.n > 0 ? $.tradeDate : $.ts` → `timestamp` with `ts: timestamp` (today `date`); `switch($.status, 'A', 1, 'B', 2.5, 0)` → `decimal` (today `integer`); `switch($.status, 'A', 1, 'B', 'x')` → `RCH-9127` (today `integer`); `coalesce($.mtm, $.n)` → `decimal` (today `integer`) |
| CF-3 | SG-10 | `Checker.isoDate` | `timestamp('2026-11-17T09:30:00Z')` and `timestamp('2026-11-17T09:30:00+05:30')` are accepted in checked mode (SPEC 4.2 writes a timestamp with `Z`; PROTOCOL 3.1 requires `Z` or an offset). Today both are `RCH-9127` "is not an ISO timestamp"; only a timestamp with no zone is accepted | new `el/check` cases: `timestamp('2026-11-17T09:30:00Z') < $.ts` → `boolean`, the same with `+05:30` → `boolean` |
| CF-4 | SG-12 | Python `drishti_rachana/server.py` `_grouped` / `_aggregate` | For a decimal column: `sum` has the longest count of digits after the point among the values that aggregate adds (its own group; for `totals` the rows after `filter`), `min` and `max` return the cell as held. Today the scale is the column's `scale`, else the longest over every row of the table, and it is applied to `min` and `max` too; the Java hosts already follow the text | new `protocol/host` case: rows `{d: A, px: "1.1"}`, `{d: A, px: "2.25"}`, `{d: B, px: "1.5"}`, group by `d` with `sum`, `min`, `max`: `[A, "3.35", "1.1", "2.25"]`, `[B, "1.5", "1.5", "1.5"]` |
| CF-5 | SG-1 | `rachana-live` `LiveTables.can` | A table paged from its source (tier `columns` or `query`) leaves `group` out of the `can` it serves. Today it lists `group` and answers a grouped query `422 rdp.query.unsupported` (`operation: group`): a capability declared and refused (PROTOCOL 2) | new `live/overlay` case: `GET /tables/orders` of the paged fixture: `can` without `group`; the grouped query stays `422` |

### Seen while reading, not decided here

- `avg` of a decimal column: the Java hosts answer the quotient to 16 significant digits as text, the Python host rounds to the column's scale; PROTOCOL 6.3 does not say and no case pins it. **Decided by the lead: `L-2` (third pass).**
- `sum` over no value (every cell null, or no row): the Java hosts answer `0` (`"0"` for a decimal), the Python host `null`; not specified, no case. **Decided by the lead: `L-1` (third pass).**
- The generated `rachana.lang.json` and `sutra.schema.json` still describe 1.0 (SPEC 7.2 rule 6), so `data` carries its `since 1.1` only in SPEC 2.2 until the generator reads Rachana's own tables.

### Flipped in the second pass

| Case id | Decision | Evidence |
|---|---|---|
| `el/check#check-n-0-mtm-0` | SG-11 | jvm and nodet pass at 1.0, 1.1 and 1.2 with the same bytes, noder the same wherever it answers |
| `el/check#check-switch-status-a-1-b-2-0` | SG-11 | jvm and nodet pass at 1.0, 1.1 and 1.2 with the same bytes, noder the same wherever it answers |
| `el/check#check-concat-status-x` | SG-14 | jvm and nodet pass at 1.0, 1.1 and 1.2 with the same bytes, noder the same wherever it answers |
| `el/check#check-let-x-n-x-2` | SG-14 | jvm and nodet pass at 1.0, 1.1 and 1.2 with the same bytes, noder the same wherever it answers |
| `el/check#check-status-in-n` | SG-13 | jvm and nodet pass at 1.0, 1.1 and 1.2 with the same bytes, noder the same wherever it answers |
| `el/check#check-status-in-1-2` | SG-13 | jvm and nodet pass at 1.0, 1.1 and 1.2 with the same bytes, noder the same wherever it answers |
| `el/check#check-round-mtm-n` | SG-10 | jvm and nodet pass at 1.0, 1.1 and 1.2 with the same bytes, noder the same wherever it answers |
| `el/check#check-date-09-10-2026-tradedate` | SG-10 | jvm and nodet pass at 1.0, 1.1 and 1.2 with the same bytes, noder the same wherever it answers |
| `el/determinism#d-m01-a` | SG-4 | expectation corrected (tolerance, no text); jvm and nodet pass at 1.0, 1.1 and 1.2 with the same bytes, noder the same wherever it answers |
| `el/determinism#d-m04-c` | SG-4 | expectation corrected (tolerance, no text); jvm and nodet pass at 1.0, 1.1 and 1.2 with the same bytes, noder the same wherever it answers |
| `el/determinism#d-m05-a` | SG-4 | expectation corrected (tolerance, no text); jvm and nodet pass at 1.0, 1.1 and 1.2 with the same bytes, noder the same wherever it answers |
| `live/declaration#blotter-reads-and-gives-its-schema` | SG-1 | jvm and nodet pass at every level from the case's own with the same bytes, noder the same wherever it answers |
| `live/declaration#read-only-table-with-a-compound-key` | SG-1 | jvm and nodet pass at every level from the case's own with the same bytes, noder the same wherever it answers |
| `patch/examples/patch#update-remove-and-a-no-op` | SG-18 | jvm and nodet pass at every level from the case's own with the same bytes, noder the same wherever it answers |
| `patch/patch#compound-key-remove-and-a-panel-frame` | SG-3 | jvm and nodet pass at every level from the case's own with the same bytes, noder the same wherever it answers |
| `patch/patch#a-strip-figure-is-a-strip-patch` | SG-3 | jvm and nodet pass at every level from the case's own with the same bytes, noder the same wherever it answers |
| `plan/checked#a-well-typed-condition-has-no-problem` | SG-2 | jvm and nodet pass at every level from the case's own with the same bytes, noder the same wherever it answers |
| `plan/checked#a-date-against-text-is-rch-9127` | SG-2 | jvm and nodet pass at every level from the case's own with the same bytes, noder the same wherever it answers |
| `plan/checked#a-number-against-text-is-rch-9127` | SG-2 | jvm and nodet pass at every level from the case's own with the same bytes, noder the same wherever it answers |
| `plan/checked#a-filter-on-a-masked-column-is-rch-9161` | SG-2 | jvm and nodet pass at every level from the case's own with the same bytes, noder the same wherever it answers |
| `plan/plan#entry-where-is-pushed-down` | SG-2 | jvm and nodet pass at every level from the case's own with the same bytes, noder the same wherever it answers |
| `plan/plan#a-panel-where-stays-local-with-two-readers` | SG-2 | jvm and nodet pass at every level from the case's own with the same bytes, noder the same wherever it answers |
| `plan/plan#outside-the-subset-stays-local` | SG-2 | jvm and nodet pass at every level from the case's own with the same bytes, noder the same wherever it answers |
| `plan/plan#a-table-that-cannot-filter-filters-locally` | SG-2 | jvm and nodet pass at every level from the case's own with the same bytes, noder the same wherever it answers |
| `plan/plan#functions-of-the-subset-and-folded-constants` | SG-2 | jvm and nodet pass at every level from the case's own with the same bytes, noder the same wherever it answers |
| `problem/data#data-is-not-a-key-of-level-1-0` | SG-5 | jvm and nodet pass at 1.0, 1.1 and 1.2 with the same bytes, noder the same wherever it answers |
| `problem/data#a-name-that-is-not-a-field-name` | SG-15 | jvm and nodet pass at every level from the case's own with the same bytes, noder the same wherever it answers |
| `protocol/approve#an-approver-reads-the-held-changes-of-others-in-scope` | SG-22 | live reference host pass (`protocol_check.py --live`, 143 of 143); the Python host has no live tables (unsupported) |
| `protocol/approve#an-approver-releases-a-held-change` | SG-22 | live reference host pass (`protocol_check.py --live`, 143 of 143); the Python host has no live tables (unsupported) |
| `protocol/approve#a-rejection-gives-its-reason` | SG-22 | live reference host pass (`protocol_check.py --live`, 143 of 143); the Python host has no live tables (unsupported) |
| `protocol/approve#a-decision-without-the-approve-right-is-forbidden` | SG-22 | live reference host pass (`protocol_check.py --live`, 143 of 143); the Python host has no live tables (unsupported) |
| `protocol/approve#the-hosts-rule-refuses-a-decision` | SG-22 | live reference host pass (`protocol_check.py --live`, 143 of 143); the Python host has no live tables (unsupported) |
| `protocol/approve#a-change-outside-the-approvers-scope-is-absent` | SG-22 | live reference host pass (`protocol_check.py --live`, 143 of 143); the Python host has no live tables (unsupported) |
| `protocol/approve#a-change-no-longer-held-is-a-conflict` | SG-22 | live reference host pass (`protocol_check.py --live`, 143 of 143); the Python host has no live tables (unsupported) |
| `protocol/approve#a-repeated-decision-id-answers-the-first-answer` | SG-22 | live reference host pass (`protocol_check.py --live`, 143 of 143); the Python host has no live tables (unsupported) |
| `protocol/approve#the-trays-schema-offers-state-to-an-approver` | SG-1 and SG-22 | live reference host pass (`protocol_check.py --live`, 143 of 143); the Python host has no live tables (unsupported) |
| `protocol/approve#an-approvers-tray-follows-the-held-changes` | SG-22 | live reference host pass (`protocol_check.py --live`, 143 of 143); the Python host has no live tables (unsupported) |
| `protocol/forms#arrow-of-a-grouped-query-with-totals` | SG-12 | live reference host pass (`protocol_check.py --live`, 143 of 143); Python host pass |
| `protocol/host#offset-needs-can-offset` | SG-7 | live reference host pass (`protocol_check.py --live`, 143 of 143); Python host pass |
| `protocol/host#group-count-distinct-min-max` | SG-12 | live reference host pass (`protocol_check.py --live`, 143 of 143); Python host pass |
| `protocol/live#a-conflated-stream-merges-by-key` | SG-17 | live reference host pass (`protocol_check.py --live`, 143 of 143); Python host pass |
| `protocol/write#a-repeated-change-id-answers-the-first-answer` | SG-19 | live reference host pass (`protocol_check.py --live`, 143 of 143); the Python host has no live tables (unsupported) |
| `protocol/write#a-held-change-waits-for-the-hosts-decision` | SG-9 | live reference host pass (`protocol_check.py --live`, 143 of 143); the Python host has no live tables (unsupported) |
| `protocol/write#an-invalid-change-names-the-column-and-rule` | SG-8 | live reference host pass (`protocol_check.py --live`, 143 of 143); the Python host has no live tables (unsupported) |
| `protocol/write#the-schema-offers-write-to-an-editor-only` | SG-1 | live reference host pass (`protocol_check.py --live`, 143 of 143); the Python host has no live tables (unsupported) |
| `sutra/binder/live-edit#a-live-panel-edits-in-its-cells` | SG-23 | jvm and nodet pass at every level from the case's own with the same bytes, noder the same wherever it answers |
| `sutra/binder/live-edit#a-live-panel-edits-beside-its-grid` | SG-23 | jvm and nodet pass at every level from the case's own with the same bytes, noder the same wherever it answers |
| `sutra/binder/live-edit#edit-none-is-read-only` | SG-23 | jvm and nodet pass at every level from the case's own with the same bytes, noder the same wherever it answers |
| `sutra/binder/live-edit#edit-is-one-of-three-words` | SG-23 | jvm and nodet pass at every level from the case's own with the same bytes, noder the same wherever it answers |
| `sutra/data#decimal-cells-are-exact` | SG-20 | jvm and nodet pass at every level from the case's own with the same bytes, noder the same wherever it answers |
| `sutra/data#table-as-record-is-its-first-row` | SG-6 | jvm and nodet pass at every level from the case's own with the same bytes, noder the same wherever it answers |

## Code fixes built (the third pass)

Each fix makes the implementation do what the text (SG-1 to SG-24) already said, on every runtime: the JVM, the Node tools and runtime
bundles and the Python V8 engine (both bundles) give the same results line byte for byte at 1.0, 1.1 and 1.2 (2673 cases; the runtime
bundle equal to the tools bundle on every case it answers); `protocol_check.py --live` 147 of 147, `--python` 57 pass (the rest
unsupported: no live tables). Against `g2/integration` at `6f621dc` with the same case files, the JVM's results differ on the new
cases only: every case that existed before answers byte for byte as before, at every level.

| Id | Built in | Behaviour now |
|---|---|---|
| CF-1 | `rachana-core` `LiveScreen` (`missing`, `DataPlan.readers`), so the JS bundles (screen operation `missing`); `TableClient.load` and `screen.js` mark a table answered `rdp.table.unknown` missing; the conformance runner marks every table not in `given.tables` missing | a `table` entry whose table the host does not have is failed: every panel that reads `$.<entry>` (itself, its parts or body) is `error: "RCH-2070 the host has no table '<table>' (data '<entry>')"`, `empty: true`, no `data`; the others bind |
| CF-2 | `rachana-core` `ElType.join`, `Checker` (`?:`, `??`, `switch`, `if`, `coalesce`, `least`, `greatest`) | one order-independent join (SPEC 4.14 rule 1); a misfit among the values of `switch`, `if`, `coalesce` is `RCH-9127` at the value: "the values of switch have different types (integer and string)" |
| CF-3 | `Checker.isoDate` | a timestamp text may end in `Z` or `±hh:mm` (SPEC 4.14 rule 6), in `date('…')`, `timestamp('…')` and a text compared with a date |
| CF-4 | Python `server.py` `_aggregate` | a decimal `sum` exact with the digits of the values it adds (its own group), `min`/`max` the cell as held; the JVM host (`RowQuery`) already did |
| CF-5 | `rachana-live` `LiveTables.can` | a table paged from its source (tier `columns` or `query`) leaves `group` out of `can`, in the table list (from its source's tier before it is open) and in its schema |

**Lead decisions** (written into PROTOCOL 3.1 and 6.3):

| Id | Decision | Built in |
|---|---|---|
| L-1 | `sum` over no value (no row, or every cell null) is `0` (`"0"` for a decimal column); `count` and `distinct` `0`; every other aggregate `null` | Python `server.py` (it gave `null`); the JVM `RowQuery` and the JS bundles (the same `RowQuery`) already did |
| L-2 | `avg` of a decimal column is exact: the exact sum divided as SPEC 4.16 rule 3 divides (decimal128, 34 significant digits, half to even), trailing zeros dropped (a computed value) | `RowQuery` (it gave 16 digits), so the JS bundles; Python `server.py` (it rounded to the column's scale) |

**Level 1.0 behaviour.** Only checked mode changes at 1.0 (it is not level-bound), in corners no case pinned before: a conditional
whose first branch is an integer and the second a decimal (`$.n > 0 ? 0 : $.mtm`) is `decimal`, was `integer`; `date` against
`timestamp` branches give `timestamp`, was `date`; `switch` is the join of its answers (was its first answer's type) and answers that
do not fit are `RCH-9127` (were accepted); `if(c, a, b)` is the join of `a` and `b` (was the first value's type), a misfit `RCH-9127`;
`coalesce`, `least` and `greatest` are the join of their arguments (were the last argument's type), a `coalesce` misfit `RCH-9127`
(was accepted); `timestamp('…Z')`, `timestamp('…+05:30')`, the same in `date('…')` and a timestamp text with a zone compared with a
date or timestamp are accepted (were `RCH-9127`). Unchecked evaluation, the ViewModel at 1.0 and every 1.0 case are unchanged.

### Cases added or replaced

| Case id | For | Evidence |
|---|---|---|
| `el/check#check-join-is-order-independent-0-mtm` | CF-2 | SPEC 4.14 rule 1 read; jvm, nodet, pyv8t (and noder, pyv8r where they answer) pass with the same bytes at 1.0, 1.1, 1.2; fails before the fix (`integer`) |
| `el/check#check-join-date-and-timestamp` | CF-2 | as above (before: `date`) |
| `el/check#check-switch-joins-its-answers` | CF-2 | SPEC 4.14 rules 1, 2; as above (before: `integer`) |
| `el/check#check-switch-answers-that-do-not-fit` | CF-2 | as above (before: `integer`, no problem) |
| `el/check#check-coalesce-joins-its-arguments` | CF-2 | as above (before: `integer`) |
| `el/check#check-coalesce-arguments-that-do-not-fit` | CF-2 | as above (before: `string`, no problem) |
| `el/check#check-if-joins-its-two-values` | CF-2 | as above (before: `integer`) |
| `el/check#check-least-joins-its-arguments` | CF-2 | as above (passed before too: the last argument was the decimal) |
| `el/check#check-timestamp-with-z` | CF-3 | SPEC 4.14 rule 6, 4.2, PROTOCOL 3.1; as above (before: `RCH-9127`) |
| `el/check#check-timestamp-with-an-offset` | CF-3 | as above (before: `RCH-9127`) |
| `sutra/data#a-missing-table-fails-its-panels` | CF-1 | replaces `sutra/data#missing-table-reads-as-no-rows` (VERIFIED SG-16); SPEC 2.4, TRANSLATION 10 read; jvm, nodet, noder, pyv8t, pyv8r pass with the same bytes at 1.1 and 1.2 |
| `sutra/data#a-missing-table-leaves-the-other-panels` | CF-1 | as above (the other panel binds) |
| `live/overlay#a-paged-table-does-not-offer-group` | CF-5 | PROTOCOL 2, 16 read; `protocol_check.py --live` pass |
| `protocol/host#a-decimal-sum-has-the-digits-of-its-own-group` | CF-4 | PROTOCOL 3.1, 6.3 read, arithmetic recomputed; `--live` and `--python` pass |
| `protocol/host#a-sum-of-nothing-is-0` | L-1 | PROTOCOL 6.3; `--live` and `--python` pass (the Python host gave `null` before) |
| `protocol/host#a-decimal-avg-is-divided-to-34-digits` | L-2 | PROTOCOL 3.1, SPEC 4.16 rule 3; `--live` and `--python` pass (before: `1.666666666666667` on the JVM, rounded to the column's scale in Python) |

The JS bundles' `query` (the static-files host, `RowQuery` compiled by TeaVM) gives the same answers for `L-1` and `L-2` as the JVM host
(checked by calling both bundles' `query` on the cases' tables).

### Flipped in the third pass

| Case id | Evidence |
|---|---|
| `el/aggregates-host-values` (all 12: `sum-of-a-filtered-list`, `net-of-two-filtered-lists`, `sum-of-nothing-is-0`, `sum-of-an-absent-list-is-0`, `avg-of-an-absent-list-is-empty`, `sum-of-a-masked-list-is-masked`, `sum-of-a-number-is-a-problem`, `sum-of-a-text-is-a-problem`, `sum-of-a-record-is-a-problem`, `avg-of-a-text-is-a-problem`, `sum-of-a-number-at-1-0-is-0`, `avg-of-a-text-at-1-0-is-empty`) | read against SPEC 5.1 by an independent reviewer (a Fable review lane that neither built the cases nor this fix), who found every expectation in agreement; jvm, nodet, pyv8t (and noder, pyv8r where they answer) pass with the same bytes at 1.0, 1.1 and 1.2 |

## The expression language's aggregates aligned (L-3)

**Lead decision L-3** (2026-11-21, branch `g2/el-avg` off `g2/integration` at `f7f4cd8`): one rule everywhere. From level 1.1 the
expression language's `avg` over integers and decimals is L-2's division (the exact sum over the count, decimal128, 34 significant
digits, half to even, trailing zeros dropped), and `min(list)` / `max(list)` give the exact element, as the host's `min`/`max` do
(PROTOCOL 3.1). `sum` over no value was already `0` in the expression language at every level (`el/aggregates-host-values`). Level
1.0 is unchanged (doubles; every 1.0 case answers as before). Written into SPEC 4.16 rule 7 and PROTOCOL 3.1; built in `rachana-core`
`Level11` (`avg`, `min`, `max` beside `sum`), so both JS bundles and the Python V8 engine.

| Aggregate | Host (PROTOCOL 3.1, 6.3) | Expression language from 1.1 | Why |
|---|---|---|---|
| `sum` | exact | exact (already) | aligned |
| `avg` | exact, 34 digits half to even | **now the same** (was a double) | aligned (L-3) |
| `min`, `max` | the cell as held | **now the exact element** (was a double) | aligned; printed as a computed decimal (SPEC 4.16 rule 8: no trailing zeros), where the host sends the cell's own text |
| `median`, `percentile` | a double (the JVM host interpolates in doubles; the Python host does not answer them) | a double | intentionally the same as the host: real-number aggregates (interpolation) |
| `variance`, `stddev` and the statistics of SPEC 5.8 | not a protocol aggregate | a double | intentionally real-number (square roots, moments; SPEC 4.16 rule 5) |

| Case id | Evidence |
|---|---|
| `el/numbers-exact#avg-of-decimals-is-exact` | SPEC 4.16 rules 3, 7; PROTOCOL 3.1's worked example (`"1"`, `"2"`, `"2"`); digits recomputed by hand (5/3 to 34 digits, last digit 6→7 half to even) |
| `el/numbers-exact#avg-of-decimals-adds-exactly` | SPEC 4.16 rule 7 (0.3 / 2) |
| `el/numbers-exact#avg-of-decimals-drops-trailing-zeros` | SPEC 4.16 rules 7, 8 |
| `el/numbers-exact#avg-of-a-field-is-exact` | SPEC 4.16 rule 7, 5.1 (an empty value is not counted: 0.9 / 3) |
| `el/numbers-exact#avg-with-a-double-is-a-double` | SPEC 4.16 rules 4, 7 (0.30000000000000004 / 2) |
| `el/numbers-exact#avg-of-decimals-at-1-0-is-a-double` | level 1.0 unchanged (N5) |
| `el/numbers-exact#min-of-decimals-is-a-decimal` | SPEC 4.16 rules 7, 8 |
| `el/numbers-exact#max-of-decimals-is-a-decimal` | SPEC 4.16 rule 7 (an element a double cannot hold) |
| `el/numbers-exact#min-with-a-double-is-a-double` | SPEC 4.16 rules 4, 7 |
| `el/numbers-exact#avg-of-decimals-is-exact-at-1-2` | SPEC 4.16 rules 3, 7 at 1.2 (6.5 / 3) |
| `el/numbers-exact#max-of-decimals-is-a-decimal-at-1-2` | SPEC 4.16 rule 7 at 1.2 |
| `el/numbers-exact#median-of-decimals-is-a-number` | SPEC 4.16 rule 7, PROTOCOL 6.3 (the intended difference above) |

Each passes on the JVM, both Node bundles and the Python V8 engine (both bundles) with the same results line at 1.0, 1.1 and 1.2; every
case that existed before answers as before (`build_js.py`: 0 errors, 0 verified failing, runtime = tools at every level).

## Not flipped

None: every case below `conformance/1/` is verified.
