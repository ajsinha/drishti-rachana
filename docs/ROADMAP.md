# Drishti Rachana roadmap

Rachana is built alongside the Drishti product; each phase lands with a Drishti release, and Drishti is always the
first customer of what ships here.

| Phase | Lands with Drishti | What |
|---|---|---|
| **R0: the spec, written down** | i7b | EBNF for Rachana-EL (operators, precedence, types); the Sutra JSON Schema published as the standard, versioned; the conformance suite harvested from Drishti's existing Sutra tests; the Rachana Data Protocol v1 written down; the line between **Sutra Core** (portable) and the **Drishti profile** (Drishti's own kinds, matching, panels). |
| **R1: the split** | i8 | This repository takes the compiler with its git history (as Drishti Elements did); the reference compiler in Java plus its JavaScript build (after a short proof); Views (the painter); Data Protocol v1; the CLI and static mode; the **Python SDK with Flask and FastAPI adapters**. Drishti vendors pinned releases. |
| **R2: the product** | i8.x, i9 | WebAssembly build of the compiler for Python-side compile and lint (no JVM); the standalone server; editor support; playground and gallery; **GenAI writes Sutras** (i9). |
| **R3: distribution** | after i9 | npm, CDN and PyPI publishing; licensing tiers; native ports only where needed, proven by the conformance suite. |

## Rules

- Nothing Drishti-specific in this repository; Drishti's extras live in Drishti's profile.
- One compiler source; other languages run it (JavaScript, WebAssembly) or are proven against it (conformance suite).
- Open specification, proprietary runtime.
- UI speed is a feature: every release carries performance budgets.
