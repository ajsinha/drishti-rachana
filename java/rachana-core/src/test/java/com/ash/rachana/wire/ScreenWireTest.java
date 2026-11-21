/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.wire;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ash.rachana.RachanaException;
import com.ash.rachana.model.Form;
import com.ash.rachana.view.ViewModel;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link ScreenWire}'s paths on a screen bound as a host binds it ({@link ScreenHarness}): a form sent ({@code submit}: checks first, then
 * the effects with the values filled in; {@code cancel}), a control's {@code change}, a click on a row that fires an event (the payload,
 * the panels that read it, the clicked panel's children) and the panels downstream of a click.
 */
class ScreenWireTest {

    static final String TICKET = """
            rachana: 1.2
            sutra: ticket
            version: 1
            title: { id: "'Ticket'" }
            state:
              qty: { type: number, default: 0 }
              sent: { type: boolean, default: false }
            controls:
              - id: desk
                kind: dropdown
                options: [ FX, RATES ]
                default: FX
                on:
                  change:
                    - { set: { state: qty, to: "0" } }
                    - { host: desk.picked, with: { desk: event, before: ctl.desk } }
              - { id: book, kind: dropdown, options: [ B1, B2 ] }
            panels:
              - id: ticket
                kind: form
                form:
                  fields:
                    - { id: side, label: Side, type: enum, options: [ BUY, SELL ], required: true }
                    - { id: qty, label: Quantity, type: number, required: true, min: 1 }
                  rules:
                    - { rule: "qty <= 1000", message: "At most 1,000", fields: [ qty ] }
                on:
                  submit:
                    - { set: { state: qty, to: event.qty } }
                    - { toggle: sent }
                    - { host: order.place, with: { side: event.side, desk: ctl.desk } }
                  cancel: [ { clear: qty } ]
              - { id: shown, kind: metric, label: Qty, value: state.qty }
            """;

    private static Map<String, Object> values(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void aSubmitThatBreaksARuleIsRefusedAndRunsNothing() {
        ScreenHarness h = ScreenHarness.of(TICKET, Map.of());
        ScreenWire.Submitted r = h.wire().submit(h.screen(), "ticket", "submit", values("side", "BUY", "qty", 2000));
        assertThat(r.refused()).isTrue();
        assertThat(r.effects()).isEmpty();
        assertThat(r.problems()).extracting(Form.Issue::message).containsExactly("At most 1,000");
    }

    @Test
    void aSubmitThatPassesRunsItsEffectsWithTheSentValues() {
        ScreenHarness h = ScreenHarness.of(TICKET, Map.of()).ctl("desk", "RATES");
        ScreenWire.Submitted r = h.wire().submit(h.screen(), "ticket", "submit", values("side", "SELL", "qty", 5));
        assertThat(r.refused()).isFalse();
        assertThat(r.effects()).hasSize(3);
        assertThat(r.effects().get(0)).containsEntry("do", "set").containsEntry("state", "qty").containsEntry("value", 5);
        assertThat(r.effects().get(1)).containsEntry("do", "toggle").containsEntry("value", true);
        assertThat(r.effects().get(2)).containsEntry("do", "host").containsEntry("action", "order.place")
                .containsEntry("with", Map.of("desk", "RATES", "side", "SELL"));
    }

    @Test
    void aRequiredFieldLeftOutAndAFieldTheFormLacksAreProblems() {
        ScreenHarness h = ScreenHarness.of(TICKET, Map.of());
        ScreenWire.Submitted r = h.wire().submit(h.screen(), "ticket", "submit", values("side", "BUY", "colour", "red"));
        assertThat(r.refused()).isTrue();
        assertThat(r.effects()).isEmpty();
        assertThat(r.problems()).extracting(Form.Issue::message).contains("the form has no field 'colour'");
        assertThat(r.problems()).anySatisfy(i -> assertThat(i.field()).isEqualTo("qty"));
    }

    @Test
    void aCancelRunsWithoutChecks() {
        ScreenHarness h = ScreenHarness.of(TICKET, Map.of()).state("qty", "7");
        ScreenWire.Submitted r = h.wire().submit(h.screen(), "ticket", "cancel", values("qty", 99999));
        assertThat(r.problems()).isEmpty();
        assertThat(r.effects()).containsExactly(values("do", "clear", "state", "qty", "value", null));
    }

    @Test
    void onlyAFormIsSentAndOnlyWithSubmitOrCancel() {
        ScreenHarness h = ScreenHarness.of(TICKET, Map.of());
        assertThatThrownBy(() -> h.wire().submit(h.screen(), "shown", "submit", Map.of())).isInstanceOf(RachanaException.class)
                .hasMessageContaining("not a form");
        assertThatThrownBy(() -> h.wire().submit(h.screen(), "ticket", "change", Map.of())).isInstanceOf(RachanaException.class)
                .hasMessageContaining("submit or cancel");
        assertThatThrownBy(() -> h.wire().submit(h.screen(), "nowhere", "submit", Map.of())).isInstanceOf(RachanaException.class);
    }

    @Test
    void aControlChangeRunsItsEffectsWithTheNewValueAsTheEvent() {
        ScreenHarness h = ScreenHarness.of(TICKET, Map.of());
        List<Map<String, Object>> effects = h.wire().change(h.screen(), "desk", "RATES");
        assertThat(effects).hasSize(2);
        assertThat(effects.get(0)).containsEntry("do", "set").containsEntry("state", "qty");
        assertThat(String.valueOf(effects.get(0).get("value"))).isEqualTo("0");
        assertThat(effects.get(1)).containsEntry("do", "host").containsEntry("action", "desk.picked")
                .containsEntry("with", Map.of("before", "FX", "desk", "RATES"));
        assertThat(h.wire().change(h.screen(), "book", "B2")).isEmpty();
        assertThatThrownBy(() -> h.wire().change(h.screen(), "nothing", "X")).isInstanceOf(RachanaException.class)
                .hasMessageContaining("no control 'nothing'");
    }

    @Test
    void theViewCarriesTheControlsEffectsAndTheChangeTheSameWayFireDoes() {
        ScreenHarness h = ScreenHarness.of(TICKET, Map.of());
        ViewModel vm = h.view();
        assertThat(vm.contract()).isEqualTo("1.2");
        assertThat(vm.controls().get(0).on()).containsOnlyKeys("change");
        assertThat(vm.controls().get(1).on()).isNull();
        var fired = com.ash.rachana.control.Fire.change(vm, "desk", "RATES");
        assertThat(fired.refusals()).isEmpty();
        assertThat(fired.effects()).isEqualTo(h.wire().change(h.screen(), "desk", "RATES"));
    }

    static final String DESK = """
            rachana: 1.2
            sutra: desk
            version: 1
            title: { id: "'Desk'" }
            panels:
              - id: trades
                kind: table
                rows: $.TRADES
                columns: [ { label: Trade, bind: "@.TRADE_ID" }, { label: Notional, bind: "@.N" } ]
                emits:
                  - { name: open, on: row-click, payload: { trade: "@.TRADE_ID", n: { bind: "@.N", type: number } } }
                children:
                  - place: expand-row
                    panels:
                      - { id: leg, kind: metric, label: Leg of, value: "@parent.open.trade" }
              - { id: picked, kind: metric, label: Picked, value: "@trades.open.trade", waits: Pick a trade }
              - { id: plain, kind: metric, label: Count, value: "count($.TRADES)" }
            """;

    static final Map<String, Object> TRADES = Map.of("TRADES", List.of(Map.of("TRADE_ID", "T1", "N", 100), Map.of("TRADE_ID", "T2", "N", 250)));

    @Test
    void aRowClickFiresTheEventAndRebindsThePanelsThatReadIt() {
        ScreenHarness h = ScreenHarness.of(DESK, TRADES);
        ScreenWire.Result r = h.wire().event(h.screen(), "trades", "open", 1);
        assertThat(r.selection().panel()).isEqualTo("trades");
        assertThat(r.selection().event()).isEqualTo("open");
        assertThat(r.selection().seen().get("trade").unwrap()).isEqualTo("T2");
        assertThat(r.panels()).extracting(ViewModel.PanelView::id).contains("picked").doesNotContain("plain");
        ViewModel.PanelView picked = r.panels().stream().filter(p -> p.id().equals("picked")).findFirst().orElseThrow();
        assertThat(((com.ash.rachana.view.PanelData.Metric) picked.data()).value().text()).isEqualTo("T2");
        assertThat(r.children()).hasSize(1);
        assertThat(r.children().get(0).hidden()).isFalse();
        assertThat(r.children().get(0).panels()).extracting(ViewModel.PanelView::id).containsExactly("leg");
    }

    @Test
    void aClickOnARowThePanelDoesNotShowOrAnEventItDoesNotFireIsRefused() {
        ScreenHarness h = ScreenHarness.of(DESK, TRADES);
        assertThatThrownBy(() -> h.wire().event(h.screen(), "trades", "open", 5)).isInstanceOf(RachanaException.class)
                .hasMessageContaining("shows no row 5");
        assertThatThrownBy(() -> h.wire().event(h.screen(), "trades", "close", 0)).isInstanceOf(RachanaException.class)
                .hasMessageContaining("emits no event 'close'");
    }

    @Test
    void beforeAnyClickTheReaderWaitsAndTheChildrenAreHidden() {
        ScreenHarness h = ScreenHarness.of(DESK, TRADES);
        ScreenWire.Screen s = h.screen();
        assertThat(ScreenWire.downstream(s, "trades")).contains("picked").doesNotContain("plain");
        assertThat(h.wire().children(s, "trades")).singleElement().satisfies(c -> assertThat(c.hidden()).isTrue());
        ViewModel vm = h.view();
        ViewModel.PanelView picked = vm.panels().stream().filter(p -> p.id().equals("picked")).findFirst().orElseThrow();
        assertThat(picked.wire().waits()).isEqualTo("Pick a trade");
    }
}
