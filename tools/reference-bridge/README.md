<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary: see LICENSE. -->
# Reference bridge (bootstrap)

`RefBridge.java` is a runtime program in the sense of `conformance/RUNNER.md` section 5: cases on standard input, actual documents on
standard output. It is written against Drishti's reference implementation (`compute/drishti-rachana` and the jars it needs) and
implements `expr`, `expr-error`, `format`, `problem` and `edit` (one operation of the reference's `design/ops` per case, its name in kebab case). It exists only until `rachana-core` is the reference (plan A10); then it is deleted.

```text
python3 tools/conformance/rachana_conformance.py --classpath-file CP.txt     # CP.txt: the jars of Drishti's release, one class path
```

`CP.txt` holds the class path of `drishti-rachana`, `drishti-api` and `drishti-common` (a release's jars) and their libraries
(Jackson, Caffeine, SLF4J, Spring core). Drishti is read, never changed. Run `java` 21 or later.

`LangDump.java` (plan H4) reads the reference's tables by reflection (the parser's key sets, `PanelKind`, `ControlKind`, the function
table, the bundled formats, `Tones`, the limits, `RachanaSchema`) and the problem codes from the compiled classes' constant pools, and
writes `rachana.lang.json` and `sutra.schema.json`: `python3 tools/conformance/spec_files.py --classpath-file CP.txt --formats
<drishti core pack formats.yaml> [--write]`. `SchemaDump.java` writes the reference's ViewModel schema (H4's comparison).
