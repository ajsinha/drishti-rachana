<!-- Copyright (c) 2026 Ashutosh Sinha. Specification licence: ../../../LICENSE-SPEC.md (draft, not yet in force). -->
# Where the reference differs from the specification (found by the first run, plan H1)

The expression cases were run on the reference (Drishti's `compute/drishti-rachana`, release i7a) by
`tools/conformance/rachana_conformance.py` through `tools/reference-bridge/RefBridge.java`. 991 cases pass and are
`verified: true`. The 45 below fail on the reference and keep `verified: false`: each case pins what SPEC says, the
reference is to change (plan N5 settles each; A1 for the codes and messages), and no case is changed to fit a defect.

| Cases | SPEC | Reference | Settled by |
|---|---|---|---|
| 43 `bad-*`, `expr-error` and limit cases | 6.3: a compile or argument problem reads `expression '<source>': <words>` (a template: `template '<source>': ...`) | the words alone, with ` at <offset>` appended | A1 (the error mapper), N5 |
| `el/text#regex-extract-ab-trade-id` | an unsupported pattern raises `RCH-2102` | `DRS-4010` (look-around refused) | A1 / A3 (SafeRegex into Core) |
| `el/distributions#standardize-42-40-1-5` | `standardize` returns a number | a decimal | N5 |

Other findings of the run: `check` cases (20) are `unsupported` by the bootstrap bridge, because the reference's checked mode reads a
Drishti pivot model (`trades.FIELD`), not record types; they wait for Core's checker (A-steps). Cases that use the Drishti
profile's format names (`pct4`) carry `profile: [core, drishti]`.

## The hand-written `problem` examples (plan H2)

Five of `problem/examples/problem` fail on the reference and stay `verified: false`: `valid-sutra-has-no-problems` (the reference reports problems),
`unknown-top-level-key` and `profile-key-without-the-profile` (one problem more than expected), `problems-in-position-order` and
`expression-problem-inside-a-panel` (a different first code). The causes are not yet analysed; each is settled with decision N5 before the examples are frozen.
