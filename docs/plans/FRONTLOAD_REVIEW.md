<!-- Copyright (c) 2026 Ashutosh Sinha. All rights reserved. -->
# Front-load review: the filter tree (F8) and the expression cases (F2)

**Owner decision, 2026-11-17: every recommendation below is accepted** (A3: Rachana-EL is exact on decimal operands; division by zero gives empty). To be applied to spec/1 and the cases when work resumes.


**2026-11-17.** The review R0_R1_I8_PLAN.md asks for: "Opus reviews the filter tree" (F8) and the expression cases (F2),
plus a cross-check of `spec/1/`, `conformance/FORMAT.md` and `examples/`. Read-only; nothing was built or run except
`python3 -I` validation scripts. What was clearly wrong is fixed in the commits listed at the end; this page lists what
was **not** fixed. Each item: where, the problem, the recommendation, and whether the owner must decide.

## Open items

### A. The filter tree and the Data Protocol (F8)

**A1. No rule turns a column's value into a Rachana-EL value.** `spec/1/data-protocol/PROTOCOL.md:46-63` (3.1) gives
the JSON forms (a `decimal` is a string `"1250.50"`, a 64-bit `integer` beyond ±2^53 a string of digits) and section 7
says filter semantics are Rachana-EL's, but nothing says what EL value a cell is. Read as text, a decimal breaks every
operator: `revenue + 1` joins text (`'120.001'`, SPEC 4.5), `revenue == 120` is numeric but `revenue == '120'` is not.
The same gap exists for CSV and NDJSON `data:` sources without `fields` (`spec/1/SPEC.md:142-148`). Java, JS and
Python runtimes, and every host, will each guess. **Recommend** a section 3.3 "Values in Rachana-EL": `integer`,
`decimal`, `number` → number (`decimal` an exact decimal as SPEC 4.10 rule 7; an `integer` beyond 2^53 kept exact or
refused, decide which); `date`, `timestamp`, `time` → ISO text; `list` → list; `object` → object; `{ "$masked": true }`
→ masked; and SPEC 2.4 to say CSV cells are text unless `fields` types them (or are inferred: decide). Add filter cases
over a decimal column with `+` and `==`. **Owner: yes.**

**A2. Timestamps: written date versus UTC date.** `PROTOCOL.md:185` compares a `type: date` literal against a timestamp
column by the timestamp's date **in UTC**; Rachana-EL (SPEC 4.2, `functions.yaml` `year`…`day`: "a timestamp counts by
its date") uses the date **as written**, and orders timestamps as text (SPEC 4.7), which is wrong across offsets
(`2026-11-17T01:00:00+05:00` sorts after `2026-11-16T23:00:00Z` but is earlier). A host and a runtime filtering locally
disagree for any timestamp not in UTC. **Recommend**: 3.1 says a timestamp on the wire MUST be UTC with `Z` and a fixed
number of fraction digits (ms), so text order is time order and the written date is the UTC date; the line at 185 then
needs no special rule. **Owner: yes.**

**A3. Arithmetic precision: doubles in EL, exact decimals in a SQL host.** SPEC 4.5/4.10 rule 7: operators compute in
binary floating point; a SQL host computing `revenue * 1.1 > 132` over a `DECIMAL` column computes exactly. Results
differ at boundaries (`0.1 + 0.2 == 0.3`: false in EL, true in SQL). `PROTOCOL.md:167-169` pushes `+ - * / %` down
without saying which. **Recommend** one of: operators over `decimal` operands are exact in EL too (a SPEC change); or
the protocol pushes arithmetic down only over `integer`/`number` columns and a host answers `rdp.query.unsupported`
for arithmetic on `decimal`. Also state that `/ 0` and `% 0` are empty (SQL raises). **Owner: yes.**

**A4. A row whose condition raises an evaluation error is undefined.** `abs(note)`, `year('soon')`, `round('x')` raise
`RCH-2102` in EL; in the reference a filter `[?p]` lets it escape and the whole expression fails
(`drishti-i7a/.../rachana/el/Expr.java:178-194`). PROTOCOL section 7 and 10 have no code for it; a SQL host either fails
the query (cast error) or yields NULL (row dropped). **Recommend**: the runtime type-checks the tree against the table
schema before sending (checked mode, SPEC 4.14), so an ill-typed tree is never sent; a host that still meets one answers
`rdp.query.invalid` naming the operation (the panel shows `RCH-2102`). Add one case. **Owner: yes.**

**A5. Text order: UTF-16 code units in filters, code points in `sort`.** Filter `<`/`>` on text follow SPEC 4.7
(UTF-16 code unit); `PROTOCOL.md:133` sorts text by code point (UTF-8 byte) order; `len` counts UTF-16 units
(`functions.yaml` `len`) where SQL counts code points. They differ only for characters outside the BMP against
U+E000–U+FFFF, but a page boundary or a filter can then disagree with a local re-sort. **Recommend** decide SPEC 9.4
once and use the same order in 6.1, 7 and `len`; until then the host declares its order. **Owner: yes** (tied to SPEC 9.4).

**A6. An operation the host does not know is `invalid`, not `unsupported`.** `filter.json:29` closes the operation
names, so a v1.0 host given an operation a later minor adds (allowed by `PROTOCOL.md:355-357`) answers
`rdp.query.invalid`, and the runtime does not fall back to filtering locally (it does so only on `unsupported`,
`PROTOCOL.md:186-188`). Case `conformance/1/protocol/examples/filter.yaml` `filter-unknown-operation-is-invalid` pins
that. Section 14's "MUST ignore fields they do not know" also contradicts `additionalProperties: false` on every node
(ignoring a field of a filter node could change its meaning). **Recommend**: an unknown `op` name → `rdp.query.unsupported`
with `operation`; an unknown field inside a filter node → `rdp.query.invalid`; section 14 names the filter tree as the
one place where unknown fields are refused. Change the case's expected code. **Owner: yes.**

**A7. The usual control filters have no node.** `one_of(v, ctl.x)` ("empty choice holds everything", SPEC 4.8, 5.2)
and `within(v, ctl.range)` are how controls filter, and neither is in the subset (`PROTOCOL.md:165-171`). The runtime
can rewrite them after substituting controls (`one_of(x, [])` → drop the conjunct; `one_of(x, L)` → `in`;
`within(v, 'a..b')` → `>=` and `<=`, numbers as numbers else text), but the rewrite is not written down, so runtimes
will differ. **Recommend** state the rewrites in section 7 (normative for runtimes), with `plan` cases; or add
`one-of`/`within` operations. **Owner: yes** (which of the two).

**A8. Grouping aggregates have no stated semantics.** `PROTOCOL.md:146-149` lists `sum … percentile` but not what they
mean. EL's `min`/`max` take numbers only (`functions.yaml` `min`: "the smallest number"), so `min` of a date column is
empty in EL and the earliest date in SQL; `count` counts non-empty values; `sum` is compensated; the result encoding
(`"255.00"` for a decimal sum in `filter.yaml` `group-with-totals`) is not specified. **Recommend**: each `agg` is the
Core aggregate of the same name over the column's EL values (A1); `min`/`max` over `date`/`timestamp`/`string` columns
either follow text order (and EL's `min`/`max` too) or are `unsupported`; the result column's type is the input's for
`sum`/`min`/`max`, `integer` for `count`/`distinct`, `number` otherwise. **Owner: yes.**

**A9. Cells masked in a column not declared masked.** `PROTOCOL.md:190` and section 11 forbid filtering, sorting and
grouping on a **masked column**, but a host may mask single cells (`page.yaml` `page-masked-cell-and-schema`) in a
column without `masked: true`. Is a filter on that column forbidden, or evaluated by SPEC 4.9 (a masked cell is never
truthy, so its row is excluded under every condition and nothing leaks)? **Recommend**: a column with any cell masked
for this caller counts as masked for filter, sort and group (simplest and safe); state it in 11. **Owner: yes.**

**A10. Dotted column paths versus dots in column names.** `filter.json` `col` reads `"legs.rate"` as a path into an
object column (`PROTOCOL.md:158`), but `schema.json:27` allows any column name, dots included. **Recommend**: column
names MUST NOT contain `.` (pattern in `schema.json`), or a whole-name match wins over a path. **Owner: no** (pick the
pattern unless the owner objects).

**A11. Integers beyond 2^53 in literals.** `filter.json` `lit` accepts a JSON number; an `id == 9007199254740993` filter
over an `integer` column loses the last digit in JS and in EL's doubles. **Recommend**: `type: integer` on a string
literal (digits), like `decimal`; with A1. **Owner: no** (follows A1).

**A12. `params` of the query document is free-form.** `filter.json` `query.params` ("Host-defined named parameters")
is a channel the "no expression text" rule (`PROTOCOL.md:153-154`) does not cover. **Recommend**: values are JSON
scalars or lists of scalars, and a host MUST NOT interpret any of them as an expression or query text. **Owner: no.**

### B. Case-insensitive text tests (affects filters and expressions)

**B1. The case rule is not stated precisely.** `contains`, `starts_with`, `ends_with` lower-case both sides with
`toLowerCase(Locale.ROOT)` in the reference (`drishti-i7a/.../el/TextFunctions.java:45,65-80`): full Unicode default
case mapping. JS `toLowerCase()` and Python `str.lower()` agree on it (final sigma, `İ` → `i̇`); SQL `ILIKE`/`LOWER`
with a collation often do not. **Recommend** `functions.yaml` says "both sides lower-cased by the Unicode default case
mapping (root locale)" and cases cover `İ`, `ß`, final `Σ`. **Owner: no.**

**B2. `starts_with(empty, '')` and `ends_with(empty, '')` are `true` in the reference**, since it lower-cases
`Values.text(null)` = `''` (`TextFunctions.java:45,65-67`), while `contains(empty, '')` is `false` and SPEC 5.1 says
the text tests answer `false` for empty. **Recommend**: the reference answers `false` (fix in Drishti), plus a case.
**Owner: yes** (a behaviour change in the reference, however small).

### C. Formats

**C1. The reference formats `MMM` in the JVM's default locale.** `FormatSpec.date` uses
`DateTimeFormatter.ofPattern(pattern)` with no locale (`drishti-i7a/.../rachana/format/FormatSpec.java`), so `dmy`
prints a different month on a French JVM. `spec/1/formats.yaml` now says English. **Recommend**: `Locale.ROOT` (or
`ENGLISH`) in the reference, and a `format` case for `dmy`. **Owner: no.**

**C2. ±Infinity in a number format fails.** `FormatSpec.number` calls `BigDecimal.valueOf(Infinity)`, which throws.
**Recommend**: print the text form (`Infinity`, `−Infinity`?) and pin it in a case (with FORMAT 9 N5). **Owner: no**
(the minus sign of `−Infinity` is a small owner call).

**C3. Who owns the format table.** `spec/1/formats.yaml` (new) is now the open statement of the Core names; the Java
bundle and Drishti Elements' `data/formats.yaml` (proprietary header) hold the same 11 entries. **Recommend**: one
source, generated into the other two, with a guard test comparing them (C9 of the plan already pins the Elements
file's hash). **Owner: yes** (which file is the source).

### D. Cross-consistency of `spec/1` and `conformance/`

**D1. ViewModel key order.** `spec/1/TRANSLATION.md:103-104` writes the envelope as `contract, title, strip, panels,
keys, provenance, timings`; `viewmodel.schema.json` lists `ref, mnemonic, title, strip, panels, keys, provenance,
timings, controls, selects, layout, contract, phone`, and FORMAT 8 makes the schema's order the canonical output
order. **Recommend**: confirm the reference's order when the schema is generated (H4), then fix the example.
**Owner: no.**

**D2. SPEC 5.8 and 5.9 arities read as shared.** `spec/1/SPEC.md:547-552` writes "`norm_dist(…)`, …, `beta_dist`
(3–6)", which reads as if all of them take 3 to 6 arguments; `functions.yaml` gives each its own (`norm_s_inv` 1,
`t_inv` 2; `weibull_dist` 3–4 where SPEC says 4–5). **Recommend**: generate the arities in SPEC 5 from
`functions.yaml` at H5. **Owner: no.**

**D3. "6 deprecated spellings"** (`SPEC.md:413`) against 7 entries in `functions.yaml` `deprecated` (`min` and `max`
apart). Cosmetic; settle with D2. **Owner: no.**

**D4. Drishti-profile formats in Core cases.** `conformance/1/el/paths-operators.yaml` cases `fmt-rate-pct4-fixed`
and `fixing-source-index-fmt-fixing-rate-pct4` need `pct4`, a format of Drishti's core pack, not of Core
(`spec/1/formats.yaml`). **Recommend**: give them `profile: [core, drishti]`, or switch to `pct2`. **Owner: no.**

### E. The expression cases (F2)

About 570 of the 1,062 cases (1,016 in the 14 files plus 26 in `el/examples/`) were checked against their Java
assertion, SPEC, `functions.yaml` or a recomputation: math 100, text 70, paths-operators 60, logic 50, dates 45, lists
40, lists-more 35, membership-parents 29, distributions 25, masking 20, check 20, finance 15, limits 13, templates 11,
examples 26. Every file validates against `case.schema.json`; every `source` names an existing test method; the 58
expressions that are also `functions.yaml` examples agree. Four defects were fixed (see the end). Open:

**E1. Host calendars in Core cases.** `conformance/1/el/dates.yaml:420-543` (`intck(... 'USNY')`, "USNY by default",
`is-business-day-*-usny`, `-gblo`) rely on the reference's USNY default and named calendars; SPEC 5.5 says Core is
weekends only without a host calendar (SPEC 9.1), and FORMAT 5.1's `given` cannot supply a calendar. **Recommend**:
`given.calendars` in FORMAT (or move these cases to the drishti profile) and pin weekends-only as Core's default.
**Owner: yes.**

**E2. `accrued` with an unknown literal basis is a run-time error.** `finance.yaml:386`
(`accrued-0-04-2026-06-15-2026-12-15-act-999`) expects `RCH-2102`, as `FinanceFunctionsTest` asserts; SPEC 4.10 rule 2
says a literal day-count basis is checked at compile time (`RCH-2101`), as `yearfrac` does (`dates.yaml:339`).
**Recommend**: fix the reference (a compile-time check for `accrued`'s basis), then the case. **Owner: yes.**

**E3. Precision of non-terminating decimals is unpinned.** `yearfrac(…, 'ACT/360')` → `0.5027777777777778`,
`accrued` → `20.54794520547945` (`finance.yaml:239`): the reference stops at 16 significant digits. Neither SPEC 4.10
rule 7 nor FORMAT 9 N4 states the precision or rounding mode, so a JS or Python decimal library will differ.
**Recommend**: pin it (e.g. IEEE DECIMAL64-like 16 digits, half-even) in SPEC 4.10 and add a `determinism` case.
**Owner: yes.**

**E4. The `type` of a whole result from a decimal function.** `sqrt(16)`, `round_up(3.2)` return a whole number in the
reference; FORMAT 5.2 does not say whether `type` is `number` or `decimal`, so the cases leave `type` out.
**Recommend**: FORMAT 5.2 states it (after H1 shows what the reference reports) and one case pins it. **Owner: no.**

**E5. "Number-like text" has no grammar.** SPEC 4.5 counts number-like text as a number; Java's parser trims and reads
`'Infinity'`, `' -1.5e-3 '`; Python also reads `'inf'`, `'nan'`; JS and Python trim Unicode spaces Java does not.
**Recommend**: a grammar for number text in SPEC 4.5 (sign, digits, point, exponent; which spaces; `Infinity`/`NaN`
or not) plus P2 probes. **Owner: yes.**

**E6. A test function that answers empty.** `is_business_day($.NOPE)` is empty in the Java test and in
`functions.yaml`; SPEC 5.1 (`SPEC.md:426`) says tests answer `true` or `false`, never empty. **Recommend**: narrow the
sentence to the text and list tests (`contains` … `all_of`, `is_*`). **Owner: no.**

**E7. No value cases for the Core list aggregates.** `sum`, `avg`, `median`, `percentile`, `count`, `distinct` have
no value cases because the reference refuses them without Drishti's pivot engine (`MathFunctionsTest`
`max($.NUMS)`; `NOT_HARVESTED.md`). These move into Core (decision D3) and SPEC 8 item 7 pins them. **Recommend**:
H5 writes them from the engine's tests before 1.0; until then the `functions.yaml` examples stand alone.
**Owner: no.**

**E8. The code of a refused regular expression.** `text.yaml:370` (`regex-extract-ab-trade-id`) maps the reference's
`REGEX_REFUSED` (`DRS-4010`, its own exception class) to `RCH-2102`; `logic.yaml:200`
(`iferror-regex-extract-a-trade-id-bad-pattern`) assumes `iferror` catches it. SPEC 6.2 has no code for a pattern
refused at run time. **Recommend**: decide `RCH-2102` (and make the reference's `iferror` catch it) or a new code.
**Owner: yes.**

**E9. Order of `reads`.** `paths-operators.yaml:94` (`legs-0-rate-2-size-legs-x`) pins first-read order as FORMAT
5.2 says; the Java test checks only a sorted set. **Recommend**: H1 confirms the reference reports first-read order;
otherwise FORMAT 5.2 switches to sorted order. **Owner: no.**

**E10. A keyword-only argument on a Core function.** `logic.yaml:631` (`sum-legs-notional-where-desk-x`) pins the
reference's run-time `RCH-2102` for `sum(…, where = …)`; by SPEC 4.10 rule 3 a name that is not a parameter of Core
`sum` is a compile error (`RCH-2101`). This is SPEC 9.7. **Recommend** settle 9.7, then the case. **Owner: yes.**

**E11. "No error" checked-mode assertions not harvested.** `NOT_HARVESTED.md` leaves out `CheckedModeTest` assertions
of acceptance (`floor($.n / 2)`) because a `check` case needs `type` or `problems`. **Recommend**: write them with
`expect.problems: []`. **Owner: no.**

## Fixed in this review

| Commit | Area | What |
|---|---|---|
| `afaef43` | filter tree (F8) | `filter.json`: a literal's `type` only on a string, with `date`, `timestamp`, `decimal` forms checked; PROTOCOL 7: each operation named against its EL operator or function (`neg`, `not-in`, `is-null` = `x == null`), n-ary operations are left folds (matters for `+`), SPEC's null exceptions stated, sorting on a masked column forbidden as in 10 and 11; `error.json` err frame accepts a host's own `rdp.` codes like the error body; three `protocol` cases (`!=` keeps empty, `!` of an ordering against empty, a filter on a masked column → 403) |
| `8ae0561` | cross-consistency | FORMAT cites PROTOCOL.md sections instead of the design's; SPEC's companion table lists `functions.yaml`, `formats.yaml`, the ViewModel schema and the Data Protocol; Data Protocol version wording; a control's `default: first` (`all` for multi-select) as the reference checks; `count`/`distinct` count values of any kind (not numbers only), as the engine's kernel does |
| `f99db44` | formats | `spec/1/formats.yaml` drafted (11 Core names + built-in `list`, rendering rules by reading `FormatSpec`); TRANSLATION's kv example and the `format` case use Core's `pct2` (`pct4` is a Drishti pack format) |
| `2235a42` | examples | the checker validates format names against `formats.yaml` and tones against SPEC 2.6; all four Sutras pass |
| `a4ceb88` | el cases (F2) | `math#sqrt-2` is an exact decimal without tolerance; `masking#mtm-fmt-mtm-signed0` (`+` form) is the mask; `asof()`/`today()` with no as-of date moved out of `dates.yaml` (they inherited its file-level `today`) into `paths-operators.yaml`; `lists#unique-mixed` note; FORMAT's `source` notation `Class#method` |

**Is a `formats.yaml` needed in `spec/1`?** Yes: SPEC 2.6 made Core's format names "the table shared with Drishti
Elements", a proprietary file outside the specification, and a JS or Python runtime needs the rendering rules (rounding,
grouping, U+2212 minus, compact units, date patterns), not only the names. The draft is small (11 entries) and is
marked as read from the reference, not run; C1 to C3 above are its open points.
