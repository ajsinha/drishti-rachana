<!-- Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved. -->
---
library: finance
version: 1.0.0
summary: Trading, P&L, market risk, limits, counterparty and collateral, market data and funds as typed fragments, in generic terms; every figure arrives as data.
fragments: 57
language: "1.2"
licence: proprietary (see LICENSE)
---

# Finance library

Trading, P&L, market risk, limits, counterparty and collateral, market data and funds as typed fragments, in generic terms; every figure arrives as data.

57 fragments, written as typed fragments (fragments are functions): local parameter and column names, the caller maps its own data and column names at the use, hermetic, events returned. Each has an example Sutra under `examples/` and a ViewModel snapshot under `goldens/`.

## Market data

### `finance/correlation-matrix@1`

Correlations between named factors as a shaded square.

| Parameter | Kind | Notes |
|---|---|---|
| `rows` | table(a, b, value) | required. One row per ordered pair |
| `title` | text | default 'Correlations'. The panel's title |
| `fmt` | format | default 'signed2'. How the correlations are written |

Example: `examples/correlation-matrix.sutra.yaml`.

### `finance/curve-compare@1`

Several curves on one tenor axis - today against yesterday, one currency against another - with the spreads underneath.

| Parameter | Kind | Notes |
|---|---|---|
| `rows` | table(curve, tenor, years, value) | required. One row per curve and tenor |
| `base` | text | default ''. The curve the spreads are taken against (none - no spreads) |
| `title` | text | default 'Curves'. The panel's title |
| `fmt` | format | default 'pct2'. How the rates are written |

Events: `point`

Example: `examples/curve-compare.sutra.yaml`.

### `finance/curve-overview@1`

A curve as a line and, under it, its pillars as a table.

| Parameter | Kind | Notes |
|---|---|---|
| `rows` | table(tenor, zero, instrument, quote, df) | required. One row per pillar |
| `title` | text | default 'Curve'. The panel's title |
| `zero-fmt` | format | default 'pct2'. How rates are written |

Events: `select`

Example: `examples/curve-overview.sutra.yaml`.

### `finance/curve-table@1`

A curve's pillars - tenor, instrument, quote, zero rate and discount factor.

| Parameter | Kind | Notes |
|---|---|---|
| `rows` | table(tenor, zero, instrument, quote, df) | required. One row per pillar |
| `title` | text | default 'Pillars'. The panel's title |
| `zero-fmt` | format | default 'pct2'. How rates are written |
| `df-fmt` | format | default 'text'. How discount factors are written |

Events: `select`

Example: `examples/curve-table.sutra.yaml`.

### `finance/fixing-ladder@1`

An index's fixings by date, newest first, with the day's change.

| Parameter | Kind | Notes |
|---|---|---|
| `rows` | table(date, value) | required. One row per fixing |
| `title` | text | default 'Fixings'. The panel's title |
| `fmt` | format | default 'pct2'. How the rates are written |
| `change-fmt` | format | default 'signed2'. How the change is written in basis points |

Example: `examples/fixing-ladder.sutra.yaml`.

### `finance/fx-scenario-chart@1`

The path of an exchange rate over scenario dates, with the shock per scenario, as a line and a table.

| Parameter | Kind | Notes |
|---|---|---|
| `rows` | table(date, rate, shock) | required. One row per scenario date |
| `pair` | text | required. The currency pair, such as EURUSD |
| `fmt` | format | default 'text'. How rates are written |
| `shock-fmt` | format | default 'pct2'. How shocks are written |

Events: `point`

Example: `examples/fx-scenario-chart.sutra.yaml`.

### `finance/instrument-header@1`

The head of an instrument screen - name, identifiers, currency, last price with change, status.

| Parameter | Kind | Notes |
|---|---|---|
| `name` | text | required. The instrument's name |
| `identifiers` | list<record{label: text, value: text}> | optional. The identifiers: a list of { label, value }, such as an ISIN or a ticker |
| `currency` | text | optional. The instrument's currency |
| `last` | number | optional. The last price |
| `change` | number | optional. The change on the day |
| `price-fmt` | format | default 'price2'. How the price is written |
| `status` | text | optional. A status word |

Example: `examples/instrument-header.sutra.yaml`.

### `finance/price-bars@1`

Open, high, low and close per period as candles, with volume under them.

| Parameter | Kind | Notes |
|---|---|---|
| `rows` | table(date, open, high, low, close, volume) | required. One row per period |
| `title` | text | default 'Prices'. The panel's title |
| `fmt` | format | default 'price2'. How prices are written |

Events: `bar`

Example: `examples/price-bars.sutra.yaml`.

### `finance/price-history@1`

A closing-price line with the period's high, low and return as figures beside it.

| Parameter | Kind | Notes |
|---|---|---|
| `rows` | table(date, close) | required. One row per period, in date order |
| `title` | text | default 'Price history'. The panel's title |
| `fmt` | format | default 'price2'. How prices are written |

Events: `point`

Example: `examples/price-history.sutra.yaml`.

### `finance/quote-card@1`

A quote at a glance - last, change, the day's range and the 52-week range with the last price placed in each.

| Parameter | Kind | Notes |
|---|---|---|
| `last` | number | required. The last price |
| `previous` | number | optional. The previous close |
| `day-low` | number | optional. The day's low |
| `day-high` | number | optional. The day's high |
| `year-low` | number | optional. The 52-week low |
| `year-high` | number | optional. The 52-week high |
| `title` | text | default 'Quote'. What the quote is |
| `fmt` | format | default 'price2'. How prices are written |
| `as-of` | text | optional. When the quote was taken |

Example: `examples/quote-card.sutra.yaml`.

### `finance/term-structure@1`

One term structure - zero rates, forwards or volatilities by expiry - drawn against tenor.

| Parameter | Kind | Notes |
|---|---|---|
| `rows` | table(tenor, value) | required. One row per tenor, in tenor order |
| `title` | text | default 'Term structure'. The panel's title |
| `fmt` | format | default 'pct2'. How the values are written |
| `step` | boolean | default False. Whether the line steps between tenors instead of joining them |
| `mark` | text | optional. A tenor to mark |

Events: `point`

Example: `examples/term-structure.sutra.yaml`.

### `finance/vol-surface@1`

A volatility surface by expiry and strike, drawn as a heat map or in three dimensions.

| Parameter | Kind | Notes |
|---|---|---|
| `rows` | table(expiry) | required. One row per expiry |
| `strikes` | columns | required. The strike columns: a list of { label, bind: "@.field", fmt } over a row |
| `title` | text | default 'Volatility surface'. The panel's title |
| `fmt` | format | default 'pct2'. How the values are written |
| `view` | enum | default 'heatmap'. How the surface is drawn |

Example: `examples/vol-surface.sutra.yaml`.

## Trading

### `finance/cashflow-ladder@1`

Cash flows by date as a ladder - date, type, signed amount, currency, with the running net.

| Parameter | Kind | Notes |
|---|---|---|
| `rows` | table(date, amount, currency, type) | required. One row per cash flow |
| `title` | text | default 'Cash flows'. The panel's title |
| `fmt` | format | default 'signed0'. How amounts are written |

Example: `examples/cashflow-ladder.sutra.yaml`.

### `finance/depth-ladder@1`

An order book as a price ladder - bid sizes on one side, ask sizes on the other, bars for depth, the spread above.

| Parameter | Kind | Notes |
|---|---|---|
| `rows` | table(price, bid_size, ask_size) | required. One row per price level |
| `levels` | integer | default 10. The levels shown on each side |
| `title` | text | default 'Depth'. The panel's title |
| `price-fmt` | format | default 'price2'. How a price is written |
| `size-fmt` | format | default 'amount0'. How a size is written |

Example: `examples/depth-ladder.sutra.yaml`.

### `finance/fills-summary@1`

The executions of an order - filled against ordered, average price, slippage, and the fills as a table.

| Parameter | Kind | Notes |
|---|---|---|
| `rows` | table(time, qty, price, venue) | required. One row per execution |
| `ordered` | number | required. The quantity ordered |
| `reference` | number | optional. A price to measure slippage against |
| `title` | text | default 'Fills'. The panel's title |
| `price-fmt` | format | default 'text'. How prices are written |

Example: `examples/fills-summary.sutra.yaml`.

### `finance/leg-table@1`

A trade's legs side by side - pay or receive, notional, currency, rate or index, spread, day count and frequency.

| Parameter | Kind | Notes |
|---|---|---|
| `rows` | table(leg, direction, notional, currency, rate, index, spread, day_count, frequency) | required. One row per leg |
| `title` | text | default 'Legs'. The panel's title |
| `rate-fmt` | format | default 'pct2'. How rates and spreads are written |
| `notional-fmt` | format | default 'amount0'. How notionals are written |

Example: `examples/leg-table.sutra.yaml`.

### `finance/lifecycle-events@1`

A trade's life as a timeline - booked, amended, fixed, paid, matured.

| Parameter | Kind | Notes |
|---|---|---|
| `rows` | table(date, event, detail, status) | required. One row per event |
| `title` | text | default 'Lifecycle'. The panel's title |

Events: `select`

Example: `examples/lifecycle-events.sutra.yaml`.

### `finance/live-blotter@1`

A trading blotter over a large live table - read a window at a time, new rows on top, changes ticking in place.

| Parameter | Kind | Notes |
|---|---|---|
| `rows` | table(id, status) | required. The live table (a data entry with table, key and live), one row per order or trade |
| `columns` | columns | required. The columns: a list of { label, bind: "@.field", fmt, tone }, each reading one field |
| `order` | id | default 'id'. The field the rows are ordered by |
| `title` | text | default 'Blotter'. The panel's title |

Example: `examples/live-blotter.sutra.yaml`.

### `finance/order-ticket@1`

An order ticket - side, quantity, type, limit price, time in force - checked before it is sent; it sends nothing.

| Parameter | Kind | Notes |
|---|---|---|
| `instrument` | text | required. The instrument ordered |
| `sides` | list<text> | default ['BUY', 'SELL']. The sides offered |
| `types` | list<text> | default ['MARKET', 'LIMIT']. The order types offered; LIMIT needs a limit price |
| `tifs` | list<text> | default ['DAY', 'GTC', 'IOC']. The times in force offered |
| `side` | text | default ''. The side to start from, an expression ("'BUY'", state.side); none when empty |
| `last` | number | optional. The last price |
| `title` | text | default 'Order ticket'. The panel's title |
| `send-label` | text | default 'Send'. The send button's text |

Events: `submit`, `cancel`

Example: `examples/order-ticket.sutra.yaml`.

### `finance/payment-schedule@1`

A trade's scheduled payments - period, payment date, rate, amount, currency, paid or due.

| Parameter | Kind | Notes |
|---|---|---|
| `rows` | table(pay_date, amount, currency, start, end, rate, status) | required. One row per payment |
| `title` | text | default 'Payments'. The panel's title |
| `fmt` | format | default 'amount2'. How amounts are written |
| `rate-fmt` | format | default 'pct2'. How the rate is written |

Example: `examples/payment-schedule.sutra.yaml`.

### `finance/position-table@1`

Positions by instrument - quantity, price, market value, P&L and the share of the total.

| Parameter | Kind | Notes |
|---|---|---|
| `rows` | table(instrument, quantity, value, price, pnl) | required. One row per instrument |
| `title` | text | default 'Positions'. The panel's title |
| `value-fmt` | format | default 'amount0'. How values are written |
| `pnl-fmt` | format | default 'signed0'. How P&L is written |

Events: `open`

Example: `examples/position-table.sutra.yaml`.

### `finance/settlement-figures@1`

Three figures over a set of payments - due today, due in the coming week, overdue.

| Parameter | Kind | Notes |
|---|---|---|
| `rows` | table(pay_date, amount, status) | required. One row per payment |
| `today` | date | required. The day the figures are taken on |
| `fmt` | format | default 'amount0'. How amounts are written |

Example: `examples/settlement-figures.sutra.yaml`.

### `finance/trade-blotter@1`

A list of trades with standard columns by default, or the columns you give; a click on a row names the trade.

| Parameter | Kind | Notes |
|---|---|---|
| `rows` | table(id, product, counterparty, book, notional, currency, maturity, status) | required. One row per trade |
| `columns` | columns | default [{'label': 'Trade', 'bind': '@.id'}, {'label': 'Product', 'bind': '@.product'}, {'label': 'Counterparty', 'bind': '@.counterparty'}, {'label': 'Book', 'bind': '@.book'}, {'label': 'Notional', 'bind': '@.notional', 'fmt': 'amount0'}, {'label': 'Currency', 'bind': '@.currency'}, {'label': 'Maturity', 'bind': '@.maturity', 'fmt': 'date'}, {'label': 'Status', 'bind': '@.status', 'tone': 'status'}]. Your own columns in place of the standard ones: a list of { label, bind: "@.field", fmt, total } |
| `title` | text | default 'Trades'. The panel's title |
| `limit` | integer | default 200. The most trades drawn |

Events: `open`

Example: `examples/trade-blotter.sutra.yaml`.

### `finance/trade-ticket@1`

The economic summary of one trade, read-only - product, direction, parties, dates, notional, price, status.

| Parameter | Kind | Notes |
|---|---|---|
| `trade` | record{id: text, product: text, direction: text, counterparty: text, book: text, trade_date: date, maturity: date, notional: number, currency: text, price: number, status: text} | required. The trade: id, product, and optionally direction, counterparty, book, trade_date, maturity, notional, currency, price, status |
| `notional-fmt` | format | default 'amount0'. How the notional is written |
| `price-fmt` | format | default 'text'. How the price is written |

Slots: `extra`

Example: `examples/trade-ticket.sutra.yaml`.

## P&L

### `finance/pnl-attribution-bars@1`

P&L by a grouping - book, desk, strategy - as bars toned by sign.

| Parameter | Kind | Notes |
|---|---|---|
| `rows` | table(label, pnl) | required. One row per group |
| `title` | text | default 'P&L by group'. The panel's title |
| `fmt` | format | default 'signed0'. How the P&L is written |
| `top` | integer | optional. Keep only this many groups |

Events: `bar`

Example: `examples/pnl-attribution-bars.sutra.yaml`.

### `finance/pnl-history@1`

Daily P&L as a line of the cumulative total, with the best and worst day as figures.

| Parameter | Kind | Notes |
|---|---|---|
| `rows` | table(date, pnl) | required. One row per day |
| `title` | text | default 'P&L history'. The panel's title |
| `fmt` | format | default 'signed0'. How the P&L is written |
| `cumulative` | boolean | default True. Whether the line is the running total |

Events: `point`

Example: `examples/pnl-history.sutra.yaml`.

### `finance/pnl-tiles@1`

Day, month-to-date and year-to-date P&L as three figures toned by sign.

| Parameter | Kind | Notes |
|---|---|---|
| `day` | number | required. The day's P&L |
| `mtd` | number | optional. The month-to-date P&L |
| `ytd` | number | optional. The year-to-date P&L |
| `fmt` | format | default 'signed0'. How the figures are written |
| `currency` | text | optional. The currency |

Example: `examples/pnl-tiles.sutra.yaml`.

### `finance/pnl-waterfall@1`

P&L explained - from the opening value through each effect to the close.

| Parameter | Kind | Notes |
|---|---|---|
| `rows` | table(effect, amount, total) | required. One row per effect, in order |
| `title` | text | default 'P&L explain'. The panel's title |
| `fmt` | format | default 'signed0'. How the amounts are written |
| `sum-label` | text | default 'Close'. What the closing figure is called |

Events: `step`

Example: `examples/pnl-waterfall.sutra.yaml`.

## Market risk

### `finance/backtest-chart@1`

Daily P&L as bars against the VaR on the loss side as a line, the exceptions marked.

| Parameter | Kind | Notes |
|---|---|---|
| `rows` | table(date, pnl, var) | required. One row per day |
| `title` | text | default 'P&L against VaR'. The panel's title |
| `fmt` | format | default 'signed0'. How the amounts are written |

Events: `point`

Example: `examples/backtest-chart.sutra.yaml`.

### `finance/backtest-traffic-light@1`

A backtest's verdict - the count of exceptions over the window and the zone it falls in, green, amber or red.

| Parameter | Kind | Notes |
|---|---|---|
| `rows` | table(date, pnl, var) | required. One row per day |
| `amber-from` | integer | default 5. The exception count from which the zone is amber |
| `red-from` | integer | default 10. The exception count from which the zone is red |
| `window` | integer | default 250. The number of latest days counted |
| `title` | text | default 'Backtest'. The panel's title |

Example: `examples/backtest-traffic-light.sutra.yaml`.

### `finance/concentration-treemap@1`

Where the risk or the exposure sits - rectangles sized by exposure, nested by group, toned by P&L.

| Parameter | Kind | Notes |
|---|---|---|
| `rows` | table(group, name, exposure, pnl) | required. One row per name |
| `title` | text | default 'Concentration'. The panel's title |
| `fmt` | format | default 'amount0'. How the exposure is written |

Events: `cell`

Example: `examples/concentration-treemap.sutra.yaml`.

### `finance/greeks-ladder@1`

Sensitivities by bucket - tenor or strike - one row a bucket, a column per greek, totals at the foot.

| Parameter | Kind | Notes |
|---|---|---|
| `rows` | table(bucket, delta, gamma, vega, theta, rho) | required. One row per bucket |
| `title` | text | default 'Sensitivities'. The panel's title |
| `fmt` | format | default 'amount0'. How the figures are written |

Events: `select`

Example: `examples/greeks-ladder.sutra.yaml`.

### `finance/risk-hierarchy-tree@1`

A risk measure down a hierarchy - firm, division, desk, book - each node with its figure, its limit and the share used.

| Parameter | Kind | Notes |
|---|---|---|
| `rows` | table(id, parent, name, value, limit) | required. One row per node |
| `title` | text | default 'Risk by hierarchy'. The panel's title |
| `open` | integer | default 2. How many levels start open |

Example: `examples/risk-hierarchy-tree.sutra.yaml`.

### `finance/scenario-matrix@1`

Scenarios down the side, books or factors across, P&L in the cells, shaded by size.

| Parameter | Kind | Notes |
|---|---|---|
| `rows` | table(scenario, group, pnl) | required. One row per scenario and group |
| `title` | text | default 'Scenarios'. The panel's title |
| `fmt` | format | default 'signed0'. How the P&L is written |
| `heat` | boolean | default True. Whether cells are shaded by size |

Example: `examples/scenario-matrix.sutra.yaml`.

### `finance/sensitivity-heatmap@1`

One sensitivity across two axes - risk factor by tenor, currency by tenor - as a shaded grid with totals.

| Parameter | Kind | Notes |
|---|---|---|
| `rows` | table(factor, bucket, value) | required. One row per factor and bucket |
| `title` | text | default 'Sensitivity grid'. The panel's title |
| `fmt` | format | default 'signed0'. How the figures are written |

Example: `examples/sensitivity-heatmap.sutra.yaml`.

### `finance/stress-table@1`

Stress scenarios with their P&L, toned by sign, the worst first and highlighted, each with its description.

| Parameter | Kind | Notes |
|---|---|---|
| `rows` | table(scenario, pnl, description) | required. One row per scenario |
| `title` | text | default 'Stress results'. The panel's title |
| `fmt` | format | default 'signed0'. How the P&L is written |

Events: `open`

Example: `examples/stress-table.sutra.yaml`.

### `finance/var-contributors@1`

What makes up a VaR - each position or book with its component, stand-alone and incremental figure, largest first.

| Parameter | Kind | Notes |
|---|---|---|
| `rows` | table(name, component, standalone, incremental) | required. One row per contributor |
| `total` | number | optional. The total VaR the components belong to |
| `top` | integer | default 20. The most contributors listed |
| `title` | text | default 'Contributors'. The panel's title |
| `fmt` | format | default 'amount0'. How the figures are written |

Events: `open`

Example: `examples/var-contributors.sutra.yaml`.

### `finance/var-histogram@1`

The distribution of scenario P&L with the VaR and the expected shortfall marked on the loss side.

| Parameter | Kind | Notes |
|---|---|---|
| `rows` | table(pnl) | required. One row per scenario |
| `var` | number | required. The value-at-risk |
| `es` | number | optional. The expected shortfall |
| `title` | text | default 'Scenario P&L'. The panel's title |
| `bins` | integer | default 40. How many bins |
| `fmt` | format | default 'compact'. How the values are written |
| `unit` | text | default ''. The unit |

Example: `examples/var-histogram.sutra.yaml`.

### `finance/var-path@1`

Scenario P&L against the scenario's date, with the worst days listed - when in history the losses come from.

| Parameter | Kind | Notes |
|---|---|---|
| `rows` | table(date, pnl) | required. One row per scenario |
| `var` | number | optional. The value-at-risk |
| `worst` | integer | default 5. How many worst scenarios are listed |
| `title` | text | default 'Scenario P&L by date'. The panel's title |
| `fmt` | format | default 'signed0'. How the P&L is written |

Events: `scenario`

Example: `examples/var-path.sutra.yaml`.

### `finance/var-tiles@1`

Value-at-risk and expected shortfall as tiles, with the change on the day and the share of a limit used.

| Parameter | Kind | Notes |
|---|---|---|
| `var` | number | required. The value-at-risk |
| `es` | number | optional. The expected shortfall |
| `var-label` | text | default 'VaR 99%'. What the VaR figure is called |
| `es-label` | text | default 'ES 97.5%'. What the expected shortfall is called |
| `previous-var` | number | optional. The previous VaR |
| `limit` | number | optional. The VaR limit |
| `horizon` | text | default '1d'. The holding period |
| `fmt` | format | default 'amount0'. How the figures are written |

Example: `examples/var-tiles.sutra.yaml`.

### `finance/what-if-shocks@1`

Market shocks entered beside the sensitivities they apply to; sent to the screen, whose host gives the estimate.

| Parameter | Kind | Notes |
|---|---|---|
| `sensitivities` | table(factor, value) | required. One row per factor |
| `shocks` | list<record{id: text, label: text, type: text, min: number?, max: number?}> | required. The shocks asked for, one field each: { id (the factor), label, type: number, min, max } |
| `estimate` | number | optional. The host's estimate for the shocks last sent |
| `title` | text | default 'What if'. The panel's title |
| `fmt` | format | default 'signed0'. How the sensitivities and the estimate are written |

Events: `change`

Example: `examples/what-if-shocks.sutra.yaml`.

## Limits

### `finance/limit-band@1`

A two-sided limit - the figure on a band between its lower and upper bounds, with the warning zones.

| Parameter | Kind | Notes |
|---|---|---|
| `value` | number | required. The figure |
| `lower` | number | required. The lower bound (usually negative) |
| `upper` | number | required. The upper bound |
| `warn-at` | number | default 0.8. The share of a bound from which the figure is near it |
| `label` | text | default 'Position'. What the figure is |
| `fmt` | format | default 'signed0'. How the figure and the bounds are written |

Example: `examples/limit-band.sutra.yaml`.

### `finance/limit-gauge@1`

One limit - the amount used against it, with a warning band and, when given, the recent history.

| Parameter | Kind | Notes |
|---|---|---|
| `used` | number | required. The amount used |
| `limit` | number | required. The limit |
| `warn-at` | number | default 0.8. The share of the limit from which the figure is near it |
| `label` | text | default 'Limit used'. What the gauge measures |
| `fmt` | format | default 'amount0'. How the used amount and the limit are written |
| `history` | table(date, used) | optional. The amount used over recent days |

Slots: `footer`

Example: `examples/limit-gauge.sutra.yaml`.

### `finance/limit-table@1`

Many limits at once - name, limit, used, headroom and the share used, toned at the warning and breach levels.

| Parameter | Kind | Notes |
|---|---|---|
| `rows` | table(name, limit, used, owner) | required. One row per limit |
| `title` | text | default 'Limits'. The panel's title |
| `warn-at` | number | default 0.8. The share of the limit from which the state is Warning |
| `fmt` | format | default 'amount0'. How the amounts are written |

Events: `open`

Example: `examples/limit-table.sutra.yaml`.

## Counterparty and collateral

### `finance/addon-breakdown@1`

A regulatory exposure built up - replacement cost, add-on by asset class, the multiplier, the exposure at default.

| Parameter | Kind | Notes |
|---|---|---|
| `rows` | table(asset_class, addon) | required. One row per asset class |
| `replacement-cost` | number | required. The replacement cost |
| `multiplier` | number | optional. The multiplier applied to the add-ons |
| `exposure` | number | required. The exposure at default |
| `title` | text | default 'Add-on by asset class'. The panel's title |
| `fmt` | format | default 'amount0'. How amounts are written |

Example: `examples/addon-breakdown.sutra.yaml`.

### `finance/agreement-terms@1`

The terms of a collateral agreement - thresholds, minimum transfer amounts, independent amounts, currency, frequency.

| Parameter | Kind | Notes |
|---|---|---|
| `terms` | record{type: text, threshold_us: number, threshold_them: number, mta_us: number, mta_them: number, independent_amount: number, currency: text, frequency: text} | required. The terms: type, threshold_us, threshold_them, mta_us, mta_them, independent_amount, currency, frequency |
| `title` | text | default 'Agreement terms'. The panel's title |
| `fmt` | format | default 'amount0'. How amounts are written |

Example: `examples/agreement-terms.sutra.yaml`.

### `finance/collateral-ledger@1`

Collateral held and posted - asset, direction, nominal, haircut, value after haircut, with totals by direction.

| Parameter | Kind | Notes |
|---|---|---|
| `rows` | table(asset, direction, nominal, currency, haircut, value) | required. One row per asset and direction |
| `title` | text | default 'Collateral'. The panel's title |
| `fmt` | format | default 'amount0'. How amounts are written |

Events: `open`

Example: `examples/collateral-ledger.sutra.yaml`.

### `finance/counterparty-card@1`

A counterparty at a glance - name, rating, country, sector, current exposure, limit and the share used.

| Parameter | Kind | Notes |
|---|---|---|
| `counterparty` | record{name: text, rating: text, country: text, sector: text, exposure: number, limit: number} | required. The counterparty: name, and optionally rating, country, sector, exposure, limit |
| `fmt` | format | default 'amount0'. How exposure and limit are written |

Slots: `footer`

Example: `examples/counterparty-card.sutra.yaml`.

### `finance/exposure-profile@1`

Exposure through time - expected and potential future exposure by date, against a limit, with the peak called out.

| Parameter | Kind | Notes |
|---|---|---|
| `rows` | table(date, expected, potential) | required. One row per date |
| `limit` | number | optional. A horizontal limit drawn across the profile |
| `title` | text | default 'Exposure profile'. The panel's title |
| `fmt` | format | default 'amount0'. How the peak is written |

Events: `point`

Example: `examples/exposure-profile.sutra.yaml`.

### `finance/margin-call-ladder@1`

Margin calls by date - direction, amount, status issued, agreed, disputed or settled - with the open total.

| Parameter | Kind | Notes |
|---|---|---|
| `rows` | table(date, direction, amount, currency, status) | required. One row per call |
| `title` | text | default 'Margin calls'. The panel's title |
| `fmt` | format | default 'amount0'. How amounts are written |

Example: `examples/margin-call-ladder.sutra.yaml`.

### `finance/netting-set-tree@1`

Group, counterparty, netting set and trade as a tree, with gross value, net value and collateral at each node.

| Parameter | Kind | Notes |
|---|---|---|
| `rows` | table(id, parent, name, level, gross, net, collateral) | required. One row per node |
| `title` | text | default 'Netting sets'. The panel's title |
| `open` | integer | default 2. How many levels start open |

Example: `examples/netting-set-tree.sutra.yaml`.

### `finance/netting-summary@1`

A netting set in four figures - gross positive, gross negative, net, after collateral - with the largest trades as bars.

| Parameter | Kind | Notes |
|---|---|---|
| `gross-positive` | number | required. The sum of positive trade values |
| `gross-negative` | number | required. The sum of negative trade values |
| `net` | number | required. The net value of the set |
| `collateral` | number | optional. The collateral held against the set |
| `trades` | table(id, value) | optional. The trades of the set |
| `fmt` | format | default 'amount0'. How the figures are written |
| `top` | integer | default 8. The most trades drawn |
| `title` | text | default 'Largest trades'. The panel's title |

Events: `bar`

Example: `examples/netting-summary.sutra.yaml`.

### `finance/valuation-adjustments@1`

Valuation adjustments per counterparty or netting set - credit, debit, funding - with totals and the change on the day.

| Parameter | Kind | Notes |
|---|---|---|
| `rows` | table(name, credit, debit, funding, change) | required. One row per counterparty or netting set |
| `title` | text | default 'Valuation adjustments'. The panel's title |
| `fmt` | format | default 'signed0'. How amounts are written |

Events: `open`

Example: `examples/valuation-adjustments.sutra.yaml`.

## Funds and equities

### `finance/allocation-donut@1`

A portfolio's split by asset class, sector or currency, as a donut, in weights or in amounts.

| Parameter | Kind | Notes |
|---|---|---|
| `rows` | table(label, value) | required. One row per slice |
| `title` | text | default 'Allocation'. The panel's title |
| `fmt` | format | default 'pct2'. How the slices are written: percentages for weights, an amount format when as-weights is off |
| `as-weights` | boolean | default True. Whether the slices are shown as shares of the whole |

Events: `slice`

Example: `examples/allocation-donut.sutra.yaml`.

### `finance/financials-table@1`

Reported figures with periods across and line items down.

| Parameter | Kind | Notes |
|---|---|---|
| `rows` | table(item, period, value) | required. One row per line item and period |
| `title` | text | default 'Financials'. The panel's title |
| `fmt` | format | default 'compact'. How the figures are written |
| `periods` | integer | default 5. How many of the latest periods are shown |

Example: `examples/financials-table.sutra.yaml`.

### `finance/holdings-table@1`

Holdings of a portfolio, or holders of a security - name, quantity, value, weight, change since the last report.

| Parameter | Kind | Notes |
|---|---|---|
| `rows` | table(name, value, quantity, weight, change) | required. One row per holding |
| `title` | text | default 'Holdings'. The panel's title |
| `fmt` | format | default 'amount0'. How values are written |
| `top` | integer | default 50. The most holdings listed |

Events: `open`

Example: `examples/holdings-table.sutra.yaml`.

### `finance/performance-chart@1`

A portfolio against its benchmark rebased to 100, the drawdown underneath and the period's return and worst drawdown.

| Parameter | Kind | Notes |
|---|---|---|
| `rows` | table(date, value, benchmark) | required. One row per date, in any order |
| `title` | text | default 'Performance'. The panel's title |
| `fmt` | format | default 'amount2'. How the rebased values are written |

Events: `point`

Example: `examples/performance-chart.sutra.yaml`.

