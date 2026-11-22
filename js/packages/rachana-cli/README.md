<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary. All rights reserved. -->
# drishti-rachana-cli

The `rachana` command for Node (20+). Needs the compiler bundles in `js/packages/rachana/dist` (`python3 tools/js/build_js.py`;
or set `RACHANA_DIST`).

    node bin/rachana.js new my-dash            # a Sutra, sample data, rachana.yaml
    node bin/rachana.js lint [paths] [--format text|json|sarif] [--strict]  # exit 0, 1 on errors, 2 usage
    node bin/rachana.js build --src my-dash --out site               # static site (lints first)
    node bin/rachana.js preview my-dash/sutras/overview.sutra.yaml --data my-dash/data
    RACHANA_CLASSPATH=... node bin/rachana.js lib build|sign|keygen|pack|verify|lock|contracts|check-host ...
    RACHANA_SERVER_JAR=... node bin/rachana.js serve --config rachana-server.yaml [--port 0] [--check]   # the standalone server (JVM)

`serve` starts `java/rachana-server` (a JVM process: `--jar` or `RACHANA_SERVER_JAR`, the `-all` jar; `--check` prints the checked settings
and starts nothing). `lib` is the Java `LibraryTool` behind `com.ash.rachana.cli.Main` (one implementation): set `RACHANA_JAR` (a runnable jar) or
`RACHANA_CLASSPATH` (the module classes or jars). Tests: `npm test` (the lib test needs `./mvnw -pl rachana-cli -am compile` in `java/`).

**`build` and `preview` use the real `<rachana-view>`** (`js/packages/rachana/src`, with the Drishti Elements 3.3.0 pack under
`js/vendor/drishti-elements/3.3.0` and the runtime bundle from `dist`). A built site holds `rachana/src/*.js` (the element, the data
client, `static_files_host.js`), `rachana/rachana.runtime.js`, `vendor/drishti-elements/3.3.0/`, one page per Sutra, the Sutras and
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

**Language level.** Each Sutra declares its level in its `rachana:` key (SPEC 2.1: `rachana: 1` is 1.0, `rachana: 1.2` is 1.2); `lint`,
`build`, `preview`, Studio and `<rachana-view>` read every Sutra at its own level, and `rachana new` writes the newest. No command takes
`--level`. `rachana upgrade` rewrites a level-1.0 Sutra or fragment to a later level with the same meaning, reading each file's level from its own `rachana:` key (needs a JVM, as `lib` does).

`dev` is `preview` of a folder (no live reload). The guide: [docs/guides/CLI_AND_SDKS.md](../../../docs/guides/CLI_AND_SDKS.md).
