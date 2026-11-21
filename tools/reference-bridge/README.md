<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary: see LICENSE. -->
# Reference bridge (bootstrap)

`RefBridge.java` is a runtime program in the sense of `conformance/RUNNER.md` section 5: cases on standard input, actual documents on
standard output. It is written against Drishti's reference implementation (`compute/drishti-rachana` and the jars it needs) and
implements `expr`, `expr-error`, `format`, `problem` and `edit` (one operation of the reference's `design/ops` per case, its name in kebab case). Since A10 the same logic runs over this repository's code as `JvmRuntime` (`rachana_conformance.py --jvm`); this bridge stays as the reference each split step is compared with, byte for byte, until Drishti consumes the pin (DRISHTI_MIGRATION_TODO 8.9).

```text
python3 tools/conformance/rachana_conformance.py --classpath-file CP.txt     # CP.txt: the jars of Drishti's release, one class path
```

`CP.txt` holds the class path of `drishti-rachana`, `drishti-api` and `drishti-common` (a release's jars) and their libraries
(Jackson, Caffeine, SLF4J, Spring core). Drishti is read, never changed. Run `java` 21 or later.

Since A14 a second program, `RefBridgeSutra.java`, answers `sutra` cases: the reference's view pipeline steps (`ViewPipeline.assemble`) over its binder,
controls, wiring and ViewModel records, with no sources, links or masks, written by a plain `ObjectMapper`. That needs the engine's
class path (`mvn -o package dependency:build-classpath -pl kernel/drishti-engine -am -DskipTests -Dmdep.outputFile=cp-engine.txt
-Dmdep.includeScope=runtime`, then the engine jar and `cp-engine.txt`), run with `--kinds sutra --cmd "java -cp <engine class path>
tools/reference-bridge/RefBridgeSutra.java spec/1/formats.yaml <drishti formats.yaml>"`. It is a program of its own because the
engine's jars bring the pivot engine's function providers, which change the answers of other kinds; `RefBridge.java` keeps the
compiler-only class path.

The language files are no longer generated here: `java/rachana-tools` `LangFiles` writes them from Rachana's own tables (`tools/conformance/spec_files.py`, `tools/regime/levels.py write`). `SchemaDump.java` writes the reference's ViewModel schema (H4's comparison).
