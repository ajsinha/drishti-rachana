# drishti-rachana (Python), skeleton

**EXPERIMENTAL at Rachana 1.0 (gate 2, O9):** every module of `drishti_rachana` may change in a minor release; the frozen
contract is the documents it carries (the ViewModel schemas, the Data Protocol, the conformance case format).

Python package for Drishti Rachana; import name `rachana`. **Skeleton (plan F7):** the Data Protocol types, the
conformance runner, the `rachana lint` command, a minimal Data Protocol host and the Flask and FastAPI adapters. It holds no language semantics: no parser, operator table or
formatter: compiling, linting and evaluating run the JavaScript bundle on V8 (`drishti_rachana.engine`, W-1).

| Module | What it is |
|---|---|
| `drishti_rachana.protocol` | Data Protocol v1 messages as dataclasses with `to_json` / `from_json`: table schema, rows page, query and filter tree, keyed patch, error. Standard library only. Names to be aligned with `spec/1/data-protocol` (F8) |
| `drishti_rachana.conformance` | the runner of `conformance/RUNNER.md`: loads and validates case files, runs an `Adapter`, compares (FORMAT 6, 7), writes `results/<runtime>.jsonl` and the summary, exit codes 0/1/2/3. `NoneAdapter` marks every case `unsupported` |
| `drishti_rachana.server`, `.rows_forms`, `.subscription`, `.bridge` | `DataHost`: a framework-neutral Data Protocol v1 host as the JVM reference is (schema, rows, query with the filter tree, sort, cursor paging, `group` with totals, keyed patches, declared schemas with `can` enforced, masked columns, key-ordered stable pages). Rows as JSON, as **Arrow IPC** (the official pyarrow, extra `arrow`; `406` when it is not installed or the table does not declare `arrow`) and as **NDJSON**; the SSE change stream (`GET` or `POST /changes`) with **filtered** (`changes.filter`, PROTOCOL 8.2) and **conflated** (`conflate: {everyMs}`, 8.4) subscriptions and gap frames; `Bridge` answers the in-process bridge incl. `sub`/`unsub`/`msg` and `res.arrow`. `drishti_rachana.testhost` serves it with the test adapter of PROTOCOL 15: `python tools/conformance/protocol_check.py --python` runs all 55 `protocol` cases against it as `--reference` does against the JVM (54 pass; the frozen `bridge-rows-request-answered-once` fails on both) |
| `drishti_rachana.assets` | the browser assets of `<rachana-view>`: the element and modules, the vendored Drishti Elements 3.2.0-dev+b466b87 pack, the runtime bundle (and Apache Arrow JS), looked up in a fixed table (package data in a wheel, `js/` in a checkout; no path is joined from the caller) with their media types |
| `drishti_rachana.adapters.flask`, `.fastapi` | `RachanaBlueprint` / `RachanaRouter`: mount Sutras, `@table(..., schema=)`, `@live`; routes `/`, `/page/<name>` (the page with the real `<rachana-view>`), `/sutras/<name>.sutra.yaml`, `/static/...` (its assets), `/data/...` (the Data Protocol, `/data/changes` GET or POST) |
| `python -m drishti_rachana lint` | compile and lint Sutras on V8 (`pip install 'drishti-rachana[v8]'`): `--format text\|json\|sarif`, `--strict`; each Sutra at the level its `rachana:` key declares (SPEC 2.1); exit 0, 1 on errors, 2 usage |
| `drishti_rachana.cli` | `rachana conformance run` |
| `drishti_rachana.engine` | **Rachana without a JVM** (decision W-1): `Rachana()` runs the JavaScript bundle (A15) on V8 through `mini-racer`: `check(sutra)` compiles and lints (each Sutra at its declared level), `evaluate`, `template`, `format`, `view`, `edit`. The answers are the bundle's, byte-identical to the JVM on every conformance case. Extra `v8`; the bundle is built by `python3 tools/js/build_js.py` into `drishti_rachana/bundle/` |

```bash
pip install -e 'python[conformance]'          # PyYAML + jsonschema to read case files
rachana conformance run --cases conformance/1 # adapter 'none': every case unsupported, exit 0
rachana conformance run --adapter mypkg.mod:MyAdapter --only 'el/**'

python3 tools/js/build_js.py                  # the bundles, into drishti_rachana/bundle/
pip install -e 'python[v8]'
python -c "from drishti_rachana.engine import Rachana; print(Rachana().check(open('examples/static-sales/overview.sutra.yaml').read()))"
```

Not yet: `--cmd` (cases on stdin, RUNNER 5), per-case timeouts, `--record` (JVM reference only).

Optional extras: `flask`, `fastapi`, `arrow`, `polars`, `conformance`, `test`. Tests: `python -m pytest -q python/tests` (or `uv run --extra test --extra flask --extra fastapi pytest` in `python/`). The live-orders board paints in Chromium: `uv run --with flask --with playwright==1.64.0 python examples/live-orders/paint_check.py` (needs the runtime bundle, `python3 tools/js/build_js.py`).

Copyright (c) 2026 Ashutosh Sinha. Proprietary; see `LICENSE` at the repository root.
