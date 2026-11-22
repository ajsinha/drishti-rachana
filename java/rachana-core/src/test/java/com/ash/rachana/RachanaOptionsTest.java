/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ash.rachana.el.ElException;
import com.ash.rachana.el.ElLimits;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Every setting in one value, read from nothing else. */
class RachanaOptionsTest {

    @Test
    void defaultsAndWithers() {
        RachanaOptions o = RachanaOptions.DEFAULTS;
        assertThat(o.expressionCacheSize()).isEqualTo(10_000);
        assertThat(o.expressionLimits()).isEqualTo(ElLimits.DEFAULTS);
        assertThat(o.formatFiles()).isEmpty();
        RachanaOptions small = o.withExpressionLimits(new ElLimits(3, 20));
        assertThatThrownBy(() -> small.compiler().compile("((((1))))")).isInstanceOf(ElException.class);
        assertThat(o.compiler().compile("((((1))))")).isNotNull();
        assertThatThrownBy(() -> o.withExpressionCacheSize(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theFormatTableTakesTheProfileFiles() {
        RachanaOptions o = RachanaOptions.DEFAULTS.withFormats(null, List.of(TestFormats.file()));
        assertThat(o.formats().format("pct4", 0.123456)).isEqualTo(RachanaOptions.DEFAULTS.withFormats(null, List.of(TestFormats.file()))
                .formats().format("pct4", 0.123456));
    }
}
