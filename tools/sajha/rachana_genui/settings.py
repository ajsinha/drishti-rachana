# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""The kit's settings: one place, read from the environment (a deployment sets them beside SAJHA's own), optionally from one JSON
file named by ``RACHANA_GENUI_CONFIG``. Every limit has a default that is safe for a shared server. Nothing here is a secret except
``RACHANA_GENUI_SECRET`` (the key of the check tokens), which is generated per process when not given."""
from __future__ import annotations

import json
import os
import secrets
from dataclasses import dataclass, field, fields
from pathlib import Path
from typing import Optional

KIT = Path(__file__).resolve().parents[1]              # tools/sajha
CHECKOUT_LIBRARIES = KIT.parents[1] / "libraries"      # a drishti-rachana checkout; absent when the folder was copied elsewhere


def _list(v: str) -> list[str]:
    return [x.strip() for x in v.replace(os.pathsep, ",").split(",") if x.strip()]


@dataclass
class Settings:
    #: Library folders or .rlib files (RACHANA_LIBS, path-separated); default: the checkout's libraries/, else the wheel's search
    libraries: list[str] = field(default_factory=list)
    #: Libraries an agent may use (empty: every library found)
    allowed_libraries: list[str] = field(default_factory=list)
    #: SAJHA tools publish_ui may call, as the caller, to fetch a table's rows (empty: none; set it)
    data_tools: list[str] = field(default_factory=list)
    #: Origins of Data Protocol hosts a board may read from directly (the page's own origin is always allowed)
    data_origins: list[str] = field(default_factory=list)
    #: The board server: address it binds, and the address readers use (default http://<host>:<port>)
    host: str = "127.0.0.1"
    port: int = 8765
    public_url: str = ""
    #: Limits (AI_GENUI 3.2 rule 5 and GENUI_KIT 6)
    max_sutra_bytes: int = 65536
    max_panels: int = 60
    max_tables: int = 12
    max_rows: int = 50000            # rows per table pushed to a board
    max_inline_rows: int = 200       # rows an agent may write itself (labels, targets): never data from a tool (GD4)
    max_attempts: int = 3            # check attempts per caller and Sutra name before stop
    max_boards: int = 500
    board_ttl_minutes: int = 1440
    search_limit: int = 10
    #: Publishing needs a check by the compiler on V8 (drishti-rachana[v8] and its bundle); off only for development and tests
    require_engine: bool = True
    #: Where audit lines go (JSON lines); empty: the logger only
    audit_file: str = ""
    secret: str = ""

    @staticmethod
    def load(env: Optional[dict] = None) -> "Settings":
        env = os.environ if env is None else env
        s = Settings()
        cfg = env.get("RACHANA_GENUI_CONFIG")
        if cfg:
            for k, v in json.loads(Path(cfg).read_text(encoding="utf-8")).items():
                if k in {f.name for f in fields(Settings)}:
                    setattr(s, k, v)
        for f in fields(Settings):
            name = "RACHANA_LIBS" if f.name == "libraries" else "RACHANA_GENUI_" + f.name.upper()
            if name not in env:
                continue
            raw = env[name]
            if f.type in ("list[str]",):
                setattr(s, f.name, _list(raw))
            elif f.type == "int":
                setattr(s, f.name, int(raw))
            elif f.type == "bool":
                setattr(s, f.name, raw.strip().lower() in ("1", "true", "yes", "on"))
            else:
                setattr(s, f.name, raw)
        if not s.libraries and CHECKOUT_LIBRARIES.is_dir():
            s.libraries = [str(CHECKOUT_LIBRARIES)]
        if not s.secret:
            s.secret = secrets.token_hex(32)
        if not s.public_url:
            s.public_url = f"http://{s.host}:{s.port}"
        return s


_current: Optional[Settings] = None


def settings() -> Settings:
    global _current
    if _current is None:
        _current = Settings.load()
    return _current


def use(s: Settings) -> Settings:
    """Replace the process's settings (tests, an embedding application)."""
    global _current
    _current = s
    return s
