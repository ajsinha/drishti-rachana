<!-- Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved. Drishti Rachana. Proprietary: see LICENSE. -->
# Java reactor

The Java reference implementation of Rachana: a Maven reactor, group `com.ash.rachana`, Java 21. Each module is a named Java
module (`module-info.java`).

## Modules

| Module | Holds |
|---|---|
| `rachana-api` | `Node` (the value interface) and Rachana's own nodes (`Node.of`); zero dependencies |
| `rachana-core` | the compiler: `com.ash.rachana` (Sutra writer, text, expressions, wiring, schema, layout and pivot editors, problems), `el` and `el.check` (Rachana-EL and its checker), `format` (formats, tones, world regions), `model`, `parse` (the positional YAML reader and the section readers), `design.ops` (editing operations), `profile` (profiles, grammar), `lib` (libraries), `table` (live-table declarations), `bind`, `view`, `control`, `wire` (the binder, the ViewModel and its canonical writer, controls, event wiring) |
| `rachana-tools` | `Upgrade` (`rachana upgrade`), `LibraryTool` and `LibraryGates` (`rachana lib`), `LangFiles` (the generated `spec/1` language files) |
| `rachana-data` | the Data Protocol: `TableProvider`, `ProtocolHost` and `ProtocolHttp`, JSON, Arrow and NDJSON rows, `TableClient`, the `ReferenceServer` |
| `rachana-live` | live tables: `LiveTables`, the snapshot, stream, authoriser, audit and change-sink interfaces (`spi`), the `Outbox`, the `LiveReferenceServer` ([guide](../docs/guides/LIVE_TABLES.md)) |
| `rachana-server` | the standalone server: a project folder of Sutras as pages, the Data Protocol over tables from files, JDBC and REST kept live, sign-in, edits; `java -jar rachana-server-<version>-all.jar serve --config <file>`; every key in [rachana-server.example.yaml](rachana-server/rachana-server.example.yaml) ([guide](../docs/guides/SERVER.md)) |
| `rachana-cli` | `com.ash.rachana.cli.Main`: `rachana upgrade` and `rachana lib build, sign, keygen, pack, verify, lock, gate, freeze, contracts, check-host` |
| `rachana-js` | the TeaVM build of the compiler into the JavaScript bundles (profile `-Pjs`) |
| `rachana-conformance` | `JvmRuntime`: the conformance runtime program over `rachana-core` (RUNNER section 5) |
| `rachana-conformance-cases` | the cases and specification as a jar, no code |
| `rachana-compat` | the compatibility regime's tests: frozen cases, level replay, customer corpus, contract append-only, API surface, reserved names, upgrade |
| `rachana-guards` | the guards: names no host, dependency allow-list, public surface, licence header and line cap |

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

Windows: `mvnw.cmd clean verify`. The wrapper needs `JAVA_HOME` (or `java` on the PATH) to be JDK 21: a shell whose `JAVA_HOME` is
Java 8 must point it at the JDK 21 folder first.

Never `install` a snapshot: only a release tag is installed, for Drishti (see [Installing a release for Drishti](#installing-a-release-for-drishti)).

## Build and run in the IDE

JetBrains (IntelliJ IDEA for Java, PyCharm for Python) and VS Code; the launchers are checked in. This is the one home of the IDE instructions of Rachana; Lake and Elements link here for the shared parts.

### IntelliJ IDEA

Java only: no Python, no Python plugin. The same Maven runs in the IDE and in a terminal.

### Prerequisites

| You need | How |
|---|---|
| **A JDK 21** | Linux `/usr/lib/jvm/java-21-openjdk-amd64`, Windows `C:\jdk-21`, macOS `/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home` |
| **IntelliJ IDEA** (Community or Ultimate) | its bundled Maven builds everything; `./mvnw` is the same Maven for a terminal |

### Open the project

1. **File → Open**, choose `java/pom.xml`, **Open as Project**. Wait for the Maven import and the indexing; the Maven tool window lists
   the `rachana-*` modules.
2. **File → Project Structure → Project → SDK**: **Add SDK → JDK…**, choose the JDK 21 folder; **Language level** *SDK default*.
3. **Settings → Build, Execution, Deployment → Build Tools → Maven → Runner → JRE**: *Use Project JDK*. Maven in the IDE ignores your
   shell's `JAVA_HOME`. An *invalid target release* or *release version 21 not supported* error means the wrong JDK is set here.
4. The drop-down at the top right lists the configurations of `java/run-configs/` under the folder **Rachana**: the Maven launchers,
   the Java programs and the JavaScript launchers below.

### The Maven launchers

Open a configuration and press **Run** (Shift+F10). All of them run from `java/`.

| Run configuration | What it runs | Use it when |
|---|---|---|
| **Rachana build all** | `clean verify -DskipTests`: every module compiles, the jars are packaged, the guards run | after a clone or a pull |
| **Rachana test** | `clean verify`: the build with the unit tests, the guards and the compatibility regime | before a commit or a push |
| **Rachana install release** | `clean install -DskipTests`: the jars into the local Maven repository (`~/.m2`) for Drishti | **only on a checkout of a release tag**, see below |
| **Rachana JS bundles** | `clean package -Pjs -DskipTests -pl :rachana-js -am`: the TeaVM JavaScript bundles into `java/rachana-js/target/js/` | you changed the compiler or the runtime and want the JavaScript bundles |

The JavaScript bundles launcher is plain Maven. Copying the bundles to `js/packages/rachana/dist/` and the Node conformance check are
`tools/js/build_js.py` (Python) and are not part of the launcher.

**From a terminal**, the same commands: `./mvnw clean verify -DskipTests` (Windows `mvnw.cmd …`), `./mvnw clean verify`,
`./mvnw clean install -DskipTests`, `./mvnw clean package -Pjs -DskipTests -pl :rachana-js -am`.

The Java programs (Application configurations, run **Rachana build all** first): **Rachana CLI (lib)** (`com.ash.rachana.cli.Main`, edit the
program arguments: `lib build|sign|pack|verify|lock|contracts|check-host`), **Rachana reference server** (`ReferenceServer` on
`http://localhost:8099/rdp/v1`) and the standalone server `com.ash.rachana.server.Main` on the example `examples/server-demo`:
**Rachana server serve (server-demo)** (`http://127.0.0.1:8090/`, sign in as `dev`, password `dev-only-pass`: **development only**, written in
`examples/server-demo/users.yaml`), **Rachana server check (server-demo)** and **Rachana server hash-password** (type a password in the Run window,
press Enter). The server's page assets are copied into `target/classes` by the Maven build, and the runtime bundle must exist first:
run **Rachana JS runtime bundle**, then **Rachana build all**, then the server.

### The JavaScript launchers (Maven, no bash)

Under the folder **Rachana JS**; each runs `exec:exec@<id>` of `js/pom.xml` (exec-maven-plugin; the pom builds nothing), so they work in IntelliJ
Community on Windows with no bash, no Shell Script plugin and no Node plugin. Node 20+ and Python 3 on the PATH, or add `-Dnode=<path to node>` /
`-Dpython=<path to python>` to the goals.

| Run configuration | id | What it runs |
|---|---|---|
| **Rachana JS runtime bundle** | `runtime-bundle` | `tools/js/build_js.py`: the TeaVM bundles into `js/packages/rachana/dist/`, then the conformance check |
| **Rachana JS Node tests** | `node-tests` | `tools/js/node_tests.mjs`: every `js/packages/*/test/*.test.mjs` through `node --test` (no shell glob) |
| **Rachana Studio** | `studio` | Studio on `examples/static-sales`, `http://localhost:4500` |
| **Rachana Studio (server-demo)** | `studio-server-demo` | Studio on the server example's Sutra and tables, `http://localhost:4501` |
| **Rachana dev server (static-sales)** | `dev` | `rachana dev`, `http://localhost:4400` |

From a terminal: `java/mvnw -f js/pom.xml exec:exec@node-tests` (Windows `java\mvnw.cmd -f js\pom.xml exec:exec@node-tests`).

### What each IDE runs

| Part | IntelliJ IDEA Community / Ultimate | PyCharm | VS Code |
|---|---|---|---|
| Java (`java/`) | `java/run-configs/` (Maven, Application) | no | tasks and launch in `drishti-rachana.code-workspace` |
| Python SDK (`python/`) | no (no Python plugin needed) | `python/run-configs/` (pytest, lint, Flask, FastAPI, protocol check, JS runtime bundle); open the folder `python/` | the same, as tasks and `debugpy` launches |
| JavaScript (`js/`) | `java/run-configs/` folder **Rachana JS**: Maven launchers of `js/pom.xml` (runtime bundle, Node tests, Studio, dev server); no bash | the same Maven launchers | tasks (Node tests, Studio, dev server) and `node` launches |

Open `java/pom.xml` in IntelliJ and `python/` in PyCharm, never one folder in both (each rewrites the other's `.idea`).

### VS Code

**File, Open Workspace from File**, choose `drishti-rachana.code-workspace` (a visible file at the repository root; no `.vscode` folder). Install the
recommended extensions (Extension Pack for Java, Python and Pylance, ESLint, YAML). The workspace sets the JDK 21 runtime (edit the path:
Windows `C:\jdk-21`), a `PYTHONPATH` to `python/src`, and has **Terminal, Run Task** entries mirroring the JetBrains launchers (Maven build, test, install
release, JS bundles; Node tests; pytest; lint; Studio; dev server; the server example: serve, check) and **Run and Debug** configurations for the Java programs, the Flask and FastAPI
apps, `rachana lint` and the Node commands.

### Where the artifacts go

An install writes `~/.m2/repository/com/ash/rachana/...` (Windows `C:\Users\<user>\.m2\repository\com\ash\rachana\...`). The install order
is **Drishti Tools (tag) → Rachana (tag) → Lake (tag) → Drishti**: each one's tag is installed, never a branch, before the next one
builds (`rachana-server` uses Tools' `tools-csv`, version `drishti.tools.version` in `java/pom.xml`; no other module uses Tools).

### Windows and company networks: `PKIX path building failed`

A company network that re-signs HTTPS with its own root certificate makes Maven stop on its first download with
`unable to find valid certification path`. Windows trusts that root; the JDK does not. The fix is one VM option, no file change and no
administrator rights:

```
-Djavax.net.ssl.trustStoreType=Windows-ROOT
```

1. **File → Settings → Build, Execution, Deployment → Build Tools → Maven → Runner → VM Options**: paste the option.
2. **Maven → Importing → VM options for importer**: paste it too (the import downloads as well).
3. **Apply**, then run **Rachana build all** again.
4. A terminal: `setx MAVEN_OPTS "-Djavax.net.ssl.trustStoreType=Windows-ROOT"`, then open a new terminal.

Other ways, if your company prefers them: the company's Maven mirror in `%USERPROFILE%\.m2\settings.xml` (Linux `~/.m2/settings.xml`;
IntelliJ reads the same file), or the company root certificate added to the JDK with `keytool -importcert -cacerts` (needs write access
to the JDK folder).

## Installing a release for Drishti

Drishti's parent POM pins the Rachana version it builds against, and that version must be in the local Maven repository (decision R-1 in
[docs/design/DECISIONS.md](../docs/design/DECISIONS.md)). Only a release is installed, and only from its tag:

```text
git checkout vX.Y.Z                              # the release tag, never a branch
cd java && ./mvnw clean install -DskipTests      # Windows: mvnw.cmd clean install -DskipTests
```

In IntelliJ, on that same tag's checkout: **Rachana install release**. Then build Drishti. A branch or a snapshot is never installed:
Drishti would then build against unreleased code. Run **Rachana test** before the tag is made.

## Conformance over this code

```text
cd java && ./mvnw clean package -DskipTests
cd .. && python3 tools/conformance/rachana_conformance.py --jvm --profiles core,drishti --out results/jvm.jsonl
```

`--jvm` runs `com.ash.rachana.conformance.JvmRuntime` on the module path that the build writes to
`rachana-conformance/target/runtime-classpath.txt`. The host profile's extra format names are read from the file named by
`RACHANA_DRISHTI_FORMATS`. The same command with `--classpath-file` instead of `--jvm` runs the reference bridge over the host's
jars (`tools/reference-bridge`); the split is proven by the two results files being byte-identical.
