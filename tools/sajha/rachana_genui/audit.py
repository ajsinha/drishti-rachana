# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""Audit hooks: every search, get, check, publish and board view is one event (who, what, the Sutra's hash, the tables with their
row counts and sources), never the rows themselves. Inside SAJHA the tool calls are audited by SAJHA as well (its tool-call audit
and policy); these events add what only the kit knows (check verdicts, boards, viewers).

    from rachana_genui import audit
    audit.add_hook(lambda event: my_siem.send(event))
"""
from __future__ import annotations

import json
import logging
import threading
import time
from typing import Any, Callable

from .settings import settings

log = logging.getLogger("rachana_genui.audit")
_hooks: list[Callable[[dict], None]] = []
_lock = threading.Lock()


def caller() -> str:
    """The user the current call runs for: SAJHA's caller when the kit runs inside SAJHA, else 'local'."""
    try:
        from sajha.observability.caller import current      # SAJHA's per-call identity (read-only use)
        return str(current().user_id or "anonymous")
    except Exception:  # noqa: BLE001 - outside SAJHA
        return "local"


def add_hook(fn: Callable[[dict], None]) -> None:
    with _lock:
        _hooks.append(fn)


def remove_hook(fn: Callable[[dict], None]) -> None:
    with _lock:
        if fn in _hooks:
            _hooks.remove(fn)


def event(kind: str, **fields: Any) -> dict:
    e = {"at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()), "event": f"rachana.genui.{kind}", "caller": caller(), **fields}
    log.info(json.dumps(e, sort_keys=True, default=str))
    path = settings().audit_file
    if path:
        with _lock, open(path, "a", encoding="utf-8") as f:
            f.write(json.dumps(e, sort_keys=True, default=str) + "\n")
    for h in list(_hooks):
        try:
            h(e)
        except Exception:  # noqa: BLE001 - a hook never breaks a tool call
            log.exception("audit hook failed")
    return e
