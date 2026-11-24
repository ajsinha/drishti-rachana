<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary. All rights reserved. -->
# Rachana and Elements in your own Flask or FastAPI application

You have a Python web application and you want Rachana screens in it: a Sutra file for the layout, a Python function for each table, and
the painter ([Drishti Elements](../../js/vendor/drishti-elements/3.3.1/) through `<rachana-view>`) in the browser. This guide shows
the few lines for FastAPI and for Flask, live tables, the choice between a static site and a data server, and how to run the
`live-orders` example both ways. Everything below was run against this repository unless a line says it was not.

Related: the short tutorial [A live board in Flask](../tutorials/PYTHON_APP.md), the wire format in [Data Protocol](DATA_PROTOCOL.md), live
tables in [Live tables](LIVE_TABLES.md), the standalone server in [Server](SERVER.md), the SDK list in [Command line and SDKs](CLI_AND_SDKS.md),
and an AI agent that writes the Sutras for your app in [GenUI with Rachana, Elements and MCP](GENUI.md).

## 1. How the pieces fit

```
browser                                        your Python app
 <rachana-view src=… data=…>  ── GET /dash/sutras/orders.sutra.yaml ──►  the Sutra file (a folder you name)
   painter = Drishti Elements ─ GET /dash/data/tables/orders/rows ────►  your @table function (list of dicts)
                              ◄─ SSE /dash/data/changes?table=orders ──  your @live generator ((key, row) changes)
                              ─  GET /dash/static/... ────────────────►  Elements, the element, the runtime bundle
```

Nothing is generated: the Sutra is data, the compiler runs in the browser (the runtime bundle), and the adapter only serves four
things: the Sutra text, the rows, the changes, and the assets. The adapter is `drishti_rachana.adapters.fastapi.RachanaRouter` (an
`APIRouter`) or `drishti_rachana.adapters.flask.RachanaBlueprint` (a `Blueprint`). Both expose the same routes under the prefix you give:

| Route (under the prefix) | What |
|---|---|
| `/` | a list of the Sutras in the folder |
| `/page/<name>` | a ready page: Elements plus one `<rachana-view>` for that Sutra |
| `/sutras/<name>.sutra.yaml` | the Sutra's text |
| `/data/...` | the Data Protocol v1: `tables`, `tables/<t>/rows`, queries, and `changes` (SSE, `GET` or `POST`) |
| `/static/...` | the assets of `<rachana-view>`: the element, the vendored Elements pack, the runtime bundle (and Arrow JS) |

The Python adapters serve the Sutra and the data **read-only**. They have no sign-in and no write-back; put your own authentication in
front (section 8). For sign-in, edits and approvals use the [Rachana server](SERVER.md).

## 2. What you need

| Need | Notes |
|---|---|
| Python | 3.13 recommended; the package accepts 3.10 or later |
| Flask 3 or later, or FastAPI 0.110 or later with uvicorn | extras `flask`, `fastapi` of the package; uvicorn you install yourself |
| the `drishti_rachana` package | **not on a public index**: install it from a checkout (below) or from a wheel you built |
| the runtime bundle `rachana.runtime.js` | the browser needs it to compile the Sutra. It is **built, not committed** (git-ignored): see "Build the runtime bundle" |
| Java | **not needed to run your app.** JDK 21 (and Node 20 for the checks) is needed once, to build the runtime bundle with `tools/js/build_js.py`; a wheel that carries the bundle needs no Java. The Java compiler and server are only for the [Rachana server](SERVER.md) |
| Node | not needed to run your app |

Optional extras of the package: `arrow` (Arrow IPC rows), `polars`, `v8` (compile and lint in Python: `python -m drishti_rachana lint`; it
runs the same bundle on V8 through `mini-racer`).

## 3. Install the package from a checkout

Drishti never makes a virtual environment for you: use the Python you choose. Below, a throwaway environment, then the editable
install (run from the repository root). The first line is the only one that is specific to uv; use `python3 -m venv` if you prefer.

```bash
uv venv ~/rachana-venv --python 3.13          # or: python3.13 -m venv ~/rachana-venv
uv pip install --python ~/rachana-venv/bin/python -e 'python[flask,fastapi]' uvicorn
# without uv:  ~/rachana-venv/bin/pip install -e 'python[flask,fastapi]' uvicorn
```

On Windows use `py -3.13 -m venv %USERPROFILE%\rachana-venv` and `%USERPROFILE%\rachana-venv\Scripts\pip`.

An editable install reads the assets from the checkout's `js/` folder. For a wheel (a deployment that is not a checkout): build the
bundle first, then `python -m build python/`; the wheel then carries the element, Elements 3.3.1, Arrow JS and the bundle as package
data (`pyproject.toml`: `force-include`). *The wheel build was not run for this guide.*

You can also run without installing: `PYTHONPATH=python/src python -m uvicorn ...` (this is what the checked-in IDE launchers do).

### Build the runtime bundle

```bash
python3 tools/js/build_js.py            # JDK 21 and Node 20; writes js/packages/rachana/dist/ and the package's bundle/ folder
```

Without it, everything below works except the painted page: `/dash/static/rachana/rachana.runtime.js` answers 404 and the board stays
empty. (In the run for this guide the data, Sutra, Elements and change routes were checked with `curl`; the bundle was not built, so the
painted page was not looked at in a browser. The painting check is `examples/live-orders/paint_check.py`, section 9.)

## 4. A minimal FastAPI app, step by step

A folder with a Sutra and two CSV files (copied from `examples/static-sales`):

```
myapp/
  app.py
  sutras/overview.sutra.yaml        # from examples/static-sales/overview.sutra.yaml
  sales.csv  targets.csv            # from examples/static-sales/data/
```

**Step 1, the Sutra.** It names tables, never rows (`data:` entries), and the host resolves each name:

```yaml
data:
  sales:   { table: sales, key: [region, product, month] }
  targets: { table: targets, key: [region, month] }
```

**Step 2, the app.** `RachanaRouter(sutras=<folder>)` serves every `*.sutra.yaml` below the folder. `@rachana.table(name, key=[...])`
registers a function that returns the rows: a list of dicts (or an Arrow, Polars or pandas table; sync or async). Then include the
router under a prefix. This is `app.py`:

```python
import csv
from pathlib import Path

from fastapi import FastAPI
from fastapi.responses import HTMLResponse
from drishti_rachana.adapters.fastapi import RachanaRouter

HERE = Path(__file__).parent

def read(name, numbers):
    with open(HERE / f"{name}.csv", newline="", encoding="utf-8") as f:
        return [{k: (float(v) if k in numbers else v) for k, v in row.items()} for row in csv.DictReader(f)]

app = FastAPI()
rachana = RachanaRouter(sutras=HERE / "sutras")         # every *.sutra.yaml in the folder

@rachana.table("sales", key=["region", "product", "month"])
def sales():
    return read("sales", {"units", "revenue"})          # a list of dicts

@rachana.table("targets", key=["region", "month"])
def targets():
    return read("targets", {"target"})

app.include_router(rachana, prefix="/dash")
```

That is the whole backend. `uvicorn app:app` now serves `/dash/` (the list of Sutras) and `/dash/page/overview` (the ready page).

**Step 3, your own page.** To put the screen inside a page of your app, give the page Elements, the element module and one
`<rachana-view>`. The asset URLs are the adapter's, under your prefix. This route belongs in the same `app.py`:

```python
@app.get("/", response_class=HTMLResponse)
def home():
    el = "/dash/static/vendor/drishti-elements/3.3.1"
    return f"""<!doctype html><meta charset=utf-8><title>My app</title>
<link rel=stylesheet href="{el}/drishti-elements.css"><script src="{el}/drishti-elements.js"></script>
<script type=module>import "/dash/static/rachana/src/rachana_view.js";</script>
<h1>My app</h1>
<rachana-view src="/dash/sutras/overview.sutra.yaml" data="/dash/data"
              runtime="/dash/static/rachana/rachana.runtime.js"></rachana-view>"""
```

The attributes of `<rachana-view>` are `src` (the Sutra's address), `data` (the Data Protocol root), `runtime` (the bundle), `live` (follow
changes) and, not used here, `formats`, `today`, `allow`, `form`, `libraries`. In a Jinja or Django template the same four lines go
in the template. The easiest start is the adapter's own page, `/dash/page/overview`; it writes exactly this markup.

**Step 4, run it.**

```bash
cd myapp
~/rachana-venv/bin/python -m uvicorn app:app --port 8000        # open http://127.0.0.1:8000/  and  /dash/
```

Checked here for this app (with `curl`, on a free port): `/` 200, the Sutra 200 (2104 bytes), `/dash/data/tables` lists `sales` and
`targets`, `rows?limit=1&shape=objects` answers a row with numbers as numbers, Elements and `rachana_view.js` 200, and the runtime
bundle 404 until it is built (section 3).

## 5. The same in Flask

`RachanaBlueprint(sutras=..., name="rachana")` has the same decorators. Only the framework lines change:

```python
from flask import Flask
from drishti_rachana.adapters.flask import RachanaBlueprint

app = Flask(__name__)
rachana = RachanaBlueprint(sutras=HERE / "sutras")

@rachana.table("sales", key=["region", "product", "month"])
def sales():
    return read("sales", {"units", "revenue"})

app.register_blueprint(rachana, url_prefix="/dash")

if __name__ == "__main__":
    app.run(threaded=True)          # threaded: each live reader holds one open response
```

Serve the page the same way (a Flask route returning the HTML of step 3). With a production server give it enough threads or
workers for the open change streams: one per open browser tab that follows a live table (a general WSGI point, not measured here).

## 6. Live tables: pushing updates

Declare the table live in the Sutra (`live: true`, with its `key`) and add a generator with `@rachana.live(name)`. It yields
`(key, row)`; a key is a tuple of the key columns' values; a row of `None` removes the row. The adapter numbers the changes and streams
them as Data Protocol patches over SSE, and the painter changes the affected tiles and rows without a reload. Copied from the real example:

```python
@rachana.table("orders", key=["order_id"])
async def orders():
    return book.snapshot()                         # the starting rows

@rachana.live("orders")                            # keyed changes, streamed over SSE
async def orders_changes():
    while True:
        await asyncio.sleep(0.25)
        patch = book.step()                        # your source: a queue, a poll, a message consumer
        for row in patch.get("add", []) + patch.get("update", []):
            yield (row["order_id"],), row
        for key in patch.get("remove", []):
            yield tuple(key), None
```

The Flask version is the same with `def`, `time.sleep` and `Blueprint` decorators (`examples/live-orders/flask_app.py`). `@live`
accepts a sync or an async generator in either framework. Two points from the adapter code: a table function that is `async` is run
with its own `asyncio.run` for each snapshot, so do not rely on resources bound to your application's event loop inside it; and
an update row may carry only the changed columns (the example's updates carry `order_id` and `status`). The shape of patches (`add`,
`update`, `remove`, `reset`; epochs and sequence numbers) is in [Data Protocol](DATA_PROTOCOL.md).

## 7. Static mode or a data server

| | Static site | Python adapter in your app | Rachana server |
|---|---|---|---|
| Data | CSV, JSON or Arrow files built into the site | your functions: database, queue, API, files | files, JDBC, REST, in a config file |
| Live changes | no change stream | yes: `@live` over SSE | yes |
| Backend | none: any static web server | your Flask or FastAPI process | the Java server (JDK 21) |
| Sign-in, edits, approvals | no | no: yours to add | yes |
| Make it | `rachana build` (Node CLI) | this guide | [Server](SERVER.md) |

Static mode: `cd examples/static-sales && rachana build` writes `site/` (one page per Sutra; the page answers the Data Protocol itself from
the table files). Details in [static-sales](../../examples/static-sales/README.md) and
[Command line and SDKs](CLI_AND_SDKS.md). The static CLI and the server were **not** run for this guide. Both need the compiler bundles
(and the server a JDK 21 and its jar).

Choose the adapter when the data already lives in your Python process or behind your Python code. Choose the server when you need
sign-in, row rules, write-back or approvals without writing them.

## 8. Putting your own sign-in in front

The adapters add no authentication. Your framework does, in its own way (framework features, not Rachana APIs, and not exercised here):
in FastAPI pass `dependencies=[Depends(your_check)]` to `app.include_router(rachana, prefix="/dash", ...)`; in Flask use
`rachana.before_request(your_check)` on the blueprint. The data routes and the assets are all under the prefix, so one check covers all of
them. Do not bind a data-bearing app to a public address without it.

## 9. Run the `live-orders` example (both ways)

`examples/live-orders` is the same board in both frameworks: `orders_seed.csv` (40 synthetic orders), `gen_orders.py` (the data source:
`OrderBook`, standard library only), `orders.sutra.yaml`, `flask_app.py`, `fastapi_app.py`. From the repository root, with the environment
of section 3 and the bundle of section 3 built:

```bash
cd examples/live-orders
~/rachana-venv/bin/python flask_app.py                     # Flask:   http://127.0.0.1:5000/dash/
~/rachana-venv/bin/uvicorn fastapi_app:app                 # FastAPI: http://127.0.0.1:8000/dash/   (add --port <n> if 8000 is taken)
```

Run here: both commands started with an editable install in a throwaway environment, `/dash/` answered 200 on both, the
assets answered 200, and `/dash/data/changes?table=orders` streamed `rdp.orders` messages (`add`, `update`, `remove`). Without the
install, set `PYTHONPATH=<repo>/python/src` and use `python -m uvicorn`.

**The IDE launchers.** In PyCharm (open the repository root, choose a Python interpreter that has the extras): the run
configurations **Rachana: run live-orders (FastAPI)** and **Rachana: run live-orders (Flask)**, in the group **PyCharm-rachana-run**
(files `run-configs/Rachana run live-orders (FastAPI).run.xml` and `... (Flask).run.xml`). They set `PYTHONPATH` to `python/src`,
the working directory to `examples/live-orders`, and run `uvicorn fastapi_app:app --port 8000` or the Flask script; open
`http://127.0.0.1:8000/dash/` (FastAPI) or `http://127.0.0.1:5000/dash/` (Flask). The interpreter needs `fastapi` and `uvicorn`, or
`flask`.

**What you should see** (from `orders.sutra.yaml` and the example's own painting check): open `/dash/` and choose **orders**. Four tiles
(Open orders, Open value, Shipped, Rejected), a bar chart of order value by status, and a grid of the order book. Every quarter second
or so a status changes, an order arrives or a finished one leaves, and the tiles, bars and grid change in place with no reload. The
orders and statuses are fictional. The same table over the command line:

```text
$ curl -s http://127.0.0.1:8000/dash/data/tables
{"tables": [{"name": "orders", "key": ["order_id"], "can": ["page", "sort", "filter", "offset", "count", "group", "changes", "replay"]}]}
$ curl -s -N "http://127.0.0.1:8000/dash/data/changes?table=orders"
id: 008b:1
event: msg
data: {"t": "msg", "topic": "rdp.orders", ..., "seq": 1, "data": {"table": "orders", "update": [{"order_id": "O-1013", "status": "Shipped"}]}}
```

The epoch (`008b`) differs on each start. Stop with Ctrl+C (or, from another terminal, by the process id). To check that the board paints in Chromium,
changes while open and logs no console error: `uv run --with flask --with playwright==1.64.0 python examples/live-orders/paint_check.py`
(needs the runtime bundle; *not run for this guide*).

## 10. Troubleshooting

| You see | Cause and fix |
|---|---|
| the page is blank; the browser console shows 404 for `rachana.runtime.js` | the runtime bundle is not built: `python3 tools/js/build_js.py` (JDK 21), then restart; a wheel must be built after it |
| `ModuleNotFoundError: drishti_rachana` | not installed in this interpreter: `pip install -e 'python[flask,fastapi]'` from the repository root, or set `PYTHONPATH` to `python/src` |
| `ModuleNotFoundError: flask` or `fastapi` | the extra is missing: install it (`flask`, `fastapi` and `uvicorn`) into the interpreter the launcher uses |
| `/dash/` lists no Sutra | `sutras=` points at a folder without `*.sutra.yaml` files below it, or the file name does not end in `.sutra.yaml`; the page name is the file name without the suffix |
| `/dash/data/tables` lacks your table | the `@rachana.table("name")` name must equal the `table:` name in the Sutra's `data:` |
| the board shows data but never changes | the table has no `key=` or no `@live`, or the Sutra's `data:` entry lacks `live: true`; check `/dash/data/changes?table=<name>` with `curl -N` |
| Flask: changes stall with several tabs open | run with `threaded=True` (or enough workers): each reader holds one stream |
| behind a reverse proxy the changes arrive in bursts | disable response buffering for the `/data/changes` path (a general proxy setting) |
| a sum shows 0 or the wrong figure | the function returned numbers as text; convert them (the CSV reader gives strings, as `read()` above does) |
| `406` on a rows request for Arrow | `pyarrow` is not installed, or the table does not declare `arrow` in its schema |
| port already in use | pick another with `--port` (uvicorn), `app.run(port=...)` (Flask) |
| a Sutra problem you cannot read | `python -m drishti_rachana lint <folder>` (extra `v8`) or `rachana lint`; the problem codes are in [Language](LANGUAGE.md) |
