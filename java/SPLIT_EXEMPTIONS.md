<!-- Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved. Drishti Rachana. Proprietary: see LICENSE. -->
# Split exemptions

The compiler moved here from its host with history (split step A10, `docs/design/SPLIT_PLAN.md`) **before** it was untangled, so
for a while it still uses libraries and host types that Rachana's design rules forbid. Each is listed below with the step that
removes it. The guards read this file: a dependency or package not listed here fails the build, and a listed one that is no longer
used fails it too, so **this list only shrinks**. When the last line goes, the file is deleted.

## Dependencies (read by `DependencyAllowListTest`; the enforcer's `includes` in `pom.xml` say the same)

- dependency: rachana-core com.fasterxml.jackson.core:jackson-databind until A8b (Core's own YAML and JSON reader)
- dependency: rachana-core com.fasterxml.jackson.dataformat:jackson-dataformat-yaml until A8b
- dependency: rachana-core com.github.ben-manes.caffeine:caffeine until A3 (Core's LRU)
- dependency: rachana-core org.assertj:assertj-core until never (test scope; the moved unit tests are written with it)

`org.yaml:snakeyaml` comes with `jackson-dataformat-yaml` and is pinned to the version the reference ran with (2.4).

## Host types (package `com.ash.rachana.hostcompat`, read by `NamesNoHostTest`)

Copied from the host with only their package changed, so behaviour stays byte-identical:

| Type | Removed by |
|---|---|
| `HostException`, `ErrorCode` (ten codes, with the host's `DRS-` text) | A1: `RachanaException` and `RCH-` codes |
| `DataNode`, `NodeType`, `ListValues`, `PathWalker`, `IdText` | A2: the evaluator reads `com.ash.rachana.api.Node` |
| `SafeRegex` | A3: becomes Core's own (`com.ash.rachana.el.SafeRegex`) |
| `BusinessCalendar` | A3: the `Calendars` hook with Core's default calendar |

## Host words (read by `NamesNoHostTest`)

Files of the reactor that still carry a host word (a `DRS-` or `DEL-` code, a `drishti.` setting name, the product name outside
"Drishti Rachana" and "Drishti Elements"). The count may only go down; A1 removes the codes, A4 the settings, A7 the rest.

- host-word files: 52
