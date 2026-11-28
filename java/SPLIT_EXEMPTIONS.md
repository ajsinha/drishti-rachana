<!-- Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved. Drishti Rachana. Proprietary: see LICENSE. -->
# Build exemptions

This file is read by the guards (it is data for them, not a document; the build instructions are in [docs/DEVELOPING.md](../docs/DEVELOPING.md)). A dependency or package not listed here fails the build, and a listed one that is no longer used fails it too, so **this list only shrinks**. When the last line goes, the file is deleted.

## Dependencies (read by `DependencyAllowListTest`; the enforcer's `includes` in `pom.xml` say the same)

- dependency: rachana-core org.assertj:assertj-core until never (test scope; the moved unit tests are written with it)

Core reads YAML and JSON itself (`com.ash.rachana.parse`); it uses no Jackson or SnakeYAML.

## Host types

None: `NamesNoHostTest` keeps host types out of `rachana-core`.

## Host words (read by `NamesNoHostTest`)

Files of `rachana-core` (the moved compiler) that still carry a host word (a `DRS-` or `DEL-` code, a `drishti.` setting name, the product name outside
"Drishti Rachana" and "Drishti Elements"). The count may only go down.

- host-word files: 2
