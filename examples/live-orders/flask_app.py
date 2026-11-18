# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
# Synthetic demo content: fictional customers, items and orders.
"""The live orders board under Flask. PLANNED API; runs once B5 lands. Not runnable today.

    pip install drishti-rachana[flask]     # planned
    python flask_app.py                    # planned: then open http://127.0.0.1:5000/dash/

The names below follow docs/MODES.md 3 and the stubs in python/src/drishti_rachana/adapters/flask.py; the decorator
names (table, live) and the (key, row) change form may still change in B5. A change of (key, None) is assumed to
remove the row.
"""
import time
from pathlib import Path

from flask import Flask
from drishti_rachana.adapters.flask import RachanaBlueprint   # planned API

from gen_orders import OrderBook

HERE = Path(__file__).parent
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
            yield tuple(key), None


app.register_blueprint(rachana, url_prefix="/dash")

if __name__ == "__main__":
    app.run(threaded=True)
