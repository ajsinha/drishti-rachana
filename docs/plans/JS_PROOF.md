<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary. All rights reserved. -->
# The JavaScript proof: plan, JDK-use inventory, determinism stress list

**Status: plan, 2026-11-17 (increment F5 of [R0_R1_I8_PLAN](R0_R1_I8_PLAN.md)). Nothing installed, built or run.** The
inventory was read from Drishti `wip/i7a` at `42047f059` (the base of the plan): `compute/drishti-rachana/src/main/java` and the
binder packages of `kernel/drishti-engine`. TeaVM coverage figures are quoted from TeaVM's published class-library report
(`teavm.org/jcl-report/recent/`, read 2026-11-17; the report names no TeaVM version and counts **members present, not behaviour**).
Anything else said about TeaVM is marked **(unverified)**: general knowledge that P1's step T1 must confirm. Paths below are
relative to `compute/drishti-rachana/src/main/java/com/ash/drishti/rachana/` (written `R/`) or
`kernel/drishti-engine/src/main/java/com/ash/drishti/engine/` (written `E/`).

This document is the input of **P1** (the JavaScript proof, T1 to T10 of
[COMPILER_AND_RUNTIMES](../design/COMPILER_AND_RUNTIMES.md) 4.2) and **P2** (the determinism probes). A builder runs both from it
without a design question. Their answers go to `docs/plans/JS_PROOF_RESULT.md`; this file is deleted with the plan at i8b's
release, and what stays true moves into COMPILER_AND_RUNTIMES.

## Contents

1. [What the proof decides](#1-what-the-proof-decides)
2. [JDK-use inventory](#2-jdk-use-inventory)
3. [Determinism stress list](#3-determinism-stress-list)
4. [P1: the steps T1 to T10](#4-p1-the-steps-t1-to-t10)
5. [Budgets to measure](#5-budgets-to-measure)
6. [Fallbacks, in order, with their triggers](#6-fallbacks-in-order-with-their-triggers)
7. [What the result file holds](#7-what-the-result-file-holds)
8. [Points for the owner](#8-points-for-the-owner)

## 1. What the proof decides

| Question | Decided by | Consumer |
|---|---|---|
| Can the compiler sources become a browser bundle with one source of semantics? | T1, T6, T7 | A15 go, or the fallback of section 6 |
| Which JDK classes Core may use (the **JDK allow-list**, enforced by a guard over compiled classes) | T1 and section 2.5 | F6's guard, A10 |
| What Core must own instead of the JDK (number text, date text, maths, YAML, JSON, cache) | T1 gap list, P2 difference table | A5 (deterministic text), A8 (own YAML and JSON) |
| Is `java.time` kept, or does Core carry a small civil-date library? | T1 + probes D-* (section 3.4) | A5 |
| Are the size and speed budgets realistic? | T2 to T5 | P1 to P3, P10 of COMPILER_AND_RUNTIMES 7 become gates at A15 |

**Thresholds are agreed before the first build** (this document is that agreement). A result that misses a threshold is
written down as a miss and a fallback is named; a threshold is never moved to make a result pass. A threshold changes only by the
owner's decision, recorded in [DECISIONS](../design/DECISIONS.md) with the measured figure.

## 2. JDK-use inventory

### 2.1 Scope read

| Package | Files | Lines | After i8a | In P1's cut |
|---|---|---|---|---|
| `R/el` (with `el/check`: 7 files, 1,386 lines) | 37 | 7,634 | Core | **yes** |
| `R/format` | 4 | 446 | Core | **yes** |
| `R/model` | 20 | 3,285 | Core (7 files split) | **yes** |
| `R/parse` | 11 | 3,087 | Core | **yes** |
| `R/design/ops` | 22 | 1,894 | Core (`rachana-tools`) | no (tools bundle size is estimated from it, T2) |
| `R/` root | 17 | 3,143 | mixed (see the plan 4.1) | `SutraText`, `SutraWiring`, `SutraExpressions`, `SutraProblem`, `SutraException` only |
| `E/bind` | 8 | 2,068 | Core `bind` but the hooks | no (A13/A14) |
| `E/view` | 7 | 1,364 | Core `view` | no |
| `E/control` | 5 | 820 | Core `bind` | no |
| `E/wire` | 3 | 1,045 | Core `bind` | no |
| `E/live` | 7 | 590 | `Frame`, `Patch`, `PatchDiffer` to Core; the rest stays (transport) | no |

The binder is inventoried now so that A13 and A14 do not meet a JDK surprise; P1 compiles only the first five rows (the plan's
P1 scope: `el`, `el/check`, `format`, `model`, `parse`). Product types (`DataNode`, `DrishtiException`, `ErrorCode`,
`BusinessCalendar`, `SafeRegex`, …) are not JDK uses: P1 replaces them **by hand** on the throwaway branch with the shapes A1 to
A3 will give them (`Node`, `RachanaException`, a `Calendars` hook, a copy of `SafeRegex`).

### 2.2 JDK and library uses, rated

Rating: **S** supported, **P** partial (present, behaviour or options to verify), **N** not supported, **U** unknown.
"Report" = TeaVM's class-library report: the share of the class's members present.

| # | API | Where (files) | TeaVM | Basis | Determinism weight | Replacement plan (owner step) |
|---|---|---|---|---|---|---|
| J1 | `Double.toString`, `String.valueOf(double)`, implicit `"" + d`, `BigDecimal.valueOf(double)` (which calls `Double.toString`) | `R/el/{Values,Printer,TextParts,Args,FinanceFunctions,Distributions,Parser,Lexer}`, `R/format/FormatSpec`, `R/model/{PivotSpec(19),Form,PanelOptions,FormExpr}`, `R/parse/ControlsReader`, `R/{SutraWriter,SutraLayoutEditor,SutraPivotEditor}`, `R/design/ops/{OpChecks,YamlText,SutraDoc}`, `E/bind/{Binder(11),MapSupport(7),ChartBinder}`, `E/control/ControlResolver(7)`: 24 files, about 90 lines | P | report: `Double` 93%; TeaVM prints doubles with its own class-library code, not with JavaScript's `Number.prototype.toString` (unverified); whether it equals JDK 19+'s shortest round-trip form for every double is **unknown** | **highest**: `Values.text` is the text form of SPEC 4.2; `Args.decimal` reads every double argument through it | Core's own `NumberText.shortest(double)` (a Ryū-class shortest round-trip printer written in Core, used on the JVM too), and `Values.text`, `Printer.literal`, `Args.decimal`, `FormatSpec.number` call it, never the JDK (A5) |
| J2 | `Double.parseDouble`, `Double.valueOf(String)`, `new BigDecimal(String)` on user text | `R/el/{Values.number,Args,TextParts,Lexer}`, `R/model/{Form,PivotSpec}`, `E/bind/*` | P | report: `Double` 93%. Java accepts `"1d"`, `"1f"`, `"0x1p3"`, `"Infinity"`, `"NaN"`, leading and trailing white space; whether TeaVM's parser accepts the same set and rounds correctly near halfway cases is **unknown** | high | Core's own `NumberText.parse` with the accepted grammar written down (SPEC 4.2's "number-like text"), correctly rounded (A5). `Values.couldBeNumber` lets `N`, `I`, `x`, `p`, `d`, `f` through today: the rule becomes the grammar, not the JDK's |
| J3 | `java.lang.Math` transcendental: `log` 30, `exp` 21, `pow` 13, `log1p` 11, `expm1` 5, `log10` 2, `tanh`, `sin`, `atan2` 1 each | `R/el/{Distributions(29),SpecialFunctions(26),FinanceFunctions(18),RootFinder(10),NumberFunctions,MathFunctions}`, `E/bind/ChartBinder(19)` | P | report: `Math` 88%, `StrictMath` 74%. TeaVM maps these to the JavaScript engine's `Math` functions (unverified). ECMA-262 lets each engine approximate them; HotSpot's `Math` may use CPU intrinsics that differ from `StrictMath` in the last place | **high**: a one-ulp difference prints a different shortest text | Core's own `CoreMath` (a port of the C fdlibm under its permissive Sun notice, **not** OpenJDK's GPL Java port) for `exp`, `log`, `log1p`, `expm1`, `log10`, `pow`, `tanh`, `sin`, `atan2`, used on the JVM too. `sqrt`, `abs`, `floor`, `ceil`, `min`, `max`, `signum` are exact in IEEE 754 and stay. Sized by P2 (A5) |
| J4 | `Math.rint`, `Math.round`, `Math.floorDiv`, `Math.floorMod`, `(long) d` casts of NaN and infinities | `R/el/{Values,Printer,MathFunctions,NumberFunctions,DateFunctions}`, `R/format/FormatSpec` | P | defined exactly by Java; TeaVM implements them in its class library (unverified) | medium | kept if probes D-N10 to D-N14 agree; else Core's own (A5) |
| J5 | `long` arithmetic (42 lines in `R/el`; `Long.toString(d.longValue())` in the text form) | `R/el/*` | S | TeaVM emulates 64-bit `long` in JavaScript (unverified how: an object pair or `BigInt`) | low for bytes, **high for speed** (T4) | measured in T4; if slow, the text form stops going through `long` (A5) |
| J6 | `java.math.BigDecimal`, `BigInteger`, `MathContext`, `RoundingMode` | 15 files: `R/el/{NumberFunctions(20),MathFunctions(21),Args(12),FinanceFunctions(10),Distributions,DateFunctions,ListFunctions,ListMore,TextParts,Values,Printer}`, `R/model/{FormExpr(15),Form(8)}`, `R/format/FormatSpec`, `E/bind/ChartBinder`; `BigInteger` in `R/el/{Args,NumberFunctions}`, `R/parse/PositionalYamlReader` | P | report: `BigDecimal` 97%, `BigInteger` 93%, `MathContext` and `RoundingMode` 100% | medium: `toPlainString`, `stripTrailingZeros`, `divide(…, DECIMAL128)` must agree digit for digit | kept (SPEC 4.10 rule 7 needs decimal arithmetic); the probes D-N20 to D-N25 hold it; size measured in T2 |
| J7 | `String.format` (8 lines): `"%02d"`, `"%04d"` (`R/el/TextParts`, 6), `"%,f"` (`R/model/Form:754`), `"\\u%04x"` (`R/design/ops/YamlText:78`) | 3 files | P | report: `Formatter` 64% | medium (`"%,f"` groups by locale rules) | removed: padding and hex by hand; `Form`'s amount text through Core's number text (A5) |
| J8 | `java.text.DecimalFormat`, `DecimalFormatSymbols` (Excel number codes in `text(v, 'code')`) | `R/el/TextParts:365` (and a comment in `R/format/FormatSpec`) | P | report: `DecimalFormat` 91%, `DecimalFormatSymbols` 80%; pattern and `HALF_UP` behaviour on TeaVM **unknown** | high: every `text(…)` case | Core's own Excel number-code formatter over `BigDecimal` (A5); `java.text` leaves Core |
| J9 | `java.time`: `LocalDate` (in the public `EvalContext.today`), `LocalDateTime`, `ChronoUnit`, `IsoFields`, `TemporalAdjusters`, `DayOfWeek`, `TextStyle` with `Locale.ENGLISH`, `DateTimeFormatter.ofPattern` | 19 files: `R/el/{DateFunctions(43),DateParts(25),DatePartFunctions,FinanceFunctions,TextParts,Args,Expr,EvalContext}`, `R/model/{Form(14),FormExpr(11),ControlRules}`, `R/format/FormatSpec`, `E/control/{ControlDates(14),ControlResolver}`, `E/wire/{ScreenWire,TableRows}`, `E/view/ViewModelSchema`, `E/live/{Patch,ViewStream}` | P | report: `LocalDate` 93%, `LocalDateTime` 100%, `ZoneId` 100%, `DateTimeFormatter` 95%, `java.time.temporal` 75% of classes full; month and day **names** need locale data and time zones need zone data, both build options of TeaVM (unverified) | high | **decided by T1 and D-T probes**: keep `LocalDate`, `ChronoUnit`, `IsoFields`, `TemporalAdjusters`, `DayOfWeek` if every D-T probe agrees and T2's size allows; else Core's civil-date library (days since epoch, proleptic Gregorian, ISO week) behind the same functions. **Either way** month and day names come from Core's own English table, not `TextStyle` (A5) |
| J10 | `DateTimeFormatter.ofPattern(pattern)` **without a locale** | `R/format/FormatSpec:121` | P | uses the JVM's default locale: `MMM` already differs between two JVMs with different defaults | **high** (a JVM-vs-JVM defect today) | Core's date-pattern formatter with its English table (A5); probe D-T12 |
| J11 | `ZoneId.of(z)` (validates a form field's zone) | `R/model/Form:482` | P | report: `ZoneId` 100%; the zone **database** is a build option, its version may differ from the JDK's (unverified) | medium | a time-zone name check against a list Core carries (the IANA names, pinned), or the check moves to the host through a hook; decided by size (T2) (A5) |
| J12 | `java.util.regex` (`Pattern`, `Matcher`, `String.matches`, `replaceAll`, `split` with regex) for **Core's own** fixed patterns | 17 files: `R/model/{Form(10),ControlRules,Wiring,PivotSpec}`, `R/parse/{SutraBlocks,WiringReader,SutraBuilder,ControlsReader,LayoutReader}`, `R/el/{DateParts,TextParts}`, `R/{SutraText,SutraPivotEditor,SutraWiring}`, `R/design/ops/{YamlText,OpChecks}`, `E/bind/MapSupport`, `E/control/ControlDates`, `E/wire/ScreenWire` | P | report: `Pattern` 79%, `Matcher` 85%; TeaVM's engine is its own class-library code (unverified: derived from Apache Harmony), so the dialect is Java's as far as it goes; J2CL's emulation uses JavaScript `RegExp` (unverified), a different dialect | medium (fixed patterns; `Form.AMOUNT` uses `CASE_INSENSITIVE`; `MapSupport:112` builds a pattern from data) | Core's fixed patterns are rewritten as small scanners or compiled by Core's own linear-time engine (the `SafeRegex` copy), so Core has **no** `java.util.regex` and the J2CL fallback is not blocked (A5) |
| J13 | User-facing regular expressions (`matches`, `regex_*`) | `R/el/{TextFunctions,TextParts}` through `common.SafeRegex` (728 lines, product) | S | hand-written NFA over `char`: no JDK regex, cross-compiles as plain code | medium: its case folding uses `Character.toLowerCase/toUpperCase` (J15) and its time limit uses `System.nanoTime` (J20) | moves into Core as Core's regex (A3); the time limit is replaced by a step budget (J20) |
| J14 | `Locale.ROOT` in `toLowerCase`/`toUpperCase`; `Locale.ENGLISH` for names | 30 files, 48 lines | P | report: `Locale` 58% | low with `ROOT` | kept for `ROOT` only; **4 calls without a locale** in `R/format/WorldRegions:77,87,96,102` (a Turkish-locale JVM already differs) get `Locale.ROOT` or ASCII folding (A5) |
| J15 | `Character.isLetter`, `isLetterOrDigit`, `isDigit`, `isWhitespace`, `isUpperCase`, `isISOControl`, `getType`, `toChars`, `toLowerCase(char)`, `toUpperCase(char)`; `String.toLowerCase/UpperCase(Locale.ROOT)`; `codePoints()` | `R/el/{Lexer(9),TextParts(9),Parser,Values}`, `R/model/{FormExpr(6),PivotSpec,Wiring}`, `R/SutraText`, `SafeRegex` | P | report: `Character` 88%, `String` 88%; the **Unicode version** of TeaVM's tables, or of the engine if it delegates to JavaScript's `toLowerCase`, is **unknown**; JDK 21 is Unicode 15.0 | high on non-ASCII text | the lexer's classes become ASCII plus a written Unicode rule (SPEC 4.13; identifiers and digits are ASCII unless the spec says otherwise); text functions keep JDK case mapping only if probes D-U* agree; else Core's own tables generated from one pinned Unicode version (A5) |
| J16 | `String.compareTo`, `Comparator.naturalOrder` on text | `R/el/{Expr:316,ListFunctions:165,MathFunctions:216,LogicFunctions:161}`, `R/model/{Form,FormExpr}` | S | Java semantics are UTF-16 code units, implemented by the class library; JavaScript's own `<` on strings is also by code unit, `localeCompare` is not | medium (only if a hand-written intrinsic slips to `localeCompare` or `Intl.Collator`) | kept; a guard forbids `localeCompare`/`Intl` in intrinsics (A15); SPEC 4.13 decides code units vs code points (H6) |
| J17 | `HashMap`/`HashSet` **iteration** reaching output; `Set.of`/`Map.of` iteration | `HashMap`/`HashSet` in 25 files; `Set.of`/`Map.of` in 49 files. Read: `R/SutraWiring.cycles` iterates `deps` (a `HashMap`) to start the cycle walk, so which panel a cycle message starts with, and its location, follow hash order; `R/el/Functions.arity()` builds a `HashMap` (its readers sort: `RachanaSchema:107`, `Parser:414`); messages that list a `Set.of` are wrapped in `TreeSet` (`DateFunctions:286`, `ListMore:49,52,100`) | S (classes present: report `HashMap` 100%) | iteration order is **not** specified: TeaVM's map (unverified: Harmony-derived) need not order like OpenJDK's; `Set.of`/`Map.of` iteration is randomised per JVM run on the JDK itself | high, and silent | guard over Core: no `HashMap`/`HashSet`/`Set.of`/`Map.of` whose iteration reaches output (a rule in the F6 guards: iteration of these types is allowed only into a sort); `SutraWiring` uses `LinkedHashMap` (A5) |
| J18 | `ServiceLoader` (aggregate providers) | `R/el/Aggregates:59` | P | report: `ServiceLoader` 66%; TeaVM resolves services at build time (unverified) | low | removed: providers come from the profile list given to `Rachana.with(…)` (A6) |
| J19 | Reflection: `getClass().getSimpleName()` in messages | `R/el/Printer:76`, `R/SutraRegistry:333-334` | P | report: `Class` 63%; simple names need class metadata, which a minifier may strip (unverified) | medium: a message text could differ | messages name the node kind from a field Core owns, never the class name (A1) |
| J20 | `System.nanoTime` / `currentTimeMillis` as a **limit** | `SafeRegex` (match deadline), `E/live/{TopicHub,ViewStream,LiveMetrics}` (transport, stays) | S | present | **high for regex**: whether a match times out depends on the machine's speed, so the same input can succeed on the JVM and fail in a slow browser | `SafeRegex`'s deadline becomes a step budget (a count of NFA steps, already bounded by `maxProgram × maxInput`), deterministic everywhere (A3) |
| J21 | Reflection over records: `getRecordComponents`, `ParameterizedType`, `WildcardType` | `E/view/ViewModelSchema` (the schema generator) | N | report: `RecordComponent` has no entry | none at run time | the generator runs on the JVM at build time only; it is outside both bundles (A14) |
| J22 | `java.nio.file` (`Files`, `Path`), `getResourceAsStream` (`formats.yaml`, `world-regions.yaml`, `signatures.yaml`) | `R/format/{Formats,WorldRegions}`, `R/el/check/Signatures`, `R/parse/SutraBlocks`, `R/{SutraRegistry,UiKitDir,UnderConstruction,RachanaConfiguration}` | P | report: `java.nio.file` 54% of classes full; there is no file system in a browser; class-path resources must be listed at build time (unverified) | none (bytes) | Core reads no file: the three tables are generated into Java constants at build time, or handed in by the host; fragment folders (`SutraBlocks`) become a hook (A3, A6) |
| J23 | Threads and concurrency: `ConcurrentHashMap` (`Functions.LIVE`, `TextParts`/`TextFunctions` pattern caches, `SutraBlocks`), `AtomicReference` (`SutraBlocks`), `CopyOnWriteArrayList`, executors, `ReentrantLock`, `synchronized` | Core: `R/el/{Functions,TextParts,TextFunctions,ElCompiler,DateFunctions}`, `R/parse/{SutraBlocks,PositionalYamlReader}`, `R/format/Formats`, `R/SutraRegistry`; transport: `E/live/*`, `E/wire/ScreenWire` | P | report: `ConcurrentHashMap` 44%, `CopyOnWriteArrayList` 100%, `ExecutorService` and `CompletableFuture` no entry; `java.util.concurrent.atomic` 18% of classes full | none (bytes); size and speed | Core is single-threaded per instance: per-instance state (A6) replaces the static concurrent maps; no executor, lock or atomic in Core; the transport stays in Drishti |
| J24 | `URLEncoder`, `URLDecoder` | `E/control/ViewState` | S | report: both 100% | low (`+` → `%20` already handled) | kept, or a 30-line percent codec if T2 shows weight (A14) |
| J25 | Jackson (`ObjectMapper`, `JsonNode`, `YAMLFactory`, `JsonParser`/`JsonLocation`, annotations `@JsonInclude`) | `R/parse/{PositionalYamlReader,SutraParser,SutraBlocks}`, `R/el/check/Signatures`, `R/format/{Formats,WorldRegions}`, `R/design/ops/Ops`, `R/UnderConstruction`, `E/view/{ViewModel,PanelData,MaskCount,ViewModelSchema}`, `E/live/Patch`: 13 files | N | reflection-based data binding and `Unsafe`-class tricks; Jackson's YAML module parses with SnakeYAML (no direct SnakeYAML use in the scope) | **highest weight of all** (the YAML reader is the input of every compile) | Core's positional YAML reader and canonical JSON writer (A8a, A8b); annotations removed with A14's writer (plan C10). P1 does **not** write the YAML reader: it feeds already-parsed trees through a stub (section 4, T1) |
| J26 | Caffeine (`Cache`, `Caffeine.newBuilder().maximumSize`) | `R/el/ElCompiler` (expression and template caches), `E/bind/MapSupport` | N | uses `sun.misc.Unsafe`/`VarHandle` and threads (unverified) | none | a bounded LRU on `LinkedHashMap(accessOrder)` per instance (A3) |
| J27 | Spring (`@ConfigurationProperties`, `@Configuration`, `@Bean`) | `R/{RachanaConfiguration,RachanaProperties}`, `R/format/Formats`, `E/bind/PanelLimits`, `E/live/LiveProperties` | N | framework | none | `RachanaOptions` record; wiring stays in Drishti (A4) |
| J28 | SLF4J | `R/{RachanaConfiguration,SutraRegistry}` | N (not needed) | | none | `Diagnostics` callback (A3) |
| J29 | HdrHistogram | `E/live/LiveMetrics` | N | | none | stays in Drishti (transport) |
| J30 | `java.util.stream`, `Optional`, `Collections`, `Arrays`, `ArrayDeque`, `IdentityHashMap`, `TreeMap`/`TreeSet`, `LinkedHashMap`/`LinkedHashSet` | streams in 40 files, `Optional` in 23 | S | report: `LinkedHashMap`, `TreeMap`, `Optional`, `IdentityHashMap`, `ArrayDeque`, `Objects` 100%; `Collections` 71%, `Arrays` 67%; `java.util.stream` 80% partial, 20% full | low; streams cost **size and speed** | kept; T1 lists any missing member; T4 decides whether hot paths (`Expr.eval`, aggregate loops) lose their streams |
| J31 | `String.repeat`, `strip`, `isBlank`, `String.join` | 59 files | P | report: `String` 88% (which members are missing is not stated) | low (`strip`/`isBlank` use `Character.isWhitespace`, J15) | kept unless T1 lists one missing |
| J32 | `StackOverflowError` caught as a value | `R/{SutraRegistry,SutraMatcher}`, `E/bind/Binder`, `E/wire/ScreenWire` | P | report: `StackOverflowError` 100% as a class; whether TeaVM turns the engine's `RangeError` into it is **unknown** | medium: a deep input could be a problem on the JVM and a crash in the browser | Core never relies on it: the parser's depth limit (SPEC 4.12) and a YAML depth limit reject first; probe D-L1 |

### 2.3 Language features (bytecode shapes, not classes)

| # | Feature | Where | TeaVM | Plan |
|---|---|---|---|---|
| L1 | records (`equals`, `hashCode`, `toString` through the `ObjectMethods` bootstrap) | 74 files, about 190 declarations | report: `Record` 100%; the `java.lang.runtime.ObjectMethods` bootstrap is not in the report: **unknown** | T1 compiles them. If unsupported: no record's generated `toString` reaches output (a guard), and `equals`/`hashCode` are written by hand only where T1 lists them |
| L2 | `switch` with type patterns and `when` guards (the `SwitchBootstraps.typeSwitch` bootstrap) | `R/el/{Printer,MeasureFunctions}`, `R/el/check/Checker`, `E/view/Emptiness` | **unknown** (not in the report) | if unsupported: four files rewritten as `instanceof` chains (mechanical, conformance holds them) |
| L3 | `instanceof` patterns | 52 files, about 217 lines | S: plain bytecode (`instanceof` + cast) | none |
| L4 | `sealed` interfaces | `R/design/ops/Op`, `E/view/PanelData` | S: a class-file attribute only | none |
| L5 | lambdas, method references (`LambdaMetafactory`), string concatenation (`StringConcatFactory`) | everywhere | S (unverified: TeaVM has handled both for years) | none |
| L6 | `switch` on strings and enums | 38 files | S: plain bytecode over `String.hashCode`, which Java specifies | none |
| L7 | text blocks | none found | – | – |

### 2.4 The ten riskiest uses (ordered)

1. **J1/J2 number text and number reading**: every printed non-whole number, every decimal argument.
2. **J25 Jackson**: the whole YAML and JSON path; the largest replacement (A8).
3. **J3 transcendental maths**: last-place differences across the JVM and three JavaScript engines.
4. **J9/J10 `java.time` and date names**: kept or replaced is T1's call; `ofPattern` without a locale is a defect today.
5. **J17 hash iteration order** reaching output: silent, and already nondeterministic on the JVM for `Set.of`.
6. **L1/L2 records and pattern switches**: bootstraps the report does not mention.
7. **J15 Unicode tables and case mapping**.
8. **J20 regex time limit** measured in wall-clock time.
9. **J8 `DecimalFormat`** for Excel number codes.
10. **J22/J23 files and threads** in Core (structural; A3 and A6 remove them whatever P1 finds).

### 2.5 The JDK allow-list (proposed; T1 confirms it, F6's guard enforces it from A10)

Allowed in `rachana-api` and `rachana-core`:

- `java.lang`: `Object`, `String`, `StringBuilder`, `CharSequence`, `Character` (classification on ASCII only, see J15),
  boxed primitives **without** `Double.toString`/`parseDouble`/`valueOf(String)` and `Float` text, `Math` limited to `abs`,
  `min`, `max`, `floor`, `ceil`, `sqrt`, `signum`, `rint`, `floorDiv`, `floorMod`, `Math.PI`/`E`, `Iterable`, `Comparable`,
  `Record`, `Enum`, exceptions; no `Class` member but `==` comparisons; no `System` but `arraycopy`; no `Thread`.
- `java.util`: `List`, `ArrayList`, `Map`, `LinkedHashMap`, `TreeMap`, `Set`, `LinkedHashSet`, `TreeSet`, `ArrayDeque`,
  `Collections` (unmodifiable views, `emptyList` and the like), `Arrays` (`asList`, `copyOf`, `sort`), `Objects`, `Optional`,
  `Iterator`, `Comparator`, `IdentityHashMap`; `HashMap`/`HashSet` only for lookups (the J17 rule); `List.of`, `Set.of`,
  `Map.of` only as constants never iterated into output.
- `java.util.function`: all.
- `java.math`: `BigDecimal`, `BigInteger`, `MathContext`, `RoundingMode` (no `BigDecimal.valueOf(double)`, no
  `new BigDecimal(double)`; doubles enter through Core's `NumberText`).
- `java.time`: `LocalDate`, `DayOfWeek`, `Month`, `ChronoUnit`, `IsoFields`, `TemporalAdjusters`, **only if** T1 and the D-T
  probes pass; never `ZoneId`, `Clock`, `now()`, `DateTimeFormatter` or `TextStyle`.
- `java.util.stream`: allowed outside hot paths named by T4.

Forbidden in Core: `java.text`, `java.util.regex`, `java.util.Locale` (but `Locale.ROOT` in `toLowerCase`/`toUpperCase` until
A5 removes those too), `java.util.Formatter` and `String.format`, `java.util.ServiceLoader`, `java.lang.reflect`,
`java.util.concurrent` (all), `java.io`, `java.nio`, `java.net`, any
third-party jar. `rachana-tools` may additionally use nothing more: it ships in `rachana.tools.js`.

## 3. Determinism stress list

Each row is one probe: an input run on the reference (the JVM, `ElDeterminismProbeTest` in Drishti, which only prints) and on
P1's build in Node, Chromium, Firefox and WebKit. **P2 fills the JVM column first; any runtime that differs by one byte is a row
of the difference table**, with the Core rule that fixes it (A5's work list). The inputs become
`conformance/1/el/determinism.yaml` in the case format of F1, with the `expect` part empty until P2 fills it from the JVM.
"Spec" gives the result SPEC states where it states one; a JVM result that differs from the spec is a known difference for H6,
not a JavaScript finding.

### 3.1 Number text (SPEC 4.2, the text form; `Values.text`, `Printer.literal`, `NumberText`)

| Id | Expression (`text` = template `"${…}"`) | Spec / what is stressed |
|---|---|---|
| D-N01 | `0.1 + 0.2` | `0.30000000000000004` (shortest round trip) |
| D-N02 | `1 / 3` | 16 or 17 digits: the shortest form, not a fixed 17 |
| D-N03 | `2 / 3`, `100 / 7`, `1e-7 * 3` | shortest form; small numbers: `3.0E-7` in Java's spelling vs `3e-7` in JavaScript's |
| D-N04 | `1e15`, `1e15 + 0.5`, `999999999999999 + 0.5` | the boundary of "whole below 10¹⁵ prints as digits" |
| D-N05 | `1e20`, `1e21`, `1e22`, `123456789012345678901` | spec: `1.0E20`; JavaScript switches to exponent form at 1e21 and spells it `1e+21` |
| D-N06 | `-0 * 1`, `0 * -1`, `-0.0 + 0` | `-0`: JVM prints `0` through the whole-number path; JSON writers differ (`-0` vs `0`) |
| D-N07 | `0 / 0` (empty by 4.5), `sqrt(-1)` (error), `ln(0)`, `exp(1000)`, `-exp(1000)` | `NaN`, `Infinity`, `-Infinity` texts; JSON has no NaN: what the canonical writer emits |
| D-N08 | `5e-324`, `2.2250738585072014e-308`, `1.7976931348623157e308` | subnormal and extreme values |
| D-N09 | `0.000001`, `0.0000001`, `1234567.125` | Java switches to `E` notation below 10⁻³ and at or above 10⁷: the exact cut-overs |
| D-N10 | `round(2.5)`, `round(-2.5)`, `round(0.5)`, `round(1.005, 2)`, `round(2.675, 2)` | half away from zero in decimal (SPEC 4.10 rule 7): `1.01`, `2.68` |
| D-N11 | `trunc(1e300)`, `int(-1e300)`, `trunc(0/0)` | `(long)` of huge values and NaN: saturation |
| D-N12 | `mod(-3, 2)`, `-3 % 2`, `5.5 % 2`, `-5.5 % 2` | truncated `%` vs `mod` |
| D-N13 | `floor(-0.5)`, `ceiling(-0.5)` | `-0` again, through the functions |
| D-N14 | `2 ** 0.5` written `power(2, 0.5)`, `power(10, -5)`, `power(1.1, 2)` | binary `pow` vs exact decimal `1.21` (rule 7) |
| D-N20 | `round_up(0.1 + 0.2, 1)`, `sum([0.1, 0.2, 0.3])`, `sum([1e16, 1, -1e16])` | 15-digit reading of doubles; Neumaier summation |
| D-N21 | `sequence(3, 0.1, 0.1)`, `yearfrac('2024-02-29', '2025-03-01', 'ACT/ACT')` | decimal results' text (`toPlainString`, `stripTrailingZeros`) |
| D-N22 | `fmt(1234.5, 'amount0')`, `fmt(-0.004, 'amount2')`, `fmt(1e9, 'compact')`, `fmt(0.125, 'pct1')` | named formats: half-up rounding, the minus sign `−` (U+2212), the negative-zero case |
| D-N23 | `text(1234.5678, '#,##0.00')`, `text(-1, '0;(0)')`, `text(0, '0;-0;"zero"')`, `text(12345, '0.00E+00')`, `text(0.5, '0%')` | Excel number codes (J8) |
| D-N24 | `value('1,234.5')`, `value('1.234,5', ',', '.')` | number reading with separators |
| D-N25 | `percentile([1,2,3,4], 0.3)`, `median([1, 2])`, `stddev([1, 2, 3, 4])` | aggregates' last digits |

### 3.2 Number reading (`Values.number`, the lexer, `Args.decimal`)

| Id | Input | Stressed |
|---|---|---|
| D-R01 | `'5' * 2`, `' 5 ' * 2`, `'5\t' * 2` | white space accepted by `parseDouble` |
| D-R02 | `'1d' * 1`, `'1f' * 1`, `'0x1p3' * 1`, `'1_000' * 1` | Java's parser accepts the first three: is that Core's rule? (H6) |
| D-R03 | `'Infinity' * 1`, `'NaN' * 1`, `'-Infinity' > 0`, `'inf' * 1` | named values |
| D-R04 | `'9007199254740993' * 1`, `'2.2250738585072012e-308' * 1`, `'0.1e-400' * 1`, `'1e400' * 1` | correct rounding, underflow, overflow |
| D-R05 | `'10' > '9'`, `'1e3' == 1000`, `'.5' == 0.5`, `'+5' == 5`, `'5.' == 5` | number-like text in comparisons |
| D-R06 | the expression `٣ + 1` (Arabic-Indic digit), `１ + 1` (full-width) | the lexer's `Character.isDigit` accepts them; then `parseDouble` refuses: error text and position |

### 3.3 Text ordering, length and case (SPEC 4.7, 4.13)

| Id | Input | Stressed |
|---|---|---|
| D-U01 | `'Z' < 'a'`, `'a' < 'B'`, `'é' > 'z'` | code-unit order, not collation |
| D-U02 | `'￿' < '😀'` (U+1F600 is `D83D DE00`), `'😀' < ''` | UTF-16 unit order vs code point order: the two disagree here |
| D-U03 | `len('😀')`, `left('😀a', 1)`, `mid('a😀b', 2, 1)`, `reverse('a😀')` | lengths and cuts in code units; a cut surrogate pair's text and JSON escaping (`\ud83d`) |
| D-U04 | `sort(['b', 'A', 'a', 'B', '😀', '￿'])`, `unique(['a', 'A'])` | the sort rule of 5.6 |
| D-U05 | `upper('ß')`, `upper('ﬁ')`, `lower('İ')`, `lower('Σ')` at a word end, `proper('ǆemal')` | special casing: `SS`, `FI`, `i̇` (two units), final sigma, title case |
| D-U06 | `upper('ꭰ')` (Cherokee small, Unicode 8), `lower('Ჰ')` (Georgian Mtavruli, Unicode 11), `upper('\u{1E922}')` (Adlam) | Unicode version of the case tables |
| D-U07 | identifiers: `$.ꭰ`, `$.ࡰ` (U+0870, Unicode 14), `$.𑼄` (U+11F04, Unicode 15) | `Character.isLetter` versions in the lexer |
| D-U08 | `trim(' x ')`, `clean('a\u0007b')`, white space ` ` in an expression | `isWhitespace` vs `strip` vs JavaScript's `trim` sets |
| D-U09 | `contains('STRASSE', 'ß')`, `matches('ǅ', '(?i)ǆ')`, `matches('K', '(?i)k')` (Kelvin sign U+212A) | case-insensitive matching by `char` folding |
| D-U10 | `char(128512)`, `code('😀')`, `char(55357)` | code points vs units; a lone surrogate |

### 3.4 Dates (SPEC 4.5, 5.5)

| Id | Input | Stressed |
|---|---|---|
| D-T01 | `edate('2024-01-31', 1)`, `edate('2023-01-31', 1)`, `edate('2024-02-29', 12)` | month ends, leap days: spec `'2024-02-29'` |
| D-T02 | `date(2026, 14, 1)`, `date(2026, 0, 1)`, `date(2026, 1, 0)`, `date(2026, 2, 30)` | Excel carry-over both ways |
| D-T03 | `week('2027-01-01')`, `week('2026-12-31')`, `week('2020-12-31')` | ISO week: spec `53` |
| D-T04 | `days_between('2024-02-28', '2024-03-01')`, `'2026-11-17' - '2026-11-01'`, `'2026-11-17T23:59:59Z' - '2026-11-16'` | day counts; a timestamp counts by its date (no zone) |
| D-T05 | `yearfrac` over the five bases across 29 February; `days360('2024-02-29', '2024-03-31', true)` | day counts |
| D-T06 | `datedif('2024-02-29', '2025-02-28', 'Y')`, `'YD'`, `'MD'` | Excel's odd units |
| D-T07 | `date('0001-01-01')`, `date('9999-12-31')`, `date('1582-10-10')`, `edate('9999-12-31', 1)` | range ends; the Gregorian switch (proleptic) |
| D-T08 | `date('2026-02-29')`, `date('2026-13-01')`, `date('26-1-1')`, `date('2026-1-1')` | refusals and their messages |
| D-T09 | `hour('2026-03-08T02:30:00-05:00')`, `timestamp('2026-11-01T01:30:00Z')` | offsets kept as written, no zone arithmetic |
| D-T10 | `shift_date('2026-11-27', 1)`, `business_days_between('2026-12-24', '2027-01-04')` with **no** calendar hook | the weekends-only default vs the reference's `USNY` (SPEC 9.1) |
| D-T11 | `text('2026-11-17', 'dddd d mmmm yyyy')`, `text('2026-11-17', 'ddd mmm')` | English names (J9) |
| D-T12 | `fmt('2026-11-17', '<a format with pattern MMM>')` under a JVM started with `-Duser.language=de` | J10: a JVM-vs-JVM defect today |
| D-T13 | a form field with zone `America/Nuuk`, `Europe/Kyiv`, `US/Pacific-New` | J11: zone names of different database versions |
| D-T14 | the host's `today` = `2024-02-29`; `asof()`, `edate(asof(), -12)` | the context date crossing a leap day |

### 3.5 Booleans and mixed operators (SPEC 4.5 to 4.8, 9.5)

| Id | Input | Spec |
|---|---|---|
| D-B01 | `true + 1`, `1 + true`, `true + true`, `'x' + false` | `'true1'`, `'1true'`, `'truetrue'`, `'xfalse'` |
| D-B02 | `true * 1`, `true - false`, `-true`, `true / 2` | `1`, `1`, `-1`, `0.5` |
| D-B03 | `true == 1`, `true == 'true'`, `1 == '1.0'`, `true == '1'` | `true`, `true`, `true`, the number rule |
| D-B04 | `true > false`, `true < 2`, `'true' > 'false'` | ordering of booleans |
| D-B05 | `0 < 5 < 10`, `10 < 5 < 1` | `(0 < x) < 10`: a boolean against a number |
| D-B06 | `!'0'`, `!0`, `!(0/0)`, `![]`, `!$.missing` | truthiness |

### 3.6 Regular expressions (SPEC 5.4; Core's engine, J13)

| Id | Input | Stressed |
|---|---|---|
| D-X01 | `regex_extract('aaa', 'a*?', 0)`, `regex_extract('abab', '(ab)+', 1)`, `regex_extract('ab', '(a|ab)(b?)', 2)` | match choice (leftmost, priority) |
| D-X02 | patterns `'(a)\\1'`, `'(?=a)'`, `'a++'`, `'(?>a)'`, `'\\bx'` as literals | refused at compile time, with the message |
| D-X03 | `regex_replace('a.b', '.', '-')`, `regex_replace('x', 'x', '$1')`, `regex_replace('x', 'x', '\\$')` | replacement syntax (group references, escapes) |
| D-X04 | `matches('é', '\\w')`, `matches('٣', '\\d')`, `matches(' ', '\\s')` | class definitions (ASCII or Unicode) |
| D-X05 | a 200-character pattern; a 201-character one; a value of 8,193 characters | the limits |
| D-X06 | `(a|aa)*c` over 8,000 `a`s | linear time: same answer, no time-out on any runtime (J20) |
| D-X07 | `matches('A\nB', '^B$')`, `matches('a\r\n', 'a$')` | `^`/`$` and line ends |

### 3.7 Iteration order, limits and errors

| Id | Input | Stressed |
|---|---|---|
| D-O01 | a Sutra whose panels `a`, `b`, `c` wait on each other in a cycle (events), and a second with two disjoint cycles | the cycle message's first panel and location (J17, `SutraWiring.cycles`) |
| D-O02 | `group($.sales, 'region', 'revenue')` with keys inserted in a scrambled order, 600 keys | key order and the 500-group cut (5.6) |
| D-O03 | an unknown function `sumx(1)`, `RCH-2101` "known: …" list | sorted list text |
| D-O04 | the problem list of a Sutra with ten problems of mixed kinds | problem order (SPEC 6.1) |
| D-O05 | `unique([1, 1.0, '1', 2, 0.1 + 0.2, 0.3])` | equality used by `unique` (`Double.hashCode` of `-0.0` vs `0.0`) |
| D-L1 | an expression nested 201 deep; a YAML document nested 1,000 deep | the depth limit fires before any stack overflow (J32) |
| D-L2 | an expression of 10,001 characters; a list literal of 1,001 items; `sequence(10001)` | limits and their messages |
| D-E1 | `sqrt(-4)`, `eomonth('soon')`, `round_up('abc')` | error messages of `RCH-2102`: no class name, no stack trace (T8) |

### 3.8 Real-number functions (SPEC 5.8, 5.9; J3)

| Id | Input | Stressed |
|---|---|---|
| D-M01 | `exp(1)`, `exp(-745)`, `exp(709.78)`, `ln(2)`, `ln(1e-300)`, `log(1000, 10)`, `log(8, 2)` | last-place agreement; `log10` vs `log/log` |
| D-M02 | `power(2, 0.5)`, `power(10, 0.1)`, `power(1.0000001, 1e7)`, `power(-8, 1/3)` | `pow` (V8's `**` is known not to be correctly rounded: unverified for current versions) |
| D-M03 | `norm_s_inv(0.99)`, `norm_s_dist(1.96, true)`, `norm_inv(0.025, 0, 1)`, `t_inv(0.975, 10)`, `chisq_inv(0.95, 3)`, `gamma(0.5)`, `gamma_ln(100)`, `erf(0.5)`, `beta_inv(0.5, 2, 3)` | spec: `2.3263478740408408`; series and continued fractions over `exp`/`log` |
| D-M04 | `irr([-100, 60, 60])`, `xirr(…)`, `rate(10, -100, 800)`, `npv(0.1, [100, 100])`, `pmt(0.05/12, 360, 200000)` | root finders over `pow`/`log1p` |
| D-M05 | `fisher(0.5)`, `tanh`-based results; `atan2` in chart bindings | the remaining functions of J3 |

The whole list is about 150 probe inputs. P2 may add rows; it never removes one.

## 4. P1: the steps T1 to T10

### 4.1 Preparation (inside the time-box)

1. Branch `proof/js` in this repository (pushed, **never merged**, never force-pushed). Copy, unchanged, `el`, `el/check`,
   `format`, `model`, `parse` and the five root files of section 2.1 from Drishti `wip/i7a` (or the released i7a if out) into
   `proof/js/java/src/main/java`, keeping package names. Record the source commit in `proof/js/SOURCE.txt`.
2. Cut the product dependencies **by hand, minimally**: `DataNode`/`NodeType` → a small `Node` interface; `DrishtiException`,
   `ErrorCode` → one `RachanaException` with a code string; `BusinessCalendar` → weekends only; `SafeRegex` → a copy; Jackson in
   the readers → replaced by a stub that accepts a `Map`/`List` tree (P1 does not write the YAML reader: that is A8); Caffeine →
   `LinkedHashMap` LRU; Spring and SLF4J → deleted; resources → Java constants. Each cut is one line in `proof/js/CUTS.md`.
   **No behaviour is changed** beyond the cut; the JVM run of the probe list on `proof/js` must equal the run on Drishti
   (the copy is faithful) before any JavaScript is measured.
3. Build with Maven and TeaVM's Maven plugin, pinned to the then-current TeaVM release (the version and its date in the result).
   JavaScript target, ES module output, minified; the optimisation level the release documents as the strongest.
4. Runners: a Node script and one HTML page loaded under Playwright in Chromium, Firefox, WebKit (Playwright's pinned browsers;
   versions recorded). Both read the probe list and a sample of cases and write `results/<runtime>.jsonl` in the canonical form.

### 4.2 The steps

Time-box: **5 builder-days in all** (plan P1 size), inside N13's **one week of light machine time** in i7b. A step that runs over
its box stops; what was found is written down and the step is marked "not answered" with the reason. P1 never stretches.

| Step | What is done | Measured | Passes when | Fails when | Box |
|---|---|---|---|---|---|
| **T1** compiles | build; read every TeaVM error and warning; for each missing class, member or bootstrap: the smallest change inside the copy that removes it | the gap list: API, files, the replacement (one, inside Core), lines changed | every gap has **one** replacement inside Core and no second code path; the gap list matches section 2.2 or adds rows to it | a gap needs a second implementation per runtime, or a construct (L1, L2) cannot be compiled and cannot be rewritten mechanically | 1.5 d |
| **T2** size | `rachana.runtime.js` from the P1 cut; `rachana.tools.js` **estimated** as runtime + `design/ops` + root editors compiled in (or measured if T1 left time) | raw, minified, gzip, Brotli (quality 11) bytes; the top 20 classes by emitted size (TeaVM's report) | runtime ≤ 150 KB Brotli; tools ≤ 350 KB Brotli | either is above; the top-20 list says which replacement of section 2.2 would bring it under (written as a sized proposal) | 0.25 d |
| **T3** start | Node and the three browsers, cold and warm: module evaluation start → first compiled Sutra (the "typical" Sutra of COMPILER_AND_RUNTIMES 7, fed as a parsed tree) | median and p95 of 30 runs each | ≤ 30 ms after module load in Chromium on the reference laptop; Firefox and WebKit recorded | above 30 ms in Chromium | 0.25 d |
| **T4** evaluation speed | the twenty expressions of section 4.3, each over 10,000 records, compiled once and evaluated per record, against a **hand-written JavaScript** evaluator of the same twenty (written for the proof only, thrown away) | ns per evaluation, median of 10 runs after warm-up; JVM figure for reference | each within **3×** of the hand-written figure, and P1, P4 of COMPILER_AND_RUNTIMES 7 met | any one above 3× (named; fallback 2's intrinsic candidate) | 1 d |
| **T5** data hand-over | a `Node` over plain JavaScript objects and one over Arrow columns (the facade's shape, minimal); a live change of 100 rows in a 50,000-row table | allocations per change (Chromium heap snapshot delta), time per change | no allocation proportional to the table size per change | allocation grows with table size | 0.5 d |
| **T6** same output | the probe list of section 3 plus the cases the copy can run (expression cases drafted by F2, as far as they exist) through the JVM, Node, Chromium, Firefox, WebKit | bytes of `results/<runtime>.jsonl`; every difference listed with its probe id | byte-identical, **or** every difference is in a row of section 2.2 whose replacement (A5) is already planned | a difference with no planned owner, or one that needs a per-runtime code path | 0.75 d |
| **T7** strict pages | the runtime bundle under `Content-Security-Policy: default-src 'self'; script-src 'self'; require-trusted-types-for 'script'`, from `file://`, and inside a Web Worker | console errors; a scan of the bundle for `eval`, `new Function`, `setTimeout(string)` | no violation in the three browsers; the scan is empty | any violation | 0.25 d |
| **T8** errors | the error probes (D-E1, D-L1, D-X02 and malformed input) in the browser | what the caller receives | always Rachana's problem: code, message, position; never a TeaVM stack trace or an engine `RangeError` | any generated trace or engine error escapes | 0.25 d |
| **T9** build anywhere, reproducibly | build twice on Linux from clean (`mvn clean`), once on the Windows QA machine (JDK only; TeaVM is Java, no `.exe`) | SHA-256 of both bundles | same bytes on all three builds | any difference (cause written down: time stamps, map order, paths) | 0.25 d |
| **T10** the tool | TeaVM's licence and the notices its emitted class-library code needs; release cadence and maintainers over the last two years; whether **J2CL** (the named second tool) can build the same copy: read its JRE emulation for J1, J9, J12 | a written page | the licence is compatible with this repository's licence and a notice line is drafted; a second tool exists and its known gaps are listed | an incompatible licence, or no maintained release in the last 12 months | 0.25 d |

Total 5 days. Order: T1 (gate for all) → T6 (the expensive question) → T2 → T3 → T4 → T5 → T7 → T8 → T9 → T10. If T1
fails, T2 to T9 are not run and section 6 applies directly.

### 4.3 The twenty expressions of T4

Over a record `$` with `legs` (10 elements) and a row `@` with `amount`, `price`, `qty`, `ccy`, `trade_date`, `type`, `desk`,
`book`, `status`:

| # | Expression | Exercises |
|---|---|---|
| 1 | `@.amount` | path read |
| 2 | `@.amount * 2` | arithmetic |
| 3 | `@.price * @.qty - @.amount` | arithmetic chain |
| 4 | `@.amount > 1000000` | comparison |
| 5 | `@.ccy == 'USD'` | equality on text |
| 6 | `@.ccy in ['USD', 'EUR', 'GBP']` | membership |
| 7 | `@.amount > 0 && @.status != 'CANCELLED'` | logic, short-circuit |
| 8 | `@.type == 'FX' ? @.amount : -@.amount` | conditional |
| 9 | `'Leg ' + #index` | text joining, number text |
| 10 | `"${@.book} / ${@.ccy}"` | template |
| 11 | `round(@.price, 2)` | decimal rounding |
| 12 | `abs(@.amount) / 1e6` | non-whole number result |
| 13 | `nonblank(@.desk, 'n/a')` | function call |
| 14 | `upper(left(@.book, 3))` | text functions |
| 15 | `@.trade_date >= '2026-01-01'` | text ordering of dates |
| 16 | `days_between(@.trade_date, '2026-11-17')` | date arithmetic |
| 17 | `sum($.legs, 'notional')` | list aggregate |
| 18 | `size($.legs[?@.type == 'FLOAT'])` | filter |
| 19 | `map($.legs, @.notional / 1e6)` | form, list result |
| 20 | `matches(@.book, '^BOOK-[0-9]+$')` | Core's regex |

### 4.4 Machine budget (N13)

- **When**: in i7b after the i7a drill has ended; **never beside a drill or a benchmark**, and one heavy job at a time on the
  machine (TeaVM build, a browser run and a Drishti test are each one job).
- **Size**: JVM builds with `-Xmx4g` at most; one TeaVM build at a time; browser runs one browser at a time; the probe and T4
  data stay small (10,000 records, never the million-row sets).
- **Where**: everything under this repository's `proof/js` and the session's scratch folder; downloaded tools in their own
  folder; temporary files removed at the end of each run.
- **Downloads**: TeaVM and its Maven plugin from Maven Central, Playwright's browsers; listed with versions in the result.
  Nothing is added to Drishti's tree except P2's one probe test (`ElDeterminismProbeTest`, deleted after).

## 5. Budgets to measure

Every figure is recorded as measured, with the machine, the runtime versions and the date; a figure above its target is a miss,
not a reason to change the target (section 1).

| # | Measure | Target | Step | Becomes |
|---|---|---|---|---|
| B1 | `rachana.runtime.js`, Brotli 11 | ≤ 150 KB | T2 | P10 |
| B2 | `rachana.tools.js`, Brotli 11 (estimate in P1) | ≤ 350 KB | T2 | P10 |
| B3 | runtime, minified (no compression) and gzip | recorded only (the parse cost of the browser follows raw size) | T2 | – |
| B4 | the weight of each replacement candidate: `java.time` in, `BigDecimal` in, regex in, streams in (from TeaVM's class size listing) | recorded; used to choose between J9's two options | T2 | A5 |
| B5 | module evaluation to first compiled Sutra, typical Sutra, Chromium | ≤ 30 ms (p95, warm cache of the bundle) | T3 | P3 |
| B6 | parse + check, typical Sutra (12 panels, 60 expressions), warm, p95 | ≤ 8 ms (browser) | T3 (from a parsed tree; the YAML read is A8's) | P1 |
| B7 | parse + check, large Sutra (50 panels, 400 expressions), warm, p95 | ≤ 30 ms (browser) | T3 | P2 |
| B8 | evaluation per expression, the twenty of 4.3 | ≤ 3× the hand-written JavaScript | T4 | A15's intrinsic list |
| B9 | one evaluation of a typical expression, ns, against the JVM's | recorded (expected 2× to 5× the JVM; unverified) | T4 | – |
| B10 | bind of a typical Sutra over 1,000 rows | ≤ 10 ms (browser), if T1 left time to compile enough of the binder path; else "not answered" | T4 | P4 |
| B11 | one live patch of 100 changed rows on 50,000 rows: time and allocation | ≤ 2 ms; no allocation proportional to 50,000 | T5 | P6 |
| B12 | heap of one compiled typical Sutra | ≤ 4 MB excluding rows | T3 | P11 |
| B13 | build time of the bundles, clean, on the machine | recorded | T9 | the drill's lane time |

WebAssembly (W1 to W6 of COMPILER_AND_RUNTIMES 5.3) is **not** part of P1: its proof is R2's. P1 records only whether the TeaVM
release it used also offers a WebAssembly GC target, as a note.

## 6. Fallbacks, in order, with their triggers

| Order | Fallback | Taken when | Cost noted for the owner |
|---|---|---|---|
| 0 | **Go**: TeaVM, as planned | T1, T6 to T10 pass; T2 to T5 pass or miss by an amount the replacements of section 2.2 close (each with its size) | A15 as planned (8) |
| 1 | **A second cross-compiler (J2CL)** on the same source | T1 fails for a reason in TeaVM itself (a bootstrap, a class-library gap that has no Core replacement), or T9, or T10 | re-run T1, T2, T6 on J2CL (about 3 days); J2CL's JRE emulation is believed to lack `java.time` and to use JavaScript `RegExp` for `java.util.regex` (unverified), which section 2.5's allow-list already avoids |
| 2a | **Split by weight**: tools in the worker, loaded on demand | only B2 misses | none for semantics; a loading rule in the facade |
| 2b | **Intrinsics**: hot paths (path read, comparison, arithmetic, the aggregate loop) written in JavaScript behind the same Java interfaces, each held by conformance cases | only B8 misses (named expressions) | a second source for **those** paths only; every one listed and held by cases |
| 3 | **A hand-written JavaScript evaluator for Core only**, generated tables from `rachana.lang.json`, held by the full suite | 1 and 2 cannot pass, and **only with the owner's explicit acceptance** (DECISIONS D3) | two evaluators; never chosen to save the proof's week |

If any fallback is taken, i8a is unchanged for Drishti (JVM only, R13 of the plan) and A15 is re-planned on the fallback before i8
is cut.

## 7. What the result file holds

`docs/plans/JS_PROOF_RESULT.md` on `main`, written by P1 and completed by P2:

1. versions: TeaVM, JDK, Node, the three browsers, the machine;
2. T1 to T10: each with its measured answer, pass or fail against section 4.2, or "not answered" with the reason;
3. the gap list of T1 (becomes A5's and A8's input and the JDK allow-list of section 2.5, corrected);
4. the budgets of section 5 as measured;
5. P2's difference table: probe id, JVM bytes, each runtime's bytes, the Core rule that removes the difference, the owner step;
6. **go, or the fallback taken**, with the reason; for G1's review.

## 8. Points for the owner

These are not decided by P1; they are the places where the proof will force a choice, listed now so that the answer is ready.

| # | Point | Recommendation |
|---|---|---|
| Q1 | SPEC 5.8 pins real-number results "to a tolerance stated per case", while conformance compares bytes | Core's own `CoreMath` (J3) makes the bytes equal in every runtime; tolerance then stays only for third-party ports. If the owner prefers tolerances, the `expr` case needs a `tolerance` field (F1) and the canonical comparison an exception for it |
| Q2 | Java's number grammar (`'1d'`, `'0x1p3'`, `'Infinity'` as numbers) is today's behaviour through `parseDouble` | Core's grammar: decimal digits, one point, an exponent, optional sign, white space trimmed; `NaN` and `Infinity` are not number-like text. Settled in H6 with the known differences |
| Q3 | Identifiers and digits beyond ASCII in the lexer (D-R06, D-U07) | ASCII digits only; identifier letters by a stated Unicode version, or ASCII only. Settled in H6 |
| Q4 | `-0` | prints `0` everywhere, and canonical JSON writes `0` |
