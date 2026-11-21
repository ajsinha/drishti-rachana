<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary. All rights reserved. -->
# js/

The npm workspace of Drishti Rachana's JavaScript packages (Node 20+). How to use them: [docs/guides/CLI_AND_SDKS.md](../docs/guides/CLI_AND_SDKS.md).

| Package | npm name | What |
|---|---|---|
| `packages/rachana` | `drishti-rachana` | the compiler bundles (`dist/`, built by `python3 tools/js/build_js.py`), the facade, `<rachana-view>`, the Data Protocol client (HTTP and the in-process bridge), the static-files host |
| `packages/rachana-cli` | `drishti-rachana-cli` | the `rachana` command: new, lint, preview, build, studio, lib, upgrade, serve |
| `packages/rachana-studio` | `drishti-rachana-studio` | Rachana Studio ([guide](../docs/guides/STUDIO.md)) |
| `packages/rachana-electron` | `drishti-rachana-electron` | placeholder for the Electron host SDK; no code yet |
| `packages/rachana-mcp` | `drishti-rachana-mcp` | placeholder for the Rachana MCP server; no code yet |
| `vendor/drishti-elements/<version>/` | (the pinned painter, copied unchanged) | Drishti Elements, with its SHA-384 manifest |

**One compiler.** The JavaScript compiler is generated from the Java reference (`java/rachana-js`, TeaVM) into `packages/rachana/dist`;
nothing in `js/` re-implements the language. The conformance suite proves the generated JavaScript matches the JVM byte for byte.

Tests: `java/mvnw -f js/pom.xml exec:exec@node-tests` (every `packages/*/test/*.test.mjs` through `node --test`), or the IDE launcher
**Rachana JS Node tests**.
