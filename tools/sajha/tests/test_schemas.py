# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""The tool definitions are SAJHA tool configs whose schemas are valid JSON Schema, whose examples validate, whose output schemas
embed the spec's doc definition (one definition: the kit's copy equals spec/1/sutra.schema.json's), and whose tools' answers validate."""
import json

import jsonschema

from conftest import KIT, ROOT
from rachana_genui.base import definition

TOOLS = ("search_fragments", "get_fragment", "check_sutra", "publish_ui")


def test_doc_definitions_are_the_specs():
    spec = json.loads((ROOT / "spec" / "1" / "sutra.schema.json").read_text())["$defs"]
    kit = json.loads((KIT / "schemas" / "doc.schema.json").read_text())["$defs"]
    assert {k: spec[k] for k in kit} == kit


def test_definitions_are_sajha_tool_configs_with_valid_schemas():
    for t in TOOLS:
        raw = json.loads((KIT / "schemas" / f"{t}.json").read_text())
        assert raw["name"] == t and raw["description"] and raw["inputSchema"]["type"] == "object"
        assert raw["implementation"].startswith(f"{t}.")
        d = definition(t)
        jsonschema.Draft202012Validator.check_schema(d["inputSchema"])
        jsonschema.Draft202012Validator.check_schema(d["outputSchema"])
        for ex in d["inputSchema"].get("examples", []):
            jsonschema.validate(ex, d["inputSchema"])
    out = definition("search_fragments")["outputSchema"]
    assert out["properties"]["results"]["items"]["properties"]["doc"] == {"$ref": "#/$defs/doc"}
    assert "summary" in out["$defs"]["doc"]["properties"]                    # inlined: the schema stands alone


def test_answers_validate_against_the_output_schemas(tools):
    import risk_data
    shapes = {"limit_use": risk_data.SCHEMAS["limit_use"]}
    answers = {"search_fragments": tools["search_fragments"].execute({"query": "limit utilisation", "tables": shapes}),
               "get_fragment": tools["get_fragment"].execute({"id": "finance/limit-table@1"}),
               "check_sutra": tools["check_sutra"].execute({"sutra": "rachana: 1.2\nsutra: x\nversion: 1\npanels: []\n"})}
    for t, a in answers.items():
        jsonschema.validate(a, definition(t)["outputSchema"])
