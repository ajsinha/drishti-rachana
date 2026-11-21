<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary. All rights reserved. -->
# drishti-rachana-cli

The `rachana` command for Node (20+). Needs the compiler bundles in `js/packages/rachana/dist` (`python3 tools/js/build_js.py`;
or set `RACHANA_DIST`).

    node bin/rachana.js new my-dash            # a Sutra, sample data, rachana.yaml
    node bin/rachana.js lint [paths] [--format text|json|sarif] [--strict] [--level 1.1|1.2]  # exit 0, 1 on errors, 2 usage
    node bin/rachana.js build --src my-dash --out site [--level 1.1]               # static site (lints first)
    node bin/rachana.js preview my-dash/sutras/overview.sutra.yaml --data my-dash/data
    RACHANA_CLASSPATH=... node bin/rachana.js lib build|sign|keygen|pack|verify|lock|contracts|check-host ...

`lib` is the Java `LibraryTool` behind `com.ash.rachana.cli.Main` (one implementation): set `RACHANA_JAR` (a runnable jar) or
`RACHANA_CLASSPATH` (the module classes or jars). Tests: `npm test` (the lib test needs `./mvnw -pl rachana-cli -am compile` in `java/`).

**`build` and `preview` use the real `<rachana-view>`** (`js/packages/rachana/src`, with the Drishti Elements 3.1.0 pack under
`js/vendor/drishti-elements/3.1.0` and the runtime bundle from `dist`). A built site holds `rachana/src/*.js` (the element, the data
client, `static_files_host.js`), `rachana/rachana.runtime.js`, `vendor/drishti-elements/3.1.0/`, one page per Sutra, the Sutras and
`data/`. With no server beside it, the page answers the Data Protocol itself from `data/index.json` and the table files
(`static_files_host.js`: schema, filter tree, sort, columns, cursor paging; no change stream); serve the folder with any static web
server (a browser does not fetch from `file://`). `test/paint_check.py`
(`uv run --with playwright==1.64.0 python js/packages/rachana-cli/test/paint_check.py`) builds `examples/static-sales`, opens every
page in Chromium and fails on a console error, a failed request or an empty page.

**`--level`** (default `1.1`, the newest built level; `1.2` when typed fragments or libraries are used) is the language level of
`lint` (`since` in the bundle's `check`) and of the pages' `<rachana-view level>`.

**Not done yet:** `dev` is `preview` of a folder without live reload. `fix`, `fmt`, `explain`, `schema`, `migrate` are not written.
