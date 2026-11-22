# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""publish_ui: publish a checked Sutra and its tables as a live board. The rows are fetched here (from a SAJHA tool, as the caller,
or left to the page's Data Protocol host), never passed through the model; the board page (spa/) receives {sutra, tables} over SSE
and paints it with <rachana-view>. Publishing again with the same board id updates the open pages. schemas/publish_ui.json."""
import sys
from pathlib import Path

_KIT = str(Path(__file__).resolve().parents[1])          # tools/sajha: the rachana_genui package beside this folder
if _KIT not in sys.path:
    sys.path.insert(0, _KIT)

from rachana_genui import audit, boards, catalogue, checker, rows  # noqa: E402
from rachana_genui.base import KitTool  # noqa: E402
from rachana_genui.settings import settings  # noqa: E402


class PublishUiTool(KitTool):
    TOOL = "publish_ui"

    def run(self, arguments):
        s = settings()
        sutra = arguments["sutra"]
        if not checker.token_ok(sutra, arguments.get("check_token", "")):
            return {"published": False, "error": {"code": "RGK-170", "message": "this exact Sutra has no passing check: call "
                    "check_sutra and pass its check_token (any change to the text needs a new check)"}}
        try:
            tables, protocol = rows.resolve_all(arguments.get("tables") or {})
        except rows.SourceError as e:
            return {"published": False, "error": {"code": e.code, "message": str(e), "table": e.table}}
        cat = catalogue.load(s.libraries)
        # the check again, now against the columns the tables really have (a tool's answer may differ from what was described)
        described = checker.describe_tables({n: t for n, t in tables.items() if "rows" in t})
        verdict = checker.check(sutra, described or None, cat, count=False)
        if not verdict["ok"]:
            return {"published": False, "error": {"code": "RGK-171", "message": "the Sutra does not fit the tables as fetched"},
                    "problems": verdict["problems"], "summary": verdict["summary"]}
        url = boards.ensure_server(lambda: catalogue.load(settings().libraries))
        try:
            b = boards.STORE.publish(sutra, tables, protocol, verdict["plan"]["libraries"], arguments.get("title", ""),
                                     arguments.get("board"))
        except (KeyError, PermissionError) as e:
            return {"published": False, "error": {"code": "RGK-172", "message": str(e).strip("'\"")}}
        counts = {n: (len(t["rows"]) if "rows" in t else None) for n, t in tables.items()}
        audit.event("publish", board=b.id, version=b.version, sutra_hash=checker.sutra_hash(sutra),
                    tables={n: {"rows": counts[n], "source": t["source"]} for n, t in tables.items()})
        return {"published": True, "board": b.id, "version": b.version, "url": f"{url}/b/{b.id}",
                "expires_minutes": s.board_ttl_minutes, "tables": {n: {"rows": c} for n, c in counts.items()},
                "fragments": verdict["plan"]["fragments"],
                "summary": f"Board {b.id} v{b.version}: {len(verdict['plan']['fragments'])} fragments over "
                           + ", ".join(f"{n} ({c if c is not None else 'Data Protocol'} rows)" for n, c in counts.items())}
