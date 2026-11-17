<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary. All rights reserved. -->
# Decisions for the owner

Status: **open.** Each question has a recommendation; the design documents are written as if the recommendation is
accepted. Answer with the numbers you want changed.

## Already fixed (not asked again)

Name `drishti-rachana`; standalone repository; licence as Drishti Elements; Drishti is the first customer and
vendors pinned releases; usable from Java, JavaScript and Python; one compiler source (Java reference, JavaScript
build, WebAssembly build for Python), other languages proven by the conformance suite; open specification,
proprietary runtime; nothing Drishti-specific here (Drishti keeps a profile); generative UI built on Rachana, with a
tool pack for sajhamcpserver and a generic MCP server; UI speed budgets; the split is the first step of Drishti i8;
R0 during i7b; desktop hosts: Electron first, then Swing through JCEF, then .NET through WebView2.

## The Core and profile line

| # | Question | Recommendation | Why | If not |
|---|---|---|---|---|
| **D1** | Where exactly is the line between Sutra Core and the Drishti profile? | As the table in [CORE_AND_PROFILES](CORE_AND_PROFILES.md) section 1: Core is everything about a screen and rows (20 panel kinds, controls, events, forms without save, fragments, the expression language, list aggregates, binding). The profile is `match`/`domain`, kinds `links` and `provenance`, sources by entity kind, write-back, model functions, registry over packs, governance, context help, masking policy. | Most of what an author types is Core; what is left is "where the data comes from and who may see it", which is a host's business. | A thinner Core (expressions and parsing only) would leave the standalone product without a binder: not a product. |
| **D2** | Do forms belong in Core? | **Yes for entry** (fields, rules, hints, steps, drafts); **no for saving** (`form.save` is the profile's). Core raises a `submit` action; the host saves. | A dashboard product needs parameter entry; write-back needs connectors and approvals. | Keeping all of forms in the profile is simpler at the split and can be relaxed later; choose this if the split must be smaller. |
| **D3** | Is the list-aggregate kernel moved from Drishti's pivot engine into Core, with the engine then using Core's? | **Yes** ([CORE_AND_PROFILES](CORE_AND_PROFILES.md) 2.4). | Core cannot work without `sum`; a second implementation would break the one-way-per-job rule. | Core without aggregates until later (the Overview example would not run), or two implementations. |
| **D4** | Does the binder (Sutra + data → ViewModel) move into Core? | **Yes, in two steps after Drishti is already on a pin** (split steps 13, 14). | It is what makes Rachana a product; doing it after the pin keeps the i8 split safe. | Rachana ships only a compiler in R1 and binds in JavaScript by hand: dual machinery. |
| **D5** | Catalogue-driven inference (screens with no Sutra)? | **Stays in Drishti** for the split. A small shape-only inference for Core ("three lines give a screen") is a later Core feature, designed separately. | Inference today reads Drishti's catalogue and semantic hints. | Moving it now enlarges the riskiest part of i8. |

## Language and specification

| # | Question | Recommendation | Why |
|---|---|---|---|
| **D6** | Where does the ViewModel specification live: here or in Drishti Elements? | **Here** (`spec/1/viewmodel.schema.json`), with the Data Protocol and the Sutra schema. The **painter** stays in Drishti Elements (`drishti-views`, `<elements-view>`), vendored here at a pin. | The ViewModel is the compiler's output contract; one home per fact. The painter is Elements code and names no host. |
| **D7** | The element that takes a Sutra: name? | **`<rachana-view sutra="…" data="…">`** in this repository's npm package; it compiles, binds and hands the ViewModel to `<elements-view>`. (The earlier drafts used `<elements-view sutra=…>` and `<elements-sutra>`.) | Keeps Elements free of any Rachana dependency; the name says which product it is. |
| **D8** | Problem codes: keep `DRS-` or introduce `RCH-`? | **`RCH-` in Core with the same numbers** (`RCH-2023` is today's `DRS-2023`); Drishti's API keeps answering `DRS-` through a one-to-one mapping in its profile. | Nothing Drishti-specific here; no renumbering to learn; Drishti's documents and tests do not change. |
| **D9** | Source of truth for the language: the compiler's tables (today) or a hand-written definition file that generates the tables? | **The compiler's tables stay the truth for `rachana: 1`**; `rachana.lang.json` and the schema are generated from them and checked for staleness. | It works today and cannot drift; flipping the direction is a rewrite with no user benefit. |
| **D10** | The `data:` keys (this settles the Overview's guesses). | Top-level **`data:`**; an entry is `{ table: <name> }` (host-resolved, Data Protocol), `{ url }`, `{ rows }` or `{ record }`; each entry is a field of `$` (`rows: $.sales`, `sum($.sales, 'revenue')`). The earlier design's `ref`, `pages`, `server`, `topic`, `fn` source types are **not** added: a host exposes any of them as a named table. | `table` already exists as a source key; one way to name data; the same Sutra runs in every mode. |
| **D11** | May `version:` be left out (default 1) and `title:` be plain text? | **Yes to both**, as additions in language 1.1. | The first Sutra a newcomer or a model writes gets two lines shorter; existing files unaffected. |
| **D12** | `match` is required today. In Core? | **Not a Core key.** A Core Sutra has no `match`; in Drishti the profile keeps it required for entity screens. | Matching a record family is a platform concept. |
| **D13** | Major-version policy. | A runtime of major N reads N and N−1; N−1 supported at least 24 months; every breaking change ships with a `rachana migrate` rule; stricter checks are warnings for two minors first. | Customers' Sutras are assets; a break without a tool is not acceptable. |
| **D14** | Is the specification's own licence different from the runtime's? | **Yes: publish `spec/` and `conformance/` under a permissive specification licence** (free to read, implement and test against; the name "Rachana" only for implementations that pass). The runtime stays proprietary. Needs a short LICENSE-SPEC text; draft for the owner to approve. | "Open specification" has to be stated in the licence, or the public LICENSE (no rights granted) contradicts it. |

## Runtimes and packaging

| # | Question | Recommendation | Why |
|---|---|---|---|
| **D15** | JavaScript build tool. | **TeaVM**, after the one-week proof (T1 to T10); J2CL as the named alternative. | Pure Java build tool, works with only a JDK on Windows and Linux. |
| **D16** | If the proof fails on speed or size by a wide margin? | Fallback order in COMPILER_AND_RUNTIMES 4.3; the last one (a hand-written Core-only JavaScript evaluator held by conformance) **needs your explicit acceptance at that time**. | It would be two evaluators. |
| **D17** | WebAssembly route for Python. | **TeaVM's WebAssembly target run under `wasmtime`**, after its proof; fallback GraalVM native-image shared library; a JVM sidecar only as a development convenience. | One toolchain; no native code of ours to ship. |
| **D18** | Jackson and Spring leave Core; Core gets its own small YAML reader. | **Yes.** | Required for the JavaScript and WebAssembly builds; smaller jar. Risk covered by a differential test over every shipped Sutra. |
| **D19** | Java package and coordinates. | Package **`com.ash.rachana`**, Maven group `com.ash.rachana`, artifacts `rachana-api`, `rachana-core`, `rachana-tools`, `rachana-data`; npm **`drishti-rachana`**; PyPI **`drishti-rachana`** (import `drishti_rachana`). | The product name stays `drishti-rachana`; code names carry no product prefix that the "names no host" guard would trip on. |
| **D20** | Primary CLI. | **npm (Node) is primary** (needs no JVM, serves `dev` and `build`); PyPI and jar launchers expose the same commands where they apply; identical `lint --format json` output is a conformance case. | Analysts and web developers have Node or Python, rarely a JVM. |
| **D21** | Standalone server stack. | **Plain Java 21, no application framework**; configuration in one YAML file. Detailed design later (R2). | Small, starts fast, third consumer of the public API. |
| **D22** | Where do Drishti's Rachana jars come from? | A release location the owner controls (GitHub Releases of this repository at first); Drishti holds the jars as it holds its other pinned jars, version in one Maven property. Public registries (Maven Central, npm, PyPI) wait for R3. | No dependency on a public registry before the licensing tiers are decided. |

## Data, desktop and AI

| # | Question | Recommendation | Why |
|---|---|---|---|
| **D23** | Live transport. | **Reuse the events wire protocol v1** designed for Drishti Elements (topics `rdp.<table>`, message kind `rows`), plus a minimal read-only SSE form for hosts with nothing. | One live mechanism across Elements, Views and Rachana. Depends on that design shipping; re-check then. |
| **D24** | Filters sent to servers. | **Only as a JSON syntax tree** of a fixed push-down subset; never expression text. | Safe, checkable, translatable to SQL with bound parameters. |
| **D25** | Desktop: where does compilation happen? | **In the web view (JavaScript build) for every host**; a Java host may optionally bind in process. | One path; .NET needs no compiler. |
| **D26** | JavaFX WebView. | **Fallback only**, supported at "renders and updates", not at the performance budgets; JCEF is the Swing path. | Its engine trails current browsers. Confirm by a painter test run before promising anything. |
| **D27** | Native (non-web) painters. | **Out of scope**; the ViewModel specification being open is the answer for anyone who needs one. | A second grid, pivot and chart stack per toolkit is dual machinery. |
| **D28** | Where does Electron sit in the roadmap? | **First item of R2** (R2a), right after R1's JavaScript build, Views and Data Protocol exist; Swing/JCEF R2b; .NET/WebView2 R2c. | It needs all three R1 deliverables; putting it inside R1 would lengthen the first step of i8. |
| **D29** | In AI mode, may a model-written Sutra use `url:` sources? | **No**: handles only (`data_to_table`). | Data access stays with the host's tools and permissions. |
| **D30** | GenUI quality gate. | Ship the prompt kit only when first-try validity ≥ 85% and validity within three attempts ≥ 99% on the held-out tasks, for at least one current model; publish the figures with dates. | "It works" must be a number. Targets to confirm after the first run. |
| **D31** | Generic MCP server runtime. | **Node** (`npx drishti-rachana-mcp`), on the JavaScript build; the sajhamcpserver pack is Python on the WASM build. | The generic server must start with one command on any machine. |
| **D32** | Until the WASM build exists (R2), how does the sajhamcpserver tool pack validate? | It **waits for R2**; meanwhile the generic Node server can be proxied by SAJHA like any other MCP server. | Avoids a temporary JVM dependency in SAJHA. |

## Open points that need a measurement, not a decision

1. TeaVM's coverage of the JDK classes Core uses, especially `java.time` and regular expressions (proof T1).
2. Bundle sizes and evaluation speed of the JavaScript build (T2 to T4).
3. WebAssembly GC support in `wasmtime` for the chosen backend (proof W4).
4. How deep the binder's entanglement with links and masks really is (split step 13 starts with a one-day spike).
5. Whether the events design (branch `design/eventing`) ships unchanged; section 6 of the Data Protocol follows it.
