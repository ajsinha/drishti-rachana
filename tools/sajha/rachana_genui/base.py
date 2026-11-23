# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""The base of the kit's tools: SAJHA's ``BaseMCPTool`` when the kit runs inside SAJHA (the plugin loader registers every concrete
subclass a ``tools/*.py`` file defines), and a small stand-in with the same surface when it runs on its own (tests, the example, a
plain Python application).

A tool's definition is its JSON file in ``schemas/`` in SAJHA's tool format (name, description, version, annotations, inputSchema,
outputSchema, metadata). Output schemas name the doc block by ``{"$ref": "#/$defs/doc"}``; the file's ``$defs`` points at
``doc.schema.json`` (the copy of spec/1/sutra.schema.json's definitions), which is inlined here so a client receives a schema that
stands alone.
"""
from __future__ import annotations

import copy
import json
import logging
from functools import lru_cache
from pathlib import Path
from typing import Any, Dict, Optional

SCHEMAS = Path(__file__).resolve().parents[1] / "schemas"
DOC_SCHEMA = SCHEMAS / "doc.schema.json"

try:                                                    # inside SAJHA (read-only use of its tool base)
    from sajha.tools.base_mcp_tool import BaseMCPTool as _Base
    IN_SAJHA = True
except Exception:  # noqa: BLE001 - on its own
    IN_SAJHA = False

    class _Base:                                        # the surface of SAJHA's BaseMCPTool that the kit uses
        def __init__(self, config: Optional[Dict] = None):
            self.config = config or {}
            self.logger = logging.getLogger(self.__class__.__name__)
            self._name = self.config.get("name", self.__class__.__name__)
            self._description = self.config.get("description", "")
            self._version = self.config.get("version", "1.0.0")
            self._enabled = self.config.get("enabled", True)
            self._input_schema = self.config.get("inputSchema", {})
            self._output_schema = self.config.get("outputSchema", {})
            self._metadata = self.config.get("metadata", {})

        name = property(lambda self: self._name)
        description = property(lambda self: self._description)
        version = property(lambda self: self._version)
        enabled = property(lambda self: self._enabled)
        input_schema = property(lambda self: self._input_schema or self.get_input_schema())
        output_schema = property(lambda self: self._output_schema or self.get_output_schema())

        def validate_arguments(self, arguments: Dict[str, Any]) -> bool:
            import jsonschema
            jsonschema.validate(arguments, self.input_schema)
            return True


@lru_cache(maxsize=1)
def doc_defs() -> dict:
    return json.loads(DOC_SCHEMA.read_text(encoding="utf-8"))["$defs"]


def _inline(schema: dict) -> dict:
    """The schema with ``$defs`` that point at doc.schema.json replaced by that file's definitions (self-contained)."""
    s = copy.deepcopy(schema)
    defs = s.get("$defs") or {}
    if any(isinstance(v, dict) and str(v.get("$ref", "")).startswith("doc.schema.json") for v in defs.values()):
        s["$defs"] = {**{k: v for k, v in defs.items() if not str((v or {}).get("$ref", "")).startswith("doc.schema.json")},
                      **doc_defs()}
    return s


@lru_cache(maxsize=None)
def definition(tool: str) -> dict:
    d = json.loads((SCHEMAS / f"{tool}.json").read_text(encoding="utf-8"))
    for k in ("inputSchema", "outputSchema"):
        if k in d:
            d[k] = _inline(d[k])
    return d


class KitTool(_Base):
    """One kit tool: its definition from ``schemas/<TOOL>.json``; ``run(arguments) -> dict`` does the work."""

    TOOL = ""

    def __init__(self, config: Optional[Dict] = None):
        merged = copy.deepcopy(definition(self.TOOL))
        merged.update({k: v for k, v in (config or {}).items() if v not in (None, {}, "")})
        merged.pop("implementation", None)
        super().__init__(merged)

    def get_input_schema(self) -> Dict:
        return self._input_schema

    def get_output_schema(self) -> Dict:
        return self._output_schema

    def execute(self, arguments: Dict[str, Any]) -> Any:
        return self.run(dict(arguments or {}))

    def run(self, arguments: Dict[str, Any]) -> Dict[str, Any]:  # pragma: no cover - each tool's own
        raise NotImplementedError
