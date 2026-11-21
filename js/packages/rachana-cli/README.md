<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary. All rights reserved. -->
# drishti-rachana-cli

The `rachana` command for Node (20+). Needs the compiler bundles in `js/packages/rachana/dist` (`python3 tools/js/build_js.py`;
or set `RACHANA_DIST`).

    node bin/rachana.js new my-dash            # a Sutra, sample data, rachana.yaml
    node bin/rachana.js lint [paths] [--format text|json|sarif] [--strict]     # exit 0, 1 on errors, 2 usage
    node bin/rachana.js build --src my-dash --out site                         # static site (lints first)
    node bin/rachana.js preview my-dash/sutras/overview.sutra.yaml --data my-dash/data
    RACHANA_CLASSPATH=... node bin/rachana.js lib build|sign|keygen|pack|verify|lock ...

`lib` is the Java `LibraryTool` behind `com.ash.rachana.cli.Main` (one implementation): set `RACHANA_JAR` (a runnable jar) or
`RACHANA_CLASSPATH` (the module classes or jars). Tests: `npm test` (the lib test needs `./mvnw -pl rachana-cli -am compile` in `java/`).

**Not done yet:** `build` and `preview` write and serve the pages, the Sutras, the data files and a protocol index, but the painter
(`<rachana-view>`, branch `g2/data`, lane C) is not in this tree; the pages reference `rachana/rachana-view.js` and the commands warn
until it is in `dist`. `dev` is `preview` of a folder without live reload. `fix`, `fmt`, `explain`, `schema`, `migrate` are not
written. `lint` runs the bundle at language level 1.0 (the facade takes no level yet), so `data:` Sutras report `RCH-2008`.
