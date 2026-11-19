<!-- Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved. Drishti Rachana. Proprietary: see LICENSE. -->
# Split exemptions

The compiler moved here from its host with history (split step A10, `docs/design/SPLIT_PLAN.md`) **before** it was untangled, so
for a while it still uses libraries and host types that Rachana's design rules forbid. Each is listed below with the step that
removes it. The guards read this file: a dependency or package not listed here fails the build, and a listed one that is no longer
used fails it too, so **this list only shrinks**. When the last line goes, the file is deleted.

## Dependencies (read by `DependencyAllowListTest`; the enforcer's `includes` in `pom.xml` say the same)

- dependency: rachana-core org.assertj:assertj-core until never (test scope; the moved unit tests are written with it)

Jackson and SnakeYAML left with A8b (2026-11-19): Core reads YAML and JSON itself (`com.ash.rachana.parse`).

## Host types

None left: the last host types left the temporary `hostcompat` package at A3 (`NamesNoHostTest` keeps it from coming back).

## Host words (read by `NamesNoHostTest`)

Files of `rachana-core` (the moved compiler) that still carry a host word (a `DRS-` or `DEL-` code, a `drishti.` setting name, the product name outside
"Drishti Rachana" and "Drishti Elements"). The count may only go down; A1 removes the codes, A4 the settings, A7 the rest.

- host-word files: 6
