# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""The IDE launchers follow the platform's launcher standard (tools/launchers/check_launchers.py)."""
import importlib.util
from pathlib import Path


def test_launchers_follow_the_standard():
    p = Path(__file__).resolve().parents[2] / "tools" / "launchers" / "check_launchers.py"
    spec = importlib.util.spec_from_file_location("check_launchers", p)
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    assert m.check() == []
