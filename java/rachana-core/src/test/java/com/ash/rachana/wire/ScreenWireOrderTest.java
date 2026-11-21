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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
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

    /**
     * Guard: the ViewModel's writers (packages wire, view, bind) build no object of two or more keys with Map.of, whose key order is
     * drawn afresh in every JVM run: an object written from one would make the bytes differ from run to run (the flaky corpus binds).
     */
    @Test
    void theViewModelWritersBuildNoMultiKeyObjectWithMapOf() throws Exception {
        Pattern twoKeys = Pattern.compile("Map\\.of\\(\\s*\"[^\"]*\"\\s*,[^;]*?,\\s*\"[^\"]*\"\\s*,");
        List<String> found = new ArrayList<>();
        for (String pkg : List.of("wire", "view", "bind")) {
            try (Stream<Path> s = Files.walk(Path.of("src/main/java/com/ash/rachana", pkg))) {
                for (Path f : s.filter(x -> x.toString().endsWith(".java")).toList()) {
                    String text = Files.readString(f);
                    var m = twoKeys.matcher(text);
                    while (m.find()) {
                        int line = (int) text.substring(0, m.start()).chars().filter(c -> c == '\n').count() + 1;
                        String l = text.lines().skip(line - 1).findFirst().orElse("");
                        if (!l.contains("static final")) {
                            found.add(f.getFileName() + ":" + line);
                        }
                    }
                }
            }
        }
        assertThat(found).as("multi-key Map.of in a ViewModel writer (use a LinkedHashMap in declaration order)").isEmpty();
    }
}
