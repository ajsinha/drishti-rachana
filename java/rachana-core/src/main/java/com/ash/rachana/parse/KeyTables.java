/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.parse;

import com.ash.rachana.model.Form;
import com.ash.rachana.model.PanelOptions;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The keys the parsers read, by the place they are read: the parsers' own sets, one view for the language files generated from them (the
 * top-level keys are {@code profile.Grammar.CORE_KEYS} and its level keys). Nothing here is a second list: each set is the parser's.
 */
public final class KeyTables {

    private KeyTables() {}

    /** The place (panel, strip, column, control, layout, phone, panel.phone, source, emit, children, form.*, level, marker) to its key set. */
    public static Map<String, Set<String>> byPlace() {
        Map<String, Set<String>> m = new LinkedHashMap<>();
        m.put("panel", SutraBuilder.panelKeys());
        m.put("strip", SutraBuilder.stripKeys());
        m.put("column", SutraBuilder.columnKeys());
        m.put("control", ControlsReader.keys());
        m.put("layout", LayoutReader.keys());
        m.put("phone", PhoneReader.screenKeys());
        m.put("panel.phone", PhoneReader.panelKeys());
        m.put("source", WiringReader.sourceKeys());
        m.put("emit", WiringReader.emitKeys());
        m.put("children", WiringReader.groupKeys());
        m.putAll(Form.keySets());
        m.put("level", PanelOptions.LEVEL_KEYS);
        m.put("marker", PanelOptions.markerKeys());
        return Collections.unmodifiableMap(m);
    }
}
