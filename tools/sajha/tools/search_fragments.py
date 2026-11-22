# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""search_fragments: find fragments by what the user asked (text) and by the shape of the data in hand (column names and types,
never rows). SAJHA's plugin loader registers the class below; schemas/search_fragments.json is its definition."""
import sys
from pathlib import Path

_KIT = str(Path(__file__).resolve().parents[1])          # tools/sajha: the rachana_genui package beside this folder
if _KIT not in sys.path:
    sys.path.insert(0, _KIT)

from rachana_genui import audit, catalogue  # noqa: E402
from rachana_genui.base import KitTool  # noqa: E402
from rachana_genui.settings import settings  # noqa: E402


class SearchFragmentsTool(KitTool):
    TOOL = "search_fragments"

    def run(self, arguments):
        s = settings()
        cat = catalogue.load(s.libraries)
        limit = min(int(arguments.get("limit") or s.search_limit), 25)
        results = cat.search(arguments.get("query", ""), tables=arguments.get("tables") or None,
                             category=arguments.get("category", ""), library=arguments.get("library", ""), limit=limit,
                             allowed=s.allowed_libraries)
        audit.event("search", query=arguments.get("query", ""), tables=sorted((arguments.get("tables") or {}).keys()),
                    results=[r["id"] for r in results])
        return {"results": results, "count": len(results),
                "categories": sorted({e.doc.get("category", "") for e in cat.entries.values()} - {""}),
                "hint": "get_fragment gives a fragment's whole interface; compose a Sutra with the 'use' of each result; then "
                        "check_sutra"}
