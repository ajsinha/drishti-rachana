<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary. All rights reserved. -->
# drishti-rachana (Python)

Drishti Rachana for Python: the Data Protocol host (JSON, Arrow, NDJSON, live changes), the conformance runner and the Flask and
FastAPI adapters that serve `<rachana-view>`.

Install it from the repository checkout (it is not on a public index), into the Python you choose, from the repository root
(`drishti-platform/drishti-rachana`):

```
python3 -m pip install -e "python[flask,fastapi]" uvicorn          # Linux, macOS
py -3.13 -m pip install -e "python[flask,fastapi]" uvicorn         # Windows
```

Run the examples: [examples/README.md](../examples/README.md). Use in your own app: [docs/IN_YOUR_APP.md](../docs/IN_YOUR_APP.md).
