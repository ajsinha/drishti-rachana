/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ash.rachana.Rachana;
import com.ash.rachana.model.Sutra;
import com.ash.rachana.parse.Json;
import com.ash.rachana.table.LiveScreen;
import com.ash.rachana.view.Frame;
import com.ash.rachana.view.ViewJson;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** End to end on the JVM: a Sutra with data:, read through its plan from the reference server, then kept live by the change stream. */
class TableClientTest {

    @Test
    @SuppressWarnings("unchecked")
    void aScreenReadsItsTablesThroughThePlanAndFollowsTheirChanges() throws Exception {
        try (ReferenceServer s = new ReferenceServer(0, "/rdp/v1", null)) {
            s.host().put(Json.read("{\"name\":\"sales\",\"key\":[\"region\"],\"columns\":[{\"name\":\"region\",\"type\":\"string\"},"
                    + "{\"name\":\"revenue\",\"type\":\"decimal\"}],\"can\":[\"page\",\"filter\",\"changes\",\"replay\"]}"),
                    (List<Map<String, Object>>) Json.read("[{\"region\":\"North\",\"revenue\":\"100\"},{\"region\":\"South\",\"revenue\":\"50\"},"
                            + "{\"region\":\"East\",\"revenue\":\"7\"}]"), "e1");
            Rachana r = Rachana.with(com.ash.rachana.RachanaOptions.DEFAULTS).language("1.1");
            Sutra sutra = r.parser().parse("""
                    rachana: 1.1
                    sutra: live-sales
                    version: 1
                    controls:
                      - { id: min, kind: dropdown, options: ['10', '60'] }
                    data:
                      sales: { table: sales, key: region, live: true, where: "@.revenue >= ctl.min" }
                    panels:
                      - { id: total, kind: metric, label: Revenue, value: "sum($.sales, 'revenue')" }
                    """, "live.sutra.yaml", "test");
            LiveScreen screen = new LiveScreen(r, sutra, LocalDate.of(2026, 1, 15), null, Map.of("min", "10"));
            TableClient client = new TableClient(null, s.url());
            TableClient.Read read = client.load(screen).get("sales");
            assertEquals(2, read.rows().size());                            // East (7) filtered by the host: the where was pushed down
            assertEquals("150", text(screen.view()));
            s.host().publish(Map.of("table", "sales", "update", List.of(Map.of("region", "North", "revenue", "120"))));
            List<List<Frame>> got = new ArrayList<>();
            client.follow(screen, "sales", read, got::add, 1);
            assertEquals(1, got.size());
            assertEquals("170", text(screen.last()));
            assertEquals("panel", got.get(0).get(0).patches().get(0).op());
        }
    }

    private static String text(com.ash.rachana.view.ViewModel vm) {
        return String.valueOf(((Map<?, ?>) ((Map<?, ?>) ((Map<?, ?>) ((List<?>) ((Map<?, ?>) Json.read(ViewJson.write(vm))).get("panels")).get(0))
                .get("data")).get("value")).get("text"));
    }
}
