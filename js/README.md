# js/

The npm workspace of Drishti Rachana's JavaScript packages. **Status:** the compiler bundles and the `rachana` CLI (`packages/rachana-cli`) are built; the rest is planned.

| Package | npm name | When |
|---|---|---|
| `packages/rachana` | `drishti-rachana` | i8a (A15 JavaScript build), i8b (B2, B3) |
| `packages/rachana-cli` | `drishti-rachana-cli` | i8b (B4): new, lint, build, preview, lib |
| `packages/rachana-studio` | `drishti-rachana-studio` | G2: Rachana Studio, first slice |
| `packages/rachana-electron` | `drishti-rachana-electron` | R2a |
| `packages/rachana-mcp` | `drishti-rachana-mcp` | R2 with GenAI |
| `vendor/drishti-elements/<version>/` | (pinned painter, copied unchanged) | i8b |

**One compiler.** The JavaScript compiler is generated from the Java reference (`java/rachana-js`, TeaVM) into `packages/rachana/dist`; nothing in `js/` re-implements the language. The conformance suite proves the generated JavaScript matches the JVM byte for byte.
