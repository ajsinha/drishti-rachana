/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ash.rachana.JsonCodec;
import com.ash.rachana.format.Formats;
import java.util.Collections;
import org.junit.jupiter.api.Test;

/** GRAM-01/GRAM-03: pathological expressions are located compile errors (RCH-2101), never a StackOverflowError. */
class ElLimitsTest {

    static final ElCompiler EL = new ElCompiler();

    static String parens(int n) {
        return "(".repeat(n) + "$.mtm" + ")".repeat(n);
    }

    static String chain(int n) {
        return String.join(" + ", Collections.nCopies(n, "$.mtm"));
    }

    static Object eval(ElCompiler el, String src, String json) {
        return Values.simplify(el.compile(src).eval(EvalContext.of(new JsonCodec().read(json), Formats.defaults())));
    }

    @Test
    void deeplyNestedParenthesesAreALocatedCompileError() {
        assertThatThrownBy(() -> EL.compile(parens(3000))).isInstanceOf(ElException.class)
                .hasMessageStartingWith("RCH-2101").hasMessageContaining("nested deeper than 200");
        assertThat(((Number) eval(EL, parens(90), "{\"mtm\":3}")).longValue()).isEqualTo(3L);
    }

    @Test
    void longChainsThatWouldOverflowEvaluationAreRefusedAtCompileTime() {
        assertThatThrownBy(() -> EL.compile(chain(5000))).isInstanceOf(ElException.class).hasMessageStartingWith("RCH-2101");
        assertThatThrownBy(() -> EL.compile("-".repeat(100_000) + "1")).isInstanceOf(ElException.class).hasMessageStartingWith("RCH-2101");
        assertThatThrownBy(() -> EL.compile("$.a[".repeat(5000) + "0" + "]".repeat(5000))).isInstanceOf(ElException.class)
                .hasMessageStartingWith("RCH-2101");
        assertThatThrownBy(() -> EL.compile("coalesce(".repeat(5000) + "1" + ")".repeat(5000))).isInstanceOf(ElException.class)
                .hasMessageStartingWith("RCH-2101");
        assertThatThrownBy(() -> EL.compile("1 ? ".repeat(5000) + "1" + " : 2".repeat(5000))).isInstanceOf(ElException.class)
                .hasMessageStartingWith("RCH-2101");
        assertThatThrownBy(() -> EL.compile("$" + ".a".repeat(5000))).isInstanceOf(ElException.class).hasMessageStartingWith("RCH-2101");
        assertThat(((Number) eval(EL, chain(100), "{\"mtm\":1}")).longValue()).isEqualTo(100L);
    }

    @Test
    void theLimitsAreSettingsAndLengthIsBoundedToo() {
        ElCompiler strict = new ElCompiler(100, new ElLimits(10, 50));
        assertThatThrownBy(() -> strict.compile(parens(11))).hasMessageContaining("nested deeper than 10");
        assertThatThrownBy(() -> strict.compile("'" + "x".repeat(60) + "'")).isInstanceOf(ElException.class)
                .hasMessageStartingWith("RCH-2101").hasMessageContaining("longer than 50");
        assertThatThrownBy(() -> strict.template("a ${" + chain(20) + "} b")).isInstanceOf(ElException.class);
        assertThat(strict.compile(parens(3))).isNotNull();
        assertThatThrownBy(() -> new ElLimits(0, 10)).isInstanceOf(IllegalArgumentException.class);
    }
    /** Runs {@code work} on a thread with {@code stack} bytes of stack, as a starved host thread would; its answer or what it threw. */
    static Object onStack(long stack, java.util.function.Supplier<Object> work) throws InterruptedException {
        Object[] out = new Object[1];
        Thread t = new Thread(null, () -> {
            try {
                out[0] = work.get();
            } catch (Throwable e) {
                out[0] = e;
            }
        }, "starved", stack);
        t.start();
        t.join();
        return out[0];
    }

    /**
     * P15 (G4 binder pass): on a host thread with too little stack for the depth bound, the walk runs again on a thread that has it, so
     * the answer is the bound's own refusal, the same text and place as on any thread; never a StackOverflowError. The parser at both
     * levels, the checker and the printer; a tree within the bound prints and checks alike.
     */
    @Test
    void aStarvedHostThreadGetsTheSameRefusalNeverAStackOverflow() throws Exception {
        String deep = "coalesce(".repeat(300) + "1" + ")".repeat(300);
        String within = "coalesce(".repeat(60) + "1" + ")".repeat(60);
        for (String level : java.util.List.of("1.2")) {
            ElCompiler el = new ElCompiler(100, ElLimits.DEFAULTS, FunctionTable.core(), level);
            String expected = String.valueOf(onStack(0, () -> {
                try {
                    el.compile(deep);
                    return "compiled";
                } catch (ElException e) {
                    return e.getMessage();
                }
            }));
            assertThat(expected).startsWith("RCH-2101").contains("nested deeper than 200");
            for (long stack : new long[] {64 << 10, 128 << 10, 256 << 10}) {
                Object starved = onStack(stack, () -> {
                    try {
                        new ElCompiler(100, ElLimits.DEFAULTS, FunctionTable.core(), level).compile(deep);
                        return "compiled";
                    } catch (ElException e) {
                        return e.getMessage();
                    }
                });
                assertThat(starved).as(level + " on " + stack + " bytes of stack").isEqualTo(expected);
                Object checked = onStack(stack, () -> CheckedMode.check(deep, null, com.ash.rachana.el.check.Fields.NONE, level).problems().get(0).message());
                assertThat(checked).as("checked mode").isInstanceOf(String.class).asString().contains("nested deeper than 200");
                Expr tree = el.compile(within);
                Object printed = onStack(stack, () -> Printer.print(tree, level));
                assertThat(printed).as("printer").isEqualTo(Printer.print(tree, level));
            }
        }
    }
}
