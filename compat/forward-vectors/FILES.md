<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary: see LICENSE. -->
# Painter forward vectors: the file list (CG7)

Coordination note for the Elements lane. CG7 (SUTRA_EXPANSION 6.5) lives in Elements, in
`elements/drishti-views/test/forward/`; Elements owns the tests and the vectors' final bytes. This list is the contract between the two
repositories: which files, what each injects into a frozen **ViewModel 1.0** document (a golden from the corpus, see
`compat/customers/drishti/<release>/`, or `spec/1/viewmodel.schema.json`'s examples), and what the painter must do
(SUTRA_EXPANSION 6.3 rule 4). A vector is one JSON file: `{ "name", "description", "input": <ViewModel or frame>, "expect": { ... } }`;
`expect` names the rule, the DOM outcome as a predicate (not a snapshot: forward vectors must survive painter changes that keep the
promise) and, for resync and refusal, the words shown.

| # | File | Injected into a 1.0 document | The painter must |
|---|---|---|---|
| 1 | `unknown-field-document.json` | an extra optional field on the root and on the title, strip item, panel, column, control record | ignore it; draw exactly what the 1.0 document draws |
| 2 | `unknown-field-nested.json` | an extra field inside a cell, a series point, a bar, a frame row | ignore it, same DOM as the 1.0 vector |
| 3 | `unknown-kind.json` | a panel of kind `holo` (with a title and a `rows` array) between two known panels | draw a titled panel that says the kind is not known to this painter; draw both neighbours as before |
| 4 | `unknown-kind-nested.json` | an unknown kind as a child inside a known container (tab, split) | as 3, inside the container; the container still lays out |
| 5 | `unknown-tone.json` | cells and strip items with tone `plasma` | give no tone (neutral style); show the text |
| 6 | `unknown-control.json` | a control of kind `knob` with a value | draw its value, read-only; labelled; no input |
| 7 | `unknown-effect.json` | an action with effect `teleport` | show a disabled action with the words "needs a newer viewer" (the words are Elements'); no event fires |
| 8 | `unknown-patch-op.json` | a frame whose patch has op `splice-all` after valid ops | answer by asking for a resync (the Data Protocol's resync request); apply none of the unknown op; the valid ops before it stand |
| 9 | `unknown-frame-field.json` | an extra field on a frame envelope | ignore it |
| 10 | `unknown-major.json` | `contract: "2.0"` | refuse in words (title and reason); draw nothing from the document |
| 11 | `minor-ahead.json` | `contract: "1.7"` with only known fields | paint as 1.0 (the stamp is advice; unknown parts follow 1 to 9) |
| 12 | `known-1.0-unchanged.json` | none: a frozen 1.0 golden per kind of panel (one file per kind in `known-1.0/`) | produce the frozen DOM snapshot of the 1.0 painter: a painter release never changes how it draws a contract minor it already drew |

Rules for the vectors:

- Each vector changes **one thing** from a 1.0 document the painter already paints, so a failure names the rule.
- Inputs come from synthetic demo data only (the corpus's rule); none from a customer.
- Vectors 1 to 11 are additions-only forever; a new contract minor adds vectors, it never edits these.
- Rachana's side: the generator of vectors 1 to 11 from a 1.0 golden is a small script in Rachana (`tools/regime/`, when Elements
  asks for it); until then Elements writes the files by hand. The compatibility regime reads this table, not the files.

State: the list is agreed on Rachana's side; Elements' lane writes the files and tests (not started in this repository).
