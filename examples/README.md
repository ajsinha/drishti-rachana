<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary. All rights reserved. -->
# Examples

Three runnable examples, all with synthetic data (fictional regions, products, customers and figures). Each folder has its own README
with the prerequisites, the exact commands for Windows (`py -3.13`) and Linux or macOS, what you should see, how to stop it and a
troubleshooting table.

| Example | What it shows | Needs |
|---|---|---|
| [live-orders](live-orders/README.md) | a live order board (tiles, bars, grid) updating without a reload, under Flask and under FastAPI | Python 3.13, `pip install -r examples/live-orders/requirements.txt` |
| [static-sales](static-sales/README.md) | CSV files and Sutras in, a three-page dashboard out (a dev server, or a static site) | Node.js 20 |
| [server-demo](server-demo/README.md) | the standalone Rachana server: a Sutra page over file tables, with sign-in | Java 21 (builds the jar once) |

Tutorials that use them: [Your first dashboard](../docs/TUTORIAL.md#part-1-your-first-dashboard), [A live board in
Flask](../docs/TUTORIAL.md#part-4-a-live-board-in-flask); the guide for your own app: [IN_YOUR_APP.md](../docs/IN_YOUR_APP.md).

`python3 -I examples/check_examples.py` checks the inputs of all three against the specification (needs PyYAML and jsonschema).
`python\tests\test_examples.py` (pytest) starts both live-orders apps in process and checks the page, the runtime bundle and the data.
