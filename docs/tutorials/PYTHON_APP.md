<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary. All rights reserved. -->
# Tutorial: a live board in Flask

You will serve a Sutra and a live table from a Flask app in about thirty lines: the board's tiles and grid change on screen as orders
arrive, with no front-end code. The same app under FastAPI is beside it. Guides: [CLI and SDKs](../guides/CLI_AND_SDKS.md#python),
[Data Protocol](../guides/DATA_PROTOCOL.md).

## Prepare your environment

Python 3.10 or later, JDK 21 and Node 20 (only to build the bundle once). From the repository root:

```bash
python3 tools/js/build_js.py                     # the compiler bundle, also copied into the Python package
python3 -m venv venv && . venv/bin/activate      # Windows: venv\Scripts\activate
pip install -e 'python[v8,flask]'                # add fastapi for the FastAPI app
```

With uv instead of pip: `cd python && uv run --extra flask python ../examples/live-orders/flask_app.py`. In PyCharm: open the folder
`python/` and use the checked-in run configurations (`run-configs/`, the `Rachana: run live-orders` launchers).

## Step 1: the Sutra

[examples/live-orders/orders.sutra.yaml](../../examples/live-orders/orders.sutra.yaml) reads one table, `orders`, declared live with
its key:

```yaml
data:
  orders: { table: orders, key: [order_id], live: true }
```

Four tiles, a bar chart of value by status and a grid of the whole book are computed from it. Check it:

```text
$ python -m drishti_rachana lint examples/live-orders
1 file(s), 0 failing problem(s)
```

## Step 2: the app

[examples/live-orders/flask_app.py](../../examples/live-orders/flask_app.py), the part that matters:

```python
from flask import Flask
from drishti_rachana.adapters.flask import RachanaBlueprint
from gen_orders import OrderBook

book = OrderBook.from_seed_file(HERE / "orders_seed.csv")
app = Flask(__name__)
rachana = RachanaBlueprint(sutras=HERE)            # serves orders.sutra.yaml

@rachana.table("orders", key=["order_id"])
def orders():
    return book.snapshot()                         # list of dicts

@rachana.live("orders")                            # keyed changes, streamed over SSE
def orders_changes():
    while True:
        time.sleep(0.25)
        patch = book.step()
        for row in patch.get("add", []) + patch.get("update", []):
            yield (row["order_id"],), row
        for key in patch.get("remove", []):
            yield tuple(key), None                 # None removes the row

app.register_blueprint(rachana, url_prefix="/dash")
```

`@table` gives the rows; `@live` yields `(key, row)` changes, which the adapter numbers and streams as Data Protocol patches.
`OrderBook` (in `gen_orders.py`) stands in for your own source; it is synthetic.

## Step 3: run it

```bash
cd examples/live-orders && python flask_app.py
```

**In the UI:** open `http://127.0.0.1:5000/dash/`, then **orders**: orders arrive, statuses move, and the tiles and grid change
without a reload.

**From the command line:** the same table over the Data Protocol:

```text
$ curl -s http://127.0.0.1:5000/dash/data/tables
{"tables": [{"name": "orders", "key": ["order_id"], "can": ["page", "sort", "filter", "offset", "count", "group", "changes", "replay"]}]}
$ curl -s "http://127.0.0.1:5000/dash/data/tables/orders/rows?limit=2&shape=objects"
{"table": "orders", "version": {"epoch": "1094", "seq": 0}, "rows": [{"order_id": "O-1001", ..., "status": "Pending"}, ...]}
$ curl -s -N "http://127.0.0.1:5000/dash/data/changes?table=orders"
id: 1094:1
event: msg
data: {"t": "msg", "topic": "rdp.orders", ..., "seq": 1, "data": {"table": "orders", "update": [{"order_id": "O-1016", "status": "Packed"}]}}
```

The epoch differs on each start. Stop with Ctrl+C.

## Step 4: the same under FastAPI

```bash
pip install -e 'python[fastapi]'
cd examples/live-orders && uvicorn fastapi_app:app
```

`RachanaRouter` takes the same `@table` and `@live` decorators; open `http://127.0.0.1:8000/dash/`.

## Next

- Check Sutras in CI: `python -m drishti_rachana lint --format sarif`.
- A standalone server with sign-in, databases and editing: [Edit a live table with approvals](LIVE_EDITS.md).
