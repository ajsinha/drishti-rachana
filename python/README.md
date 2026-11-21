# drishti-rachana (Python), skeleton

Python package for Drishti Rachana; import name `rachana`. **Skeleton (plan F7):** the Data Protocol types, the
conformance runner, the `rachana lint` command, a minimal Data Protocol host and the Flask and FastAPI adapters. It holds no language semantics: no parser, operator table or
formatter: compiling, linting and evaluating run the JavaScript bundle on V8 (`drishti_rachana.engine`, W-1).

| Module | What it is |
|---|---|
| `drishti_rachana.protocol` | Data Protocol v1 messages as dataclasses with `to_json` / `from_json`: table schema, rows page, query and filter tree, keyed patch, error. Standard library only. Names to be aligned with `spec/1/data-protocol` (F8) |
| `drishti_rachana.conformance` | the runner of `conformance/RUNNER.md`: loads and validates case files, runs an `Adapter`, compares (FORMAT 6, 7), writes `results/<runtime>.jsonl` and the summary, exit codes 0/1/2/3. `NoneAdapter` marks every case `unsupported` |
| `drishti_rachana.server` | `DataHost`: a framework-neutral Data Protocol v1 host (schema, rows, query with the filter tree, sort, cursor paging, keyed patches, minimal SSE change stream). JSON only: Arrow, NDJSON and grouping answer 406 or `rdp.query.unsupported` |
| `drishti_rachana.adapters.flask`, `.fastapi` | `RachanaBlueprint` / `RachanaRouter`: mount Sutras, `@table`, `@live`; routes `/`, `/page/<name>`, `/sutras/<name>.sutra.yaml`, `/data/...` |
| `python -m drishti_rachana lint` | compile and lint Sutras on V8 (`pip install 'drishti-rachana[v8]'`): `--format text\|json\|sarif`, `--strict`; exit 0, 1 on errors, 2 usage |
| `drishti_rachana.cli` | `rachana conformance run` |
| `drishti_rachana.engine` | **Rachana without a JVM** (decision W-1): `Rachana()` runs the JavaScript bundle (A15) on V8 through `mini-racer`: `check(sutra)` compiles and lints, `evaluate`, `template`, `format`, `view`, `edit`. The answers are the bundle's, byte-identical to the JVM on every conformance case. Extra `v8`; the bundle is built by `python3 tools/js/build_js.py` into `drishti_rachana/bundle/` |

```bash
pip install -e 'python[conformance]'          # PyYAML + jsonschema to read case files
rachana conformance run --cases conformance/1 # adapter 'none': every case unsupported, exit 0
rachana conformance run --adapter mypkg.mod:MyAdapter --only 'el/**'

python3 tools/js/build_js.py                  # the bundles, into drishti_rachana/bundle/
pip install -e 'python[v8]'
python -c "from drishti_rachana.engine import Rachana; print(Rachana().check(open('examples/static-sales/overview.sutra.yaml').read()))"
```

Not yet: `--cmd` (cases on stdin, RUNNER 5), per-case timeouts, `--record` (JVM reference only), the protocol runs.

Optional extras: `flask`, `fastapi`, `arrow`, `polars`, `conformance`, `test`. Tests: `python -m pytest -q python/tests`.

Copyright (c) 2026 Ashutosh Sinha. Proprietary; see `LICENSE` at the repository root.
