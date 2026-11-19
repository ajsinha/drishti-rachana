<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary: see LICENSE. -->
# Conformance driver

`rachana_conformance.py` implements `conformance/RUNNER.md`: it validates the case files against the schema, drives a runtime
program over standard input (`--cmd`), compares by FORMAT 6 and 7, writes `results/<runtime>.jsonl` and the summary, and exits by
RUNNER 4.4. `--record` rewrites the one-line `expect` of unverified cases from the reference. `pip install -r requirements.txt`.
