<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary: see LICENSE. -->
# YAML and JSON differential (plan A8a)

`YamlDiff.java` compares Core's own YAML reader (`com.ash.rachana.parse.YamlReader`) with the reference's
`PositionalYamlReader` (Jackson 2.21.4 on SnakeYAML 2.4, from Drishti's i7a jars) node for node: every value with its
type and position, every issue, and for text that is not YAML the error text and its line and column. It also compares
Core's `Json` reader and writer with Jackson's. Drishti is only read.

```text
(cd java && mvn -o -q -pl rachana-core -am clean compile)
python3 tools/yaml-diff/yaml_diff.py --drishti <Drishti checkout> --mutations 20      # add --record to rewrite the vectors
```

Inputs: every `*.yaml`/`*.yml` file of the Drishti checkout and of this repository, every multi-line text inside the
conformance case files, about a hundred edge inputs (types, styles, positions, every refusal), and N deterministic
mutations of each file (cut, a character deleted or doubled, a tab, a line break, indentation), so that refusals are
compared as well as trees.

**Result (2026-11-19, Drishti `c6bc1d1bc`):** 168,923 inputs (1,669 files, 253 case texts, 101 edge inputs, 166,900
mutations): 127,283 trees and 40,051 refusals identical, byte for byte; the only difference is the one Core adds on
purpose: an **issue for a YAML anchor or alias** (1,589 inputs, all in files Core never reads as Sutras: Drishti data
contracts, its contract policy and this repository's protocol examples; no Sutra, block or fragment uses one).

`--record` writes `java/rachana-core/src/test/resources/yaml/reference-vectors.jsonl`: the reference's answers for this
repository's own inputs only (1,113 vectors), which `YamlReaderTest` checks on every build without the reference.
