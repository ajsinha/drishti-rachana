/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.bind;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Table columns bound to an identifier field link to the entity without {@code link: true}. */
class IdColumnTest {

    @Test
    void identifierFieldsLinkAndOtherFieldsDoNot() {
        assertThat(Binder.namesAnId("@.tradeId")).isTrue();
        assertThat(Binder.namesAnId("$.counterpartyId")).isTrue();
        assertThat(Binder.namesAnId("@.legs[0].bookRef")).isTrue();
        assertThat(Binder.namesAnId("@.account_id")).isTrue();
        assertThat(Binder.namesAnId("@.product")).isFalse();
        assertThat(Binder.namesAnId("@.id")).isFalse();                       // too vague: an id of what?
        assertThat(Binder.namesAnId("@.TRADE_ID")).isTrue();                  // UPPER_SNAKE, as the shipped packs write them
        assertThat(Binder.namesAnId("$.LEGS[0].BOOK_REF")).isTrue();
        assertThat(Binder.namesAnId("@.ID")).isFalse();
        assertThat(Binder.namesAnId("fmt(@.tradeId, 'text')")).isFalse();     // an expression decides for itself
        assertThat(Binder.namesAnId(null)).isFalse();
    }
}
