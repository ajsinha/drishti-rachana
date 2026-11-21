<!-- Copyright (c) 2026 Ashutosh Sinha. Specification licence: ../../../LICENSE-SPEC.md (draft, not yet in force). -->
# Where the reference differs from the specification (found by the first run, plan H1)

The expression cases were run on the reference (Drishti's `compute/drishti-rachana`, release i7a) by
`tools/conformance/rachana_conformance.py` through `tools/reference-bridge/RefBridge.java`. The expression cases that differ from the draft specification were settled by decision N5: at level 1.0 the reference wins and the case is verified; the
specified behaviour is the 1.1 variant of the case.

| Cases | SPEC (draft) | Reference (level 1.0) | Settled |
|---|---|---|---|
| 43 `bad-*`, `expr-error` and limit cases | 6.3: `expression '<source>': <reason>` | the words alone, with ` at <offset>` | N5: the cases pin the reference and are verified; the prefix is level 1.1 (`el/messages-level-1-1`, 43 cases, unverified) |
| `el/text#regex-extract-ab-trade-id` | `RCH-2102` | `RCH-4010` | N5: `RCH-4010` verified; `-l11` expects `RCH-2102` |
| `el/distributions#standardize-42-40-1-5` | a number | a decimal | N5: decimal verified; `-l11` expects a number |

Other findings of the run: `check` cases (20) are `unsupported` by the bootstrap bridge, because the reference's checked mode reads a
Drishti pivot model (`trades.FIELD`), not record types; they wait for Core's checker (A-steps). Cases that use the Drishti
profile's format names (`pct4`) carry `profile: [core, drishti]`.

## The hand-written `problem` examples (plan H2, analysed)

The examples assumed a Core Sutra with no `match` and JSON-pointer paths. The reference always loads the Drishti profile (so `match: { kind }`
is required) and reports lines and columns but no `path` or `expr` offsets inside a Sutra, and stops at the first problem a later one depends on.
By N5 level 1.0 is the reference: the examples gained a `match` line, lost `path`/`expr`, and `unknown-top-level-key` expects the reference's column; four are verified.
`problems-in-position-order` reports one problem at 1.0 and two at 1.1 (`-l11`, unverified). `profile-key-without-the-profile` needs a Core runtime
with no profile and waits for rachana-core (unverified).

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

**Built at level 1.1 (2026-11-21, EL phase 1).** The three changes are the compiler's at level 1.1 (SPEC 4.16, 4.17, 4.13): the cases of
`el/reserved`, `el/numbers-exact` and `el/text-code-points` that the reference failed carry `since: "1.1"` and are verified there;
those it passed stay at 1.0; the 22 `changes-with-xd3` cases stay verified at 1.0 (the reference's doubles), since a case runs at its own
level. Until that commit the suite holds both answers for literal arithmetic, and a runner on the reference exits with code 3
(only unverified cases fail). No case pinned UTF-16 lengths or a reserved token's old words, so nothing else changes.
