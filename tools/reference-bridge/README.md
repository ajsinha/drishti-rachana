<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary: see LICENSE. -->
# Reference bridge (bootstrap)

`RefBridge.java` is a runtime program in the sense of `conformance/RUNNER.md` section 5: cases on standard input, actual documents on
standard output. It is written against Drishti's reference implementation (`compute/drishti-rachana` and the jars it needs) and
implements `expr` and `expr-error`. It exists only until `rachana-core` is the reference (plan A10); then it is deleted.

```text
python3 tools/conformance/rachana_conformance.py --classpath-file CP.txt     # CP.txt: the jars of Drishti's release, one class path
```

`CP.txt` holds the class path of `drishti-rachana`, `drishti-api` and `drishti-common` (a release's jars) and their libraries
(Jackson, Caffeine, SLF4J, Spring core). Drishti is read, never changed. Run `java` 21 or later.
