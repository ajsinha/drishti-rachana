<!-- Copyright (c) 2026 Ashutosh Sinha. Specification licence: ../LICENSE-SPEC.md (draft, not yet in force). -->
# Rachana conformance suite

**Status: draft 1 (2026-11-17).** The format and the runner contract are written (front-load item F1); cases are being
harvested from the reference implementation (plan items F2, H1 to H6). Nothing here has been run yet: every case is
`verified: false` until the reference runner has produced it and a person has reviewed it.

The conformance suite is the **executable form of the specification** ([../spec/1/](../spec/1/)): a set of cases, each
an input and the exact output a conforming runtime gives. Where the specification's prose and a case disagree, that is
a defect in one of them and a release blocker; until it is fixed, the case wins (SPEC section 0).

"Is Rachana" means: passes `conformance/<major>/` for the profile and minor claimed ([RUNNER.md](RUNNER.md) section 7).

## Contents

| File | What it is |
|---|---|
| [FORMAT.md](FORMAT.md) | the case format: files, the eleven kinds and their fields, values, how expected problems are matched (code, path, range), comparison, canonical JSON, number text pinned to the Java reference |
| [schema/case.schema.json](schema/case.schema.json) | the JSON Schema (draft 2020-12) every case file validates against |
| [RUNNER.md](RUNNER.md) | the contract every runtime's runner implements: options, the results file, statuses, exit codes, cases on stdin for third-party runtimes, record mode, the gate |
| `1/` | the cases of language major 1, one folder per area |
| `1/<area>/examples/` | hand-written examples of the format, one file per kind; ordinary cases |

## The kinds

| Kind | Pins | Area |
|---|---|---|
| `expr` | an expression's value, type, text form and the paths it read | `el/` |
| `expr-error` | an expression that does not compile: code, message, offsets | `el/` |
| `check` | checked mode: the type, or the refusal | `el/` |
| `grammar` | the grammar accepts or refuses a text | `grammar/` |
| `format` | a format and a tone applied to a value | `format/` |
| `sutra` | Sutra + data → the ViewModel | `sutra/` |
| `problem` | a Sutra's complete, ordered problem list | `problem/` |
| `patch` | table patches → ViewModel patch frames | `patch/` |
| `plan` | what is asked of each table: columns, filter tree, push-down | `plan/` |
| `edit` | an editing operation → the new text, comments kept | `edit/` |
| `protocol` | a Data Protocol host's answers | `protocol/` |

## Running

```text
rachana conformance run                              # this runtime, conformance/1, results/<runtime>.jsonl
rachana conformance run --cmd "./my-port --stdio"    # a third-party runtime, cases on stdin
rachana conformance protocol --base http://host/data/v1
```

The JVM runner runs on every build of this repository (and, from plan H1, inside Drishti from a pinned cases jar); the
other runtimes join as they are built. The gate: the reference passes and every runtime's results file is
byte-identical to the reference's (RUNNER section 7).

## Writing a case

Read [FORMAT.md](FORMAT.md) section 10. In short: self-contained, one point per case, expect only what is certain,
`verified: false` with a `source` until the reference has confirmed it, validate against the schema before committing.

## Licence

Everything in `conformance/` is covered by [LICENSE-SPEC.md](../LICENSE-SPEC.md) (draft, not yet in force): free to
read, implement and test against.
