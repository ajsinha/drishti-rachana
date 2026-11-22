<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary: see LICENSE. -->
# Conformance driver

`rachana_conformance.py` implements `conformance/RUNNER.md`: it validates the case files against the schema, drives a runtime
program over standard input (`--cmd`), compares by FORMAT 6 and 7, writes `results/<runtime>.jsonl` and the summary, and exits by
RUNNER 4.4. `--record` rewrites the one-line `expect` of unverified cases from the reference. `pip install -r requirements.txt`.

| Script | Plan | What |
|---|---|---|
| `rachana_conformance.py` | H1, A10 | the driver above; `--jvm` runs Rachana's own JVM runtime (`java/rachana-conformance`, after `./mvnw clean package`); `--classpath-file` runs the bootstrap bridge (`../reference-bridge`) over Drishti's jars, the reference every split step is compared with |
| `schema_cases.py` | 1.0.1 | the schema guard: every Sutra of the cases, examples and libraries that compiles validates against `spec/1/sutra.schema.json` (run by `SchemaCasesGuardTest` in `verify`) |
| `spec_files.py` | H4 | checks `spec/1/rachana.lang.json` and `spec/1/sutra.schema.json` against `rachana-tools` `LangFiles` at the newest level; exit 1 when stale, `--write` regenerates |
| `coverage.py` | H5 | the coverage gate: every Core entry of `rachana.lang.json` and every Core function of `functions.yaml` is in a case, or in `conformance/coverage-gaps.txt` (the work queue, which only shrinks) |
| `compare_viewmodel_schema.py` | H4 | `spec/1/viewmodel.schema.json` against the reference's `ViewModelSchema.build()` |
| `reach_report.py` | H3 | the Rachana-side reach report of the reference (`docs/reports/R0_REACH_REPORT.md`) |

Run the Java programs on Java 21, the reference's production runtime: `fisher_inv` differs in its last digit on Java 25
(`docs/plans/DRISHTI_MIGRATION_TODO.md` section 7).
