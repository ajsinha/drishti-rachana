/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.wire;

import static org.assertj.core.api.Assertions.assertThat;

import com.ash.rachana.model.Panel;
import com.ash.rachana.model.PanelKind;
import com.ash.rachana.model.Wiring;
import com.ash.rachana.view.ViewModel;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The ViewModel's wire is the same bytes on every run: an event's keys and a child group's keys in declaration order (never Map.of's). */
class ScreenWireOrderTest {

    @Test
    void anEventsKeysComeInDeclarationOrder() {
        Wiring w = new Wiring(null, List.of(new Wiring.Emit("select", "row-click", Map.of())), null, List.of());
        Panel p = new Panel("t", PanelKind.TABLE, "T", null, null, null, false, List.of(), null, Map.of(), null, List.of(), w);
        ViewModel.Wire wire = ScreenWire.wire(p, null, null);
        assertThat(List.copyOf(wire.emits().get(0).keySet())).containsExactly("name", "on");
        assertThat(wire.emits().get(0)).containsEntry("name", "select").containsEntry("on", "row-click");
    }
}
