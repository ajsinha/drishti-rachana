<!-- Copyright (c) 2026 Ashutosh Sinha. Specification licence: ../../../LICENSE-SPEC.md (draft, not yet in force). -->
# Assertions of Drishti's expression tests that are not cases (plan F2)

Drafted by reading `compute/drishti-rachana/src/test/java/**/el/*Test.java` (read only). Every other assertion of those tests is a
case in a sibling file of this folder with `source: <TestFile>#<method>`, all `verified: false`.

| Test and method | What it asserts | Why it is not a case |
|---|---|---|
| `PrinterTest#printsWhatItParses`, `#dropsParenthesesTheGrammarDoesNotNeed` | the Java `Printer` writes a parsed `Expr` back as text | tests a Java AST printer, not language behaviour; no case kind prints an expression. Candidate for the `edit` area once the tools layer is specified |
| `ElTest#reportsDocumentPathsForLiveUpdates` | `paths(consumer)` of a compiled expression | converted to the `reads` field of `el/paths-operators`; the Java test sorts the set, the case uses SPEC's first-read order (flagged in the case's note) |
| `ElTest#integerArithmeticMatchesJava` (300 seeded cases), `#comparisonsAgreeWithJava` (200) | randomised arithmetic and comparison against Java `int` | generated from a seed, not a fixed list; four and three sample inputs are written out. Randomised agreement belongs to the determinism probes (plan P2) |
| `ValuesNumberTest` | `Values.couldBeNumber` and `Values.number` (Java API) | `couldBeNumber` is an internal fast path; `Values.number` is converted by multiplying the text by 1 in `el/paths-operators` |
| `MaskTest#whatLiesUnderAMaskIsMasked` (`DOC.at("counterparty.address.city").isMasked()`, `DOC.at("notional.x").isMissing()`) | the document node API | `DataNode.at` is Java API; the two path results are cases in `el/masking` where an expression can say them |
| `MaskTest#valuesDerivedFromAMaskAreMasked` (`F.format("signed0", masked)`, `F.format("date", MASK)`) | the shared format table masks | belongs to the `format` area (`format` cases), not here; to write when F-series format cases are drafted |
| `MaskTest#aConditionOnAMaskIsNeverTrue` (`Values.truthy(...)` is false) | a masked result is not truthy | no case field reads truthiness; each condition is pinned as a masked result (SPEC 4.9 rules 1 and 2), which implies it |
| `ElLimitsTest#theLimitsAreSettingsAndLengthIsBoundedToo` (`new ElLimits(0, 10)` throws) | a constructor refuses a zero bound | Java API; a host setting, not language behaviour |
| `ElLimitsTest` 3,000 to 100,000 level inputs | depth bound with huge inputs | the schema allows expressions of at most 20,000 characters; cases use 300 levels and `$` plus 5,000 `.a`. Stack-overflow safety at 100,000 levels stays a Java test |
| `ListFunctionsTest#aggregatesCompileAndAreRefusedWithTheReasonWithoutTheEngine` (`sum`, `avg`, `min`, `group`, `measure` refused without the pivot engine; `Functions.catalogue()`) | aggregates are an engine feature of Drishti | SPEC 5.7 puts the list aggregates in Core, where they compute; the refusal is a defect-shaped difference of the reference module without its engine (SPEC 9). Only `min(3, 1, 2)` and `group($.NOPE, 'x')` are cases. The aggregates' results are to come from `functions.yaml` examples (H5) |
| `ListFunctionsTest#theOldListFormsKeepTheirResults` | old forms | converted (size, first, last, flatten) |
| `LogicFunctionsTest#theOneNamesAndTheOldReadAlike` (`Functions.arity()` keys), `#theEditorSchemaOffersTheOneNamesAndChecksTheOld` (`RachanaSchema.build`) | the Java function registry and the editor schema | registry and editor-schema API; the catalogue is `spec/1/functions.yaml` |
| `TextFunctionsTest#theTextTestsHaveOneNameAndTheOldSpellingsStillRead` (`Functions.oldSpellings()` map of five) | the old-spelling table | table API; the five spellings are SPEC 5.10 and `functions.yaml` `deprecated`; each is a case of its own reading where the test evaluates it (`startsWith`, `matchesCase`, `oneOf`, `days`, `today`) |
| `FoldedSyntaxTest#todayIsReadAsAsOf` (`Functions.oneName("today")`) | the old-spelling table | as above; `today()` and `asof()` are cases in `el/dates` |
| `FoldedSyntaxTest#namedArgumentsBindBySignature` (`EL.compile(...)` is an `Expr.Call`, `calc.named()` keys, `calc.arg("ignore")`) | AST shape of a pivot `calc` call | Java AST API and a Drishti-profile function (`calc`) |
| `FoldedSyntaxTest#theParentPathReadsTheElementAbove` (`EvalContext.withRows`) | a two-row context | converted with `given.parents`; the `LEG_ID` 9 case uses a row `{AMOUNT: 5}` and parents `[{LEG_ID: 9}, record]` |
| `CheckedModeTest#refusesWithDelsCodes` rows with `trades.`/`desks.` aliases, aggregates (`sum`, `count`, `percentile`, `calc`, `rank`, `ytd`, `measure`, `share_of`, `parent_of`, `as_of`, `avg_by`, `latest`), `param.`, `user.`, scopes AGGREGATE and RULE, and the masked-field rows (`DRS-9161`) | Drishti's pivot model | these need a model, row-rule or aggregate scope that is the Drishti profile, not Core, or a masked field in `types` (the `check` type map has no way to mark a field masked). To be written with the Drishti profile's own suite |
| `CheckedModeTest#typesWhatIsRight` rows with aggregates, `param.shock`, `user.desks`; `#avgByCannotGroupOnAMaskedField`; `#problemsPointAtWhereTheyWereWritten`; `#requireRefusesWithTheCode` (`CheckedMode.require`) | the same model; `Problem.start/end` Java API | profile functions and Java API; the `expr` positions of a refusal could be added once H1 records them |
| `CheckedModeTest#typesWhatIsRight` assertions of "no error" without a type (`floor($.n / 2)`, `$.tradeDate >= '2026-10-01'`, `... not in [...]`) | the expression checks without a problem | a `check` case must name `type` or `problems`; no type is asserted. To be added when H1 records the types |
| `CheckedModeTest#everyCatalogueExampleChecks` | every example of `signatures.yaml` checks | belongs to the `functions.yaml` example harvest (H5), not to this folder |
| `FinanceFunctionsTest`: Java helper `price(y)` and the central-difference convexity | expected value computed in the test | the expected numbers are computed in Python by the same formulas and marked in the notes; H1 replaces them with the reference's values |
| `DateFunctionsTest#yearfracMatchesExcelAndIsda` | the result set to 8 places HALF_UP | written as `round(yearfrac(...), 8)`; the case id names the dates and basis |
| `TextFunctionsTest#regexExtractTakesTheMatchOrAGroup` last assertion | `ErrorCode.REGEX_REFUSED` at run time | SPEC 6.2 has no code for it; mapped provisionally to `RCH-2102` and tagged `code-unmapped` (decision for G1) |

## DRS- to RCH- mapping used

SPEC 6.2: Core keeps the reference's numbers, one to one. `DRS-2101` is `RCH-2101` (does not compile), `DRS-2102` is `RCH-2102` (evaluation
error), `DRS-9127`, `DRS-9129`, `DRS-9152`, `DRS-9161` are `RCH-9127`, `RCH-9129`, `RCH-9152`, `RCH-9161`. The Java tests assert the code
through `ErrorCode.EL_SYNTAX` and `ErrorCode.EL_EVAL` plus `contains(...)` words; the cases pin the code and, for a short expression, the
message start of SPEC 6.3 (`expression '<source>': `), and list the words the Java test checks in `note` (a `contains` check has no case field).
