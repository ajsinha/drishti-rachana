/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** The compiler's cache (Core's own LRU): bounded, shared, and a refused expression is never held. */
class ElCompilerCacheTest {

    @Test
    void boundedSharedAndRefusalsNotHeld() {
        ElCompiler c = new ElCompiler(3);
        Expr a = c.compile("1 + 1");
        assertThat(c.compile("1 + 1")).isSameAs(a);
        for (int i = 0; i < 10; i++) {
            c.compile("2 * " + i);
        }
        assertThat(c.size()).isEqualTo(3);
        assertThatThrownBy(() -> c.compile("1 +")).isInstanceOf(ElException.class);
        assertThat(c.size()).isEqualTo(3);
    }
}
