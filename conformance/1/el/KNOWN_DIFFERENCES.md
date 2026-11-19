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

## The function examples (plan H5)

`el/functions-*` is generated from the examples of `spec/1/functions.yaml` (one case per example; 10 examples that need a fixture or
are described in words are not cases). On the reference 273 of 288 pass. The 15 that stay `verified: false`: the list aggregates
(`sum`, `count`, `avg`, `median`, `variance`, `stddev`, `variance_pop`, `stddev_pop`, `distinct`, `percentile`, `wavg`: SPEC 5.7 puts them in Core,
the reference computes them only inside the pivot engine and answers empty), and `min` and `max` over a list. They are settled by A6/A-steps
(the aggregates move into Core). `xnpv`, `cumprinc` and `ln` carry a tolerance of 1e-9 (SPEC 4.10 rule 7).
`tools/conformance/coverage.py` shows every one of the 207 Core functions has at least one case.

## The decisions for 1.0 (2026-11-19: EL_EXPANSION XD2 to XD4)

The owner settled three things before 1.0 is cut: the reservations (SPEC 4.15, `spec/1/reserved.yaml`), the numbers
(SPEC 4.16) and text by code point (SPEC 4.13). The reference implements none of them yet, and no language change enters
the compiler before `v0.10.0` is pinned (SPLIT_PLAN rule 4: the split's proof is that every verified case is the same before
and after each step). So:

| Cases | Pin | State |
|---|---|---|
| `el/reserved` (25), `grammar/reserved` (16) | the reserved tokens and names refused with their messages; what stays allowed | `verified: false`; the tokens are refused today with other words ("unexpected character", "bad number"), the names as "unknown function" |
| `el/numbers-exact` (34) | exact integer and decimal literals, decimal128, `/` half-even to 34 digits, doubles with doubles, overflow of the integer part | `verified: false`; the reference computes in doubles |
| `el/text-code-points` (17) | lengths, positions and order by code point; source offsets in UTF-16 units | `verified: false`; the reference counts UTF-16 units |
| 22 cases tagged `changes-with-xd3` in `el/examples/arithmetic` and `el/paths-operators` | the reference's doubles for literal arithmetic (`0.1 + 0.2` → `0.30000000000000004`, `0.0001 * 1` → `1.0E-4`, `type: number`) | **kept verified and unchanged** as the split's proof; rewritten, ids kept, in the commit that changes the compiler (expected kind `decimal`, the text of SPEC 4.2) |

Until that commit the suite holds both answers for literal arithmetic, and a runner on the reference exits with code 3
(only unverified cases fail). No case pinned UTF-16 lengths or a reserved token's old words, so nothing else changes.
