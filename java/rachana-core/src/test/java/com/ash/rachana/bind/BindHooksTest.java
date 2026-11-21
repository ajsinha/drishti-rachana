/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.bind;

import static org.assertj.core.api.Assertions.assertThat;

import com.ash.rachana.Rachana;
import com.ash.rachana.TestGrammar;
import com.ash.rachana.api.Node;
import com.ash.rachana.el.EvalContext;
import com.ash.rachana.model.Panel;
import com.ash.rachana.model.PanelKind;
import com.ash.rachana.model.Sutra;
import com.ash.rachana.view.MaskCount;
import com.ash.rachana.view.PanelData;
import com.ash.rachana.view.ViewJson;
import com.ash.rachana.view.ViewModel.PanelView;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The binder behind its hooks (split step A13): Core alone binds the Core kinds; a host's hooks add links, codes and profile panels. */
class BindHooksTest {

    private static final Rachana R = Rachana.core();

    private static final Sutra SUTRA = TestGrammar.parser().parse("""
            rachana: 1
            sutra: trade
            version: 1
            match: { kind: trade }
            panels:
              - { id: total, kind: metric, label: Notional, value: "$.notional", fmt: amount0 }
              - { id: legs, kind: table, rows: "$.legs", columns: [ { label: Leg, bind: "@.leg" }, { label: Book, bind: "@.BOOK_ID" } ] }
              - { id: who, kind: kv, columns: [ { label: Counterparty, bind: "$.counterpartyId" } ] }
              - { id: rel, kind: links }
            """, "trade.sutra.yaml", "x");

    private static final Node DATA = Node.of(Map.of("notional", 1250000, "counterpartyId", "CP-1",
            "legs", List.of(Map.of("leg", "pay", "BOOK_ID", "B-7"), Map.of("leg", "receive", "BOOK_ID", Node.MASK))));

    private static BindContext context() {
        Entity doc = new Entity(EntityRef.of("trade", "T-1"), DATA);
        return BindContext.of(doc, Layout.written("trade v1"), EvalContext.of(DATA, R.formats()));
    }

    private static Panel panel(String id) {
        return SUTRA.panels().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void coreAloneBindsTheCoreKindsAndNamesNoKinds() {
        Binder b = new Binder(R.compiler(), R.formats());
        PanelView total = b.bind(panel("total"), context());
        assertThat(total.error()).isNull();
        assertThat(((PanelData.Metric) total.data()).value().text()).isEqualTo("1,250,000");
        PanelView who = b.bind(panel("who"), context());
        assertThat(((PanelData.Fields) who.data()).fields().get(0).link()).isNull();     // no kinds: an id stays text
        PanelView rel = b.bind(panel("rel"), context());
        assertThat(rel.data()).isNull();
        assertThat(rel.error()).contains("needs the profile that adds it");
    }

    @Test
    void aHostsHooksLinkIdsAndBindItsProfilePanels() {
        LinkResolver links = new LinkResolver() {
            @Override
            public Optional<String> kindOf(String id) {
                return id.startsWith("CP-") ? Optional.of("counterparty") : id.startsWith("B-") ? Optional.of("book") : Optional.empty();
            }

            @Override
            public String codeFor(String kind) {
                return kind.equals("counterparty") ? "CPTY" : null;
            }
        };
        BindHooks hooks = BindHooks.core().withLinks(links).withPanel(PanelKind.LINKS,
                (p, c, binder) -> new PanelData.Links(List.of(new PanelData.LinkItem("Counterparty", "CP-1", binder.linkView("counterparty", "CP-1"), "", "resolved"))));
        Binder b = new Binder(R.compiler(), R.formats(), hooks, PivotEngine.NONE);
        PanelView who = b.bind(panel("who"), context());
        var link = ((PanelData.Fields) who.data()).fields().get(0).link();
        assertThat(link.kind()).isEqualTo("counterparty");
        assertThat(link.mnemonic()).isEqualTo("CPTY");
        PanelView rel = b.bind(panel("rel"), context());
        assertThat(rel.error()).isNull();
        assertThat(((PanelData.Links) rel.data()).links()).hasSize(1);
        PanelView legs = b.bind(panel("legs"), context());
        assertThat(ViewJson.write(legs.data())).contains("\"book\"");
    }

    @Test
    void theCanonicalWriterLeavesOutWhatTheRulesSayAndMasksAreCounted() {
        Binder b = new Binder(R.compiler(), R.formats());
        PanelView legs = b.bind(panel("legs"), context());
        String json = ViewJson.write(legs);
        assertThat(json).startsWith("{\"id\":\"legs\",\"kind\":\"table\"");
        assertThat(json).doesNotContain("\"denied\"");                       // null and NON_NULL: left out
        assertThat(MaskCount.of(null, List.of(), List.of(legs)).count()).isEqualTo(1);
    }
}
