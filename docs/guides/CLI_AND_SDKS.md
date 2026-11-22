<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary. All rights reserved. -->
# Command line and SDKs

Rachana has one compiler, written in Java and built for JavaScript (TeaVM); Python runs the same JavaScript bundle on V8. Every answer
is byte-identical on the JVM, in Node, in the browsers and in Python (each runtime passes the same conformance suite). This guide
lists the ways in and when to use each.

| You want | Use |
|---|---|
| a dashboard site from files, no backend | the `rachana` command: `new`, `lint`, `preview`, `build` (static mode) |
| to edit Sutras visually | `rachana studio` ([Studio](STUDIO.md)) |
| a server with sign-in, databases and live tables | `rachana serve` / the server jar ([Server](SERVER.md)) |
| screens inside your own web app | `<rachana-view>` and the JavaScript facade |
| screens inside a Flask or FastAPI app | the Python package `drishti-rachana` |
| to compile, lint or evaluate on the JVM | the Java modules (`com.ash.rachana`) |
| to check Sutras in CI | `rachana lint --format sarif`, or `python -m drishti_rachana lint` |

**Stability.** The JSON documents are the frozen contract: the ViewModel schemas, the Data Protocol and the conformance case format.
The JavaScript facade, `<rachana-view>`'s attributes and the Python SDK are marked experimental and may change in a minor release; the
Java entry surface (`Rachana`, `RachanaOptions`, `Node`, `ElCompiler`, `Expr`, `Formats`, `LibrarySet`, ... marked `[stable]` in
[java/api-surface.txt](../../java/api-surface.txt)) is stable within major 1.

## The `rachana` command (Node 20+)

The CLI is `js/packages/rachana-cli/bin/rachana.js` (no npm dependencies). It needs the compiler bundles:
`python3 tools/js/build_js.py` (JDK 21) writes them to `js/packages/rachana/dist/`. Below, `rachana` stands for
`node js/packages/rachana-cli/bin/rachana.js`.

| Command | What it does |
|---|---|
| `rachana new <dir> [--template basic]` | a starter folder: `sutras/overview.sutra.yaml` at the newest level, `data/sales.csv`, `rachana.yaml`, `README.md` |
| `rachana lint [paths] [--format text\|json\|sarif] [--strict] [--libs <path>,...]` | compiles and lints every `*.sutra.yaml` and `*.live.yaml`, each at its declared level; exit 0, 1 on errors (`--strict`: also on warnings), 2 usage |
| `rachana preview <file> [--data <dir>] [--port <n>]` | one screen in the browser; `rachana dev <dir>` serves a folder |
| `rachana build [--src <dir>] [--out site] [--libs <path>,...]` | lints, then writes a static site: one page per Sutra, the data, the runtime and the painter |
| `rachana studio [folder] ...` | [Rachana Studio](STUDIO.md) |
| `rachana lib <command> ...` | [library commands](LIBRARIES.md#4-rachana-lib) (needs a JVM) |
| `rachana serve --config <file> [--port n] [--jar file] [--check]` | [the server](SERVER.md) (needs a JVM) |

A built site needs no server of its own: the page answers the Data Protocol from `data/index.json` and the table files (schema,
filter, sort, columns, cursor paging; no change stream). Serve the folder with any static web server (a browser does not fetch from
`file://`).

### The Java launcher

`lib` and `upgrade` run the Java implementation (one implementation, `com.ash.rachana.cli.Main`). Point the CLI at it with
`RACHANA_JAR` (a runnable jar) or `RACHANA_CLASSPATH` (the module jars), and `RACHANA_JAVA` for another `java`:

```bash
cd java && ./mvnw clean package -DskipTests && cd ..
export RACHANA_CLASSPATH="$(ls java/rachana-{cli,tools,core,api}/target/rachana-*.jar | grep -v -e sources -e tests | tr '\n' ':')"
```

On Windows (PowerShell): `$env:RACHANA_CLASSPATH = (Get-ChildItem java\rachana-cli\target\*.jar, java\rachana-tools\target\*.jar,
java\rachana-core\target\*.jar, java\rachana-api\target\*.jar | ForEach-Object FullName) -join ';'`. The same commands run without Node:
`java -cp "$RACHANA_CLASSPATH" com.ash.rachana.cli.Main lib ...` or `... Main upgrade ...`.

## JavaScript: the facade and `<rachana-view>`

```js
import * as rachana from "./js/packages/rachana/dist/rachana.runtime.js";   // an ES module; works under a strict CSP
rachana.init("core", "[]");
JSON.parse(rachana.evaluate(JSON.stringify({ expr: "$.a * 2", given: { record: { a: 21 } } })));   // {value: 42, text: "42", ...}
JSON.parse(rachana.check(JSON.stringify({ sutra: yamlText })));                                     // {ok, problems}
```

`rachana.runtime.js` compiles, binds and diffs; `rachana.tools.js` adds the editing tools (Studio uses it). In a page:

```html
<script src="/vendor/drishti-elements/3.3.0/drishti-elements.js"></script>
<script type="module" src="/rachana/src/rachana_view.js"></script>
<rachana-view src="/sutras/sales.sutra.yaml" data="/data/v1" runtime="/rachana/dist/rachana.runtime.js" live></rachana-view>
```

Attributes: `src`, `data` (the Data Protocol base), `runtime`, `formats`, `today`, `allow` (other data origins), `live`, `form`
(`json`, `arrow` or `ndjson` rows), `libraries`. Properties: `sutra` (text instead of `src`), `record`, `controls`, `host` (the page's
handling of `notify` and `host` effects), `linkResolver`, `fetch`, `files`. Events: `rachana-view:ready`, `rachana-view:change`,
`rachana-view:error`, `rachana-view:write`. The painter is Drishti Elements, vendored under `js/vendor/drishti-elements/3.3.0/`.
The full list is the header of [rachana_view.js](../../js/packages/rachana/src/rachana_view.js).

## Python

The package `drishti-rachana` (folder `python/`, import `drishti_rachana`) holds no language logic of its own: it runs the JavaScript
bundle on V8.

```bash
python3 tools/js/build_js.py             # the bundles, also into the package's bundle folder
pip install -e 'python[v8,flask]'        # extras: v8, flask, fastapi, arrow, polars, conformance, test
python -m drishti_rachana lint examples/static-sales        # 3 file(s), 0 failing problem(s)
```

| Module | What it is |
|---|---|
| `drishti_rachana.engine` | `Rachana()`: `check(sutra)`, `evaluate(expr, record=...)`, `template`, `format`, `view`, `edit` |
| `drishti_rachana.adapters.flask`, `.fastapi` | `RachanaBlueprint` / `RachanaRouter`: mount a folder of Sutras, `@table(name, key=...)`, `@live(name)`; routes `/`, `/page/<name>`, `/sutras/...`, `/static/...`, `/data/...` |
| `drishti_rachana.server` | `DataHost`: a framework-neutral Data Protocol host (JSON, Arrow IPC, NDJSON, filtered and conflated SSE change streams, `can` enforced, masked columns) |
| `drishti_rachana.protocol` | the protocol's messages as dataclasses |
| `python -m drishti_rachana lint \| conformance run` | lint (`--format text\|json\|sarif`, `--strict`, `--libs`), the conformance runner |

```python
from drishti_rachana.engine import Rachana
r = Rachana()
r.check(open("examples/static-sales/overview.sutra.yaml").read())    # {'ok': True, 'problems': []}
r.evaluate("$.a * 2", record={"a": 21})                              # {'value': 42, 'type': 'number', 'text': '42', 'reads': ['$.a']}
```

A whole live app in about thirty lines: [examples/live-orders](../../examples/live-orders/README.md) (Flask and FastAPI), walked through
in the tutorial [A live board in Flask](../tutorials/PYTHON_APP.md).

## Java

The reactor `java/` (group `com.ash.rachana`, Java 21, named modules): `rachana-api` (the `Node` value interface), `rachana-core` (the
compiler, binder and ViewModel), `rachana-tools` (upgrade, generators, library tool), `rachana-data` (the Data Protocol host and client),
`rachana-live` (live tables), `rachana-server`, `rachana-cli`, plus the conformance runtime and the guards.

```java
Rachana rachana = Rachana.with(RachanaOptions.DEFAULTS, Profile.core());
Expr e = rachana.compiler().compile("upper(@.DESK)");
```

Building, testing, the IDE launchers and installing a release for another product: [java/README.md](../../java/README.md).
