# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""Light tests of the GenUI kit, on its own (no SAJHA, no JVM, no V8 bundle needed):  python -m pytest -q tools/sajha/tests"""
import importlib.util
import sys
from pathlib import Path

import pytest

KIT = Path(__file__).resolve().parents[1]
ROOT = KIT.parents[1]
for p in (KIT, KIT / "example", ROOT / "python" / "src"):
    if str(p) not in sys.path:
        sys.path.insert(0, str(p))

from rachana_genui import boards, checker, rows  # noqa: E402
from rachana_genui.settings import Settings, use  # noqa: E402


@pytest.fixture(autouse=True)
def kit(tmp_path):
    s = Settings.load({})
    s.libraries = [str(ROOT / "libraries")]
    s.host, s.port, s.public_url = "127.0.0.1", 0, ""
    s.require_engine = False
    s.data_tools = ["risk_data"]
    s.audit_file = str(tmp_path / "audit.jsonl")
    use(s)
    checker.reset_attempts()
    import risk_data
    rows.register_tool("risk_data", risk_data.tool)
    yield s
    rows.unregister_tool("risk_data")
    boards.stop_server()
    boards.STORE.boards.clear()


def load_tool(name):
    spec = importlib.util.spec_from_file_location(name, KIT / "tools" / f"{name}.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


@pytest.fixture
def tools():
    out = {}
    for n in ("search_fragments", "get_fragment", "check_sutra", "publish_ui"):
        mod = load_tool(n)
        cls = next(v for v in vars(mod).values() if isinstance(v, type) and getattr(v, "TOOL", "") == n)
        out[n] = cls({})
    return out
