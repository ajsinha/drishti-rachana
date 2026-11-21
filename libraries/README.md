<!-- Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved. -->
# Rachana fragment libraries

Typed fragments, grouped by domain, built and released with Rachana. Each library is a folder here (`<name>.rachana-lib.yaml`,
`fragments/`, `examples/`, `goldens/`, a generated `README.md`).

| Library | Status | What it holds |
|---|---|---|
| [core](core/README.md) | built, 1.0.0 (28 fragments) | generic screens for any domain: figures, records, tables, basic charts, states, history |
| finance | planned | |
| genomics | planned | |
| economy | planned | |
| geo | planned | |
| ops | planned | |
| ai | planned | |

Build, test, sign and pack: `python3 tools/libraries/build.py [--update-goldens] [LIBRARY ...]` (after `cd java && ./mvnw clean package -DskipTests`;
the jars come from this repository's build, nothing is installed). The check alone, `tools/libraries/LibCheck.java`, also runs
in the Maven build (`LibrariesCheckTest` in rachana-cli). `rachana studio` offers these libraries by default.

## Adding your own

The libraries here are one source among others, never the only one. Drop a library folder or an `.rlib` into a folder and name it:
`rachana lint|build|studio --libs <path>[,<path>]` (Python `rachana lint --libs` too), or `RACHANA_LIBS=<path>[:<path>]`, or
`<project>/rachana_libs`. Nothing in Rachana is modified or rebuilt. A name found twice with different contents is refused with a
problem, never silently shadowed (rename or remove one).
