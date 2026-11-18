<!-- Copyright (c) 2026 Ashutosh Sinha. Specification licence: ../../LICENSE-SPEC.md (draft, not yet in force). -->
# Rachana specification, major version 1

**Status: draft 1 (2026-11-17).** Written in phase R0 (step 0e) from the reference implementation inside Drishti and
the accepted design ([../../docs/design/](../../docs/design/)). Not yet normative: it becomes so when the owner approves
it and the first conformance suite (`conformance/1/`) is cut.

This folder is the open specification of `rachana: 1`, the language of Sutra files. It says what a Sutra means; it
does not describe any one runtime.

## Contents

| File | What it is | State |
|---|---|---|
| [SPEC.md](SPEC.md) | the normative text: document model, Sutra Core keys and the `data:` entries, Rachana-EL values and evaluation, the Core function catalogue, the error model, versioning, what conformance pins | draft 1 |
| [rachana-el.ebnf](rachana-el.ebnf) | the complete grammar of Rachana-EL in ISO 14977 EBNF: tokens, operators and precedence, templates (59 rules) | draft 1 |
| [TRANSLATION.md](TRANSLATION.md) | the translation rules: Sutra + data → ViewModel, construct by construct | draft 1 |
| `functions.yaml` | every Core function in full (signature, types, null and error behaviour, examples) | planned, R0 step 0d |
| `rachana.lang.json`, `sutra.schema.json` | the language definition and the Core JSON Schema, generated from the reference compiler's tables | planned, R0 step 0c |
| [viewmodel.schema.json](viewmodel.schema.json), [VIEWMODEL.md](VIEWMODEL.md) | the ViewModel's JSON Schema (decision D6, N3): 55 definitions, transcribed by reading from the reference's records; and a short description with the differences from the Views design | draft; schema replaced by the generated one in R0 step H4 |

Read SPEC.md first; it links the other two where they apply.

## Precedence

The conformance suite (`conformance/1/`, planned) is the executable form of this specification. Where a case and this
text disagree, the case wins until the defect is fixed in one of them. Within this folder, generated files win over
prose for the names they list (keys, options, functions), and SPEC.md wins over TRANSLATION.md for the meaning of an
expression.

## Licence

Everything in `spec/` is covered by [LICENSE-SPEC.md](../../LICENSE-SPEC.md): free to read, implement and test
against; the names "Rachana" and "Drishti Rachana" only for implementations that pass the conformance suite of the
version they claim. **That licence is still a draft for the owner's review and not yet in force.** The runtime in this
repository stays under [LICENSE](../../LICENSE).

## Publication

Each release publishes `spec/1/` unchanged at a stable address (planned:
`https://ajsinha.github.io/drishti-rachana/spec/1/`). `spec/1/` on `main` is always the newest minor of major 1; a
future major gets its own folder and this one stays published.
