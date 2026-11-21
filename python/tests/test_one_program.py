# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""One program is named `rachana`: the Node CLI. The Python package declares no console script (it runs as python -m drishti_rachana)."""
import pathlib


def test_python_installs_no_program_named_rachana():
    text = (pathlib.Path(__file__).resolve().parents[1] / "pyproject.toml").read_text()
    assert "[project.scripts]" not in text
