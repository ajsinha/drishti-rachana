# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""get_fragment: one fragment's whole interface (doc, params with their columns, slots, events), its source and its example Sutra
(synthetic data), so an agent can write a correct use. schemas/get_fragment.json is its definition."""
import sys
from pathlib import Path

_KIT = str(Path(__file__).resolve().parents[1])          # tools/sajha: the rachana_genui package beside this folder
if _KIT not in sys.path:
    sys.path.insert(0, _KIT)

from rachana_genui import audit, catalogue  # noqa: E402
from rachana_genui.base import KitTool  # noqa: E402
from rachana_genui.settings import settings  # noqa: E402


class GetFragmentTool(KitTool):
    TOOL = "get_fragment"

    def run(self, arguments):
        s = settings()
        cat = catalogue.load(s.libraries)
        ref = str(arguments["id"])
        e = cat.find(ref)
        if e is None or (s.allowed_libraries and e.library not in s.allowed_libraries):
            return {"found": False, "id": ref, "nearest": cat.nearest(ref)}
        files = cat.libraries[e.library]["files"]
        out = {"found": True, **e.full_json(), "use": cat.use_of(e, cat.fit(e, arguments["tables"]) if arguments.get("tables") else None),
               "libraries_line": f"{e.library}@{e.major}"}
        if arguments.get("include_source", True):
            out["source"] = files.get(e.file, "")
        example = files.get(f"examples/{e.name}.sutra.yaml")
        if example and arguments.get("include_example", True):
            out["example_sutra"] = example
        audit.event("get", fragment=e.id)
        return out
