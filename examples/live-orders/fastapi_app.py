# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
# Synthetic demo content: fictional customers, items and orders.
"""The live orders board under FastAPI (drishti_rachana.adapters.fastapi).

    pip install -r requirements.txt        (from the repository root: examples/live-orders/requirements.txt; README.md has the steps)
    python -m uvicorn fastapi_app:app                         # then open http://127.0.0.1:8000/dash/

Same board, same Sutra, same data and the same changes as flask_app.py. The decorator is ``live``. A change of (key, None) removes the row.
"""
import asyncio
from pathlib import Path

from fastapi import FastAPI
from drishti_rachana.adapters.fastapi import RachanaRouter 

from gen_orders import OrderBook

HERE = Path(__file__).parent
book = OrderBook.from_seed_file(HERE / "orders_seed.csv")

app = FastAPI()
rachana = RachanaRouter(sutras=HERE)               # serves orders.sutra.yaml


@rachana.table("orders", key=["order_id"])
async def orders():
    return book.snapshot()


@rachana.live("orders")                            # keyed changes, streamed over SSE
async def orders_changes():
    while True:
        await asyncio.sleep(0.25)
        patch = book.step()
        for row in patch.get("add", []) + patch.get("update", []):
            yield (row["order_id"],), row
        for key in patch.get("remove", []):
            yield tuple(key), None


app.include_router(rachana, prefix="/dash")
