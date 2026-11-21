<!-- Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved. -->
# Rachana fragment libraries

Typed fragments, grouped by domain, built and released with Rachana. Each library is a folder here (`<name>.rachana-lib.yaml`,
`fragments/`, `examples/`, `goldens/`, a generated `README.md`).

| Library | Status | What it holds |
|---|---|---|
| [core](core/README.md) | built, 1.0.0 (33 fragments) | generic screens for any domain: figures, records, tables, basic charts, forms, states, history |
| [finance](finance/README.md) | built, 1.0.0 (50 fragments, two examples each) | trading, P&L, market risk, limits, counterparty and collateral, market data, funds; generic terms, every figure arrives as data |
| genomics | planned | |
| economy | planned | |
| geo | planned | |
| ops | planned | |
| ai | planned | |

Build, test, sign and pack: `python3 tools/libraries/build.py [--update-goldens] [LIBRARY ...]` (after `cd java && ./mvnw clean package -DskipTests`;
the jars come from this repository's build, nothing is installed). The check alone, `tools/libraries/LibCheck.java`, also runs
in the Maven build (`LibrariesCheckTest` in rachana-cli). `rachana studio` offers these libraries by default.

## Adding your own

The libraries here are one source among others, never the only one. Drop a library folder or an `.rlib` into a folder and name it:
`rachana lint|build|studio --libs <path>[,<path>]` (Python `rachana lint --libs` too), or `RACHANA_LIBS=<path>[:<path>]`, or
`<project>/rachana_libs`. Nothing in Rachana is modified or rebuilt. A name found twice with different contents is refused with a
problem, never silently shadowed (rename or remove one).

## Differences from the catalogue

Where a built fragment differs from its entry in `docs/design/LIBRARY_CATALOGUE.md` (until the compiler feature it waits for):

- `core/entry-form`, `core/stepped-form`: no `initial` (a form panel's `initial:` names fields, which a list parameter cannot
  know) and no draft kept between visits; `stepped-form` takes `steps: list<record{id, label}>` and fields that each name their
  `step` (a record inside a record is not a parameter type). Both take `rules` and name both buttons.
- `finance/order-ticket`: no `max-qty` (a form rule cannot read a parameter) and no slots; `side` is an expression
  (`"'BUY'"`); the ticket raises `submit {order}` with every field, `instrument` included.
- `finance/curve-table`: `df-fmt` defaults to `df4` (the catalogue's `df6` is no format name); `finance/order-ticket` and
  `finance/performance-chart` keep their own format parameters (no `price-fmt`, `frequency-fmt`, `level-fmt` or `growth-fmt`).
- `finance/what-if-shocks`: a form of number fields (`shocks`, each `{id, label, type: number, min, max}`) in place of sliders;
  `change {shocks}` hands the shocks to the screen, and the estimate is the host's (`estimate`), never computed by Rachana.
