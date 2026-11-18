<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary. All rights reserved. -->
# Sutra expansion: extremely complex screens, with zero effect on Drishti

Status: **design proposal for the owner (2026-11-18).** Nothing here is built. Syntax is a sketch: every key, word and
default below is settled only when it enters `spec/1/SPEC.md` with a `since` and conformance cases. Sizes are
builder-days for compiler, binder, painter, cases and documents together.

This document is about the **document model**: layout, composition, state, interaction, data declarations, forms,
panel kinds, and the rules that keep every addition invisible to a host that did not ask for it. The expression
language (new functions, new forms, typing) is [EL_EXPANSION](EL_EXPANSION.md)'s; where an addition here needs
something from expressions it says so and adds nothing itself.

Read with: [SPEC](../../spec/1/SPEC.md) (the language today), [TRANSLATION](../../spec/1/TRANSLATION.md),
[VIEWMODEL](../../spec/1/VIEWMODEL.md), [CORE_AND_PROFILES](CORE_AND_PROFILES.md), [SPLIT_PLAN](SPLIT_PLAN.md),
[R0_R1_I8_PLAN](../plans/R0_R1_I8_PLAN.md) (the pin, decisions N1 to N14), [DECISIONS](DECISIONS.md) (D1 to D32).

## Contents

1. [Vision](#1-vision)
2. [Where Sutra stands, and the gap](#2-where-sutra-stands-and-the-gap)
3. [The additions](#3-the-additions)
4. [A screen that needs most of it](#4-a-screen-that-needs-most-of-it)
5. [What is not added, and why](#5-what-is-not-added-and-why)
6. [The compatibility regime](#6-the-compatibility-regime)
7. [Roadmap and sizes](#7-roadmap-and-sizes)
8. [Decisions for the owner](#8-decisions-for-the-owner)

## 1. Vision

A Sutra today describes **one screen of panels over a record and tables**. That covers entity screens, dashboards and
drill tables. It does not yet cover a trading blotter with an order ticket, a cross-filtered analysis board, a
multi-step onboarding flow, or a launchpad of linked windows.

The expansion keeps one sentence true: **a Sutra is data that a compiler checks completely and a painter draws without
computing.** Complex screens are reached by adding *structure* (containers, overlays, components), *named state*
(screen state, selections, parameters), a *closed list of effects* (actions) and *more things to draw* (a chart
grammar, grid depth, more kinds). No addition gives a Sutra a loop, a callback, a style sheet or an address it builds.

And one sentence is new, and outranks every feature: **a host sees an addition only when it asks for it.** A host
states the language level it runs and the ViewModel contract its painter draws. Below that line Rachana behaves, byte
for byte, as the release the host first pinned. Drishti is the first host; the regime (section 6) is built so that no
Rachana change can alter a Drishti screen, a Drishti problem message or a Drishti API answer unless Drishti's own
release moves a number.

## 2. Where Sutra stands, and the gap

Today (`rachana: 1`, SPEC 2): title, strip (≤ 8), controls (10 kinds, ≤ 12), 20 Core panel kinds, a 12-column grid with
named areas, one split, tabs of an area, folding sections, phone and tablet pictures, events (`emits`, `waits`,
`children` to three levels, `on-select`), function keys F1 to F12, fragments with text parameters, forms (fields,
rules, hints, steps, drafts; `cells` and `beside` editing), `data:` entries (`table`, `url`, `rows`, `record`), live
patches, masked values, refresh.

| Need | Who sets the bar | Sutra today | Gap | Items |
|---|---|---|---|---|
| Nested layout: rows in columns in tabs, accordions, nested splitters | Retool, Appsmith, Lightning pages, Grafana rows | one level: areas, one split, area tabs, sections | no container that holds panels and has its own layout | L1 |
| Modals, drawers, popovers | Retool, Lightning, ServiceNow | only a form's New drawer and `children` places | no general overlay | L2 |
| Responsive beyond phone and tablet | Figma auto-layout, Grafana | `phone`, `tablet` | no wide tier, no container-width rule | L3, L5 |
| Docking, multi-window, linked windows | Bloomberg Launchpad, OpenFin desktops | one screen per Sutra | no document above the screen | L4 |
| Pixel-placed pages, print | Power BI report pages, Figma | grid units only | no canvas, no page rules | L6, L7 |
| Reusable components with typed inputs, slots, outputs | React, Lightning components, Vega-Lite `concat` | fragments: panels with `%{param}` text | no types, slots, outputs, controls or state in a fragment | C1 |
| Variants of one screen | Lightning record types, Power BI templates | copy the file | no inheritance | C2 |
| Theme and variants | Power BI themes, Grafana | tones and formats by name | no panel variants, no palette choice | C3 |
| Screen state that is not a control | Streamlit session state, Dash stores, React state | controls only; events are write-once reads | no named state, no derived names | S1, S2 |
| Deep links, saved views | Tableau, Power BI bookmarks, Grafana URLs | the host keeps controls in the address | not declared in the document | S1, S4 |
| Workflows, wizards, undo | ServiceNow flows, Lightning screen flows | form `steps` only | no bounded state machine | S3, S5 |
| A click that does several things | Retool event handlers, Power BI buttons | `emits`, `on-select` (set one control) | no list of effects, no host actions | I1 |
| Cross-filter, brush, linked selection | Tableau, Power BI, Vega-Lite selections | a click sets a control | no named selection, no brush, no multi-select | I2 |
| Bulk actions, optimistic edits | trading blotters, Salesforce list views | row edit, cell edit, drafts | no multi-row selection, no pending state | I3, I7 |
| Drill up, down and through on any visual | Power BI, Tableau | `table.levels`, pivot expand | not on charts; no drill-through with state | I4 |
| Context menus, key chords, drag and drop | Bloomberg, blotters, Figma | F1 to F12 | none of the three | I5, I6, I10 |
| Screen parameters | Power BI parameters, Grafana variables, BQL app inputs | the host's record `$` | no declared inputs for a table-only screen | D1 |
| Millions of rows in a grid | AG Grid server row model, Bloomberg blotters | `limit`, `more`, server push-down per panel | no windowed (virtual) binding | D2 |
| Joins and shaping in the document | Power Query, Tableau relationships, Grafana transforms | host exposes a joined table | no in-document derived table | D3 |
| Load order, caching, streaming detail | Grafana, Dash | `refresh`, `live` | no lazy panels, cache hints, tail or flash | D4, D5, D6 |
| Dependent fields, repeating and conditional sections, server validation | ServiceNow, Lightning, order tickets | rules, hints, steps in a small rules language | fields do not react; no lists of sub-forms; no host errors per field | F1 to F5 |
| Any chart: layered, dual axis, stacked, faceted | Vega-Lite, Power BI, Tableau | 12 fixed chart kinds | no composition of marks | V1 |
| Conditional formatting, data bars, icons, sparklines in cells | Excel, Power BI, Bloomberg | `tone`, `highlight`, `heat` | no rule list, no in-cell visuals | V2 |
| Grid depth: column groups, pinning, row groups with subtotals | AG Grid, blotters | plain columns, totals, trees | none | V3 |
| Treemap, sankey, gantt, box, funnel, calendar, map layers, 3D | Power BI, Tableau, Grafana | absent | absent | V4, V5, V6 |
| Accessibility and languages | WCAG 2.2, Lightning | the painter's defaults | nothing the author can say; one language | A1, A2 |
| Budgets per document | Grafana query limits, Power BI performance analyser | fixed limits per kind | no declared budget | B1 |
| Permissions per element | Lightning field-level security, Retool | masks (host), `editable-by` (profile) | no neutral "requires" on a panel, column or action | G1, G2 |
| Model-written and model-edited screens | GenUI, Copilot in Power BI | validate-and-fix loop, editing operations | no patch document, no outline, no streaming rule | M1 to M3 |
| Tests and lifecycle of a document | dbt tests, Storybook | conformance for the language only | no test beside a Sutra; no diff, no migrate | T1, T2, N1, N2 |

## 3. The additions

Columns of the tables: **P** priority (P1 needed for "extremely complex", P2 depth, P3 later comfort); **Days**
builder-days; **Rel** R1 (with Drishti i8), R2 (with i9), later; **Home** Core or profile. "ViewModel / painter" says
what the contract gains and what an older painter does: **ignore-safe** (the screen is still right without it) or
**refuse** (the compiler refuses the Sutra for a host whose painter lacks it, section 6.3). Every new key, kind and
option is absent from every existing Sutra, so it changes no existing output (section 6.1).

New names an expression may read (`state.`, `sel.`, `arg.`, top-level `let` names) are **context roots declared
here**; their typing, checked-mode rules and any function they need are EL_EXPANSION's.

### 3.1 Layout and containers

```yaml
panels:
  - id: risk                          # L1: a container. arrange: grid | row | column | tabs | accordion | split | steps
    kind: group
    arrange: split
    sizes: [7, 5]                     # grid units, as everywhere; never pixels
    panels:
      - { id: positions, kind: table, rows: $.positions, columns: [ ... ] }
      - id: detail
        kind: group
        arrange: tabs
        panels: [ { use: greeks@1 }, { id: hist, kind: line, rows: $.history } ]
  - id: by-desk                       # L1 with each: small multiples, one copy of the panels per element (at most 50)
    kind: group
    arrange: grid
    each: "group($.positions, 'desk', 'pnl')"
    panels: [ { id: desk-pnl, kind: metric, title: "${@.label}", value: "@.value", fmt: signed0 } ]
overlays:                             # L2: drawn over the screen, opened and closed only by actions (3.4)
  - { id: ticket, kind: drawer, side: right, size: 5, title: "Order ${state.order}", panels: [ { use: order-ticket@2 } ] }
  - { id: confirm-cancel, kind: modal, title: Cancel order, panels: [ { id: why, kind: markdown, text: "Cancel ${state.order}?" } ] }
layout:
  at:                                 # L3: container-width tiers; phone and tablet keep their meaning
    wide: { areas: [ "head head head", "book risk news" ], columns: [5, 4, 3] }
  density: compact                    # L5
```

```yaml
rachana: 1                            # L4: a separate document kind, <name>.workspace.yaml; a Sutra stays one screen
workspace: rates-desk
tiles:
  - { id: blotter, sutra: rates-blotter, channel: blue, dock: left, size: 7 }
  - { id: curve,   sutra: curve-viewer,  channel: blue, dock: right }
  - { id: news,    sutra: news-feed,     dock: bottom, floating-allowed: true }
channels: { blue: { carries: [instrument, book] } }   # a tile's state of that name follows the channel
```

| # | Addition | Semantics | ViewModel / painter | P | Days | Rel | Home |
|---|---|---|---|---|---|---|---|
| L1 | Container kind `group` (with `each`) | Holds panels; its own arrangement; nests to depth 4; ids stay unique on the screen; `each` binds the inner panels once per element with `@` the element (ids suffixed by index). A `steps` arrangement shows one inner panel at a time under a state name | new kind `group` with `data: {arrange, sizes, panels: [PanelView]}`, recursive as `tabs` already is; **refuse** | P1 | 10 | R2 | Core |
| L2 | `overlays:` (modal, drawer, popover) | Declared once; closed until an action opens one; its panels bind only while open; one modal at a time, drawers stack to 2 | `overlays[]` at the top level, left out when none; **refuse** | P1 | 6 | R2 | Core |
| L3 | `layout.at` tiers | Named tiers by **container** width (`narrow`, `medium`, `wide`, `ultra`; bounds are the painter's tokens); each tier may restate `areas`, `columns`, order, and hide panels | `layout.at`; **ignore-safe** (the base layout is drawn) | P2 | 3 | later | Core |
| L4 | Workspace document | Arranges several Sutras as docked tiles, tabs or windows; **channels** carry named state between tiles (the launchpad's linked groups). Each tile is compiled and bound as an ordinary Sutra. Windows are the host's (desktop hosts, COMPILER_AND_RUNTIMES 9) | a separate `Workspace` document with its own contract; a new painter element; a Sutra's ViewModel is unchanged | P2 | 12 | later | Core |
| L5 | Sizing and density | `height: fill`, `min-height`, `gap`, `density` | fields on panel and layout; **ignore-safe** | P2 | 2 | later | Core |
| L6 | Canvas layout | `layout.canvas: {width, height}` and `at: {x, y, w, h, z}` per panel in canvas units, scaled to fit; for report pages | `layout.canvas`; **refuse** | P3 | 5 | later | Core |
| L7 | Print rules | `print: {page, orientation, break-before: [ids], repeat-header}` | `print`; **ignore-safe** | P3 | 4 | later | Core |

### 3.2 Composition

```yaml
rachana: 1                            # C1: a fragment grows into a component. Text parameters (%{x}) keep working
block: order-ticket
version: 2
params:
  side:   { type: text, enum: [BUY, SELL], default: BUY }
  book:   { type: text, required: true }
slots:  { footer: { doc: "panels under the form" } }
emits:  [ { name: placed, payload: { id: text } } ]
state:  { qty: { type: number, default: 0 } }          # private to each use
panels:
  - { id: entry, kind: kv, form: { ... } }
  - { slot: footer }
---
# in a Sutra (shown as a second file)
panels:
  - use: order-ticket@2
    as: tk                             # ids become tk.entry; state tk.qty; events @tk.placed
    with: { side: SELL, book: "arg.book" }
    slots: { footer: [ { id: limits, kind: status, fields: [ ... ] } ] }
extends: trade-base@3                  # C2
override:
  panels: { legs: { limit: 20 } }      # set options of an inherited panel
  remove: [ provenance ]
  insert: [ { after: legs, panels: [ { id: fixings, kind: table, rows: $.FIXINGS, columns: [ ... ] } ] } ]
theme: { palette: categorical-2, density: compact }     # C3: names from the painter's tables, never a colour
```

| # | Addition | Semantics | ViewModel / painter | P | Days | Rel | Home |
|---|---|---|---|---|---|---|---|
| C1 | Components | Typed parameters (checked at the use; an expression parameter is compiled in the user's context), slots, declared events, private state, controls and overlays inside a block, `as:` namespacing. Expansion stays a compile-time step: the output never shows a component | none (expanded before binding, TRANSLATION T1) | P1 | 8 | R2 | Core; where blocks come from stays the host's resolver |
| C2 | `extends` and `override` | The base is loaded, then the override is applied as a fixed list of the existing editing operations (set option, remove, insert, move). One level of inheritance per file, chain depth ≤ 4; the result is checked as a whole | none | P2 | 6 | later | Core |
| C3 | Theme and variants | `theme:` picks named palette and density; a panel takes `variant: card | plain | hero | flush`. Names only; the painter's theme tokens decide | `theme`, panel `variant`; **ignore-safe** | P2 | 4 | later | Core; palettes are the painter's |

### 3.3 State

```yaml
args:                                  # D1 (3.5): what the opener passes; read as arg.<id>
  book: { type: text, required: true }
state:                                 # S1: named values with no widget; read as state.<id>; written only by actions
  order:   { type: text, url: true }                  # url: kept in the address (a deep link restores it)
  compare: { type: boolean, default: false, remember: user }
let:                                   # S2: named expressions, evaluated once per bind; no cycles (compile error)
  open-orders: "$.orders[?@.status == 'OPEN' && @.book == arg.book]"
  exposure:    "sum(open-orders, 'notional')"
flow:                                  # S3: one bounded state machine per screen or component
  id: stage
  states: [ draft, review, sent ]
  initial: draft
  transitions:
    - { from: draft,  to: review, on: next, when: "exposure <= $.limit" }
    - { from: review, to: draft,  on: back }
    - { from: review, to: sent,   on: submit }
```

| # | Addition | Semantics | ViewModel / painter | P | Days | Rel | Home |
|---|---|---|---|---|---|---|---|
| S1 | `state:` with `url` and `remember` | Typed (`text`, `number`, `boolean`, `date`, `list`), default, at most 40 per screen. Only an action writes it. `url: true` puts it in the view-state document (S4) the host writes into the address; `remember: user` is kept by the host per reader, as control values are. Controls gain the same `url` word | `state[] {id, value, url, reads}` left out when none; like `controls`, `reads` names the panels to rebuild; **refuse** | P1 | 5 | R2 | Core |
| S2 | Top-level `let:` | Names bound in written order, each may read earlier ones; memoised per bind; a name shadows nothing (a clash with a `data:` entry or control is a compile error) | none (values are inlined at bind) | P1 | 3 | R2 | Core |
| S3 | `flow:` state machine, wizards | Flat (no nested or parallel states), ≤ 32 states, ≤ 128 transitions; a transition fires on a named action when its `when` holds; the current state is a state value (`state.stage`); reachability and dead states are checked. A wizard is a `flow` plus a `group` with `arrange: steps` | `flow {id, state, can: [action names]}`; **refuse** | P2 | 8 | later | Core |
| S4 | View-state document | One canonical JSON of controls, state, selections and column filters, with the Sutra name and version; hosts save it as a bookmark, a share or a link. Defined so every runtime writes the same bytes | a small schema beside the ViewModel; nothing in it | P2 | 2 | later | Core; storage is the host's |
| S5 | Undo and redo | `history: true`: the painter keeps the last 100 view-state changes and offers undo; never across a `submit` or host action | `history` flag; **ignore-safe** | P3 | 3 | later | Core |

### 3.4 Interaction

```yaml
selections:                            # I2: a named selection; read as sel.<id> (a list of values, or a from..to range)
  desk:   { field: desk, multi: true }
  window: { field: date, range: true }
panels:
  - id: by-desk
    kind: hbar
    rows: "group($.positions, 'desk', 'pnl')"
    selects: desk                      # a click, shift-click or lasso feeds the selection
  - id: pnl-line
    kind: line
    rows: $.daily
    brush: { x: window }               # dragging over the x axis feeds a range selection
    filter-by: [ desk ]                # rows are kept by one_of(@.desk, sel.desk); highlight-by dims instead
  - id: orders
    kind: table
    rows: open-orders
    select: multi                      # I3: tick boxes; the chosen keys are sel.orders
    on:                                # I1: triggers and a list of effects, run in order
      row-click:  [ { set: { order: "@.id" } }, { open: ticket } ]
      row-menu:                        # I5: a context menu is a list of labelled action lists
        - { label: Amend,  do: [ { set: { order: "@.id" } }, { open: ticket } ] }
        - { label: Cancel, requires: order.cancel, confirm: "Cancel ${@.id}?",
            do: [ { host: order.cancel, with: { id: "@.id" }, optimistic: { status: "'CANCELLING'" } } ] }
    bulk:
      - { label: "Cancel ${size(sel.orders)} orders", requires: order.cancel, confirm: true,
          do: [ { host: order.cancel-many, with: { ids: sel.orders } } ] }
    drill: { through: { sutra: order-history, args: { id: "@.id" } } }      # I4
keys:
  "Ctrl+N": [ { set: { order: null } }, { open: ticket } ]                  # I6
  Escape:   [ { close: ticket } ]
```

The effects are a **closed list**: `set` (state or a control), `toggle`, `clear` (a selection), `open`, `close`, `go`
(a link, or another Sutra with `args`), `fire` (a flow or component event), `submit` (a form), `refresh`, `export`,
`copy`, `notify`, and `host` (a name from the host's action catalogue, with a payload of expressions). An effect may
carry `when`. Effects run in order and stop at the first that fails or is refused. There is no branching beyond `when`,
no loop, no effect that produces a value for the next one.

| # | Addition | Semantics | ViewModel / painter | P | Days | Rel | Home |
|---|---|---|---|---|---|---|---|
| I1 | Actions: `on:`, `actions:` (buttons), the effect list | Triggers per kind (`row-click`, `row-dblclick`, `bar-click`, `point-click`, `cell-click`, `change`, `open`, `close`). Payload expressions are evaluated by the runtime when the trigger fires, with `@` the row. `emits` and `on-select` keep their meaning and their output; internally they are read into the same model, so there is one mechanism | `actions[]` per panel and view: `{id, label, tone, requires?, confirm?, effects}` with payloads already evaluated per row where they depend on it (`ev` index, as today); **refuse** | P1 | 8 | R2 | Core; `host` names are the host's catalogue (declared in its profile) |
| I2 | `selections:`, `selects`, `brush`, `filter-by`, `highlight-by` | A selection is state with a field: panels feed it and panels read it. Over a client table the runtime re-binds only the readers; over a server table the reader's plan gains the filter (push-down subset) | `selections[] {id, values, reads}`; marks carry `ev` as today; **refuse** | P1 | 8 | R2 | Core |
| I3 | `select: multi` and `bulk:` | Row keys chosen in a table (needs the table's `key`); `bulk` actions read `sel.<panel>`; at most 10,000 keys | `Table.select`, `bulk[]`; **refuse** | P1 | 3 | R2 | Core |
| I4 | `drill:` on charts and pivots | `levels: [region, country, city]` regroups in place with a crumb (as `table.levels` does); `through:` opens another Sutra with `args` | `drill {levels, at, crumb}`; **refuse** | P2 | 5 | later | Core |
| I5 | Context menus | `row-menu`, `cell-menu`, `panel-menu`: labelled action lists, each with `when` and `requires` | `menus`; **ignore-safe** only when every entry is also reachable otherwise, else **refuse** | P2 | 3 | later | Core |
| I6 | Key map beyond F-keys | Chords (`Ctrl+N`, `Alt+1`, `g then o`), scoped to the view or a panel; a host's reserved keys are refused at compile time | `keys[]` gains chord entries; an older painter ignores a chord it cannot bind (**ignore-safe**) | P2 | 3 | later | Core |
| I7 | Optimistic host actions | `optimistic:` field values shown at once, marked pending; the host answers `ok` (the live patch confirms) or `rejected` with words (the painter restores and says why) | cell state `pending`; an action result shape; **refuse** | P2 | 3 | later | Core; the answer is the host's |
| I8 | Tips and hover cards | `tip:` template on a column or mark; `hover: {panels}` a small popover bound on demand | `tip`, `hover`; **ignore-safe** | P2 | 2 | later | Core |
| I9 | Alerts | `alerts: [{when, tone, text, actions?}]` as a banner; `notify` effect for a toast | `alerts[]`; **ignore-safe** | P2 | 2 | later | Core |
| I10 | Drag and drop | `drag: {kind, payload}` on rows; `drop: {accepts, do}` on panels: a drop is a trigger with the payload as `@drop` | `drag`, `drop`; **ignore-safe** only with a menu equivalent | P3 | 6 | later | Core |

### 3.5 Data

```yaml
data:
  orders:    { table: orders, key: id, live: true, args: { book: "arg.book" },    # D1: named arguments of a host table
               window: 200 }                                                      # D2: never read whole; bound by window
  positions: { table: positions, key: [book, instrument], cache: { ttl: 30, serve-stale: true } }   # D5
  limits:    { table: limits }
  book-view:                                                                      # D3: a derived table, a fixed pipeline
    from: positions
    join:  { with: limits, on: { book: book }, kind: left, take: [ limit ] }
    where: "@.desk == ctl.desk"
    group: { by: [ book ], take: { exposure: "sum(@.notional)", limit: "first(@.limit)" } }
    sort:  [ { by: exposure, desc: true } ]
panels:
  - { id: tape, kind: table, rows: $.orders, load: visible, flash: changes, tail: 500, columns: [ ... ] }   # D4, D6
```

| # | Addition | Semantics | ViewModel / painter | P | Days | Rel | Home |
|---|---|---|---|---|---|---|---|
| D1 | `args:` and table `args` | Declared, typed inputs of a screen (from the opener, a link, a workspace channel, the host); a missing required one is a state with words, not a crash. A table entry may pass named arguments the host's `TableProvider` receives (never an address, D10) | `args[]` echoed for the view-state document; none otherwise | P1 | 3 | R2 | Core |
| D2 | Windowed tables | A table marked `window` (or `server` by the host) is bound a window at a time: sort, filter and group go to the provider; the painter asks for `{panel, offset, size}` and gets rows already formatted. In-browser runtimes bind the page they fetched; a server host answers the same request | `Table.window {offset, size, total, more}` and one request in the event round trip; **refuse** | P1 | 10 | later | Core; Data Protocol unchanged (paging exists) |
| D3 | Derived tables | A fixed order of steps: `from`, `join` (≤ 3, equi-joins, `left` or `inner`), `where`, `group`, `sort`, `limit`. Pushed to the provider when it can; else computed by the runtime under the client threshold; above it a compile-time refusal (`RCH-2074`), never a silent slow path | none (it is one more field of `$`) | P2 | 8 | later | Core |
| D4 | Lazy panels | `load: eager | visible | idle`; a deferred panel arrives as a patch | panel state `pending`; an older painter shows the frame until the patch (**ignore-safe**) | P1 | 3 | R2 | Core |
| D5 | Cache hints | `cache: {ttl, serve-stale}` per entry: a hint the host may honour; never changes a value's meaning | none | P2 | 3 | later | Core |
| D6 | Streaming detail | `tail: n` (an appending table keeps the last n), `flash: changes`, `throttle: ms` per panel | `flash`, `tail`; **ignore-safe** | P2 | 4 | later | Core |

### 3.6 Forms

```yaml
form:
  new: drawer
  fields:
    - { id: product, type: enum, options: [ SWAP, FRA, CAP ] }
    - { id: index, type: lookup, options: { from: "$.indices[?@.product == form.product]", value: id, label: name } }  # F2
    - { id: strike, type: number, when: "form.product == 'CAP'", required-when: "form.product == 'CAP'" }               # F2
    - id: legs                                   # F3: a repeating section
      type: list
      min: 1
      max: 8
      fields: [ { id: notional, type: money, currency: ccy }, { id: ccy, type: enum, options: [ USD, EUR ] } ]
  sections: [ { id: terms, title: Terms }, { id: fees, title: Fees, when: "form.product != 'FRA'" } ]                   # F4
  checks: [ { check: "sum(form.legs, 'notional') <= $.limit", message: "Over the book limit", fields: [ legs ] } ]      # F2
  validate: host                                 # F1: the host answers [{field, code, message}] and the form shows them
```

| # | Addition | Semantics | ViewModel / painter | P | Days | Rel | Home |
|---|---|---|---|---|---|---|---|
| F1 | Host validation | On `submit` (or on blur with `validate: {on: change}`) the host answers field problems in one fixed shape; shown beside the fields | a result shape for the `submit` action; **ignore-safe** (problems shown as one message) | P1 | 2 | R2 | Core; the checks are the host's |
| F2 | Dependent fields; `checks` in Rachana-EL | `when`, `enabled-when`, `required-when`, options filtered by other fields, all over `form.<field>`. `checks:` are Rachana-EL, evaluated by the same compiled runtime in the browser and on the server. The older `rules:` small language stays read for all of major 1 (a deprecation with a `migrate` rule) | field states already exist (`readonly`, `locked`, `reason`); `visible`, `required` become per-state; **refuse** | P2 | 5 | later | Core |
| F3 | Repeating sections | `type: list` with sub-fields, `min`, `max` (≤ 50 items) | `FormField.items`; **refuse** | P2 | 5 | later | Core |
| F4 | Conditional sections, step rules | `sections` with `when`; a step with `when` and its own checks; a review step | `form.sections`; **refuse** | P2 | 4 | later | Core |
| F5 | Field types | `radio`, `checkboxes`, `slider`, `time`, `duration`, `tags`, `file` (a host handle, never content in the document) | new `type` words; an older painter draws a text input (**ignore-safe**), `file` **refuse** | P2 | 3 | later | Core |

### 3.7 Visuals

```yaml
- id: pnl-vs-var
  kind: chart                          # V1: layers of marks on shared scales
  rows: $.daily
  x: { field: date, scale: time }
  layers:
    - { mark: bar,  y: pnl, tone: sign, stack: none }
    - { mark: line, y: var99, axis: right, tone: warn }
    - { mark: rule, y: "0" }
    - { mark: band, from: "-$.limit", to: "$.limit", tone: muted }
  facet: { by: desk, columns: 3 }
  legend: bottom
- id: orders
  kind: table
  rows: $.orders
  group-by: [ book ]                   # V3: row groups with subtotals
  column-groups: [ { title: Price, columns: [ limit, last, away ] } ]
  pin: { left: [ id ] }
  columns:
    - { id: away, label: Away, bind: "@.last - @.limit", fmt: price4,
        rules: [ { when: "abs(@.last - @.limit) < 0.01", tone: warn, icon: alert }, { bar: { max: 1 } } ] }   # V2
    - { id: trend, label: Trend, spark: "@.ticks", shape: line }                                            # V2
```

| # | Addition | Semantics | ViewModel / painter | P | Days | Rel | Home |
|---|---|---|---|---|---|---|---|
| V1 | Kind `chart`: a closed mark grammar | Marks `bar`, `line`, `area`, `point`, `rule`, `band`, `text`, `tick`; shared `x`, per-layer `y`; `stack: none | sum | percent`; `axis: left | right`; scales `linear`, `log`, `time`, `band`; `facet`; `color` and `size` by field; sorting; annotations. The twelve existing chart kinds stay as they are, with their own output, for ever | new kind with `data: {x, layers, axes, legend, facets}` of drawable numbers and texts; **refuse** | P1 | 15 | later | Core |
| V2 | Cell rules and in-cell visuals | `rules:` first match wins: `tone`, `icon` (a name from the painter's icon table), `bar`, `scale` (a colour scale by name); `spark:` a small line or bar from a list | `Cell.icon`, `bar`, `spark`; **ignore-safe** (text and tone still say it) | P1 | 5 | R2 | Core; icon names are the painter's table |
| V3 | Grid depth | Column `id`, `width`, `align`, `wrap`; `column-groups`; `pin`; `group-by` with subtotals; multi-sort; a second line in a cell (`sub:`) | `Table.groups`, `pin`, `bands`; **ignore-safe** except `group-by` (**refuse**) | P1 | 8 | R2 | Core |
| V4 | More kinds, in three batches | `treemap`, `sunburst`, `sankey`, `funnel`; `box`, `bullet`, `radar`, `calendar`; `gantt`, `tree`, `image` (an address from the host's allow-list) | one data record per kind; **refuse** | P2 | 12 | later | Core |
| V5 | Map layers | `layers:` of points, bubbles, lines and shaded regions over rows of the screen; admin-1 regions; no tile servers (shapes stay vendored) | `GeoMap.layers`; **refuse** | P2 | 6 | later | Core |
| V6 | 3D views | `surface` and `scatter` with `view: 3d` (rotate, slice) | a `view` word; an older painter draws the 2D view (**ignore-safe**) | P3 | 5 | later | Core |
| V7 | Rich text | `markdown` with `rich: true`: headings, lists, emphasis, tables and `link()` values; still never HTML | `Text.blocks`; an older painter prints the text (**ignore-safe**) | P3 | 2 | later | Core |

### 3.8 Accessibility, languages, budgets, permissions

```yaml
a11y: { label: "Rates blotter", lang: en, heading-level: 2 }                   # A1
messages: { en: { cancel: "Cancel order" }, fr: { cancel: "Annuler l'ordre" } }  # A2; read as t('cancel')
budget: { panels: 40, bound-rows: 200000, payload-kb: 512, bind-ms: 50 }       # B1
panels:
  - { id: pnl-line, kind: line, rows: $.daily, a11y: { summary: "P&L by day against the 99% VaR", live: polite } }
  - { id: cpty, kind: kv, requires: client.view, columns: [ { label: Tax id, bind: "$.tax_id", sensitive: true } ] }   # G1, G2
```

| # | Addition | Semantics | ViewModel / painter | P | Days | Rel | Home |
|---|---|---|---|---|---|---|---|
| A1 | `a11y:` on view and panel | Label, description, summary (a template), live politeness, language, direction, heading level; a lint warning for a chart without a summary | `a11y` as the Views design section 6.3 names it; **ignore-safe** | P1 | 3 | R1 | Core |
| A2 | Languages | A message table in the file or beside it (`<name>.messages.<lang>.yaml`); `t('key', …)`; the locale comes from the host through the context (determinism holds: no locale given means today's text); right-to-left by `dir` | none (text is resolved at bind) | P2 | 8 | later | Core; `t` is specified in EL_EXPANSION |
| B1 | `budget:` | Static limits (panels, expressions, nesting, declared rows) are checked at compile time; measured ones (bind time, payload) are reported in `timings` and by `rachana explain --cost`. Over budget is a warning by default, an error where the host says so | none (`timings` is outside conformance) | P2 | 4 | later | Core |
| G1 | `requires:` on a panel, column, field, action, overlay | A **permission name**. The host's `Permissions` hook answers per reader. Refused: the element is left out (or `denied`, with words, when the author sets `show-denied`). Core knows no role and no user | none new (`denied` exists); **ignore-safe** | P1 | 3 | R2 | Core; the answer is the host's. Roles (`editable-by`) stay the profile's |
| G2 | `sensitive`, `export: false`, `copy: false` | Hints carried to the painter and the host's export: a sensitive column is hidden until asked for (a host action), and never exported | `Cell.sensitive`, column flags; **ignore-safe** is not enough for `export: false`: enforced by the host's export, not the painter | P2 | 2 | later | Core |

Masking and row rules stay where they are: decided by the host where the data lives (`Masker`, `TableProvider`),
never by the document (CORE_AND_PROFILES 3.5).

### 3.9 AI, tests, document versions

```yaml
# M1: a patch document. Addresses are by id, never by index, so two patches commute when they touch different things
patch: rates-blotter@4
ops:
  - { op: add-panel, after: orders, panel: { id: fills, kind: table, rows: $.fills, columns: [ ... ] } }
  - { op: set, at: "panels[orders].columns[away].fmt", value: price6 }
  - { op: remove, at: "panels[news]" }
---
# T1: rates-blotter.sutra.test.yaml, beside the Sutra; never inside it
tests:
  - name: cancel is offered only for open orders
    given: { data: { orders: orders-small.json }, args: { book: RATES-1 }, asof: "2026-11-18" }
    do:    [ { trigger: row-menu, panel: orders, row: { id: O-7 } } ]
    expect:
      - { at: "panels[orders].rows[id=O-7].cells[status]", text: OPEN }
      - { at: "menus[orders]", has: { label: Cancel } }
      - { problems: [] }
```

| # | Addition | Semantics | Output | P | Days | Rel | Home |
|---|---|---|---|---|---|---|---|
| M1 | Patch documents | The existing editing operations (`conformance/1/edit`) as one document: a closed op list, id-addressed, applied atomically, comments and layout kept, then validated; the result is the new text or the problem list. A model edits a 60-panel screen by sending 300 bytes | the `edit` case kind grows `patch` cases | P1 | 5 | R2 | Core |
| M2 | `rachana describe` | A compact outline (ids, kinds, titles, what each reads, state, actions) under 2 KB for a typical Sutra: what a model reads before patching | CLI and API; a `describe` case kind | P1 | 2 | R1 | Core |
| M3 | Streaming generation | A defined prefix rule: a Sutra is drawable as soon as its head (identity, `data`, `args`, `state`) is complete; each further panel is a `panel-add`. Lets a host paint a model's answer while it is written, links intact | none new (the stream's `panel-add` exists in the transport) | P2 | 4 | later | Core |
| T1 | Document tests | Given data, args, control values, state and an as-of date; optional triggers; partial expectations by id-addressed paths, or a whole-ViewModel snapshot. `rachana test`; runs in every runtime | results as conformance results | P1 | 5 | R1 | Core |
| T2 | Visual snapshots | `rachana test --paint`: the painter's DOM snapshot per test, for a host that wants pictures pinned | painter tooling | P3 | 3 | later | Elements |
| N1 | `rachana migrate` | The rule engine of section 6.7, with `--check` for a build | CLI | P1 | 3 | R2 | Core |
| N2 | Lifecycle and diff | `status: draft | active | deprecated`, `replaces:`; `rachana diff a b` in words (panels added, a binding changed), for review and for release notes | `status` in provenance (**ignore-safe**) | P2 | 3 | later | Core |

The language minor a file needs is never written by hand: the compiler knows it from the names used (`rachana
explain --needs`), and section 6.2 says how a host bounds it.

## 4. A screen that needs most of it

A rates blotter: live orders in a grouped grid of a million rows, a cross-filter by desk, an order ticket in a drawer,
bulk cancel with a confirmation, a deep link to one order. Every construct is from section 3; nothing is code.

```yaml
rachana: 1
sutra: rates-blotter
title: "Rates blotter"
args:  { book: { type: text, required: true } }
state: { order: { type: text, url: true } }
selections: { desk: { field: desk, multi: true } }
data:
  orders: { table: orders, key: id, live: true, window: 200, args: { book: "arg.book" } }
  desks:  { table: desk-pnl, key: desk, live: true }
let:   { chosen: "size(sel.orders)" }
overlays:
  - { id: ticket, kind: drawer, size: 5, panels: [ { use: order-ticket@2, as: tk, with: { book: "arg.book" } } ] }
panels:
  - { id: by-desk, kind: hbar, rows: $.desks, label: desk, value: pnl, fmt: signed0, selects: desk, span: 4,
      a11y: { summary: "P&L by desk; choose desks to filter the orders" } }
  - id: orders
    kind: table
    rows: $.orders
    span: 8
    filter-by: [ desk ]
    group-by: [ status ]
    select: multi
    flash: changes
    columns:
      - { id: id, label: Order, bind: "@.id" }
      - { id: qty, label: Qty, bind: "@.qty", fmt: amount0, total: true }
      - { id: away, label: Away, bind: "@.last - @.limit", fmt: price4, rules: [ { when: "abs(@.last - @.limit) < 0.01", tone: warn } ] }
    on: { row-click: [ { set: { order: "@.id" } }, { open: ticket } ] }
    bulk: [ { label: "Cancel ${chosen}", requires: order.cancel, confirm: true, do: [ { host: order.cancel-many, with: { ids: sel.orders } } ] } ]
keys: { "Ctrl+N": [ { set: { order: null } }, { open: ticket } ] }
```

What the host supplies: the two tables, the `order.cancel-many` action, the answer to `requires`, and where the
address and reader preferences are kept. What stays impossible: reaching another table, sending an order without the
host's action, or showing a masked field.

## 5. What is not added, and why

| Not added | Why | What is offered instead |
|---|---|---|
| Scripts, callbacks, user-defined functions with bodies, loops, recursion | A Sutra must terminate, be checked completely, be safe to accept from a model, and mean the same in three runtimes. One script ends all four | the closed effect list (3.4), `let`, `flow`, components; a host registers a function or an action in its own language |
| Arbitrary HTML, CSS, class names, pixel styles, colours | The painter cannot keep text safe, themes consistent or accessibility true over author markup; a ViewModel never names a colour | tones, variants, palettes and icons by name; `markdown` stays text |
| Addresses built by expressions; fetch from an action | Data exfiltration and server-side request forgery; D10 and D29 already forbid it | `table:` names with `args`; `go` to a link the host resolves |
| Writes to data from the document | Write-back needs connectors, approvals and audit: the host's (D2) | `submit` and `host` effects with typed payloads |
| Row-level security, roles, users in Core | Authorisation is decided where the data lives (CORE_AND_PROFILES 3.5); a document that could state it could weaken it | `requires:` a name; masks from the host |
| A general constraint or layout solver; z-order scripting | Unbounded cost and unpredictable results on a phone | grid areas, containers, tiers, a bounded canvas |
| Nested or parallel statecharts, timers in flows, effects that wait | Verification stops being cheap; timing belongs to the host | one flat `flow`; `refresh`; live tables |
| Client-side joins above the threshold; cross joins; free-form SQL | A silent slow path; injection | derived tables with a refusal above the threshold; the host exposes a joined table |
| A second way to do what exists (a new `tabs`, a new `emits`) | One way per job. Where an addition generalises an old key, the old key keeps its meaning and output and is read into the same mechanism | `rachana migrate` rules to the newer spelling, applied only when a host raises its level |
| Bidirectional binding between panels; computed controls that write state | Cycles and order dependence | state is written only by effects; values are derived only by `let` |
| Plugin code inside a document (a panel's own renderer by address) | Supply-chain and CSP hazard | a host registers a kind with the painter and a profile (CORE_AND_PROFILES 3.2) |
| Changing any default, limit or heuristic of `rachana: 1` | It would change an existing screen (6.1 rule 3) | a new option, or major 2 |

Bounds that keep the model terminating (defaults; a host may lower them): container depth 4; `each` copies 50; overlays
12; state names 40; selections 16; `let` names 63; effects per trigger 12; flow states 32; derived-table joins 3;
component nesting 4; inheritance chain 4; panels after expansion 200.

## 6. The compatibility regime

The promise, in one line: **for a host that changes nothing in its own configuration, replacing one Rachana release
by any later one of the same major changes no byte of what its users see or its API answers.** It holds from the
first pinned release, `v0.9.0`, although that number is below 1.0.

### 6.1 The rules

1. **Additive only.** Within `rachana: 1` a release only adds names: keys, kinds, options, words, functions, formats,
   tones, effects, problem codes, ViewModel fields. Each carries `since` (SPEC 7.2).
2. **Invisible unless used.** A Sutra that uses no name newer than level L produces, on every runtime of level ≥ L,
   identical bytes for: the ViewModel and its frames, the problem list (codes, words, ranges, paths, `expected`,
   fixes), the plan, lint output, the result of every editing operation, and the generated schema at level L.
3. **Frozen behaviour.** Defaults, limits (in both directions: a larger limit changes what a full screen shows),
   orderings, the text of every problem message, the quirks pinned by decision N5 and the field-or-expression
   heuristic are part of the language. They change only by a new option or a new major.
4. **The host states its level** (6.2) and **its painter's contract** (6.3). Nothing above either line reaches it.
5. **Exact pins.** A host consumes tagged, immutable releases by exact version; never a range, a branch or a
   snapshot. Drishti moves its pin only inside its own release, before that release's drill (SPLIT_PLAN 4).
6. **Two independent gates.** Every Rachana change passes the compatibility gate in this repository before it merges
   (6.5); every Rachana release candidate passes the gate inside a Drishti checkout before it is tagged; and Drishti's
   own drill runs after the pin moves. Rachana cannot re-record an expected file that came from a host.
7. **Breaks wait for a major** (6.8), which ships beside the old one.

### 6.2 Language levels

The file keeps saying `rachana: 1`. The **host** says which minor it runs:

```java
// planned API, subject to change
Rachana rachana = Rachana.with(RachanaOptions.defaults().language("1.0").paints(painterCapabilities), Profile.core(), drishti);
```

- A level is a released language minor. At level L the compiler behaves as minor L did: a name with `since` > L is
  **not offered** (not in `expected`, not in near-match lists, not in the schema, `rachana.lang.json`, editor
  completion or the prompt kit generated for that host) and **using it is `RCH-2008`** ("`overlays` needs Rachana
  1.2; this host runs 1.0"), with the fix "raise the host's language level".
- The Java API has **no default level**: an embedding host must name one, so no host follows the newest by accident.
  The CLI and the playground default to the newest.
- New warnings (a deprecation, a new lint rule) belong to the level that introduced them: a host at L never sees a
  warning minor L did not give.
- **Names a profile owns.** A profile may register a name that Core defines at a level above the host's: at that
  level Core's name does not exist. This is how a function or key moves from Drishti's profile into Core without a
  flag day: Core adds it at level K with the meaning proven by the profile's own cases; Drishti drops its registration
  in the same commit that raises its level to K.
- **Reserved names.** `compat/reserved-names.yaml` lists every name a registered customer's profile declares (from its
  `<name>.profile.json`: keys, kinds, options, source forms, functions, context roots, formats, key actions,
  operations). Core claims one only through an entry in `compat/promotions.yaml` naming the level. This is why this
  document writes `args:` and `arg.`: `param` is a context root of Drishti's profile.
- **Experimental names** live behind `RachanaOptions.experimental(...)`, belong to no level, carry no promise, and are
  refused by a guard in Drishti (its list is empty).
- **Level switches.** If a defect must be fixed where a conformance case pinned it, the corrected behaviour is tied to
  the level that introduces it; lower levels keep the old bytes. Each switch is one line in
  `compat/level-switches.yaml` with the owner's approval, and the list is meant to stay near empty: it is the cost of
  the promise, not a design tool. Security fixes are the exception: applied at every level, with a notice.

### 6.3 The ViewModel contract

1. **One contract, minors add** (VIEWMODEL, decision N3). A new field is optional, is **left out when unused** (never
   written as `null`, `false` or empty), never changes the meaning of an existing field, and comes after the existing
   fields of its record in canonical order.
2. **Minimal stamp.** `contract` is the smallest minor that describes *this* document: a Sutra that uses nothing new
   keeps producing `"contract": "1.0"`. (Today the stamp is a constant; without this rule the first contract minor
   would change every golden.)
3. **Capabilities.** The host hands the compiler what its painter draws: the contract minor, the panel kinds, control
   kinds, effects and field types (`<painter>.capabilities.json`, generated with the painter; `<rachana-view>` asks
   the painter directly). Each addition is classed in section 3: **ignore-safe** additions are emitted and an older
   painter skips them; **refuse** additions make the Sutra `RCH-2008` for that host ("`group` needs a painter of
   ViewModel 1.2; this host paints 1.0"). So a screen that would be drawn wrongly is never sent.
4. **The painter's duties** (Drishti Elements `drishti-views`, checked by its forward vectors): ignore an unknown
   field; draw an unknown kind as a titled panel that says so; give an unknown tone no tone; draw an unknown control
   kind as its value, read-only; show an unknown effect as a disabled action with words; answer an unknown patch op by
   asking for a resync; refuse an unknown **major** in words. A painter release never changes how it draws a contract
   minor it already drew (its DOM snapshots over the 1.0 vectors are frozen).
5. **Frames** follow the document's rules; a new patch op is a contract minor.
6. **Drishti's API bytes** stay those of contract 1.0 until Drishti raises its ceiling: its contract writer (N3 c)
   is fed only what the ceiling allows.

### 6.4 Versions

| Thing | Version | Moves when | Promise |
|---|---|---|---|
| Release (jars, bundles) | `X.Y.Z` | every tag | `Z`: no expected file of any gate changes. `Y`: adds names, API or levels. `X`: may drop a language major or break the Java API. From `v0.9.0` the `0.Y` releases keep the `Y` promise |
| Language | `1.minor` | a release that adds language names | 6.1; a release states the levels it implements (all of them since 1.0) |
| ViewModel contract | `1.minor` | a release that adds fields or kinds | 6.3 |
| Public Java API (`com.ash.rachana`, not `.internal`) | the release's | | source and binary compatible within `X`. A hook a host implements gains a method only as a `default` whose behaviour is today's; otherwise a new interface |
| JavaScript API, `<rachana-view>` attributes, CLI flags and JSON outputs | the release's | | additions only within `X` |
| Profile declaration format, conformance case format, Data Protocol | their own majors | | additions only; unknown fields ignored |

### 6.5 The gates: which tests, where, who, what they block

| # | Gate | Lives in | Checks | Runs | Blocks |
|---|---|---|---|---|---|
| CG1 | **Frozen cases** | `drishti-rachana`: `conformance/1/FROZEN.sha384`, test `FrozenCasesTest` | every case file with `since` ≤ the last released minor has its released hash; a case is added, never edited | CI on every push and pull request (`./mvnw verify`) | merge |
| CG2 | **Level replay** | `drishti-rachana`: `compat/levels/<L>/{results.jsonl, rachana.lang.json, sutra.schema.json, functions.yaml}`, test `LevelReplayTest` | for every released level L: the suite run at level L gives the committed results file byte for byte; the generated definition files at L equal the committed ones | CI, every push | merge |
| CG3 | **Customer corpus: the Drishti compatibility gate** | `drishti-rachana`: `compat/customers/drishti/<drishti release>/` (6.6), test `CustomerCorpusTest` | all 503 shipped Sutras and their blocks compile at the corpus's level with its profile declaration to the recorded problem lists, canonical model text and plans; every recorded bind replays to the frozen golden ViewModel and frames (after dropping `timings` and the two provenance times); the Drishti-profile conformance cases pass; the documented bad Sutras give the recorded problem lists | CI, every push, for the corpora of the last two Drishti releases and the one in development | merge |
| CG4 | **Contract append-only** | `drishti-rachana`: `spec/1/released/viewmodel-<minor>.schema.json`, test `ContractAppendOnlyTest` | the schema of this commit contains every released definition, property and enum word with the same type; no new property is required; canonical order of old fields unchanged; a 1.0 golden validates and re-serialises to itself | CI, every push | merge |
| CG5 | **API surface** | `drishti-rachana`: `java/api-surface.txt`, test `ApiSurfaceTest`; the same for the JavaScript facade | a removed or changed signature fails; a new abstract method on a host hook fails; an addition must update the baseline in the same commit, so review sees it | CI, every push | merge |
| CG6 | **Reserved names** | `drishti-rachana`: `compat/reserved-names.yaml`, `compat/promotions.yaml`, test `ReservedNamesTest` | no Core name equals a reserved one without a promotion entry; a level switch has an entry | CI, every push | merge |
| CG7 | **Painter forward vectors** | `drishti-elements`: `elements/drishti-views/test/forward/`, and the frozen 1.0 DOM snapshots | 6.3 rule 4 over vectors with unknown fields, kinds, tones, controls, effects and ops; old vectors paint as before | Elements' gate, every change | an Elements merge |
| CG8 | **Release candidate in Drishti** | `drishti-rachana`: `tools/gate/drishti_gate.py`; it drives a Drishti checkout at the latest released tag and at `develop` | puts the candidate's jars and bundles into a scratch copy of `packaging/pinned`, then runs Drishti's own `ViewModelGoldenTest` (source mode and packaged), `PackSutrasTest`, Core conformance with the Drishti profile loaded, `RachanaPinGuardTest`, the lanes the affected map names for a pin move, the VaR goldens (exact) and `HotPathBenchmark` (within 3%) | the builder cutting a release, through `tools/release/release.py`, on the owner's machine, never beside a drill | a tag: the release tool refuses to tag without a gate report that names the candidate commit and both Drishti commits |
| CG9 | **Pin move** | Drishti | SPLIT_PLAN 4's checklist, then the release's full drill | Drishti's release | Drishti's merge to `main` |
| CG10 | **Speed** | both | budgets P1 to P11 (a regression above 10% fails); CG8's benchmark | CI and CG8 | merge, tag |

Rules of the gates: there is **no re-record flag in this repository** for CG2 and CG3 (a level's files are written once
by the release that creates the level; a corpus arrives only from a Drishti release); a red CG1 to CG6 is fixed by
changing the code, never the expectation; an exception needs the owner and is written into the release notes'
compatibility section with the files that changed. CG3 runs in seconds because it needs no Drishti code: that is what
lets it block every merge.

### 6.6 The customer corpus

Drishti's release produces one code-free artefact, `drishti-compat-corpus-<drishti version>` (a step of Drishti's
release; the reverse of the pin):

| Part | Content |
|---|---|
| `sutras/`, `blocks/` | every shipped Sutra and block (the 503 files source mode reads today) |
| `profile/drishti.profile.json`, `level.txt`, `capabilities.json` | the declarative half of the profile; the language level and painter capabilities Drishti runs |
| `compiled/` | per file: the problem list, the canonical model text, the plan |
| `binds/` | per golden: the inputs as **recorded hook answers** (records and rows from the table provider and source resolvers, link resolutions, mask decisions, calendar and clock, results of profile functions by call) and the expected ViewModel; live cases with their frames |
| `problems/` | the documented bad Sutras and their problem lists |
| `cases/` | the Drishti-profile conformance cases |
| `MANIFEST.sha384` | a hash per file |

Rachana's `CustomerCorpusTest` replays `binds/` through a `RecordedHost` (hooks that answer from the recording and
fail on an unrecorded call, which is itself a finding: Core started asking the host something new). The binder moves
into Core at `v0.10.0`; for `v0.9.0` the corpus has `compiled/`, `problems/` and `cases/`, and CG8 covers binding.
The data in the corpus is the synthetic demo data the goldens already use; no customer value enters it. Where the
corpus is held is decision Q6. The mechanism is per customer: a second host gets a second folder and the same test.

### 6.7 Deprecation and `rachana migrate`

- A deprecation is a warning (`RCH-2090`) with a machine-applicable fix, from the level that introduces it; never an
  error within the major; the old spelling keeps its exact output for the whole major.
- `rachana migrate --to <level> [--check] <files>` rewrites files to the newest spelling of that level, keeping
  comments and layout (it is built on the editing operations). It is idempotent; `--check` exits non-zero when it
  would change something. Every rule has `edit` cases and a proof case: the file before and after bind to the same
  ViewModel.
- A host raising its level runs `migrate --check` over its Sutras first; Drishti does this in the release that raises
  the level, and its goldens show that nothing moved.

### 6.8 When a break is unavoidable: `rachana: 2` beside 1

1. Candidates are collected in `docs/design/MAJOR_2_CANDIDATES.md`; none leaks into major 1. A major is cut when the
   list justifies it, not per item.
2. `rachana: 2` is a second front end (reader, checker, translation rules, `spec/2/`, `conformance/2/`) in the **same
   runtime**, sharing the evaluator where the meaning is equal. A file says which major it is; files of both majors
   live side by side in one host, and a block of one major can be used from a Sutra of the other (each is compiled by
   its own major, joined at the model).
3. Major 1 stays supported for at least 24 months after 2 ships (D13) **and** for at least four Drishti releases after
   Drishti's last file has migrated, whichever is later; gates CG1 to CG3 for major 1 stay on for that whole time.
4. Every breaking change ships with its `migrate` rule; Drishti moves file by file inside its own releases; there is
   no flag day.
5. ViewModel contract 2 is separate from language 2: one painter reads both contract majors by upgrading the older
   on load (Views design 12.1); a host chooses the contract it is sent.

## 7. Roadmap and sizes

R1 is deliberately small: i8 is the split, behaviour is frozen through it, and the best use of R1 is to make the
regime real while the painter and the pin are being built anyway. The expansion proper starts in R2, entirely above
Drishti's level until Drishti chooses to raise it.

| Phase | With | Items | Days |
|---|---|---|---|
| **R1: the regime, and three additions with no screen risk** | Drishti i8 (i8a for levels and gates, i8b for the painter rules) | language levels, `since` on every name, `RCH-2008` behaviour, level files (4); minimal contract stamp, capabilities, refuse or ignore-safe classes (3); reserved names and guard (1); corpus export in Drishti's release (4); `RecordedHost` and `CustomerCorpusTest` (6); `drishti_gate.py` and the release tool's refusal (3); painter forward vectors (2); **A1** (3), **T1** (5), **M2** (2) | **33** |
| **R2: structure, state, interaction** | Drishti i9 | L1 (10), L2 (6), C1 (8), S1 (5), S2 (3), I1 (8), I2 (8), I3 (3), D1 (3), D4 (3), F1 (2), V2 (5), V3 (8), G1 (3), M1 (5), N1 (3) | **83** |
| **Later, P1: scale and the chart grammar** | after i9 | D2 (10), V1 (15) | **25** |
| **Later, P2: depth** | after i9, in themed minors | L3 (3), L4 (12), L5 (2), C2 (6), C3 (4), S3 (8), S4 (2), I4 (5), I5 (3), I6 (3), I7 (3), I8 (2), I9 (2), D3 (8), D5 (3), D6 (4), F2 (5), F3 (5), F4 (4), F5 (3), V4 (12), V5 (6), A2 (8), B1 (4), G2 (2), M3 (4), N2 (3) | **126** |
| **Later, P3** | when asked for | L6 (5), L7 (4), S5 (3), I10 (6), V6 (5), V7 (2), T2 (3) | **28** |
| **Total** | | 55 additions and the regime | **295** |

Order inside R2 (three builders, about six weeks): S1, S2, D1 and G1 first (small, everything else reads them); then
I1 and L1 in parallel (different files: effects, containers); then L2, I2, I3, C1; V2 and V3 in the painter lane
throughout; M1 and N1 at the end, when the names they edit exist. Each item lands as: SPEC text with `since`, cases,
compiler, binder, painter, a gallery example, the level's files. Each language minor groups a theme (1.1 the settled
additions of D10 and D11 with A1; 1.2 state and effects; 1.3 containers and overlays; 1.4 grids and selections).

What R2 needs from elsewhere: EL_EXPANSION for the new context roots and `t`; the Views painter of Elements 2.6.0
(N11); the events design for `host` action answers.

## 8. Decisions for the owner

| # | Question | Recommendation | Why | If not |
|---|---|---|---|---|
| **Q1** | Is "invisible unless used, byte for byte" (6.1 rules 2 and 3), including problem wording, `expected` lists and limits, the promise? | **Yes.** | It is the only form of "no impact" a test can prove | "same behaviour" judged by people at each pin move |
| **Q2** | Language levels: the host names the minor it runs; no default in the Java API (6.2)? | **Yes; Drishti starts at 1.0 and raises it only in its own releases.** | Without levels, adding one panel kind already changes an `expected` list in a Drishti problem message | additive-only alone, with re-recorded goldens at every pin move |
| **Q3** | The ViewModel stamp is the smallest minor that describes the document, and new fields are left out when unused (6.3)? | **Yes, written into TRANSLATION before `v0.9.0`.** | Otherwise the first contract minor changes every golden | Drishti's contract writer rewrites the stamp: a second place that knows versions |
| **Q4** | Capabilities: the compiler refuses what the host's painter cannot draw, per addition class (6.3)? | **Yes.** | A screen is never sent to a painter that would draw it wrongly | painters guess, or every host upgrades painter and compiler together |
| **Q5** | The Drishti compatibility gate as CG1 to CG10 (6.5), with CG3 blocking every merge and CG8 blocking every tag? | **Yes.** | Two independent proofs before Drishti's own drill; the fast one needs no Drishti code | only C8 and the drill: a break is found days after it was written |
| **Q6** | Where is the Drishti corpus held? | **Committed in this repository under `compat/customers/drishti/` while the repository is private (exempt, by exact path, from the "names no host" guard); moved to a private companion repository that CI checks out before this repository is opened.** | CI must reach it on every push without a secret; it is code-free and synthetic | a release asset fetched with a token (CI needs a secret; offline builds fail) |
| **Q7** | Reserved names and promotions (6.2): Core never takes a name a customer's profile uses, except level-gated? | **Yes.** | A Core `param` or `rank` would make Drishti's profile fail to register at start-up | names negotiated by hand at each release |
| **Q8** | Level switches for defects pinned by a case: allowed, each with your approval, target near zero? | **Yes, as the exception.** | The alternative is to break the promise or never fix | pinned defects wait for major 2 |
| **Q9** | R1 scope: the regime plus A1, T1, M2 only (33 days), nothing else of the expansion inside i8? | **Yes.** | i8 is the split; behaviour is frozen through it; the regime cannot be retrofitted after `v0.9.0` | additions inside the split, with the drill attributing failures to two causes |
| **Q10** | R2 scope: the sixteen items of section 7 (83 days), with D2 and V1 after i9? | **Yes; if i9 must be smaller, drop V3 and C1 to the next minor (67 days).** | State, effects, containers and overlays unlock most complex screens; grids and the chart grammar are large and independent | a smaller R2 that adds kinds but not structure: more screens, none of them complex |
| **Q11** | Effects are a closed list with `host` as the only door to behaviour; no scripting, ever, in major 1 (section 5)? | **Yes.** | Safety for model-written screens, three runtimes that agree, checks that finish | a scripting escape hatch that every complex screen then uses |
| **Q12** | `emits`, `on-select`, form `rules:` and `tabs` keep their meaning and output for all of major 1, read into the new mechanisms, with `migrate` rules offered? | **Yes.** | One mechanism inside, no change outside | two mechanisms (against the motto), or a break |
| **Q13** | The workspace (L4) is a separate document kind, not a bigger Sutra? | **Yes; design and mock-ups first, with the desktop hosts.** | A Sutra stays one screen with one ViewModel; windows are a host matter | docking inside a Sutra: the contract and every painter grow a window manager |
| **Q14** | `rachana: 2` runs beside 1 in one runtime; 1 is supported 24 months and four Drishti releases after Drishti's last file migrates (6.8)? | **Yes (extends D13).** | No flag day for the first customer | D13 as it stands: 24 months from the major, whatever Drishti's state |
| **Q15** | Experimental names exist behind an option and Drishti refuses them by a guard? | **Yes.** | A place to try syntax with other hosts without a promise | every trial is a permanent name |
