/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * The function catalogue is the one source of Core's parameter names ({@link CoreParams} reads it): every function Core implements
 * has a catalogue entry and every catalogue entry is implemented; from level 1.1 named arguments bind by the catalogue's names.
 */
class CoreParamsTest {

    @Test
    void theCatalogueBuiltInIsTheSpecification() throws Exception {
        String spec = Files.readString(Path.of("../../spec/1/functions.yaml"));
        try (var in = CoreParams.class.getResourceAsStream("/" + CoreParams.RESOURCE)) {
            assertThat(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(spec);
        }
    }

    @Test
    void everyImplementedFunctionHasACatalogueEntryAndViceVersa() throws Exception {
        FunctionTable core = FunctionTable.core();
        Set<String> implemented = new TreeSet<>();
        for (String n : core.arity().keySet()) {
            implemented.add(Functions.oneName(n));
        }
        implemented.add("span");                                  // level 1.1's name of the aggregate (Level11)
        // Core's entries carry the parameters; the profile's functions and the engine's list functions Core compiles are listed too
        Object spec = com.ash.rachana.internal.Plain.yaml(Files.readString(Path.of("../../spec/1/functions.yaml")));
        Set<String> catalogued = new TreeSet<>(CoreParams.all().keySet());
        for (String section : new String[] {"drishti_profile", "engine_list_functions"}) {
            for (Object f : com.ash.rachana.internal.Plain.elements(com.ash.rachana.internal.Plain.path(spec, section))) {
                catalogued.add(com.ash.rachana.internal.Plain.text(com.ash.rachana.internal.Plain.path(f, "name")));
            }
        }
        Set<String> notCatalogued = new TreeSet<>(implemented);
        notCatalogued.removeAll(catalogued);
        Set<String> notImplemented = new TreeSet<>(catalogued);
        notImplemented.removeAll(implemented);
        assertThat(notCatalogued).as("implemented, not in the catalogue").isEmpty();
        assertThat(notImplemented).as("in the catalogue, not implemented").isEmpty();
    }

    @Test
    void namedArgumentsBindByTheCataloguesNamesFromLevel11() {
        ElCompiler v11 = new ElCompiler(16, ElLimits.DEFAULTS, FunctionTable.core(), "1.1");
        assertThat(v11.compile("avg([1, 2, 3], field = null)").eval(null)).isEqualTo(java.math.BigDecimal.valueOf(2));   // exact (SPEC 4.16 rule 7)
        assertThat(v11.compile("avg(list = [{'x': 1}, {'x': 3}], field = 'x')").eval(null)).isEqualTo(java.math.BigDecimal.valueOf(2));
        assertThat(v11.compile("round(x = 2.45, n = 1)").eval(null).toString()).isEqualTo("2.5");
        assertThatThrownBy(() -> v11.compile("avg([1], where = true)")).hasMessageContaining("its arguments: [list, field]");
        assertThatThrownBy(() -> v11.compile("coalesce(values = 1)")).hasMessageContaining("repeatable");
        ElCompiler v10 = new ElCompiler(16, ElLimits.DEFAULTS, FunctionTable.core(), "1.0");
        assertThatThrownBy(() -> v10.compile("avg([1, 2], field = 'x')")).hasMessageContaining("its arguments: [x, where]");
    }
}
