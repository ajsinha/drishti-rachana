/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ash.rachana.api.Node;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** A Sutra's declared names (X41, X42, S2): inlined at the call, a let read once per bind, cached apart per Sutra, absent outside. */
class DeclarationsTest {

    private static final ElCompiler EL = new ElCompiler(64, ElLimits.DEFAULTS, FunctionTable.core(), "1.2");

    private static Declarations decls() {
        Map<String, Declarations.Function> fns = new LinkedHashMap<>();
        fns.put("twice", new Declarations.Function("twice", Map.of("x", "number"), "x * 2", "number", null));
        fns.put("lib.inc", new Declarations.Function("lib.inc", Map.of("x", "number"), "x + 1", null, null));
        Map<String, String> lets = new LinkedHashMap<>();
        lets.put("n", "size($.rows)");
        lets.put("m", "twice(n)");
        return new Declarations(fns, lets);
    }

    @Test
    void namesAreReadOnlyWithTheirDeclarationsInForce() {
        Node doc = new com.ash.rachana.JsonCodec().read("{\"rows\": [1, 2, 3]}");
        Object v = Declarations.within(decls(), () -> EL.compile("lib.inc(m) + twice(1)").eval(EvalContext.of(doc, null)));
        assertThat(Values.text(v)).isEqualTo("9");
        assertThatThrownBy(() -> EL.compile("twice(1)")).hasMessageContaining("unknown function 'twice'");
    }

    @Test
    void aLetIsEvaluatedOncePerBind() {
        Node doc = new com.ash.rachana.JsonCodec().read("{\"rows\": [1, 2, 3]}");
        Declarations.within(decls(), () -> {
            EvalContext c = EvalContext.of(doc, null);
            EL.compile("m").eval(c);
            assertThat(Declarations.current().memo).containsKeys("m", "n");
            Object[] held = Declarations.current().memo.get("n");
            EL.compile("n + 1").eval(c.withRow(new com.ash.rachana.JsonCodec().read("{\"rows\": []}"), 0));     // a row changes nothing: a let reads no row
            assertThat(Declarations.current().memo.get("n")).isSameAs(held);
            return null;
        });
    }
}
