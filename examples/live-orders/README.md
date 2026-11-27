<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary. All rights reserved. -->
# live-orders: a live board in Flask or FastAPI

A live order board in about thirty lines of Python: four tiles (open orders, open value, shipped, rejected), a bar chart of value by
status and a grid of the whole book. Orders arrive, statuses move and finished orders leave about four times a second, and the page
changes in place with no reload. The same board runs under Flask (`flask_app.py`) and under FastAPI (`fastapi_app.py`). All data is
synthetic (fictional customers and items). Background: [Tutorial, part 4](../../docs/TUTORIAL.md#part-4-a-live-board-in-flask) and
[In your app](../../docs/IN_YOUR_APP.md#rachana-and-elements-in-your-own-flask-or-fastapi-application).

## Prerequisites

- Python 3.13 (3.10 or later works). Rachana never creates a virtual environment or installs anything for you: you install into the
  Python you choose.
- Packages: the `drishti_rachana` package from this repository (it is not on a public index) and Flask, or FastAPI with uvicorn.
- No Java and no Node: the browser runtime bundle is committed (`python/src/drishti_rachana/bundle/rachana.runtime.js`).
- A modern browser (Chrome, Edge, Firefox).

## Install (once)

From `drishti-platform\drishti-rachana` (the repository root).

Windows (PowerShell or cmd):

```
cd drishti-platform\drishti-rachana
py -3.13 -m pip install -r examples\live-orders\requirements.txt
```

Linux and macOS:

```
cd drishti-platform/drishti-rachana
python3.13 -m pip install -r examples/live-orders/requirements.txt
```

`requirements.txt` installs the package from `python/` (editable) plus Flask, FastAPI and uvicorn. Use another Python's `pip` to
install into that Python instead.

## Run with Flask

Windows:

```
cd drishti-platform\drishti-rachana\examples\live-orders
py -3.13 flask_app.py
```

Linux and macOS:

```
cd drishti-platform/drishti-rachana/examples/live-orders
python3.13 flask_app.py
```

Open <http://127.0.0.1:5000/dash/> (a list of pages) and click **orders**, or go straight to <http://127.0.0.1:5000/dash/page/orders>.

## Run with FastAPI

Windows:

```
cd drishti-platform\drishti-rachana\examples\live-orders
py -3.13 -m uvicorn fastapi_app:app
```

Linux and macOS:

```
cd drishti-platform/drishti-rachana/examples/live-orders
python3.13 -m uvicorn fastapi_app:app
```

Open <http://127.0.0.1:8000/dash/page/orders>. Run it from this folder (uvicorn imports `fastapi_app` from the current folder).

## What you should see

Within a second the board paints: four tiles with numbers, a bar chart with one bar per status, and a grid with about 40 orders. Then
it moves on its own: the tile numbers change, bars grow and shrink, grid rows change status, appear and disappear. Nothing needs a
reload. An empty page, or a page with a message "Rachana runtime bundle missing", means a problem from the table below.

Without a browser, the same data over the command line:

```
curl http://127.0.0.1:5000/dash/data/tables
curl -N "http://127.0.0.1:5000/dash/data/changes?table=orders"      # a stream of changes; Ctrl+C ends it
python gen_orders.py --duration 5 --rate 4 --seed 7                 # the data source alone, no app needed
```

## Stop

Press Ctrl+C in the window that runs it. From another window, end the process by its id.

## Check it (optional)

`py -3.13 -m pytest python\tests\test_examples.py` from the repository root starts both apps in process and checks the page, the
bundle and the data (needs `pytest` and `httpx`). `paint_check.py` opens the Flask board in Chromium and fails when nothing paints,
nothing changes or the console shows an error (needs `pip install playwright` and `playwright install chromium`).

## Troubleshooting

| You see | Cause and fix |
|---|---|
| `ModuleNotFoundError: drishti_rachana` | the package is not installed in this Python: run the install step above with the same `py -3.13` you run the app with |
| `ModuleNotFoundError: flask` (or `fastapi`, `uvicorn`) | same: `py -3.13 -m pip install -r examples\live-orders\requirements.txt` |
| `No module named fastapi_app` or `gen_orders` | you are not in `examples\live-orders`: `cd` there first |
| a page "Rachana runtime bundle missing", or a blank page and 404 for `rachana.runtime.js` in the browser console | the file `python\src\drishti_rachana\bundle\rachana.runtime.js` is missing from your copy: unzip the platform again (or `git checkout` it); to rebuild it: `python3 tools/js/build_js.py` (JDK 21, Node 20) |
| `Address already in use` / `only one usage of each socket address` | port 5000 (Flask) or 8000 (FastAPI) is taken: Flask: `py -3.13 flask_app.py 5001`; uvicorn: add `--port 8001` |
| the board paints but never changes | the page shows an old copy: reload with Ctrl+F5; check `curl -N "http://127.0.0.1:5000/dash/data/changes?table=orders"` prints events |
| `pip` says "Readme file does not exist" or "no matching distribution" | you run `pip` from a folder other than `drishti-rachana`, or `python/README.md` is missing: run it from the repository root |
