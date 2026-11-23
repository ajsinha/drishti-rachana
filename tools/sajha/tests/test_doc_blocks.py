# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""The guard: every fragment of the shipped libraries carries a complete doc block (RCH-2093, the kit's mirror of the compiler's
DocBlockLint), its see-also names fragments that exist, and its sample row validates against the doc schema. And the rule itself."""
import glob
import json

import jsonschema
import yaml

from conftest import KIT, ROOT
from rachana_genui import doclint

FRAGMENTS = sorted(glob.glob(str(ROOT / "libraries" / "*" / "fragments" / "*.fragment.yaml")))


def test_every_library_fragment_has_a_complete_doc():
    assert len(FRAGMENTS) >= 90
    problems = [p.to_json() for f in FRAGMENTS for p in doclint.check(open(f, encoding="utf-8").read(), f, "fragment")]
    assert problems == []


def test_see_also_names_fragments_that_exist_and_docs_fit_the_schema():
    names = {f"{f.split('/')[-3]}/{f.split('/')[-1].split('.v')[0]}" for f in FRAGMENTS}
    defs = json.loads((KIT / "schemas" / "doc.schema.json").read_text())["$defs"]
    v = jsonschema.Draft202012Validator({"$defs": defs, "$ref": "#/$defs/fragment"})
    for f in FRAGMENTS:
        d = yaml.safe_load(open(f, encoding="utf-8").read().replace("%{", "_{"))
        assert [s for s in d["doc"]["see-also"] if s.split("@")[0] not in names] == [], f
        assert [e.message for e in v.iter_errors(d)] == [], f


THIN = """rachana: 1.2
fragment: thin-one
version: 1
doc:
  summary: Bars.
  description: Bars.
  use-when: [bars]
  see-also: [Not A Name]
  surprise: 1
params:
  rows: { type: table, required: true, doc: The rows, columns: { label: { type: text, doc: L }, value: { type: number, doc: V } } }
panels: []
"""


def test_a_thin_doc_is_reported_at_its_place():
    got = {(p.path, p.line) for p in doclint.check(THIN, "thin.yaml")}
    paths = {p for p, _ in got}
    for want in ("/doc/summary", "/doc/description", "/doc/category", "/doc/use-when/0", "/doc/avoid-when", "/doc/keywords",
                 "/doc/data", "/doc/example", "/doc/see-also/0", "/doc/surprise"):
        assert want in paths, want
    assert ("/doc/summary", 5) in got and ("/doc/surprise", 9) in got
    assert all(p.code == "RCH-2093" for p in doclint.check(THIN, "thin.yaml"))


def test_a_sample_row_needs_the_required_columns_and_the_example_the_required_params():
    text = THIN.replace("  surprise: 1\n", "  data: { table: rows, in-words: One row per bar with its label, sample-row: { label: A } }\n"
                        "  example: { use: core/other@1, with: { nope: 1 } }\n")
    msgs = {p.path: p.message for p in doclint.check(text, "thin.yaml")}
    assert "missing value" in msgs["/doc/data/sample-row"]
    assert "use of thin-one" in msgs["/doc/example/use"]
    assert "/doc/example/with/nope" in msgs and "required parameter 'rows'" in msgs["/doc/example/with"]


def test_a_sutra_without_doc_and_a_screen_doc_needs_less_than_a_fragment():
    assert [p.path for p in doclint.check("rachana: 1.2\nsutra: s\nversion: 1\npanels: []\n")] == ["/doc"]
    screen = ("rachana: 1.2\nsutra: s\nversion: 1\ndoc:\n  summary: The desk's risk at a glance today\n  description: Firm VaR, VaR "
              "by desk and limit use for the morning meeting.\n  category: market-risk\n  use-when: [The morning risk meeting reads it]\n"
              "  avoid-when: [\"Intraday trading: use the live blotter\"]\n  keywords: [var, desk, limits]\npanels: []\n")
    assert doclint.check(screen) == []
