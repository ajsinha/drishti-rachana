<!-- Copyright (c) 2026 Ashutosh Sinha. Proprietary: see LICENSE. -->
# R0 H2: the harvest of Drishti's Sutra tests

Plan [R0_R1_I8_PLAN](../plans/R0_R1_I8_PLAN.md) H2 (SPLIT_PLAN 0a). Drishti's tests were **read** at tag `v2.9.11.i_7a` (Drishti is
not changed: owner rule 2026-11-19); every case was recorded on the reference (the i7a jars, through
`tools/reference-bridge/RefBridge.java`, Java 21) with the driver's record mode, then reviewed against the Java assertion it replaces
(code, words, line, validity) before `verified: true`. Where a Java test's `anyMatch` checks one problem, the case pins the complete,
ordered list with exact messages: stricter, never looser. Deleting the Java assertions is a Drishti step:
[DRISHTI_MIGRATION_TODO](../plans/DRISHTI_MIGRATION_TODO.md) section 4.

## Cases

| File | Cases | Verified | From |
|---|---|---|---|
| `problem/controls.yaml` | 43 | 43 | ControlsTest |
| `el/controls-ctl.yaml` | 20 | 20 | ControlsTest (expressions over `ctl`, projection, `within`, `oneOf`, masks) |
| `problem/forms.yaml` | 17 | 17 | FormTest |
| `problem/drill-levels.yaml` | 8 | 8 | DrillLevelsTest |
| `problem/panel-source.yaml` | 3 | 3 | PanelSourceTest |
| `problem/screen-layout.yaml` | 18 | 18 | ScreenLayoutTest |
| `problem/nested-tabs.yaml` | 10 | 10 | NestedTabsTest |
| `problem/pivot-options.yaml` | 23 | 23 | PivotOptionTest |
| `problem/wiring.yaml` | 21 | 21 | SutraWiringTest |
| `problem/phone.yaml` | 12 | 12 | PhoneReaderTest, SutraLayoutEditorTest (span and height grammar) |
| `problem/blocks.yaml` | 1 | 1 | SutraBlocksTest |
| `edit/ops.yaml` | 41 | 41 | OpApplierTest (`edit` kind: one operation of `design/ops` per case) |
| **Total** | **217** | **217** | |

Every file is `profile: [core, drishti]`: the tests' Sutras carry `match:`, a Drishti-profile key. The `edit` operation names are the
reference's in kebab case (`set-option` is `setOption`), their fields the reference's own (`design/ops/Ops.java`).

## Ledger: per test method

`n` = assertion lines (`assertThat`, `assertThatThrownBy`) in the method, as read; "validity" = the case pins that the Sutra compiles
with no problem (`{ok: true, problems: []}`).

### ControlsTest → `problem/controls`, `el/controls-ctl`

| Method | n | Cases | Not harvested (reason) |
|---|---|---|---|
| everyKindReadsWithItsFields | 9 | `every-kind-valid` | 8: `Control` ids, kinds, `from`, `remember`, `required`, `choices`, `placement`, `panel` (model) |
| aSutraWithoutControlsHasNone | 1 | `no-controls-valid` | `controls()` empty (model) |
| malformedControlsAreDrs2034 | 11 | `malformed-*` (11) | none |
| duplicateIdsAreDrs2035 | 1 | `duplicate-ids` | none |
| aCtlReferenceToAnUnknownControlIsDrs2036 | 4 | `ctl-unknown-control`, `ctl-with-no-controls`, `ctl-unknown-in-title`, `ctl-known-control-valid` | none |
| aCascadingControlNeedsAnEarlierParentDrs2037 | 2 | `cascading-without-parent`, `cascading-parent-declared-later` | none |
| aSliderReadsItsBoundsStepAndHandlesAndIsCheckedDrs2034And2039 | 14 | `slider-*`, `dropdown-takes-no-step`, `dropdown-takes-no-range` (9) | 5: slider accessors (model) |
| onSelectMustNameAControlAndPanelsDrs2038 | 4 | `on-select-*` (4) | none |
| defaultsMustFitTheKindDrs2039 | 6 | `*-default-*`, `defaults-valid` (6) | none |
| wherePivotFreeOptionsAreChecked | 3 | `where-not-a-condition`, `where-on-kv-refused`, `limit-from-ctl-valid` | none |
| ctlReadsTheControlValuesInExpressions | 6 | `el/controls-ctl#ctl-*` (6) | none |
| projectionListsAFieldOfEveryElement | 5 | `projection-ids`, `projection-size`, `projection-missing-is-empty` | `DataNode.size/get` calls (Java API; their values are `projection-ids`) |
| withinAndOneOfFilterRows | 10 | `within-*`, `oneof-*` (10) | none |
| aMaskedValueIsNeverWithinOrOneOf | 1 | `masked-never-within` (a masked condition, SPEC 4.9) | none |
| theWorkbenchSetControlsOpWritesTheSection | 8 | none | an `edit` case with `set-controls`: to write |
| thePanelOptionsAreKeptOnThePanel | 2 | `panel-options-valid` | `options().get(...)` (model) |

### FormTest → `problem/forms`

| Method | n | Cases | Not harvested (reason) |
|---|---|---|---|
| aFormReads | 6 | `full-form-valid` | accessors (model) |
| lintSaysWhatIsWrongInWords | 5 | `edit-mode-unknown`, `field-type-unknown`, `rule-reads-unknown-field`, `rule-severity-unknown`, `step-unknown`, `max-length-not-a-number`, `key-not-a-field`, `pattern-not-a-regex`, `form-without-how-it-opens`, `edit-cells-without-key` | the two `Form.problems(Map)` calls are written as Sutra-level cases (same words) |
| expressions | 8 | none | `FormExpr` API |
| theServerChecksValuesAndRules | 6 | none | `Form.check` (server-side) |
| rolesAndReadOnly | 5 | none | `editableBy`, `enterableBy` |
| moneyDatetimeEntityAndDefaults | 21 | `money-*`, `datetime-*`, `default-formula-reads-unknown-field`, `entity-source-where-and-search` (6) | 15: `currency`, `zone`, `lookup`, `Form.amount`, `check`, `withDefaults` (API) |
| theJsonSchemaDescribesForms | 1 | none | `RachanaSchema` text |

### PanelSourceTest, DrillLevelsTest

| Method | n | Cases | Not harvested (reason) |
|---|---|---|---|
| PanelSourceTest#everyKindThatReadsDataTakesASource | 2 (loop) | `table-takes-source`, `kv-takes-source`, `metric-takes-source` | `PanelKind.accepts`: the compiler does not refuse `source:` on `links`/`provenance` (reference: ok), so the refusal is not observable |
| DrillLevelsTest#aTableDrillsIntoLevelsAtAnyDepth | 6 | `three-levels-valid` | 5: `levels` accessors |
| DrillLevelsTest#aBadLevelIsRefusedWithTheReason | 6 | `levels-not-a-list`, `level-without-rows`, `level-sortby-refused`, `level-column-without-bind`, `levels-and-children`, `seven-levels` | none |
| DrillLevelsTest#aBrokenExpressionInALevelIsReportedAtLoad | 1 | `broken-expressions-in-levels` | none |

### ScreenLayoutTest, NestedTabsTest

| Method | n | Cases | Not harvested (reason) |
|---|---|---|---|
| aLayoutWithAreasReadsEveryPart | 13 | `layout-with-areas-valid` | 12 layout accessors |
| aSutraWithoutALayoutFlowsAsBefore | 2 | `no-layout-flows-valid` | `layout()`, `area()` |
| theFlowGridTakesSectionsAndOrder | 3 | `flow-grid-sections-valid` | `sections()`, `toView()` |
| lintFindsUnknownAreasOverlapsAndBadSplitsOrTabs | 14 | 13 refusals + `layout-with-areas-valid` | none |
| aNamedAreaNeedsTheLayoutToNameIt | 2 | `named-area-without-layout`, `order-out-of-range` | none |
| aTabsPanelHoldsPanelsReadAgainstTheTabsElement | 4 | `tab-panels-valid` | `parts()` accessors |
| theSutraWrittenBackParsesToTheSamePanels | 2 | `tab-panels-valid` | the `SutraWriter` round trip |
| anExpressionInsideATabIsCheckedLikeAnyOther | 1 | `tab-expression-broken` | none |
| aPanelInATabNeedsAnIdAndAKindATabCanHold | 4 | `tab-panel-needs-id`, `tab-own-chart-valid`, `tab-surface-refused`, `tab-nested-tabs-refused` | none |
| idsAreUniqueWithinATabAndSizesAndPivotsBelongToTopLevelPanels | 3 | `tab-duplicate-id`, `tab-panel-span-refused`, `tab-panel-pivot-refused` | none |
| onlyTabsTakePanels | 1 | `panels-only-on-tabs` | none |
| theWorkbenchSchemaOffersPanelsOnEveryPanel, theEditorsOptionListForTabs... | 1 + 1 | none | `RachanaSchema` output (now covered by `spec/1/sutra.schema.json` being generated) |

### PivotOptionTest, SutraWiringTest

| Method | n | Cases | Not harvested (reason) |
|---|---|---|---|
| noOptionNoPivotAndFalseIsOff | 2 | `pivot-absent-valid`, `pivot-false-valid` | `pivot()` empty |
| trueOffersTheColumnsNamedByTheirPathsOrLabels | 7 | `pivot-true-valid` | `PivotSpec` accessors |
| aMappingListsFieldsAndTheArrangementItOpensWith | 14 | `pivot-mapping-ladder-valid` | `PivotSpec` accessors, `toMap` |
| aSpecMayNameHowThePivotScreenOpensIt | 4 | `pivot-mode-panes-valid`, `pivot-mode-default-valid`, `pivot-mode-unknown` | `mode()`, `arrangementMap()` |
| mistakesAreDrs2031WithTheirReasons | 14 | 13 `pivot-*` refusals | none (one case answers two lines) |
| onlyTablesAndLaddersTakeAPivot | 2 | `pivot-on-kv-refused`, `pivot-on-pivot-panel-refused` | none |
| aBrokenFieldExpressionIsCaughtAtLoad | 1 | `pivot-field-expression-broken` | none |
| anArrangementIsReadAgainstTheFieldsOnOffer, aKindsSpecReadsDocumentPaths | 6 + 6 | none | `PivotSpec` API, no Sutra |
| theGrammarReadsSourcesEventsWaitsAndChildren | 21 | `wiring-desk-valid` | 20 `Wiring` accessors, `SutraWiring.nodes/depth/reads` |
| aComputedSourceTakesTypedParametersAsExpressions | 5 | `source-params-with-where`, `source-params-not-mapping`, `source-params-bad-names` | 2 accessors; a parse-only Sutra reading an undeclared `ctl.conf` (the full compile refuses it, RCH-2036) |
| theOlderFormsStayOptions | 4 | `older-forms-valid` | options, `wiring().isNone()` |
| eventsAndSourceAreReadInExpressions | 8 | none | follow-up: `expr` cases with `given.events` and `src` |
| lintFindsUnknownPanelsEventsAndFields | 7 | 7 `wiring#*` refusals | none |
| lintFindsACycleAndADepthOverThree | 3 | `wiring-cycle`, `children-nested-four-deep`, `children-nested-three-deep-valid` | none |
| theParserRefusesWhatIsNotBuilt | 7 | `emits-on-kv-row-click`, `emits-on-tick`, `source-names-no-id-or-where`, `source-model-not-built`, `children-on-kv`, `child-duplicate-panel-id` | none |
| aUseCarriesItsPanelsWiring | 5 | none | `use:` fragments need `files` in the runner |

### parse/*, design/ops/*, the editors, WorldRegionsTest

| Method | n | Cases | Not harvested (reason) |
|---|---|---|---|
| PhoneReaderTest#aPanelsTierAndCompactOptionsAndTheScreensBlockAreRead | 6 | `phone-tiers-options-and-screen-block-valid` | phone accessors |
| PhoneReaderTest#aWordThatIsNotATierOrAnOptionThatDoesNotFitTheKindIsDrs2059 | 4 | `phone-word-not-a-tier`, `phone-option-does-not-fit-kind`, `phone-order-names-a-desk-panel`, `phone-order-names-no-panel` | none |
| PhoneReaderTest#aFragmentsDocPhoneIsPutOnItsPanelsAndAUseOverridesIt | 3 | none | fragment file |
| SutraLayoutEditorTest#theGrammarChecksSpanAndHeight | 6 | `span-and-height-in-range-valid`, `span-zero-is-rch-2030`, `span-13-is-rch-2030`, `span-not-a-number-is-rch-2030`, `height-25-is-rch-2030`, `height-not-whole-is-rch-2030`, `tabs-body-takes-its-panels-size` | none |
| SutraLayoutEditorTest (the other five methods) | 29 | none | `SutraLayoutEditor` is not an operation of `design/ops`; `SutraText.flowSet` internal |
| SutraBlocksTest#anUnknownBlockOrParameterIsDrs2048AndABlockMayNotUseAnother | 4 | `blocks#use-of-an-unknown-block` | 3 need block files |
| SutraBlocksTest (other methods), SutraFragmentsTest (8 methods), UiKitBlocksTest (3) | 9 + 49 + 17 | none | block, fragment and kit files; registry state |
| OpApplierTest#everyOperationOnEveryKindKeepsCommentsAndOrder | 19 | `set-option-title-keeps-comments`, `set-option-span`, `set-option-null-removes-the-option`, `move-to-right-area-after-a-panel`, `add-panel-after-another`, `remove-a-panel-keeps-other-comments` | the loop over the guide examples and all 22 kinds (the cases pin exact texts instead of comment counts) |
| OpApplierTest#theSutrasOwnKeysAreSetAndRemovedInPlace | 14 | `set-title-in-place`, `set-match-in-place`, `set-strip-added-before-panels`, `set-keys-added-after-panels`, `set-strip-empty-removes-the-key`, `set-keys-empty-removes-the-key` | `Sutra` getters; the batch is one case per op |
| OpApplierTest#setStripKeepsTheCommentsInsideTheStripBlock | 3 | `set-strip-keeps-comments-inside-the-block` | none |
| OpApplierTest#aListOrMappingForAOptionThatTakesOneValueIsRefused | 2 | `refused-area-given-a-mapping`, `refused-title-given-a-mapping`, `refused-option-given-a-list` | `applied()` |
| OpApplierTest#theTablesPivotMappingIsAccepted | 2 | `set-option-pivot-mapping-on-a-table` | none |
| OpApplierTest#aBadOperationIsALocatedProblemAndNeverCorruptsTheText | 9 | 11 `refused-*` | `applied()`, the problem's op index and name, the batch's final text |
| OpApplierTest#textReplacesTheWholeSutraOrIsRefused | 7 | `text-replaces-the-whole-sutra`, `refused-text-that-is-not-yaml` | the third step (a batch over a text that does not parse) |
| OpApplierTest#bindsPathsInTheRolesAKindTakes | 9 | `bind-*` (6) | `Bind.roles` API |
| OpApplierTest#operationsReadAndWriteJson | 11 | `refused-unknown-operation`, `refused-missing-panel-field`, `refused-unknown-field`, `refused-span-not-a-whole-number`, `refused-set-option-without-a-value` | 6: the JSON API (`toJson`, list shape) |
| BlockOpsTest (4 methods), SutraPivotEditorTest (5), WorldRegionsTest (3) | 39 + 14 + ~14 | none | block library; a separate editor class; the `WorldRegions` API (no format reaches it) |

## Findings

1. `edit/ops#refused-option-the-kind-does-not-accept` and `#refused-unknown-operation` use `messageStart`: the reference's
   "expected one of ..." comes from a `Set.of` and its order changes between JVM runs (a determinism defect for the A-steps).
2. A list-valued control reaches the evaluator as a document list (the host's `ControlValues`); the bridge now does the same, which
   `within`/`oneOf` need.
3. The fragment, block and kit tests (about 75 assertions) wait for `files` support in the runner (FORMAT 5.7 already defines
   `files`); the `sutra` kind (ViewModel) waits for a runner with the engine's binder (DRISHTI_MIGRATION_TODO 2.4).
