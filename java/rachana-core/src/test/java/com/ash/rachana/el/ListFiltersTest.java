/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import static com.ash.rachana.el.Fx.eval;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The list filters (contains / any of / all of): {@code has}, {@code any_of}, {@code all_of} over a list attribute, whether it comes as
 * an array (a document) or as its JSON text (a promoted {@code array<string>} column read back); a single value is a list of one; no
 * value chosen holds everything; a missing list holds nothing.
 */
class ListFiltersTest {

    @Test
    void containsAnyOfAndAllOfOverAnArray() {
        assertThat(eval("has(['fx', 'rates'], 'fx')")).isEqualTo(true);
        assertThat(eval("has(['fx', 'rates'], 'credit')")).isEqualTo(false);
        assertThat(eval("any_of(['fx', 'rates'], ['credit', 'rates'])")).isEqualTo(true);
        assertThat(eval("any_of(['fx', 'rates'], ['credit', 'equity'])")).isEqualTo(false);
        assertThat(eval("all_of(['fx', 'rates', 'hedge'], ['fx', 'hedge'])")).isEqualTo(true);
        assertThat(eval("all_of(['fx', 'rates'], ['fx', 'hedge'])")).isEqualTo(false);
        assertThat(eval("has([2025, 2026], 2026)")).isEqualTo(true);            // numbers numerically
        assertThat(eval("has([2025, 2026], '2026')")).isEqualTo(true);
    }

    @Test
    void aListsJsonTextReadsAsItsListAndASingleValueAsAListOfOne() {
        assertThat(eval("has('[\"fx\",\"rates\"]', 'rates')")).isEqualTo(true);
        assertThat(eval("all_of('[\"fx\",\"rates\"]', ['rates', 'fx'])")).isEqualTo(true);
        assertThat(eval("has('fx', 'fx')")).isEqualTo(true);
        assertThat(eval("has('fx, rates', 'fx')")).isEqualTo(false);            // text is not split: a list is a list
    }

    @Test
    void nothingChosenHoldsEverythingAndAMissingListHoldsNothing() {
        assertThat(eval("any_of(['fx'], [])")).isEqualTo(true);
        assertThat(eval("all_of(['fx'], [])")).isEqualTo(true);
        assertThat(eval("has($.NOPE, 'fx')")).isEqualTo(false);
        assertThat(eval("any_of($.NOPE, ['fx'])")).isEqualTo(false);
        assertThat(eval("all_of([], ['fx'])")).isEqualTo(false);
    }
}
