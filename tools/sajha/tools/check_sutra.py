# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""check_sutra: the mandatory check of a composed Sutra: the kit's safety rules, then the compiler (RCH codes) with the libraries it
uses; problems with exact positions and the expected names; a check_token when it is clean. schemas/check_sutra.json."""
import sys
from pathlib import Path

_KIT = str(Path(__file__).resolve().parents[1])          # tools/sajha: the rachana_genui package beside this folder
if _KIT not in sys.path:
    sys.path.insert(0, _KIT)

from rachana_genui import checker  # noqa: E402
from rachana_genui.base import KitTool  # noqa: E402


class CheckSutraTool(KitTool):
    TOOL = "check_sutra"

    def run(self, arguments):
        return checker.check(arguments["sutra"], arguments.get("tables") or None)
