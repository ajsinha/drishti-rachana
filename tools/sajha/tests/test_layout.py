# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""The folder deploys into SAJHA as a plugin: plugin.json lists the tools; each tools/*.py defines exactly the one concrete tool
class SAJHA's plugin loader registers (it instantiates each class defined in the file with an empty config) under its own name;
and the example runs end to end."""
import inspect
import json

from conftest import KIT, load_tool
from rachana_genui.base import KitTool


def test_plugin_manifest_and_tool_files():
    manifest = json.loads((KIT / "plugin.json").read_text())
    files = sorted(p.stem for p in (KIT / "tools").glob("*.py"))
    assert sorted(manifest["tools"]) == files
    for name in files:
        mod = load_tool(name)
        # SAJHA's rule (sajha/core/plugins.py): classes whose __module__ is the file's own, concrete, BaseMCPTool subclasses
        own = [v for v in vars(mod).values() if isinstance(v, type) and issubclass(v, KitTool) and v.__module__ == mod.__name__
               and not inspect.isabstract(v)]
        assert len(own) == 1, name
        t = own[0]({})
        assert t.name == name and t.description and t.input_schema and t.output_schema


def test_example_runs_end_to_end(capsys):
    import risk_mashup
    assert risk_mashup.main(["--port", "0"]) == 0
    out = capsys.readouterr().out
    assert "RGK-112" not in out or "fix" in out
    assert "check_sutra attempt 2: ok=True" in out and "/b/" in out
    text = (KIT / "example" / "risk-mashup.sutra.yaml").read_text()
    assert text.count("- use:") == 5 and "$.firm_risk.ES_975" in text
