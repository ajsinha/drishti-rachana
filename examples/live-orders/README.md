<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary. All rights reserved. -->
# live-orders (deliverable D2)

Demo inputs for the Python app mode (docs/MODES.md 3): the same live board under Flask and under FastAPI. **Synthetic
data**: fictional customers and items. The CSV has no header comment; it is covered by the repository licence.

| File | What |
|---|---|
| `orders_seed.csv` | 40 starting orders: `order_id, ts, customer, region, item, qty, price, amount, status`; key `order_id` |
| `gen_orders.py` | standard library only; reads the seed, then writes one keyed patch per line to stdout in Data Protocol v1 patch format (`spec/1/data-protocol/patch.json`): `add` new orders, `update` a status, `remove` a finished order (a key is a list) |
| `orders.sutra.yaml` | the board: four KPI tiles, a bar chart of value by status, a grid of the whole book (`data:` entry `orders`, `key: [order_id]`, `live: true`) |
| `flask_app.py`, `fastapi_app.py` | the adapters in use (`drishti_rachana.adapters`), about thirty lines each; the data endpoints run today, the page needs the painter (`<rachana-view>`, branch g2/data) |

```bash
python3 gen_orders.py --duration 5 --rate 4 --seed 7     # works today: patches on stdout
```

`python flask_app.py` or `uvicorn fastapi_app:app`, open `/dash/`; orders arrive, statuses move and the
tiles and grid change on screen without a reload. `gen_orders.OrderBook` is shared by both apps as the data source: the
adapters turn each patch into `(key, row)` changes (a `None` row removes) and publish them as `rdp.orders` messages.
