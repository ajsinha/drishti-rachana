/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A model's measure functions (G4 gate F25): the measure and every body are read by the one parse of Rachana-EL, at the one level, whole.
 * The inputs are the gate's probes: each once cut to its first operand, or refused although it compiles at 1.2.
 */
class MeasureFunctionsTest {

    private static final Map<String, MeasureFunctions.Def> NET = Map.of("net", new MeasureFunctions.Def(List.of("x"), "x - 1"));

    private static String expand(String measure) {
        return MeasureFunctions.expand(measure, NET);
    }

    @Test
    void aConditionalIsReadWhole() {
        assertEquals("NOTIONAL - 1 if A else 0", expand("net(NOTIONAL) if A else 0"));
        assertEquals("NOTIONAL if A else B - 1", expand("NOTIONAL if A else net(B)"));
    }

    @Test
    void textAfterAWholeExpressionIsRefusedAtItsPlace() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> expand("net(NOTIONAL) total rubbish )("));
        ElException located = assertInstanceOf(ElException.class, e.getCause());
        assertEquals(14, located.position());
        assertEquals("expression 'net(NOTIONAL) total rubbish )(': unexpected 'total' at 14", e.getMessage());
    }

    @Test
    void theOneLevelsSyntaxIsRead() {
        assertEquals("(NOTIONAL ?? 0) - 1", expand("net(NOTIONAL ?? 0)"));
        assertEquals("(NOTIONAL - 1) ** 2", expand("net(NOTIONAL) ** 2"));
        assertEquals("1000 - 1", expand("net(1_000)"));
        assertEquals("not A - 1 > 0", expand("not net(A) > 0"));
    }

    @Test
    void aBodyIsReadWholeToo() {
        Map<String, MeasureFunctions.Def> bad = Map.of("net", new MeasureFunctions.Def(List.of("x"), "x - 1 junk"));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> MeasureFunctions.expand("1 + net(A)", bad));
        assertEquals("measure function 'net': unexpected 'junk' at 6", e.getMessage());
        assertEquals(4, assertInstanceOf(ElException.class, e.getCause()).position());     // at the call whose function is at fault
    }

    @Test
    void everyPartOfTheTreeIsExpanded() {
        assertEquals("[A - 1, {k: B - 1}][0]", expand("[net(A), {k: net(B)}][0]"));
        assertEquals("sum(map(L, lambda y: y - 1))", expand("sum(map(L, lambda y: net(y)))"));
        Map<String, MeasureFunctions.Def> shadow = Map.of("f", new MeasureFunctions.Def(List.of("x"), "let(x=2, x) + x"));
        assertEquals("let(x=2, x) + A", MeasureFunctions.expand("f(A)", shadow));
    }

    @Test
    void aCallByNameOrOfTheWrongCountIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> expand("net(x=A)"));
        assertThrows(IllegalArgumentException.class, () -> expand("net(A, B)"));
    }
}
