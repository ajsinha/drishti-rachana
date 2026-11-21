<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary. All rights reserved. -->
# Scope change: Rachana complete inside Drishti i7b (pointer)

**Status: proposed to the owner, 2026-11-19. Not yet decided.** Until it is,
[R0_R1_I8_PLAN](R0_R1_I8_PLAN.md) stands as written.

The owner has directed that Drishti release i7b become "the products, then Drishti proven on them": Drishti Elements
and Drishti Rachana complete as products in themselves, and Drishti migrated onto their released pins with published
evidence. The plan is one document in the Drishti repository, `docs/plans/I7B_PRODUCTS_PLAN.md` (branch
`wip/i7b-products`); it is the one home of the sizes, milestones and decisions. This page only says what it means here.

| In this repository | Was planned for | Now |
|---|---|---|
| R0: proofs P1, P2; harvest H1 to H8 | i7b | milestone M0 |
| The split, `v0.9.0` to `v0.11.0` ([SPLIT_PLAN](../design/SPLIT_PLAN.md), increments A1 to A16) | i8a | M1 |
| `data:`, the Data Protocol client, `<rachana-view>`, the CLI, the Python SDK, `v1.0.0` (B1 to B5, B9) | i8b | M2 |
| Rachana-EL X1 to X50 ([EL_EXPANSION](../design/EL_EXPANSION.md)) | 1.0 to 1.4 over several releases | levels 1.0 (M0), 1.1 (M2), 1.2 (M3), 1.3 (M5), 1.4 (M6) |
| The Sutra expansion and the compatibility regime ([SUTRA_EXPANSION](../design/SUTRA_EXPANSION.md)) | R1, R2, later | the regime in M0 and M1; the additions in the same four levels |
| The Sutra libraries: core, finance, genomics (`docs/design/LIBRARIES.md`, `LIBRARY_CATALOGUE.md`) | new | framework and core M3, finance M5, genomics M6 |
| WebAssembly for Python, the standalone server, Electron, Swing over JCEF, .NET over WebView2, Studio, editor support, the AI kit and MCP servers | R2 | M2 to M4 (the WebAssembly proof in M0) |
| Server halves of the events wire protocol (Java, Python, Node) | in the host | proposed here, under neutral coordinates |

Points of the proposal that change something written in this repository, once accepted:

1. One language numbering for both expansions: a level holds an EL phase and a group of Sutra additions.
2. The customer corpus of the compatibility regime is held in a private companion repository from the first day
   (this repository is public), replacing the recommendation of SUTRA_EXPANSION Q6.
3. No language change enters the compiler before `v0.10.0` is pinned by the first host (SPLIT_PLAN rule 4 stays).
4. ROADMAP R3's "CDN" is struck: release trees and packages only; public registries on the owner's word.
5. The three proofs (JavaScript, determinism, WebAssembly) all run in M0.

This page is deleted when the decision is taken and ROADMAP is rewritten from it.
