# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""``python -m drishti_rachana lint`` (docs/design/COMPILER_AND_RUNTIMES.md 8): exit codes and output formats."""
import json

import pytest

pytest.importorskip("py_mini_racer")
from drishti_rachana.engine import BUNDLE_DIR  # noqa: E402
from drishti_rachana.cli import main, find_sutras  # noqa: E402

pytestmark = pytest.mark.skipif(not (BUNDLE_DIR / "rachana.tools.js").is_file(), reason="bundle not built (tools/js/build_js.py)")

GOOD = "rachana: 1\nsutra: ok\nversion: 1\ntitle: { id: \"'Ok'\" }\npanels:\n  - { id: m, kind: metric, label: Revenue, value: 1 }\n"
BAD = "rachana: 1\nsutra: bad\nversion: 1\npanels:\n  - { id: m, kind: metric, label: Revenue, value: \"1 +\" }\n"


def test_exit_codes_and_text(tmp_path, capsys):
    (tmp_path / "a.sutra.yaml").write_text(GOOD)
    assert main(["lint", str(tmp_path)]) == 0
    (tmp_path / "b.sutra.yaml").write_text(BAD)
    assert main(["lint", str(tmp_path)]) == 1
    out = capsys.readouterr().out
    assert "b.sutra.yaml:" in out and "error RCH-" in out and "a.sutra.yaml:" not in out


def test_json_and_sarif(tmp_path, capsys):
    (tmp_path / "b.sutra.yaml").write_text(BAD)
    assert main(["lint", "--format", "json", str(tmp_path)]) == 1
    res = json.loads(capsys.readouterr().out)
    assert res[0]["ok"] is False and res[0]["problems"][0]["code"].startswith("RCH-")
    assert main(["lint", "--format", "sarif", str(tmp_path / "b.sutra.yaml")]) == 1
    s = json.loads(capsys.readouterr().out)
    assert s["version"] == "2.1.0" and s["runs"][0]["results"][0]["ruleId"].startswith("RCH-")


def test_usage_error_and_discovery(tmp_path):
    assert main(["lint", str(tmp_path / "missing")]) == 2
    (tmp_path / "node_modules").mkdir()
    (tmp_path / "node_modules" / "x.sutra.yaml").write_text(GOOD)
    (tmp_path / "a.sutra.yaml").write_text(GOOD)
    assert [f.name for f in find_sutras([str(tmp_path)])] == ["a.sutra.yaml"]


def test_each_sutra_declares_its_level_and_examples_lint_clean():
    from pathlib import Path
    from drishti_rachana.engine import Rachana
    ex = Path(__file__).resolve().parents[2] / "examples"
    assert main(["lint", str(ex / "static-sales"), str(ex / "live-orders")]) == 0
    assert main(["lint", str(ex)]) == 0                               # each Sutra at the level its rachana: key declares (SPEC 2.1)
    assert main(["lint", "--level", "1.2", str(ex)]) == 2             # usage error: no --level
    assert Rachana().check(GOOD)["ok"] and Rachana().check(GOOD, level="1.2")["ok"]


def test_upgrade_runs_the_one_java_implementation(tmp_path, monkeypatch):
    from pathlib import Path
    from drishti_rachana.cli import java_command
    assert java_command("upgrade", ["x"], {}) is None
    assert java_command("upgrade", ["x"], {"RACHANA_JAR": "r.jar"}) == ["java", "-jar", "r.jar", "upgrade", "x"]
    monkeypatch.delenv("RACHANA_JAR", raising=False)
    monkeypatch.delenv("RACHANA_CLASSPATH", raising=False)
    assert main(["upgrade", "x"]) == 2                               # no JVM named
    java = Path(__file__).resolve().parents[2] / "java"
    if not (java / "rachana-cli" / "target" / "classes").is_dir():
        return                                                        # the Java classes are not built here
    monkeypatch.setenv("RACHANA_CLASSPATH", ":".join(str(java / m / "target" / "classes") for m in ("rachana-cli", "rachana-tools", "rachana-core", "rachana-api")))
    f = tmp_path / "u.sutra.yaml"
    f.write_text("rachana: 1\nsutra: u\nversion: 1\npanels:\n  - { id: m, kind: metric, label: S, value: \"range($.r, 'p')\" }\n")
    assert main(["upgrade", str(f), "--write"]) == 0
    assert f.read_text().startswith("rachana: 1.2\n") and "span(" in f.read_text()
