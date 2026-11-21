<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary. All rights reserved. -->
# The Rachana specification: decisions

The normative text is [spec/1/SPEC.md](../../spec/1/SPEC.md) (the document model, Rachana-EL, the function catalogue, the problem
model, versions and compatibility, conformance); the ViewModel is [spec/1/VIEWMODEL.md](../../spec/1/VIEWMODEL.md) and the Data
Protocol is [spec/1/data-protocol/PROTOCOL.md](../../spec/1/data-protocol/PROTOCOL.md). This note keeps only the decisions behind
them and why; it repeats none of the rules. Related: [DECISIONS](DECISIONS.md), [CORE_AND_PROFILES](CORE_AND_PROFILES.md),
[COMPILER_AND_RUNTIMES](COMPILER_AND_RUNTIMES.md).

## The specification is open, the runtime is not

Anyone may read the specification and write Sutras (LICENSE-SPEC.md). The runtime that implements it is proprietary.

## One source of truth

The parser's own tables (`PanelKind`, `ControlKind`, `Functions`, `Wiring`, `Form`, `PivotSpec`) are the truth.
`rachana.lang.json` and `sutra.schema.json` are generated from them at build time and committed; a gate test fails when a committed
file is stale. So an editor never offers what the parser refuses, and nothing is written by hand twice. Where the text and the
conformance suite disagree, it is a defect in one of them and a release blocker: the suite is the executable meaning.

## Publication

Each release publishes `spec/<major>/` unchanged at a stable address (the schema's `$id` is that address), the pattern Drishti
Elements uses for its docs site. A tagged release is immutable; `spec/1/` on `main` is the newest minor of major 1.

## Compatibility

- A minor adds only: nothing is removed and nothing changes meaning, so a Sutra valid in 1.x is valid, with the same ViewModel, in every
  later 1.y, and the conformance suite of 1.x passes unchanged (a release gate).
- Deprecation is a warning with a machine-applicable fix, never an error; an old spelling keeps compiling and is never offered.
  A check that would refuse existing files ships as a warning for at least two minors.
- A major is cut only for a change that cannot be an addition. A runtime reads N and N-1; every breaking change comes with its
  migration rule (`rachana upgrade`); a breaking change without one is not accepted. The ViewModel and the Data Protocol version
  independently, so a language major forces no painter or server upgrade.
- A profile may add; it may not change the meaning of anything in Core or make a valid Core Sutra invalid. Core's conformance suite
  runs with every profile loaded.

## Problems

Core keeps the numbers the product had, with the prefix changed from `DRS-` to `RCH-`, so existing knowledge and tests carry over;
the product maps one to the other in its profile adapter. A code never changes meaning (wording may improve; tools match on codes).
Messages carry names, never values from a user's tables, so a problem is safe to send to a model. A panel without data is a state
of that panel, not a problem: only a Sutra that does not compile produces problems.
