# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
# Synthetic demo content: fictional customers, items and orders.
"""Order-book change generator for the live-orders example. Standard library only.

Reads orders_seed.csv as the starting book, then writes one keyed patch per line to stdout in the Rachana Data
Protocol v1 patch format (spec/1/data-protocol/patch.json, PROTOCOL.md 8.1), for the table ``orders`` (key: order_id):

    {"table": "orders", "add": [{...a whole new order...}]}
    {"table": "orders", "update": [{"order_id": "O-1007", "status": "Packed"}]}
    {"table": "orders", "remove": [["O-1003"]]}

A key in ``remove`` is a list of key values. Money is canonical decimal text with two places ("570.00").

    python3 gen_orders.py                       # until interrupted, about 4 patches a second
    python3 gen_orders.py --duration 2 --rate 10
    python3 gen_orders.py --seed 7 --duration 0 # duration 0: until interrupted

An adapter wraps each patch in a ``msg`` frame (stream, seq); this script writes only the patch.
"""
from __future__ import annotations

import argparse
import csv
import json
import random
import sys
import time
from datetime import datetime, timezone
from decimal import Decimal
from pathlib import Path

TABLE = "orders"
KEY = ["order_id"]
FLOW = {"Pending": "Packed", "Packed": "Shipped"}
ITEMS = [("Alder Desk", "420.00"), ("Birch Chair", "155.00"), ("Cedar Shelf", "95.00"), ("Dune Lamp", "60.00")]
REGIONS = ["North", "South", "East", "West", "Central"]


class OrderBook:
    """The current rows by key, and a step that changes them and returns the patch that describes the change."""

    def __init__(self, rows: list[dict], seed: int | None = None) -> None:
        self.rows = {r["order_id"]: dict(r) for r in rows}
        self.rng = random.Random(seed)
        self.next_id = 1 + max((int(k.split("-")[1]) for k in self.rows), default=1000)

    @classmethod
    def from_seed_file(cls, path: str | Path, seed: int | None = None) -> "OrderBook":
        with open(path, newline="", encoding="utf-8") as f:
            return cls(list(csv.DictReader(f)), seed)

    def snapshot(self) -> list[dict]:
        return list(self.rows.values())

    def step(self) -> dict:
        r = self.rng.random()
        pending = [k for k, v in self.rows.items() if v["status"] in FLOW]
        shipped = [k for k, v in self.rows.items() if v["status"] in ("Shipped", "Rejected")]
        if r < 0.30 or not pending:
            return self._add()
        if r < 0.85:
            key = self.rng.choice(pending)
            self.rows[key]["status"] = FLOW[self.rows[key]["status"]]
            return {"table": TABLE, "update": [{"order_id": key, "status": self.rows[key]["status"]}]}
        if r < 0.92:
            key = self.rng.choice(pending)
            self.rows[key]["status"] = "Rejected"
            return {"table": TABLE, "update": [{"order_id": key, "status": "Rejected"}]}
        if shipped:
            key = self.rng.choice(shipped)
            del self.rows[key]
            return {"table": TABLE, "remove": [[key]]}
        return self._add()

    def _add(self) -> dict:
        item, price = self.rng.choice(ITEMS)
        qty = self.rng.randint(1, 12)
        key = f"O-{self.next_id}"
        self.next_id += 1
        row = {
            "order_id": key,
            "ts": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
            "customer": f"C-{self.rng.randint(100, 140)}",
            "region": self.rng.choice(REGIONS),
            "item": item,
            "qty": qty,
            "price": price,
            "amount": f"{Decimal(price) * qty:.2f}",
            "status": "Pending",
        }
        self.rows[key] = row
        return {"table": TABLE, "add": [row]}


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--seed-file", default=str(Path(__file__).with_name("orders_seed.csv")))
    ap.add_argument("--rate", type=float, default=4.0, help="patches per second (default 4)")
    ap.add_argument("--duration", type=float, default=0.0, help="seconds to run; 0 = until interrupted")
    ap.add_argument("--seed", type=int, default=None, help="random seed, for repeatable changes")
    args = ap.parse_args(argv)
    book = OrderBook.from_seed_file(args.seed_file, args.seed)
    end = time.monotonic() + args.duration if args.duration > 0 else None
    pause = 1.0 / args.rate if args.rate > 0 else 0.0
    try:
        while end is None or time.monotonic() < end:
            sys.stdout.write(json.dumps(book.step(), separators=(",", ":")) + "\n")
            sys.stdout.flush()
            time.sleep(pause)
    except (KeyboardInterrupt, BrokenPipeError):
        pass
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
