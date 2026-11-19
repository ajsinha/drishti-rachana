<!-- Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved. Drishti Rachana. Proprietary: see LICENSE. -->
# Java reactor

The Java reference implementation of Rachana: a Maven reactor, group `com.ash.rachana`, Java 21. Each module is a named Java
module (`module-info.java`).

## Modules as built

| Module | Holds | State |
|---|---|---|
| `rachana-api` | `Node` (the value interface) and Rachana's own nodes (`Node.of`); zero dependencies | built (A2): the evaluator, formats and checker read only `Node` |
| `rachana-core` | **the compiler, moved from its first host with its history** (split step A10): `com.ash.rachana` (Sutra writer, text, expressions, wiring, schema, layout and pivot editors, problems), `el` and `el.check` (Rachana-EL and its checker), `format` (formats, tones, world regions), `model`, `parse` (the positional YAML reader and the section readers), `design.ops` (editing operations); `profile` (skeleton) | built; 790 unit tests |
| `rachana-tools` | editing tools, writer, generators | skeleton (the editing code is still in `rachana-core`) |
| `rachana-data` | Data Protocol | skeleton |
| `rachana-cli` | command line | skeleton |
| `rachana-conformance` | `JvmRuntime`: the conformance runtime program over `rachana-core` (RUNNER section 5) | built |
| `rachana-conformance-cases` | the cases and specification as a jar, no code | built |
| `rachana-compat` | the compatibility regime's tests (CG1 to CG6) | built by the regime lane |
| `rachana-guards` | the guards: names no host, dependency allow-list, public surface, licence header and line cap | built |

`rachana-core` bundles two data files whose home is Drishti Elements (`formats.yaml`, `world-regions.yaml`), read from the
pinned copy under `js/vendor/drishti-elements/<drishti.elements.version>/data/`; there is no second copy.

## Split exemptions

The compiler moved before it was untangled, so it still uses Jackson (until A8b). [SPLIT_EXEMPTIONS.md](SPLIT_EXEMPTIONS.md) lists each with the step that removes it; the guards
read that file, fail on anything not listed, and fail when a listed item is no longer used, so the list only shrinks.

## Build and test

```text
cd java
./mvnw clean verify                 # every module, its unit tests and the guards (JDK 21)
```

Never `install`: nothing outside this reactor consumes a snapshot.

## Conformance over this code

```text
cd java && ./mvnw clean package -DskipTests
cd .. && python3 tools/conformance/rachana_conformance.py --jvm --profiles core,drishti --out results/jvm.jsonl
```

`--jvm` runs `com.ash.rachana.conformance.JvmRuntime` on the module path that the build writes to
`rachana-conformance/target/runtime-classpath.txt`. The host profile's extra format names are read from the file named by
`RACHANA_DRISHTI_FORMATS`. The same command with `--classpath-file` instead of `--jvm` runs the reference bridge over the host's
jars (`tools/reference-bridge`); the split is proven by the two results files being byte-identical.
