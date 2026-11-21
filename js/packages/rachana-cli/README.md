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

**`build` and `preview` use the real `<rachana-view>`** (`js/packages/rachana/src`, with the Drishti Elements 3.2.0-dev+475232e pack under
`js/vendor/drishti-elements/3.2.0-dev+475232e` and the runtime bundle from `dist`). A built site holds `rachana/src/*.js` (the element, the data
client, `static_files_host.js`), `rachana/rachana.runtime.js`, `vendor/drishti-elements/3.2.0-dev+475232e/`, one page per Sutra, the Sutras and
`data/`. With no server beside it, the page answers the Data Protocol itself from `data/index.json` and the table files
(`static_files_host.js`: schema, filter tree, sort, columns, cursor paging; no change stream); serve the folder with any static web
server (a browser does not fetch from `file://`). `test/paint_check.py`
(`uv run --with playwright==1.64.0 python js/packages/rachana-cli/test/paint_check.py`) builds `examples/static-sales`, opens every
page in Chromium and fails on a console error, a failed request or an empty page.

**`studio`** (`rachana studio [folder] [--libs <path>,...] [--data <dir>] [--port <n>]`) opens Rachana Studio on a folder of Sutras (127.0.0.1 only):
a palette of the fragments of the project's libraries (`<folder>/rachana_libs`, each `--libs` path: an unpacked library, a `.rlib`, or a folder of
them, and `RACHANA_LIBS`), the live preview, the Sutra source with the bundle's `check` at level 1.2 shown as you type, a parameter form that
inserts a `use:` block (tables and column mapping from the folder's data files), the unsaved changes as a diff, and Save into the folder (a
`*.sutra.yaml` path inside it; nothing else is written). The assistant bar is a placeholder until the GenAI release. Tests: `npm test` in
`js/packages/rachana-studio`; `uv run --with playwright==1.64.0 python js/packages/rachana-studio/test/studio_check.py` (uses the repository's libraries/
`RACHANA_LIBS` adds more).

**`--level`** (default `1.1`, the newest built level; `1.2` when typed fragments or libraries are used) is the language level of
`lint` (`since` in the bundle's `check`) and of the pages' `<rachana-view level>`.

**Not done yet:** `dev` is `preview` of a folder without live reload. `fix`, `fmt`, `explain`, `schema`, `migrate` are not written.
