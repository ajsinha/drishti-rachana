/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ash.rachana.Rachana;
import com.ash.rachana.RachanaOptions;
import com.ash.rachana.SutraException;
import com.ash.rachana.SutraProblem;
import com.ash.rachana.SutraStore;
import com.ash.rachana.design.ops.OpApplier;
import com.ash.rachana.design.ops.Ops;
import com.ash.rachana.model.Match;
import com.ash.rachana.model.Sutra;
import com.ash.rachana.parse.FragmentResolver;
import com.ash.rachana.parse.PNode;
import com.ash.rachana.parse.SutraBlocks;
import com.ash.rachana.parse.SutraParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Split step A7: the grammar line between Core and a profile's additions. */
class GrammarTest {

    private static final String CORE = "rachana: 1\nsutra: plain\nversion: 1\npanels:\n  - { id: total, kind: metric, value: \"1 + 2\" }\n";
    private static final String ENTITY = "rachana: 1\nsutra: deal\nversion: 2\nmatch: { kind: trade, where: \"$.x > 1\", priority: 3 }\n"
            + "panels:\n  - { id: l, kind: links }\n";

    private static Rachana host() {
        return Rachana.with(RachanaOptions.DEFAULTS, Entities.addTo(Profile.named("host")).build());
    }

    private static List<String> codes(SutraParser p, String text) {
        try {
            p.parse(text, "t.sutra.yaml", "rates");
            return List.of();
        } catch (SutraException e) {
            return e.problems().stream().map(x -> x.code() + " " + x.message()).toList();
        }
    }

    @Test
    void coreAloneReadsAScreenWithoutMatchAndRefusesTheEntityAdditions() {
        SutraParser core = Rachana.core().parser();
        Sutra s = core.parse(CORE, "t.sutra.yaml", "rates");
        assertThat(s.match()).isNull();
        assertThat(s.domain()).isNull();
        assertThat(s.extensions()).isEmpty();                     // no profile key; the compile's own index is read, not listed
        assertThat(s.extension(com.ash.rachana.model.Sutra.PLACES)).isNotNull();
        assertThat(codes(core, ENTITY)).anyMatch(m -> m.startsWith("RCH-2021 unknown panel kind 'links'"));
        assertThat(codes(core, CORE.replace("value: \"1 + 2\" }", "value: \"1\", source: { kind: trade, id: \"1\" } }")))
                .anyMatch(m -> m.startsWith("RCH-2010 a source names the table"));
        assertThat(codes(core, CORE.replace("value: \"1 + 2\" }", "value: \"1\", form: { fields: [ { id: a } ], save: { target: owned } } }")))
                .anyMatch(m -> m.contains("'form.save'"));
    }

    @Test
    void theEntityAdditionsReadMatchAndDomain() {
        Sutra s = host().parser().parse(ENTITY, "t.sutra.yaml", "rates");
        assertThat(s.match()).isEqualTo(new Match("trade", "$.x > 1", 3));
        assertThat(s.domain()).isEqualTo("rates");
        assertThat(s.extensions()).isEmpty();                     // domain and match are the record's own components
        assertThat(s.extension("domain")).isEqualTo("rates");
        assertThat(codes(host().parser(), CORE)).singleElement().satisfies(m -> assertThat(m).startsWith("RCH-2010 missing 'match'"));
    }

    @Test
    void theSchemaFollowsTheGrammar() {
        Map<String, Object> core = Rachana.core().schema(List.of("date"));
        Map<String, Object> host = host().schema(List.of("date"));
        assertThat(((Map<?, ?>) core.get("properties")).keySet().stream().map(Object::toString).toList()).doesNotContain("match", "domain");
        assertThat(core.get("required")).isEqualTo(List.of("rachana", "sutra", "version"));
        assertThat(((Map<?, ?>) host.get("properties")).keySet().stream().map(Object::toString).toList()).containsSubsequence("version", "domain", "description", "notes", "doc", "match", "title");
        assertThat(host.get("required")).isEqualTo(List.of("rachana", "sutra", "version", "match"));
        assertThat(core.toString()).doesNotContain("provenance");
        assertThat(host.toString()).contains("provenance");
    }

    @Test
    void setMatchNeedsTheMatchKey() {
        var ops = Ops.parse((Object) List.of(Map.of("op", "setMatch", "match", Map.of("kind", "bond"))));
        assertThat(new OpApplier().apply(CORE, ops).problems()).singleElement().satisfies(p -> assertThat(p.code()).isEqualTo("RCH-5022"));
        String withMatch = CORE.replace("version: 1\n", "version: 1\nmatch: { kind: trade }\n");
        assertThat(new OpApplier(host().parser()).apply(withMatch, ops).problems()).isEmpty();
    }

    @Test
    void aProfileCannotTakeACoreKeyOrBringTwoResolvers() {
        SutraKey title = new SutraKey() {
            public String name() { return "title"; }
            public Object read(PNode node, Context context) { return null; }
            public Map<String, Object> schema() { return Map.of(); }
        };
        assertThatThrownBy(() -> Rachana.with(null, Profile.named("x").keys(title).build())).hasMessageContaining("Core reads it");
        FragmentResolver none = FragmentResolver.of(List.of());
        assertThatThrownBy(() -> Rachana.with(null, Profile.named("a").fragments(none).build(), Profile.named("b").fragments(none).build()))
                .hasMessageContaining("fragment resolver");
        assertThatThrownBy(() -> Profile.named("x").panelKinds(com.ash.rachana.model.PanelKind.TABLE)).hasMessageContaining("Core panel kind");
    }

    @Test
    void anInMemoryFragmentResolverServesUse() {
        SutraBlocks.Block b = SutraBlocks.read("block: tile\nversion: 1\npanels:\n  - { id: t, kind: metric, value: \"2\" }\n", "tile.v1.block.yaml");
        Rachana r = Rachana.with(null, Profile.named("x").fragments(FragmentResolver.of(List.of(b))).build());
        Sutra s = r.parser().parse(CORE.replace("panels:\n", "panels:\n  - { use: tile }\n"), "t.sutra.yaml", null);
        assertThat(s.panels()).extracting(p -> p.id()).containsExactly("t", "total");
        List<SutraProblem> problems = new ArrayList<>();
        SutraBlocks.expand(List.of(new PNode(Map.of("use", new PNode("nope", 1, 1)), 1, 1)), "f", problems, FragmentResolver.of(List.of(b)));
        assertThat(problems).singleElement().satisfies(p -> assertThat(p.message()).isEqualTo("no block nope (blocks: tile)"));
    }

    @Test
    void theStoreKeepsEveryVersionAndTellsItsListeners() {
        SutraStore store = new SutraStore();
        List<String> heard = new ArrayList<>();
        Runnable stop = store.listen(c -> heard.add(c.kind() + " " + c.sutra().id()));
        Sutra v1 = Rachana.core().parser().parse(CORE, "a", null);
        Sutra v2 = Rachana.core().parser().parse(CORE.replace("version: 1", "version: 2"), "b", null);
        store.put(v1);
        store.put(v2);
        assertThat(store.get("plain")).contains(v2);
        assertThat(store.get("plain@1")).contains(v1);
        assertThat(store.get("plain@x")).isEmpty();
        assertThat(store.versions("plain")).containsExactly(v1, v2);
        assertThat(store.remove("plain", 2)).isTrue();
        assertThat(store.latest()).containsExactly(v1);
        stop.run();
        store.remove("plain", 1);
        assertThat(heard).containsExactly("PUT plain@1", "PUT plain@2", "REMOVED plain@2");
        assertThat(store.latest()).isEmpty();
    }
}
