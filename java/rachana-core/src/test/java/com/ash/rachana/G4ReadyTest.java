/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ash.rachana.api.Node;
import com.ash.rachana.api.NodeFactory;
import com.ash.rachana.el.ElCompiler;
import com.ash.rachana.el.ElException;
import com.ash.rachana.el.EvalContext;
import com.ash.rachana.el.Expr;
import com.ash.rachana.el.FunctionProvider;
import com.ash.rachana.el.Printer;
import com.ash.rachana.el.Values;
import com.ash.rachana.format.Formats;
import com.ash.rachana.model.Sutra;
import com.ash.rachana.profile.Entities;
import com.ash.rachana.profile.Profile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What a host needs before it moves onto Core (G4 rehearsal, pass 2): aggregates over a host's nodes give the right figure and refuse
 * what they cannot read; a host's node factory; a live format table; the printer at a level; a Sutra's JSON shape; an instance pinned at
 * a level in one call; a profile that redefines a Core function refused.
 */
class G4ReadyTest {

    // ---- a host's node type, as a real host's is shaped (sealed records, covariant navigation, its own factory) ----

    sealed interface Host extends Node permits Obj, Arr, Val, Missing {
        NodeFactory NODES = new NodeFactory() {
            public Node of(Object value) {
                return host(value);
            }

            public Node list(List<? extends Node> elements) {
                return new Arr(elements.stream().map(G4ReadyTest::host).toList());
            }
        };

        default Host get(String field) {
            return Missing.I;
        }

        default Host get(int index) {
            return Missing.I;
        }
    }

    record Obj(Map<String, Host> fields, boolean factory) implements Host {
        public Kind kind() { return Kind.OBJECT; }
        public Host get(String f) { Host n = fields.get(f); return n == null ? Missing.I : n; }
        public int size() { return fields.size(); }
        public NodeFactory nodes() { return factory ? NODES : NodeFactory.core(); }
        public Object unwrap() { Map<String, Object> m = new LinkedHashMap<>(); fields.forEach((k, v) -> m.put(k, v.unwrap())); return m; }
    }

    record Arr(List<Host> elements) implements Host {
        public Kind kind() { return Kind.ARRAY; }
        public Host get(int i) { int k = i < 0 ? elements.size() + i : i; return k >= 0 && k < elements.size() ? elements.get(k) : Missing.I; }
        public int size() { return elements.size(); }
        public NodeFactory nodes() { return NODES; }
        public Object unwrap() { return elements.stream().map(Host::unwrap).toList(); }
    }

    /** A scalar that gives its value only through {@code asDouble}/{@code asText} (Node's default {@code value()} is null). */
    record Val(Object v) implements Host {
        // a record is final: the test's "number that gives no number" is Val(NaN), whose asDouble is NaN and whose value() is null
        public Kind kind() { return v == null ? Kind.NULL : v instanceof Number ? Kind.NUMBER : v instanceof Boolean ? Kind.BOOLEAN : Kind.STRING; }
        public String asText() { return String.valueOf(v); }
        public double asDouble() { return v instanceof Number n ? n.doubleValue() : Double.NaN; }
        public boolean asBoolean() { return Boolean.TRUE.equals(v); }
        public Object unwrap() { return v; }
    }

    static final class Missing implements Host {
        static final Missing I = new Missing();
        public Kind kind() { return Kind.MISSING; }
        public Object unwrap() { return null; }
    }

    static Host host(Object v) {
        return host(v, true);
    }

    static Host host(Object v, boolean factory) {
        if (v instanceof Host h) return h;
        if (v instanceof Node n && n.kind() == Node.Kind.ARRAY) return new Arr(n.elements().stream().map(x -> host(x, factory)).toList());
        if (v instanceof Node n && n.kind() == Node.Kind.OBJECT) {
            Map<String, Host> f = new LinkedHashMap<>();
            n.fields().forEach((k, x) -> f.put(k, host(x, factory)));
            return new Obj(f, factory);
        }
        if (v instanceof Node n) return new Val(Values.simplify(n));
        if (v instanceof Map<?, ?> m) {
            Map<String, Host> f = new LinkedHashMap<>();
            m.forEach((k, x) -> f.put(String.valueOf(k), host(x, factory)));
            return new Obj(f, factory);
        }
        if (v instanceof List<?> l) return new Arr(l.stream().map(x -> host(x, factory)).toList());
        return new Val(v);
    }

    /** The collateral balance of the rehearsal's P4 (counterparty-risk, COLL-MERIDIAN-FRA). */
    static Map<String, Object> collateral() {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("HELD", 78104);
        d.put("POSITIONS", List.of(Map.of("DIRECTION", "Held", "VALUE", 25815), Map.of("DIRECTION", "Held", "VALUE", 26079),
                Map.of("DIRECTION", "Held", "VALUE", 26210), Map.of("DIRECTION", "Posted", "VALUE", 1)));
        d.put("TEXT", "abc");
        d.put("REC", Map.of("a", 1));
        return d;
    }

    static final ElCompiler EL = Rachana.core().compiler();

    static Object eval(String e, Node doc) {
        return eval(e, EvalContext.of(doc, Formats.defaults()));
    }

    static Object eval(String e, EvalContext c) {
        return Values.simplify(EL.compile(e).eval(c));
    }

    // ---- 1: sums over a host's nodes ----

    @Test
    void aSumOverAFilteredListOfAHostsNodesIsTheFigure() {
        String held = "sum($.POSITIONS[?@.DIRECTION == 'Held'], 'VALUE')";
        for (Node doc : List.of(Node.of(collateral()), host(collateral(), true), host(collateral(), false))) {
            assertThat(eval(held, doc)).as(doc.getClass().getSimpleName()).isEqualTo(78104L);
            assertThat(eval("sum($.POSITIONS[*].VALUE)", doc)).isEqualTo(78105L);
            assertThat(eval("sum($.POSITIONS[?@.DIRECTION == 'none'], 'VALUE')", doc)).as("a sum of nothing").isEqualTo(0L);
            assertThat(eval("sum($.NOTHING)", doc)).as("a sum of an absent list").isEqualTo(0L);
            assertThat(eval("avg($.NOTHING)", doc)).as("any other aggregate of nothing").isNull();
        }
    }

    @Test
    void anAggregateOfSomethingThatIsNotAListIsAProblemNeverASilentZero() {
        Node doc = host(collateral());
        ElCompiler v11 = Rachana.at("1.1", null).compiler();
        for (String e : List.of("sum($.TEXT)", "sum($.HELD)", "sum($.REC)", "count($.REC)", "avg(true)", "median($.TEXT)")) {
            assertThatThrownBy(() -> v11.compile(e).eval(EvalContext.of(doc, Formats.defaults()))).as(e).isInstanceOf(ElException.class)
                    .satisfies(x -> assertThat(((ElException) x).code()).isEqualTo(RachanaCode.EL_EVAL)).hasMessageContaining("is not a list");
            assertThat(Values.text(eval(e, doc))).as("level 1.0 as recorded: " + e).isIn("0", "");
        }
        assertThatThrownBy(() -> v11.compile("span($.TEXT)").eval(EvalContext.of(doc, Formats.defaults()))).hasMessageContaining("is not a list");
        assertThat(Values.text(v11.compile("sum($.POSITIONS, 'VALUE')").eval(EvalContext.of(doc, Formats.defaults())))).isEqualTo("78105");
        assertThat(Values.text(v11.compile("min(3, 1)").eval(EvalContext.of(doc, Formats.defaults())))).as("min stays least").isEqualTo("1");
        for (ElCompiler el : List.of(EL, v11)) {
            EvalContext withJavaList = EvalContext.of(doc, Formats.defaults()).withControls(Map.of("picked", List.of(1, 2)));
            assertThatThrownBy(() -> el.compile("sum(ctl.picked)").eval(withJavaList)).as("a host value Core cannot read, at every level")
                    .hasMessageContaining("a value of type java.util");
        }
        assertThat(eval("sum($.MASKED)", Node.of(Map.of("MASKED", Node.masked())))).isEqualTo(Node.MASK);
    }

    @Test
    void aHostsNumberIsReadThroughItsAccessorAndOneThatGivesNoNumberIsAProblem() {
        Node odd = new Val(Double.NaN);                            // a host's number that gives no number: no value(), asDouble NaN
        assertThat(eval("$.POSITIONS[0].VALUE + 1", host(collateral()))).isEqualTo(25816L);
        Node doc = new Obj(Map.of("N", (Host) odd), true);
        assertThatThrownBy(() -> eval("$.N + 1", doc)).isInstanceOf(ElException.class).hasMessageContaining("gives no number");
    }

    // ---- 2: the host's node factory ----

    @Test
    void theListsAndRecordsAnExpressionBuildsComeOutInTheHostsType() {
        Node doc = host(collateral(), true);
        for (String e : List.of("$.POSITIONS[?@.DIRECTION == 'Held']", "$.POSITIONS[*].VALUE", "flatten([[1], [2]])", "[1, 2]",
                "map($.POSITIONS, @.VALUE)", "group($.POSITIONS, @.DIRECTION)")) {
            assertThat(EL.compile(e).eval(EvalContext.of(doc, Formats.defaults()))).as(e).isInstanceOf(Arr.class);
        }
        Object filtered = EL.compile("$.POSITIONS[?@.DIRECTION == 'Held']").eval(EvalContext.of(doc, Formats.defaults()));
        assertThat(((Arr) filtered).elements().get(0)).as("the host's own elements are kept, not copied")
                .isSameAs(((Host) doc).get("POSITIONS").get(0));
        assertThat(EL.compile("[1, 2]").eval(EvalContext.of(Node.of(collateral()), Formats.defaults()))).as("Rachana's own documents")
                .isNotInstanceOf(Host.class);
        assertThat(EL.compile("[1, 2]").eval(EvalContext.of(host(collateral(), false), Formats.defaults()))).as("a host without a factory")
                .isNotInstanceOf(Host.class);
    }

    // ---- 3: a live format table ----

    @Test
    void formatsAPackAddsAtRunTimeReachEveryReader(@TempDir Path dir) throws Exception {
        Path pack = dir.resolve("pack-formats.yaml");
        Files.writeString(pack, "formats:\n  one: { type: number, decimals: 1 }\n");
        Rachana r = Rachana.at("1.1", RachanaOptions.DEFAULTS.withFormats(null, List.of(pack.toString())));
        Formats held = r.formats();
        EvalContext ctx = EvalContext.of(Node.of(Map.of("x", 2.25)), held);
        assertThat(held.isLive()).isTrue();
        assertThat(held.has("two")).isFalse();
        Path second = dir.resolve("second-formats.yaml");
        Files.writeString(second, "formats:\n  two: { type: number, decimals: 2 }\n");
        r.formats().reload(List.of(pack.toString(), second.toString()));
        assertThat(held.has("two")).as("the reader's own instance").isTrue();
        assertThat(ctx.formats().format("two", 2.25)).isEqualTo("2.25");
        assertThat(r.language("1.0").formats().has("two")).as("a level view of the same table").isTrue();
        Files.writeString(pack, "formats:\n  one: { type: number, decimals: 3 }\n");
        r.formats().reload();
        assertThat(held.format("one", 2.25)).as("the same files read again").isEqualTo("2.250");
        assertThatThrownBy(() -> held.reload(List.of(dir.resolve("absent.yaml").toString())));
        assertThat(held.has("two")).as("a failed reload changes nothing").isTrue();
        assertThatThrownBy(() -> Formats.defaults().reload()).isInstanceOf(IllegalStateException.class);
    }

    // ---- 4: the printer at a level ----

    @Test
    void thePrinterWritesTheFormsOfTheSutrasLevel() {
        ElCompiler v10 = Rachana.core().compiler();
        ElCompiler v11 = Rachana.at("1.1", null).compiler();
        Map<String, String[]> cases = new LinkedHashMap<>();
        cases.put("!(a == b) || c && !d", new String[] {"!(a == b) || c && !d", "not a == b or c and not d"});
        cases.put("a ? b : c ? d : e", new String[] {"a ? b : c ? d : e", "b if a else d if c else e"});
        cases.put("(a ? b : c) ? d : e", new String[] {"(a ? b : c) ? d : e", "d if (b if a else c) else e"});
        cases.put("!a == b", new String[] {"!a == b", "(not a) == b"});
        cases.put("a && (b || c)", new String[] {"a && (b || c)", "a and (b or c)"});
        cases.put("!(a && b)", new String[] {"!(a && b)", "not (a and b)"});
        cases.forEach((text, want) -> {
            assertThat(Printer.print(v10.compile(text))).as(text).isEqualTo(want[0]).isEqualTo(Printer.print(v10.compile(text), "1.0"));
            String at11 = Printer.print(v11.compile(text), "1.1");
            assertThat(at11).as(text).isEqualTo(want[1]).isEqualTo(Printer.print(v11.compile(text), "1.2"));
            assertThat(v11.compile(at11)).as("1.1 reads back the same tree: " + at11).isEqualTo(v11.compile(text));
            assertThat(v10.compile(want[0])).isEqualTo(v10.compile(text));
        });
        Expr measure = new Expr.Call("sum", null, List.of(v10.compile("PNL")), Map.of("where", v10.compile("DESK == 'RATES' && PNL > 0")));
        assertThat(Printer.print(measure, "1.0")).isEqualTo("sum(PNL, where = DESK == 'RATES' && PNL > 0)");
        assertThat(Printer.print(measure, "1.1")).isEqualTo("sum(PNL, where=DESK == 'RATES' and PNL > 0)");
        assertThatThrownBy(() -> Printer.print(v10.compile("1"), "9.9")).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- 5: the Sutra's JSON shape ----

    static final String ENTITY = "rachana: 1\nsutra: deal\nversion: 2\nmatch: { kind: trade, where: \"$.x > 1\", priority: 3 }\n"
            + "panels:\n  - { id: l, kind: links }\n";

    @Test
    void aSutrasComponentsAreItsJsonWithTheProfileKeysInTheirPlaces() {
        assertThat(Arrays.stream(Sutra.class.getRecordComponents()).map(c -> c.getName()).toList()).containsExactly("name", "version",
                "domain", "match", "title", "strip", "panels", "keys", "location", "description", "controls", "layout", "phone", "extensions");
        Rachana r = Rachana.with(null, Entities.addTo(Profile.named("host")).build());
        Sutra s = r.parser().parse(ENTITY, "t.sutra.yaml", "rates");
        assertThat(s.domain()).isEqualTo("rates");
        assertThat(s.match()).isNotNull();
        assertThat(s.extensions()).as("no compile internals listed: a writer of the components meets only data").isEmpty();
        assertThat(s.extension(Sutra.PLACES)).isNotNull();
        Sutra back = new Sutra(s.name(), s.version(), s.domain(), s.match(), s.title(), s.strip(), s.panels(), s.keys(), s.location(),
                s.description(), s.controls(), s.layout(), s.phone(), Map.copyOf(s.extensions()));
        assertThat(back).as("read back through the canonical constructor").isEqualTo(s);
        assertThat(back.domain()).isEqualTo("rates");
    }

    // ---- 6: an instance pinned at a level ----

    @Test
    void anInstanceIsPinnedAtALevelInOneCall() {
        assertThat(Rachana.at("1.0", null).language()).isEqualTo("1.0");
        assertThat(Rachana.at("1.2", RachanaOptions.DEFAULTS).language()).isEqualTo("1.2");
        assertThat(Rachana.at("1.1", null).compiler().level()).isEqualTo("1.1");
        assertThat(Rachana.at("1.1", null).parser().grammar().level()).isEqualTo("1.1");
        assertThatThrownBy(() -> Rachana.at("2.0", null)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- 7: one way per job ----

    @Test
    void aProfileThatRedefinesACoreFunctionIsRefused() {
        FunctionProvider engine = new FunctionProvider() {
            public String source() { return "the pivot engine"; }
            public List<Provided> functions() {
                return List.of(new Provided("sum", 1, 2, "", "mergeable", "a copy", "", (a, c) -> 0L));
            }
        };
        assertThatThrownBy(() -> Rachana.with(null, Profile.named("host").functions(engine).build())).isInstanceOf(IllegalStateException.class)
                .hasMessage("Rachana-EL function sum is Core's: a profile cannot redefine it (the pivot engine provides it too; drop it from that "
                        + "provider, Core's sum is the one)");
        FunctionProvider grouping = new FunctionProvider() {
            public String source() { return "the pivot engine"; }
            public List<Provided> functions() { return List.of(); }
            public Grouping grouping() { return (keys, columns, aggregate, options, max) -> List.of(); }
        };
        assertThatThrownBy(() -> Rachana.with(null, Profile.named("host").functions(grouping).build())).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("group is Core's");
    }
}
